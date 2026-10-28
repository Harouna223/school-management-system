#!/usr/bin/env bash
# ============================================================
# Sahel Edu - installation HORS LIGNE sur Ubuntu Server 24.04 LTS
#
#   sudo ./deploy/offline/install-offline.sh
#
# A executer depuis un bundle prepare par deploy/offline/build-bundle.sh :
#   deploy/offline/images/        images Docker (backend, frontend, MySQL)
#   deploy/offline/docker-debs/   paquets .deb de Docker Engine
#
# Aucun acces Internet n'est necessaire : ni compilation, ni telechargement
# d'image, ni apt-get. L'application reste joignable sur le reseau local
# meme si le serveur perd sa connexion Internet.
#
# Variables reconnues (meme contrat que deploy/install.sh) :
#   APP_DIR       /opt/sahel-edu      code de l'application
#   DATA_DIR      /srv/sahel-edu      donnees persistantes (uploads, backups)
#   DOMAIN        nom de domaine public (facultatif : renseigne CORS_ORIGINS)
#   ADMIN_PASSWORD mot de passe admin initial (sinon genere)
#   SERVICE_USER  utilisateur ajoute au groupe docker (defaut : sudo user)
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"
APP_DIR="${APP_DIR:-/opt/sahel-edu}"
DATA_DIR="${DATA_DIR:-/srv/sahel-edu}"
SCHOOL_NAME="${SCHOOL_NAME:-Sahel Edu}"
DOMAIN="${DOMAIN:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
SERVICE_USER="${SERVICE_USER:-${SUDO_USER:-root}}"
ENV_FILE="$APP_DIR/docker/.env"
IMAGES_DIR="${IMAGES_DIR:-$HERE/images}"
DEBS_DIR="${DEBS_DIR:-$HERE/docker-debs}"

# shellcheck source=../common.sh
. "$HERE/../common.sh"

require_root
require_ubuntu

if [[ "$(uname -m)" != "x86_64" ]]; then
  die "bundle construit pour x86_64 : architecture $(uname -m) non supportee."
fi

log "installation hors ligne dans $APP_DIR (donnees : $DATA_DIR)"

# ------------------------------------------------------------------
# 1. Verification du bundle (integrite avant toute modification)
# ------------------------------------------------------------------
[[ -d "$IMAGES_DIR" ]] || die "images introuvables : $IMAGES_DIR (lancer deploy/offline/build-bundle.sh)"
if [[ -f "$HERE/SHA256SUMS" ]]; then
  log "verification des sommes de controle"
  ( cd "$HERE" && sha256sum -c --quiet SHA256SUMS ) \
    || die "bundle corrompu : les sommes de controle ne correspondent pas."
fi

# ------------------------------------------------------------------
# 2. Docker Engine depuis les paquets du bundle
# ------------------------------------------------------------------
command -v openssl >/dev/null 2>&1 || die "openssl requis pour generer les secrets (paquet openssl)."

