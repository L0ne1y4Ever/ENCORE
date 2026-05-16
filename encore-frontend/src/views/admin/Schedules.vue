<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { mockSchedules, mockShows } from '../../mock/shows'
import type { Schedule } from '../../mock/shows'

const { t } = useI18n()

interface ScheduleVM extends Schedule {
  showTitle: string
  reservedCount: number
}

const tableData = ref<ScheduleVM[]>([])

onMounted(() => {
  const data: ScheduleVM[] = []
  for (const showId in mockSchedules) {
    const show = mockShows.find(s => s.id === showId)
    const schs = mockSchedules[showId]
    schs.forEach(sch => {
      data.push({
        ...sch,
        showTitle: show?.title || 'Unknown Show',
        // Mock a reserved count based on status
        reservedCount: sch.status === 'PREPARING' || sch.status === 'COMING_SOON' ? Math.floor(Math.random() * 10000) : 0
      })
    })
  }
  tableData.value = data
})

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleString()
}

const toggleStatus = (row: ScheduleVM) => {
  if (row.status === 'COMING_SOON') row.status = 'PREPARING'
  else if (row.status === 'PREPARING') row.status = 'ON_SALE'
  else if (row.status === 'ON_SALE') row.status = 'SOLD_OUT'
  else row.status = 'COMING_SOON'
}
</script>

<template>
  <div class="schedules-page">
    <div class="page-header">
      <h1>{{ t('admin.schedules') }}</h1>
    </div>

    <div class="table-container">
      <el-table :data="tableData" style="width: 100%" :empty-text="'No Data'">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="showTitle" :label="t('admin.shows')" min-width="200" />
        <el-table-column prop="theaterName" label="Theater" width="150" />
        <el-table-column :label="'Time'" min-width="180">
          <template #default="{ row }">
            {{ formatDate(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.reservedCount')" width="150">
          <template #default="{ row }">
            <span v-if="row.reservedCount > 0" class="reserved-count">{{ row.reservedCount }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.scheduleStatus')" width="150">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === 'ON_SALE' ? 'success' : (row.status === 'SOLD_OUT' ? 'danger' : 'info')"
              effect="dark"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.action')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="toggleStatus(row)">Toggle</el-button>
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
}
</style>
