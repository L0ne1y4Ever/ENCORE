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
import type { Schedule } from '../../mock/shows'
import { createOrder, getOrderDetail } from '../../api/order'
import { formatMoney } from '../../utils/money'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t, locale } = useI18n()
const scheduleId = route.params.id as string

const schedule = ref<Schedule | null>(null)
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
const groupTicketReady = ref(false)
const groupTicketSyncing = ref(false)
const inviteInputRef = ref<HTMLInputElement | null>(null)
const now = ref(Date.now())
const realtimeState = ref<SeatRealtimeConnectionState>('connecting')
const realtimeNotice = ref<string | null>(null)
const maxSelect = 6
const hasUnsavedSelection = ref(false)
let disconnectRealtime: (() => void) | undefined
let realtimeNoticeTimer: ReturnType<typeof setTimeout> | undefined
let groupPollTimer: ReturnType<typeof setInterval> | undefined
let groupCountdownTimer: ReturnType<typeof setInterval> | undefined
let groupCopiedTimer: ReturnType<typeof setTimeout> | undefined

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
const groupClaimedCount = computed(() => {
  return groupOrder.value?.members.reduce((sum, member) => sum + member.seatIds.length, 0) || 0
})
const groupPaid = computed(() => {
  return groupOrder.value?.status === 'PAID' || groupOrder.value?.orderStatus === 'PAID'
})
const groupCheckedOut = computed(() => {
  return groupOrder.value?.status === 'CHECKED_OUT'
          || (Boolean(groupOrder.value?.orderId) && groupOrder.value?.orderStatus === 'PENDING_PAYMENT')
})
const groupClosed = computed(() => {
  return groupOrder.value?.status === 'CANCELLED' || groupOrder.value?.status === 'EXPIRED'
})
const groupCanEdit = computed(() => {
  return !isGroupMode.value || groupOrder.value?.status === 'OPEN'
})
const selectedCountText = computed(() => {
  if (isGroupMode.value && groupOrder.value) {
    return `${groupClaimedCount.value}/${groupOrder.value.maxSeats || maxSelect}`
  }
  return `${selectedSeatIds.value.size}/${maxSelect}`
})
const groupInviteUrl = computed(() => {
  if (!groupInviteCode.value) return ''
  return `${window.location.origin}${route.path}?group=${groupInviteCode.value}`
})
const isLocalHost = computed(() => {
  const host = window.location.hostname
  return host === 'localhost' || host === '127.0.0.1' || host === '::1'
})
const showGroupHttpsHint = computed(() => !window.isSecureContext && !isLocalHost.value)
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
const groupStatusLabel = computed(() => {
  if (groupPaid.value && !groupTicketReady.value) return t('seat.group.ticketSyncingShort')
  if (groupPaid.value) return t('seat.group.paymentCompletedShort')
  if (groupCheckedOut.value) return t('seat.group.checkoutPendingShort')
  if (groupClosed.value) return t('seat.group.closedShort')
  return groupExpiresIn.value
})
const currentMemberSeatLabel = computed(() => {
  return currentMember.value ? memberSeatLabel(currentMember.value) : t('seat.group.notJoined')
})
const currentMemberAmount = computed(() => currentMember.value?.amount || 0)
const currentMemberSeatCount = computed(() => currentMember.value?.seatIds.length || 0)
const groupPersonalStatus = computed(() => {
  if (groupPaid.value && !groupTicketReady.value) return t('seat.group.myStatusSyncing')
  if (groupPaid.value) return t('seat.group.myStatusIssued')
  if (groupCheckedOut.value) return t('seat.group.myStatusLocked')
  if (groupClosed.value) return t('seat.group.myStatusClosed')
  if (currentMember.value) return t('seat.group.myStatusEditable')
  return t('seat.group.myStatusPendingJoin')
})
const groupProgressSteps = computed(() => [
  {
    key: 'selecting',
    label: t('seat.group.stageSelecting'),
    active: Boolean(groupOrder.value),
    current: Boolean(groupOrder.value) && !groupCheckedOut.value && !groupPaid.value && !groupClosed.value
  },
  {
    key: 'checkout',
    label: t('seat.group.stageCheckout'),
    active: groupCheckedOut.value || groupPaid.value,
    current: groupCheckedOut.value
  },
  {
    key: 'issued',
    label: t('seat.group.stageIssued'),
    active: groupPaid.value,
    current: groupPaid.value && groupTicketReady.value
  }
])

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
  } catch {
    areas.value = []
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
  } catch (error) {
    alert(error instanceof Error ? error.message : t('order.createFailed'))
  } finally {
    bookingZoned.value = false
  }
}

