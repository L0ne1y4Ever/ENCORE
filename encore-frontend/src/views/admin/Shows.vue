<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  archiveAdminShow,
  createAdminShow,
  getAdminShows,
  updateAdminShow,
  updateAdminShowStatus
} from '../../api/admin'
import type { AdminShow, AdminShowPayload, AdminShowStatus } from '../../api/admin'
import { adminCategoryLabel } from '../../utils/adminLabels'

const { t } = useI18n()

type DialogMode = 'create' | 'edit'

interface ShowForm {
  id: string
  title: string
  subtitle: string
  coverUrl: string
  description: string
  intro: string
  castMembers: string
  creativeTeam: string
  fullSynopsis: string
  duration: number
  category: string
  tagsText: string
  status: AdminShowStatus
  sortOrder: number | null
}

const categoryOptions = ['Movie', 'Musical', 'Play', 'Concert', 'Ballet']

const tableData = ref<AdminShow[]>([])
const loading = ref(false)
const saving = ref(false)
const operatingId = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const searchKeyword = ref('')
const categoryFilter = ref('')
const statusFilter = ref('')
const sortKey = ref<'sortOrder' | 'category' | 'title' | 'scheduleDesc'>('sortOrder')

const emptyForm = (): ShowForm => ({
  id: '',
  title: '',
  subtitle: '',
  coverUrl: '',
  description: '',
  intro: '',
  castMembers: '',
  creativeTeam: '',
  fullSynopsis: '',
  duration: 120,
  category: 'Movie',
  tagsText: '',
  status: 'DRAFT',
  sortOrder: null
})

const form = reactive<ShowForm>(emptyForm())

const filteredShows = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const rows = tableData.value.filter(row => {
    if (keyword) {
      const haystack = [
        row.title,
        row.subtitle,
        row.category,
        row.tags.join(' ')
      ].join(' ').toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    if (categoryFilter.value && row.category !== categoryFilter.value) return false
    if (statusFilter.value && row.status !== statusFilter.value) return false
    return true
  })
  return [...rows].sort((left, right) => {
    if (sortKey.value === 'category') return `${left.category}-${left.sortOrder}`.localeCompare(`${right.category}-${right.sortOrder}`)
    if (sortKey.value === 'title') return left.title.localeCompare(right.title)
    if (sortKey.value === 'scheduleDesc') return right.scheduleCount - left.scheduleCount || left.sortOrder - right.sortOrder
    return left.sortOrder - right.sortOrder || left.title.localeCompare(right.title)
  })
})

const showMetrics = computed(() => {
  const rows = tableData.value
  return {
    total: rows.length,
    published: rows.filter(row => row.status === 'PUBLISHED').length,
    draft: rows.filter(row => row.status === 'DRAFT').length,
    archived: rows.filter(row => row.status === 'ARCHIVED').length,
    scheduled: rows.filter(row => row.scheduleCount > 0).length
  }
})

const resetForm = () => {
  Object.assign(form, emptyForm())
}

