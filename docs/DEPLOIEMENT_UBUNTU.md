# Déploiement sur Ubuntu Server 24.04 LTS

Guide d'installation, de sécurisation et d'exploitation de **Sahel Edu** sur un
serveur physique dédié, avec Docker Compose.

> Le module « Cours vidéo et PDF » a ses propres détails (stockage, quotas,
> vérification fonctionnelle) dans [COURS_VIDEO_PDF.md](COURS_VIDEO_PDF.md).
> Ce document-ci couvre l'installation complète du serveur.

---

## 1. Architecture déployée

```
Navigateur (LAN / Internet)
        │  HTTPS 443
        ▼
┌──────────────────────────────┐
│ nginx HÔTE (TLS, Let's Encrypt) │  ← deploy/https.sh
│  proxy -> 127.0.0.1:8081      │
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐        ┌───────────────────────┐
│ conteneur frontend (nginx)     │  /api  │ conteneur backend      │
│  SPA React + proxy interne     │───────▶│ Spring Boot :8080      │
└──────────────────────────────┘        └──────────┬────────────┘
                                                   │
                        ┌──────────────────────────┴─────────────┐
                        ▼                                        ▼
              ┌──────────────────┐                    ┌────────────────────┐
              │ mysql:8.4        │                    │ /srv/sahel-edu/    │
              │ volume mysql_data│                    │ uploads (vidéos,   │
              │ 127.0.0.1:3307   │                    │ PDF, photos)       │
              └──────────────────┘                    └────────────────────┘
```

Principes :

- **Un seul port exposé** vers l'extérieur : 443 (et 80 pour la redirection).
- MySQL et le backend ne sont publiés que sur `127.0.0.1`.
- Les fichiers pédagogiques et les photos vivent sur le **disque de l'hôte**,
  pas dans la couche du conteneur.
- Trois unités systemd : la pile, la sauvegarde, son minuteur.

---

## 2. Prérequis

| Élément | Minimum |
|---|---|
| Serveur | x86_64, 2 vCPU, 4 Go de RAM, 40 Go de disque (les vidéos consomment vite) |
| Système | **Ubuntu Server 24.04 LTS** (Debian 12 fonctionne aussi) |
| Accès | compte `sudo`, SSH actif |
| Réseau | IP fixe sur le LAN ; nom de domaine seulement pour HTTPS depuis Internet |

Vérifier la version avant de commencer :

```bash
lsb_release -a         # attendu : Ubuntu 24.04.x LTS
df -h /                # au moins 20 Go libres
free -h
```

---

## 3. Installation (4 commandes)

> **Serveur sans Internet** (ou installation entièrement hors ligne) : utiliser
> le bundle autonome décrit dans
> [DEPLOIEMENT_HORS_LIGNE.md](DEPLOIEMENT_HORS_LIGNE.md). Les deux procédures
> installent la même pile ; seule la provenance des images diffère.

```bash
sudo apt-get update && sudo apt-get install -y git
sudo git clone https://github.com/Harouna223/school-management-system.git /opt/sahel-edu-install
cd /opt/sahel-edu-install
# OFFSITE_DIR est OBLIGATOIRE : un disque distinct de celui des donnees.
sudo OFFSITE_DIR=/media/backup/sahel-edu ./deploy/install.sh
```

Le script est **idempotent** : le relancer met à jour le code et répare
l'installation, sans jamais régénérer les secrets existants.

Variantes utiles :

```bash
# Nom d'école personnalisé et domaine connu dès le départ
sudo SCHOOL_NAME="Complexe Scolaire Sahel" DOMAIN=ecole.example.com ./deploy/install.sh

# Installer ailleurs (déconseillé de changer APP_DIR après coup)
sudo APP_DIR=/opt/sahel-edu DATA_DIR=/srv/sahel-edu ./deploy/install.sh
```

### Ce que fait `install.sh`

