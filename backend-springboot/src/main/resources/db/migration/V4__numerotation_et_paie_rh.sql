-- ============================================================
-- V4 — Numérotation persistée et comptabilisation de la paie RH
-- ------------------------------------------------------------
-- 1. Table `number_sequences` : compteurs de numérotation.
--
--    Les numéros de facture, de reçu élève et de reçu de paie
--    enseignant étaient calculés en Java par `count() + 1`.
--    Après une suppression de ligne ou une restauration de
--    sauvegarde, le numéro recalculé retombait sur une valeur déjà
--    émise : la contrainte unique rejetait l'enregistrement, et le
--    paiement du salaire devenait impossible (409 définitif, sans
--    aucune reprise côté paie enseignant).
--
--    Un compteur dédié, incrémenté sous verrou de ligne, ne peut ni
--    collisionner ni régresser.
--
-- 2. Colonne `payrolls.expense_id` : les salaires versés depuis le
--    module RH n'apparaissaient dans AUCUNE dépense, alors que la
--    paie à l'heure en créait une. Les totaux du tableau de bord et
--    le rapport financier sous-estimaient donc les charges
--    salariales.
--
-- Amorçage des compteurs : la valeur de départ est le nombre de
-- lignes déjà présentes. Les anciens numéros étant attribués
-- séquentiellement à partir de 1, ce comptage est une borne
-- supérieure sûre pour les préfixes globaux (FAC-, RCP-) ; pour les
-- reçus de paie (REC-ENS-), dont le numéro contient la date du jour,
-- il garantit également qu'aucun numéro déjà émis aujourd'hui ne
-- peut être réattribué.
--
-- La migration est IDEMPOTENTE.
-- ============================================================


-- ------------------------------------------------------------------
-- 1. Compteurs de numérotation
-- ------------------------------------------------------------------

-- Le nom de colonne est `last_seq_value` : `last_value` est un MOT RÉSERVÉ de
-- MySQL 8, ce qui obligerait tout SQL manuel à l'entourer de backticks.

CREATE TABLE IF NOT EXISTS `number_sequences` (
  `prefix` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_seq_value` bigint NOT NULL DEFAULT 0,
  `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `number_sequences` (`prefix`, `last_seq_value`, `version`)
SELECT 'FAC-', COUNT(*), 0 FROM `invoices`;

INSERT IGNORE INTO `number_sequences` (`prefix`, `last_seq_value`, `version`)
SELECT 'RCP-', COUNT(*), 0 FROM `payments`;

INSERT IGNORE INTO `number_sequences` (`prefix`, `last_seq_value`, `version`)
SELECT 'REC-ENS-', COUNT(*), 0 FROM `teacher_payment_transactions`;


-- ------------------------------------------------------------------
-- 2. payrolls.expense_id
-- ------------------------------------------------------------------

SET @has := (SELECT COUNT(*)
               FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'payrolls'
                AND COLUMN_NAME = 'expense_id');

SET @sql := IF(@has = 0,
               'ALTER TABLE `payrolls` ADD COLUMN `expense_id` bigint NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