const loadShows = async () => {
  loading.value = true
  try {
    tableData.value = await getAdminShows()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadShows)

const categoryLabel = (category: string) => {
  return adminCategoryLabel(t, category)
}

const statusLabel = (status: AdminShowStatus) => {
  return t(`admin.showStatus.${status.toLowerCase()}`)
}

const statusTagType = (status: AdminShowStatus) => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

const selectStatusMetric = (status: AdminShowStatus | '') => {
  statusFilter.value = status
}

const replaceShow = (updated: AdminShow) => {
  const index = tableData.value.findIndex(item => item.id === updated.id)
  if (index >= 0) {
    tableData.value[index] = updated
  } else {
    tableData.value.unshift(updated)
  }
}

const openCreate = () => {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

const openEdit = (row: AdminShow) => {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    subtitle: row.subtitle,
    coverUrl: row.coverUrl,
    description: row.description,
    intro: row.intro || '',
    castMembers: row.castMembers || '',
    creativeTeam: row.creativeTeam || '',
    fullSynopsis: row.fullSynopsis || '',
    duration: row.duration,
    category: row.category,
    tagsText: row.tags.join(', '),
    status: row.status,
    sortOrder: row.sortOrder
  })
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

const validateForm = () => {
  if (!form.title.trim() || !form.subtitle.trim() || !form.coverUrl.trim() || !form.description.trim() || !form.category.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return false
  }
  if (!Number.isFinite(form.duration) || form.duration < 1) {
    ElMessage.error(t('admin.durationInvalid'))
    return false
  }
  return true
}

const buildPayload = (): AdminShowPayload => {
  const payload: AdminShowPayload = {
    title: form.title.trim(),
    subtitle: form.subtitle.trim(),
    coverUrl: form.coverUrl.trim(),
    description: form.description.trim(),
    intro: form.intro.trim() || null,
    castMembers: form.castMembers.trim() || null,
    creativeTeam: form.creativeTeam.trim() || null,
    fullSynopsis: form.fullSynopsis.trim() || null,
    duration: form.duration,
    category: form.category,
    tags: form.tagsText
      .split(',')
      .map(tag => tag.trim())
      .filter(Boolean),
    status: form.status
  }
  if (form.sortOrder !== null) {
    payload.sortOrder = form.sortOrder
  }
  return payload
}

const submitForm = async () => {
  if (!validateForm()) return

  saving.value = true
  try {
    const payload = buildPayload()
    const updated = dialogMode.value === 'create'
      ? await createAdminShow(payload)
      : await updateAdminShow(form.id, payload)
    replaceShow(updated)
    dialogVisible.value = false
    ElMessage.success(t('admin.showSaved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}

const setShowStatus = async (row: AdminShow, status: AdminShowStatus) => {
  operatingId.value = row.id
  try {
    replaceShow(await updateAdminShowStatus(row.id, status))
    ElMessage.success(status === 'PUBLISHED' ? t('admin.showPublished') : t('admin.showUnpublished'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}

const handleDelete = async (row: AdminShow) => {
  try {
    await ElMessageBox.confirm(t('admin.deleteShowConfirm', { title: row.title }), t('admin.delete'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }

  operatingId.value = row.id
  try {
    replaceShow(await archiveAdminShow(row.id))
    ElMessage.success(t('admin.showDeleted'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}
</script>

<template>
  <div class="shows-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.showsManagement') }}</h1>
        <p>{{ t('admin.showsManagementSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain :icon="Refresh" :loading="loading" @click="loadShows">
          {{ t('admin.refresh') }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          {{ t('admin.addNewShow') }}
        </el-button>
      </div>
    </div>

    <div class="metric-strip">
      <button type="button" class="metric-card" :class="{ active: !statusFilter }" @click="selectStatusMetric('')">
        <span>{{ t('admin.allShows') }}</span>
        <strong>{{ showMetrics.total }}</strong>
      </button>
      <button type="button" class="metric-card" :class="{ active: statusFilter === 'PUBLISHED' }" @click="selectStatusMetric('PUBLISHED')">
        <span>{{ statusLabel('PUBLISHED') }}</span>
        <strong>{{ showMetrics.published }}</strong>
      </button>
      <button type="button" class="metric-card" :class="{ active: statusFilter === 'DRAFT' }" @click="selectStatusMetric('DRAFT')">
        <span>{{ statusLabel('DRAFT') }}</span>
        <strong>{{ showMetrics.draft }}</strong>
      </button>
      <button type="button" class="metric-card" :class="{ active: statusFilter === 'ARCHIVED' }" @click="selectStatusMetric('ARCHIVED')">
        <span>{{ statusLabel('ARCHIVED') }}</span>
        <strong>{{ showMetrics.archived }}</strong>
      </button>
      <div class="metric-card passive">
        <span>{{ t('admin.scheduledShows') }}</span>
        <strong>{{ showMetrics.scheduled }}</strong>
      </div>
    </div>

    <div class="table-container">
      <div class="table-toolbar">
        <el-input
          v-model="searchKeyword"
          class="search-input"
          clearable
          :prefix-icon="Search"
          :placeholder="t('admin.searchShows')"
        />
        <div class="toolbar-filters">
          <el-select v-model="categoryFilter" class="compact-filter" clearable :placeholder="t('admin.allCategories')">
            <el-option v-for="category in categoryOptions" :key="category" :label="categoryLabel(category)" :value="category" />
          </el-select>
          <el-select v-model="statusFilter" class="compact-filter" clearable :placeholder="t('admin.allStatuses')">
            <el-option value="DRAFT" :label="statusLabel('DRAFT')" />
            <el-option value="PUBLISHED" :label="statusLabel('PUBLISHED')" />
            <el-option value="ARCHIVED" :label="statusLabel('ARCHIVED')" />
          </el-select>
          <el-select v-model="sortKey" class="sort-filter" :placeholder="t('admin.sortBy')">
            <el-option value="sortOrder" :label="t('admin.sortOrder')" />
            <el-option value="category" :label="t('admin.sortCategory')" />
            <el-option value="title" :label="t('admin.sortTitle')" />
            <el-option value="scheduleDesc" :label="t('admin.sortScheduleDesc')" />
          </el-select>
        </div>
      </div>
      <el-table :data="filteredShows" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column :label="t('admin.title')" min-width="310">
          <template #default="{ row }">
            <div class="show-cell">
              <img class="cover-thumb" :src="row.coverUrl" :alt="row.title" loading="lazy" />
              <div class="show-copy">
                <div class="show-title">{{ row.title }}</div>
                <div class="show-subtitle">{{ row.subtitle }}</div>
                <div class="show-tags">
                  <span v-for="tag in row.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.category')" width="120">
          <template #default="{ row }">
            {{ categoryLabel(row.category) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.durationMinutes')" width="120">
          <template #default="{ row }">{{ row.duration }} {{ t('detail.minutes') }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.showStatusLabel')" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.scheduleCount')" width="120">
          <template #default="{ row }">
            <strong class="numeric-cell">{{ row.scheduleCount }}</strong>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.sortOrder')" width="100">
          <template #default="{ row }">{{ row.sortOrder }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.actions')" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" :disabled="operatingId === row.id" @click="openEdit(row)">
              {{ t('admin.edit') }}
            </el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              :icon="Upload"
              :loading="operatingId === row.id"
              @click="setShowStatus(row, 'PUBLISHED')"
            >
              {{ t('admin.publish') }}
            </el-button>
            <el-button
              v-else
              link
              type="warning"
              :loading="operatingId === row.id"
              @click="setShowStatus(row, 'DRAFT')"
            >
              {{ t('admin.unpublish') }}
            </el-button>
            <el-button
              link
              type="danger"
              :icon="Delete"
              :loading="operatingId === row.id"
              :disabled="row.status === 'ARCHIVED'"
              @click="handleDelete(row)"
            >
              {{ t('admin.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('admin.createShow') : t('admin.editShow')"
      width="680px"
      class="show-dialog"
      @closed="resetForm"
    >
      <el-form label-position="top" class="show-form">
        <div class="form-section">
          <h3>{{ t('admin.showBasicInfo') }}</h3>
          <div class="form-grid">
            <el-form-item :label="t('admin.title')" required>
              <el-input v-model="form.title" maxlength="128" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('admin.subtitle')" required>
              <el-input v-model="form.subtitle" maxlength="128" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('admin.category')" required>
              <el-select v-model="form.category" class="full-control">
                <el-option
                  v-for="category in categoryOptions"
                  :key="category"
                  :label="categoryLabel(category)"
                  :value="category"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('admin.durationMinutes')" required>
              <el-input-number v-model="form.duration" :min="1" :step="5" class="full-control" />
            </el-form-item>
            <el-form-item :label="t('admin.coverUrl')" required class="span-2">
              <el-input v-model="form.coverUrl" maxlength="512" />
            </el-form-item>
            <el-form-item :label="t('admin.tags')" class="span-2">
              <el-input v-model="form.tagsText" :placeholder="t('admin.tagsPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('admin.sortOrder')">
              <el-input-number v-model="form.sortOrder" :step="10" class="full-control" />
            </el-form-item>
            <el-form-item :label="t('admin.showStatusLabel')">
              <el-select v-model="form.status" class="full-control">
                <el-option :label="statusLabel('DRAFT')" value="DRAFT" />
                <el-option :label="statusLabel('PUBLISHED')" value="PUBLISHED" />
                <el-option :label="statusLabel('ARCHIVED')" value="ARCHIVED" />
              </el-select>
            </el-form-item>
          </div>
        </div>
        <div class="form-section">
          <h3>{{ t('admin.showContentInfo') }}</h3>
          <div class="form-grid">
            <el-form-item :label="t('admin.description')" required class="span-2">
              <el-input v-model="form.description" type="textarea" :rows="5" maxlength="2000" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('admin.intro')" class="span-2">
              <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="2000" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('admin.castMembers')" class="span-2">
              <el-input v-model="form.castMembers" type="textarea" :rows="3" maxlength="2000" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('admin.creativeTeam')" class="span-2">
              <el-input v-model="form.creativeTeam" type="textarea" :rows="3" maxlength="2000" show-word-limit />
            </el-form-item>
            <el-form-item :label="t('admin.fullSynopsis')" class="span-2">
              <el-input v-model="form.fullSynopsis" type="textarea" :rows="5" maxlength="4000" show-word-limit />
            </el-form-item>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.shows-page {
  width: 100%;
}

.page-header {
  margin-bottom: var(--spacing-4);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-4);

  h1 {
    font-family: var(--font-family-display);
    font-size: 32px;
    line-height: 1.2;
  }

  p {
    margin-top: var(--spacing-2);
    color: var(--color-text-secondary);
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.metric-strip {
  margin-bottom: var(--spacing-3);
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 0;
  padding: 8px 0;
  border-top: 1px solid rgba(240, 237, 232, 0.08);
  border-bottom: 1px solid rgba(240, 237, 232, 0.08);
  background: linear-gradient(180deg, rgba(240, 237, 232, 0.02), rgba(240, 237, 232, 0.004));
}

.metric-card {
  position: relative;
  min-width: 0;
  min-height: 64px;
  border: 0;
  border-right: 1px solid rgba(240, 237, 232, 0.07);
  border-radius: 0;
  background: transparent;
  padding: 10px 20px;
  display: grid;
  align-content: center;
  gap: 6px;
  color: var(--color-text-primary);
  cursor: pointer;
  text-align: left;
  transition: background-color 160ms ease, color 160ms ease;

  &:last-child {
    border-right: 0;
  }

  span {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 12px;
    line-height: 1.2;
  }

  strong {
    font-family: var(--font-family-sans);
    font-size: 22px;
    font-variant-numeric: tabular-nums;
    line-height: 1.1;
  }

  &:hover {
    background: rgba(240, 237, 232, 0.035);
  }

  &:focus-visible {
    outline: 1px solid rgba(200, 149, 90, 0.48);
    outline-offset: -2px;
  }

  &.active::after {
    content: '';
    position: absolute;
    left: 20px;
    right: 20px;
    bottom: -9px;
    height: 2px;
    border-radius: 2px;
    background: var(--color-accent);
  }

  &.active span,
  &.active strong {
    color: var(--color-text-primary);
  }

  &.active strong {
    color: #f0c078;
  }

  &.passive {
    cursor: default;

    &:hover {
      background: transparent;
    }
  }
}

.compact-filter {
  width: 132px;
}

.sort-filter {
  width: 168px;
}

.table-container {
  background-color: transparent;
  border: 0;
  padding: 0;
  border-radius: 0;

  .table-toolbar {
    margin-bottom: 12px;
    padding: 10px 0 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--spacing-3);
    border-bottom: 1px solid rgba(240, 237, 232, 0.08);
  }

  .search-input {
    width: min(360px, 100%);
  }

  .toolbar-filters {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    flex-wrap: wrap;
    gap: var(--spacing-2);
  }

  .table-toolbar :deep(.el-input__wrapper),
  .table-toolbar :deep(.el-select__wrapper) {
    min-height: 34px;
    border-radius: 4px;
    box-shadow: 0 0 0 1px rgba(240, 237, 232, 0.08) inset;
    background: rgba(240, 237, 232, 0.035);
  }

  .table-toolbar :deep(.el-input__wrapper.is-focus),
  .table-toolbar :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px rgba(200, 149, 90, 0.46) inset;
  }

  :deep(.el-table) {
    background-color: transparent;
    --el-table-border-color: var(--color-border);
    --el-table-header-bg-color: rgba(240, 237, 232, 0.035);
    --el-table-header-text-color: var(--color-text-secondary);
    --el-table-text-color: var(--color-text-primary);
    --el-table-row-hover-bg-color: rgba(240, 237, 232, 0.035);
    border: 1px solid rgba(240, 237, 232, 0.09);
    border-radius: 6px;
    overflow: hidden;

    th.el-table__cell {
      font-family: var(--font-family-sans);
      font-weight: 600;
      border-bottom: 1px solid rgba(240, 237, 232, 0.1);
    }

    td.el-table__cell {
      border-bottom: 1px solid rgba(240, 237, 232, 0.07);
      font-family: var(--font-family-sans);
    }
  }
}

.show-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.cover-thumb {
  width: 52px;
  height: 70px;
  flex: 0 0 auto;
  border: 1px solid rgba(240, 237, 232, 0.1);
  border-radius: 4px;
  object-fit: cover;
  background: rgba(255, 255, 255, 0.04);
}

.show-copy {
  min-width: 0;
}

.show-title {
  color: var(--color-text-primary);
  font-weight: 600;
  line-height: 1.4;
}

.show-subtitle {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.show-tags {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  span {
    max-width: 88px;
    overflow: hidden;
    border: 1px solid rgba(240, 237, 232, 0.14);
    border-radius: 3px;
    padding: 2px 7px;
    color: var(--color-text-secondary);
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.numeric-cell {
  font-family: var(--font-family-sans);
  font-variant-numeric: tabular-nums;
}

.show-form {
  display: grid;
  gap: var(--spacing-4);

  .form-section {
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    padding: var(--spacing-4);
    background: rgba(255, 255, 255, 0.02);

    h3 {
      margin-bottom: var(--spacing-3);
      font-size: 16px;
    }
  }

  .form-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: var(--spacing-4);
  }

  .span-2 {
    grid-column: span 2;
  }

  .full-control {
    width: 100%;
  }
}

:deep(.show-dialog) {
  max-width: calc(100vw - 32px);
}

@media (max-width: 720px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-container {
    .table-toolbar {
      align-items: stretch;
      flex-direction: column;
    }

    .search-input,
    .compact-filter,
    .sort-filter {
      width: 100%;
    }
  }

  .show-form {
    .form-grid {
      grid-template-columns: 1fr;
    }

    .span-2 {
      grid-column: auto;
    }
  }
}
</style>
