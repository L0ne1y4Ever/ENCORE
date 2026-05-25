<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

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

// Mock tickets data
const tickets = ref([
  { id: 't-001', showTitle: 'SWAN LAKE', date: '2026-06-10 20:00', venue: 'Opera House', seat: 'VIP-9-9', status: 'VALID' },
  { id: 't-002', showTitle: 'HAMILTON', date: '2026-07-22 19:30', venue: 'Grand Theater', seat: 'A-12-14', status: 'USED' }
])

// Mock orders data
const orders = ref([
  { id: 'ORD-8X9A2B', date: '2026-05-10', total: 280, items: 2, status: 'COMPLETED' },
  { id: 'ORD-3M7C1P', date: '2026-04-15', total: 120, items: 1, status: 'COMPLETED' }
])

// Mock reservations data
const reservations = ref([
  { id: 'r-001', show: 'COLDPLAY: MUSIC OF THE SPHERES', date: '2026-08-15', lineNum: 8204 }
])
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
      <button :class="{ active: activeTab === 'tickets' }" @click="activeTab = 'tickets'">My Tickets</button>
      <button :class="{ active: activeTab === 'orders' }" @click="activeTab = 'orders'">Orders</button>
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
            <h2>My Tickets</h2>
            <div class="ticket-list" v-if="tickets.length > 0">
              <div class="t-card" v-for="tkt in tickets" :key="tkt.id" :class="{ used: tkt.status === 'USED' }">
                <div class="t-info">
                  <h3>{{ tkt.showTitle }}</h3>
                  <p>{{ tkt.date }} &bull; {{ tkt.venue }}</p>
                  <p class="t-seat">Seat: {{ tkt.seat }}</p>
                </div>
                <div class="t-status">{{ tkt.status }}</div>
              </div>
            </div>
            <div class="empty-state" v-else>No tickets found.</div>
          </section>
        </div>

        <div v-else-if="activeTab === 'orders'" class="tab-pane" key="orders">
          <section class="orders-section">
            <h2>Order History</h2>
            <div class="order-list" v-if="orders.length > 0">
              <div class="o-card" v-for="ord in orders" :key="ord.id">
                <div class="o-header">
                  <span class="o-id">{{ ord.id }}</span>
                  <span class="o-date">{{ ord.date }}</span>
                </div>
                <div class="o-details">
                  <span>{{ ord.items }} item(s)</span>
                  <span class="o-total">${{ ord.total }}</span>
                </div>
              </div>
            </div>
            <div class="empty-state" v-else>No order history.</div>
          </section>
        </div>

        <div v-else-if="activeTab === 'reservations'" class="tab-pane" key="reservations">
          <section class="reservations-section">
            <h2>{{ t('reservation.myReservations') }}</h2>
            <div class="reservation-list" v-if="reservations.length > 0">
              <div class="r-card" v-for="res in reservations" :key="res.id">
                <div class="r-info">
                  <h3>{{ res.show }}</h3>
                  <p>{{ res.date }}</p>
                </div>
                <div class="r-status">
                  <span class="line-num">#{{ res.lineNum }}</span>
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

  .save-msg {
    font-size: 14px;
    color: var(--color-accent);
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
  }
}

.empty-state {
  color: var(--color-text-secondary);
  font-style: italic;
  padding: var(--spacing-4) 0;
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
    font-size: 14px;

    .o-total {
      font-size: 18px;
      font-weight: 700;
      color: var(--color-accent);
    }
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
