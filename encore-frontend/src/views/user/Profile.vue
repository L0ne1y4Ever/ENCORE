<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Calendar, Close, Money, Tickets, User, View, Wallet } from '@element-plus/icons-vue'
import { cancelOrder, getMyOrders, refundOrder } from '../../api/order'
import type { Order, TicketItem } from '../../mock/orders'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatMoney } from '../../utils/money'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

type ProfileTab = 'info' | 'tickets' | 'orders' | 'reservations'

const isEditing = ref(false)
const editNicknameValue = ref(authStore.currentUser?.nickname || authStore.currentUser?.displayName || '')
const saveMessage = ref('')
const profileSaving = ref(false)
const activeTab = ref<ProfileTab>('tickets')
const orders = ref<Order[]>([])
const orderLoading = ref(false)
const orderError = ref('')
const operatingOrderId = ref('')
const refundDialogVisible = ref(false)
const refundSubmitting = ref(false)
const refundReason = ref('')
const refundTargetOrder = ref<Order | null>(null)
const refundTargetTicketIds = ref<string[] | undefined>(undefined)
const refundReasonInput = ref<HTMLTextAreaElement | null>(null)

interface TicketView {
  id: string
  orderId: string
  showTitle: string
  date: string
  venue: string
  seat: string
  holder: string
  status: TicketItem['status']
  isGroupHeld: boolean
  canRefund: boolean
}

const displayName = computed(() => {
  const user = authStore.currentUser
  return user?.nickname || user?.displayName || user?.username || 'ENCORE'
})
const currentUserId = computed(() => authStore.currentUser?.id || '')

const syncTabFromRoute = () => {
  const tab = typeof route.query.tab === 'string' ? route.query.tab : ''
  if (['info', 'tickets', 'orders', 'reservations'].includes(tab)) {
    activeTab.value = tab as ProfileTab
  }
}

watch(() => route.query.tab, syncTabFromRoute, { immediate: true })

const selectTab = (tab: ProfileTab) => {
  activeTab.value = tab
  router.replace({ path: '/profile', query: { tab } })
}

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}

const toggleEdit = async () => {
  if (profileSaving.value) return

  if (isEditing.value) {
    const nextNickname = editNicknameValue.value.trim()
    if (!nextNickname) {
      ElMessage.warning(t('login.nicknameRequired'))
      return
    }

    profileSaving.value = true
    try {
      const user = await authStore.updateNickname(nextNickname)
      editNicknameValue.value = user.nickname || user.displayName || nextNickname
      ElMessage.success(t('profile.successSave'))
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('profile.saveFailed'))
      return
    } finally {
      profileSaving.value = false
    }
  }
  isEditing.value = !isEditing.value
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

watch(() => route.query.tab, (nextTab, previousTab) => {
  if (nextTab === 'tickets' && previousTab !== undefined && nextTab !== previousTab) {
    void loadOrders()
  }
})

watch(refundDialogVisible, async visible => {
  document.body.classList.toggle('refund-modal-open', visible)
  if (visible) {
    await nextTick()
    refundReasonInput.value?.focus()
  }
})

onUnmounted(() => {
  document.body.classList.remove('refund-modal-open')
})

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
        holder: ticket.holderDisplayName || '',
        status: ticket.status,
        isGroupHeld: order.userId !== currentUserId.value,
        canRefund: order.status === 'PAID' && ticket.status === 'UNUSED'
      })))
})

const isOwnedOrder = (order: Order) => order.userId === currentUserId.value

const pendingReservations = computed(() => orders.value.filter(order => order.status === 'PENDING_PAYMENT' && isOwnedOrder(order)))
const totalSpend = computed(() => {
  return orders.value
    .filter(order => order.status === 'PAID' || order.status === 'PENDING_REFUND')
    .reduce((sum, order) => sum + Number(order.totalAmount || 0), 0)
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

const formatAmount = (value: number | string) => formatMoney(value, locale.value)

const orderStatusLabel = (status: Order['status']) => t(`profile.orderStatus.${status.toLowerCase()}`)

const ticketStatusLabel = (status: TicketItem['status']) => {
  const key = status.toLowerCase().replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase())
  return t(`ticket.status.${key}`)
}

const statusTone = (status: Order['status'] | TicketItem['status']) => status.toLowerCase().replace(/_/g, '-')

const canRefundOrder = (order: Order) => {
  if (order.status !== 'PAID') return false
  return refundableTickets(order).length > 0
}

const refundDialogTicketCount = computed(() => {
  const order = refundTargetOrder.value
  if (!order) return 0
  return refundTargetTicketIds.value?.length || refundableTickets(order).length
})

const refundDialogMessage = computed(() => {
  const order = refundTargetOrder.value
  if (!order) return ''
  return t(refundTargetTicketIds.value?.length ? 'profile.ticketRefundConfirm' : 'profile.refundConfirm', {
    id: order.id,
    count: refundDialogTicketCount.value
  })
})

const refundReasonError = computed(() => {
  return refundReason.value.length > 500 ? t('profile.refundReasonTooLong') : ''
})

const orderById = (orderId: string) => orders.value.find(order => order.id === orderId) || null

const refundableTickets = (order: Order) => {
  return (order.tickets || []).filter(ticket => {
    if (ticket.status !== 'UNUSED') return false
    if (isOwnedOrder(order)) return true
    return ticket.holderUserId === currentUserId.value
  })
}

const requestTicketRefund = (ticket: TicketView) => {
  const order = orderById(ticket.orderId)
  if (!order) return
  void requestRefund(order, [ticket.id])
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
  try {
    await ElMessageBox.confirm(
      t('profile.cancelReservationConfirm', { id: order.id }),
      locale.value === 'zh' ? '取消预约' : 'Cancel Reservation',
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
        customClass: 'encore-dark-box'
      }
    )
    operatingOrderId.value = order.id
    replaceOrder(await cancelOrder(order.id))
    ElMessage.success(locale.value === 'zh' ? '预约已成功取消' : 'Reservation cancelled successfully')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : t('profile.cancelReservationFailed'))
    }
  } finally {
    operatingOrderId.value = ''
  }
}

