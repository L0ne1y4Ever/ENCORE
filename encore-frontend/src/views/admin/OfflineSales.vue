<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import ScheduleSeatMap2D from '../../components/ScheduleSeatMap2D.vue'
import { subscribeToSeatUpdates } from '../../api/seatRealtime'
import type { SeatStatusEvent } from '../../api/seatRealtime'
import {
  createAdminOfflineSale,
  getAdminSchedules
} from '../../api/admin'
import type {
  AdminOfflineSaleResponse,
  AdminSchedule,
  AdminScheduleInventory,
  AdminScheduleInventoryArea,
  AdminScheduleInventorySeat
} from '../../api/admin'
import { getScheduleAreas, getSeatMap } from '../../api/seat'
import { formatMoney, toBaseAmount } from '../../utils/money'

type SaleMode = 'SEATED' | 'AREA'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const schedules = ref<AdminSchedule[]>([])
const inventory = ref<AdminScheduleInventory | null>(null)
const selectedScheduleId = ref('')
const saleMode = ref<SaleMode>('SEATED')
const selectedSeatIds = ref<string[]>([])
const selectedAreaId = ref('')
const quantity = ref(1)
const loading = ref(false)
const inventoryLoading = ref(false)
const submitting = ref(false)
const successVisible = ref(false)
const saleResult = ref<AdminOfflineSaleResponse | null>(null)
const keyword = ref('')
let disconnectRealtime: (() => void) | undefined
let inventoryRefreshTimer: ReturnType<typeof setTimeout> | undefined
let liveSyncTimer: ReturnType<typeof setInterval> | undefined

const buyerForm = reactive({
  username: '',
  displayName: ''
})

const money = (value: number | string | undefined | null) => formatMoney(value, locale.value)
const toAmount = (value: number | string | undefined | null) => toBaseAmount(value)

const formatDate = (value?: string | null) => value ? new Date(value).toLocaleString() : '-'

const scheduleLabel = (schedule: AdminSchedule) => {
  return `${schedule.showTitle} · ${schedule.hallName || schedule.theaterName} · ${formatDate(schedule.startTime)}`
}

const isFutureDate = (value?: string | null) => {
  if (!value) return false
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? true : time > Date.now()
}

const isPastDate = (value?: string | null) => {
  if (!value) return false
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? false : time < Date.now()
}

const isConcertCategory = (category?: string | null) => {
  const value = String(category || '').trim().toLowerCase()
  return value === 'concert' || value.includes('演唱会')
}

const isSellableSchedule = (schedule: AdminSchedule) => {
  return schedule.status === 'ON_SALE'
    && schedule.publishStatus === 'PUBLISHED'
    && !isConcertCategory(schedule.category)
    && !isFutureDate(schedule.saleStartTime)
    && !isPastDate(schedule.saleEndTime)
    && !isPastDate(schedule.endTime)
}

const isCounterVisibleSchedule = (schedule: AdminSchedule) => {
  return schedule.status === 'ON_SALE'
    && schedule.publishStatus === 'PUBLISHED'
    && !isFutureDate(schedule.saleStartTime)
    && !isPastDate(schedule.saleEndTime)
    && !isPastDate(schedule.endTime)
}

const filteredSchedules = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return schedules.value
    .filter(isCounterVisibleSchedule)
    .filter(schedule => {
      if (!key) return true
      return [
        schedule.showTitle,
        schedule.theaterName,
        schedule.hallName,
        schedule.priceRange,
        schedule.id
      ].filter(Boolean).join(' ').toLowerCase().includes(key)
    })
    .sort((left, right) => left.startTime.localeCompare(right.startTime))
})

const offlineAvailableSchedules = computed(() => filteredSchedules.value.filter(isSellableSchedule))

const selectedSchedule = computed(() => schedules.value.find(item => item.id === selectedScheduleId.value) || null)
const seats = computed(() => inventory.value?.seats || [])
const areas = computed(() => (inventory.value?.areas || []).filter(area => !area.isSeated))
const availableSeats = computed(() => seats.value.filter(seat => seat.status === 'AVAILABLE'))
const availableAreas = computed(() => areas.value.filter(area => area.status === 'AVAILABLE' && area.availableCount > 0))
const canSellSeats = computed(() => !!inventory.value && ['SEATED', 'MIXED'].includes(String(inventory.value.ticketMode)) && seats.value.length > 0)
const canSellAreas = computed(() => !!inventory.value && ['ZONED', 'MIXED'].includes(String(inventory.value.ticketMode)) && availableAreas.value.length > 0)

