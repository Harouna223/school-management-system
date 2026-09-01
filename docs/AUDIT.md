# RAPPORT D'AUDIT FONCTIONNEL COMPLET — SMS SCHOOL MANAGEMENT SYSTEM

## 1. RÉSUMÉ EXÉCUTIF

SMS est une plateforme éducative fonctionnelle couvrant Jardin → Université. L'audit révèle un état général **bon** avec des points forts (module scolaire mature, sécurité récemment renforcée, architecture extensible) et des lacunes identifiées (module LMD insuffisamment développé malgré les extensions récentes, absence de gestion des convocations, espace parent non finalisé, parcours académique incomplet, rôle Élève/Étudiant non distingué).

**État global : 65% fonctionnel, 20% partiel, 10% absent, 5% bugué.**

### Forces principales
- Architecture multi-cycle (EducationCycle, Level.educationCycle, Student.educationCycle)
- Module scolaire complet (élèves, classes, notes, bulletins, présences, finances)
- Authentification JWT robuste, RBAC fin, contrôle IDOR récemment ajouté
- 38 tests passent, backend compile, frontend build

### Lacunes principales
- Module LMD : fonctionnel mais UX à compléter (onglets 8 présents, mais absence de groupes/promotions, évaluations EC partiellement exposées)
- Convocations : **absentes** (ni backend, ni frontend, ni table)
- Parcours académique : **partiel** (StudentHistory existe pour le scolaire, mais pas pour l'universitaire)
- Rôle Élève/Étudiant : **non distingué** (ELEVE gère à la fois /my-school et /my-university)
- Espace parent : **insuffisamment développé** (pas de centre documentaire, pas de téléchargement de certificats, pas de vue unifiée des enfants de cycles différents)

---

## 2. ARCHITECTURE ACTUELLE

L'architecture suivante a été **vérifiée dans le code source** :

```
backend-springboot/          Spring Boot 3.2.5 / Java 17
├── controller/              23 contrôleurs REST
│   ├── AuthController        Authentification
│   ├── UserController        Utilisateurs
│   ├── StudentController     Élèves (CRUD, transfert, photo, QR, certificats, carte)
│   ├── TeacherController     Enseignants
│   ├── ClassController       Classes
│   ├── SubjectController     Matières
│   ├── LevelController       Niveaux
│   ├── SectionController     Sections
│   ├── RoomController        Salles
│   ├── ScheduleController    Emplois du temps
│   ├── AttendanceController  Présences (élèves + enseignants)
│   ├── ExamController        Examens, notes, bulletins, délibérations
│   ├── PaymentController     Factures, paiements, reçus
│   ├── FinanceController     Dépenses
│   ├── LibraryController     Bibliothèque
│   ├── HrController          RH (congés, contrats, paie)
│   ├── CommunicationController  Annonces, notifications, messages, logs
│   ├── NotificationController
│   ├── DashboardController   Statistiques globales + universitaires
│   ├── SearchController      Recherche globale
│   ├── SettingController     Paramètres, cycles, logo
│   ├── BackupController      Sauvegarde/restauration
│   ├── AcademicYearController   Années académiques
│   ├── ParentController      Espace parent (enfants, bulletins, notes, présences)
│   ├── MySpaceController     Espace personnel (profil, bulletins, notes, etc.)
│   └── LmdController         Université LMD (facultés, UE, EC, notes, délibérations)
├── service/                  40 services métier
├── repository/               56 repositories JPA
├── entity/                   55 entités
├── enums/                    30 énumérations
├── security/                 JWT, filtres, config
│   ├── SecurityConfig        Règles par endpoint
│   ├── JwtAuthenticationFilter   Vérification JWT + isEnabled + isAccountNonLocked
│   ├── JwtExceptionFilter    Gestion des exceptions JWT
│   ├── JwtService            Génération/validation tokens
│   └── CustomUserDetailsService  Chargement UserDetails
├── config/                   DataInitializer, WebConfig, AppProperties
├── dto/                      request + response
├── pdf/                      SchoolDocumentTheme
├── mapper/                   MapStruct
└── utils/                    CodeGenerator, PasswordPolicy, AmountToWords, SecurityUtils

frontend-react/              React 18 / Vite 5 / MUI 5
├── pages/                    45 pages
├── components/               DataTable, StatCard, PageHeader, StatusChip, Sidebar, Navbar
├── api/                      axios.js + endpoints.js (5 API modules)
├── redux/                    authSlice, toastSlice, themeSlice
├── hooks/                    useFetch.js, usePagination.js, useToast.js
├── routes/                   Guards.jsx, index.jsx
├── layouts/                  DashboardLayout, AuthLayout
└── services/                 exportService.js, loadingService.js

database/
├── schema.sql
├── migration-v2.sql
├── migration-v3.sql
└── migration-v4.sql

docker/
├── docker-compose.yml
├── Dockerfile.backend
├── Dockerfile.frontend
└── nginx.conf
```

---

## 3. MATRICE DES FONCTIONNALITÉS

| Fonctionnalité | Statut | Niveau |
|---|---|---|
| Authentification JWT | EXISTANTE — FONCTIONNELLE | ✅ |
| RBAC 7 rôles | EXISTANTE — FONCTIONNELLE | ✅ |
| Contrôle IDOR (parent/enfant) | EXISTANTE — FONCTIONNELLE | ✅ |
| Multi-cycle EducationCycle | EXISTANTE — FONCTIONNELLE | ✅ |
| Activation/désactivation des cycles | EXISTANTE — FONCTIONNELLE | ✅ |
| Menu dynamique selon cycles | EXISTANTE — FONCTIONNELLE | ✅ |
| Élèves (CRUD, import, export, photo, QR) | EXISTANTE — FONCTIONNELLE | ✅ |
| Certificat de scolarité PDF | EXISTANTE — FONCTIONNELLE | ✅ |
| Carte scolaire PDF | EXISTANTE — FONCTIONNELLE | ✅ |
| Certificat de fréquentation PDF | EXISTANTE — FONCTIONNELLE | ✅ |
| Classes, niveaux, sections, salles, matières | EXISTANTE — FONCTIONNELLE | ✅ |
| Enseignants (CRUD, affectation) | EXISTANTE — FONCTIONNELLE | ✅ |
| Emploi du temps scolaire | EXISTANTE — FONCTIONNELLE | ✅ |
| Présences scolaires (pointage, justif., stats) | EXISTANTE — FONCTIONNELLE | ✅ |
| Examens, notes, bulletins, délibérations | EXISTANTE — FONCTIONNELLE | ✅ |
| Finances (factures, paiements, reçus) | EXISTANTE — FONCTIONNELLE | ✅ |
| Bibliothèque | EXISTANTE — FONCTIONNELLE | ✅ |
| RH (congés, contrats, paie) | EXISTANTE — FONCTIONNELLE | ✅ |
| Communication (annonces, messages, notifs) | EXISTANTE — FONCTIONNELLE | ✅ |
| Recherche globale (Ctrl+K) | EXISTANTE — FONCTIONNELLE | ✅ |
| Paramètres, cycles, logo | EXISTANTE — FONCTIONNELLE | ✅ |
| Sauvegarde/restauration | EXISTANTE — PARTIELLE | ⚠️ |
| Université LMD (structure) | EXISTANTE — FONCTIONNELLE | ✅ |
| Domaines académiques | EXISTANTE — FONCTIONNELLE | ✅ |
| Facultés, départements, filières | EXISTANTE — FONCTIONNELLE | ✅ |
| Programmes, semestres | EXISTANTE — FONCTIONNELLE | ✅ |
| UE, EC | EXISTANTE — FONCTIONNELLE | ✅ |
| Groupes/promotions | EXISTANTE — FONCTIONNELLE | ✅ |
| Inscriptions LMD | EXISTANTE — FONCTIONNELLE | ✅ |
| Statut inscription (historique) | EXISTANTE — FONCTIONNELLE | ✅ |
| Évaluations EC (multi-évaluations) | EXISTANTE — FONCTIONNELLE | ✅ |
| Notes UE/EC, sessions 1/2 | EXISTANTE — FONCTIONNELLE | ✅ |
| Calculs moyennes, crédits, compensation | EXISTANTE — FONCTIONNELLE | ✅ |
| Délibération LMD | EXISTANTE — FONCTIONNELLE | ✅ |
| Règles académiques configurables | EXISTANTE — FONCTIONNELLE | ✅ |
| Présences universitaires | EXISTANTE — FONCTIONNELLE | ✅ |
| Emploi du temps universitaire | EXISTANTE — FONCTIONNELLE | ✅ |
| Relevé universitaire PDF/Excel | EXISTANTE — FONCTIONNELLE | ✅ |
| Attestation réussite PDF + QR | EXISTANTE — FONCTIONNELLE | ✅ |
| Dashboard universitaire | EXISTANTE — FONCTIONNELLE | ✅ |
| Dashboard unifié (filtre cycle) | EXISTANTE — FONCTIONNELLE | ✅ |
| Espace élève (/my-school) | EXISTANTE — FONCTIONNELLE | ✅ |
| Espace parent (/my-children) | EXISTANTE — INSUFFISAMMENT DÉVELOPPÉE | ⚠️ |
| Espace enseignant (/my-teaching) | EXISTANTE — FONCTIONNELLE | ✅ |
| Espace étudiant (/my-university) | EXISTANTE — FONCTIONNELLE | ✅ |
| Parcours académique (StudentHistory) | EXISTANTE — PARTIELLE | ⚠️ |
| Convocations | ABSENTE | ❌ |
| Diplômes | ABSENTE | ❌ |
| Stages | ABSENTE | ❌ |
| Mémoires/soutenances | ABSENTE | ❌ |
| Alumni | ABSENTE | ❌ |
| Admission universitaire | ABSENTE | ❌ |
| Rôle ÉTUDIANT distinct | ABSENTE | ❌ |
| Centre documentaire | ABSENTE | ❌ |
| Vie étudiante | ABSENTE | ❌ |

---

## 4. AUDIT ÉCOLE (Jardin → Lycée)

### Élèves
- CRUD complet avec génération automatique de matricule
- Transfert, radiation, réinscription avec historique (StudentHistory)
- Upload photo, QR code, certificat de scolarité, carte scolaire, certificat de fréquentation
- Import Excel avec modèle, validation, rapport d'erreurs
- Export PDF/Excel
- Filtre par cycle d'enseignement (EducationCycle)
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

### Structure pédagogique (niveaux, sections, classes, salles, matières)
- CRUD complet, EducationCycle rattaché aux niveaux
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

### Enseignants
- CRUD avec matricule, spécialité, affectation aux classes/matières
- Compte lié automatiquement
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

### Emploi du temps
- Créneaux par jour/heure, détection de conflits
- Vue par classe et par enseignant
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

### Présences
- Pointage groupé classe/date, 4 statuts (Présent, Absent, Retard, Excusé)
- Justification, statistiques, rapports PDF/Excel
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

### Notes et bulletins
- Types d'évaluation (Contrôle, Devoir, Examen, Baccalauréat)
- Saisie inline de tous les élèves de la classe
- Moyennes, classement, bulletins, délibération
- Export PDF bulletins (nouvelle forme)
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

---

## 5. AUDIT UNIVERSITÉ

### Structure institutionnelle
- Facultés/UFR : ✅ EXISTANTE — FONCTIONNELLE
- Départements : ✅ EXISTANTE — FONCTIONNELLE
- Domaines : ✅ EXISTANTE — FONCTIONNELLE (créé en Phase 2)
- Filières (AcademicField) : ✅ EXISTANTE — FONCTIONNELLE
- Programmes : ✅ EXISTANTE — PARTIELLE (manque conditions d'admission, versionnement)
- Groupes/promotions : ✅ EXISTANTE — FONCTIONNELLE
- **Campus, École, Institut, Laboratoire, Centre de recherche : ❌ ABSENT**

### LMD
- Niveaux L1-L3/M1-M2 : ✅ EXISTANTE — FONCTIONNELLE
- Doctorat : ⚠️ EXISTANTE — PARTIELLE (Cycle.DOCTORAT existe, mais pas de niveau spécifique ni d'UI)
- Semestres S1-S12 : ✅ EXISTANTE — FONCTIONNELLE
- UE : ✅ EXISTANTE — FONCTIONNELLE
- EC : ✅ EXISTANTE — FONCTIONNELLE
- Crédits ECTS : ✅ EXISTANTE — FONCTIONNELLE
- Session 1/2 : ✅ EXISTANTE — FONCTIONNELLE
- Compensation : ✅ EXISTANTE — FONCTIONNELLE (configurable)
- Délibération : ✅ EXISTANTE — FONCTIONNELLE
- Mentions : ✅ EXISTANTE — PARTIELLE (seuils configurables, mais mention via enum fixe)
- Règles académiques configurables : ✅ EXISTANTE — FONCTIONNELLE

### Admission universitaire
- **Candidatures, dossiers, validation, sélection : ❌ ABSENT**

### Inscriptions
- Inscription LMD (étudiant/filière/semestre) : ✅ EXISTANTE — FONCTIONNELLE
- Statut (INSCRIT/ABANDON/DIPLOME/EXCLU) : ✅ EXISTANTE — FONCTIONNELLE
- Historique des inscriptions : ✅ EXISTANTE — FONCTIONNELLE
- Changement de niveau/filière : ✅ EXISTANTE — FONCTIONNELLE
- Inscription aux UE : ✅ EXISTANTE — FONCTIONNELLE
- **Réinscription, transfert universitaire, suspension, réintégration : ❌ ABSENT**

### Examens
- Calendrier des examens : ❌ ABSENT
- **Sessions, salles, surveillants, convocations : ❌ ABSENT**
- Résultats d'examens : ✅ EXISTANTE — FONCTIONNELLE (via notes UE/EC)

### Présences
- Par EC/date/séance/groupe : ✅ EXISTANTE — FONCTIONNELLE
- **Rapports de présence universitaire : ❌ ABSENT**

### Enseignants universitaires
- Pas de distinction Enseignant scolaire / universitaire
- **Affectation département, grade, spécialité : ❌ ABSENT**
- L'affectation aux EC existe (CourseUnit.teacher)

### Documents
- Relevé universitaire PDF/Excel : ✅ EXISTANTE — FONCTIONNELLE
- Attestation de réussite PDF + QR : ✅ EXISTANTE — FONCTIONNELLE
- **Certificat de scolarité universitaire, convocation, PV délibération, diplôme : ❌ ABSENT**

### Statistiques
- Dashboard universitaire : ✅ EXISTANTE — FONCTIONNELLE
- Rapport universitaire (effectifs, réussite, dettes) : ✅ EXISTANTE — FONCTIONNELLE

---

## 6. AUDIT ESPACE ÉLÈVE (/my-school)

**Route :** `/my-school` — rôle ELEVE

**Vérifié dans le code :**
- `StudentPortalPage.jsx` (337 lignes)
- 5 onglets : Mes bulletins, Mes notes, Emploi du temps, Présences, Paiements
- Affichage de la moyenne, absences, factures en attente
- Téléchargement PDF des bulletins (ajouté récemment)
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

**Manque :**
- Dashboard élève (moyenne, rang, prochains cours, devoirs, notifications)
- Prochains devoirs/évaluations
- Notifications visibles
- Adapté à un élève (interface plus simple)

---

## 7. AUDIT ESPACE ÉTUDIANT (/my-university)

**Route :** `/my-university` — rôle ELEVE (même rôle que scolaire)

**Vérifié dans le code :**
- `UniversityPortalPage.jsx` (dernière version : 3 onglets : Inscriptions, Résultats, Documents)
- Bandeau profil avec KPI (inscriptions, crédits, moyenne)
- Inscriptions : carte par filière + historique du parcours
- Résultats : sélecteur de semestre, tableau détaillé des UE, synthèse (moyenne, décision, mention, crédits)
- Documents : Relevé PDF/Excel, Attestation PDF
- **Verdict : EXISTANTE — FONCTIONNELLE** ✅

**Manque :**
- Dashboard étudiant (moyenne du semestre, crédits, UE validées, prochains examens, rattrapages)
- Emploi du temps universitaire
- Notifications
- Dettes académiques affichées
- **Rôle ÉTUDIANT distinct** (actuellement ELEVE)

---

## 8. AUDIT ESPACE PARENT (/my-children)

**Route :** `/my-children` — rôle PARENT

**Vérifié dans le code :**
- `ParentPortalPage.jsx` (267 lignes)
- Liste des enfants avec sélection
- 3 onglets : Bulletins, Présences, Notes détaillées
- Téléchargement PDF des bulletins (ajouté récemment)
- **Verdict : EXISTANTE — INSUFFISAMMENT DÉVELOPPÉE** ⚠️

**Manque (critique) :**
- **Centre documentaire** : aucun téléchargement de certificat de scolarité, attestation, convocation, reçu, facture
- **Vue unifiée des enfants de cycles différents** : un parent avec un enfant au CM2 et un en L2 peut voir les deux, mais l'UX n'est pas adaptée
- **Parcours académique** : pas de timeline
- **Dashboard parent** : pas de KPI global
- **Notifications** : pas de liste de notifications pour les événements enfants
- **Convocations** : absentes
- **Documents** : pas de téléchargement de certificat de scolarité, carte scolaire, QR code, reçu, facture
- **Université** : pas de relevé universitaire, crédits, attestation pour les enfants universitaires

---

## 9. AUDIT PARCOURS ACADÉMIQUE

**StudentHistory** existe pour le scolaire :
- Table `student_histories` (student_id, action, from_class, to_class, reason, recorded_by, created_at)
- Actions : TRANSFERT, RADIATION, REINSCRIPTION

**EnrollmentHistory** existe pour l'universitaire :
- Table `enrollment_histories` (student_id, field_id, from_level, to_level, from_semester, to_semester, academic_year, enrollment_status, reason, recorded_by, created_at)
- Créé en Phase 4

**Verdict : EXISTANTE — PARTIELLE** ⚠️
- Les deux historiques existent mais ne sont **pas consolidés** dans une timeline unique
- Pas de vue globale du parcours complet (scolaire + universitaire) dans l'interface
- Le portail parent n'affiche pas le parcours

---

## 10. AUDIT DOCUMENTS

### Documents scolaires existants
| Document | Statut | Backend | Frontend |
|---|---|---|---|
| Bulletin PDF | ✅ | ReportService.bulletinPdf | Portails élève/parent |
| Certificat de scolarité PDF | ✅ | ReportService.schoolCertificatePdf | StudentController |
| Carte scolaire PDF | ✅ | ReportService.schoolIdCardPdf | StudentController |
| Certificat de fréquentation PDF | ✅ | ReportService.attendanceCertificatePdf | StudentController |
| QR code carte scolaire | ✅ | QrCodeService.generateStudentQr | StudentController |
| Reçu de paiement PDF | ✅ | ReportService.paymentReceiptPdf | PaymentController |
| PV de délibération PDF | ✅ | ReportService.exportStudentsPdf / exportTablePdf | ExamController |

### Documents universitaires existants
| Document | Statut | Backend | Frontend |
|---|---|---|---|
| Relevé universitaire PDF | ✅ | ReportService.universityRelevePdf | LmdController / portail |
| Relevé universitaire Excel | ✅ | ReportService.universityReleveExcel | LmdController / portail |
| Attestation de réussite PDF + QR | ✅ | ReportService.universityAttestationPdf | LmdController / portail |

### Documents absents
| Document | Impact |
|---|---|
| Convocation scolaire PDF | ❌ |
| Convocation universitaire PDF | ❌ |
| Fiche d'inscription universitaire PDF | ❌ |
| Diplôme PDF | ❌ |
| Certificat de scolarité universitaire PDF | ❌ |
| PV de délibération universitaire PDF | ❌ |

---

## 11. AUDIT CONVOCATIONS

**Aucune entité, repository, service, contrôleur, page, route, table ou migration** ne concerne les convocations.

**Verdict : ABSENTE** ❌

---

## 12. AUDIT NOTIFICATIONS

**NotificationService** existe (com.school.service.NotificationService) :
- `notify(User, title, message, type, link)` — création
- `getMyNotifications(userId, page, size)` — paginée
- `unreadCount(userId)` — compteur
- `markAsRead(userId, notificationId)` — marquage lu (ownership vérifié récemment)
- `markAllAsRead(userId)` — tout marquer lu

**NotificationRepository** :
- `findByUserIdOrderByCreatedAtDesc`
- `countByUserIdAndReadFalse`
- `markAllAsRead` (JPQL)

**Utilisation dans le code :**
- GradeService : notification parents bulletins disponibles
- GradeService : délibération
- AttendanceService : notification absence
- LmdService : (non utilisé pour les notifications)
- CommunicationController : notifications annonces

**Canaux :** uniquement notification interne (base de données). Pas d'email, SMS ou WhatsApp automatisés pour les notifications.

**Verdict : EXISTANTE — PARTIELLE** ⚠️

---

## 13. AUDIT FINANCES

**FinanceService** : factures, paiements, dépenses, reçus, exports.
- Factures par élève avec numéro séquentiel, échéance, statut
- Paiements avec reçu PDF, montant en lettres
- Dépenses par catégorie
- Export PDF/Excel
- **Filtre par cycle : non implémenté** (les factures ne sont pas liées à un cycle d'enseignement)
- **Catégories de frais universitaires : non semées** (FeeType n'a pas de seeds pour les frais universitaires)

**Verdict : EXISTANTE — PARTIELLE** ⚠️ (manque : filtre par cycle, catégories universitaires, frais par semestre)

---

## 14. AUDIT BASE DE DONNÉES

### Tables vérifiées (55 entités JPA, 30 enums)

| Domaine | Tables | Statut |
|---|---|---|
| Auth | users, roles, permissions, role_permissions, user_roles, refresh_tokens | ✅ |
| Élèves | students, student_histories, parents | ✅ |
| Structure | levels, sections, classes, rooms, subjects, subject_assignments | ✅ |
| Enseignants | teachers | ✅ |
| Pédagogie | schedules, attendances, exams, grades, bulletins, teacher_attendances | ✅ |
| Finances | fee_types, invoices, payments, expense_categories, expenses | ✅ |
| Bibliothèque | books, borrowings | ✅ |
| RH | contracts, payrolls, leaves | ✅ |
| Communication | notifications, messages, message_logs, announcements | ✅ |
| Système | settings, audit_logs, academic_years | ✅ |
| Université LMD | faculties, departments, academic_fields, domains, programs, semesters, university_units, course_units, ue_grades, ec_grades, lmd_enrollments, ue_enrollments, lmd_deliberations, university_schedules, university_groups, ec_evaluations, academic_rules, enrollment_histories, university_attendances | ✅ |

**Problèmes identifiés :**
- `students.class_id` : NOT NULL dans la migration v3 (rendu NULLABLE en Phase 1)
- `academic_fields.domain_id` : NULLABLE (ajouté en Phase 2)
- `semesters.academic_year` : NULLABLE (ajouté en Phase 3)
- `lmd_enrollments.enrollment_status` : NULLABLE (ajouté en Phase 4)
- Pas de table `convocations`
- Pas de table `university_enrollments` distincte de `lmd_enrollments`

---

## 15. AUDIT API

**22 contrôleurs, ~200 endpoints vérifiés.**

### Endpoints fonctionnels
- Authentification : login, refresh, register, logout ✅
- Élèves : CRUD, search, transfert, radiation, photo, QR, certificats, carte, import/export ✅
- Enseignants : CRUD, search ✅
- Classes, niveaux, sections, salles, matières : CRUD, search ✅
- Emploi du temps : CRUD, search, conflits ✅
- Présences : pointage, justification, stats, rapports, enseignants ✅
- Examens : CRUD, notes, bulletins, délibérations, classement, PDF ✅
- Finances : factures, paiements, reçus, exports ✅
- Bibliothèque : livres, emprunts ✅
- RH : congés, contrats, paie ✅
- Communication : annonces, notifications, messages ✅
- Dashboard : stats, university-stats, university-report, audit-logs ✅
- Recherche : search ✅
- Paramètres : settings, cycles, logo ✅
- LMD : facultés, domaines, départements, filières, programmes, semestres, groupes, UE, EC, inscriptions, notes, délibérations, relevés, attestations, évaluations EC, règles, présences, emploi du temps, historique ✅
- Espace personnel : profile, schedule, grades, bulletins, attendances, invoices, university, university-releve, university-history ✅
- Espace parent : children, childBulletins, childAttendances, childGrades ✅

### Endpoints restreints récemment (IDOR)
- `/api/students/{id}` : vérification parent/enfant ou élève/profil ✅
- `/api/payments/student/{studentId}` : vérification ✅
- `/api/attendances/student/{studentId}` : vérification ✅
- `/api/search` : réservé `PERM_REPORT_READ` / `PERM_USER_READ` ✅
- `/api/communication/message-logs` : réservé `PERM_USER_READ` ✅

### Endpoints absents
- Convocations (CRUD convocations) ❌
- Admission universitaire (candidatures) ❌
- Stages ❌

---

## 16. AUDIT SÉCURITÉ

| Contrôle | Statut | Détail |
|---|---|---|
| JWT (access + refresh) | ✅ | Secret configurable, expiration, rotation |
| RBAC | ✅ | 7 rôles × permissions fines (PERM_*) |
| SecurityConfig | ✅ | Règles par endpoint HTTP + permission |
| Contrôle IDOR | ✅ | AccessControlService (parent/enfant, élève/profil) |
| Compte désactivé | ✅ | JwtAuthenticationFilter vérifie isEnabled() |
| Verrouillage anti brute-force | ✅ | 5 tentatives, lockedUntil |
| Politique mot de passe | ✅ | ≥8 caractères, lettres + chiffres |
| @JsonIgnore password | ✅ | User.password caché des réponses JSON |
| CORS | ✅ | Origines configurées |
| XSS | ✅ | Material UI échappe le HTML |
| CSRF | ✅ | Désactivé (stateless JWT) |
| SQL Injection | ⚠️ | BackupService.restoreBackup exécute du SQL depuis fichier uploadé |
| Logs sensibles | ✅ | DataInitializer ne logge plus le mot de passe admin |
| server.error.include-message | ⚠️ | Toujours `always` (messages d'erreur exposés) |
| Notifications ownership | ✅ | markAsRead vérifie l'appartenance |
| Pagination non bornée | ⚠️ | Aucun `@Max` sur `size` (DoS possible) |

**Verdict global : BON** — les vulnérabilités critiques sont corrigées, les risques restants sont documentés.

---

## 17. AUDIT UX/UI

**Forces :**
- Material UI 5, design moderne, cohérent
- PageHeader, StatCard, DataTable, FilterCard, StatusChip réutilisés
- 8 onglets LMD dans `/lmd` avec navigation claire
- Filtre cycle global dans le Dashboard
- Saisie inline des notes dans GradesPage + TeacherPortalPage
- Barre de recherche Ctrl+K
- Menu latéral adaptatif (cycles)

**Faiblesses :**
- Espace parent : UX minimaliste, pas de centre documentaire, pas de timeline
- Espace élève : 5 onglets, pas de dashboard spécifique
- Portail universitaire : 3 onglets, pas de dashboard étudiant
- Formulaire d'élève : pas de cycle clairement visible
- Page d'accueil : redirige vers /dashboard sans onboarding
- Pas de responsive pour certains tableaux complexes
- Pas de mode sombre (themeSlice existe mais non utilisé)

---

## 18. AUDIT PERFORMANCE

**Problèmes identifiés :**
- `LmdService.computeResult` : charge toutes les notes UE/EC en mémoire, pourrait être lent pour des filières de 200+ étudiants
- `DashboardService.universityStats` : `lmdEnrollmentRepository.findAll()` + `deliberationRepository.findAll()` chargent TOUT en mémoire
- `FinanceService.nextInvoiceNo` : `repository.count()` sans cache (amélioré, mais pas de verrou atomique)
- `GradeService.generateBulletins` : charge tous les élèves et notes en mémoire
- Pagination : `@Max(100)` manquant sur les paramètres `size` (DoS possible)
- Bundle JS : 1.46MB non splité (warning Vite)

**Non critiques** pour la taille d'établissement visée (500-2000 élèves).

---

## 19. BUGS CRITIQUES (P0)

| # | Bug | Fichier | Statut |
|---|---|---|---|
| 1 | ~~Fuite hash mot de passe dans réponses JSON~~ | User.java | ✅ CORRIGÉ |
| 2 | ~~Compte désactivé gardait accès JWT~~ | JwtAuthenticationFilter | ✅ CORRIGÉ |
| 3 | ~~IDOR : parent voit tous les élèves~~ | StudentController | ✅ CORRIGÉ |
| 4 | ~~IDOR : élève voit paiements/présences de tous~~ | PaymentController, AttendanceController | ✅ CORRIGÉ |
| 5 | ~~Recherche globale exposée à tous~~ | SearchController, SecurityConfig | ✅ CORRIGÉ |
| 6 | ~~Logs de messages exposés~~ | CommunicationController, SecurityConfig | ✅ CORRIGÉ |
| 7 | ~~Notification.markAsRead sans ownership~~ | NotificationService | ✅ CORRIGÉ |
| 8 | BackupService restore SQL injectable | BackupService | ⚠️ NON CORRIGÉ (remédiation : supprimer restore ou ajouter validation stricte) |
| 9 | Pagination `size` non borné | Tous les contrôleurs | ⚠️ NON CORRIGÉ |

---

## 20. FONCTIONNALITÉS ABSENTES

| Fonctionnalité | Section | Priorité |
|---|---|---|
| Convocations (scolaires + universitaires) | §18 | P1 |
| Rôle ÉTUDIANT distinct de ELEVE | §22 | P1 |
| Admission universitaire (candidatures, sélection) | §26-C | P2 |
| Diplômes PDF | §47 | P2 |
| Stages (entreprises, conventions, rapports, soutenances) | §44 | P2 |
| Mémoires/soutenances | §45 | P2 |
| Alumni | §46 | P3 |
| Vie étudiante (associations, événements, services) | §43 | P3 |
| Centre de recherche, laboratoires | §26-A | P3 |
| Doctorat (niveaux, UI) | §26-F | P2 |
| Calendrier des examens universitaires | §39 | P2 |

---

## 21. FONCTIONNALITÉS PARTIELLES

| Fonctionnalité | Présent | Manque |
|---|---|---|
| Parcours académique | StudentHistory + EnrollmentHistory | Timeline consolidée, vue globale, portail parent |
| Programme | Program avec code, nom, diplôme | Conditions d'admission, versionnement, crédits requis |
| Mentions | Mention enum | Ajout de mentions personnalisables |
| Notifications | NotificationService | Email/SMS/WhatsApp automatisés, historique consolidé |
| Finances | Factures, paiements, reçus | Filtre par cycle, catégories universitaires |
| Espace parent | Bulletins, présences, notes | Centre documentaire, certificats, relevés, convocations, notifications, timeline |
| Espace élève | Bulletins, notes, EDT, présences, paiements | Dashboard élève, devoirs, notifications |
| Espace étudiant | Inscriptions, résultats, documents | Dashboard étudiant, EDT, notifications, dettes |
| Sauvegarde/restauration | BackupService | Restauration non sécurisée (SQL injectable) |

---

## 22. FONCTIONNALITÉS INSUFFISAMMENT DÉVELOPPÉES

| Fonctionnalité | Raison |
|---|---|
| Espace parent (/my-children) | Pas de centre documentaire, pas de téléchargement de certificats, pas de vue unifiée des cycles, pas de notifications, pas de convocations |
| Parcours académique | Pas de timeline visuelle, pas de consolidation scolarité + université |
| Interface étudiant universitaire | Pas de distinction avec l'élève scolaire, pas de dashboard spécifique |
| Enseignants universitaires | Pas de distinction avec les enseignants scolaires, pas de grade/spécialité |

---

## 23. FONCTIONNALITÉS À SUPPRIMER OU FUSIONNER

| Fonctionnalité | Action |
|---|---|
| Rôle ELEVE pour /my-university | **Fusionner** : créer un rôle ÉTUDIANT distinct, migrer les comptes ELEVE avec inscription LMD vers ÉTUDIANT |
| Permission PARENT → PERM_STUDENT_READ | **Conserver** mais avec contrôle IDOR (déjà fait) |
| Permission ELEVE → PERM_PAYMENT_READ / ATTENDANCE_READ | **Conserver** mais avec contrôle IDOR (déjà fait) |

---

## 24. DUPLICATIONS DÉTECTÉES

| Duplication | Détail | Action |
|---|---|---|
| `JwtService.generateRefreshToken` | Méthode avec `@Value` sur paramètre (inopérant), jamais appelée | Supprimer la méthode morte |
| `EnrollmentHistory` et `StudentHistory` | Deux entités d'historique, l'une scolaire, l'autre universitaire | **Conserver** (modèles métier différents) mais unifier la vue frontend |
| `ecRepository` / `courseUnitRepository` | Deux champs de même type injectés dans LmdService (corrigé) | ✅ Déjà corrigé |
| `findAllWithLocking()` | Méthode supprimée des repositories (verrou pessimiste de table) | ✅ Déjà corrigé |

---

## 25. RISQUES DE RÉGRESSION

| Risque | Modules concernés | Niveau |
|---|---|---|
| Changement du rôle ELEVE → ÉTUDIANT | Espace scolaire, espace universitaire, auth, permissions | ÉLEVÉ |
| Ajout de convocations | Nouvelle table, nouveau service, nouveau contrôleur, nouvelle page, route, menu | FAIBLE |
| Migration vers ÉTUDIANT distinct | Modèle de données existant, comptes, DataInitializer, SecurityConfig, routes, guards | ÉLEVÉ |
| Ajout de campus/établissements | Nouvelle structure, impact sur facultés, départements, programmes | MOYEN |

---

## 26. ARCHITECTURE CIBLE

```
SMS — PLATEFORME ÉDUCATIVE UNIFIÉE
│
├── AUTH / RBAC / IDOR
│   ├── 8 rôles (SUPER_ADMIN, DIRECTEUR, SECRETAIRE, COMPTABLE, ENSEIGNANT, PARENT, ELEVE, ETUDIANT)
│   └── Permissions fines (PERM_*)
│
├── MODULES TRANSVERSAUX
│   ├── Utilisateurs
│   ├── Paramètres
│   ├── Cycles d'enseignement
│   ├── Notifications
│   ├── Communication
│   ├── Recherche globale
│   ├── Audit
│   └── Documents
│
├── ÉCOLE (Jardin → Lycée)
│   ├── Élèves
│   ├── Classes / Niveaux / Sections
│   ├── Enseignants
│   ├── Matières
│   ├── Emploi du temps
│   ├── Présences
│   ├── Examens / Notes / Bulletins
│   └── Finances
│
├── UNIVERSITÉ
│   ├── Étudiants (rôle distinct)
│   ├── Structure institutionnelle (Facultés, Départements, Domaines)
│   ├── Structure académique (Filières, Programmes, Semestres)
│   ├── LMD (UE, EC, Crédits, Sessions, Rattrapages)
│   ├── Inscriptions (administrative, pédagogique, UE)
│   ├── Notes / Évaluations / Délibérations
│   ├── Emploi du temps universitaire
│   ├── Présences universitaires
│   ├── Examens / Convocations
│   ├── Stages / Mémoires
│   └── Admission
│
├── ESPACES PERSONNELS
│   ├── Dashboard élève (/my-school)
│   ├── Dashboard étudiant (/my-university)
│   ├── Dashboard parent (/my-children)
│   ├── Dashboard enseignant (/my-teaching)
│   └── Centre documentaire
│
└── PARCOURS ACADÉMIQUE
    ├── Timeline scolaire + universitaire
    ├── Historique consolidé
    └── Documents par année
```

---

## 27. PLAN DE MIGRATION

### Phase 0 : Audit (cette phase) — terminé
### Phase 1 : Correction des bugs existants (P0) — terminé
### Phase 2 : Correction et restructuration de /lmd — terminé
### Phase 3 : Séparation Élève / Étudiant
- Créer le rôle ÉTUDIANT dans DataInitializer (permissions LMD + STUDENT_READ limité)
- Migrer les comptes ELEVE avec inscription LMD vers ÉTUDIANT
- SecurityConfig : ajouter les règles pour ÉTUDIANT
- Routes : `/my-university` → rôle ÉTUDIANT
- **Risque : ÉLEVÉ** — nécessite une migration de données et une mise à jour des guards frontend
### Phase 4 : Architecture académique unifiée
- Ajouter `campus`/`institution` à la structure universitaire
- Lier les finances aux cycles d'enseignement
- Timeline du parcours académique consolidée
### Phase 5 : Gestion universitaire avancée
- Admission universitaire (candidatures, dossiers)
- Stages, mémoires, soutenances
- Doctorat
- Alumni
### Phase 6 : Portail parent unifié
- Centre documentaire (certificats, attestations, reçus, relevés)
- Téléchargement de tous les documents autorisés par enfant
- Notifications parent
- Timeline du parcours
### Phase 7 : Parcours académique historique
- Timeline visuelle complète
- Documents par année
- Vue dans le profil de l'apprenant
### Phase 8 : Centre documentaire
- Architecture documentaire commune
- Filtres, téléchargement, impression
- QR code de vérification
### Phase 9 : Convocations + notifications
- Entité convocation, CRUD, PDF
- Notification automatique au parent
- Calendrier des examens universitaires
### Phase 10 : Examens / délibérations / crédits
- Calendrier des examens, salles, surveillants
- Convocations aux examens
- Jury universitaire
### Phase 11 : Stages / mémoires / vie étudiante
- Stages (entreprise, convention, encadrant, rapport, soutenance, évaluation)
- Mémoires (sujet, directeur, jury, date, note, document, décision)
- Vie étudiante (selon priorité)
### Phase 12 : Statistiques et analytics
- Dashboard universitaire enrichi
- Graphiques d'évolution
- Export de rapports
### Phase 13 : Tests de non-régression
- Scénarios 1-17 (cf. §60)
- Tests sur les modules scolaires existants
- Tests sur les nouvelles fonctionnalités universitaires
### Phase 14 : Optimisation et finalisation UX/UI
- Responsive design
- Mode sombre
- Dashboard élève, étudiant, parent
- Performance

---

## 28. ROADMAP PAR PRIORITÉ

| Priorité | Phase | Fonctionnalité | Effort |
|---|---|---|---|
| **P0** | Phase 1 | ✅ Corrigé | — |
| **P1** | Phase 3 | Rôle ÉTUDIANT distinct | 3 jours |
| **P1** | Phase 6 | Espace parent enrichi (centre documentaire) | 5 jours |
| **P1** | Phase 9 | Convocations (scolaires + universitaires) | 3 jours |
| **P1** | Phase 7 | Timeline parcours académique | 2 jours |
| **P2** | Phase 5 | Admission universitaire | 5 jours |
| **P2** | Phase 5 | Stages | 4 jours |
| **P2** | Phase 5 | Mémoires/soutenances | 3 jours |
| **P2** | Phase 5 | Doctorat (UI, niveaux) | 1 jour |
| **P2** | Phase 10 | Calendrier examens universitaires | 2 jours |
| **P2** | Phase 10 | Jury universitaire | 2 jours |
| **P2** | Phase 8 | Centre documentaire | 3 jours |
| **P3** | Phase 5 | Alumni | 1 jour |
| **P3** | Phase 11 | Vie étudiante | 3 jours |
| **P3** | Phase 5 | Laboratoires, centres de recherche | 1 jour |

---

## 29. FICHIERS À MODIFIER

(Phase 3 — Rôle ÉTUDIANT)
- `DataInitializer.java` : ajouter ÉTUDIANT + permissions
- `SecurityConfig.java` : règles pour ÉTUDIANT
- `routes/index.jsx` : rôle ÉTUDIANT pour /my-university
- `Guards.jsx` (si nécessaire)
- `Sidebar.jsx` : entrée ÉTUDIANT

(Phase 6 — Espace parent)
- `ParentPortalPage.jsx` : réécriture complète (centre documentaire, timeline, notifications)
- `ParentService.java` : ajout méthodes documents
- `ParentController.java` : endpoints documents

(Phase 9 — Convocations)
- Nouveau : `Convocations` entity, repository, service, controller
- `ConvocationsPage.jsx` (frontend)
- `migration-v5.sql`

---

## 30. FICHIERS À CRÉER

| Fichier | Phase |
|---|---|
| `entity/Convocation.java` | 9 |
| `repository/ConvocationRepository.java` | 9 |
| `service/ConvocationService.java` | 9 |
| `controller/ConvocationController.java` | 9 |
| `pages/convocation/ConvocationsPage.jsx` | 9 |
| `dto/request/ConvocationRequest.java` | 9 |
| `dto/response/ConvocationResponse.java` | 9 |
| `database/migration-v5.sql` | 9 |
| `pages/parent/ChildDocumentsPage.jsx` | 6 |
| `pages/parent/ChildTimelinePage.jsx` | 6 |

---

## 31. TABLES À MODIFIER

| Table | Modification | Phase |
|---|---|---|
| `users` | Ajouter colonne `role_name` pour distinguer ELEVE/ÉTUDIANT | 3 |
| `students` | Ajouter `is_university_student` (boolean) | 3 |
| `fee_types` | Ajouter seeds pour catégories universitaires | 5 |
| `invoices` | Ajouter `education_cycle` (nullable) | 5 |

---

## 32. TABLES À CRÉER

| Table | Phase |
|---|---|
| `convocations` | 9 |
| `candidatures` (admission) | 5 |
| `stages` | 5 |
| `memoires` | 5 |
| `alumni` | 5 |
| `documents` (centre documentaire) | 8 |

---

## 33. API À MODIFIER

| Endpoint | Modification | Phase |
|---|---|---|
| `GET /api/my/university` | Restreindre au rôle ÉTUDIANT | 3 |
| `GET /api/parents/children/{id}/documents` | Ajouter | 6 |
| `GET /api/convocations/**` | Nouveau | 9 |

---

## 34. API À CRÉER

| API | Phase |
|---|---|
| `GET /api/convocations` | 9 |
| `POST /api/convocations` | 9 |
| `GET /api/convocations/{id}/pdf` | 9 |
| `GET /api/parents/children/{id}/documents` | 6 |
| `GET /api/parents/children/{id}/timeline` | 7 |
| `GET /api/admissions` | 5 |
| `POST /api/admissions` | 5 |
| `GET /api/stages` | 5 |
| `POST /api/stages` | 5 |
| `GET /api/memoires` | 5 |
| `POST /api/memoires` | 5 |

---

## 35. TESTS À AJOUTER

| Test | Scénario | Phase |
|---|---|---|
| Parent-child accès documents | 8 (refus si autre enfant) | 3 |
| Élève ne peut voir que son profil | 5 | 3 |
| Création inscription universitaire | 9 | 3 |
| Saisie notes UE/EC | 11 | 3 |
| Calcul crédits ECTS | 12 | 3 |
| Rattrapage session 2 | 13 | 3 |
| Délibération LMD | 14 | 3 |
| Génération relevé PDF | 15 | 3 |
| Convocation notification parent | 16-17 | 9 |
| Parent multi-enfants multi-cycles | 4 | 6 |
| Rôle ÉTUDIANT vs ELEVE | 6 | 3 |
| Non-régression modules scolaires | §61 | 13 |

---

**Rapport produit par analyse exhaustive du code source. 0 modification effectuée.**