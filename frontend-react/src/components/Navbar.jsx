import { useEffect, useState, useRef } from 'react'
import {
  AppBar,
  Toolbar,
  IconButton,
  Typography,
  Box,
  Avatar,
  Menu,
  MenuItem,
  Badge,
  Tooltip,
  Autocomplete,
  TextField,
  InputAdornment,
  ListItemAvatar,
  ListItemText,
  ListItemButton,
  Divider,
  Chip,
  Button,
} from '@mui/material'
import {
  Menu as MenuIcon,
  Notifications as NotificationsIcon,
  DarkMode,
  LightMode,
  AccountCircle,
  Logout,
  Search as SearchIcon,
  MailOutline as MailIcon,
  ChevronLeft,
  ChevronRight,
  DoneAll,
  KeyboardArrowDown,
} from '@mui/icons-material'
import { useNavigate, useLocation } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { logout, clearCredentials } from '../redux/slices/authSlice'
import { communicationApi, searchApi } from '../api/endpoints'
import { useThemeContext } from '../context/ThemeContext'
import { useI18n } from '../i18n/I18nContext'
import { initials, formatDateTime } from '../utils/format'

const PAGE_META = {
  '/dashboard': { title: 'nav.dashboard', group: 'nav.group.main' },
  '/profile': { title: 'nav.profile', group: 'nav.group.main' },
  '/students': { title: 'nav.students', group: 'nav.group.admin' },
  '/students/new': { title: 'nav.students.new', group: 'nav.group.admin' },
  '/teachers': { title: 'nav.teachers', group: 'nav.group.admin' },
  '/teachers/new': { title: 'nav.teachers.new', group: 'nav.group.admin' },
  '/classes': { title: 'nav.classes', group: 'nav.group.admin' },
  '/subjects': { title: 'nav.subjects', group: 'nav.group.pedagogy' },
  '/schedules': { title: 'nav.schedule', group: 'nav.group.pedagogy' },
  '/attendances': { title: 'nav.attendances', group: 'nav.group.pedagogy' },
  '/grades': { title: 'nav.grades', group: 'nav.group.pedagogy' },
  '/exams': { title: 'nav.exams', group: 'nav.group.pedagogy' },
  '/reports': { title: 'nav.reports', group: 'nav.group.pedagogy' },
  '/payments': { title: 'nav.payments', group: 'nav.group.finance' },
  '/expenses': { title: 'nav.expenses', group: 'nav.group.finance' },
  '/teacher-payroll': { title: 'nav.teacherPayroll', group: 'nav.group.finance' },
  '/library': { title: 'nav.library', group: 'nav.group.services' },
  '/hr': { title: 'nav.hr', group: 'nav.group.services' },
  '/messages': { title: 'nav.messages', group: 'nav.group.services' },
  '/announcements': { title: 'nav.announcements', group: 'nav.group.services' },
  '/my-children': { title: 'nav.children', group: 'nav.group.spaces' },
  '/my-school': { title: 'nav.my-school', group: 'nav.group.spaces' },
  '/my-teaching': { title: 'nav.my-teaching', group: 'nav.group.spaces' },
  '/users': { title: 'nav.users', group: 'nav.group.system' },
  '/audit': { title: 'nav.audit', group: 'nav.group.system' },
  '/settings': { title: 'nav.settings', group: 'nav.group.system' },
}

const ROLE_STYLE = {
  SUPER_ADMIN: { bg: 'rgba(124,58,237,0.12)', color: '#7c3aed' },
  DIRECTEUR: { bg: 'rgba(37,99,235,0.12)', color: '#2563eb' },
  COMPTABLE: { bg: 'rgba(2,132,199,0.12)', color: '#0284c7' },
  SECRETAIRE: { bg: 'rgba(217,119,6,0.12)', color: '#b45309' },
  ENSEIGNANT: { bg: 'rgba(22,163,74,0.12)', color: '#15803d' },
  PARENT: { bg: 'rgba(6,182,212,0.12)', color: '#0e7490' },
  ELEVE: { bg: 'rgba(226,232,240,0.9)', color: '#475569' },
}

/**
 * Barre supérieure moderne : breadcrumb, recherche globale, notifications,
 * mode sombre et profil.
 */