const showGroupFeatures = computed(() => {
  return schedule.value?.ticketMode === 'SEATED' || (schedule.value?.ticketMode === 'MIXED' && activeStandId.value !== null)
})
const ticketMode = computed(() => schedule.value?.ticketMode || 'SEATED')

onMounted(async () => {
  try {
    schedule.value = await getScheduleDetail(scheduleId)
  } catch {
    schedule.value = null
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
  if (groupCopiedTimer) {
    clearTimeout(groupCopiedTimer)
  }
})

const toggleSeat = (seat: Seat) => {
  if (!groupCanEdit.value) return

  if (selectedSeatIds.value.has(seat.id)) {
    selectedSeatIds.value.delete(seat.id)
    hasUnsavedSelection.value = true
    return
  }

  if (seat.status !== 'AVAILABLE') return

  if (selectedSeatIds.value.size < maxSelect) {
    selectedSeatIds.value.add(seat.id)
    hasUnsavedSelection.value = true
  }
}

watch(groupInviteCode, async (nextCode, previousCode) => {
  if (nextCode === previousCode) return
  stopGroupPolling()
  groupOrder.value = null
  groupError.value = ''
  groupCopied.value = false
  groupTicketReady.value = false
  groupTicketSyncing.value = false
  selectedSeatIds.value.clear()
  if (nextCode) {
    await loadGroupOrder(true)
    startGroupPolling()
  }
})

const syncSelectionFromGroup = (order: GroupOrder | null) => {
  const member = order?.members.find(item => item.userId === currentUserId.value)
  selectedSeatIds.value = new Set(member?.seatIds || [])
  hasUnsavedSelection.value = false
}

// 轮询回写仅在"本地没有未提交改动"时进行，避免 5 秒一次的轮询把成员
// 刚点选、还没提交"加入/更新座位"的座位悄悄重置回已认领集合。
const shouldSyncSelectionFromGroup = (order: GroupOrder | null, force: boolean) => {
  if (force) return true
  if (!order) return false
  const isMember = order.members.some(member => member.userId === currentUserId.value)
  if (!isMember) return false
  if (order.status === 'OPEN' && hasUnsavedSelection.value) return false
  return true
}

const confirmGroupTicketsVisible = async (order: GroupOrder) => {
  if (!order.orderId) {
    groupTicketReady.value = false
    return false
  }
  groupTicketSyncing.value = true
  try {
    const detail = await getOrderDetail(order.orderId)
    const visibleTickets = detail.tickets || []
    const hasVisibleTicket = visibleTickets.some(ticket => ticket.status !== 'VOID')
    groupTicketReady.value = hasVisibleTicket
    return hasVisibleTicket
  } catch {
    groupTicketReady.value = false
    return false
  } finally {
    groupTicketSyncing.value = false
  }
}

const loadGroupOrder = async (syncSelection = false) => {
  if (!groupInviteCode.value) return
  groupLoading.value = true
  try {
    const previousStatus = groupOrder.value?.status
    const previousOrderStatus = groupOrder.value?.orderStatus
    const data = await getGroupOrder(groupInviteCode.value)
    groupOrder.value = data
    groupError.value = ''
    if (shouldSyncSelectionFromGroup(data, syncSelection)) {
      syncSelectionFromGroup(data)
    }
    if ((previousStatus !== data.status || previousOrderStatus !== data.orderStatus) && data.status !== 'OPEN') {
      await refreshSeatMap()
    }
    const isPaid = data.status === 'PAID' || data.orderStatus === 'PAID'
    if (isPaid) {
      const ticketsReady = await confirmGroupTicketsVisible(data)
      if (ticketsReady) {
        stopGroupPolling()
      }
    } else {
      groupTicketReady.value = false
      groupTicketSyncing.value = false
    }
    if (['CANCELLED', 'EXPIRED'].includes(data.status)) {
      stopGroupPolling()
    }
  } catch (error) {
    groupOrder.value = null
    if (isGroupUnavailableError(error)) {
      stopGroupPolling()
      groupError.value = t('seat.group.closed')
    } else {
      groupError.value = error instanceof Error ? error.message : t('seat.group.loadFailed')
    }
  } finally {
    groupLoading.value = false
  }
}

const startGroupPolling = () => {
  stopGroupPolling()
  groupPollTimer = setInterval(() => {
    void loadGroupOrder(false)
  }, 3000)
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
    groupTicketReady.value = false
    groupTicketSyncing.value = false
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
    groupTicketReady.value = false
    groupTicketSyncing.value = false
    syncSelectionFromGroup(data)
    await refreshSeatMap()
  } catch (error) {
    groupError.value = error instanceof Error ? error.message : t('seat.group.actionFailed')
    await refreshSeatMap()
  } finally {
    groupBusy.value = false
  }
}

