<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { cancelOrder, getMyOrders, refundOrder } from '../../api/order'
import type { Order, TicketItem } from '../../mock/orders'

const authStore = useAuthStore()
const router = useRouter()
const { t } = useI18n()

const isEditing = ref(false)
const editNicknameValue = ref(authStore.currentUser?.nickname || authStore.currentUser?.displayName || '')
const saveMessage = ref('')

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}

const toggleEdit = () => {
  if (isEditing.value) {
    authStore.updateNickname(editNicknameValue.value)
    saveMessage.value = t('profile.successSave')
    setTimeout(() => { saveMessage.value = '' }, 2000)
  }
  isEditing.value = !isEditing.value
}

const activeTab = ref<'info' | 'tickets' | 'orders' | 'reservations'>('info')
const orders = ref<Order[]>([])
const orderLoading = ref(false)
const orderError = ref('')
const operatingOrderId = ref('')

interface TicketView {
  id: string
  orderId: string
  showTitle: string
  date: string
  venue: string
  seat: string
  status: TicketItem['status']
}

const loadOrders = async () => {
  orderLoading.value = true
  orderError.value = ''
  try {
    orders.value = await getMyOrders()
  } catch (error) {
    orderError.value = error instanceof Error ? error.message : t('profile.ordersLoadFailed')
  } finally {
    orderLoading.value = false
  }
}

onMounted(loadOrders)

const tickets = computed<TicketView[]>(() => {
  return orders.value
    .filter(order => order.status === 'PAID')
    .flatMap(order => (order.tickets || [])
      .filter(ticket => ticket.status !== 'VOID')
      .map(ticket => ({
        id: ticket.id,
        orderId: order.id,
        showTitle: order.showTitle || order.scheduleId,
        date: order.startTime || order.createdAt,
        venue: order.theaterName || '-',
        seat: ticket.rowNo != null && ticket.colNo != null
          ? t('seat.info', { row: ticket.rowNo, col: ticket.colNo })
          : (ticket.seatLabel || ticket.seatId || t('ticket.unassigned')),
        status: ticket.status
      })))
})

const pendingReservations = computed(() => {
  return orders.value.filter(order => order.status === 'PENDING_PAYMENT')
})

const viewTicket = (orderId: string) => {
  router.push(`/ticket/${orderId}`)
}

