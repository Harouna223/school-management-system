-- ============================================================
-- V10 — Modèles et apparence des documents
-- ------------------------------------------------------------
-- 1. Table `document_theme_versions` : une ligne par configuration
--    ENREGISTRÉE (jamais modifiée ensuite, sauf l'indicateur actif).
--    C'est l'historique : un thème antérieur peut être réactivé tel quel.
--
--    `config_json` contient la configuration complète et résolue
--    (modèle + couleurs + typographie + mise en page). Aucune couleur
--    n'est stockée en colonne : le moteur PDF résout tout depuis ce JSON,
--    ce qui évite une table de paramètres éclatée et rend le retour
--    arrière trivial (il suffit de réactiver une version).
--
-- 2. `document_style_overrides` : réglages propres à un TYPE de document
--    (niveau 2), facultatifs. Ce ne sont pas des thèmes concurrents mais
--    des remplacements ponctuels de clés (ex. « bulletin en paysage ») :
--    tout ce qui n'est pas mentionné reste celui du thème actif.
--    Aucune ligne = le document suit strictement le thème actif.
--
-- 3. `bulletins.document_style_id` : épingle la version du thème utilisée
--    à la génération d'un bulletin délivré. Modifier le thème ne réécrit
--    donc jamais l'apparence d'un bulletin déjà émis — même logique que
--    `appreciation_scale_id` (V8).
--
-- AUCUNE donnée existante n'est modifiée : la colonne est nullable et
-- les tables sont nouvelles. Un établissement sans configuration continue
-- d'utiliser exactement la charte codée en dur.
--
-- IDEMPOTENTE : réexécutable sans erreur.
-- ============================================================

CREATE TABLE IF NOT EXISTS `document_theme_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `label` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `template` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLASSIC',
  `palette` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `config_json` json NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_document_theme_active` (`active`),
  KEY `idx_document_theme_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `document_style_overrides` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` json NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_overrides_type` (`document_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Épingle du thème sur un bulletin déjà généré (nullable : les bulletins
-- existants restent valides et s'affichent avec la charte historique).
SET @col := (SELECT COUNT(*)
               FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'bulletins'
                AND COLUMN_NAME = 'document_style_id');
SET @sql := IF(@col = 0,
               'ALTER TABLE `bulletins` ADD COLUMN `document_style_id` bigint DEFAULT NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*)
               FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'bulletins'
                AND INDEX_NAME = 'fk_bulletin_document_style');
SET @sql := IF(@idx = 0,
               'ALTER TABLE `bulletins` ADD KEY `fk_bulletin_document_style` (`document_style_id`)',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Configuration initiale : celle qui correspond exactement à la charte
-- actuelle. Elle n'est créée que si la table est vide, et n'est PAS
-- activée : tant que l'administrateur n'a rien enregistré, l'application
-- utilise ses valeurs par défaut codées en dur (aucun changement visible).
INSERT INTO `document_theme_versions` (`label`, `template`, `palette`, `config_json`, `active`)
SELECT 'Charte d''origine', 'CLASSIC', 'BLUE_INSTITUTIONAL',
       JSON_OBJECT(
         'template', 'CLASSIC',
         'palette', 'BLUE_INSTITUTIONAL',
         'showLogo', true, 'logoPosition', 'LEFT',
         'showSchoolName', true, 'showContact', true, 'showAddress', true,
         'headerRule', true, 'titleAlign', 'LEFT',
         'footerText', '', 'institutionalText', '',
         'pageFormat', 'A4', 'pageNumbers', true,
         'zebraStripes', true, 'tableBorders', false,
         'colors', JSON_OBJECT(
            'primary', '#1e3a8a', 'secondary', '#3b82f6', 'accent', '#3b82f6',
            'heading', '#1e3a8a', 'tableHeader', '#1e3a8a', 'headerText', '#ffffff',
            'border', '#cbd5e1', 'text', '#1e293b', 'sectionBackground', '#eff6ff',
            'footerText', '#64748b'),
         'typography', JSON_OBJECT(
            'fontFamily', 'HELVETICA', 'bodySize', 11, 'titleSize', 16,
            'subtitleSize', 9.5, 'tableSize', 9),
         'layout', JSON_OBJECT(
            'marginTop', 32, 'marginRight', 32, 'marginBottom', 70, 'marginLeft', 32,
            'sectionSpacing', 10, 'orientation', 'PORTRAIT')
       ),
       0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `document_theme_versions`);
