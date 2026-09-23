import api from './axios'

/**
 * Endpoints du module authentification.
 */
export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }),
  register: (data) => api.post('/auth/register', data),
  changePassword: (data) => api.post('/auth/change-password', data),
}

/**
 * Endpoints du tableau de bord.
 */
export const dashboardApi = {
  stats: (cycle) => api.get('/dashboard/stats', { params: cycle ? { cycle } : undefined }),
  universityStats: () => api.get('/dashboard/university-stats'),
  universityReport: () => api.get('/dashboard/university-report'),
  auditLogs: (params) => api.get('/dashboard/audit-logs', { params }),
}

/**
 * Endpoints du module élèves.
 */
export const studentApi = {
  search: (params) => api.get('/students', { params }),
  get: (id) => api.get(`/students/${id}`),
  create: (data) => api.post('/students', data),
  update: (id, data) => api.put(`/students/${id}`, data),
  remove: (id) => api.delete(`/students/${id}`),
  transfer: (id, data) => api.patch(`/students/${id}/transfer`, null, { params: data }),
  radiate: (id, reason) => api.patch(`/students/${id}/radiate`, null, { params: { reason } }),
  reinscribe: (id, data) => api.patch(`/students/${id}/reinscribe`, null, { params: data }),
  history: (id) => api.get(`/students/${id}/history`),
  uploadPhoto: (id, file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post(`/students/${id}/photo`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  exportPdf: (params) => api.get('/students/export/pdf', { params, responseType: 'blob' }),
  exportExcel: (params) => api.get('/students/export/excel', { params, responseType: 'blob' }),
  importTemplate: () => api.get('/students/import/template', { responseType: 'blob' }),
  importExcel: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/students/import/excel', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  qr: (id) => api.get(`/students/${id}/qr`, { responseType: 'blob' }),
  certificate: (id) => api.get(`/students/${id}/certificate`, { responseType: 'blob' }),
  card: (id) => api.get(`/students/${id}/card`, { responseType: 'blob' }),
  attendanceCertificate: (id) => api.get(`/students/${id}/attendance-certificate`, { responseType: 'blob' }),
}

/**
 * Endpoints du module enseignants.
 */
export const teacherApi = {
  search: (params) => api.get('/teachers', { params }),
  get: (id) => api.get(`/teachers/${id}`),
  create: (data) => api.post('/teachers', data),
  update: (id, data) => api.put(`/teachers/${id}`, data),
  updateStatus: (id, status) => api.patch(`/teachers/${id}/status`, null, { params: { status } }),
  remove: (id) => api.delete(`/teachers/${id}`),
  exportExcel: (params) => api.get('/teachers/export/excel', { params, responseType: 'blob' }),
  uploadPhoto: (id, file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post(`/teachers/${id}/photo`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

/**
 * Endpoints du module classes.
 */
export const classApi = {
  search: (params) => api.get('/classes', { params }),
  all: () => api.get('/classes/all'),
  get: (id) => api.get(`/classes/${id}`),
  create: (data) => api.post('/classes', data),
  update: (id, data) => api.put(`/classes/${id}`, data),
  remove: (id) => api.delete(`/classes/${id}`),
  levels: () => api.get('/classes/levels'),
  sections: () => api.get('/classes/sections'),
  rooms: () => api.get('/classes/rooms'),
  createLevel: (data) => api.post('/classes/levels', data),
  updateLevel: (id, data) => api.put(`/classes/levels/${id}`, data),
  deleteLevel: (id) => api.delete(`/classes/levels/${id}`),
  createSection: (data) => api.post('/classes/sections', data),
  updateSection: (id, data) => api.put(`/classes/sections/${id}`, data),
  deleteSection: (id) => api.delete(`/classes/sections/${id}`),
  createRoom: (data) => api.post('/classes/rooms', data),
  updateRoom: (id, data) => api.put(`/classes/rooms/${id}`, data),
  deleteRoom: (id) => api.delete(`/classes/rooms/${id}`),
}

/**
 * Endpoints du module matières.
 */
export const subjectApi = {
  search: (params) => api.get('/subjects', { params }),
  all: () => api.get('/subjects/all'),
  get: (id) => api.get(`/subjects/${id}`),
  create: (data) => api.post('/subjects', data),
  update: (id, data) => api.put(`/subjects/${id}`, data),
  remove: (id) => api.delete(`/subjects/${id}`),
  assignments: (params) => api.get('/subjects/assignments', { params }),
  assign: (data) => api.post('/subjects/assignments', data),
  unassign: (id) => api.delete(`/subjects/assignments/${id}`),
}

/**
 * Endpoints du module emplois du temps.
 */
export const scheduleApi = {
  all: () => api.get('/schedules'),
  byClass: (classId) => api.get(`/schedules/class/${classId}`),
  byTeacher: (teacherId) => api.get(`/schedules/teacher/${teacherId}`),
  byRoom: (roomId) => api.get(`/schedules/room/${roomId}`),
  create: (data) => api.post('/schedules', data),
  update: (id, data) => api.put(`/schedules/${id}`, data),
  remove: (id) => api.delete(`/schedules/${id}`),
  checkConflict: (params) => api.get('/schedules/conflict', { params }),
}

/**
 * Endpoints du module présences.
 */
export const attendanceApi = {
  byClassAndDate: (classId, date) => api.get(`/attendances/class/${classId}`, { params: { date } }),
  byStudent: (studentId) => api.get(`/attendances/student/${studentId}`),
  record: (data) => api.post('/attendances', data),
  justify: (attendanceId, justification) =>
    api.patch(`/attendances/${attendanceId}/justify`, null, { params: { justification } }),
  stats: (studentId) => api.get(`/attendances/student/${studentId}/stats`),
  report: (classId, from, to) => api.get('/attendances/report', { params: { classId, from, to } }),
  reportExcel: (classId, from, to) => api.get('/attendances/report/export/excel', { params: { classId, from, to }, responseType: 'blob' }),
  reportPdf: (classId, from, to) => api.get('/attendances/report/export/pdf', { params: { classId, from, to }, responseType: 'blob' }),
  teachersByDate: (date) => api.get('/attendances/teachers', { params: { date } }),
  recordTeachers: (data) => api.post('/attendances/teachers', data),
}

/**
 * Endpoints du module parents.
 */
export const parentApi = {
  children: () => api.get('/parents/children'),
  childBulletins: (studentId) => api.get(`/parents/children/${studentId}/bulletins`),
  childBulletinPdf: (studentId, bulletinId) => api.get(`/parents/children/${studentId}/bulletins/${bulletinId}/pdf`, { responseType: 'blob' }),
  childAttendances: (studentId) => api.get(`/parents/children/${studentId}/attendances`),
  childGrades: (studentId) => api.get(`/parents/children/${studentId}/grades`),
  childTimeline: (studentId) => api.get(`/parents/children/${studentId}/timeline`),
  childCertificate: (studentId) => api.get(`/students/${studentId}/certificate`, { responseType: 'blob' }),
  childCard: (studentId) => api.get(`/students/${studentId}/card`, { responseType: 'blob' }),
  childAttendanceCertificate: (studentId) => api.get(`/students/${studentId}/attendance-certificate`, { responseType: 'blob' }),
  childQr: (studentId) => api.get(`/students/${studentId}/qr`, { responseType: 'blob' }),
  childInvoices: (studentId) => api.get(`/payments/invoices/student/${studentId}`),
  childPayments: (studentId) => api.get(`/payments/student/${studentId}`),
  childUniversityEnrollments: (studentId) => api.get(`/parents/children/${studentId}/university/enrollments`),
  childReleve: (params) => api.get(`/parents/children/${params.studentId}/university/releve`, { params: { fieldId: params.fieldId, semester: params.semester, session: params.session || 1 }, responseType: 'blob' }),
  childAttestation: (params) => api.get(`/parents/children/${params.studentId}/university/attestation`, { params: { fieldId: params.fieldId, semester: params.semester, session: params.session || 1 }, responseType: 'blob' }),
}

/**
 * Endpoints du module espace personnel.
 */
export const myApi = {
  profile: () => api.get('/my/profile'),
  schedule: () => api.get('/my/schedule'),
  grades: (term) => api.get('/my/grades', { params: { term } }),
  bulletins: () => api.get('/my/bulletins'),
  attendances: () => api.get('/my/attendances'),
  invoices: () => api.get('/my/invoices'),
  teacherProfile: () => api.get('/my/teacher-profile'),
  teacherSchedule: () => api.get('/my/teacher/schedule'),
  teacherClasses: () => api.get('/my/teacher/classes'),
  university: () => api.get('/my/university'),
  universityReleve: (params) => api.get('/my/university/releve', { params }),
  universityHistory: () => api.get('/my/university/history'),
}

/**
 * Endpoints du module examens.
 */
export const examApi = {
  search: (params) => api.get('/exams', { params }),
  byClass: (classId) => api.get(`/exams/class/${classId}`),
  get: (id) => api.get(`/exams/${id}`),
  create: (data) => api.post('/exams', data),
  update: (id, data) => api.put(`/exams/${id}`, data),
  changeStatus: (id, status) => api.patch(`/exams/${id}/status`, null, { params: { status } }),
  remove: (id) => api.delete(`/exams/${id}`),
  gradesByExam: (examId) => api.get(`/exams/${examId}/grades`),
  saveGrade: (data) => api.post('/exams/grades', data),
  gradesByStudent: (studentId) => api.get(`/exams/student/${studentId}/grades`),
  average: (studentId, term) => api.get(`/exams/student/${studentId}/average`, { params: { term } }),
  ranking: (classId, term) => api.get(`/exams/class/${classId}/ranking`, { params: { term } }),
  generateBulletins: (classId, term) =>
    api.post(`/exams/class/${classId}/bulletins`, null, { params: { term } }),
  deliberate: (classId, term) =>
    api.post(`/exams/class/${classId}/deliberation`, null, { params: { term } }),
  deliberationPv: (classId, term) =>
    api.get(`/exams/class/${classId}/deliberation/pv`, { params: { term }, responseType: 'blob' }),
  bulletinsByStudent: (studentId) => api.get(`/exams/student/${studentId}/bulletins`),
  bulletinPdf: (bulletinId) => api.get(`/exams/bulletins/${bulletinId}/pdf`, { responseType: 'blob' }),
  classBulletinsPdf: (classId, term) => api.get(`/exams/class/${classId}/bulletins/pdf`, { params: { term }, responseType: 'blob' }),
}

/**
 * Endpoints du module paiements.
 */
export const paymentApi = {
  searchInvoices: (params) => api.get('/payments/invoices', { params }),
  invoicesByStudent: (studentId) => api.get(`/payments/invoices/student/${studentId}`),
  createInvoice: (data) => api.post('/payments/invoices', data),
  search: (params) => api.get('/payments', { params }),
  byStudent: (studentId) => api.get(`/payments/student/${studentId}`),
  record: (data) => api.post('/payments', data),
  receiptPdf: (paymentId) => api.get(`/payments/${paymentId}/receipt/pdf`, { responseType: 'blob' }),
  exportExcel: (params) => api.get('/payments/export/excel', { params, responseType: 'blob' }),
  exportPdf: (params) => api.get('/payments/export/pdf', { params, responseType: 'blob' }),
  exportInvoicesExcel: (params) => api.get('/payments/invoices/export/excel', { params, responseType: 'blob' }),
  exportInvoicesPdf: (params) => api.get('/payments/invoices/export/pdf', { params, responseType: 'blob' }),
}

/**
 * Endpoints du module finances.
 */
export const financeApi = {
  expenses: (params) => api.get('/finance/expenses', { params }),
  createExpense: (data) => api.post('/finance/expenses', data),
  deleteExpense: (id) => api.delete(`/finance/expenses/${id}`),
  exportExpensesExcel: (params) => api.get('/finance/expenses/export/excel', { params, responseType: 'blob' }),
  feeTypes: () => api.get('/finance/fee-types'),
  createFeeType: (data) => api.post('/finance/fee-types', data),
  updateFeeType: (id, data) => api.put(`/finance/fee-types/${id}`, data),
  deleteFeeType: (id) => api.delete(`/finance/fee-types/${id}`),
  categories: () => api.get('/finance/expense-categories'),
  summary: () => api.get('/finance/summary'),
  markOverdue: () => api.post('/finance/invoices/mark-overdue'),
  remind: () => api.post('/finance/invoices/remind'),
  report: (from, to) => api.get('/finance/report', { params: { from, to } }),
  reportExcel: (from, to) => api.get('/finance/report/export/excel', { params: { from, to }, responseType: 'blob' }),
  reportPdf: (from, to) => api.get('/finance/report/export/pdf', { params: { from, to }, responseType: 'blob' }),
  exportExpensesPdf: (params) => api.get('/finance/expenses/export/pdf', { params, responseType: 'blob' }),
}

/**
 * Endpoints du module paramètres.
 */
export const settingsApi = {
  all: () => api.get('/settings'),
  defaults: () => api.get('/settings/defaults'),
  update: (values) => api.put('/settings', values),
  uploadLogo: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/settings/logo', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  cycles: () => api.get('/settings/cycles'),
  updateCycles: (cycles) => api.put('/settings/cycles', cycles),
}

/**
 * Endpoints du module années scolaires.
 */
export const academicYearApi = {
  all: () => api.get('/academic-years'),
  get: (id) => api.get(`/academic-years/${id}`),
  create: (data) => api.post('/academic-years', data),
  update: (id, data) => api.put(`/academic-years/${id}`, data),
  setCurrent: (id) => api.patch(`/academic-years/${id}/current`),
  remove: (id) => api.delete(`/academic-years/${id}`),
}

/**
 * Endpoints du module sauvegarde / restauration.
 */
export const backupApi = {
  export: () => api.get('/backup/export', { responseType: 'blob' }),
  restore: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/backup/restore', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

/**
 * Endpoints du module universitaire LMD.
 */
export const lmdApi = {
  faculties: () => api.get('/lmd/faculties'),
  createFaculty: (data) => api.post('/lmd/faculties', data),
  deleteFaculty: (id) => api.delete(`/lmd/faculties/${id}`),
  departments: (facultyId) => api.get('/lmd/departments', { params: { facultyId } }),
  createDepartment: (data) => api.post('/lmd/departments', data),
  deleteDepartment: (id) => api.delete(`/lmd/departments/${id}`),
  fields: (departmentId) => api.get('/lmd/fields', { params: { departmentId } }),
  createField: (data) => api.post('/lmd/fields', data),
  deleteField: (id) => api.delete(`/lmd/fields/${id}`),
  ues: (fieldId, semester) => api.get('/lmd/ues', { params: { fieldId, semester } }),
  createUe: (data) => api.post('/lmd/ues', data),
  deleteUe: (id) => api.delete(`/lmd/ues/${id}`),
  enroll: (data) => api.post('/lmd/enrollments', data),
  enrollments: (params) => api.get('/lmd/enrollments', { params }),
  saveGrade: (data) => api.post('/lmd/grades', data),
  result: (params) => api.get('/lmd/result', { params }),
  deliberate: (fieldId, semester, session) => api.post('/lmd/deliberate', null, { params: { fieldId, semester, session } }),
  deliberations: (fieldId, semester) => api.get('/lmd/deliberations', { params: { fieldId, semester } }),
  lock: (fieldId, semester, locked) => api.patch('/lmd/lock', null, { params: { fieldId, semester, locked } }),
  programs: (fieldId) => api.get('/lmd/programs', { params: { fieldId } }),
  createProgram: (data) => api.post('/lmd/programs', data),
  deleteProgram: (id) => api.delete(`/lmd/programs/${id}`),
  semesters: (fieldId) => api.get('/lmd/semesters', { params: { fieldId } }),
  createSemester: (data) => api.post('/lmd/semesters', data),
  deleteSemester: (id) => api.delete(`/lmd/semesters/${id}`),
  ecs: (ueId) => api.get('/lmd/ecs', { params: { ueId } }),
  createEc: (data) => api.post('/lmd/ecs', data),
  deleteEc: (id) => api.delete(`/lmd/ecs/${id}`),
  ueEnrollments: (studentId) => api.get('/lmd/ue-enrollments', { params: { studentId } }),
  enrollUe: (studentId, ueId) => api.post('/lmd/ue-enrollments', null, { params: { studentId, ueId } }),
  unenrollUe: (id) => api.delete(`/lmd/ue-enrollments/${id}`),
  saveEcGrade: (params) => api.post('/lmd/ec-grades', null, { params }),
  ecGrades: (studentId, fieldId) => api.get('/lmd/ec-grades', { params: { studentId, fieldId } }),
  relevePdf: (params) => api.get('/lmd/releve/pdf', { params, responseType: 'blob' }),
  attestationPdf: (params) => api.get('/lmd/attestation/pdf', { params, responseType: 'blob' }),
  universitySchedules: (params) => api.get('/lmd/university-schedules', { params }),
  createUniversitySchedule: (data) => api.post('/lmd/university-schedules', data),
  deleteUniversitySchedule: (id) => api.delete(`/lmd/university-schedules/${id}`),

  // Domaines
  domains: () => api.get('/lmd/domains'),
  createDomain: (data) => api.post('/lmd/domains', data),
  deleteDomain: (id) => api.delete(`/lmd/domains/${id}`),

  // Groupes
  groups: (fieldId) => api.get('/lmd/groups', { params: { fieldId } }),
  createGroup: (data) => api.post('/lmd/groups', data),
  deleteGroup: (id) => api.delete(`/lmd/groups/${id}`),

  // Statut inscription & historique
  updateEnrollmentStatus: (id, status, reason) => api.patch(`/lmd/enrollments/${id}/status`, null, { params: { status, reason } }),
  changeLevel: (id, params) => api.post(`/lmd/enrollments/${id}/change-level`, null, { params }),
  enrollmentHistory: (studentId) => api.get('/lmd/enrollment-history', { params: { studentId } }),

  // Évaluations EC
  ecEvaluations: (ecId, studentId) => api.get('/lmd/ec-evaluations', { params: { ecId, studentId } }),
  createEcEvaluation: (data) => api.post('/lmd/ec-evaluations', data),
  deleteEcEvaluation: (id) => api.delete(`/lmd/ec-evaluations/${id}`),

  // Règles académiques
  academicRules: (cycle) => api.get('/lmd/academic-rules', { params: { cycle } }),
  createAcademicRule: (data) => api.post('/lmd/academic-rules', data),
  deleteAcademicRule: (id) => api.delete(`/lmd/academic-rules/${id}`),

  // Relevé Excel
  releveExcel: (params) => api.get('/lmd/releve/excel', { params, responseType: 'blob' }),

  // Présences universitaires
  universityAttendances: (params) => api.get('/lmd/university-attendances', { params }),
  studentUniversityAttendances: (studentId) => api.get('/lmd/student-university-attendances', { params: { studentId } }),
  saveUniversityAttendance: (params) => api.post('/lmd/university-attendances', null, { params }),
  deleteUniversityAttendance: (id) => api.delete(`/lmd/university-attendances/${id}`),
}

/**
 * Endpoints du module bibliothèque.
 */
export const libraryApi = {
  books: (params) => api.get('/library/books', { params }),
  createBook: (data) => api.post('/library/books', data),
  updateBook: (id, data) => api.put(`/library/books/${id}`, data),
  deleteBook: (id) => api.delete(`/library/books/${id}`),
  borrowings: (params) => api.get('/library/borrowings', { params }),
  borrowingsByStudent: (studentId) => api.get(`/library/borrowings/student/${studentId}`),
  borrow: (data) => api.post('/library/borrowings', data),
  returnBook: (id) => api.patch(`/library/borrowings/${id}/return`),
  markOverdue: () => api.post('/library/borrowings/mark-overdue'),
}

/**
 * Endpoints du module RH.
 */
export const hrApi = {
  leaves: (params) => api.get('/hr/leaves', { params }),
  requestLeave: (data) => api.post('/hr/leaves', data),
  decide: (id, status) => api.patch(`/hr/leaves/${id}/decide`, null, { params: { status } }),
  contracts: (params) => api.get('/hr/contracts', { params }),
  createContract: (data) => api.post('/hr/contracts', data),
  updateContract: (id, data) => api.put(`/hr/contracts/${id}`, data),
  deleteContract: (id) => api.delete(`/hr/contracts/${id}`),
  payrolls: (params) => api.get('/hr/payrolls', { params }),
  generatePayroll: (data) => api.post('/hr/payrolls/generate', data),
  payPayroll: (id) => api.patch(`/hr/payrolls/${id}/pay`),
}

/**
 * Endpoints du module heures enseignées / paie des enseignants à l'heure.
 */
export const teacherHoursApi = {
  // Tarifs horaires
  rates: (params) => api.get('/teacher-hours/rates', { params }),
  createRate: (data) => api.post('/teacher-hours/rates', data),
  toggleRate: (id, active) => api.patch(`/teacher-hours/rates/${id}/status`, null, { params: { active } }),
  // Saisie quotidienne
  workHours: (params) => api.get('/teacher-hours/work-hours', { params }),
  workHoursOfDay: (params) => api.get('/teacher-hours/work-hours/day', { params }),
  recordHours: (data) => api.post('/teacher-hours/work-hours', data),
  updateHours: (id, data) => api.put(`/teacher-hours/work-hours/${id}`, data),
  deleteHours: (id) => api.delete(`/teacher-hours/work-hours/${id}`),
  // Calcul mensuel / paie
  monthly: (params) => api.get('/teacher-hours/monthly', { params }),
  pay: (data) => api.post('/teacher-hours/payments', data),
  transactions: (params) => api.get('/teacher-hours/transactions', { params }),
  receipt: (id) => api.get(`/teacher-hours/payments/receipt/${id}`, { responseType: 'blob' }),
  // Rapports
  reportPdf: (params) => api.get('/teacher-hours/report/monthly', { params, responseType: 'blob' }),
  reportExcel: (params) => api.get('/teacher-hours/report/monthly/excel', { params, responseType: 'blob' }),
  // Clôture mensuelle
  closeMonth: (month) => api.post(`/teacher-hours/months/${month}/close`),
  reopenMonth: (month) => api.post(`/teacher-hours/months/${month}/reopen`),
  monthStatus: (month) => api.get(`/teacher-hours/months/${month}/status`),
  // Espace enseignant
  myMonthly: (params) => api.get('/teacher-hours/my/monthly', { params }),
  myTransactions: () => api.get('/teacher-hours/my/transactions'),
  myReceipt: (id) => api.get(`/teacher-hours/my/receipt/${id}`, { responseType: 'blob' }),
}

export const universityExamApi = {
  list: (params) => api.get('/university-exams', { params }),
  create: (data) => api.post('/university-exams', data),
  update: (id, data) => api.put(`/university-exams/${id}`, data),
  delete: (id) => api.delete(`/university-exams/${id}`),
}

export const stageApi = {
  list: (studentId) => api.get('/stages', { params: { studentId } }),
  create: (data) => api.post('/stages', data),
  update: (id, data) => api.put(`/stages/${id}`, data),
  delete: (id) => api.delete(`/stages/${id}`),
}

export const candidatureApi = {
  list: (params) => api.get('/candidatures', { params }),
  create: (data) => api.post('/candidatures', data),
  updateStatus: (id, status) => api.patch(`/candidatures/${id}/status`, null, { params: { status } }),
  delete: (id) => api.delete(`/candidatures/${id}`),
}

export const memoireApi = {
  list: (studentId) => api.get('/memoires', { params: { studentId } }),
  create: (data) => api.post('/memoires', data),
  update: (id, data) => api.put(`/memoires/${id}`, data),
  delete: (id) => api.delete(`/memoires/${id}`),
}

export const alumnusApi = {
  list: (fieldId) => api.get('/alumni', { params: { fieldId } }),
  create: (data) => api.post('/alumni', data),
  delete: (id) => api.delete(`/alumni/${id}`),
}

export const searchApi = {
  globalSearch: (q) => api.get('/search', { params: { q } }),
}

/**
 * Endpoints du module communication.
 */
export const communicationApi = {
  notifications: (params) => api.get('/communication/notifications', { params }),
  unreadCount: () => api.get('/communication/notifications/unread-count'),
  markRead: (id) => api.patch(`/communication/notifications/${id}/read`),
  markAllRead: () => api.patch('/communication/notifications/read-all'),
  inbox: (params) => api.get('/communication/messages/inbox', { params }),
  sent: (params) => api.get('/communication/messages/sent', { params }),
  send: (data) => api.post('/communication/messages', data),
  unreadMessages: () => api.get('/communication/messages/unread-count'),
  markMessagesRead: () => api.patch('/communication/messages/read-all'),
  announcements: () => api.get('/communication/announcements'),
  allAnnouncements: () => api.get('/communication/announcements/all'),
  createAnnouncement: (data) => api.post('/communication/announcements', data),
  deleteAnnouncement: (id) => api.delete(`/communication/announcements/${id}`),
  messageLogs: (params) => api.get('/communication/message-logs', { params }),
}

/**
 * Endpoints du module convocations.
 */
export const convocationApi = {
  search: (params) => api.get('/convocations', { params }),
  byStudent: (studentId) => api.get(`/convocations/student/${studentId}`),
  create: (data) => api.post('/convocations', data),
  delete: (id) => api.delete(`/convocations/${id}`),
}

/**
 * Endpoints du module utilisateurs.
 */
export const userApi = {
  search: (params) => api.get('/users', { params }),
  get: (id) => api.get(`/users/${id}`),
  roles: () => api.get('/users/roles'),
  updateRoles: (id, roles) => api.put(`/users/${id}/roles`, roles),
  toggleEnabled: (id, enabled) => api.patch(`/users/${id}/enabled`, { enabled }),
  unlock: (id) => api.post(`/users/${id}/unlock`),
  resetPassword: (id, newPassword) => api.post(`/users/${id}/reset-password`, { newPassword }),
}
