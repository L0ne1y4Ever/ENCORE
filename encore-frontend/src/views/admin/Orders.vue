<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { forceCheckInAdminOrder, getAdminOrders, refundAdminOrder } from '../../api/admin'
import type { AdminOrder } from '../../api/admin'

const { t } = useI18n()

const tableData = ref<AdminOrder[]>([])
const loading = ref(false)
const operatingId = ref('')
const statusFilter = ref('')
const sortKey = ref<'createdDesc' | 'createdAsc' | 'amountDesc' | 'showName'>('createdDesc')

const orderStatusOptions = ['PENDING_PAYMENT', 'PAID', 'CHECKED_IN', 'EXPIRED', 'CANCELLED', 'REFUNDED']

const filteredOrders = computed(() => {
  const rows = tableData.value.filter(row => !statusFilter.value || displayStatus(row) === statusFilter.value)
  return [...rows].sort((left, right) => {
    if (sortKey.value === 'createdAsc') return left.createdAt.localeCompare(right.createdAt)
    if (sortKey.value === 'amountDesc') return Number(right.totalAmount) - Number(left.totalAmount)
    if (sortKey.value === 'showName') return left.showName.localeCompare(right.showName) || right.createdAt.localeCompare(left.createdAt)
    return right.createdAt.localeCompare(left.createdAt)
  })
})

const orderMetrics = computed(() => {
  const rows = tableData.value
  const checkedIn = rows.filter(row => displayStatus(row) === 'CHECKED_IN').length
  return [
    { status: '', label: t('admin.allOrders'), value: rows.length },
    { status: 'PENDING_PAYMENT', label: t('profile.orderStatus.pending_payment'), value: rows.filter(row => row.status === 'PENDING_PAYMENT').length },
    { status: 'PAID', label: t('profile.orderStatus.paid'), value: rows.filter(row => displayStatus(row) === 'PAID').length },
    { status: 'CHECKED_IN', label: t('admin.checkedInTickets'), value: checkedIn },
    { status: 'REFUNDED', label: t('profile.orderStatus.refunded'), value: rows.filter(row => row.status === 'REFUNDED').length }
  ]
})

const loadOrders = async () => {
  loading.value = true
  try {
    tableData.value = await getAdminOrders()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadOrders)

const formatDate = (dateStr: string | null) => {
  return dateStr ? new Date(dateStr).toLocaleString() : '-'
}

const formatAmount = (amount: number | string) => {
  return `$${Number(amount).toFixed(2)}`
}

const displayStatus = (row: AdminOrder) => {
  if (row.status === 'PAID' && row.ticketCount > 0 && row.checkedInCount >= row.ticketCount) {
    return 'CHECKED_IN'
  }
  return row.status
}

const statusTagType = (row: AdminOrder) => {
  const status = displayStatus(row)
  if (status === 'PAID') return 'success'
  if (status === 'REFUNDED' || status === 'EXPIRED') return 'info'
  if (status === 'CHECKED_IN') return 'warning'
  return 'danger'
}

const replaceOrder = (updated: AdminOrder) => {
  const index = tableData.value.findIndex(item => item.id === updated.id)
  if (index >= 0) tableData.value[index] = updated
}

const handleRefund = async (row: AdminOrder) => {
  try {
    await ElMessageBox.confirm(t('admin.refundConfirm', { id: row.id }), t('admin.refund'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }
  operatingId.value = row.id
  try {
    replaceOrder(await refundAdminOrder(row.id))
    ElMessage.success(t('admin.refundSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}

const handleCheckin = async (row: AdminOrder) => {
  operatingId.value = row.id
  try {
    replaceOrder(await forceCheckInAdminOrder(row.id))
    ElMessage.success(t('admin.checkinSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}
</script>

<template>
  <div class="orders-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.orders') }}</h1>
        <p>{{ t('admin.ordersSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-select v-model="statusFilter" class="compact-filter" clearable :placeholder="t('admin.allStatuses')">
          <el-option v-for="status in orderStatusOptions" :key="status" :label="status" :value="status" />
        </el-select>
        <el-select v-model="sortKey" class="sort-filter" :placeholder="t('admin.sortBy')">
          <el-option value="createdDesc" :label="t('admin.sortCreatedDesc')" />
          <el-option value="createdAsc" :label="t('admin.sortCreatedAsc')" />
          <el-option value="amountDesc" :label="t('admin.sortAmountDesc')" />
          <el-option value="showName" :label="t('admin.sortShowName')" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="loadOrders">
          {{ t('admin.refresh') }}
        </el-button>
      </div>
    </div>

    <div class="summary-strip">
      <button
        v-for="item in orderMetrics"
        :key="item.status || 'all'"
        type="button"
        class="metric-card"
        :class="{ active: statusFilter === item.status }"
        @click="statusFilter = item.status"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </button>
    </div>

    <div class="table-container">
      <el-table :data="filteredOrders" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column prop="id" :label="t('admin.orderId')" width="120" />
        <el-table-column prop="username" :label="t('admin.user')" width="120" />
        <el-table-column prop="showName" :label="t('admin.shows')" min-width="200" />
        <el-table-column prop="theaterName" :label="t('admin.theater')" width="140" />
        <el-table-column :label="t('admin.amount')" width="110">
          <template #default="{ row }">
            {{ formatAmount(row.totalAmount) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.time')" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.tickets')" width="120">
          <template #default="{ row }">
            {{ row.checkedInCount }} / {{ row.ticketCount }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.scheduleStatus')" width="120">
          <template #default="{ row }">
            <el-tag
              :type="statusTagType(row)"
              effect="plain"
            >
              {{ displayStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.action')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button 
              link 
              type="danger" 
              :loading="operatingId === row.id"
              :disabled="row.status !== 'PAID' || row.checkedInCount > 0"
              @click="handleRefund(row)"
            >
              {{ t('admin.refund') }}
            </el-button>
            <el-button 
              link 
              type="primary" 
              :loading="operatingId === row.id"
              :disabled="row.status !== 'PAID' || row.checkedInCount >= row.ticketCount"
              @click="handleCheckin(row)"
            >
              {{ t('admin.forceCheckin') }}
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
  flex-wrap: wrap;
  justify-content: flex-end;
}

.compact-filter {
  width: 150px;
}

.sort-filter {
  width: 160px;
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
