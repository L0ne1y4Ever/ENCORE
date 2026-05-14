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
  
  .meta {
    font-size: 14px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-2);
    font-family: monospace;
  }
  
  .amount {
    font-family: var(--font-family-sans);
    font-size: 48px;
    font-weight: 700;
    color: var(--color-accent);
  }
}

.mock-gateway {
  border: 1px solid var(--color-border);
  padding: var(--spacing-6);
  text-align: center;
  
  .gateway-title {
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-4);
  }
  
  p {
    font-size: 14px;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-6);
  }
}

.btn-pay {
  width: 100%;
  padding: 16px;
  background-color: var(--color-success);
  color: #fff;
  border: none;
  font-family: var(--font-family-sans);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 150ms ease;

  &:hover:not(:disabled) {
    opacity: 0.9;
  }
  
  &:disabled {
    background-color: var(--color-bg-elevated);
    color: var(--color-text-ghost);
    cursor: not-allowed;
  }
}
</style>
