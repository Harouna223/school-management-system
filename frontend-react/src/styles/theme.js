import { createTheme } from '@mui/material/styles'

/* ============================================================
   SMS — School Management System
   Design System v3 — ERP SaaS moderne
   Palette : bleu #2563EB, surfaces neutres, badges doux
   ============================================================ */

const BRAND = {
  primary: '#2563EB',
  primaryHover: '#1D4ED8',
  primaryActive: '#1E40AF',
  primaryDeep: '#1E3A8A',
  primaryLight: '#EFF6FF',
  primarySoft: '#DBEAFE',
  primaryGradient: 'linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%)',
  bg: '#F8FAFC',
  surface: '#FFFFFF',
  surfaceAlt: '#F1F5F9',
  border: '#E2E8F0',
  textPrimary: '#1E293B',
  textSecondary: '#64748B',
  textMuted: '#94A3B8',
  success: '#16A34A',
  successLight: '#DCFCE7',
  warning: '#F59E0B',
  warningLight: '#FEF3C7',
  danger: '#DC2626',
  dangerLight: '#FEE2E2',
  info: '#0284C7',
  infoLight: '#E0F2FE',
}

/* Ombres : jeu doux et progressif */
const shadows = {
  card: '0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 16px rgba(15, 23, 42, 0.05)',
  cardHover: '0 2px 4px rgba(15, 23, 42, 0.05), 0 12px 32px rgba(15, 23, 42, 0.1)',
  popover: '0 12px 40px rgba(15, 23, 42, 0.14)',
  dialog: '0 24px 64px rgba(15, 23, 42, 0.18)',
  primaryGlow: '0 4px 14px rgba(37, 99, 235, 0.28)',
  primaryGlowHover: '0 8px 22px rgba(37, 99, 235, 0.36)',
  darkCard: '0 1px 2px rgba(0, 0, 0, 0.3), 0 6px 20px rgba(0, 0, 0, 0.25)',
  darkCardHover: '0 4px 8px rgba(0, 0, 0, 0.35), 0 16px 40px rgba(0, 0, 0, 0.4)',
  darkPopover: '0 12px 44px rgba(0, 0, 0, 0.55)',
  darkDialog: '0 24px 64px rgba(0, 0, 0, 0.6)',
}

/* Typographie : Inter, hiérarchie claire 12 → 32 px */
const typography = {
  fontFamily: "'Inter Variable', 'Inter', system-ui, -apple-system, sans-serif",
  h1: { fontWeight: 800, letterSpacing: '-0.03em', fontSize: '2rem' },
  h2: { fontWeight: 800, letterSpacing: '-0.025em', fontSize: '1.75rem' },
  h3: { fontWeight: 700, letterSpacing: '-0.02em', fontSize: '1.5rem' },
  h4: { fontWeight: 800, letterSpacing: '-0.02em', fontSize: '1.375rem', lineHeight: 1.3 },
  h5: { fontWeight: 700, letterSpacing: '-0.015em', fontSize: '1.125rem', lineHeight: 1.35 },
  h6: { fontWeight: 700, letterSpacing: '-0.01em', fontSize: '1rem', lineHeight: 1.4 },
  subtitle1: { fontWeight: 600, fontSize: '0.9375rem' },
  subtitle2: { fontWeight: 600, fontSize: '0.875rem' },
  body1: { fontSize: '0.9375rem', lineHeight: 1.6 },
  body2: { fontSize: '0.875rem', lineHeight: 1.55 },
  caption: { fontSize: '0.75rem', lineHeight: 1.5 },
  button: { fontWeight: 600, letterSpacing: '0.01em' },
}

const shape = { borderRadius: 12 }

const softChip = (bg, fg) => ({
  background: bg,
  color: fg,
  fontWeight: 600,
  '& .MuiChip-deleteIcon': { color: fg, opacity: 0.6, '&:hover': { opacity: 1 } },
})

