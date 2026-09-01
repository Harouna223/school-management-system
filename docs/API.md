# Référence API

Toutes les routes sont préfixées par `/api` et exigent un token Bearer JWT (sauf login/refresh/register).

## Format des réponses

```json
{ "success": true, "message": "...", "data": {...}, "path": "/api/...", "timestamp": "2026-..." }
```

Pages : `{ "content": [...], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5, "first": true, "last": false }`

Paramètres de pagination : `?page=0&size=10` ; recherche : `?search=...`.

## Authentification — `/api/auth`

| Méthode | Route | Description |
|---|---|---|
| POST | `/api/auth/login` | Connexion (username, password) → accessToken + refreshToken + profil |
| POST | `/api/auth/refresh` | Renouvellement de l'access token |
| POST | `/api/auth/logout` | Déconnexion (révocation du refresh token) |
| POST | `/api/auth/register` | Création d'un compte (réservé admin) |

## Élèves — `/api/students`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/students` | Liste paginée + recherche (matricule, nom) |
| GET | `/api/students/{id}` | Détail |
| POST | `/api/students` | Création (photo, parent, classe) — champs parent + `createParentAccount`/`parentUsername`/`parentPassword` pour créer le compte parent |
| PUT | `/api/students/{id}` | Modification |
| DELETE | `/api/students/{id}` | Suppression |
| GET | `/api/students/{id}/qr` | QR code de l'élève (PNG) |
| GET | `/api/students/export/pdf` | Export PDF des élèves |
| GET | `/api/students/export/excel` | Export Excel (Apache POI) |

## Enseignants — `/api/teachers`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/teachers` | Liste paginée |
| GET | `/api/teachers/{id}` | Détail |
| POST | `/api/teachers` | Création |
| PUT | `/api/teachers/{id}` | Modification |
| DELETE | `/api/teachers/{id}` | Suppression |
| PATCH | `/api/teachers/{id}/status` | Changer le statut (ACTIVE, INACTIVE, ON_LEAVE) |

## Classes — `/api/classes`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/classes` | Liste paginée |
| GET | `/api/classes/all` | Toutes les classes |
| GET | `/api/classes/{id}` | Détail |
| POST | `/api/classes` | Création |
| PUT | `/api/classes/{id}` | Modification |
| DELETE | `/api/classes/{id}` | Suppression |
| GET | `/api/classes/sections` | Sections (primaire, secondaire…) |
| GET | `/api/classes/levels` | Niveaux |
| GET | `/api/classes/rooms` | Salles |

## Matières — `/api/subjects`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/subjects` | Liste paginée |
| GET | `/api/subjects/all` | Toutes les matières |
| GET | `/api/subjects/{id}` | Détail |
| POST | `/api/subjects` | Création |
| PUT | `/api/subjects/{id}` | Modification |
| DELETE | `/api/subjects/{id}` | Suppression |
| GET | `/api/subjects/assignments` | Affectations matière ↔ classe |
| POST | `/api/subjects/assignments` | Affecter une matière à une classe |
| DELETE | `/api/subjects/assignments/{id}` | Retirer une affectation |

## Emplois du temps — `/api/schedules`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/schedules` | Liste paginée |
| GET | `/api/schedules/class/{classId}` | Emploi du temps d'une classe |
| GET | `/api/schedules/teacher/{teacherId}` | Emploi du temps d'un enseignant |
| GET | `/api/schedules/room/{roomId}` | Emploi du temps d'une salle |
| POST | `/api/schedules` | Création |
| PUT | `/api/schedules/{id}` | Modification |
| DELETE | `/api/schedules/{id}` | Suppression |
| GET | `/api/schedules/conflict` | Vérifier les conflits (prof/salle) |

## Présences — `/api/attendances`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/attendances/class/{classId}` | Présences d'une classe (`?date=YYYY-MM-DD`) |
| POST | `/api/attendances` | Pointage groupé `{ classId, date, entries: [{studentId, status, justification}] }` |
| GET | `/api/attendances/student/{studentId}` | Historique d'un élève |
| GET | `/api/attendances/student/{studentId}/stats` | Statistiques (présents, absences, retards) |
| PATCH | `/api/attendances/{attendanceId}/justify` | Justifier une absence/retard |

