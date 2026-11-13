#!/usr/bin/env bash
# ============================================================
# Sahel Edu - durcissement du serveur Ubuntu 24.04 (idempotent)
#
#   sudo ./deploy/harden-server.sh
#   sudo ./deploy/harden-server.sh --ssh
#   sudo ./deploy/harden-server.sh --swap 4096 --timezone Africa/Bamako
#   sudo ./deploy/harden-server.sh --hostname sahel-edu.ecole.example.com
#
# Le script est IDEMPOTENT : relancez-le autant de fois que necessaire, il ne
# modifie que ce qui diverge de l'etat attendu. install.sh l'appelle sans
# --ssh, afin qu'une installation soit complete du premier coup.
#
# Ce qu'il ne fait PAS, volontairement :
#
#   - SSH n'est touche que si --ssh est passe. Une erreur de configuration SSH
#     coupe l'acces au serveur : ce doit rester un geste explicite, jamais un
#     effet de bord de l'installation.
#   - La machine n'est jamais redemarree. Un noyau en retard est moins grave
#     qu'une coupure de production non planifiee ; le besoin est signale.
#   - IPv6 n'est pas desactive. Le VPS LWS en fournit, et le couper casserait
#     la resolution AAAA du domaine ainsi que les enregistrements Let's Encrypt.
# ============================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=common.sh
. "$HERE/common.sh"

require_root
require_ubuntu

DO_SSH=0
TIMEZONE="${TIMEZONE:-Africa/Bamako}"
SWAP_MB="${SWAP_MB:-2048}"
NEW_HOSTNAME="${HOSTNAME_FQDN:-}"
# Adresses a ne jamais bannir (le poste d'administration, par exemple).
TRUSTED_IPS="${TRUSTED_IPS:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ssh)        DO_SSH=1 ;;
    --timezone)   TIMEZONE="${2:?valeur attendue apres --timezone}"; shift ;;
    --swap)       SWAP_MB="${2:?valeur attendue apres --swap}"; shift ;;
    --hostname)   NEW_HOSTNAME="${2:?valeur attendue apres --hostname}"; shift ;;
    --trusted-ip) TRUSTED_IPS="${TRUSTED_IPS:+$TRUSTED_IPS }${2:?valeur attendue apres --trusted-ip}"; shift ;;
    -h|--help)
      sed -n '3,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) die "option inconnue : $1 (voir --help)" ;;
  esac
  shift
done

[[ "$SWAP_MB" =~ ^[0-9]+$ ]] || die "--swap attend un nombre de Mo (ex. 2048)."

CHANGED=0

# Ecrit un fichier seulement si son contenu differe (contenu lu sur l'entree
# standard). Sans cette comparaison, chaque execution reecrirait les fichiers
# de configuration et declencherait un redemarrage de service a chaque passage.
#
# Le contenu vient d'un heredoc et non d'une chaine entre apostrophes : les
# commentaires sont en francais et contiennent des apostrophes, qui fermeraient
# la chaine et produiraient une erreur de syntaxe.
write_if_changed() {
  local target="$1" mode="${2:-0644}" tmp
  tmp="$(mktemp)"
  cat > "$tmp"
  if [[ -f "$target" ]] && cmp -s "$tmp" "$target"; then
    rm -f "$tmp"
    log "  deja conforme : $target"
    return 0
  fi
  chmod "$mode" "$tmp"
  mv -f "$tmp" "$target"
  CHANGED=1
  log "  ecrit : $target"
}

export DEBIAN_FRONTEND=noninteractive

# ==================================================================
# 1. Horloge
#
# Un serveur dont l'horloge derive invalide les jetons JWT (exp/iat), fait
# echouer la validation des certificats TLS et rend les journaux inexploitables
# pour correler un incident. C'est la premiere chose a remettre d'aplomb.
# ==================================================================
log "horloge : fuseau $TIMEZONE et synchronisation NTP"

if ! timedatectl list-timezones 2>/dev/null | grep -qx "$TIMEZONE"; then
  die "fuseau inconnu : $TIMEZONE (liste : timedatectl list-timezones)"