const selectedSeats = computed(() => {
  const selected = new Set(selectedSeatIds.value)
  return seats.value.filter(seat => selected.has(seat.id))
})

const selectedArea = computed(() => areas.value.find(area => area.id === selectedAreaId.value) || null)

const totalAmount = computed(() => {
  if (saleMode.value === 'AREA') {
    return selectedArea.value ? toAmount(selectedArea.value.price) * quantity.value : 0
  }
  return selectedSeats.value.reduce((sum, seat) => sum + toAmount(seat.price), 0)
})

const canSubmit = computed(() => {
  if (!selectedSchedule.value || !isSellableSchedule(selectedSchedule.value) || submitting.value || totalAmount.value <= 0) return false
  if (saleMode.value === 'AREA') return !!selectedArea.value && quantity.value >= 1
  return selectedSeats.value.length > 0
})

const scheduleSaleNote = (schedule: AdminSchedule) => {
  if (isConcertCategory(schedule.category)) return t('admin.onlineOnlySchedule')
  if (!isSellableSchedule(schedule)) return t('admin.notCounterSellable')
  return ''
}

const resetSelection = () => {
  selectedSeatIds.value = []
  selectedAreaId.value = ''
  quantity.value = 1
}

const isAreaSellable = (area: AdminScheduleInventoryArea | undefined | null) => {
  return Boolean(area && area.status === 'AVAILABLE' && area.availableCount > 0)
}

const chooseDefaultMode = () => {
  if (canSellSeats.value) {
    saleMode.value = 'SEATED'
  } else if (canSellAreas.value) {
    saleMode.value = 'AREA'
  }
  if (saleMode.value === 'AREA' && !selectedAreaId.value && availableAreas.value.length > 0) {
    selectedAreaId.value = availableAreas.value[0].id
  }
}

const keepValidSelection = (previousSeatIds: Set<string>, previousAreaId: string, previousQuantity: number) => {
  const availableSeatIds = new Set(seats.value.filter(seat => seat.status === 'AVAILABLE').map(seat => seat.id))
  selectedSeatIds.value = Array.from(previousSeatIds).filter(seatId => availableSeatIds.has(seatId))
  selectedAreaId.value = previousAreaId
  quantity.value = previousQuantity

  if (saleMode.value === 'SEATED' && !canSellSeats.value) {
    saleMode.value = canSellAreas.value ? 'AREA' : 'SEATED'
    selectedSeatIds.value = []
  }

  if (saleMode.value === 'AREA') {
    const area = areas.value.find(item => item.id === selectedAreaId.value)
    if (!isAreaSellable(area)) {
      selectedAreaId.value = availableAreas.value[0]?.id || ''
      quantity.value = 1
    }
    clampQuantity()
  }
}

const calculateInventoryStats = (seatRows: AdminScheduleInventorySeat[], areaRows: AdminScheduleInventoryArea[]) => {
  const nonSeatedAreas = areaRows.filter(area => !area.isSeated)
  return {
    totalSeats: seatRows.length + nonSeatedAreas.reduce((sum, area) => sum + Number(area.totalCount || 0), 0),
    availableSeats: seatRows.filter(seat => seat.status === 'AVAILABLE').length
      + nonSeatedAreas.reduce((sum, area) => sum + Number(area.availableCount || 0), 0),
    lockedSeats: seatRows.filter(seat => seat.status === 'LOCKED').length
      + nonSeatedAreas.reduce((sum, area) => sum + Number(area.lockedCount || 0), 0),
    soldSeats: seatRows.filter(seat => seat.status === 'SOLD').length
      + nonSeatedAreas.reduce((sum, area) => sum + Number(area.soldCount || 0), 0),
    disabledSeats: seatRows.filter(seat => seat.status === 'DISABLED').length
  }
}

