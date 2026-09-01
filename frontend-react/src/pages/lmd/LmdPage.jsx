import { useState, useEffect } from 'react'
import {
  Box, Card, CardContent, Grid, TextField, MenuItem, Button, Typography, Tabs, Tab,
  Dialog, DialogTitle, DialogContent, DialogActions, Chip, IconButton, Select, FormControl, InputLabel,
} from '@mui/material'
import {
  AccountTree, MenuBook, EditNote, FactCheck, Add, Delete, Lock, LockOpen, Save, PictureAsPdf,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { lmdApi, studentApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { downloadBlob } from '../../utils/format'
import StatusChip from '../../components/StatusChip'

const SEMESTERS = ['S1', 'S2', 'S3', 'S4', 'S5', 'S6', 'S7', 'S8', 'S9', 'S10', 'S11', 'S12']
const CYCLES = ['LICENCE', 'MASTER', 'DOCTORAT']

/**
 * Module universitaire LMD : structure, UE, notes, délibérations.
 */
export default function LmdPage() {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState(0)

  // Structure
  const [faculties, setFaculties] = useState([])
  const [departments, setDepartments] = useState([])
  const [fields, setFields] = useState([])
  const [facultyId, setFacultyId] = useState('')
  const [departmentId, setDepartmentId] = useState('')

  // Domaines
  const [domains, setDomains] = useState([])
  const [domainName, setDomainName] = useState('')
  const [domainCode, setDomainCode] = useState('')

  // Groupes / promotions
  const [groups, setGroups] = useState([])
  const [groupFieldId, setGroupFieldId] = useState('')
  const [groupName, setGroupName] = useState('')
  const [groupCode, setGroupCode] = useState('')
  const [groupLevel, setGroupLevel] = useState('')

  // Règles académiques
  const [rules, setRules] = useState([])
  const [ruleKey, setRuleKey] = useState('')
  const [ruleValue, setRuleValue] = useState('')
  const [ruleDescription, setRuleDescription] = useState('')

  // UE
  const [ues, setUes] = useState([])
  const [ueFieldId, setUeFieldId] = useState('')
  const [ueSemester, setUeSemester] = useState('S1')
  const [ueDialog, setUeDialog] = useState(false)
  const [ueForm, setUeForm] = useState({ code: '', name: '', coefficient: 1, credits: 0, semester: 'S1', field: { id: '' }, type: '', optionalUe: false })
  const [ecs, setEcs] = useState([])
  const [ecDialog, setEcDialog] = useState(false)
  const [ecForm, setEcForm] = useState({ code: '', name: '', credits: 0, coefficient: 1, volumeCm: 0, volumeTd: 0, volumeTp: 0, ue: { id: '' } })

  // Programmes de formation
  const [programs, setPrograms] = useState([])
  const [programFieldId, setProgramFieldId] = useState('')
  const [programDialog, setProgramDialog] = useState(false)
  const [programForm, setProgramForm] = useState({ name: '', code: '', diploma: '', duration: 3, academicYear: '', field: { id: '' } })

  // Notes
  const [gradeFieldId, setGradeFieldId] = useState('')
  const [gradeSemester, setGradeSemester] = useState('S1')
  const [enrollments, setEnrollments] = useState([])
  const [students, setStudents] = useState([])
  const [grades, setGrades] = useState({})
  const [selectedUe, setSelectedUe] = useState('')

  // Notes EC (multi-évaluations)
  const [ecGradeFieldId, setEcGradeFieldId] = useState('')
  const [ecGradeUeId, setEcGradeUeId] = useState('')
  const [ecGradeEcId, setEcGradeEcId] = useState('')
  const [ecGradeSession, setEcGradeSession] = useState('1')
  const [ecEvalType, setEcEvalType] = useState('EXAMEN')
  const [ecGradeUes, setEcGradeUes] = useState([])
  const [ecGradeEcs, setEcGradeEcs] = useState([])
  const [ecGradesList, setEcGradesList] = useState([])
  const [ecEvalValues, setEcEvalValues] = useState({})

  const EVAL_TYPES = ['CC', 'TD', 'TP', 'PROJET', 'ORAL', 'EXAMEN']

  // Présences universitaires
  const [attFieldId, setAttFieldId] = useState('')
  const [attUeId, setAttUeId] = useState('')
  const [attEcId, setAttEcId] = useState('')
  const [attUes, setAttUes] = useState([])
  const [attEcs, setAttEcs] = useState([])
  const [attDate, setAttDate] = useState(new Date().toISOString().slice(0, 10))
  const [attSessionType, setAttSessionType] = useState('CM')
  const [attStatus, setAttStatus] = useState('PRESENT')
  const [attGroup, setAttGroup] = useState('')
  const [attStudents, setAttStudents] = useState([])
  const [attValues, setAttValues] = useState({})
  const [attList, setAttList] = useState([])
  const [attStudentId, setAttStudentId] = useState('')
  const [attHist, setAttHist] = useState([])

  // Statut d'inscription
  const [enrStatus, setEnrStatus] = useState('')
  const [enrReason, setEnrReason] = useState('')
  const [changeLevelId, setChangeLevelId] = useState('')
  const [changeLevelValue, setChangeLevelValue] = useState('')
  const [changeSemesterValue, setChangeSemesterValue] = useState('')
  const [historyOpen, setHistoryOpen] = useState(false)
  const [historyList, setHistoryList] = useState([])
  const [historyStudent, setHistoryStudent] = useState('')

  // Délibération
  const [delibFieldId, setDelibFieldId] = useState('')
  const [delibSemester, setDelibSemester] = useState('S1')
  const [deliberations, setDeliberations] = useState([])

  const loadFaculties = async () => {
    try {
      const { data } = await lmdApi.faculties()
      setFaculties(data.data || [])
    } catch { /* ignore */ }
  }

  const loadDepartments = async (fid) => {
    if (!fid) { setDepartments([]); setFields([]); return }
    try {
      const { data } = await lmdApi.departments(fid)
      setDepartments(data.data || [])
    } catch { /* ignore */ }
  }

  const loadFields = async (did) => {
    if (!did) { setFields([]); return }
    try {
      const { data } = await lmdApi.fields(did)
      setFields(data.data || [])
    } catch { /* ignore */ }
  }

  useEffect(() => { loadFaculties() }, [])

  // Auto-sélection de la première faculté si aucune n'est encore choisie
  useEffect(() => {
    if (faculties.length > 0 && !facultyId) {
      setFacultyId(String(faculties[0].id))
    }
  }, [faculties, facultyId])

  useEffect(() => { loadDepartments(facultyId); setDepartmentId(''); setFields([]) }, [facultyId])
  useEffect(() => { loadFields(departmentId) }, [departmentId])
  useEffect(() => { setFields([]); setDepartments([]) }, [])

  // Domaines
  useEffect(() => { lmdApi.domains().then((r) => setDomains(r.data.data || [])).catch(() => {}) }, [])
  // Groupes
  useEffect(() => { if (groupFieldId) lmdApi.groups(groupFieldId).then((r) => setGroups(r.data.data || [])).catch(() => {}) }, [groupFieldId])
  // Règles académiques
  useEffect(() => { lmdApi.academicRules().then((r) => setRules(r.data.data || [])).catch(() => {}) }, [])

  // --- Structure : création en cascade ---
  const [facultyName, setFacultyName] = useState('')
  const [facultyCode, setFacultyCode] = useState('')
  const [deptName, setDeptName] = useState('')
  const [deptCode, setDeptCode] = useState('')
  const [deptSubmitted, setDeptSubmitted] = useState(false)
  const [fieldName, setFieldName] = useState('')
  const [fieldCode, setFieldCode] = useState('')
  const [fieldCycle, setFieldCycle] = useState('LICENCE')
  const [fieldDomainId, setFieldDomainId] = useState('')

  const createFaculty = async () => {
    if (!facultyName.trim() || !facultyCode.trim()) {
      toastError('Le nom et le code de la faculté sont obligatoires')
      return
    }
    try {
      await lmdApi.createFaculty({ name: facultyName, code: facultyCode })
      success('Faculté créée')
      setFacultyName(''); setFacultyCode('')
      loadFaculties()
    } catch (err) { toastError(extractError(err)) }
  }

  const createDepartment = async () => {
    setDeptSubmitted(true)
    if (!deptName.trim() || !deptCode.trim()) return
    try {
      await lmdApi.createDepartment({ name: deptName, code: deptCode, faculty: { id: Number(facultyId) } })
      success('Département créé')
      setDeptName(''); setDeptCode(''); setDeptSubmitted(false)
      loadDepartments(facultyId)
    } catch (err) { toastError(extractError(err)) }
  }

  const createField = async () => {
    if (!fieldName.trim() || !fieldCode.trim()) {
      toastError('Le nom et le code de la filière sont obligatoires')
      return
    }
    if (!departmentId) {
      toastError('Veuillez sélectionner un département')
      return
    }
    try {
      await lmdApi.createField({
        name: fieldName, code: fieldCode, cycle: fieldCycle,
        department: { id: Number(departmentId) },
        domain: fieldDomainId ? { id: Number(fieldDomainId) } : undefined,
      })
      success('Filière créée')
      setFieldName(''); setFieldCode(''); setFieldDomainId('')
      loadFields(departmentId)
    } catch (err) { toastError(extractError(err)) }
  }

  const deleteFaculty = async (id) => {
    try { await lmdApi.deleteFaculty(id); success('Faculté supprimée'); loadFaculties() } catch (err) { toastError(extractError(err)) }
  }
  const deleteDepartment = async (id) => {
    try { await lmdApi.deleteDepartment(id); success('Département supprimé'); loadDepartments(facultyId) } catch (err) { toastError(extractError(err)) }
  }
  const deleteField = async (id) => {
    try { await lmdApi.deleteField(id); success('Filière supprimée'); loadFields(departmentId) } catch (err) { toastError(extractError(err)) }
  }

  // --- Domaines ---
  const createDomain = async () => {
    try {
      await lmdApi.createDomain({ name: domainName, code: domainCode })
      success('Domaine créé')
      setDomainName(''); setDomainCode('')
      lmdApi.domains().then((r) => setDomains(r.data.data || [])).catch(() => {})
    } catch (err) { toastError(extractError(err)) }
  }
  const deleteDomain = async (id) => {
    try { await lmdApi.deleteDomain(id); success('Domaine supprimé'); lmdApi.domains().then((r) => setDomains(r.data.data || [])).catch(() => {}) } catch (err) { toastError(extractError(err)) }
  }

  // --- Groupes ---
  const createGroup = async () => {
    try {
      await lmdApi.createGroup({ name: groupName, code: groupCode, level: groupLevel, field: { id: Number(groupFieldId) } })
      success('Groupe créé')
      setGroupName(''); setGroupCode(''); setGroupLevel('')
      lmdApi.groups(groupFieldId).then((r) => setGroups(r.data.data || [])).catch(() => {})
    } catch (err) { toastError(extractError(err)) }
  }
  const deleteGroup = async (id) => {
    try { await lmdApi.deleteGroup(id); success('Groupe supprimé'); lmdApi.groups(groupFieldId).then((r) => setGroups(r.data.data || [])).catch(() => {}) } catch (err) { toastError(extractError(err)) }
  }

  // --- Règles académiques ---
  const createRule = async () => {
    try {
      await lmdApi.createAcademicRule({ cycle: 'UNIVERSITE', ruleKey, ruleValue, description: ruleDescription })
      success('Règle créée')
      setRuleKey(''); setRuleValue(''); setRuleDescription('')
      lmdApi.academicRules().then((r) => setRules(r.data.data || [])).catch(() => {})
    } catch (err) { toastError(extractError(err)) }
  }
  const deleteRule = async (id) => {
    try { await lmdApi.deleteAcademicRule(id); success('Règle supprimée'); lmdApi.academicRules().then((r) => setRules(r.data.data || [])).catch(() => {}) } catch (err) { toastError(extractError(err)) }
  }

  // --- UE ---
  const loadUes = async () => {
    if (!ueFieldId) { setUes([]); return }
    try {
      const { data } = await lmdApi.ues(ueFieldId, ueSemester)
      setUes(data.data || [])
    } catch { /* ignore */ }
  }
  useEffect(() => { loadUes() /* eslint-disable-line */ }, [ueFieldId, ueSemester])

  // --- Programme de formation ---
  const loadPrograms = async () => {
    try { const { data } = await lmdApi.programs(programFieldId || undefined); setPrograms(data.data || []) } catch { /* ignore */ }
  }
  useEffect(() => { loadPrograms() /* eslint-disable-line */ }, [programFieldId])

  const saveProgram = async () => {
    try {
      await lmdApi.createProgram({ ...programForm, field: { id: Number(programForm.field.id) }, duration: Number(programForm.duration) || 3 })
      success('Programme créé')
      setProgramDialog(false)
      setProgramForm({ name: '', code: '', diploma: '', duration: 3, academicYear: '', field: { id: '' } })
      loadPrograms()
    } catch (err) { toastError(extractError(err)) }
  }

  const deleteProgram = async (id) => {
    try { await lmdApi.deleteProgram(id); success('Programme supprimé'); loadPrograms() } catch (err) { toastError(extractError(err)) }
  }

  // --- EC ---
  const loadEcs = async (ueId) => {
    if (!ueId) { setEcs([]); return }
    try { const { data } = await lmdApi.ecs(ueId); setEcs(data.data || []) } catch { /* ignore */ }
  }

  const saveEc = async () => {
    try {
      await lmdApi.createEc({ ...ecForm, ue: { id: Number(ecForm.ue.id) } })
      success('EC créé')
      setEcDialog(false)
      setEcForm({ code: '', name: '', credits: 0, coefficient: 1, volumeCm: 0, volumeTd: 0, volumeTp: 0, ue: { id: '' } })
      loadEcs(ecForm.ue.id)
    } catch (err) { toastError(extractError(err)) }
  }

  const deleteEc = async (id) => {
    try { await lmdApi.deleteEc(id); success('EC supprimé'); loadEcs(ecForm.ue.id || selectedUe) } catch (err) { toastError(extractError(err)) }
  }

  const saveUe = async () => {
    try {
      await lmdApi.createUe({ ...ueForm, field: { id: Number(ueForm.field.id) } })
      success('UE enregistrée')
      setUeDialog(false)
      loadUes()
    } catch (err) { toastError(extractError(err)) }
  }

  const deleteUe = async (id) => {
    try { await lmdApi.deleteUe(id); success('UE supprimée'); loadUes() } catch (err) { toastError(extractError(err)) }
  }

  // --- Notes ---
  useEffect(() => {
    if (!gradeFieldId) { setEnrollments([]); return }
    lmdApi.enrollments({ fieldId: gradeFieldId }).then((r) => setEnrollments(r.data.data || [])).catch(() => {})
    studentApi.search({ page: 0, size: 500 }).then((r) => setStudents(r.data.data.content || [])).catch(() => {})
  }, [gradeFieldId])

  useEffect(() => {
    if (!gradeFieldId) { setUes([]); return }
    lmdApi.ues(gradeFieldId, gradeSemester).then((r) => setUes(r.data.data || [])).catch(() => {})
    setSelectedUe('')
    setGrades({})
  }, [gradeFieldId, gradeSemester])

  useEffect(() => {
    if (selectedUe) {
      lmdApi.ues(gradeFieldId, gradeSemester).then((r) => {
        const ue = (r.data.data || []).find((u) => u.id === Number(selectedUe))
        if (ue) setGrades({})
      }).catch(() => {})
    }
  }, [selectedUe])

  const gradeFor = (studentId) => grades[studentId] ?? ''

  const saveGrade = async (studentId) => {
    const value = grades[studentId]
    if (value === undefined || value === '') return
    try {
      await lmdApi.saveGrade({
        studentId, ueId: Number(selectedUe), semester: gradeSemester,
        value: Number(value),
      })
      success('Note enregistrée')
    } catch (err) { toastError(extractError(err)) }
  }

  // --- Notes EC (multi-évaluations) ---
  useEffect(() => {
    if (!ecGradeFieldId) { setEcGradeUeId(''); setEcGradeEcId(''); setEcGradeUes([]); return }
    lmdApi.ues(ecGradeFieldId, 'S1').then((r) => setEcGradeUes(r.data.data || [])).catch(() => {})
    lmdApi.enrollments({ fieldId: ecGradeFieldId }).then((r) => setEcGradesList(r.data.data || [])).catch(() => {})
  }, [ecGradeFieldId])

  useEffect(() => {
    if (!ecGradeUeId) { setEcGradeEcId(''); setEcGradeEcs([]); return }
    lmdApi.ecs(ecGradeUeId).then((r) => {
      setEcGradeEcs(r.data.data || [])
      if (r.data.data?.length) setEcGradeEcId(String(r.data.data[0].id)); else setEcGradeEcId('')
    }).catch(() => {})
  }, [ecGradeUeId])

  useEffect(() => {
    if (!ecGradeEcId) { setEcEvalValues({}); return }
    lmdApi.ecEvaluations(ecGradeEcId).then((r) => {
      const byStudent = {}
      ;(r.data.data || []).forEach((e) => {
        const key = `${e.student?.id}|${e.evaluationType}|${e.session}`
        byStudent[key] = { value: e.value, appreciation: e.appreciation }
      })
      setEcEvalValues(byStudent)
    }).catch(() => {})
  }, [ecGradeEcId])

  const saveEcGrade = async (studentId) => {
    const val = ecEvalValues[`${studentId}|${ecEvalType}|${ecGradeSession}`]
    if (!val) return
    const body = {
      ec: { id: Number(ecGradeEcId) },
      student: { id: studentId },
      evaluationType: ecEvalType,
      session: Number(ecGradeSession),
      value: Number(val.value),
      maxValue: 20,
      appreciation: (val.appreciation || '').trim(),
    }
    try {
      await lmdApi.createEcEvaluation(body)
      success('Note EC enregistrée')
      lmdApi.ecEvaluations(ecGradeEcId).then((r) => {
        const byStudent = {}
        ;(r.data.data || []).forEach((e) => {
          const key = `${e.student?.id}|${e.evaluationType}|${e.session}`
          byStudent[key] = { value: e.value, appreciation: e.appreciation }
        })
        setEcEvalValues(byStudent)
      }).catch(() => {})
    } catch (err) { toastError(extractError(err)) }
  }

  // --- Présences universitaires ---
  useEffect(() => {
    if (!attFieldId) { setAttUeId(''); setAttEcId(''); setAttUes([]); setAttStudents([]); return }
    lmdApi.ues(attFieldId, 'S1').then((r) => setAttUes(r.data.data || [])).catch(() => {})
    lmdApi.enrollments({ fieldId: attFieldId }).then((r) => setAttStudents(r.data.data || [])).catch(() => {})
  }, [attFieldId])

  useEffect(() => {
    if (!attUeId) { setAttEcId(''); setAttEcs([]); return }
    lmdApi.ecs(attUeId).then((r) => {
      setAttEcs(r.data.data || [])
      if (r.data.data?.length) setAttEcId(String(r.data.data[0].id)); else setAttEcId('')
    }).catch(() => {})
  }, [attUeId])

  useEffect(() => {
    if (!attEcId) { setAttList([]); return }
    lmdApi.universityAttendances({ ecId: attEcId, date: attDate, sessionType: attSessionType })
      .then((r) => {
        setAttList(r.data.data || [])
        const byStudent = {}
        ;(r.data.data || []).forEach((a) => { byStudent[`${a.student?.id}`] = a.status })
        setAttValues(byStudent)
      }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attEcId, attDate, attSessionType])

  const pointAttendance = async (studentId) => {
    const status = attValues[studentId] || 'PRESENT'
    try {
      await lmdApi.saveUniversityAttendance({
        ecId: Number(attEcId), studentId, date: attDate, sessionType: attSessionType,
        status, groupName: attGroup || undefined,
      })
      success('Présence enregistrée')
      lmdApi.universityAttendances({ ecId: attEcId, date: attDate, sessionType: attSessionType })
        .then((r) => {
          setAttList(r.data.data || [])
          const byStudent = {}
          ;(r.data.data || []).forEach((a) => { byStudent[`${a.student?.id}`] = a.status })
          setAttValues(byStudent)
        }).catch(() => {})
    } catch (err) { toastError(extractError(err)) }
  }

  // Historique universitaire d'un étudiant
  const loadAttHistory = async (studentId) => {
    if (!studentId) { setAttHist([]); return }
    try {
      const { data } = await lmdApi.studentUniversityAttendances(studentId)
      setAttHist(data.data || [])
    } catch { setAttHist([]) }
  }

  // --- Statut d'inscription / changement de niveau ---
  const updateStatus = async (id) => {
    if (!enrStatus) return
    try {
      await lmdApi.updateEnrollmentStatus(id, enrStatus, enrReason || undefined)
      success('Statut mis à jour')
      setEnrStatus(''); setEnrReason('')
      lmdApi.enrollments({ fieldId: gradeFieldId }).then((r) => setEnrollments(r.data.data || [])).catch(() => {})
    } catch (err) { toastError(extractError(err)) }
  }

  const doChangeLevel = async () => {
    if (!changeLevelId) return
    try {
      await lmdApi.changeLevel(changeLevelId, { level: changeLevelValue || undefined, semester: changeSemesterValue || undefined })
      success('Niveau modifié')
      setChangeLevelId(''); setChangeLevelValue(''); setChangeSemesterValue('')
      lmdApi.enrollments({ fieldId: gradeFieldId }).then((r) => setEnrollments(r.data.data || [])).catch(() => {})
    } catch (err) { toastError(extractError(err)) }
  }

  const openHistory = async (studentId) => {
    try {
      const { data } = await lmdApi.enrollmentHistory(studentId)
      setHistoryList(data.data || [])
      setHistoryStudent(studentId)
      setHistoryOpen(true)
    } catch (err) { toastError(extractError(err)) }
  }

  // --- Délibération ---
  const loadDeliberations = async () => {
    if (!delibFieldId) { setDeliberations([]); return }
    try {
      const { data } = await lmdApi.deliberations(delibFieldId, delibSemester)
      setDeliberations(data.data || [])
    } catch { setDeliberations([]) }
  }
  useEffect(() => { loadDeliberations() /* eslint-disable-line */ }, [delibFieldId, delibSemester])

  const runDeliberation = async () => {
    try {
      await lmdApi.deliberate(delibFieldId, delibSemester)
      success('Délibération calculée')
      loadDeliberations()
    } catch (err) { toastError(extractError(err)) }
  }

  const downloadDoc = async (type, d) => {
    try {
      const params = { studentId: d.studentId, fieldId: d.fieldId, semester: d.semester, session: 1 }
      const res = type === 'releve' ? await lmdApi.relevePdf(params) : await lmdApi.attestationPdf(params)
      downloadBlob(res.data, `${type}-${d.matricule}-${d.semester}.pdf`)
      success(`${type === 'releve' ? 'Relevé' : 'Attestation'} téléchargé`)
    } catch (err) { toastError(extractError(err)) }
  }

  return (
    <>
      <PageHeader title="Université (LMD)" subtitle="Structure, unités d'enseignement, notes et délibérations" />

      <Card sx={{ mb: 3, borderRadius: '16px' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto" sx={{ px: 2 }}>
          <Tab label="Structure" icon={<AccountTree />} iconPosition="start" />
          <Tab label="Programmes" icon={<MenuBook />} iconPosition="start" />
          <Tab label="Unités d'enseignement" icon={<EditNote />} iconPosition="start" />
          <Tab label="Notes UE" icon={<FactCheck />} iconPosition="start" />
          <Tab label="Notes EC" icon={<EditNote />} iconPosition="start" />
          <Tab label="Présences" icon={<FactCheck />} iconPosition="start" />
          <Tab label="Délibération" icon={<FactCheck />} iconPosition="start" />
          <Tab label="Règles" icon={<EditNote />} iconPosition="start" />
        </Tabs>
      </Card>

      {/* ---------- STRUCTURE ---------- */}
      {tab === 0 && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={4}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Facultés / UFR</Typography>
              <Grid container spacing={1.5} mb={2}>
                <Grid item xs={7}><TextField size="small" fullWidth label="Nom" value={facultyName} onChange={(e) => setFacultyName(e.target.value)} /></Grid>
                <Grid item xs={5}><TextField size="small" fullWidth label="Code" value={facultyCode} onChange={(e) => setFacultyCode(e.target.value)} /></Grid>
                <Grid item xs={12}><Button size="small" variant="contained" startIcon={<Add />} onClick={createFaculty}>Ajouter</Button></Grid>
              </Grid>
              <Box display="flex" flexDirection="column" gap={1}>
                {faculties.map((f) => (
                  <Box key={f.id} display="flex" alignItems="center" gap={1} sx={{ p: 1, borderRadius: '10px', border: '1px solid', borderColor: 'divider', cursor: 'pointer' }}
                    onClick={() => setFacultyId(String(f.id))} bgcolor={String(f.id) === facultyId ? 'action.selected' : 'transparent'}>
                    <Typography variant="body2" fontWeight={600} flex={1}>{f.name}</Typography>
                    <Chip size="small" label={f.code} />
                    <IconButton size="small" color="error" onClick={(e) => { e.stopPropagation(); deleteFaculty(f.id) }}><Delete fontSize="small" /></IconButton>
                  </Box>
                ))}
                {faculties.length === 0 && <Typography variant="body2" color="text.secondary">Aucune faculté.</Typography>}
              </Box>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Départements</Typography>
              <FormControl size="small" fullWidth sx={{ mb: 1.5 }}>
                <InputLabel>Faculté</InputLabel>
                <Select label="Faculté" value={facultyId} onChange={(e) => setFacultyId(e.target.value)}>
                  {faculties.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                </Select>
              </FormControl>
              <Grid container spacing={1.5} mb={2}>
                <Grid item xs={7}><TextField size="small" fullWidth label="Nom" value={deptName} onChange={(e) => setDeptName(e.target.value)} error={!deptName && deptSubmitted} helperText={!deptName && deptSubmitted ? 'Requis' : ' '} /></Grid>
                <Grid item xs={5}><TextField size="small" fullWidth label="Code" value={deptCode} onChange={(e) => setDeptCode(e.target.value)} error={!deptCode && deptSubmitted} helperText={!deptCode && deptSubmitted ? 'Requis' : ' '} /></Grid>
                <Grid item xs={12}><Button size="small" variant="contained" startIcon={<Add />} disabled={!facultyId || !deptName.trim() || !deptCode.trim()} onClick={createDepartment}>Ajouter</Button></Grid>
              </Grid>
              <Box display="flex" flexDirection="column" gap={1}>
                {departments.map((d) => (
                  <Box key={d.id} display="flex" alignItems="center" gap={1} sx={{ p: 1, borderRadius: '10px', border: '1px solid', borderColor: 'divider', cursor: 'pointer' }}
                    onClick={() => setDepartmentId(String(d.id))} bgcolor={String(d.id) === departmentId ? 'action.selected' : 'transparent'}>
                    <Typography variant="body2" fontWeight={600} flex={1}>{d.name}</Typography>
                    <Chip size="small" label={d.code} />
                    <IconButton size="small" color="error" onClick={(e) => { e.stopPropagation(); deleteDepartment(d.id) }}><Delete fontSize="small" /></IconButton>
                  </Box>
                ))}
                {departments.length === 0 && <Typography variant="body2" color="text.secondary">Aucun département.</Typography>}
              </Box>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Filières</Typography>
              <FormControl size="small" fullWidth sx={{ mb: 1.5 }}>
                <InputLabel>Département</InputLabel>
                <Select label="Département" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
                  {departments.map((d) => <MenuItem key={d.id} value={String(d.id)}>{d.name}</MenuItem>)}
                </Select>
              </FormControl>
              <Grid container spacing={1.5} mb={2}>
                <Grid item xs={12}><TextField size="small" fullWidth label="Nom" value={fieldName} onChange={(e) => setFieldName(e.target.value)} /></Grid>
                <Grid item xs={6}><TextField size="small" fullWidth label="Code" value={fieldCode} onChange={(e) => setFieldCode(e.target.value)} /></Grid>
                <Grid item xs={6}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Cycle</InputLabel>
                    <Select label="Cycle" value={fieldCycle} onChange={(e) => setFieldCycle(e.target.value)}>
                      {CYCLES.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Domaine (optionnel)</InputLabel>
                    <Select label="Domaine" value={fieldDomainId} onChange={(e) => setFieldDomainId(e.target.value)}>
                      <MenuItem value="">— Aucun —</MenuItem>
                      {domains.map((d) => <MenuItem key={d.id} value={String(d.id)}>{d.name}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12}><Button size="small" variant="contained" startIcon={<Add />} disabled={!departmentId} onClick={createField}>Ajouter</Button></Grid>
              </Grid>
              <Box display="flex" flexDirection="column" gap={1}>
                {fields.map((f) => (
                  <Box key={f.id} display="flex" alignItems="center" gap={1} sx={{ p: 1, borderRadius: '10px', border: '1px solid', borderColor: 'divider' }}>
                    <Typography variant="body2" fontWeight={600} flex={1}>{f.name}</Typography>
                    <Chip size="small" label={f.cycle} />
                    <IconButton size="small" color="error" onClick={() => deleteField(f.id)}><Delete fontSize="small" /></IconButton>
                  </Box>
                ))}
                {fields.length === 0 && <Typography variant="body2" color="text.secondary">Aucune filière.</Typography>}
              </Box>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Domaines académiques</Typography>
              <Grid container spacing={1.5} mb={2}>
                <Grid item xs={12}><TextField size="small" fullWidth label="Nom" value={domainName} onChange={(e) => setDomainName(e.target.value)} /></Grid>
                <Grid item xs={8}><TextField size="small" fullWidth label="Code" value={domainCode} onChange={(e) => setDomainCode(e.target.value)} /></Grid>
                <Grid item xs={4}><Button size="small" variant="contained" startIcon={<Add />} onClick={createDomain} disabled={!domainName || !domainCode}>Ajouter</Button></Grid>
              </Grid>
              <Box display="flex" flexDirection="column" gap={1}>
                {domains.map((d) => (
                  <Box key={d.id} display="flex" alignItems="center" gap={1} sx={{ p: 1, borderRadius: '10px', border: '1px solid', borderColor: 'divider' }}>
                    <Typography variant="body2" fontWeight={600} flex={1}>{d.name}</Typography>
                    <Chip size="small" label={d.code} />
                    <IconButton size="small" color="error" onClick={() => deleteDomain(d.id)}><Delete fontSize="small" /></IconButton>
                  </Box>
                ))}
                {domains.length === 0 && <Typography variant="body2" color="text.secondary">Aucun domaine.</Typography>}
              </Box>
            </Card>
          </Grid>

          <Grid item xs={12} md={4}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Groupes / promotions</Typography>
              <FormControl size="small" fullWidth sx={{ mb: 1.5 }}>
                <InputLabel>Filière</InputLabel>
                <Select label="Filière" value={groupFieldId} onChange={(e) => setGroupFieldId(e.target.value)}>
                  <MenuItem value="">Toutes</MenuItem>
                  {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                </Select>
              </FormControl>
              <Grid container spacing={1.5} mb={2}>
                <Grid item xs={12}><TextField size="small" fullWidth label="Nom" value={groupName} onChange={(e) => setGroupName(e.target.value)} /></Grid>
                <Grid item xs={6}><TextField size="small" fullWidth label="Code" value={groupCode} onChange={(e) => setGroupCode(e.target.value)} /></Grid>
                <Grid item xs={6}><TextField size="small" fullWidth label="Niveau (L1...)" value={groupLevel} onChange={(e) => setGroupLevel(e.target.value)} /></Grid>
                <Grid item xs={12}><Button size="small" variant="contained" startIcon={<Add />} onClick={createGroup} disabled={!groupName || !groupCode}>Ajouter</Button></Grid>
              </Grid>
              <Box display="flex" flexDirection="column" gap={1}>
                {groups.map((g) => (
                  <Box key={g.id} display="flex" alignItems="center" gap={1} sx={{ p: 1, borderRadius: '10px', border: '1px solid', borderColor: 'divider' }}>
                    <Typography variant="body2" fontWeight={600} flex={1}>{g.name}</Typography>
                    <Chip size="small" label={g.level || g.code} />
                    <IconButton size="small" color="error" onClick={() => deleteGroup(g.id)}><Delete fontSize="small" /></IconButton>
                  </Box>
                ))}
                {groups.length === 0 && <Typography variant="body2" color="text.secondary">Aucun groupe.</Typography>}
              </Box>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* ---------- PROGRAMMES ---------- */}
      {tab === 1 && (
        <Card sx={{ p: 2.5, borderRadius: '16px' }}>
          <Grid container spacing={2} alignItems="flex-end" mb={2}>
            <Grid item xs={12} md={4}>
              <FormControl size="small" fullWidth>
                <InputLabel>Filière</InputLabel>
                <Select label="Filière" value={programFieldId} onChange={(e) => setProgramFieldId(e.target.value)}>
                  <MenuItem value="">Toutes</MenuItem>
                  {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={4}>
              <Button variant="contained" startIcon={<Add />} onClick={() => { setProgramForm({ name: '', code: '', diploma: '', duration: 3, academicYear: '', field: { id: programFieldId } }); setProgramDialog(true) }}>
                Nouveau programme
              </Button>
            </Grid>
          </Grid>
          {programs.length === 0 ? (
            <Typography variant="body2" color="text.secondary">Aucun programme de formation.</Typography>
          ) : (
            <Box display="flex" flexDirection="column" gap={1}>
              {programs.map((p) => (
                <Box key={p.id} display="flex" alignItems="center" gap={1} sx={{ p: 1.5, borderRadius: '10px', border: '1px solid', borderColor: 'divider' }}>
                  <Box flex={1}>
                    <Typography variant="body2" fontWeight={700}>{p.name} <Chip size="small" label={p.diploma} sx={{ ml: 1 }} /></Typography>
                    <Typography variant="caption" color="text.secondary">{p.code} — {p.duration} an(s) {p.academicYear ? `— ${p.academicYear}` : ''}</Typography>
                  </Box>
                  <IconButton size="small" color="error" onClick={() => deleteProgram(p.id)}><Delete fontSize="small" /></IconButton>
                </Box>
              ))}
            </Box>
          )}
        </Card>
      )}

      {/* ---------- UE ---------- */}
      {tab === 2 && (
        <Card sx={{ p: 2.5, borderRadius: '16px' }}>
          <Grid container spacing={2} alignItems="flex-end" mb={2}>
            <Grid item xs={12} md={4}>
              <FormControl size="small" fullWidth>
                <InputLabel>Filière</InputLabel>
                <Select label="Filière" value={ueFieldId} onChange={(e) => setUeFieldId(e.target.value)}>
                  {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={3}>
              <FormControl size="small" fullWidth>
                <InputLabel>Semestre</InputLabel>
                <Select label="Semestre" value={ueSemester} onChange={(e) => setUeSemester(e.target.value)}>
                  {SEMESTERS.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={3}>
              <Button variant="contained" startIcon={<Add />} disabled={!ueFieldId}
                onClick={() => { setUeForm({ code: '', name: '', coefficient: 1, credits: 0, semester: ueSemester, field: { id: ueFieldId } }); setUeDialog(true) }}>
                Nouvelle UE
              </Button>
            </Grid>
          </Grid>

          <Box display="flex" flexDirection="column" gap={1}>
            {ues.map((u) => (
              <Box key={u.id} display="flex" alignItems="center" gap={1.5} sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                <Chip size="small" label={u.code} color="primary" />
                <Typography variant="body2" fontWeight={600} flex={1}>{u.name}</Typography>
                <Chip size="small" label={`Coef ${u.coefficient}`} variant="outlined" />
                <Chip size="small" label={`${u.credits} ECTS`} variant="outlined" />
                <IconButton size="small" color="error" onClick={() => deleteUe(u.id)}><Delete fontSize="small" /></IconButton>
              </Box>
            ))}
            {ues.length === 0 && <Typography variant="body2" color="text.secondary">Aucune UE pour ce semestre.</Typography>}
          </Box>
        </Card>
      )}

      {/* ---------- NOTES UE ---------- */}
      {tab === 3 && (
        <Card sx={{ p: 2.5, borderRadius: '16px' }}>
          <Grid container spacing={2} alignItems="flex-end" mb={2}>
            <Grid item xs={12} md={4}>
              <FormControl size="small" fullWidth>
                <InputLabel>Filière</InputLabel>
                <Select label="Filière" value={gradeFieldId} onChange={(e) => setGradeFieldId(e.target.value)}>
                  {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={2}>
              <FormControl size="small" fullWidth>
                <InputLabel>Semestre</InputLabel>
                <Select label="Semestre" value={gradeSemester} onChange={(e) => setGradeSemester(e.target.value)}>
                  {SEMESTERS.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={4}>
              <FormControl size="small" fullWidth>
                <InputLabel>UE</InputLabel>
                <Select label="UE" value={selectedUe} onChange={(e) => setSelectedUe(e.target.value)}>
                  <MenuItem value="">Sélectionner…</MenuItem>
                  {ues.map((u) => <MenuItem key={u.id} value={String(u.id)}>{u.code} — {u.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
          </Grid>

          {selectedUe ? (
            <Box display="flex" flexDirection="column" gap={1}>
              {enrollments.map((enr) => {
                const sid = enr.student?.id
                return (
                  <Box key={enr.id} display="flex" alignItems="center" gap={1.5} sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                    <Box flex={1}>
                      <Typography variant="body2" fontWeight={600}>{enr.student?.firstName} {enr.student?.lastName}</Typography>
                      <Typography variant="caption" color="text.secondary">{enr.student?.matricule} · {enr.level || ''} {enr.academicYear ? `· ${enr.academicYear}` : ''}</Typography>
                    </Box>
                    {enr.enrollmentStatus && <Chip size="small" color={enr.active ? 'success' : 'error'} label={enr.enrollmentStatus} />}
                    <TextField size="small" type="number" inputProps={{ min: 0, max: 20, step: 0.25 }} label="Note /20"
                      value={gradeFor(sid)} onChange={(e) => setGrades((g) => ({ ...g, [sid]: e.target.value }))} sx={{ width: 130 }} />
                    <Button size="small" variant="contained" startIcon={<Save />} disabled={gradeFor(sid) === ''} onClick={() => saveGrade(sid)}>
                      Enregistrer
                    </Button>
                  </Box>
                )
              })}
              {enrollments.length === 0 && <Typography variant="body2" color="text.secondary">Aucun étudiant inscrit. Inscrivez d'abord des élèves dans la filière.</Typography>}
            </Box>
          ) : (
            <Typography variant="body2" color="text.secondary">Sélectionnez une UE pour saisir les notes.</Typography>
          )}

          {enrollments.length > 0 && (
            <Box mt={3} pt={2} sx={{ borderTop: '1px solid', borderColor: 'divider' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Statut d'inscription & niveau</Typography>
              <Grid container spacing={2} alignItems="flex-end">
                <Grid item xs={12} md={3}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Étudiant</InputLabel>
                    <Select label="Étudiant" value={changeLevelId} onChange={(e) => setChangeLevelId(e.target.value)}>
                      <MenuItem value="">Sélectionner…</MenuItem>
                      {enrollments.map((enr) => (
                        <MenuItem key={enr.id} value={String(enr.id)}>{enr.student?.firstName} {enr.student?.lastName}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={6} md={2}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Statut</InputLabel>
                    <Select label="Statut" value={enrStatus} onChange={(e) => setEnrStatus(e.target.value)}>
                      <MenuItem value="">—</MenuItem>
                      <MenuItem value="INSCRIT">Inscrit</MenuItem>
                      <MenuItem value="ABANDON">Abandon</MenuItem>
                      <MenuItem value="DIPLOME">Diplômé</MenuItem>
                      <MenuItem value="EXCLU">Exclu</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={6} md={2}>
                  <TextField size="small" fullWidth label="Motif" value={enrReason} onChange={(e) => setEnrReason(e.target.value)} />
                </Grid>
                <Grid item xs={6} md={2}>
                  <TextField size="small" fullWidth label="Nouveau niveau" value={changeLevelValue} onChange={(e) => setChangeLevelValue(e.target.value)} placeholder="L2" />
                </Grid>
                <Grid item xs={6} md={2}>
                  <TextField size="small" fullWidth label="Semestre" value={changeSemesterValue} onChange={(e) => setChangeSemesterValue(e.target.value)} placeholder="S3" />
                </Grid>
                <Grid item xs={12} md={1}>
                  <Button size="small" variant="contained" disabled={!changeLevelId} onClick={() => updateStatus(changeLevelId)} sx={{ mb: 0.5 }}>Statut</Button>
                </Grid>
                <Grid item xs={12} md={1}>
                  <Button size="small" variant="outlined" disabled={!changeLevelId} onClick={doChangeLevel}>Niveau</Button>
                </Grid>
                <Grid item xs={12} md={1}>
                  <Button size="small" variant="outlined" color="info" disabled={!changeLevelId} onClick={() => openHistory(enrollments.find((e) => String(e.id) === String(changeLevelId))?.student?.id)}>Historique</Button>
                </Grid>
              </Grid>
            </Box>
          )}

          {students.length > 0 && (
            <Box mt={3} pt={2} sx={{ borderTop: '1px solid', borderColor: 'divider' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Inscrire un étudiant</Typography>
              <EnrollBar gradeFieldId={gradeFieldId} students={students} enrollments={enrollments}
                onDone={() => lmdApi.enrollments({ fieldId: gradeFieldId }).then((r) => setEnrollments(r.data.data || [])).catch(() => {})}
                success={success} error={toastError} />
            </Box>
          )}
        </Card>
      )}

      {/* ---------- NOTES EC (multi-évaluations) ---------- */}
      {tab === 4 && (
        <Card sx={{ p: 2.5, borderRadius: '16px' }}>
          <Grid container spacing={2} alignItems="flex-end" mb={2}>
            <Grid item xs={12} md={3}>
              <FormControl size="small" fullWidth>
                <InputLabel>Filière</InputLabel>
                <Select label="Filière" value={ecGradeFieldId} onChange={(e) => setEcGradeFieldId(e.target.value)}>
                  {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
<Grid item xs={12} md={4}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>UE</InputLabel>
                    <Select label="UE" value={ecGradeUeId} onChange={(e) => setEcGradeUeId(e.target.value)}>
                      <MenuItem value="">Sélectionner…</MenuItem>
                      {ecGradeUes.map((u) => (
                        <MenuItem key={u.id} value={String(u.id)}>{u.code} — {u.name}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={4}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>EC</InputLabel>
                    <Select label="EC" value={ecGradeEcId} onChange={(e) => setEcGradeEcId(e.target.value)}>
                      <MenuItem value="">Sélectionner…</MenuItem>
                      {ecGradeEcs.map((ec) => (
                        <MenuItem key={ec.id} value={String(ec.id)}>{ec.code} — {ec.name}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={1}>
                  <Button variant="outlined" size="small" onClick={() => {
                    if (ecGradeUeId) lmdApi.ecs(ecGradeUeId).then((r) => setEcGradeEcs(r.data.data || [])).catch(() => {})
                  }}>Actualiser</Button>
                </Grid>
                <Grid item xs={12} md={2}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Session</InputLabel>
                    <Select label="Session" value={ecGradeSession} onChange={(e) => setEcGradeSession(e.target.value)}>
                      <MenuItem value="1">Session 1 (normale)</MenuItem>
                      <MenuItem value="2">Session 2 (rattrapage)</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={2}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Type</InputLabel>
                    <Select label="Type" value={ecEvalType} onChange={(e) => setEcEvalType(e.target.value)}>
                      {EVAL_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
              </Grid>

          <Typography variant="body2" color="text.secondary" mb={1.5}>
            La moyenne d'un EC est la moyenne pondérée de ses évaluations (CC, TD, TP, projet, oral, examen).
          </Typography>

          {ecGradeEcId ? (
            <Box display="flex" flexDirection="column" gap={1}>
              {ecGradesList.map((enr) => {
                const sid = enr.student?.id
                const val = ecEvalValues[`${sid}|${ecEvalType}|${ecGradeSession}`] || {}
                return (
                  <Box key={enr.id} display="flex" alignItems="center" gap={1.5} sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                    <Box flex={1}>
                      <Typography variant="body2" fontWeight={600}>{enr.student?.firstName} {enr.student?.lastName}</Typography>
                      <Typography variant="caption" color="text.secondary">{enr.student?.matricule}</Typography>
                    </Box>
                    <TextField size="small" type="number" inputProps={{ min: 0, max: 20, step: 0.25 }} label={`Note ${ecEvalType} /20`}
                      value={val.value ?? ''} onChange={(e) => setEcEvalValues((g) => ({ ...g, [`${sid}|${ecEvalType}|${ecGradeSession}`]: { ...g[`${sid}|${ecEvalType}|${ecGradeSession}`], value: e.target.value } }))} sx={{ width: 130 }} />
                    <TextField size="small" label="Appréciation" value={val.appreciation || ''}
                      onChange={(e) => setEcEvalValues((g) => ({ ...g, [`${sid}|${ecEvalType}|${ecGradeSession}`]: { ...g[`${sid}|${ecEvalType}|${ecGradeSession}`], appreciation: e.target.value } }))} sx={{ width: 160 }} />
                    <Button size="small" variant="contained" startIcon={<Save />} onClick={() => saveEcGrade(sid)}>
                      Enregistrer
                    </Button>
                  </Box>
                )
              })}
              {ecGradesList.length === 0 && <Typography variant="body2" color="text.secondary">Aucun étudiant inscrit.</Typography>}
            </Box>
          ) : (
            <Typography variant="body2" color="text.secondary">Sélectionnez une UE puis un EC pour saisir les notes.</Typography>
          )}
        </Card>
      )}

      {/* ---------- PRÉSENCES UNIVERSITAIRES ---------- */}
      {tab === 5 && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={7}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Pointer les présences</Typography>
              <Grid container spacing={2} alignItems="flex-end" mb={2}>
                <Grid item xs={12} md={3}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Filière</InputLabel>
                    <Select label="Filière" value={attFieldId} onChange={(e) => setAttFieldId(e.target.value)}>
                      {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={3}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>UE</InputLabel>
                    <Select label="UE" value={attUeId} onChange={(e) => setAttUeId(e.target.value)}>
                      <MenuItem value="">Sélectionner…</MenuItem>
                      {attUes.map((u) => (
                        <MenuItem key={u.id} value={String(u.id)}>{u.code} — {u.name}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={3}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>EC</InputLabel>
                    <Select label="EC" value={attEcId} onChange={(e) => setAttEcId(e.target.value)}>
                      <MenuItem value="">Sélectionner…</MenuItem>
                      {attEcs.map((ec) => <MenuItem key={ec.id} value={String(ec.id)}>{ec.code} — {ec.name}</MenuItem>)}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={6} md={3}>
                  <TextField size="small" type="date" fullWidth label="Date" value={attDate} onChange={(e) => setAttDate(e.target.value)} />
                </Grid>
                <Grid item xs={6} md={2}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Séance</InputLabel>
                    <Select label="Séance" value={attSessionType} onChange={(e) => setAttSessionType(e.target.value)}>
                      <MenuItem value="CM">CM</MenuItem>
                      <MenuItem value="TD">TD</MenuItem>
                      <MenuItem value="TP">TP</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={2}>
                  <TextField size="small" fullWidth label="Groupe" value={attGroup} onChange={(e) => setAttGroup(e.target.value)} placeholder="Optionnel" />
                </Grid>
              </Grid>
              <Box display="flex" flexDirection="column" gap={1}>
                {attStudents.map((enr) => {
                  const sid = enr.student?.id
                  return (
                    <Box key={enr.id} display="flex" alignItems="center" gap={1.5} sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                      <Box flex={1}>
                        <Typography variant="body2" fontWeight={600}>{enr.student?.firstName} {enr.student?.lastName}</Typography>
                        <Typography variant="caption" color="text.secondary">{enr.student?.matricule} · {enr.currentSemester || '—'}</Typography>
                      </Box>
                      <FormControl size="small" sx={{ width: 130 }}>
                        <InputLabel>Statut</InputLabel>
                        <Select label="Statut" value={attValues[sid] || 'PRESENT'} onChange={(e) => setAttValues((v) => ({ ...v, [sid]: e.target.value }))}>
                          <MenuItem value="PRESENT">Présent</MenuItem>
                          <MenuItem value="ABSENT">Absent</MenuItem>
                          <MenuItem value="LATE">Retard</MenuItem>
                          <MenuItem value="EXCUSED">Excusé</MenuItem>
                        </Select>
                      </FormControl>
                      <Button size="small" variant="contained" startIcon={<Save />} onClick={() => pointAttendance(sid)}>Pointer</Button>
                    </Box>
                  )
                })}
                {attStudents.length === 0 && <Typography variant="body2" color="text.secondary">Sélectionnez une filière avec des étudiants inscrits.</Typography>}
              </Box>
            </Card>
          </Grid>

          <Grid item xs={12} md={5}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Historique d'un étudiant</Typography>
              <Grid container spacing={1.5} mb={2}>
                <Grid item xs={8}>
                  <FormControl size="small" fullWidth>
                    <InputLabel>Étudiant</InputLabel>
                    <Select label="Étudiant" value={attStudentId} onChange={(e) => { setAttStudentId(e.target.value); loadAttHistory(e.target.value) }}>
                      <MenuItem value="">Sélectionner…</MenuItem>
                      {attStudents.map((enr) => (
                        <MenuItem key={enr.id} value={String(enr.student?.id)}>{enr.student?.firstName} {enr.student?.lastName}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={4}>
                  <Button size="small" variant="outlined" fullWidth onClick={() => loadAttHistory(attStudentId)}>Afficher</Button>
                </Grid>
              </Grid>
              <Box display="flex" flexDirection="column" gap={1}>
                {attHist.map((a) => (
                  <Box key={a.id} display="flex" alignItems="center" gap={1} sx={{ p: 1, borderRadius: '10px', border: '1px solid', borderColor: 'divider' }}>
                    <Box flex={1}>
                      <Typography variant="body2" fontWeight={600}>{a.ec?.code} — {a.ec?.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{a.date} · {a.sessionType}{a.groupName ? ` · ${a.groupName}` : ''}</Typography>
                    </Box>
                    <StatusChip status={a.status} />
                  </Box>
                ))}
                {attHist.length === 0 && <Typography variant="body2" color="text.secondary">Aucun historique.</Typography>}
              </Box>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* ---------- DÉLIBÉRATION ---------- */}
      {tab === 6 && (
        <Card sx={{ p: 2.5, borderRadius: '16px' }}>
          <Grid container spacing={2} alignItems="flex-end" mb={2}>
            <Grid item xs={12} md={4}>
              <FormControl size="small" fullWidth>
                <InputLabel>Filière</InputLabel>
                <Select label="Filière" value={delibFieldId} onChange={(e) => setDelibFieldId(e.target.value)}>
                  {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={2}>
              <FormControl size="small" fullWidth>
                <InputLabel>Semestre</InputLabel>
                <Select label="Semestre" value={delibSemester} onChange={(e) => setDelibSemester(e.target.value)}>
                  {SEMESTERS.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={3}>
              <Button variant="contained" disabled={!delibFieldId} onClick={runDeliberation}>Calculer la délibération</Button>
            </Grid>
          </Grid>

          <Typography variant="body2" color="text.secondary" mb={2}>
            Règle appliquée : moyenne ≥ 10 <b>et</b> aucune UE &lt; 8 → ADMIS ; sinon moyenne ≥ 8 → AJOURNÉ (session 2) ; sinon REDOUBLE.
          </Typography>

          <Box display="flex" flexDirection="column" gap={1}>
            {deliberations.map((d) => (
              <Box key={`${d.studentId}-${d.semester}`} display="flex" alignItems="center" gap={1.5} sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                <Box flex={1}>
                  <Typography variant="body2" fontWeight={600}>{d.studentName}</Typography>
                  <Typography variant="caption" color="text.secondary">{d.matricule} · Rang {d.rankInClass ?? '—'} · {d.creditsObtained ?? 0}/{d.totalCredits ?? '—'} ECTS</Typography>
                </Box>
                <Typography variant="body2" fontWeight={700}>Moy. {d.average ?? '—'}</Typography>
                <StatusChip status={d.decision} />
                {d.uesToRetake?.length > 0 && (
                  <Chip size="small" color="warning" label={`À repasser : ${d.uesToRetake.join(', ')}`} />
                )}
                <Button size="small" variant="outlined" startIcon={<PictureAsPdf />}
                  onClick={() => downloadDoc('releve', d)} sx={{ fontSize: 11 }}>Relevé</Button>
                <Button size="small" variant="outlined" startIcon={<PictureAsPdf />}
                  onClick={() => downloadDoc('attestation', d)} sx={{ fontSize: 11 }}>Attest.</Button>
              </Box>
            ))}
            {deliberations.length === 0 && <Typography variant="body2" color="text.secondary">Aucune délibération pour cette filière et ce semestre.</Typography>}
          </Box>
        </Card>
      )}

      {/* ---------- RÈGLES ACADÉMIQUES ---------- */}
      {tab === 7 && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={5}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Nouvelle règle (UNIVERSITE)</Typography>
              <Grid container spacing={1.5}>
                <Grid item xs={12}><TextField size="small" fullWidth label="Clé" value={ruleKey} onChange={(e) => setRuleKey(e.target.value)} placeholder="ex. validation_threshold" /></Grid>
                <Grid item xs={12}><TextField size="small" fullWidth label="Valeur" value={ruleValue} onChange={(e) => setRuleValue(e.target.value)} placeholder="ex. 10" /></Grid>
                <Grid item xs={12}><TextField size="small" fullWidth label="Description" value={ruleDescription} onChange={(e) => setRuleDescription(e.target.value)} /></Grid>
                <Grid item xs={12}><Button size="small" variant="contained" startIcon={<Add />} onClick={createRule} disabled={!ruleKey || !ruleValue}>Ajouter la règle</Button></Grid>
              </Grid>
            </Card>
          </Grid>
          <Grid item xs={12} md={7}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Règles configurables</Typography>
              <Box display="flex" flexDirection="column" gap={1}>
                {rules.map((r) => (
                  <Box key={r.id} display="flex" alignItems="center" gap={1} sx={{ p: 1, borderRadius: '10px', border: '1px solid', borderColor: 'divider' }}>
                    <Chip size="small" label={r.ruleKey} />
                    <Typography variant="body2" fontWeight={600} flex={1}>{r.ruleValue}</Typography>
                    <Typography variant="caption" color="text.secondary">{r.description}</Typography>
                    <IconButton size="small" color="error" onClick={() => deleteRule(r.id)}><Delete fontSize="small" /></IconButton>
                  </Box>
                ))}
                {rules.length === 0 && <Typography variant="body2" color="text.secondary">Aucune règle configurée.</Typography>}
              </Box>
            </Card>
          </Grid>
</Grid>
      )}

      {/* Dialogue Historique */}
      <Dialog open={historyOpen} onClose={() => setHistoryOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Historique des inscriptions</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={1} mt={1}>
            {historyList.map((h) => (
              <Box key={h.id} display="flex" alignItems="center" gap={1.5} sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                <Box flex={1}>
                  <Typography variant="body2" fontWeight={600}>{h.field?.name || '—'}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {h.fromLevel ? `${h.fromLevel} → ${h.toLevel || '—'}` : ''}
                    {h.fromSemester ? ` · ${h.fromSemester} → ${h.toSemester || '—'}` : ''}
                    {h.academicYear ? ` · ${h.academicYear}` : ''}
                  </Typography>
                </Box>
                <Chip size="small" label={h.enrollmentStatus || '—'} />
                <Typography variant="caption" color="text.secondary">{h.createdAt?.slice(0, 10) || '—'}</Typography>
              </Box>
            ))}
            {historyList.length === 0 && <Typography variant="body2" color="text.secondary">Aucun historique.</Typography>}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setHistoryOpen(false)}>Fermer</Button>
        </DialogActions>
      </Dialog>

      {/* Dialogue UE */}
      <Dialog open={ueDialog} onClose={() => setUeDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Nouvelle unité d'enseignement</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12} sm={6}><TextField fullWidth label="Code" value={ueForm.code} onChange={(e) => setUeForm({ ...ueForm, code: e.target.value })} /></Grid>
            <Grid item xs={12} sm={6}><TextField fullWidth label="Semestre" select value={ueForm.semester} onChange={(e) => setUeForm({ ...ueForm, semester: e.target.value })}>
              {SEMESTERS.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </TextField></Grid>
            <Grid item xs={12}><TextField fullWidth label="Nom de l'UE" value={ueForm.name} onChange={(e) => setUeForm({ ...ueForm, name: e.target.value })} /></Grid>
            <Grid item xs={6}><TextField fullWidth type="number" label="Coefficient" value={ueForm.coefficient} onChange={(e) => setUeForm({ ...ueForm, coefficient: Number(e.target.value) })} /></Grid>
            <Grid item xs={6}><TextField fullWidth type="number" label="Crédits ECTS" value={ueForm.credits} onChange={(e) => setUeForm({ ...ueForm, credits: Number(e.target.value) })} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUeDialog(false)}>Annuler</Button>
          <Button variant="contained" onClick={saveUe} disabled={!ueForm.code || !ueForm.name}>Enregistrer</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

function EnrollBar({ gradeFieldId, students, enrollments, onDone, success, error }) {
  const [studentId, setStudentId] = useState('')
  const [semester, setSemester] = useState('S1')
  const enrolled = new Set(enrollments.map((e) => e.student?.id))
  const available = students.filter((s) => !enrolled.has(s.id))

  const enroll = async () => {
    if (!studentId) return
    try {
      const res = await lmdApi.enroll({ studentId: Number(studentId), fieldId: Number(gradeFieldId), currentSemester: semester })
      success(`Étudiant inscrit (${res.data.data.student?.matricule})`)
      setStudentId('')
      onDone()
    } catch (err) { error(extractError(err)) }
  }

  return (
    <Grid container spacing={2} alignItems="flex-end">
      <Grid item xs={12} md={5}>
        <FormControl size="small" fullWidth>
          <InputLabel>Étudiant</InputLabel>
          <Select label="Étudiant" value={studentId} onChange={(e) => setStudentId(e.target.value)}>
            {available.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName} ({s.matricule})</MenuItem>)}
          </Select>
        </FormControl>
      </Grid>
      <Grid item xs={6} md={4}>
        <FormControl size="small" fullWidth>
          <InputLabel>Semestre</InputLabel>
          <Select label="Semestre" value={semester} onChange={(e) => setSemester(e.target.value)}>
            {SEMESTERS.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
          </Select>
        </FormControl>
      </Grid>
      <Grid item xs={6} md={3}>
        <Button variant="outlined" startIcon={<Add />} disabled={!studentId} onClick={enroll}>Inscrire</Button>
      </Grid>
    </Grid>
  )
}
