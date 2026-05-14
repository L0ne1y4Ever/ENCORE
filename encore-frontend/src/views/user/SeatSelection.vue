<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSeatMap, lockSeats } from '../../api/seat'
import type { Seat } from '../../mock/seats'

const route = useRoute()
const router = useRouter()
const scheduleId = route.params.id as string

const seats = ref<Seat[]>([])
const selectedSeatIds = ref<Set<string>>(new Set())
const loading = ref(true)
const locking = ref(false)

onMounted(async () => {
  seats.value = await getSeatMap(scheduleId)
  loading.value = false
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
    alert('Seats are no longer available, please reselect.')
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
        <div class="stage-bar">STAGE</div>
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
              :title="`Row ${seat.row} Col ${seat.col} - $${seat.price}`"
            >
            </div>
          </div>
        </div>
      </div>
    </div>

    <aside class="side-panel">
      <h2>Selection</h2>
      
      <div class="legend">
        <div class="legend-item"><div class="box status-available"></div> Available</div>
        <div class="legend-item"><div class="box status-locked"></div> Locked</div>
        <div class="legend-item"><div class="box status-sold"></div> Sold</div>
        <div class="legend-item"><div class="box selected"></div> Your Selection</div>
      </div>

      <div class="selected-list">
        <div class="empty-msg" v-if="selectedSeats.length === 0">No seats selected</div>
        <div v-for="s in selectedSeats" :key="s.id" class="selected-item">
          <span>Row {{ s.row }} Col {{ s.col }}</span>
          <span>${{ s.price }}</span>
        </div>
      </div>

      <div class="total-bar">
        <span>Total</span>
        <span class="amount">${{ totalAmount }}</span>
      </div>

      <button 
        class="btn-checkout" 
        :disabled="selectedSeatIds.size === 0 || locking"
        @click="submitLock"
      >
        {{ locking ? 'Locking...' : 'Checkout' }}
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
