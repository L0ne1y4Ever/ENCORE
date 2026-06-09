<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAdminAuditLogs } from '../../api/admin'
import type { AdminOperationLog } from '../../api/admin'
import { adminAuditActionLabel, adminAuditModuleLabel } from '../../utils/adminLabels'
import { formatLocaleDateTime } from '../../utils/date'

const { t, locale } = useI18n()

const logs = ref<AdminOperationLog[]>([])
const loading = ref(false)
const filters = reactive({
  module: '',
  result: '',
  keyword: '',
  limit: 100
})

const moduleOptions = [
  'SHOW',
  'VENUE',
  'HALL',
  'LAYOUT',
  'LAYOUT_SEAT',
  'LAYOUT_SYNC',
  'SCHEDULE',
  'INVENTORY',
  'ORDER',
  'STAFF',
  'ADMIN'
]

const resultOptions = ['SUCCESS', 'FAILED']
const limitOptions = [50, 100, 200, 500]

const loadLogs = async () => {
  loading.value = true
  try {
    logs.value = await getAdminAuditLogs({
      module: filters.module || undefined,
      result: filters.result || undefined,
      keyword: filters.keyword.trim() || undefined,
      limit: filters.limit
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.module = ''
  filters.result = ''
  filters.keyword = ''
  filters.limit = 100
  void loadLogs()
}

onMounted(loadLogs)

const formatDate = (dateStr: string | null) => {
  return formatLocaleDateTime(dateStr, locale.value)
}

const actorLabel = (row: AdminOperationLog) => {
  const name = row.actorUsername || row.actorId || '-'
  return row.actorRole ? `${name} · ${roleLabel(row.actorRole)}` : name
}

const roleLabel = (role?: string | null) => {
  const key = String(role || '').toLowerCase()
  if (key === 'sysadmin') return t('role.sysadmin')
  if (key === 'admin') return t('role.admin')
  if (key === 'checker') return t('role.checker')
  if (key === 'user') return t('role.user')
  return role || '-'
}

const targetLabel = (row: AdminOperationLog) => {
  if (row.targetId && row.targetLabel) return `${row.targetId} · ${row.targetLabel}`
  return row.targetId || row.targetLabel || '-'
}

const resultType = (result: string) => result === 'SUCCESS' ? 'success' : 'danger'

const moduleLabel = (module?: string | null) => adminAuditModuleLabel(t, module)

const actionLabel = (action?: string | null) => adminAuditActionLabel(t, action)

const visibleCount = computed(() => logs.value.length)
</script>

<template>
  <div class="audit-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.auditLogs') }}</h1>
        <p>{{ t('admin.auditLogsSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-select v-model="filters.module" class="compact-filter" clearable :placeholder="t('admin.allModules')">
          <el-option v-for="module in moduleOptions" :key="module" :label="moduleLabel(module)" :value="module" />
        </el-select>
        <el-select v-model="filters.result" class="compact-filter" clearable :placeholder="t('admin.allResults')">
          <el-option v-for="result in resultOptions" :key="result" :label="t(`admin.auditResult.${result.toLowerCase()}`)" :value="result" />
        </el-select>
        <el-select v-model="filters.limit" class="limit-filter" :placeholder="t('admin.limit')">
          <el-option v-for="limit in limitOptions" :key="limit" :label="String(limit)" :value="limit" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          class="keyword-filter"
          clearable
          :placeholder="t('admin.auditKeyword')"
          @keyup.enter="loadLogs"
        />
        <el-button plain @click="resetFilters">{{ t('admin.resetFilters') }}</el-button>
        <el-button type="primary" plain :loading="loading" @click="loadLogs">
          {{ t('admin.refresh') }}
        </el-button>
      </div>
    </div>

    <div class="summary-strip">
      <div class="metric-card passive">
        <span>{{ t('admin.auditVisible') }}</span>
        <strong>{{ visibleCount }}</strong>
      </div>
      <div class="metric-card passive">
        <span>{{ t('admin.auditSuccess') }}</span>
        <strong>{{ logs.filter(item => item.result === 'SUCCESS').length }}</strong>
      </div>
      <div class="metric-card passive">
        <span>{{ t('admin.auditFailed') }}</span>
        <strong>{{ logs.filter(item => item.result === 'FAILED').length }}</strong>
      </div>
    </div>

    <div class="table-container">
      <el-table :data="logs" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column :label="t('admin.createdAt')" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.actor')" min-width="150">
          <template #default="{ row }">{{ actorLabel(row) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.module')" width="130">
          <template #default="{ row }">{{ moduleLabel(row.module) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.auditAction')" width="150">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.auditTarget')" min-width="180">
          <template #default="{ row }">{{ targetLabel(row) }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.result')" width="110">
          <template #default="{ row }">
            <el-tag :type="resultType(row.result)" effect="plain">
              {{ t(`admin.auditResult.${row.result.toLowerCase()}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" :label="t('admin.detail')" min-width="260" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
.audit-page {
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
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--spacing-3);
}

.compact-filter {
  width: 142px;
}

.limit-filter {
  width: 96px;
}

.keyword-filter {
  width: 220px;
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
</style>
