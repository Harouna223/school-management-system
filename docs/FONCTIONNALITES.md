# 📚 SMS — School Management System
## Documentation fonctionnelle complète

**SMS** est une plateforme de gestion scolaire unifiée qui couvre les cycles d'enseignement
**Jardin → Primaire → Collège → Lycée**, ainsi que les **modules annexes du cycle Université**
(stages, mémoires, alumni, admission).

---

## 1. Vue d'ensemble

| Élément | Valeur |
|---|---|
| **Frontend** | React 18, Vite, Material UI 5, Redux Toolkit, Recharts, Formik/Yup, Axios |
| **Backend** | Java 17, Spring Boot 3.2, Spring Security JWT, Spring Data JPA, MapStruct |
| **Base de données** | MySQL 8 (utf8mb4) |
| **Documents** | OpenPDF (PDF) — Apache POI (Excel) — ZXing (QR Code) |
| **Déploiement** | Docker Compose (nginx + backend + MySQL) |
| **Accès** | http://localhost (admin / Admin@123) |

### Architecture multi-cycle

```
SMS
 └── ENSEIGNEMENT
      ├── JARDIN (PS, MS, GS)
      ├── PRIMAIRE (CP → CM2)
      ├── COLLÈGE (6ème → 3ème)
      ├── LYCÉE (2nde → Terminale)
      └── UNIVERSITÉ — modules annexes (stages, mémoires, alumni, admission)
```

Les cycles sont **activables/désactivables** dans *Paramètres → Cycles d'enseignement*. Le menu latéral s'adapte automatiquement.

---

## 2. Rôles et permissions

| Rôle | Accès principaux |
|---|---|
| **SUPER_ADMIN** | Toutes les fonctionnalités, paramètres, sauvegardes |
| **DIRECTEUR** | Administration complète : élèves, enseignants, classes, notes, finances, RH, utilisateurs, audit |
| **SECRETAIRE** | Scolarité : élèves, classes, notes, présences, communications |
| **COMPTABLE** | Finances : paiements, factures, dépenses, reçus |
| **ENSEIGNANT** | Ses classes, saisie des notes, présences, emploi du temps |
| **PARENT** | Ses enfants : bulletins, présences, notes, paiements |
| **ELEVE** | Son profil : bulletins, notes, emploi du temps, paiements |

Chaque rôle dispose de **permissions fines** (`PERM_STUDENT_READ`, `PERM_PAYMENT_WRITE`, `PERM_LMD_READ` / `PERM_LMD_WRITE` pour les modules annexes université, etc.) contrôlées côté backend.

---

## 3. Authentification et comptes

| Fonctionnalité | Description |
|---|---|
| **Connexion** | Login par nom d'utilisateur + mot de passe → JWT (access + refresh) |
| **Déconnexion** | Invalidation du refresh token |
| **Refresh token** | Renouvellement automatique du jeton (le frontend réessaie la requête) |
| **Verrouillage de compte** | 5 échecs → verrouillage temporaire avec compte à rebours |
| **Réinitialisation du mot de passe** | Par un administrateur (≥8 caractères, lettres + chiffres) |
| **Déverrouillage** | Un administrateur peut déverrouiller un compte verrouillé |
| **Activation/désactivation** | Un compte désactivé perd l'accès immédiatement (même avec JWT valide) |
| **Comptes liés** | Création automatique d'un compte élève/parent/enseignant lors de l'inscription |
| **Sécurité** | Hash bcrypt, JWT secret configurable, CORS configuré, contrôle d'accès IDOR |

---

## 4. Scolarité (Jardin → Lycée)

### 4.1 Élèves
- **CRUD complet** : inscription avec génération automatique de matricule, modification, radiation, réinscription, transfert de classe
- **Recherche avancée** : nom, matricule, classe, statut, **cycle d'enseignement**, pagination
- **Fiche élève détaillée** : identité, classe, parent, historique du parcours (transferts, radiations)
- **Photos** : upload et affichage de la photo de profil
- **Import Excel** : modèle téléchargeable, validation ligne par ligne, détection de doublons, rapport d'import
- **Export** : liste PDF et Excel
- **Documents officiels** : certificat de scolarité PDF, carte d'identité scolaire PDF (format ID-1), certificat de fréquentation PDF, **QR code** de la carte scolaire

