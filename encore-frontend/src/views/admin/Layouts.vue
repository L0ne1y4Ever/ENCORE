<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AdminSeatMapEditor from '../../components/AdminSeatMapEditor.vue'
import {
  createAdminLayout,
  getAdminSchedules,
  getAdminHalls,
  getAdminLayoutAreas,
  getAdminLayouts,
  getAdminLayoutSeats,
  syncAdminLayoutSeatStatus,
  updateAdminLayout,
  updateAdminLayoutStatus,
  updateAdminLayoutSeatStatus
} from '../../api/admin'
import type {
  AdminHall,
  AdminLayout,
  AdminLayoutArea,
  AdminLayoutPayload,
  AdminSchedule,
  AdminLayoutSeat,
  LayoutStatus,
  SeatStatus,
  TicketMode
} from '../../api/admin'

const { t } = useI18n()

type DialogMode = 'create' | 'edit'

const layouts = ref<AdminLayout[]>([])
const halls = ref<AdminHall[]>([])
const areas = ref<AdminLayoutArea[]>([])
const seats = ref<AdminLayoutSeat[]>([])
const selectedHallId = ref('')
const selectedLayout = ref<AdminLayout | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const operatingId = ref('')
const operatingSeat = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const syncDialogVisible = ref(false)
const syncLoading = ref(false)
const syncing = ref(false)
const syncSchedules = ref<AdminSchedule[]>([])
const selectedSyncScheduleIds = ref<string[]>([])

const form = reactive({
  id: '',
  hallId: '',
  name: '',
  ticketMode: 'SEATED' as TicketMode,
  status: 'DRAFT' as LayoutStatus,
  rows: 10,
  cols: 15,
  vipPrice: 150,
  standardPrice: 100,
  economyPrice: 50
})

const filteredLayouts = computed(() => {
  if (!selectedHallId.value) return layouts.value
  return layouts.value.filter(layout => layout.hallId === selectedHallId.value)
})

const syncableSchedules = computed(() => {
  if (!selectedLayout.value) return []
  const now = Date.now()
  return syncSchedules.value.filter(schedule => {
    const startsInFuture = new Date(schedule.startTime).getTime() > now
    return schedule.layoutId === selectedLayout.value?.id
      && startsInFuture
      && schedule.status !== 'CANCELLED'
      && schedule.status !== 'ENDED'
  })
})

const syncAllSelected = computed({
  get() {
    return syncableSchedules.value.length > 0
      && selectedSyncScheduleIds.value.length === syncableSchedules.value.length
  },
  set(checked: boolean) {
    selectedSyncScheduleIds.value = checked ? syncableSchedules.value.map(schedule => schedule.id) : []
  }
})

const resetForm = () => {
  Object.assign(form, {
    id: '',
    hallId: selectedHallId.value || halls.value[0]?.id || '',
    name: '',
    ticketMode: 'SEATED',
    status: 'DRAFT',
    rows: 10,
    cols: 15,
    vipPrice: 150,
    standardPrice: 100,
    economyPrice: 50
  })
}

