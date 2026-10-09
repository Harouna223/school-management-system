# Analyse technique — School Management System (SMS)

> Analyse indépendante menée le **9 octobre 2026** par inspection directe du code **et par exécution**
> (build, tests, lint, requêtes SQL, conteneur MySQL neuf).
> Périmètre : `backend-springboot/`, `frontend-react/`, `database/`, `docker/`, `.github/`, `docs/`, racine.
> Référence de code : `HEAD = af1e5f3` (2026-09-25) **plus 27 fichiers modifiés non commités**.

> **Mise à jour ultérieure — le module universitaire / LMD a été entièrement supprimé.**
> Ce rapport décrit l'état du produit **avant** cette suppression. Les constats qui concernent
> exclusivement le module universitaire (LMD, UE/EC, délibérations universitaires, portail
> `/my-university`, rôle `ETUDIANT`, endpoints `/api/lmd/**` et `/api/university-exams/**`) ne sont
> donc **plus applicables** tels quels — notamment les points **P0-4** (IDOR LMD) et **P0-5**
> (cloisonnement des notes), qui disparaissent avec les endpoints concernés.
> Les constats transverses (installation neuve cassée, absence de migration, secrets par défaut,
> pagination non bornée, traçabilité du déploiement, hygiène du dépôt) **restent valides**.
> Les modules **Stages, Mémoires, Alumni et Admission sont conservés**, ainsi que les permissions
> `LMD_READ` / `LMD_WRITE` qui les protègent.

---

## 1. Synthèse exécutive

SMS est une application full-stack **de grande envergure et sérieusement construite** : ~302 endpoints,
67 entités JPA, 40 000 lignes de code applicatif, **444 tests backend verts**, lint frontend propre,
Docker Compose complet avec sauvegarde quotidienne. Ce n'est pas un prototype.

**Mais l'état « prêt pour la production » n'est pas atteint**, pour une raison qui n'est pas visible
dans le code applicatif : **l'installation neuve est cassée**. Le script `database/schema.sql` échoue
sur une base vierge — reproduit de bout en bout sur un conteneur MySQL 8.4 neuf, qui **termine en
`exit 1`**. Le chemin d'installation documenté (`docker compose up`) ne démarre donc pas.

S'y ajoutent : **aucun outil de migration versionnée** (le schéma est arbitré en production par
Hibernate), des **secrets par défaut présents dans le dépôt** — et même **affichés sur l'écran de
connexion** —, une **faille IDOR confirmée** qui permet à un compte étudiant de lire les résultats
universitaires d'un autre étudiant, et une **exposition du fichier élèves** qui permet à un compte
parent de lister tous les élèves de l'établissement.

Les trois audits de détail convergent : les fondations sont bonnes, la dette est concentrée sur
**la reproductibilité du déploiement, la sécurité résiduelle et l'hygiène du dépôt**.

| Indicateur | Valeur vérifiée |
|---|---|
| Backend — fichiers Java (main) | **345** (20 074 lignes) |
| Backend — fichiers de test | **24** (7 801 lignes) |
| Contrôleurs / services / entités / repositories | **29 / 42 / 67 / 67** |
| Endpoints REST | **302** |
| Frontend — `.jsx` / `.js` | **78 / 25** (19 710 lignes), 48 pages, 14 composants |
| Tests backend | **444 — 0 échec** (`BUILD SUCCESS`, exécuté le 09/10 à 23:05) |
| Tests frontend | **64 — 0 échec** (12 fichiers) |
| Lint frontend (ESLint 9) | **0 erreur** |
| Tables : scripts SQL vs attendu par JPA vs base réelle | **51 / 64 / 71** |
| Commits / contributeurs | 49 / 1 (`Harouna Siby`) |
| Artefacts de debug à la racine | **53 fichiers, 1,29 Mo** |

---

## 2. Méthode et limites

Ce qui a été **réellement exécuté** (pas seulement lu) :

| Vérification | Résultat |
|---|---|
| `mvn -B test` | 444 tests, 0 échec, `BUILD SUCCESS` |
| `npm run lint` (ESLint 9) | 0 erreur |
| `npm run test` (Vitest) | 64/64 — **une première exécution a échoué 1/64 (flake), voir §7** |
| `database/schema.sql` sur base vierge (copie isolée) | **5 instructions en erreur**, 35 tables créées |
| `docker run mysql:8.4` neuf + `schema.sql` | **`exit 1`** — échec dur de l'initialisation |
| `GET /actuator/health` public | `{"status":"UP","groups":[...]}` — pas de fuite de détails |
| `GET /api/students` sans jeton | `401` — protection correcte |
| Lecture de la base de production | 71 tables, données de référence présentes |

**Limites assumées :**
- Je n'ai pas démarré l'application complète pour tester chaque parcours fonctionnel.
- Les comptages de couches proviennent de l'énumération de fichiers, non d'une résolution de beans Spring.
- Je n'ai pas exécuté `migration-v2..v7.sql` (ils ne sont exécutés par personne — c'est précisément le constat).
- Aucune donnée de production n'a été modifiée : les essais SQL ont utilisé des bases jetables
  (`sms_schema_check`, conteneur `sms-fresh-test`), toutes supprimées après usage.

---

## 3. Périmètre fonctionnel réel (le README le sous-déclare fortement)

