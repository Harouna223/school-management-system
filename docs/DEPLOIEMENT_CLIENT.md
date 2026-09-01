# Déploiement SMS chez un client — Guide pas à pas

Ce guide couvre l'installation de l'application SMS (School Management System) sur
un serveur client avec Docker Compose : configuration, sécurisation, HTTPS,
sauvegardes, mise à jour et dépannage.

---

## ÉTAPE 1 — Prérequis du serveur client

| Ressource | Minimum | Recommandé |
|---|---|---|
| OS | Linux (Ubuntu 22.04/24.04 recommandé) ou Windows Server 2019+ | Ubuntu 24.04 LTS |
| RAM | 2 Go | 4 Go |
| CPU | 2 vCPU | 4 vCPU |
| Disque | 20 Go | 50 Go (SSD) |
| Docker | Docker Engine 24+ + Docker Compose v2 | Dernières versions |
| Réseau | IP fixe ou nom de domaine | Domaine + DNS pointé |

Ports requis (voir ÉTAPE 6 pour la sécurisation) :

- `80` — frontend (HTTP)
- `443` — HTTPS si activé
- `22` — SSH d'administration
- `3306`/`3307` — MySQL (INTERNE uniquement, jamais exposé)

### Installer Docker sur Ubuntu

```bash
sudo apt update && sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update && sudo apt install -y docker-ce docker-compose-plugin
sudo systemctl enable --now docker
```

Vérifier : `docker --version` et `docker compose version`.

---

## ÉTAPE 2 — Récupérer le code de l'application

**Option A — dépôt Git (recommandé pour les mises à jour) :**

```bash
cd /opt
sudo git clone <URL_DU_DEPOT> sms
sudo chown -R $(whoami) sms
cd sms
```

**Option B — copie directe du dossier** (via scp/rsync depuis le poste de dev) :

```bash
# Sur le poste de dev
scp -r school-management-system/ user@serveur:/opt/sms
```

> La structure attendue est `sms/docker/docker-compose.yml` et les dossiers
> `sms/backend-springboot`, `sms/frontend-react`, `sms/database`.

---

## ÉTAPE 3 — Configurer le fichier d'environnement (CRITIQUE)

```bash
cd /opt/sms
cp docker/.env.example docker/.env
nano docker/.env
```

### Valeurs à ABSOLUMENT changer

| Variable | Valeur recommandée | Pourquoi |
|---|---|---|
| `DB_PASSWORD` | Mot de passe fort (≥ 20 caractères) | Sécurité de la base |
| `JWT_SECRET` | `openssl rand -base64 48` | Secret de signature des sessions |
| `ADMIN_PASSWORD` | Mot de passe fort | Compte admin initial |
| `CORS_ORIGINS` | `https://votredomaine.com` | Autoriser le domaine réel |
| `SCHOOL_NAME` | Nom réel de l'établissement | Affiché dans l'application |
| `FRONTEND_PORT` | `80` | Port d'accès HTTP |

### Générer un secret JWT sécurisé

```bash
openssl rand -base64 48
```

### Variables optionnelles (notification parents)

```bash
WA_ENABLED=false            # WhatsApp Business API (Meta/Twilio)
SMS_ENABLED=false           # SMS Twilio
MAIL_HOST=smtp.gmail.com    # SMTP pour les e-mails
MAIL_USERNAME=
MAIL_PASSWORD=
```

> **Attention** : changer `JWT_SECRET` après une première mise en service déconnecte
> tous les utilisateurs existants (normal). Le faire **avant** la première connexion.

---

## ÉTAPE 4 — Démarrer l'application

```bash
cd /opt/sms
docker compose -f docker/docker-compose.yml up -d --build
```

### Vérifier le démarrage

```bash
docker compose -f docker/docker-compose.yml ps
# 4 services attendus : mysql (healthy), backend (healthy), frontend (healthy), db-backup
```

Attendre que `school-backend` devienne `healthy` (1 à 2 min au premier démarrage).
Au premier lancement :

- le schéma SQL (`database/schema.sql`) initialise la base ;
- Hibernate crée automatiquement les nouvelles tables (`settings`, `academic_years`,
  `faculties`, LMD, etc.) via `ddl-auto: update` ;
- `DataInitializer` crée le compte admin et les permissions.

### Accès initial

```
http://IP_DU_SERVEUR/       (ou http://domaine/)
```

- Identifiant : `admin`
- Mot de passe : celui défini dans `ADMIN_PASSWORD`

**Imposer le changement du mot de passe admin dès la première connexion.**

---

## ÉTAPE 5 — Vérifications de bon fonctionnement

```bash
# 1. Santé des conteneurs
docker compose -f docker/docker-compose.yml ps

# 2. Frontend répond
curl -s -o /dev/null -w "%{http_code}" http://localhost/

# 3. API login répond (le code 500 sur GET est normal : endpoint POST uniquement)
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/login

# 4. Vérifier la création de l'admin et des permissions
docker compose -f docker/docker-compose.yml exec mysql \
  mysql -uroot -p"$DB_PASSWORD" school_management -e "SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM permissions;"
```

### Journalisation

```bash
docker compose -f docker/docker-compose.yml logs -f backend
docker compose -f docker/docker-compose.yml logs -f frontend
```

---

## ÉTAPE 6 — Sécuriser le serveur (indispensable en production)

### 1. Pare-feu (UFW)

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

> **Ne JAMAIS ouvrir** les ports 8080 (backend) ni 3306/3307 (MySQL) vers l'extérieur.
> Le frontend Nginx fait office de reverse proxy vers le backend en interne.

### 2. HTTPS avec Let's Encrypt (recommandé)

