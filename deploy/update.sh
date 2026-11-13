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

# Le .env contient DB_PASSWORD, JWT_SECRET et ADMIN_PASSWORD. Un fichier cree a
# la main (ou copie depuis un poste de travail) reste en 644 : tous les comptes
# du serveur peuvent alors lire les secrets. Aucun autre script ne corrige cette
# permission, donc elle survivrait indefiniment a chaque mise a jour.
if [[ -f "$ENV_FILE" ]]; then
  ETAT_ENV="$(stat -c '%a %U:%G' "$ENV_FILE")"
  if [[ "$ETAT_ENV" != "600 root:root" ]]; then
    warn "permissions de $ENV_FILE : $ETAT_ENV -> 600 root:root"
    chown root:root "$ENV_FILE"
    chmod 600 "$ENV_FILE"
  fi
fi

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
# C'est UPLOAD_HOST_DIR qui est monte dans le conteneur, pas forcement
# $DATA_DIR/uploads. Chown-er le mauvais repertoire laissait les
# televersements casses apres une mise a jour qui semblait pourtant reussie.
UPLOAD_DIR_REELLE="$(env_get UPLOAD_HOST_DIR "$DATA_DIR/uploads")"
if [[ -d "$UPLOAD_DIR_REELLE" ]]; then
  chown -R "${APP_UID}:${APP_GID}" "$UPLOAD_DIR_REELLE"
  chmod 750 "$UPLOAD_DIR_REELLE"
  log "  $UPLOAD_DIR_REELLE -> ${APP_UID}:${APP_GID}"
else
  warn "$UPLOAD_DIR_REELLE introuvable : verification manuelle necessaire."
  warn "  (valeur lue dans UPLOAD_HOST_DIR de $ENV_FILE)"
fi

log "migration : environnement de la sauvegarde"
install -m 0755 -d "$(dirname "$BACKUP_ENV_FILE")"
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

# Le repertoire hors site n'etait cree que par install.sh. Une mise a jour qui
# recoit OFFSITE_DIR doit le creer aussi : sinon la sauvegarde echouerait sur un
# chemin inexistant, longtemps apres une mise a jour qui semblait reussie.
# La valeur est relue depuis le fichier, qu'il vienne d'etre cree ou qu'il
# existe deja : c'est la seule qui compte pour l'unite systemd.
OFFSITE_EFFECTIF="$(sed -n 's/^OFFSITE_DIR=//p' "$BACKUP_ENV_FILE" | tail -n 1)"
if [[ -n "$OFFSITE_EFFECTIF" ]]; then
  mkdir -p "$OFFSITE_EFFECTIF"
  chmod 750 "$OFFSITE_EFFECTIF"
  chown root:root "$OFFSITE_EFFECTIF"
  log "  copie hors site vers $OFFSITE_EFFECTIF"
  if [[ "$(stat -c %d "$OFFSITE_EFFECTIF")" == "$(stat -c %d "$DATA_DIR")" ]]; then
    warn "$OFFSITE_EFFECTIF est sur le MEME disque que $DATA_DIR : protection nulle."
  fi
fi

log "reinstallation des unites systemd et des scripts d'exploitation"
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu.service" /etc/systemd/system/sahel-edu.service
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu-backup.service" /etc/systemd/system/sahel-edu-backup.service
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu-backup.timer" /etc/systemd/system/sahel-edu-backup.timer
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu-check.service" /etc/systemd/system/sahel-edu-check.service
install -m 0644 "$APP_DIR/deploy/systemd/sahel-edu-check.timer" /etc/systemd/system/sahel-edu-check.timer
install -m 0755 "$APP_DIR/deploy/backup.sh" /usr/local/sbin/sahel-edu-backup
install -m 0755 "$APP_DIR/deploy/healthcheck.sh" /usr/local/sbin/sahel-edu-healthcheck
systemctl daemon-reload
# Le minuteur de controle n'existait pas dans les versions precedentes : sans
# cet enable, une installation mise a jour resterait sans surveillance alors
# que ses unites sont bien presentes sur le disque.
systemctl enable --now sahel-edu-check.timer >/dev/null 2>&1 \
  || warn "timer de controle de sante non active"

# ------------------------------------------------------------------
# Garde-fou JWT : depuis REQUIRE_SECURE_SECRETS=true, un .env encore muni du
# secret par defaut du depot (public), vide, ou trop court pour HS256 fait
# ECHOUER le demarrage du backend. On le regenere ici, sinon une mise a jour
# laisserait la production en panne.
# ------------------------------------------------------------------
[[ -f "$ENV_FILE" ]] || die "docker/.env introuvable ($ENV_FILE) : lancer d'abord deploy/install.sh."

# ------------------------------------------------------------------
# Garde-fou : variables sans valeur par defaut dans le nouveau compose.
#
# Un .env genere par une version anterieure peut ignorer une variable que le
# compose actuel reference sans defaut. Compose la resout alors a la chaine
# vide et refuse de demarrer : la mise a jour couperait le service, sans que
# la cause soit lisible dans les journaux.
#
# UPLOAD_HOST_DIR est la seule variable dans ce cas (le job CI
# « Exploitation » verifie qu'il n'y en a pas d'autre). Si une variable de ce
# genre est ajoutee plus tard, c'est ici qu'il faudra la traiter.
# ------------------------------------------------------------------
if [[ -z "$(env_get UPLOAD_HOST_DIR)" ]]; then
  warn "UPLOAD_HOST_DIR absent de $ENV_FILE : ajout de $DATA_DIR/uploads"
  env_set UPLOAD_HOST_DIR "$DATA_DIR/uploads"
fi

# Documentation interactive : les .env anterieurs ne la connaissaient pas, et
# le compose la resout a true par defaut. On l'ajoute UNIQUEMENT si la cle est
# absente : une installation qui l'a volontairement laissee a true (recette,
# demonstration) ne doit pas etre fermee a son insu par une mise a jour.
if ! grep -qE '^[[:space:]]*SWAGGER_ENABLED=' "$ENV_FILE"; then
  warn "SWAGGER_ENABLED absent de $ENV_FILE : ajout de la valeur de production (false)"
  env_set SWAGGER_ENABLED false
fi

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

# La pile appartient a systemd : `sahel-edu.service` fait tourner un
# `docker compose up` au premier plan. Lancer un second `compose up -d` ici
# mettrait deux processus en concurrence sur les memes conteneurs, et le
# service pouvait finir « dead » alors que les conteneurs tournaient — un
# redemarrage ulterieur aurait alors tout arrete sans prevenir.
# On arrete donc la pile, on reconstruit, puis on la relance par systemd,
# exactement comme le fait install.sh.
log "arret de la pile (les volumes sont conserves)"
systemctl stop sahel-edu.service 2>/dev/null || true

log "reconstruction des images (les migrations Flyway s'appliquent au demarrage)"
if ! compose build; then
  # Une construction en echec laisse le serveur sans service : on relance
  # l'ancienne version, qui reste utilisable, plutot que de laisser le site
  # a l'arret en attendant quelqu'un.
  warn "construction en echec : redemarrage de la version precedente."
  systemctl start sahel-edu.service 2>/dev/null || true
  die "mise a jour interrompue ; l'ancienne version a ete relancee."
fi

log "demarrage de la nouvelle version"
systemctl start sahel-edu.service

wait_healthy "http://127.0.0.1:$(env_get BACKEND_PORT 8080)/actuator/health" || true

printf '\n'
log "mise a jour terminee"
compose ps
printf '\nJournaux : journalctl -u sahel-edu -n 100\n'
