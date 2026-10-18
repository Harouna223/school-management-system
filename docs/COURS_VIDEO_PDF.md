# Cours vidéo et PDF — déploiement, sécurité et exploitation

Ce document décrit le module **« Cours et ressources »** de Sahel Edu : ce qu'il
ajoute au dépôt, comment le déployer sur un serveur Ubuntu Server 24.04 LTS
administré avec Docker Compose, comment le sauvegarder et le vérifier.

---

## 1. Ce que fait le module

| Acteur | Droits |
|---|---|
| Administration (`SUPER_ADMIN`, `DIRECTEUR`, `SECRETAIRE`) | Gère **toutes** les ressources : création, modification, téléversement, publication, archivage, suppression. |
| Enseignant (`ENSEIGNANT`) | Crée et gère **ses propres** ressources, uniquement pour les paires (classe, matière) où il a une **affectation réelle** (`subject_assignments`). Voit en plus tout le catalogue publié. |
| Élève (`ELEVE`) | Voit **uniquement** les ressources `PUBLISHED` visant **sa** classe. Lecture vidéo, consultation PDF, téléchargement si autorisé. |
| Comptable / Parent | Pas d'accès au module (403). |

Tous les contrôles sont faits **côté serveur** à chaque requête. Un identifiant
modifié dans l'URL ne donne jamais accès à une autre classe : l'élève reçoit un
**404** (et non 403) pour ne pas révéler l'existence d'un brouillon.

### Cycle de vie d'un cours

`DRAFT` (brouillon, sans fichier possible) → `PUBLISHED` (visible des élèves)
→ `ARCHIVED` (retiré du catalogue, conservé). La publication **exige** un
fichier : elle est refusée sinon.

---

## 2. Fichiers concernés

### Backend (`backend-springboot/`)

