<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrderDetail, simulatePayment } from '../../api/order'
import type { Order } from '../../mock/orders'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const orderId = route.query.id as string

const order = ref<Order | null>(null)
const loading = ref(true)
const paying = ref(false)

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
  const success = await simulatePayment(orderId)
  paying.value = false
  if (success) {
    // 支付成功，跳转到电子票页面
    router.replace(`/ticket/${orderId}`)
  } else {
    alert(t('payment.failed'))
  }
}
</script>

<template>
  <div class="payment-page" v-if="!loading && order">
    <div class="content">
      <h1 class="page-title">{{ t('payment.title') }}</h1>

      <div class="order-info">
        <div class="meta">{{ t('payment.orderId') }}: {{ order.id }}</div>
        <div class="amount">${{ order.totalAmount }}</div>
      </div>

      <div class="mock-gateway">
        <div class="gateway-title">{{ t('payment.gateway') }}</div>
        <p>{{ t('payment.description') }}</p>

        <button class="btn-pay" @click="handlePay" :disabled="paying">
          {{ paying ? t('common.processing') : t('payment.pay', { amount: order.totalAmount }) }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.payment-page {
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

.order-info {
  text-align: center;
  margin-bottom: var(--spacing-8);
  background-color: var(--color-bg-elevated);
  padding: var(--spacing-6);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  box-shadow: 0 10px 30px rgba(0,0,0,0.5);

  .meta {
    font-size: 14px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-2);
    font-family: var(--font-family-sans);
    text-transform: uppercase;
    letter-spacing: 0.1em;
  }

  .amount {
    font-family: var(--font-family-display);
    font-size: 56px;
    font-weight: 900;
    color: var(--color-accent);
    line-height: 1;
  }
}

.mock-gateway {
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  padding: var(--spacing-6);
  text-align: center;
  background-color: rgba(255, 255, 255, 0.02);

  .gateway-title {
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.15em;
    color: var(--color-text-primary);
    margin-bottom: var(--spacing-4);

    &::before, &::after {
      content: '—';
      margin: 0 var(--spacing-2);
      color: var(--color-text-secondary);
      font-weight: 300;
    }
  }

  p {
    font-family: var(--font-family-sans);
    font-size: 14px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-8);
  }
}

.btn-pay {
  width: 100%;
  padding: 18px;
  background-color: var(--color-success);
  color: #fff;
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
    background-color: #55c28a;
    box-shadow: 0 4px 20px rgba(76, 175, 125, 0.3);
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