const isGroupUnavailableError = (error: unknown) => {
  const message = error instanceof Error ? error.message.toLowerCase() : ''
  return message.includes('不存在或已过期') || message.includes('expired') || message.includes('not found')
}

const exitGroupMode = async () => {
  groupOrder.value = null
  selectedSeatIds.value.clear()
  hasUnsavedSelection.value = false
  groupTicketReady.value = false
  groupTicketSyncing.value = false
  stopGroupPolling()
  await router.replace({ path: route.path, query: { ...route.query, group: undefined } })
  await refreshSeatMap()
}

const leaveCurrentGroup = async () => {
  if (!groupInviteCode.value) return
  groupBusy.value = true
  groupError.value = ''
  try {
    if (groupOrder.value?.status === 'OPEN' && currentMember.value) {
      await leaveGroupOrder(groupInviteCode.value)
    }
    await exitGroupMode()
  } catch (error) {
    if (isGroupUnavailableError(error)) {
      await exitGroupMode()
      return
    }
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
    if (groupOrder.value && groupOrder.value.status !== 'CHECKED_OUT') {
      await cancelGroupOrder(groupInviteCode.value)
    }
    await exitGroupMode()
  } catch (error) {
    if (isGroupUnavailableError(error)) {
      await exitGroupMode()
      return
    }
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

const viewGroupOrderTicket = () => {
  if (groupOrder.value?.orderId) {
    router.push(`/ticket/${groupOrder.value.orderId}`)
    return
  }
  viewMyTickets()
}

const viewMyTickets = () => {
  router.push({ path: '/profile', query: { tab: 'tickets' } })
}

const selectGroupInvite = () => {
  inviteInputRef.value?.focus()
  inviteInputRef.value?.select()
}

const copyWithLegacySelection = (text: string) => {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.top = '-1000px'
  textarea.style.left = '-1000px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  textarea.setSelectionRange(0, text.length)

  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}

const copyInviteText = async (text: string) => {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // HTTP/IP 访问或移动端 WebView 可能会拒绝 Clipboard API，继续走传统选择复制兜底。
    }
  }
  return copyWithLegacySelection(text)
}

const copyGroupInvite = async () => {
  if (!groupInviteUrl.value) return
  const copied = await copyInviteText(groupInviteUrl.value)
  if (copied) {
    groupError.value = ''
    groupCopied.value = true
    if (groupCopiedTimer) {
      clearTimeout(groupCopiedTimer)
    }
    groupCopiedTimer = setTimeout(() => {
      groupCopied.value = false
    }, 1600)
    return
  }

  selectGroupInvite()
  groupError.value = t('seat.group.copyFailed')
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
  if (isGroupMode.value && groupOrder.value) {
    return Number(groupOrder.value.totalAmount || 0)
  }
  return selectedSeats.value.reduce((sum, seat) => sum + seat.price, 0)
})

const money = (value: number | string | undefined | null) => formatMoney(value, locale.value)

const columnNumbers = computed(() => {
  return Array.from(new Set(seats.value.map(seat => seat.col)))
    .filter(col => Number.isFinite(col))
    .sort((a, b) => a - b)
})

const priceTierColors = [
  { className: 'tier-premium', color: '#d8a66c' },
  { className: 'tier-standard', color: '#8aa6c1' },
  { className: 'tier-value', color: '#72b89b' },
  { className: 'tier-extra', color: '#b0a8d6' }
]

const priceTiers = computed(() => {
  const prices = Array.from(new Set(seats.value
    .filter(seat => seat.status !== 'DISABLED' && Number.isFinite(Number(seat.price)) && Number(seat.price) > 0)
    .map(seat => Number(seat.price))))
    .sort((a, b) => b - a)

  return prices.map((price, index) => {
    const color = priceTierColors[index % priceTierColors.length]
    return {
      price,
      label: money(price),
      className: color.className,
      color: color.color
    }
  })
})

const priceTierByPrice = computed(() => {
  const map = new Map<number, { className: string; color: string }>()
  priceTiers.value.forEach(tier => {
    map.set(tier.price, tier)
  })
  return map
})

const seatPriceTier = (seat: Seat) => {
  if (seat.status === 'DISABLED') return ''
  return priceTierByPrice.value.get(Number(seat.price))?.className || ''
}

const seatTitle = (seat: Seat) => {
  if (seat.status === 'DISABLED') return ''
  const status = t(`seat.${seat.status.toLowerCase()}`)
  return `${t('seat.info', { row: seat.row, col: seat.col })} · ${seat.section} · ${money(seat.price)} · ${status}`
}

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
    alert(e instanceof Error && e.message ? e.message : t('seat.conflict'))
    await refreshSeatMap()
    selectedSeatIds.value.clear()
  } finally {
    locking.value = false
  }
}

