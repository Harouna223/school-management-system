#!/usr/bin/env bash
# ============================================================
# Sahel Edu - controle de sante du serveur
#
#   sudo ./deploy/healthcheck.sh            rapport complet
#   sudo ./deploy/healthcheck.sh --quiet    ne parle qu'en cas de probleme
#
# Installe sous /usr/local/sbin/sahel-edu-healthcheck et declenche par
# sahel-edu-check.timer toutes les 15 minutes. Une anomalie laisse le service
# en echec, donc visible dans `systemctl --failed` sans que personne n'ait a
# lire les journaux.
#
# POURQUOI CE SCRIPT
# Un serveur d'ecole n'est surveille par personne. Les pannes qui comptent ici
# ne sont pas des exceptions applicatives : ce sont des etats silencieux —
# disque plein, sauvegarde arretee depuis trois semaines, certificat expire,
# conteneur qui redemarre en boucle, base injoignable. Aucun ne produit
# d'erreur visible avant la panne complete, et tous se detectent en une
# commande.
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

QUIET=0
WEBHOOK="${ALERT_WEBHOOK:-}"
MONITOR_ENV_FILE="${MONITOR_ENV_FILE:-/etc/sahel-edu/monitor.env}"
# Seuils, surchargeables par variable d'environnement (utile sur un petit VPS
# dont le disque est volontairement rempli, ou pour un test).
SEUIL_DISQUE_PCT="${SEUIL_DISQUE_PCT:-15}"          # espace libre minimal, en %
AGE_SAUVEGARDE_MAX_H="${AGE_SAUVEGARDE_MAX_H:-48}"  # une sauvegarde quotidienne
SEUIL_CERT_JOURS="${SEUIL_CERT_JOURS:-14}"          # certbot renouvelle a 30 j

ALERTES=()

usage() {
  cat <<'EOF'
Controle de sante du serveur Sahel Edu.

  sudo ./deploy/healthcheck.sh              rapport complet
  sudo ./deploy/healthcheck.sh --quiet      ne parle qu'en cas de probleme

Options :
  --quiet, -q          n'affiche que les anomalies
  --webhook <url>      envoie les anomalies a cette URL (JSON {"text": ...})
  -h, --help           affiche cette aide

Code de sortie : 0 si tout est au vert, 1 des qu'une anomalie est detectee.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --quiet|-q) QUIET=1; shift ;;
    --webhook)  [[ $# -ge 2 ]] || die "--webhook attend une URL"; WEBHOOK="$2"; shift 2 ;;
    -h|--help)  usage; exit 0 ;;
    *)          usage; die "option inconnue : $1" ;;
  esac
done

require_root

ok()      { [[ "$QUIET" -eq 1 ]] || printf '  \033[1;32mOK\033[0m       %s\n' "$*"; }
anomalie() {
  printf '  \033[1;31mANOMALIE\033[0m %s\n' "$*" >&2
  ALERTES+=("$*")
}
titre()   { [[ "$QUIET" -eq 1 ]] || printf '\n\033[1m%s\033[0m\n' "$*"; }

domaine_public() {
  local valeur
  valeur="$(env_get CORS_ORIGINS)"
  valeur="${valeur%%,*}"
  valeur="${valeur#http://}"
  valeur="${valeur#https://}"
  valeur="${valeur%%/*}"
  printf '%s' "$valeur"
}

