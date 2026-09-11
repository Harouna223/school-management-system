# Architecture

## Vue d'ensemble

```
                    ┌────────────────────────────────────────────────┐
                    │                Frontend React                  │
                    │  Vite + MUI + Tailwind + Redux Toolkit         │
                    │  /src/pages · /src/components · /src/redux     │
                    └───────────────┬────────────────────────────────┘
                                    │  HTTP/JSON (Axios, JWT Bearer)
                                    ▼
                    ┌────────────────────────────────────────────────┐
                    │                Backend Spring Boot             │
                    │  Controllers → Services → Repositories (JPA)   │
                    │  Spring Security + JWT · MapStruct · Swagger   │
                    └───────────────┬────────────────────────────────┘
                                    │  JDBC
                                    ▼
                    ┌────────────────────────────────────────────────┐
                    │                  MySQL 8                        │
                    │  school_management (database/schema.sql)       │
                    └────────────────────────────────────────────────┘
```

## Structure du projet

```
school-management-system/
├── database/
│   └── schema.sql              # Schéma complet + données initiales
├── backend-springboot/
│   └── src/main/java/com/school/
│       ├── config/             # AppProperties, OpenApiConfig, WebConfig, DataInitializer
│       ├── controller/         # 28 contrôleurs REST (/api/...)
│       ├── dto/                # DTOs request/response (validation Jakarta)
│       ├── entity/             # 27 entités JPA
│       ├── enums/              # Rôles, statuts, types...
│       ├── exception/          # Exceptions métier + GlobalExceptionHandler
│       ├── mapper/             # MapStruct (entité ↔ DTO)
│       ├── repository/         # Spring Data JPA
│       ├── security/           # JwtService, filtres, SecurityConfig, UserDetails
│       ├── service/            # 21 services métier
│       └── utils/              # CodeGenerator, SecurityUtils
│   └── src/main/resources/
│       ├── application.yml     # Configuration (env vars)
│       └── templates/          # Modèles PDF (bulletins, recus, attestations)
├── frontend-react/
│   └── src/
│       ├── api/                # client Axios (refresh token) + endpoints
│       ├── components/         # Sidebar, Navbar, DataTable, StatCard...
│       ├── context/            # ThemeContext (dark/light)
│       ├── hooks/              # useToast, useFetch, usePagination
│       ├── layouts/            # DashboardLayout, AuthLayout
│       ├── pages/              # 20 pages (élèves, notes, paiements...)
│       ├── redux/              # store + slices (auth, theme, toast)
│       ├── routes/             # Router + guards (rôles)
│       ├── styles/             # index.css, thèmes MUI
│       └── utils/              # format.js, auth.js
├── docker/
│   ├── docker-compose.yml      # mysql + backend + frontend
│   ├── Dockerfile.backend      # multi-stage maven → JRE
│   ├── Dockerfile.frontend     # multi-stage node → nginx
│   ├── nginx.conf              # SPA + proxy /api et /uploads
│   └── .env.example
└── docs/
```

## Fonctionnement clé

### Sécurité (JWT)
- `POST /api/auth/login` → `accessToken` (1 h) + `refreshToken` (7 j).
- Filtre `JwtAuthenticationFilter` valide le Bearer sur chaque requête.
- `POST /api/auth/refresh` renouvelle l'access token (refresh token stocké haché en base, rotation + révocation).
- Autorisations par rôle via `@PreAuthorize("hasRole('...')")` ; les permissions granulaires sont définies en base (table `permissions`) et vérifiées par `PermissionEvaluator`-style dans les services.
- Journalisation systématique des actions sensibles dans `audit_logs` (qui, quoi, quand, IP).

### Réponses API
Toute réponse est enveloppée : `{ success, message, data, path, timestamp }`.
Les listes paginées renvoient : `{ content, page, size, totalElements, totalPages, first, last }`.
Les erreurs sont normalisées par `GlobalExceptionHandler` (404, 409, 400, 401, 403, 500).

### Sécurité de la couche data
- Requêtes à paramètres : Spring Data JPA (JPQL paramétré) ; pas de concaténation SQL → protection SQL injection.
- Mots de passe : BCrypt ; refresh tokens hachés (SHA-256).
- Fichiers : extension/vérification de contenu (photos JPG/PNG, PDF reçus max 5 Mo), stockage hors du contexte web, servis via contrôleur dédié.
- XSS : React échappe le contenu par défaut ; les entrées sont validées côté backend (Bean Validation `@Valid`).
- CSRF : API stateless avec JWT dans l'en-tête `Authorization` (jamais de cookie de session) — les jetons de session/topologie CSRF ne s'appliquent pas ; CORS restreint à `CORS_ORIGINS`.

### Données initiales
- `DataInitializer` crée le super admin (si absent), les rôles, les permissions et les data de référence (sections, niveaux, salles).
- `database/schema.sql` fournit le même jeu de données complet (exécuté par MySQL en Docker).

## Docker

- **mysql** : image officielle 8.4, volume persistant, init via `schema.sql` monté dans `/docker-entrypoint-initdb.d/`.
- **backend** : build multi-stage (Maven → JRE 21), santé vérifiée avant démarrage du frontend, volume pour les uploads.
- **frontend** : build multi-stage (Node → Nginx), le SPA fallback et le proxy `/api` sont gérés par `nginx.conf`.

## Vérifications qualité

```bash
cd backend-springboot && mvn compile        # compilation backend
cd frontend-react && npm run build          # build frontend (équivaut à typecheck+bundle)
```