| Étape | Détail |
|---|---|
| Paquets | `curl`, `git`, `openssl`, `rsync`, `jq`, `ufw` |
| Docker | dépôt officiel : `docker-ce`, `buildx`, `compose-plugin` |
| Répertoires | `/srv/sahel-edu/uploads` (propriétaire **10001:10001**, l'UID du backend non-root), `/srv/sahel-edu/backups/{db,uploads}` (root, 750) |
| Hors site | crée `OFFSITE_DIR`, avertit s'il est sur le même disque que les données |
| Code | copie depuis le dépôt courant, ou `git clone` dans `/opt/sahel-edu` |
| Secrets | `docker/.env` généré en **mode 600** : `DB_PASSWORD` (root MySQL), `DB_APP_USER`/`DB_APP_PASSWORD` (compte applicatif), `JWT_SECRET` (48 octets aléatoires), `ADMIN_PASSWORD` |
| Sauvegarde | `/etc/sahel-edu/backup.env` (mode 600) : chemins et `OFFSITE_DIR`, lu par l'unité systemd |
| Port MySQL | premier port libre à partir de 3307 (évite un MySQL local) |
| systemd | installe et active `sahel-edu.service`, `sahel-edu-backup.timer` |
| Durcissement | appelle `deploy/harden-server.sh` (horloge, swap, pare-feu, fail2ban, journaux) — voir §5 |
| Pare-feu | UFW : port SSH réellement écouté, 80, 443 entrants ; le reste fermé |
| Vérification | attend `actuator/health` = `UP` (≈ 90 s au premier démarrage : migrations Flyway) |

À la fin, le script affiche **le mot de passe administrateur généré**. Notez-le :
il n'est pas réaffiché et n'est stocké qu'en clair dans `docker/.env` (600).

Accès immédiat : `http://IP_DU_SERVEUR`

---

## 4. HTTPS (Let's Encrypt)

### 4.1 Préparer le nom de domaine

1. Créer un enregistrement DNS **A** : `ecole.example.com -> IP_PUBLIQUE`.
2. Sur le routeur, rediriger **80/tcp et 443/tcp** vers l'IP locale du serveur.
3. Vérifier depuis l'extérieur :

```bash
dig +short ecole.example.com
curl -I http://ecole.example.com      # doit répondre (301 ou 200)
```

### 4.2 Activer TLS

```bash
cd /opt/sahel-edu
sudo ./deploy/https.sh ecole.example.com admin@example.com
```

Le script :

1. vérifie que le domaine résout vers **une** des adresses du serveur, en IPv4
   **et** en IPv6 (sinon il s'arrête en affichant les deux listes ; `--force`
   pour passer outre) ;
2. installe `nginx` + `certbot` ;
3. configure nginx en **HTTP seul** pour la validation ACME — **uniquement si
   aucun certificat n'existe encore** (voir l'encadré ci-dessous) ;
4. obtient le certificat (`certbot certonly --webroot --keep-until-expiring`) ;
5. bascule nginx en **HTTPS complet** : HSTS, TLS 1.2/1.3, OCSP stapling,
   redirection 80→443, repli `444` pour tout nom d'hôte inconnu ;
6. déplace le conteneur frontend sur `127.0.0.1:8081` (plus accessible en direct) ;
7. installe un *deploy hook* qui recharge nginx à chaque renouvellement ;
8. active `certbot.timer` (renouvellement automatique).

> **Le script est rejouable.** Relancer `https.sh` sur une installation déjà en
> HTTPS ne la dégrade pas : la phase « HTTP seul » est ignorée dès qu'un
> certificat existe. C'était le principal défaut de la version précédente —
> une relance réécrivait le site en clair, et si `certbot` échouait ensuite
> (DNS modifié, quota ACME atteint) la production restait servie en HTTP, donc
> **inaccessible** pour tout navigateur ayant déjà reçu le HSTS.

Vérifications :

```bash
systemctl list-timers certbot.timer
certbot renew --dry-run
curl -I https://ecole.example.com          # 200 + Strict-Transport-Security
```

Le script les exécute déjà en fin de course et affiche :

| Contrôle | Attendu |
|---|---|
| redirection HTTP | `HTTP 301` |
| page d'accueil | `HTTP 200` |
| en-tête HSTS | `max-age=31536000; includeSubDomains` |
| écoute du frontend | `127.0.0.1:8081` uniquement |
| jours restants du certificat | affiché, alerte sous 14 jours |

> **`http://IP_DU_SERVEUR` cesse de répondre** après cette étape. C'est voulu :
> le frontend n'écoute plus que sur la boucle locale, et le repli `444` ferme
> les connexions dont le `Host` n'est pas le domaine configuré. Sans ce repli,
> nginx répondait à **n'importe quel** domaine pointant vers l'IP du serveur.