const requestRefund = (order: Order, ticketIds?: string[]) => {
  const targets = ticketIds?.length ? ticketIds : (isOwnedOrder(order) ? undefined : refundableTickets(order).map(ticket => ticket.id))
  const targetCount = targets?.length || refundableTickets(order).length
  if (order.status !== 'PAID' || targetCount < 1) {
    ElMessage.warning(t('profile.refundFailed'))
    return
  }

  refundTargetOrder.value = order
  refundTargetTicketIds.value = targets
  refundReason.value = ''
  refundDialogVisible.value = true
}

const closeRefundDialog = () => {
  if (refundSubmitting.value) return
  refundDialogVisible.value = false
  refundTargetOrder.value = null
  refundTargetTicketIds.value = undefined
  refundReason.value = ''
}

const submitRefund = async () => {
  const order = refundTargetOrder.value
  if (!order || refundReasonError.value || refundSubmitting.value) return
  const targets = refundTargetTicketIds.value
  try {
    refundSubmitting.value = true
    operatingOrderId.value = order.id
    const updated = await refundOrder(order.id, refundReason.value.trim(), targets)
    replaceOrder(updated)
    refundDialogVisible.value = false
    refundTargetOrder.value = null
    refundTargetTicketIds.value = undefined
    refundReason.value = ''
    ElMessage.success(
      updated.status === 'REFUNDED'
        ? t('profile.refundCompleted')
        : t('profile.refundSubmitted')
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('profile.refundFailed'))
  } finally {
    refundSubmitting.value = false
    operatingOrderId.value = ''
  }
}
</script>

