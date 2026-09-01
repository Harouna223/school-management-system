# Diagrammes — School Management System

Diagrammes (format Mermaid) de l'architecture, du modèle de données et des flux clés.
Rendu sur [mermaid.live](https://mermaid.live) ou dans tout éditeur compatible.

---

## 1. Architecture générale (conteneurs)

```mermaid
flowchart LR
  U[Utilisateurs\nadmin / direction / comptable / secrétaire\nenseignants / parents / élèves]
  N[Nginx :80]
  F[Frontend React.js + Vite\nMUI + Tailwind + Redux Toolkit]
  B[Backend Spring Boot :8080\nSecurity JWT - API REST]
  M[(MySQL 8.4\nschool_management)]
  BK[db-backup\nmysqldump quotidien\nrétention 14 j]
  W[WhatsApp Cloud API / Twilio]
  S[SMTP - Envoi d'e-mails]
  T[Twilio SMS]

  U --> N --> F -->|/api| B
  B --> M
  B --> W
  B --> S
  B --> T
  M --> BK
```

## 2. Modèle de données (entités principales)

```mermaid
erDiagram
  USERS ||--o{ USER_ROLES : possède
  ROLES ||--o{ USER_ROLES : "assigné à"
  ROLES ||--o{ ROLE_PERMISSIONS : détient
  PERMISSIONS ||--o{ ROLE_PERMISSIONS : "associé à"

  USERS ||--o{ PARENTS : "compte parent"
  USERS ||--o{ STUDENTS : "compte élève"
  USERS ||--o{ REFRESH_TOKENS : ""
  USERS ||--o{ NOTIFICATIONS : reçoit
  USERS ||--o{ MESSAGES : "échange (expéditeur)"
  USERS ||--o{ AUDIT_LOGS : "auteur des actions"

  PARENTS ||--o{ STUDENTS : "parent/tuteur de"

  LEVELS ||--o{ CLASSES : "composée de"
  SECTIONS ||--o{ CLASSES : ""
  ROOMS ||--o{ CLASSES : ""
  CLASSES ||--o{ STUDENTS : inscrit
  CLASSES ||--o{ EXAMS : planifié
  CLASSES ||--o{ SCHEDULES : "emploi du temps"
  CLASSES ||--o{ ATTENDANCES : "pointages"

  SUBJECTS ||--o{ SUBJECT_ASSIGNMENTS : "affecté à"
  TEACHERS ||--o{ SUBJECT_ASSIGNMENTS : enseigne
  TEACHERS ||--o{ CONTRACTS : ""
  TEACHERS ||--o{ PAYROLLS : "paie"
  TEACHERS ||--o{ LEAVES : "congés"
  TEACHERS ||--o{ TEACHER_ATTENDANCES : "présences"

  EXAMS ||--o{ GRADES : "notes"
  STUDENTS ||--o{ GRADES : ""
  EXAMS ||--o{ SCHEDULES : ""
  STUDENTS ||--o{ BULLETINS : ""
  BULLETINS ||--o{ BULLETIN_SUBJECTS : "" : "agrégats par matière"

  FEE_TYPES ||--o{ INVOICES : ""
  STUDENTS ||--o{ INVOICES : ""
  INVOICES ||--o{ PAYMENTS : "encaissé"
  USERS ||--o{ PAYMENTS : "enregistré par"
  EXPENSE_CATEGORIES ||--o{ EXPENSES : ""
  USERS ||--o{ EXPENSES : "validé par"

  BOOKS ||--o{ BORROWINGS : "emprunté"
  STUDENTS ||--o{ BORROWINGS : ""

  ANNOUNCEMENTS }o--o{ ROLES : "ciblée"
```

## 3. Authentification (flux JWT)

```mermaid
sequenceDiagram
  participant F as Frontend
  participant B as Backend (Security)
  participant DB as MySQL

  F->>B: POST /api/auth/login {username, password}
  B->>B: Vérification BCrypt + statut
  B->>DB: Chargement utilisateur + rôles + permissions
  B-->>F: 200 {accessToken (JWT), refreshToken}
  F->>B: GET /api/... Authorization: Bearer <JWT>
  B->>B: JwtAuthenticationFilter (validation signature + exp)
  B-->>F: 200 réponse JSON standardisée
  Note over F,B: JWT expiré (401) → POST /api/auth/refresh avec refreshToken
```

## 4. Notification parents (bulletin / absence)

```mermaid
sequenceDiagram
  participant A as Admin / Secrétaire
  participant B as Backend
  participant P as Parent (espace parent)
  participant W as WhatsApp / E-mail / SMS

  A->>B: POST /api/exams/class/{id}/deliberation?term=T1
  B->>B: Calcul moyennes + classement + décision
  B-->>P: Notification in-app "Bulletin disponible"
  B-->>W: Message WhatsApp + e-mail + SMS (si configurés)

  A->>B: POST /api/attendances (élève ABSENT/LATE)
  B-->>P: Notification in-app "Absence signalée"
  B-->>W: Message WhatsApp + e-mail + SMS (si configurés)
```

## 5. Sauvegarde automatique

```mermaid
flowchart TD
  BK[Conteneur db-backup] -->|chaque 24 h| D[mysqldump --single-transaction\n--routines --triggers]
  D --> F1[backup-AAAAMMJJ-HHMM.sql]
  F1 --> V[Volume docker backup_data]
  V --> R[find -mtime +14 -delete\nrétention 14 jours]
```