fi
if [[ "$(timedatectl show -p Timezone --value 2>/dev/null || true)" != "$TIMEZONE" ]]; then
  timedatectl set-timezone "$TIMEZONE"
  log "  fuseau applique : $TIMEZONE"
else
  log "  deja sur $TIMEZONE"
fi

# chrony et systemd-timesyncd se disputent le reglage de l'heure : on n'active
# le second que si le premier est absent.
if command -v chronyc >/dev/null 2>&1; then
  systemctl enable --now chrony >/dev/null 2>&1 || warn "chrony n'a pas demarre"
else
  systemctl enable --now systemd-timesyncd >/dev/null 2>&1 || warn "systemd-timesyncd n'a pas demarre"
fi
timedatectl set-ntp true >/dev/null 2>&1 || true

if [[ -n "$NEW_HOSTNAME" ]]; then
  log "nom d'hote : $NEW_HOSTNAME"
  if [[ "$(hostname)" != "$NEW_HOSTNAME" ]]; then
    # hostnamectl ecrit /etc/hostname ; /etc/hosts doit suivre, sinon `sudo`
    # ralentit (resolution du nom local) et certains services se plaignent.
    hostnamectl set-hostname "$NEW_HOSTNAME"
    if ! grep -qE "[[:space:]]${NEW_HOSTNAME}([[:space:]]|$)" /etc/hosts; then
      printf '127.0.1.1 %s\n' "$NEW_HOSTNAME" >> /etc/hosts
    fi
    log "  applique (reconnexion conseillee pour voir le prompt a jour)"
  else
    log "  deja positionne"
  fi
fi

# ==================================================================
# 2. Memoire d'echange
#
# Sans swap, un pic de memoire (compilation Maven, import Excel volumineux,
# migration Flyway) fait tuer un processus par l'OOM killer : le symptome
# visible est un conteneur qui redemarre tout seul, sans erreur applicative.
# ==================================================================
log "memoire d'echange"

SWAP_ACTIF="$(free -m | awk '/^Swap:/{print $2}')"
if [[ "${SWAP_ACTIF:-0}" -gt 0 ]]; then
  log "  deja active (${SWAP_ACTIF} Mo)"
else
  # 2 Go suffisent pour une machine a 2-4 Go ; au-dela, le disque du VPS est
  # souvent le premier facteur limitant.
  log "  creation de /swapfile (${SWAP_MB} Mo)"
  if ! fallocate -l "${SWAP_MB}M" /swapfile 2>/dev/null; then
    # Certains systemes de fichiers refusent fallocate : le fichier serait
    # cree creux et swapon le rejetterait.
    dd if=/dev/zero of=/swapfile bs=1M count="$SWAP_MB" status=none
  fi
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  if ! swapon /swapfile 2>/dev/null; then
    warn "swapon a refuse le fichier : nouvelle tentative sans fallocate"
    rm -f /swapfile
    dd if=/dev/zero of=/swapfile bs=1M count="$SWAP_MB" status=none
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
    swapon /swapfile || die "impossible d'activer /swapfile"
  fi
  if ! grep -qE '^[[:space:]]*/swapfile[[:space:]]' /etc/fstab; then
    printf '/swapfile none swap sw 0 0\n' >> /etc/fstab
    log "  entree ajoutee a /etc/fstab (persistant au redemarrage)"
  fi
  CHANGED=1
fi

# ==================================================================
# 3. Parametres noyau et reseau
#
# Objectif : limiter la surface d'attaque reseau et empecher un processus
# compromis de lire la memoire du noyau (kptr_restrict, dmesg_restrict).
#
# ATTENTION rp_filter : la valeur stricte (1) peut couper la connectivite des
# conteneurs sur certaines configurations a routage asymetrique. Si un
# conteneur perd l'acces reseau apres ce script, passer a 2 (mode lache).
# ==================================================================
log "parametres noyau"

write_if_changed /etc/sysctl.d/99-sahel-edu.conf 0644 <<'SYSCTL'
# Genere par deploy/harden-server.sh - ne pas editer a la main.
# Reappliquer : sudo sysctl --system