<template>
  <div class="profile-page">
    <header class="profile-hero">
      <div class="identity-block">
        <div class="avatar-mark">{{ displayName.slice(0, 1).toUpperCase() }}</div>
        <div class="identity">
          <span class="eyebrow">{{ t('profile.accountCenter') }}</span>
          <h1>{{ displayName }}</h1>
          <p>{{ authStore.currentUser?.username }} · {{ authStore.currentUser?.role.toUpperCase() }}</p>
        </div>
      </div>
      <div class="hero-actions">
        <button class="text-action refresh-btn" type="button" :disabled="orderLoading" @click="loadOrders">
          {{ t('profile.refresh') }}
        </button>
        <button class="text-action logout-btn" type="button" @click="handleLogout">
          {{ t('common.logout') }}
        </button>
      </div>
    </header>

    <section class="metric-strip">
      <button type="button" :class="{ active: activeTab === 'tickets' }" @click="selectTab('tickets')">
        <strong>{{ tickets.length }}</strong>
        <span class="metric-label">{{ t('profile.availableTickets') }}</span>
      </button>
      <button type="button" :class="{ active: activeTab === 'reservations' }" @click="selectTab('reservations')">
        <strong>{{ pendingReservations.length }}</strong>
        <span class="metric-label">{{ t('profile.pendingPayments') }}</span>
      </button>
      <button type="button" :class="{ active: activeTab === 'orders' }" @click="selectTab('orders')">
        <strong>{{ orders.length }}</strong>
        <span class="metric-label">{{ t('profile.totalOrders') }}</span>
      </button>
      <div class="spend-card">
        <strong>{{ formatAmount(totalSpend) }}</strong>
        <span class="metric-label">{{ t('profile.memberValue') }}</span>
      </div>
    </section>

    <nav class="profile-tabs">
      <button :class="{ active: activeTab === 'tickets' }" @click="selectTab('tickets')">{{ t('profile.myTickets') }}</button>
      <button :class="{ active: activeTab === 'orders' }" @click="selectTab('orders')">{{ t('profile.orderHistory') }}</button>
      <button :class="{ active: activeTab === 'reservations' }" @click="selectTab('reservations')">{{ t('reservation.myReservations') }}</button>
      <button :class="{ active: activeTab === 'info' }" @click="selectTab('info')">{{ t('profile.personalInfo') }}</button>
    </nav>

    <main class="tab-content">
      <div v-if="orderLoading" class="state-card">{{ t('common.loading') }}</div>
      <div v-else-if="orderError" class="state-card error">{{ orderError }}</div>

      <transition v-else name="tab-fade" mode="out-in">
        <section v-if="activeTab === 'tickets'" key="tickets" class="panel-section">
          <div class="section-header">
            <div>
              <span>{{ t('profile.ticketWallet') }}</span>
              <h2>{{ t('profile.myTickets') }}</h2>
            </div>
          </div>
          <div v-if="tickets.length > 0" class="ticket-list">
            <article v-for="tkt in tickets" :key="tkt.id" class="ticket-card" :class="{ used: tkt.status === 'CHECKED_IN' }">
              <div class="ticket-rail" aria-hidden="true">
                <Tickets />
              </div>
              <div class="ticket-main">
                <div class="ticket-title-line">
                  <h3>{{ tkt.showTitle }}</h3>
                  <span v-if="tkt.isGroupHeld" class="group-ticket-tag">{{ t('profile.groupTicket') }}</span>
                </div>
                <p>{{ formatDateTime(tkt.date) }} · {{ tkt.venue }} · {{ t('ticket.seat') }} {{ tkt.seat }}<template v-if="tkt.holder"> · {{ t('ticket.holder') }} {{ tkt.holder }}</template></p>
                <small v-if="tkt.isGroupHeld" class="ticket-scope-note">{{ t('profile.ownTicketOnly') }}</small>
              </div>
              <div class="row-side">
                <span class="card-kicker" :class="statusTone(tkt.status)">{{ ticketStatusLabel(tkt.status) }}</span>
                <button
                  v-if="tkt.status !== 'PENDING_REFUND'"
                  class="icon-action primary-link"
                  type="button"
                  :aria-label="t('profile.viewTicket')"
                  @click="viewTicket(tkt.orderId)"
                >
                  <View />
                  <span>{{ t('profile.viewTicket') }}</span>
                </button>
                <button
                  v-if="tkt.canRefund"
                  class="icon-action danger-link"
                  :disabled="operatingOrderId === tkt.orderId"
                  type="button"
                  :aria-label="t('profile.requestRefund')"
                  @click="requestTicketRefund(tkt)"
                >
                  <Close />
                  <span>{{ t('profile.requestRefund') }}</span>
                </button>
              </div>
            </article>
          </div>
          <div v-else class="state-card ticket-empty">
            <span>{{ t('profile.noTickets') }}</span>
            <small>{{ t('profile.ticketRefreshHint') }}</small>
            <button class="icon-action primary-link" type="button" :disabled="orderLoading" @click="loadOrders">
              {{ t('profile.refresh') }}
            </button>
          </div>
        </section>

        <section v-else-if="activeTab === 'orders'" key="orders" class="panel-section">
          <div class="section-header">
            <div>
              <span>{{ t('profile.orderSummary') }}</span>
              <h2>{{ t('profile.orderHistory') }}</h2>
            </div>
          </div>
          <div v-if="orders.length > 0" class="order-list">
            <article v-for="ord in orders" :key="ord.id" class="order-card">
              <div class="ticket-rail" aria-hidden="true">
                <Wallet />
              </div>
              <div class="order-body">
                <div>
                  <h3>{{ ord.showTitle || ord.scheduleId }}</h3>
                  <p>{{ ord.id }} · {{ formatDateTime(ord.startTime || ord.createdAt) }} · {{ ord.theaterName || '-' }} · {{ (ord.tickets || []).length }} {{ t('order.tickets') }}</p>
                  <p v-if="ord.refundRequest" class="refund-meta">
                    {{ t('profile.refundRequestStatus') }}:
                    {{ t(`profile.refundReviewStatus.${ord.refundRequest.status.toLowerCase()}`) }}
                    <template v-if="ord.refundRequest.requestedAt">
                      · {{ t('profile.refundRequestedAt') }} {{ formatDateTime(ord.refundRequest.requestedAt) }}
                    </template>
                    <template v-if="ord.refundRequest.reason">
                      · {{ t('profile.refundReason') }}: {{ ord.refundRequest.reason }}
                    </template>
                    <template v-if="ord.refundRequest.reviewNote">
                      · {{ t('profile.refundReviewNote') }}: {{ ord.refundRequest.reviewNote }}
                    </template>
                  </p>
                </div>
              </div>
              <div class="row-side">
                <strong class="card-kicker" :class="statusTone(ord.status)">{{ orderStatusLabel(ord.status) }}</strong>
                <strong class="amount">{{ formatAmount(ord.totalAmount) }}</strong>
                <button
                  v-if="ord.status === 'PENDING_PAYMENT' && isOwnedOrder(ord)"
                  class="icon-action primary-link"
                  :disabled="operatingOrderId === ord.id"
                  type="button"
                  :aria-label="t('profile.continuePayment')"
                  @click="continuePayment(ord.id)"
                >
                  <Money />
                  <span>{{ t('profile.continuePayment') }}</span>
                </button>
                <button
                  v-if="ord.status === 'PENDING_PAYMENT' && isOwnedOrder(ord)"
                  class="icon-action danger-link"
                  :disabled="operatingOrderId === ord.id"
                  type="button"
                  :aria-label="t('profile.cancelReservation')"
                  @click="cancelReservation(ord)"
                >
                  <Close />
                  <span>{{ t('profile.cancelReservation') }}</span>
                </button>
                <button v-if="ord.status === 'PAID'" class="icon-action primary-link" type="button" :aria-label="t('profile.viewTicket')" @click="viewTicket(ord.id)">
                  <View />
                  <span>{{ t('profile.viewTicket') }}</span>
                </button>
                <button
                  v-if="ord.status === 'PAID' && canRefundOrder(ord)"
                  class="icon-action danger-link"
                  :disabled="!canRefundOrder(ord) || operatingOrderId === ord.id"
                  type="button"
                  :aria-label="t('profile.requestRefund')"
                  @click="requestRefund(ord)"
                >
                  <Close />
                  <span>{{ t('profile.requestRefund') }}</span>
                </button>
              </div>
            </article>
          </div>
          <div v-else class="state-card">{{ t('profile.noOrders') }}</div>
        </section>

        <section v-else-if="activeTab === 'reservations'" key="reservations" class="panel-section">
          <div class="section-header">
            <div>
              <span>{{ t('reservation.title') }}</span>
              <h2>{{ t('reservation.myReservations') }}</h2>
            </div>
          </div>
          <div v-if="pendingReservations.length > 0" class="order-list">
            <article v-for="res in pendingReservations" :key="res.id" class="order-card">
              <div class="ticket-rail" aria-hidden="true">
                <Calendar />
              </div>
              <div class="order-body">
                <div>
                  <h3>{{ res.showTitle || res.scheduleId }}</h3>
                  <p>{{ res.id }} · {{ formatDateTime(res.startTime || res.createdAt) }} · {{ res.theaterName || '-' }} · {{ t('order.paymentDeadline') }} {{ formatDateTime(res.expiresAt) }}</p>
                </div>
              </div>
              <div class="row-side">
                <strong class="card-kicker" :class="statusTone(res.status)">{{ orderStatusLabel(res.status) }}</strong>
                <strong class="amount">{{ formatAmount(res.totalAmount) }}</strong>
                <button class="icon-action primary-link" type="button" :disabled="operatingOrderId === res.id" :aria-label="t('profile.continuePayment')" @click="continuePayment(res.id)">
                  <Money />
                  <span>{{ t('profile.continuePayment') }}</span>
                </button>
                <button class="icon-action danger-link" type="button" :disabled="operatingOrderId === res.id" :aria-label="t('profile.cancelReservation')" @click="cancelReservation(res)">
                  <Close />
                  <span>{{ t('profile.cancelReservation') }}</span>
                </button>
              </div>
            </article>
          </div>
          <div v-else class="state-card">{{ t('reservation.noReservations') }}</div>
        </section>

        <section v-else key="info" class="panel-section">
          <div class="section-header">
            <div>
              <span>{{ t('profile.manageAccount') }}</span>
              <h2>{{ t('profile.personalInfo') }}</h2>
            </div>
            <span v-if="saveMessage" class="save-msg">{{ saveMessage }}</span>
          </div>
          <div class="info-card">
            <div class="info-row">
              <span>{{ t('common.username') }}</span>
              <strong>{{ authStore.currentUser?.username }}</strong>
            </div>
            <div class="info-row">
              <span>{{ t('common.nickname') }}</span>
              <strong v-if="!isEditing">{{ authStore.currentUser?.nickname || authStore.currentUser?.displayName || 'N/A' }}</strong>
              <input v-else v-model="editNicknameValue" type="text" />
            </div>
            <button class="icon-action primary-link" type="button" :disabled="profileSaving" :aria-label="isEditing ? t('common.save') : t('profile.editNickname')" @click="toggleEdit">
              <User />
              <span>{{ profileSaving ? t('common.processing') : (isEditing ? t('common.save') : t('profile.editNickname')) }}</span>
            </button>
          </div>
        </section>
      </transition>
    </main>

    <transition name="refund-pop" appear>
      <div
        v-if="refundDialogVisible"
        class="refund-modal-overlay"
        role="presentation"
        tabindex="-1"
        @click.self="closeRefundDialog"
        @keydown.esc.stop.prevent="closeRefundDialog"
      >
        <section class="refund-modal" role="dialog" aria-modal="true" :aria-label="t('profile.requestRefund')">
          <header class="refund-modal-header">
            <div class="refund-modal-title">
              <span class="refund-modal-mark" aria-hidden="true">
                <Tickets />
              </span>
              <div>
                <h2>{{ t('profile.requestRefund') }}</h2>
              </div>
            </div>
            <button class="refund-modal-close" type="button" :disabled="refundSubmitting" :aria-label="t('common.cancel')" @click="closeRefundDialog">
              <Close />
            </button>
          </header>
          <div class="refund-dialog-body">
            <div class="refund-dialog-message">
              <p>{{ refundDialogMessage }}</p>
            </div>
            <label class="refund-reason-field">
              <span class="refund-reason-label">{{ t('profile.refundReason') }}</span>
              <textarea
                ref="refundReasonInput"
                v-model="refundReason"
                :maxlength="500"
                :placeholder="t('profile.refundReasonPlaceholder')"
                :disabled="refundSubmitting"
              />
            </label>
            <div class="refund-reason-meta">
              <p v-if="refundReasonError" class="refund-reason-error">{{ refundReasonError }}</p>
              <span>{{ refundReason.length }} / 500</span>
            </div>
          </div>
          <footer class="refund-modal-footer">
            <button class="refund-dialog-btn secondary" type="button" :disabled="refundSubmitting" @click="closeRefundDialog">
              {{ t('common.cancel') }}
            </button>
            <button
              class="refund-dialog-btn danger"
              type="button"
              :disabled="refundSubmitting || !!refundReasonError"
              @click="submitRefund"
            >
              {{ refundSubmitting ? t('common.processing') : t('common.confirm') }}
            </button>
          </footer>
        </section>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.profile-page {
  --profile-red: #e50914;
  --profile-red-soft: #ff5a66;
  width: min(860px, calc(100% - 40px));
  margin: 0 auto;
  padding: var(--spacing-7) 0;
  position: relative;
}