const loadData = async () => {
  loading.value = true
  try {
    const [layoutRows, hallRows] = await Promise.all([getAdminLayouts(), getAdminHalls()])
    layouts.value = layoutRows
    halls.value = hallRows
    if (!selectedHallId.value && hallRows.length > 0) {
      selectedHallId.value = hallRows[0].id
    }
    if (!selectedLayout.value && layoutRows.length > 0) {
      await selectLayout(layoutRows[0])
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

const selectLayout = async (layout: AdminLayout) => {
  selectedLayout.value = layout
  detailLoading.value = true
  try {
    const [areaRows, seatRows] = await Promise.all([
      getAdminLayoutAreas(layout.id),
      getAdminLayoutSeats(layout.id)
    ])
    areas.value = areaRows
    seats.value = seatRows
  } catch (error) {
    areas.value = []
    seats.value = []
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    detailLoading.value = false
  }
}

const openCreate = () => {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

const openEdit = (layout: AdminLayout) => {
  Object.assign(form, {
    id: layout.id,
    hallId: layout.hallId,
    name: layout.name,
    ticketMode: layout.ticketMode as TicketMode,
    status: layout.status as LayoutStatus,
    rows: 10,
    cols: 15,
    vipPrice: 150,
    standardPrice: 100,
    economyPrice: 50
  })
  dialogMode.value = 'edit'
  dialogVisible.value = true
}

const saveLayout = async () => {
  if (!form.hallId || !form.name.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return
  }

  const payload: AdminLayoutPayload = {
    hallId: form.hallId,
    name: form.name.trim(),
    ticketMode: form.ticketMode,
    status: form.status,
    seatRows: form.rows,
    seatCols: form.cols,
    vipPrice: form.vipPrice,
    standardPrice: form.standardPrice,
    economyPrice: form.economyPrice
  }

  saving.value = true
  try {
    const updated = dialogMode.value === 'create'
      ? await createAdminLayout(payload)
      : await updateAdminLayout(form.id, {
        name: form.name.trim(),
        status: form.status
      })
    const index = layouts.value.findIndex(item => item.id === updated.id)
    if (index >= 0) {
      layouts.value[index] = updated
    } else {
      layouts.value.unshift(updated)
    }
    dialogVisible.value = false
    await selectLayout(updated)
    ElMessage.success(t('admin.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}

const changeStatus = async (layout: AdminLayout, status: LayoutStatus) => {
  operatingId.value = layout.id
  try {
    const updated = await updateAdminLayoutStatus(layout.id, status)
    const index = layouts.value.findIndex(item => item.id === updated.id)
    if (index >= 0) layouts.value[index] = updated
    if (selectedLayout.value?.id === updated.id) selectedLayout.value = updated
    ElMessage.success(t('admin.statusUpdated'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingId.value = ''
  }
}

const statusTagType = (status: string) => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'danger'
  return 'info'
}

const modeTagType = (mode: string) => {
  if (mode === 'MIXED') return 'success'
  if (mode === 'ZONED') return 'warning'
  return 'info'
}

const canToggleSeat = (seat: AdminLayoutSeat) => {
  return selectedLayout.value?.status !== 'ARCHIVED' && (seat.status === 'AVAILABLE' || seat.status === 'DISABLED')
}

const toggleSeatStatus = async (seat: AdminLayoutSeat) => {
  if (!selectedLayout.value || !canToggleSeat(seat)) return
  operatingSeat.value = seat.id
  try {
    const nextStatus: SeatStatus = seat.status === 'AVAILABLE' ? 'DISABLED' : 'AVAILABLE'
    const updated = await updateAdminLayoutSeatStatus(selectedLayout.value.id, seat.seatCode, nextStatus)
    const index = seats.value.findIndex(item => item.id === updated.id)
    if (index >= 0) {
      seats.value[index] = updated
    }
    ElMessage.success(t('admin.layoutSeatUpdated'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    operatingSeat.value = ''
  }
}

const toggleSeatFromMap = (seat: unknown) => {
  return toggleSeatStatus(seat as AdminLayoutSeat)
}

const openSyncDialog = async () => {
  if (!selectedLayout.value) return
  syncDialogVisible.value = true
  syncLoading.value = true
  try {
    syncSchedules.value = await getAdminSchedules()
    selectedSyncScheduleIds.value = syncableSchedules.value.map(schedule => schedule.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    syncLoading.value = false
  }
}

const toggleSyncSchedule = (scheduleId: string, checked: boolean) => {
  if (checked) {
    if (!selectedSyncScheduleIds.value.includes(scheduleId)) {
      selectedSyncScheduleIds.value.push(scheduleId)
    }
    return
  }
  selectedSyncScheduleIds.value = selectedSyncScheduleIds.value.filter(id => id !== scheduleId)
}

const onSyncScheduleChecked = (scheduleId: string, checked: unknown) => {
  toggleSyncSchedule(scheduleId, Boolean(checked))
}

const syncLayoutSeatStatus = async () => {
  if (!selectedLayout.value || selectedSyncScheduleIds.value.length === 0) {
    ElMessage.error(t('admin.layoutSyncNoSelection'))
    return
  }
  syncing.value = true
  try {
    const result = await syncAdminLayoutSeatStatus(selectedLayout.value.id, selectedSyncScheduleIds.value)
    syncDialogVisible.value = false
    ElMessage.success(t('admin.layoutSyncSuccess', {
      scheduleCount: result.scheduleCount,
      seatCount: result.updatedSeatCount
    }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    syncing.value = false
  }
}

const formatDateTime = (value: string) => new Date(value).toLocaleString()
</script>

<template>
  <div class="layouts-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.layouts') }}</h1>
        <p>{{ t('admin.layoutsSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedHallId" class="hall-filter" clearable :placeholder="t('admin.allHalls')">
          <el-option v-for="hall in halls" :key="hall.id" :label="`${hall.venueName} / ${hall.name}`" :value="hall.id" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="loadData">{{ t('admin.refresh') }}</el-button>
        <el-button type="primary" @click="openCreate">{{ t('admin.addLayout') }}</el-button>
      </div>
    </div>

    <div class="layout-shell">
      <section class="panel layout-list">
        <el-table :data="filteredLayouts" :empty-text="t('admin.empty')" v-loading="loading" row-key="id" @row-click="selectLayout">
          <el-table-column prop="name" :label="t('admin.layoutName')" min-width="190" />
          <el-table-column :label="t('admin.ticketMode')" width="105">
            <template #default="{ row }">
              <el-tag size="small" :type="modeTagType(row.ticketMode)">{{ t(`ticketMode.${row.ticketMode?.toLowerCase()}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" :label="t('admin.version')" width="75" />
          <el-table-column :label="t('admin.layoutStatus')" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.action')" width="190">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="operatingId === row.id"
                @click.stop="openEdit(row)"
              >
                {{ t('admin.edit') }}
              </el-button>
              <el-button
                v-if="row.status !== 'PUBLISHED'"
                link
                type="primary"
                :loading="operatingId === row.id"
                @click.stop="changeStatus(row, 'PUBLISHED')"
              >
                {{ t('admin.publish') }}
              </el-button>
              <el-button
                v-if="row.status !== 'ARCHIVED'"
                link
                type="danger"
                :loading="operatingId === row.id"
                @click.stop="changeStatus(row, 'ARCHIVED')"
              >
                {{ t('admin.archive') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel detail-panel" v-loading="detailLoading">
        <template v-if="selectedLayout">
          <div class="detail-head">
            <div>
              <h2>{{ selectedLayout.name }}</h2>
              <p>{{ selectedLayout.hallName }} · v{{ selectedLayout.version }}</p>
            </div>
            <div class="detail-actions">
              <div class="detail-stats">
                <span>{{ t('admin.areas') }} {{ selectedLayout.areaCount }}</span>
                <span>{{ t('admin.seats') }} {{ selectedLayout.seatCount }}</span>
              </div>
              <el-button
                v-if="seats.length > 0"
                type="primary"
                plain
                size="small"
                :disabled="selectedLayout.status === 'ARCHIVED'"
                @click="openSyncDialog"
              >
                {{ t('admin.syncLayoutSeats') }}
              </el-button>
            </div>
          </div>

          <div class="preview-band">
            <div v-if="areas.length > 0" class="area-preview">
              <button
                v-for="area in areas"
                :key="area.id"
                type="button"
                class="area-shape"
                :class="{ seated: area.isSeated }"
                :style="{ borderColor: area.color || '#c8955a', color: area.color || '#c8955a' }"
              >
                <strong>{{ area.name }}</strong>
                <span>{{ area.capacity }} · {{ area.basePrice }}</span>
              </button>
            </div>
            <AdminSeatMapEditor
              v-if="seats.length > 0"
              :seats="seats"
              :operating-seat-id="operatingSeat"
              :hint="t('admin.layoutSeatStatusHint')"
              :disabled="selectedLayout.status === 'ARCHIVED'"
              :disabled-reason="t('admin.archivedLayoutReadonly')"
              :stage-label="t('seat.stage')"
              @toggle="toggleSeatFromMap"
            />
            <el-empty v-if="areas.length === 0 && seats.length === 0" :description="t('admin.empty')" />
          </div>

          <div class="detail-tables">
            <el-table :data="areas" :empty-text="t('admin.empty')" size="small">
              <el-table-column prop="name" :label="t('admin.areaName')" min-width="130" />
              <el-table-column prop="code" label="Code" width="110" />
              <el-table-column prop="isSeated" :label="t('admin.seatedArea')" width="90">
                <template #default="{ row }">{{ row.isSeated ? t('common.yes') : t('common.no') }}</template>
              </el-table-column>
              <el-table-column prop="capacity" :label="t('admin.capacity')" width="90" />
              <el-table-column prop="basePrice" :label="t('admin.price')" width="90" />
            </el-table>
            <el-table v-if="seats.length > 0" :data="seats" :empty-text="t('admin.empty')" size="small" max-height="320" class="seat-detail-table">
              <el-table-column prop="seatCode" :label="t('admin.seatCode')" min-width="130" />
              <el-table-column prop="rowNo" :label="t('admin.rowNo')" width="72" />
              <el-table-column prop="colNo" :label="t('admin.colNo')" width="72" />
              <el-table-column prop="section" :label="t('ticket.section')" width="90" />
              <el-table-column prop="status" :label="t('common.status')" width="120">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'DISABLED' ? 'danger' : 'success'" effect="plain">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="price" :label="t('admin.price')" width="90" />
              <el-table-column :label="t('admin.action')" width="120">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    :disabled="!canToggleSeat(row) || operatingSeat === row.id"
                    :loading="operatingSeat === row.id"
                    @click="toggleSeatStatus(row)"
                  >
                    {{ row.status === 'AVAILABLE' ? t('admin.disableSeat') : t('admin.restoreSeat') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
        <el-empty v-else :description="t('admin.empty')" />
      </section>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('admin.addLayout') : t('admin.editLayout')"
      width="640px"
      @closed="resetForm"
    >
      <el-form label-position="top" class="layout-form">
        <el-form-item :label="t('admin.hallName')" required>
          <el-select v-model="form.hallId" filterable class="full-control" :disabled="dialogMode === 'edit'">
            <el-option v-for="hall in halls" :key="hall.id" :label="`${hall.venueName} / ${hall.name}`" :value="hall.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.layoutName')" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('admin.ticketMode')" required>
          <el-select v-model="form.ticketMode" class="full-control" :disabled="dialogMode === 'edit'">
            <el-option value="SEATED" :label="t('ticketMode.seated')" />
            <el-option value="ZONED" :label="t('ticketMode.zoned')" />
            <el-option value="MIXED" :label="t('ticketMode.mixed')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.layoutStatus')" required>
          <el-select v-model="form.status" class="full-control">
            <el-option value="DRAFT" label="DRAFT" />
            <el-option value="PUBLISHED" label="PUBLISHED" />
          </el-select>
        </el-form-item>
        <div v-if="dialogMode === 'edit'" class="edit-hint span-2">
          {{ t('admin.layoutEditHint') }}
        </div>
        <template v-if="form.ticketMode === 'SEATED' && dialogMode === 'create'">
          <el-form-item :label="t('admin.seatRows')" required>
            <el-input-number v-model="form.rows" :min="1" :max="30" class="full-control" />
          </el-form-item>
          <el-form-item :label="t('admin.seatCols')" required>
            <el-input-number v-model="form.cols" :min="1" :max="40" class="full-control" />
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
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveLayout">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="syncDialogVisible"
      :title="t('admin.syncLayoutSeats')"
      width="780px"
    >
      <div class="sync-dialog-body">
        <p class="sync-hint">{{ t('admin.syncLayoutSeatsHint') }}</p>
        <div class="sync-toolbar">
          <el-checkbox v-model="syncAllSelected" :disabled="syncableSchedules.length === 0">
            {{ t('admin.selectAll') }}
          </el-checkbox>
          <span>{{ t('admin.syncableScheduleCount', { count: syncableSchedules.length }) }}</span>
        </div>
        <el-table :data="syncableSchedules" :empty-text="t('admin.noSyncableSchedules')" v-loading="syncLoading" max-height="420">
          <el-table-column width="52">
            <template #default="{ row }">
              <el-checkbox
                :model-value="selectedSyncScheduleIds.includes(row.id)"
                @change="onSyncScheduleChecked(row.id, $event)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="showTitle" :label="t('admin.title')" min-width="160" />
          <el-table-column :label="t('admin.time')" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column prop="status" :label="t('admin.scheduleStatus')" width="110" />
          <el-table-column prop="availableSeats" :label="t('admin.availableSeats')" width="90" />
          <el-table-column prop="lockedSeats" :label="t('admin.lockedSeats')" width="90" />
          <el-table-column prop="soldSeats" :label="t('admin.soldSeats')" width="90" />
        </el-table>
      </div>
      <template #footer>
        <el-button :disabled="syncing" @click="syncDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="syncing"
          :disabled="selectedSyncScheduleIds.length === 0"
          @click="syncLayoutSeatStatus"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.layouts-page {
  width: 100%;
}

.page-header {
  margin-bottom: var(--spacing-6);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-4);

  h1 {
    font-family: var(--font-family-display);
    font-size: 32px;
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
}

.hall-filter {
  width: 260px;
}

.layout-shell {
  display: grid;
  grid-template-columns: minmax(520px, 1fr) minmax(420px, 0.9fr);
  gap: var(--spacing-4);
}

.panel {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-elevated);
  padding: var(--spacing-4);
}

.detail-head {
  margin-bottom: var(--spacing-4);
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-4);

  h2 {
    font-size: 18px;
  }

  p {
    margin-top: 4px;
    color: var(--color-text-secondary);
  }
}

.detail-stats {
  display: flex;
  gap: var(--spacing-3);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.detail-actions {
  display: grid;
  justify-items: end;
  gap: var(--spacing-2);
}

.preview-band {
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.02);
  border-radius: var(--radius-sm);
  padding: var(--spacing-4);
}

.area-preview {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-4);
}

.area-shape {
  min-height: 76px;
  border: 1px solid;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.03);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-family: var(--font-family-sans);

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  &.seated {
    border-style: dashed;
  }
}

.detail-tables {
  margin-top: var(--spacing-4);
  display: grid;
  gap: var(--spacing-4);
}

.seat-detail-table {
  margin-top: var(--spacing-2);
}

:deep(.el-table) {
  background-color: transparent;
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.02);
  --el-table-header-text-color: var(--color-text-secondary);
  --el-table-text-color: var(--color-text-primary);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
}

.layout-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--spacing-4);

  .span-2 {
    grid-column: 1 / -1;
  }
}

.edit-hint {
  border: 1px solid rgba(200, 149, 90, 0.28);
  border-radius: var(--radius-sm);
  padding: var(--spacing-3);
  color: var(--color-text-secondary);
  background: rgba(200, 149, 90, 0.08);
  font-size: 13px;
}

.sync-dialog-body {
  display: grid;
  gap: var(--spacing-3);
}

.sync-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.sync-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.full-control {
  width: 100%;
}

@media (max-width: 1180px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }
}
</style>
