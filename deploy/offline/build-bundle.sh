#!/usr/bin/env bash
# ============================================================
# Sahel Edu - fabrication du bundle de deploiement HORS LIGNE
#
#   ./deploy/offline/build-bundle.sh            # depuis le depot
#   VERSION=1.0.0 ./deploy/offline/build-bundle.sh
#   SKIP_DOCKER_DEBS=1 ./deploy/offline/build-bundle.sh
#
# A executer sur une machine AVEC Internet et Docker (Linux, WSL, macOS,
# ou Git Bash + Docker Desktop). Le resultat est un arbre autonome :
#
#   deploy/offline/dist/sahel-edu-offline-<version>/
#     deploy/offline/images/        backend + frontend + mysql (tar.gz)
#     deploy/offline/docker-debs/   Docker Engine pour Ubuntu 24.04 amd64
#     deploy/offline/manifest.txt
#     deploy/offline/SHA256SUMS
#     ...reste des sources de l'application
#
# Copier ce dossier sur le serveur puis :
#   sudo ./deploy/offline/install-offline.sh
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"
VERSION="${VERSION:-$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M)}"
DIST_DIR="$HERE/dist"
OUT="$DIST_DIR/sahel-edu-offline-$VERSION"
PLATFORM="linux/amd64"

log()  { printf '\033[1;34m[bundle]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[attention]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[erreur]\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || die "Docker est requis pour construire et exporter les images."
docker info >/dev/null 2>&1 || die "le demon docker ne repond pas."
command -v gzip >/dev/null 2>&1 || die "gzip est requis."

log "version : $VERSION"
log "sortie  : $OUT"

# ------------------------------------------------------------------
# 1. Copie des sources (sans artefacts ni secrets)
# ------------------------------------------------------------------
rm -rf "$OUT"
mkdir -p "$OUT"
log "copie des sources"
if command -v rsync >/dev/null 2>&1; then
  rsync -a \
    --exclude '.git' --exclude 'node_modules' --exclude 'target' --exclude 'dist' \
    --exclude 'docker/.env' --exclude 'uploads' \
    --exclude 'deploy/offline/dist' --exclude 'deploy/offline/images' \
    --exclude 'deploy/offline/docker-debs' \
    "$REPO_ROOT/" "$OUT/"
else
  # Git Bash n'embarque pas rsync : tar fait le meme travail avec les memes exclusions.
  tar -C "$REPO_ROOT" -cf - \
    --exclude='./.git' --exclude='./node_modules' --exclude='*/node_modules' \
    --exclude='*/target' --exclude='*/dist' --exclude='./dist' \
    --exclude='./docker/.env' --exclude='./uploads' \
    --exclude='./deploy/offline/dist' --exclude='./deploy/offline/images' \
    --exclude='./deploy/offline/docker-debs' \
    . | tar -C "$OUT" -xf -
fi
mkdir -p "$OUT/deploy/offline/images" "$OUT/deploy/offline/docker-debs"
# Un bundle copie depuis Windows peut perdre le bit executable : les scripts
# doivent rester lancables par `sudo ./deploy/...` sur le serveur.
chmod +x "$OUT"/deploy/*.sh "$OUT"/deploy/offline/*.sh 2>/dev/null || true

# ------------------------------------------------------------------
# 2. Images Docker (construites pour l'architecture cible amd64)
# ------------------------------------------------------------------
# Les registres publics sont parfois instables : trois tentatives, puis
# utilisation de l'image deja presente localement si elle existe.
pull_avec_reprise() {
  local image="$1" tentative
  for tentative in 1 2 3; do
    if docker pull --platform "$PLATFORM" "$image" >/dev/null 2>&1; then
      return 0
    fi
    warn "telechargement de $image echoue (tentative $tentative/3)"
  done
  docker image inspect "$image" >/dev/null 2>&1 || die "impossible de recuperer $image."
  warn "utilisation de l'image locale pour $image"
}

if docker image inspect mysql:8.4 >/dev/null 2>&1; then
  log "mysql:8.4 deja presente (pull ignore)"
else
  log "recuperation de mysql:8.4"
  pull_avec_reprise mysql:8.4
fi

# Construction avec reprise : les couches deja construites sont reutilisees,
# donc une relance apres une coupure reseau coute peu.
construire_image() {
  local image="$1" dockerfile="$2" context="${3:-$REPO_ROOT}" tentative
  for tentative in 1 2 3 4 5; do
    if docker build --platform "$PLATFORM" -f "$dockerfile" -t "$image" "$context"; then
      return 0
    fi
    warn "construction de $image echouee (tentative $tentative/5)"
  done
  die "construction de $image impossible (reseau indisponible ?)."
}

image_valide() {
  docker image inspect "$1" >/dev/null 2>&1 \
    && [[ "$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$1")" == "$PLATFORM" ]]
}

# --- Backend ---
if [[ "${REUSE_IMAGES:-0}" == "1" ]] && image_valide sahel-edu-backend:local; then
  log "image backend locale reutilisee (aucune reconstruction)"
elif [[ "${USE_LOCAL_JAR:-0}" == "1" ]]; then
  shopt -s nullglob
  jars=( "$REPO_ROOT"/backend-springboot/target/*.jar )
  shopt -u nullglob
  (( ${#jars[@]} > 0 )) || die "aucun jar dans backend-springboot/target : lancer 'mvn -f backend-springboot/pom.xml -DskipTests package'."
  JAR="${jars[0]}"
  CTX_JAR="$(mktemp -d)"
  cp "$JAR" "$CTX_JAR/app.jar"
  log "construction de sahel-edu-backend:local depuis $(basename "$JAR")"
  construire_image "sahel-edu-backend:local" "$REPO_ROOT/docker/Dockerfile.backend.jar" "$CTX_JAR"
  rm -rf "$CTX_JAR"
else
  log "construction de sahel-edu-backend:local"
  construire_image "sahel-edu-backend:local" "$REPO_ROOT/docker/Dockerfile.backend"
fi

# --- Frontend ---
if [[ "${REUSE_IMAGES:-0}" == "1" ]] && image_valide sahel-edu-frontend:local; then
  log "image frontend locale reutilisee (aucune reconstruction)"
else
  log "construction de sahel-edu-frontend:local"
  construire_image "sahel-edu-frontend:local" "$REPO_ROOT/docker/Dockerfile.frontend"
fi

exporter_image() {
  local image="$1" fichier="$2"
  log "export de $image -> $fichier"
  docker save "$image" | gzip -6 > "$OUT/deploy/offline/images/$fichier"
}

exporter_image "mysql:8.4"                "mysql-8.4.tar.gz"
exporter_image "sahel-edu-backend:local"  "sahel-edu-backend.tar.gz"
exporter_image "sahel-edu-frontend:local" "sahel-edu-frontend.tar.gz"

# ------------------------------------------------------------------
# 3. Docker Engine pour Ubuntu 24.04 (noble) amd64
# ------------------------------------------------------------------
if [[ "${SKIP_DOCKER_DEBS:-0}" == "1" ]]; then
  warn "SKIP_DOCKER_DEBS=1 : paquets Docker non telecharges (Docker devra etre present sur le serveur)."
else
  log "telechargement des paquets Docker Engine (noble/amd64)"
  pull_avec_reprise ubuntu:24.04
  # Git Bash convertit mal les chemins POSIX passes a `-v` : `--mount` avec un
  # chemin natif (C:/...) fonctionne sur Windows comme sur Linux/macOS.
  DEBS_HOST="$OUT/deploy/offline/docker-debs"
  if command -v cygpath >/dev/null 2>&1; then
    DEBS_HOST="$(cygpath -m "$DEBS_HOST")"
  fi
  docker run --rm --mount "type=bind,source=$DEBS_HOST,target=/out" ubuntu:24.04 bash -c '
    set -euo pipefail
    export DEBIAN_FRONTEND=noninteractive
    APT_OPTS="-o Acquire::Retries=5"
    apt-get $APT_OPTS update -qq
    apt-get $APT_OPTS install -y -qq ca-certificates curl >/dev/null
    install -m 0755 -d /etc/apt/keyrings
    curl --retry 5 --retry-delay 2 -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable" \
      > /etc/apt/sources.list.d/docker.list
    apt-get $APT_OPTS update -qq
    cd /out
    for p in docker-ce docker-ce-cli containerd.io docker-compose-plugin docker-buildx-plugin; do
      apt-get download "$p"
    done
    # Dependances systeme frequemment absentes dune installation minimale.
    # Deja presentes sur un Ubuntu Server standard : les telecharger ne coute
    # que quelques Mo et evite un blocage hors ligne.
    for p in iptables libip4tc2 libip6tc2 libxtables12 netbase libdevmapper1.02.1 \
             libseccomp2 conntrack libmnl0 libnfnetlink0 libnetfilter-conntrack3 libnftables1; do
      apt-get download "$p" 2>/dev/null || echo "[bundle] paquet optionnel absent : $p"
    done
  ' || die "telechargement des paquets Docker echoue (relancer, ou SKIP_DOCKER_DEBS=1)."
fi

# ------------------------------------------------------------------
# 4. Manifeste et sommes de controle
# ------------------------------------------------------------------
BACKEND_ID="$(docker image inspect --format '{{.Id}}' sahel-edu-backend:local)"
FRONTEND_ID="$(docker image inspect --format '{{.Id}}' sahel-edu-frontend:local)"
MYSQL_ID="$(docker image inspect --format '{{.Id}}' mysql:8.4)"

cat > "$OUT/deploy/offline/manifest.txt" <<EOF
application=Sahel Edu
version=$VERSION
construit_le=$(date -Is)
architecture=$PLATFORM
mysql=mysql:8.4 $MYSQL_ID
backend=sahel-edu-backend:local $BACKEND_ID
frontend=sahel-edu-frontend:local $FRONTEND_ID
EOF

log "calcul des sommes de controle"
(
  cd "$OUT/deploy/offline"
  shopt -s nullglob
  { sha256sum images/*.tar.gz docker-debs/*.deb manifest.txt; } > SHA256SUMS
)

printf '\n'
log "bundle pret : $OUT"
du -sh "$OUT" "$OUT/deploy/offline/images" 2>/dev/null || true
printf '\nCopier ce dossier sur le serveur, puis :\n'
printf '  sudo ./deploy/offline/install-offline.sh\n'
