<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAdminSchedule,
  createAdminSchedule,
  getAdminHalls,
  getAdminLayouts,
  getAdminSchedules,
  getAdminShows,
  getAdminVenues,
  updateAdminSchedule,
  updateAdminScheduleStatus
} from '../../api/admin'
import type {
  AdminHall,
  AdminLayout,
  AdminSchedule,
  AdminShow,
  AdminVenue,
  CreateAdminSchedulePayload,
  PublishStatus,
  ScheduleStatus,
  UpdateAdminSchedulePayload
} from '../../api/admin'

const { t } = useI18n()
const router = useRouter()

type DialogMode = 'create' | 'edit'
type ViewMode = 'list' | 'calendar'

interface ScheduleForm {
  id: string
  showId: string
  hallId: string
  layoutId: string
  theaterName: string
  startTime: string
  endTime: string
  saleStartTime: string
  saleEndTime: string
  status: ScheduleStatus
  publishStatus: PublishStatus
  priceRange: string
  ticketMode: string
  seatRows: number
  seatCols: number
  vipPrice: number
  standardPrice: number
  economyPrice: number
}

const scheduleStatusOptions: ScheduleStatus[] = [
  'DRAFT',
  'PUBLISHED',
  'COMING_SOON',
  'PREPARING',
  'ON_SALE',
  'SOLD_OUT',
  'CANCELLED',
  'ENDED'
]

const scheduleStatusKey: Record<ScheduleStatus, string> = {
  DRAFT: 'draft',
  PUBLISHED: 'published',
  COMING_SOON: 'comingSoon',
  PREPARING: 'preparing',
  ON_SALE: 'onSale',
  SOLD_OUT: 'soldOut',
  CANCELLED: 'cancelled',
  ENDED: 'ended'
}

const tableData = ref<AdminSchedule[]>([])
const shows = ref<AdminShow[]>([])
const venues = ref<AdminVenue[]>([])
const halls = ref<AdminHall[]>([])
const layouts = ref<AdminLayout[]>([])
const loading = ref(false)
const saving = ref(false)
const operatingId = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const viewMode = ref<ViewMode>('list')
const categoryFilter = ref('')
const modeFilter = ref('')
const statusFilter = ref('')
const sortKey = ref<'startAsc' | 'startDesc' | 'category' | 'availableDesc'>('startAsc')

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

const defaultSaleStartTime = () => {
  const date = new Date()
  date.setHours(10, 0, 0, 0)
  return date
}

const firstPublishedLayout = (hallId: string) => {
  return layouts.value.find(layout => layout.hallId === hallId && layout.status === 'PUBLISHED')
    || layouts.value.find(layout => layout.hallId === hallId)
}

const emptyForm = (): ScheduleForm => {
  const start = defaultStartTime()
  const end = new Date(start.getTime() + 150 * 60 * 1000)
  const hall = halls.value[0]
  const layout = hall ? firstPublishedLayout(hall.id) : undefined
  return {
    id: '',
    showId: shows.value.find(show => show.status !== 'ARCHIVED')?.id || '',
    hallId: hall?.id || '',
    layoutId: layout?.id || '',
    theaterName: hall?.name || 'Main Hall',
    startTime: toDateTimeValue(start),
    endTime: toDateTimeValue(end),
    saleStartTime: toDateTimeValue(defaultSaleStartTime()),
    saleEndTime: toDateTimeValue(start),
    status: 'PREPARING',
    publishStatus: 'DRAFT',
    priceRange: '$50 - $150',
    ticketMode: layout?.ticketMode || 'SEATED',
    seatRows: 10,
    seatCols: 15,
    vipPrice: 150,
    standardPrice: 100,
    economyPrice: 50
  }
}

const form = reactive<ScheduleForm>(emptyForm())

const schedulableShows = computed(() => shows.value.filter(show => show.status !== 'ARCHIVED'))
const layoutsForSelectedHall = computed(() => layouts.value.filter(layout => layout.hallId === form.hallId && layout.status !== 'ARCHIVED'))
const publishedLayoutsForSelectedHall = computed(() => layoutsForSelectedHall.value.filter(layout => layout.status === 'PUBLISHED'))
const categoryOptions = computed(() => {
  return Array.from(new Set(tableData.value.map(row => row.category).filter(Boolean))).sort()
})

