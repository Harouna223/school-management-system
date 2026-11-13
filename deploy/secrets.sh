#!/usr/bin/env bash
# ============================================================
# Sahel Edu - audit et rotation des secrets
#
#   sudo ./deploy/secrets.sh audit          etat des lieux (lecture seule)
#   sudo ./deploy/secrets.sh perms          remet .env en 600 root:root
#   sudo ./deploy/secrets.sh rotate-jwt     nouveau JWT_SECRET
#   sudo ./deploy/secrets.sh rotate-db-app  nouveau mot de passe applicatif
#
# POURQUOI CE SCRIPT
# Les secrets d'une installation vivent dans un fichier plat, docker/.env, lu
# par docker compose. Rien ne verifie qu'il est lisible par root seul, que ses
# valeurs ne sont pas restees celles du modele, ni qu'elles ont ete renouvelees
# apres un depart de personnel. Ce script repond a ces trois questions.
#
# Il ne devine pas : chaque constat est une mesure (stat, comparaison, requete
# SQL), et le code de sortie vaut 1 des qu'un probleme est confirme.
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

# Doit rester identique a DEFAULT_JWT_SECRET de
# backend-springboot/src/main/java/com/school/config/SecurityStartupWarning.java.
# Cette valeur est publique : la reutiliser permet de forger des jetons valides.
JWT_SECRET_DU_DEPOT="Y2hhbmdlLXRoaXMtc2VjcmV0LWtleS1pbi1wcm9kdWN0aW9uLXdpdGgtYTM3LWNyYXdhbGxlbg=="
MOT_DE_PASSE_ADMIN_DEFAUT="Admin@123"
MIN_OCTETS_JWT=32

PROBLEMES=0
AVERTISSEMENTS=0

usage() {
  cat <<'EOF'
Audit et rotation des secrets Sahel Edu.

  sudo ./deploy/secrets.sh audit            etat des lieux, lecture seule
  sudo ./deploy/secrets.sh perms            .env en 600 root:root
  sudo ./deploy/secrets.sh rotate-jwt       nouveau secret de signature JWT
  sudo ./deploy/secrets.sh rotate-db-app    nouveau mot de passe applicatif

Le code de sortie vaut 1 si un probleme est confirme (secret absent, faible,
ou fichier lisible par un autre compte que root).
EOF
}

ok()    { printf '  \033[1;32mOK\033[0m         %s\n' "$*"; }
ko()    { printf '  \033[1;31mA CORRIGER\033[0m  %s\n' "$*"; PROBLEMES=$((PROBLEMES + 1)); }
alerte() { printf '  \033[1;33mATTENTION\033[0m  %s\n' "$*"; AVERTISSEMENTS=$((AVERTISSEMENTS + 1)); }
info()  { printf '  %s\n' "$*"; }

mysql_root() {
  compose exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql mysql -uroot "$@"
}

# Longueur en octets d'un secret Base64 une fois decode. C'est cette valeur,
# pas la longueur de la chaine, qui decide si HS256 est sur.
octets_decodes() {
  local n
  n="$(printf '%s' "$1" | base64 -d 2>/dev/null | wc -c)" || n=0
  printf '%s' "${n// /}"
}

# ------------------------------------------------------------------
# audit
# ------------------------------------------------------------------
audit_fichiers() {
  log "Fichiers de secrets"
  local fichier etat mode
  for fichier in "$ENV_FILE" "$BACKUP_ENV_FILE"; do
    if [[ ! -f "$fichier" ]]; then
      ko "$fichier absent"
      continue
    fi
    etat="$(stat -c '%a %U:%G' "$fichier")"
    mode="${etat%% *}"
    if [[ "$mode" != "600" ]]; then
      ko "$fichier -> $etat (attendu 600 root:root) : lisible par d'autres comptes"
    elif [[ "${etat#* }" != "root:root" ]]; then
      alerte "$fichier -> $etat (proprietaire attendu : root:root)"
    else
      ok "$fichier -> $etat"
    fi
  done
}

audit_derive() {
  log "Derive par rapport a docker/.env.example"
  local modele="$APP_DIR/docker/.env.example" manquantes
  if [[ ! -f "$modele" ]]; then
    alerte "modele introuvable ($modele) : derive non verifiable"
    return 0
  fi
  # Une cle ajoutee au modele apres l'installation n'existe pas dans le .env
  # d'une machine deja en service. Le compose retombe alors sur sa valeur par
  # defaut, qui n'est pas toujours la bonne : sans DB_APP_USER, le backend se
  # reconnecte en root sans que rien ne le signale.
  manquantes="$(comm -23 \
    <(grep -oE '^[A-Z_0-9]+=' "$modele" | tr -d '=' | sort -u) \
    <(grep -oE '^[A-Z_0-9]+=' "$ENV_FILE" | tr -d '=' | sort -u) | tr '\n' ' ')"
  if [[ -z "${manquantes// /}" ]]; then
    ok "aucune cle manquante"
  else
    alerte "cles du modele absentes du .env : ${manquantes% }"
    info "les valeurs par defaut du compose s'appliquent a ces cles"
  fi
}