.profile-hero {
  min-height: 190px;
  border: 0;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-4);
  padding: clamp(24px, 4vw, 38px);
  position: relative;
  overflow: hidden;

  &::after {
    content: none;
  }

  > * {
    position: relative;
    z-index: 1;
  }
}

.identity-block {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
}

.avatar-mark {
  width: clamp(62px, 8vw, 86px);
  aspect-ratio: 1;
  border-radius: 14px;
  background: #121216;
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fff;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  font-family: var(--font-family-sans);
  font-size: clamp(32px, 4vw, 44px);
  font-weight: 900;
  box-shadow: none;
}

.identity {
  min-width: 0;

  .eyebrow {
    color: rgba(255, 255, 255, 0.7);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 900;
    letter-spacing: 0;
    text-transform: uppercase;
  }

  h1 {
    margin-top: var(--spacing-2);
    max-width: 640px;
    overflow-wrap: anywhere;
    color: #fff;
    font-size: clamp(38px, 6vw, 68px);
    line-height: 0.98;
    text-shadow: 0 18px 48px rgba(0, 0, 0, 0.54);
  }

  p {
    margin-top: var(--spacing-2);
    color: rgba(240, 237, 232, 0.68);
    font-family: var(--font-family-sans);
  }
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.refresh-btn,
.logout-btn,
.primary-link,
.danger-link {
  min-height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 9px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 850;
  letter-spacing: 0;
  padding: 0 14px;
  transition: border-color 180ms ease, color 180ms ease, background-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover:not(:disabled) {
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.42;
    cursor: not-allowed;
  }
}

.refresh-btn,
.primary-link {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.92);

  &:hover:not(:disabled) {
    border-color: rgba(255, 255, 255, 0.2);
    background: rgba(255, 255, 255, 0.12);
    box-shadow: none;
  }
}

.logout-btn,
.danger-link {
  border-color: rgba(255, 101, 112, 0.22);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.012)),
    rgba(229, 9, 20, 0.035);
  color: rgba(255, 190, 194, 0.86);

  &:hover:not(:disabled) {
    border-color: rgba(255, 101, 112, 0.34);
    background: rgba(229, 9, 20, 0.1);
    color: #ffd5d8;
    box-shadow: none;
  }
}

