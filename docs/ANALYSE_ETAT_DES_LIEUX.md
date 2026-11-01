# Analyse — état des lieux technique

> Analyse statique du dépôt au **2026-11-01** (commit `6b24988`, 2026-10-28).
> Complète `docs/ANALYSE_PROJET.md` (audit P0/P1 du 2026-10-28) en vérifiant
> **ce qui a effectivement été corrigé depuis** et ce qui reste ouvert.

---

## 1. Synthèse

Sahel Edu est un système de gestion scolaire multi-établissement, **fonctionnellement
complet et d'un niveau d'ingénierie nettement supérieur à la moyenne** des projets de
ce type : 284 endpoints, 53 entités, migrations versionnées, tests d'intégration sur
MySQL réel, chaîne de déploiement Docker + scripts Ubuntu + bundle hors ligne.

Les six constats P0 de l'audit du 28 octobre sont **traités** (vérifié dans le code,
pas seulement dans le changelog). Le risque résiduel n'est plus applicatif mais
**d'exploitation** : conteneurs root, base accédée en root, sauvegardes non
externalisées, dépendances non mises à jour.

| Dimension | Note | Commentaire |
|---|---|---|
| Couverture fonctionnelle | ★★★★★ | 12 modules métier, 284 endpoints |
| Architecture backend | ★★★★☆ | Couches nettes, quelques classes-dieux |
| Sécurité applicative | ★★★★☆ | IDOR centralisé, secrets obligatoires |
| Sécurité d'exploitation | ★★★☆☆ | Conteneurs root, base en root, pas d'offsite |
| Tests | ★★★★☆ (back) / ★★☆☆☆ (front) | 23 k LOC de tests back, 1,7 k front |
| Maintenabilité | ★★★☆☆ | God classes, i18n à moitié fait |
| Documentation | ★★★★☆ | Abondante, avec redondances |

---

## 2. Chiffres clés

| Métrique | Valeur |
|---|---|
| Contrôleurs REST | 32 |
| Endpoints | 284 |
| Entités JPA / repositories / services | 53 / 52 / 47 |
| Code Java (principal) | 30 885 lignes |
| Code Java (tests) | 23 034 lignes — 78 fichiers |
| Code frontend | 23 616 lignes — 112 fichiers |
| Code frontend (tests) | 1 694 lignes — 14 fichiers |
| Migrations Flyway | 12 (V1 baseline 74 Ko + V2→V12) |
| Rôles | 7 (SUPER_ADMIN, DIRECTEUR, COMPTABLE, SECRETAIRE, ENSEIGNANT, PARENT, ELEVE) |
| Commits | 122, dernier le 2026-10-28 |
| Fichiers versionnés | 610 |

---

## 3. Ce qui est solide (vérifié)

### 3.1 Schéma de base maîtrisé
`ddl-auto: validate` + Flyway comme source unique de vérité, `baseline-on-migrate`
pour les bases existantes, `out-of-order: false`. `database/schema.sql` n'est plus
exécuté. Le P0 « installation neuve impossible » est bien refermé.

### 3.2 Secrets et démarrage sûr
`JWT_SECRET` sans valeur par défaut, `REQUIRE_SECURE_SECRETS=true`,
`SecurityStartupWarning` qui **fait échouer le démarrage** si le secret est vide,
trop court ou resté celui du dépôt, et refuse un `ADMIN_PASSWORD` vide ou égal à
`Admin@123`. `.env` n'est pas versionné (`git ls-files` le confirme).