### 4.2 Structure pédagogique
- **Niveaux** : associés à un cycle (PS→TLE), codes et noms
- **Sections** : subdivision des niveaux
- **Classes** : rattachées à un niveau + section, code unique, capacité
- **Salles** : numérotation, capacité, localisation
- **Matières** : code, nom, coefficient

### 4.3 Enseignants
- **CRUD complet** : numéro d'employé généré automatiquement, spécialité, contact
- **Affectation aux classes/matières** : un enseignant peut enseigner plusieurs matières dans plusieurs classes
- **Compte enseignant** lié automatiquement

---

## 5. Pédagogie

### 5.1 Emploi du temps (salle de classe)
- **Génération et gestion** : créneaux par jour/heure, matière, enseignant, salle
- **Détection de conflits** : enseignant, salle, groupe simultanés
- **Affichage** : par classe et par enseignant (dans leur espace)

### 5.2 Présences
- **Pointage groupé** : classe + date, statuts Présent / Absent / Retard / Excusé
- **Justification** : motif d'absence ou de retard
- **Statistiques** : total présences, absences, retards par élève
- **Rapports** : par classe sur une période, export Excel/PDF
- **Présences enseignants** : pointage du personnel par date

### 5.3 Évaluations
- **Types d'évaluation** : Contrôle, Devoir, Examen, Baccalauréat (page dédiée)
- **CRUD évaluations** : classe, matière, trimestre, coefficient, date, statut (DRAFT/PUBLISHED/CLOSED)
- **Saisie des notes** : tableau de tous les élèves de la classe, **saisie inline** note /20 + appréciation automatique
- **Moyennes** : par élève, par trimestre
- **Classement** : par classe et trimestre
- **Bulletins** : génération automatique par classe/trimestre, moyenne, rang, mention, décision (ADMIS/AJOURNE/REDOUBLE)
- **Délibération** : procès-verbal PDF (classement + décisions)
- **PDF bulletins** : téléchargeable par les élèves, parents et administration (nouvelle forme moderne)

### 5.4 Notes et bulletins
- **Historique des bulletins** par élève
- **Synthèse** : moyenne générale, rang, mention, appréciations

---

## 6. Finances

| Fonctionnalité | Description |
|---|---|
| **Types de frais** | Inscription, scolarité, cantine, transport |
| **Factures** | Génération par élève, numéro séquentiel, montant, échéance, statut (IMP/PAYEE/PARTIELLE) |
| **Paiements** | Enregistrement, **numéro de reçu** séquentiel, mode (espèces, mobile money, banque, chèque), mise à jour de la facture |
| **Reçu PDF** | Reçu officiel avec montant en lettres (montant en toutes lettres) et solde restant |
| **Impression/Export** | Historique des paiements et factures en PDF + Excel avec totaux |
| **Espace élève/parent** | Consultation des factures et paiements de l'étudiant |

---

## 7. Bibliothèque

- **Livres** : recherche (titre, auteur, catégorie), CRUD complet
- **Emprunts** : prêt à un élève, retour, statut (EMPRUNTE/RENDU/EN_RETARD)
- **Suivi** : liste des livres empruntés, compteurs (total, empruntés)

---

## 8. Ressources Humaines (RH)

- **Congés** : demande, approbation/rejet, statuts (EN_ATTENTE/APPROUVE/REJETE)
- **Contrats** : création, statut, dates
- **Paie** : génération des fiches de paie, statuts, montants

---

## 9. Communication

- **Annonces** : publication ciblée, notification des utilisateurs concernés, liste pour l'utilisateur courant
- **Messagerie interne** : envoi de messages aux parents/élèves
- **Notifications** : création (absences, bulletins, relances), compteur de non-lues, marquage lu
- **Logs de messages** : historique des envois (WhatsApp/SMS/Email) — **réservé à l'administration**