.icon-action {
  min-width: 42px;

  svg {
    width: 17px;
    height: 17px;
    flex: 0 0 auto;
  }
}

.panel-section {
  display: grid;
  gap: var(--spacing-4);
}

.section-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-4);

  span {
    color: rgba(255, 255, 255, 0.6);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 900;
    letter-spacing: 0;
    text-transform: uppercase;
  }

  h2 {
    margin-top: 4px;
    color: #fff;
    font-size: 34px;
    line-height: 1.1;
  }
}

.save-msg {
  letter-spacing: 0;
  text-transform: none;
}

.ticket-list,
.order-list {
  display: grid;
  gap: var(--spacing-3);
}

.ticket-card,
.order-card,
.info-card,
.state-card {
  padding: var(--spacing-4);
  border-color: rgba(255, 255, 255, 0.075);
}

.ticket-card {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--spacing-4);
  position: relative;
  overflow: hidden;
  min-height: 160px;
  background: transparent;
  box-shadow: none;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-left: 1px solid rgba(255, 255, 255, 0.08);
    box-shadow: none;
    opacity: 0.9;
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    top: 18px;
    bottom: 18px;
    left: 82px;
    width: 1px;
    background-image: linear-gradient(to bottom, rgba(255, 255, 255, 0.22) 45%, transparent 45%);
    background-size: 1px 10px;
    opacity: 0.42;
    pointer-events: none;
  }

  &.used {
    opacity: 0.62;
  }

  > * {
    position: relative;
    z-index: 1;
  }

  h3 {
    margin-top: 8px;
    color: #fff;
    font-size: clamp(21px, 2.8vw, 29px);
    line-height: 1.12;
  }

  p {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 14px;
    line-height: 1.45;
    margin-top: 5px;
  }
}

.ticket-main {
  min-width: 0;
}

.ticket-rail {
  min-width: 0;
  min-height: 112px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.055);
  color: rgba(255, 255, 255, 0.84);
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 12px 8px;
  position: relative;
  z-index: 1;

  svg {
    width: 24px;
    height: 24px;
    filter: none;
  }

  span {
    max-width: 100%;
    overflow: hidden;
    font-family: var(--font-family-sans);
    font-size: 10px;
    font-weight: 900;
    letter-spacing: 0;
    text-overflow: ellipsis;
  }
}

.card-kicker {
  width: max-content;
  max-width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.085);
  border-radius: 8px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.018)),
    rgba(255, 255, 255, 0.045);
  color: rgba(255, 255, 255, 0.76);
  display: inline-flex;
  align-items: center;
  font-family: var(--font-family-sans);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0;
  padding: 6px 9px;
  text-transform: uppercase;
}

.card-kicker.unused {
  color: rgba(255, 255, 255, 0.76);
  border-color: rgba(255, 255, 255, 0.14);
}

.card-kicker.checked-in,
.card-kicker.refunded {
  color: rgba(255, 255, 255, 0.56);
}

.card-kicker.pending-refund {
  color: #f4c76a;
  border-color: rgba(244, 199, 106, 0.28);
  background: rgba(244, 199, 106, 0.055);
}

.order-body,
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
}

.order-body {
  align-items: flex-start;
  padding: var(--spacing-3) 0;

  h3 {
    color: #fff;
    font-size: clamp(21px, 2.8vw, 28px);
    line-height: 1.16;
  }

  p {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 14px;
    margin-top: 5px;
  }

  .refund-meta {
    max-width: 720px;
    color: rgba(255, 255, 255, 0.62);
    font-size: 13px;
    line-height: 1.55;
  }

  .amount {
    color: #fff;
    flex: 0 0 auto;
    font-family: var(--font-family-sans);
    font-size: 30px;
    font-weight: 900;
    text-shadow: none;
  }
}

.info-card {
  display: grid;
  gap: var(--spacing-4);
}

.info-row {
  border-bottom: 1px solid rgba(240, 237, 232, 0.08);
  padding-bottom: var(--spacing-3);

  span {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 800;
  }

  strong {
    color: #fff;
    font-family: var(--font-family-sans);
  }

  input {
    min-height: 42px;
    border: 0;
    border-radius: 9px;
    background: rgba(8, 8, 8, 0.48);
    color: var(--color-text-primary);
    font-family: var(--font-family-sans);
    font-size: 16px;
    outline: none;
    padding: 0 12px;

    &:focus {
      border: 1px solid rgba(229, 9, 20, 0.36);
      box-shadow: none;
    }
  }
}

.state-card {
  min-height: 168px;
  color: var(--color-text-secondary);
  display: grid;
  place-items: center;
  font-family: var(--font-family-sans);
  line-height: 1.6;
  text-align: center;

  &.error {
    color: #ffb1a8;
  }
}

.tab-fade-enter-active,
.tab-fade-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.tab-fade-enter-from,
.tab-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 920px) {
  .profile-page {
    width: min(100% - 24px, 1120px);
    padding: var(--spacing-5) 0;
  }

  .profile-hero,
  .identity-block,
  .order-body {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-actions {
    justify-content: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .refresh-btn,
  .logout-btn,
  .primary-link,
  .danger-link,
  .metric-strip button,
  .tab-fade-enter-active,
  .tab-fade-leave-active {
    transition: none;
  }
}

.profile-hero {
  padding: 0 0 24px;
  align-items: center;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  overflow: visible;

  &::after {
    content: none;
  }
}

.identity-block {
  align-items: center;
  gap: 20px;
}

.avatar-mark {
  width: 68px;
  height: 68px;
  border-radius: 10px;
  font-size: 32px;
  background: #111114;
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    transform: scale(1.05);
  }
}

.identity {
  .eyebrow {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.45);
    text-transform: uppercase;
    letter-spacing: 1.2px;
    font-weight: 600;
  }

  h1 {
    margin-top: 4px;
    font-size: 36px;
    font-weight: 700;
    line-height: 1.1;
    color: #fff;
    text-shadow: none;
  }

  p {
    margin-top: 6px;
    font-size: 15px;
    color: rgba(255, 255, 255, 0.5);
  }
}

