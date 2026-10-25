# Sahel Edu

Système complet de gestion scolaire : élèves, enseignants, classes, emplois du temps, présences, notes, examens, bulletins et délibérations, paiements, bibliothèque, RH, messagerie interne, espace parent (bulletins, absences, notifications WhatsApp / e-mail / SMS) et journal d'audit.

## Stack technique

| Couche | Technologies |
|---|---|
| Frontend | React 18, Vite, MUI, Tailwind CSS, Redux Toolkit, Formik + Yup, Recharts, Axios |
| Backend | Java 21, Spring Boot 3.2.5, Spring Security (JWT), Spring Data JPA, MapStruct, Swagger |
| Base de données | MySQL 8 (utf8mb4) — schéma géré par **Flyway** (`backend-springboot/src/main/resources/db/migration/`) |
| Déploiement | Docker Compose + Nginx |

## Migrations de schéma (Flyway — Phase 7)

Hibernate ne modifie **plus jamais** le schéma (`ddl-auto: validate` : vérification seule).
Le schéma est la propriété exclusive de Flyway :

- `V1__baseline.sql` — schéma complet au 2026-10-03 (baseline, appliqué une seule fois sur une base vierge).
- Toute évolution = un nouveau fichier `Vn__description.sql` (ex : `V2__add_index_users_email.sql`).
- Au démarrage, l'application applique automatiquement les migrations en attente
  (`baseline-on-migrate: true` : une base existante reçoit un historique SANS rejouer V1).
- Historique consultable : table `flyway_schema_history`.
- **Règle d'or** : sauvegarder la base avant chaque déploiement
  (`mysqldump --single-transaction --databases school_management`).

## Démarrage rapide

### Option 1 — Docker (recommandé)

> **Prérequis :** Docker Desktop démarré. Le port MySQL est défini dans `docker/.env` (`DB_PORT`).
> Sur cette machine : **3307** (un ancien MySQL local 5.6 occupe déjà le 3306).

```bash
# 1. Créer le fichier d'environnement puis y définir JWT_SECRET (obligatoire)
cp docker/.env.example docker/.env
openssl rand -base64 48   # coller la valeur obtenue dans docker/.env, ligne JWT_SECRET=

# 2. Construire et démarrer
docker compose -f docker/docker-compose.yml up -d --build
```

> ⚠️ **`JWT_SECRET` est obligatoire.** Sa valeur par défaut a été retirée du dépôt et
> `REQUIRE_SECURE_SECRETS=true` par défaut fait **échouer le démarrage** du backend tant que le
> secret est absent, vide, resté celui du dépôt ou plus court que 32 octets décodés.

| Service | Accès |
|---|---|
| Frontend | http://localhost |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| MySQL | localhost:`DB_PORT` (root / root) — voir `docker/.env` |

Connexion : `admin` / `Admin@123` (modifiable via `ADMIN_PASSWORD`).

### Option 2 — Développement local

1. **Base de données** : rien à créer. Flyway est la source de vérité du schéma et le crée
   intégralement (`V1__baseline.sql` puis `V2..V10`) au premier démarrage du backend.
   `database/schema.sql` est un **héritage** : il n'est plus exécuté et ne correspond plus au
   schéma courant — ne pas l'utiliser.
   Astuce : utiliser le MySQL Docker sur un autre port ne change rien à votre MySQL local :
   ```bash
   docker compose -f docker/docker-compose.yml up -d mysql
   # MySQL joignable sur localhost:3307 (voir docker/.env)
   ```

2. **Backend** (`backend-springboot/`) :
   ```bash
   mvn spring-boot:run
   ```
   Si vous utilisez le MySQL Docker (port ≠ 3306) :
   ```bash
   # Windows (cmd) :
   set DB_PORT=3307 && mvn spring-boot:run
   # ou bash / Git Bash :
   DB_PORT=3307 mvn spring-boot:run
   ```
   Démarre sur `http://localhost:8080`.

3. **Frontend** (`frontend-react/`) :
   ```bash
   npm install
   npm run dev
   ```
   Démarre sur `http://localhost:5173` (proxy `/api` vers le backend).

## Identifiants par défaut

| Rôle | Utilisateur | Mot de passe |
|---|---|---|
| Super admin | `admin` | `Admin@123` |

L'admin est créé automatiquement au démarrage par `DataInitializer` (s'il n'existe pas). Les autres comptes (directeur, comptable, secrétaire, enseignant, parent, élève) sont créés depuis l'interface d'administration des utilisateurs.

## Documentation

- [Installation](docs/INSTALLATION.md) — prérequis et procédure détaillée
- [Architecture](docs/ARCHITECTURE.md) — structure du projet et fonctionnement interne
- [API](docs/API.md) — liste des endpoints
- [Diagrammes](docs/DIAGRAMMES.md) — architecture, modèle de données, flux (UML/Mermaid)