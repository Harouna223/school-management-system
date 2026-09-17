import { useState, useEffect, createContext, useContext } from 'react'
import {
  Drawer, Box, List, ListItemButton, ListItemIcon, ListItemText,
  Divider, Typography, Collapse, Tooltip, Avatar,
} from '@mui/material'
import {
  Dashboard as DashboardIcon,
  People as StudentsIcon,
  School as TeachersIcon,
  AccountBalance as UniversityIcon,
  Class as ClassesIcon,
  MenuBook as SubjectsIcon,
  CalendarMonth as ScheduleIcon,
  FactCheck as AttendanceIcon,
  Assignment as GradesIcon,
  Quiz as ExamsIcon,
  Payments as PaymentsIcon,
  AccountBalanceWallet as FinanceIcon,
  LocalLibrary as LibraryIcon,
  Group as HrIcon,
  Notifications as MessagesIcon,
  Campaign as AnnouncementsIcon,
  FamilyRestroom as ChildrenIcon,
  Logout as LogoutIcon,
  ExpandLess,
  ExpandMore,
  ManageAccounts,
  Shield,
  Settings as SettingsIcon,
  ChevronLeft,
  ChevronRight,
  AccountTree as LmdIcon,
  Assessment as ReportsIcon,
  Assignment as AssignmentIcon,
  EditNote as EditNoteIcon,
  Event as ConvocationIcon,
  Layers as LevelsIcon,
  Splitscreen as SectionsIcon,
  MeetingRoom as RoomsIcon,
  Receipt as FeeTypesIcon,
  Paid as TeacherPayrollIcon,
} from '@mui/icons-material'
import { useNavigate, useLocation, NavLink } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { logout } from '../redux/slices/authSlice'
import { settingsApi } from '../api/endpoints'
import { primaryRole, hasPermission } from '../utils/auth'
import { useI18n } from '../i18n/I18nContext'
import { initials, humanize } from '../utils/format'
import logo from '../assets/logo.svg'

const SidebarContext = createContext(null)

export const useSidebar = () => useContext(SidebarContext)

/* ============================================================
   Navigation : groupes hiérarchisés, filtrage par rôles.
   ============================================================ */
