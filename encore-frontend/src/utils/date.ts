import type { LocaleCode } from '../i18n'

const toIntlLocale = (locale: string): string => locale === 'zh' ? 'zh-CN' : 'en-US'

export const formatLocaleDateTime = (value: string | null | undefined, locale: string | LocaleCode) => {
  if (!value) return '-'
  return new Date(value).toLocaleString(toIntlLocale(locale), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export const formatLocaleDay = (value: string, locale: string | LocaleCode) => {
  return new Date(`${value}T00:00:00`).toLocaleDateString(toIntlLocale(locale), {
    month: 'short',
    day: 'numeric'
  })
}

export const formatLocaleWeekday = (value: string, locale: string | LocaleCode) => {
  return new Date(`${value}T00:00:00`).toLocaleDateString(toIntlLocale(locale), {
    weekday: 'short'
  })
}