### 4.3 Cas particuliers

| Situation | Solution |
|---|---|
| **CGNAT** (aucune redirection entrante possible) | Tunnel sortant (WireGuard/Tailscale) vers une passerelle publique, ou IP publique dédiée chez le fournisseur. Ne jamais exposer MySQL/backend. |
| **Pas de domaine** | Rester en HTTP sur le LAN (installation par défaut) ou créer un certificat auto-signé pour le LAN : `openssl req -x509` puis adapter manuellement la conf nginx. |
| **Proxy/CDN devant** | `sudo ./deploy/https.sh domaine email --force`, et laisser le CDN gérer le certificat. |
| **VPS IPv4 + IPv6 (LWS)** | Géré : le script compare le domaine à l'IPv4 **et** à l'IPv6 du serveur. L'enregistrement `AAAA` est facultatif ; s'il existe et pointe ailleurs, le diagnostic l'affiche. |
| **Le site répond sur un domaine qui n'est pas le vôtre** | Impossible depuis cette version : un bloc `default_server` renvoie `444` pour tout `Host` inconnu. |

---

## 5. Réseau local et pare-feu

### IP fixe

```bash
# /etc/netplan/01-sahel-edu.yaml (adapter l'interface et la passerelle)
network:
  version: 2
  ethernets:
    enp3s0:
      addresses: [192.168.1.50/24]
      routes:
        - to: default
          via: 192.168.1.1
      nameservers:
        addresses: [192.168.1.1, 1.1.1.1]
```

```bash
sudo chmod 600 /etc/netplan/01-sahel-edu.yaml && sudo netplan apply
```

### Pare-feu

Le pare-feu est configuré par `deploy/harden-server.sh`, appelé par
`install.sh`. Il est volontairement **dans un seul endroit** : deux scripts
appliquant une politique réseau finissent toujours par diverger.

```bash
sudo ./deploy/harden-server.sh     # idempotent, relançable à tout moment
sudo ufw status verbose
```

Politique appliquée :

| Règle | Détail |
|---|---|
| `deny incoming` / `allow outgoing` | politique par défaut |
| SSH | port lu dans la configuration **effective** de sshd (`sshd -T`), pas supposé à 22 |
| 80, 443 | HTTP/HTTPS publics (validation ACME puis TLS) |
| MySQL, backend | jamais ouverts : publiés sur `127.0.0.1` par le compose |

> Le port SSH est détecté et non codé en dur : un serveur déjà déplacé sur un
> autre port aurait été rendu injoignable par une règle `22/tcp` seule.

Pour restreindre SSH au réseau de l'école (recommandé) :

```bash
sudo ufw delete allow 22/tcp
sudo ufw allow from 192.168.1.0/24 to any port 22 proto tcp
sudo ufw status numbered
```

### Accès SSH durci

```bash
# 1. depuis VOTRE poste, installer la clé publique
ssh-copy-id root@IP_DU_SERVEUR

# 2. vérifier que la connexion par clé fonctionne (nouveau terminal)
ssh root@IP_DU_SERVEUR

# 3. seulement ensuite, fermer les mots de passe
sudo ./deploy/harden-server.sh --ssh
```

Le script refuse de s'exécuter s'il ne trouve **aucune** clé publique : sans
clé, `PasswordAuthentication no` équivaut à se couper définitivement l'accès.
Il valide aussi le fichier avec `sshd -t` **avant** de recharger sshd, et le
retire si la syntaxe est invalide.

Ce qui est appliqué (`/etc/ssh/sshd_config.d/99-sahel-edu.conf`) :

```
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password     # root par clé uniquement
MaxAuthTries 4
X11Forwarding no
```

> ⚠️ **Ne pas mettre `PermitRootLogin no`** sur un VPS LWS dont l'accès se fait
> en `root` : c'est la seule voie d'entrée, et elle serait fermée sans recours
> autre que la console de secours du fournisseur. `prohibit-password` interdit
> déjà le mot de passe de root, ce qui est l'objectif réel.

Si un jour un utilisateur `sudo` non-root devient la voie d'accès principale,
`PermitRootLogin no` devient légitime — mais après avoir vérifié la connexion.

