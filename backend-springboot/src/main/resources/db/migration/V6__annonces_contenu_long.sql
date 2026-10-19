-- ============================================================
-- V6 — Contenu des annonces : tinytext -> mediumtext
-- ------------------------------------------------------------
-- Le contenu des annonces etait declare en `tinytext` (255 OCTETS,
-- soit environ 127 caracteres accentues en utf8mb4) alors que :
--   * l'entite Java le declare en @Lob (donc sans limite applicative) ;
--   * `AnnouncementRequest.content` n'avait aucune validation de taille ;
--   * `database/schema.sql` le declare en `TEXT`.
--
-- Consequence en production : une annonce de trois phrases depassait la
-- colonne, MySQL repondait « Data too long for column 'content' », et la
-- transaction etait annulee (annonce + notifications perdues) avec un
-- message generique.
--
-- Hibernate `validate` ne pouvait pas detecter l'ecart : il compare le type
-- JDBC (tinytext et mediumtext sont tous deux LONGVARCHAR), pas la taille.
--
-- mediumtext (16 Mo) couvre tout usage realiste et reste bien en dessous de
-- longtext. La colonne n'est elargie QUE si elle est encore etroite : la
-- migration est IDEMPOTENTE et ne retrecit jamais une colonne deja large.
-- ============================================================

SET @type := (SELECT DATA_TYPE
                FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'announcements'
                 AND COLUMN_NAME = 'content');

SET @sql := IF(@type IN ('tinytext', 'text'),
               'ALTER TABLE `announcements` MODIFY COLUMN `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