| GET | `/api/attendances/teachers` | Présences des enseignants par date |
| POST | `/api/attendances/teachers` | Pointer les présences des enseignants |

## Examens & notes — `/api/exams`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/exams` | Liste paginée |
| GET | `/api/exams/class/{classId}` | Examens d'une classe |
| GET | `/api/exams/{id}` | Détail |
| POST | `/api/exams` | Création |
| PUT | `/api/exams/{id}` | Modification |
| DELETE | `/api/exams/{id}` | Suppression |
| PATCH | `/api/exams/{id}/status` | Changement de statut (PLANNED → ONGOING → COMPLETED → DELIBERATED) |
| GET | `/api/exams/{examId}/grades` | Notes d'un examen |
| POST | `/api/exams/grades` | Enregistrer une note |
| GET | `/api/exams/student/{studentId}/grades` | Notes d'un élève |
| GET | `/api/exams/student/{studentId}/average` | Moyenne d'un élève |
| GET | `/api/exams/class/{classId}/ranking` | Classement d'une classe |
| POST | `/api/exams/class/{classId}/bulletins` | Générer les bulletins (PDF) |

## Paiements — `/api/payments`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/payments/invoices` | Factures paginées (statut, recherche) |
| GET | `/api/payments/invoices/student/{studentId}` | Factures d'un élève |
| POST | `/api/payments/invoices` | Générer une facture |
| GET | `/api/payments/student/{studentId}` | Paiements d'un élève |
| POST | `/api/payments` | Encaisser un paiement (recalcul du statut de facture) |
| GET | `/api/payments/{paymentId}/receipt/pdf` | Reçu de paiement (PDF) |

## Finances — `/api/finance`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/finance/summary` | Synthèse (recettes, dépenses, impayés, mois) |
| GET | `/api/finance/expenses` | Dépenses paginées |
| POST | `/api/finance/expenses` | Enregistrer une dépense |
| DELETE | `/api/finance/expenses/{id}` | Supprimer une dépense |
| GET | `/api/finance/fee-types` | Types de frais |
| POST | `/api/finance/fee-types` | Créer un type de frais |
| GET | `/api/finance/expense-categories` | Catégories de dépenses |

## Bibliothèque — `/api/library`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/library/books` | Livres paginés (recherche) |
| POST | `/api/library/books` | Ajouter un livre |
| PUT | `/api/library/books/{id}` | Modifier un livre |
| DELETE | `/api/library/books/{id}` | Supprimer un livre |
| GET | `/api/library/borrowings` | Emprunts (statut) |
| GET | `/api/library/borrowings/student/{studentId}` | Emprunts d'un élève |
| POST | `/api/library/borrowings` | Nouvel emprunt |
| PATCH | `/api/library/borrowings/{id}/return` | Retour de livre |
| POST | `/api/library/borrowings/mark-overdue` | Marquer les emprunts en retard |

## RH — `/api/hr`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/hr/leaves` | Congés (filtre statut) |
| POST | `/api/hr/leaves` | Demander un congé |
| PATCH | `/api/hr/leaves/{id}/decide` | Approuver / rejeter |
| GET | `/api/hr/contracts` | Contrats (filtre enseignant / statut) |
| POST | `/api/hr/contracts` | Créer un contrat |
| PUT | `/api/hr/contracts/{id}` | Modifier un contrat |
| DELETE | `/api/hr/contracts/{id}` | Supprimer un contrat |
| GET | `/api/hr/payrolls` | Bulletins de paie (filtre enseignant / mois / statut) |
| POST | `/api/hr/payrolls/generate` | Générer un bulletin mensuel (net = base + primes − retenues) |
| PATCH | `/api/hr/payrolls/{id}/pay` | Marquer un bulletin comme payé |

