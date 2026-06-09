export const CNY_PER_USD = 7.2

const isZhLocale = (locale: string) => locale.toLowerCase().startsWith('zh')

export const moneyCurrency = (locale: string) => isZhLocale(locale) ? 'CNY' : 'USD'

export function toBaseAmount(value: number | string | undefined | null) {
  if (value == null || value === '') return 0
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0

  const match = String(value).replace(/,/g, '').match(/[+-]?\d+(?:\.\d+)?/)
  if (!match) return 0

  const amount = Number(match[0])
  return Number.isFinite(amount) ? amount : 0
}

export function toDisplayAmount(value: number | string | undefined | null, locale: string) {
  const amount = toBaseAmount(value)
  return isZhLocale(locale) ? amount : amount / CNY_PER_USD
}

export function formatMoney(
  value: number | string | undefined | null,
  locale: string,
  options: Intl.NumberFormatOptions = {}
) {
  const appLocale = isZhLocale(locale) ? 'zh-CN' : 'en-US'
  return new Intl.NumberFormat(appLocale, {
    style: 'currency',
    currency: moneyCurrency(locale),
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    ...options
  }).format(toDisplayAmount(value, locale))
}

export function formatMoneyRange(value: number | string | undefined | null, locale: string, limit = 2) {
  if (value == null || value === '') return ''
  if (typeof value === 'number') return formatMoney(value, locale)

  const text = String(value).trim()
  if (!text) return ''

  const matches = text.replace(/,/g, '').match(/[+-]?\d+(?:\.\d+)?/g)
  if (!matches?.length) return text

  return matches
    .slice(0, limit)
    .map(amount => formatMoney(amount, locale))
    .join(' - ')
}

export function formatLowestMoney(value: number | string | undefined | null, locale: string) {
  return formatMoneyRange(value, locale, 1)
}
