import { settingsApi } from '../api/endpoints'

let defaultCode = null
let promise = null

/**
 * Charge (une seule fois) l'indicatif pays par défaut depuis les paramètres.
 * Ne déduit jamais automatiquement un indicatif incorrect.
 */
export const getDefaultCountryCode = async () => {
  if (defaultCode !== null) return defaultCode
  if (!promise) {
    promise = settingsApi.defaults()
      .then((res) => {
        const defaults = res.data.data || {}
        defaultCode = (defaults.WHATSAPP_DEFAULT_NUMBER || '237').replace(/\D/g, '')
        return defaultCode
      })
      .catch(() => '237')
  }
  return promise
}

/**
 * Normalise un numéro : conserve un préfixe + ou 00 ; sinon ajoute l'indicatif
 * par défaut configuré dans les paramètres de l'établissement.
 */
export const normalizePhone = (phone, countryCode = '237') => {
  if (!phone) return null
  let digits = phone.replace(/[^\d+]/g, '')
  if (digits.startsWith('+')) return digits
  if (digits.startsWith('00')) return `+${digits.slice(2)}`
  if (digits.length >= 11) return `+${digits}`
  return `+${countryCode}${digits}`
}

/**
 * Construit un lien wa.me sécurisé (deep-link, aucune API payante).
 */
export const buildWhatsAppLink = (phone, message = '') => {
  const normalized = normalizePhone(phone, defaultCode || '237')
  if (!normalized) return null
  const url = `https://wa.me/${normalized.replace('+', '')}`
  return message ? `${url}?text=${encodeURIComponent(message)}` : url
}