const activeView = ref<'2d' | '3d'>('2d')
const expandsGroupSidePanel = computed(() => {
  return isGroupMode.value && activeView.value === '2d' && !isZonedOrMixedOverview.value
})

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
    const seatByColumn = new Map(rowSeats.map(seat => [seat.col, seat]))
    return {
      rowNum,
      seats: columnNumbers.value.map(col => seatByColumn.get(col) || null)
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
  <div class="seat-selection" :class="{ 'group-wide-2d': expandsGroupSidePanel }">
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
              <div class="column-label-row" v-if="columnNumbers.length">
                <span class="row-label spacer" aria-hidden="true"></span>
                <div class="column-labels">
                  <span v-for="col in columnNumbers" :key="`mixed-top-${col}`">{{ col }}</span>
                </div>
                <span class="row-label spacer" aria-hidden="true"></span>
              </div>
              <div v-for="row in rowsGrouped" :key="row.rowNum" class="seat-row">
                <span class="row-label">{{ row.rowNum }}</span>
                <div class="seats-container">
                  <button
                    v-for="(seat, index) in row.seats"
                    :key="seat?.id || `mixed-empty-${row.rowNum}-${index}`"
                    type="button"
                    class="seat-item btn-interactive"
                    :class="{
                      'status-available': seat?.status === 'AVAILABLE',
                      'status-locked': seat?.status === 'LOCKED',
                      'status-sold': seat?.status === 'SOLD',
                      'status-disabled': !seat || seat.status === 'DISABLED',
                      'selected': seat ? selectedSeatIds.has(seat.id) : false,
                      [seat ? seatPriceTier(seat) : '']: true
                    }"
                    :style="{ '--seat-tier-color': seat ? priceTierByPrice.get(Number(seat.price))?.color || 'rgba(255,255,255,0.34)' : 'rgba(255,255,255,0.34)' }"
                    :disabled="!seat || !groupCanEdit || (seat.status !== 'AVAILABLE' && !selectedSeatIds.has(seat.id))"
                    :title="seat ? seatTitle(seat) : ''"
                    @click="seat && toggleSeat(seat)"
                  >
                    <span class="seat-dot" v-if="seat && selectedSeatIds.has(seat.id)"></span>
                  </button>
                </div>
                <span class="row-label">{{ row.rowNum }}</span>
              </div>
              <div class="column-label-row bottom" v-if="columnNumbers.length">
                <span class="row-label spacer" aria-hidden="true"></span>
                <div class="column-labels">
                  <span v-for="col in columnNumbers" :key="`mixed-bottom-${col}`">{{ col }}</span>
                </div>
                <span class="row-label spacer" aria-hidden="true"></span>
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
                <div class="column-label-row" v-if="columnNumbers.length">
                  <span class="row-label spacer" aria-hidden="true"></span>
                  <div class="column-labels">
                    <span v-for="col in columnNumbers" :key="`top-${col}`">{{ col }}</span>
                  </div>
                  <span class="row-label spacer" aria-hidden="true"></span>
                </div>
                <div v-for="row in rowsGrouped" :key="row.rowNum" class="seat-row">
                  <span class="row-label">{{ row.rowNum }}</span>
                  <div class="seats-container">
                    <button
                    v-for="(seat, index) in row.seats"
                      :key="seat?.id || `empty-${row.rowNum}-${index}`"
                      type="button"
                      class="seat-item btn-interactive"
                      :class="{
                        'status-available': seat?.status === 'AVAILABLE',
                        'status-locked': seat?.status === 'LOCKED',
                        'status-sold': seat?.status === 'SOLD',
                        'status-disabled': !seat || seat.status === 'DISABLED',
                        'selected': seat ? selectedSeatIds.has(seat.id) : false,
                        [seat ? seatPriceTier(seat) : '']: true
                      }"
                      :style="{ '--seat-tier-color': seat ? priceTierByPrice.get(Number(seat.price))?.color || 'rgba(255,255,255,0.34)' : 'rgba(255,255,255,0.34)' }"
                      :disabled="!seat || !groupCanEdit || (seat.status !== 'AVAILABLE' && !selectedSeatIds.has(seat.id))"
                      :title="seat ? seatTitle(seat) : ''"
                      @click="seat && toggleSeat(seat)"
                    >
                      <span class="seat-dot" v-if="seat && selectedSeatIds.has(seat.id)"></span>
                    </button>
                  </div>
                  <span class="row-label">{{ row.rowNum }}</span>
                </div>
                <div class="column-label-row bottom" v-if="columnNumbers.length">
                  <span class="row-label spacer" aria-hidden="true"></span>
                  <div class="column-labels">
                    <span v-for="col in columnNumbers" :key="`bottom-${col}`">{{ col }}</span>
                  </div>
                  <span class="row-label spacer" aria-hidden="true"></span>
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
          <TicketModeBadge v-if="schedule" :mode="ticketMode" class="badge-center" />
        </div>
      </template>

      <!-- SEATED flow or MIXED Stand view -->
      <template v-else>
        <div class="summary-header">
          <div>
            <p class="summary-kicker">{{ t('seat.orderSummary') }}</p>
            <h2>{{ t('seat.selection') }}</h2>
          </div>
          <TicketModeBadge v-if="schedule" :mode="ticketMode" />
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
            <span class="group-timer" :class="{ settled: groupCheckedOut || groupPaid || groupClosed }">{{ groupStatusLabel }}</span>
          </div>

          <div class="group-loading" v-if="groupLoading">{{ t('common.loading') }}</div>
          <div class="group-error" v-if="groupError">{{ groupError }}</div>

          <template v-if="groupOrder">
            <div class="invite-box" v-if="groupInviteUrl">
              <label class="invite-field">
                <span>{{ t('seat.group.inviteLink') }}</span>
                <input
                  ref="inviteInputRef"
                  class="invite-url"
                  :value="groupInviteUrl"
                  readonly
                  @focus="selectGroupInvite"
                  @click="selectGroupInvite"
                />
              </label>
              <div class="invite-actions">
                <button class="btn-small" type="button" @click="copyGroupInvite">
                  {{ groupCopied ? t('seat.group.copied') : t('seat.group.copyInvite') }}
                </button>
                <span class="invite-hint" v-if="showGroupHttpsHint">{{ t('seat.group.httpCopyHint') }}</span>
              </div>
            </div>

            <div class="group-meta">
              <span>{{ t('seat.group.host') }} {{ groupOrder.hostDisplayName }}</span>
              <span>{{ t('seat.group.totalSeats') }} {{ groupClaimedCount }}/{{ groupOrder.maxSeats }}</span>
              <span>{{ t('seat.group.groupTotal') }} {{ money(groupOrder.totalAmount) }}</span>
            </div>

            <div class="group-progress" aria-label="group order progress">
              <div
                v-for="step in groupProgressSteps"
                :key="step.key"
                class="group-progress-step"
                :class="{ active: step.active, current: step.current }"
              >
                <span></span>
                <strong>{{ step.label }}</strong>
              </div>
            </div>

            <div class="my-group-summary">
              <div>
                <span>{{ t('seat.group.myGroup') }}</span>
                <strong>{{ currentMemberSeatCount }} {{ t('order.tickets') }}</strong>
              </div>
              <p>{{ currentMemberSeatLabel }}</p>
              <div class="my-group-footer">
                <small>{{ groupPersonalStatus }}</small>
                <strong>{{ money(currentMemberAmount) }}</strong>
              </div>
            </div>

            <div class="group-status-card pending" v-if="groupCheckedOut">
              <strong>{{ t('seat.group.checkoutPending') }}</strong>
              <p>{{ t('seat.group.checkoutPendingHint') }}</p>
            </div>

            <div class="group-status-card paid" v-else-if="groupPaid">
              <strong>{{ groupTicketReady ? t('seat.group.paymentCompleted') : t('seat.group.ticketSyncing') }}</strong>
              <p>{{ groupTicketReady ? t('seat.group.paymentCompletedHint') : t('seat.group.ticketSyncingHint') }}</p>
              <div class="group-sync-line" v-if="!groupTicketReady">
                <span class="sync-dot" :class="{ spinning: groupTicketSyncing }"></span>
                <small>{{ t('seat.group.ticketSyncingHint') }}</small>
              </div>
              <div class="group-ticket-actions" v-else>
                <button
                  class="btn-small primary"
                  type="button"
                  :disabled="!groupOrder.orderId || !groupTicketReady"
                  @click="viewGroupOrderTicket"
                >
                  {{ t('seat.group.openMyTicket') }}
                </button>
                <button class="btn-small" type="button" @click="viewMyTickets">
                  {{ t('seat.group.viewMyTickets') }}
                </button>
              </div>
            </div>

            <div class="group-status-card closed" v-else-if="groupClosed">
              <strong>{{ t('seat.group.closed') }}</strong>
              <p>{{ t('seat.group.closedHint') }}</p>
            </div>

            <div class="member-list">
              <div class="member-row" v-for="member in groupOrder.members" :key="member.userId">
                <div>
                  <strong>{{ member.displayName }}</strong>
                  <p>{{ memberSeatLabel(member) }}</p>
                </div>
                <span>{{ money(member.amount) }}</span>
              </div>
            </div>
          </template>
        </section>

        <div class="summary-meta">
          <div>
            <span>{{ t('seat.selectedCount') }}</span>
            <strong>{{ selectedCountText }}</strong>
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
                  <button
                    class="ticket-close btn-interactive"
                    type="button"
                    :disabled="!groupCanEdit"
                    @click="toggleSeat(s)"
                    :title="t('common.cancel')"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                      <line x1="18" y1="6" x2="6" y2="18"></line>
                      <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                  </button>
                </div>
                <div class="ticket-body">
                  <span class="seat-name">{{ t('seat.info', { row: s.row, col: s.col }) }}</span>
                  <span class="seat-price">{{ money(s.price) }}</span>
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
          <div class="legend-divider" v-if="priceTiers.length > 1">{{ t('seat.priceTier') }}</div>
          <div
            v-for="tier in priceTiers"
            :key="tier.price"
            class="legend-item price-tier-item"
          >
            <div class="box price-tier-box" :style="{ '--seat-tier-color': tier.color }"></div>
            {{ tier.label }}
          </div>
        </div>

        <div class="checkout-dock">
          <div class="total-bar">
            <span>{{ t('seat.total') }}</span>
            <span class="amount">{{ money(totalAmount) }}</span>
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
            :disabled="selectedSeatIds.size === 0 || groupBusy || !groupCanEdit"
            @click="joinCurrentGroup"
          >
            {{ groupBusy ? t('common.processing') : t('seat.group.joinOrUpdate') }}
          </button>
          <button
            v-if="isGroupHost"
            class="btn-checkout"
            :disabled="groupBusy || !groupOrder || !groupCanEdit"
            @click="checkoutCurrentGroup"
          >
            {{ t('seat.group.hostCheckout') }}
          </button>
          <button
            v-if="isGroupHost && groupCanEdit"
            class="btn-secondary danger"
            :disabled="groupBusy || !groupOrder || !groupCanEdit"
            @click="cancelCurrentGroup"
          >
            {{ t('seat.group.cancel') }}
          </button>
          <button
            v-if="isGroupHost && !groupCanEdit"
            class="btn-secondary"
            :disabled="groupBusy"
            @click="exitGroupMode"
          >
            {{ t('seat.group.leave') }}
          </button>
          <button
            v-if="!isGroupHost"
            class="btn-secondary"
            :disabled="groupBusy || (!currentMember && groupOrder?.status === 'OPEN')"
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
  --selection-side-width: 420px;
  --selection-map-shift: 0px;
  display: flex;
  min-height: calc(100vh - 76px);
  height: calc(100vh - 76px);
  width: 100%;

  &.group-wide-2d {
    --selection-side-width: clamp(500px, 36vw, 580px);
    --selection-map-shift: -18px;
  }

  @media (max-width: 900px) {
    --selection-side-width: 100%;
    --selection-map-shift: 0px;
    flex-direction: column;
    height: auto;
  }
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: rgba(3, 3, 5, 0.94);
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
  transform: translate3d(var(--selection-map-shift), 0, 0);
  transition: transform 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