en_https() {
  [[ "$(env_get CORS_ORIGINS)" == https://* ]]
}

# ------------------------------------------------------------------
titre "Service"
if systemctl is-active --quiet sahel-edu.service 2>/dev/null; then
  ok "sahel-edu.service actif"
else
  anomalie "sahel-edu.service n'est pas actif (systemctl status sahel-edu)"
fi

# ------------------------------------------------------------------
titre "Conteneurs"
for service in mysql backend frontend; do
  # --all : un conteneur arrete doit etre signale comme arrete, pas comme
  # absent. Sans ce drapeau, `ps -q` ne liste que les conteneurs en marche et
  # le diagnostic envoyait chercher un conteneur qui existait tres bien.
  id="$(compose ps -q --all "$service" 2>/dev/null || true)"
  if [[ -z "$id" ]]; then
    anomalie "$service : conteneur absent"
    continue
  fi
  etat="$(docker inspect -f '{{.State.Status}}' "$id" 2>/dev/null || echo inconnu)"
  sante="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}-{{end}}' "$id" 2>/dev/null || echo inconnu)"
  if [[ "$etat" != "running" ]]; then
    anomalie "$service : etat $etat"
  elif [[ "$sante" == "unhealthy" ]]; then
    anomalie "$service : unhealthy (docker logs $(compose ps --format '{{.Name}}' "$service"))"
  else
    ok "$service : $etat / $sante"
  fi
done

# ------------------------------------------------------------------
titre "Reponses HTTP"
port_backend="$(env_get BACKEND_PORT 8080)"
# La reponse est capturee avant d'etre inspectee : `curl ... | grep -q` ferme le
# tube a la premiere correspondance, curl meurt en SIGPIPE (23) et `pipefail`
# transformerait un backend sain en anomalie.
reponse_backend="$(curl -fsS --max-time 10 "http://127.0.0.1:$port_backend/actuator/health" 2>/dev/null || true)"
if [[ "$reponse_backend" == *'"status":"UP"'* ]]; then
  ok "backend : UP sur 127.0.0.1:$port_backend"
else
  anomalie "backend : pas de reponse UP sur 127.0.0.1:$port_backend"
fi

port_frontend="$(env_get FRONTEND_PORT 80)"
# Pas de `|| echo 000` : `-w '%{http_code}'` ecrit deja 000 quand la connexion
# echoue, et le ajout produisait « 000000 » dans le rapport.
code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://127.0.0.1:$port_frontend/" 2>/dev/null || true)"
code="${code:-000}"
if [[ "$code" == "200" || "$code" == "301" || "$code" == "302" ]]; then
  ok "frontend : HTTP $code sur 127.0.0.1:$port_frontend"
else
  anomalie "frontend : HTTP $code sur 127.0.0.1:$port_frontend (attendu 200 ou 301)"
fi

# Le controle public est le seul qui prouve que la chaine complete fonctionne :
# DNS, nginx hote, TLS, proxy, conteneur. Il est saute si l'installation est
# encore en HTTP direct, pour ne pas signaler une anomalie permanente.
if en_https; then
  domaine="$(domaine_public)"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "https://$domaine/" 2>/dev/null || true)"
  code="${code:-000}"
  if [[ "$code" == "200" ]]; then
    ok "site public : HTTPS 200 sur $domaine"
  else
    anomalie "site public : HTTPS $code sur $domaine"
  fi

  certificat="/etc/letsencrypt/live/$domaine/fullchain.pem"
  if [[ ! -f "$certificat" ]]; then
    anomalie "certificat introuvable : $certificat"
  elif openssl x509 -checkend "$((SEUIL_CERT_JOURS * 86400))" -noout -in "$certificat" 2>/dev/null; then
    ok "certificat valide plus de $SEUIL_CERT_JOURS jours"
  else
    anomalie "certificat expire dans moins de $SEUIL_CERT_JOURS jours (certbot renew)"
  fi
fi

# ------------------------------------------------------------------
titre "Disque"
for chemin in "$DATA_DIR" /; do
  [[ -d "$chemin" ]] || continue
  libre="$(df -P "$chemin" 2>/dev/null | awk 'NR==2 {gsub("%", "", $5); print $5}')"
  if [[ -z "${libre:-}" ]]; then
    anomalie "$chemin : espace libre illisible"
  elif [[ "$libre" -lt "$SEUIL_DISQUE_PCT" ]]; then
    anomalie "$chemin : seulement $libre% d'espace libre (seuil $SEUIL_DISQUE_PCT%)"
  else
    ok "$chemin : $libre% d'espace libre"
  fi
done

# ------------------------------------------------------------------
titre "Sauvegardes"
DB_DIR="$DATA_DIR/backups/db"
if [[ ! -d "$DB_DIR" ]]; then
  anomalie "repertoire de sauvegardes absent : $DB_DIR"
else
  # On interroge l'age du fichier le plus recent, pas l'existence d'un fichier :
  # une sauvegarde arretee depuis trois semaines laisse des archives en place et
  # ne declenche aucun autre signal.
  recente="$(find "$DB_DIR" -maxdepth 1 -name 'db-*.sql.gz' -type f \
    -newermt "-${AGE_SAUVEGARDE_MAX_H} hours" -print -quit 2>/dev/null || true)"
  if [[ -n "$recente" ]]; then
    ok "sauvegarde de moins de $AGE_SAUVEGARDE_MAX_H h : $(basename "$recente")"
  else
    anomalie "aucune sauvegarde de base depuis plus de $AGE_SAUVEGARDE_MAX_H h (systemctl status sahel-edu-backup)"
  fi
fi

# La copie hors site est la seule qui protege d'une panne du disque : son
# absence est une anomalie, pas un detail.
OFFSITE="${OFFSITE_DIR:-}"
if [[ -z "$OFFSITE" && -f "$BACKUP_ENV_FILE" ]]; then
  OFFSITE="$(sed -n 's/^[[:space:]]*OFFSITE_DIR=//p' "$BACKUP_ENV_FILE" | tail -n 1 | tr -d '\r')"
fi
if [[ -z "$OFFSITE" ]]; then
  anomalie "OFFSITE_DIR non defini : la sauvegarde ne quitte pas le disque des donnees"
elif [[ ! -d "$OFFSITE" ]]; then
  anomalie "copie hors site inaccessible : $OFFSITE (disque non monte ?)"
else
  recente="$(find "$OFFSITE" -maxdepth 1 -name 'db-*.sql.gz' -type f \
    -newermt "-${AGE_SAUVEGARDE_MAX_H} hours" -print -quit 2>/dev/null || true)"
  if [[ -n "$recente" ]]; then
    ok "copie hors site a jour : $(basename "$recente")"
  else
    anomalie "aucune copie hors site recente dans $OFFSITE"
  fi
fi

# ------------------------------------------------------------------
titre "Base de donnees"
# `mysqladmin ping` sort en 0 meme sur « Access denied » : il prouve que le
# serveur repond, ce qui est exactement ce qu'on veut verifier ici. Interroger
# les donnees demanderait le mot de passe root pour un resultat equivalent.
if compose exec -T mysql mysqladmin ping -h 127.0.0.1 >/dev/null 2>&1; then
  ok "MySQL repond"
else
  anomalie "MySQL ne repond pas"
fi

# ------------------------------------------------------------------
titre "Unites systemd en echec"
# Cette unite est exclue : elle est en echec precisement quand une anomalie
# existe, donc elle se signalerait elle-meme a chaque passage et masquerait
# les vraies causes.
echouees="$(systemctl --failed --no-legend --no-pager 2>/dev/null \
  | grep -v 'sahel-edu-check\.service' || true)"
if [[ -z "$echouees" ]]; then
  ok "aucune unite en echec"
else
  anomalie "unites en echec : $(printf '%s' "$echouees" | awk '{print $1}' | tr '\n' ' ')"
fi

# ------------------------------------------------------------------
if [[ "${#ALERTES[@]}" -eq 0 ]]; then
  [[ "$QUIET" -eq 1 ]] || printf '\n'
  [[ "$QUIET" -eq 1 ]] || log "tous les controles sont au vert"
  exit 0
fi

printf '\n'
log "${#ALERTES[@]} anomalie(s) detectee(s)"

# Alerte sortante facultative : sans elle, le seul signal est `systemctl
# --failed`, que personne ne consulte sur un serveur d'ecole. La charge utile
# est au format Slack/Mattermost/Teams, qui accepte un simple champ « text ».
if [[ -n "$WEBHOOK" ]]; then
  charge="$(printf '%s' "${ALERTES[*]}" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
  if curl -fsS --max-time 10 -X POST -H 'Content-Type: application/json' \
       -d "{\"text\": \"Sahel Edu ($(hostname)) : ${charge}\"}" "$WEBHOOK" >/dev/null 2>&1; then
    log "alerte envoyee"
  else
    warn "envoi de l'alerte impossible (verifier ALERT_WEBHOOK dans $MONITOR_ENV_FILE)"
  fi
fi

exit 1
