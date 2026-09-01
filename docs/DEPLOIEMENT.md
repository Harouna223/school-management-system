# SMS — School Management System
## Guide complet de déploiement et d'adoption multi-écoles

---

## Première partie : Déploiement pour une école

### 1. Prérequis techniques

**Serveur (minimum recommandé)**
- **CPU** : 2 cœurs
- **RAM** : 4 Go
- **Stockage** : 20 Go (SSD recommandé)
- **OS** : Ubuntu 22.04 / 24.04 LTS ou Debian 12
- **Docker** : 24+ et Docker Compose v2 (installés)
- **Domaine** : un nom de domaine pointant vers votre serveur (ex. `ecole-moderne-ndjamena.school`)
- **Ports ouverts** : 80 (HTTP) et 443 (HTTPS)

**Sur votre machine de développement**
- Git
- Un éditeur de code

---

### 2. Préparer le serveur

```bash
# Connexion SSH
ssh root@<votre-serveur>

# Mise à jour du système
apt update && apt upgrade -y

# Installation de Docker (si pas déjà installé)
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Vérification
docker --version
docker compose version
```

---

### 3. Récupérer l'application

```bash
# Créer un dossier pour l'application
mkdir /opt/sms && cd /opt/sms

# Copier les fichiers du projet (via git ou SCP)
# Option A : dépôt Git
git clone https://github.com/votre-org/school-management-system.git .

# Option B : transfert depuis votre machine
# Sur votre machine locale :
# scp -r /chemin/vers/school-management-system/* root@<serveur>:/opt/sms/
```

---

### 4. Configurer l'environnement

```bash
cd /opt/sms

# Copier le fichier d'environnement
cp docker/.env.example docker/.env
```

Éditer `docker/.env` avec les valeurs de votre école :

```bash
nano docker/.env
```

```env
# ──────────────────────────────────────────────
# Base de données
# ──────────────────────────────────────────────
DB_PASSWORD=UnMotDePasseFortEtUnique
DB_NAME=school_management
DB_PORT=3306

# ──────────────────────────────────────────────
# Application
# ──────────────────────────────────────────────
JWT_SECRET=<GÉNÉRER UNE CLÉ UNIQUE>
ADMIN_PASSWORD=MotDePasseAdminFort
SCHOOL_NAME=École Moderne de N'Djaména
CORS_ORIGINS=http://ecole-moderne-ndjamena.school,https://ecole-moderne-ndjamena.school

# Port d'exposition du frontend
FRONTEND_PORT=80
```

**Générer une clé JWT sécurisée :**
```bash
openssl rand -base64 64
```

Copier la sortie dans `JWT_SECRET`.

