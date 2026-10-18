#!/usr/bin/env bash
# ============================================================
# Sahel Edu - fonctions partagees par les scripts de deploiement
# Sourcer ce fichier : . "$(dirname "$0")/common.sh"
# ============================================================

APP_DIR="${APP_DIR:-/opt/sahel-edu}"
DATA_DIR="${DATA_DIR:-/srv/sahel-edu}"
ENV_FILE="${ENV_FILE:-$APP_DIR/docker/.env}"
COMPOSE_FILE="$APP_DIR/docker/docker-compose.yml"

log()  { printf '\033[1;34m[sahel-edu]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[attention]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[erreur]\033[0m %s\n' "$*" >&2; exit 1; }

require_root() {
  [[ "${EUID}" -eq 0 ]] || die "A executer avec sudo (ou en root)."
}

require_ubuntu() {
  [[ -r /etc/os-release ]] || die "/etc/os-release introuvable : distribution non supportee."
  # shellcheck disable=SC1091
  . /etc/os-release
  case "${ID:-}" in
    ubuntu|debian) : ;;
    *) warn "distribution ${PRETTY_NAME:-inconnue} : le script est prevu pour Ubuntu 24.04." ;;
  esac
  [[ "${VERSION_ID:-}" == "24.04" ]] || warn "version ${VERSION_ID:-inconnue} detectee (cible : 24.04 LTS)."
}

compose() {
  if [[ ! -f "$COMPOSE_FILE" ]]; then
    die "docker-compose.yml introuvable dans $APP_DIR (installer d'abord : deploy/install.sh)."
  fi
  docker compose -f "$COMPOSE_FILE" "$@"
}

# Lit une cle du fichier .env SANS sourcer le fichier : les valeurs peuvent
# contenir des espaces (SCHOOL_NAME) ou des caracteres speciaux (JWT base64),
# qu'un `source` interpreterait comme du code shell.
#
# `tr -d '\r'` : un .env edite sous Windows (CRLF) donnerait sinon des valeurs
# terminees par un retour chariot, donc un mot de passe faux et un message
# trompeur (`Access denied`). Les guillemets eventuels sont retires.
env_get() {
  local key="$1" default="${2:-}" value
  [[ -f "$ENV_FILE" ]] || { printf '%s' "$default"; return 0; }
  value="$(sed -n "s/^[[:space:]]*${key}=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r')"
  value="${value%\"}"
  value="${value#\"}"
  printf '%s' "${value:-$default}"
}

env_set() {
  local key="$1" value="$2"
  [[ -f "$ENV_FILE" ]] || die "fichier .env introuvable : $ENV_FILE"
  if grep -qE "^[[:space:]]*${key}=" "$ENV_FILE"; then
    # Le separateur | evite de casser les valeurs contenant / (URL, chemins).
    sed -i "s|^[[:space:]]*${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

wait_healthy() {
  local url="${1:-http://127.0.0.1:8080/actuator/health}"
  local tries="${2:-60}"
  local i
  for ((i = 1; i <= tries; i++)); do
    if curl -fsS --max-time 5 "$url" 2>/dev/null | grep -q '"UP"'; then
      log "backend sain : $url"
      return 0
    fi
    sleep 5
  done
  warn "le backend ne repond pas encore sur $url (verifier : journalctl -u sahel-edu -n 100)"
  return 1
}

pick_free_port() {
  local port="${1:-3307}" last=$(( ${1:-3307} + 20 ))
  while [[ "$port" -le "$last" ]]; do
    if ! ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]${port}$"; then
      printf '%s' "$port"
      return 0
    fi
    port=$(( port + 1 ))
  done
  printf '%s' "${1:-3307}"
}
