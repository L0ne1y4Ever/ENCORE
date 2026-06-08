<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AdminSeatMapEditor from '../../components/AdminSeatMapEditor.vue'
import {
  createAdminHall,
  createAdminLayout,
  createAdminVenue,
  getAdminHalls,
  getAdminLayoutAreas,
  getAdminLayouts,
  getAdminLayoutSeats,
  getAdminSchedules,
  getAdminVenues,
  syncAdminLayoutSeatStatus,
  updateAdminHall,
  updateAdminLayout,
  updateAdminLayoutSeatStatus,
  updateAdminLayoutStatus,
  updateAdminVenue
} from '../../api/admin'
import type {
  AdminHall,
  AdminHallPayload,
  AdminLayout,
  AdminLayoutArea,
  AdminLayoutPayload,
  AdminLayoutSeat,
  AdminSchedule,
  AdminVenue,
  AdminVenuePayload,
  HallType,
  LayoutStatus,
  SeatStatus,
  TicketMode
} from '../../api/admin'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

type DialogMode = 'create' | 'edit'
type WorkspaceTab = 'venues' | 'layouts'

const venues = ref<AdminVenue[]>([])
const halls = ref<AdminHall[]>([])
const layouts = ref<AdminLayout[]>([])
const areas = ref<AdminLayoutArea[]>([])
const seats = ref<AdminLayoutSeat[]>([])

const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const selectedVenueId = ref('')
const selectedHallId = ref('')
const selectedLayout = ref<AdminLayout | null>(null)
const activeTab = ref<WorkspaceTab>(route.query.tab === 'layouts' ? 'layouts' : 'venues')

const venueDialogVisible = ref(false)
const hallDialogVisible = ref(false)
const layoutDialogVisible = ref(false)
const venueDialogMode = ref<DialogMode>('create')
const hallDialogMode = ref<DialogMode>('create')
const layoutDialogMode = ref<DialogMode>('create')

const operatingId = ref('')
const operatingSeat = ref('')
const syncDialogVisible = ref(false)
const syncLoading = ref(false)
const syncing = ref(false)
const syncSchedules = ref<AdminSchedule[]>([])
const selectedSyncScheduleIds = ref<string[]>([])

const venueForm = reactive({
  id: '',
  name: '',
  city: '',
  address: '',
  status: 'ACTIVE'
})

const hallForm = reactive({
  id: '',
  venueId: '',
  name: '',
  hallType: 'THEATER' as HallType,
  capacity: 150,
  clearanceMinutes: 30,
  defaultLayoutId: '',
  status: 'ACTIVE'
})