### Durcissement du système — détail et vérifications

`deploy/harden-server.sh` est **idempotent** : relancé, il ne réécrit que ce
qui diverge et n'affiche `deja conforme` que lorsque rien n'a bougé. Il ne
redémarre jamais la machine et ne touche pas à SSH sans `--ssh`.

| # | Mesure | Pourquoi | Vérification | Attendu |
|---|---|---|---|---|
| 1 | Fuseau `Africa/Bamako` + NTP | une horloge qui dérive invalide les JWT et le TLS | `timedatectl` | `Time zone: Africa/Bamako`, `NTP service: active` |
| 2 | `/swapfile` 2 Go, `swappiness=10` | sans swap, un pic mémoire fait tuer un conteneur par l'OOM killer | `free -h` puis `swapon --show` | `Swap:` non nul, `/swapfile` listé |
| 3 | `99-sahel-edu.conf` (sysctl) | anti-usurpation, anti-SYN-flood, noyau non lisible | `sysctl vm.swappiness kernel.dmesg_restrict` | `10`, `1` |
| 4 | `unattended-upgrades` | correctifs de sécurité sans intervention | `systemctl status unattended-upgrades` | `active (running)` |
| 5 | `fail2ban` (prison `sshd`) | UFW ferme le port, pas les 10 000 tentatives dessus | `fail2ban-client status sshd` | `Currently banned:` (0 ou plus) |
| 6 | journald plafonné à 500 Mo | un disque plein fait tomber MySQL | `journalctl --disk-usage` | `<= 500M` |
| 7 | rotation des journaux Docker (10 Mo × 3) | par défaut Docker ne supprime **jamais** ses journaux | `docker inspect -f '{{.HostConfig.LogConfig}}' school-backend` | `json-file map[max-file:3 max-size:10m]` |
| 8 | UFW | politique réseau unique | `ufw status verbose` | `Status: active`, `deny (incoming)` |

Une seule commande pour tout revérifier :

```bash
sudo ./deploy/harden-server.sh
```

S'il ne reste rien à faire, la sortie se termine par
`serveur deja conforme : aucune modification necessaire`.

> **`live-restore` et redémarrages** : le fichier `/etc/docker/daemon.json` est
> fusionné avec l'existant (jamais écrasé : un miroir de registre configuré par
> le fournisseur serait perdu). Docker n'est redémarré que si ce fichier vient
> réellement de changer — un redémarrage arrête tous les conteneurs.

### Compte MySQL applicatif

Le backend **ne se connecte pas en root**. Il utilise le compte `sahel_app`,
dont les droits sont limités à la base `school_management` : une injection SQL
ou une image compromise ne peut donc ni lire `mysql.user`, ni toucher une autre
base hébergée sur le même serveur.

Les identifiants sont générés par `install.sh` (`DB_APP_USER`, `DB_APP_PASSWORD`
dans `docker/.env`) et le compte est créé automatiquement par l'image MySQL au
premier démarrage.

Sur une installation **déjà en service**, le volume MySQL est déjà initialisé :
l'image ignore `MYSQL_USER`. C'est `deploy/update.sh` qui crée le compte — il le
fait avant de redémarrer les conteneurs, de façon idempotente. Une mise à jour
faite à la main (`git pull` puis `docker compose up`) sans passer par ce script
laisserait donc le backend sans compte valide.

Vérifier le compte effectif :

```bash
sudo docker compose -f /opt/sahel-edu/docker/docker-compose.yml \
  exec -T -e MYSQL_PWD="$(sudo sed -n 's/^DB_PASSWORD=//p' /opt/sahel-edu/docker/.env)" mysql \
  mysql -uroot -e "SELECT user, host FROM mysql.user WHERE user='sahel_app';
                   SHOW GRANTS FOR 'sahel_app'@'%';"
```

La sauvegarde (`sahel-edu-backup`) reste volontairement en root : `mysqldump
--routines` exige le privilège global `SHOW_ROUTINE`, que le compte applicatif
ne doit pas recevoir. Ce conteneur n'expose aucun port et n'accepte aucune
entrée utilisateur.

### Limitation de débit des connexions

