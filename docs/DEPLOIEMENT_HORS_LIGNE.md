# Déploiement hors ligne (Ubuntu 24.04 LTS)

Guide pour installer **Sahel Edu** sur un serveur qui n'a **aucun accès Internet**
au moment de l'installation, puis l'exploiter :

- **en local en permanence** — le réseau de l'école suffit, même si la connexion
  Internet tombe ou n'existe pas ;
- **en ligne** dès qu'un nom de domaine, un NAT et Internet sont disponibles
  (HTTPS Let's Encrypt).

Pour une installation classique avec Internet, voir
[DEPLOIEMENT_UBUNTU.md](DEPLOIEMENT_UBUNTU.md).

---

## 1. Principe

Tout est préconstruit sur une machine connectée : images Docker de l'application,
image MySQL et paquets Docker Engine. Le serveur cible ne compile rien, ne
télécharge rien, n'exécute aucun `apt-get`.

```
Machine connectée (Linux/WSL/macOS/Git Bash + Docker)
   └─ deploy/offline/build-bundle.sh
        └─ bundle autonome (~550 Mo)

Serveur Ubuntu 24.04, sans Internet
   └─ deploy/offline/install-offline.sh
        ├─ vérifie les sommes de contrôle
        ├─ installe Docker Engine depuis les .deb du bundle
        ├─ charge les images (docker load)
        ├─ génère docker/.env (secrets aléatoires, une seule fois)
        └─ démarre la pile via systemd (--no-build, pull_policy: never)
```

Aucune ressource externe n'est chargée à l'exécution : la police Inter est
embarquée dans le bundle frontend (`@fontsource-variable/inter`), l'API et MySQL
sont hébergés localement. Une coupure Internet n'affecte donc pas l'application.

---

## 2. Fabriquer le bundle (machine connectée)

Prérequis : Docker (démon démarré), `git`, `gzip`, ~15 Go d'espace libre.
Sous Windows, Git Bash + Docker Desktop suffisent.

```bash
git clone https://github.com/Harouna223/school-management-system.git
cd school-management-system
./deploy/offline/build-bundle.sh
```

Résultat : `deploy/offline/dist/sahel-edu-offline-<version>/` contenant :

| Élément | Rôle |
|---|---|
| `deploy/offline/images/*.tar.gz` | `mysql:8.4`, `sahel-edu-backend:local`, `sahel-edu-frontend:local` (linux/amd64) |
| `deploy/offline/docker-debs/*.deb` | Docker Engine (noble/amd64) + dépendances système courantes |
| `deploy/offline/manifest.txt` | version, commit, identifiants d'images |
| `deploy/offline/SHA256SUMS` | intégrité des archives |
| reste de l'arborescence | sources, scripts, documentation |

Variantes :

```bash
VERSION=1.0.0 ./deploy/offline/build-bundle.sh        # nom de bundle explicite
SKIP_DOCKER_DEBS=1 ./deploy/offline/build-bundle.sh   # si Docker est déjà installé sur le serveur
REUSE_IMAGES=1 ./deploy/offline/build-bundle.sh       # réutiliser des images locales déjà construites
# Réseau instable : construire le backend depuis le jar local (produit par
# `mvn -f backend-springboot/pom.xml -DskipTests package`)
USE_LOCAL_JAR=1 ./deploy/offline/build-bundle.sh
```

> Les scripts s'arrêtent si le bundle est incomplet ; une relance après une
> coupure réseau reprend les téléchargements déjà effectués (cache Maven
> BuildKit, archives d'images conservées).

> Le bundle est construit pour **linux/amd64** (serveurs Ubuntu x86_64). Les
> artefacts volumineux sont ignorés par Git : ne pas les committer.

### Transfert

```bash
# depuis la machine connectée
rsync -av deploy/offline/dist/sahel-edu-offline-<version>/ \
  utilisateur@serveur:/tmp/sahel-edu-offline/
```

Clé USB, partage SMB ou `scp` fonctionnent aussi. Le serveur n'a besoin que de
ce dossier.

---

## 3. Installer sans Internet (serveur)

Prérequis : Ubuntu Server 24.04 LTS, accès `sudo`, IP fixe sur le LAN.

```bash
cd /tmp/sahel-edu-offline
sudo ./deploy/offline/install-offline.sh
```

Le script est **idempotent** et ne régénère jamais `docker/.env` s'il existe.
Options identiques à `install.sh` :

```bash
sudo SCHOOL_NAME="Complexe Scolaire Sahel" ./deploy/offline/install-offline.sh
sudo APP_DIR=/opt/sahel-edu DATA_DIR=/srv/sahel-edu ./deploy/offline/install-offline.sh
sudo ADMIN_PASSWORD='...' ./deploy/offline/install-offline.sh   # sinon généré
```

À la fin, le script affiche le **mot de passe administrateur initial** (une seule
fois) et l'URL locale.

| Étape | Détail |
|---|---|
| Intégrité | `sha256sum -c SHA256SUMS` — arrêt si le bundle est altéré |
| Docker | `dpkg -i` des paquets du bundle, puis `apt-get -f install` local si besoin |
| Images | `docker load` des trois archives (aucun pull) |
| Données | `/srv/sahel-edu/uploads` et `/srv/sahel-edu/backups` (mode 750) |
| Secrets | `docker/.env` en mode 600 : `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_PASSWORD` |
| systemd | `sahel-edu.service` en variante hors ligne + sauvegarde quotidienne |
| Pare-feu | UFW : 22, 80, 443 entrants |
| Vérification | `/actuator/health` = `UP` (compter ~90 s au premier démarrage) |

Accès immédiat depuis le réseau local : `http://IP_DU_SERVEUR`.

> Si le serveur est un Ubuntu Server **minimal** et que `dpkg` signale une
> dépendance absente, installer les `.deb` correspondants fournis dans
> `deploy/offline/docker-debs/`. Le bundle couvre les cas courants
> (`iptables`, `netbase`, `libdevmapper`, `libseccomp`, `conntrack`…).

---

## 4. Accès local permanent (sans Internet)

Rien à faire : c'est le mode par défaut.

- `FRONTEND_BIND=0.0.0.0`, `FRONTEND_PORT=80` → `http://IP_DU_SERVEUR` sur le LAN.
- Le service systemd ne dépend pas de `network-online.target` : il démarre même
  si le serveur n'a jamais eu Internet.
- Aucun CDN, aucune police distante, aucune API tierce : le frontend et l'API
  fonctionnent entièrement sur le serveur.
- Les notifications WhatsApp/SMS/e-mail restent **désactivées** par défaut ; sans
  Internet elles ne partent pas, le reste de l'application n'est pas impacté.

Pour une IP stable et des noms courts, voir la section 5 de
[DEPLOIEMENT_UBUNTU.md](DEPLOIEMENT_UBUNTU.md) (netplan, UFW, SSH).

---

## 5. Accès en ligne (quand Internet est disponible)

L'application est déjà prête pour l'exposition publique ; seule la terminaison
TLS demande Internet **une fois** (émission du certificat).

1. Créer un enregistrement DNS **A** : `ecole.example.com → IP publique`.
2. Sur le routeur, rediriger **80/tcp** et **443/tcp** vers l'IP locale du serveur.
3. Depuis le serveur, quand Internet est disponible :

   ```bash
   cd /opt/sahel-edu
   sudo ./deploy/https.sh ecole.example.com admin@example.com
   ```

Le script installe nginx + certbot (nécessite Internet), obtient le certificat,
bascule le frontend sur `127.0.0.1:8081` et active le renouvellement automatique.

### Ce qui se passe si Internet disparaît ensuite

| Élément | Comportement |
|---|---|
| Accès LAN | Continue (nginx hôte + certificat déjà présent) |
| Certificat | Valide ~90 jours ; le renouvellement exige Internet périodiquement |
| Notifications | Suspendues tant que le fournisseur est injoignable |
| Application, base, fichiers | Aucun impact |

> Sans nom de domaine, rester en HTTP sur le LAN (installation par défaut).
> Un certificat auto-signé est possible mais provoque un avertissement navigateur.

---

## 6. Mise à jour hors ligne

1. Sur la machine connectée : `./deploy/offline/build-bundle.sh` (nouvelle version).
2. Transférer le nouveau dossier sur le serveur.
3. Relancer `sudo ./deploy/offline/install-offline.sh`.

Comportement :

- `docker/.env` est **conservé** (secrets inchangés) ;
- le volume MySQL et `/srv/sahel-edu/uploads` ne sont **jamais** supprimés ;
- les migrations Flyway s'appliquent au démarrage du backend ;
- le script recharge les images et redémarre la pile.

> Ne pas utiliser `deploy/update.sh` sur un serveur isolé : il fait un `git pull`
> et une reconstruction qui exigent Internet.

### Retour arrière

Conserver le dossier de bundle précédent. Relancer son
`install-offline.sh` restaure les images et le code de cette version. Si une
migration a modifié le schéma, restaurer la sauvegarde correspondante
(section 6 de [DEPLOIEMENT_UBUNTU.md](DEPLOIEMENT_UBUNTU.md)).

---

## 7. Vérifications et dépannage

```bash
systemctl status sahel-edu.service
docker compose -f /opt/sahel-edu/docker/docker-compose.yml \
  -f /opt/sahel-edu/docker/docker-compose.offline.yml ps
curl -s http://127.0.0.1:8080/actuator/health
journalctl -u sahel-edu -n 100 --no-pager
```

| Symptôme | Cause probable | Action |
|---|---|---|
| `image ... not found` au démarrage | images non chargées | relancer `install-offline.sh`, vérifier `docker images` |
| `pull access denied` / tentative réseau | compose hors ligne non utilisé | vérifier que le service systemd inclut `docker-compose.offline.yml` |
| `dpkg` : dépendance non satisfaite | Ubuntu minimal | installer les `.deb` du dossier `docker-debs/` |
| `Bind for 0.0.0.0:80 failed` | port occupé | `ss -ltnp | grep :80`, libérer le port ou changer `FRONTEND_PORT` |
| Backend en boucle | secret JWT absent/invalide | vérifier `docker/.env` (`JWT_SECRET`, `ADMIN_PASSWORD`) |
| `502` après activation HTTPS | conteneur frontend arrêté | `docker ps`, `systemctl restart sahel-edu` |
| Certificat expiré hors ligne | renouvellement impossible | reconnecter Internet puis `certbot renew` |

---

## 8. Sécurité

- `docker/.env` est créé en mode **600** et n'est **jamais** versionné.
- MySQL et le backend ne sont publiés que sur `127.0.0.1` ; seul le frontend
  écoute sur le réseau (80 en HTTP local, 443 via nginx hôte en HTTPS).
- Les images du bundle contiennent l'application compilée, pas de secrets.
  Le mot de passe admin est généré sur le serveur au premier démarrage.
- Sauvegarder le bundle et `docker/.env` séparément : sans `.env`, la base
  existante reste illisible (mot de passe root inconnu).
