-- ============================================================
-- V11 — Convocations elargies : eleve, parent ou enseignant
-- ------------------------------------------------------------
-- Une convocation ne pouvait viser qu'un eleve (`student_id NOT NULL`).
-- L'administration doit aussi pouvoir convoquer un parent d'eleve ou un
-- enseignant : on ajoute une cible explicite (`target`) et les deux
-- rattachements correspondants, tous facultatifs.
--
-- Aucune cle etrangere n'est ajoutee sur `parent_id` / `teacher_id` :
-- une convocation est une piece d'HISTORIQUE. Une cascade la supprimerait
-- silencieusement avec le parent ou l'enseignant convoque — exactement le
-- defaut corrige ailleurs sur la suppression des enseignants. Les colonnes
-- sont indexees pour la consultation, et le service resout toujours
-- l'entite reelle avant ecriture (aucun identifiant fantome possible).
--
-- Les convocations existantes visaient toutes un eleve : le defaut
-- 'ELEVE' conserve leur sens.
--
-- `target` est un ENUM natif, comme `status` et les autres colonnes
-- d'enumeration du schema : Hibernate 6 mappe @Enumerated(STRING) sur un
-- enum MySQL et REFUSE au demarrage (ddl-auto: validate) un simple varchar.
-- ============================================================

ALTER TABLE `convocations`
  MODIFY COLUMN `student_id` bigint NULL,
  ADD COLUMN `target` enum('ELEVE','PARENT','ENSEIGNANT') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ELEVE',
  ADD COLUMN `parent_id` bigint DEFAULT NULL,
  ADD COLUMN `teacher_id` bigint DEFAULT NULL,
  ADD KEY `idx_convocation_parent` (`parent_id`),
  ADD KEY `idx_convocation_teacher` (`teacher_id`);
