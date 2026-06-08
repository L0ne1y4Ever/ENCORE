<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Clock, Tickets, Wallet } from '@element-plus/icons-vue'
import { createOrder } from '../../api/order'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()
const orderData = ref<any>(null)
const timeLeft = ref(15 * 60)
const timer = ref<number | null>(null)
const submitting = ref(false)

onMounted(() => {
  const data = sessionStorage.getItem('tempOrder')
  if (!data) {
    router.replace('/')
    return
  }
  try {
    const parsed = JSON.parse(data)
    if (
      !parsed ||
      typeof parsed.scheduleId !== 'string' ||
      !Array.isArray(parsed.seatIds) ||
      parsed.seatIds.length === 0
    ) {
      throw new Error('Invalid temp order')
    }
    orderData.value = parsed
  } catch {
    sessionStorage.removeItem('tempOrder')
    router.replace('/')
    return
  }

  timer.value = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--
    } else {
      clearInterval(timer.value!)
      window.alert(t('order.expired'))
      router.replace('/')
    }
  }, 1000) as unknown as number
})

onUnmounted(() => {
  if (timer.value) clearInterval(timer.value)
})

const formatTime = (secs: number) => {
  const m = Math.floor(secs / 60).toString().padStart(2, '0')
  const s = (secs % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

const ticketCount = computed(() => orderData.value?.seatIds?.length || 0)

const doConfirm = async () => {
  if (submitting.value || !orderData.value) return
  submitting.value = true
  try {
    const orderId = await createOrder(orderData.value.scheduleId, orderData.value.seatIds)
    sessionStorage.removeItem('tempOrder')
    router.push(`/payment?id=${orderId}`)
  } catch {
    window.alert(t('order.createFailed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="orderData" class="confirm-page">
    <div class="checkout-shell">
      <div class="checkout-copy">
        <span class="eyebrow">{{ t('order.checkout') }}</span>
        <h1>{{ t('order.confirmation') }}</h1>
        <p>{{ t('order.reviewCopy') }}</p>
      </div>

      <div class="checkout-card">
        <ol class="stepper" aria-label="Checkout steps">
          <li class="done"><Tickets /> <span>{{ t('order.stepSelect') }}</span></li>
          <li class="active"><Clock /> <span>{{ t('order.stepConfirm') }}</span></li>
          <li><Wallet /> <span>{{ t('order.stepPay') }}</span></li>
        </ol>

        <div class="timer-box">
          <span>{{ t('order.paymentDeadline') }}</span>
          <strong>{{ formatTime(timeLeft) }}</strong>
        </div>

        <div class="summary-card">
          <div class="row">
            <span>{{ t('order.seats') }}</span>
            <strong>{{ ticketCount }} {{ t('order.tickets') }}</strong>
          </div>
          <div class="row muted">
            <span>{{ t('order.lockedForCheckout') }}</span>
            <strong>{{ t('seat.locking') }}</strong>
          </div>
          <div class="row total-row">
            <span>{{ t('order.totalAmount') }}</span>
            <strong class="amount">${{ orderData.totalAmount }}</strong>
          </div>
        </div>

        <button class="btn-confirm" type="button" @click="doConfirm" :disabled="submitting">
          <span>{{ submitting ? t('common.processing') : t('order.proceedToPayment') }}</span>
          <ArrowRight v-if="!submitting" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.confirm-page {
  min-height: calc(100vh - 76px);
  display: grid;
  place-items: center;
  padding: var(--spacing-6) var(--spacing-4);
  background:
    linear-gradient(180deg, rgba(200, 149, 90, 0.06) 0%, rgba(8, 8, 8, 0) 300px),
    var(--color-bg-base);
}

.checkout-shell {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 460px);
  gap: var(--spacing-5);
  align-items: center;
}

.checkout-copy {
  .eyebrow {
    color: var(--color-accent);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 900;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  h1 {
    margin-top: var(--spacing-3);
    font-size: clamp(40px, 6vw, 72px);
    line-height: 1;
  }

  p {
    max-width: 480px;
    margin-top: var(--spacing-3);
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 17px;
    line-height: 1.6;
  }
}

.checkout-card {
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-bg-elevated);
  padding: var(--spacing-4);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.38);
}

.stepper {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-2);
  margin: 0 0 var(--spacing-4);
  padding: 0;

  li {
    min-height: 64px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    color: var(--color-text-secondary);
    display: grid;
    place-items: center;
    gap: 5px;
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 800;
    text-align: center;

    svg {
      width: 18px;
      height: 18px;
    }

    &.done,
    &.active {
      border-color: rgba(200, 149, 90, 0.4);
      color: var(--color-accent);
    }

    &.active {
      background: rgba(200, 149, 90, 0.08);
    }
  }
}

.timer-box {
  border: 1px solid rgba(200, 149, 90, 0.36);
  border-radius: var(--radius-md);
  background: rgba(200, 149, 90, 0.08);
  margin-bottom: var(--spacing-4);
  padding: var(--spacing-4);
  display: flex;
  align-items: center;
  justify-content: space-between;

  span {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 800;
  }

  strong {
    color: var(--color-accent);
    font-family: var(--font-family-sans);
    font-size: 32px;
    font-variant-numeric: tabular-nums;
  }
}

.summary-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-base);
  padding: var(--spacing-4);
  display: grid;
  gap: var(--spacing-3);
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  font-family: var(--font-family-sans);

  span {
    color: var(--color-text-secondary);
  }

  strong {
    color: var(--color-text-primary);
  }

  &.muted strong {
    color: var(--color-accent);
    font-size: 13px;
  }

  &.total-row {
    border-top: 1px dashed var(--color-border-strong);
    padding-top: var(--spacing-3);
  }

  .amount {
    color: var(--color-accent);
    font-family: var(--font-family-display);
    font-size: 36px;
  }
}

.btn-confirm {
  width: 100%;
  min-height: 54px;
  margin-top: var(--spacing-4);
  border: none;
  border-radius: 4px;
  background: #e50914;
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2);
  font-family: var(--font-family-sans);
  font-size: 16px;
  font-weight: 900;

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover:not(:disabled) {
    background: #f6121d;
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

@media (max-width: 820px) {
  .checkout-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .stepper {
    grid-template-columns: 1fr;
  }
}
</style>