const continuePayment = (orderId: string) => {
  router.push(`/payment?id=${orderId}`)
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '-'
  return new Date(value).toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatAmount = (value: number | string) => {
  return Number(value || 0).toFixed(2)
}

const orderStatusLabel = (status: Order['status']) => {
  return t(`profile.orderStatus.${status.toLowerCase()}`)
}

const canRefundOrder = (order: Order) => {
  if (order.status !== 'PAID') return false
  if ((order.tickets || []).some(ticket => ticket.status === 'CHECKED_IN')) return false
  if (!order.startTime) return false
  return Date.now() < new Date(order.startTime).getTime() - 2 * 60 * 60 * 1000
}

const replaceOrder = (updated: Order) => {
  const index = orders.value.findIndex(order => order.id === updated.id)
  if (index >= 0) {
    orders.value[index] = updated
  } else {
    orders.value.unshift(updated)
  }
}

const cancelReservation = async (order: Order) => {
  if (!window.confirm(t('profile.cancelReservationConfirm', { id: order.id }))) return
  operatingOrderId.value = order.id
  try {
    replaceOrder(await cancelOrder(order.id))
  } catch (error) {
    alert(error instanceof Error ? error.message : t('profile.cancelReservationFailed'))
  } finally {
    operatingOrderId.value = ''
  }
}

const requestRefund = async (order: Order) => {
  if (!window.confirm(t('profile.refundConfirm', { id: order.id }))) return
  operatingOrderId.value = order.id
  try {
    replaceOrder(await refundOrder(order.id))
  } catch (error) {
    alert(error instanceof Error ? error.message : t('profile.refundFailed'))
  } finally {
    operatingOrderId.value = ''
  }
}
</script>

<template>
  <div class="profile-page">
    <header class="profile-header">
      <div class="user-info">
        <h1 class="greeting">Hello, {{ authStore.currentUser?.nickname || authStore.currentUser?.displayName || authStore.currentUser?.username }}</h1>
        <div class="role-tag">{{ authStore.currentUser?.role.toUpperCase() }}</div>
      </div>
      <button class="logout-btn" @click="handleLogout">{{ t('common.logout') }}</button>
    </header>

    <nav class="profile-tabs">
      <button :class="{ active: activeTab === 'info' }" @click="activeTab = 'info'">{{ t('profile.personalInfo') }}</button>
      <button :class="{ active: activeTab === 'tickets' }" @click="activeTab = 'tickets'">{{ t('profile.myTickets') }}</button>
      <button :class="{ active: activeTab === 'orders' }" @click="activeTab = 'orders'">{{ t('profile.orderHistory') }}</button>
      <button :class="{ active: activeTab === 'reservations' }" @click="activeTab = 'reservations'">{{ t('reservation.myReservations') }}</button>
    </nav>

    <div class="tab-content">
      <transition name="tab-fade" mode="out-in">
        <div v-if="activeTab === 'info'" class="tab-pane" key="info">
          <section class="personal-info-section">
            <div class="section-header">
              <h2>{{ t('profile.personalInfo') }}</h2>
              <span class="save-msg" v-if="saveMessage">{{ saveMessage }}</span>
            </div>
            <div class="info-card">
              <div class="info-row">
                <label>{{ t('common.username') }}</label>
                <div class="value">{{ authStore.currentUser?.username }}</div>
              </div>
              <div class="info-row">
                <label>{{ t('common.nickname') }}</label>
                <div class="value" v-if="!isEditing">
                  {{ authStore.currentUser?.nickname || authStore.currentUser?.displayName || 'N/A' }}
                </div>
                <div class="value edit-mode" v-else>
                  <input type="text" v-model="editNicknameValue" />
                </div>
              </div>
              <div class="actions">
                <button class="btn-edit" @click="toggleEdit">
                  {{ isEditing ? t('common.save') : t('profile.editNickname') }}
                </button>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="activeTab === 'tickets'" class="tab-pane" key="tickets">
          <section class="tickets-section">
            <div class="section-header">
              <h2>{{ t('profile.myTickets') }}</h2>
              <button class="link-btn" :disabled="orderLoading" @click="loadOrders">{{ t('admin.refresh') }}</button>
            </div>
            <div class="loading-state" v-if="orderLoading">{{ t('common.loading') }}</div>
            <div class="error-state" v-else-if="orderError">{{ orderError }}</div>
            <div class="ticket-list" v-else-if="tickets.length > 0">
              <div class="t-card" v-for="tkt in tickets" :key="tkt.id" :class="{ used: tkt.status === 'CHECKED_IN' }">
                <div class="t-info">
                  <h3>{{ tkt.showTitle }}</h3>
                  <p>{{ formatDateTime(tkt.date) }} &bull; {{ tkt.venue }}</p>
                  <p class="t-seat">{{ t('ticket.seat') }}: {{ tkt.seat }}</p>
                </div>
                <div class="ticket-actions">
                  <div class="t-status">{{ tkt.status }}</div>
                  <button class="link-btn" @click="viewTicket(tkt.orderId)">{{ t('profile.viewTicket') }}</button>
                </div>
              </div>
            </div>
            <div class="empty-state" v-else>{{ t('profile.noTickets') }}</div>
          </section>
        </div>

        <div v-else-if="activeTab === 'orders'" class="tab-pane" key="orders">
          <section class="orders-section">
            <div class="section-header">
              <h2>{{ t('profile.orderHistory') }}</h2>
              <button class="link-btn" :disabled="orderLoading" @click="loadOrders">{{ t('admin.refresh') }}</button>
            </div>
            <div class="loading-state" v-if="orderLoading">{{ t('common.loading') }}</div>
            <div class="error-state" v-else-if="orderError">{{ orderError }}</div>
            <div class="order-list" v-else-if="orders.length > 0">
              <div class="o-card" v-for="ord in orders" :key="ord.id">
                <div class="o-header">
                  <span class="o-id">{{ ord.id }}</span>
                  <span class="status-pill">{{ orderStatusLabel(ord.status) }}</span>
                </div>
                <div class="o-details">
                  <div>
                    <strong>{{ ord.showTitle || ord.scheduleId }}</strong>
                    <p>{{ formatDateTime(ord.startTime || ord.createdAt) }} &bull; {{ ord.theaterName || '-' }}</p>
                    <p>{{ (ord.tickets || []).length }} {{ t('order.tickets') }}</p>
                  </div>
                  <span class="o-total">${{ formatAmount(ord.totalAmount) }}</span>
                </div>
                <div class="order-actions">
                  <button
                    v-if="ord.status === 'PENDING_PAYMENT'"
                    class="link-btn"
                    :disabled="operatingOrderId === ord.id"
                    @click="continuePayment(ord.id)"
                  >
                    {{ t('profile.continuePayment') }}
                  </button>
                  <button
                    v-if="ord.status === 'PENDING_PAYMENT'"
                    class="link-btn danger"
                    :disabled="operatingOrderId === ord.id"
                    @click="cancelReservation(ord)"
                  >
                    {{ t('profile.cancelReservation') }}
                  </button>
                  <button
                    v-if="ord.status === 'PAID'"
                    class="link-btn"
                    @click="viewTicket(ord.id)"
                  >
                    {{ t('profile.viewTicket') }}
                  </button>
                  <button
                    v-if="ord.status === 'PAID'"
                    class="link-btn danger"
                    :disabled="!canRefundOrder(ord) || operatingOrderId === ord.id"
                    @click="requestRefund(ord)"
                  >
                    {{ t('profile.requestRefund') }}
                  </button>
                </div>
              </div>
            </div>
            <div class="empty-state" v-else>{{ t('profile.noOrders') }}</div>
          </section>
        </div>

        <div v-else-if="activeTab === 'reservations'" class="tab-pane" key="reservations">
          <section class="reservations-section">
            <div class="section-header">
              <h2>{{ t('reservation.myReservations') }}</h2>
              <button class="link-btn" :disabled="orderLoading" @click="loadOrders">{{ t('admin.refresh') }}</button>
            </div>
            <div class="loading-state" v-if="orderLoading">{{ t('common.loading') }}</div>
            <div class="error-state" v-else-if="orderError">{{ orderError }}</div>
            <div class="reservation-list" v-else-if="pendingReservations.length > 0">
              <div class="r-card" v-for="res in pendingReservations" :key="res.id">
                <div class="r-info">
                  <h3>{{ res.showTitle || res.scheduleId }}</h3>
                  <p>{{ formatDateTime(res.startTime || res.createdAt) }} &bull; {{ res.theaterName || '-' }}</p>
                  <p>{{ (res.tickets || []).length }} {{ t('order.tickets') }} · {{ t('order.paymentDeadline') }} {{ formatDateTime(res.expiresAt) }}</p>
                </div>
                <div class="reservation-actions">
                  <span class="line-num">${{ formatAmount(res.totalAmount) }}</span>
                  <button class="link-btn" :disabled="operatingOrderId === res.id" @click="continuePayment(res.id)">
                    {{ t('profile.continuePayment') }}
                  </button>
                  <button class="link-btn danger" :disabled="operatingOrderId === res.id" @click="cancelReservation(res)">
                    {{ t('profile.cancelReservation') }}
                  </button>
                </div>
              </div>
            </div>
            <div class="empty-state" v-else>
              {{ t('reservation.noReservations') }}
            </div>
          </section>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped lang="scss">
.profile-page {
  max-width: 800px;
  margin: 0 auto;
  padding: var(--spacing-8) var(--spacing-6);
  width: 100%;
}

.profile-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-8);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: var(--spacing-6);

  .greeting {
    font-family: var(--font-family-display);
    font-size: 40px;
    margin-bottom: var(--spacing-2);
  }

  .role-tag {
    display: inline-block;
    padding: 2px 8px;
    border: 1px solid var(--color-accent);
    color: var(--color-accent);
    font-size: 12px;
    font-family: var(--font-family-sans);
    border-radius: var(--radius-full);
  }

  .logout-btn {
    background: transparent;
    border: 1px solid var(--color-border-strong);
    color: var(--color-text-secondary);
    padding: 8px 16px;
    font-family: var(--font-family-sans);
    cursor: pointer;
    transition: all 150ms ease;

    &:hover {
      border-color: var(--color-text-primary);
      color: var(--color-text-primary);
    }
  }
}

