-- ============================================================
-- V8 — Barèmes d'appréciation configurables par cycle
-- ------------------------------------------------------------
-- Remplace les seuils codés en dur (GradeObservation) par une
-- configuration administrable, sans toucher aux formules de
-- moyennes ni aux coefficients.
--
-- 1. `appreciation_scales` : un barème par cycle, versionné.
--    Une modification d'un barème DÉJÀ UTILISÉ crée une nouvelle
--    version (l'ancienne reste consultable) : les bulletins déjà
--    générés gardent l'appréciation qu'ils ont imprimée
--    (référence `bulletins.appreciation_scale_id`).
-- 2. `appreciation_bands` : intervalles [borne min incluse,
--    borne max exclue], le dernier incluant la borne maximale.
-- 3. `bulletins.appreciation_scale_id` : épingle la version.
-- 4. Permissions APPRECIATION_READ / WRITE.
-- 5. Amorçage : le barème d'exemple (7 bandes) pour chacun des
--    5 cycles existants, actif, version 1 — pour qu'aucune
--    appréciation ne devienne introuvable après la migration.
--
-- La migration est IDEMPOTENTE.
-- ============================================================


-- ------------------------------------------------------------------
-- 1. Barèmes
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `appreciation_scales` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cycle` varchar(15) COLLATE utf8mb4_unicode_ci NOT NULL,
  `min_note` decimal(5,2) NOT NULL DEFAULT 0.00,
  `max_note` decimal(5,2) NOT NULL DEFAULT 20.00,
  `version` int NOT NULL DEFAULT 1,
  `active` tinyint(1) NOT NULL DEFAULT 0,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scale_cycle_version` (`cycle`, `version`),
  KEY `idx_scale_cycle_active` (`cycle`, `active`),
  CONSTRAINT `fk_scale_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ------------------------------------------------------------------
-- 2. Intervalles
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `appreciation_bands` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scale_id` bigint NOT NULL,
  `lower_bound` decimal(5,2) NOT NULL,
  `upper_bound` decimal(5,2) NOT NULL,
  `label` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `observation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_band_scale` (`scale_id`, `sort_order`),
  CONSTRAINT `fk_band_scale` FOREIGN KEY (`scale_id`)
      REFERENCES `appreciation_scales` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ------------------------------------------------------------------
-- 3. Épinglage de la version sur les bulletins
-- ------------------------------------------------------------------

SET @has := (SELECT COUNT(*)
               FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'bulletins'
                AND COLUMN_NAME = 'appreciation_scale_id');

SET @sql := IF(@has = 0,
               'ALTER TABLE `bulletins` ADD COLUMN `appreciation_scale_id` bigint NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk := (SELECT COUNT(*)
                  FROM information_schema.TABLE_CONSTRAINTS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'bulletins'
                   AND CONSTRAINT_NAME = 'fk_bulletin_scale');

SET @sql := IF(@has_fk = 0,
               'ALTER TABLE `bulletins` ADD CONSTRAINT `fk_bulletin_scale` FOREIGN KEY (`appreciation_scale_id`) REFERENCES `appreciation_scales` (`id`)',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ------------------------------------------------------------------
-- 4. Permissions
-- ------------------------------------------------------------------

INSERT IGNORE INTO `permissions` (`name`, `description`) VALUES
  ('APPRECIATION_READ',  'Consulter les barèmes d''appréciation'),
  ('APPRECIATION_WRITE', 'Configurer les barèmes d''appréciation');

INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `roles` r
  JOIN `permissions` p ON p.name IN ('APPRECIATION_READ', 'APPRECIATION_WRITE')
 WHERE r.name IN ('SUPER_ADMIN', 'DIRECTEUR');

INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `roles` r
  JOIN `permissions` p ON p.name = 'APPRECIATION_READ'
 WHERE r.name = 'SECRETAIRE';


-- ------------------------------------------------------------------
-- 5. Amorçage : barème d'exemple pour chaque cycle
-- ------------------------------------------------------------------

INSERT IGNORE INTO `appreciation_scales`
  (`name`, `cycle`, `min_note`, `max_note`, `version`, `active`, `created_at`, `updated_at`)
VALUES
  ('Barème par défaut — Jardin',     'JARDIN',     0.00, 20.00, 1, 1, NOW(), NOW()),
  ('Barème par défaut — Primaire',   'PRIMAIRE',   0.00, 20.00, 1, 1, NOW(), NOW()),
  ('Barème par défaut — Collège',    'COLLEGE',    0.00, 20.00, 1, 1, NOW(), NOW()),
  ('Barème par défaut — Lycée',      'LYCEE',      0.00, 20.00, 1, 1, NOW(), NOW()),
  ('Barème par défaut — Université', 'UNIVERSITE', 0.00, 20.00, 1, 1, NOW(), NOW());

INSERT INTO `appreciation_bands`
  (`scale_id`, `lower_bound`, `upper_bound`, `label`, `sort_order`)
SELECT s.id, b.lo, b.hi, b.label, b.ord
  FROM `appreciation_scales` s
  JOIN (
        SELECT 0.00  AS lo, 8.00  AS hi, 'Très insuffisant' AS label, 1 AS ord
  UNION ALL SELECT 8.00,  10.00, 'Insuffisant', 2
  UNION ALL SELECT 10.00, 12.00, 'Passable', 3
  UNION ALL SELECT 12.00, 14.00, 'Assez bien', 4
  UNION ALL SELECT 14.00, 16.00, 'Bien', 5
  UNION ALL SELECT 16.00, 18.00, 'Très bien', 6
  UNION ALL SELECT 18.00, 20.00, 'Excellent', 7
  ) b
 WHERE s.version = 1
   AND NOT EXISTS (SELECT 1 FROM `appreciation_bands` x WHERE x.scale_id = s.id);
