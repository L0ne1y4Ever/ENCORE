<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { House, User } from '@element-plus/icons-vue'
import LanguageSwitch from '../components/LanguageSwitch.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()

const displayName = computed(() => {
  const user = authStore.currentUser
  return user?.nickname || user?.displayName || user?.username || 'ENCORE'
})

const goProfileTab = (tab: 'tickets' | 'orders' | 'info') => {
  router.push({ path: '/profile', query: { tab } })
}

onMounted(() => {
  // Force dark mode globally by clearing theme attributes/cache
  document.documentElement.removeAttribute('data-theme')
  localStorage.removeItem('encore-theme')
})
</script>

<template>
  <div class="user-layout">
    <header class="ticket-header">
      <button class="brand-lockup" type="button" @click="router.push('/')">
        <span class="brand-mark">ENCORE<span>.</span></span>
      </button>

      <nav class="nav-links" :aria-label="t('home.primaryNavigation')">
        <button class="nav-item" :class="{ active: route.path === '/' }" type="button" @click="router.push('/')">
          <House />
          <span>{{ t('home.shows') }}</span>
        </button>
        <button class="account-chip" :class="{ active: route.path === '/profile' }" type="button" @click="goProfileTab('tickets')">
          <User />
          <span>{{ displayName }}</span>
        </button>
        <LanguageSwitch />
      </nav>
    </header>

    <main class="main-content" id="main-content">
      <router-view v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped lang="scss">
.user-layout {
  --ticket-red: #e50914;
  --neon-cyan: #39f5ff;
  --neon-magenta: #ff3d9a;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    linear-gradient(120deg, rgba(229, 9, 20, 0.1) 0%, rgba(8, 8, 8, 0) 32%),
    linear-gradient(220deg, rgba(57, 245, 255, 0.055) 0%, rgba(8, 8, 8, 0) 34%),
    linear-gradient(180deg, rgba(255, 61, 154, 0.045) 0%, rgba(8, 8, 8, 0) 280px),
    var(--color-bg-base);
}

.ticket-header {
  min-height: 76px;
  padding: 0 clamp(20px, 4vw, 56px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(240, 237, 232, 0.1);
  background:
    linear-gradient(90deg, rgba(229, 9, 20, 0.06), rgba(57, 245, 255, 0.03), rgba(255, 61, 154, 0.035)),
    rgba(8, 8, 8, 0.78);
  backdrop-filter: blur(22px) saturate(1.18);
  -webkit-backdrop-filter: blur(22px) saturate(1.18);
  position: sticky;
  top: 0;
  z-index: 100;
  gap: var(--spacing-4);
}

.brand-lockup {
  min-height: 52px;
  border: none;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  display: grid;
  align-content: center;
  gap: 2px;
  padding: 0;
  text-align: left;
}

.brand-mark {
  font-family: var(--font-family-display);
  font-weight: 900;
  font-size: 24px;
  letter-spacing: 0.06em;
  line-height: 1;

  span {
    color: var(--ticket-red);
  }
}

.nav-links {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
}

.nav-item,
.account-chip {
  position: relative;
  min-height: 38px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 700;
  padding: 0 11px;
  transition: border-color 180ms ease, color 180ms ease, background-color 180ms ease;

  &::after {
    content: '';
    position: absolute;
    left: 10px;
    right: 10px;
    bottom: 3px;
    height: 2px;
    border-radius: 2px;
    background: var(--ticket-red);
    opacity: 0;
    transform: scaleX(0.65);
    transition: opacity 180ms ease, transform 180ms ease;
  }

  svg {
    width: 16px;
    height: 16px;
    flex: 0 0 auto;
  }

  &:hover,
  &.active {
    border-color: rgba(255, 255, 255, 0.08);
    background: rgba(255, 255, 255, 0.055);
    color: var(--color-text-primary);
  }

  &.active::after {
    opacity: 1;
    transform: scaleX(1);
  }
}

.account-chip {
  max-width: 220px;
  border-color: transparent;
  color: var(--color-text-primary);

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 640px) {
  .ticket-header {
    min-height: 68px;
    padding: var(--spacing-3) var(--spacing-4);
    gap: var(--spacing-2);

    .nav-links {
      flex: 1;
      justify-content: flex-end;
    }

    .nav-item {
      padding: 0 10px;

      span {
        display: none;
      }

      &::after {
        left: 8px;
        right: 8px;
      }
    }

    .account-chip {
      max-width: min(42vw, 170px);
      padding: 0 10px;
    }
  }
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
