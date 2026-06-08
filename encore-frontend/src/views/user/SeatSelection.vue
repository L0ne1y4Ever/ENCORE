<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSeatMap, lockSeats, getScheduleAreas } from '../../api/seat'
import type { ScheduleAreaResponse } from '../../api/seat'
import {
  cancelGroupOrder,
  checkoutGroupOrder,
  createGroupOrder,
  getGroupOrder,
  joinGroupOrder,
  leaveGroupOrder
} from '../../api/groupOrder'
import type { GroupOrder, GroupOrderMember } from '../../api/groupOrder'
import { subscribeToSeatUpdates } from '../../api/seatRealtime'
import type { SeatRealtimeConnectionState, SeatStatusEvent } from '../../api/seatRealtime'
import type { Seat } from '../../mock/seats'
import { useI18n } from 'vue-i18n'
import SeatStagePreview from '../../components/SeatStagePreview.vue'
import ConcertVenuePreview from '../../components/ConcertVenuePreview.vue'
import AreaTicketPanel from '../../components/AreaTicketPanel.vue'
import TicketModeBadge from '../../components/TicketModeBadge.vue'
import { useAuthStore } from '../../stores/auth'
import { getScheduleDetail } from '../../api/show'
import { createOrder } from '../../api/order'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const scheduleId = route.params.id as string

const schedule = ref<any>(null)
const areas = ref<ScheduleAreaResponse[]>([])
const selectedArea = ref<ScheduleAreaResponse | null>(null)
const activeStandId = ref<string | null>(null)
const bookingZoned = ref(false)

const seats = ref<Seat[]>([])
const selectedSeatIds = ref<Set<string>>(new Set())
const loading = ref(true)
const locking = ref(false)
const groupBusy = ref(false)
const groupLoading = ref(false)
const groupError = ref('')
const groupCopied = ref(false)
const groupOrder = ref<GroupOrder | null>(null)
const now = ref(Date.now())
const realtimeState = ref<SeatRealtimeConnectionState>('connecting')
const realtimeNotice = ref<string | null>(null)
let disconnectRealtime: (() => void) | undefined
let realtimeNoticeTimer: ReturnType<typeof setTimeout> | undefined
let groupPollTimer: ReturnType<typeof setInterval> | undefined
let groupCountdownTimer: ReturnType<typeof setInterval> | undefined

