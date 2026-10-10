-- ============================================================
-- V2 — Suppression du module universitaire / LMD
-- ------------------------------------------------------------
-- Le cursus universitaire (LMD) a ete retire du produit : plus
-- aucune entite JPA ne mappe les tables supprimees ici.
--
-- Cette migration :
--   1. decouple les tables CONSERVEES (`alumni`, `candidatures`)
--      de `academic_fields` : la filiere devient un texte libre
--      (`field_name`) au lieu d'une cle etrangere ;
--   2. supprime les 20 tables du coeur LMD ;
--   3. supprime le role ETUDIANT et ses liaisons.
--
-- Les permissions LMD_READ et LMD_WRITE sont CONSERVEES : elles
-- protegent encore les modules annexes Stages, Memoires, Alumni
-- et Admission (voir SecurityConfig).
--
-- La table `academic_years` (annee scolaire) et l'entite `Level`
-- (niveaux scolaires ET option UNIVERSITE) sont egalement
-- conservees : elles ne font pas partie du module LMD.
--
-- La migration est IDEMPOTENTE : elle verifie l'existence des
-- colonnes et des contraintes avant d'agir, et peut donc etre
-- rejouee sans erreur sur une base deja nettoyee.
-- ============================================================


-- ------------------------------------------------------------------
-- 1. Decouplage des tables conservees
-- ------------------------------------------------------------------

-- 1.1 `alumni` : cle etrangere vers academic_fields
SET @fk := (SELECT CONSTRAINT_NAME
              FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'alumni'
               AND COLUMN_NAME = 'field_id'
               AND REFERENCED_TABLE_NAME IS NOT NULL
             LIMIT 1);
SET @sql := IF(@fk IS NULL, 'DO 0', CONCAT('ALTER TABLE `alumni` DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 `alumni` : colonne field_id -> field_name
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumni' AND COLUMN_NAME = 'field_id');
SET @sql := IF(@has = 0, 'DO 0', 'ALTER TABLE `alumni` DROP COLUMN `field_id`');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumni' AND COLUMN_NAME = 'field_name');
SET @sql := IF(@has > 0, 'DO 0',
               'ALTER TABLE `alumni` ADD COLUMN `field_name` VARCHAR(150) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 `candidatures` : cle etrangere vers academic_fields
SET @fk := (SELECT CONSTRAINT_NAME
              FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'candidatures'
               AND COLUMN_NAME = 'field_id'
               AND REFERENCED_TABLE_NAME IS NOT NULL
             LIMIT 1);
SET @sql := IF(@fk IS NULL, 'DO 0', CONCAT('ALTER TABLE `candidatures` DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 `candidatures` : colonne field_id -> field_name
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'candidatures' AND COLUMN_NAME = 'field_id');
SET @sql := IF(@has = 0, 'DO 0', 'ALTER TABLE `candidatures` DROP COLUMN `field_id`');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'candidatures' AND COLUMN_NAME = 'field_name');
SET @sql := IF(@has > 0, 'DO 0',
               'ALTER TABLE `candidatures` ADD COLUMN `field_name` VARCHAR(150) NOT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ------------------------------------------------------------------
-- 2. Suppression des 20 tables du coeur LMD
--    Contraintes desactivees : 19 cles etrangeres relient ces tables
--    entre elles, l'ordre de suppression n'a donc pas d'importance.
-- ------------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `academic_fields`;
DROP TABLE IF EXISTS `academic_rules`;
DROP TABLE IF EXISTS `course_units`;
DROP TABLE IF EXISTS `departments`;
DROP TABLE IF EXISTS `domains`;
DROP TABLE IF EXISTS `ec_evaluations`;
DROP TABLE IF EXISTS `ec_grades`;
DROP TABLE IF EXISTS `enrollment_histories`;
DROP TABLE IF EXISTS `faculties`;
DROP TABLE IF EXISTS `lmd_deliberations`;
DROP TABLE IF EXISTS `lmd_enrollments`;
DROP TABLE IF EXISTS `programs`;
DROP TABLE IF EXISTS `semesters`;
DROP TABLE IF EXISTS `ue_enrollments`;
DROP TABLE IF EXISTS `ue_grades`;
DROP TABLE IF EXISTS `university_attendances`;
DROP TABLE IF EXISTS `university_exams`;
DROP TABLE IF EXISTS `university_groups`;
DROP TABLE IF EXISTS `university_schedules`;
DROP TABLE IF EXISTS `university_units`;

SET FOREIGN_KEY_CHECKS = 1;


-- ------------------------------------------------------------------
-- 3. Suppression du role ETUDIANT
-- ------------------------------------------------------------------

DELETE rp FROM `role_permissions` rp
  JOIN `roles` r ON r.id = rp.role_id
 WHERE r.name = 'ETUDIANT';

DELETE ur FROM `user_roles` ur
  JOIN `roles` r ON r.id = ur.role_id
 WHERE r.name = 'ETUDIANT';

DELETE FROM `roles` WHERE name = 'ETUDIANT';
