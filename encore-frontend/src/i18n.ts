import { createI18n } from 'vue-i18n'
import en from './locales/en'
import zh from './locales/zh'

export type LocaleCode = 'en' | 'zh'

const LOCALE_STORAGE_KEY = 'encore.locale'

const isLocaleCode = (value: string | null): value is LocaleCode => {
  return value === 'en' || value === 'zh'
}

const getInitialLocale = (): LocaleCode => {
  const storedLocale = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (isLocaleCode(storedLocale)) return storedLocale

  const browserLocale = navigator.language.toLowerCase()
  return browserLocale.startsWith('zh') ? 'zh' : 'en'
}

const i18n = createI18n({
  legacy: false, // use Composition API
  locale: getInitialLocale(),
  fallbackLocale: 'en',
  messages: {
    en,
    zh
  }
})

export function setAppLocale(locale: LocaleCode) {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en'
  document.documentElement.dataset.locale = locale
}

setAppLocale(getInitialLocale())

export default i18n
