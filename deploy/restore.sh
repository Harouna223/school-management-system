#!/usr/bin/env bash
# ============================================================
# Sahel Edu - restauration d'une sauvegarde
#
#   sudo ./deploy/restore.sh --db /srv/sahel-edu/backups/db/db-AAAAMMJJ-HHMM.sql.gz
#   sudo ./deploy/restore.sh --db <archive> --uploads <archive>
#   sudo ./deploy/restore.sh --db <archive> --verify
#
# POURQUOI CE SCRIPT
# La restauration n'existait que sous forme de commandes a recopier depuis la
# documentation. Une procedure qu'on n'a jamais executee n'est pas une
# procedure : le jour de la panne c'est la premiere fois qu'on la tape, sur une
# production arretee, sans moyen de savoir si elle a reussi.
#
# DEUX MODES
#   restauration  Ecrase la base et les fichiers. Prend une sauvegarde de
#                 securite AVANT d'ecrire, pour que l'operation soit elle-meme
#                 reversible.
#   --verify      Restaure dans une base jetable et compare le CONTENU reel
#                 (CHECKSUM TABLE) table par table. Aucun effet de bord.
#                 C'est l'exercice a refaire regulierement.
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

usage() {
  cat <<'EOF'
Restauration d'une sauvegarde Sahel Edu.

  sudo ./deploy/restore.sh --db <archive.sql.gz> [--uploads <archive.tar.gz>]
  sudo ./deploy/restore.sh --db <archive.sql.gz> --verify

Options :
  --db <fichier>        archive de la base a restaurer (obligatoire)
  --uploads <fichier>   archive des fichiers pedagogiques (videos, PDF, photos)
  --verify              restaure dans une base jetable et compare le contenu,
                        sans toucher a la production (exercice de restauration)
  --yes, -y             ne pas demander de confirmation
  -h, --help            affiche cette aide

Sans --verify, l'operation ECRASE la base et, si --uploads est fourni, les
fichiers pedagogiques. Une sauvegarde de securite de l'etat actuel est prise
automatiquement avant toute ecriture.
EOF
}

DB_ARCHIVE=""
UP_ARCHIVE=""
MODE_VERIF=0
ASSUME_YES=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db)      [[ $# -ge 2 ]] || die "--db attend un fichier en argument"; DB_ARCHIVE="$2"; shift 2 ;;
    --uploads) [[ $# -ge 2 ]] || die "--uploads attend un fichier en argument"; UP_ARCHIVE="$2"; shift 2 ;;
    --verify)  MODE_VERIF=1; shift ;;
    --yes|-y)  ASSUME_YES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *)         usage; die "option inconnue : $1" ;;
  esac
done

[[ -n "$DB_ARCHIVE" ]] || { usage; die "--db est obligatoire"; }
[[ -f "$DB_ARCHIVE" ]] || die "archive introuvable : $DB_ARCHIVE"
if [[ -n "$UP_ARCHIVE" && ! -f "$UP_ARCHIVE" ]]; then
  die "archive de fichiers introuvable : $UP_ARCHIVE"
fi

require_root

DB_PASSWORD="$(env_get DB_PASSWORD)"
DB_NAME="$(env_get DB_NAME school_management)"
[[ -n "$DB_PASSWORD" ]] || die "DB_PASSWORD absent de $ENV_FILE"

STAMP="$(date +%Y%m%d-%H%M%S)"
SAFETY_DIR="$DATA_DIR/backups/db"

# Le client mysql est interroge en root : la restauration recree la base, ce
# qu'un compte applicatif n'a pas le droit de faire. Le mot de passe passe par
# MYSQL_PWD pour ne pas apparaitre dans `ps`, visible de tout utilisateur.
mysql_root() {
  compose exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql mysql -uroot "$@"
}

compter_tables() {
  local base="$1"
  mysql_root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$base'"
}

