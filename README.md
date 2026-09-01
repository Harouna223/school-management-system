# School Management System

Système complet de gestion scolaire : élèves, enseignants, classes, emplois du temps, présences, notes, examens, bulletins et délibérations, paiements, bibliothèque, RH, messagerie interne, espace parent (bulletins, absences, notifications WhatsApp / e-mail / SMS) et journal d'audit.

## Stack technique

| Couche | Technologies |
|---|---|
| Frontend | React 18, Vite, MUI, Tailwind CSS, Redux Toolkit, Formik + Yup, Recharts, Axios |
| Backend | Java 17, Spring Boot 3.2.5, Spring Security (JWT), Spring Data JPA, MapStruct, Swagger |
| Base de données | MySQL 8 (utf8mb4) |
| Déploiement | Docker Compose + Nginx |

## Démarrage rapide

### Option 1 — Docker (recommandé)

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

| Service | Accès |
|---|---|
| Frontend | http://localhost |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| MySQL | localhost:3306 (root / root) |

Connexion : `admin` / `Admin@123` (modifiable via `ADMIN_PASSWORD`).

### Option 2 — Développement local

1. **Base de données** : exécuter `database/schema.sql` dans MySQL (ou laisser `ddl-auto: update` créer le schéma).

2. **Backend** (`backend-springboot/`) :
   ```bash
   mvn spring-boot:run
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