.stage-container {
  width: 78%;
  max-width: 740px;
  text-align: center;
  position: relative;

  .stage-apron {
    height: 10px;
    border-radius: 50% / 100% 100% 0 0;
    background: linear-gradient(180deg, #d8a66c 0%, rgba(200, 149, 90, 0.18) 100%);
    box-shadow: 0 -5px 18px rgba(200, 149, 90, 0.42);
    border: 1px solid rgba(222, 176, 116, 0.92);
    border-bottom: none;
  }

  .stage-label {
    font-family: var(--font-family-sans);
    font-size: 11px;
    font-weight: 700;
    color: #d8a66c;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    margin-top: 8px;
    text-shadow: 0 0 12px rgba(200, 149, 90, 0.28);
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
    background: rgba(255, 255, 255, 0.26);
    border-radius: 3px;
  }
}

.seat-map {
  /* 座位尺寸统一由变量驱动：演示/大屏下加大座位，提高投影可读性；
     中小屏自动回落，超宽看台仍有横向滚动兜底。图例方块在 .seat-map 外，
     取 fallback 24px 不受影响。 */
  --seat-size: 40px;
  --seat-gap: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2);

  @media (max-width: 1600px) {
    --seat-size: 32px;
    --seat-gap: 7px;
  }

  @media (max-width: 1200px) {
    --seat-size: 26px;
    --seat-gap: 6px;
  }

  .column-label-row {
    display: flex;
    align-items: center;
    gap: var(--spacing-4);
    padding-bottom: 2px;

    &.bottom {
      padding-top: 2px;
      padding-bottom: 0;
    }

    .row-label.spacer {
      width: var(--seat-size, 24px);
      visibility: hidden;
    }

    .column-labels {
      display: flex;
      gap: var(--seat-gap, 6px);

      span {
        width: var(--seat-size, 24px);
        height: 14px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: rgba(255, 255, 255, 0.38);
        font-family: var(--font-family-sans);
        font-size: 11px;
        font-weight: 700;
        font-variant-numeric: tabular-nums;
        line-height: 1;
      }
    }
  }

  .seat-row {
    display: flex;
    align-items: center;
    gap: var(--spacing-4);

    .row-label {
      width: var(--seat-size, 24px);
      text-align: center;
      font-size: 13px;
      color: rgba(255, 255, 255, 0.54);
      font-family: var(--font-family-sans);
      font-weight: 700;
    }

    .seats-container {
      display: flex;
      gap: var(--seat-gap, 6px);
    }
  }
}

