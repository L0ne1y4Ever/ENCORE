<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface OrderVM {
  id: string
  userId: string
  showName: string
  amount: string
  status: 'PAID' | 'REFUNDED' | 'USED'
  date: string
}

const tableData = ref<OrderVM[]>([
  { id: 'ORD-10001', userId: 'u-101', showName: 'THE PHANTOM OF THE OPERA', amount: '$150', status: 'PAID', date: '2026-05-14 10:30' },
  { id: 'ORD-10002', userId: 'u-102', showName: 'SWAN LAKE', amount: '$80', status: 'USED', date: '2026-05-13 14:20' },
  { id: 'ORD-10003', userId: 'u-103', showName: 'HAMILTON', amount: '$300', status: 'REFUNDED', date: '2026-05-12 09:15' },
  { id: 'ORD-10004', userId: 'u-101', showName: 'DUNE: PART TWO', amount: '$25', status: 'PAID', date: '2026-05-14 11:00' },
])

const handleRefund = (row: OrderVM) => {
  if (confirm(`Refund order ${row.id}?`)) {
    row.status = 'REFUNDED'
  }
}

const handleCheckin = (row: OrderVM) => {
  row.status = 'USED'
}
</script>

<template>
  <div class="orders-page">
    <div class="page-header">
      <h1>{{ t('admin.orders') }}</h1>
    </div>

    <div class="table-container">
      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="id" :label="t('admin.orderId')" width="120" />
        <el-table-column prop="userId" label="User ID" width="100" />
        <el-table-column prop="showName" :label="t('admin.shows')" min-width="200" />
        <el-table-column prop="amount" :label="t('admin.amount')" width="100" />
        <el-table-column prop="date" label="Date" width="160" />
        <el-table-column :label="t('admin.scheduleStatus')" width="120">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === 'PAID' ? 'success' : (row.status === 'REFUNDED' ? 'info' : 'warning')"
              effect="plain"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.action')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button 
              link 
              type="danger" 
              :disabled="row.status !== 'PAID'"
              @click="handleRefund(row)"
            >
              {{ t('admin.refund') }}
            </el-button>
            <el-button 
              link 
              type="primary" 
              :disabled="row.status !== 'PAID'"
              @click="handleCheckin(row)"
            >
              {{ t('admin.checkin') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
.orders-page {
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