const layoutForm = reactive({
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

const selectedVenue = computed(() => venues.value.find(venue => venue.id === selectedVenueId.value) || null)
const selectedHall = computed(() => halls.value.find(hall => hall.id === selectedHallId.value) || null)
const filteredHalls = computed(() => {
  if (!selectedVenueId.value) return halls.value
  return halls.value.filter(hall => hall.venueId === selectedVenueId.value)
})
const selectedVenueHallIds = computed(() => new Set(filteredHalls.value.map(hall => hall.id)))
const visibleLayouts = computed(() => {
  if (selectedHallId.value) return layouts.value.filter(layout => layout.hallId === selectedHallId.value)
  if (selectedVenueId.value) return layouts.value.filter(layout => selectedVenueHallIds.value.has(layout.hallId))
  return layouts.value
})
const selectedVenueName = computed(() => selectedVenue.value?.name || t('admin.allVenues'))
const selectedHallName = computed(() => {
  if (!selectedHall.value) return t('admin.allHalls')
  return `${selectedHall.value.venueName} / ${selectedHall.value.name}`
})
const totalCapacity = computed(() => halls.value.reduce((sum, hall) => sum + Number(hall.capacity || 0), 0))
const publishedLayoutCount = computed(() => layouts.value.filter(layout => layout.status === 'PUBLISHED').length)

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

watch(() => route.query.tab, tab => {
  activeTab.value = tab === 'layouts' ? 'layouts' : 'venues'
})

watch(activeTab, tab => {
  const query = { ...route.query }
  if (tab === 'layouts') query.tab = 'layouts'
  else delete query.tab
  router.replace({ path: route.path, query })
})

const resetVenueForm = () => {
  Object.assign(venueForm, {
    id: '',
    name: '',
    city: '',
    address: '',
    status: 'ACTIVE'
  })
}

const resetHallForm = () => {
  Object.assign(hallForm, {
    id: '',
    venueId: selectedVenueId.value || venues.value[0]?.id || '',
    name: '',
    hallType: 'THEATER',
    capacity: 150,
    clearanceMinutes: 30,
    defaultLayoutId: '',
    status: 'ACTIVE'
  })
}

const resetLayoutForm = () => {
  Object.assign(layoutForm, {
    id: '',
    hallId: selectedHallId.value || filteredHalls.value[0]?.id || halls.value[0]?.id || '',
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

const ensureLayoutSelection = async () => {
  const rows = visibleLayouts.value
  if (rows.length === 0) {
    selectedLayout.value = null
    areas.value = []
    seats.value = []
    return
  }
  if (!selectedLayout.value || !rows.some(layout => layout.id === selectedLayout.value?.id)) {
    await selectLayout(rows[0])
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const [venueRows, hallRows, layoutRows] = await Promise.all([
      getAdminVenues(),
      getAdminHalls(),
      getAdminLayouts()
    ])
    venues.value = venueRows
    halls.value = hallRows
    layouts.value = layoutRows

    if (!selectedVenueId.value || !venueRows.some(venue => venue.id === selectedVenueId.value)) {
      selectedVenueId.value = venueRows[0]?.id || ''
    }
    const hallCandidates = selectedVenueId.value
      ? hallRows.filter(hall => hall.venueId === selectedVenueId.value)
      : hallRows
    if (!selectedHallId.value || !hallCandidates.some(hall => hall.id === selectedHallId.value)) {
      selectedHallId.value = hallCandidates[0]?.id || ''
    }
    await ensureLayoutSelection()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

const selectVenue = async (venue?: AdminVenue) => {
  selectedVenueId.value = venue?.id || ''
  const hallRows = filteredHalls.value
  if (!hallRows.some(hall => hall.id === selectedHallId.value)) {
    selectedHallId.value = hallRows[0]?.id || ''
  }
  await ensureLayoutSelection()
}

const selectHall = async (hall: AdminHall) => {
  selectedVenueId.value = hall.venueId
  selectedHallId.value = hall.id
  activeTab.value = 'layouts'
  await ensureLayoutSelection()
}

const onHallFilterChange = async () => {
  const hall = selectedHall.value
  if (hall) selectedVenueId.value = hall.venueId
  await ensureLayoutSelection()
}

const openCreateVenue = () => {
  resetVenueForm()
  venueDialogMode.value = 'create'
  venueDialogVisible.value = true
}

const openEditVenue = (venue: AdminVenue) => {
  Object.assign(venueForm, {
    id: venue.id,
    name: venue.name,
    city: venue.city || '',
    address: venue.address || '',
    status: venue.status || 'ACTIVE'
  })
  venueDialogMode.value = 'edit'
  venueDialogVisible.value = true
}

const openCreateHall = () => {
  if (venues.value.length === 0) {
    ElMessage.error(t('admin.formRequired'))
    return
  }
  resetHallForm()
  hallDialogMode.value = 'create'
  hallDialogVisible.value = true
}

const openEditHall = (hall: AdminHall) => {
  Object.assign(hallForm, {
    id: hall.id,
    venueId: hall.venueId,
    name: hall.name,
    hallType: hall.hallType || 'THEATER',
    capacity: hall.capacity,
    clearanceMinutes: hall.clearanceMinutes,
    defaultLayoutId: hall.defaultLayoutId || '',
    status: hall.status || 'ACTIVE'
  })
  hallDialogMode.value = 'edit'
  hallDialogVisible.value = true
}

const openCreateLayout = () => {
  if (halls.value.length === 0) {
    ElMessage.error(t('admin.formRequired'))
    return
  }
  resetLayoutForm()
  layoutDialogMode.value = 'create'
  layoutDialogVisible.value = true
}

const openEditLayout = (layout: AdminLayout) => {
  Object.assign(layoutForm, {
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
  layoutDialogMode.value = 'edit'
  layoutDialogVisible.value = true
}

const saveVenue = async () => {
  if (!venueForm.name.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return
  }

  const payload: AdminVenuePayload = {
    name: venueForm.name.trim(),
    city: venueForm.city.trim() || null,
    address: venueForm.address.trim() || null,
    status: venueForm.status
  }

  saving.value = true
  try {
    const updated = venueDialogMode.value === 'create'
      ? await createAdminVenue(payload)
      : await updateAdminVenue(venueForm.id, payload)
    const index = venues.value.findIndex(venue => venue.id === updated.id)
    if (index >= 0) venues.value[index] = updated
    else venues.value.unshift(updated)
    selectedVenueId.value = updated.id
    venueDialogVisible.value = false
    ElMessage.success(t('admin.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}

const saveHall = async () => {
  if (!hallForm.venueId || !hallForm.name.trim() || hallForm.capacity < 0 || hallForm.clearanceMinutes < 0) {
    ElMessage.error(t('admin.formRequired'))
    return
  }

  const payload: AdminHallPayload = {
    venueId: hallForm.venueId,
    name: hallForm.name.trim(),
    hallType: hallForm.hallType,
    capacity: hallForm.capacity,
    clearanceMinutes: hallForm.clearanceMinutes,
    defaultLayoutId: hallForm.defaultLayoutId || null,
    status: hallForm.status
  }

  saving.value = true
  try {
    const updated = hallDialogMode.value === 'create'
      ? await createAdminHall(payload)
      : await updateAdminHall(hallForm.id, payload)
    const index = halls.value.findIndex(hall => hall.id === updated.id)
    if (index >= 0) halls.value[index] = updated
    else halls.value.unshift(updated)
    selectedVenueId.value = updated.venueId
    selectedHallId.value = updated.id
    hallDialogVisible.value = false
    ElMessage.success(t('admin.saved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    saving.value = false
  }
}

const saveLayout = async () => {
  if (!layoutForm.hallId || !layoutForm.name.trim()) {
    ElMessage.error(t('admin.formRequired'))
    return
  }

  const payload: AdminLayoutPayload = {
    hallId: layoutForm.hallId,
    name: layoutForm.name.trim(),
    ticketMode: layoutForm.ticketMode,
    status: layoutForm.status,
    seatRows: layoutForm.rows,
    seatCols: layoutForm.cols,
    vipPrice: layoutForm.vipPrice,
    standardPrice: layoutForm.standardPrice,
    economyPrice: layoutForm.economyPrice
  }

  saving.value = true
  try {
    const updated = layoutDialogMode.value === 'create'
      ? await createAdminLayout(payload)
      : await updateAdminLayout(layoutForm.id, {
        name: layoutForm.name.trim(),
        status: layoutForm.status
      })
    const index = layouts.value.findIndex(item => item.id === updated.id)
    if (index >= 0) layouts.value[index] = updated
    else layouts.value.unshift(updated)
    const hall = halls.value.find(item => item.id === updated.hallId)
    if (hall) selectedVenueId.value = hall.venueId
    selectedHallId.value = updated.hallId
    activeTab.value = 'layouts'
    layoutDialogVisible.value = false
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
    if (index >= 0) seats.value[index] = updated
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
  <div class="venues-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.venueLayout') }}</h1>
        <p>{{ t('admin.venueLayoutSubtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain :loading="loading" @click="loadData">{{ t('admin.refresh') }}</el-button>
        <el-button type="primary" @click="openCreateVenue">{{ t('admin.addVenue') }}</el-button>
        <el-button type="primary" @click="openCreateHall">{{ t('admin.addHall') }}</el-button>
        <el-button type="primary" @click="openCreateLayout">{{ t('admin.addLayout') }}</el-button>
      </div>
    </div>

    <div class="summary-strip">
      <div class="metric">
        <span>{{ t('admin.venues') }}</span>
        <strong>{{ venues.length }}</strong>
      </div>
      <div class="metric">
        <span>{{ t('admin.halls') }}</span>
        <strong>{{ halls.length }}</strong>
      </div>
      <div class="metric">
        <span>{{ t('admin.layouts') }}</span>
        <strong>{{ layouts.length }}</strong>
      </div>
      <div class="metric">
        <span>{{ t('admin.capacity') }}</span>
        <strong>{{ totalCapacity }}</strong>
      </div>
      <div class="metric">
        <span>{{ t('admin.publishedLayouts') }}</span>
        <strong>{{ publishedLayoutCount }}</strong>
      </div>
    </div>

    <div class="venue-workspace">
      <aside class="navigator-panel">
        <div class="navigator-head">
          <div>
            <h2>{{ t('admin.venueList') }}</h2>
            <p>{{ selectedVenueName }}</p>
          </div>
          <el-button link type="primary" @click="selectVenue()">{{ t('admin.allVenues') }}</el-button>
        </div>

        <div class="venue-list" v-loading="loading">
          <button
            v-for="venue in venues"
            :key="venue.id"
            type="button"
            class="navigator-item"
            :class="{ active: selectedVenueId === venue.id }"
            @click="selectVenue(venue)"
          >
            <strong>{{ venue.name }}</strong>
            <span>{{ venue.city || '-' }} · {{ venue.hallCount }} {{ t('admin.halls') }}</span>
          </button>
          <el-empty v-if="venues.length === 0 && !loading" :description="t('admin.empty')" />
        </div>

        <div class="navigator-head compact">
          <div>
            <h2>{{ t('admin.hallList') }}</h2>
            <p>{{ selectedHallName }}</p>
          </div>
        </div>

        <div class="hall-list">
          <button
            v-for="hall in filteredHalls"
            :key="hall.id"
            type="button"
            class="navigator-item hall-item"
            :class="{ active: selectedHallId === hall.id }"
            @click="selectHall(hall)"
          >
            <strong>{{ hall.name }}</strong>
            <span>{{ hall.hallType }} · {{ hall.capacity }} · {{ hall.layoutCount }} {{ t('admin.layouts') }}</span>
          </button>
          <el-empty v-if="filteredHalls.length === 0 && !loading" :description="t('admin.empty')" />
        </div>
      </aside>

      <section class="workspace-panel">
        <el-tabs v-model="activeTab" class="workspace-tabs">
          <el-tab-pane :label="t('admin.venues')" name="venues">
            <div class="tab-toolbar">
              <div>
                <h2>{{ t('admin.venueOperations') }}</h2>
                <p>{{ t('admin.venuesSubtitle') }}</p>
              </div>
            </div>

            <div class="management-grid">
              <div class="table-block">
                <div class="block-head">
                  <h3>{{ t('admin.venueList') }}</h3>
                </div>
                <el-table :data="venues" :empty-text="t('admin.empty')" v-loading="loading" row-key="id" @row-click="selectVenue">
                  <el-table-column prop="name" :label="t('admin.venueName')" min-width="160" />
                  <el-table-column prop="city" :label="t('admin.city')" width="100" />
                  <el-table-column :label="t('admin.halls')" width="86">
                    <template #default="{ row }">{{ row.hallCount }}</template>
                  </el-table-column>
                  <el-table-column :label="t('admin.action')" width="90">
                    <template #default="{ row }">
                      <el-button link type="primary" @click.stop="openEditVenue(row)">{{ t('admin.edit') }}</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="table-block">
                <div class="block-head">
                  <h3>{{ t('admin.hallList') }} · {{ selectedVenueName }}</h3>
                </div>
                <el-table :data="filteredHalls" :empty-text="t('admin.empty')" v-loading="loading" row-key="id" @row-click="selectHall">
                  <el-table-column prop="name" :label="t('admin.hallName')" min-width="150" />
                  <el-table-column prop="hallType" :label="t('admin.hallType')" width="105" />
                  <el-table-column prop="capacity" :label="t('admin.capacity')" width="88" />
                  <el-table-column prop="clearanceMinutes" :label="t('admin.clearanceMinutes')" width="122" />
                  <el-table-column prop="layoutCount" :label="t('admin.layouts')" width="86" />
                  <el-table-column :label="t('admin.action')" width="90">
                    <template #default="{ row }">
                      <el-button link type="primary" @click.stop="openEditHall(row)">{{ t('admin.edit') }}</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="t('admin.layouts')" name="layouts">
            <div class="tab-toolbar">
              <div>
                <h2>{{ t('admin.layoutOperations') }}</h2>
                <p>{{ t('admin.layoutsSubtitle') }}</p>
              </div>
              <el-select
                v-model="selectedHallId"
                class="hall-filter"
                clearable
                :placeholder="t('admin.allHalls')"
                @change="onHallFilterChange"
              >
                <el-option v-for="hall in halls" :key="hall.id" :label="`${hall.venueName} / ${hall.name}`" :value="hall.id" />
              </el-select>
            </div>

            <div class="layout-grid">
              <div class="layout-list">
                <div class="block-head">
                  <h3>{{ t('admin.layouts') }} · {{ selectedHallName }}</h3>
                </div>
                <el-table :data="visibleLayouts" :empty-text="t('admin.empty')" v-loading="loading" row-key="id" @row-click="selectLayout">
                  <el-table-column prop="name" :label="t('admin.layoutName')" min-width="180" />
                  <el-table-column :label="t('admin.ticketMode')" width="112">
                    <template #default="{ row }">
                      <el-tag size="small" :type="modeTagType(row.ticketMode)">{{ t(`ticketMode.${row.ticketMode?.toLowerCase()}`) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="version" :label="t('admin.version')" width="76" />
                  <el-table-column :label="t('admin.layoutStatus')" width="108">
                    <template #default="{ row }">
                      <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ row.status }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column :label="t('admin.action')" width="192">
                    <template #default="{ row }">
                      <el-button link type="primary" :disabled="operatingId === row.id" @click.stop="openEditLayout(row)">
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
              </div>

              <div class="layout-detail" v-loading="detailLoading">
                <template v-if="selectedLayout">
                  <div class="detail-head">
                    <div>
                      <h3>{{ selectedLayout.name }}</h3>
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
                    <el-table v-if="seats.length > 0" :data="seats" :empty-text="t('admin.empty')" size="small" max-height="320">
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
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>

    <el-dialog v-model="venueDialogVisible" :title="venueDialogMode === 'create' ? t('admin.addVenue') : t('admin.editVenue')" width="520px">
      <el-form label-position="top">
        <el-form-item :label="t('admin.venueName')" required>
          <el-input v-model="venueForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('admin.city')">
          <el-input v-model="venueForm.city" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('admin.address')">
          <el-input v-model="venueForm.address" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="venueDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveVenue">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="hallDialogVisible" :title="hallDialogMode === 'create' ? t('admin.addHall') : t('admin.editHall')" width="640px">
      <el-form label-position="top" class="dialog-grid-form">
        <el-form-item :label="t('admin.venueName')" required>
          <el-select v-model="hallForm.venueId" class="full-control" filterable>
            <el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.hallName')" required>
          <el-input v-model="hallForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('admin.hallType')" required>
          <el-select v-model="hallForm.hallType" class="full-control">
            <el-option label="THEATER" value="THEATER" />
            <el-option label="CINEMA" value="CINEMA" />
            <el-option label="STADIUM" value="STADIUM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.capacity')" required>
          <el-input-number v-model="hallForm.capacity" :min="0" class="full-control" />
        </el-form-item>
        <el-form-item :label="t('admin.clearanceMinutes')" required>
          <el-input-number v-model="hallForm.clearanceMinutes" :min="0" :max="180" class="full-control" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="hallDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveHall">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="layoutDialogVisible"
      :title="layoutDialogMode === 'create' ? t('admin.addLayout') : t('admin.editLayout')"
      width="640px"
      @closed="resetLayoutForm"
    >
      <el-form label-position="top" class="dialog-grid-form">
        <el-form-item :label="t('admin.hallName')" required>
          <el-select v-model="layoutForm.hallId" filterable class="full-control" :disabled="layoutDialogMode === 'edit'">
            <el-option v-for="hall in halls" :key="hall.id" :label="`${hall.venueName} / ${hall.name}`" :value="hall.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.layoutName')" required>
          <el-input v-model="layoutForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('admin.ticketMode')" required>
          <el-select v-model="layoutForm.ticketMode" class="full-control" :disabled="layoutDialogMode === 'edit'">
            <el-option value="SEATED" :label="t('ticketMode.seated')" />
            <el-option value="ZONED" :label="t('ticketMode.zoned')" />
            <el-option value="MIXED" :label="t('ticketMode.mixed')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.layoutStatus')" required>
          <el-select v-model="layoutForm.status" class="full-control">
            <el-option value="DRAFT" label="DRAFT" />
            <el-option value="PUBLISHED" label="PUBLISHED" />
          </el-select>
        </el-form-item>
        <div v-if="layoutDialogMode === 'edit'" class="edit-hint span-2">
          {{ t('admin.layoutEditHint') }}
        </div>
        <template v-if="layoutForm.ticketMode === 'SEATED' && layoutDialogMode === 'create'">
          <el-form-item :label="t('admin.seatRows')" required>
            <el-input-number v-model="layoutForm.rows" :min="1" :max="30" class="full-control" />
          </el-form-item>
          <el-form-item :label="t('admin.seatCols')" required>
            <el-input-number v-model="layoutForm.cols" :min="1" :max="40" class="full-control" />
          </el-form-item>
          <el-form-item :label="t('admin.vipPrice')" required>
            <el-input-number v-model="layoutForm.vipPrice" :min="1" :step="10" class="full-control" />
          </el-form-item>
          <el-form-item :label="t('admin.standardPrice')" required>
            <el-input-number v-model="layoutForm.standardPrice" :min="1" :step="10" class="full-control" />
          </el-form-item>
          <el-form-item :label="t('admin.economyPrice')" required>
            <el-input-number v-model="layoutForm.economyPrice" :min="1" :step="10" class="full-control" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="layoutDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveLayout">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="syncDialogVisible" :title="t('admin.syncLayoutSeats')" width="780px">
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
        <el-button type="primary" :loading="syncing" :disabled="selectedSyncScheduleIds.length === 0" @click="syncLayoutSeatStatus">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.venues-page {
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
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.summary-strip {
  margin-bottom: var(--spacing-4);
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: var(--spacing-3);
}

.metric {
  min-width: 0;
  min-height: 76px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  padding: var(--spacing-3);
  display: grid;
  gap: 4px;

  span {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 12px;
  }

  strong {
    color: var(--color-text-primary);
    font-family: var(--font-family-sans);
    font-size: 24px;
    line-height: 1.1;
  }
}

.venue-workspace {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: var(--spacing-4);
  align-items: start;
}

.navigator-panel,
.workspace-panel {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
}

.navigator-panel {
  padding: var(--spacing-3);
  display: grid;
  gap: var(--spacing-3);
  position: sticky;
  top: var(--spacing-4);
}

.navigator-head {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-2);

  h2 {
    font-size: 15px;
  }

  p {
    margin-top: 4px;
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  &.compact {
    padding-top: var(--spacing-2);
    border-top: 1px solid var(--color-border);
  }
}

.venue-list,
.hall-list {
  display: grid;
  gap: 8px;
}

.navigator-item {
  width: 100%;
  min-height: 58px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.02);
  padding: var(--spacing-2);
  display: grid;
  gap: 4px;
  color: var(--color-text-primary);
  cursor: pointer;
  text-align: left;
  transition: background-color 160ms ease, border-color 160ms ease;

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
  }

  span {
    overflow: hidden;
    color: var(--color-text-secondary);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &:hover {
    border-color: var(--color-border-strong);
    background: rgba(255, 255, 255, 0.04);
  }

  &.active {
    border-color: rgba(200, 149, 90, 0.38);
    background: rgba(200, 149, 90, 0.1);
  }
}

.hall-item {
  min-height: 52px;
}

.workspace-panel {
  padding: 0 var(--spacing-4) var(--spacing-4);
}

.workspace-tabs {
  :deep(.el-tabs__header) {
    margin: 0 0 var(--spacing-4);
  }

  :deep(.el-tabs__nav-wrap::after) {
    background-color: var(--color-border);
  }
}

.tab-toolbar {
  margin-bottom: var(--spacing-4);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-3);

  h2 {
    font-size: 18px;
  }

  p {
    margin-top: 4px;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.hall-filter {
  width: 280px;
}

.management-grid,
.layout-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(420px, 1.15fr);
  gap: var(--spacing-4);
}

.table-block,
.layout-list,
.layout-detail {
  min-width: 0;
}

.block-head {
  min-height: 36px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  h3 {
    font-size: 15px;
  }
}

.detail-head {
  min-height: 48px;
  margin-bottom: var(--spacing-3);
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-3);

  h3 {
    font-size: 16px;
  }

  p {
    margin-top: 4px;
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}

.detail-actions {
  display: grid;
  justify-items: end;
  gap: var(--spacing-2);
}

.detail-stats {
  display: flex;
  gap: var(--spacing-2);
  color: var(--color-text-secondary);
  font-size: 12px;
}

.preview-band {
  border: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.02);
  border-radius: var(--radius-md);
  padding: var(--spacing-3);
}

.area-preview {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-3);
}

.area-shape {
  min-height: 72px;
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
  margin-top: var(--spacing-3);
  display: grid;
  gap: var(--spacing-3);
}

.dialog-grid-form {
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

:deep(.el-table) {
  background-color: transparent;
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.02);
  --el-table-header-text-color: var(--color-text-secondary);
  --el-table-text-color: var(--color-text-primary);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
}

@media (max-width: 1280px) {
  .summary-strip {
    grid-template-columns: repeat(3, minmax(120px, 1fr));
  }

  .venue-workspace,
  .management-grid,
  .layout-grid {
    grid-template-columns: 1fr;
  }

  .navigator-panel {
    position: static;
  }
}

@media (max-width: 760px) {
  .page-header,
  .tab-toolbar,
  .detail-head {
    display: grid;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hall-filter {
    width: 100%;
  }

  .area-preview,
  .dialog-grid-form {
    grid-template-columns: 1fr;
  }
}
</style>
