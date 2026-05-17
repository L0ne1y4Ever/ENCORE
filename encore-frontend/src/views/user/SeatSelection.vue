<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSeatMap, lockSeats } from '../../api/seat'
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
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const scheduleId = route.params.id as string

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
    seats.value = await getSeatMap(scheduleId)
  } finally {
    loading.value = false
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
  showRealtimeNotice('seat.liveUpdated')
}

onMounted(async () => {
  await refreshSeatMap(true)
  disconnectRealtime = subscribeToSeatUpdates(scheduleId, {
    onEvent: (event) => {
      void applySeatStatusEvent(event)
    },
    onStateChange: (state) => {
      realtimeState.value = state
    }
  })
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
    const data = await leaveGroupOrder(groupInviteCode.value)
    groupOrder.value = data
    selectedSeatIds.value.clear()
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
    const data = await cancelGroupOrder(groupInviteCode.value)
    groupOrder.value = data
    selectedSeatIds.value.clear()
    stopGroupPolling()
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
    return `${t('seat.row')} ${seat.row} ${t('seat.col')} ${seat.col}`
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
    // 成功后，将选中的座位和金额传递给确认页面 (这里简单用 sessionStorage 模拟传递)
    sessionStorage.setItem('tempOrder', JSON.stringify({
      scheduleId,
      seatIds: Array.from(selectedSeatIds.value),
      totalAmount: totalAmount.value
    }))
    router.push('/confirm')
  } catch (e) {
    alert(t('seat.conflict'))
    seats.value = await getSeatMap(scheduleId) // refresh
    selectedSeatIds.value.clear()
  } finally {
    locking.value = false
  }
}

// 基于数据简单分行
const seatGrid = computed(() => {
  const grid: Record<number, Seat[]> = {}
  seats.value.forEach(s => {
    if (!grid[s.row]) grid[s.row] = []
    grid[s.row].push(s)
  })
  return Object.values(grid)
})
</script>

<template>
  <div class="seat-selection">
    <div class="main-area">
      <SeatStagePreview
        v-if="!loading"
        :seats="seats"
        :selected-seat-ids="selectedSeatIds"
        :stage-label="t('seat.stagePreview')"
        :unavailable-label="t('seat.previewUnavailable')"
        :row-label="t('seat.row')"
        :col-label="t('seat.col')"
        @toggle-seat="toggleSeat"
      />

      <header class="stage-header">
        <div class="stage-bar">{{ t('seat.stage') }}</div>
      </header>

      <div class="seat-map" v-if="!loading">
        <div class="seat-row" v-for="(rowSeats, idx) in seatGrid" :key="idx">
          <div class="row-label">{{ rowSeats[0].row }}</div>
          <div class="seats-container">
            <div 
              v-for="seat in rowSeats" 
              :key="seat.id"
              class="seat-item"
              :class="[
                `status-${seat.status.toLowerCase()}`, 
                { 'selected': selectedSeatIds.has(seat.id) }
              ]"
              @click="toggleSeat(seat)"
              :title="`${t('seat.row')} ${seat.row} ${t('seat.col')} ${seat.col} - $${seat.price}`"
            >
            </div>
          </div>
        </div>
      </div>
    </div>

    <aside class="side-panel">
      <h2>{{ t('seat.selection') }}</h2>

      <div class="realtime-panel" aria-live="polite">
        <div class="realtime-status" :class="`state-${realtimeState}`">
          <span class="status-dot"></span>
          <span>{{ t(`seat.live.${realtimeState}`) }}</span>
        </div>
        <div class="realtime-notice" v-if="realtimeNotice">
          {{ t(realtimeNotice) }}
        </div>
      </div>

      <section class="group-panel" v-if="isGroupMode || groupOrder" aria-live="polite">
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
      
      <div class="legend">
        <div class="legend-item"><div class="box status-available"></div> {{ t('seat.available') }}</div>
        <div class="legend-item"><div class="box status-locked"></div> {{ t('seat.locked') }}</div>
        <div class="legend-item"><div class="box status-sold"></div> {{ t('seat.sold') }}</div>
        <div class="legend-item"><div class="box selected"></div> {{ t('seat.yourSelection') }}</div>
      </div>

      <div class="selected-list">
        <div class="empty-msg" v-if="selectedSeats.length === 0">{{ t('seat.noSeats') }}</div>
        <div v-for="s in selectedSeats" :key="s.id" class="selected-item">
          <span>{{ t('seat.row') }} {{ s.row }} {{ t('seat.col') }} {{ s.col }}</span>
          <span>${{ s.price }}</span>
        </div>
      </div>

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

      <button
        v-if="!isGroupMode"
        class="btn-secondary"
        :disabled="selectedSeatIds.size === 0 || groupBusy"
        @click="startGroupOrder"
      >
        {{ groupBusy ? t('common.processing') : t('seat.group.start') }}
      </button>

      <div class="group-actions" v-else>
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
    </aside>
  </div>