# Verifie qu'une archive est reellement restaurable et renvoie son nombre de
# tables. `gzip -t` seul ne prouve que l'integrite de la compression : un dump
# parfaitement valide peut ne contenir aucune table.
verifier_archive_db() {
  local fichier="$1" nb nb_use
  gzip -t "$fichier" 2>/dev/null || die "archive corrompue (gzip -t) : $fichier"
  # `grep -c` et non `grep -q` : avec `pipefail`, un `grep -q` qui trouve la
  # premiere occurrence ferme le tube, gzip meurt en SIGPIPE (141) et le
  # pipeline est declare en echec alors que l'archive est valide.
  nb="$(gzip -dc "$fichier" | grep -c '^CREATE TABLE' || true)"
  [[ "${nb:-0}" -ge 1 ]] || die "archive sans aucune table : $fichier"
  # backup.sh produit le dump avec --databases : il contient USE, sans quoi
  # l'import n'aurait aucune base cible et echouerait a mi-parcours.
  nb_use="$(gzip -dc "$fichier" | grep -c '^USE `' || true)"
  [[ "${nb_use:-0}" -ge 1 ]] \
    || die "archive sans instruction USE : produite sans --databases ? $fichier"
  printf '%s' "$nb"
}

NB_TABLES_ARCHIVE="$(verifier_archive_db "$DB_ARCHIVE")"

# ==================================================================
# Mode exercice : restauration dans une base jetable, production intacte
# ==================================================================
if [[ "$MODE_VERIF" -eq 1 ]]; then
  mysql_root -N -e "SELECT 1" >/dev/null 2>&1 \
    || die "MySQL injoignable. Demarrez la pile : sudo systemctl start sahel-edu.service"

  log "archive verifiee : $(basename "$DB_ARCHIVE") ($(du -h "$DB_ARCHIVE" | cut -f1), $NB_TABLES_ARCHIVE tables)"
  SCRATCH="verif_$(date +%Y%m%d%H%M%S)"
  log "exercice de restauration dans la base jetable $SCRATCH"

  mysql_root -e "DROP DATABASE IF EXISTS \`$SCRATCH\`"

  # Seules les deux lignes d'entete qui nomment la base sont reecrites. Un
  # remplacement global du nom corromprait les donnees si une chaine egale au
  # nom de la base figurait dans une ligne (un nom d'etablissement, par ex.).
  gzip -dc "$DB_ARCHIVE" \
    | sed -e "/^CREATE DATABASE/{s/\`$DB_NAME\`/\`$SCRATCH\`/}" \
          -e "/^USE /{s/\`$DB_NAME\`/\`$SCRATCH\`/}" \
    | mysql_root

  NB_SCRATCH="$(compter_tables "$SCRATCH")"
  if [[ "$NB_SCRATCH" -ne "$NB_TABLES_ARCHIVE" ]]; then
    mysql_root -e "DROP DATABASE \`$SCRATCH\`" || true
    die "restauration incomplete : $NB_SCRATCH tables restaurees sur $NB_TABLES_ARCHIVE attendues"
  fi

  # CHECKSUM TABLE compare le CONTENU, pas le nombre de lignes : un decalage
  # d'une ligne compense par une autre passerait inapercu avec des COUNT(*).
  # Une seule requete pour toutes les tables : autant d'allers-retours vers le
  # conteneur seraient inutilement lents.
  TABLES="$(mysql_root -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_type='BASE TABLE' ORDER BY table_name" || true)"
  NB_TABLES_COMPAREES="$(printf '%s\n' "$TABLES" | grep -c . || true)"

  if [[ "${NB_TABLES_COMPAREES:-0}" -eq 0 ]]; then
    warn "la base $DB_NAME est vide : aucune comparaison possible, seule la restauration est verifiee"
    mysql_root -e "DROP DATABASE \`$SCRATCH\`"
    log "exercice termine : $NB_TABLES_ARCHIVE tables restaurees depuis l'archive"
    exit 0
  fi

  PAIRES=""
  while IFS= read -r t; do
    [[ -n "$t" ]] || continue
    PAIRES="$PAIRES\`$DB_NAME\`.\`$t\`, \`$SCRATCH\`.\`$t\`, "
  done <<< "$TABLES"
  PAIRES="${PAIRES%, }"

  ECARTS="$(mysql_root -N -e "CHECKSUM TABLE $PAIRES" \
    | awk -F'\t' '{ v[NR] = $2; n[NR] = $1 }
        END {
          for (i = 1; i < NR; i += 2) {
            if (v[i] != v[i + 1]) printf "  ECART  %s : %s vs %s\n", n[i], v[i], v[i + 1]
          }
        }')"

  # La base jetable ne doit pas survivre a l'exercice : elle occuperait sinon
  # en double l'espace disque des donnees reelles.
  mysql_root -e "DROP DATABASE \`$SCRATCH\`"

  if [[ -n "$ECARTS" ]]; then
    printf '%s\n' "$ECARTS" >&2
    die "la sauvegarde n'est PAS fidele : des tables different apres restauration"
  fi

  log "exercice reussi : $NB_TABLES_COMPAREES tables comparees, contenu identique"
  log "la sauvegarde $(basename "$DB_ARCHIVE") est restaurable"
  exit 0
