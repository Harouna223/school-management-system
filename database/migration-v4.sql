-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM - MIGRATION v4
-- Phases 2-10 : Université LMD étendue
--  - Domaines académiques (P2)
--  - Filières → Domaine ; Programmes enrichis (P2)
--  - Groupes / promotions (P3)
--  - Semestres → année académique (P3)
--  - Statut d'inscription + historique (P4)
--  - Évaluations EC multiples (P5)
--  - Règles académiques configurables (P6)
-- Applicable sur une base existante (idempotent, rétrocompatible).
-- ============================================================

USE school_management;

-- ------------------------------------------------------------------
-- 1. Domaines académiques
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS domains (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  code VARCHAR(20) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_domains_name (name),
  UNIQUE KEY uk_domains_code (code)
) ENGINE=InnoDB;

-- Filière → Domaine (nullable, rétrocompatible)
SET @has_field_domain := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academic_fields' AND COLUMN_NAME = 'domain_id'
);
SET @ddl = IF(@has_field_domain = 0,
  'ALTER TABLE academic_fields ADD COLUMN domain_id BIGINT NULL AFTER department_id',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Programme : crédits requis + conditions d'admission
SET @has_prog_credits := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'programs' AND COLUMN_NAME = 'total_credits'
);
SET @ddl2 = IF(@has_prog_credits = 0,
  'ALTER TABLE programs ADD COLUMN total_credits INT NULL AFTER academic_year,
   ADD COLUMN admission_requirements TEXT NULL AFTER total_credits',
  'SELECT 1');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ------------------------------------------------------------------
-- 2. Semestre → année académique
-- ------------------------------------------------------------------
SET @has_sem_year := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'semesters' AND COLUMN_NAME = 'academic_year'
);
SET @ddl3 = IF(@has_sem_year = 0,
  'ALTER TABLE semesters ADD COLUMN academic_year VARCHAR(20) NULL AFTER order_index',
  'SELECT 1');
PREPARE stmt3 FROM @ddl3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- ------------------------------------------------------------------
-- 3. Groupes / promotions universitaires
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS university_groups (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  code VARCHAR(20) NOT NULL,
  field_id BIGINT NOT NULL,
  level VARCHAR(10),
  academic_year VARCHAR(20),
  student_count INT,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_field_code (field_id, code),
  KEY idx_groups_field (field_id),
  CONSTRAINT fk_groups_field FOREIGN KEY (field_id) REFERENCES academic_fields (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 4. Statut d'inscription + historique universitaire
-- ------------------------------------------------------------------
SET @has_enroll_status := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lmd_enrollments' AND COLUMN_NAME = 'enrollment_status'
);
SET @ddl4 = IF(@has_enroll_status = 0,
  'ALTER TABLE lmd_enrollments ADD COLUMN enrollment_status VARCHAR(15) NULL AFTER active',
  'SELECT 1');
PREPARE stmt4 FROM @ddl4;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

CREATE TABLE IF NOT EXISTS enrollment_histories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  field_id BIGINT NULL,
  from_level VARCHAR(10),
  to_level VARCHAR(10),
  from_semester VARCHAR(5),
  to_semester VARCHAR(5),
  academic_year VARCHAR(20),
  enrollment_status VARCHAR(15),
  reason VARCHAR(500),
  recorded_by VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_enroll_hist_student (student_id),
  CONSTRAINT fk_enroll_hist_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
  CONSTRAINT fk_enroll_hist_field FOREIGN KEY (field_id) REFERENCES academic_fields (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 5. Évaluations EC (multi-évaluations : CC, TD, TP, projet, oral, examen)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ec_evaluations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ec_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  evaluation_type VARCHAR(15) NOT NULL,
  session INT NOT NULL DEFAULT 1,
  value DECIMAL(5,2) NOT NULL,
  max_value DECIMAL(5,2) NOT NULL DEFAULT 20,
  appreciation VARCHAR(100),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ec_eval (ec_id, student_id, evaluation_type, session),
  KEY idx_ec_eval_ec (ec_id),
  KEY idx_ec_eval_student (student_id),
  CONSTRAINT fk_ec_eval_ec FOREIGN KEY (ec_id) REFERENCES course_units (id) ON DELETE CASCADE,
  CONSTRAINT fk_ec_eval_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 6. Règles académiques configurables
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS academic_rules (
  id BIGINT NOT NULL AUTO_INCREMENT,
  cycle VARCHAR(15),
  rule_key VARCHAR(50) NOT NULL,
  rule_value VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_cycle_key (cycle, rule_key)
) ENGINE=InnoDB;

-- Règles par défaut (LMD) : seuils configurables de validation et mentions
INSERT INTO academic_rules (cycle, rule_key, rule_value, description) VALUES
('UNIVERSITE', 'validation_threshold', '10', 'Note minimale pour valider une UE/EC'),
('UNIVERSITE', 'compensation_enabled', 'true', 'Activer la compensation entre UE'),
('UNIVERSITE', 'mention_passable', '10', 'Mention Passable à partir de'),
('UNIVERSITE', 'mention_assez_bien', '12', 'Mention Assez bien à partir de'),
('UNIVERSITE', 'mention_bien', '14', 'Mention Bien à partir de'),
('UNIVERSITE', 'mention_tres_bien', '16', 'Mention Très bien à partir de'),
('UNIVERSITE', 'mention_excellent', '18', 'Mention Excellent à partir de')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value);