if ! command -v docker >/dev/null 2>&1; then
  log "installation de Docker Engine depuis les paquets du bundle"
  export DEBIAN_FRONTEND=noninteractive
  shopt -s nullglob
  debs=( "$DEBS_DIR"/*.deb )
  shopt -u nullglob
  (( ${#debs[@]} > 0 )) || die "aucun paquet .deb dans $DEBS_DIR"
  if ! dpkg -i "${debs[@]}" >/dev/null; then
    warn "installation directe incomplete : resolution des dependances locales"
    # Fonctionne hors ligne si les dependances sont deja presentes sur le
    # systeme (cas d'Ubuntu Server 24.04 standard) ; sinon apt indique
    # precisement le paquet manquant, avec un delai court pour ne pas bloquer
    # un serveur sans Internet.
    apt-get -o Acquire::http::Timeout=10 -o Acquire::https::Timeout=10 -f install -y >/dev/null \
      || die "dependances Docker manquantes : installer les .deb fournis dans $DEBS_DIR."
  fi
fi
systemctl enable --now docker >/dev/null 2>&1 || die "impossible de demarrer docker.service"
docker compose version >/dev/null 2>&1 || die "plugin 'docker compose' introuvable (paquet docker-compose-plugin)."

# ------------------------------------------------------------------
# 3. Chargement des images
# ------------------------------------------------------------------
charger_image() {
  local image="$1" archive="$2"
  [[ -f "$archive" ]] || die "archive manquante : $archive"
  if docker image inspect "$image" >/dev/null 2>&1; then
    log "image deja presente : $image"
  else
    log "chargement de $image"
    docker load -i "$archive" >/dev/null || die "echec du chargement de $archive"
    docker image inspect "$image" >/dev/null 2>&1 || die "image $image absente apres chargement."
  fi
}

charger_image "mysql:8.4"                "$IMAGES_DIR/mysql-8.4.tar.gz"
charger_image "sahel-edu-backend:local"  "$IMAGES_DIR/sahel-edu-backend.tar.gz"
charger_image "sahel-edu-frontend:local" "$IMAGES_DIR/sahel-edu-frontend.tar.gz"

# ------------------------------------------------------------------
# 4. Utilisateur et repertoires persistants
# ------------------------------------------------------------------
if [[ "$SERVICE_USER" != "root" ]] && id "$SERVICE_USER" >/dev/null 2>&1; then
  if ! id -nG "$SERVICE_USER" | tr ' ' '\n' | grep -qx docker; then
    log "ajout de $SERVICE_USER au groupe docker"
    usermod -aG docker "$SERVICE_USER"
    warn "$SERVICE_USER doit se reconnecter pour utiliser docker sans sudo."
  fi
fi

log "creation des repertoires persistants"
mkdir -p "$DATA_DIR/uploads" "$DATA_DIR/backups/db" "$DATA_DIR/backups/uploads"
chmod 750 "$DATA_DIR" "$DATA_DIR/uploads" "$DATA_DIR/backups"
chown -R root:root "$DATA_DIR"

# ------------------------------------------------------------------
# 5. Code de l'application
# ------------------------------------------------------------------
if [[ "$REPO_ROOT" == "$APP_DIR" ]]; then
  log "application deja en place dans $APP_DIR"
else
  log "copie de l'application vers $APP_DIR"
  mkdir -p "$APP_DIR"
  # Les artefacts du bundle (images, .deb) ne sont pas recopies : ils sont
  # deja charges dans Docker et ne doivent pas peser dans l'installation.
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete \
      --exclude '.git' --exclude 'node_modules' --exclude 'target' --exclude 'dist' \
      --exclude 'docker/.env' --exclude 'uploads' \
      --exclude 'deploy/offline/images' --exclude 'deploy/offline/docker-debs' \
      --exclude 'deploy/offline/SHA256SUMS' --exclude 'deploy/offline/manifest.txt' \
      "$REPO_ROOT/" "$APP_DIR/"
  else
    # rsync n'est pas garanti sur une installation minimale : tar applique
    # les memes exclusions et suffit pour une (re)installation.
    tar -C "$REPO_ROOT" -cf - \
      --exclude='./.git' --exclude='*/node_modules' --exclude='*/target' \
      --exclude='*/dist' --exclude='./dist' --exclude='./docker/.env' \
      --exclude='./uploads' --exclude='./deploy/offline/images' \
      --exclude='./deploy/offline/docker-debs' --exclude='./deploy/offline/SHA256SUMS' \
      --exclude='./deploy/offline/manifest.txt' \
      . | tar -C "$APP_DIR" -xf -
  fi
fi

# ------------------------------------------------------------------
# 6. Fichier d'environnement (secrets generes une seule fois)
# ------------------------------------------------------------------
GENERATED=0
if [[ -f "$ENV_FILE" ]]; then
  log "fichier .env existant conserve (secrets inchanges)"
else
  log "generation de docker/.env (secrets aleatoires)"
  GENERATED=1
  ADMIN_PASSWORD="$(generate_env_file "$ADMIN_PASSWORD")"
fi

# ------------------------------------------------------------------
# 7. Unites systemd (variante hors ligne : --no-build)
# ------------------------------------------------------------------
log "installation des unites systemd"
# L'unite est ecrite pour /opt/sahel-edu : on l'adapte si APP_DIR change.
sed "s|/opt/sahel-edu|$APP_DIR|g" "$HERE/../systemd/sahel-edu-offline.service" \
  > /etc/systemd/system/sahel-edu.service
chmod 0644 /etc/systemd/system/sahel-edu.service
install -m 0644 "$HERE/../systemd/sahel-edu-backup.service" /etc/systemd/system/sahel-edu-backup.service
install -m 0644 "$HERE/../systemd/sahel-edu-backup.timer" /etc/systemd/system/sahel-edu-backup.timer
install -m 0755 "$HERE/../backup.sh" /usr/local/sbin/sahel-edu-backup
systemctl daemon-reload

log "demarrage de la pile (aucune compilation, aucune connexion externe)"
systemctl enable sahel-edu.service >/dev/null 2>&1 || true
systemctl restart sahel-edu.service

# `curl` n'est pas garanti sur un serveur minimal : on lit l'etat de sante
# fourni par le healthcheck Docker du conteneur backend.
attendre_backend() {
  local i etat
  for ((i = 1; i <= 60; i++)); do
    etat="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' school-backend 2>/dev/null || true)"
    if [[ "$etat" == "healthy" ]]; then
      log "backend sain (healthcheck Docker)"
      return 0
    fi
    sleep 5
  done
  warn "le backend n'est pas encore sain (verifier : journalctl -u sahel-edu -n 100)"
  return 1
}
attendre_backend || true
systemctl enable --now sahel-edu-backup.timer >/dev/null 2>&1 || warn "timer de sauvegarde non active"

# ------------------------------------------------------------------
# 8. Pare-feu
# ------------------------------------------------------------------
if command -v ufw >/dev/null 2>&1; then
  if ! ufw status 2>/dev/null | grep -q "Status: active"; then
    warn "activation de UFW : les connexions SSH EN COURS sont conservees,"
    warn "mais le port 22 doit rester ouvert pour toute nouvelle connexion."
  fi
  log "configuration du pare-feu (22, 80, 443 entrant ; reste ferme)"
  ufw allow 22/tcp   >/dev/null 2>&1 || true
  ufw allow 80/tcp   >/dev/null 2>&1 || true
  ufw allow 443/tcp  >/dev/null 2>&1 || true
  ufw --force enable >/dev/null 2>&1 || true
fi

# ------------------------------------------------------------------
# 9. Recapitulatif
# ------------------------------------------------------------------
IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
printf '\n'
log "installation hors ligne terminee"
printf '  application   : %s\n' "$APP_DIR"
printf '  donnees       : %s\n' "$DATA_DIR"
printf '  URL locale    : http://%s\n' "${IP:-<ip-du-serveur>}"
printf '  etat          : systemctl status sahel-edu\n'
printf '  journaux      : journalctl -u sahel-edu -f\n'
printf '  sauvegardes   : systemctl list-timers sahel-edu-backup.timer\n'
if [[ "$GENERATED" -eq 1 ]]; then
  printf '\n'
  warn "MOT DE PASSE ADMINISTRATEUR INITIAL : $ADMIN_PASSWORD"
  warn "changez-le des la premiere connexion (Profil > mot de passe)."
  warn "il est aussi enregistre dans $ENV_FILE (chmod 600)."
fi
printf '\n'
printf 'Acces Internet : une fois le domaine et le NAT en place, lancer\n'
printf '  sudo ./deploy/https.sh ecole.example.com admin@example.com\n'
printf '(cette etape demande Internet pour obtenir le certificat ; le service\n'
printf ' local continue de fonctionner sans elle).\n'