Le nginx du conteneur frontend plafonne les routes d'authentification à
**120 requêtes par minute et par adresse IP**, avec une réserve de 40 requêtes
instantanées. Au-delà, la réponse est un `429` accompagné d'un message JSON
lisible par l'interface.

Ce plafond ne vise pas le mot de passe deviné — le backend verrouille déjà le
compte ciblé et compte les échecs par IP — mais la saturation de Tomcat : un
déluge de requêtes atteindrait le serveur Java avant que le code applicatif ne
puisse réagir. La valeur est volontairement généreuse pour qu'une classe entière
se connectant à 8 h derrière une même adresse publique passe sans encombre.

Seules les cinq routes `/api/auth/*` (login, refresh, logout, register,
change-password) sont concernées : `/api/` est appelé en permanence par
l'interface et un plafond global casserait l'usage normal.

Pour ajuster, modifier `limit_req_zone` dans `docker/nginx.conf` puis
reconstruire l'image frontend (`deploy/update.sh`).

L'en-tête `X-Forwarded-For` est **écrasé** par chaque nginx avec l'adresse réelle
du client, et non complété. C'est indispensable : le backend lit la première
valeur de cet en-tête, donc un en-tête fabriqué par le client permettait de
contourner la limitation par IP. Le conteneur restaure l'adresse d'origine via
`set_real_ip_from` (plages Docker uniquement), ce qui la rend fiable derrière le
nginx hôte.

---

## 6. Sauvegarde et restauration

### Automatique

`sahel-edu-backup.timer` déclenche chaque nuit à **02:30** (rattrapage si le
serveur était éteint) :

| Fichier produit | Contenu |
|---|---|
| `/srv/sahel-edu/backups/db/db-AAAAMMJJ-HHMM.sql.gz` | dump complet (données, routines, triggers) |
| `/srv/sahel-edu/backups/uploads/uploads-AAAAMMJJ-HHMM.tar.gz` | vidéos, PDF, photos |

Les archives sont vérifiées avant d'être conservées : intégrité de la compression
(`gzip -t`, `tar tzf`), présence effective de tables dans le dump, et comparaison
octet par octet de la copie hors site avec l'original. Une sauvegarde inutilisable
fait donc échouer le service, au lieu d'être découverte le jour de la restauration.
Rétention **14 jours**.

```bash
systemctl list-timers sahel-edu-backup.timer
sudo systemctl start sahel-edu-backup.service     # sauvegarde immédiate
journalctl -u sahel-edu-backup -n 30
```

### Copie hors site (obligatoire)

Une sauvegarde sur le même disque ne protège ni d'une panne de ce disque, ni d'un
ransomware, ni d'un vol de la machine. `OFFSITE_DIR` est donc **exigé à
l'installation** :

```bash
sudo OFFSITE_DIR=/media/backup/sahel-edu ./deploy/install.sh
```

Le script crée le répertoire, le réserve à root, et **avertit si le disque cible
est le même que celui des données** (`stat -c %d`) — un répertoire « hors site »
posé sur le même disque n'apporte rien.

La valeur est écrite dans `/etc/sahel-edu/backup.env` (mode 600), lu par l'unité
systemd via `EnvironmentFile`. Pour la changer sur une installation existante,
éditez ce fichier plutôt que l'unité — sinon la prochaine mise à jour l'écraserait :

```bash
sudo nano /etc/sahel-edu/backup.env
sudo systemctl start sahel-edu-backup.service   # vérifier immédiatement
```

**En l'absence de `OFFSITE_DIR`, la sauvegarde échoue** au lieu de se contenter
d'un avertissement : `systemctl --failed` le montre, et le problème ne peut plus
passer inaperçu pendant des mois. Pour installer malgré tout sans copie hors site
(déconseillé, mais explicite) : `sudo ALLOW_NO_OFFSITE=1 ./deploy/install.sh`.

Ou par `rsync` planifié vers un NAS, si la copie est gérée hors de l'application :

```bash
sudo rsync -av --delete /srv/sahel-edu/backups/ /mnt/nas/sahel-edu/
```

Dans ce cas, `OFFSITE_DIR` doit tout de même être renseigné — sinon le service de
sauvegarde reste en échec, ce qui est le comportement voulu : une copie gérée
ailleurs doit être une décision écrite, pas un oubli.

### Restauration