</template>

<style scoped lang="scss">
.seat-selection {
  display: flex;
  height: calc(100vh - 80px);
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
  overflow: auto;
  padding: var(--spacing-6);
  background-color: var(--color-bg-base);
}

.stage-header {
  display: flex;
  justify-content: center;
  margin-bottom: var(--spacing-5);

  .stage-bar {
    width: 60%;
    height: 40px;
    border: 2px solid var(--color-border-strong);
    border-radius: 50% 50% 0 0 / 100% 100% 0 0;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    padding-bottom: var(--spacing-2);
    font-family: var(--font-family-sans);
    font-size: 14px;
    letter-spacing: 0.2em;
    color: var(--color-text-secondary);
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
      width: 20px;
      text-align: right;
      font-size: 12px;
      color: var(--color-text-ghost);
      font-family: var(--font-family-sans);
    }

    .seats-container {
      display: flex;
      gap: 6px;
    }
  }
}

.seat-item, .box {
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 150ms ease;

  &.status-available {
    background-color: var(--color-bg-elevated);
    border: 1px solid var(--color-border-strong);
    &:hover {
      border-color: var(--color-text-primary);
    }
  }

  &.status-locked {
    background-color: var(--color-text-ghost);
    cursor: not-allowed;
  }

  &.status-sold {
    background-color: transparent;
    border: 1px solid var(--color-border);
    opacity: 0.3;
    cursor: not-allowed;
  }

  &.status-disabled {
    opacity: 0;
    pointer-events: none;
  }

  &.selected {
    background-color: var(--color-accent);
    border-color: var(--color-accent);
  }
}

.box {
  cursor: default;
}

.side-panel {
  width: 320px;
  background-color: var(--color-bg-elevated);
  border-left: 1px solid var(--color-border);
  padding: var(--spacing-6);
  display: flex;
  flex-direction: column;

  @media (max-width: 900px) {
    width: 100%;
    border-left: none;
    border-top: 1px solid var(--color-border);
  }

  h2 {
    font-family: var(--font-family-display);
    font-size: 24px;
    margin-bottom: var(--spacing-4);
  }

  .realtime-panel {
    min-height: 42px;
    margin-bottom: var(--spacing-4);
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

  .group-panel {
    margin-bottom: var(--spacing-5);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    padding: var(--spacing-4);
    background: rgba(0, 0, 0, 0.16);
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
    margin-bottom: var(--spacing-6);
    font-size: 12px;
    color: var(--color-text-secondary);

    .legend-item {
      display: flex;
      align-items: center;
      gap: var(--spacing-2);
    }
  }

  .selected-list {
    flex: 1;
    overflow-y: auto;
    margin-bottom: var(--spacing-4);
    border-top: 1px solid var(--color-border);
    padding-top: var(--spacing-4);

    .empty-msg {
      color: var(--color-text-ghost);
      font-size: 14px;
    }

    .selected-item {
      display: flex;
      justify-content: space-between;
      padding: var(--spacing-2) 0;
      font-family: var(--font-family-sans);
      font-size: 14px;
      color: var(--color-text-primary);
      border-bottom: 1px solid var(--color-border);
    }
  }

  .total-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--spacing-4);
    font-family: var(--font-family-sans);
    font-size: 16px;
    
    .amount {
      font-size: 24px;
      font-weight: 700;
      color: var(--color-accent);
    }
  }

  .btn-checkout {
    width: 100%;
    padding: 16px;
    background-color: var(--color-text-primary);
    color: var(--color-bg-base);
    border: none;
    border-radius: var(--radius-sm);
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: background-color 150ms ease;

    &:hover:not(:disabled) {
      background-color: var(--color-accent);
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
    border: 1px solid var(--color-border-strong);
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--color-text-primary);
    cursor: pointer;
    font-family: var(--font-family-sans);
    font-size: 15px;
    font-weight: 700;
    transition: border-color 150ms ease, color 150ms ease;

    &:hover:not(:disabled) {
      border-color: var(--color-accent);
      color: var(--color-accent);
    }

    &:disabled {
      border-color: var(--color-border);
      color: var(--color-text-ghost);
      cursor: not-allowed;
    }

    &.danger:hover:not(:disabled) {
      border-color: #f0a86b;
      color: #f0a86b;
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
</style>
