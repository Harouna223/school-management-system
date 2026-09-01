/**
 * Formate un nombre en devise (FCFA par défaut).
 */
export function formatCurrency(value, currency = 'FCFA') {
  if (value == null) return `0 ${currency}`
  return new Intl.NumberFormat('fr-FR', {
    maximumFractionDigits: 0,
  }).format(Number(value)) + ` ${currency}`
}

/**
 * Formate une date ISO en date lisible fr-FR.
 */
export function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}

/**
 * Formate une date avec heure.
 */
export function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Formate une note sur 20.
 */
export function formatGrade(value) {
  if (value == null) return '—'
  return `${Number(value).toFixed(2)}/20`
}

/**
 * Nom du jour de semaine en français.
 */
export const DAYS_FR = [
  'Lundi',
  'Mardi',
  'Mercredi',
  'Jeudi',
  'Vendredi',
  'Samedi',
  'Dimanche',
]

export const DAY_KEYS = {
  MONDAY: 0,
  TUESDAY: 1,
  WEDNESDAY: 2,
  THURSDAY: 3,
  FRIDAY: 4,
  SATURDAY: 5,
}

/**
 * Télécharge un blob (PDF/Excel) avec un nom de fichier.
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * Initiales d'une personne (pour les avatars).
 */
export function initials(firstName, lastName) {
  return `${(firstName || '?')[0]}${(lastName || '?')[0]}`.toUpperCase()
}

/**
 * Libellé lisible d'un enum (AFFICHAGE_PROPRE -> "Affichage propre").
 */
export function humanize(value) {
  if (!value) return '—'
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}