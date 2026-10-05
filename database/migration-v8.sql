-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM - MIGRATION v8
-- Sécurité : hachage des refresh tokens existants (SHA-256, préfixe « sha256: »)
-- Idempotente : ne modifie que les lignes stockées en clair (sans le préfixe).
-- Compatible avec AuthService.createRefreshToken() (stocke désormais le hash).
-- ============================================================

USE school_management;

-- 1) Sauvegarde réversible, à conserver jusqu'à vérification post-migration.
CREATE TABLE IF NOT EXISTS refresh_tokens_backup_v8 AS SELECT * FROM refresh_tokens;
SELECT COUNT(*) AS lignes_sauvegardees FROM refresh_tokens_backup_v8;

-- 2) Hachage des jetons encore en clair.
UPDATE refresh_tokens
SET token = CONCAT('sha256:', SHA2(token, 256))
WHERE token NOT LIKE 'sha256:%'
  AND token IS NOT NULL;

-- 3) Contrôle : total | hachés | encore en clair (= 0 attendu).
SELECT COUNT(*) AS total,
       SUM(token LIKE 'sha256:%') AS hashes,
       SUM(token NOT LIKE 'sha256:%') AS en_clair
FROM refresh_tokens;