const groupInviteCode = computed(() => {
  return typeof route.query.group === 'string' ? route.query.group : ''
})
const isGroupMode = computed(() => Boolean(groupInviteCode.value))
const currentUserId = computed(() => authStore.currentUser?.id || '')
const currentMember = computed(() => {
  return groupOrder.value?.members.find(member => member.userId === currentUserId.value) || null
})
const isGroupHost = computed(() => {
  return Boolean(groupOrder.value && groupOrder.value.hostUserId === currentUserId.value)
})
const groupInviteUrl = computed(() => {
  if (!groupInviteCode.value) return ''
  return `${window.location.origin}${route.path}?group=${groupInviteCode.value}`
})
function formatTime(secs: number) {
  const minutes = Math.floor(secs / 60).toString().padStart(2, '0')
  const seconds = (secs % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
}
const groupExpiresIn = computed(() => {
  if (!groupOrder.value?.expiresAt) return '--:--'
  const diff = Math.max(0, Math.floor((new Date(groupOrder.value.expiresAt).getTime() - now.value) / 1000))
  return formatTime(diff)
})

const refreshSeatMap = async (showLoading = false) => {
  if (showLoading) {
    loading.value = true
  }
  try {
    if (schedule.value?.ticketMode === 'MIXED' && activeStandId.value) {
      seats.value = await getSeatMap(scheduleId, activeStandId.value)
    } else {
      seats.value = await getSeatMap(scheduleId)
    }
  } finally {
    loading.value = false
  }
}

const loadAreas = async () => {
  try {
    areas.value = await getScheduleAreas(scheduleId)
  } catch (e) {
    console.error('Failed to load areas', e)
  }
}

const showRealtimeNotice = (messageKey: string) => {
  realtimeNotice.value = messageKey
  if (realtimeNoticeTimer) {
    clearTimeout(realtimeNoticeTimer)
  }
  realtimeNoticeTimer = setTimeout(() => {
    realtimeNotice.value = null
  }, 2500)
}

const applySeatStatusEvent = async (event: SeatStatusEvent) => {
  if (event.reason === 'CANCELLED') {
    selectedSeatIds.value.clear()
    showRealtimeNotice('seat.scheduleCancelled')
    await refreshSeatMap()
    return
  }

  if (event.areas && event.areas.length > 0) {
    const changeByArea = new Map(event.areas.map((area) => [area.areaId, area]))
    areas.value = areas.value.map((area) => {
      const change = changeByArea.get(area.areaId)
      if (!change) {
        return area
      }
      return {
        ...area,
        availableCount: change.availableCount ?? area.availableCount,
        lockedCount: change.lockedCount ?? area.lockedCount,
        soldCount: change.soldCount ?? area.soldCount,
        status: change.status ?? area.status
      }
    })
    if (selectedArea.value) {
      const change = changeByArea.get(selectedArea.value.areaId)
      if (change) {
        selectedArea.value = {
          ...selectedArea.value,
          availableCount: change.availableCount ?? selectedArea.value.availableCount,
          lockedCount: change.lockedCount ?? selectedArea.value.lockedCount,
          soldCount: change.soldCount ?? selectedArea.value.soldCount
        }
      }
    }
    showRealtimeNotice('seat.liveUpdated')
    return
  }

  if (event.seats.length === 0) {
    return
  }

  const nextStatusBySeat = new Map(event.seats.map((seat) => [seat.seatId, seat.status]))
  seats.value = seats.value.map((seat) => {
    const nextStatus = nextStatusBySeat.get(seat.id)
    if (!nextStatus) {
      return seat
    }

    if (
      nextStatus !== 'AVAILABLE' &&
      selectedSeatIds.value.has(seat.id) &&
      !(event.reason === 'LOCKED' && (locking.value || groupBusy.value || currentMember.value?.seatIds.includes(seat.id)))
    ) {
      selectedSeatIds.value.delete(seat.id)
    }

    return {
      ...seat,
      status: nextStatus
    }
  })

  // MIXED 下看台座位的真相源是 schedule_seat，座位事件后刷新区域聚合数，
  // 让体育场总览里看台的可售数与实际售出保持一致。
  if (schedule.value?.ticketMode === 'MIXED') {
    void loadAreas()
  }

  showRealtimeNotice('seat.liveUpdated')
}

const selectArea = async (area: ScheduleAreaResponse) => {
  selectedArea.value = area
  if (area.isSeated) {
    activeStandId.value = area.areaId
    await refreshSeatMap(true)
  }
}

const goBackToStadium = () => {
  activeStandId.value = null
  selectedArea.value = null
  selectedSeatIds.value.clear()
}

const handleBookZonedArea = async (qty: number) => {
  if (!selectedArea.value) return
  bookingZoned.value = true
  try {
    const orderId = await createOrder(
      scheduleId,
      null,
      selectedArea.value.id,
      qty
    )
    router.push(`/payment?id=${orderId}`)
  } catch (error: any) {
    alert(error?.message || t('order.createFailed'))
  } finally {
    bookingZoned.value = false
  }
}

const showGroupFeatures = computed(() => {
  return schedule.value?.ticketMode === 'SEATED' || (schedule.value?.ticketMode === 'MIXED' && activeStandId.value !== null)
})

onMounted(async () => {
  try {
    schedule.value = await getScheduleDetail(scheduleId)
  } catch (e) {
    console.error('Failed to load schedule detail', e)
  }

  if (schedule.value?.ticketMode === 'ZONED' || schedule.value?.ticketMode === 'MIXED') {
    await loadAreas()
    loading.value = false
  } else {
    await refreshSeatMap(true)
  }

  // 三种模式都订阅：SEATED/MIXED 用座位事件，ZONED/MIXED 还需区域库存事件。
  if (schedule.value) {
    disconnectRealtime = subscribeToSeatUpdates(scheduleId, {
      onEvent: (event) => {
        void applySeatStatusEvent(event)
      },
      onStateChange: (state) => {
        realtimeState.value = state
      }
    })
  }

  groupCountdownTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  if (groupInviteCode.value) {
    await loadGroupOrder(true)
    startGroupPolling()
  }
})

onBeforeUnmount(() => {
  disconnectRealtime?.()
  stopGroupPolling()
  if (realtimeNoticeTimer) {
    clearTimeout(realtimeNoticeTimer)
  }
  if (groupCountdownTimer) {
    clearInterval(groupCountdownTimer)
  }
})

const maxSelect = 6

const toggleSeat = (seat: Seat) => {
  if (selectedSeatIds.value.has(seat.id)) {
    selectedSeatIds.value.delete(seat.id)
    return
  }

  if (seat.status !== 'AVAILABLE') return

  if (selectedSeatIds.value.size < maxSelect) {
    selectedSeatIds.value.add(seat.id)
  }
}

watch(groupInviteCode, async (nextCode, previousCode) => {
  if (nextCode === previousCode) return
  stopGroupPolling()
  groupOrder.value = null
  groupError.value = ''
  groupCopied.value = false
  selectedSeatIds.value.clear()
  if (nextCode) {
    await loadGroupOrder(true)
    startGroupPolling()
  }
})

const syncSelectionFromGroup = (order: GroupOrder | null) => {
  const member = order?.members.find(item => item.userId === currentUserId.value)
  selectedSeatIds.value = new Set(member?.seatIds || [])
}

const loadGroupOrder = async (syncSelection = false) => {
  if (!groupInviteCode.value) return
  groupLoading.value = true
  try {
    const data = await getGroupOrder(groupInviteCode.value)
    groupOrder.value = data
    groupError.value = ''
    if (syncSelection) {
      syncSelectionFromGroup(data)
    }
  } catch (error) {
    groupOrder.value = null
    groupError.value = error instanceof Error ? error.message : t('seat.group.loadFailed')
  } finally {
    groupLoading.value = false
  }
}

const startGroupPolling = () => {
  stopGroupPolling()
  groupPollTimer = setInterval(() => {
    void loadGroupOrder(false)
  }, 5000)
}

const stopGroupPolling = () => {
  if (groupPollTimer) {
    clearInterval(groupPollTimer)
    groupPollTimer = undefined
  }
}

const startGroupOrder = async () => {
  if (selectedSeatIds.value.size === 0) return
  groupBusy.value = true
  groupError.value = ''
  try {
    const data = await createGroupOrder(scheduleId, Array.from(selectedSeatIds.value))
    groupOrder.value = data
    syncSelectionFromGroup(data)
    await router.replace({ path: route.path, query: { ...route.query, group: data.inviteCode } })
    startGroupPolling()
  } catch (error) {
    groupError.value = error instanceof Error ? error.message : t('seat.group.actionFailed')
    await refreshSeatMap()
  } finally {
    groupBusy.value = false
  }
}

const joinCurrentGroup = async () => {
  if (!groupInviteCode.value || selectedSeatIds.value.size === 0) return
  groupBusy.value = true
  groupError.value = ''
  try {
    const data = await joinGroupOrder(groupInviteCode.value, Array.from(selectedSeatIds.value))
    groupOrder.value = data
    syncSelectionFromGroup(data)
    await refreshSeatMap()
  } catch (error) {
    groupError.value = error instanceof Error ? error.message : t('seat.group.actionFailed')
    await refreshSeatMap()
  } finally {
    groupBusy.value = false
  }
}

const leaveCurrentGroup = async () => {
  if (!groupInviteCode.value) return
  groupBusy.value = true
  groupError.value = ''
  try {
    await leaveGroupOrder(groupInviteCode.value)
    groupOrder.value = null
    selectedSeatIds.value.clear()
    stopGroupPolling()
    await router.replace({ path: route.path, query: { ...route.query, group: undefined } })
    await refreshSeatMap()
  } catch (error) {
    groupError.value = error instanceof Error ? error.message : t('seat.group.actionFailed')
  } finally {
    groupBusy.value = false
  }
}

const cancelCurrentGroup = async () => {
  if (!groupInviteCode.value) return
  groupBusy.value = true
  groupError.value = ''
  try {
    await cancelGroupOrder(groupInviteCode.value)
    groupOrder.value = null
    selectedSeatIds.value.clear()
    stopGroupPolling()
    await router.replace({ path: route.path, query: { ...route.query, group: undefined } })
    await refreshSeatMap()
  } catch (error) {
    groupError.value = error instanceof Error ? error.message : t('seat.group.actionFailed')
  } finally {
    groupBusy.value = false
  }
}

const checkoutCurrentGroup = async () => {
  if (!groupInviteCode.value) return
  groupBusy.value = true
  groupError.value = ''
  try {
    const orderId = await checkoutGroupOrder(groupInviteCode.value)
    router.push(`/payment?id=${orderId}`)
  } catch (error) {
    groupError.value = error instanceof Error ? error.message : t('seat.group.actionFailed')
  } finally {
    groupBusy.value = false
  }
}

const copyGroupInvite = async () => {
  if (!groupInviteUrl.value) return
  try {
    await navigator.clipboard.writeText(groupInviteUrl.value)
    groupCopied.value = true
    setTimeout(() => {
      groupCopied.value = false
    }, 1600)
  } catch {
    groupError.value = t('seat.group.copyFailed')
  }
}

const memberSeatLabel = (member: GroupOrderMember) => {
  if (member.seatIds.length === 0) return t('seat.group.noMemberSeats')
  return member.seatIds.map(seatId => {
    const seat = seats.value.find(item => item.id === seatId)
    if (!seat) return seatId
    return t('seat.info', { row: seat.row, col: seat.col })
  }).join(' / ')
}

const selectedSeats = computed(() => {
  return seats.value.filter(s => selectedSeatIds.value.has(s.id))
})

const totalAmount = computed(() => {
  return selectedSeats.value.reduce((sum, seat) => sum + seat.price, 0)
})

const submitLock = async () => {
  if (selectedSeatIds.value.size === 0) return

  locking.value = true
  try {
    await lockSeats(scheduleId, Array.from(selectedSeatIds.value))
    sessionStorage.setItem('tempOrder', JSON.stringify({
      scheduleId,
      seatIds: Array.from(selectedSeatIds.value),
      totalAmount: totalAmount.value
    }))
    router.push('/confirm')
  } catch (e) {
    alert(t('seat.conflict'))
    await refreshSeatMap()
    selectedSeatIds.value.clear()
  } finally {
    locking.value = false
  }
}

const activeView = ref<'2d' | '3d'>('2d')

const rowsGrouped = computed(() => {
  const map = new Map<number, Seat[]>()
  seats.value.forEach(seat => {
    if (!map.has(seat.row)) {
      map.set(seat.row, [])
    }
    map.get(seat.row)!.push(seat)
  })
  const sortedRows = Array.from(map.keys()).sort((a, b) => a - b)
  return sortedRows.map(rowNum => {
    const rowSeats = map.get(rowNum)!.sort((a, b) => a.col - b.col)
    return {
      rowNum,
      seats: rowSeats
    }
  })
})

const isZonedOrMixedOverview = computed(() => {
  if (!schedule.value) return false
  const mode = schedule.value.ticketMode
  if (mode === 'ZONED') return true
  if (mode === 'MIXED' && activeStandId.value === null) return true
  return false
})

const stageDisplayLabel = computed(() => {
  const cat = schedule.value?.category?.toLowerCase()
  return cat === 'movie' ? t('seat.screen') : t('seat.stage')
})
</script>

<template>
  <div class="seat-selection">
    <div class="main-area">
      <!-- Loading Indicator -->
      <div class="view-loading" v-if="loading">
        <div class="spinner"></div>
        <p>{{ t('common.loading') }}</p>
      </div>

      <template v-else>
        <!-- Concert Venue Map Overview (ZONED or MIXED Stadium view) -->
        <ConcertVenuePreview
          v-if="isZonedOrMixedOverview"
          :areas="areas"
          :selectedAreaId="selectedArea?.id || null"
          @select-area="selectArea"
        />

        <!-- MIXED Mode Seated Stand View -->
        <div class="seat-map-wrapper" v-else-if="schedule?.ticketMode === 'MIXED' && activeStandId">
          <div class="stand-navigator">
            <button class="btn-back btn-interactive" @click="goBackToStadium">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="19" y1="12" x2="5" y2="12"></line>
                <polyline points="12 19 5 12 12 5"></polyline>
              </svg>
              {{ t('common.back') }}
            </button>
            <span class="stand-name-tag">{{ selectedArea?.name }}</span>
          </div>

          <div class="stage-container">
            <div class="stage-apron"></div>
            <div class="stage-label">{{ stageDisplayLabel }}</div>
          </div>

          <div class="seat-grid-container">
            <div class="seat-map">
              <div v-for="row in rowsGrouped" :key="row.rowNum" class="seat-row">
                <span class="row-label">{{ row.rowNum }}</span>
                <div class="seats-container">
                  <button
                    v-for="seat in row.seats"
                    :key="seat.id"
                    type="button"
                    class="seat-item btn-interactive"
                    :class="{
                      'status-available': seat.status === 'AVAILABLE',
                      'status-locked': seat.status === 'LOCKED',
                      'status-sold': seat.status === 'SOLD',
                      'status-disabled': seat.status === 'DISABLED',
                      'selected': selectedSeatIds.has(seat.id)
                    }"
                    :disabled="seat.status !== 'AVAILABLE' && !selectedSeatIds.has(seat.id)"
                    :title="seat.status !== 'DISABLED' ? t('seat.info', { row: seat.row, col: seat.col }) + ' - $' + seat.price : ''"
                    @click="toggleSeat(seat)"
                  >
                    <span class="seat-dot" v-if="selectedSeatIds.has(seat.id)"></span>
                  </button>
                </div>
                <span class="row-label">{{ row.rowNum }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Standard SEATED flow (Original 2D/3D selection) -->
        <template v-else>
          <!-- 视图切换控件 -->
          <div class="view-controls">
            <button
              class="control-btn"
              :class="{ active: activeView === '2d' }"
              @click="activeView = '2d'"
            >
              <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="9" y1="3" x2="9" y2="21"></line>
                <line x1="15" y1="3" x2="15" y2="21"></line>
                <line x1="3" y1="9" x2="21" y2="9"></line>
                <line x1="3" y1="15" x2="21" y2="15"></line>
              </svg>
              {{ t('seat.view2d') }}
            </button>
            <button
              class="control-btn"
              :class="{ active: activeView === '3d' }"
              @click="activeView = '3d'"
            >
              <svg class="icon" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
                <line x1="12" y1="22.08" x2="12" y2="12"></line>
              </svg>
              {{ t('seat.view3d') }}
            </button>
          </div>

          <!-- 2D 平面选座 -->
          <div class="seat-map-wrapper" v-if="activeView === '2d'">
            <div class="stage-container">
              <div class="stage-apron"></div>
              <div class="stage-label">{{ stageDisplayLabel }}</div>
            </div>

            <div class="seat-grid-container">
              <div class="seat-map">
                <div v-for="row in rowsGrouped" :key="row.rowNum" class="seat-row">
                  <span class="row-label">{{ row.rowNum }}</span>
                  <div class="seats-container">
                    <button
                      v-for="seat in row.seats"
                      :key="seat.id"
                      type="button"
                      class="seat-item btn-interactive"
                      :class="{
                        'status-available': seat.status === 'AVAILABLE',
                        'status-locked': seat.status === 'LOCKED',
                        'status-sold': seat.status === 'SOLD',
                        'status-disabled': seat.status === 'DISABLED',
                        'selected': selectedSeatIds.has(seat.id)
                      }"
                      :disabled="seat.status !== 'AVAILABLE' && !selectedSeatIds.has(seat.id)"
                      :title="seat.status !== 'DISABLED' ? t('seat.info', { row: seat.row, col: seat.col }) + ' - $' + seat.price : ''"
                      @click="toggleSeat(seat)"
                    >
                      <span class="seat-dot" v-if="selectedSeatIds.has(seat.id)"></span>
                    </button>
                  </div>
                  <span class="row-label">{{ row.rowNum }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 3D 效果预览 -->
          <SeatStagePreview
            v-else
            :seats="seats"
            :selected-seat-ids="selectedSeatIds"
            :stage-label="t('seat.stagePreview')"
            :unavailable-label="t('seat.previewUnavailable')"
            :row-label="t('seat.row')"
            :col-label="t('seat.col')"
            :category="schedule?.category"
            @toggle-seat="toggleSeat"
          />
        </template>
      </template>
    </div>

    <!-- Side Control Panel -->
    <aside class="side-panel">
      <!-- If ZONED overview or MIXED standing selected overview -->
      <template v-if="schedule?.ticketMode === 'ZONED' || (schedule?.ticketMode === 'MIXED' && !activeStandId)">
        <AreaTicketPanel
          v-if="selectedArea && !selectedArea.isSeated"
          :area="selectedArea"
          :loading="bookingZoned"
          @book-area="handleBookZonedArea"
        />
        <div class="zoned-placeholder" v-else>
          <div class="placeholder-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
            </svg>
          </div>
          <h3>{{ t('seat.selectAreaTitle') }}</h3>
          <p>{{ t('seat.selectAreaHint') }}</p>
          <TicketModeBadge v-if="schedule" :mode="schedule.ticketMode" class="badge-center" />
        </div>
      </template>

      <!-- SEATED flow or MIXED Stand view -->
      <template v-else>
        <div class="summary-header">
          <div>
            <p class="summary-kicker">{{ t('seat.orderSummary') }}</p>
            <h2>{{ t('seat.selection') }}</h2>
          </div>
          <TicketModeBadge v-if="schedule" :mode="schedule.ticketMode" />
        </div>

        <div class="realtime-panel" aria-live="polite">
          <div class="realtime-status" :class="`state-${realtimeState}`">
            <span class="status-dot"></span>
            <span>{{ t(`seat.live.${realtimeState}`) }}</span>
          </div>
          <div class="realtime-notice" v-if="realtimeNotice">
            {{ t(realtimeNotice) }}
          </div>
        </div>

        <section class="group-panel" v-if="showGroupFeatures && (isGroupMode || groupOrder)" aria-live="polite">
          <div class="group-header">
            <div>
              <p class="group-kicker">{{ t('seat.group.kicker') }}</p>
              <h3>{{ t('seat.group.title') }}</h3>
            </div>
            <span class="group-timer">{{ groupExpiresIn }}</span>
          </div>

          <div class="group-loading" v-if="groupLoading">{{ t('common.loading') }}</div>
          <div class="group-error" v-if="groupError">{{ groupError }}</div>

          <template v-if="groupOrder">
            <div class="invite-box" v-if="groupInviteUrl">
              <div class="invite-url">{{ groupInviteUrl }}</div>
              <button class="btn-small" type="button" @click="copyGroupInvite">
                {{ groupCopied ? t('seat.group.copied') : t('seat.group.copyInvite') }}
              </button>
            </div>

            <div class="group-meta">
              <span>{{ t('seat.group.host') }} {{ groupOrder.hostDisplayName }}</span>
              <span>{{ t('seat.group.totalSeats') }} {{ groupOrder.members.reduce((sum, member) => sum + member.seatIds.length, 0) }}/{{ groupOrder.maxSeats }}</span>
              <span>{{ t('seat.group.groupTotal') }} ${{ groupOrder.totalAmount }}</span>
            </div>

            <div class="member-list">
              <div class="member-row" v-for="member in groupOrder.members" :key="member.userId">
                <div>
                  <strong>{{ member.displayName }}</strong>
                  <p>{{ memberSeatLabel(member) }}</p>
                </div>
                <span>${{ member.amount }}</span>
              </div>
            </div>
          </template>
        </section>

        <div class="summary-meta">
          <div>
            <span>{{ t('seat.selectedCount') }}</span>
            <strong>{{ selectedSeatIds.size }}/{{ maxSelect }}</strong>
          </div>
          <div>
            <span>{{ t('seat.orderExpires') }}</span>
            <strong>15:00</strong>
          </div>
        </div>

        <div class="selection-block">
          <div class="block-head">
            <span>{{ t('seat.yourSelection') }}</span>
            <small>{{ t('seat.lockTip') }}</small>
          </div>

          <transition-group name="list-anim" tag="div" class="selected-list">
            <div class="empty-msg" v-if="selectedSeats.length === 0" key="empty">{{ t('seat.noSeats') }}</div>
            <div v-for="s in selectedSeats" :key="s.id" class="ticket-item">
              <div class="ticket-details">
                <div class="ticket-header">
                  <span class="ticket-badge">{{ s.section }}</span>
                  <button class="ticket-close btn-interactive" type="button" @click="toggleSeat(s)" :title="t('common.cancel')">
                    <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                      <line x1="18" y1="6" x2="6" y2="18"></line>
                      <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                  </button>
                </div>
                <div class="ticket-body">
                  <span class="seat-name">{{ t('seat.info', { row: s.row, col: s.col }) }}</span>
                  <span class="seat-price">${{ s.price }}</span>
                </div>
              </div>
            </div>
          </transition-group>
        </div>

        <div class="legend">
          <div class="legend-item"><div class="box status-available"></div> {{ t('seat.available') }}</div>
          <div class="legend-item"><div class="box status-locked"></div> {{ t('seat.locked') }}</div>
          <div class="legend-item"><div class="box status-sold"></div> {{ t('seat.sold') }}</div>
          <div class="legend-item"><div class="box selected"></div> {{ t('seat.yourSelection') }}</div>
        </div>

        <div class="checkout-dock">
          <div class="total-bar">
            <span>{{ t('seat.total') }}</span>
            <span class="amount">${{ totalAmount }}</span>
          </div>

          <button
            v-if="!isGroupMode"
            class="btn-checkout"
            :disabled="selectedSeatIds.size === 0 || locking"
            @click="submitLock"
          >
            {{ locking ? t('seat.locking') : t('seat.checkout') }}
          </button>
        </div>

        <button
          v-if="!isGroupMode && showGroupFeatures"
          class="btn-secondary"
          :disabled="selectedSeatIds.size === 0 || groupBusy"
          @click="startGroupOrder"
        >
          {{ groupBusy ? t('common.processing') : t('seat.group.start') }}
        </button>

        <div class="group-actions" v-if="isGroupMode">
          <button
            class="btn-checkout"
            :disabled="selectedSeatIds.size === 0 || groupBusy || groupOrder?.status !== 'OPEN'"
            @click="joinCurrentGroup"
          >
            {{ groupBusy ? t('common.processing') : t('seat.group.joinOrUpdate') }}
          </button>
          <button
            v-if="isGroupHost"
            class="btn-checkout"
            :disabled="groupBusy || !groupOrder || groupOrder.status !== 'OPEN'"
            @click="checkoutCurrentGroup"
          >
            {{ t('seat.group.hostCheckout') }}
          </button>
          <button
            v-if="isGroupHost"
            class="btn-secondary danger"
            :disabled="groupBusy || !groupOrder || groupOrder.status !== 'OPEN'"
            @click="cancelCurrentGroup"
          >
            {{ t('seat.group.cancel') }}
          </button>
          <button
            v-else
            class="btn-secondary"
            :disabled="groupBusy || !currentMember || groupOrder?.status !== 'OPEN'"
            @click="leaveCurrentGroup"
          >
            {{ t('seat.group.leave') }}
          </button>
        </div>
      </template>
    </aside>
  </div>
</template>

<style scoped lang="scss">
.seat-selection {
  display: flex;
  min-height: calc(100vh - 76px);
  height: calc(100vh - 76px);
  width: 100%;

  @media (max-width: 900px) {
    flex-direction: column;
    height: auto;
  }
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: var(--color-bg-base);
  position: relative;
}

.view-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: var(--spacing-3);
  color: var(--color-text-secondary);

  .spinner {
    width: 40px;
    height: 40px;
    border: 3px solid rgba(200, 149, 90, 0.12);
    border-top-color: var(--color-accent);
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }
}

