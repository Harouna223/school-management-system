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
sudo ./deploy/install.sh
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
| Répertoires | `/srv/sahel-edu/uploads`, `/srv/sahel-edu/backups/{db,uploads}` (mode 750, propriétaire root) |
| Code | copie depuis le dépôt courant, ou `git clone` dans `/opt/sahel-edu` |
| Secrets | `docker/.env` généré en **mode 600** : `DB_PASSWORD`, `JWT_SECRET` (48 octets aléatoires), `ADMIN_PASSWORD` |
| Port MySQL | premier port libre à partir de 3307 (évite un MySQL local) |
| systemd | installe et active `sahel-edu.service`, `sahel-edu-backup.timer` |
| Pare-feu | UFW : 22, 80, 443 entrants ; le reste fermé |
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

1. vérifie que le domaine résout vers l'IP publique du serveur (sinon il
   s'arrête avec un diagnostic ; `--force` pour passer outre) ;
2. installe `nginx` + `certbot` ;
3. configure nginx en **HTTP seul** pour la validation ACME ;
4. obtient le certificat (`certbot certonly --webroot`) ;
5. bascule nginx en **HTTPS complet** (HSTS, TLS 1.2/1.3, redirection 80→443) ;
6. déplace le conteneur frontend sur `127.0.0.1:8081` (plus accessible en direct) ;
7. installe un *deploy hook* qui recharge nginx à chaque renouvellement ;
8. active `certbot.timer` (renouvellement automatique).

Vérifications :

```bash
systemctl list-timers certbot.timer
certbot renew --dry-run
curl -I https://ecole.example.com          # 200 + Strict-Transport-Security
```

### 4.3 Cas particuliers

| Situation | Solution |
|---|---|
| **CGNAT** (aucune redirection entrante possible) | Tunnel sortant (WireGuard/Tailscale) vers une passerelle publique, ou IP publique dédiée chez le fournisseur. Ne jamais exposer MySQL/backend. |
| **Pas de domaine** | Rester en HTTP sur le LAN (installation par défaut) ou créer un certificat auto-signé pour le LAN : `openssl req -x509` puis adapter manuellement la conf nginx. |
| **Proxy/CDN devant** | `sudo ./deploy/https.sh domaine email --force`, et laisser le CDN gérer le certificat. |

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

`install.sh` applique déjà :

```bash
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

Pour restreindre SSH au réseau de l'école (recommandé) :

```bash
sudo ufw delete allow 22/tcp
sudo ufw allow from 192.168.1.0/24 to any port 22 proto tcp
sudo ufw status numbered
```

### Accès SSH durci

```bash
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
```

---

## 6. Sauvegarde et restauration

### Automatique

`sahel-edu-backup.timer` déclenche chaque nuit à **02:30** (rattrapage si le
serveur était éteint) :

| Fichier produit | Contenu |
|---|---|
| `/srv/sahel-edu/backups/db/db-AAAAMMJJ-HHMM.sql.gz` | dump complet (données, routines, triggers) |
| `/srv/sahel-edu/backups/uploads/uploads-AAAAMMJJ-HHMM.tar.gz` | vidéos, PDF, photos |

Les archives sont vérifiées (`gzip -t`, `tar tzf`) avant d'être conservées ;
rétention **14 jours**.

```bash
systemctl list-timers sahel-edu-backup.timer
sudo systemctl start sahel-edu-backup.service     # sauvegarde immédiate
journalctl -u sahel-edu-backup -n 30
```

### Copie hors site (obligatoire)

Une sauvegarde sur le même disque ne protège pas d'une panne de ce disque.
Brancher un disque USB et définir `OFFSITE_DIR` dans l'unité :

```bash
sudo systemctl edit sahel-edu-backup.service
# [Service]
# Environment=OFFSITE_DIR=/media/backup/sahel-edu
sudo systemctl daemon-reload
```

Ou par `rsync` planifié vers un NAS :

```bash
sudo rsync -av --delete /srv/sahel-edu/backups/ /mnt/nas/sahel-edu/
```

### Restauration

```bash
# 1. Arrêter l'application
sudo systemctl stop sahel-edu.service

# 2. Restaurer la base (écrase la base actuelle !)
sudo docker compose -f /opt/sahel-edu/docker/docker-compose.yml \
  exec -T -e MYSQL_PWD="$(sudo sed -n 's/^DB_PASSWORD=//p' /opt/sahel-edu/docker/.env)" mysql \
  mysql -uroot -e "DROP DATABASE IF EXISTS school_management; CREATE DATABASE school_management;"
zcat /srv/sahel-edu/backups/db/db-AAAAMMJJ-HHMM.sql.gz | \
  sudo docker compose -f /opt/sahel-edu/docker/docker-compose.yml \
  exec -T -e MYSQL_PWD="$(sudo sed -n 's/^DB_PASSWORD=//p' /opt/sahel-edu/docker/.env)" mysql \
  mysql -uroot

# 3. Restaurer les fichiers
sudo tar xzf /srv/sahel-edu/backups/uploads/uploads-AAAAMMJJ-HHMM.tar.gz -C /srv/sahel-edu/uploads

# 4. Redémarrer
sudo systemctl start sahel-edu.service
```

> Le dump est produit avec `--databases` : il contient `CREATE DATABASE` et
> `USE`. L'étape 2 peut donc se limiter à `zcat ... | mysql -uroot`.

### Test de restauration

À faire **au moins une fois** avant la mise en service :

```bash
# Sur une base jetable, pas sur la production
sudo docker compose -f /opt/sahel-edu/docker/docker-compose.yml \
  exec -T mysql mysql -uroot -e "CREATE DATABASE restauration_test;"
zcat /srv/sahel-edu/backups/db/db-*.sql.gz | head -c 50 >/dev/null   # l'archive est lisible
```

---

## 7. Mise à jour

```bash
cd /opt/sahel-edu
sudo ./deploy/update.sh
```

Le script sauvegarde d'abord, puis `git pull --ff-only`, reconstruit les images,
redémarre et vérifie la santé.

- Les migrations Flyway s'appliquent **au démarrage** du backend.
- Le volume `mysql_data` et `/srv/sahel-edu/uploads` ne sont jamais supprimés.
- ⚠️ Ne jamais lancer `docker compose down -v` : `-v` supprime les volumes, donc
  la base de données.

### Retour arrière

```bash
cd /opt/sahel-edu
sudo git log --oneline -5
sudo git checkout <commit-precedent>
sudo docker compose -f docker/docker-compose.yml build && sudo docker compose -f docker/docker-compose.yml up -d
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
3. Connexion `admin` avec le mot de passe affiché à l'installation
4. **Changer le mot de passe admin** (Profil)
5. Créer une classe, une matière, un élève de test
6. Menu **Cours et ressources** : créer un cours PDF, le publier
7. Se connecter avec l'élève de test : le cours apparaît, s'ouvre, se télécharge
8. Vérifier qu'un élève d'une autre classe ne le voit pas
9. `sudo systemctl start sahel-edu-backup.service` → archives présentes dans `/srv/sahel-edu/backups`
10. `https://` → cadenas valide, HTTP redirigé

La checklist fonctionnelle détaillée du module est en §7 de
[COURS_VIDEO_PDF.md](COURS_VIDEO_PDF.md).
