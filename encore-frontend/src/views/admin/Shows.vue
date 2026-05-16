<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveAdminShow,
  createAdminShow,
  getAdminShows,
  updateAdminShow,
  updateAdminShowStatus
} from '../../api/admin'
import type { AdminShow, AdminShowPayload, AdminShowStatus } from '../../api/admin'

const { t } = useI18n()

type DialogMode = 'create' | 'edit'

interface ShowForm {
  id: string
  title: string
  subtitle: string
  coverUrl: string
  description: string
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

const emptyForm = (): ShowForm => ({
  id: '',
  title: '',
  subtitle: '',
  coverUrl: '',
  description: '',
  duration: 120,
  category: 'Movie',
  tagsText: '',
  status: 'DRAFT',
  sortOrder: null
})

const form = reactive<ShowForm>(emptyForm())

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
  const key = category.toLowerCase()
  return t(`home.${key}`)
}

const statusLabel = (status: AdminShowStatus) => {
  return t(`admin.showStatus.${status.toLowerCase()}`)
}

const statusTagType = (status: AdminShowStatus) => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
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
      <h1>{{ t('admin.showsManagement') }}</h1>
      <div class="header-actions">
        <el-button type="primary" plain :loading="loading" @click="loadShows">
          {{ t('admin.refresh') }}
        </el-button>
        <el-button type="primary" @click="openCreate">
          {{ t('admin.addNewShow') }}
        </el-button>
      </div>
    </div>

    <div class="table-container">
      <el-table :data="tableData" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column prop="id" label="ID" width="125" />
        <el-table-column :label="t('admin.title')" min-width="240">
          <template #default="{ row }">
            <div class="show-title">{{ row.title }}</div>
            <div class="show-subtitle">{{ row.subtitle }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.category')" width="120">
          <template #default="{ row }">
            {{ categoryLabel(row.category) }}
          </template>
        </el-table-column>
        <el-table-column prop="duration" :label="t('admin.durationMinutes')" width="130" />
        <el-table-column :label="t('admin.showStatusLabel')" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scheduleCount" :label="t('admin.scheduleCount')" width="120" />
        <el-table-column prop="sortOrder" :label="t('admin.sortOrder')" width="110" />
        <el-table-column :label="t('admin.actions')" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="operatingId === row.id" @click="openEdit(row)">
              {{ t('admin.edit') }}
            </el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
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
              <el-option label="DRAFT" value="DRAFT" />
              <el-option label="PUBLISHED" value="PUBLISHED" />
              <el-option label="ARCHIVED" value="ARCHIVED" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('admin.description')" required class="span-2">
            <el-input v-model="form.description" type="textarea" :rows="5" maxlength="2000" show-word-limit />
          </el-form-item>
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
  margin-bottom: var(--spacing-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);

  h1 {
    font-family: var(--font-family-display);
    font-size: 32px;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.table-container {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  padding: var(--spacing-4);
  border-radius: var(--radius-sm);

  :deep(.el-table) {
    background-color: transparent;
    --el-table-border-color: var(--color-border);
    --el-table-header-bg-color: rgba(255, 255, 255, 0.02);
    --el-table-header-text-color: var(--color-text-secondary);
    --el-table-text-color: var(--color-text-primary);
    --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);

    th.el-table__cell {
      font-family: var(--font-family-sans);
      font-weight: 600;
      border-bottom: 1px solid var(--color-border-strong);
    }

    td.el-table__cell {
      border-bottom: 1px solid var(--color-border);
      font-family: var(--font-family-sans);
    }
  }
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

.show-form {
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
