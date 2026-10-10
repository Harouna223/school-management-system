-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM - MIGRATION v5
-- Convocations et stages
-- ============================================================

USE school_management;

CREATE TABLE IF NOT EXISTS convocations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  reference VARCHAR(30) NOT NULL,
  student_id BIGINT NOT NULL,
  context VARCHAR(15) NOT NULL,
  subject VARCHAR(200) NOT NULL,
  message TEXT,
  convocation_date DATE NOT NULL,
  time DATETIME NOT NULL,
  location VARCHAR(150) NOT NULL,
  status VARCHAR(15) NOT NULL DEFAULT 'SENT',
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_convocation_reference (reference),
  KEY idx_convocation_student (student_id),
  CONSTRAINT fk_convocation_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
  CONSTRAINT fk_convocation_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- Stages
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  company VARCHAR(150) NOT NULL,
  company_contact VARCHAR(150),
  subject VARCHAR(200),
  start_date DATE,
  end_date DATE,
  supervisor_id BIGINT,
  convention_ref VARCHAR(50),
  report_submitted TINYINT(1) DEFAULT 0,
  defense_date DATE,
  grade DECIMAL(5,2),
  evaluation TEXT,
  status VARCHAR(20),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_stage_student (student_id),
  CONSTRAINT fk_stage_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
  CONSTRAINT fk_stage_supervisor FOREIGN KEY (supervisor_id) REFERENCES teachers (id) ON DELETE SET NULL
) ENGINE=InnoDB;