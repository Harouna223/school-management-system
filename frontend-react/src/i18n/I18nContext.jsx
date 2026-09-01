import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { fr } from './locales/fr'
import { en } from './locales/en'

const STORAGE_KEY = 'sms.lang'

const translations = { fr, en }

const I18nContext = createContext(null)

/**
 * Fournisseur i18n : langue courante (fr/en), traduction par clé,
 * bascule persistée dans le localStorage.
 *
 * Si une clé manque dans la langue active, on retombe sur l'autre langue,
 * puis sur la clé elle-même.
 */
export function I18nProvider({ children }) {
  const [lang, setLang] = useState(() => {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'en' ? 'en' : 'fr'
  })

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, lang)
    document.documentElement.lang = lang
  }, [lang])

  const value = useMemo(() => {
    const t = (key, params) => {
      let text = translations[lang][key]
      if (text === undefined) {
        text = translations[lang === 'fr' ? 'en' : 'fr'][key]
      }
      if (text === undefined) return key
      if (params) {
        Object.entries(params).forEach(([k, v]) => {
          text = text.replaceAll(`{{${k}}}`, String(v))
        })
      }
      return text
    }
    return { lang, setLang, t }
  }, [lang])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export const useI18n = () => {
  const ctx = useContext(I18nContext)
  if (!ctx) {
    throw new Error('useI18n doit être utilisé dans un I18nProvider')
  }
  return ctx
}
