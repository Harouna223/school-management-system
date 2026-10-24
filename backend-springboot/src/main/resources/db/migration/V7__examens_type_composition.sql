-- ============================================================
-- V7 — Évaluations de type « COMPOSITION »
-- ------------------------------------------------------------
-- Le bulletin comporte désormais deux colonnes : « Note/classe »
-- (moyenne des évaluations courantes) et « Note/compo » (épreuve de
-- synthèse du trimestre).
--
-- Le type COMPOSITION n'existait pas dans l'énumération `exams.type`.
-- Sans lui, aucune note ne pouvait alimenter la colonne « Note/compo ».
--
-- La migration AJOUTE la valeur sans jamais retirer les valeurs
-- existantes, et ne s'exécute que si elle manque : elle est IDEMPOTENTE.
-- ============================================================

SET @type := (SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'exams'
                 AND COLUMN_NAME = 'type');

SET @sql := IF(@type IS NOT NULL AND @type NOT LIKE '%COMPOSITION%',
               'ALTER TABLE `exams` MODIFY COLUMN `type` enum(''CONTROLE'',''DEVOIR'',''EXAMEN'',''BACCALAUREAT'',''COMPOSITION'') COLLATE utf8mb4_unicode_ci NOT NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
