<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import LanguageSwitch from '../components/LanguageSwitch.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const menuItems = computed(() => {
  const items = [
    { path: '/admin', label: t('admin.dashboard'), icon: 'Histogram' },
    { path: '/admin/shows', label: t('admin.shows'), icon: 'Film' },
    { path: '/admin/venues', label: t('admin.venues'), icon: 'OfficeBuilding' },
    { path: '/admin/layouts', label: t('admin.layouts'), icon: 'Grid' },
    { path: '/admin/schedules', label: t('admin.schedules'), icon: 'Calendar' },
    { path: '/admin/orders', label: t('admin.orders'), icon: 'List' },
  ]
  if (authStore.currentUser?.role === 'sysadmin') {
    items.push({ path: '/admin/users', label: t('admin.staffUsers'), icon: 'UserFilled' })
  }
  return items
})

const isActive = (path: string) => {
  if (path === '/admin') return route.path === '/admin'
  return route.path === path || route.path.startsWith(`${path}/`)
}

const logout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="brand">ENCORE<span class="dot">.</span></div>
      <nav class="menu">
        <div 
          v-for="item in menuItems" 
          :key="item.path"
          class="menu-item"
          :class="{ active: isActive(item.path) }"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </div>
      </nav>
      <div class="bottom-action">
        <LanguageSwitch />
        <button class="logout" type="button" @click="logout">{{ t('common.logout') }}</button>
      </div>
    </aside>
    <main class="content-area">
      <header class="top-bar">
        <div class="breadcrumb">{{ route.name?.toString().replace('Admin', '') || t('admin.dashboard') }}</div>
      </header>
      <div class="page-container">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: var(--color-bg-base);
}

.sidebar {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  background-color: var(--color-bg-elevated);

  .brand {
    height: 80px;
    display: flex;
    align-items: center;
    padding: 0 var(--spacing-4);
    font-family: var(--font-family-display);
    font-weight: 900;
    font-size: 20px;
    letter-spacing: 0.05em;

    .dot {
      color: var(--color-accent);
    }
  }

  .menu {
    flex: 1;
    padding: var(--spacing-4) 0;

    .menu-item {
      padding: var(--spacing-3) var(--spacing-4);
      margin: 0 var(--spacing-2) var(--spacing-2);
      border-radius: var(--radius-sm);
      cursor: pointer;
      font-family: var(--font-family-sans);
      font-size: 14px;
      font-weight: 500;
      color: var(--color-text-secondary);
      transition: all 150ms ease;

      &:hover {
        background-color: rgba(255, 255, 255, 0.05);
        color: var(--color-text-primary);
      }

      &.active {
        background-color: rgba(200, 149, 90, 0.1); /* accent with low opacity */
        color: var(--color-accent);
        font-weight: 700;
      }
    }
  }

  .bottom-action {
    padding: var(--spacing-4);
    border-top: 1px solid var(--color-border);
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--spacing-3);

    .user-info {
      font-size: 12px;
      color: var(--color-text-primary);
    }

    .logout {
      background: transparent;
      border: 0;
      font-size: 12px;
      color: var(--color-text-secondary);
      cursor: pointer;
      font-family: var(--font-family-sans);
      &:hover {
        color: var(--color-error);
      }
    }
  }
}

.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .top-bar {
    height: 80px;
    border-bottom: 1px solid var(--color-border);
    display: flex;
    align-items: center;
    padding: 0 var(--spacing-6);

    .breadcrumb {
      font-family: var(--font-family-display);
      font-size: 24px;
      font-weight: 700;
    }
  }

  .page-container {
    flex: 1;
    overflow-y: auto;
    padding: var(--spacing-6);
  }
}
</style>
