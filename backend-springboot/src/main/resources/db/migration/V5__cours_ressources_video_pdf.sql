-- ============================================================
-- V5 — Cours pédagogiques : vidéos et documents PDF
-- ------------------------------------------------------------
-- Nouveau module « Cours et ressources ».
--
-- 1. Table `course_resources` : une ressource pédagogique
--    (vidéo ou PDF) avec ses métadonnées, son fichier, son
--    statut de publication et son auteur.
-- 2. Table `course_resource_classes` : classes destinataires.
--    Une ressource peut viser PLUSIEURS classes : la relation
--    est portée par une table de jointure, pas par une colonne
--    `class_id` dupliquée (une ligne par classe créerait des
--    doublons de métadonnées et de fichier).
-- 3. Permissions `COURSE_READ` / `COURSE_WRITE` et attribution
--    aux rôles concernés.
--
-- Choix de conception :
--  * `subject_id` et `author_id` sont en RESTRICT : supprimer
--    une matière ou un compte qui porte des cours échoue
--    explicitement (409) au lieu de détruire silencieusement le
--    contenu pédagogique.
--  * `course_resource_classes` est en CASCADE : retirer une
--    classe ne doit pas bloquer la suppression de la classe,
--    et la seule conséquence est la perte du ciblage (le cours
--    reste, simplement visible de personne). Le service
--    `ClassService` garde déjà les cascades lourdes.
--  * `storage_filename` est UNIQUE mais NULLABLE : un cours
--    peut exister en brouillon sans fichier, et MySQL autorise
--    plusieurs NULL dans un index unique.
--  * Aucun fichier n'est stocké en base : la colonne
--    `storage_filename` porte le chemin RELATIF sous
--    `${UPLOAD_DIR}/courses/`. Le binaire vit sur le volume
--    persistant monté sur l'hôte (/srv/sahel-edu/uploads).
--
-- La migration est IDEMPOTENTE (IF NOT EXISTS / INSERT IGNORE).
-- ============================================================


-- ------------------------------------------------------------------
-- 1. Ressources pédagogiques
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `course_resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci NULL,
  `chapter` varchar(150) COLLATE utf8mb4_unicode_ci NULL,
  `type` enum('VIDEO','PDF') COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci NULL,
  `storage_filename` varchar(255) COLLATE utf8mb4_unicode_ci NULL,
  `size_bytes` bigint NULL,
  `mime_type` varchar(100) COLLATE utf8mb4_unicode_ci NULL,
  `status` enum('DRAFT','PUBLISHED','ARCHIVED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `downloadable` tinyint(1) NOT NULL DEFAULT 1,
  `subject_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `published_at` datetime NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_storage_filename` (`storage_filename`),
  KEY `idx_course_status` (`status`),
  KEY `idx_course_type` (`type`),
  KEY `idx_course_subject` (`subject_id`),
  KEY `idx_course_author` (`author_id`),
  KEY `idx_course_published_at` (`published_at`),
  KEY `idx_course_created_at` (`created_at`),
  CONSTRAINT `fk_course_subject` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`id`),
  CONSTRAINT `fk_course_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ------------------------------------------------------------------
-- 2. Classes destinataires
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `course_resource_classes` (
  `resource_id` bigint NOT NULL,
  `class_id` bigint NOT NULL,
  PRIMARY KEY (`resource_id`, `class_id`),
  KEY `idx_crc_class` (`class_id`),
  CONSTRAINT `fk_crc_resource` FOREIGN KEY (`resource_id`)
      REFERENCES `course_resources` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_crc_class` FOREIGN KEY (`class_id`)
      REFERENCES `classes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ------------------------------------------------------------------
-- 3. Permissions du module
-- ------------------------------------------------------------------

INSERT IGNORE INTO `permissions` (`name`, `description`) VALUES
  ('COURSE_READ',  'Consulter les cours vidéo et PDF'),
  ('COURSE_WRITE', 'Publier et gérer les cours vidéo et PDF');

-- Personnel de direction et secrétariat : lecture + écriture.
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
  FROM `roles` r
  JOIN `permissions` p ON p.name IN ('COURSE_READ', 'COURSE_WRITE')
 WHERE r.name IN ('SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE');

-- Enseignants : lecture + écriture (le service borne l'écriture à
-- leurs classes/matières réellement affectées et à leurs propres
-- ressources).
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
  FROM `roles` r
  JOIN `permissions` p ON p.name IN ('COURSE_READ', 'COURSE_WRITE')
 WHERE r.name = 'ENSEIGNANT';

-- Élèves : lecture seule des ressources publiées de leur classe.
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
  FROM `roles` r
  JOIN `permissions` p ON p.name = 'COURSE_READ'
 WHERE r.name = 'ELEVE';