### 3.3 IDOR traité de façon centralisée
`AccessControlService` implémente un vrai cloisonnement par périmètre :
`assertCanAccessStudent`, `assertCanAccessClass`, `assertCanTeachSubjectInClass`,
et surtout `resolveStudentScope` qui **ramène toute liste non filtrée au périmètre du
compte** au lieu de renvoyer l'établissement entier. Les trois P0 d'accès horizontal
(parent voyant tous les élèves, élève lisant les notes d'un autre) sont refermés.

### 3.4 Garde structurelle sur les autorisations
`AuthorizationCoverageTest` lit la source de `SecurityConfig` et **échoue si un
endpoint n'est couvert par aucune règle explicite**, avec une liste de replis assumés
et justifiés. C'est rare et particulièrement pertinent ici : la chaîne se termine par
`.anyRequest().authenticated()`, donc un contrôleur ajouté sans règle serait ouvert à
tout compte connecté, silencieusement.

### 3.5 Tests d'intégration exigeants
14 classes `*IT` sur MySQL 8.4 via Testcontainers, et la CI **échoue si un test
d'intégration est ignoré** (`Skipped > 0` ou `Tests run: 0`). C'est exactement le
garde-fou qui empêche une migration Flyway cassée de passer inaperçue.

### 3.6 Exploitation
- `docker-compose` : healthchecks MySQL et backend (liveness), `depends_on:
  condition: service_healthy`, sidecar `db-backup` (dump quotidien + archive des
  uploads, rétention 14 jours).
- MySQL et backend **publiés sur 127.0.0.1 uniquement** — jamais exposés au LAN.
- nginx : CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`,
  `resolver 127.0.0.11` (corrige les 502 après redéploiement du backend),
  `proxy_buffering off` + `Range` pour la lecture vidéo.
- Uploads sur volume hôte (survivent à une reconstruction d'image).
- `deploy/` : `install.sh`, `update.sh`, `backup.sh`, `https.sh`, systemd, bundle
  hors ligne pour serveur sans Internet.
- Actuator : probes liveness/readiness, `show-details: when-authorized`.

### 3.7 Qualité applicative
`open-in-view: false` assumé avec `@EntityGraph` sur les endpoints concernés,
`include-message: never`, réponses 400/415 au lieu de 500 sur requête malformée,
enveloppe de réponse homogène, `GlobalExceptionHandler` normalisé, `LoginAttemptService`
contre le bourrage d'identifiants.

---

## 4. Risques et dette résiduels

### P1 — à traiter avant mise en production

| # | Constat | Impact | Correctif |
|---|---|---|---|
| 1 | Aucun `USER` dans `Dockerfile.backend` ni `Dockerfile.frontend` | Backend tourne en **root** avec le volume uploads monté | `useradd` + `USER app` (UID 10001), `chown` du volume |
| 2 | `DB_USERNAME: root` dans `docker-compose.yml` | L'application a tous les droits sur la base : une injection = dump complet, `DROP`, lecture des autres schémas | Utilisateur MySQL dédié, `GRANT` limité à `school_management` |
| 3 | Jetons en `localStorage` + CSP `script-src 'unsafe-inline'` | Toute XSS exfiltre l'access **et** le refresh token (7 jours) | Retirer `unsafe-inline` (Vite n'émet pas de script inline) ; à terme, cookie `HttpOnly` + `SameSite` |
| 4 | Sauvegardes dans un volume Docker du même hôte | Perte totale en cas de panne disque, ransomware ou sinistre | Copie hors site (rsync/S3), chiffrement, **test de restauration** documenté |
| 5 | Spring Boot 3.2.5 (mai 2024) alors que la 4.0 est sortie en janvier 2026 | Branche plus maintenue en open source : plus de correctifs de sécurité | Montée en 3.5/4.0 + Dependabot ou OWASP Dependency-Check dans la CI |
| 6 | Aucun rate limiting nginx sur `/api/auth/login` | Le `LoginAttemptService` protège le compte, pas la disponibilité (credential stuffing volumétrique) | `limit_req_zone` sur `/api/auth/` |
| 7 | Pas de `mem_limit`, `read_only`, `cap_drop` dans compose | Une fuite mémoire JVM ou un conteneur compromis déstabilise l'hôte | Limites + `cap_drop: ALL` + `read_only` sur le frontend |

**État au 2026-11-01** — traité depuis la rédaction de ce tableau :

- **#2 (base en root)** : corrigé. Compte `sahel_app` limité à `school_management`,
  créé par `install.sh` ou par `deploy/update.sh` sur une installation existante.
  Modèle de privilèges vérifié sur une instance MySQL réelle.
- **#6 (rate limiting nginx)** : corrigé. `limit_req` sur `/api/auth/`, 120 req/min
  par IP. La limitation applicative par IP était par ailleurs **contournable** :
  l'en-tête `X-Forwarded-For` était complété au lieu d'être écrasé, alors que le
  backend lit sa première valeur. Corrigé dans les deux nginx.
- **#4 (sauvegardes)** : partiellement traité. `OFFSITE_DIR` existait déjà ; la
  copie hors site est désormais vérifiée octet par octet, et un dump sans aucune
  table est refusé (`gzip -t` seul déclarait valide un dump inutilisable).
  **Reste ouvert** : `OFFSITE_DIR` demeure facultatif, et aucun chiffrement.
- **#3 (CSP)** : partiellement traité. `script-src` n'autorise plus
  `'unsafe-inline'` — vérifié dans un navigateur : la page se charge sans
  violation, un script en ligne injecté est bloqué. `style-src 'unsafe-inline'`
  reste **nécessaire** (MUI/Emotion injecte ses styles à l'exécution).
  **Reste ouvert** : les jetons sont toujours en `localStorage`.
- **#5 (Spring Boot)** : partiellement traité. Passage de **3.2.5 à 3.5.16**
  (dernière 3.5.x maintenue), avec springdoc 2.5.0 → 2.8.17 (la 2.5 ne connaît
  pas Spring Framework 6.2). Vérifié : 1 006 tests unitaires et 66 tests
  d'intégration sur MySQL réel, aucun échec, aucune propriété de configuration
  obsolète. **Reste ouvert** : la branche 4.x est une migration majeure
  (Spring Framework 7) qui demande sa propre étape.
- **#5 (veille automatique)** : corrigé. `.github/dependabot.yml` branche
  Dependabot sur les 4 écosystèmes (maven, npm, docker, github-actions),
  hebdomadaire. C'était la cause racine : sans veille, 3.5.16 vieillira
  exactement comme 3.2.5 et personne ne le verra. Voir la section suivante
  pour les majeures volontairement écartées.

Non traités : **#1** (conteneurs root), **#7** (limites de ressources).

### 4.1 Majeures en attente d'arbitrage (veille Dependabot)

Dependabot ne regroupe que les mises à jour **mineures et patch** ; chaque
majeure arrive donc en PR isolée. Les trois premières ont été vérifiées sur la
CI réelle le 2026-11-01 : aucune n'est fusionnable en l'état, et aucune n'est
un simple changement de numéro de version.

| Dépendance | Saut | Ce qui casse, précisément | Ce qu'il faut faire |
|---|---|---|---|
| Spring Boot | 3.5.16 → 4.1.2 | `HandlerMethodValidationException.getAllValidationResults()` supprimé ; `DaoAuthenticationProvider` n'a plus de constructeur sans argument (Spring Security 7) | Migration dédiée : réécrire `GlobalExceptionHandler` et `SecurityConfig`, springdoc 3.x |
| MUI | 5.18 → 9.5 | `@mui/icons-material/MailOutline` n'existe plus : 3 suites de tests ne compilent même pas | Recenser les icônes et API retirées, migrer par paliers (5 → 7 → 9) |
| eslint | 9 → 10 | `eslint-plugin-react@7.37.5` plafonne à `^9.7` : `npm install` échoue en `ERESOLVE` | Bloqué en amont, rien à faire côté projet avant une version compatible |
| Tailwind CSS | 3.4 → 4.3 | Le plugin PostCSS a été déplacé : `It looks like you're trying to use tailwindcss directly as a PostCSS plugin` | Installer `@tailwindcss/postcss`, réécrire `postcss.config` et migrer la configuration CSS-first |
| OpenPDF | 1.3.39 → 3.0.5 | Le paquet change de nom : `package com.lowagie.text.pdf does not exist` | Renommer les imports `com.lowagie.*` en `com.github.librepdf.openpdf.*` — 72 occurrences dans 7 fichiers, dont `ReportService` et `SchoolDocumentTheme` |

**Couplage `eslint` / `@eslint/js`** : ces deux paquets doivent partager la même
majeure (`@eslint/js` 10 exige `eslint` ^10). Le motif `eslint*` ne capturait
pas le nom *scopé* `@eslint/js`, qui partait donc seul et cassait `npm install`
(PR #24). Ils sont désormais dans un groupe commun qui inclut les majeures.

Ces trois PR ont été fermées : Dependabot ne représente pas une version dont la
PR a été fermée, donc elles ne reviendront pas avant la version suivante. C'est
ce tableau qui fait office de rappel.

**Décision assumée** : `ignore: version-update:semver-major` n'est **pas**
utilisé. La documentation GitHub ne dit pas clairement si `ignore` s'applique
aussi aux mises à jour de sécurité ; si c'était le cas, une faille dans Spring
Boot 3.5.x passerait inaperçue — exactement le problème que la veille doit
résoudre. Fermer une PR de majeure donne le même résultat sans ce risque.


### P2 — dette à planifier

8. **God classes** : `ReportService` 1 224 lignes, `SchoolDocumentTheme` 752,
   `DocumentThemePage` 944, `StudentCardPage` 739, `TeacherPayrollPage` 696.
   Les services de 700+ lignes mélangent requêtage, mise en forme et rendu PDF.
9. **Tests frontend très fins** : 1 694 lignes pour 23 616 (7 %), 14 fichiers pour
   52 pages. La logique métier sensible côté client (délibérations, paie, barèmes,
   calcul de moyennes) n'est pas couverte.
10. **i18n à moitié migré** : `I18nProvider` + deux locales existent, mais seuls
    `Navbar`, `Sidebar`, `DataTable`, `LoginPage`, `Loader`, `EmptyState` et
    `ConfirmDialog` l'utilisent — **2 pages sur 52**. Le reste est en français en dur :
    l'interface est incohérente dès qu'on bascule en anglais. Terminer ou retirer.
11. **`npm install` au lieu de `npm ci`** dans `Dockerfile.frontend` : build non
    reproductible (une image reconstruite peut embarquer d'autres versions).
12. **Trois chemins de build** à maintenir : `Dockerfile.backend`,
    `Dockerfile.backend.jar`, `docker-compose.offline.yml`.
13. **Divergence CI/production** : la CI force `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
    pour les tests unitaires alors que la production est en `validate` — un décalage
    entité/schéma peut passer en test unitaire.
14. **`database/` reste un piège** : `schema.sql` (32 Ko) et `migration-v2..v8.sql`
    sont documentés comme héritage mais toujours présents à côté de `db/migration/`.
    À archiver ou supprimer.
15. **HSTS commenté** dans `nginx.conf` : dépend entièrement de `deploy/https.sh`.
    Une installation sans TLS n'a aucune protection contre le downgrade.
16. **Redondance documentaire** : `DEPLOIEMENT.md`, `DEPLOIEMENT_UBUNTU.md`,
    `DEPLOIEMENT_CLIENT.md`, `DEPLOIEMENT_HORS_LIGNE.md` + `ANALYSE_PROJET.md` et
    `AUDIT.md` se recouvrent partiellement — risque de divergence à terme.

### P3 — hygiène

17. Résidus locaux non versionnés (donc sans risque pour le dépôt, mais encombrants) :
    `frontend-react/dist*` (5 dossiers), 5 fichiers `vite.config.js.timestamp-*.mjs`,
    `_compile.log` / `_compile2.log`, dossier vide `backend-springboot/backend-springboot/jar/`.
18. Un seul auteur, 122 commits, pas de workflow de revue (push direct sur `main`).
19. Pas d'ADR ni de CHANGELOG : les décisions structurantes (Flyway, ACL centralisée,
    jetons de lecture vidéo) ne sont tracées que dans les commentaires de code — qui
    sont d'ailleurs excellents, mais non découvrables.

---

## 5. Plan d'action

**Sous 2 semaines — durcissement (P1)**
1. Utilisateur non-root dans les deux Dockerfiles.
2. Utilisateur MySQL dédié + `GRANT` restreint.
3. Retirer `unsafe-inline` de la CSP et vérifier le build Vite.
4. `limit_req` sur `/api/auth/`.
5. Limites mémoire et `cap_drop` dans compose.

**Sous 2 mois — soutenabilité**
6. Montée Spring Boot + Dependabot dans la CI.
7. Sauvegarde hors site + procédure de restauration testée.
8. Découper `ReportService` (export PDF / calculs / requêtage).
9. Trancher sur l'i18n : finir la migration ou retirer le provider.

**Sous 6 mois — qualité**
10. Tests frontend sur les 5 pages métier les plus sensibles.
11. `npm ci` + build reproductible.
12. Archiver `database/`, consolider les docs de déploiement en une seule.

---

## 6. Verdict

Le projet est **prêt fonctionnellement** et sa sécurité applicative est sérieuse :
cloisonnement des données centralisé, secrets obligatoires, garde structurelle sur les
autorisations, tests d'intégration non contournables.

Ce qui manque n'est pas du code mais de l'**exploitation** : les conteneurs et la base
tournent avec des privilèges excessifs, et la seule copie des données vit sur le même
disque que les données. Ce sont les deux points qui, en cas d'incident réel dans un
établissement, coûtent le plus cher.

Les P1 1, 2 et 4 se corrigent en moins d'une journée et changent réellement le
profil de risque.