fi

# ==================================================================
# Restauration reelle
# ==================================================================

# --- Confirmation --------------------------------------------------
if [[ "$ASSUME_YES" -ne 1 ]]; then
  [[ -t 0 ]] || die "mode non interactif : ajoutez --yes pour confirmer"
  warn "La base $DB_NAME va etre SUPPRIMEE et remplacee par :"
  warn "  $(basename "$DB_ARCHIVE")"
  if [[ -n "$UP_ARCHIVE" ]]; then
    warn "Les fichiers pedagogiques seront remplaces par : $(basename "$UP_ARCHIVE")"
  fi
  printf 'Taper "restaurer" pour confirmer : '
  read -r reponse
  [[ "$reponse" == "restaurer" ]] || die "restauration annulee"
fi

# La pile appartient a systemd. Arreter le seul conteneur backend laisserait le
# processus `docker compose up` du service en vie mais prive de son conteneur :
# les journaux du backend cesseraient d'arriver dans journald sans que rien ne
# le signale. On arrete donc la pile par systemd, on ne relance que MySQL pour
# la duree de l'import, puis on rend la main a systemd.
#
# Le trap couvre le cas ou le script echoue en cours de route (archive tronquee,
# disque plein) : le serveur ne doit jamais rester a l'arret sans que personne
# ne le sache.
PILE_ARRETEE=0
relancer_pile_si_besoin() {
  local statut=$?
  if [[ "${PILE_ARRETEE}" -eq 1 ]]; then
    warn "restauration interrompue : relance de la pile pour remettre le site en ligne"
    systemctl start sahel-edu.service 2>/dev/null || true
  fi
  exit "$statut"
}
trap relancer_pile_si_besoin EXIT

log "arret de la pile (les volumes sont conserves)"
systemctl stop sahel-edu.service 2>/dev/null || true
PILE_ARRETEE=1

log "demarrage de MySQL seul pour la restauration"
compose up -d mysql >/dev/null
for _ in $(seq 1 60); do
  mysql_root -N -e "SELECT 1" >/dev/null 2>&1 && break
  sleep 2
done
mysql_root -N -e "SELECT 1" >/dev/null 2>&1 \
  || die "MySQL n'a pas demarre : docker compose ps / journalctl -u sahel-edu -n 50"

# --- Sauvegarde de securite de l'etat actuel -----------------------
# Une restauration est destructrice : si l'archive choisie n'est pas la bonne,
# il faut pouvoir revenir. Le nom ne correspond volontairement pas au motif
# db-*.sql.gz, sinon la retention de backup.sh purgerait au bout de 14 jours la
# seule copie de l'etat d'avant restauration.
NB_ACTUEL="$(compter_tables "$DB_NAME" 2>/dev/null || printf '0')"
if [[ "${NB_ACTUEL:-0}" -gt 0 ]]; then
  mkdir -p "$SAFETY_DIR"
  SAFETY="$SAFETY_DIR/avant-restauration-$STAMP.sql.gz"
  log "sauvegarde de securite de la base actuelle ($NB_ACTUEL tables)"
  compose exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql \
    mysqldump -uroot --single-transaction --routines --triggers --databases "$DB_NAME" \
    | gzip -9 > "$SAFETY.part"
  gzip -t "$SAFETY.part" || die "sauvegarde de securite illisible : abandon avant toute ecriture"
  mv -f "$SAFETY.part" "$SAFETY"
  log "etat actuel conserve dans $SAFETY"