const filteredSchedules = computed(() => {
  const rows = tableData.value.filter(row => {
    if (categoryFilter.value && row.category !== categoryFilter.value) return false
    if (modeFilter.value && row.ticketMode !== modeFilter.value) return false
    if (statusFilter.value && row.status !== statusFilter.value) return false
    return true
  })
  return [...rows].sort((left, right) => {
    if (sortKey.value === 'startDesc') return right.startTime.localeCompare(left.startTime)
    if (sortKey.value === 'category') {
      return `${left.category}-${left.startTime}`.localeCompare(`${right.category}-${right.startTime}`)
    }
    if (sortKey.value === 'availableDesc') {
      return right.availableSeats - left.availableSeats || left.startTime.localeCompare(right.startTime)
    }
    return left.startTime.localeCompare(right.startTime)
  })
})

const calendarGroups = computed(() => {
  const groups = new Map<string, AdminSchedule[]>()
  for (const row of filteredSchedules.value) {
    const key = row.startTime.slice(0, 10)
    const list = groups.get(key) || []
    list.push(row)
    groups.set(key, list)
  }
  return Array.from(groups.entries())
    .map(([date, rows]) => ({
      date,
      weekday: new Date(date).toLocaleDateString(undefined, { weekday: 'short' }),
      rows: rows.sort((left, right) => left.startTime.localeCompare(right.startTime))
    }))
    .sort((left, right) => left.date.localeCompare(right.date))
})

const resetForm = () => {
  Object.assign(form, emptyForm())
}