---

## 10. Espaces personnels (portails)

### 10.1 Espace élève (`/my-school`)
- Bandeau profil (moyenne, absences, factures en attente)
- **Mes bulletins** : moyenne, rang, mention, décision + **téléchargement PDF**
- **Mes notes** : détail par matière/évaluation/trimestre
- **Emploi du temps** : par jour
- **Présences** : historique
- **Paiements** : factures et statuts

### 10.2 Espace parent (`/my-children`)
- **Sélection d'enfant** : liste des enfants liés au compte
- **Bulletins** : moyenne, rang, mention + **téléchargement PDF**
- **Présences** : historique de l'enfant
- **Notes détaillées** : par matière

### 10.3 Espace enseignant (`/my-teaching`)
- **Emploi du temps** : par jour
- **Mes classes** : liste des classes et matières enseignées
- **Saisie des notes** : classe → évaluation → **saisie inline** des notes pour tous les élèves

---

## 11. Dashboard

### 11.1 Dashboard principal
- **Filtre par cycle** : [Tous] [Jardin] [Primaire] [Collège] [Lycée] [Université]
- **KPI** : élèves, actifs, enseignants, classes, matières, moyenne élèves/classe, factures impayées, paiements, revenus/dépenses du mois, présences du jour, livres, congés en attente
- **Graphiques** : revenus sur 6 mois, élèves par classe, répartition par genre
- **Actualisation automatique** toutes les 5 minutes
- **Activité récente** : journal d'audit

---

## 12. Administration

### 12.1 Utilisateurs
- **CRUD** : création, modification, activation/désactivation
- **Rôles et permissions** : attribution des rôles
- **Réinitialisation de mot de passe**, déverrouillage de compte

### 12.2 Journal d'audit
- Traçabilité complète des actions (création, modification, suppression, connexions) avec utilisateur, IP, date

### 12.3 Paramètres
- **Établissement** : nom, adresse, téléphone, email, slogan, logo
- **Reçus** : pied de page des reçus
- **Cycles d'enseignement** : activation/désactivation des 5 cycles
- **WhatsApp** : activation, numéro par défaut
- **Années académiques** : gestion
- **Sauvegarde / Restauration** : export de la base, restauration
- **Logs de messages**

---

## 13. Rapports

Page centralisée avec :
- Exports **PDF** et **Excel** de la plupart des modules
- Rapports de présences, finances, élèves

---

## 14. Recherche globale (Ctrl+K)

Recherche instantanée dans :
- **Élèves** (nom, matricule)
- **Enseignants**
- **Classes**
- **Factures**
- **Convocations**

Réservée aux rôles d'administration.

---

## 15. Notifications

- Générées automatiquement : bulletins disponibles, absences, relances financières
- Compteur de non-lues dans l'interface
- Marquage lu / tout marquer lu

---

## 16. Sécurité

| Mesure | Description |
|---|---|
| **JWT** | Access + refresh tokens, expiration, secret configurable |
| **RBAC** | 7 rôles × permissions fines, vérifiées sur chaque endpoint |
| **Contrôle d'accès IDOR** | Un PARENT ne voit que ses enfants ; un ELEVE ne voit que son profil (vérifié côté serveur) |
| **Comptes désactivés** | Accès refusé même avec JWT valide |
| **Verrouillage** | Anti force brute (5 tentatives) |
| **Politique de mot de passe** | ≥8 caractères, lettres + chiffres |
| **CORS** | Origines configurées |
| **@JsonIgnore** | Hash de mot de passe jamais sérialisé dans les réponses |

---

## 17. Architecture technique

```
backend-springboot/          Spring Boot 3.2 / Java 17
├── controller/              28 contrôleurs REST
├── service/                 40 services métier
├── repository/              47 repositories JPA
├── entity/                  47 entités
├── enums/                   25 énumérations
├── security/                JWT, filtres, config
├── dto/                     request / response
├── pdf/                     SchoolDocumentTheme
├── mapper/                  MapStruct
└── utils/                   CodeGenerator, PasswordPolicy, AmountToWords…

frontend-react/              React 18 / Vite / MUI 5
├── pages/                   45 pages (scolarité, portails, administration, modules annexes)
├── components/              DataTable, StatCard, PageHeader, StatusChip…
├── api/                     axios + endpoints centralisés
├── redux/                   auth, toast, theme
├── hooks/                   useFetch, usePagination, useToast
└── routes/                  Guards par rôle, arbre de routage
```