else
  warn "base actuelle vide ou absente : aucune sauvegarde de securite a prendre"
fi

# --- Base ----------------------------------------------------------
log "restauration de la base $DB_NAME"
# DROP explicite : le dump contient CREATE DATABASE IF NOT EXISTS, qui
# laisserait en place les tables presentes en production mais absentes de
# l'archive. La base restauree serait alors un melange des deux etats, et rien
# ne le signalerait.
mysql_root -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;
               CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gzip -dc "$DB_ARCHIVE" | mysql_root

NB_RESTAURE="$(compter_tables "$DB_NAME")"
[[ "$NB_RESTAURE" -eq "$NB_TABLES_ARCHIVE" ]] \
  || die "restauration incomplete : $NB_RESTAURE tables en base, $NB_TABLES_ARCHIVE dans l'archive
  Etat precedent disponible : $SAFETY_DIR/avant-restauration-$STAMP.sql.gz"
log "base restauree : $NB_RESTAURE tables"

# --- Fichiers pedagogiques -----------------------------------------
if [[ -n "$UP_ARCHIVE" ]]; then
  tar tzf "$UP_ARCHIVE" >/dev/null 2>&1 || die "archive de fichiers corrompue : $UP_ARCHIVE"
  UP_DIR="$(env_get UPLOAD_HOST_DIR "$DATA_DIR/uploads")"
  mkdir -p "$UP_DIR"

  # L'existant est deplace, jamais supprime : une restauration de fichiers est
  # elle aussi reversible. `mv` du repertoire echoue si UPLOAD_HOST_DIR est un
  # point de montage (disque dedie) ; on deplace alors son contenu.
  if [[ -n "$(find "$UP_DIR" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
    ASIDE="${UP_DIR}.avant-restauration-$STAMP"
    if mv "$UP_DIR" "$ASIDE" 2>/dev/null; then
      mkdir -p "$UP_DIR"
    else
      mkdir -p "$ASIDE"
      find "$UP_DIR" -mindepth 1 -maxdepth 1 -exec mv {} "$ASIDE/" \;
    fi
    log "fichiers actuels conserves dans $ASIDE"
  fi

  log "restauration des fichiers pedagogiques"
  # Extraction en root : l'archive enregistre l'UID 10001 du backend, et c'est
  # cet UID qui doit etre retabli. Le chown qui suit couvre le cas d'une archive
  # produite ailleurs, ou d'un tar qui n'a pas preserve les proprietaires.
  tar xzf "$UP_ARCHIVE" -C "$UP_DIR"
  chown -R "$APP_UID:$APP_GID" "$UP_DIR"

  NB_FICHIERS_ARCHIVE="$(tar tzf "$UP_ARCHIVE" | grep -cv '/$' || true)"
  NB_FICHIERS_DISQUE="$(find "$UP_DIR" -type f | wc -l)"
  [[ "$NB_FICHIERS_DISQUE" -ge "$NB_FICHIERS_ARCHIVE" ]] \
    || warn "fichiers extraits ($NB_FICHIERS_DISQUE) < fichiers attendus ($NB_FICHIERS_ARCHIVE)"
  log "fichiers restaures : $NB_FICHIERS_DISQUE (proprietaire $APP_UID:$APP_GID)"
fi

# --- Redemarrage ---------------------------------------------------
log "redemarrage de la pile"
systemctl start sahel-edu.service
PILE_ARRETEE=0

# Le backend applique les migrations Flyway au demarrage : une archive plus
# ancienne que le code est migree automatiquement ; une archive plus recente
# fait echouer `validate` et le backend ne demarre pas. C'est le signal.
if wait_healthy "http://127.0.0.1:$(env_get BACKEND_PORT 8080)/actuator/health" 60; then
  log "restauration terminee"
  printf '\nJournaux : journalctl -u sahel-edu -n 100\n'
  if [[ -n "$UP_ARCHIVE" ]]; then
    printf 'Verification finale : televerser un fichier dans Cours et ressources.\n'
  fi
else
  die "le backend ne repond pas apres restauration
  Verifier : journalctl -u sahel-edu -n 100
  Une archive plus recente que le code fait echouer la validation Flyway."
fi
