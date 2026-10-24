-- ============================================================
-- V9 — appreciation_scales.cycle : varchar -> enum
-- ------------------------------------------------------------
-- V8 avait créé `cycle` en varchar(15). Hibernate 6 valide les
-- colonnes @Enumerated(STRING) contre un ENUM MySQL : au démarrage,
-- la validation échouait (« found [varchar], but expecting [enum
-- ('jardin','primaire','college','lycee','universite')] ») et toute
-- l'application refusait de démarrer.
--
-- Les tables existantes (students.education_cycle, levels.education_cycle)
-- utilisent déjà cet ENUM : on s'aligne sur la convention du projet.
--
-- V8 n'est PAS modifiée : elle est déjà appliquée, et Flyway vérifie
-- les sommes de contrôle (validate-on-migrate). La correction passe
-- donc par une nouvelle migration.
--
-- IDEMPOTENTE : ne touche à rien si la colonne est déjà un ENUM.
-- ============================================================

SET @type := (SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'appreciation_scales'
                 AND COLUMN_NAME = 'cycle');

SET @sql := IF(@type IS NOT NULL AND @type NOT LIKE 'enum%',
               'ALTER TABLE `appreciation_scales` MODIFY COLUMN `cycle` enum(''JARDIN'',''PRIMAIRE'',''COLLEGE'',''LYCEE'',''UNIVERSITE'') COLLATE utf8mb4_unicode_ci NOT NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
