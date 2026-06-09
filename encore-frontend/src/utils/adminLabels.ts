import type { ComposerTranslation } from 'vue-i18n'

const normalize = (value?: string | null) => String(value || '').trim()

export const adminCategoryLabel = (t: ComposerTranslation, category?: string | null) => {
  const raw = normalize(category)
  const key = raw.toUpperCase()
  const labels: Record<string, string> = {
    CONCERT: t('admin.categoryConcert'),
    LIVE: t('admin.categoryConcert'),
    MOVIE: t('admin.categoryMovie'),
    CINEMA: t('admin.categoryMovie'),
    MUSICAL: t('admin.categoryMusical'),
    DRAMA: t('admin.categoryDrama'),
    PLAY: t('admin.categoryDrama'),
    BALLET: t('admin.categoryBallet'),
    UNKNOWN: t('admin.categoryUnknown')
  }
  return labels[key] || raw || t('admin.categoryUnknown')
}

export const adminPublishStatusLabel = (t: ComposerTranslation, status?: string | null) => {
  const key = normalize(status).toLowerCase()
  if (key === 'published') return t('admin.showStatus.published')
  if (key === 'draft') return t('admin.showStatus.draft')
  if (key === 'archived') return t('admin.showStatus.archived')
  return status || '-'
}

export const adminAuditModuleLabel = (t: ComposerTranslation, module?: string | null) => {
  const key = normalize(module).toLowerCase()
  return key ? t(`admin.auditModule.${key}`, module || key) : '-'
}

export const adminAuditActionLabel = (t: ComposerTranslation, action?: string | null) => {
  const key = normalize(action).toLowerCase()
  return key ? t(`admin.auditActionMap.${key}`, action || key) : '-'
}