const syncSelectedScheduleStats = () => {
  if (!inventory.value) return
  schedules.value = schedules.value.map(schedule => {
    if (schedule.id !== inventory.value?.scheduleId) return schedule
    return {
      ...schedule,
      totalSeats: inventory.value.totalSeats,
      availableSeats: inventory.value.availableSeats,
      lockedSeats: inventory.value.lockedSeats,
      soldSeats: inventory.value.soldSeats,
      disabledSeats: inventory.value.disabledSeats
    }
  })
}

const loadInventory = async (options: { preserveSelection?: boolean; silent?: boolean } = {}) => {
  if (!selectedScheduleId.value) {
    inventory.value = null
    return
  }
  const previousSeatIds = new Set(selectedSeatIds.value)
  const previousAreaId = selectedAreaId.value
  const previousQuantity = quantity.value
  if (!options.silent) {
    inventoryLoading.value = true
  }
  try {
    const schedule = selectedSchedule.value
    const [seatRows, areaRows] = await Promise.all([
      getSeatMap(selectedScheduleId.value),
      getScheduleAreas(selectedScheduleId.value)
    ])
    const mappedSeats = seatRows.map<AdminScheduleInventorySeat>(seat => ({
      id: seat.id,
      row: seat.row,
      col: seat.col,
      section: seat.section,
      status: seat.status,
      price: seat.price
    }))
    const mappedAreas = areaRows.map<AdminScheduleInventoryArea>(area => ({
      id: area.id,
      areaId: area.areaId,
      name: area.name,
      code: area.code,
      areaType: area.areaType,
      isSeated: area.isSeated,
      price: area.price,
      totalCount: area.totalCount,
      availableCount: area.availableCount,
      lockedCount: area.lockedCount,
      soldCount: area.soldCount,
      status: area.status || 'AVAILABLE',
      color: area.color,
      description: area.description,
      positionData: area.positionData
    }))
    const stats = calculateInventoryStats(mappedSeats, mappedAreas)
    inventory.value = {
      scheduleId: selectedScheduleId.value,
      showTitle: schedule?.showTitle || '',
      theaterName: schedule?.theaterName || schedule?.hallName || '',
      ticketMode: schedule?.ticketMode || 'SEATED',
      ...stats,
      seats: mappedSeats,
      areas: mappedAreas
    }
    syncSelectedScheduleStats()
    if (options.preserveSelection) {
      keepValidSelection(previousSeatIds, previousAreaId, previousQuantity)
    } else {
      resetSelection()
      chooseDefaultMode()
    }
  } catch (error) {
    if (!options.silent) {
      inventory.value = null
      ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
    }
  } finally {
    if (!options.silent) {
      inventoryLoading.value = false
    }
  }
}

const scheduleInventoryRefresh = () => {
  if (!selectedScheduleId.value) return
  if (inventoryRefreshTimer) {
    clearTimeout(inventoryRefreshTimer)
  }
  inventoryRefreshTimer = setTimeout(() => {
    void loadInventory({ preserveSelection: true, silent: true })
  }, 400)
}

const startInventoryPolling = () => {
  stopInventoryPolling()
  liveSyncTimer = setInterval(() => {
    void loadInventory({ preserveSelection: true, silent: true })
  }, 5000)
}

const stopInventoryPolling = () => {
  if (liveSyncTimer) {
    clearInterval(liveSyncTimer)
    liveSyncTimer = undefined
  }
}