/* ---------- Composants communs clair/sombre ---------- */
const baseComponents = {
  MuiCssBaseline: {
    styleOverrides: {
      body: {
        WebkitFontSmoothing: 'antialiased',
        MozOsxFontSmoothing: 'grayscale',
        transition: 'background-color 240ms ease',
      },
    },
  },
  MuiButton: {
    defaultProps: { disableElevation: true },
    styleOverrides: {
      root: {
        textTransform: 'none',
        fontWeight: 600,
        borderRadius: 10,
        transition: 'all 180ms ease',
        '&:active': { transform: 'translateY(0px) scale(0.985)' },
        '&.Mui-focusVisible': { outline: '2px solid rgba(37, 99, 235, 0.45)', outlineOffset: 2 },
      },
      sizeSmall: { padding: '6px 14px', fontSize: 13 },
      sizeMedium: { padding: '9px 18px' },
      sizeLarge: { padding: '12px 24px', fontSize: 15 },
    },
  },
  MuiIconButton: {
    styleOverrides: {
      root: {
        transition: 'all 160ms ease',
        '&.Mui-focusVisible': { outline: '2px solid rgba(37, 99, 235, 0.45)', outlineOffset: 2 },
      },
    },
  },
  MuiCard: {
    styleOverrides: {
      root: {
        borderRadius: 16,
        transition: 'box-shadow 220ms ease, transform 220ms ease, border-color 220ms ease',
      },
    },
  },
  MuiPaper: {
    styleOverrides: { root: { backgroundImage: 'none' } },
  },
  MuiDialog: { styleOverrides: { paper: { borderRadius: 20 } } },
  MuiDialogTitle: { styleOverrides: { root: { fontWeight: 700, fontSize: 18, paddingBottom: 8 } } },
  MuiMenu: {
    styleOverrides: { paper: { borderRadius: 14 } },
  },
  MuiMenuItem: {
    styleOverrides: { root: { fontSize: 14, borderRadius: 8, margin: '0 6px' } },
  },
  MuiAutocomplete: { styleOverrides: { paper: { borderRadius: 14 } } },
  MuiDrawer: { styleOverrides: { paper: { borderRight: 'none' } } },
  MuiAppBar: { styleOverrides: { root: { boxShadow: 'none' } } },
  MuiTooltip: {
    defaultProps: { arrow: true, enterDelay: 350 },
    styleOverrides: {
      tooltip: { borderRadius: 8, fontSize: 12, padding: '6px 10px', fontWeight: 500 },
    },
  },
  MuiAvatar: { styleOverrides: { root: { borderRadius: 10 } } },
  MuiSkeleton: { styleOverrides: { root: { borderRadius: 8 } } },
  MuiAlert: { styleOverrides: { root: { borderRadius: 12 } } },
  MuiCardContent: {
    styleOverrides: {
      root: { '&:last-child': { paddingBottom: 0 } },
    },
  },
  MuiSnackbar: {
    styleOverrides: {
      root: {
        '& .MuiPaper-root': { borderRadius: 12, boxShadow: shadows.popover },
      },
    },
  },
  MuiTab: {
    styleOverrides: {
      root: {
        textTransform: 'none',
        fontWeight: 600,
        fontSize: 13.5,
        minHeight: 44,
      },
    },
  },
  MuiTabs: {
    styleOverrides: {
      root: { minHeight: 44 },
      indicator: { height: 3, borderRadius: 3 },
    },
  },
  MuiListItemIcon: { styleOverrides: { root: { minWidth: 40 } } },
  MuiListItemButton: {
    styleOverrides: {
      root: {
        borderRadius: 10,
        marginBottom: 2,
        '&:hover': { background: 'rgba(37, 99, 235, 0.06)' },
      },
    },
  },
  MuiTableSortLabel: {
    styleOverrides: { root: { '&.Mui-active': { fontWeight: 700 } } },
  },
  MuiTablePagination: {
    styleOverrides: {
      root: { borderTop: '1px solid' },
      selectLabel: { fontSize: 13 },
      displayedRows: { fontSize: 13, fontWeight: 500 },
    },
  },
  MuiInputLabel: { styleOverrides: { root: { fontWeight: 500 } } },
  MuiDivider: { styleOverrides: { root: {} } },
  MuiSwitch: { styleOverrides: { root: {} } },
  MuiChip: {
    styleOverrides: {
      root: { borderRadius: 999, fontWeight: 600, height: 26 },
      sizeSmall: { fontSize: 12, height: 22 },
    },
  },
  MuiCheckbox: {
    styleOverrides: {
      root: {
        borderRadius: 7,
        transition: 'all 160ms ease',
        '& .MuiSvgIcon-root': { borderRadius: 6, transition: 'all 160ms ease' },
      },
    },
  },
  MuiRadio: {
    styleOverrides: {
      root: { transition: 'all 160ms ease' },
    },
  },
  MuiLinearProgress: {
    styleOverrides: {
      root: {
        borderRadius: 999,
        height: 8,
        backgroundColor: 'transparent',
        overflow: 'hidden',
      },
      bar: { borderRadius: 999 },
    },
  },
  MuiBackdrop: {
    styleOverrides: {
      root: {
        backgroundColor: 'rgba(15, 23, 42, 0.45)',
        backdropFilter: 'blur(3px)',
      },
    },
  },
}