const loadSchedules = async () => {
  loading.value = true
  try {
    const [scheduleRows, showRows, venueRows, hallRows, layoutRows] = await Promise.all([
      getAdminSchedules(),
      getAdminShows(),
      getAdminVenues(),
      getAdminHalls(),
      getAdminLayouts()
    ])
    tableData.value = scheduleRows
    shows.value = showRows
    venues.value = venueRows
    halls.value = hallRows
    layouts.value = layoutRows
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadSchedules)

const formatDate = (dateStr?: string | null) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

const statusFlow: ScheduleStatus[] = ['DRAFT', 'PUBLISHED', 'PREPARING', 'ON_SALE', 'SOLD_OUT']

const nextStatus = (status: ScheduleStatus): ScheduleStatus => {
  const currentIndex = statusFlow.indexOf(status)
  if (currentIndex === -1 || currentIndex === statusFlow.length - 1) return 'DRAFT'
  return statusFlow[currentIndex + 1]
}

const statusLabel = (status: ScheduleStatus) => {
  return t(`admin.scheduleStatusMap.${scheduleStatusKey[status]}`)
}

const statusTagType = (status: ScheduleStatus) => {
  if (status === 'ON_SALE') return 'success'
  if (status === 'SOLD_OUT' || status === 'CANCELLED') return 'danger'
  if (status === 'PREPARING' || status === 'PUBLISHED') return 'warning'
  return 'info'
}

const publishTagType = (status: string) => {
  return status === 'PUBLISHED' ? 'success' : 'info'
}

const modeTagType = (mode: string) => {
  if (mode === 'MIXED') return 'success'
  if (mode === 'ZONED') return 'warning'
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

const handleHallChange = () => {
  const hall = halls.value.find(item => item.id === form.hallId)
  const layout = form.hallId ? firstPublishedLayout(form.hallId) : undefined
  form.theaterName = hall?.name || ''
  form.layoutId = layout?.id || ''
  if (layout) {
    form.ticketMode = layout.ticketMode
  }
}

const handleLayoutChange = () => {
  const layout = layouts.value.find(item => item.id === form.layoutId)
  if (layout) {
    form.ticketMode = layout.ticketMode
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
    hallId: row.hallId || '',
    layoutId: row.layoutId || '',
    theaterName: row.theaterName,
    startTime: row.startTime,
    endTime: row.endTime,
    saleStartTime: row.saleStartTime || '',
    saleEndTime: row.saleEndTime || '',
    status: row.status,
    publishStatus: (row.publishStatus === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT') as PublishStatus,
    priceRange: row.priceRange,
    ticketMode: row.ticketMode || 'SEATED',
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
  if (!form.showId || !form.hallId || !form.layoutId || !form.theaterName.trim() || !form.startTime || !form.endTime || !form.priceRange.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return false
  }
  if (new Date(form.startTime).getTime() >= new Date(form.endTime).getTime()) {
    ElMessage.error(t('admin.startBeforeEnd'))
    return false
  }
  if (form.saleStartTime && form.saleEndTime && new Date(form.saleStartTime).getTime() >= new Date(form.saleEndTime).getTime()) {
    ElMessage.error(t('admin.saleTimeInvalid'))
    return false
  }
  if (dialogMode.value === 'create' && !form.layoutId && (form.seatRows < 1 || form.seatCols < 1 || form.vipPrice <= 0 || form.standardPrice <= 0 || form.economyPrice <= 0)) {
    ElMessage.error(t('admin.seatsInvalid'))
    return false
  }
  return true
}

const buildCreatePayload = (): CreateAdminSchedulePayload => ({
  showId: form.showId,
  hallId: form.hallId,
  layoutId: form.layoutId,
  theaterName: form.theaterName.trim(),
  startTime: form.startTime,
  endTime: form.endTime,
  saleStartTime: form.saleStartTime || null,
  saleEndTime: form.saleEndTime || null,
  status: form.status,
  publishStatus: form.publishStatus,
  priceRange: form.priceRange.trim(),
  ticketMode: form.ticketMode,
  seatRows: form.seatRows,
  seatCols: form.seatCols,
  vipPrice: form.vipPrice,
  standardPrice: form.standardPrice,
  economyPrice: form.economyPrice
})

const buildUpdatePayload = (): UpdateAdminSchedulePayload => ({
  showId: form.showId,
  hallId: form.hallId,
  layoutId: form.layoutId,
  theaterName: form.theaterName.trim(),
  startTime: form.startTime,
  endTime: form.endTime,
  saleStartTime: form.saleStartTime || null,
  saleEndTime: form.saleEndTime || null,
  status: form.status,
  publishStatus: form.publishStatus,
  priceRange: form.priceRange.trim(),
  ticketMode: form.ticketMode
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

const openInventory = (row: AdminSchedule) => {
  router.push(`/admin/schedules/${row.id}/inventory`)
}
</script>

<template>
  <div class="schedules-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.schedules') }}</h1>
        <p>{{ t('admin.schedulesSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button label="list">{{ t('admin.listView') }}</el-radio-button>
          <el-radio-button label="calendar">{{ t('admin.calendarView') }}</el-radio-button>
        </el-radio-group>
        <el-select v-model="categoryFilter" class="compact-filter" clearable :placeholder="t('admin.allCategories')">
          <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
        </el-select>
        <el-select v-model="modeFilter" class="compact-filter" clearable :placeholder="t('admin.allTicketModes')">
          <el-option value="SEATED" :label="t('ticketMode.seated')" />
          <el-option value="ZONED" :label="t('ticketMode.zoned')" />
          <el-option value="MIXED" :label="t('ticketMode.mixed')" />
        </el-select>
        <el-select v-model="statusFilter" class="compact-filter" clearable :placeholder="t('admin.allStatuses')">
          <el-option v-for="status in scheduleStatusOptions" :key="status" :label="statusLabel(status)" :value="status" />
        </el-select>
        <el-select v-model="sortKey" class="sort-filter" :placeholder="t('admin.sortBy')">
          <el-option value="startAsc" :label="t('admin.sortStartAsc')" />
          <el-option value="startDesc" :label="t('admin.sortStartDesc')" />
          <el-option value="category" :label="t('admin.sortCategory')" />
          <el-option value="availableDesc" :label="t('admin.sortAvailableDesc')" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="loadSchedules">
          {{ t('admin.refresh') }}
        </el-button>
        <el-button type="primary" @click="openCreate">
          {{ t('admin.addNewSchedule') }}
        </el-button>
      </div>
    </div>

    <div v-if="viewMode === 'calendar'" class="calendar-board" v-loading="loading">
      <div v-for="group in calendarGroups" :key="group.date" class="day-column">
        <div class="day-head">
          <strong>{{ group.date }}</strong>
          <span>{{ group.weekday }}</span>
        </div>
        <button
          v-for="row in group.rows"
          :key="row.id"
          type="button"
          class="schedule-chip"
          @click="openEdit(row)"
        >
          <span>{{ row.startTime.slice(11, 16) }} · {{ row.showTitle }}</span>
          <small>{{ row.hallName || row.theaterName }} / {{ statusLabel(row.status) }}</small>
        </button>
      </div>
      <el-empty v-if="calendarGroups.length === 0" :description="t('admin.empty')" />
    </div>

    <div v-else class="table-container">
      <el-table :data="filteredSchedules" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column prop="id" label="ID" width="130" />
        <el-table-column prop="showTitle" :label="t('admin.shows')" min-width="190" />
        <el-table-column prop="category" :label="t('admin.category')" width="105" />
        <el-table-column :label="t('admin.hallName')" min-width="150">
          <template #default="{ row }">{{ row.hallName || row.theaterName }}</template>
        </el-table-column>
        <el-table-column :label="t('admin.layoutName')" min-width="160">
          <template #default="{ row }">{{ row.layoutName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ticketMode" :label="t('admin.ticketMode')" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="modeTagType(row.ticketMode)">
              {{ t(`ticketMode.${row.ticketMode?.toLowerCase()}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.time')" min-width="210">
          <template #default="{ row }">
            <div>{{ formatDate(row.startTime) }}</div>
            <div class="secondary-time">{{ formatDate(row.endTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.saleWindow')" min-width="180">
          <template #default="{ row }">
            <div>{{ formatDate(row.saleStartTime) }}</div>
            <div class="secondary-time">{{ formatDate(row.saleEndTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="priceRange" :label="t('admin.priceRange')" width="130" />
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
        <el-table-column :label="t('admin.scheduleStatus')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.publishStatus')" width="110">
          <template #default="{ row }">
            <el-tag :type="publishTagType(row.publishStatus)" effect="plain">{{ row.publishStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.action')" width="285" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="operatingId === row.id" @click="openInventory(row)">
              {{ t('admin.inventory') }}
            </el-button>
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
      width="820px"
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
          <el-form-item :label="t('admin.hallName')" required>
            <el-select v-model="form.hallId" filterable class="full-control" @change="handleHallChange">
              <el-option
                v-for="hall in halls"
                :key="hall.id"
                :label="`${hall.venueName} / ${hall.name}`"
                :value="hall.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('admin.layoutName')" required>
            <el-select v-model="form.layoutId" filterable class="full-control" @change="handleLayoutChange">
              <el-option
                v-for="layout in layoutsForSelectedHall"
                :key="layout.id"
                :label="`${layout.name} · v${layout.version} · ${layout.status}`"
                :value="layout.id"
                :disabled="layout.status !== 'PUBLISHED'"
              />
            </el-select>
            <div v-if="publishedLayoutsForSelectedHall.length === 0" class="field-hint">
              {{ t('admin.noPublishedLayout') }}
            </div>
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
          <el-form-item :label="t('admin.saleStartTime')">
            <el-date-picker
              v-model="form.saleStartTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="full-control"
            />
          </el-form-item>
          <el-form-item :label="t('admin.saleEndTime')">
            <el-date-picker
              v-model="form.saleEndTime"
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
          <el-form-item :label="t('admin.publishStatus')" required>
            <el-select v-model="form.publishStatus" class="full-control">
              <el-option value="DRAFT" :label="t('admin.showStatus.draft')" />
              <el-option value="PUBLISHED" :label="t('admin.showStatus.published')" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('admin.priceRange')" required>
            <el-input v-model="form.priceRange" maxlength="64" />
          </el-form-item>
          <el-form-item :label="t('admin.ticketMode')" required>
            <el-select v-model="form.ticketMode" class="full-control">
              <el-option value="SEATED" :label="t('ticketMode.seated')" />
              <el-option value="ZONED" :label="t('ticketMode.zoned')" />
              <el-option value="MIXED" :label="t('ticketMode.mixed')" />
            </el-select>
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
.schedules-page {
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
  flex-wrap: wrap;
  justify-content: flex-end;
}

.compact-filter {
  width: 132px;
}

.sort-filter {
  width: 168px;
}

.table-container,
.calendar-board {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  padding: var(--spacing-4);
  border-radius: var(--radius-sm);
}

.calendar-board {
  min-height: 420px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--spacing-3);
}

.day-column {
  min-height: 180px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--spacing-3);
  background: rgba(255, 255, 255, 0.02);
}

.day-head {
  margin-bottom: var(--spacing-3);
  display: flex;
  justify-content: space-between;
  color: var(--color-text-secondary);

  strong {
    color: var(--color-text-primary);
  }
}

.schedule-chip {
  width: 100%;
  margin-bottom: var(--spacing-2);
  border: 1px solid rgba(200, 149, 90, 0.25);
  border-radius: var(--radius-sm);
  background: rgba(200, 149, 90, 0.08);
  color: var(--color-text-primary);
  padding: var(--spacing-2);
  text-align: left;
  cursor: pointer;

  span,
  small {
    display: block;
  }

  small {
    margin-top: 3px;
    color: var(--color-text-secondary);
  }
}

.table-container {
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

.field-hint {
  margin-top: 6px;
  color: var(--color-warning);
  font-size: 12px;
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .schedule-form .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