La restauration est un script, pas une suite de commandes à recopier : c'est une
procédure qu'on exécute rarement, donc précisément celle qu'on risque de se
tromper le jour où elle compte.

```bash
# Base seule (écrase la base actuelle)
sudo ./deploy/restore.sh --db /srv/sahel-edu/backups/db/db-AAAAMMJJ-HHMM.sql.gz

# Base + fichiers pédagogiques
sudo ./deploy/restore.sh \
  --db      /srv/sahel-edu/backups/db/db-AAAAMMJJ-HHMM.sql.gz \
  --uploads /srv/sahel-edu/backups/uploads/uploads-AAAAMMJJ-HHMM.tar.gz
```

Le script, dans l'ordre :

1. vérifie l'archive (`gzip -t`, présence de tables, présence de `USE`) ;
2. demande confirmation (taper `restaurer`), `--yes` pour un usage non interactif ;
3. arrête la pile par systemd, ne relance que MySQL ;
4. **prend une sauvegarde de sécurité de l'état actuel** dans
   `/srv/sahel-edu/backups/db/avant-restauration-AAAAMMJJ-HHMMSS.sql.gz` ;
5. supprime et recrée la base, puis importe l'archive ;
6. contrôle que le nombre de tables restaurées égale celui de l'archive ;
7. déplace les fichiers actuels dans `<UPLOAD_HOST_DIR>.avant-restauration-…`
   avant d'extraire l'archive, et rétablit le propriétaire `10001:10001` ;
8. redémarre la pile et attend que le backend réponde.

| Situation | Ce que fait le script |
| --- | --- |
| Archive tronquée ou corrompue | Refuse avant toute écriture |
| Archive valide mais sans aucune table | Refuse : un dump vide n'est pas une sauvegarde |
| Archive sans instruction `USE` | Refuse : l'import n'aurait pas de base cible |
| Restauration interrompue en cours de route | Le site est relancé automatiquement (piège `EXIT`) |
| Nombre de tables incohérent après import | Échoue et indique où se trouve la sauvegarde de sécurité |
| Backend qui ne redémarre pas | Échoue avec le renvoi vers `journalctl` |

> **Une restauration est réversible.** L'état d'avant restauration est conservé
> dans `avant-restauration-*.sql.gz`. Ce nom ne correspond volontairement pas au
> motif `db-*.sql.gz` : la rétention de `backup.sh` ne purgera jamais la seule
> copie de l'état d'avant restauration.
>
> **Le propriétaire des fichiers est rétabli.** Les archives enregistrent l'UID
> **10001** du backend ; le script extrait en root puis force
> `chown -R 10001:10001`. Sans cela le backend ne pourrait plus remplacer ni
> supprimer les fichiers, et l'échec n'apparaîtrait qu'au premier téléversement.

### Vérifier qu'une sauvegarde est réellement restaurable

Une sauvegarde dont on n'a jamais essayé la restauration n'est pas une
sauvegarde : c'est une archive dont on espère quelque chose. Le mode `--verify`
restaure l'archive dans une base jetable et **compare le contenu** de chaque
table avec la production, sans rien modifier :

```bash
sudo ./deploy/restore.sh --verify \
  --db "$(ls -t /srv/sahel-edu/backups/db/db-*.sql.gz | head -1)"
```

```
[sahel-edu] archive verifiee : db-20261113-0611.sql.gz (68K, 58 tables)
[sahel-edu] exercice de restauration dans la base jetable verif_20261113061246
[sahel-edu] exercice reussi : 58 tables comparees, contenu identique
[sahel-edu] la sauvegarde db-20261113-0611.sql.gz est restaurable
```

La comparaison utilise `CHECKSUM TABLE` et non `COUNT(*)` : un décalage d'une
ligne compensé par une autre passerait inaperçu avec de simples comptages. La
base jetable est supprimée à la fin, y compris en cas d'échec.

À exécuter **après chaque changement de version** et au moins une fois par
trimestre — c'est le seul contrôle qui prouve que la chaîne
`mysqldump → gzip → import` fonctionne sur vos données réelles.

### Deux restaurations à ne pas confondre

L'application propose aussi **Paramètres → Sauvegarde → Restaurer**, qui importe
un fichier `.sql` depuis l'interface. Ce n'est pas la même chose :

