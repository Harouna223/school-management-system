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
  /usr/local/sbin/sahel-edu-backup
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

log "reconstruction des images (les migrations Flyway s'appliquent au demarrage)"
compose build
compose up -d --remove-orphans

wait_healthy "http://127.0.0.1:$(env_get BACKEND_PORT 8080)/actuator/health" || true

printf '\n'
log "mise a jour terminee"
compose ps
printf '\nJournaux : journalctl -u sahel-edu -n 100\n'
