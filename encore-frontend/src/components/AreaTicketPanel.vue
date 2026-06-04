<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ScheduleAreaResponse } from '../api/seat'

const props = defineProps<{
  area: ScheduleAreaResponse
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'book-area', quantity: number): void
}>()

const { t } = useI18n()
const quantity = ref(1)

// Reset quantity to 1 when area changes
watch(() => props.area.id, () => {
  quantity.value = 1
})

const maxSelectable = computed(() => {
  return Math.min(4, props.area.availableCount)
})

const totalPrice = computed(() => {
  return (props.area.price * quantity.value).toFixed(2)
})

const increment = () => {
  if (quantity.value < maxSelectable.value) {
    quantity.value++
  }
}

const decrement = () => {
  if (quantity.value > 1) {
    quantity.value--
  }
}

const handleSubmit = () => {
  emit('book-area', quantity.value)
}
</script>

<template>
  <div class="area-ticket-panel">
    <div class="area-header">
      <div class="title-wrap">
        <span class="area-badge" :style="{ backgroundColor: props.area.color + '20', color: props.area.color }">
          {{ props.area.areaType }}
        </span>
        <h3>{{ props.area.name }}</h3>
      </div>
      <div class="price-info">
        <span class="unit-label">{{ t('seat.total') }}</span>
        <span class="price-val">${{ props.area.price }}</span>
      </div>
    </div>

    <div class="area-details">
      <p class="description">{{ props.area.description || t('seat.areaDescFallback') }}</p>
      <div class="stats-row">
        <div class="stat-item">
          <span class="label">{{ t('seat.areaAvailable') }}</span>
          <span class="val">{{ props.area.availableCount }} / {{ props.area.totalCount }}</span>
        </div>
        <div class="stat-item">
          <span class="label">{{ t('seat.areaType') }}</span>
          <span class="val">{{ props.area.isSeated ? t('seat.seatedAuto') : t('seat.standing') }}</span>
        </div>
      </div>
    </div>

    <div class="selection-control">
      <span class="control-label">{{ t('seat.quantity') }}</span>
      <div class="quantity-stepper">
        <button
          type="button"
          class="step-btn btn-interactive"
          :disabled="quantity <= 1 || props.loading"
          @click="decrement"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
        </button>
        <span class="quantity-display">{{ quantity }}</span>
        <button
          type="button"
          class="step-btn btn-interactive"
          :disabled="quantity >= maxSelectable || props.loading"
          @click="increment"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
        </button>
      </div>
    </div>

    <div class="checkout-footer">
      <div class="total-row">
        <span>{{ t('seat.total') }}</span>
        <span class="total-val">${{ totalPrice }}</span>
      </div>
      <button
        type="button"
        class="btn-checkout"
        :disabled="props.area.availableCount === 0 || props.loading"
        @click="handleSubmit"
      >
        <span v-if="props.loading">{{ t('seat.locking') }}</span>
        <span v-else>{{ t('seat.checkout') }}</span>
      </button>
      <p class="limit-hint">{{ t('seat.areaLimitHint') }}</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.area-ticket-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-4);
  font-family: var(--font-family-sans);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(10px);
}

.area-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: var(--spacing-4);
  margin-bottom: var(--spacing-4);

  .title-wrap {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-2);

    h3 {
      font-family: var(--font-family-display);
      font-size: 22px;
      margin: 0;
      color: var(--color-text-primary);
    }
  }

  .area-badge {
    align-self: flex-start;
    font-size: 10px;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: var(--radius-full);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .price-info {
    text-align: right;
    display: flex;
    flex-direction: column;

    .unit-label {
      font-size: 11px;
      color: var(--color-text-ghost);
    }

    .price-val {
      font-size: 24px;
      font-weight: 700;
      color: var(--color-accent);
    }
  }
}

.area-details {
  background-color: rgba(0, 0, 0, 0.2);
  border-radius: var(--radius-md);
  padding: var(--spacing-4);
  margin-bottom: var(--spacing-5);

  .description {
    font-size: 13px;
    line-height: 1.5;
    color: var(--color-text-secondary);
    margin: 0 0 var(--spacing-4) 0;
  }

  .stats-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--spacing-3);
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    padding-top: var(--spacing-4);
  }

  .stat-item {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-1);

    .label {
      font-size: 11px;
      color: var(--color-text-ghost);
    }

    .val {
      font-size: 13px;
      font-weight: 600;
      color: var(--color-text-primary);
    }
  }
}

.selection-control {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-6);

  .control-label {
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-primary);
  }
}

.quantity-stepper {
  display: flex;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.25);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  padding: 2px;

  .step-btn {
    width: 36px;
    height: 36px;
    background: transparent;
    border: none;
    color: var(--color-text-primary);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--radius-sm);
    transition: all 0.2s ease;

    &:hover:not(:disabled) {
      background-color: rgba(255, 255, 255, 0.05);
      color: var(--color-accent);
    }

    &:disabled {
      color: var(--color-text-ghost);
      cursor: not-allowed;
      opacity: 0.5;
    }
  }

  .quantity-display {
    width: 44px;
    text-align: center;
    font-size: 16px;
    font-weight: 700;
    color: var(--color-text-primary);
  }
}

.checkout-footer {
  margin-top: auto;
  border-top: 1px solid var(--color-border);
  padding-top: var(--spacing-5);

  .total-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--spacing-4);
    font-size: 16px;
    color: var(--color-text-secondary);

    .total-val {
      font-size: 28px;
      font-weight: 700;
      color: var(--color-accent);
    }
  }

  .btn-checkout {
    width: 100%;
    padding: 16px;
    background-color: var(--color-accent);
    color: #080808;
    border: none;
    border-radius: var(--radius-sm);
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 0 4px 20px rgba(200, 149, 90, 0.25);

    &:hover:not(:disabled) {
      background-color: #d6a469;
      transform: translateY(-2px);
      box-shadow: 0 6px 24px rgba(200, 149, 90, 0.35);
    }

    &:disabled {
      background-color: var(--color-border-strong);
      color: var(--color-text-ghost);
      cursor: not-allowed;
      transform: none;
      box-shadow: none;
    }
  }

  .limit-hint {
    text-align: center;
    font-size: 11px;
    color: var(--color-text-ghost);
    margin: var(--spacing-3) 0 0 0;
  }
}
</style>
