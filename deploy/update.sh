#!/usr/bin/env bash
# ============================================================
# Sahel Edu - mise a jour sans perte de donnees
#
#   sudo ./deploy/update.sh
#
# 1. sauvegarde complete (base + fichiers pedagogiques)
# 2. git pull (fast-forward uniquement)
# 3. reconstruction des images
# 4. redemarrage et verification de sante
#
# La base (volume mysql_data) et les fichiers ($DATA_DIR/uploads) ne sont
# jamais supprimes : `docker compose up -d` ne touche pas aux volumes.
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-/opt/sahel-edu}"
BRANCH="${BRANCH:-main}"

# shellcheck source=common.sh
. "$HERE/common.sh"

require_root
[[ -d "$APP_DIR/.git" ]] || die "$APP_DIR n'est pas un depot Git (installation par copie ?)."

log "sauvegarde prealable"
if [[ -x /usr/local/sbin/sahel-edu-backup ]]; then
  # ALLOW_NO_OFFSITE=1 : le disque hors site peut etre debranche, cela ne doit
  # pas empecher une mise a jour. En revanche un dump qui echoue fait toujours
  # echouer la commande (set -e), donc on ne remplace pas du code sans copie.
  ALLOW_NO_OFFSITE=1 /usr/local/sbin/sahel-edu-backup
else
  warn "script de sauvegarde absent : poursuite SANS sauvegarde prealable."
fi

log "recuperation du code ($BRANCH)"
git -C "$APP_DIR" fetch --prune origin
git -C "$APP_DIR" checkout "$BRANCH"
BEFORE="$(git -C "$APP_DIR" rev-parse --short HEAD)"
git -C "$APP_DIR" pull --ff-only origin "$BRANCH"
AFTER="$(git -C "$APP_DIR" rev-parse --short HEAD)"
log "version : $BEFORE -> $AFTER"

# ------------------------------------------------------------------
# Migration : conteneurs non root + copie hors site obligatoire.
#
# Deux consequences de cette version cassent une installation existante si
# elles ne sont pas appliquees ici :
#
#   1. le backend tourne desormais en UID 10001. Un volume uploads reste en
#      root:root le laisserait dans l'impossibilite d'ecrire : les
#      televersements echouent alors que le conteneur demarre normalement,
#      donc sans aucune erreur visible au deploiement.
#   2. le script de sauvegarde ne vit PAS dans le depot deploye, mais dans
#      /usr/local/sbin : `git pull` ne le met jamais a jour. Sans cette
#      reinstalation, une installation existante conserverait indefiniment
#      l'ancienne version, celle qui se contentait d'avertir en l'absence de
#      copie hors site — c'est-a-dire exactement le defaut qu'on corrige.
# ------------------------------------------------------------------
log "migration : proprietaire du volume des fichiers pedagogiques"
if [[ -d "$DATA_DIR/uploads" ]]; then
  chown -R "${APP_UID}:${APP_GID}" "$DATA_DIR/uploads"
  chmod 750 "$DATA_DIR/uploads"
  log "  $DATA_DIR/uploads -> ${APP_UID}:${APP_GID}"
else
  warn "$DATA_DIR/uploads introuvable : verification manuelle necessaire."
fi

log "migration : environnement de la sauvegarde"
install -m 0755 -d /etc/sahel-edu
if [[ -f "$BACKUP_ENV_FILE" ]]; then
  log "  $BACKUP_ENV_FILE conserve (valeur existante respectee)"
else
  cat > "$BACKUP_ENV_FILE" <<EOF
# Genere par deploy/update.sh — ne pas editer a la main, relancer le script.
APP_DIR=$APP_DIR
DATA_DIR=$DATA_DIR
OFFSITE_DIR=${OFFSITE_DIR:-}
RETENTION_DAYS=14
EOF
  chmod 600 "$BACKUP_ENV_FILE"
  if [[ -z "${OFFSITE_DIR:-}" ]]; then
    warn "OFFSITE_DIR n'est pas configure : la sauvegarde va ECHOUER."
    warn "definissez-le dans $BACKUP_ENV_FILE, sur un disque distinct de $DATA_DIR,"
    warn "puis : sudo systemctl start sahel-edu-backup"
  fi
fi

log "reinstallation des unites systemd et du script de sauvegarde"
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu.service" /etc/systemd/system/sahel-edu.service
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu-backup.service" /etc/systemd/system/sahel-edu-backup.service
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu-backup.timer" /etc/systemd/system/sahel-edu-backup.timer
install -m 0755 "$APP_DIR/deploy/backup.sh" /usr/local/sbin/sahel-edu-backup
systemctl daemon-reload