# Memoire : n'utiliser le swap qu'en dernier recours.
vm.swappiness = 10
vm.vfs_cache_pressure = 50

# Reseau : anti-usurpation et anti-inondation SYN.
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
net.ipv4.tcp_syncookies = 1
net.ipv4.tcp_max_syn_backlog = 2048
net.ipv4.conf.all.accept_redirects = 0
net.ipv4.conf.default.accept_redirects = 0
net.ipv4.conf.all.send_redirects = 0
net.ipv4.conf.all.accept_source_route = 0
net.ipv4.conf.default.accept_source_route = 0
net.ipv4.icmp_echo_ignore_broadcasts = 1
net.ipv4.icmp_ignore_bogus_error_responses = 1

net.ipv6.conf.all.accept_redirects = 0
net.ipv6.conf.default.accept_redirects = 0
net.ipv6.conf.all.accept_source_route = 0

# Noyau : un processus non privilegie ne doit pas pouvoir lire les adresses du
# noyau (base d'une exploitation) ni vider le tampon dmesg.
kernel.dmesg_restrict = 1
kernel.kptr_restrict = 2
kernel.sysrq = 0
fs.protected_hardlinks = 1
fs.protected_symlinks = 1
SYSCTL
sysctl --system >/dev/null 2>&1 || warn "sysctl --system a signale une erreur (voir : sysctl --system)"

# ==================================================================
# 4. Mises a jour de securite automatiques
#
# Un serveur expose sur Internet qui accumule les correctifs devient une cible
# facile. On installe les correctifs de securite seuls, sans redemarrage
# automatique : c'est a l'administrateur de choisir la fenetre.
# ==================================================================
log "mises a jour de securite automatiques"

if ! dpkg -s unattended-upgrades >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y -qq unattended-upgrades apt-listchanges >/dev/null
  CHANGED=1
  log "  paquet unattended-upgrades installe"
fi

write_if_changed /etc/apt/apt.conf.d/20auto-upgrades 0644 <<'AUTOUPGRADE'
// Genere par deploy/harden-server.sh - ne pas editer a la main.
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
AUTOUPGRADE

# Les origines de securite sont deja activees par le paquet sur Ubuntu 24.04 ;
# on le verifie plutot que de reecrire un fichier de 100 lignes.
if ! grep -q 'distro_codename}-security' /etc/apt/apt.conf.d/50unattended-upgrades 2>/dev/null; then
  warn "les mises a jour de securite semblent desactivees dans 50unattended-upgrades."
  warn "  verifiez la ligne : \"\${distro_id}:\${distro_codename}-security\";"
fi

systemctl enable --now unattended-upgrades >/dev/null 2>&1 || warn "unattended-upgrades n'a pas demarre"

# ==================================================================
# 5. fail2ban
#
# UFW ferme les ports ; il n'empeche pas mille tentatives de connexion SSH sur
# le port 22 ouvert. fail2ban lit les journaux et bannit l'adresse source.
#
# backend = systemd : Ubuntu 24.04 n'ecrit plus forcement /var/log/auth.log
# (rsyslog n'est plus installe par defaut), le filtre fichier ne verrait rien
# et le bannissement resterait silencieusement inoperant.
# ==================================================================
log "fail2ban"

if ! dpkg -s fail2ban >/dev/null 2>&1; then
  apt-get update -qq
  # python3-systemd fournit le backend systemd utilise ci-dessous.
  apt-get install -y -qq fail2ban python3-systemd >/dev/null
  CHANGED=1
  log "  paquet fail2ban installe"
fi

JAIL_IGNORE="127.0.0.1/8 ::1"
for ip in $TRUSTED_IPS; do
  JAIL_IGNORE="$JAIL_IGNORE $ip"
done

# Heredoc non protege : $JAIL_IGNORE doit etre developpe ici. Le contenu ne
# contient aucun autre caractere interprete par le shell.
write_if_changed /etc/fail2ban/jail.d/sahel-edu.local 0644 <<JAIL
# Genere par deploy/harden-server.sh - ne pas editer a la main.
# Recharger : sudo fail2ban-client reload

