<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAdminSchedules, updateAdminScheduleStatus } from '../../api/admin'
import type { AdminSchedule, ScheduleStatus } from '../../api/admin'

const { t } = useI18n()

const tableData = ref<AdminSchedule[]>([])
const loading = ref(false)
const updatingId = ref('')

const loadSchedules = async () => {
  loading.value = true
  try {
    tableData.value = await getAdminSchedules()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadSchedules)

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleString()
}

const statusFlow: ScheduleStatus[] = ['COMING_SOON', 'PREPARING', 'ON_SALE', 'SOLD_OUT']

const nextStatus = (status: ScheduleStatus): ScheduleStatus => {
  const currentIndex = statusFlow.indexOf(status)
  if (currentIndex === -1 || currentIndex === statusFlow.length - 1) return 'COMING_SOON'
  return statusFlow[currentIndex + 1]
}

const statusTagType = (status: ScheduleStatus) => {
  if (status === 'ON_SALE') return 'success'
  if (status === 'SOLD_OUT' || status === 'CANCELLED') return 'danger'
  if (status === 'PREPARING') return 'warning'
  return 'info'
}

const toggleStatus = async (row: AdminSchedule) => {
  const status = nextStatus(row.status)
  updatingId.value = row.id
  try {
    const updated = await updateAdminScheduleStatus(row.id, status)
    const index = tableData.value.findIndex(item => item.id === row.id)
    if (index >= 0) tableData.value[index] = updated
    ElMessage.success(t('admin.statusUpdated'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    updatingId.value = ''
  }
}
</script>

<template>
  <div class="schedules-page">
    <div class="page-header">
      <h1>{{ t('admin.schedules') }}</h1>
      <el-button type="primary" plain :loading="loading" @click="loadSchedules">
        {{ t('admin.refresh') }}
      </el-button>
    </div>

    <div class="table-container">
      <el-table :data="tableData" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="showTitle" :label="t('admin.shows')" min-width="200" />
        <el-table-column prop="theaterName" :label="t('admin.theater')" width="150" />
        <el-table-column :label="t('admin.time')" min-width="180">
          <template #default="{ row }">
            {{ formatDate(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.totalSeats')" width="120">
          <template #default="{ row }">
            {{ row.totalSeats }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.availableSeats')" width="120">
          <template #default="{ row }">
            <span class="available-count">{{ row.availableSeats }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.soldSeats')" width="120">
          <template #default="{ row }">
            <span class="sold-count">{{ row.soldSeats }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.checkedInTickets')" width="120">
          <template #default="{ row }">
            {{ row.checkedInTickets }} / {{ row.paidTickets }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.scheduleStatus')" width="150">
          <template #default="{ row }">
            <el-tag
              :type="statusTagType(row.status)"
              effect="dark"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.action')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="updatingId === row.id"
              @click="toggleStatus(row)"
            >
              {{ t('admin.statusNext') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
.schedules-page {
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

.table-container {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  padding: var(--spacing-4);
  border-radius: var(--radius-sm);

  /* Deep Element Plus Overrides for Admin Layout */
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
      text-transform: uppercase;
      letter-spacing: 0.05em;
      border-bottom: 1px solid var(--color-border-strong);
    }

    td.el-table__cell {
      border-bottom: 1px solid var(--color-border);
      font-family: var(--font-family-sans);
    }
  }

  .reserved-count {
    color: var(--color-accent);
    font-weight: bold;
  }

  .available-count {
    color: var(--color-success);
    font-weight: 700;
  }

  .sold-count {
    color: var(--color-warning);
    font-weight: 700;
  }
}
</style>
