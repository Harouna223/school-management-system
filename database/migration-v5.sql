-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM - MIGRATION v5
-- Phase 9 : Convocations scolaires et universitaires
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
-- Phase 10 : Calendrier des examens universitaires
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS university_exams (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_unit_id BIGINT NOT NULL,
  room_id BIGINT NULL,
  supervisor_id BIGINT NULL,
  exam_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  session INT NOT NULL DEFAULT 1,
  group_name VARCHAR(50),
  semester VARCHAR(20),
  notes VARCHAR(255),
  PRIMARY KEY (id),
  KEY idx_univ_exam_ec (course_unit_id),
  KEY idx_univ_exam_date (exam_date),
  CONSTRAINT fk_univ_exam_ec FOREIGN KEY (course_unit_id) REFERENCES course_units (id) ON DELETE CASCADE,
  CONSTRAINT fk_univ_exam_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE SET NULL,
  CONSTRAINT fk_univ_exam_supervisor FOREIGN KEY (supervisor_id) REFERENCES teachers (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- Phase 11 : Stages universitaires
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