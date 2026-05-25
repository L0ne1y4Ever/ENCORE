<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
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
  orderData.value = JSON.parse(data)

  timer.value = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--
    } else {
      clearInterval(timer.value!)
      alert(t('order.expired'))
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

const doConfirm = async () => {
  submitting.value = true
  try {
    // 假设当前用户 ID 为 u-101
    const orderId = await createOrder('u-101', orderData.value.scheduleId, orderData.value.seatIds, orderData.value.totalAmount)
    sessionStorage.removeItem('tempOrder')
    router.push(`/payment?id=${orderId}`)
  } catch (e) {
    alert(t('order.createFailed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="confirm-page" v-if="orderData">
    <div class="content">
      <h1 class="page-title">{{ t('order.confirmation') }}</h1>

      <div class="timer-box">
        <div class="timer-label">{{ t('order.paymentDeadline') }}</div>
        <div class="timer-value">{{ formatTime(timeLeft) }}</div>
      </div>

      <div class="summary-card">
        <div class="row">
          <span class="label">{{ t('order.seats') }}</span>
          <span class="value">{{ orderData.seatIds.length }} {{ t('order.tickets') }}</span>
        </div>
        <div class="row total-row">
          <span class="label">{{ t('order.totalAmount') }}</span>
          <span class="value amount">${{ orderData.totalAmount }}</span>
        </div>
      </div>

      <button class="btn-confirm" @click="doConfirm" :disabled="submitting">
        {{ submitting ? t('common.processing') : t('order.proceedToPayment') }}
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.confirm-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 80px);
}

.content {
  width: 100%;
  max-width: 480px;
  padding: var(--spacing-6);
}

.page-title {
  font-family: var(--font-family-display);
  font-size: 40px;
  text-align: center;
  margin-bottom: var(--spacing-8);
}

.timer-box {
  text-align: center;
  margin-bottom: var(--spacing-8);
  padding: var(--spacing-4);
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);

  .timer-label {
    font-size: 14px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-2);
  }

  .timer-value {
    font-family: var(--font-family-sans);
    font-size: 32px;
    font-weight: 700;
    color: var(--color-accent);
  }
}

.summary-card {
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--spacing-6);
  margin-bottom: var(--spacing-6);
  box-shadow: 0 10px 30px rgba(0,0,0,0.5);

  .row {
    display: flex;
    justify-content: space-between;
    margin-bottom: var(--spacing-4);
    font-size: 16px;
    font-family: var(--font-family-sans);

    .label {
      color: var(--color-text-secondary);
    }

    &.total-row {
      margin-top: var(--spacing-6);
      padding-top: var(--spacing-4);
      border-top: 1px dashed var(--color-border-strong);
      margin-bottom: 0;
      align-items: center;

      .amount {
        font-family: var(--font-family-display);
        font-size: 32px;
        font-weight: 700;
        color: var(--color-accent);
      }
    }
  }
}

.btn-confirm {
  width: 100%;
  padding: 18px;
  background-color: var(--color-accent);
  color: #000;
  border: none;
  border-radius: var(--radius-sm);
  font-family: var(--font-family-sans);
  font-size: 16px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: all 150ms cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform;

  &:hover:not(:disabled) {
    background-color: var(--color-accent-hover);
    box-shadow: 0 4px 20px rgba(200, 149, 90, 0.3);
  }

  &:active:not(:disabled) {
    transform: scale(0.98);
  }

  &:disabled {
    background-color: var(--color-bg-elevated);
    color: var(--color-text-ghost);
    cursor: not-allowed;
  }
}
</style>