const applyRealtimeEvent = (event: SeatStatusEvent) => {
  if (!inventory.value) return
  let removedSelectedSeat = false
  let adjustedArea = false

  if (event.seats.length > 0) {
    const nextStatusBySeat = new Map(event.seats.map(seat => [seat.seatId, seat.status]))
    const nextSeats = inventory.value.seats.map((seat) => {
      const nextStatus = nextStatusBySeat.get(seat.id)
      if (!nextStatus) return seat
      if (nextStatus !== 'AVAILABLE' && selectedSeatIds.value.includes(seat.id)) {
        selectedSeatIds.value = selectedSeatIds.value.filter(seatId => seatId !== seat.id)
        removedSelectedSeat = true
      }
      return { ...seat, status: nextStatus }
    })
    inventory.value = {
      ...inventory.value,
      seats: nextSeats,
      ...calculateInventoryStats(nextSeats, inventory.value.areas)
    }
  }

  if (event.areas && event.areas.length > 0) {
    const changeByArea = new Map(event.areas.map(area => [area.areaId, area]))
    inventory.value = {
      ...inventory.value,
      areas: inventory.value.areas.map((area) => {
        const change = changeByArea.get(area.areaId)
        if (!change) return area
        return {
          ...area,
          availableCount: change.availableCount ?? area.availableCount,
          lockedCount: change.lockedCount ?? area.lockedCount,
          soldCount: change.soldCount ?? area.soldCount,
          status: change.status ?? area.status
        }
      })
    }

    const currentArea = selectedArea.value
    if (currentArea && changeByArea.has(currentArea.areaId)) {
      if (!isAreaSellable(selectedArea.value)) {
        selectedAreaId.value = availableAreas.value[0]?.id || ''
        quantity.value = 1
      } else {
        clampQuantity()
      }
      adjustedArea = true
    }
    inventory.value = {
      ...inventory.value,
      ...calculateInventoryStats(inventory.value.seats, inventory.value.areas)
    }
  }

  syncSelectedScheduleStats()

  if (removedSelectedSeat || adjustedArea) {
    ElMessage.warning(t('seat.liveUpdated'))
  }
  scheduleInventoryRefresh()
}

const stopRealtime = () => {
  disconnectRealtime?.()
  disconnectRealtime = undefined
  stopInventoryPolling()
}

const startRealtime = (scheduleId: string) => {
  stopRealtime()
  if (!scheduleId) return
  disconnectRealtime = subscribeToSeatUpdates(scheduleId, {
    onEvent: applyRealtimeEvent
  })
  startInventoryPolling()
}

const selectSchedule = async (scheduleId: string) => {
  const schedule = schedules.value.find(item => item.id === scheduleId)
  if (schedule && !isSellableSchedule(schedule)) {
    ElMessage.warning(scheduleSaleNote(schedule))
    return
  }
  if (selectedScheduleId.value === scheduleId) return
  selectedScheduleId.value = scheduleId
  await router.replace({ query: { ...route.query, scheduleId } })
}

const loadSchedules = async () => {
  loading.value = true
  try {
    schedules.value = await getAdminSchedules()
    const queryScheduleId = typeof route.query.scheduleId === 'string' ? route.query.scheduleId : ''
    const preferred = offlineAvailableSchedules.value.find(schedule => schedule.id === queryScheduleId)
      || offlineAvailableSchedules.value[0]
    if (preferred) {
      const previousScheduleId = selectedScheduleId.value
      selectedScheduleId.value = preferred.id
      await router.replace({ query: { ...route.query, scheduleId: preferred.id } })
      if (previousScheduleId === preferred.id) {
        await loadInventory()
      }
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.loadFailed'))
  } finally {
    loading.value = false
  }
}

const switchMode = (mode: SaleMode) => {
  saleMode.value = mode
  resetSelection()
  if (mode === 'AREA' && availableAreas.value.length > 0) {
    selectedAreaId.value = availableAreas.value[0].id
  }
}

const toggleSeat = (seat: AdminScheduleInventorySeat) => {
  if (seat.status !== 'AVAILABLE') return
  const exists = selectedSeatIds.value.includes(seat.id)
  if (exists) {
    selectedSeatIds.value = selectedSeatIds.value.filter(id => id !== seat.id)
    return
  }
  if (selectedSeatIds.value.length >= 6) {
    ElMessage.warning(t('admin.offlineSeatLimit'))
    return
  }
  selectedSeatIds.value = [...selectedSeatIds.value, seat.id]
}

const toggleSeatFromMap = (seat: { id: string }) => {
  const fullSeat = seats.value.find(item => item.id === seat.id)
  if (fullSeat) toggleSeat(fullSeat)
}

const clampQuantity = () => {
  const max = Math.min(4, selectedArea.value?.availableCount || 4)
  quantity.value = Math.max(1, Math.min(max, quantity.value || 1))
}

watch(selectedScheduleId, () => {
  startRealtime(selectedScheduleId.value)
  void loadInventory()
})

watch(selectedAreaId, clampQuantity)

onBeforeUnmount(() => {
  stopRealtime()
  if (inventoryRefreshTimer) {
    clearTimeout(inventoryRefreshTimer)
  }
})

const submitSale = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const result = await createAdminOfflineSale({
      scheduleId: selectedScheduleId.value,
      buyerUsername: buyerForm.username.trim() || null,
      buyerDisplayName: buyerForm.displayName.trim() || null,
      seatIds: saleMode.value === 'SEATED' ? selectedSeatIds.value : null,
      areaInventoryId: saleMode.value === 'AREA' ? selectedAreaId.value : null,
      quantity: saleMode.value === 'AREA' ? quantity.value : null
    })
    saleResult.value = result
    successVisible.value = true
    ElMessage.success(t('admin.offlineSaleSuccess'))
    await loadSchedules()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('admin.operationFailed'))
  } finally {
    submitting.value = false
  }
}