section {
  margin-bottom: var(--spacing-8);

  h2 {
    font-family: var(--font-family-sans);
    font-size: 20px;
    font-weight: 600;
    margin-bottom: var(--spacing-6);
  }
}

.profile-tabs {
  display: flex;
  gap: var(--spacing-6);
  margin-bottom: var(--spacing-8);
  border-bottom: 1px solid var(--color-border);

  button {
    background: transparent;
    border: none;
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 600;
    padding: var(--spacing-3) 0;
    cursor: pointer;
    position: relative;
    transition: color 150ms ease;

    &::after {
      content: '';
      position: absolute;
      bottom: -1px;
      left: 0;
      right: 0;
      height: 2px;
      background-color: var(--color-accent);
      transform: scaleX(0);
      transition: transform 150ms ease;
      transform-origin: left;
    }

    &:hover {
      color: var(--color-text-primary);
    }

    &.active {
      color: var(--color-text-primary);
      &::after {
        transform: scaleX(1);
      }
    }
  }
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-4);
  justify-content: space-between;

  .save-msg {
    font-size: 14px;
    color: var(--color-accent);
  }
}

.link-btn {
  background: transparent;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  color: var(--color-text-primary);
  cursor: pointer;
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 700;
  padding: 6px 12px;
  transition: border-color 150ms ease, color 150ms ease;

  &:hover:not(:disabled) {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }

  &:disabled {
    color: var(--color-text-ghost);
    cursor: not-allowed;
  }

  &.danger {
    color: #f0a86b;

    &:hover:not(:disabled) {
      border-color: #f0a86b;
      color: #ffd0ad;
    }
  }
}