.stand-navigator {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
  padding: var(--spacing-4) var(--spacing-6);
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-bg-elevated);

  .btn-back {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: transparent;
    border: 1px solid var(--color-border-strong);
    border-radius: var(--radius-sm);
    color: var(--color-text-secondary);
    font-size: 13px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      color: var(--color-accent);
      border-color: var(--color-accent);
    }
  }

  .stand-name-tag {
    font-family: var(--font-family-display);
    font-size: 16px;
    font-weight: 700;
    color: var(--color-text-primary);
  }
}

.zoned-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--spacing-8) var(--spacing-4);
  height: 100%;
  color: var(--color-text-secondary);

  .placeholder-icon {
    color: var(--color-border-strong);
    margin-bottom: var(--spacing-4);
  }

  h3 {
    font-family: var(--font-family-display);
    font-size: 20px;
    color: var(--color-text-primary);
    margin: 0 0 var(--spacing-2) 0;
  }

  p {
    font-size: 13px;
    line-height: 1.6;
    margin: 0 0 var(--spacing-6) 0;
  }

  .badge-center {
    margin-top: var(--spacing-2);
  }
}

/* 列表过渡动画 */
.list-anim-enter-active,
.list-anim-leave-active {
  transition: all 300ms cubic-bezier(0.4, 0, 0.2, 1);
}
.list-anim-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.list-anim-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

