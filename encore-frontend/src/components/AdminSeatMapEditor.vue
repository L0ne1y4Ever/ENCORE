<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatMoney } from '../utils/money'

type SeatMapItem = {
  id: string
  status?: string
  row?: number | string
  rowNo?: number | string
  col?: number | string
  colNo?: number | string
  seatCode?: string
  section?: string
  price?: unknown
  [key: string]: unknown
}

const props = withDefaults(defineProps<{
  seats: SeatMapItem[]
  operatingSeatId?: string
  hint?: string
  emptyText?: string
  disabled?: boolean
  disabledReason?: string
  stageLabel?: string
}>(), {
  operatingSeatId: '',
  hint: '',
  emptyText: '',
  disabled: false,
  disabledReason: '',
  stageLabel: ''
})

const emit = defineEmits<{
  (e: 'toggle', seat: SeatMapItem): void
}>()

const { t, locale } = useI18n()

const seatRow = (seat: SeatMapItem) => Number(seat.row ?? seat.rowNo ?? 0)
const seatCol = (seat: SeatMapItem) => Number(seat.col ?? seat.colNo ?? 0)
const seatCode = (seat: SeatMapItem) => String(seat.seatCode ?? seat.id ?? '')

const rowsGrouped = computed(() => {
  const map = new Map<number, SeatMapItem[]>()
  props.seats.forEach((seat) => {
    const row = seatRow(seat)
    if (!map.has(row)) {
      map.set(row, [])
    }
    map.get(row)!.push(seat)
  })

  return Array.from(map.keys())
    .sort((a, b) => a - b)
    .map(rowNum => ({
      rowNum,
      seats: map.get(rowNum)!.sort((a, b) => seatCol(a) - seatCol(b))
    }))
})

const normalizedStatus = (seat: SeatMapItem) => String(seat.status || 'AVAILABLE').toLowerCase().replaceAll('_', '-')

const canToggle = (seat: SeatMapItem) => {
  if (props.disabled || props.operatingSeatId) return false
  return seat.status === 'AVAILABLE' || seat.status === 'DISABLED'
}

const money = (value: unknown) => {
  if (value == null || value === '') return ''
  return formatMoney(value as number | string, locale.value)
}

const seatTitle = (seat: SeatMapItem) => {
  const row = seatRow(seat)
  const col = seatCol(seat)
  const price = money(seat.price)
  return [
    seatCode(seat),
    t('seat.info', { row, col }),
    seat.section,
    seat.status,
    price ? `${t('admin.price')} ${price}` : ''
  ].filter(Boolean).join(' · ')
}

const handleToggle = (seat: SeatMapItem) => {
  if (!canToggle(seat)) return
  emit('toggle', seat)
}
</script>

<template>
  <div class="admin-seat-map-editor">
    <div class="map-toolbar">
      <div>
        <strong>{{ t('admin.seatMapEditor') }}</strong>
        <p>{{ disabled ? (disabledReason || t('admin.seatMapReadonly')) : (hint || t('admin.seatMapClickHint')) }}</p>
      </div>
      <div class="map-legend">
        <span><i class="status-available"></i>{{ t('seat.available') }}</span>
        <span><i class="status-disabled"></i>{{ t('seat.disabled') }}</span>
        <span><i class="status-locked"></i>{{ t('seat.locked') }}</span>
        <span><i class="status-sold"></i>{{ t('seat.sold') }}</span>
      </div>
    </div>

    <template v-if="seats.length > 0">
      <div class="stage-container">
        <div class="stage-apron"></div>
        <div class="stage-label">{{ stageLabel || t('seat.stage') }}</div>
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
                class="seat-item"
                :class="[
                  `status-${normalizedStatus(seat)}`,
                  {
                    operating: operatingSeatId === seat.id,
                    editable: canToggle(seat)
                  }
                ]"
                :disabled="!canToggle(seat)"
                :title="seatTitle(seat)"
                @click="handleToggle(seat)"
              >
                <span class="seat-dot" v-if="seat.status === 'DISABLED'"></span>
              </button>
            </div>
            <span class="row-label">{{ row.rowNum }}</span>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="map-empty">
      {{ emptyText || t('admin.seatMapUnavailable') }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-seat-map-editor {
  width: 100%;
  min-width: 0;
}

.map-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-4);

  strong {
    display: block;
    font-size: 15px;
    color: var(--color-text-primary);
  }

  p {
    margin-top: 4px;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 1.45;
  }
}