.hero-actions {
  align-self: center;
  gap: 16px;
}

.text-action.refresh-btn,
.text-action.logout-btn {
  min-height: auto;
  border: none;
  border-radius: 0;
  background: none;
  box-shadow: none;
  color: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  font-family: var(--font-family-sans);
  font-size: 14px;
  font-weight: 700;
  padding: 0;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover:not(:disabled) {
    background: none;
    color: #fff;
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: scale(0.95);
  }
}

.metric-strip {
  margin: 26px 0 24px;
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 28px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);

  button,
  .spend-card {
    min-height: auto;
    border-radius: 0;
    background: transparent;
    border: none;
    box-shadow: none;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-start;
    padding: 0;
    text-align: left;
    transition: color 0.2s ease, opacity 0.2s ease;
    box-sizing: border-box;
  }

  button {
    cursor: pointer;

    &:hover {
      background: transparent;
      color: #fff;
    }

    &:active {
      transform: none;
    }

    &.active {
      background: transparent;
      color: #fff;
    }
  }

  .spend-card {
    cursor: default;
    background: transparent;
    border-color: transparent;
  }

  strong {
    color: #fff;
    font-size: 26px;
    font-weight: 600;
    line-height: 1.2;
  }

  .metric-label {
    color: rgba(255, 255, 255, 0.48);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.8px;
    text-transform: uppercase;
    margin-top: 4px;
  }
}

.profile-tabs {
  margin-bottom: 28px;
  border-radius: 0;
  background: transparent;
  display: flex;
  gap: 28px;
  padding: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);

  button {
    min-height: auto;
    border-radius: 0;
    color: rgba(255, 255, 255, 0.45);
    font-size: 16px;
    font-weight: 700;
    padding: 0 0 12px;
    position: relative;
    background: transparent;
    border: none;
    cursor: pointer;
    transition: color 0.4s cubic-bezier(0.16, 1, 0.3, 1);

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 2px;
      background: #e50914;
      transform: scaleX(0);
      transform-origin: left;
      transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    &.active,
    &:hover {
      background: transparent;
      color: #fff;
    }

    &.active::after {
      transform: scaleX(1);
    }

    &:active {
      transform: scale(0.96);
    }
  }
}

.ticket-list,
.order-list {
  gap: 0;
}

.ticket-card,
.order-card {
  min-height: 72px;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 16px 12px;
  margin: 0 -12px;
  overflow: visible;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);

  &::before,
  &::after {
    content: none;
  }

  &:hover {
    background: rgba(255, 255, 255, 0.025);
  }
}

.ticket-rail {
  width: 36px;
  min-height: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
  display: grid;
  place-items: center;
  padding: 0;

  svg {
    width: 18px;
    height: 18px;
    filter: none;
  }
}

.ticket-card h3,
.order-body h3 {
  margin: 0;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
}

.ticket-card p,
.order-body p {
  margin-top: 4px;
  color: rgba(255, 255, 255, 0.56);
  font-size: 14px;
  line-height: 1.45;
}

.ticket-title-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.group-ticket-tag {
  border: 1px solid rgba(229, 9, 20, 0.28);
  border-radius: 4px;
  background: rgba(229, 9, 20, 0.08);
  color: rgba(255, 190, 196, 0.92);
  font-family: var(--font-family-sans);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  padding: 5px 7px;
}

.ticket-scope-note {
  display: inline-flex;
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.45);
  font-family: var(--font-family-sans);
  font-size: 12px;
  line-height: 1.35;
}

.order-body {
  padding: 0;
}

.row-side {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
}

.card-kicker {
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  padding: 5px 9px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.02);
  color: rgba(255, 255, 255, 0.7);
  display: inline-flex;
  align-items: center;
}

.card-kicker.unused {
  color: rgba(255, 255, 255, 0.72);
  border-color: rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.025);
  box-shadow: none;
}

.card-kicker.pending-payment {
  color: #ffb53d;
  border-color: rgba(255, 181, 61, 0.25);
  background: rgba(255, 181, 61, 0.04);
}

.card-kicker.pending-refund {
  color: #f4c76a;
  border-color: rgba(244, 199, 106, 0.28);
  background: rgba(244, 199, 106, 0.055);
}

.card-kicker.cancelled,
.card-kicker.expired {
  color: #ff6b7a;
  border-color: rgba(255, 107, 122, 0.25);
  background: rgba(255, 107, 122, 0.04);
}

.card-kicker.refunded,
.card-kicker.checked-in {
  color: rgba(255, 255, 255, 0.4);
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.02);
}

.amount {
  color: #fff;
  font-family: var(--font-family-sans);
  font-size: 15px;
  font-weight: 700;
}

.primary-link,
.danger-link {
  min-height: 38px;
  border-radius: 4px;
  padding: 0 15px;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);

  svg {
    width: 16px;
    height: 16px;
  }

  &:active {
    transform: scale(0.96);
  }
}

.primary-link {
  border: none;
  background: #e50914;
  color: #fff;

  &:hover:not(:disabled) {
    background: #f6121d;
    box-shadow: none;
  }
}

.danger-link {
  border: none;
  min-height: auto;
  background: none;
  color: rgba(255, 101, 112, 0.85);
  padding: 0 2px;
  text-decoration: none;
  border-bottom: 1px dashed rgba(255, 101, 112, 0.3);
  border-radius: 0;

  &:hover:not(:disabled) {
    color: #ffcbd0;
    border-bottom-color: #ffcbd0;
    background: none;
    box-shadow: none;
  }
}

