/**
 * Locale d'affichage des nombres et des dates, alignée sur la langue de
 * l'interface. Les fonctions restent utilisables hors composant React : la
 * locale est poussée par le fournisseur i18n, pas lue via un hook.
 */
let currentLocale = 'fr-FR'

/** Applique la langue active aux formats numériques et de date. */
export function setFormatLocale(lang) {
  currentLocale = lang === 'en' ? 'en-GB' : 'fr-FR'
}

/**
 * Formate un nombre en devise (FCFA par défaut).
 */
export function formatCurrency(value, currency = 'FCFA') {
  if (value == null) return `0 ${currency}`
  return new Intl.NumberFormat(currentLocale, {
    maximumFractionDigits: 0,
  }).format(Number(value)) + ` ${currency}`
}

/**
 * Formate une date ISO en date lisible selon la langue active.
 */
export function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString(currentLocale, {
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
  return new Date(value).toLocaleString(currentLocale, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Formate une note avec son barème réel.
 *
 * Le backend accepte un barème libre (`GradeResponse.maxValue`) : une note peut
 * être saisie sur 10, 20, 50 ou 100. Afficher « /20 » en dur faisait apparaître
 * « 85.00/20 » pour une note sur 100 — incohérent avec la moyenne, qui est bien
 * ramenée sur 20 par le serveur.
 *
 * @param {number|string} value    note saisie
 * @param {number|string} maxValue barème de l'évaluation (défaut 20)
 * @param {{normalized?: boolean}} [options] `normalized: true` pour ramener sur 20
 */
export function formatGrade(value, maxValue = 20, options = {}) {
  if (value == null) return '—'
  const note = Number(value)
  if (!Number.isFinite(note)) return '—'
  const bareme = Number(maxValue) > 0 ? Number(maxValue) : 20
  if (options.normalized) {
    return `${((note * 20) / bareme).toFixed(2)}/20`
  }
  return `${note.toFixed(2)}/${bareme}`
}

/**
 * Ramène une note sur 20 selon son barème (échelle du backend).
 * Renvoie `null` si la valeur n'est pas exploitable.
 */
export function toGradeOver20(value, maxValue = 20) {
  if (value == null) return null
  const note = Number(value)
  if (!Number.isFinite(note)) return null
  const bareme = Number(maxValue) > 0 ? Number(maxValue) : 20
  return (note * 20) / bareme
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