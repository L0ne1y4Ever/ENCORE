<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatMoney } from '../utils/money'

type SeatLike = {
  id: string
  row: number
  col: number
  section?: string | null
  status: string
  price: number | string
}

const props = withDefaults(defineProps<{
  seats: SeatLike[]
  selectedSeatIds: string[]
  disabled?: boolean
  stageLabel?: string
}>(), {
  disabled: false,
  stageLabel: ''
})

const emit = defineEmits<{
  (e: 'toggle-seat', seat: SeatLike): void
}>()

const { t, locale } = useI18n()

const selectedSet = computed(() => new Set(props.selectedSeatIds))

const columnNumbers = computed(() => {
  return Array.from(new Set(
    props.seats
      .map(seat => Number(seat.col))
      .filter(col => Number.isFinite(col))
  )).sort((a, b) => a - b)
})

const rowsGrouped = computed(() => {
  const map = new Map<number, SeatLike[]>()
  props.seats.forEach((seat) => {
    const row = Number(seat.row || 0)
    if (!map.has(row)) map.set(row, [])
    map.get(row)!.push(seat)
  })
  return Array.from(map.keys())
    .sort((a, b) => a - b)
    .map(row => {
      const rowSeats = map.get(row)!.sort((a, b) => Number(a.col || 0) - Number(b.col || 0))
      const seatByColumn = new Map(rowSeats.map(seat => [Number(seat.col), seat]))
      return {
        row,
        seats: columnNumbers.value.map(col => seatByColumn.get(col) || null)
      }
    })
})

const priceTiers = computed(() => {
  const prices = Array.from(new Set(props.seats.map(seat => Number(seat.price)).filter(Number.isFinite)))
    .sort((a, b) => b - a)
  const colors = ['#d8a66c', '#78c7ae', '#8aa9c7', '#c5c2b8']
  return new Map(prices.map((price, index) => [price, colors[index] || colors[colors.length - 1]]))
})

const seatClass = (seat: SeatLike | null) => {
  if (!seat) return ['status-disabled', 'empty']
  const status = String(seat.status || 'AVAILABLE').toLowerCase().replaceAll('_', '-')
  return [
    `status-${status}`,
    selectedSet.value.has(seat.id) ? 'selected' : ''
  ]
}

const seatStyle = (seat: SeatLike | null) => {
  const color = seat ? priceTiers.value.get(Number(seat.price)) || 'rgba(255,255,255,0.34)' : 'rgba(255,255,255,0.34)'
  return { '--seat-tier-color': color }
}

const seatTitle = (seat: SeatLike | null) => {
  if (!seat) return ''
  return [
    seat.id,
    t('seat.info', { row: seat.row, col: seat.col }),
    seat.section,
    seat.status,
    formatMoney(seat.price, locale.value)
  ].filter(Boolean).join(' · ')
}

const canToggle = (seat: SeatLike | null) => {
  if (!seat || props.disabled) return false
  return seat.status === 'AVAILABLE' || selectedSet.value.has(seat.id)
}
</script>

<template>
  <div class="schedule-seat-map-2d">
    <div class="stage-container">
      <div class="stage-apron"></div>
      <div class="stage-label">{{ stageLabel || t('seat.stage') }}</div>
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

        <div v-for="row in rowsGrouped" :key="row.row" class="seat-row">
          <span class="row-label">{{ row.row }}</span>
          <div class="seats-container">
            <button
              v-for="(seat, index) in row.seats"
              :key="seat?.id || `empty-${row.row}-${index}`"
              type="button"
              class="seat-item"
              :class="seatClass(seat)"
              :style="seatStyle(seat)"
              :title="seatTitle(seat)"
              :disabled="!canToggle(seat)"
              @click="seat && emit('toggle-seat', seat)"
            >
              <span class="seat-dot" v-if="seat && selectedSet.has(seat.id)"></span>
            </button>
          </div>
          <span class="row-label">{{ row.row }}</span>
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
</template>

<style scoped lang="scss">
.schedule-seat-map-2d {
  min-width: 0;
}

.stage-container {
  width: min(560px, 74%);
  margin: 0 auto var(--spacing-6);
  text-align: center;
  pointer-events: none;

  .stage-apron {
    height: 16px;
    border-radius: 50%;
    background: radial-gradient(ellipse at center, rgba(216, 166, 108, 0.9), rgba(216, 166, 108, 0.18) 45%, transparent 72%);
    filter: blur(1px);
  }

  .stage-label {
    margin-top: -2px;
    font-size: 12px;
    font-weight: 700;
    color: #d8a66c;
    letter-spacing: .08em;
  }
}

.seat-grid-container {
  overflow: auto;
  padding: var(--spacing-2) 0 var(--spacing-4);
}

.seat-map {
  width: max-content;
  min-width: min(100%, 560px);
  margin: 0 auto;
}

.column-label-row,
.seat-row {
  display: grid;
  grid-template-columns: 34px 1fr 34px;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.column-label-row {
  margin-bottom: 12px;

  &.bottom {
    margin-top: 12px;
    margin-bottom: 0;
  }
}

.row-label {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.column-labels,
.seats-container {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 34px;
  gap: 10px;
}

.column-labels span {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 700;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.seat-item {
  position: relative;
  width: 34px;
  height: 34px;
  border-radius: 7px;
  border: 1px solid color-mix(in srgb, var(--seat-tier-color) 54%, rgba(255,255,255,0.18));
  background:
    linear-gradient(180deg, rgba(255,255,255,0.08), rgba(255,255,255,0.02)),
    color-mix(in srgb, var(--seat-tier-color) 18%, rgba(14, 15, 18, 0.86));
  box-shadow: inset 0 -3px 0 color-mix(in srgb, var(--seat-tier-color) 64%, transparent);
  cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, background .16s ease, box-shadow .16s ease;

  &:not(:disabled):hover {
    transform: translateY(-2px);
    border-color: rgba(255,255,255,0.72);
  }

  &.selected {
    border-color: #e4ad61;
    background: linear-gradient(180deg, #e0aa61, #bd7f34);
    box-shadow: 0 0 18px rgba(216,166,108,.38), inset 0 -3px 0 rgba(255,255,255,.32);
  }

  &.status-locked {
    border-color: rgba(255,255,255,.24);
    background: rgba(255,255,255,.08);
    box-shadow: none;
    cursor: not-allowed;
  }

  &.status-locked::after,
  &.status-sold::after {
    content: '';
    position: absolute;
    inset: 50% auto auto 50%;
    width: 7px;
    height: 7px;
    border-radius: 999px;
    background: rgba(255,255,255,.42);
    transform: translate(-50%, -50%);
  }

  &.status-sold {
    border-color: rgba(255,255,255,.1);
    background: rgba(255,255,255,.025);
    box-shadow: none;
    cursor: not-allowed;
  }

  &.status-disabled,
  &.empty {
    border-color: rgba(255,255,255,.08);
    background: rgba(255,255,255,.018);
    box-shadow: none;
    cursor: not-allowed;
  }

  &.empty {
    opacity: 0;
    pointer-events: none;
  }

  &:disabled {
    opacity: .82;
  }
}

.seat-dot {
  position: absolute;
  inset: 50% auto auto 50%;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #fff6e7;
  transform: translate(-50%, -50%);
}

@media (max-width: 760px) {
  .column-label-row,
  .seat-row {
    grid-template-columns: 28px 1fr 28px;
    gap: 8px;
  }

  .column-labels,
  .seats-container {
    grid-auto-columns: 30px;
    gap: 8px;
  }

  .seat-item {
    width: 30px;
    height: 30px;
  }
}
</style>