Le plus simple : un reverse proxy Nginx sur la machine hôte, ou Caddy (HTTPS auto).

**Exemple avec Caddy (dossier `/etc/caddy/Caddyfile`) :**

```
domaine-client.com {
    reverse_proxy 127.0.0.1:80
}
```

Caddy génère et renouvelle automatiquement le certificat HTTPS.

**Exemple avec Nginx + certbot :**

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
sudo nano /etc/nginx/sites-available/sms
```

```nginx
server {
    server_name domaine-client.com;
    location / {
        proxy_pass http://127.0.0.1:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/sms /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d domaine-client.com
```

Puis mettre à jour `CORS_ORIGINS=https://domaine-client.com` dans `docker/.env`
et relancer : `docker compose -f docker/docker-compose.yml up -d`.

### 3. Sauvegarde des mots de passe

Stocker `DB_PASSWORD`, `JWT_SECRET` et `ADMIN_PASSWORD` dans un gestionnaire de
mots de passe — jamais dans le code ni dans les logs.

---

## ÉTAPE 7 — Sauvegardes et restauration

### Sauvegarde automatique (déjà en place)

Le service `db-backup` effectue un `mysqldump` quotidien dans le volume
`backup_data` (rétention : 14 jours).

### Sauvegarder aussi les fichiers uploadés (photos, reçus, documents)

```bash
# Planifier (cron) un tar du volume des uploads
sudo mkdir -p /var/backups/sms
sudo crontab -e
# Ajouter :
0 2 * * * docker run --rm -v school-management_uploads_data:/uploads -v /var/backups/sms:/out alpine sh -c "tar czf /out/uploads-$(date +\%F).tgz -C /uploads . && find /out -name 'uploads-*.tgz' -mtime +30 -delete"
```

### Sauvegarde complète (base + uploads) sur un serveur distant (optionnel)

```bash
# Extraire le dump quotidien vers la machine hôte
docker run --rm -v school-management_backup_data:/backups -v /var/backups/sms:/out \
  alpine sh -c "cp /backups/backup-*.sql /out/"
# Puis rsync vers un autre site/disque :
rsync -av /var/backups/sms/ user@autre-serveur:/backups/sms/
```

### Restaurer une sauvegarde SQL

```bash
# Depuis la machine hôte
docker compose -f docker/docker-compose.yml exec -T mysql \
  mysql -uroot -p"$DB_PASSWORD" school_management < /var/backups/sms/backup-XXXX.sql

# Depuis le volume des sauvegardes
docker compose -f docker/docker-compose.yml run --rm db-backup \
  sh -c 'cat /backups/backup-XXXX.sql | mysql -h mysql -uroot -p"$DB_PASSWORD" school_management'
```

> **Tester régulièrement la restauration** sur un serveur de test.

---

## ÉTAPE 8 — Mise à jour de l'application

```bash
cd /opt/sms

# 1. Sauvegarde de précaution
docker compose -f docker/docker-compose.yml exec -T mysql \
  mysql -uroot -p"$DB_PASSWORD" school_management -e "SELECT 'ok';" > /dev/null

# 2. Récupérer la nouvelle version
git pull                                # ou recopier le dossier

# 3. Reconstruire et redémarrer
docker compose -f docker/docker-compose.yml up -d --build

# 4. Vérifier
docker compose -f docker/docker-compose.yml ps
```

> Hibernate applique automatiquement les évolutions de schéma (`ddl-auto: update`).
> Pour les très grosses évolutions, faire une sauvegarde SQL complète avant.

---

## ÉTAPE 9 — Dépannage côté client

| Symptôme | Cause probable | Solution |
|---|---|---|
| Page blanche / rien ne s'affiche | Cache navigateur obsolète | `Ctrl+Shift+R` ou navigation privée |
| `admin` refusé | Mot de passe oublié | `ADMIN_PASSWORD` réinitialisé + rebuild, ou reset via base |
| Port 80 occupé | Autre service web | Changer `FRONTEND_PORT` dans `docker/.env` |
| Backend en `Restarting` | Secret JWT invalide / DB injoignable | `docker compose logs backend` pour lire l'erreur |
| WARNING secret JWT au démarrage | `JWT_SECRET` par défaut | Générer `openssl rand -base64 48` dans `docker/.env` |
| Uploads perdus après redémarrage | Volume `uploads_data` supprimé avec `down -v` | Ne jamais utiliser `-v` en production |
| WhatsApp/SMS non reçus | Variables non configurées | Vérifier `WA_ENABLED`, jetons Meta/Twilio |

---

## ÉTAPE 10 — Checklist de livraison

- [ ] `JWT_SECRET` remplacé par une valeur aléatoire
- [ ] `DB_PASSWORD` et `ADMIN_PASSWORD` forts et stockés dans un gestionnaire de mots de passe
- [ ] `CORS_ORIGINS` = domaine HTTPS réel
- [ ] HTTPS actif (certificat valide) et renouvellement automatique
- [ ] Pare-feu : seuls 22, 80, 443 ouverts
- [ ] Ports 8080 et 3306/3307 non exposés publiquement
- [ ] `docker compose ps` → les 4 services sont `healthy`
- [ ] Connexion `admin` + changement du mot de passe initial
- [ ] Création d'un établissement réel : paramètres, année scolaire, classes, matières
- [ ] Sauvegarde quotidienne opérationnelle (vérifier un dump récent dans `backup_data`)
- [ ] Restauration testée sur un serveur de test
- [ ] Sauvegarde des uploads planifiée (cron)
- [ ] Documentation remise au client : URL, identifiants, procédure de mise à jour
