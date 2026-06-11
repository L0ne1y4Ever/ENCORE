<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, CreditCard, Tickets, Wallet } from '@element-plus/icons-vue'
import { cancelOrder, getOrderDetail, simulatePayment } from '../../api/order'
import type { Order } from '../../mock/orders'
import { useI18n } from 'vue-i18n'
import { formatMoney } from '../../utils/money'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const orderId = route.query.id as string

const order = ref<Order | null>(null)
const loading = ref(true)
const paying = ref(false)
const cancelling = ref(false)
const displayAmount = computed(() => formatMoney(order.value?.totalAmount, locale.value))
const canCancel = computed(() => order.value?.status === 'PENDING_PAYMENT')
const paymentDeadline = computed(() => order.value?.expiresAt ? new Date(order.value.expiresAt).toLocaleString() : '-')

onMounted(async () => {
  if (!orderId) {
    router.replace('/')
    return
  }
  order.value = (await getOrderDetail(orderId)) || null
  loading.value = false
})

const handlePay = async () => {
  paying.value = true
  try {
    const success = await simulatePayment(orderId)
    if (success) {
      router.replace(`/ticket/${orderId}`)
    } else {
      window.alert(t('payment.failed'))
    }
  } catch {
    window.alert(t('payment.failed'))
  } finally {
    paying.value = false
  }
}

const handleCancel = async () => {
  if (!order.value || cancelling.value) return
  cancelling.value = true
  try {
    const scheduleId = order.value.scheduleId
    await cancelOrder(order.value.id)
    router.replace(`/seat/${scheduleId}`)
  } catch {
    window.alert(t('payment.cancelFailed'))
  } finally {
    cancelling.value = false
  }
}
</script>

<template>
  <div v-if="loading" class="payment-page loading-page">
    {{ t('common.loading') }}
  </div>

  <div v-else-if="order" class="payment-page">
    <div class="payment-shell">
      <section class="payment-summary">
        <span class="eyebrow">{{ t('payment.title') }}</span>
        <h1>{{ t('payment.securePayment') }}</h1>
        <p>{{ t('payment.description') }}</p>
        <div class="order-chip">{{ t('payment.orderId') }} {{ order.id }}</div>
      </section>

      <section class="gateway-card">
        <ol class="stepper" aria-label="Checkout steps">
          <li class="done"><Tickets /> <span>{{ t('order.stepSelect') }}</span></li>
          <li class="done"><Check /> <span>{{ t('order.stepConfirm') }}</span></li>
          <li class="active"><Wallet /> <span>{{ t('order.stepPay') }}</span></li>
        </ol>

        <div class="amount-panel">
          <span>{{ t('order.totalAmount') }}</span>
          <strong>{{ displayAmount }}</strong>
          <small>{{ t('payment.gateway') }}</small>
          <small v-if="canCancel">{{ t('payment.deadline', { deadline: paymentDeadline }) }}</small>
        </div>

        <button class="btn-pay" type="button" @click="handlePay" :disabled="paying">
          <CreditCard />
          <span>{{ paying ? t('common.processing') : t('payment.pay', { amount: displayAmount }) }}</span>
        </button>
        <button v-if="canCancel" class="btn-cancel-order" type="button" @click="handleCancel" :disabled="paying || cancelling">
          {{ cancelling ? t('common.processing') : t('payment.cancelOrder') }}
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.payment-page {
  min-height: calc(100vh - 76px);
  display: grid;
  place-items: center;
  padding: var(--spacing-6) var(--spacing-4);
  background: transparent;
}

.loading-page {
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
}

.payment-shell {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 460px);
  gap: var(--spacing-5);
  align-items: center;
}

.payment-summary {
  .eyebrow {
    color: var(--color-success);
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
    max-width: 460px;
    margin-top: var(--spacing-3);
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 17px;
    line-height: 1.6;
  }
}

.order-chip {
  width: max-content;
  max-width: 100%;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 800;
  margin-top: var(--spacing-4);
  padding: 8px 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gateway-card {
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
      border-color: rgba(76, 175, 125, 0.45);
      color: var(--color-success);
    }

    &.active {
      background: rgba(76, 175, 125, 0.08);
    }
  }
}

.amount-panel {
  border: 1px solid rgba(76, 175, 125, 0.32);
  border-radius: var(--radius-md);
  background: rgba(76, 175, 125, 0.08);
  display: grid;
  gap: 8px;
  padding: var(--spacing-5);
  text-align: center;

  span,
  small {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 800;
  }

  strong {
    color: var(--color-success);
    font-family: var(--font-family-display);
    font-size: 60px;
    line-height: 1;
  }
}

.btn-pay {
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
    filter: none;
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.btn-cancel-order {
  width: 100%;
  min-height: 44px;
  margin-top: var(--spacing-2);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-family: var(--font-family-sans);
  font-size: 14px;
  font-weight: 800;

  &:hover:not(:disabled) {
    border-color: rgba(229, 9, 20, 0.55);
    color: #fff;
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

@media (max-width: 820px) {
  .payment-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .stepper {
    grid-template-columns: 1fr;
  }
}
</style>