/* ---------- Surcharges clair ---------- */
const lightComponents = {
  ...baseComponents,
  MuiCssBaseline: {
    styleOverrides: {
      body: {
        WebkitFontSmoothing: 'antialiased',
        MozOsxFontSmoothing: 'grayscale',
        transition: 'background-color 240ms ease',
      },
    },
  },
  MuiButton: {
    defaultProps: { disableElevation: true },
    styleOverrides: {
      root: {
        textTransform: 'none',
        fontWeight: 600,
        borderRadius: 10,
        transition: 'all 180ms ease',
        '&:active': { transform: 'translateY(0px) scale(0.985)' },
        '&.Mui-focusVisible': { outline: '2px solid rgba(37, 99, 235, 0.45)', outlineOffset: 2 },
      },
      sizeSmall: { padding: '6px 14px', fontSize: 13 },
      sizeMedium: { padding: '9px 18px' },
      sizeLarge: { padding: '12px 24px', fontSize: 15 },
      containedPrimary: {
        background: BRAND.primaryGradient,
        boxShadow: shadows.primaryGlow,
        '&:hover': {
          background: 'linear-gradient(135deg, #1D4ED8 0%, #1E40AF 100%)',
          transform: 'translateY(-1px)',
          boxShadow: shadows.primaryGlowHover,
        },
        '&:disabled': { boxShadow: 'none' },
      },
      containedSecondary: {
        boxShadow: '0 4px 12px rgba(124, 58, 237, 0.22)',
        '&:hover': { transform: 'translateY(-1px)', boxShadow: '0 8px 20px rgba(124, 58, 237, 0.3)' },
      },
      containedSuccess: {
        background: '#16A34A',
        boxShadow: '0 4px 12px rgba(22, 163, 74, 0.24)',
        '&:hover': { background: '#15803D', transform: 'translateY(-1px)' },
      },
      containedError: {
        background: '#DC2626',
        boxShadow: '0 4px 12px rgba(220, 38, 38, 0.22)',
        '&:hover': { background: '#B91C1C', transform: 'translateY(-1px)' },
      },
      outlined: {
        borderColor: BRAND.border,
        '&:hover': { borderColor: '#CBD5E1', background: BRAND.primaryLight },
      },
      outlinedPrimary: {
        borderColor: 'rgba(37, 99, 235, 0.35)',
        '&:hover': { borderColor: BRAND.primary, background: BRAND.primaryLight },
      },
      text: { '&:hover': { background: BRAND.primaryLight } },
    },
  },
  MuiCard: {
    styleOverrides: {
      root: {
        borderRadius: 16,
        boxShadow: shadows.card,
        border: '1px solid rgba(226, 232, 240, 0.9)',
        transition: 'box-shadow 220ms ease, transform 220ms ease, border-color 220ms ease',
      },
    },
  },
  MuiPaper: {
    styleOverrides: {
      root: { backgroundImage: 'none' },
      elevation1: { boxShadow: shadows.card },
      elevation8: { boxShadow: shadows.popover },
    },
  },
  MuiOutlinedInput: {
    styleOverrides: {
      root: {
        borderRadius: 10,
        background: 'rgba(255, 255, 255, 0.7)',
        transition: 'all 160ms ease',
        '& .MuiOutlinedInput-notchedOutline': {
          borderColor: BRAND.border,
          transition: 'border-color 160ms ease, box-shadow 160ms ease',
        },
        '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: '#CBD5E1' },
        '&.Mui-focused': {
          background: '#fff',
          '& .MuiOutlinedInput-notchedOutline': { borderColor: BRAND.primary, borderWidth: 1.5 },
          boxShadow: '0 0 0 3px rgba(37, 99, 235, 0.12)',
        },
      },
      input: { '&::placeholder': { opacity: 0.6 } },
    },
  },
  MuiTableCell: {
    styleOverrides: {
      root: {
        padding: '13px 16px',
        borderBottom: '1px solid rgba(226, 232, 240, 0.7)',
        fontSize: 14,
        color: '#334155',
      },
      head: {
        fontWeight: 700,
        fontSize: 11.5,
        textTransform: 'uppercase',
        letterSpacing: '0.06em',
        color: BRAND.textMuted,
        background: '#F8FAFC',
        borderBottom: '1px solid #E2E8F0',
      },
    },
  },
  MuiTableRow: {
    styleOverrides: {
      root: {
        transition: 'background 140ms ease',
        '&:hover': { background: '#F8FAFC !important' },
        '&:last-child td': { borderBottom: 'none' },
      },
    },
  },
  MuiTableSortLabel: {
    styleOverrides: { root: { '&.Mui-active': { color: BRAND.primary } } },
  },
  MuiTablePagination: {
    styleOverrides: {
      root: { borderTop: '1px solid rgba(226, 232, 240, 0.7)' },
      selectLabel: { fontSize: 13 },
      displayedRows: { fontSize: 13, fontWeight: 500 },
    },
  },
  MuiChip: {
    styleOverrides: {
      root: { borderRadius: 999, fontWeight: 600, height: 26 },
      sizeSmall: { fontSize: 12, height: 22 },
      colorSuccess: softChip(BRAND.successLight, '#15803D'),
      colorError: softChip(BRAND.dangerLight, '#B91C1C'),
      colorWarning: softChip(BRAND.warningLight, '#B45309'),
      colorInfo: softChip(BRAND.infoLight, '#0369A1'),
      colorPrimary: softChip(BRAND.primaryLight, BRAND.primary),
      colorSecondary: softChip('#EDE9FE', '#6D28D9'),
      colorDefault: { background: '#F1F5F9', color: '#475569', fontWeight: 600 },
      outlined: {
        borderColor: BRAND.border,
        background: 'transparent',
        color: '#475569',
        '&.MuiChip-colorPrimary': { borderColor: 'rgba(37,99,235,0.4)', color: BRAND.primary },
        '&.MuiChip-colorSuccess': { borderColor: 'rgba(22,163,74,0.4)', color: '#15803D' },
        '&.MuiChip-colorError': { borderColor: 'rgba(220,38,38,0.4)', color: '#B91C1C' },
        '&.MuiChip-colorWarning': { borderColor: 'rgba(217,119,6,0.4)', color: '#B45309' },
        '&.MuiChip-colorInfo': { borderColor: 'rgba(2,132,199,0.4)', color: '#0369A1' },
      },
    },
  },
  MuiDialog: {
    styleOverrides: {
      paper: { borderRadius: 20, boxShadow: shadows.dialog },
    },
  },
  MuiMenu: {
    styleOverrides: {
      paper: {
        borderRadius: 14,
        boxShadow: shadows.popover,
        border: '1px solid rgba(226, 232, 240, 0.7)',
      },
    },
  },
  MuiMenuItem: {
    styleOverrides: {
      root: { fontSize: 14, borderRadius: 8, margin: '0 6px', '&:hover': { background: '#F1F5F9' } },
    },
  },
  MuiAutocomplete: {
    styleOverrides: { paper: { borderRadius: 14, boxShadow: shadows.popover } },
  },
  MuiListItemButton: {
    styleOverrides: {
      root: {
        borderRadius: 10,
        marginBottom: 2,
        '&.Mui-selected': {
          background: BRAND.primaryLight,
          '& .MuiListItemIcon-root': { color: BRAND.primary },
          '& .MuiListItemText-primary': { color: BRAND.primary, fontWeight: 600 },
        },
        '&.Mui-selected:hover': { background: BRAND.primarySoft },
      },
    },
  },
  MuiListItemIcon: { styleOverrides: { root: { minWidth: 40, color: '#64748B' } } },
  MuiTabs: {
    styleOverrides: {
      root: { minHeight: 44 },
      indicator: { height: 3, borderRadius: 3, background: 'linear-gradient(90deg, #2563EB, #4F46E5)' },
    },
  },
  MuiTab: {
    styleOverrides: {
      root: {
        textTransform: 'none',
        fontWeight: 600,
        fontSize: 13.5,
        minHeight: 44,
        color: '#64748B',
        '&.Mui-selected': { color: BRAND.primary },
      },
    },
  },
  MuiAlert: {
    styleOverrides: {
      root: { borderRadius: 12 },
      standardSuccess: { background: BRAND.successLight, color: '#15803D' },
      standardError: { background: BRAND.dangerLight, color: '#B91C1C' },
      standardWarning: { background: BRAND.warningLight, color: '#B45309' },
      standardInfo: { background: BRAND.infoLight, color: '#0369A1' },
    },
  },
  MuiDivider: { styleOverrides: { root: { borderColor: 'rgba(226, 232, 240, 0.7)' } } },
  MuiSwitch: {
    styleOverrides: {
      switchBase: {
        '&.Mui-checked': { color: BRAND.primary },
        '&.Mui-checked + .MuiSwitch-track': { backgroundColor: BRAND.primary, opacity: 1 },
      },
    },
  },
}

