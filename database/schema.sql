-- ============================================================
-- SCHOOL MANAGEMENT SYSTEM
-- Schéma de base de données MySQL
-- Version : 1.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS school_management
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE school_management;

-- ============================================================
-- 1. SÉCURITÉ : utilisateurs, rôles, permissions
-- ============================================================

CREATE TABLE roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB;

CREATE TABLE permissions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_permissions_name (name)
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
  CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(100) NOT NULL,
  email VARCHAR(150) NULL,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  phone VARCHAR(30),
  avatar VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_enabled (enabled)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 2. STRUCTURE PÉDAGOGIQUE : sections, niveaux, classes, salles
-- ============================================================

CREATE TABLE sections (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sections_name (name)
) ENGINE=InnoDB;

CREATE TABLE levels (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  code VARCHAR(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_levels_code (code)
) ENGINE=InnoDB;

CREATE TABLE rooms (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  capacity INT NOT NULL DEFAULT 30,
  location VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rooms_name (name)
) ENGINE=InnoDB;

CREATE TABLE classes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  code VARCHAR(20) NOT NULL,
  level_id BIGINT NOT NULL,
  section_id BIGINT,
  room_id BIGINT,
  capacity INT NOT NULL DEFAULT 30,
  PRIMARY KEY (id),
  UNIQUE KEY uk_classes_code (code),
  KEY idx_classes_level (level_id),
  KEY idx_classes_section (section_id),
  CONSTRAINT fk_classes_level FOREIGN KEY (level_id) REFERENCES levels (id),
  CONSTRAINT fk_classes_section FOREIGN KEY (section_id) REFERENCES sections (id),
  CONSTRAINT fk_classes_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB;

-- ============================================================
-- 3. PERSONNES : parents, élèves, enseignants
-- ============================================================

CREATE TABLE parents (
  id BIGINT NOT NULL AUTO_INCREMENT,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  phone VARCHAR(30),
  email VARCHAR(150),
  profession VARCHAR(100),
  address VARCHAR(255),
  user_id BIGINT,
  PRIMARY KEY (id),
  KEY idx_parents_user (user_id),
  CONSTRAINT fk_parents_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE students (
  id BIGINT NOT NULL AUTO_INCREMENT,
  matricule VARCHAR(30) NOT NULL,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  birth_date DATE,
  birth_place VARCHAR(150),
  gender ENUM('MALE', 'FEMALE') NOT NULL,
  address VARCHAR(255),
  phone VARCHAR(30),
  email VARCHAR(150),
  photo VARCHAR(255),
  enrollment_date DATE NOT NULL,
  status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED', 'GRADUATED', 'TRANSFERRED', 'RADIATED') NOT NULL DEFAULT 'ACTIVE',
  class_id BIGINT NOT NULL,
  parent_id BIGINT,
  user_id BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_students_matricule (matricule),
  KEY idx_students_class (class_id),
  KEY idx_students_parent (parent_id),
  KEY idx_students_user (user_id),
  KEY idx_students_status (status),
  CONSTRAINT fk_students_class FOREIGN KEY (class_id) REFERENCES classes (id),
  CONSTRAINT fk_students_parent FOREIGN KEY (parent_id) REFERENCES parents (id) ON DELETE SET NULL,
  CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE teachers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  employee_no VARCHAR(30) NOT NULL,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  birth_date DATE,
  gender ENUM('MALE', 'FEMALE') NOT NULL,
  phone VARCHAR(30),
  email VARCHAR(150),
  address VARCHAR(255),
  hire_date DATE NOT NULL,
  contract_type ENUM('CDI', 'CDD', 'VACATAIRE') NOT NULL DEFAULT 'CDD',
  salary DECIMAL(12, 2) NOT NULL DEFAULT 0,
  photo VARCHAR(255),
  user_id BIGINT,
  status ENUM('ACTIVE', 'INACTIVE', 'ON_LEAVE') NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (id),
  UNIQUE KEY uk_teachers_employee_no (employee_no),
  KEY idx_teachers_user (user_id),
  KEY idx_teachers_status (status),
  CONSTRAINT fk_teachers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 4. MATIÈRES ET AFFECTATIONS
-- ============================================================

CREATE TABLE subjects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  code VARCHAR(20) NOT NULL,
  coefficient INT NOT NULL DEFAULT 1,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_subjects_code (code)
) ENGINE=InnoDB;

CREATE TABLE subject_assignments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_assignment (teacher_id, subject_id, class_id),
  CONSTRAINT fk_sa_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
  CONSTRAINT fk_sa_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE,
  CONSTRAINT fk_sa_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 5. EMPLOI DU TEMPS
-- ============================================================

CREATE TABLE schedules (
  id BIGINT NOT NULL AUTO_INCREMENT,
  day_of_week ENUM('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY') NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  class_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  room_id BIGINT,
  PRIMARY KEY (id),
  KEY idx_schedules_class (class_id),
  KEY idx_schedules_teacher (teacher_id),
  KEY idx_schedules_room (room_id),
  KEY idx_schedules_day (day_of_week),
  CONSTRAINT fk_schedules_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE,
  CONSTRAINT fk_schedules_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
  CONSTRAINT fk_schedules_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id),
  CONSTRAINT fk_schedules_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB;

-- ============================================================
-- 6. PRÉSENCES
-- ============================================================

CREATE TABLE attendances (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  date DATE NOT NULL,
  status ENUM('PRESENT', 'ABSENT', 'LATE', 'JUSTIFIED') NOT NULL DEFAULT 'PRESENT',
  justification VARCHAR(255),
  recorded_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attendance (student_id, date),
  KEY idx_attendance_date (date),
  KEY idx_attendance_class (class_id),
  CONSTRAINT fk_att_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
  CONSTRAINT fk_att_class FOREIGN KEY (class_id) REFERENCES classes (id),
  CONSTRAINT fk_att_user FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 7. NOTES ET ÉVALUATIONS
-- ============================================================

CREATE TABLE exams (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  type ENUM('CONTROLE', 'DEVOIR', 'EXAMEN', 'BACCALAUREAT') NOT NULL,
  term ENUM('T1', 'T2', 'T3') NOT NULL,
  academic_year VARCHAR(20) NOT NULL,
  class_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  exam_date DATE,
  coefficient INT NOT NULL DEFAULT 1,
  status ENUM('PLANNED', 'ONGOING', 'COMPLETED', 'DELIBERATED') NOT NULL DEFAULT 'PLANNED',
  PRIMARY KEY (id),
  KEY idx_exams_class (class_id),
  KEY idx_exams_subject (subject_id),
  KEY idx_exams_status (status),
  CONSTRAINT fk_exams_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE,
  CONSTRAINT fk_exams_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE=InnoDB;

CREATE TABLE grades (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  exam_id BIGINT NOT NULL,
  value DECIMAL(5, 2) NOT NULL,
  max_value DECIMAL(5, 2) NOT NULL DEFAULT 20,
  appreciation VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_grade (student_id, exam_id),
  KEY idx_grades_exam (exam_id),
  CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
  CONSTRAINT fk_grades_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE bulletins (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  term ENUM('T1', 'T2', 'T3') NOT NULL,
  academic_year VARCHAR(20) NOT NULL,
  average DECIMAL(5, 2),
  `rank` INT,
  mention VARCHAR(50),
  decision ENUM('ADMIS', 'AJOURNE', 'REDOUBLE'),
  pdf_path VARCHAR(255),
  generated_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bulletin (student_id, term, academic_year),
  KEY idx_bulletins_year (academic_year),
  CONSTRAINT fk_bulletins_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 8. FINANCES
-- ============================================================

CREATE TABLE fee_types (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fee_types_name (name)
) ENGINE=InnoDB;

CREATE TABLE invoices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_no VARCHAR(30) NOT NULL,
  student_id BIGINT NOT NULL,
  fee_type_id BIGINT NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
  due_date DATE NOT NULL,
  status ENUM('UNPAID', 'PARTIAL', 'PAID', 'OVERDUE') NOT NULL DEFAULT 'UNPAID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoices_no (invoice_no),
  KEY idx_invoices_student (student_id),
  KEY idx_invoices_status (status),
  CONSTRAINT fk_invoices_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
  CONSTRAINT fk_invoices_fee FOREIGN KEY (fee_type_id) REFERENCES fee_types (id)
) ENGINE=InnoDB;

CREATE TABLE payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  receipt_no VARCHAR(30) NOT NULL,
  invoice_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  method ENUM('CASH', 'CARD', 'BANK_TRANSFER', 'MOBILE_MONEY', 'CHECK') NOT NULL DEFAULT 'CASH',
  payment_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  recorded_by BIGINT,
  note VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_receipt (receipt_no),
  KEY idx_payments_invoice (invoice_id),
  KEY idx_payments_student (student_id),
  KEY idx_payments_date (payment_date),
  CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
  CONSTRAINT fk_payments_student FOREIGN KEY (student_id) REFERENCES students (id),
  CONSTRAINT fk_payments_user FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE expense_categories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (id),
  UNIQUE KEY uk_exp_cat_name (name)
) ENGINE=InnoDB;

CREATE TABLE expenses (
  id BIGINT NOT NULL AUTO_INCREMENT,
  description VARCHAR(255) NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  category_id BIGINT NOT NULL,
  expense_date DATE NOT NULL,
  paid_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_expenses_date (expense_date),
  KEY idx_expenses_category (category_id),
  CONSTRAINT fk_expenses_category FOREIGN KEY (category_id) REFERENCES expense_categories (id),
  CONSTRAINT fk_expenses_user FOREIGN KEY (paid_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 9. BIBLIOTHÈQUE
-- ============================================================

CREATE TABLE books (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(150),
  isbn VARCHAR(30),
  category VARCHAR(100),
  quantity INT NOT NULL DEFAULT 1,
  available INT NOT NULL DEFAULT 1,
  publisher VARCHAR(150),
  publication_year INT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_books_isbn (isbn),
  KEY idx_books_title (title)
) ENGINE=InnoDB;

CREATE TABLE borrowings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  book_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  borrow_date DATE NOT NULL,
  due_date DATE NOT NULL,
  return_date DATE,
  status ENUM('BORROWED', 'RETURNED', 'OVERDUE') NOT NULL DEFAULT 'BORROWED',
  PRIMARY KEY (id),
  KEY idx_borrowings_book (book_id),
  KEY idx_borrowings_student (student_id),
  KEY idx_borrowings_status (status),
  CONSTRAINT fk_borrowings_book FOREIGN KEY (book_id) REFERENCES books (id),
  CONSTRAINT fk_borrowings_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB;

-- ============================================================
-- 10. RH : congés, contrats, salaires
-- ============================================================

CREATE TABLE leaves (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  type ENUM('ANNUAL', 'SICK', 'MATERNITY', 'UNPAID', 'OTHER') NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(255),
  status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
  approved_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_leaves_teacher (teacher_id),
  KEY idx_leaves_status (status),
  CONSTRAINT fk_leaves_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE,
  CONSTRAINT fk_leaves_user FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 11. COMMUNICATION
-- ============================================================

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT,
  title VARCHAR(150) NOT NULL,
  message VARCHAR(500) NOT NULL,
  type ENUM('INFO', 'SUCCESS', 'WARNING', 'ERROR', 'PAYMENT', 'ABSENCE', 'GRADE', 'ANNOUNCEMENT', 'ACCOUNT') NOT NULL DEFAULT 'INFO',
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_user (user_id),
  KEY idx_notifications_read (is_read),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sender_id BIGINT NOT NULL,
  recipient_id BIGINT NOT NULL,
  subject VARCHAR(150) NOT NULL,
  content TEXT NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_messages_sender (sender_id),
  KEY idx_messages_recipient (recipient_id),
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 12. AUDIT LOGS
-- ============================================================

CREATE TABLE audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT,
  username VARCHAR(50),
  action VARCHAR(100) NOT NULL,
  entity VARCHAR(100),
  entity_id BIGINT,
  details VARCHAR(500),
  ip_address VARCHAR(45),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_user (user_id),
  KEY idx_audit_action (action),
  KEY idx_audit_created (created_at),
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 13. JETONS DE RAFRAÎCHISSEMENT (Refresh Tokens)
-- ============================================================

CREATE TABLE refresh_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token VARCHAR(500) NOT NULL,
  expiry_date DATETIME NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_token (token),
  KEY idx_refresh_user (user_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 15. RH : contrats et paie
-- ============================================================

CREATE TABLE contracts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  base_salary DECIMAL(12,2) NOT NULL,
  description VARCHAR(255),
  status VARCHAR(15) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_contracts_teacher (teacher_id),
  KEY idx_contracts_status (status),
  CONSTRAINT fk_contracts_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE payrolls (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  month_date DATE NOT NULL,
  base_salary DECIMAL(12,2) NOT NULL,
  allowances DECIMAL(12,2) NOT NULL DEFAULT 0,
  deductions DECIMAL(12,2) NOT NULL DEFAULT 0,
  net_salary DECIMAL(12,2) NOT NULL,
  status VARCHAR(15) NOT NULL,
  paid_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payrolls_teacher_month (teacher_id, month_date),
  KEY idx_payrolls_month (month_date),
  KEY idx_payrolls_status (status),
  CONSTRAINT fk_payrolls_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 16. Présences des enseignants
-- ============================================================

CREATE TABLE teacher_attendances (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  attendance_date DATE NOT NULL,
  status VARCHAR(15) NOT NULL,
  justification VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ta_teacher_date (teacher_id, attendance_date),
  KEY idx_ta_date (attendance_date),
  CONSTRAINT fk_ta_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 17. Annonces administratives
-- ============================================================

CREATE TABLE announcements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  target_role VARCHAR(20),
  pinned BOOLEAN NOT NULL DEFAULT FALSE,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_announcements_role (target_role),
  KEY idx_announcements_created (created_at),
  CONSTRAINT fk_announcements_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- DONNÉES INITIALES
-- ============================================================

-- Rôles
INSERT INTO roles (name, description) VALUES
('SUPER_ADMIN', 'Administrateur système complet'),
('DIRECTEUR', 'Direction de l''établissement'),
('COMPTABLE', 'Gestion financière'),
('SECRETAIRE', 'Secrétariat et scolarité'),
('ENSEIGNANT', 'Personnel enseignant'),
('PARENT', 'Parent ou tuteur d''élève'),
('ELEVE', 'Élève de l''établissement');

-- Permissions
INSERT INTO permissions (name, description) VALUES
('STUDENT_READ', 'Consulter les élèves'),
('STUDENT_WRITE', 'Créer / modifier les élèves'),
('STUDENT_DELETE', 'Supprimer les élèves'),
('TEACHER_READ', 'Consulter les enseignants'),
('TEACHER_WRITE', 'Créer / modifier les enseignants'),
('CLASS_READ', 'Consulter les classes'),
('CLASS_WRITE', 'Créer / modifier les classes'),
('SUBJECT_READ', 'Consulter les matières'),
('SUBJECT_WRITE', 'Créer / modifier les matières'),
('GRADE_READ', 'Consulter les notes'),
('GRADE_WRITE', 'Saisir les notes'),
('PAYMENT_READ', 'Consulter les paiements'),
('PAYMENT_WRITE', 'Enregistrer les paiements'),
('ATTENDANCE_READ', 'Consulter les présences'),
('ATTENDANCE_WRITE', 'Pointer les présences'),
('EXAM_READ', 'Consulter les examens'),
('EXAM_WRITE', 'Gérer les examens'),
('SCHEDULE_READ', 'Consulter les emplois du temps'),
('SCHEDULE_WRITE', 'Gérer les emplois du temps'),
('REPORT_READ', 'Consulter les rapports'),
('USER_READ', 'Consulter les utilisateurs'),
('USER_WRITE', 'Gérer les utilisateurs'),
('FINANCE_READ', 'Consulter la comptabilité'),
('FINANCE_WRITE', 'Gérer la comptabilité'),
('LIBRARY_READ', 'Consulter la bibliothèque'),
('LIBRARY_WRITE', 'Gérer la bibliothèque'),
('HR_READ', 'Consulter le personnel'),
('HR_WRITE', 'Gérer le personnel');

-- Rôles -> Permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'SUPER_ADMIN';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DIRECTEUR' AND p.name IN ('STUDENT_READ','STUDENT_WRITE','TEACHER_READ','TEACHER_WRITE','CLASS_READ','CLASS_WRITE','SUBJECT_READ','SUBJECT_WRITE','GRADE_READ','PAYMENT_READ','ATTENDANCE_READ','EXAM_READ','EXAM_WRITE','SCHEDULE_READ','SCHEDULE_WRITE','REPORT_READ','FINANCE_READ','LIBRARY_READ','HR_READ');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPTABLE' AND p.name IN ('PAYMENT_READ','PAYMENT_WRITE','FINANCE_READ','FINANCE_WRITE','REPORT_READ','STUDENT_READ');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SECRETAIRE' AND p.name IN ('STUDENT_READ','STUDENT_WRITE','TEACHER_READ','CLASS_READ','ATTENDANCE_READ','ATTENDANCE_WRITE','PAYMENT_READ','EXAM_READ','SCHEDULE_READ','LIBRARY_READ','LIBRARY_WRITE');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ENSEIGNANT' AND p.name IN ('GRADE_READ','GRADE_WRITE','ATTENDANCE_READ','ATTENDANCE_WRITE','EXAM_READ','SCHEDULE_READ','STUDENT_READ','SUBJECT_READ');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PARENT' AND p.name IN ('STUDENT_READ','GRADE_READ','ATTENDANCE_READ','PAYMENT_READ','SCHEDULE_READ');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ELEVE' AND p.name IN ('STUDENT_READ','GRADE_READ','ATTENDANCE_READ','SCHEDULE_READ');

-- Utilisateur administrateur par défaut
-- Mot de passe : Admin@123 (hash BCrypt)
INSERT INTO users (username, password, email, first_name, last_name, phone, enabled) VALUES
('admin', '$2a$10$xFhSN6cNWtjRm4nveOEt.eRHgobokP.VsOa.MIgOIRsv8/tctgZpS', 'admin@school.com', 'Super', 'Admin', '+237600000000', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.name = 'SUPER_ADMIN';

-- Sections, niveaux, salles, classes par défaut
INSERT INTO sections (name, description) VALUES
('Générale', 'Section d''enseignement général'),
('Scientifique', 'Section scientifique'),
('Littéraire', 'Section littéraire');

INSERT INTO levels (name, code) VALUES
('6ème', '6E'), ('5ème', '5E'), ('4ème', '4E'), ('3ème', '3E'),
('2nde', '2ND'), ('1ère', '1RE'), ('Terminale', 'TLE');

INSERT INTO rooms (name, capacity, location) VALUES
('Salle A1', 40, 'Bâtiment A - Rez-de-chaussée'),
('Salle A2', 40, 'Bâtiment A - Rez-de-chaussée'),
('Salle B1', 35, 'Bâtiment B - 1er étage'),
('Salle B2', 35, 'Bâtiment B - 1er étage'),
('Salle Info', 25, 'Bâtiment C - 2e étage');

INSERT INTO classes (name, code, level_id, section_id, room_id, capacity) VALUES
('6ème A', '6A', 1, 1, 1, 40),
('6ème B', '6B', 1, 1, 2, 40),
('5ème A', '5A', 2, 1, 3, 35),
('4ème A', '4A', 3, 1, 4, 35),
('3ème A', '3A', 4, 1, 1, 40),
('2nde A', '2A', 5, 2, 5, 25),
('1ère A', '1A', 6, 3, 3, 35),
('Terminale A', 'TLE A', 7, 2, 4, 35);

-- Matières par défaut
INSERT INTO subjects (name, code, coefficient, description) VALUES
('Mathématiques', 'MAT', 4, 'Mathématiques'),
('Français', 'FR', 4, 'Langue française et littérature'),
('Anglais', 'ANG', 3, 'Langue anglaise'),
('Physique-Chimie', 'PC', 3, 'Physique et chimie'),
('SVT', 'SVT', 2, 'Sciences de la vie et de la terre'),
('Histoire-Géographie', 'HG', 2, 'Histoire et géographie'),
('EPS', 'EPS', 1, 'Éducation physique et sportive'),
('Informatique', 'INFO', 2, 'Initiation à l''informatique');

-- Catégories de dépenses
INSERT INTO expense_categories (name, description) VALUES
('Salaires', 'Rémunération du personnel'),
('Fournitures', 'Fournitures scolaires et de bureau'),
('Maintenance', 'Entretien des bâtiments et équipements'),
('Électricité', 'Factures d''électricité'),
('Eau', 'Factures d''eau'),
('Transport', 'Frais de transport'),
('Autres', 'Autres dépenses');

-- Types de frais scolaires
INSERT INTO fee_types (name, amount, description) VALUES
('Inscription', 25000, 'Frais d''inscription annuelle'),
('Scolarité Trimestre 1', 45000, 'Frais de scolarité T1'),
('Scolarité Trimestre 2', 45000, 'Frais de scolarité T2'),
('Scolarité Trimestre 3', 45000, 'Frais de scolarité T3'),
('Assurance scolaire', 5000, 'Assurance scolaire annuelle'),
('Bibliothèque', 5000, 'Carte de bibliothèque annuelle');