Le `README.md` (75 lignes) décrit un système de gestion scolaire classique. Le code va **nettement
plus loin** :

- **Scolarité** : élèves (matricule auto, import/export Excel, documents PDF + QR), niveaux, sections,
  classes, salles, matières, enseignants, affectations.
- **Pédagogie** : emploi du temps avec détection de conflits, présences (élèves + personnel),
  évaluations, notes, moyennes, classements, bulletins PDF, délibérations, convocations.
- **Université / LMD** *(absent du README)* : facultés, départements, domaines, filières, programmes,
  groupes, semestres S1–S12, **UE/EC avec crédits ECTS**, inscriptions, notes par session
  (normale/rattrapage), moyennes UE & semestre, compensation, délibérations, relevés PDF/Excel,
  attestations, règles académiques.
- **Périphérie universitaire** *(absent du README)* : stages, mémoires, alumni, candidatures/admissions.
- **Finances** : types de frais, factures, paiements (reçus PDF, montant en lettres), dépenses, rapports.
- **RH** : congés, contrats, paie, et **paie des enseignants à l'heure** (`/api/teacher-hours`,
  21 endpoints — totalement absente du README).
- **Portails** : élève (`/my-school`), parent (`/my-children`), enseignant (`/my-teaching`),
  étudiant (`/my-university`).
- **Transverse** : recherche globale, journal d'audit, paramètres, notifications WhatsApp / SMS / e-mail,
  sauvegarde/restauration.

---

## 4. P0 — Constats bloquants (reproduits ou vérifiés)

### P0-1 — `schema.sql` casse toute installation neuve 🔴 *reproduit*

**Preuve par exécution.** Conteneur MySQL 8.4 vierge avec le script monté :

```
[Entrypoint]: running /docker-entrypoint-initdb.d/01-schema.sql
ERROR 1824 (HY000) at line 572: Failed to open the referenced table 'academic_years'
→ conteneur : exited (exit code 1)
```

**Les 5 instructions fautives** (toutes des clés étrangères vers des tables inexistantes
*à cet endroit du fichier*) :

| Ligne | Référence | Cause |
|---|---|---|
| 572 | `academic_years` | table **jamais créée** dans `database/` (0 occurrence de `CREATE TABLE academic_years`) |
| 589 | `school_classes` | **nom erroné** : la table créée ligne 96 s'appelle `classes` (`SchoolClass.java:13`) |
| 614 | `academic_years` | idem 572 |
| 635 | `teacher_monthly_payments` | **référence avant création** — la table est créée plus bas dans le fichier |
| 656 | `academic_years` | idem 572 |

**Conséquences en cascade :**
1. MySQL s'arrête à la ligne 572 → **`exit 1`**. Avec `restart: unless-stopped`, le conteneur boucle.
2. Comme `backend` déclare `depends_on: mysql: condition: service_healthy`
   (`docker/docker-compose.yml:37-39`), **le backend ne démarre jamais**.
3. Le bloc `-- DONNÉES INITIALES` (ligne 692+) n'est jamais atteint : rôles, permissions, admin,
   niveaux, sections, salles, classes, matières, types de frais sont perdus.

**Nuance importante et vérifiée :** l'installation **existante n'est pas affectée**. La base de
production contient bien 15 niveaux, 8 matières, 7 classes, 12 élèves, 11 utilisateurs. Le script a
donc été valide par le passé ; le bug est apparu en ajoutant les tables de paie enseignante.
**Le risque est donc double et non un incident en cours : (a) toute nouvelle installation échoue,
(b) toute reprise après sinistre (perte du volume) est impossible.**

*Correctif :* ligne 589 `school_classes` → `classes` ; créer (ou supprimer la contrainte vers)
`academic_years` ; réordonner `teacher_monthly_payments` avant ses références. **Puis ajouter une
étape CI qui exécute réellement le SQL** — sans cela le bug reviendra.

### P0-2 — Aucun outil de migration : le schéma est arbitré par Hibernate 🔴 *reproduit*

- `pom.xml` : **0 occurrence** de Flyway ou Liquibase.
- `application.yml:9-13` : `ddl-auto: update` (et `open-in-view: true`).
- `docker/docker-compose.yml:24` ne monte **que** `schema.sql`.
- Les 7 fichiers `migration-v2..v7.sql` (539 lignes) ne sont **exécutés par personne** : ni Docker,
  ni la CI, ni un script, ni la documentation. Ce sont des **artefacts morts**.
- Écart mesuré : `schema.sql` crée 40 tables, les migrations en ajoutent 20 (51 noms distincts),
  alors que JPA déclare **64 noms de tables distincts** (`@Table`) et que la base réelle en compte **71**.
  L'écart est comblé silencieusement par Hibernate au démarrage en production.
- Aggravant : `migration-v4.sql:35/47/62/94` fait des `ALTER TABLE` sur des tables
  (`academic_fields`, `programs`, `semesters`, `lmd_enrollments`) qu'**aucun script ne crée**.

*Correctif :* introduire Flyway (baseline sur `schema.sql`, puis `ddl-auto: validate`), **ou** assumer
explicitement Hibernate et supprimer les scripts morts. L'entre-deux actuel est le pire des cas.

### P0-3 — Secrets par défaut présents dans le dépôt 🔴