/* ---------- Surcharges sombre (surfaces empilées) ---------- */
const darkComponents = {
  ...baseComponents,
  MuiCssBaseline: {
    styleOverrides: {
      body: {
        WebkitFontSmoothing: 'antialiased',
        MozOsxFontSmoothing: 'grayscale',
        transition: 'background-color 240ms ease',
      },
    },
  },
  MuiButton: {
    defaultProps: { disableElevation: true },
    styleOverrides: {
      root: {
        textTransform: 'none',
        fontWeight: 600,
        borderRadius: 10,
        transition: 'all 180ms ease',
        '&:active': { transform: 'translateY(0px) scale(0.985)' },
        '&.Mui-focusVisible': { outline: '2px solid rgba(37, 99, 235, 0.45)', outlineOffset: 2 },
      },
      sizeSmall: { padding: '6px 14px', fontSize: 13 },
      sizeMedium: { padding: '9px 18px' },
      sizeLarge: { padding: '12px 24px', fontSize: 15 },
      containedPrimary: {
        background: 'linear-gradient(135deg, #3B82F6 0%, #2563EB 100%)',
        boxShadow: '0 4px 14px rgba(59, 130, 246, 0.3)',
        '&:hover': {
          background: 'linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%)',
          transform: 'translateY(-1px)',
          boxShadow: '0 8px 24px rgba(59, 130, 246, 0.4)',
        },
        '&:disabled': { boxShadow: 'none' },
      },
      containedSuccess: { background: '#16A34A', '&:hover': { background: '#15803D', transform: 'translateY(-1px)' } },
      containedError: { background: '#DC2626', '&:hover': { background: '#B91C1C', transform: 'translateY(-1px)' } },
      outlined: { borderColor: '#334155', '&:hover': { borderColor: '#475569', background: 'rgba(59, 130, 246, 0.08)' } },
      outlinedPrimary: {
        borderColor: 'rgba(59, 130, 246, 0.4)',
        '&:hover': { borderColor: '#3B82F6', background: 'rgba(59, 130, 246, 0.1)' },
      },
      text: { '&:hover': { background: 'rgba(59, 130, 246, 0.1)' } },
    },
  },
  MuiCard: {
    styleOverrides: {
      root: {
        borderRadius: 16,
        boxShadow: shadows.darkCard,
        border: '1px solid rgba(51, 65, 85, 0.6)',
        transition: 'box-shadow 220ms ease, transform 220ms ease, border-color 220ms ease',
      },
    },
  },
  MuiPaper: {
    styleOverrides: {
      root: { backgroundImage: 'none' },
      elevation1: { boxShadow: shadows.darkCard },
      elevation8: { boxShadow: shadows.darkPopover },
    },
  },
  MuiOutlinedInput: {
    styleOverrides: {
      root: {
        borderRadius: 10,
        background: 'rgba(30, 41, 59, 0.45)',
        transition: 'all 160ms ease',
        '& .MuiOutlinedInput-notchedOutline': {
          borderColor: '#334155',
          transition: 'border-color 160ms ease, box-shadow 160ms ease',
        },
        '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: '#475569' },
        '&.Mui-focused': {
          background: '#1E293B',
          '& .MuiOutlinedInput-notchedOutline': { borderColor: '#3B82F6', borderWidth: 1.5 },
          boxShadow: '0 0 0 3px rgba(59, 130, 246, 0.18)',
        },
      },
      input: { '&::placeholder': { opacity: 0.5 } },
    },
  },
  MuiTableCell: {
    styleOverrides: {
      root: {
        padding: '13px 16px',
        borderBottom: '1px solid rgba(51, 65, 85, 0.5)',
        fontSize: 14,
        color: '#CBD5E1',
      },
      head: {
        fontWeight: 700,
        fontSize: 11.5,
        textTransform: 'uppercase',
        letterSpacing: '0.06em',
        color: '#94A3B8',
        background: 'rgba(30, 41, 59, 0.65)',
        borderBottom: '1px solid #334155',
      },
    },
  },
  MuiTableRow: {
    styleOverrides: {
      root: {
        transition: 'background 140ms ease',
        '&:hover': { background: 'rgba(51, 65, 85, 0.35) !important' },
        '&:last-child td': { borderBottom: 'none' },
      },
    },
  },
  MuiTableSortLabel: { styleOverrides: { root: { '&.Mui-active': { color: '#60A5FA' } } } },
  MuiTablePagination: {
    styleOverrides: {
      root: { borderTop: '1px solid rgba(51, 65, 85, 0.5)' },
      selectLabel: { fontSize: 13 },
      displayedRows: { fontSize: 13, fontWeight: 500 },
    },
  },
  MuiChip: {
    styleOverrides: {
      root: { borderRadius: 999, fontWeight: 600, height: 26 },
      sizeSmall: { fontSize: 12, height: 22 },
      colorSuccess: softChip('rgba(34,197,94,0.16)', '#4ADE80'),
      colorError: softChip('rgba(239,68,68,0.16)', '#F87171'),
      colorWarning: softChip('rgba(245,158,11,0.16)', '#FBBF24'),
      colorInfo: softChip('rgba(56,189,248,0.16)', '#38BDF8'),
      colorPrimary: softChip('rgba(59,130,246,0.18)', '#93C5FD'),
      colorSecondary: softChip('rgba(167,139,250,0.16)', '#C4B5FD'),
      colorDefault: { background: 'rgba(148,163,184,0.14)', color: '#CBD5E1', fontWeight: 600 },
      outlined: {
        borderColor: '#334155',
        background: 'transparent',
        color: '#CBD5E1',
        '&.MuiChip-colorPrimary': { borderColor: 'rgba(96,165,250,0.4)', color: '#93C5FD' },
        '&.MuiChip-colorSuccess': { borderColor: 'rgba(74,222,128,0.4)', color: '#4ADE80' },
        '&.MuiChip-colorError': { borderColor: 'rgba(248,113,113,0.4)', color: '#F87171' },
        '&.MuiChip-colorWarning': { borderColor: 'rgba(251,191,36,0.4)', color: '#FBBF24' },
        '&.MuiChip-colorInfo': { borderColor: 'rgba(56,189,248,0.4)', color: '#38BDF8' },
      },
    },
  },
  MuiDialog: {
    styleOverrides: {
      paper: { borderRadius: 20, boxShadow: shadows.darkDialog },
    },
  },
  MuiMenu: {
    styleOverrides: {
      paper: {
        borderRadius: 14,
        boxShadow: shadows.darkPopover,
        border: '1px solid rgba(51, 65, 85, 0.7)',
      },
    },
  },
  MuiMenuItem: {
    styleOverrides: {
      root: { fontSize: 14, borderRadius: 8, margin: '0 6px', '&:hover': { background: 'rgba(148,163,184,0.1)' } },
    },
  },
  MuiAutocomplete: {
    styleOverrides: { paper: { borderRadius: 14, boxShadow: shadows.darkPopover } },
  },
  MuiListItemButton: {
    styleOverrides: {
      root: {
        borderRadius: 10,
        marginBottom: 2,
        '&.Mui-selected': {
          background: 'rgba(59, 130, 246, 0.16)',
          '& .MuiListItemIcon-root': { color: '#60A5FA' },
          '& .MuiListItemText-primary': { color: '#93C5FD', fontWeight: 600 },
        },
        '&.Mui-selected:hover': { background: 'rgba(59, 130, 246, 0.24)' },
      },
    },
  },
  MuiListItemIcon: { styleOverrides: { root: { minWidth: 40, color: '#94A3B8' } } },
  MuiTabs: {
    styleOverrides: {
      root: { minHeight: 44 },
      indicator: { height: 3, borderRadius: 3, background: 'linear-gradient(90deg, #3B82F6, #818CF8)' },
    },
  },
  MuiTab: {
    styleOverrides: {
      root: {
        textTransform: 'none',
        fontWeight: 600,
        fontSize: 13.5,
        minHeight: 44,
        color: '#94A3B8',
        '&.Mui-selected': { color: '#93C5FD' },
      },
    },
  },
  MuiAlert: {
    styleOverrides: {
      root: { borderRadius: 12 },
      standardSuccess: { background: 'rgba(34,197,94,0.14)', color: '#4ADE80' },
      standardError: { background: 'rgba(239,68,68,0.14)', color: '#F87171' },
      standardWarning: { background: 'rgba(245,158,11,0.14)', color: '#FBBF24' },
      standardInfo: { background: 'rgba(56,189,248,0.14)', color: '#38BDF8' },
    },
  },
  MuiDivider: { styleOverrides: { root: { borderColor: 'rgba(51, 65, 85, 0.7)' } } },
  MuiSwitch: {
    styleOverrides: {
      switchBase: {
        '&.Mui-checked': { color: '#3B82F6' },
        '&.Mui-checked + .MuiSwitch-track': { backgroundColor: '#3B82F6', opacity: 1 },
      },
    },
  },
}