audit_secrets() {
  log "Valeurs des secrets"
  local jwt db_password db_app_user db_app_password admin_password octets admin_existe

  jwt="$(env_get JWT_SECRET)"
  if [[ -z "$jwt" ]]; then
    ko "JWT_SECRET vide : le backend refuse de demarrer (REQUIRE_SECURE_SECRETS)"
  elif [[ "$jwt" == "$JWT_SECRET_DU_DEPOT" ]]; then
    ko "JWT_SECRET est la valeur du depot : n'importe qui peut forger des jetons"
  else
    octets="$(octets_decodes "$jwt")"
    if [[ "$octets" -lt "$MIN_OCTETS_JWT" ]]; then
      ko "JWT_SECRET decode en $octets octets (< $MIN_OCTETS_JWT requis pour HS256)"
    else
      ok "JWT_SECRET defini ($octets octets decodes)"
    fi
  fi

  db_password="$(env_get DB_PASSWORD)"
  case "$db_password" in
    "")      ko "DB_PASSWORD vide" ;;
    root|changeme|password) ko "DB_PASSWORD vaut « $db_password »" ;;
    *)       ok "DB_PASSWORD defini" ;;
  esac

  db_app_user="$(env_get DB_APP_USER)"
  db_app_password="$(env_get DB_APP_PASSWORD)"
  if [[ -z "$db_app_user" ]]; then
    alerte "DB_APP_USER vide : le backend se connecte en root MySQL"
  elif [[ -z "$db_app_password" ]]; then
    ko "DB_APP_USER defini sans DB_APP_PASSWORD : MySQL refuse de demarrer"
  elif [[ "$db_app_password" == "$db_password" ]]; then
    ko "DB_APP_PASSWORD identique a DB_PASSWORD : le compte applicatif devient administrateur"
  else
    ok "compte applicatif $db_app_user defini"
  fi

  # ADMIN_PASSWORD ne sert qu'a la CREATION du compte admin. Une fois le compte
  # en base, la valeur du .env n'a plus aucun effet — mais reste exposee par
  # `docker inspect` et dans toute copie du fichier. C'est un secret mort.
  admin_password="$(env_get ADMIN_PASSWORD)"
  admin_existe="?"
  if mysql_root -N -e "SELECT 1" >/dev/null 2>&1; then
    admin_existe="$(mysql_root -N -e \
      "SELECT COUNT(*) FROM \`$(env_get DB_NAME school_management)\`.users WHERE username='admin'" 2>/dev/null || printf '?')"
  fi
  if [[ "$admin_existe" == "0" ]]; then
    if [[ -z "$admin_password" ]]; then
      ko "ADMIN_PASSWORD vide alors qu'aucun compte admin n'existe : demarrage impossible"
    elif [[ "$admin_password" == "$MOT_DE_PASSE_ADMIN_DEFAUT" ]]; then
      ko "ADMIN_PASSWORD vaut la valeur par defaut « $MOT_DE_PASSE_ADMIN_DEFAUT »"
    else
      ok "ADMIN_PASSWORD defini (compte admin a creer au prochain demarrage)"
    fi
  elif [[ "$admin_existe" == "?" ]]; then
    alerte "MySQL injoignable : impossible de savoir si ADMIN_PASSWORD sert encore"
  elif [[ -n "$admin_password" ]]; then
    alerte "ADMIN_PASSWORD inutile (compte admin deja cree) mais toujours expose : vider la cle"
    info "le mot de passe reel est celui stocke (hache) en base, pas cette valeur"
  else
    ok "ADMIN_PASSWORD vide (compte admin deja cree)"
  fi
}

audit_exposition() {
  log "Exposition"
  local conteneur secrets nb=0
  for conteneur in $(compose ps -q 2>/dev/null || true); do
    secrets="$(docker inspect "$conteneur" \
      --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
      | grep -cE '^(JWT_SECRET|DB_PASSWORD|DB_APP_PASSWORD|ADMIN_PASSWORD|MAIL_PASSWORD|WA_META_TOKEN|WA_TWILIO_TOKEN|SMS_TWILIO_TOKEN)=.+' || true)"
    nb=$((nb + ${secrets:-0}))
  done
  if [[ "$nb" -gt 0 ]]; then
    info "$nb variables sensibles visibles par « docker inspect » sur les conteneurs en cours"
    info "attendu : le groupe docker equivaut a root ; ne pas coller cette sortie dans un ticket"
  else
    ok "aucun secret visible dans les conteneurs en cours"
  fi
  # Le mot de passe MySQL ne doit jamais apparaitre en argument de commande :
  # `ps` est lisible par tous les comptes du serveur.
  if command -v pgrep >/dev/null 2>&1 && pgrep -af 'mysql.*-p[^ ]' >/dev/null 2>&1; then
    alerte "un processus mysql expose un mot de passe en argument (visible par ps)"
  else
    ok "aucun mot de passe en argument de commande"
  fi
}

cmd_audit() {
  log "audit des secrets - $ENV_FILE"
  DB_PASSWORD="$(env_get DB_PASSWORD)"
  audit_fichiers
  audit_derive
  audit_secrets
  audit_exposition
  printf '\n'
  if [[ "$PROBLEMES" -gt 0 ]]; then
    die "$PROBLEMES probleme(s) confirme(s), $AVERTISSEMENTS avertissement(s)"
  fi
  log "audit termine : $AVERTISSEMENTS avertissement(s), aucun probleme confirme"
}

# ------------------------------------------------------------------
# perms
# ------------------------------------------------------------------
cmd_perms() {
  local fichier
  for fichier in "$ENV_FILE" "$BACKUP_ENV_FILE"; do
    [[ -f "$fichier" ]] || continue
    log "durcissement de $fichier"
    info "avant : $(stat -c '%a %U:%G' "$fichier")"
    chown root:root "$fichier"
    chmod 600 "$fichier"
    info "apres : $(stat -c '%a %U:%G' "$fichier")"
  done
}

# ------------------------------------------------------------------
# Rotation : le backend est recree par systemd, jamais par un compose direct
# ------------------------------------------------------------------
recharger_et_verifier() {
  local ancien_jwt="${1:-}"
  systemctl reload sahel-edu.service 2>/dev/null || true
  if wait_healthy "http://127.0.0.1:$(env_get BACKEND_PORT 8080)/actuator/health" 60; then
    return 0
  fi
  if [[ -n "$ancien_jwt" ]]; then
    warn "le backend ne demarre pas avec le nouveau secret : restauration de l'ancien"
    env_set JWT_SECRET "$ancien_jwt"
    systemctl reload sahel-edu.service 2>/dev/null || true
    wait_healthy "http://127.0.0.1:$(env_get BACKEND_PORT 8080)/actuator/health" 60 || true
  fi
  die "rotation annulee : le backend ne repond pas (journalctl -u sahel-edu -n 100)"
}

cmd_rotate_jwt() {
  require_root
  local ancien nouveau
  ancien="$(env_get JWT_SECRET)"
  nouveau="$(openssl rand -base64 48 | tr -d '\n')"
  log "rotation du secret JWT"
  env_set JWT_SECRET "$nouveau"
  info "nouveau secret ecrit dans $ENV_FILE"
  recharger_et_verifier "$ancien"
  ok "secret JWT renouvele"
  warn "toutes les sessions en cours sont invalides : chacun doit se reconnecter"
}

cmd_rotate_db_app() {
  require_root
  local utilisateur ancien nouveau db_name
  utilisateur="$(env_get DB_APP_USER)"
  [[ -n "$utilisateur" ]] || die "DB_APP_USER est vide : le backend se connecte en root MySQL.
  Lancez d'abord deploy/update.sh, qui cree le compte applicatif."
  db_name="$(env_get DB_NAME school_management)"
  DB_PASSWORD="$(env_get DB_PASSWORD)"
  ancien="$(env_get DB_APP_PASSWORD)"
  nouveau="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-28)"

  log "rotation du mot de passe du compte $utilisateur"
  # Le mot de passe genere ne contient que des alphanumeriques : il peut etre
  # insere tel quel entre apostrophes, sans echappement.
  mysql_root <<SQL
ALTER USER '${utilisateur}'@'%' IDENTIFIED BY '${nouveau}';
GRANT ALL PRIVILEGES ON \`${db_name}\`.* TO '${utilisateur}'@'%';
FLUSH PRIVILEGES;
SQL
  env_set DB_APP_PASSWORD "$nouveau"
  info "nouveau mot de passe ecrit dans $ENV_FILE"

  systemctl reload sahel-edu.service 2>/dev/null || true
  if ! wait_healthy "http://127.0.0.1:$(env_get BACKEND_PORT 8080)/actuator/health" 60; then
    warn "le backend ne se connecte pas avec le nouveau mot de passe : retour a l'ancien"
    mysql_root <<SQL
ALTER USER '${utilisateur}'@'%' IDENTIFIED BY '${ancien}';
FLUSH PRIVILEGES;
SQL
    env_set DB_APP_PASSWORD "$ancien"
    systemctl reload sahel-edu.service 2>/dev/null || true
    die "rotation annulee : le backend ne repond pas (journalctl -u sahel-edu -n 100)"
  fi
  ok "mot de passe du compte $utilisateur renouvele"
}

case "${1:-audit}" in
  audit)        cmd_audit ;;
  perms)        require_root; cmd_perms ;;
  rotate-jwt)   cmd_rotate_jwt ;;
  rotate-db-app) cmd_rotate_db_app ;;
  -h|--help)    usage ;;
  *)            usage; die "commande inconnue : $1" ;;
esac