[DEFAULT]
# Une adresse bannie 1 h apres 5 echecs en 10 min. Un utilisateur qui se trompe
# deux fois de mot de passe n'est pas bloque ; un balayage automatique l'est.
bantime  = 1h
findtime = 10m
maxretry = 5
backend  = systemd
ignoreip = ${JAIL_IGNORE}
# Bannissement cote noyau (regle UFW) : l'attaque n'atteint plus sshd du tout.
banaction = ufw

[sshd]
enabled  = true
# Plus strict que la valeur par defaut : sshd n'autorise que 4 essais.
maxretry = 4
JAIL

systemctl enable fail2ban >/dev/null 2>&1 || true
systemctl restart fail2ban >/dev/null 2>&1 || warn "fail2ban n'a pas demarre (journalctl -u fail2ban -n 50)"
if ! fail2ban-client status sshd >/dev/null 2>&1; then
  warn "la prison sshd n'est pas active : verifiez 'fail2ban-client status'."
fi

# ==================================================================
# 6. Journaux systeme
#
# Sans plafond, journald peut remplir le disque du VPS - et un disque plein
# fait tomber MySQL, donc l'application entiere.
# ==================================================================
log "journaux systemd"

write_if_changed /etc/systemd/journald.conf.d/99-sahel-edu.conf 0644 <<'JOURNALD'
# Genere par deploy/harden-server.sh - ne pas editer a la main.
[Journal]
SystemMaxUse=500M
SystemMaxFileSize=50M
# Toujours laisser de la place libre, meme si le plafond n'est pas atteint.
SystemKeepFree=1G
MaxRetentionSec=1month
JOURNALD
systemctl restart systemd-journald >/dev/null 2>&1 || warn "systemd-journald n'a pas redemarre"

# ==================================================================
# 7. Journaux Docker
#
# Par defaut, Docker conserve les journaux d'un conteneur SANS LIMITE : une
# application bavarde remplit le disque en quelques semaines, et le disque
# plein casse MySQL. C'est la panne la plus banale et la plus evitable.
# ==================================================================
log "journaux Docker"

DAEMON_JSON=/etc/docker/daemon.json
DESIRED_JSON='{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"},"live-restore":true}'
# Le pilote « json-file » est deja celui de Docker par defaut : tester le
# pilote ne dirait donc RIEN de l'application de max-size/max-file. Ces options
# ne sont lues qu'au demarrage du demon, d'ou ce drapeau dedie.
DOCKER_CONF_CHANGED=0

if [[ -f "$DAEMON_JSON" ]] && command -v jq >/dev/null 2>&1; then
  # Fusion et non ecrasement : ce fichier peut deja contenir des reglages du
  # fournisseur (miroir de registre, plage d'adresses...). Le * de jq fusionne
  # recursivement, donc les cles existantes sont conservees.
  TMP_JSON="$(mktemp)"
  if jq --argjson d "$DESIRED_JSON" '. * $d' "$DAEMON_JSON" > "$TMP_JSON" 2>/dev/null; then
    if cmp -s "$TMP_JSON" "$DAEMON_JSON"; then
      rm -f "$TMP_JSON"
      log "  deja conforme : $DAEMON_JSON"
    else
      chmod 0644 "$TMP_JSON"
      mv -f "$TMP_JSON" "$DAEMON_JSON"
      CHANGED=1
      DOCKER_CONF_CHANGED=1
      log "  fusionne dans $DAEMON_JSON (reglages existants conserves)"
    fi
  else
    rm -f "$TMP_JSON"
    warn "$DAEMON_JSON n'est pas du JSON valide : fichier laisse intact."
    warn "  corrigez-le puis relancez ce script."
  fi
elif [[ -f "$DAEMON_JSON" ]]; then
  warn "$DAEMON_JSON existe mais jq est absent : fusion impossible, fichier laisse intact."
  warn "  installez jq (apt-get install -y jq) puis relancez ce script."