.seat-item, .box {
  width: var(--seat-size, 24px);
  height: var(--seat-size, 24px);
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.34);
  background-color: rgba(255, 255, 255, 0.045);
  cursor: pointer;
  position: relative;
  transition: all 150ms cubic-bezier(0.4, 0, 0.2, 1);

  &:hover:not(:disabled) {
    border-color: #d8a66c;
    background-color: rgba(200, 149, 90, 0.12);
    transform: scale(1.15);
    box-shadow: 0 4px 10px rgba(200, 149, 90, 0.24);
  }

  &.status-available {
    background-color: rgba(255, 255, 255, 0.045);
    border-color: rgba(255, 255, 255, 0.34);

    &.tier-premium,
    &.tier-standard,
    &.tier-value,
    &.tier-extra {
      border-color: color-mix(in srgb, var(--seat-tier-color) 76%, rgba(255, 255, 255, 0.2));
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.025)),
        color-mix(in srgb, var(--seat-tier-color) 16%, transparent);

      &::before {
        content: '';
        position: absolute;
        left: 5px;
        right: 5px;
        bottom: 4px;
        height: 2px;
        border-radius: 999px;
        background: var(--seat-tier-color);
        opacity: 0.9;
      }
    }
  }

  &.status-locked {
    background-color: rgba(255, 255, 255, 0.085);
    border-color: rgba(255, 255, 255, 0.22);
    border-style: dashed;
    cursor: not-allowed;
    &::after {
      content: '';
      position: absolute;
      width: 5px;
      height: 5px;
      background-color: rgba(255, 255, 255, 0.56);
      border-radius: 50%;
    }
  }

  &.status-sold {
    background-color: rgba(255, 255, 255, 0.02);
    border-color: rgba(255, 255, 255, 0.08);
    cursor: not-allowed;
    &::after {
      content: '';
      position: absolute;
      width: 7px;
      height: 7px;
      background-color: rgba(255, 255, 255, 0.42);
      border-radius: 50%;
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

    &::before {
      opacity: 0;
    }

    .seat-dot {
      width: 8px;
      height: 8px;
      background-color: #080808;
      border-radius: 50%;
    }
  }
}

