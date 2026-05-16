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

  .empty-state {
    color: var(--color-text-secondary);
    font-style: italic;
  }
}
</style>