| Secret | Emplacement | Valeur |
|---|---|---|
| Clé de signature JWT | `application.yml:37`, `docker/docker-compose.yml:46`, `docker/.env.example:10` | `Y2hhbmdlLXRoaXMtc2VjcmV0LWtleS1pbi1wcm9kdWN0aW9uLXdpdGgtYTM3LWNyYXdhbGxlbg==` → décodé : « change-this-secret-key-in-production-with-a37-crawalleg » |
| Mot de passe admin | `application.yml:62-64`, `DataInitializer.java:61`, `schema.sql:767` | `Admin@123` (+ hash bcrypt public dans le dépôt) |
| Mot de passe MySQL | `docker-compose.yml:14/45` | `root` |

La clé JWT étant **publique dans le dépôt**, quiconque y a accès peut **forger des jetons valides**
si `JWT_SECRET` n'est pas surchargée. `SecurityStartupWarning.java:30-33` détecte ce cas mais se
contente d'un `log.warn` puis `return` — **le démarrage n'est pas bloqué**.

Bonne pratique constatée : `docker/.env` **n'est pas** versionné (seul `.env.example` l'est).

*Correctif :* rendre `JWT_SECRET` obligatoire (échec au démarrage, pas un avertissement) ; retirer la
clé par défaut du dépôt et la rotationner ; supprimer l'admin seedé de `schema.sql` ; forcer le
changement de mot de passe à la première connexion.

### P0-4 — IDOR confirmé : un étudiant lit les résultats d'un autre étudiant 🔴 *vérifié*

`AccessControlService` existe et est correct (`assertCanAccessStudent`, 75 lignes : admin = tout,
PARENT = seulement ses enfants, ELEVE/ETUDIANT = seulement son propre profil). **Mais il n'est
injecté que dans 5 contrôleurs sur 29** (Student, Payment, Attendance, Convocation, Lmd).

Dans `LmdController`, le garde-fou est appliqué **de façon incohérente** — les paires
« protégé / non protégé » cohabitent dans le même fichier :

| Endpoint | Ligne | Garde |
|---|---|---|
| `GET /api/lmd/enrollments?studentId=` | 262 | ✅ `assertCanAccessStudent` |
| `GET /api/lmd/enrollment-history?studentId=` | 284-288 | ❌ **aucun** |
| `GET /api/lmd/ec-evaluations?studentId=` | 292-296 | ❌ **aucun** |
| `GET /api/lmd/result?studentId=&fieldId=&semester=` | 342-348 | ❌ **aucun** |
| `GET /api/lmd/releve/pdf?studentId=` | 378 | ✅ |
| `GET /api/lmd/student-university-attendances?studentId=` | 432-437 | ❌ **aucun** |

Le rôle **ETUDIANT détient `LMD_READ`** (`DataInitializer.java:122-124`), suffisant pour
`/api/lmd/**` en lecture (`SecurityConfig.java:106`). Un compte étudiant peut donc, en changeant
`studentId`, obtenir **moyenne, crédits validés, UE à repasser, mention, notes EC, historique
d'inscription et présences** d'un autre étudiant.

*Correctif :* appeler `assertCanAccessStudent(studentId)` aux lignes 284, 292, 342 et 432, **et
ajouter un test d'intégration MockMvc** qui vérifie un 403 en accès croisé — c'est son absence qui
rend ce type de faille invisible (§7).

### P0-5 — Accès enseignant non cloisonné sur notes et bulletins 🟠 *vérifié, cadrage corrigé*

`ExamController` n'injecte **pas** `AccessControlService`. Ses endpoints portés par un élève
(`GET /api/exams/student/{studentId}/grades` :107, `/average` :113, `/student/{studentId}/bulletins`
:148, `/bulletins/{bulletinId}/pdf` :155, `/{examId}/grades` :94) ne vérifient aucune propriété.

**Correction d'un diagnostic automatique :** ces endpoints sont protégés par `PERM_EXAM_READ`
(`SecurityConfig.java:72`), que le rôle **ELEVE ne détient pas** (`DataInitializer.java:118-120` :
GRADE_READ, ATTENDANCE_READ, PAYMENT_READ, SCHEDULE_READ, COMMUNICATION_READ). Un élève reçoit donc
un **403**, et non les notes de ses camarades. L'affirmation « n'importe quel élève lit les notes de
tous » est **inexacte**.

Le constat réel, lui, tient : `PERM_EXAM_READ` est détenu par **ENSEIGNANT, SECRETAIRE, DIRECTEUR,
SUPER_ADMIN**, or un enseignant n'a aucune raison de lire les bulletins et moyennes d'élèves qu'il
n'enseigne pas. **C'est un excès de privilège enseignant, pas une fuite ouverte aux élèves.**

*Correctif :* injecter `AccessControlService` dans `ExamController` et cloisonner les lectures
portées par un élève selon les classes réellement enseignées.

### P0-6 — Un parent peut lister **tous** les élèves de l'établissement 🔴 *vérifié*

Chaîne complète vérifiée, de l'interface jusqu'à la requête SQL :

