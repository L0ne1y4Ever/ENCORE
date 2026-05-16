<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSeatMap, lockSeats } from '../../api/seat'
import { subscribeToSeatUpdates } from '../../api/seatRealtime'
import type { SeatRealtimeConnectionState, SeatStatusEvent } from '../../api/seatRealtime'
import type { Seat } from '../../mock/seats'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const scheduleId = route.params.id as string

const seats = ref<Seat[]>([])
const selectedSeatIds = ref<Set<string>>(new Set())
const loading = ref(true)
const locking = ref(false)
const realtimeState = ref<SeatRealtimeConnectionState>('connecting')
const realtimeNotice = ref<string | null>(null)
let disconnectRealtime: (() => void) | undefined
let realtimeNoticeTimer: ReturnType<typeof setTimeout> | undefined

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
      !(event.reason === 'LOCKED' && locking.value)
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
})

onBeforeUnmount(() => {
  disconnectRealtime?.()
  if (realtimeNoticeTimer) {
    clearTimeout(realtimeNoticeTimer)
  }
})

const maxSelect = 6

const toggleSeat = (seat: Seat) => {
  if (seat.status !== 'AVAILABLE') return
  
  if (selectedSeatIds.value.has(seat.id)) {
    selectedSeatIds.value.delete(seat.id)
  } else {
    if (selectedSeatIds.value.size < maxSelect) {
      selectedSeatIds.value.add(seat.id)
    }
  }
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
        class="btn-checkout" 
        :disabled="selectedSeatIds.size === 0 || locking"
        @click="submitLock"
      >
        {{ locking ? t('seat.locking') : t('seat.checkout') }}
      </button>
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
  margin-bottom: var(--spacing-8);

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
}
</style>