const receiptText = computed(() => {
  if (!saleResult.value) return ''
  const lines = [
    t('admin.offlineReceipt'),
    `${t('admin.orderId')}: ${saleResult.value.order.id}`,
    `${t('admin.showName')}: ${saleResult.value.order.showName}`,
    `${t('admin.venueAndLayout')}: ${saleResult.value.order.theaterName}`,
    `${t('admin.scheduleTime')}: ${formatDate(saleResult.value.order.startTime)}`,
    `${t('admin.totalAmount')}: ${money(saleResult.value.totalAmount)}`,
    ''
  ]
  saleResult.value.tickets.forEach((ticket, index) => {
    lines.push(`${index + 1}. ${ticket.seatLabel || ticket.seatId || ticket.areaName || '-'} · ${ticket.ticketCode} · ${ticket.holderDisplayName} · ${money(ticket.price)}`)
  })
  return lines.join('\n')
})

const fallbackCopy = (text: string) => {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(textarea)
  return ok
}

const copyReceipt = async () => {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(receiptText.value)
    } else if (!fallbackCopy(receiptText.value)) {
      throw new Error(t('admin.copyFailed'))
    }
    ElMessage.success(t('admin.copied'))
  } catch {
    if (fallbackCopy(receiptText.value)) {
      ElMessage.success(t('admin.copied'))
    } else {
      ElMessage.error(t('admin.copyFailed'))
    }
  }
}

const escapeHtml = (value: string) => value
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#39;')

const printReceipt = () => {
  const win = window.open('', '_blank', 'width=720,height=760')
  if (!win) {
    ElMessage.error(t('admin.printBlocked'))
    return
  }
  win.document.write(`
    <html>
      <head>
        <title>${escapeHtml(t('admin.offlineReceipt'))}</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; padding: 28px; color: #111; }
          pre { white-space: pre-wrap; font-size: 14px; line-height: 1.7; }
        </style>
      </head>
      <body><pre>${escapeHtml(receiptText.value)}</pre></body>
    </html>
  `)
  win.document.close()
  win.focus()
  win.print()
}

onMounted(loadSchedules)
</script>

