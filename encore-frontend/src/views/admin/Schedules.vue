<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
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
type ScheduleStatusScope = '' | ScheduleStatus | 'UPCOMING'

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
const searchKeyword = ref('')
const venueFilter = ref('')
const hallFilter = ref('')
const dateRange = ref<[string, string] | null>(null)
const categoryFilter = ref('')
const modeFilter = ref('')
const statusFilter = ref<ScheduleStatusScope>('')
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
const filteredHallOptions = computed(() => {
  if (!venueFilter.value) return halls.value
  return halls.value.filter(hall => hall.venueId === venueFilter.value)
})

const filteredSchedules = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const rows = tableData.value.filter(row => {
    if (keyword) {
      const haystack = [
        row.showTitle,
        row.category,
        row.hallName,
        row.layoutName,
        row.theaterName,
        row.priceRange
      ].join(' ').toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    if (venueFilter.value) {
      const hall = halls.value.find(item => item.id === row.hallId)
      if (hall?.venueId !== venueFilter.value) return false
    }
    if (hallFilter.value && row.hallId !== hallFilter.value) return false
    if (dateRange.value) {
      const date = row.startTime.slice(0, 10)
      if (date < dateRange.value[0] || date > dateRange.value[1]) return false
    }
    if (categoryFilter.value && row.category !== categoryFilter.value) return false
    if (modeFilter.value && row.ticketMode !== modeFilter.value) return false
    if (statusFilter.value === 'UPCOMING') {
      if (row.status !== 'PREPARING' && row.status !== 'COMING_SOON') return false
    } else if (statusFilter.value && row.status !== statusFilter.value) {
      return false
    }
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

const scheduleMetrics = computed(() => {
  const rows = tableData.value
  return {
    total: rows.length,
    onSale: rows.filter(row => row.status === 'ON_SALE').length,
    preparing: rows.filter(row => row.status === 'PREPARING' || row.status === 'COMING_SOON').length,
    cancelled: rows.filter(row => row.status === 'CANCELLED').length,
    available: rows.reduce((sum, row) => sum + Number(row.availableSeats || 0), 0),
    sold: rows.reduce((sum, row) => sum + Number(row.soldSeats || 0), 0)
  }
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

const statusClass = (status: ScheduleStatus) => status.toLowerCase().replaceAll('_', '-')

const selectScheduleStatus = (status: ScheduleStatusScope) => {
  statusFilter.value = status
}

const handleVenueFilterChange = () => {
  if (hallFilter.value && !filteredHallOptions.value.some(hall => hall.id === hallFilter.value)) {
    hallFilter.value = ''
  }
}

const ticketProgress = (row: AdminSchedule) => {
  if (!row.totalSeats) return 0
  return Math.min(100, Math.round((row.soldSeats / row.totalSeats) * 100))
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
        <el-button type="primary" plain :icon="Refresh" :loading="loading" @click="loadSchedules">
          {{ t('admin.refresh') }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          {{ t('admin.addNewSchedule') }}
        </el-button>
      </div>
    </div>

    <div class="metric-strip">
      <button type="button" class="metric-card" :class="{ active: !statusFilter }" @click="selectScheduleStatus('')">
        <span>{{ t('admin.allSchedules') }}</span>
        <strong>{{ scheduleMetrics.total }}</strong>
      </button>
      <button type="button" class="metric-card" :class="{ active: statusFilter === 'ON_SALE' }" @click="selectScheduleStatus('ON_SALE')">
        <span>{{ t('admin.onSaleSchedules') }}</span>
        <strong>{{ scheduleMetrics.onSale }}</strong>
      </button>
      <button type="button" class="metric-card" :class="{ active: statusFilter === 'UPCOMING' }" @click="selectScheduleStatus('UPCOMING')">
        <span>{{ t('admin.preparingSchedules') }}</span>
        <strong>{{ scheduleMetrics.preparing }}</strong>
      </button>
      <button type="button" class="metric-card" :class="{ active: statusFilter === 'CANCELLED' }" @click="selectScheduleStatus('CANCELLED')">
        <span>{{ t('admin.cancelledSchedules') }}</span>
        <strong>{{ scheduleMetrics.cancelled }}</strong>
      </button>
      <div class="metric-card passive">
        <span>{{ t('admin.totalAvailable') }}</span>
        <strong>{{ scheduleMetrics.available }}</strong>
      </div>
      <div class="metric-card passive">
        <span>{{ t('admin.totalSold') }}</span>
        <strong>{{ scheduleMetrics.sold }}</strong>
      </div>
    </div>

    <div class="schedule-workspace">
      <div class="schedule-toolbar">
        <el-radio-group v-model="viewMode" size="small" class="view-switcher">
          <el-radio-button label="list">{{ t('admin.listView') }}</el-radio-button>
          <el-radio-button label="calendar">{{ t('admin.calendarView') }}</el-radio-button>
        </el-radio-group>
        <div class="toolbar-fields">
          <el-input
            v-model="searchKeyword"
            class="search-input"
            clearable
            :prefix-icon="Search"
            :placeholder="t('admin.searchSchedules')"
          />
          <el-select v-model="venueFilter" class="compact-filter" clearable :placeholder="t('admin.allVenues')" @change="handleVenueFilterChange">
            <el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id" />
          </el-select>
          <el-select v-model="hallFilter" class="compact-filter" clearable :placeholder="t('admin.allHalls')">
            <el-option v-for="hall in filteredHallOptions" :key="hall.id" :label="`${hall.venueName} / ${hall.name}`" :value="hall.id" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            unlink-panels
            value-format="YYYY-MM-DD"
            class="date-filter"
            :start-placeholder="t('admin.dateStart')"
            :end-placeholder="t('admin.dateEnd')"
          />
          <el-select v-model="categoryFilter" class="compact-filter" clearable :placeholder="t('admin.allCategories')">
            <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
          </el-select>
          <el-select v-model="modeFilter" class="compact-filter" clearable :placeholder="t('admin.allTicketModes')">
            <el-option value="SEATED" :label="t('ticketMode.seated')" />
            <el-option value="ZONED" :label="t('ticketMode.zoned')" />
            <el-option value="MIXED" :label="t('ticketMode.mixed')" />
          </el-select>
          <el-select v-model="statusFilter" class="compact-filter" clearable :placeholder="t('admin.allStatuses')">
            <el-option value="UPCOMING" :label="t('admin.preparingSchedules')" />
            <el-option v-for="status in scheduleStatusOptions" :key="status" :label="statusLabel(status)" :value="status" />
          </el-select>
          <el-select v-model="sortKey" class="sort-filter" :placeholder="t('admin.sortBy')">
            <el-option value="startAsc" :label="t('admin.sortStartAsc')" />
            <el-option value="startDesc" :label="t('admin.sortStartDesc')" />
            <el-option value="category" :label="t('admin.sortCategory')" />
            <el-option value="availableDesc" :label="t('admin.sortAvailableDesc')" />
          </el-select>
        </div>
      </div>

      <div v-if="viewMode === 'calendar'" class="calendar-board" v-loading="loading">
        <div v-for="group in calendarGroups" :key="group.date" class="day-column">
          <div class="day-head">
            <div>
              <strong>{{ group.date }}</strong>
              <span>{{ group.weekday }}</span>
            </div>
            <em>{{ group.rows.length }}</em>
          </div>
          <button
            v-for="row in group.rows"
            :key="row.id"
            type="button"
            class="schedule-chip"
            :class="statusClass(row.status)"
            :aria-label="`${row.showTitle} ${formatDate(row.startTime)}`"
            @click="openEdit(row)"
          >
            <span class="chip-time">{{ row.startTime.slice(11, 16) }} - {{ row.endTime.slice(11, 16) }}</span>
            <strong>{{ row.showTitle }}</strong>
            <small>{{ row.hallName || row.theaterName }} / {{ statusLabel(row.status) }}</small>
            <div class="chip-progress">
              <span :style="{ width: `${ticketProgress(row)}%` }" />
            </div>
            <small>{{ row.soldSeats }} / {{ row.totalSeats }} {{ t('admin.soldSeats') }}</small>
          </button>
        </div>
        <el-empty v-if="calendarGroups.length === 0" :description="t('admin.empty')" />
      </div>

      <div v-else class="table-container">
      <el-table :data="filteredSchedules" style="width: 100%" :empty-text="t('admin.empty')" v-loading="loading">
        <el-table-column :label="t('admin.showAndCategory')" min-width="260">
          <template #default="{ row }">
            <div class="schedule-show-cell">
              <div class="show-title">{{ row.showTitle }}</div>
              <div class="show-meta">
                <el-tag size="small" effect="plain">{{ row.category || t('admin.uncategorized') }}</el-tag>
                <span>#{{ row.id }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.venueAndLayout')" min-width="220">
          <template #default="{ row }">
            <div class="venue-cell">
              <strong>{{ row.hallName || row.theaterName }}</strong>
              <span>{{ row.layoutName || '-' }}</span>
              <el-tag size="small" :type="modeTagType(row.ticketMode)" effect="plain">
                {{ t(`ticketMode.${row.ticketMode?.toLowerCase()}`) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.scheduleTime')" min-width="210">
          <template #default="{ row }">
            <div class="time-stack">
              <strong>{{ formatDate(row.startTime) }}</strong>
              <span>{{ formatDate(row.endTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.saleWindow')" min-width="190">
          <template #default="{ row }">
            <div class="time-stack subtle">
              <span>{{ formatDate(row.saleStartTime) }}</span>
              <span>{{ formatDate(row.saleEndTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.salesProgress')" min-width="170">
          <template #default="{ row }">
            <div class="sales-cell">
              <el-progress :percentage="ticketProgress(row)" :stroke-width="8" :show-text="false" />
              <div class="ticket-stats">
                <span>{{ row.soldSeats }} / {{ row.totalSeats }}</span>
                <span>{{ t('admin.availableSeats') }} {{ row.availableSeats }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="priceRange" :label="t('admin.priceRange')" width="130" />
        <el-table-column :label="t('admin.statusAndPublish')" width="140">
          <template #default="{ row }">
            <div class="status-stack">
              <el-tag :type="statusTagType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              <el-tag :type="publishTagType(row.publishStatus)" effect="plain">{{ row.publishStatus }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.action')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Calendar" :disabled="operatingId === row.id" @click="openInventory(row)">
              {{ t('admin.inventory') }}
            </el-button>
            <el-button link type="primary" :icon="Edit" :disabled="operatingId === row.id" @click="openEdit(row)">
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
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('admin.createSchedule') : t('admin.editSchedule')"
      width="820px"
      class="schedule-dialog"
      @closed="resetForm"
    >
      <el-form label-position="top" class="schedule-form">
        <div class="form-section">
          <h3>{{ t('admin.scheduleShowVenue') }}</h3>
          <div class="form-grid">
            <el-form-item :label="t('admin.shows')" required class="span-2">
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
            <el-form-item :label="t('admin.theater')" required class="span-2">
              <el-input v-model="form.theaterName" maxlength="128" />
            </el-form-item>
          </div>
        </div>
        <div class="form-section">
          <h3>{{ t('admin.scheduleTimeSales') }}</h3>
          <div class="form-grid">
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
            <el-form-item :label="t('admin.priceRange')" required class="span-2">
              <el-input v-model="form.priceRange" maxlength="64" />
            </el-form-item>
          </div>
        </div>
        <div class="form-section">
          <h3>{{ t('admin.scheduleStatusTicketing') }}</h3>
          <div class="form-grid">
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
            <el-form-item :label="t('admin.ticketMode')" required class="span-2">
              <el-select v-model="form.ticketMode" class="full-control">
                <el-option value="SEATED" :label="t('ticketMode.seated')" />
                <el-option value="ZONED" :label="t('ticketMode.zoned')" />
                <el-option value="MIXED" :label="t('ticketMode.mixed')" />
              </el-select>
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
.schedules-page {
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
  margin-bottom: var(--spacing-4);
  display: grid;
  grid-template-columns: repeat(6, minmax(116px, 1fr));
  gap: var(--spacing-3);
}

.metric-card {
  min-width: 0;
  min-height: 76px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  padding: var(--spacing-3);
  display: grid;
  align-content: center;
  gap: 4px;
  color: var(--color-text-primary);
  cursor: pointer;
  text-align: left;
  transition: border-color 160ms ease, background-color 160ms ease;

  span {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 12px;
  }

  strong {
    font-family: var(--font-family-sans);
    font-size: 24px;
    font-variant-numeric: tabular-nums;
    line-height: 1.1;
  }

  &:hover,
  &.active {
    border-color: rgba(200, 149, 90, 0.38);
    background: rgba(200, 149, 90, 0.1);
  }

  &.passive {
    cursor: default;

    &:hover {
      border-color: var(--color-border);
      background: var(--color-bg-elevated);
    }
  }
}

.schedule-workspace {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background-color: var(--color-bg-elevated);
  padding: var(--spacing-4);
}

.schedule-toolbar {
  margin-bottom: var(--spacing-4);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-3);
}

.view-switcher {
  flex: 0 0 auto;
}

.toolbar-fields {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.search-input {
  width: min(300px, 100%);
}

.compact-filter {
  width: 132px;
}

.date-filter {
  width: 260px;
}

.sort-filter {
  width: 168px;
}

.table-container {
  min-width: 0;
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
  align-items: flex-start;
  justify-content: space-between;
  color: var(--color-text-secondary);

  div {
    display: grid;
    gap: 2px;
  }

  strong {
    color: var(--color-text-primary);
  }

  span {
    font-size: 12px;
  }

  em {
    min-width: 28px;
    border-radius: var(--radius-full);
    background: rgba(255, 255, 255, 0.05);
    color: var(--color-text-secondary);
    font-style: normal;
    font-size: 12px;
    line-height: 24px;
    text-align: center;
  }
}

.schedule-chip {
  width: 100%;
  margin-bottom: var(--spacing-2);
  border: 1px solid var(--color-border);
  border-left: 3px solid rgba(200, 149, 90, 0.5);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.03);
  color: var(--color-text-primary);
  padding: var(--spacing-3);
  text-align: left;
  cursor: pointer;
  transition: border-color 160ms ease, background-color 160ms ease;

  &:hover {
    border-color: rgba(200, 149, 90, 0.38);
    background: rgba(200, 149, 90, 0.08);
  }

  strong,
  small,
  .chip-time {
    display: block;
  }

  strong {
    margin-top: 4px;
    overflow: hidden;
    font-size: 14px;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    margin-top: 4px;
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  &.on-sale {
    border-left-color: var(--color-success);
  }

  &.cancelled,
  &.sold-out {
    border-left-color: var(--color-error);
  }

  &.preparing,
  &.coming-soon,
  &.published {
    border-left-color: var(--color-warning);
  }
}

.chip-time {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.chip-progress {
  height: 5px;
  margin-top: var(--spacing-2);
  overflow: hidden;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.08);

  span {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: var(--color-warning);
  }
}

.schedule-workspace {
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

.schedule-show-cell,
.venue-cell,
.time-stack,
.status-stack {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.show-title,
.venue-cell strong,
.time-stack strong {
  overflow: hidden;
  color: var(--color-text-primary);
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.show-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }
}

.venue-cell span,
.time-stack span {
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-stack {
  font-variant-numeric: tabular-nums;
}

.sales-cell {
  min-width: 0;
  display: grid;
  gap: 7px;

  :deep(.el-progress-bar__outer) {
    background-color: rgba(255, 255, 255, 0.08);
  }
}

.ticket-stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-2);
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.schedule-form {
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

.field-hint {
  margin-top: 6px;
  color: var(--color-warning);
  font-size: 12px;
}

:deep(.schedule-dialog) {
  max-width: calc(100vw - 32px);
}

@media (max-width: 1120px) {
  .metric-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .schedule-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-fields {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .schedule-workspace {
    padding: var(--spacing-3);
  }

  .view-switcher,
  .toolbar-fields,
  .search-input,
  .compact-filter,
  .date-filter,
  .sort-filter {
    width: 100%;
  }

  .schedule-form {
    .form-grid {
      grid-template-columns: 1fr;
    }

    .span-2 {
      grid-column: auto;
    }
  }
}
</style>
