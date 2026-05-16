<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAdminSchedule,
  createAdminSchedule,
  getAdminSchedules,
  getAdminShows,
  updateAdminSchedule,
  updateAdminScheduleStatus
} from '../../api/admin'
import type {
  AdminSchedule,
  AdminShow,
  CreateAdminSchedulePayload,
  ScheduleStatus,
  UpdateAdminSchedulePayload
} from '../../api/admin'

const { t } = useI18n()

type DialogMode = 'create' | 'edit'

interface ScheduleForm {
  id: string
  showId: string
  theaterName: string
  startTime: string
  endTime: string
  status: ScheduleStatus
  priceRange: string
  seatRows: number
  seatCols: number
  vipPrice: number
  standardPrice: number
  economyPrice: number
}

const scheduleStatusOptions: ScheduleStatus[] = ['COMING_SOON', 'PREPARING', 'ON_SALE', 'SOLD_OUT', 'CANCELLED']
const scheduleStatusKey: Record<ScheduleStatus, string> = {
  COMING_SOON: 'comingSoon',
  PREPARING: 'preparing',
  ON_SALE: 'onSale',
  SOLD_OUT: 'soldOut',
  CANCELLED: 'cancelled'
}

const tableData = ref<AdminSchedule[]>([])
const shows = ref<AdminShow[]>([])
const loading = ref(false)
const saving = ref(false)
const operatingId = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')

