#!/usr/bin/env bash
# ============================================================
# Sahel Edu - sauvegarde de la base et des fichiers pedagogiques
#
#   sudo /usr/local/sbin/sahel-edu-backup
#
# Ce script est INSTALLE SEUL dans /usr/local/sbin : il ne source donc
# aucun fichier annexe (contrairement aux scripts lances depuis le depot).
#
# Appele chaque nuit par sahel-edu-backup.timer. Peut aussi etre lance
# a la main avant une mise a jour.
#
# Produit, dans $DATA_DIR/backups :
#   db/db-AAAAMMJJ-HHMM.sql.gz              dump complet (donnees + routines + triggers)
#   uploads/uploads-AAAAMMJJ-HHMM.tar.gz    videos, PDF et photos
#
# Retention : RETENTION_DAYS jours (14 par defaut).
# OFFSITE_DIR (facultatif) : copie des archives vers un disque distinct.
# ============================================================
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/sahel-edu}"
DATA_DIR="${DATA_DIR:-/srv/sahel-edu}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
OFFSITE_DIR="${OFFSITE_DIR:-}"
ENV_FILE="$APP_DIR/docker/.env"
COMPOSE_FILE="$APP_DIR/docker/docker-compose.yml"

log()  { printf '\033[1;34m[sahel-edu]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[attention]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[erreur]\033[0m %s\n' "$*" >&2; exit 1; }

[[ "${EUID}" -eq 0 ]] || die "a executer avec sudo"
[[ -f "$ENV_FILE" ]] || die "fichier .env introuvable : $ENV_FILE"
[[ -f "$COMPOSE_FILE" ]] || die "docker-compose.yml introuvable : $COMPOSE_FILE"

# Lecture sans `source` : les valeurs peuvent contenir espaces et caracteres speciaux.
# `tr -d '\r'` couvre le cas d'un .env edite sous Windows (CRLF) : sans cela le
# mot de passe lu se termine par un retour chariot et MySQL repond
# `Access denied` alors que le secret est correct.
env_get() {
  local key="$1" default="${2:-}" value
  value="$(sed -n "s/^[[:space:]]*${key}=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r')"
  value="${value%\"}"
  value="${value#\"}"
  printf '%s' "${value:-$default}"
}

DB_PASSWORD="$(env_get DB_PASSWORD)"
DB_NAME="$(env_get DB_NAME school_management)"
[[ -n "$DB_PASSWORD" ]] || die "DB_PASSWORD absent de $ENV_FILE"

STAMP="$(date +%Y%m%d-%H%M)"
DB_DIR="$DATA_DIR/backups/db"
UP_DIR="$DATA_DIR/backups/uploads"
mkdir -p "$DB_DIR" "$UP_DIR"

# Ecriture dans un fichier temporaire puis renommage : une sauvegarde
# interrompue (panne, disque plein, conteneur arrete) ne doit JAMAIS laisser
# une archive tronquee qui porterait le nom d'une sauvegarde valide.
DB_TMP="$DB_DIR/.db-$STAMP.sql.gz.part"
UP_TMP="$UP_DIR/.uploads-$STAMP.tar.gz.part"
cleanup() { rm -f "$DB_TMP" "$UP_TMP"; }
trap cleanup EXIT

log "sauvegarde de la base $DB_NAME"
# MYSQL_PWD evite le mot de passe en argument (visible dans ps) et le warning CLI.
docker compose -f "$COMPOSE_FILE" exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql \
  mysqldump -uroot --single-transaction --routines --triggers --databases "$DB_NAME" \
  | gzip -9 > "$DB_TMP"

[[ -s "$DB_TMP" ]] || die "dump vide : sauvegarde annulee"
gzip -t "$DB_TMP" || die "archive SQL corrompue"
mv -f "$DB_TMP" "$DB_DIR/db-$STAMP.sql.gz"
log "base sauvegardee : $(du -h "$DB_DIR/db-$STAMP.sql.gz" | cut -f1)"

log "sauvegarde des fichiers pedagogiques"
tar czf "$UP_TMP" -C "$DATA_DIR/uploads" .
tar tzf "$UP_TMP" >/dev/null || die "archive des fichiers corrompue"
mv -f "$UP_TMP" "$UP_DIR/uploads-$STAMP.tar.gz"
log "fichiers sauvegardes : $(du -h "$UP_DIR/uploads-$STAMP.tar.gz" | cut -f1)"

# ------------------------------------------------------------------
# Retention (uniquement dans les DEUX repertoires de sauvegarde)
# ------------------------------------------------------------------
find "$DB_DIR" -maxdepth 1 -name 'db-*.sql.gz'      -type f -mtime "+$RETENTION_DAYS" -delete
find "$UP_DIR" -maxdepth 1 -name 'uploads-*.tar.gz' -type f -mtime "+$RETENTION_DAYS" -delete
log "retention appliquee : $RETENTION_DAYS jours"

# ------------------------------------------------------------------
# Copie hors site : une sauvegarde sur le meme disque ne protege pas
# d'une panne de ce disque.
# ------------------------------------------------------------------
if [[ -n "$OFFSITE_DIR" ]]; then
  if mkdir -p "$OFFSITE_DIR" 2>/dev/null; then
    log "copie hors site vers $OFFSITE_DIR"
    cp -f "$DB_DIR/db-$STAMP.sql.gz" "$OFFSITE_DIR/"
    cp -f "$UP_DIR/uploads-$STAMP.tar.gz" "$OFFSITE_DIR/"
    find "$OFFSITE_DIR" -maxdepth 1 \( -name 'db-*.sql.gz' -o -name 'uploads-*.tar.gz' \) \
      -type f -mtime "+$RETENTION_DAYS" -delete
  else
    warn "copie hors site impossible : $OFFSITE_DIR non monte ?"
  fi
else
  warn "OFFSITE_DIR non defini : la sauvegarde reste sur le disque principal."
fi

log "sauvegarde terminee"