.selected-list {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-4);

  .empty-msg {
    color: var(--color-text-secondary);
    font-size: 14px;
    padding: var(--spacing-2) 0;
  }
}

.ticket-item {
  position: relative;
  flex-shrink: 0;
  background-color: var(--color-bg-base);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  padding: var(--spacing-3);
  margin-bottom: var(--spacing-2);
  overflow: hidden;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    transform: translateY(-2px);
    border-color: var(--color-accent);
    background-color: var(--color-bg-elevated);
    box-shadow: 0 4px 12px rgba(200, 149, 90, 0.16);
  }

  &::before,
  &::after {
    content: '';
    position: absolute;
    top: 50%;
    width: 10px;
    height: 10px;
    background-color: var(--color-bg-elevated);
    border-radius: 50%;
    transform: translateY(-50%);
    border: 1px solid var(--color-border-strong);
    z-index: 1;
    transition: background-color 200ms ease, border-color 200ms ease;
  }

  &::before {
    left: -6px;
  }

  &::after {
    right: -6px;
  }

  .ticket-details {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .ticket-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px dashed var(--color-border);
    padding-bottom: 6px;
  }

  .ticket-badge {
    font-family: var(--font-family-sans);
    font-size: 10px;
    font-weight: 700;
    border: 1px solid rgba(255, 255, 255, 0.2);
    background: transparent;
    color: rgba(255, 255, 255, 0.7);
    padding: 2px 6px;
    border-radius: 3px;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .ticket-close {
    background: none;
    border: none;
    padding: 2px;
    color: var(--color-text-secondary);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    transition: all 150ms ease;

    &:hover {
      color: var(--color-error);
      background-color: rgba(224, 84, 84, 0.1);
    }
  }

  .ticket-body {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .seat-name {
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-primary);
  }

  .seat-price {
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 700;
    color: var(--color-accent);
  }
}

