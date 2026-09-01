-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM - MIGRATION v6
-- Phase 5 : Admission, Mémoires, Alumni, Doctorat
-- ============================================================

USE school_management;

-- ------------------------------------------------------------------
-- 1. Candidatures d'admission universitaire
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS candidatures (
  id BIGINT NOT NULL AUTO_INCREMENT,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  birth_date DATE,
  email VARCHAR(150),
  phone VARCHAR(30),
  field_id BIGINT NOT NULL,
  level VARCHAR(20),
  reference VARCHAR(50),
  notes TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_candidature_field (field_id),
  CONSTRAINT fk_candidature_field FOREIGN KEY (field_id) REFERENCES academic_fields (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 2. Mémoires / soutenances
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS memoires (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  subject VARCHAR(200) NOT NULL,
  director_id BIGINT,
  defense_date DATE,
  jury VARCHAR(100),
  defense_location VARCHAR(150),
  grade DECIMAL(5,2),
  decision TEXT,
  status VARCHAR(20),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_memoire_student (student_id),
  CONSTRAINT fk_memoire_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
  CONSTRAINT fk_memoire_director FOREIGN KEY (director_id) REFERENCES teachers (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 3. Alumni (diplômés)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alumni (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  field_id BIGINT,
  diploma VARCHAR(20),
  academic_year VARCHAR(20),
  email VARCHAR(150),
  phone VARCHAR(30),
  current_job VARCHAR(200),
  company VARCHAR(200),
  notes VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_alumni_student (student_id),
  KEY idx_alumni_field (field_id),
  CONSTRAINT fk_alumni_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE SET NULL,
  CONSTRAINT fk_alumni_field FOREIGN KEY (field_id) REFERENCES academic_fields (id) ON DELETE SET NULL
) ENGINE=InnoDB;
