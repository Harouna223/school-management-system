#!/usr/bin/env bash
# ============================================================
# Sahel Edu - installation sur Ubuntu Server 24.04 LTS
#
#   sudo ./deploy/install.sh
#   sudo DOMAIN=ecole.example.com ./deploy/install.sh
#
# Le script est IDEMPOTENT : relancez-le pour mettre a jour le code ou
# reparer une installation. Il ne remplace JAMAIS un fichier docker/.env
# existant (les secrets ne sont pas regeneres).
#
# Variables reconnues :
#   APP_DIR       /opt/sahel-edu      code de l'application
#   DATA_DIR      /srv/sahel-edu      donnees persistantes (uploads, backups)
#   OFFSITE_DIR   (obligatoire)       disque distinct recevant la copie hors site
#   ALLOW_NO_OFFSITE  0               passer a 1 pour installer SANS copie hors site
#   BRANCH        main
#   REPO_URL      depot Git (utilise si APP_DIR n'existe pas encore)
#   DOMAIN        nom de domaine public (facultatif : renseigne CORS_ORIGINS)
#   ADMIN_PASSWORD mot de passe admin initial (sinon genere)
#   SERVICE_USER  utilisateur ajoute au groupe docker (defaut : sudo user)
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$HERE")"
APP_DIR="${APP_DIR:-/opt/sahel-edu}"
DATA_DIR="${DATA_DIR:-/srv/sahel-edu}"
OFFSITE_DIR="${OFFSITE_DIR:-}"
ALLOW_NO_OFFSITE="${ALLOW_NO_OFFSITE:-0}"
BRANCH="${BRANCH:-main}"
REPO_URL="${REPO_URL:-https://github.com/Harouna223/school-management-system.git}"
SCHOOL_NAME="${SCHOOL_NAME:-Sahel Edu}"
DOMAIN="${DOMAIN:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
SERVICE_USER="${SERVICE_USER:-${SUDO_USER:-root}}"
ENV_FILE="$APP_DIR/docker/.env"

# UID/GID du backend non-root (voir docker/Dockerfile.backend : ARG APP_UID).
# Le volume des fichiers pedagogiques doit appartenir a cet UID, sinon les
# televersements echouent en « Permission denied ».
APP_UID="${APP_UID:-10001}"
APP_GID="${APP_GID:-10001}"

# shellcheck source=common.sh
. "$HERE/common.sh"

require_root
require_ubuntu

# L'unite sahel-edu.service code en dur /opt/sahel-edu (WorkingDirectory et
# chemin du compose). APP_DIR reste configurable pour le code et les donnees,
# mais le service ne suivrait pas : mieux vaut le dire maintenant que laisser
# `systemctl restart` echouer sur un "WorkingDirectory path does not exist"
# apres plusieurs minutes de construction d'images.
if [[ "$APP_DIR" != "/opt/sahel-edu" ]]; then
  warn "APP_DIR=$APP_DIR : l'unite systemd pointe sur /opt/sahel-edu."
  warn "le service ne demarrera pas tant que l'unite n'aura pas ete surchargee :"
  warn "  sudo systemctl edit sahel-edu.service   (voir docs/DEPLOIEMENT_UBUNTU.md)"
fi

log "installation dans $APP_DIR (donnees : $DATA_DIR)"

# ------------------------------------------------------------------
# 1. Paquets de base
# ------------------------------------------------------------------
log "mise a jour des paquets de base"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl git openssl rsync jq ufw >/dev/null

# ------------------------------------------------------------------
# 2. Docker Engine + plugin compose
# ------------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  log "installation de Docker Engine (depot officiel)"
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  # shellcheck disable=SC1091
  . /etc/os-release
  printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
    "$(dpkg --print-architecture)" "${VERSION_CODENAME}" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin >/dev/null
fi

systemctl enable --now docker >/dev/null 2>&1 || die "impossible de demarrer docker.service"
docker compose version >/dev/null 2>&1 || die "plugin 'docker compose' introuvable"

# ------------------------------------------------------------------
# 3. Utilisateur et repertoires persistants
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
chmod 750 "$DATA_DIR" "$DATA_DIR/backups"
chown -R root:root "$DATA_DIR/backups"

# Les fichiers pedagogiques appartiennent a l'UID du backend non-root, pas a
# root : le conteneur ecrit dans ce volume en tant que 10001. Un volume reste
# en root:root rendait le passage en non-root inoperant (uploads casses).
mkdir -p "$DATA_DIR/uploads"
chown -R "${APP_UID}:${APP_GID}" "$DATA_DIR/uploads"
chmod 750 "$DATA_DIR/uploads"

