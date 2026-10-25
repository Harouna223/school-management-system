-- ============================================================
-- V12 — Inscription d'une candidature d'admission
-- ------------------------------------------------------------
-- Une candidature acceptee reste jusque-la un dossier isole : rien
-- ne la reliait a l'eleve reellement inscrit. Cette migration :
--   1. ajoute le rattachement `candidatures.student_id` ;
--   2. ajoute le statut INSCRITE, qui distingue une candidature
--      acceptee d'une candidature dont l'eleve existe vraiment.
--
-- La migration est IDEMPOTENTE : les colonnes et la contrainte sont
-- verifiees avant d'agir (une reprise sur base partiellement migree
-- ne doit pas echouer).
-- ============================================================


-- ------------------------------------------------------------------
-- 1. Rattachement de la candidature a l'eleve cree
-- ------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'candidatures'
                AND COLUMN_NAME = 'student_id');
SET @sql := IF(@has > 0, 'DO 0',
               'ALTER TABLE `candidatures` ADD COLUMN `student_id` bigint DEFAULT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ON DELETE SET NULL : supprimer un eleve ne doit jamais etre bloque par une
-- candidature. Le dossier d'admission survit, simplement detache.
SET @has_fk := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'candidatures'
                   AND CONSTRAINT_NAME = 'fk_candidature_student'
                   AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @sql := IF(@has_fk > 0, 'DO 0',
               'ALTER TABLE `candidatures`
                  ADD KEY `idx_candidature_student` (`student_id`),
                  ADD CONSTRAINT `fk_candidature_student` FOREIGN KEY (`student_id`)
                    REFERENCES `students` (`id`) ON DELETE SET NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ------------------------------------------------------------------
-- 2. Statut INSCRITE
--    Le type ENUM natif est obligatoire : Hibernate 6 valide le
--    schema sur un enum MySQL, pas sur un varchar (voir V11).
-- ------------------------------------------------------------------
ALTER TABLE `candidatures`
  MODIFY COLUMN `status` enum('EN_ATTENTE','RECUE','ACCEPTEE','LISTE_ATTENTE','REFUSEE','INSCRITE')
    COLLATE utf8mb4_unicode_ci NOT NULL;