const toDateTimeValue = (date: Date) => {
  const pad = (value: number) => value.toString().padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

const defaultStartTime = () => {
  const date = new Date()
  date.setDate(date.getDate() + 7)
  date.setHours(19, 30, 0, 0)
  return date
}

const emptyForm = (): ScheduleForm => {
  const start = defaultStartTime()
  const end = new Date(start.getTime() + 150 * 60 * 1000)
  return {
    id: '',
    showId: shows.value.find(show => show.status !== 'ARCHIVED')?.id || '',
    theaterName: 'Main Hall',
    startTime: toDateTimeValue(start),
    endTime: toDateTimeValue(end),
    status: 'PREPARING',
    priceRange: '$50 - $150',
    seatRows: 10,
    seatCols: 15,
    vipPrice: 150,
    standardPrice: 100,
    economyPrice: 50
  }
}

const form = reactive<ScheduleForm>(emptyForm())

const schedulableShows = computed(() => shows.value.filter(show => show.status !== 'ARCHIVED'))

const resetForm = () => {
  Object.assign(form, emptyForm())
}

const loadSchedules = async () => {
  loading.value = true
  try {
    const [scheduleRows, showRows] = await Promise.all([getAdminSchedules(), getAdminShows()])
    tableData.value = scheduleRows
    shows.value = showRows
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

const statusLabel = (status: ScheduleStatus) => {
  return t(`admin.scheduleStatusMap.${scheduleStatusKey[status]}`)
}

const statusTagType = (status: ScheduleStatus) => {
  if (status === 'ON_SALE') return 'success'
  if (status === 'SOLD_OUT' || status === 'CANCELLED') return 'danger'
  if (status === 'PREPARING') return 'warning'
  return 'info'
}

const replaceSchedule = (updated: AdminSchedule) => {
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

const openEdit = (row: AdminSchedule) => {
  Object.assign(form, {
    id: row.id,
    showId: row.showId,
    theaterName: row.theaterName,
    startTime: row.startTime,
    endTime: row.endTime,
    status: row.status,
    priceRange: row.priceRange,
    seatRows: 10,
    seatCols: 15,
    vipPrice: 150,
    standardPrice: 100,
    economyPrice: 50
  })
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

const validateForm = () => {
  if (!form.showId || !form.theaterName.trim() || !form.startTime || !form.endTime || !form.priceRange.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return false
  }
  if (new Date(form.startTime).getTime() >= new Date(form.endTime).getTime()) {
    ElMessage.error(t('admin.startBeforeEnd'))
    return false
  }
  if (dialogMode.value === 'create' && (form.seatRows < 1 || form.seatCols < 1 || form.vipPrice <= 0 || form.standardPrice <= 0 || form.economyPrice <= 0)) {
    ElMessage.error(t('admin.seatsInvalid'))
    return false
  }
  return true
}

const buildCreatePayload = (): CreateAdminSchedulePayload => ({
  showId: form.showId,
  theaterName: form.theaterName.trim(),
  startTime: form.startTime,
  endTime: form.endTime,
  status: form.status,
  priceRange: form.priceRange.trim(),
  seatRows: form.seatRows,
  seatCols: form.seatCols,
  vipPrice: form.vipPrice,
  standardPrice: form.standardPrice,
  economyPrice: form.economyPrice
})

const buildUpdatePayload = (): UpdateAdminSchedulePayload => ({
  showId: form.showId,
  theaterName: form.theaterName.trim(),
  startTime: form.startTime,
  endTime: form.endTime,
  status: form.status,
  priceRange: form.priceRange.trim()
})

const submitForm = async () => {
  if (!validateForm()) return

  saving.value = true
  try {
    const updated = dialogMode.value === 'create'
      ? await createAdminSchedule(buildCreatePayload())
      : await updateAdminSchedule(form.id, buildUpdatePayload())
    replaceSchedule(updated)
    dialogVisible.value = false
    ElMessage.success(t('admin.scheduleSaved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}

const setScheduleStatus = async (row: AdminSchedule, status: ScheduleStatus) => {
  operatingId.value = row.id
  try {
    replaceSchedule(await updateAdminScheduleStatus(row.id, status))
    ElMessage.success(t('admin.statusUpdated'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}

const handleCancel = async (row: AdminSchedule) => {
  try {
    await ElMessageBox.confirm(t('admin.cancelScheduleConfirm', { id: row.id }), t('admin.cancelSchedule'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }

  operatingId.value = row.id
  try {
    replaceSchedule(await cancelAdminSchedule(row.id))
    ElMessage.success(t('admin.scheduleCancelled'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}
</script>

<template>
  <div class="schedules-page">
    <div class="page-header">
      <h1>{{ t('admin.schedules') }}</h1>
      <div class="header-actions">
        <el-button type="primary" plain :loading="loading" @click="loadSchedules">
          {{ t('admin.refresh') }}
        </el-button>
        <el-button type="primary" @click="openCreate">
          {{ t('admin.addNewSchedule') }}
        </el-button>
      </div>
    </div>

    <div class="table-container">
      <el-table :data="tableData" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column prop="id" label="ID" width="130" />
        <el-table-column prop="showTitle" :label="t('admin.shows')" min-width="200" />
        <el-table-column prop="theaterName" :label="t('admin.theater')" width="150" />
        <el-table-column :label="t('admin.time')" min-width="210">
          <template #default="{ row }">
            <div>{{ formatDate(row.startTime) }}</div>
            <div class="secondary-time">{{ formatDate(row.endTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="priceRange" :label="t('admin.priceRange')" width="130" />
        <el-table-column :label="t('admin.totalSeats')" width="110">
          <template #default="{ row }">
            {{ row.totalSeats }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.availableSeats')" width="110">
          <template #default="{ row }">
            <span class="available-count">{{ row.availableSeats }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.soldSeats')" width="100">
          <template #default="{ row }">
            <span class="sold-count">{{ row.soldSeats }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.checkedInTickets')" width="120">
          <template #default="{ row }">
            {{ row.checkedInTickets }} / {{ row.paidTickets }}
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.scheduleStatus')" width="130">
          <template #default="{ row }">
            <el-tag
              :type="statusTagType(row.status)"
              effect="plain"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.action')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="operatingId === row.id" @click="openEdit(row)">
              {{ t('admin.edit') }}
            </el-button>
            <el-button
              link
              type="primary"
              :loading="operatingId === row.id"
              :disabled="row.status === 'CANCELLED'"
              @click="setScheduleStatus(row, nextStatus(row.status))"
            >
              {{ t('admin.statusNext') }}
            </el-button>
            <el-button
              link
              type="danger"
              :loading="operatingId === row.id"
              :disabled="row.status === 'CANCELLED'"
              @click="handleCancel(row)"
            >
              {{ t('admin.cancelSchedule') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('admin.createSchedule') : t('admin.editSchedule')"
      width="740px"
      class="schedule-dialog"
      @closed="resetForm"
    >
      <el-form label-position="top" class="schedule-form">
        <div class="form-grid">
          <el-form-item :label="t('admin.shows')" required>
            <el-select v-model="form.showId" filterable class="full-control">
              <el-option
                v-for="show in schedulableShows"
                :key="show.id"
                :label="show.title"
                :value="show.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('admin.theater')" required>
            <el-input v-model="form.theaterName" maxlength="128" />
          </el-form-item>
          <el-form-item :label="t('admin.startTime')" required>
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="full-control"
            />
          </el-form-item>
          <el-form-item :label="t('admin.endTime')" required>
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="full-control"
            />
          </el-form-item>
          <el-form-item :label="t('admin.scheduleStatus')" required>
            <el-select v-model="form.status" class="full-control">
              <el-option
                v-for="status in scheduleStatusOptions"
                :key="status"
                :label="statusLabel(status)"
                :value="status"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('admin.priceRange')" required>
            <el-input v-model="form.priceRange" maxlength="64" />
          </el-form-item>
          <template v-if="dialogMode === 'create'">
            <el-form-item :label="t('admin.seatRows')" required>
              <el-input-number v-model="form.seatRows" :min="1" :max="30" class="full-control" />
            </el-form-item>
            <el-form-item :label="t('admin.seatCols')" required>
              <el-input-number v-model="form.seatCols" :min="1" :max="40" class="full-control" />
            </el-form-item>
            <el-form-item :label="t('admin.vipPrice')" required>
              <el-input-number v-model="form.vipPrice" :min="1" :step="10" class="full-control" />
            </el-form-item>
            <el-form-item :label="t('admin.standardPrice')" required>
              <el-input-number v-model="form.standardPrice" :min="1" :step="10" class="full-control" />
            </el-form-item>
            <el-form-item :label="t('admin.economyPrice')" required>
              <el-input-number v-model="form.economyPrice" :min="1" :step="10" class="full-control" />
            </el-form-item>
          </template>
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

.secondary-time {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.available-count {
  color: var(--color-success);
  font-weight: 700;
}

.sold-count {
  color: var(--color-warning);
  font-weight: 700;
}

.schedule-form {
  .form-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: var(--spacing-4);
  }

  .full-control {
    width: 100%;
  }
}

:deep(.schedule-dialog) {
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

  .schedule-form {
    .form-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
