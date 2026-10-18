#!/usr/bin/env bash
# ============================================================
# Sahel Edu - activation de HTTPS (Let's Encrypt) sur Ubuntu
#
#   sudo ./deploy/https.sh ecole.example.com admin@example.com
#   sudo ./deploy/https.sh ecole.example.com admin@example.com --force
#
# Topologie mise en place :
#   Internet -> nginx hote (80/443, TLS) -> conteneur frontend (127.0.0.1:8081)
#   Le conteneur frontend continue de proxifier /api vers le backend.
#
# --force : ignore la verification DNS (utile derriere un proxy/CDN ou si
# l'API de detection d'IP publique est injoignable).
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-/opt/sahel-edu}"
DOMAIN="${1:-}"
EMAIL="${2:-}"
FORCE="${3:-}"
NGINX_SITE="/etc/nginx/sites-available/sahel-edu.conf"
WEBROOT="/var/www/certbot"

# shellcheck source=common.sh
. "$HERE/common.sh"

require_root
[[ -n "$DOMAIN" && -n "$EMAIL" ]] || die "usage : sudo ./deploy/https.sh <domaine> <email> [--force]"
[[ "$DOMAIN" == *.* ]] || die "domaine invalide : $DOMAIN"
[[ -f "$APP_DIR/docker/.env" ]] || die "installation introuvable : lancez d'abord deploy/install.sh"

# ------------------------------------------------------------------
# 1. Verification DNS
# ------------------------------------------------------------------
PUBLIC_IP="$(curl -fsS --max-time 10 https://api.ipify.org 2>/dev/null || true)"
RESOLVED_IP="$(getent hosts "$DOMAIN" 2>/dev/null | awk '{print $1}' | head -n 1 || true)"

log "IP publique detectee : ${PUBLIC_IP:-inconnue}"
log "IP resolue pour $DOMAIN : ${RESOLVED_IP:-aucune}"

if [[ -z "$RESOLVED_IP" ]]; then
  if [[ "$FORCE" == "--force" ]]; then
    warn "$DOMAIN ne resout pas encore : poursuite forcee (Let's Encrypt echouera si le DNS est absent)."
  else
    die "$DOMAIN ne resout vers aucune adresse. Creez l'enregistrement DNS A/AAAA, attendez la propagation, ou relancez avec --force."
  fi
elif [[ -n "$PUBLIC_IP" && "$RESOLVED_IP" != "$PUBLIC_IP" ]]; then
  if [[ "$FORCE" == "--force" ]]; then
    warn "$DOMAIN resout vers $RESOLVED_IP et non vers $PUBLIC_IP : poursuite forcee."
  else
    die "$DOMAIN resout vers $RESOLVED_IP alors que ce serveur a pour IP publique $PUBLIC_IP.
  - si un routeur/NAT est devant : verifiez la redirection 80/443 ;
  - si un CDN/proxy est devant : relancez avec --force ;
  - en CGNAT, aucune redirection entrante n'est possible : voir docs/DEPLOIEMENT_UBUNTU.md."
  fi
fi

# ------------------------------------------------------------------
# 2. Paquets
# ------------------------------------------------------------------
export DEBIAN_FRONTEND=noninteractive
log "installation de nginx et certbot"
apt-get update -qq
apt-get install -y -qq nginx certbot python3-certbot-nginx >/dev/null
mkdir -p "$WEBROOT"

# ------------------------------------------------------------------
# 3. Configuration HTTP minimale (necessaire a la validation ACME)
# ------------------------------------------------------------------
log "configuration nginx (phase HTTP)"
sed "s|__DOMAIN__|$DOMAIN|g" "$HERE/nginx/sahel-edu-http.conf.template" > "$NGINX_SITE"
ln -sf "$NGINX_SITE" /etc/nginx/sites-enabled/sahel-edu.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx >/dev/null 2>&1
systemctl reload nginx

# ------------------------------------------------------------------
# 4. Certificat
# ------------------------------------------------------------------
log "demande du certificat Let's Encrypt pour $DOMAIN"
certbot certonly --webroot -w "$WEBROOT" -d "$DOMAIN" \
  --email "$EMAIL" --agree-tos --non-interactive --keep-until-expiring

[[ -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ]] \
  || die "certificat introuvable dans /etc/letsencrypt/live/$DOMAIN"

# ------------------------------------------------------------------
# 5. Configuration HTTPS complete
# ------------------------------------------------------------------
log "configuration nginx (phase HTTPS)"
sed "s|__DOMAIN__|$DOMAIN|g" "$HERE/nginx/sahel-edu.conf.template" > "$NGINX_SITE"
nginx -t

# ------------------------------------------------------------------
# 6. Bascule du conteneur derriere nginx (127.0.0.1:8081)
# ------------------------------------------------------------------
log "bascule du frontend sur 127.0.0.1:8081"
env_set FRONTEND_BIND 127.0.0.1
env_set FRONTEND_PORT 8081
env_set CORS_ORIGINS "https://$DOMAIN"
compose up -d frontend

systemctl reload nginx

# ------------------------------------------------------------------
# 7. Renouvellement automatique
# ------------------------------------------------------------------
HOOK="/etc/letsencrypt/renewal-hooks/deploy/10-reload-nginx.sh"
mkdir -p "$(dirname "$HOOK")"
printf '#!/bin/sh\nsystemctl reload nginx\n' > "$HOOK"
chmod 0755 "$HOOK"
systemctl enable --now certbot.timer >/dev/null 2>&1 || warn "certbot.timer non active : verifiez le renouvellement."

# ------------------------------------------------------------------
# 8. Verification
# ------------------------------------------------------------------
sleep 3
CODE="$(curl -s -o /dev/null -w '%{http_code}' --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/" || true)"
if [[ "$CODE" == "200" ]]; then
  log "HTTPS operationnel : https://$DOMAIN (HTTP $CODE)"
else
  warn "reponse inattendue en HTTPS (HTTP ${CODE:-aucune}). Verifiez : nginx -t && journalctl -u nginx -n 50"
fi

printf '\n'
log "termine"
printf '  site      : https://%s\n' "$DOMAIN"
printf '  renouvellement : systemctl list-timers certbot.timer\n'
printf '  test a blanc   : certbot renew --dry-run\n'