.box {
  cursor: default;

  &.price-tier-box {
    border-color: color-mix(in srgb, var(--seat-tier-color) 72%, rgba(255, 255, 255, 0.24));
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02)),
      color-mix(in srgb, var(--seat-tier-color) 22%, transparent);

    &::before {
      content: '';
      position: absolute;
      left: 5px;
      right: 5px;
      bottom: 4px;
      height: 2px;
      border-radius: 999px;
      background: var(--seat-tier-color);
    }
  }
}

.side-panel {
  width: var(--selection-side-width);
  flex-basis: var(--selection-side-width);
  height: calc(100vh - 76px);
  flex-shrink: 0;
  position: sticky;
  top: 76px;
  overflow-y: auto;
  background: rgba(12, 12, 15, 0.94);
  border-left: 1px solid var(--color-border);
  padding: var(--spacing-4);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
  transition:
    width 260ms cubic-bezier(0.22, 1, 0.36, 1),
    flex-basis 260ms cubic-bezier(0.22, 1, 0.36, 1);

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
    flex-basis: auto;
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
    contain: layout paint;
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
    min-width: 82px;
    border: 1px solid rgba(212, 175, 55, 0.35);
    border-radius: 6px;
    color: var(--color-accent);
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    line-height: 1;
    padding: 8px 10px;
    text-align: center;

    &.settled {
      border-color: rgba(255, 255, 255, 0.18);
      color: var(--color-text-primary);
    }
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
    min-height: 86px;
  }

  .invite-field {
    display: grid;
    gap: 6px;

    span {
      color: var(--color-text-secondary);
      font-size: 12px;
      font-weight: 700;
    }
  }

  .invite-url {
    width: 100%;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.04);
    color: var(--color-text-secondary);
    cursor: text;
    font-family: var(--font-family-sans);
    font-size: 12px;
    line-height: 1.4;
    outline: none;
    padding: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;

    &:focus {
      border-color: rgba(212, 175, 55, 0.55);
      color: var(--color-text-primary);
    }
  }

  .invite-actions {
    display: grid;
    gap: 8px;
  }

  .invite-hint {
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.45;
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

    &:disabled {
      opacity: 0.48;
      cursor: not-allowed;
    }

    &.primary {
      background: var(--color-accent);
      border-color: var(--color-accent);
      color: #080808;

      &:hover:not(:disabled) {
        color: #080808;
        filter: brightness(1.05);
      }
    }
  }

  .group-meta {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    margin-bottom: var(--spacing-3);
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.35;

    span {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
  }

  .group-progress {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    margin-bottom: var(--spacing-3);
  }

  .group-progress-step {
    min-width: 0;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.025);
    color: var(--color-text-secondary);
    display: grid;
    gap: 6px;
    padding: 9px 8px;

    span {
      width: 18px;
      height: 2px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.22);
    }

    strong {
      overflow: hidden;
      font-size: 11px;
      font-weight: 800;
      line-height: 1.25;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &.active {
      border-color: rgba(212, 175, 55, 0.26);
      color: var(--color-text-primary);

      span {
        background: var(--color-accent);
      }
    }

    &.current {
      background: rgba(212, 175, 55, 0.06);
    }
  }

  .my-group-summary {
    display: grid;
    gap: 9px;
    margin-bottom: var(--spacing-3);
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 8px;
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.018)),
      rgba(8, 8, 8, 0.38);
    padding: var(--spacing-3);

    > div:first-child,
    .my-group-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--spacing-2);
    }

    span,
    small {
      color: var(--color-text-secondary);
      font-size: 12px;
      font-weight: 700;
      line-height: 1.35;
    }

    strong {
      color: var(--color-text-primary);
      font-size: 13px;
      font-weight: 800;
      line-height: 1.25;
    }

    p {
      margin: 0;
      color: var(--color-text-primary);
      font-size: 12px;
      line-height: 1.45;
    }

    .my-group-footer strong {
      color: var(--color-accent);
      font-variant-numeric: tabular-nums;
    }
  }

  .group-status-card {
    display: grid;
    gap: 8px;
    margin-bottom: var(--spacing-3);
    min-height: 94px;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    padding: var(--spacing-3);
    background: rgba(255, 255, 255, 0.04);

    strong {
      color: var(--color-text-primary);
      font-size: 13px;
    }

    p {
      margin: 0;
      color: var(--color-text-secondary);
      font-size: 12px;
      line-height: 1.45;
    }

    &.pending {
      border-color: rgba(212, 175, 55, 0.35);
      background: rgba(212, 175, 55, 0.06);
    }

    &.paid {
      border-color: rgba(93, 214, 143, 0.35);
      background: rgba(93, 214, 143, 0.06);
    }

    &.closed {
      border-color: rgba(255, 255, 255, 0.16);
      background: rgba(255, 255, 255, 0.035);
    }
  }

  .group-ticket-actions {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .group-sync-line {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.45;
  }

  .sync-dot {
    width: 7px;
    height: 7px;
    border-radius: 999px;
    background: var(--color-accent);
    box-shadow: 0 0 0 4px rgba(212, 175, 55, 0.08);

    &.spinning {
      animation: pulse-sync 900ms ease-in-out infinite;
    }
  }

  .member-list {
    display: grid;
    gap: var(--spacing-2);
    min-height: 54px;
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
      font-variant-numeric: tabular-nums;
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

    .legend-divider {
      grid-column: 1 / -1;
      margin-top: 2px;
      padding-top: var(--spacing-2);
      border-top: 1px solid var(--color-border);
      color: rgba(255, 255, 255, 0.52);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .price-tier-item {
      min-width: 0;
      font-variant-numeric: tabular-nums;
      color: rgba(240, 237, 232, 0.76);
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

@keyframes pulse-sync {
  0%, 100% {
    opacity: 0.45;
    transform: scale(0.86);
  }
  50% {
    opacity: 1;
    transform: scale(1.12);
  }
}
</style>