<template>
  <div class="offline-sales-page">
    <div class="page-header">
      <div>
        <h1>{{ t('admin.offlineSales') }}</h1>
        <p>{{ t('admin.offlineSalesSubtitle') }}</p>
      </div>
      <el-button type="primary" plain :loading="loading" @click="loadSchedules">{{ t('admin.refresh') }}</el-button>
    </div>

    <div class="sale-workbench">
      <section class="panel schedule-panel">
        <div class="panel-head">
          <div>
            <h2>{{ t('admin.chooseSchedule') }}</h2>
            <span>{{ offlineAvailableSchedules.length }} {{ t('admin.onSaleSchedules') }}</span>
          </div>
        </div>
        <el-input
          v-model="keyword"
          :placeholder="t('admin.searchSchedules')"
          clearable
          class="schedule-search"
        />
        <div class="schedule-list" v-loading="loading">
          <button
            v-for="schedule in filteredSchedules"
            :key="schedule.id"
            type="button"
            class="schedule-card"
            :class="{ active: schedule.id === selectedScheduleId, disabled: !isSellableSchedule(schedule) }"
            :disabled="!isSellableSchedule(schedule)"
            @click="selectSchedule(schedule.id)"
          >
            <strong>{{ schedule.showTitle }}</strong>
            <span>{{ schedule.hallName || schedule.theaterName }}</span>
            <small>{{ formatDate(schedule.startTime) }}</small>
            <em v-if="isSellableSchedule(schedule)">{{ schedule.availableSeats }} {{ t('admin.availableSeats') }} · {{ t(`ticketMode.${String(schedule.ticketMode).toLowerCase()}`) }}</em>
            <em v-else>{{ scheduleSaleNote(schedule) }}</em>
          </button>
          <el-empty v-if="!loading && filteredSchedules.length === 0" :description="t('admin.noOfflineSchedules')" />
        </div>
      </section>

      <section class="panel selection-panel" v-loading="inventoryLoading">
        <div class="panel-head">
          <div>
            <h2>{{ selectedSchedule ? selectedSchedule.showTitle : t('admin.chooseTicket') }}</h2>
            <span v-if="selectedSchedule">{{ scheduleLabel(selectedSchedule) }}</span>
          </div>
          <el-radio-group
            v-if="canSellSeats || canSellAreas"
            v-model="saleMode"
            @change="switchMode(saleMode)"
          >
            <el-radio-button label="SEATED" :disabled="!canSellSeats">{{ t('admin.seatedTicket') }}</el-radio-button>
            <el-radio-button label="AREA" :disabled="!canSellAreas">{{ t('admin.areaTicket') }}</el-radio-button>
          </el-radio-group>
        </div>

        <template v-if="inventory && saleMode === 'SEATED'">
          <div class="seat-toolbar">
            <span>{{ t('admin.selectedSeats') }} {{ selectedSeatIds.length }} / 6</span>
            <span>{{ t('admin.availableSeats') }} {{ availableSeats.length }}</span>
          </div>
          <ScheduleSeatMap2D
            :seats="seats"
            :selected-seat-ids="selectedSeatIds"
            :stage-label="t('seat.stage')"
            @toggle-seat="toggleSeatFromMap"
          />
        </template>

        <template v-else-if="inventory && saleMode === 'AREA'">
          <div class="area-grid">
            <button
              v-for="area in availableAreas"
              :key="area.id"
              type="button"
              class="area-card"
              :class="{ active: area.id === selectedAreaId }"
              @click="selectedAreaId = area.id"
            >
              <strong>{{ area.name }}</strong>
              <span>{{ money(area.price) }}</span>
              <small>{{ t('admin.availableSeats') }} {{ area.availableCount }}</small>
            </button>
          </div>
          <div class="quantity-line" v-if="selectedArea">
            <span>{{ t('admin.ticketQuantity') }}</span>
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="Math.min(4, selectedArea.availableCount)"
              @change="clampQuantity"
            />
          </div>
        </template>

        <el-empty v-else-if="!inventoryLoading" :description="t('admin.noSellableInventory')" />
      </section>

      <section class="panel checkout-panel">
        <div class="panel-head">
          <div>
            <h2>{{ t('admin.checkout') }}</h2>
            <span>{{ t('admin.checkoutHint') }}</span>
          </div>
        </div>

        <el-form label-position="top" class="buyer-form">
          <el-form-item :label="t('admin.buyerUsername')">
            <el-input v-model="buyerForm.username" :placeholder="t('admin.buyerUsernamePlaceholder')" clearable />
          </el-form-item>
          <el-form-item :label="t('admin.guestDisplayName')">
            <el-input v-model="buyerForm.displayName" :placeholder="t('admin.guestDisplayNamePlaceholder')" clearable />
          </el-form-item>
        </el-form>

        <div class="summary-box">
          <div>
            <span>{{ t('admin.ticketCount') }}</span>
            <strong>{{ saleMode === 'AREA' ? quantity : selectedSeatIds.length }}</strong>
          </div>
          <div>
            <span>{{ t('admin.totalAmount') }}</span>
            <strong>{{ money(totalAmount) }}</strong>
          </div>
        </div>

        <div class="selected-list">
          <template v-if="saleMode === 'SEATED'">
            <span v-for="seat in selectedSeats" :key="seat.id">{{ seat.id }} · {{ money(seat.price) }}</span>
          </template>
          <template v-else-if="selectedArea">
            <span>{{ selectedArea.name }} × {{ quantity }} · {{ money(totalAmount) }}</span>
          </template>
          <em v-if="totalAmount <= 0">{{ t('admin.noTicketSelected') }}</em>
        </div>

        <el-button
          type="primary"
          size="large"
          class="submit-sale"
          :disabled="!canSubmit"
          :loading="submitting"
          @click="submitSale"
        >
          {{ t('admin.confirmOfflineSale') }}
        </el-button>
      </section>
    </div>

    <el-dialog v-model="successVisible" :title="t('admin.offlineSaleSuccess')" width="760px" class="receipt-dialog">
      <template v-if="saleResult">
        <div class="receipt-summary">
          <div><span>{{ t('admin.orderId') }}</span><strong>{{ saleResult.order.id }}</strong></div>
          <div><span>{{ t('admin.totalAmount') }}</span><strong>{{ money(saleResult.totalAmount) }}</strong></div>
          <div><span>{{ t('admin.ticketCount') }}</span><strong>{{ saleResult.tickets.length }}</strong></div>
        </div>
        <el-table :data="saleResult.tickets" max-height="320">
          <el-table-column :label="t('admin.ticketCode')" prop="ticketCode" min-width="170" />
          <el-table-column :label="t('admin.seatOrArea')" min-width="150">
            <template #default="{ row }">{{ row.seatLabel || row.seatId || row.areaName || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('admin.holder')" prop="holderDisplayName" min-width="120" />
          <el-table-column :label="t('admin.price')" width="110">
            <template #default="{ row }">{{ money(row.price) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="copyReceipt">{{ t('admin.copyReceipt') }}</el-button>
        <el-button @click="printReceipt">{{ t('admin.printReceipt') }}</el-button>
        <el-button type="primary" @click="successVisible = false">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.offline-sales-page {
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-6);

  h1 {
    font-family: var(--font-family-display);
    font-size: 32px;
  }

  p {
    margin-top: var(--spacing-2);
    color: var(--color-text-secondary);
  }
}

