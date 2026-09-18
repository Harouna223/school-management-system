-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM - MIGRATION v7
-- Gestion des heures enseignées & paie des enseignants à l'heure
-- Idempotente : ne modifie aucune table existante.
-- ============================================================

USE school_management;

-- ------------------------------------------------------------------
-- 1. Tarifs horaires des enseignants (historisés par période)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher_hourly_rates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  hourly_rate DECIMAL(12,2) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  school_year_id BIGINT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME,
  PRIMARY KEY (id),
  KEY idx_thr_teacher (teacher_id),
  KEY idx_thr_period (start_date, end_date),
  CONSTRAINT fk_thr_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
  CONSTRAINT fk_thr_year FOREIGN KEY (school_year_id) REFERENCES academic_years (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 2. Heures enseignées par jour
--    Le tarif appliqué (hourly_rate_applied) est figé à la saisie :
--    changer un tarif ne recalcule jamais les anciens mois.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher_work_hours (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  hours DECIMAL(5,2) NOT NULL,
  subject_id BIGINT,
  class_id BIGINT,
  school_year_id BIGINT,
  hourly_rate_applied DECIMAL(12,2) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  observation VARCHAR(255),
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_twh_teacher_date_subject_class (teacher_id, work_date, subject_id, class_id),
  KEY idx_twh_date (work_date),
  KEY idx_twh_teacher (teacher_id),
  CONSTRAINT fk_twh_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
  CONSTRAINT fk_twh_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE SET NULL,
  CONSTRAINT fk_twh_class FOREIGN KEY (class_id) REFERENCES school_classes (id) ON DELETE SET NULL,
  CONSTRAINT fk_twh_year FOREIGN KEY (school_year_id) REFERENCES academic_years (id) ON DELETE SET NULL,
  CONSTRAINT fk_twh_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 3. Salaires mensuels des enseignants (paie à l'heure)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher_monthly_payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  month_date DATE NOT NULL,
  school_year_id BIGINT,
  total_hours DECIMAL(8,2) NOT NULL DEFAULT 0,
  hourly_rate DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  amount_paid DECIMAL(12,2) NOT NULL DEFAULT 0,
  remaining_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  status VARCHAR(15) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tmp_teacher_month (teacher_id, month_date),
  KEY idx_tmp_month (month_date),
  KEY idx_tmp_status (status),
  CONSTRAINT fk_tmp_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
  CONSTRAINT fk_tmp_year FOREIGN KEY (school_year_id) REFERENCES academic_years (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 4. Transactions de paiement (total ou partiel) avec reçu
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher_payment_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  monthly_payment_id BIGINT NOT NULL,
  receipt_no VARCHAR(30) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  method VARCHAR(20) NOT NULL,
  payment_date DATETIME NOT NULL,
  reference VARCHAR(100),
  received_by BIGINT,
  observation VARCHAR(255),
  expense_id BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tpt_receipt (receipt_no),
  KEY idx_tpt_payment (monthly_payment_id),
  KEY idx_tpt_date (payment_date),
  CONSTRAINT fk_tpt_payment FOREIGN KEY (monthly_payment_id)
    REFERENCES teacher_monthly_payments (id) ON DELETE CASCADE,
  CONSTRAINT fk_tpt_user FOREIGN KEY (received_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 5. Clôture mensuelle (verrouille la saisie des heures du mois)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher_month_closures (
  id BIGINT NOT NULL AUTO_INCREMENT,
  month_date DATE NOT NULL,
  school_year_id BIGINT,
  closed_by BIGINT,
  closed_at DATETIME,
  reopened_by BIGINT,
  reopened_at DATETIME,
  closed BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tmc_month (month_date),
  CONSTRAINT fk_tmc_year FOREIGN KEY (school_year_id) REFERENCES academic_years (id) ON DELETE SET NULL,
  CONSTRAINT fk_tmc_closed_by FOREIGN KEY (closed_by) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_tmc_reopened_by FOREIGN KEY (reopened_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 6. Permissions du module (idempotent)
--    Les rôles sont rattachés automatiquement par DataInitializer.
-- ------------------------------------------------------------------
INSERT INTO permissions (name, description)
SELECT 'HOURS_READ', 'Consulter les heures enseignées et les salaires des enseignants'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'HOURS_READ');

INSERT INTO permissions (name, description)
SELECT 'HOURS_WRITE', 'Saisir et corriger les heures enseignées'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'HOURS_WRITE');

INSERT INTO permissions (name, description)
SELECT 'HOURS_PAY', 'Effectuer les paiements des salaires des enseignants'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'HOURS_PAY');