.info-card {
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  box-shadow: none;
  padding: 24px;
  display: grid;
  gap: 20px;

  button.primary-link {
    justify-self: start;
    margin-top: 8px;
    border-radius: 4px;
  }
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding-bottom: 14px;
  gap: 16px;

  span {
    color: rgba(255, 255, 255, 0.45);
    font-family: var(--font-family-sans);
    font-size: 15px;
    font-weight: 500;
  }

  strong {
    color: #fff;
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 600;
  }

  input {
    min-height: 36px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 6px;
    background: rgba(0, 0, 0, 0.3);
    color: #fff;
    font-family: var(--font-family-sans);
    font-size: 15px;
    outline: none;
    padding: 0 12px;
    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);

    &:focus {
      border-color: #e50914;
      background: rgba(0, 0, 0, 0.5);
      box-shadow: none;
    }
  }
}

.state-card {
  min-height: 120px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.015);
  box-shadow: none;
  color: rgba(255, 255, 255, 0.45);
  font-size: 15px;
  display: grid;
  place-items: center;
  padding: 24px;
  box-sizing: border-box;

  &.error {
    color: #ff8b94;
    border-color: rgba(255, 107, 122, 0.15);
    background: rgba(255, 107, 122, 0.02);
  }
}

.ticket-empty {
  gap: 10px;

  span {
    color: rgba(255, 255, 255, 0.62);
    font-weight: 600;
  }

  small {
    max-width: 420px;
    color: rgba(255, 255, 255, 0.42);
    font-family: var(--font-family-sans);
    font-size: 13px;
    line-height: 1.5;
  }

  .primary-link {
    margin-top: 4px;
  }
}

/* Media Query Responsive Adjustments */
@media (max-width: 768px) {
  .profile-page {
    width: min(100% - 24px, 860px);
    padding: 28px 0 40px;
  }

  .profile-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .hero-actions {
    align-self: stretch;
    justify-content: flex-start;
  }

  .metric-strip {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .profile-tabs {
    gap: 20px;
  }
}

@media (max-width: 560px) {
  .avatar-mark {
    width: 56px;
    height: 56px;
    font-size: 28px;
  }

  .profile-tabs {
    gap: 16px;
    overflow-x: auto;
    padding-bottom: 4px;

    button {
      font-size: 14px;
      padding-bottom: 8px;
      white-space: nowrap;
    }
  }

  .metric-strip {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .ticket-card,
  .order-card {
    grid-template-columns: 36px minmax(0, 1fr);
    gap: 12px;
  }

  .row-side {
    grid-column: 2;
    justify-content: flex-start;
    margin-top: 4px;
  }
}

.refund-modal-overlay {
  position: fixed !important;
  inset: 0;
  z-index: 2100 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  box-sizing: border-box;
  padding: 20px;
  background: rgba(0, 0, 0, 0.72);
  backdrop-filter: blur(10px);
}

:global(body.refund-modal-open) {
  overflow: hidden;
}

.refund-modal {
  position: fixed !important;
  top: 50% !important;
  left: 50% !important;
  transform: translate(-50%, -50%) !important;
  width: min(500px, calc(100vw - 32px)) !important;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 40px);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.052), rgba(255, 255, 255, 0.012)),
    #111113;
  box-shadow: 0 28px 70px rgba(0, 0, 0, 0.78), 0 0 0 1px rgba(229, 9, 20, 0.055);
  color: rgba(255, 255, 255, 0.92);

  &::before {
    content: "";
    position: absolute;
    inset: 0 0 auto;
    height: 2px;
    background: linear-gradient(90deg, rgba(229, 9, 20, 0.76), rgba(229, 9, 20, 0.18), transparent 72%);
    pointer-events: none;
  }
}

.refund-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px 12px;
}

.refund-modal-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
}

.refund-modal-mark {
  width: 40px;
  height: 40px;
  border: 1px solid rgba(229, 9, 20, 0.22);
  border-radius: 7px;
  background: rgba(229, 9, 20, 0.09);
  color: rgba(255, 132, 140, 0.92);
  display: grid;
  place-items: center;
  flex: 0 0 auto;

  svg {
    width: 19px;
    height: 19px;
  }
}

.refund-modal-title h2 {
  margin: 0;
  color: rgba(255, 255, 255, 0.96);
  font-size: 24px;
  font-weight: 850;
  line-height: 1.25;
  letter-spacing: 0;
}

.refund-modal-close {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: rgba(255, 255, 255, 0.58);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background-color 180ms ease, color 180ms ease;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.07);
    color: rgba(255, 255, 255, 0.88);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.52;
  }
}

.refund-dialog-body {
  display: grid;
  gap: 16px;
  padding: 8px 28px 4px;
}

.refund-dialog-message {
  border: 1px solid rgba(255, 255, 255, 0.075);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.032);
  padding: 12px 14px;

  p {
    margin: 0;
    color: rgba(255, 255, 255, 0.7);
    font-size: 14px;
    font-weight: 620;
    line-height: 1.7;
  }
}

.refund-reason-field {
  display: grid;
  gap: 9px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
  font-weight: 760;
}

.refund-reason-label {
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.2;
}

.refund-reason-field textarea {
  width: 100%;
  min-height: 118px;
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.13);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.26);
  box-shadow: none;
  color: rgba(255, 255, 255, 0.9);
  font: inherit;
  font-weight: 500;
  line-height: 1.55;
  outline: none;
  padding: 11px 12px;
  resize: none;
  transition: border-color 180ms ease, background-color 180ms ease, box-shadow 180ms ease;

  &::placeholder {
    color: rgba(255, 255, 255, 0.36);
  }

  &:focus {
    border-color: rgba(229, 9, 20, 0.5);
    background: rgba(0, 0, 0, 0.34);
    box-shadow: 0 0 0 3px rgba(229, 9, 20, 0.1);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.68;
  }
}

.refund-reason-meta {
  min-height: 18px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: rgba(255, 255, 255, 0.38);
  font-size: 12px;
}