.sale-workbench {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(480px, 1fr) minmax(300px, 360px);
  gap: var(--spacing-4);
  align-items: start;
}

.panel {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg-elevated);
  padding: var(--spacing-4);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-4);

  h2 {
    font-size: 18px;
    color: var(--color-text-primary);
  }

  span {
    display: block;
    margin-top: 4px;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.45;
  }
}

.schedule-search {
  margin-bottom: var(--spacing-3);
}

.schedule-list {
  display: grid;
  gap: var(--spacing-2);
  max-height: min(68vh, 720px);
  overflow: auto;
  padding-right: 4px;
}

.schedule-card,
.area-card {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.03);
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color .18s ease, background .18s ease, transform .18s ease;

  &:hover {
    border-color: rgba(255, 255, 255, 0.28);
    transform: translateY(-1px);
  }

  &.active {
    border-color: rgba(222, 176, 116, 0.85);
    background: rgba(222, 176, 116, 0.12);
  }

  &.disabled,
  &:disabled {
    opacity: .52;
    cursor: not-allowed;
    transform: none;
  }
}

.schedule-card {
  display: grid;
  gap: 6px;
  padding: var(--spacing-3);

  strong,
  span,
  small,
  em {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span,
  small,
  em {
    color: var(--color-text-secondary);
    font-size: 12px;
    font-style: normal;
  }
}

.seat-toolbar {
  display: flex;
  justify-content: space-between;
  color: var(--color-text-secondary);
  font-size: 12px;
  margin-bottom: var(--spacing-3);
}

.stage-line {
  width: min(520px, 72%);
  margin: 0 auto var(--spacing-4);
  border-top: 2px solid rgba(222, 176, 116, 0.76);
  padding-top: 8px;
  color: #d8a66c;
  text-align: center;
  font-size: 11px;
  letter-spacing: .16em;
}

.seat-map {
  max-height: min(62vh, 680px);
  overflow: auto;
  display: grid;
  gap: 8px;
  justify-content: center;
  padding: var(--spacing-2) 0;
}

.seat-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.column-labels {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 2px 0;
  background: linear-gradient(180deg, rgba(13, 13, 18, 0.96), rgba(13, 13, 18, 0.78));

  &.bottom {
    position: static;
    background: transparent;
  }
}

.row-label {
  width: 32px;
  flex: 0 0 32px;
  color: var(--color-text-ghost);
  font-size: 12px;
  text-align: right;
}

.column-label {
  width: 28px;
  flex: 0 0 28px;
  color: var(--color-text-ghost);
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.seat-cell {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.07);
  color: var(--color-text-secondary);
  font-size: 10px;
  cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, background .16s ease;

  &.available:hover {
    border-color: rgba(222, 176, 116, 0.82);
    transform: translateY(-1px);
  }

  &.selected {
    border-color: rgba(222, 176, 116, 0.95);
    background: rgba(222, 176, 116, 0.28);
    color: #fff;
  }

  &.sold,
  &.locked,
  &.disabled {
    cursor: not-allowed;
    opacity: .78;
  }

  &.sold {
    border-color: rgba(229, 9, 20, 0.62);
    background:
      repeating-linear-gradient(
        135deg,
        rgba(229, 9, 20, 0.22) 0,
        rgba(229, 9, 20, 0.22) 4px,
        rgba(255, 255, 255, 0.04) 4px,
        rgba(255, 255, 255, 0.04) 8px
      );
    color: rgba(255, 210, 210, 0.92);
  }

  &.locked {
    border-color: rgba(222, 176, 116, 0.72);
    border-style: dashed;
    background: rgba(222, 176, 116, 0.12);
    color: rgba(255, 232, 190, 0.86);
  }

  &.disabled {
    border-style: dashed;
    background: rgba(255, 255, 255, 0.035);
    color: var(--color-text-ghost);
  }

  &.empty {
    pointer-events: none;
    cursor: default;
    opacity: 0;
    border-color: transparent;
    background: transparent;
  }
}

.area-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: var(--spacing-3);
}