---

## 18. Liste des routes principales

| Route | Fonctionnalité | Rôles |
|---|---|---|
| `/dashboard` | Dashboard principal | Tous |
| `/students` | Liste des élèves | SUPER_ADMIN, DIRECTEUR, SECRETAIRE, COMPTABLE, PARENT |
| `/students/:id` | Fiche élève | idem |
| `/students/:id/documents` | Documents de l'élève | idem |
| `/teachers` | Enseignants | DIRECTEUR, SECRETAIRE |
| `/classes` `/levels` `/sections` `/rooms` `/subjects` | Structure | DIRECTEUR, SECRETAIRE |
| `/schedules` | Emploi du temps | + ENSEIGNANT |
| `/attendances` | Présences | + ENSEIGNANT |
| `/grades` | Saisie des notes | + ENSEIGNANT |
| `/exams` `/exam-types` | Évaluations | DIRECTEUR, SECRETAIRE |
| `/payments` `/fee-types` | Finances | + COMPTABLE |
| `/expenses` | Dépenses | DIRECTEUR, COMPTABLE |
| `/library` | Bibliothèque | + SUPER_ADMIN |
| `/hr` | RH | DIRECTEUR, COMPTABLE |
| `/stages` | Stages (modules annexes université) | SUPER_ADMIN, DIRECTEUR, SECRETAIRE, ENSEIGNANT |
| `/candidatures` | Admission — candidatures (modules annexes université) | SUPER_ADMIN, DIRECTEUR, SECRETAIRE |
| `/memoires` | Mémoires (modules annexes université) | SUPER_ADMIN, DIRECTEUR, SECRETAIRE, ENSEIGNANT |
| `/alumni` | Alumni (modules annexes université) | SUPER_ADMIN, DIRECTEUR, SECRETAIRE |
| `/reports` | Rapports | + COMPTABLE |
| `/messages` `/announcements` | Communication | Tous |
| `/my-school` | Espace élève | ELEVE |
| `/my-children` | Espace parent | PARENT |
| `/my-teaching` | Espace enseignant | ENSEIGNANT |
| `/users` `/audit` | Administration | DIRECTEUR |
| `/settings` | Paramètres | SUPER_ADMIN |

---

## 19. Modules annexes « Université » (conservés)

Ces quatre modules restent **disponibles dans le produit** lorsque le cycle d'enseignement
**Université** est activé dans *Paramètres → Cycles d'enseignement* ; ils sont protégés par
les permissions **`LMD_READ` / `LMD_WRITE`** (`PERM_LMD_READ` / `PERM_LMD_WRITE` côté API).

| Module | Route | Fonctionnalités | Rôles |
|---|---|---|---|
| **Stages** | `/stages` | Suivi des stages : élève, entreprise et contact, sujet, période, enseignant encadrant, référence de convention, rapport remis, date de soutenance, note et évaluation, statut | SUPER_ADMIN, DIRECTEUR, SECRETAIRE, ENSEIGNANT |
| **Mémoires** | `/memoires` | Suivi des mémoires : élève, sujet, directeur, date et lieu de soutenance, jury, note, décision, statut | SUPER_ADMIN, DIRECTEUR, SECRETAIRE, ENSEIGNANT |
| **Alumni** | `/alumni` | Annuaire des anciens : identité, filière, diplôme, année académique, contacts, poste et entreprise actuels, notes | SUPER_ADMIN, DIRECTEUR, SECRETAIRE |
| **Admission** | `/candidatures` | Candidatures d'admission : identité du candidat, contacts, filière et niveau visés, référence, notes, statut de la candidature | SUPER_ADMIN, DIRECTEUR, SECRETAIRE |