.personal-info-section {
  .info-card {
    padding: var(--spacing-6);
    background-color: var(--color-bg-elevated);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
  }

  .info-row {
    display: flex;
    align-items: center;
    margin-bottom: var(--spacing-4);
    font-family: var(--font-family-sans);

    label {
      width: 120px;
      color: var(--color-text-secondary);
      font-size: 14px;
    }

    .value {
      font-size: 16px;
      flex: 1;

      &.edit-mode input {
        background: transparent;
        border: none;
        border-bottom: 1px solid var(--color-border-strong);
        color: var(--color-text-primary);
        font-family: var(--font-family-sans);
        font-size: 16px;
        padding: 4px 0;
        outline: none;

        &:focus {
          border-color: var(--color-accent);
        }
      }
    }
  }

  .actions {
    margin-top: var(--spacing-6);
    padding-top: var(--spacing-4);
    border-top: 1px solid var(--color-border);

    .btn-edit {
      background: transparent;
      border: 1px solid var(--color-text-primary);
      color: var(--color-text-primary);
      padding: 6px 16px;
      cursor: pointer;
      font-family: var(--font-family-sans);
      transition: all 150ms ease;

      &:hover {
        background: var(--color-text-primary);
        color: var(--color-bg-base);
      }
    }
  }
}

.reservations-section {
  .reservation-list {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-4);
  }

  .r-card {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--spacing-5);
    background-color: var(--color-bg-elevated);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);

    h3 {
      font-family: var(--font-family-display);
      font-size: 20px;
      margin-bottom: var(--spacing-2);
    }

    p {
      color: var(--color-text-secondary);
      font-family: var(--font-family-sans);
      font-size: 14px;
    }

    .line-num {
      font-family: monospace;
      font-size: 24px;
      color: var(--color-accent);
    }

    .reservation-actions {
      align-items: flex-end;
      display: flex;
      flex-direction: column;
      gap: var(--spacing-2);
    }
  }
}

.empty-state {
  color: var(--color-text-secondary);
  font-style: italic;
  padding: var(--spacing-4) 0;
}

.loading-state,
.error-state {
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  padding: var(--spacing-4) 0;
}

.error-state {
  color: #f0a86b;
}

/* Cards shared styling */
.ticket-list, .order-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
}

.t-card, .o-card {
  padding: var(--spacing-5);
  background-color: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.t-card {
  display: flex;
  justify-content: space-between;
  align-items: center;

  &.used {
    opacity: 0.5;
  }

  h3 {
    font-family: var(--font-family-display);
    font-size: 20px;
    margin-bottom: 4px;
  }

  p {
    color: var(--color-text-secondary);
    font-size: 14px;
  }

  .t-seat {
    font-family: monospace;
    margin-top: 4px;
    color: var(--color-text-primary);
  }

  .t-status {
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.1em;
    padding: 4px 8px;
    border: 1px solid currentColor;
    border-radius: var(--radius-sm);
  }

  .ticket-actions {
    align-items: flex-end;
    display: flex;
    flex-direction: column;
    gap: var(--spacing-2);
  }
}

.o-card {
  .o-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: var(--spacing-3);
    border-bottom: 1px solid var(--color-border);
    padding-bottom: var(--spacing-3);

    .o-id {
      font-family: monospace;
      font-weight: 700;
    }
    .o-date {
      color: var(--color-text-secondary);
      font-size: 14px;
    }
  }
  .o-details {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--spacing-4);
    font-size: 14px;

    p {
      color: var(--color-text-secondary);
      margin-top: 4px;
    }

    .o-total {
      font-size: 18px;
      font-weight: 700;
      color: var(--color-accent);
    }
  }

  .status-pill {
    border: 1px solid var(--color-border-strong);
    border-radius: var(--radius-sm);
    color: var(--color-text-secondary);
    font-size: 12px;
    font-weight: 700;
    padding: 4px 8px;
  }

  .order-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--spacing-2);
    margin-top: var(--spacing-3);
  }
}

/* Transitions */
.tab-fade-enter-active,
.tab-fade-leave-active {
  transition: opacity 150ms ease;
}

.tab-fade-enter-from,
.tab-fade-leave-to {
  opacity: 0;
}
</style>