| | `deploy/restore.sh` | Restaurer (interface) |
| --- | --- | --- |
| Portée | Base **et** fichiers pédagogiques | Données uniquement |
| Taille | Aucune limite | 100 Mo, 500 000 instructions |
| Arrêt du service | Oui, contrôlé | Non, à chaud |
| Usage | Sinistre, panne disque, migration de serveur | Correction ponctuelle de données |
| Sauvegarde préalable | Automatique | Aucune |

> ⚠️ **La restauration depuis l'interface ne peut pas être annulée en cas
> d'échec.** Elle est déclarée `@Transactional` mais exécute du DDL
> (`DROP TABLE`, `CREATE TABLE`), que MySQL valide implicitement : si l'import
> s'interrompt au milieu, le schéma reste partiellement détruit et la
> transaction ne peut rien annuler. Prenez systématiquement une sauvegarde
> `backup.sh` juste avant, et préférez `restore.sh` dès qu'il s'agit de plus
> qu'une correction de quelques lignes.

---

## 7. Mise à jour

```bash
cd /opt/sahel-edu
sudo OFFSITE_DIR=/media/backup/sahel-edu ./deploy/update.sh
```

Le script, dans cet ordre : sauvegarde complète, `git pull --ff-only`, migration
des permissions et des unités systemd, reconstruction des images, redémarrage,
vérification de santé.

- **La pile est arrêtée pendant la reconstruction.** Comptez quelques minutes
  d'indisponibilité. Si la construction échoue, le script relance l'ancienne
  version au lieu de laisser le site à l'arrêt.
- Les migrations Flyway s'appliquent **au démarrage** du backend.
- Le volume `mysql_data` et `/srv/sahel-edu/uploads` ne sont jamais supprimés.
- ⚠️ Ne jamais lancer `docker compose down -v` : `-v` supprime les volumes, donc
  la base de données.
- La pile appartient à systemd. Pour redémarrer à la main, utiliser
  `sudo systemctl restart sahel-edu.service`, **pas** `docker compose up -d`,
  qui lancerait un second processus sur les mêmes conteneurs.

### Première mise à jour depuis une version antérieure au durcissement

Cette version fait tourner le backend en UID 10001 et exige une copie hors site.
`update.sh` s'en charge, mais deux points méritent une vérification après coup.

1. **Le propriétaire de `/srv/sahel-edu/uploads` change** (root → 10001), de
   façon automatique :

   ```bash
   stat -c '%u:%g' /srv/sahel-edu/uploads   # attendu : 10001:10001
   ```

   Puis **téléversez réellement un fichier** dans « Cours et ressources » : un
   `Permission denied` à cet endroit signifie que la migration n'a pas eu lieu,
   et c'est le seul symptôme visible.

2. **`OFFSITE_DIR` devient obligatoire.** En le passant en argument de
   `update.sh`, il est écrit dans `/etc/sahel-edu/backup.env`. Sans lui,
   `sahel-edu-backup` échoue — c'est voulu, mais `systemctl --failed` doit
   rester vide :

   ```bash
   sudo systemctl start sahel-edu-backup && systemctl status sahel-edu-backup
   ```

3. **La documentation interactive de l'API est refermée.** Un `docker/.env`
   antérieur ne contient pas `SWAGGER_ENABLED` ; le compose la résolvait donc à
   `true`. `update.sh` ajoute `SWAGGER_ENABLED=false` **uniquement si la clé est
   absente** — une installation qui l'a volontairement laissée à `true` n'est
   pas fermée à son insu :

   ```bash
   grep '^SWAGGER_ENABLED' /opt/sahel-edu/docker/.env   # attendu : SWAGGER_ENABLED=false
   ```

### Retour arrière

```bash
cd /opt/sahel-edu
sudo git log --oneline -5
sudo git checkout <commit-precedent>
sudo docker compose -f docker/docker-compose.yml build
sudo systemctl restart sahel-edu.service
```

Si une migration a modifié le schéma, restaurer la sauvegarde correspondante.

---

## 8. Exploitation

