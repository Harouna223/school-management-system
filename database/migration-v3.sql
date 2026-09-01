-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM - MIGRATION v3
-- Phase 1 : Multi-cycle éducatif (Jardin → Primaire → Collège → Lycée → Université)
-- Applicable sur une base déjà initialisée (rétrocompatible).
-- ============================================================

USE school_management;

-- ------------------------------------------------------------------
-- 1. Cycle d'enseignement sur les niveaux (levels)
--    La colonne education_cycle existe déjà via Hibernate (ddl-auto:update)
--    sur les installations récentes ; on garantit sa présence ici.
-- ------------------------------------------------------------------
SET @has_level_cycle := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'levels' AND COLUMN_NAME = 'education_cycle'
);
SET @ddl = IF(@has_level_cycle = 0,
  'ALTER TABLE levels ADD COLUMN education_cycle VARCHAR(15) NULL AFTER code',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 2. Rendre class_id nullable sur students
--    Un apprenant universitaire (cycle UNIVERSITE) n'a pas de classe scolaire.
-- ------------------------------------------------------------------
ALTER TABLE students MODIFY class_id BIGINT NULL;

-- ------------------------------------------------------------------
-- 3. Cycle d'enseignement sur les students (colonne ajoutée par Hibernate
--    sur les installations récentes ; garantie ici).
-- ------------------------------------------------------------------
SET @has_student_cycle := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'education_cycle'
);
SET @ddl2 = IF(@has_student_cycle = 0,
  'ALTER TABLE students ADD COLUMN education_cycle VARCHAR(15) NULL AFTER status',
  'SELECT 1');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ------------------------------------------------------------------
-- 4. Alimenter le cycle d'enseignement des niveaux existants (idempotent)
-- ------------------------------------------------------------------
UPDATE levels SET education_cycle = 'JARDIN'  WHERE education_cycle IS NULL AND code IN ('PS','MS','GS');
UPDATE levels SET education_cycle = 'PRIMAIRE' WHERE education_cycle IS NULL AND code IN ('CP','CE1','CE2','CM1','CM2');
UPDATE levels SET education_cycle = 'COLLEGE'  WHERE education_cycle IS NULL AND code IN ('6E','5E','4E','3E');
UPDATE levels SET education_cycle = 'LYCEE'    WHERE education_cycle IS NULL AND code IN ('2ND','1RE','TLE');

-- ------------------------------------------------------------------
-- 5. Mettre à jour le cycle des élèves existants d'après leur classe
--    (via le niveau de la classe), sans écraser un cycle déjà défini.
-- ------------------------------------------------------------------
UPDATE students s
JOIN classes c ON c.id = s.class_id
JOIN levels l ON l.id = c.level_id
SET s.education_cycle = l.education_cycle
WHERE s.education_cycle IS NULL AND l.education_cycle IS NOT NULL;
