# Guide d'installation

## 1. Prérequis

### Développement local
- **Java 17+** (testé avec JDK 21 Temurin)
- **Maven 3.9+**
- **Node.js 18+** et npm
- **MySQL 8+** (ou compatible, ex. MariaDB)

### Déploiement Docker
- **Docker** avec Docker Compose v2

## 2. Installation avec Docker (recommandé)

```bash
# 1. Créer le fichier d'environnement (optionnel, valeurs par défaut fournies)
cp docker/.env.example docker/.env

# 2. Construire et démarrer tous les services
docker compose -f docker/docker-compose.yml up -d --build

# 3. Vérifier l'état
docker compose -f docker/docker-compose.yml ps
```

Le schéma de base de données (`database/schema.sql`) est exécuté automatiquement au premier démarrage de MySQL.

**Arrêt** : `docker compose -f docker/docker-compose.yml down` (ajouter `-v` pour supprimer aussi les volumes).

### Sauvegarde automatique

Le service `db-backup` effectue un dump MySQL quotidien dans le volume `backup_data`
(conservation : 14 jours) :

```bash
# Restaurer une sauvegarde dans le conteneur MySQL
docker compose -f docker/docker-compose.yml exec -T mysql \
  mysql -uroot -p"$(grep DB_PASSWORD docker/.env | cut -d= -f2)" school_management < backup-XXXX.sql

# Restaurer depuis le volume des sauvegardes
docker compose -f docker/docker-compose.yml run --rm db-backup \
  sh -c 'cat /backups/backup-XXXX.sql | mysql -h mysql -uroot -p"$DB_PASSWORD" school_management'
```

> Astuce : récupérez le fichier `.sql` depuis le volume avec :
> `docker run --rm -v school-management_backup_data:/backups -v $PWD:/out alpine sh -c "cp /backups/backup-*.sql /out/"`

## 3. Installation en développement

### 3.1 Base de données

```sql
mysql -u root -p < database/schema.sql
```

Ou laisser Hibernate créer les tables : le backend démarre avec `ddl-auto: update` et l'URL JDBC contient `createDatabaseIfNotExist=true`.

### 3.2 Backend

```bash
cd backend-springboot
mvn spring-boot:run
```

Variables d'environnement (toutes optionnelles) :

| Variable | Défaut | Description |
|---|---|---|
| `DB_HOST` | `localhost` | Hôte MySQL |
| `DB_PORT` | `3306` | Port MySQL |
| `DB_NAME` | `school_management` | Nom de la base |
| `DB_USERNAME` | `root` | Utilisateur MySQL |
| `DB_PASSWORD` | `root` | Mot de passe MySQL |
| `JWT_SECRET` | secret de dev | Clé JWT (Base64) — **à changer en production** |
| `SERVER_PORT` | `8080` | Port HTTP du backend |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Origines autorisées |
| `UPLOAD_DIR` | `./uploads` | Dossier des fichiers téléversés |
| `ADMIN_PASSWORD` | `Admin@123` | Mot de passe initial de l'admin |

Swagger UI : http://localhost:8080/swagger-ui.html

### 3.3 Frontend

```bash
cd frontend-react
npm install
npm run dev
```

- Application : http://localhost:5173
- Le proxy Vite redirige `/api` et `/uploads` vers http://localhost:8080

### 3.4 Build de production

```bash
cd frontend-react && npm run build   # -> dist/
cd backend-springboot && mvn clean package
```

## 4. Dépannage

| Problème | Solution |
|---|---|
| `Access denied for user 'root'` | Vérifier `DB_USERNAME`/`DB_PASSWORD` ; par défaut `root`/`root` |
| Le login `admin` échoue | Vérifier que l'utilisateur n'a pas été inséré avec un hash invalide par schema.sql (retirer l'INSERT users concerné pour laisser `DataInitializer` recréer l'admin) |
| Port 80 occupé | Changer `FRONTEND_PORT` dans `docker/.env` |
| CORS en dev | Le proxy Vite évite le CORS ; vérifier `CORS_ORIGINS` si accès direct |
| Fichiers reçus introuvables | Vérifier `UPLOAD_DIR` ; en Docker, volume `uploads_data` persiste dans `/app/uploads` |