/* 视图切换控件 */
.view-controls {
  display: flex;
  justify-content: center;
  gap: var(--spacing-2);
  padding: var(--spacing-3) 0;
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-bg-elevated);
  z-index: 10;
  width: 100%;

  .control-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    border-radius: 4px;
    border: 1px solid rgba(255, 255, 255, 0.2);
    background-color: transparent;
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 700;
    cursor: pointer;
    transition: all 180ms ease;

    &:hover {
      color: var(--color-text-primary);
      border-color: var(--color-text-primary);
    }

    &.active {
      background-color: transparent;
      border-color: #fff;
      color: #fff;
      box-shadow: none;
    }

    .icon {
      flex: 0 0 auto;
    }
  }
}

/* 2D 选座区域样式 */
.seat-map-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-5) var(--spacing-4);
  overflow-y: auto;
  gap: var(--spacing-5);
  position: relative;
  width: 100%;
}

.stage-container {
  width: 70%;
  max-width: 500px;
  text-align: center;
  position: relative;

  .stage-apron {
    height: 10px;
    border-radius: 50% / 100% 100% 0 0;
    background: linear-gradient(180deg, var(--color-accent) 0%, rgba(200, 149, 90, 0.12) 100%);
    box-shadow: 0 -4px 16px rgba(200, 149, 90, 0.3);
    border: 1px solid var(--color-accent);
    border-bottom: none;
  }

  .stage-label {
    font-family: var(--font-family-sans);
    font-size: 11px;
    font-weight: 700;
    color: var(--color-accent);
    letter-spacing: 0.2em;
    text-transform: uppercase;
    margin-top: 8px;
  }
}