.map-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--spacing-2);
  color: var(--color-text-secondary);
  font-size: 12px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;
  }

  i {
    width: 14px;
    height: 14px;
    border-radius: 4px;
    border: 1px solid rgba(255, 255, 255, 0.34);
    background: rgba(255, 255, 255, 0.05);
  }

  .status-disabled {
    border-style: dashed;
    border-color: rgba(255, 255, 255, 0.34);
    background: rgba(255, 255, 255, 0.08);
  }

  .status-locked {
    border-style: dashed;
    border-color: rgba(255, 255, 255, 0.24);
    background: rgba(255, 255, 255, 0.09);
  }

  .status-sold {
    border-color: rgba(255, 255, 255, 0.14);
    background: rgba(255, 255, 255, 0.24);
  }
}

.stage-container {
  width: 70%;
  max-width: 500px;
  margin: 0 auto var(--spacing-5);
  text-align: center;
  position: relative;

  .stage-apron {
    height: 10px;
    border: 1px solid rgba(222, 176, 116, 0.92);
    border-bottom: none;
    border-radius: 50% / 100% 100% 0 0;
    background: linear-gradient(180deg, #d8a66c 0%, rgba(200, 149, 90, 0.18) 100%);
    box-shadow: 0 -5px 18px rgba(200, 149, 90, 0.44);
  }

  .stage-label {
    margin-top: 8px;
    color: #d8a66c;
    font-family: var(--font-family-sans);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    text-shadow: 0 0 12px rgba(200, 149, 90, 0.28);
  }
}

.seat-grid-container {
  width: 100%;
  overflow: auto;
  display: flex;
  justify-content: center;
  padding: var(--spacing-2) 0 var(--spacing-4);
  max-height: min(62vh, 680px);

  &::-webkit-scrollbar {
    width: 6px;
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
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2);
}

.seat-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
}

.row-label {
  width: 24px;
  text-align: center;
  color: rgba(255, 255, 255, 0.54);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 700;
}

.seats-container {
  display: flex;
  gap: 6px;
}

.seat-item {
  width: 24px;
  height: 24px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.045);
  cursor: not-allowed;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  position: relative;
  transition: border-color 150ms cubic-bezier(0.4, 0, 0.2, 1),
    background-color 150ms cubic-bezier(0.4, 0, 0.2, 1),
    transform 150ms cubic-bezier(0.4, 0, 0.2, 1),
    box-shadow 150ms cubic-bezier(0.4, 0, 0.2, 1);

  &.editable {
    cursor: pointer;

    &:hover:not(:disabled) {
      border-color: #d8a66c;
      background: rgba(200, 149, 90, 0.12);
      transform: scale(1.15);
      box-shadow: 0 4px 10px rgba(200, 149, 90, 0.24);
    }
  }

  &.status-disabled {
    border-style: dashed;
    border-color: rgba(255, 255, 255, 0.34);
    background: rgba(255, 255, 255, 0.085);
  }

  &.status-locked {
    border-style: dashed;
    border-color: rgba(255, 255, 255, 0.22);
    background: rgba(255, 255, 255, 0.085);

    &::after {
      content: '';
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.56);
    }
  }

  &.status-sold {
    border-color: rgba(255, 255, 255, 0.08);
    background: rgba(255, 255, 255, 0.02);

    &::after {
      content: '';
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.42);
    }
  }

  &.operating {
    border-color: var(--color-accent);
    box-shadow: 0 0 10px rgba(200, 149, 90, 0.4);
    opacity: 0.65;
  }

  &:disabled {
    cursor: not-allowed;
  }
}

.seat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.48);
}

.map-empty {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
}

@media (max-width: 900px) {
  .map-toolbar {
    flex-direction: column;
  }

  .map-legend {
    justify-content: flex-start;
  }

  .seat-row {
    gap: var(--spacing-2);
  }
}
</style>
