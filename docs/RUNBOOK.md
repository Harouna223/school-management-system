# Runbook — incidents Sahel Edu

Ce document s'ouvre **quand quelque chose ne va pas**. Il ne remplace pas
[DEPLOIEMENT_UBUNTU.md](DEPLOIEMENT_UBUNTU.md) (qui explique l'installation) :
il donne, pour chaque panne courante, l'ordre exact des vérifications.

Principe : **diagnostiquer avant de réparer.** Redémarrer la pile résout un
symptôme sur deux et efface les traces qui expliquaient l'autre.

---

## Les cinq commandes à lancer d'abord

```bash
sudo /usr/local/sbin/sahel-edu-healthcheck          # ce qui ne va pas, en clair
systemctl status sahel-edu --no-pager               # état de la pile
docker compose -f /opt/sahel-edu/docker/docker-compose.yml ps
journalctl -u sahel-edu -n 100 --no-pager           # journaux de la pile
df -h / /srv                                        # espace disque
```

Le contrôle de santé couvre déjà la majorité des cas. S'il est muet, le
problème est probablement en amont du serveur (DNS, réseau, registrar).

---

## 1. Le site ne répond pas

**Symptôme** : navigateur en timeout, ou `502 Bad Gateway`.

```bash
# 1. La pile tourne-t-elle ?
docker compose -f /opt/sahel-edu/docker/docker-compose.yml ps

# 2. Le backend répond-il en local ?
curl -s http://127.0.0.1:8080/actuator/health

# 3. Le frontend répond-il en local ?
curl -sI http://127.0.0.1:$(grep -E '^FRONTEND_PORT=' /opt/sahel-edu/docker/.env | cut -d= -f2)/ | head -1

# 4. nginx hôte (si HTTPS est actif) ?
sudo nginx -t && systemctl status nginx --no-pager
```

| Constat | Cause | Action |
|---|---|---|
| Conteneurs absents | pile arrêtée | `sudo systemctl start sahel-edu` |
| Backend absent, MySQL `healthy` | plantage applicatif | voir §2 |
| Backend `UP` mais 502 | nginx hôte pointe au mauvais port | `FRONTEND_PORT` du `.env` vs `proxy_pass` de `/etc/nginx/sites-available/sahel-edu` |
| Tout est `UP` en local, rien en public | DNS ou pare-feu | `getent hosts DOMAINE`, `sudo ufw status` |
| `nginx -t` échoue | configuration hôte invalide | corriger avant tout `reload` |

---

## 2. Le backend redémarre en boucle

```bash
docker logs --tail 120 school-backend
```

Les causes réelles, par ordre de fréquence :

1. **Migration Flyway en échec** — le message contient `Validate failed` ou
   `Migration V… was not applied`. Le code est plus récent que la base, ou
   l'inverse après une restauration. → revenir à la version de code
   correspondant à l'archive (§6), ou restaurer la sauvegarde prise avant la
   mise à jour.
2. **`JWT_SECRET` refusé** — `REQUIRE_SECURE_SECRETS=true` et secret vide, resté
   celui du dépôt, ou décodé en moins de 32 octets. → `sudo ./deploy/secrets.sh
   audit`, puis `rotate-jwt`.
3. **`ADMIN_PASSWORD` vide alors qu'aucun compte admin n'existe** — se produit
   après restauration d'une base sans le compte. → renseigner `ADMIN_PASSWORD`
   dans `.env` et redémarrer.
4. **Base injoignable** — `Communications link failure`. → vérifier
   `docker logs school-mysql`, l'espace disque, et que `DB_APP_PASSWORD`
   correspond au compte réel (`secrets.sh rotate-db-app` le réaligne).

---

## 3. Disque plein

```bash
df -h /
sudo du -sh /srv/sahel-edu/* | sort -h
sudo find /srv/sahel-edu/uploads -type f -size +200M -printf '%s %p\n' | sort -nr | head
sudo du -sh /var/lib/docker /var/log
```

Par ordre de récupération, du moins risqué au plus risqué :

```bash
# 1. Journaux systemd (la limite est a 500M, mais un pic est possible)
journalctl --disk-usage
sudo journalctl --vacuum-size=200M

# 2. Anciennes sauvegardes locales (la copie hors site reste)
ls -lt /srv/sahel-edu/backups/db | tail

# 3. Images Docker orphelines (ne supprime pas les images utilisees)
docker image prune -f

# 4. Fichiers pedagogiques obsoletes — APRES avoir verifie qu'ils sont
#    dans la derniere sauvegarde
sudo tar tzf /srv/sahel-edu/backups/uploads/uploads-*.tar.gz | head
```

⚠️ **Ne jamais lancer `docker compose down -v`** : `-v` supprime les volumes,
donc la base de données.

---

## 4. La sauvegarde échoue

```bash
systemctl status sahel-edu-backup --no-pager
journalctl -u sahel-edu-backup -n 50 --no-pager
```

| Message | Cause | Action |
|---|---|---|
| `copie hors site impossible` | disque externe non monté | rebrancher, `mount`, puis relancer |
| `OFFSITE_DIR non defini` | `/etc/sahel-edu/backup.env` incomplet | renseigner la clé |
| `dump vide` / `sans aucune table` | MySQL arrêté ou base vide | vérifier la pile avant de conclure |
| `No space left on device` | disque plein | voir §3 |

La sauvegarde est **volontairement bloquante** : elle échoue plutôt que de
produire une archive inutilisable. Un `failed` dans `systemctl --failed` est le
signal attendu, pas un bug.

Après réparation, vérifier que l'archive est réellement restaurable :

```bash
sudo ./deploy/restore.sh --verify \
  --db "$(ls -t /srv/sahel-edu/backups/db/db-*.sql.gz | head -1)"
```

---

## 5. HTTPS : certificat expiré ou renouvellement en échec

```bash
sudo certbot certificates
sudo certbot renew --dry-run
```

| Erreur | Cause | Action |
|---|---|---|
| `Connection refused` sur le port 80 | UFW ferme 80, ou nginx arrêté | `sudo ufw allow 80`, `systemctl status nginx` |
| `DNS problem: NXDOMAIN` | enregistrement supprimé ou AAAA erroné | corriger le DNS, retenter |
| `too many failed authorizations` | quota Let's Encrypt atteint | attendre (1 h à 1 semaine selon le quota) |
| Certificat valide mais navigateur en alerte | chaîne incomplète | utiliser `fullchain.pem`, pas `cert.pem` |

Renouvellement forcé (uniquement après avoir corrigé la cause) :

```bash
sudo certbot renew --force-renewal
sudo systemctl reload nginx
```

---

## 6. Restaurer des données

```bash
# 1. Toujours vérifier d'abord ce que contient l'archive
sudo ./deploy/restore.sh --verify --db <archive>

# 2. Puis restaurer
sudo ./deploy/restore.sh --db <archive> --uploads <archive-fichiers>
```

Le script prend automatiquement une sauvegarde de l'état actuel dans
`/srv/sahel-edu/backups/db/avant-restauration-*.sql.gz`, et relance la pile même
s'il échoue en cours de route.

Si le backend ne remonte pas après restauration : c'est presque toujours
**Flyway** — l'archive vient d'une version plus récente que le code déployé. Il
faut alors déployer le code correspondant (ou restaurer une archive plus
ancienne), pas insister.

---

## 7. Une mise à jour a cassé quelque chose

```bash
cd /opt/sahel-edu
sudo git log --oneline -5
sudo git checkout <commit-precedent>
sudo docker compose -f docker/docker-compose.yml build
sudo systemctl restart sahel-edu.service
```

> ⚠️ **Ne pas relancer `deploy/update.sh` après un retour arrière** : il fait
> `git checkout main` puis `git pull`, ce qui ramènerait immédiatement la
> version fautive. Le retour arrière tient jusqu'à ce qu'un correctif soit
> publié sur `main`.

Si la mise à jour a appliqué une migration, restaurer la sauvegarde prise
automatiquement par `update.sh` avant la reconstruction.

---

## 8. Comptes et accès

**Mot de passe administrateur perdu.** Il n'existe pas de réinitialisation en
ligne de commande : les mots de passe sont hachés en bcrypt et `ADMIN_PASSWORD`
ne sert qu'à la *création* du compte.

- Voie sûre : restaurer une sauvegarde antérieure au changement de mot de passe.
- Voie rapide, à n'utiliser que si aucun enregistrement ne référence ce compte :
  supprimer la ligne `users` de l'administrateur ; elle sera recréée au
  démarrage suivant avec la valeur d'`ADMIN_PASSWORD` du `.env`.

**Prévention** : créer un second compte administrateur, et conserver les accès
dans un gestionnaire de mots de passe.

**Accès SSH perdu après `harden-server.sh --ssh`.** Le durcissement écrit
`PasswordAuthentication no` : **aucune** connexion par mot de passe n'est plus
possible, pour root comme pour les autres comptes. Une clé perdue ne se répare
donc pas par SSH.

Repasser par la **console de secours du fournisseur** (LWS en propose une, hors
SSH), puis rétablir la clé publique dans `/root/.ssh/authorized_keys` et vérifier
avec `sshd -t` avant de recharger le service.

C'est pour cette raison que le script écrit `PermitRootLogin prohibit-password`
et non `no` : sur un VPS où root est le seul compte, `no` supprimerait la
dernière voie d'accès SSH, y compris pour une clé valide.

---

## 9. Où trouver quoi

| Élément | Emplacement |
|---|---|
| Code | `/opt/sahel-edu` |
| Secrets (`.env`) | `/opt/sahel-edu/docker/.env` (600 root) |
| Configuration sauvegarde | `/etc/sahel-edu/backup.env` (600 root) |
| Configuration alertes | `/etc/sahel-edu/monitor.env` (600 root, facultatif) |
| Données pédagogiques | `/srv/sahel-edu/uploads` (10001:10001) |
| Sauvegardes | `/srv/sahel-edu/backups/{db,uploads}` |
| Scripts d'exploitation | `/usr/local/sbin/sahel-edu-{backup,healthcheck}` |
| Unités systemd | `/etc/systemd/system/sahel-edu*.{service,timer}` |
| nginx hôte | `/etc/nginx/sites-available/sahel-edu` |
| Certificats | `/etc/letsencrypt/live/<domaine>/` |
| Journaux de la pile | `journalctl -u sahel-edu` |
| Journaux applicatifs | `docker logs school-backend` |
| Journaux MySQL | `docker logs school-mysql` |

**À sauvegarder hors du serveur, en plus des archives** : une copie du
`docker/.env` (gestionnaire de mots de passe). Sans elle, un serveur neuf repart
avec de nouveaux secrets — ce qui fonctionne, mais invalide toutes les sessions
et impose de recréer le compte MySQL applicatif.