.seat-grid-container {
  width: 100%;
  overflow-x: auto;
  display: flex;
  justify-content: center;
  padding: var(--spacing-2) 0;

  &::-webkit-scrollbar {
    height: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--color-border-strong);
    border-radius: 3px;
  }
}

.seat-map {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2);

  .seat-row {
    display: flex;
    align-items: center;
    gap: var(--spacing-4);

    .row-label {
      width: 24px;
      text-align: center;
      font-size: 12px;
      color: var(--color-text-ghost);
      font-family: var(--font-family-sans);
      font-weight: 700;
    }

    .seats-container {
      display: flex;
      gap: 6px;
    }
  }
}

.seat-item, .box {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--color-border-strong);
  background-color: var(--color-bg-elevated);
  cursor: pointer;
  position: relative;
  transition: all 150ms cubic-bezier(0.4, 0, 0.2, 1);

  &:hover:not(:disabled) {
    border-color: var(--color-accent);
    transform: scale(1.15);
    box-shadow: 0 4px 8px rgba(200, 149, 90, 0.18);
  }

  &.status-available {
    background-color: transparent;
    border-color: var(--color-border-strong);
  }

  &.status-locked {
    background-color: rgba(255, 255, 255, 0.05);
    border-color: var(--color-border);
    border-style: dashed;
    cursor: not-allowed;
    &::after {
      content: '';
      position: absolute;
      width: 4px;
      height: 4px;
      background-color: var(--color-text-ghost);
      border-radius: 50%;
    }
  }

  &.status-sold {
    background-color: transparent;
    border-color: transparent;
    cursor: not-allowed;
    &::after {
      content: '';
      position: absolute;
      width: 6px;
      height: 6px;
      background-color: var(--color-border-strong);
      border-radius: 50%;
      opacity: 0.4;
    }
  }

  &.status-disabled {
    opacity: 0;
    pointer-events: none;
  }

  &.selected {
    background-color: var(--color-accent) !important;
    border-color: var(--color-accent) !important;
    box-shadow: 0 0 10px rgba(200, 149, 90, 0.42);

    .seat-dot {
      width: 6px;
      height: 6px;
      background-color: #080808;
      border-radius: 50%;
    }
  }
}

