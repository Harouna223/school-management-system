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
# OFFSITE_DIR (OBLIGATOIRE) : copie des archives vers un disque distinct.
#   Lu depuis /etc/sahel-edu/backup.env (EnvironmentFile de l'unite systemd).
#   Son absence fait ECHOUER le service : une sauvegarde qui ne quitte pas le
#   disque des donnees ne protege d'aucune panne materielle.
#
# Chaque archive est verifiee avant d'etre conservee : integrite de la
# compression, presence effective de tables dans le dump, et concordance octet
# par octet de la copie hors site. Une sauvegarde inutilisable fait echouer le
# service au lieu de rester silencieusement en place.
# ============================================================
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/sahel-edu}"
DATA_DIR="${DATA_DIR:-/srv/sahel-edu}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
OFFSITE_DIR="${OFFSITE_DIR:-}"
# Derogation EXPLICITE : la copie hors site echoue en simple avertissement.
# Utilisee par deploy/update.sh pour la sauvegarde prealable — une mise a jour
# ne doit pas etre bloquee par un disque externe debranche — et jamais par le
# service systemd, qui doit au contraire signaler la panne.
ALLOW_NO_OFFSITE="${ALLOW_NO_OFFSITE:-0}"
ENV_FILE="$APP_DIR/docker/.env"
COMPOSE_FILE="$APP_DIR/docker/docker-compose.yml"

log()  { printf '\033[1;34m[sahel-edu]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[attention]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[erreur]\033[0m %s\n' "$*" >&2; exit 1; }

# La copie hors site n'a pas pu etre faite. Par defaut c'est un ECHEC du
# service : sans cela l'installation reste des annees sans sauvegarde
# reellement detachee, et personne ne s'en apercoit avant la panne.
hors_site_indisponible() {
  if [[ "$ALLOW_NO_OFFSITE" == "1" ]]; then
    warn "copie hors site NON effectuee : $1"
    warn "ALLOW_NO_OFFSITE=1 : echec tolere (la sauvegarde locale est produite)."
    return 0
  fi
  die "copie hors site impossible : $1
  La sauvegarde locale a bien ete produite, mais elle reste sur le disque des
  donnees. Renseignez OFFSITE_DIR dans /etc/sahel-edu/backup.env, puis :
    sudo systemctl start sahel-edu-backup"
}

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

# `gzip -t` ne prouve que l'integrite de la compression : un dump parfaitement
# valide peut ne contenir AUCUNE table (mauvais nom de base, base vide, droits
# insuffisants). Cette sauvegarde-la serait acceptee puis decouverte inutilisable
# le jour de la restauration. On exige donc la presence effective de tables.
NB_TABLES="$(gzip -dc "$DB_TMP" | grep -c '^CREATE TABLE' || true)"
[[ "${NB_TABLES:-0}" -ge 1 ]] || die "dump sans aucune table : sauvegarde refusee"

mv -f "$DB_TMP" "$DB_DIR/db-$STAMP.sql.gz"
log "base sauvegardee : $(du -h "$DB_DIR/db-$STAMP.sql.gz" | cut -f1) ($NB_TABLES tables)"

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
    # Une copie tronquee (disque externe plein, support debranche en cours de
    # copie) ne doit jamais passer pour une sauvegarde valide : on compare
    # octet par octet avec l'original.
    verifier_copie() {
      cmp -s "$1" "$OFFSITE_DIR/$2" || die "copie hors site alteree ou incomplete : $2"
    }
    verifier_copie "$DB_DIR/db-$STAMP.sql.gz" "db-$STAMP.sql.gz"
    verifier_copie "$UP_DIR/uploads-$STAMP.tar.gz" "uploads-$STAMP.tar.gz"
    log "copie hors site verifiee"
    find "$OFFSITE_DIR" -maxdepth 1 \( -name 'db-*.sql.gz' -o -name 'uploads-*.tar.gz' \) \
      -type f -mtime "+$RETENTION_DAYS" -delete
  else
    # Le support hors site est absent ou non monte : c'est exactement le cas
    # qu'un simple avertissement laissait passer inapercu pendant des mois.
    hors_site_indisponible "$OFFSITE_DIR inaccessible (disque non monte ?)"
  fi
else
  hors_site_indisponible "OFFSITE_DIR non defini"
fi

log "sauvegarde terminee"