export const lightTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: BRAND.primary,
      dark: BRAND.primaryHover,
      light: BRAND.primaryLight,
      contrastText: '#fff',
    },
    secondary: { main: '#7C3AED', dark: '#6D28D9', light: '#EDE9FE', contrastText: '#fff' },
    background: { default: BRAND.bg, paper: BRAND.surface },
    text: { primary: BRAND.textPrimary, secondary: BRAND.textSecondary, disabled: BRAND.textMuted },
    divider: BRAND.border,
    success: { main: BRAND.success, light: BRAND.successLight, dark: '#15803D' },
    warning: { main: BRAND.warning, light: BRAND.warningLight, dark: '#B45309' },
    error: { main: BRAND.danger, light: BRAND.dangerLight, dark: '#B91C1C' },
    info: { main: BRAND.info, light: BRAND.infoLight, dark: '#0369A1' },
    action: { hover: '#F1F5F9', selected: BRAND.primaryLight, focus: 'rgba(37,99,235,0.12)' },
  },
  shape,
  typography,
  components: lightComponents,
})

export const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#3B82F6', dark: '#1D4ED8', light: 'rgba(59,130,246,0.22)', contrastText: '#fff' },
    secondary: { main: '#A78BFA', dark: '#8B5CF6', light: 'rgba(167,139,250,0.2)', contrastText: '#fff' },
    background: { default: '#0B1220', paper: '#0F172A' },
    text: { primary: '#E2E8F0', secondary: '#94A3B8', disabled: '#64748B' },
    divider: '#1E293B',
    success: { main: '#22C55E', light: 'rgba(34,197,94,0.16)', dark: '#16A34A' },
    warning: { main: '#F59E0B', light: 'rgba(245,158,11,0.16)', dark: '#FBBF24' },
    error: { main: '#EF4444', light: 'rgba(239,68,68,0.16)', dark: '#F87171' },
    info: { main: '#38BDF8', light: 'rgba(56,189,248,0.16)', dark: '#0EA5E9' },
    action: { hover: 'rgba(148, 163, 184, 0.08)', selected: 'rgba(59, 130, 246, 0.16)', focus: 'rgba(59,130,246,0.2)' },
  },
  shape,
  typography,
  components: darkComponents,
})

export default lightTheme