.area-card {
  padding: var(--spacing-4);

  strong,
  span,
  small {
    display: block;
  }

  span {
    margin-top: 8px;
    color: #d8a66c;
    font-weight: 700;
  }

  small {
    margin-top: 6px;
    color: var(--color-text-secondary);
  }
}

.quantity-line {
  margin-top: var(--spacing-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  color: var(--color-text-secondary);
}

.checkout-panel {
  position: sticky;
  top: var(--spacing-4);
}

.buyer-form {
  margin-bottom: var(--spacing-4);
}

.summary-box {
  border: 1px solid rgba(222, 176, 116, 0.34);
  border-radius: var(--radius-sm);
  background: rgba(222, 176, 116, 0.08);
  padding: var(--spacing-3);
  display: grid;
  gap: var(--spacing-3);

  div {
    display: flex;
    justify-content: space-between;
    gap: var(--spacing-3);
  }

  span {
    color: var(--color-text-secondary);
  }

  strong {
    font-variant-numeric: tabular-nums;
  }
}

.selected-list {
  min-height: 96px;
  margin: var(--spacing-4) 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;

  span {
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    padding: 6px 8px;
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  em {
    color: var(--color-text-ghost);
    font-style: normal;
  }
}

.submit-sale {
  width: 100%;
}

.receipt-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-4);

  div {
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    padding: var(--spacing-3);
    background: rgba(255, 255, 255, 0.03);
  }

  span {
    display: block;
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 6px;
    word-break: break-all;
  }
}

@media (max-width: 1280px) {
  .sale-workbench {
    grid-template-columns: minmax(240px, 300px) minmax(420px, 1fr);
  }

  .checkout-panel {
    grid-column: 1 / -1;
    position: static;
  }
}

@media (max-width: 860px) {
  .page-header,
  .panel-head {
    flex-direction: column;
  }

  .sale-workbench {
    grid-template-columns: 1fr;
  }

  .receipt-summary {
    grid-template-columns: 1fr;
  }
}
</style>
