import { formatLowestMoney } from './money'

export function lowestPriceLabel(priceRange?: string | null, locale = 'zh') {
  const text = String(priceRange || '').trim()
  if (!text) return ''
  const match = text.match(/[$￥¥€£]?\s*\d+(?:[.,]\d+)?/)
  return match ? formatLowestMoney(match[0], locale) : text
}

export function posterFallbackDataUri(title = 'ENCORE') {
  const safeTitle = (String(title || 'ENCORE').trim().slice(0, 42) || 'ENCORE')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 1200">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#151515"/>
          <stop offset="0.52" stop-color="#090909"/>
          <stop offset="1" stop-color="#1a1a1a"/>
        </linearGradient>
        <radialGradient id="glow" cx="50%" cy="22%" r="62%">
          <stop offset="0" stop-color="#e50914" stop-opacity="0.18"/>
          <stop offset="1" stop-color="#e50914" stop-opacity="0"/>
        </radialGradient>
      </defs>
      <rect width="900" height="1200" fill="url(#bg)"/>
      <rect width="900" height="1200" fill="url(#glow)"/>
      <rect x="64" y="64" width="772" height="1072" rx="36" fill="none" stroke="#e50914" stroke-opacity="0.26" stroke-width="3"/>
      <text x="450" y="520" text-anchor="middle" fill="#e50914" font-family="Arial, Helvetica, sans-serif" font-size="54" font-weight="800" letter-spacing="8">ENCORE</text>
      <text x="450" y="620" text-anchor="middle" fill="#f0ede8" font-family="Arial, Helvetica, sans-serif" font-size="42" font-weight="700">${safeTitle}</text>
      <text x="450" y="700" text-anchor="middle" fill="#b8b0a6" font-family="Arial, Helvetica, sans-serif" font-size="24" letter-spacing="4">TICKETS</text>
    </svg>
  `
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

export function posterImageSrc(src?: string | null, title?: string | null) {
  const value = String(src || '').trim()
  return value || posterFallbackDataUri(title || 'ENCORE')
}

export function handlePosterError(event: Event, title?: string | null) {
  const img = event.target as HTMLImageElement | null
  if (!img || img.dataset.fallbackApplied === 'true') return
  img.dataset.fallbackApplied = 'true'
  img.src = posterFallbackDataUri(title || img.alt || 'ENCORE')
}

export function formatScheduleDay(value: string, locale: string) {
  const date = new Date(value)
  const dateLocale = locale === 'zh' ? 'zh-CN' : 'en-US'
  return date.toLocaleDateString(dateLocale, {
    month: 'short',
    day: '2-digit',
    weekday: 'short'
  })
}

export function formatScheduleTime(value: string, locale: string) {
  const date = new Date(value)
  const dateLocale = locale === 'zh' ? 'zh-CN' : 'en-US'
  return date.toLocaleTimeString(dateLocale, {
    hour: '2-digit',
    minute: '2-digit'
  })
}
