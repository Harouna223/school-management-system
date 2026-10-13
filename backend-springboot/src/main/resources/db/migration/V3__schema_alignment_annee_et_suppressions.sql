-- ============================================================
-- V3 — Alignement du schéma sur les règles métier
-- ------------------------------------------------------------
-- 1. `students.class_id` devient NULLABLE.
--
--    Pourquoi : la migration historique `database/migration-v3.sql`
--    (`ALTER TABLE students MODIFY class_id BIGINT NULL;`) n'avait jamais
--    été reportée dans `db/migration`, alors que Flyway ne lit QUE ce
--    dossier. Sur toute base créée par V1, la colonne était donc
--    `NOT NULL` alors que l'entité JPA, `StudentService.applyClassAndCycle`
--    et l'interface (option « — Aucune — ») autorisent l'absence de classe
--    (élève en attente d'affectation, cycle universitaire sans classe).
--
--    Conséquence avant correction : `POST /api/students` sans classe
--    renvoyait « Donnée en conflit » (MySQL 1048 transformé en 409 par le
--    gestionnaire global), et toute modification partielle d'un élève
--    remettait la classe à NULL et échouait de la même façon.
--
--    Aucune donnée n'est modifiée : la contrainte est seulement assouplie.
--
-- 2. Garde-fous de suppression : les évaluations d'une classe ne peuvent
--    plus être supprimées en cascade par la base. Les notes sont
--    désormais protégées côté service (`ExamService.delete`,
--    `ClassService.delete`) ET côté base, pour qu'une suppression SQL
--    directe ne puisse pas détruire un historique de notes.
--
-- La migration est IDEMPOTENTE : elle interroge information_schema avant
-- d'agir et peut donc être rejouée sans erreur.
-- ============================================================


-- ------------------------------------------------------------------
-- 1. students.class_id nullable
-- ------------------------------------------------------------------

SET @nullable := (SELECT IS_NULLABLE
                    FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'students'
                     AND COLUMN_NAME = 'class_id');

SET @sql := IF(@nullable = 'NO',
               'ALTER TABLE `students` MODIFY `class_id` bigint NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ------------------------------------------------------------------
-- 2. fk_exams_class : CASCADE -> RESTRICT
--    (une classe portant encore des évaluations ne doit pas pouvoir
--     déclencher la suppression de leurs notes)
-- ------------------------------------------------------------------

SET @fk_exams := (SELECT DELETE_RULE
                    FROM information_schema.REFERENTIAL_CONSTRAINTS
                   WHERE CONSTRAINT_SCHEMA = DATABASE()
                     AND CONSTRAINT_NAME = 'fk_exams_class');

SET @sql := IF(@fk_exams = 'CASCADE',
               'ALTER TABLE `exams` DROP FOREIGN KEY `fk_exams_class`',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk := (SELECT COUNT(*)
                  FROM information_schema.TABLE_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'exams'
                   AND CONSTRAINT_NAME = 'fk_exams_class'
                   AND CONSTRAINT_TYPE = 'FOREIGN KEY');

SET @sql := IF(@has_fk = 0,
               'ALTER TABLE `exams` ADD CONSTRAINT `fk_exams_class` '
               'FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE RESTRICT',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ------------------------------------------------------------------
-- 3. fk_grades_exam : CASCADE -> RESTRICT
--    (une évaluation portant des notes ne doit plus pouvoir les emporter)
-- ------------------------------------------------------------------

SET @fk_grades := (SELECT DELETE_RULE
                     FROM information_schema.REFERENTIAL_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA = DATABASE()
                      AND CONSTRAINT_NAME = 'fk_grades_exam');

SET @sql := IF(@fk_grades = 'CASCADE',
               'ALTER TABLE `grades` DROP FOREIGN KEY `fk_grades_exam`',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk := (SELECT COUNT(*)
                  FROM information_schema.TABLE_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'grades'
                   AND CONSTRAINT_NAME = 'fk_grades_exam'
                   AND CONSTRAINT_TYPE = 'FOREIGN KEY');

SET @sql := IF(@has_fk = 0,
               'ALTER TABLE `grades` ADD CONSTRAINT `fk_grades_exam` '
               'FOREIGN KEY (`exam_id`) REFERENCES `exams` (`id`) ON DELETE RESTRICT',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