| Maillon | Emplacement | Constat |
|---|---|---|
| Route frontend | `routes/index.jsx:79` | `PARENT` autorisé sur `/students`, qui rend `StudentsPage` |
| Page | `StudentsPage.jsx:420` (`studentApi.search`) | appelle la liste complète paginée |
| Permission | `SecurityConfig.java:64` | `GET /api/students/**` → `PERM_STUDENT_READ` |
| Rôle | `DataInitializer.java:115-117` | **PARENT détient `STUDENT_READ`** |
| Contrôleur | `StudentController.java:45-55` | `search` **n'appelle pas** `accessControlService` (seul `getById:60` le fait) |
| Service | `StudentService.java:53-60` | `search` exécute `studentRepository.search(...)` **sans aucun filtre par rôle ni par parent** |

Conséquence : **un compte parent peut parcourir l'intégralité du fichier élèves** (noms, matricules,
classes, statuts) — donc les données personnelles de tous les mineurs de l'établissement — sans
aucune manipulation. Ce n'est pas une faille théorique : le rôle parent y est explicitement autorisé
côté interface **et** côté API.

À noter : la fiche individuelle `/students/{id}` est, elle, **correctement protégée**
(`StudentController.java:60` → `assertCanAccessStudent`). Seule la **liste** ne l'est pas.

*Correctif :* dans `StudentService.search`, si l'appelant est PARENT (ou ELEVE/ETUDIANT), forcer le
filtrage sur les élèves rattachés à son compte, comme le fait déjà `AccessControlService`.

---

## 5. P1 — Robustesse et sécurité résiduelle

| # | Constat | Preuve | Correctif |
|---|---|---|---|
| 1 | **Pagination non bornée** — 0 `@Max` dans les 29 contrôleurs, 23 `PageRequest.of(page, size)` directs ; le front demande `size=500` (`ConvocationsPage`) ; `page=0&size=1000000` accepté | grep `@Max` dans `controller/` → **0** | borner `size` (`@Max(200)`) et valider `page` |
| 2 | **Les refus d'accès renvoient 400, pas 403** — `AccessControlService` lève `BusinessException`, mappée sur `BAD_REQUEST` (`GlobalExceptionHandler.java:37-41`) | idem | introduire une exception dédiée → 403 |
| 3 | **Restauration de sauvegarde à moitié appliquée possible** — `restoreBackup` est `@Transactional` mais exécute du DDL (`DROP`/`CREATE`), qui **commite implicitement** en MySQL : aucun rollback. Le `SET FOREIGN_KEY_CHECKS = 1` du `finally` peut atterrir sur une **autre connexion du pool** | `BackupService.java:242-282` | retirer `@Transactional`, exécuter sur une connexion dédiée. *À créditer : la whitelist d'instructions (marqueur `-- SMS BACKUP`, découpage conscient des littéraux, `isSingleParenthesised`) est solide et couverte par 23 tests.* |
| 4 | **`/uploads/**` est public** — les documents téléversés (bulletins PDF, justificatifs, photos) sont servis sans authentification | `SecurityConfig.java:55`, `WebConfig.java:23` | exiger un jeton, ou servir via un contrôleur avec contrôle IDOR |
| 5 | **N+1 dans le calcul des moyennes** — `averageForStudent` déréférence `exam` et `exam.subject` (LAZY) par note ; `rankStudents` l'appelle par élève ; `generateBulletins` ajoute un `findById` par élève | `GradeService.java:115-126/135-143/166-183` | `@EntityGraph` + une requête d'agrégat |
| 6 | **Graphe d'autorités EAGER rechargé à chaque requête** — `User.roles` et `Role.permissions` en `EAGER`, `JwtAuthenticationFilter` appelle `loadUserByUsername` à chaque appel ; 0 `@EntityGraph`/`JOIN FETCH` dans tout le code | `User.java:74`, `Role.java:30`, `JwtAuthenticationFilter.java:46` | passer en LAZY + mettre les autorités en cache |
| 7 | **27 `@RequestBody` sans `@Valid`, 16 réponses d'entités brutes** — permet le *mass assignment* (`id`, relations) | `LmdController.java:44,63,82,101,120,180,200,219,239,300,321,410`… | DTO de requête + `@Valid` |
| 8 | **Injection de formules Excel** — valeurs écrites telles quelles via `setCellValue(String)` ; un champ commençant par `=`, `+`, `-`, `@` devient une formule vivante dans le `.xlsx` exporté | `ReportService.java:943-947` | préfixer par `'` |
| 9 | **Jeton d'un utilisateur supprimé → 500 au lieu de 401** — `loadUserByUsername` lève `UsernameNotFoundException`, non capturée par `JwtExceptionFilter` (qui ne traite que `ExpiredJwtException`/`JwtException`/`IllegalArgumentException`) | `JwtAuthenticationFilter.java:46`, `JwtExceptionFilter.java:30-34` | capturer et renvoyer 401 |
| 10 | **Token de rafraîchissement sans recontrôle du compte** — `AuthService.java:143-168` ne vérifie ni `isEnabled()` ni `isAccountNonLocked()`. **Non exploitable** : `JwtAuthenticationFilter.java:49-51` recontrôle ces deux états à *chaque* requête, donc les jetons émis sont inutilisables | idem | ajouter le contrôle (défense en profondeur) |
| 11 | **Identifiants en clair dans les journaux d'audit** — `getRemoteAddr()` derrière nginx renvoie l'IP du proxy, pas du client | `AuditService.java:28` | lire `X-Forwarded-For` |
| 12 | **`db-backup` code en dur la base `school_management`** au lieu de `${DB_NAME}` : la sauvegarde quotidienne échoue si le nom change | `docker-compose.yml:106` | utiliser `${DB_NAME}` |
| 13 | **Modules sans couverture d'autorisation** — `/api/my/**` et `/api/parents/**` ne sont couverts par **aucune règle explicite** : ils tombent sur `anyRequest().authenticated()`. La protection repose **entièrement** sur le service (elle existe : `ParentService.java:172`, `MySpaceService`) | `SecurityConfig.java:140` | ajouter des règles de route explicites (défense en profondeur) |
| 14 | **`@PreAuthorize` utilisé 2 fois sur 302 endpoints** malgré `@EnableMethodSecurity` : toute l'autorisation repose sur le filtrage d'URL. Un nouveau contrôleur sur un chemin non listé est ouvert à tout compte authentifié | `TeacherPayrollController.java:247,256` | annoter les services sensibles |