else
  write_if_changed "$DAEMON_JSON" 0644 <<'DAEMONJSON'
{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"},"live-restore":true}
DAEMONJSON
  DOCKER_CONF_CHANGED=1
fi

# Un redemarrage de Docker est necessaire pour que daemon.json soit relu. Il
# arrete tous les conteneurs : on ne le fait donc que si le fichier vient
# reellement de changer, et on relance ensuite le service applicatif (la pile
# appartient a systemd, pas a un `compose up -d` lance a la main).
if [[ "$DOCKER_CONF_CHANGED" -eq 1 ]]; then
  warn "redemarrage de Docker pour appliquer la rotation des journaux"
  warn "  les conteneurs en cours vont redemarrer."
  systemctl restart docker || die "docker n'a pas redemarre"
  systemctl restart sahel-edu.service >/dev/null 2>&1 || true
fi

# ==================================================================
# 8. Pare-feu
#
# UFW est configure ICI et non dans install.sh : une seule source de verite
# pour la politique reseau. Il doit etre active AVANT le demarrage de Docker,
# sinon les reglages de conteneurs deja lances peuvent contredire la politique.
#
# Le port SSH est lu dans la configuration EFFECTIVE de sshd (sshd -T) et non
# suppose a 22 : un serveur deja deplace sur un autre port serait rendu
# injoignable par une regle 22/tcp seule.
# ==================================================================
log "pare-feu"

if ! command -v ufw >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y -qq ufw >/dev/null
  CHANGED=1
fi

SSH_PORT="$(sshd -T 2>/dev/null | awk '/^port /{print $2; exit}' || true)"
SSH_PORT="${SSH_PORT:-22}"
if [[ "$SSH_PORT" != "22" ]]; then
  warn "sshd ecoute sur le port $SSH_PORT : c'est ce port qui sera ouvert."
fi

if ! ufw status 2>/dev/null | grep -q "Status: active"; then
  warn "activation de UFW : les connexions SSH deja etablies sont conservees."
  warn "  toute NOUVELLE connexion devra passer par le port $SSH_PORT."
fi

ufw allow "${SSH_PORT}/tcp" >/dev/null 2>&1 || true
ufw allow 80/tcp  >/dev/null 2>&1 || true
ufw allow 443/tcp >/dev/null 2>&1 || true
ufw --force enable >/dev/null 2>&1 || die "impossible d'activer UFW"
ufw default deny incoming  >/dev/null 2>&1 || true
ufw default allow outgoing >/dev/null 2>&1 || true

# fail2ban bannit via une regle UFW (banaction = ufw) : tant que le pare-feu
# n'est pas actif, un bannissement n'a aucun effet. Ce rechargement rejoue les
# bannissements deja enregistres avec l'action desormais operationnelle.
fail2ban-client reload >/dev/null 2>&1 || true

# ==================================================================
# 9. SSH (uniquement sur demande explicite)
# ==================================================================
if [[ "$DO_SSH" -eq 1 ]]; then
  log "durcissement SSH"

  # Verifier qu'une cle existe AVANT de couper les mots de passe : sans cle,
  # PasswordAuthentication no equivaut a se couper definitivement l'acces.
  KEYS_FOUND=0
  for k in /root/.ssh/authorized_keys "/home/${SERVICE_USER:-root}/.ssh/authorized_keys"; do
    if [[ -s "$k" ]]; then
      log "  cle(s) trouvee(s) : $k"
      KEYS_FOUND=1
    fi
  done
  if [[ "$KEYS_FOUND" -eq 0 ]]; then
    die "aucune cle publique installee : desactiver les mots de passe vous
  enfermerait dehors.
  Depuis votre poste, installez d'abord votre cle :
    ssh-copy-id root@<ip-du-serveur>
  puis relancez : sudo ./deploy/harden-server.sh --ssh"
  fi

  if [[ ! -t 0 && "${ASSUME_YES:-0}" != "1" ]]; then
    die "--ssh demande une confirmation interactive.
  En script : ASSUME_YES=1 sudo -E ./deploy/harden-server.sh --ssh"
  fi
  if [[ -t 0 && "${ASSUME_YES:-0}" != "1" ]]; then
    warn "Cette etape coupe la connexion par MOT DE PASSE (les cles restent)."
    warn "Gardez une session ouverte et testez depuis un AUTRE terminal."
    printf 'Continuer ? [oui/non] '
    read -r ANSWER
    [[ "$ANSWER" == "oui" ]] || die "annule a votre demande."
  fi

  write_if_changed /etc/ssh/sshd_config.d/99-sahel-edu.conf 0644 <<'SSHD'
# Genere par deploy/harden-server.sh - ne pas editer a la main.
# Verifier avant rechargement : sudo sshd -t
# Ce fichier est inclus en tete de /etc/ssh/sshd_config : il a priorite.

# Les cles restent acceptees ; le mot de passe non. C'est la mesure qui rend
# le brute-force inutile, quelle que soit la robustesse du mot de passe.
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitEmptyPasswords no

# root peut encore se connecter, mais uniquement par cle.
PermitRootLogin prohibit-password

MaxAuthTries 4
LoginGraceTime 20
X11Forwarding no
ClientAliveInterval 300
ClientAliveCountMax 2
AllowAgentForwarding no
AllowTcpForwarding no
SSHD

  # sshd -t valide la syntaxe AVANT tout rechargement : une faute de frappe
  # dans le fichier couperait sinon toute nouvelle connexion.
  if ! sshd -t; then
    rm -f /etc/ssh/sshd_config.d/99-sahel-edu.conf
    die "configuration SSH invalide : fichier retire, aucun rechargement effectue."
  fi
  systemctl reload ssh 2>/dev/null || systemctl restart ssh
  log "  configuration appliquee et validee par sshd -t"
  warn "testez MAINTENANT depuis un autre terminal : ssh root@<ip-du-serveur>"
fi

# ==================================================================
# 10. Verification
# ==================================================================
log "verification"
printf '\n'

report() {
  printf '  %-34s %s\n' "$1" "$2"
}

report "fuseau horaire" "$(timedatectl show -p Timezone --value 2>/dev/null || echo inconnu)"
if [[ "$(timedatectl show -p NTPSynchronized --value 2>/dev/null || echo no)" == "yes" ]]; then
  report "horloge synchronisee" "oui"
else
  report "horloge synchronisee" "PAS ENCORE (normal dans les 5 min suivant le demarrage)"
fi
report "memoire d'echange" "$(free -m | awk '/^Swap:/{print $2" Mo"}')"
report "pare-feu" "$(ufw status 2>/dev/null | head -n 1 | sed 's/Status: //')"
report "port SSH ouvert" "${SSH_PORT}/tcp"
if fail2ban-client status sshd >/dev/null 2>&1; then
  report "fail2ban (prison sshd)" "active"
else
  report "fail2ban (prison sshd)" "A VERIFIER"
fi
if systemctl is-active --quiet unattended-upgrades; then
  report "mises a jour automatiques" "actives"
else
  report "mises a jour automatiques" "A VERIFIER"
fi
report "rotation journaux Docker" "$(docker info --format '{{.LoggingDriver}}' 2>/dev/null || echo 'docker arrete')"
if [[ "$DO_SSH" -eq 1 ]]; then
  report "mot de passe SSH" "desactive (cles seules)"
else
  report "mot de passe SSH" "inchange (relancer avec --ssh)"
fi
printf '\n'

if [[ "$CHANGED" -eq 0 ]]; then
  log "serveur deja conforme : aucune modification necessaire"
else
  log "durcissement applique"
fi

# Un redemarrage reste necessaire pour charger un eventuel noyau mis a jour.
if [[ -f /var/run/reboot-required ]]; then
  printf '\n'
  warn "un redemarrage est requis (mise a jour du noyau ou de la libc)."
  warn "  planifiez-le : sudo systemctl reboot"
  warn "  l'application revient seule (sahel-edu.service est active au demarrage)."
fi
printf '\n'
