-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM
-- Suppression du module universitaire / LMD
-- ------------------------------------------------------------
-- À exécuter UNE SEULE FOIS sur une base EXISTANTE, après avoir
-- déployé le code dont le module universitaire a été retiré.
--
-- Sur une base créée par `schema.sql`, ce script est inutile : le
-- schéma a déjà été régénéré sans ces tables.
--
-- Ce script :
--   1. découple les tables CONSERVÉES (`alumni`, `candidatures`)
--      de `academic_fields`, qui disparaît ;
--   2. supprime les 20 tables du cœur LMD ;
--   3. supprime le rôle ETUDIANT et ses liaisons.
--
-- Les permissions LMD_READ et LMD_WRITE sont CONSERVÉES : elles
-- protègent encore les modules Stages, Mémoires, Alumni et
-- Admission (`SecurityConfig`).
--
-- ⚠️ OPÉRATION IRRÉVERSIBLE. Sauvegarde préalable :
--   docker exec school-mysql sh -c "mysqldump -uroot -p<mdp> --single-transaction \
--     --no-tablespaces school_management \
--     academic_fields academic_rules course_units departments domains \
--     ec_evaluations ec_grades enrollment_histories faculties lmd_deliberations \
--     lmd_enrollments programs semesters ue_enrollments ue_grades \
--     university_attendances university_exams university_groups \
--     university_schedules university_units \
--     roles permissions role_permissions user_roles > /tmp/backup-lmd.sql"
-- ============================================================

USE school_management;

-- ------------------------------------------------------------
-- 1. Découplage des tables conservées
--    `field_id` (clé étrangère vers academic_fields) est remplacé
--    par `field_name` (filière en texte libre).
-- ------------------------------------------------------------

ALTER TABLE alumni DROP FOREIGN KEY FKftknnmlbx3qncndiw0wofqllr;
ALTER TABLE alumni DROP COLUMN field_id;
ALTER TABLE alumni ADD COLUMN field_name VARCHAR(150) NULL;

ALTER TABLE candidatures DROP FOREIGN KEY FKgbdrh8qr0olrr3w45sg6wmkhy;
ALTER TABLE candidatures DROP INDEX idx_candidature_field;
ALTER TABLE candidatures DROP COLUMN field_id;
ALTER TABLE candidatures ADD COLUMN field_name VARCHAR(150) NULL;

-- ------------------------------------------------------------
-- 2. Suppression des 20 tables du cœur LMD
--    (contraintes désactivées : 19 clés étrangères relient ces
--    tables entre elles, l'ordre de suppression n'a pas d'importance)
-- ------------------------------------------------------------

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS academic_fields;
DROP TABLE IF EXISTS academic_rules;
DROP TABLE IF EXISTS course_units;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS domains;
DROP TABLE IF EXISTS ec_evaluations;
DROP TABLE IF EXISTS ec_grades;
DROP TABLE IF EXISTS enrollment_histories;
DROP TABLE IF EXISTS faculties;
DROP TABLE IF EXISTS lmd_deliberations;
DROP TABLE IF EXISTS lmd_enrollments;
DROP TABLE IF EXISTS programs;
DROP TABLE IF EXISTS semesters;
DROP TABLE IF EXISTS ue_enrollments;
DROP TABLE IF EXISTS ue_grades;
DROP TABLE IF EXISTS university_attendances;
DROP TABLE IF EXISTS university_exams;
DROP TABLE IF EXISTS university_groups;
DROP TABLE IF EXISTS university_schedules;
DROP TABLE IF EXISTS university_units;

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 3. Suppression du rôle ETUDIANT
--    Les tables `academic_years`, `levels`, `stages`, `memoires`,
--    `alumni` et `candidatures` sont CONSERVÉES.
-- ------------------------------------------------------------

DELETE FROM role_permissions
 WHERE role_id IN (SELECT id FROM roles WHERE name = 'ETUDIANT');

DELETE FROM user_roles
 WHERE role_id IN (SELECT id FROM roles WHERE name = 'ETUDIANT');

DELETE FROM roles WHERE name = 'ETUDIANT';

-- ------------------------------------------------------------
-- Contrôle final : il ne doit plus rester aucune table LMD.
-- ------------------------------------------------------------

SELECT COUNT(*) AS tables_lmd_restantes
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('academic_fields', 'academic_rules', 'course_units', 'departments',
                      'domains', 'ec_evaluations', 'ec_grades', 'enrollment_histories',
                      'faculties', 'lmd_deliberations', 'lmd_enrollments', 'programs',
                      'semesters', 'ue_enrollments', 'ue_grades', 'university_attendances',
                      'university_exams', 'university_groups', 'university_schedules',
                      'university_units');