---

## 6. Traçabilité du déploiement — l'environnement ne correspond à aucun commit 🟠

Éléments mesurés :

| Élément | Valeur |
|---|---|
| `HEAD` | `af1e5f3` — **2026-09-25** |
| Date d'analyse | 2026-10-09 → **14 jours d'écart** |
| Image `school-management-backend:latest` | construite il y a **2 jours** |
| Image `school-management-frontend:latest` | construite il y a **6 jours** |
| Conteneur `school-backend` | démarré le 2026-10-07 |
| Conteneur `school-frontend` | démarré le 2026-10-03 |
| Tag résiduel | `school-frontend:latest` vieux de **3 semaines** |
| Arbre de travail | **27 fichiers modifiés + 12 non suivis** ≠ déployé |

Conclusion : **les deux moitiés de la stack en cours ne proviennent pas du même build** (4 jours
d'écart) et rien ne relie l'environnement en cours à un commit. Un diagnostic de production est donc,
en l'état, non reproductible. *Correctif :* étiqueter les images avec le SHA du commit et exposer ce
SHA dans `/actuator/info`.

---

## 7. Qualité, tests, CI

**Backend — 444 tests, 0 échec, mais structurellement aveugle.**
Les 24 classes de test sont **exclusivement** des tests unitaires Mockito (`@ExtendWith(MockitoExtension.class)`).
**Zéro** `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, `MockMvc` ou `spring-security-test`, alors que
les deux starters sont au classpath (`pom.xml:140-150`). Aucun test de contrôleur, de filtre, de
`SecurityConfig`, de `GlobalExceptionHandler`, de `JwtService` ni de `DataInitializer`.
`AuthServiceTest` (26 tests, verrouillage et rotation des jetons) et `AccessControlServiceTest` sont de
bonne qualité — **mais rien ne vérifie que les contrôleurs *appellent* `AccessControlService`** :
c'est exactement pourquoi l'IDOR LMD (§P0-4) passe inaperçu.

**Frontend — 64 tests, dont la stabilité est fragile.**
Une première exécution de la suite complète a échoué : `routes.smoke.test.jsx:210`
(« PARENT : rendu complet »), soit **1 échec sur 64**. Le même fichier relancé **en isolation passe 7/7**,
et la suite complète relancée passe **64/64**. L'échec est donc un **flake de timing** (le `findByText`
par défaut attend 1 000 ms ; la suite met 150–350 s sur cette machine). **La CI peut donc virer au rouge
sans régression réelle.** *Correctif :* augmenter le timeout par défaut (`testTimeout`, `asyncUtilTimeout`).

**ESLint : 0 erreur** — la CI exécute bien `lint` avant les tests (`ci.yml:93-99`), ce qui est une
excellente pratique.

**La CI ne valide jamais le SQL.** Aucune étape n'exécute `schema.sql` ou les migrations : c'est
précisément pourquoi P0-1 et P0-2 passent inaperçus. Le job `docker` construit les deux images
(seule validation des `Dockerfile`) mais **ne démarre pas la stack**. Enfin la CI utilise `mysql:8.0`
alors que la production utilise `mysql:8.4`.

**25 tests ne sont pas dans Git.** `AcademicYearServiceTest.java` (462 lignes) est **non suivi** :
ces 25 tests tournent en local mais **la CI ne les exécute pas**.

---

## 8. Travail en cours non commité — à traiter en priorité

Le delta (27 fichiers, +802 / −466 lignes) correspond aux correctifs issus de l'analyse de bugs du
2026-09-26. Il contient de **vraies corrections de fond**, actuellement **ni commitées, ni déployées,
ni couvertes par la CI** :

- `GradeService` **normalise désormais les notes par leur barème** (`maxValue`) avant de calculer
  moyenne, rang et mention — corrige un bug de fond où une note /100 faussait tous les bulletins.
  `GradeRequest` retire le `@DecimalMax(20)` codé en dur associé.
- `SecurityConfig` **réordonne des règles** : `/api/communication/message-logs/**` (données
  personnelles) passe **avant** la règle générale des communications, et `/api/dashboard/stats`,
  `/university-stats`, `/university-report` (données financières) sont réservés aux rôles habilités.
  Ce sont des durcissements réels.
- 20 pages frontend + `Navbar.jsx` remaniés (essentiellement de la mise en page responsive).

**Ces correctifs de sécurité ne protègent rien tant qu'ils ne sont pas déployés.**

---

## 9. Hygiène du dépôt et conformité de la documentation

**Racine :** 53 artefacts, **1,29 Mo** — dont `_blog.txt` (1,04 Mo, 81 % du volume).
**4 de ces fichiers sont réellement suivis par Git** malgré le `.gitignore` :
`AUDIT_SUMMARY.md`, `backend_full_tail.txt`, `backend_health_debug.txt`, `_health.json`.
Aucun motif `.verify-*` n'existe dans le `.gitignore` (8 fichiers concernés).

**Dérive documentaire** — le code a évolué beaucoup plus vite que les documents :

| Document | Affirme | Réalité vérifiée |
|---|---|---|
| `README.md` | Périmètre scolaire classique | **+ université/LMD, stages, mémoires, alumni, candidatures, paie à l'heure** ; ne lie que 4 docs sur 8 |
| `docs/AUDIT.md` | « ABSENTES » : convocations, stages, mémoires, alumni, candidatures, examens universitaires | **Toutes livrées** (contrôleurs + pages + migrations) |
| `docs/AUDIT.md` | `include-message: always` | `never` (`application.yml:33`) |
| `docs/ARCHITECTURE.md:35-43` | 28 contrôleurs / 27 entités / 21 services | **29 / 67 / 42** |
| `docs/FONCTIONNALITES.md:14` | Java 17 | **Java 21** (`pom.xml:21`) |
| `docs/DIAGRAMMES.md:67` | table `BULLETIN_SUBJECTS` | **0 occurrence dans le code** |
| `docs/INSTALLATION.md`, `DEPLOIEMENT_CLIENT.md` | s'en remettent à `ddl-auto: update` | contredit P0-1 |

---

## 10. Frontend — constats vérifiés

### 10.1 Les identifiants d'administration sont affichés sur l'écran de connexion 🔴 *vérifié*

`i18n/locales/fr.js:95` (et `en.js:95`) :

```
'login.encrypted': 'Connexion chiffrée — Compte par défaut : admin / Admin@123'
```

Cette clé est rendue sur la page publique de connexion (`LoginPage.jsx:139`), **dans les deux
langues**. Combinée au fait que `Admin@123` est effectivement le mot de passe super-admin par défaut
(§P0-3), l'application **public elle-même ses identifiants d'administration** sur son écran d'entrée.
*Correctif :* supprimer la clé `login.encrypted` (ou la vider hors développement).

### 10.2 Le loader global bloque toute l'interface à chaque requête 🟠 *vérifié*

`api/axios.js:16-27` incrémente un compteur global pour **toute** URL hors `/auth/`. `Loader.jsx`
s'abonne à ce compteur ; il est monté **sans la prop `open`** dans `DashboardLayout.jsx:50`, donc
`visible = pending`. Résultat : un `Backdrop` plein écran avec flou (`zIndex drawer + 9999`) recouvre
l'application **à chaque appel API**, y compris les mutations CRUD.
*Correctif :* retirer ce `<Loader />` global et s'appuyer sur les états de chargement locaux, déjà
supportés par `DataTable`.

### 10.3 Les gardes de rôle sont purement cosmétiques (côté client) 🟡 *vérifié*

`Guards.jsx:32-44` (`RoleRoute`) lit `state.auth.user.roles`, hydraté depuis `localStorage`
(`authSlice.js:5-20`) **sans revalidation serveur**. Modifier `localStorage.user.roles` affiche
n'importe quelle page. **L'impact réel est faible** car le backend applique ses propres règles par
permission et renvoie 403 — mais l'interface ne doit pas être considérée comme un contrôle.
*Correctif :* revalider les rôles via un `GET /auth/me` au démarrage.

### 10.4 i18n déclaré mais quasi inutilisé 🟠 *vérifié*

Infrastructure complète (contexte + `fr.js`/`en.js`, **128 clés symétriques**), mais **seulement
5 fichiers sur 78** appellent `useI18n` : `Sidebar`, `Navbar`, `DataTable`, `LoginPage`,
`DashboardPage`. **~92 % de l'interface reste en français codé en dur** — la bascule de langue est
essentiellement cosmétique. Sont notamment non traduisibles car sans clé : `EmptyState.jsx:7`,
`ConfirmDialog.jsx`, `Loader.jsx:9`, `PageHeader`. De plus, dates et montants sont figés en `fr-FR`
(`utils/format.js:6,16,28`) alors que `DashboardPage.jsx:125` adapte la locale — incohérence interne.
*Correctif :* rendre les composants partagés paramétrables par clé, puis migrer page par page.

### 10.5 Tailwind est une dépendance morte 🟡 *vérifié*

`tailwind.config.js` et `postcss.config.js` existent, `src/styles/index.css:1-3` charge les
directives `@tailwind`, mais **aucune classe utilitaire Tailwind n'apparaît dans les JSX** (recherche
sur `className` : 0 occurrence). Seules les classes du `@layer components` (`.page-title`,
`.hero-banner`, `.filter-card`…) sont utilisées. S'ajoute un **triple reset CSS** en conflit :
`@tailwind base` (preflight) + `index.css:5-9` (`* { margin:0; padding:0 }`) + `<CssBaseline/>`
(`AppProviders.jsx:15`). Cela contredit le tableau des technologies du README pour 3 dépendances.
*Correctif :* soit migrer réellement vers les utilitaires, soit retirer Tailwind et écrire ces
~20 classes en CSS — mais pas conserver les deux resets.

### 10.6 Autres constats vérifiés

- **Police Inter chargée deux fois** : `<link>` Google Fonts (`index.html:9-11`) **et**
  `import '@fontsource-variable/inter'` (`main.jsx:3`), en plus des fichiers woff2 du bundle.
  Dépendance externe inutile au CDN Google.
- **Bundle non découpé** : aucun `manualChunks` dans `vite.config.js` ; MUI et Recharts sont dans le
  chunk d'entrée (~1,15 Mo).
- **Accessibilité très faible** : **2 `aria-label`** dans tout `src/`, aucun `role=` ; les boutons
  d'action de `DataTable` ne sont accessibles que par `Tooltip`.
- **43 styles inline**, concentrés dans `SettingsPage.jsx:330-355` (un `<table>` HTML brut au lieu de
  `Table` MUI).
- **Contrats de pagination hétérogènes** : `DataTable` normalise en `onPageChange(page)` (10 pages
  passent `setPage`, correct), tandis que `CandidaturesPage:170` et `ConvocationsPage:197` utilisent
  leur **propre** `TablePagination` avec la signature MUI brute `(_, page)` — également correcte,
  mais c'est un piège pour toute modification future. À unifier.
- **~60 erreurs d'amorçage avalées** (`catch(() => {})`) → écran vide sans message ni « Réessayer ».
- **Redux réel mais limité à un cache de session** (auth, theme, toast) ; trois sources de vérité
  pour l'identité (`utils/auth.js` sur `localStorage`, Redux, `RoleDashboard`).
- **Points positifs confirmés** : 1 seul `console.*` (légitime, dans `ErrorBoundary`), **0
  `TODO/FIXME`**, `ErrorBoundary` en place, Redux vivant, aucun secret versionné (seul
  `docker/.env.example` existe), et `eslint.config.js` correctement configuré (0 erreur).

### 10.7 Ce qui est propre côté rafraîchissement de jeton ✅

Deux « bugs critiques » rapportés par un audit automatique se révèlent **inexacts**, et il ne faut
donc pas « corriger » ce code :

- **La file d'attente réattache bien le nouveau Bearer.** `axios.js:55` relance les requêtes en
  attente via `api(originalRequest)` ; l'intercepteur de requête (`axios.js:16-20`) réinjecte alors
  le jeton **depuis `localStorage`**, qui vient d'être mis à jour (ligne 71) **avant** la
  résolution de la file (ligne 73). L'ordre est correct.
- **Le nouveau `refreshToken` est bien persisté.** `axios.js:72` exécute
  `localStorage.setItem('refreshToken', data.data.refreshToken)` — la ligne existe et s'exécute.
  *Réserve :* si le backend ne renvoyait pas de `refreshToken` en rotation, la valeur stockée
  deviendrait la chaîne `"undefined"` ; le contrat serveur n'a pas été vérifié ici.

---

## 11. Corrections apportées à des analyses antérieures

Deux documents circulent déjà dans le dépôt ; plusieurs de leurs constats sont **périmés ou inexacts**.
Les signaler évite de « corriger » du code déjà sain.

| Affirmation rencontrée | Statut | Ce qui est vrai |
|---|---|---|
| `RAPPORT_ANALYSE.md` : `restoreBackup` exécute du SQL arbitraire | **Périmé** | Le service a été durci : marqueur `-- SMS BACKUP`, whitelist d'instructions, découpage conscient des littéraux, 23 tests. Reste le problème transactionnel (§P1-3) |
| `RAPPORT_ANALYSE.md` : « 71 tests backend » | **Périmé** | **444 tests** |
| Audit : « `PUT /api/settings/cycles` ouvert à tout compte authentifié » | **Faux positif** | La règle permissive est liée à `HttpMethod.GET` (`SecurityConfig.java:136`). Un `PUT` ne la satisfait pas et retombe sur `hasRole("SUPER_ADMIN")` (`:138`) — correctement protégé |
| Audit : « tout ELEVE lit les notes de ses camarades via `/api/exams/**` » | **Inexact** | ELEVE ne détient pas `PERM_EXAM_READ` → 403. Le vrai problème est l'**excès de privilège enseignant** (§P0-5) |
| Audit : « `/actuator/health` avec `show-details: always` expose la base » | **Non reproduit** | Réponse publique observée : `{"status":"UP","groups":["liveness","readiness"]}`, sans détail. Risque latent à surveiller |
| `RAPPORT_ANALYSE.md` : « `schema.sql` est monté par Docker » | **Incomplet** | Il est monté, **mais il échoue** (§P0-1) |
| Audit frontend : « la file d'attente de refresh repart avec l'ancien jeton » | **Faux positif** | `axios.js:16-20` réinjecte le jeton depuis `localStorage`, mis à jour avant la résolution de la file (§10.7) |
| Audit frontend : « le nouveau `refreshToken` n'est jamais persisté » | **Faux positif** | `axios.js:72` le persiste explicitement |
| Audit frontend : « pagination morte sur `CandidaturesPage`/`ConvocationsPage` » | **Faux positif** | Ces deux pages utilisent leur **propre** `TablePagination`, où `(_, page)` est la signature MUI correcte (§10.6) |
| Audit frontend : « `DataTable` n'applique jamais la page courante » | **Non concluant** | Les pages consommatrices travaillent en pagination **serveur** et lui passent déjà la tranche courante : comportement correct. Contrat ambigu, pas un bug (§10.6) |
| Audit frontend : « les gardes de rôle sont une faille critique » | **Sévérité à revoir** | Le contournement est purement visuel : le backend applique ses règles par permission et renvoie 403. À classer MINEUR (§10.3) |

---

## 12. Plan d'action priorisé

### P0 — à traiter avant toute mise en production
1. **Réparer `schema.sql`** (5 instructions : lignes 572, 589, 614, 635, 656) — sans quoi aucune
   installation neuve ni reprise après sinistre n'est possible.
2. **Ajouter une étape CI qui exécute réellement le SQL** sur un MySQL neuf — c'est le seul moyen
   d'empêcher la réapparition de P0-1.
3. **Introduire Flyway** (baseline `schema.sql`, `ddl-auto: validate`) ou assumer Hibernate et
   supprimer les 7 migrations mortes.
4. **Éliminer les secrets par défaut** : `JWT_SECRET` obligatoire (échec au démarrage), retrait +
   rotation de la clé publique du dépôt, suppression de l'admin seedé, changement de mot de passe
   forcé à la première connexion.
5. **Retirer les identifiants `admin / Admin@123` de l'écran de connexion** (`fr.js:95`, `en.js:95`).
6. **Fermer l'exposition du fichier élèves aux parents** : filtrer `StudentService.search` par les
   élèves rattachés au compte (P0-6).
7. **Fermer l'IDOR LMD** (4 sites) et **cloisonner `ExamController`**, avec un test d'intégration
   MockMvc vérifiant un 403 en accès croisé.
8. **Committer ou annuler** le delta en cours : il contient des correctifs de sécurité non déployés.

### P1 — robustesse
9. Borner la pagination (`@Max`), renvoyer **403** et non 400 sur refus d'accès.
10. Rendre `/uploads/**` authentifié.
11. Corriger la restauration (DDL hors transaction, connexion dédiée) et utiliser `${DB_NAME}` dans
    le service `db-backup`.
12. `@Valid` + DTO sur les 27 corps de requête ; protection contre l'injection de formules Excel.
13. Traiter les N+1 (`@EntityGraph`) et passer `User.roles`/`Role.permissions` en LAZY.
14. Retirer le `<Loader />` global bloquant (`DashboardLayout.jsx:50`) au profit des états locaux.
15. Étiqueter les images Docker avec le SHA du commit et exposer ce SHA dans `/actuator/info`.
16. Stabiliser la suite frontend (timeouts) et committer `AcademicYearServiceTest.java`.

### P2 — hygiène et cohérence
17. Nettoyer la racine (53 artefacts, 1,29 Mo) ; retirer les 4 fichiers suivis à tort ; compléter
    le `.gitignore` (`.verify-*`) ; supprimer les 4 répertoires de build `dist*` obsolètes.
18. Réécrire `README`, `ARCHITECTURE`, `FONCTIONNALITES`, `AUDIT`, `DIAGRAMMES` depuis l'état réel
    du code.
19. Trancher sur i18n (**terminer** la traduction ou renoncer à l'anglais) et sur Tailwind
    (**l'utiliser** ou le retirer) ; lever le conflit des trois resets CSS.
20. Unifier le manifeste de routes (RBAC aujourd'hui dupliqué entre routeur, menu et `utils/auth.js`)
    et les contrats de pagination.
21. Durcir Docker/Nginx : `USER` non-root, limites mémoire/CPU, images épinglées par digest, MySQL
    non publié sur l'hôte, en-têtes de sécurité nginx (CSP, `X-Frame-Options`, HSTS), healthcheck
    frontend ; découper le bundle Vite et supprimer le double chargement de la police Inter.

---

## 13. Conclusion

**Ce qui est solide :** l'architecture en couches est propre et respectée (Controller → Service →
Repository), les DTO et MapStruct sont en place, la gestion d'erreurs est centralisée, `AccessControlService`
est une réponse juste au problème IDOR, `BackupService` a été durci avec rigueur, la couverture de tests
des services à risque est réelle et verte (444 tests), ESLint est branché en CI, la CI construit les
images, la sauvegarde quotidienne est automatisée, et le `docker/.env` n'est pas versionné.
Le périmètre fonctionnel — scolaire **et** universitaire LMD — est rare et impressionnant.

**Ce qui bloque :** la **reproductibilité**. Un projet ne peut pas être considéré comme livrable quand
`docker compose up` échoue sur une machine neuve, quand trois sources de schéma concurrentes
(`schema.sql`, 7 migrations mortes, `ddl-auto: update`) divergent de 20 tables, quand la clé de
signature des jetons est publique dans le dépôt, et quand l'environnement en cours ne correspond à
aucun commit.

**Le chemin est court, mais il est ordonné :** les points 1 à 8 du plan P0 sont bien délimités,
chacun a un correctif précis identifié, et aucun ne remet en cause les fondations. Traiter ces huit
points ferait passer le projet d'« application prometteuse impossible à installer » à
« application livrable ».