| Fichier | Rôle |
|---|---|
| `src/main/resources/db/migration/V5__cours_ressources_video_pdf.sql` | Tables `course_resources`, `course_resource_classes` + permissions `COURSE_READ` / `COURSE_WRITE`. |
| `entity/CourseResource.java` | Entité (métadonnées + chemin relatif du fichier + classes ciblées). |
| `enums/CourseResourceType.java`, `enums/CourseStatus.java` | `VIDEO`/`PDF`, `DRAFT`/`PUBLISHED`/`ARCHIVED`. |
| `repository/CourseResourceRepository.java` | Recherches personnel / enseignant / élève. |
| `dto/request/CourseResourceRequest.java` | Validation des métadonnées. |
| `dto/response/CourseResourceResponse.java`, `CourseAccessTokenResponse.java`, `CourseOptionsResponse.java` | Réponses API. |
| `service/CourseFileStorageService.java` | Validation (signature binaire, MIME, extension, taille) + écriture disque + anti-traversée. |
| `service/CourseAccessTokenService.java` | Jetons de lecture HMAC courts, liés à une ressource et un usage. |
| `service/CourseResourceService.java` | Règles métier et permissions. |
| `controller/CourseResourceController.java` | API REST + streaming HTTP `Range`. |
| `security/SecurityConfig.java` | Règles `/api/courses/**` et ouverture contrôlée des 3 endpoints binaires. |
| `config/AppProperties.java` | Bloc `app.courses` (limites, TTL du jeton). |
| `application.yml` | Limites multipart, `max-swallow-size`, `app.courses`. |
| `exception/GlobalExceptionHandler.java` | Message 413 générique (la limite n'est plus « 5 Mo »). |

### Frontend (`frontend-react/src/`)

| Fichier | Rôle |
|---|---|
| `api/endpoints.js` | `courseApi` + `courseStreamUrl` / `coursePreviewUrl` / `courseDownloadUrl`. |
| `pages/courses/CoursesPage.jsx` | Gestion (administration + enseignants) : filtres, publication, archivage, suppression. |
| `pages/courses/CourseFormDialog.jsx` | Création/édition + téléversement avec **barre de progression réelle**. |
| `pages/courses/StudentCoursesPage.jsx` | Catalogue élève en cartes responsives. |
| `components/CourseViewerDialog.jsx` | Lecteur vidéo intégré (sans auto-play sonore) et visionneuse PDF. |
| `components/Sidebar.jsx` | Entrées « Cours et ressources » (personnel) et espace élève. |
| `routes/index.jsx` | Routes `/courses` et `/my-courses`. |
| `components/StatusChip.jsx` | Couleurs `PUBLISHED` / `DRAFT` / `ARCHIVED`. |
| `i18n/locales/fr.js`, `en.js` | `nav.courses`, `nav.myCourses`. |

### Docker / configuration

| Fichier | Modification |
|---|---|
| `docker/docker-compose.yml` | **Montage de l'hôte** pour `/app/uploads`, env du module, base et backend publiés **sur 127.0.0.1 uniquement**, sauvegarde des fichiers pédagogiques. |
| `docker/nginx.conf` | `client_max_body_size 512m`, pas de mise en tampon, délais longs pour le streaming `Range`. |
| `docker/.env.example` | Nouvelles variables, sans secret. |

---

## 3. API REST

Toutes les réponses sont enveloppées dans `ApiResponse` (sauf les flux binaires).

| Méthode | Chemin | Description |
|---|---|---|
| `GET` | `/api/courses` | Liste filtrée (`search`, `type`, `status`, `subjectId`, `classId`, `page`, `size`). Périmètre selon le rôle. |
| `GET` | `/api/courses/options` | Classes/matières ciblables (réduites aux affectations pour un enseignant). |
| `GET` | `/api/courses/{id}` | Détails (404 si hors périmètre). |
| `POST` | `/api/courses` | Créer un cours (brouillon). |
| `PUT` | `/api/courses/{id}` | Modifier les métadonnées. |
| `POST` | `/api/courses/{id}/file` | Téléverser / remplacer le fichier (multipart, champ `file`). |
| `PATCH` | `/api/courses/{id}/status?status=` | `DRAFT`, `PUBLISHED`, `ARCHIVED`. |
| `DELETE` | `/api/courses/{id}` | Supprimer la ressource **et son fichier**. |
| `GET` | `/api/courses/{id}/token?purpose=` | Jeton court : `stream`, `preview` ou `download`. |
| `GET` | `/api/courses/{id}/stream?token=` | Vidéo avec **HTTP Range** (206 si plage demandée). |
| `GET` | `/api/courses/{id}/preview?token=` | PDF `inline` (affichage navigateur). |
| `GET` | `/api/courses/{id}/download?token=` | Pièce jointe, si `downloadable`. |

### Pourquoi un jeton pour les fichiers ?

Un élément `<video>` ou `<iframe>` **ne peut pas envoyer** l'en-tête
`Authorization`. Trois options existaient : rendre les fichiers publics (fuite),
télécharger toute la vidéo en mémoire (perte du `Range`, donc plus de navigation
dans la vidéo), ou un jeton en paramètre. Le jeton retenu est :

- signé en **HMAC-SHA256** avec le secret JWT de l'application ;
- limité à **une ressource** et **un usage** ;
- de durée courte (`COURSE_TOKEN_TTL_MINUTES`, 120 min par défaut) ;
- **revalidé** contre les droits réels à chaque requête : si l'élève change de
  classe ou si le cours est dépublié, le jeton ne sert plus à rien.

---

## 4. Stockage persistant sur Ubuntu

Les fichiers **ne vivent pas dans le conteneur**. Ils sont montés depuis l'hôte :

```
hôte      : /srv/sahel-edu/uploads        (variable UPLOAD_HOST_DIR)
conteneur : /app/uploads                  (UPLOAD_DIR)
arborescence : courses/<id-ressource>/<uuid>.<ext>
```

Préparation (une seule fois) :

```bash
sudo mkdir -p /srv/sahel-edu/uploads
sudo chown -R "$USER":"$USER" /srv/sahel-edu
sudo chmod 750 /srv/sahel-edu/uploads
```

Le nom physique est un UUID généré par le serveur : **aucun nom fourni par
l'utilisateur ne construit un chemin**. Toute écriture est refusée si le chemin
normalisé sort du répertoire d'uploads.

---

## 5. Configuration

```bash
cd /chemin/vers/school-management-system
cp docker/.env.example docker/.env
```

Variables à ajuster impérativement :

| Variable | Rôle |
|---|---|
| `DB_PASSWORD` | Mot de passe MySQL (générer une valeur forte). |
| `JWT_SECRET` | Secret **fort** ; `REQUIRE_SECURE_SECRETS=true` en production fait **échouer le démarrage** si le défaut est conservé. |
| `UPLOAD_HOST_DIR` | Répertoire persistant des vidéos/PDF. |
| `COURSE_MAX_VIDEO_MB` | Taille maximale d'une vidéo (500 par défaut). |
| `COURSE_MAX_PDF_MB` | Taille maximale d'un PDF (25 par défaut). |
| `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE` | Limites Spring/Tomcat, **≥** `COURSE_MAX_VIDEO_MB`. |
| `FRONTEND_PORT` | 80 par défaut ; 443 si terminaison TLS sur ce conteneur. |

La limite de nginx (`client_max_body_size 512m` dans `docker/nginx.conf`) doit
rester supérieure ou égale aux limites backend, sinon la requête est coupée
**avant** d'atteindre l'application.

---

## 6. Construire, démarrer, mettre à jour

```bash
# Depuis la racine du dépôt
docker compose -f docker/docker-compose.yml build
docker compose -f docker/docker-compose.yml up -d
```

Mise à jour **sans perte de données** :

```bash
git pull
docker compose -f docker/docker-compose.yml build
docker compose -f docker/docker-compose.yml up -d
```

- La base est dans le volume nommé `mysql_data` : jamais supprimé par `up -d`.
- Les fichiers sont sur l'hôte (`UPLOAD_HOST_DIR`) : jamais supprimés par une
  reconstruction d'image.
- Les migrations Flyway (`V5__…`) s'appliquent automatiquement au démarrage ;
  elles sont **idempotentes** et ne détruisent aucune table existante.

⚠️ Ne **jamais** lancer `docker compose down -v` : `-v` supprime les volumes,
donc la base.

### Vérification de l'état

```bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs -f backend
docker compose -f docker/docker-compose.yml exec backend \
  curl -s http://127.0.0.1:8080/actuator/health

# La migration V5 doit être présente :
docker compose -f docker/docker-compose.yml exec mysql \
  mysql -uroot -p"$DB_PASSWORD" school_management \
  -e "SELECT version, description, success FROM flyway_schema_history;"
```

---

## 7. Vérification fonctionnelle après déploiement

À faire dans un navigateur, dans cet ordre :

1. **Configuration** — se connecter en administrateur, vérifier que le menu
   « Cours et ressources » apparaît.
2. **Création** — créer un cours PDF « Test », choisir matière + classe,
   téléverser un petit PDF : la barre de progression doit atteindre 100 %.
3. **Publication** — cliquer « Publier » ; le statut passe à *Publié*.
4. **Élève** — se connecter avec un compte élève de la **même classe** : le
   cours apparaît dans « Cours et ressources » de son espace ; « Ouvrir »
   affiche le PDF dans le dialogue.
5. **Isolation** — se connecter avec un élève d'une **autre** classe : le cours
   ne doit pas apparaître.
6. **Vidéo** — téléverser une courte vidéo MP4, la publier, ouvrir : la lecture
   démarre **sans son automatique**, et la barre de progression de la vidéo
   permet d'avancer (preuve que les requêtes `Range` répondent 206).
7. **Téléchargement** — si « téléchargement autorisé » est décoché, le bouton
   disparaît et l'API répond une erreur explicite.
8. **Enseignant** — avec un compte enseignant, vérifier que seules ses classes
   et matières affectées sont proposées, et qu'il ne peut pas modifier un cours
   d'un autre enseignant.

---

## 8. Sauvegarde et restauration

### Sauvegarde automatique

Le service `db-backup` produit chaque jour, dans le volume `backup_data` :

- `backup-AAAAMMJJ-HHMM.sql` — dump MySQL complet ;
- `uploads-AAAAMMJJ-HHMM.tar.gz` — vidéos et PDF.

Rétention : 14 jours.

### Sauvegarde manuelle (recommandée avant toute mise à jour importante)

```bash
mkdir -p /srv/sahel-edu/backups
docker compose -f docker/docker-compose.yml exec mysql \
  mysqldump -uroot -p"$DB_PASSWORD" --single-transaction --routines --triggers \
  school_management > /srv/sahel-edu/backups/db-$(date +%F).sql

sudo tar czf /srv/sahel-edu/backups/uploads-$(date +%F).tar.gz \
  -C /srv/sahel-edu/uploads .
```

### Copie hors du disque principal

Une sauvegarde sur le **même disque** ne protège pas d'une panne de disque.
Copier les archives vers un disque USB, un NAS ou un stockage distant :

```bash
rsync -av --delete /srv/sahel-edu/backups/ /media/USB/sahel-edu-backups/
```

### Restauration

```bash
# 1. Arrêter l'application
docker compose -f docker/docker-compose.yml stop backend frontend

# 2. Restaurer la base
docker compose -f docker/docker-compose.yml exec -T mysql \
  mysql -uroot -p"$DB_PASSWORD" school_management < /chemin/db-AAAA-MM-JJ.sql

# 3. Restaurer les fichiers
sudo tar xzf /chemin/uploads-AAAA-MM-JJ.tar.gz -C /srv/sahel-edu/uploads

# 4. Redémarrer
docker compose -f docker/docker-compose.yml start backend frontend
```

### Ce qu'il ne faut PAS mettre dans les archives ordinaires

`docker/.env` contient `JWT_SECRET`, `DB_PASSWORD`, identifiants SMTP/Twilio.
Le conserver à part, dans un coffre ou un gestionnaire de secrets, jamais dans
une archive de sauvegarde non chiffrée.

---

## 9. Accès réseau local et Internet

### Réseau local

1. Réserver une **IP fixe** pour le serveur (bail DHCP statique sur le routeur,
   ou configuration netplan).
2. Accès : `http://IP_DU_SERVEUR` (port `FRONTEND_PORT`).
3. Pare-feu UFW restrictif :

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from 192.168.0.0/16 to any port 22 proto tcp   # SSH (adapter au sous-réseau)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Les ports MySQL (`3306`) et backend (`8080`) ne sont **pas** ouverts : ils sont
publiés sur `127.0.0.1` uniquement.

### Depuis Internet

Trois conditions à vérifier **avant** toute configuration :

1. **Adresse publique** — le fournisseur d'accès attribue-t-il une IP publique
   fixe ? Sinon, utiliser un DNS dynamique.
2. **Redirection de ports** — rediriger le port 80/443 du routeur vers l'IP
   locale du serveur.
3. **CGNAT** — si le fournisseur place l'abonnement derrière un CGNAT, aucune
   redirection entrante n'est possible. Solutions acceptables :
   - demander une IP publique au fournisseur ;
   - ou un tunnel sortant (WireGuard/Tailscale) vers une passerelle existante,
     en gardant l'application derrière ce tunnel.

### HTTPS

Placer un reverse proxy TLS devant le conteneur frontend (Caddy, nginx hôte,
ou Traefik) et laisser `FRONTEND_PORT` sur `127.0.0.1:8080`. Une fois HTTPS
actif, décommenter `Strict-Transport-Security` dans `docker/nginx.conf` et
passer `CORS_ORIGINS` à l'origine HTTPS réelle.

### SSH

```bash
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
```

---

## 10. Diagnostic

| Symptôme | Cause probable | Action |
|---|---|---|
| `413` au téléversement | Limite nginx ou Spring dépassée | Vérifier `client_max_body_size` (nginx) et `MAX_FILE_SIZE` (env) ≥ `COURSE_MAX_VIDEO_MB`. |
| `400` « Format non supporté » | Contenu réel non PDF/MP4/WebM | Le format est refusé sur la **signature binaire**, pas sur l'extension. Réencoder le fichier. |
| `400` « extension … ne correspond pas » | Fichier renommé | Utiliser un fichier réellement au bon format. |
| `404` sur `/stream` | Jeton expiré ou ressource hors périmètre | Recharger la page : un nouveau jeton est demandé. |
| `416` | Plage `Range` invalide | Comportement normal, le lecteur redemande une plage valide. |
| `403` sur `/api/courses` | Permission `COURSE_READ` absente | Vérifier que la migration V5 a bien été appliquée et que l'utilisateur s'est reconnecté (les permissions sont dans le JWT). |
| Vidéo qui ne démarre pas | Proxy sans `Range` | Vérifier que `proxy_buffering off` est actif et que `/api/` n'est pas servi par un cache statique. |
| Disque plein | Vidéos volumineuses | `df -h`, purger les brouillons inutiles, ajuster `COURSE_MAX_VIDEO_MB`, archiver sur un disque externe. |

Journaux utiles :

```bash
docker compose -f docker/docker-compose.yml logs -f backend
docker compose -f docker/docker-compose.yml logs -f frontend
docker compose -f docker/docker-compose.yml logs -f db-backup
```