**IMPORTANT** : Changez `ADMIN_PASSWORD` (l'admin par défaut a le login `admin`).

---

### 5. Configurer le nom de domaine et le SSL

**Option A — Avec un reverse proxy externe (Nginx / Caddy) — RECOMMANDÉE**

Le frontend écoute sur le port 80. Vous placez un reverse proxy devant pour gérer le SSL.

Exemple avec Nginx :

```nginx
# /etc/nginx/sites-available/ecole-moderne-ndjamena.school
server {
    listen 443 ssl;
    server_name ecole-moderne-ndjamena.school;

    ssl_certificate /etc/letsencrypt/live/ecole-moderne-ndjamena.school/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/ecole-moderne-ndjamena.school/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 10m;
    }
}
```

Obtenir le certificat SSL avec Let's Encrypt :

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d ecole-moderne-ndjamena.school
```

**Option B — HTTPS direct sur le conteneur frontend**

Modifier `docker/nginx.conf` pour ajouter SSL, puis monter les certificats. Moins pratique.

---

### 6. Configurer les services optionnels

**WhatsApp (notification des parents)**
- Créez un compte WhatsApp Business API (via Meta Cloud API ou Twilio).
- Renseignez les variables dans `docker/.env` :
  ```
  WA_ENABLED=true
  WA_PROVIDER=meta
  WA_META_TOKEN=<votre-token>
  WA_META_PHONE_ID=<votre-phone-id>
  WA_COUNTRY_CODE=235  # indicatif du pays (Tchad : 235, Mali : 223, Cameroun : 237)
  ```

**SMS (notification Twilio)**
```env
SMS_ENABLED=true
SMS_TWILIO_SID=<votre-sid>
SMS_TWILIO_TOKEN=<votre-token>
SMS_TWILIO_FROM=<votre-numero>
```

**Email (SMTP)**
```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=votre-email@gmail.com
MAIL_PASSWORD=<mot-de-passe-d-application>
```

---

### 7. Lancer l'application

```bash
cd /opt/sms

# Construire et démarrer tous les services
docker compose -f docker/docker-compose.yml up -d --build

# Vérifier l'état
docker compose -f docker/docker-compose.yml ps

# Voir les logs
docker compose -f docker/docker-compose.yml logs -f backend
```

Les conteneurs se lancent dans cet ordre :
1. `mysql` — base de données, initialisée avec le schéma
2. `backend` — API Spring Boot, attend que MySQL soit prêt
3. `frontend` — Nginx servant le frontend React
4. `db-backup` — sauvegarde automatique quotidienne

---

### 8. Première connexion

1. Ouvrir `https://ecole-moderne-ndjamena.school` dans un navigateur.
2. Page de connexion : email `admin` / mot de passe = celui défini dans `ADMIN_PASSWORD`.
3. Ouvrir **Paramètres** → onglet **Établissement** :
   - Compléter le nom, adresse, téléphone, email, slogan, site web.
   - Uploader le **logo** de l'école (PNG/JPEG, carré de préférence).
4. Configurer l'**année scolaire** courante.
5. Créer les **niveaux** (6ème, 5ème, 4ème…), **sections** (Scientifique, Littéraire…), **salles**.
6. Créer les **classes**.
7. Ajouter les **matières**.
8. Créer les **enseignants**.
9. Ajouter les **élèves** (manuellement ou via import Excel).
10. Créer les **types de frais** (scolarité, cantine, etc.).

---

### 9. Sauvegarde et restauration

**Sauvegarde automatique** : le conteneur `db-backup` effectue un dump SQL quotidien à 00:00, conservé 14 jours. Les fichiers sont dans le volume `backup_data` :
```bash
docker exec school-db-backup ls -la /backups
```

**Sauvegarde manuelle** : depuis l'interface → Paramètres → Sauvegarde → Télécharger.

**Restauration** : Paramètres → Sauvegarde → Restaurer (fichier `.sql` généré par l'application uniquement).

**Sauvegarde complète (recommandé)** : en plus du dump SQL, sauvegardez le volume `uploads_data` (photos, logo) :
```bash
docker run --rm -v uploads_data:/data -v /backup:/backup alpine tar czf /backup/uploads-$(date +%Y%m%d).tar.gz -C /data .
```

---

### 10. Mise à jour de l'application

```bash
cd /opt/sms

# Récupérer les nouvelles sources (via git)
git pull origin main

# Reconstruire et redémarrer
docker compose -f docker/docker-compose.yml up -d --build

# Vérifier
docker compose -f docker/docker-compose.yml ps
```

---

### 11. Surveillance et maintenance

**Logs**
```bash
# Suivre les logs du backend
docker compose -f docker/docker-compose.yml logs -f --tail 100 backend

# Tous les services
docker compose -f docker/docker-compose.yml logs -f
```

**Redémarrage propre**
```bash
docker compose -f docker/docker-compose.yml down
docker compose -f docker/docker-compose.yml up -d
```

**Nettoyage des images obsolètes**
```bash
docker system prune -f
```

---

## Deuxième partie : Adopter l'application pour plusieurs écoles

### Stratégie recommandée : une instance Docker par école

Chaque école obtient sa propre copie de l'application avec sa propre base de données, ses propres fichiers et son propre nom de domaine.

#### Arborescence sur le serveur

```
/opt/sms/
├── ecole-moderne-ndjamena/
│   ├── docker/
│   │   └── .env
│   └── docker-compose.override.yml  (optionnel, pour changer les ports)
├── lycee-francais-bangui/
│   ├── docker/
│   │   └── .env
│   └── docker-compose.override.yml
└── groupe-scolaire-bamako/
    ├── docker/
    │   └── .env
    └── docker-compose.override.yml
```

#### Procédure pour ajouter une école

```bash
# 1. Copier les sources
cd /opt/sms
cp -r school-management-system ecole-moderne-ndjamena
cd ecole-moderne-ndjamena

# 2. Copier .env.example → .env
cp docker/.env.example docker/.env

# 3. Modifier .env avec les données de l'école
#    - SCHOOL_NAME, DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD
#    - FRONTEND_PORT=81 (port différent de la première école ou du reverse proxy)

# 4. Créer un docker-compose.override.yml pour isoler les noms de conteneurs
```

**docker-compose.override.yml** pour éviter les conflits de noms de conteneurs :

```yaml
name: sms-ecole-moderne-ndjamena

services:
  mysql:
    container_name: sms-ndjamena-mysql
    ports:
      - "3307:3306"   # changer le port MySQL externe

  backend:
    container_name: sms-ndjamena-backend
    ports:
      - "8081:8080"   # changer le port backend externe

  frontend:
    container_name: sms-ndjamena-frontend
    ports:
      - "81:80"       # changer le port frontend externe

  db-backup:
    container_name: sms-ndjamena-db-backup
```

#### Configuration du reverse proxy

Exemple Nginx avec plusieurs écoles :

```nginx
# /etc/nginx/sites-available/ecoles
server {
    listen 443 ssl;
    server_name ecole-moderne-ndjamena.school;
    ssl_certificate ...;
    ssl_certificate_key ...;

    location / {
        proxy_pass http://127.0.0.1:81;  # port 81 = FRONTEND_PORT de l'école
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8081;  # backend sur 8081
    }
}

server {
    listen 443 ssl;
    server_name lycee-francais-bangui.school;
    ssl_certificate ...;
    ssl_certificate_key ...;

    location / {
        proxy_pass http://127.0.0.1:82;  # port 82
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8082;
    }
}
```

#### Script d'ajout d'une nouvelle école

```bash
#!/bin/bash
# /opt/sms/scripts/ajouter-ecole.sh

set -e

if [ -z "$1" ]; then
  echo "Usage : $0 <nom-ecole>"
  echo "Exemple : $0 lycee-francais-bangui"
  exit 1
fi

ECOLE="$1"
PORT_FRONT=80
PORT_BACK=8080
PORT_MYSQL=3306

# Trouver le prochain port disponible
for i in $(seq 80 99); do
  if ! ss -tlnp | grep -q ":$i "; then
    PORT_FRONT=$i
    break
  fi
done

for i in $(seq 8080 8099); do
  if ! ss -tlnp | grep -q ":$i "; then
    PORT_BACK=$i
    break
  fi
done

for i in $(seq 3306 3315); do
  if ! ss -tlnp | grep -q ":$i "; then
    PORT_MYSQL=$i
    break
  fi
done

echo "→ Création de l'instance $ECOLE (front:$PORT_FRONT back:$PORT_BACK mysql:$PORT_MYSQL)"

cd /opt/sms
cp -r school-management-system "$ECOLE"
cd "$ECOLE"

cp docker/.env.example docker/.env

# Générer les secrets
JWT_SECRET=$(openssl rand -base64 64)
DB_PASS=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9')

# Configurer .env
sed -i "s/DB_PASSWORD=.*/DB_PASSWORD=$DB_PASS/" docker/.env
sed -i "s/JWT_SECRET=.*/JWT_SECRET=$JWT_SECRET/" docker/.env
sed -i "s/ADMIN_PASSWORD=.*/ADMIN_PASSWORD=Admin@123/" docker/.env
sed -i "s/FRONTEND_PORT=.*/FRONTEND_PORT=$PORT_FRONT/" docker/.env
sed -i "s/DB_PORT=.*/DB_PORT=$PORT_MYSQL/" docker/.env
sed -i "s|CORS_ORIGINS=.*|CORS_ORIGINS=http://$ECOLE.school,https://$ECOLE.school|" docker/.env

# Créer docker-compose.override.yml
cat > docker-compose.override.yml << EOF
name: sms-$ECOLE
services:
  mysql:
    container_name: sms-$ECOLE-mysql
    ports:
      - "$PORT_MYSQL:3306"
  backend:
    container_name: sms-$ECOLE-backend
    ports:
      - "$PORT_BACK:8080"
  frontend:
    container_name: sms-$ECOLE-frontend
    ports:
      - "$PORT_FRONT:80"
  db-backup:
    container_name: sms-$ECOLE-db-backup
EOF

echo "→ Lancement de l'instance..."
docker compose -f docker/docker-compose.yml up -d --build

echo "✅ Instance $ECOLE déployée"
echo "   Frontend : http://localhost:$PORT_FRONT"
echo "   Backend  : http://localhost:$PORT_BACK"
echo "   MySQL    : localhost:$PORT_MYSQL"
echo "   Admin    : admin / Admin@123"
echo "   ⚠️  Changez le mot de passe admin après la première connexion !"
```

---

### Alternative : Multi-tenant complet (refactoring lourd)

Si vous gérez des dizaines d'écoles et souhaitez une seule instance, un refactoring multi-tenant est nécessaire :

1. **Base de données** : ajouter une table `schools` et un champ `school_id` sur toutes les tables.
2. **Backend** : injecter l'école courante via le sous-domaine (`ecole1.votreplateforme.com`), filtrer toutes les requêtes par `school_id`. Utiliser Spring Filter ou `@TenantFilter`.
3. **Frontend** : adapter les endpoints pour inclure l'école.
4. **Documents** : uploads isolés par dossier `/uploads/{school_id}/`.

**Ce n'est pas recommandé pour commencer** — la complexité et le risque de régression sont élevés. La stratégie une-instance-par-école est plus simple, plus fiable et facile à maintenir.

---

### Résumé des recommandations

| Situation | Recommandation |
|---|---|
| 1 à 20 écoles | Une instance Docker par école (copie du projet + .env + override) |
| + de 20 écoles | Envisager le multi-tenant (refactoring) ou une architecture Kubernetes |
| Données isolées | Instance par école = isolation totale |
| Mutualisation | Multi-tenant = une seule infrastructure, mais plus complexe |
| Budget | Instance par école = autant de serveurs. Multi-tenant = 1 serveur |

---

## Troisième partie : Checklist de mise en production

Avant d'ouvrir l'application aux utilisateurs :

- [ ] **Mot de passe admin** changé (pas `Admin@123`)
- [ ] **Clé JWT** générée (pas la valeur par défaut)
- [ ] **Mot de passe MySQL** fort (pas `root`)
- [ ] **HTTPS** activé (certificat SSL valide)
- [ ] **CORS_ORIGINS** configuré avec le domaine réel
- [ ] **Logo** de l'école uploadé
- [ ] **Paramètres établissement** remplis (nom, adresse, téléphone, email)
- [ ] **Année scolaire** configurée
- [ ] **Sauvegarde automatique** vérifiée
- [ ] **WhatsApp** testé en mode simulation (désactivé) puis activé
- [ ] **SMTP** configuré et testé (envoi d'email)
- [ ] **Ports non exposés** : le backend (8080) ne doit pas être accessible depuis l'extérieur (reverse proxy obligatoire)
- [ ] **Limite de fichiers** : 5 Mo par upload, 10 Mo par requête (configuré)