.box {
  cursor: default;
}

.side-panel {
  width: 420px;
  height: calc(100vh - 76px);
  flex-shrink: 0;
  position: sticky;
  top: 76px;
  overflow-y: auto;
  background:
    linear-gradient(180deg, rgba(200, 149, 90, 0.07) 0%, rgba(17, 17, 17, 0) 180px),
    var(--color-bg-elevated);
  border-left: 1px solid var(--color-border);
  padding: var(--spacing-4);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--color-border-strong);
    border-radius: var(--radius-full);
  }

  @media (max-width: 900px) {
    width: 100%;
    height: auto;
    position: static;
    overflow: visible;
    border-left: none;
    border-top: 1px solid var(--color-border);
    padding: var(--spacing-4);
  }

  .summary-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--spacing-3);
    border-bottom: 1px solid var(--color-border);
    padding-bottom: var(--spacing-4);
  }

  .summary-kicker {
    margin: 0 0 4px;
    color: var(--color-accent);
    font-family: var(--font-family-sans);
    font-size: 11px;
    font-weight: 900;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  h2 {
    font-family: var(--font-family-display);
    font-size: 28px;
    line-height: 1.1;
  }

  .realtime-panel {
    min-height: 38px;
    font-family: var(--font-family-sans);
    font-size: 12px;
  }

  .realtime-status {
    display: inline-flex;
    align-items: center;
    gap: var(--spacing-2);
    color: var(--color-text-secondary);
  }

  .status-dot {
    width: 8px;
    height: 8px;
    border-radius: 999px;
    background-color: var(--color-text-ghost);
  }

  .state-connected .status-dot {
    background-color: var(--color-accent);
  }

  .state-connecting .status-dot {
    background-color: var(--color-border-strong);
  }

  .state-disconnected .status-dot {
    background-color: var(--color-text-ghost);
  }

  .realtime-notice {
    margin-top: var(--spacing-1);
    color: var(--color-text-ghost);
  }

  .summary-meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--spacing-3);

    div {
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--color-bg-base);
      padding: var(--spacing-3);
      display: grid;
      gap: 4px;
    }

    span {
      color: var(--color-text-secondary);
      font-family: var(--font-family-sans);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    strong {
      color: var(--color-text-primary);
      font-family: var(--font-family-sans);
      font-size: 22px;
      font-variant-numeric: tabular-nums;
      line-height: 1;
    }
  }

  .selection-block {
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: rgba(8, 8, 8, 0.42);
    padding: var(--spacing-3);
  }

  .block-head {
    display: grid;
    gap: 4px;
    margin-bottom: var(--spacing-3);

    span {
      color: var(--color-text-primary);
      font-family: var(--font-family-sans);
      font-size: 14px;
      font-weight: 900;
    }

    small {
      color: var(--color-text-secondary);
      font-family: var(--font-family-sans);
      line-height: 1.45;
    }
  }

  .group-panel {
    margin-bottom: var(--spacing-5);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    padding: var(--spacing-4);
    background: var(--color-bg-base);
    font-family: var(--font-family-sans);
  }

  .group-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--spacing-3);
    margin-bottom: var(--spacing-3);

    h3 {
      margin: 0;
      font-family: var(--font-family-display);
      font-size: 20px;
      line-height: 1.2;
    }
  }

  .group-kicker {
    margin-bottom: 4px;
    color: var(--color-accent);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  .group-timer {
    flex: 0 0 auto;
    border: 1px solid rgba(212, 175, 55, 0.35);
    border-radius: 6px;
    color: var(--color-accent);
    font-weight: 700;
    line-height: 1;
    padding: 8px 10px;
  }

  .group-loading,
  .group-error {
    margin-bottom: var(--spacing-3);
    font-size: 13px;
  }

  .group-error {
    color: #f0a86b;
  }

  .invite-box {
    display: grid;
    gap: var(--spacing-2);
    margin-bottom: var(--spacing-3);
  }

  .invite-url {
    overflow: hidden;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    color: var(--color-text-secondary);
    font-size: 12px;
    padding: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .btn-small {
    min-height: 38px;
    border: 1px solid var(--color-border-strong);
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--color-text-primary);
    cursor: pointer;
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 700;
    transition: border-color 150ms ease, color 150ms ease;

    &:hover {
      border-color: var(--color-accent);
      color: var(--color-accent);
    }
  }

  .group-meta {
    display: grid;
    gap: 6px;
    margin-bottom: var(--spacing-3);
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.35;
  }

  .member-list {
    display: grid;
    gap: var(--spacing-2);
  }

  .member-row {
    display: flex;
    justify-content: space-between;
    gap: var(--spacing-3);
    border-top: 1px solid var(--color-border);
    padding-top: var(--spacing-2);

    strong {
      display: block;
      margin-bottom: 3px;
      color: var(--color-text-primary);
      font-size: 13px;
    }

    p {
      color: var(--color-text-secondary);
      font-size: 12px;
      line-height: 1.35;
    }

    span {
      flex: 0 0 auto;
      color: var(--color-accent);
      font-weight: 700;
    }
  }

  .legend {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--spacing-3);
    font-size: 12px;
    color: var(--color-text-secondary);

    .legend-item {
      display: flex;
      align-items: center;
      gap: var(--spacing-2);
    }
  }

  .selected-list {
    max-height: 280px;
    overflow-y: auto;
    padding-right: 4px;

    &::-webkit-scrollbar {
      width: 4px;
    }
    &::-webkit-scrollbar-track {
      background: transparent;
    }
    &::-webkit-scrollbar-thumb {
      background: var(--color-border-strong);
      border-radius: 2px;
    }

    .empty-msg {
      color: var(--color-text-ghost);
      font-size: 14px;
    }
  }

  .total-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-family: var(--font-family-sans);
    font-size: 16px;

    .amount {
      font-size: 24px;
      font-weight: 700;
      color: var(--color-accent);
    }
  }

  .checkout-dock {
    margin-top: auto;
    border-top: 1px solid var(--color-border);
    padding-top: var(--spacing-4);
    display: grid;
    gap: var(--spacing-3);
  }

  .btn-checkout {
    width: 100%;
    padding: 16px;
    background-color: #e50914;
    color: #fff;
    border: none;
    border-radius: 4px;
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: background-color 150ms ease;

    &:hover:not(:disabled) {
      background-color: #f6121d;
    }

    &:disabled {
      background-color: var(--color-border-strong);
      color: var(--color-text-ghost);
      cursor: not-allowed;
    }
  }

  .btn-secondary {
    width: 100%;
    min-height: 48px;
    margin-top: var(--spacing-3);
    border: none;
    border-radius: 4px;
    background: rgba(255, 255, 255, 0.1);
    color: var(--color-text-primary);
    cursor: pointer;
    font-family: var(--font-family-sans);
    font-size: 15px;
    font-weight: 700;
    transition: border-color 150ms ease, color 150ms ease;

    &:hover:not(:disabled) {
      background: rgba(255, 255, 255, 0.16);
      color: #fff;
    }

    &:disabled {
      color: var(--color-text-ghost);
      cursor: not-allowed;
    }

    &.danger:hover:not(:disabled) {
      background: rgba(229, 9, 20, 0.08);
      color: rgba(255, 101, 112, 0.95);
    }
  }

  .group-actions {
    display: grid;
    gap: var(--spacing-3);

    .btn-secondary {
      margin-top: 0;
    }
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
