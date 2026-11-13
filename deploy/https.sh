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
# Le script est IDEMPOTENT : relance-le sur une installation deja en HTTPS
# ne la degrade pas. C'est le point le plus important de ce fichier, voir le
# commentaire de l'etape 4.
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
CERT="/etc/letsencrypt/live/$DOMAIN/fullchain.pem"

# shellcheck source=common.sh
. "$HERE/common.sh"

require_root
[[ -n "$DOMAIN" && -n "$EMAIL" ]] || die "usage : sudo ./deploy/https.sh <domaine> <email> [--force]"
[[ "$DOMAIN" == *.* ]] || die "domaine invalide : $DOMAIN"
[[ -f "$APP_DIR/docker/.env" ]] || die "installation introuvable : lancez d'abord deploy/install.sh"

# ------------------------------------------------------------------
# 1. Verification DNS, en IPv4 ET en IPv6
#
# La version precedente comparait la premiere reponse de `getent hosts` a
# l'IPv4 publique. Sur un VPS dual-stack - c'est le cas chez LWS - `getent
# hosts` renvoie souvent l'AAAA en premier : le script comparait alors une
# adresse IPv6 a une IPv4 et s'arretait sur un faux diagnostic, alors que le
# domaine etait parfaitement configure.
# ------------------------------------------------------------------
PUBLIC_IP4="$(curl -4 -fsS --max-time 10 https://api.ipify.org 2>/dev/null || true)"
PUBLIC_IP6="$(curl -6 -fsS --max-time 10 https://api64.ipify.org 2>/dev/null || true)"

# ahostsv4/ahostsv6 viennent de la glibc, contrairement a `dig` (paquet
# dnsutils) qui n'est pas forcement installe sur un serveur minimal.
mapfile -t A_RECORDS < <(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk '{print $1}' | sort -u || true)
mapfile -t AAAA_RECORDS < <(getent ahostsv6 "$DOMAIN" 2>/dev/null | awk '$1 ~ /:/ {print $1}' | sort -u || true)

log "IPv4 publique du serveur : ${PUBLIC_IP4:-inconnue}"
log "IPv6 publique du serveur : ${PUBLIC_IP6:-aucune}"
log "enregistrements A de $DOMAIN    : ${A_RECORDS[*]:-aucun}"
log "enregistrements AAAA de $DOMAIN : ${AAAA_RECORDS[*]:-aucun}"