const NAV_GROUPS = [
  {
    label: 'nav.group.main',
    items: [
      // Tableau de bord réservé à l'administration (direction + staff).
      // Les espaces élève / parent / enseignant n'exposent pas de tableau de bord.
      { key: 'dashboard', label: 'nav.dashboard', icon: <DashboardIcon />, to: '/dashboard', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE', 'SECRETAIRE'] },
    ],
  },
  {
    label: 'nav.group.admin',
    items: [
      { key: 'students', label: 'nav.students', icon: <StudentsIcon />, to: '/students', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'COMPTABLE'], requires: 'school' },
      { key: 'teachers', label: 'nav.teachers', icon: <TeachersIcon />, to: '/teachers', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
      { key: 'classes', label: 'nav.classes', icon: <ClassesIcon />, to: '/classes', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
      { key: 'levels', label: 'Niveaux', icon: <LevelsIcon />, to: '/levels', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
      { key: 'sections', label: 'Sections', icon: <SectionsIcon />, to: '/sections', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
      { key: 'rooms', label: 'Salles', icon: <RoomsIcon />, to: '/rooms', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
    ],
  },
  {
    label: 'nav.group.pedagogy',
    items: [
      { key: 'subjects', label: 'nav.subjects', icon: <SubjectsIcon />, to: '/subjects', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
      { key: 'schedule', label: 'nav.schedule', icon: <ScheduleIcon />, to: '/schedules', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT'], requires: 'school' },
      { key: 'attendances', label: 'nav.attendances', icon: <AttendanceIcon />, to: '/attendances', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT'], requires: 'school' },
      { key: 'grades', label: 'nav.grades', icon: <GradesIcon />, to: '/grades', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT'], requires: 'school' },
      { key: 'exams', label: 'nav.exams', icon: <ExamsIcon />, to: '/exams', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
      { key: 'exam-types', label: 'Types d\'évaluation', icon: <EditNoteIcon />, to: '/exam-types', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'school' },
      { key: 'convocations', label: 'Convocations', icon: <ConvocationIcon />, to: '/convocations', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'] },
      { key: 'university-exams', label: 'Examens universitaires', icon: <ExamsIcon />, to: '/university-exams', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT'], requires: 'university' },
      { key: 'stages', label: 'Stages', icon: <AssignmentIcon />, to: '/stages', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT'], requires: 'university' },
      { key: 'candidatures', label: 'Admission', icon: <AssignmentIcon />, to: '/candidatures', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'university' },
      { key: 'memoires', label: 'Mémoires', icon: <EditNoteIcon />, to: '/memoires', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT'], requires: 'university' },
      { key: 'alumni', label: 'Alumni', icon: <EditNoteIcon />, to: '/alumni', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'], requires: 'university' },
      { key: 'reports', label: 'nav.reports', icon: <ReportsIcon />, to: '/reports', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'COMPTABLE', 'ENSEIGNANT'], requires: 'school' },
    ],
  },
  {
    label: 'nav.group.finance',
    items: [
      { key: 'payments', label: 'nav.payments', icon: <PaymentsIcon />, to: '/payments', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE', 'SECRETAIRE'] },
      { key: 'expenses', label: 'nav.expenses', icon: <FinanceIcon />, to: '/expenses', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE'] },
      { key: 'teacher-payroll', label: 'nav.teacherPayroll', icon: <TeacherPayrollIcon />, to: '/teacher-payroll', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE', 'SECRETAIRE'] },
      { key: 'fee-types', label: 'Types de frais', icon: <FeeTypesIcon />, to: '/fee-types', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE', 'SECRETAIRE'] },
    ],
  },
  {
    label: 'nav.group.services',
    items: [
      { key: 'library', label: 'nav.library', icon: <LibraryIcon />, to: '/library', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE'] },
      { key: 'hr', label: 'nav.hr', icon: <HrIcon />, to: '/hr', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE'] },
      { key: 'lmd', label: 'nav.lmd', icon: <LmdIcon />, to: '/lmd', roles: ['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT'], requires: 'university' },
      { key: 'messages', label: 'nav.messages', icon: <MessagesIcon />, to: '/messages', roles: 'all' },
      { key: 'announcements', label: 'nav.announcements', icon: <AnnouncementsIcon />, to: '/announcements', roles: 'all' },
    ],
  },
  {
    label: 'nav.group.spaces',
    items: [
      { key: 'children', label: 'nav.children', icon: <ChildrenIcon />, to: '/my-children', roles: ['PARENT'], requires: 'school' },
      { key: 'my-school', label: 'nav.my-school', icon: <StudentsIcon />, to: '/my-school', roles: ['ELEVE'], requires: 'school' },
      { key: 'my-university', label: 'Espace université', icon: <UniversityIcon />, to: '/my-university', roles: ['ETUDIANT', 'ELEVE'], requires: 'university' },
      { key: 'my-teaching', label: 'nav.my-teaching', icon: <TeachersIcon />, to: '/my-teaching', roles: ['ENSEIGNANT'], requires: 'school' },
    ],
  },
]

const ROLE_LABELS = {
  SUPER_ADMIN: 'role.SUPER_ADMIN',
  DIRECTEUR: 'role.DIRECTEUR',
  COMPTABLE: 'role.COMPTABLE',
  SECRETAIRE: 'role.SECRETAIRE',
  ENSEIGNANT: 'role.ENSEIGNANT',
  PARENT: 'role.PARENT',
  ELEVE: 'role.ELEVE',
}

/**
 * Barre latérale moderne : logo, groupes hiérarchisés, mode réduit
 * avec infobulles et filtrage par permissions.
 *
 * Deux Drawers sont rendus (pattern responsive MUI) :
 *  - permanent : visible sur grand écran (≥ lg), rétractable
 *  - temporary : tiroir mobile ouvert par le bouton hamburger (< lg)
 */
export default function Sidebar({ open, onClose, collapsed, onCollapseToggle, variant = 'permanent', width = 280 }) {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch()
  const user = useSelector((state) => state.auth.user)
  const { t } = useI18n()
  const [openGroups, setOpenGroups] = useState({})

  const role = primaryRole()
  const can = (perm) => hasPermission(perm)

  // Cycles d'enseignement activés (Jardin, Primaire, Collège, Lycée, Université)
  const [activeCycles, setActiveCycles] = useState(['JARDIN', 'PRIMAIRE', 'COLLEGE', 'LYCEE', 'UNIVERSITE'])
  useEffect(() => {
    let mounted = true
    settingsApi.cycles().then((r) => {
      if (mounted && r.data?.data?.length) setActiveCycles(r.data.data)
    }).catch(() => {})
    return () => { mounted = false }
  }, [])

  const schoolActive = activeCycles.some((c) => ['JARDIN', 'PRIMAIRE', 'COLLEGE', 'LYCEE'].includes(c))
  const universityActive = activeCycles.includes('UNIVERSITE')

  const allowed = (item) => {
    const byRole = item.roles === 'all' || (item.roles?.includes(role) ?? false) || role === 'SUPER_ADMIN'
    if (!byRole) return false
    if (item.requires === 'university') return universityActive
    if (item.requires === 'school') return schoolActive
    return true
  }

  const navGroups = [...NAV_GROUPS]
  if (can('USER_READ') || role === 'SUPER_ADMIN') {
    navGroups.push({
      label: 'nav.group.system',
      items: [
        { key: 'users', label: 'nav.users', icon: <ManageAccounts />, to: '/users', roles: ['SUPER_ADMIN', 'DIRECTEUR'] },
        { key: 'audit', label: 'nav.audit', icon: <Shield />, to: '/audit', roles: ['SUPER_ADMIN'] },
        { key: 'settings', label: 'nav.settings', icon: <SettingsIcon />, to: '/settings', roles: ['SUPER_ADMIN'] },
      ],
    })
  }

  const filteredGroups = navGroups
    .map((g) => ({ ...g, items: g.items.filter(allowed) }))
    .filter((g) => g.items.length > 0)

  const toggleGroup = (key) =>
    setOpenGroups((prev) => ({ ...prev, [key]: !prev[key] }))

  const isGroupActive = (items) => items.some((c) => location.pathname.startsWith(c.to))

  const handleLogout = async () => {
    await dispatch(logout())
    navigate('/login')
  }

  const renderItem = (item, indent = 0, compact = false, isTemp = false) => {
    const isActive = item.to ? location.pathname.startsWith(item.to) : false
    const button = item.to ? (
      <ListItemButton
        component={NavLink}
        to={item.to}
        selected={isActive}
        onClick={() => isTemp && onClose()}
        sx={{ pl: 2 + indent, minHeight: 42, mb: 0.4, borderRadius: '10px', mx: 0.4 }}
      >
        <ListItemIcon sx={{ minWidth: 38, color: isActive ? 'primary.main' : 'text.secondary' }}>
          {item.icon}
        </ListItemIcon>
        {!compact && (
          <ListItemText
            primary={t(item.label)}
            primaryTypographyProps={{ fontSize: 13.5, fontWeight: isActive ? 650 : 500 }}
          />
        )}
      </ListItemButton>
    ) : (
      <ListItemButton onClick={item.onClick} sx={{ minHeight: 42, mb: 0.4, borderRadius: '10px', mx: 0.4 }}>
        <ListItemIcon sx={{ minWidth: 38, color: 'error.main' }}>
          {item.icon}
        </ListItemIcon>
        {!compact && (
          <ListItemText primary={t(item.label)} primaryTypographyProps={{ fontSize: 13.5, fontWeight: 500 }} />
        )}
      </ListItemButton>
    )
    return compact ? (
      <Tooltip title={t(item.label)} placement="right" key={item.key}>
        <Box key={item.key} className="nav-item">{button}</Box>
      </Tooltip>
    ) : (
      <Box key={item.key} className="nav-item">{button}</Box>
    )
  }

  const renderContent = (compact, isTemp = false) => (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', bgcolor: 'background.paper' }}>
      {/* Logo */}
      <Box
        px={compact ? 1.2 : 2.4}
        py={2.2}
        display="flex"
        alignItems="center"
        gap={1.6}
        justifyContent={compact ? 'center' : 'flex-start'}
      >
        <Box
          sx={{
            width: 44,
            height: 44,
            borderRadius: '14px',
            background: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 6px 16px rgba(37, 99, 235, 0.35)',
            flexShrink: 0,
            overflow: 'hidden',
          }}
        >
          <img src={logo} alt="Logo SMS" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        </Box>
        {!compact && (
          <Box minWidth={0}>
            <Typography variant="subtitle1" fontWeight={800} lineHeight={1.15} noWrap sx={{ fontSize: 17, letterSpacing: '-0.01em' }}>
              SMS
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap sx={{ fontSize: 10.5, textTransform: 'uppercase', letterSpacing: '0.08em' }}>
              School Management
            </Typography>
          </Box>
        )}
      </Box>

      <Divider sx={{ mx: 2.2, borderStyle: 'dashed' }} />

      {/* Navigation */}
      <List component="nav" sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', px: 1.1, py: 1.4 }}>
        {filteredGroups.map((group, gi) => {
          const groupActive = isGroupActive(group.items)
          const open = openGroups[group.label] ?? groupActive
          const isSingle = group.items.length === 1
          const onlyItem = group.items[0]

          if (isSingle) return renderItem(onlyItem, 0, compact, isTemp)

          return (
            <div key={group.label}>
              {!compact && (
                <Box display="flex" alignItems="center" gap={1} px={1.6} pt={gi === 0 ? 0.8 : 2} pb={0.7}>
                  <Box sx={{ width: 4, height: 4, borderRadius: '50%', bgcolor: 'primary.main', flexShrink: 0 }} />
                  <Typography
                    sx={{
                      fontSize: 10.5, fontWeight: 800, textTransform: 'uppercase',
                      letterSpacing: '0.09em', color: 'text.disabled',
                    }}
                  >
                    {t(group.label)}
                  </Typography>
                </Box>
              )}
              {compact ? (
                <Tooltip title={t(group.label)} placement="right">
                  <ListItemButton
                    onClick={() => onCollapseToggle(false)}
                    sx={{ minHeight: 42, justifyContent: 'center', mb: 0.4, borderRadius: '10px', mx: 0.4 }}
                  >
                    <ListItemIcon sx={{ minWidth: 38, color: groupActive ? 'primary.main' : 'text.secondary' }}>
                      {group.items[0].icon}
                    </ListItemIcon>
                  </ListItemButton>
                </Tooltip>
              ) : (
                <ListItemButton
                  onClick={() => toggleGroup(group.label)}
                  sx={{ minHeight: 42, mb: 0.4, borderRadius: '10px', mx: 0.4 }}
                  selected={groupActive}
                >
                  <ListItemIcon sx={{ minWidth: 38, color: groupActive ? 'primary.main' : 'text.secondary' }}>
                    {group.items[0].icon}
                  </ListItemIcon>
                  <ListItemText
                    primary={t(group.label)}
                    primaryTypographyProps={{ fontSize: 13.5, fontWeight: groupActive ? 700 : 600 }}
                  />
                  {open ? <ExpandLess sx={{ fontSize: 19 }} /> : <ExpandMore sx={{ fontSize: 19 }} />}
                </ListItemButton>
              )}
              {!compact && (
                <Collapse in={open} timeout="auto" unmountOnExit>
                  <List component="div" disablePadding>
                    {group.items.map((item) => renderItem(item, 1.8, compact, isTemp))}
                  </List>
                </Collapse>
              )}
            </div>
          )
        })}
      </List>

      <Divider sx={{ mx: 2.2, borderStyle: 'dashed' }} />

      {/* Pied de sidebar : utilisateur + actions */}
      <Box px={compact ? 1.2 : 2.2} py={1.6}>
        {!compact && (
          <Box
            display="flex"
            alignItems="center"
            gap={1.2}
            mb={1.2}
            p={1.2}
            borderRadius="12px"
            border="1px solid"
            borderColor="divider"
            sx={{
              background: (t) => (t.palette.mode === 'dark' ? 'rgba(148,163,184,0.06)' : 'rgba(241,245,249,0.7)'),
            }}
          >
            <Avatar
              src={user?.avatar}
              sx={{ width: 34, height: 34, bgcolor: 'primary.main', fontSize: 12.5 }}
            >
              {initials(user?.firstName, user?.lastName)}
            </Avatar>
            <Box minWidth={0}>
              <Typography variant="body2" fontWeight={700} noWrap sx={{ fontSize: 12.5, lineHeight: 1.3 }}>
                {user?.firstName} {user?.lastName}
              </Typography>
              <Typography variant="caption" color="text.secondary" noWrap sx={{ fontSize: 10.5 }}>
                {ROLE_LABELS[role] ? t(ROLE_LABELS[role]) : humanize(role)}
              </Typography>
            </Box>
          </Box>
        )}
        {renderItem({ key: 'logout', label: 'nav.logout', icon: <LogoutIcon />, onClick: handleLogout }, 0, compact, isTemp)}
        {!compact ? (
          <ListItemButton onClick={() => onCollapseToggle(true)} sx={{ minHeight: 42, mb: 0.4, borderRadius: '10px', mx: 0.4 }}>
            <ListItemIcon sx={{ minWidth: 38, color: 'text.secondary' }}>
              <ChevronLeft />
            </ListItemIcon>
            <ListItemText primary={t('topbar.collapse')} primaryTypographyProps={{ fontSize: 13.5, fontWeight: 500 }} />
          </ListItemButton>
        ) : (
          <Tooltip title={t('topbar.expand')} placement="right">
            <ListItemButton onClick={() => onCollapseToggle(false)} sx={{ minHeight: 42, justifyContent: 'center', mb: 0.4, borderRadius: '10px', mx: 0.4 }}>
              <ListItemIcon sx={{ minWidth: 38, color: 'text.secondary', justifyContent: 'center' }}>
                <ChevronRight />
              </ListItemIcon>
            </ListItemButton>
          </Tooltip>
        )}
        {!compact && (
          <Typography variant="caption" color="text.disabled" align="center" display="block" sx={{ fontSize: 10, mt: 0.8, letterSpacing: '0.04em' }}>
            SMS v1.0 — School Management
          </Typography>
        )}
      </Box>
    </Box>
  )

  return (
    <SidebarContext.Provider value={{ collapsed, onCollapseToggle }}>
      {variant !== 'temporary' && (
        <Drawer
          variant="permanent"
          open
          sx={{
            width: collapsed ? 84 : width,
            flexShrink: 0,
            transition: 'width 220ms ease',
            '& .MuiDrawer-paper': {
              width: collapsed ? 84 : width,
              boxSizing: 'border-box',
              border: 'none',
              borderRight: '1px solid',
              borderColor: 'divider',
              bgcolor: 'background.paper',
              transition: 'width 220ms ease, background 220ms ease',
              overflowX: 'hidden',
            },
            display: { xs: 'none', lg: 'block' },
          }}
        >
          {renderContent(collapsed)}
        </Drawer>
      )}
      <Drawer
        variant="temporary"
        open={open}
        onClose={onClose}
        ModalProps={{ keepMounted: true }}
        sx={{
          '& .MuiDrawer-paper': {
            width,
            boxSizing: 'border-box',
            bgcolor: 'background.paper',
          },
          display: { xs: 'block', lg: 'none' },
        }}
      >
        {renderContent(false, true)}
      </Drawer>
    </SidebarContext.Provider>
  )
}