## Communication — `/api/communication`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/communication/messages/inbox` | Messages reçus |
| GET | `/api/communication/messages/sent` | Messages envoyés |
| POST | `/api/communication/messages` | Envoyer un message |
| GET | `/api/communication/messages/unread-count` | Compteur non lus |
| PATCH | `/api/communication/messages/read-all` | Tout marquer lu |
| GET | `/api/communication/notifications` | Notifications |
| GET | `/api/communication/notifications/unread-count` | Compteur |
| PATCH | `/api/communication/notifications/{id}/read` | Marquer lue |
| PATCH | `/api/communication/notifications/read-all` | Tout marquer lu |
| GET | `/api/communication/announcements` | Annonces visibles par l'utilisateur (ciblage rôle) |
| GET | `/api/communication/announcements/all` | Toutes les annonces (administration) |
| POST | `/api/communication/announcements` | Publier une annonce (diffusion en notifications) |
| DELETE | `/api/communication/announcements/{id}` | Supprimer une annonce |

## Espace Parent — `/api/parents`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/parents/children` | Enfants liés au compte du parent connecté |
| GET | `/api/parents/children/{studentId}/bulletins` | Bulletins de notes d'un enfant (accès vérifié : parent de l'enfant) |
| GET | `/api/parents/children/{studentId}/attendances` | Présences / absences d'un enfant |
| GET | `/api/parents/children/{studentId}/grades` | Notes détaillées d'un enfant |

Notifications automatiques : à la génération des bulletins (`POST /api/exams/class/{id}/bulletins`), chaque parent concerné reçoit une notification « Bulletin disponible » ; à chaque absence ou retard pointé (`POST /api/attendances`), le parent de l'élève concerné reçoit une notification « Absence signalée ». Dans les deux cas, le parent reçoit aussi un **message WhatsApp** (via `WhatsAppService`) si le service est activé ; sinon les messages sont journalisés (mode simulation).

### Notifications WhatsApp

Configuration via variables d'environnement (dans `docker/.env`) :

| Variable | Description | Défaut |
|---|---|---|
| `WA_ENABLED` | `true` pour envoyer réellement sur WhatsApp, `false` = simulation (messages journalisés) | `false` |
| `WA_PROVIDER` | `meta` (WhatsApp Cloud API) ou `twilio` | `meta` |
| `WA_META_TOKEN` | Jeton d'accès permanent de l'API Meta | — |
| `WA_META_PHONE_ID` | ID du numéro WhatsApp (Phone ID) | — |
| `WA_TWILIO_SID` | Account SID Twilio | — |
| `WA_TWILIO_TOKEN` | Auth Token Twilio | — |
| `WA_TWILIO_FROM` | Numéro WhatsApp Twilio expéditeur (format international) | — |

Le numéro du parent est utilisé tel que saisi (normalisation automatique : `6XXXXXXXX`/`06XXXXXXXX` → `+2376XXXXXXXX`). WhatsApp Cloud API exige un numéro au format international E.164.

## Utilisateurs — `/api/users`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/users` | Liste paginée |
| GET | `/api/users/{id}` | Détail |
| GET | `/api/users/roles` | Liste des rôles |
| PUT | `/api/users/{id}/roles` | Assigner des rôles |
| PATCH | `/api/users/{id}/enabled` | Activer / désactiver |
| POST | `/api/users/{id}/reset-password` | Réinitialiser le mot de passe |

## Dashboard & audit — `/api/dashboard`

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/dashboard/stats` | Statistiques globales (élèves, finances, présences, livres…) |
| GET | `/api/dashboard/audit-logs` | Journal d'audit paginé (`?username=`) |

## Fichiers

- `GET /uploads/**` : fichiers téléversés (photos, reçus PDF) — servis par le backend.

## Codes d'erreur

| Statut | Signification |
|---|---|
| 400 | Validation échouée (détails par champ) |
| 401 | Token absent / invalide / expiré |
| 403 | Permission insuffisante |
| 404 | Ressource introuvable |
| 409 | Conflit (doublon, contrainte métier) |
| 500 | Erreur interne |

La documentation interactive (Swagger) est disponible sur `/swagger-ui.html`.