```bash
# État
systemctl status sahel-edu.service
sudo docker compose -f /opt/sahel-edu/docker/docker-compose.yml ps

# Journaux
journalctl -u sahel-edu -f
sudo docker logs -f school-backend
sudo docker logs -f school-mysql

# Santé
curl -s http://127.0.0.1:8080/actuator/health

# Redémarrage
sudo systemctl restart sahel-edu.service

# Espace disque (les vidéos pèsent vite)
df -h /srv
du -sh /srv/sahel-edu/uploads/courses
find /srv/sahel-edu/uploads/courses -type f -size +300M -printf '%s %p\n' | sort -nr | head
```

---

## 9. Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| `502 Bad Gateway` en HTTPS | conteneur frontend arrêté ou mauvais port | `docker compose ps`, `env_get FRONTEND_PORT` ; vérifier `127.0.0.1:8081` |
| `413 Request Entity Too Large` | limite nginx hôte < vidéo | `client_max_body_size` (512m) et `MAX_FILE_SIZE`/`COURSE_MAX_VIDEO_MB` |
| `400` au téléversement vidéo | format réel non supporté | le backend valide la **signature binaire** (PDF/MP4/WebM) |
| `403` sur `/api/courses` | permission `COURSE_READ` absente | migration V5 appliquée ? se reconnecter (permissions dans le JWT) |
| Le backend redémarre en boucle | migration Flyway en échec | `docker logs school-backend`, corriger, restaurer la sauvegarde si nécessaire |
| `Bind for 127.0.0.1:3307 failed` | port occupé (MySQL local) | changer `DB_PORT` dans `docker/.env`, `systemctl restart sahel-edu` |
| HTTPS échoue à la validation | DNS, NAT ou CGNAT | `dig`, `curl -I http://domaine`, relancer avec `--force` après correction |
| Disque plein | vidéos volumineuses | purger les brouillons, archiver, augmenter `COURSE_MAX_VIDEO_MB` raisonnablement |

Journaux détaillés : `journalctl -u sahel-edu -n 200 --no-pager`.

---

## 10. Désinstallation (sans perte de données)

```bash
sudo systemctl disable --now sahel-edu.service sahel-edu-backup.timer
sudo docker compose -f /opt/sahel-edu/docker/docker-compose.yml down
# Les volumes NE sont PAS supprimés. Pour les retirer (destructif) :
#   sudo docker volume rm school-management_mysql_data
```

Les fichiers restent dans `/srv/sahel-edu/uploads` et les sauvegardes dans
`/srv/sahel-edu/backups`.

---

## 11. Recette après déploiement

1. `systemctl status sahel-edu.service` → `active (running)`
2. `curl -s http://127.0.0.1:8080/actuator/health` → `{"status":"UP"}`
3. **Conteneurs non root** — les deux doivent renvoyer un UID différent de 0 :
   ```bash
   docker compose -f docker/docker-compose.yml exec backend  id -u   # attendu : 10001
   docker compose -f docker/docker-compose.yml exec frontend id -u   # attendu : 101
   ```
4. Connexion `admin` avec le mot de passe affiché à l'installation
5. **Changer le mot de passe admin** (Profil)
6. Créer une classe, une matière, un élève de test
7. Menu **Cours et ressources** : créer un cours PDF, le publier.
   C'est aussi le test des permissions du volume : un `Permission denied` ici
   signifie que `$DATA_DIR/uploads` n'appartient pas à 10001.
8. Se connecter avec l'élève de test : le cours apparaît, s'ouvre, se télécharge
9. Vérifier qu'un élève d'une autre classe ne le voit pas
10. `sudo systemctl start sahel-edu-backup.service` → archives dans
    `/srv/sahel-edu/backups` **et** copie identique dans `OFFSITE_DIR`.
    `systemctl status sahel-edu-backup` doit être en `success` : un `failed`
    signifie que la copie hors site n'a pas eu lieu.
11. `https://` → cadenas valide, HTTP redirigé
12. **La sauvegarde est restaurable** — le contrôle qui valide toute la chaîne,
    à faire une fois avant la mise en service :
    ```bash
    sudo ./deploy/restore.sh --verify \
      --db "$(ls -t /srv/sahel-edu/backups/db/db-*.sql.gz | head -1)"
    ```
    Attendu : `exercice reussi : N tables comparees, contenu identique`.

La checklist fonctionnelle détaillée du module est en §7 de
[COURS_VIDEO_PDF.md](COURS_VIDEO_PDF.md).