export default function Navbar({ onMenuClick, collapsed, onCollapseToggle }) {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch()
  const user = useSelector((state) => state.auth.user)
  const { mode, toggleMode } = useThemeContext()
  const { t, lang, setLang } = useI18n()
  const [anchorEl, setAnchorEl] = useState(null)
  const [notifAnchor, setNotifAnchor] = useState(null)
  const [notifications, setNotifications] = useState([])
  const [unread, setUnread] = useState(0)
  const [unreadMessages, setUnreadMessages] = useState(0)
  const [search, setSearch] = useState('')
  const [results, setResults] = useState([])
  const [searchOpen, setSearchOpen] = useState(false)
  const [mobileSearchAnchor, setMobileSearchAnchor] = useState(null)
  const searchInputRef = useRef(null)
  const mobileSearchInputRef = useRef(null)
  const mobileSearchButtonRef = useRef(null)
  const debounceRef = useRef(null)

  const pageMeta =
    PAGE_META[location.pathname] ||
    Object.entries(PAGE_META).find(([p]) => location.pathname.startsWith(`${p}/`))?.[1] ||
    { title: 'topbar.schoolName', group: 'topbar.home' }

  useEffect(() => {
    let mounted = true
    const load = async () => {
      try {
        const { data } = await communicationApi.unreadCount()
        if (mounted) setUnread(data.data ?? 0)
        const res = await communicationApi.notifications({ page: 0, size: 6 })
        if (mounted) setNotifications(res.data.data.content ?? [])
        const msgs = await communicationApi.unreadMessages()
        if (mounted) setUnreadMessages(msgs.data.data ?? 0)
      } catch {
        // silencieux
      }
    }
    load()
    const interval = setInterval(load, 60000)
    return () => {
      mounted = false
      clearInterval(interval)
    }
  }, [])

  /* Recherche globale multi-modules : élèves, enseignants, classes, factures, convocations */
  useEffect(() => {
    clearTimeout(debounceRef.current)
    if (search.trim().length < 2) {
      setResults([])
      return
    }
    debounceRef.current = setTimeout(async () => {
      try {
        const { data } = await searchApi.globalSearch(search.trim())
        const hits = []
        ;(data.data?.students || []).forEach((s) => hits.push({
          type: 'student', id: s.id, label: s.fullName, sublabel: `${s.matricule || ''} ${s.className || ''}`.trim(), group: 'Élèves',
        }))
        ;(data.data?.teachers || []).forEach((t) => hits.push({
          type: 'teacher', id: t.id, label: t.fullName, sublabel: t.employeeNo || 'Enseignant', group: 'Enseignants',
        }))
        ;(data.data?.classes || []).forEach((c) => hits.push({
          type: 'class', id: c.id, label: c.name, sublabel: c.code || '', group: 'Classes',
        }))
        ;(data.data?.invoices || []).forEach((i) => hits.push({
          type: 'invoice', id: i.id, label: i.invoiceNo, sublabel: `${i.studentName || ''} — ${i.feeTypeName || ''}`.trim(), group: 'Factures',
        }))
        ;(data.data?.convocations || []).forEach((c) => hits.push({
          type: 'convocation', id: c.id, label: c.name, sublabel: `${c.code || ''} — ${c.subInfo || ''}`.trim(), group: 'Convocations',
        }))
        setResults(hits)
        setSearchOpen(true)
      } catch {
        // silencieux
      }
    }, 350)
    return () => clearTimeout(debounceRef.current)
  }, [search])

  /* Raccourci clavier Ctrl/Cmd+K : focus recherche */
  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        if (window.matchMedia('(max-width:599px)').matches) {
          setMobileSearchAnchor(mobileSearchButtonRef.current)
          setTimeout(() => mobileSearchInputRef.current?.focus(), 0)
        } else {
          searchInputRef.current?.focus()
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const goToSearchHit = (hit) => {
    setSearch('')
    setResults([])
    if (!hit) return
    switch (hit.type) {
      case 'student':
        navigate(`/students?search=${encodeURIComponent(hit.label)}`)
        break
      case 'teacher':
        navigate(`/teachers`)
        break
      case 'class':
        navigate(`/classes`)
        break
      case 'invoice':
        navigate(`/payments?tab=invoices`)
        break
      case 'convocation':
        navigate(`/convocations`)
        break
      default:
        break
    }
  }

  const handleLogout = async () => {
    setAnchorEl(null)
    await dispatch(logout())
    dispatch(clearCredentials())
    navigate('/login')
  }

  const openProfile = (e) => setAnchorEl(e.currentTarget)
  const openNotifs = (e) => {
    setNotifAnchor(e.currentTarget)
    setUnread(0)
    communicationApi.markAllRead().catch(() => {})
  }

  const handleNotifClick = (n) => {
    setNotifAnchor(null)
    if (n.link) navigate(n.link)
  }

  const role = user?.roles?.[0]?.replace('_', ' ') || ''

  return (
    <AppBar
      position="sticky"
      elevation={0}
      color="inherit"
      sx={{
        backdropFilter: 'blur(12px)',
        bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(11, 18, 32, 0.8)' : 'rgba(255, 255, 255, 0.85)'),
        borderBottom: '1px solid',
        borderColor: 'divider',
        zIndex: (t) => t.zIndex.drawer - 1,
      }}
    >
      <Toolbar sx={{ gap: { xs: 0.5, sm: 1 }, minHeight: 64, px: { xs: 1, sm: 1.5, md: 3 } }}>
        <IconButton edge="start" onClick={onMenuClick} sx={{ display: { lg: 'none' }, width: { xs: 36, sm: 40 }, height: { xs: 36, sm: 40 }, p: 0.5, flexShrink: 0 }}>
          <MenuIcon />
        </IconButton>
        {!collapsed ? (
          <Tooltip title={t('topbar.collapse')}>
            <IconButton
              onClick={() => onCollapseToggle(true)}
              sx={{ display: { xs: 'none', lg: 'inline-flex' }, border: '1px solid', borderColor: 'divider', borderRadius: '10px' }}
              size="small"
            >
              <ChevronLeft sx={{ fontSize: 20 }} />
            </IconButton>
          </Tooltip>
        ) : (
          <Tooltip title={t('topbar.expand')}>
            <IconButton
              onClick={() => onCollapseToggle(false)}
              sx={{ display: { xs: 'none', lg: 'inline-flex' }, border: '1px solid', borderColor: 'divider', borderRadius: '10px' }}
              size="small"
            >
              <ChevronRight sx={{ fontSize: 20 }} />
            </IconButton>
          </Tooltip>
        )}

        {/* Breadcrumb */}
        <Box sx={{ mr: 1, display: { xs: 'none', lg: 'block' }, minWidth: 0 }}>
          <Box display="flex" alignItems="center" gap={0.8}>
            <Typography
              variant="caption"
              sx={{
                color: 'text.disabled',
                fontSize: 10.5,
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.07em',
              }}
            >
              {t(pageMeta.group)}
            </Typography>
            <Typography variant="caption" sx={{ color: 'text.disabled' }}>/</Typography>
            <Typography variant="h6" sx={{ fontWeight: 800, fontSize: 16.5, lineHeight: 1.2 }}>
              {t(pageMeta.title)}
            </Typography>
          </Box>
        </Box>

        <Box sx={{ flexGrow: 1 }} />

        {/* Recherche globale */}
        <Autocomplete
          freeSolo
          size="small"
          options={results}
          open={searchOpen}
          onClose={() => setSearchOpen(false)}
          getOptionLabel={(opt) => (typeof opt === 'string' ? opt : opt.label)}
          groupBy={(opt) => (typeof opt === 'string' ? '' : opt.group)}
          filterOptions={(x) => x}
          inputValue={search}
          onInputChange={(_, v) => setSearch(v)}
          onChange={(_, v) => v && typeof v !== 'string' && goToSearchHit(v)}
          noOptionsText={t('topbar.noStudent')}
          sx={{
            display: { xs: 'none', sm: 'block' },
            width: { xs: 0, sm: 160, md: 180, lg: 220 },
            flexShrink: 0,
            '& .MuiOutlinedInput-root': {
              background: (t) => (t.palette.mode === 'dark' ? 'rgba(30,41,59,0.6)' : '#f1f5f9'),
              borderRadius: '12px',
              transition: 'background 160ms ease, box-shadow 160ms ease',
              '&:hover': { background: (th) => (th.palette.mode === 'dark' ? '#1e293b' : '#e2e8f0') },
              '&.Mui-focused': {
                background: '#fff',
                boxShadow: '0 0 0 3px rgba(37, 99, 235, 0.12)',
              },
            },
          }}
          renderInput={(params) => (
            <TextField
              {...params}
              inputRef={searchInputRef}
              placeholder={t('topbar.search')}
              InputProps={{
                ...params.InputProps,
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <Box
                      component="span"
                      sx={{
                        display: { xs: 'none', sm: 'inline-flex' },
                        alignItems: 'center',
                        px: 0.8,
                        py: 0.2,
                        borderRadius: '6px',
                        border: '1px solid',
                        borderColor: 'divider',
                        fontSize: 10.5,
                        fontWeight: 700,
                        color: 'text.disabled',
                        background: (t) => (t.palette.mode === 'dark' ? 'rgba(30,41,59,0.6)' : '#fff'),
                      }}
                    >
                      Ctrl K
                    </Box>
                  </InputAdornment>
                ),
              }}
            />
          )}
          renderOption={(props, option) => (
            <ListItemButton component="li" {...props} dense>
              <ListItemAvatar>
                <Avatar sx={{ width: 30, height: 30, bgcolor: option.type === 'student' ? 'primary.main' : option.type === 'teacher' ? 'success.main' : option.type === 'class' ? 'warning.main' : 'error.main', fontSize: 11 }}>
                  {option.type === 'student' ? 'É' : option.type === 'teacher' ? 'P' : option.type === 'class' ? 'C' : 'F'}
                </Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={option.label}
                secondary={option.sublabel}
                primaryTypographyProps={{ fontSize: 13, fontWeight: 600 }}
                secondaryTypographyProps={{ fontSize: 12 }}
              />
            </ListItemButton>
          )}
        />

        <IconButton
          ref={mobileSearchButtonRef}
          onClick={(event) => setMobileSearchAnchor(event.currentTarget)}
          aria-label={t('topbar.search')}
          sx={{ display: { xs: 'inline-flex', sm: 'none' }, width: 36, height: 36, p: 0.5, flexShrink: 0, border: '1px solid', borderColor: 'divider', borderRadius: '10px' }}
        >
          <SearchIcon sx={{ fontSize: 20 }} />
        </IconButton>
        <Menu
          anchorEl={mobileSearchAnchor}
          open={Boolean(mobileSearchAnchor)}
          onClose={() => {
            setMobileSearchAnchor(null)
            setSearch('')
            setResults([])
            setSearchOpen(false)
          }}
          slotProps={{ paper: { sx: { width: 'calc(100vw - 24px)', maxWidth: 'calc(100vw - 24px)', p: 1 } } }}
        >
          <Box sx={{ width: '100%' }}>
            <Autocomplete
              freeSolo
              fullWidth
              autoHighlight
              size="small"
              options={results}
              open={searchOpen}
              onClose={() => setSearchOpen(false)}
              getOptionLabel={(opt) => (typeof opt === 'string' ? opt : opt.label)}
              groupBy={(opt) => (typeof opt === 'string' ? '' : opt.group)}
              filterOptions={(options) => options}
              inputValue={search}
              onInputChange={(_, value) => setSearch(value)}
              onChange={(_, value) => {
                if (value && typeof value !== 'string') {
                  goToSearchHit(value)
                  setMobileSearchAnchor(null)
                }
              }}
              noOptionsText={t('topbar.noStudent')}
              sx={{ width: '100%' }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  inputRef={mobileSearchInputRef}
                  autoFocus
                  placeholder={t('topbar.search')}
                />
              )}
            />
          </Box>
        </Menu>

        <Tooltip title={mode === 'dark' ? t('topbar.lightMode') : t('topbar.darkMode')}>
          <IconButton onClick={toggleMode} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: '10px', width: { xs: 36, sm: 40 }, height: { xs: 36, sm: 40 }, p: 0.5, flexShrink: 0 }}>
            {mode === 'dark' ? <LightMode sx={{ fontSize: 20 }} /> : <DarkMode sx={{ fontSize: 20 }} />}
          </IconButton>
        </Tooltip>

        <Tooltip title={lang === 'fr' ? 'English' : 'Français'}>
          <IconButton onClick={() => setLang(lang === 'fr' ? 'en' : 'fr')} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: '10px', width: { xs: 36, sm: 40 }, height: { xs: 36, sm: 40 }, p: 0.5, flexShrink: 0 }}>
            <Typography variant="body2" fontWeight={800} sx={{ fontSize: 13 }}>
              {lang === 'fr' ? 'EN' : 'FR'}
            </Typography>
          </IconButton>
        </Tooltip>

        <Tooltip title={t('topbar.messages')}>
          <IconButton onClick={() => navigate('/messages')} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: '10px', width: { xs: 36, sm: 40 }, height: { xs: 36, sm: 40 }, p: 0.5, flexShrink: 0 }}>
            <Badge badgeContent={unreadMessages} color="error">
              <MailIcon sx={{ fontSize: 20 }} />
            </Badge>
          </IconButton>
        </Tooltip>

        <Tooltip title={t('topbar.notifications')}>
          <IconButton onClick={openNotifs} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: '10px', width: { xs: 36, sm: 40 }, height: { xs: 36, sm: 40 }, p: 0.5, flexShrink: 0 }}>
            <Badge badgeContent={unread} color="error">
              <NotificationsIcon sx={{ fontSize: 20 }} />
            </Badge>
          </IconButton>
        </Tooltip>

        <Menu
          anchorEl={notifAnchor}
          open={Boolean(notifAnchor)}
          onClose={() => setNotifAnchor(null)}
          slotProps={{ paper: { sx: { width: { xs: 'calc(100vw - 24px)', sm: 380 }, maxWidth: 'calc(100vw - 24px)', maxHeight: 460, p: 1 } } }}
        >
          <Box px={1.5} py={1} display="flex" alignItems="center" justifyContent="space-between" gap={1}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle2" fontWeight={800} sx={{ overflowWrap: 'anywhere' }}>
                {t('topbar.notifications')}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t('topbar.notifSubtitle')}
              </Typography>
            </Box>
            {notifications.length > 0 && (
              <Button size="small" startIcon={<DoneAll sx={{ fontSize: 16 }} />} onClick={openNotifs}>
                {t('topbar.markAllRead')}
              </Button>
            )}
          </Box>
          <Divider sx={{ my: 0.5 }} />
          {notifications.length === 0 && (
            <MenuItem disabled sx={{ justifyContent: 'center', py: 3 }}>
              <Typography variant="body2" color="text.secondary">{t('topbar.noNotifications')}</Typography>
            </MenuItem>
          )}
          {notifications.map((n) => (
            <MenuItem
              key={n.id}
              onClick={() => handleNotifClick(n)}
              sx={{
                whiteSpace: 'normal',
                borderRadius: '10px',
                position: 'relative',
                bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(59,130,246,0.08)' : 'rgba(37,99,235,0.05)'),
              }}
            >
              <Box sx={{ width: '100%', minWidth: 0 }}>
                <Box display="flex" alignItems="center" justifyContent="space-between" gap={1}>
                  <Typography variant="subtitle2" fontWeight={700} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>{n.title}</Typography>
                  {n.createdAt && (
                    <Typography variant="caption" color="text.disabled" noWrap sx={{ flexShrink: 0 }}>
                      {formatDateTime(n.createdAt)}
                    </Typography>
                  )}
                </Box>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ overflowWrap: 'anywhere' }}>
                  {n.message}
                </Typography>
              </Box>
            </MenuItem>
          ))}
        </Menu>

        {/* Profil */}
        <Box
          display="flex"
          alignItems="center"
          gap={1.2}
          sx={{
            cursor: 'pointer',
            borderRadius: '12px',
            p: { xs: 0.25, sm: 0.5 },
            pl: { xs: 0.25, sm: 0.6 },
            pr: { xs: 0.25, sm: 1 },
            gap: { xs: 0, sm: 1.2 },
            flexShrink: 0,
            border: '1px solid',
            borderColor: 'divider',
            transition: 'background 160ms ease, border-color 160ms ease',
            '&:hover': { background: 'action.hover', borderColor: 'primary.light' },
          }}
          onClick={openProfile}
        >
          <Avatar
            src={user?.avatar}
            sx={{
              bgcolor: 'primary.main',
              width: 34,
              height: 34,
              fontSize: 13,
              boxShadow: '0 2px 8px rgba(37, 99, 235, 0.35)',
            }}
          >
            {initials(user?.firstName, user?.lastName)}
          </Avatar>
          <Box sx={{ display: { xs: 'none', md: 'block' } }}>
            <Typography variant="body2" fontWeight={700} lineHeight={1.2} sx={{ fontSize: 13.5 }}>
              {user?.firstName} {user?.lastName}
            </Typography>
            <Box display="flex" alignItems="center" gap={0.4}>
              <Chip
                label={role}
                size="small"
                sx={{
                  height: 17,
                  fontSize: 9.5,
                  fontWeight: 800,
                  letterSpacing: '0.05em',
                  textTransform: 'uppercase',
                  ...(ROLE_STYLE[user?.roles?.[0]] || ROLE_STYLE.ELEVE),
                }}
              />
              <KeyboardArrowDown sx={{ fontSize: 14, color: 'text.disabled' }} />
            </Box>
          </Box>
        </Box>
        <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
          <Box px={2} py={1.2}>
            <Typography variant="subtitle2" fontWeight={700}>
              {user?.firstName} {user?.lastName}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {user?.email || t('topbar.connectedAccount')}
            </Typography>
          </Box>
          <Divider sx={{ mb: 0.5 }} />
          <MenuItem onClick={() => { setAnchorEl(null); navigate('/profile') }}>
            <AccountCircle sx={{ mr: 1.2 }} fontSize="small" /> {t('topbar.myProfile')}
          </MenuItem>
          <MenuItem onClick={handleLogout} sx={{ color: 'error.main' }}>
            <Logout sx={{ mr: 1.2 }} fontSize="small" /> {t('nav.logout')}
          </MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  )
}