# ------------------------------------------------------------------
# Copie hors site : OBLIGATOIRE.
#
# Une sauvegarde posee sur le meme disque que les donnees ne protege ni d'une
# panne de ce disque, ni d'un ransomware, ni d'un vol de la machine. Le script
# de sauvegarde eteignait cette alerte en simple avertissement, ce qui
# revenait a installer une protection inexistante sans que personne ne le
# remarque.
#
# ALLOW_NO_OFFSITE=1 permet d'assumer explicitement l'absence de copie hors
# site (poste de developpement, evaluation) : le choix devient conscient et
# laisse une trace dans la sortie de l'installation.
# ------------------------------------------------------------------
if [[ -z "$OFFSITE_DIR" ]]; then
  if [[ "$ALLOW_NO_OFFSITE" == "1" ]]; then
    warn "ALLOW_NO_OFFSITE=1 : installation SANS copie hors site."
    warn "une panne du disque de $DATA_DIR detruirait les donnees ET leurs sauvegardes."
  else
    die "OFFSITE_DIR est obligatoire.
  Indiquez un repertoire situe sur un DISQUE DIFFERENT de $DATA_DIR, par exemple
  un disque USB monte en permanence :

    sudo OFFSITE_DIR=/media/backup/sahel-edu ./deploy/install.sh

  Pour installer malgre tout sans copie hors site (deconseille) :
    sudo ALLOW_NO_OFFSITE=1 ./deploy/install.sh"
  fi
else
  mkdir -p "$OFFSITE_DIR"
  chmod 750 "$OFFSITE_DIR"
  chown root:root "$OFFSITE_DIR"
  log "copie hors site vers $OFFSITE_DIR"
  # Un repertoire sur le meme disque n'apporte rien : on le signale sans
  # bloquer, car seul l'administrateur connait le montage reel (LVM, bind...).
  if [[ "$(stat -c %d "$OFFSITE_DIR")" == "$(stat -c %d "$DATA_DIR")" ]]; then
    warn "$OFFSITE_DIR est sur le MEME disque que $DATA_DIR."
    warn "la copie hors site ne protegerait pas d'une panne materielle."
  fi
fi

# ------------------------------------------------------------------
# 4. Code de l'application
# ------------------------------------------------------------------
if [[ -d "$APP_DIR/.git" ]]; then
  log "mise a jour du depot existant ($BRANCH)"
  git -C "$APP_DIR" fetch --prune origin
  git -C "$APP_DIR" checkout "$BRANCH"
  git -C "$APP_DIR" pull --ff-only origin "$BRANCH"
elif [[ -f "$REPO_ROOT/docker/docker-compose.yml" ]]; then
  log "copie des sources locales vers $APP_DIR"
  mkdir -p "$APP_DIR"
  rsync -a --delete \
    --exclude '.git' --exclude 'node_modules' --exclude 'target' --exclude 'dist' \
    --exclude 'docker/.env' --exclude 'uploads' \
    --exclude 'deploy/offline/images' --exclude 'deploy/offline/docker-debs' \
    "$REPO_ROOT/" "$APP_DIR/"
else
  log "clonage de $REPO_URL"
  mkdir -p "$(dirname "$APP_DIR")"
  git clone --branch "$BRANCH" --depth 1 "$REPO_URL" "$APP_DIR"
fi

# ------------------------------------------------------------------
# 5. Fichier d'environnement (secrets generes une seule fois)
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
# 6. Unites systemd
# ------------------------------------------------------------------
log "installation des unites systemd"
install -m 0644 "$HERE/systemd/sahel-edu.service" /etc/systemd/system/sahel-edu.service
install -m 0644 "$HERE/systemd/sahel-edu-backup.service" /etc/systemd/system/sahel-edu-backup.service
install -m 0644 "$HERE/systemd/sahel-edu-backup.timer" /etc/systemd/system/sahel-edu-backup.timer
install -m 0755 "$HERE/backup.sh" /usr/local/sbin/sahel-edu-backup

# Environnement de la sauvegarde, lu par l'unite via EnvironmentFile.
#
# Pourquoi un fichier separe plutot qu'une substitution dans l'unite : l'unite
# est un fichier du depot, copie tel quel. Y ecrire des chemins a l'installation
# la rendrait differente de celle du depot, et une mise a jour du depot
# ecraserait silencieusement OFFSITE_DIR. Ici, relancer install.sh ou update.sh
# ne fait que reecrire ce fichier d'environnement.
install -m 0755 -d "$(dirname "$BACKUP_ENV_FILE")"
cat > "$BACKUP_ENV_FILE" <<EOF
# Genere par deploy/install.sh — ne pas editer a la main, relancer le script.
APP_DIR=$APP_DIR
DATA_DIR=$DATA_DIR
OFFSITE_DIR=$OFFSITE_DIR
RETENTION_DAYS=14
EOF
chmod 600 "$BACKUP_ENV_FILE"

systemctl daemon-reload

log "demarrage de la pile"
systemctl enable sahel-edu.service >/dev/null 2>&1 || true
systemctl restart sahel-edu.service

BACKEND_PORT_VALUE="$(env_get BACKEND_PORT 8080)"
wait_healthy "http://127.0.0.1:${BACKEND_PORT_VALUE}/actuator/health" || true
systemctl enable --now sahel-edu-backup.timer >/dev/null 2>&1 || warn "timer de sauvegarde non active"

# ------------------------------------------------------------------
# 7. Pare-feu
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
# 8. Recapitulatif
# ------------------------------------------------------------------
IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
printf '\n'
log "installation terminee"
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
printf 'Pour activer HTTPS : sudo ./deploy/https.sh ecole.example.com admin@example.com\n'