# ------------------------------------------------------------------
# Garde-fou JWT : depuis REQUIRE_SECURE_SECRETS=true, un .env encore muni du
# secret par defaut du depot (public), vide, ou trop court pour HS256 fait
# ECHOUER le demarrage du backend. On le regenere ici, sinon une mise a jour
# laisserait la production en panne.
# ------------------------------------------------------------------
[[ -f "$ENV_FILE" ]] || die "docker/.env introuvable ($ENV_FILE) : lancer d'abord deploy/install.sh."

DEFAULT_JWT_SECRET="Y2hhbmdlLXRoaXMtc2VjcmV0LWtleS1pbi1wcm9kdWN0aW9uLXdpdGgtYTM3LWNyYXdhbGxlbg=="
CURRENT_JWT_SECRET="$(env_get JWT_SECRET)"
if [[ -z "$CURRENT_JWT_SECRET" || "$CURRENT_JWT_SECRET" == "$DEFAULT_JWT_SECRET" || "${#CURRENT_JWT_SECRET}" -lt 44 ]]; then
  warn "JWT_SECRET absent, par defaut ou trop court : generation d'une nouvelle valeur."
  warn "changer ce secret invalide les sessions en cours (les utilisateurs devront se reconnecter)."
  env_set JWT_SECRET "$(openssl rand -base64 48 | tr -d '\n')"
fi

# ------------------------------------------------------------------
# Compte applicatif MySQL : le backend ne doit pas se connecter en root.
#
# Les installations anterieures utilisent root. Le volume mysql_data etant deja
# initialise, la variable MYSQL_USER de docker-compose.yml est IGNOREE par
# l'image : l'utilisateur doit donc etre cree explicitement ici, AVANT le
# redemarrage. Sans cela, le backend relancerait avec un compte inexistant et
# resterait en panne apres la mise a jour.
# ------------------------------------------------------------------
DB_APP_USER="$(env_get DB_APP_USER)"
DB_APP_PASSWORD="$(env_get DB_APP_PASSWORD)"
DB_NAME_VALUE="$(env_get DB_NAME school_management)"
DB_ROOT_PASSWORD="$(env_get DB_PASSWORD)"

if [[ -z "$DB_APP_USER" || -z "$DB_APP_PASSWORD" ]]; then
  DB_APP_USER="${DB_APP_USER:-sahel_app}"
  DB_APP_PASSWORD="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-28)"
  log "compte applicatif MySQL absent du .env : creation de $DB_APP_USER"
fi

# Les valeurs sont injectees dans du SQL : un guillemet ou un antislash
# permettrait de sortir de la chaine. Les mots de passe generes ici sont
# alphanumeriques, la garde ne concerne donc que les .env edites a la main.
case "$DB_APP_USER" in
  *[\'\"\\]* ) die "DB_APP_USER contient un caractere interdit : $DB_APP_USER" ;;
esac
case "$DB_APP_PASSWORD" in
  *[\'\"\\]* ) die "DB_APP_PASSWORD contient un guillemet ou un antislash : retirez-le." ;;
esac

log "creation ou actualisation du compte applicatif $DB_APP_USER"
# Idempotent : rejoue sans risque a chaque mise a jour et realigne le mot de
# passe sur celui du .env. Les droits sont limites a la base de l'application.
if ! printf '%s\n' \
      "CREATE USER IF NOT EXISTS '${DB_APP_USER}'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';" \
      "ALTER USER '${DB_APP_USER}'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';" \
      "GRANT ALL PRIVILEGES ON \`${DB_NAME_VALUE}\`.* TO '${DB_APP_USER}'@'%';" \
      "FLUSH PRIVILEGES;" \
    | compose exec -T -e MYSQL_PWD="$DB_ROOT_PASSWORD" mysql mysql -uroot >/dev/null; then
  die "impossible de creer le compte applicatif : MySQL est-il demarre ? (docker compose ps)"
fi

env_set DB_APP_USER "$DB_APP_USER"
env_set DB_APP_PASSWORD "$DB_APP_PASSWORD"
log "compte applicatif enregistre dans $ENV_FILE"

log "reconstruction des images (les migrations Flyway s'appliquent au demarrage)"
compose build
compose up -d --remove-orphans

wait_healthy "http://127.0.0.1:$(env_get BACKEND_PORT 8080)/actuator/health" || true

printf '\n'
log "mise a jour terminee"
compose ps
printf '\nJournaux : journalctl -u sahel-edu -n 100\n'