contains() {
  local needle="$1" item
  shift
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

DNS_OK=0
if [[ -n "$PUBLIC_IP4" ]] && contains "$PUBLIC_IP4" "${A_RECORDS[@]+"${A_RECORDS[@]}"}"; then
  DNS_OK=1
fi
if [[ -n "$PUBLIC_IP6" ]] && contains "$PUBLIC_IP6" "${AAAA_RECORDS[@]+"${AAAA_RECORDS[@]}"}"; then
  DNS_OK=1
fi

if [[ "$DNS_OK" -eq 1 ]]; then
  log "le domaine pointe bien vers ce serveur"
elif [[ -z "${A_RECORDS[*]:-}${AAAA_RECORDS[*]:-}" ]]; then
  if [[ "$FORCE" == "--force" ]]; then
    warn "$DOMAIN ne resout vers aucune adresse : poursuite forcee (Let's Encrypt echouera si le DNS est absent)."
  else
    die "$DOMAIN ne resout vers aucune adresse.
  Creez l'enregistrement DNS A (et AAAA si le serveur a une IPv6), attendez la
  propagation, ou relancez avec --force."
  fi
elif [[ "$FORCE" == "--force" ]]; then
  warn "$DOMAIN ne pointe pas vers ce serveur : poursuite forcee."
else
  die "$DOMAIN ne pointe vers aucune adresse de ce serveur.
  - A     attendu : ${PUBLIC_IP4:-<aucune IPv4 detectee>}  |  trouve : ${A_RECORDS[*]:-aucun}
  - AAAA  attendu : ${PUBLIC_IP6:-<aucune IPv6 detectee>}  |  trouve : ${AAAA_RECORDS[*]:-aucun}
  - si un routeur/NAT est devant : verifiez la redirection 80/443 ;
  - si un CDN/proxy est devant : relancez avec --force ;
  - en CGNAT, aucune redirection entrante n'est possible : voir docs/DEPLOIEMENT_UBUNTU.md."
fi

# ------------------------------------------------------------------
# 2. Paquets
# ------------------------------------------------------------------
export DEBIAN_FRONTEND=noninteractive
log "installation de nginx et certbot"
apt-get update -qq
apt-get install -y -qq nginx certbot python3-certbot-nginx >/dev/null
mkdir -p "$WEBROOT"

# Resolver utilise par nginx pour la validation OCSP (stapling). Il est lu sur
# le systeme et non code en dur : un VPS dont le DNS sortant est filtre (ou qui
# utilise systemd-resolved sur 127.0.0.53) n'a pas forcement acces a 1.1.1.1.
RESOLVER="$(awk '/^nameserver/ {print $2; exit}' /etc/resolv.conf 2>/dev/null || true)"
RESOLVER="${RESOLVER:-1.1.1.1}"
log "resolver DNS pour nginx : $RESOLVER"

# ------------------------------------------------------------------
# 3. Phase HTTP minimale, UNIQUEMENT si aucun certificat n'existe
#
# Reecrire le site en HTTP seul sur une installation deja en HTTPS la ferait
# basculer en clair. Si certbot echouait ensuite (DNS modifie, quota ACME
# atteint), le site restait servi en HTTP - et comme HSTS est deja en cache
# dans les navigateurs, ceux-ci refusaient de s'y connecter. Une simple
# relance du script suffisait donc a mettre la production hors service.
# ------------------------------------------------------------------
if [[ -f "$CERT" ]]; then
  log "certificat deja present : phase HTTP ignoree (aucune coupure de TLS)"
else
  log "configuration nginx (phase HTTP, necessaire a la validation ACME)"
  sed "s|__DOMAIN__|$DOMAIN|g" "$HERE/nginx/sahel-edu-http.conf.template" > "$NGINX_SITE"
  ln -sf "$NGINX_SITE" /etc/nginx/sites-enabled/sahel-edu.conf
  rm -f /etc/nginx/sites-enabled/default
  nginx -t
  systemctl enable --now nginx >/dev/null 2>&1
  systemctl reload nginx
fi

# ------------------------------------------------------------------
# 4. Certificat
#
# --keep-until-expiring rend l'appel sans effet si le certificat est encore
# valide : le script peut donc etre relance sans consommer de quota ACME ni
# toucher au certificat en service.
# ------------------------------------------------------------------
log "demande du certificat Let's Encrypt pour $DOMAIN"
certbot certonly --webroot -w "$WEBROOT" -d "$DOMAIN" \
  --email "$EMAIL" --agree-tos --non-interactive --keep-until-expiring

[[ -f "$CERT" ]] || die "certificat introuvable dans /etc/letsencrypt/live/$DOMAIN"

EXPIRY="$(openssl x509 -enddate -noout -in "$CERT" | cut -d= -f2)"
EXP_EPOCH="$(date -d "$EXPIRY" +%s)"
DAYS_LEFT=$(( (EXP_EPOCH - $(date +%s)) / 86400 ))
log "certificat valide encore $DAYS_LEFT jours (expire le $EXPIRY)"
if [[ "$DAYS_LEFT" -lt 14 ]]; then
  warn "moins de 14 jours : verifiez le renouvellement (certbot renew --dry-run)."
fi

# ------------------------------------------------------------------
# 5. Configuration HTTPS complete
# ------------------------------------------------------------------
log "configuration nginx (phase HTTPS)"
sed -e "s|__DOMAIN__|$DOMAIN|g" -e "s|__RESOLVER__|$RESOLVER|g" \
  "$HERE/nginx/sahel-edu.conf.template" > "$NGINX_SITE"
ln -sf "$NGINX_SITE" /etc/nginx/sites-enabled/sahel-edu.conf
rm -f /etc/nginx/sites-enabled/default
# Valide AVANT de recharger : une erreur ici laisserait le site injoignable.
nginx -t

# ------------------------------------------------------------------
# 6. Bascule du conteneur derriere nginx (127.0.0.1:8081)
#
# La pile appartient a systemd : `sahel-edu.service` fait tourner un
# `docker compose up` au premier plan. Lancer un `compose up -d` ici mettait
# deux processus en concurrence sur les memes conteneurs, et le service
# pouvait finir « dead » alors que les conteneurs tournaient - un redemarrage
# ulterieur aurait alors tout arrete sans prevenir. C'est exactement le defaut
# corrige dans update.sh ; `systemctl reload` execute l'ExecReload de l'unite,
# qui fait le `compose up -d` attendu en gardant systemd aux commandes.
# ------------------------------------------------------------------
log "bascule du frontend sur 127.0.0.1:8081"
env_set FRONTEND_BIND 127.0.0.1
env_set FRONTEND_PORT 8081
env_set CORS_ORIGINS "https://$DOMAIN"
systemctl reload sahel-edu.service

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

CODE_HTTP="$(curl -s -o /dev/null -w '%{http_code}' --resolve "$DOMAIN:80:127.0.0.1" "http://$DOMAIN/" || true)"
if [[ "$CODE_HTTP" == "301" ]]; then
  log "redirection HTTP -> HTTPS : HTTP 301"
else
  warn "redirection HTTP inattendue (HTTP ${CODE_HTTP:-aucune}). Attendu : 301."
fi

CODE="$(curl -s -o /dev/null -w '%{http_code}' --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/" || true)"
if [[ "$CODE" == "200" ]]; then
  log "HTTPS operationnel : https://$DOMAIN (HTTP $CODE)"
else
  warn "reponse inattendue en HTTPS (HTTP ${CODE:-aucune}). Verifiez : nginx -t && journalctl -u nginx -n 50"
fi

HSTS="$(curl -sI --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/" | tr -d '\r' | awk -F': ' 'tolower($1)=="strict-transport-security" {print $2}' || true)"
if [[ -n "$HSTS" ]]; then
  log "HSTS actif : $HSTS"
else
  warn "en-tete Strict-Transport-Security absent."
fi

# Le frontend ne doit plus etre joignable en direct : c'est ce qui garantit
# qu'aucune requete ne contourne le TLS.
if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE ':8081$'; then
  log "frontend en ecoute sur 127.0.0.1:8081 (TLS obligatoire)"
else
  warn "aucune ecoute sur 8081 : verifiez 'systemctl status sahel-edu' et 'docker compose ps'."
fi

printf '\n'
log "termine"
printf '  site      : https://%s\n' "$DOMAIN"
printf '  renouvellement : systemctl list-timers certbot.timer\n'
printf '  test a blanc   : certbot renew --dry-run\n'
printf '  expiration     : echo | openssl s_client -connect %s:443 2>/dev/null | openssl x509 -noout -dates\n' "$DOMAIN"