.refund-reason-error {
  margin: 0;
  color: rgba(255, 120, 128, 0.95);
  font-size: 12px;
}

.refund-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.065);
  margin-top: 18px;
  padding: 18px 28px 24px;
  background: rgba(255, 255, 255, 0.018);
}

.refund-dialog-btn {
  min-width: 86px;
  min-height: 38px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  transition: border-color 180ms ease, background-color 180ms ease, color 180ms ease, transform 180ms ease, opacity 180ms ease;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.58;
  }

  &:not(:disabled):active {
    transform: translateY(1px);
  }
}

.refund-dialog-btn.secondary {
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.045);
  color: rgba(255, 255, 255, 0.82);

  &:hover:not(:disabled) {
    border-color: rgba(255, 255, 255, 0.22);
    background: rgba(255, 255, 255, 0.095);
  }
}

.refund-dialog-btn.danger {
  border: 1px solid rgba(229, 9, 20, 0.48);
  background: #9f1019;
  color: rgba(255, 255, 255, 0.96);

  &:hover:not(:disabled) {
    border-color: rgba(246, 18, 29, 0.58);
    background: #b41420;
    box-shadow: 0 10px 22px rgba(229, 9, 20, 0.2);
  }
}

.refund-pop-enter-active,
.refund-pop-leave-active {
  transition: opacity 180ms ease;
}

.refund-pop-enter-active .refund-modal,
.refund-pop-leave-active .refund-modal {
  transition: opacity 180ms ease, transform 180ms ease;
}

.refund-pop-enter-from,
.refund-pop-leave-to {
  opacity: 0;
}

.refund-pop-enter-from .refund-modal,
.refund-pop-leave-to .refund-modal {
  opacity: 0;
  transform: translate(-50%, calc(-50% + 8px)) scale(0.985) !important;
}

@media (prefers-reduced-motion: reduce) {
  .refund-pop-enter-active,
  .refund-pop-leave-active,
  .refund-pop-enter-active .refund-modal,
  .refund-pop-leave-active .refund-modal {
    transition: none;
  }
}

/* Element Plus MessageBox Dark Theme Customization */
:global(.encore-dark-box) {
  --el-bg-color-overlay: #181818 !important;
  --el-text-color-primary: #ffffff !important;
  --el-text-color-regular: rgba(255, 255, 255, 0.7) !important;
  --el-border-color-lighter: rgba(255, 255, 255, 0.1) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  border-radius: 8px !important;
  background-color: #141414 !important;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.6) !important;
  backdrop-filter: blur(20px) !important;
  
  .el-message-box__title {
    color: #ffffff !important;
    font-weight: 700 !important;
  }
  
  .el-message-box__content {
    color: rgba(255, 255, 255, 0.8) !important;
  }
  
  .el-message-box__btns {
    .el-button {
      border-radius: 4px !important;
      font-weight: 600 !important;
      transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1) !important;
      
      &:active {
        transform: scale(0.96) !important;
      }
    }
    
    .el-button--default {
      background: rgba(255, 255, 255, 0.08) !important;
      border: none !important;
      color: #ffffff !important;
      
      &:hover {
        background: rgba(255, 255, 255, 0.15) !important;
      }
    }
    
    .el-button--primary {
      background: #e50914 !important;
      border: none !important;
      color: #ffffff !important;
      
      &:hover {
        background: #f6121d !important;
        box-shadow: none !important;
      }
    }
  }
}

:global(.encore-dark-box.encore-refund-box) {
  --el-bg-color-overlay: rgba(12, 12, 14, 0.96) !important;
  --el-border-color-lighter: rgba(229, 9, 20, 0.18) !important;
  width: min(440px, calc(100vw - 32px)) !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.014)),
    rgba(10, 10, 12, 0.96) !important;
  box-shadow: 0 24px 58px rgba(0, 0, 0, 0.74), 0 0 0 1px rgba(229, 9, 20, 0.08) !important;

  .el-message-box__header {
    padding-bottom: 6px !important;
  }

  .el-message-box__status {
    display: none !important;
  }

  .el-message-box__title {
    color: rgba(255, 255, 255, 0.96) !important;
    font-size: 18px !important;
    letter-spacing: 0 !important;
  }

  .el-message-box__message {
    color: rgba(255, 255, 255, 0.68) !important;
    line-height: 1.6 !important;
  }

  .el-message-box__input {
    padding-top: 12px !important;
  }

  .el-textarea__inner {
    min-height: 96px !important;
    border-color: rgba(255, 255, 255, 0.11) !important;
    border-radius: 6px !important;
    background: rgba(0, 0, 0, 0.32) !important;
    box-shadow: none !important;
    color: rgba(255, 255, 255, 0.92) !important;
    resize: vertical !important;

    &::placeholder {
      color: rgba(255, 255, 255, 0.34) !important;
    }

    &:focus {
      border-color: rgba(229, 9, 20, 0.52) !important;
      box-shadow: 0 0 0 2px rgba(229, 9, 20, 0.12) !important;
    }
  }

  .el-message-box__btns {
    gap: 10px !important;
    padding-top: 14px !important;

    .el-button--default {
      border: 1px solid rgba(255, 255, 255, 0.12) !important;
      background: rgba(255, 255, 255, 0.055) !important;
      color: rgba(255, 255, 255, 0.82) !important;

      &:hover {
        border-color: rgba(255, 255, 255, 0.22) !important;
        background: rgba(255, 255, 255, 0.095) !important;
      }
    }

    .el-button--primary {
      border: 1px solid rgba(229, 9, 20, 0.42) !important;
      background: rgba(229, 9, 20, 0.92) !important;
      color: rgba(255, 255, 255, 0.96) !important;

      &:hover {
        border-color: rgba(246, 18, 29, 0.62) !important;
        background: rgba(246, 18, 29, 0.96) !important;
        box-shadow: 0 10px 24px rgba(229, 9, 20, 0.22) !important;
      }
    }
  }
}
</style>
