<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { computed, ref, watch, type Component } from 'vue'
import {
  Calendar,
  Expand,
  Film,
  Fold,
  Histogram,
  Money,
  List,
  OfficeBuilding,
  Operation,
  SwitchButton,
  UserFilled
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import LanguageSwitch from '../components/LanguageSwitch.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const SIDEBAR_COLLAPSED_KEY = 'encore.admin.sidebarCollapsed'

const readSidebarCollapsed = () => {
  try {
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
  } catch {
    return false
  }
}

const writeSidebarCollapsed = (collapsed: boolean) => {
  try {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(collapsed))
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
}

const isSidebarCollapsed = ref(readSidebarCollapsed())

interface AdminMenuItem {
  path: string
  label: string
  icon: Component
}

const menuItems = computed(() => {
  const items: AdminMenuItem[] = [
    { path: '/admin', label: t('admin.dashboard'), icon: Histogram },
    { path: '/admin/shows', label: t('admin.shows'), icon: Film },
    { path: '/admin/venues', label: t('admin.venueLayout'), icon: OfficeBuilding },
    { path: '/admin/schedules', label: t('admin.schedules'), icon: Calendar },
    { path: '/admin/orders', label: t('admin.orders'), icon: List },
    { path: '/admin/finance', label: t('admin.finance'), icon: Money },
  ]
  if (authStore.currentUser?.role === 'sysadmin') {
    items.push({ path: '/admin/audit-logs', label: t('admin.auditLogs'), icon: Operation })
    items.push({ path: '/admin/users', label: t('admin.staffUsers'), icon: UserFilled })
  }
  return items
})

const isActive = (path: string) => {
  if (path === '/admin') return route.path === '/admin'
  return route.path === path || route.path.startsWith(`${path}/`)
}

const activeMenuItem = computed(() => menuItems.value.find(item => isActive(item.path)))
const pageTitle = computed(() => activeMenuItem.value?.label || t('admin.dashboard'))
const sidebarToggleLabel = computed(() => (
  isSidebarCollapsed.value ? t('admin.expandSidebar') : t('admin.collapseSidebar')
))
const accountName = computed(() => {
  const user = authStore.currentUser
  return user?.displayName || user?.nickname || user?.username || 'ENCORE'
})
const accountRole = computed(() => authStore.currentUser?.role?.toUpperCase() || 'ADMIN')

watch(isSidebarCollapsed, collapsed => {
  writeSidebarCollapsed(collapsed)
})

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

const logout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-copy">
          <div class="brand">ENCORE<span class="dot">.</span></div>
          <div class="brand-caption">{{ t('admin.adminConsole') }}</div>
        </div>
        <button
          class="sidebar-toggle"
          type="button"
          :aria-label="sidebarToggleLabel"
          :title="sidebarToggleLabel"
          :aria-expanded="!isSidebarCollapsed"
          @click="toggleSidebar"
        >
          <component :is="isSidebarCollapsed ? Expand : Fold" class="toggle-icon" />
        </button>
      </div>
      <nav class="menu">
        <button
          v-for="item in menuItems" 
          :key="item.path"
          type="button"
          class="menu-item"
          :class="{ active: isActive(item.path) }"
          :title="item.label"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" class="menu-icon" />
          <span class="menu-label">{{ item.label }}</span>
        </button>
      </nav>
      <div class="bottom-action">
        <div class="account-card" :title="`${accountName} · ${accountRole}`">
          <div class="account-avatar">{{ accountName.slice(0, 1).toUpperCase() }}</div>
          <div class="account-meta">
            <strong>{{ accountName }}</strong>
            <span>{{ accountRole }}</span>
          </div>
        </div>
        <div class="utility-row">
          <LanguageSwitch />
          <button class="logout" type="button" :aria-label="t('common.logout')" @click="logout">
            <SwitchButton class="logout-icon" />
            <span class="logout-label">{{ t('common.logout') }}</span>
          </button>
        </div>
      </div>
    </aside>
    <main class="content-area">
      <header class="top-bar">
        <div>
          <div class="breadcrumb">{{ t('admin.adminConsole') }}</div>
          <h1>{{ pageTitle }}</h1>
        </div>
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
  width: 232px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  background:
    linear-gradient(180deg, rgba(17, 17, 17, 0.98), rgba(10, 10, 10, 0.98));
  transition: width 180ms ease;

  .brand-block {
    min-height: 72px;
    display: grid;
    grid-template-columns: minmax(0, 1fr) 32px;
    align-items: center;
    gap: var(--spacing-2);
    padding: 0 var(--spacing-4);
    border-bottom: 1px solid var(--color-border);
  }

  .brand-copy {
    min-width: 0;
    display: grid;
    gap: 4px;
  }

  .brand {
    font-family: var(--font-family-display);
    font-weight: 900;
    font-size: 22px;
    line-height: 1;
    letter-spacing: 0;
    overflow: hidden;
    text-overflow: clip;
    white-space: nowrap;

    .dot {
      color: var(--color-accent);
    }
  }

  .brand-caption {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 12px;
  }

  .sidebar-toggle {
    width: 32px;
    height: 32px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    display: inline-grid;
    place-items: center;
    background: rgba(255, 255, 255, 0.035);
    color: var(--color-text-secondary);
    cursor: pointer;
    transition: background-color 160ms ease, border-color 160ms ease, color 160ms ease;

    .toggle-icon {
      width: 16px;
      height: 16px;
    }

    &:hover {
      border-color: rgba(200, 149, 90, 0.38);
      background: rgba(200, 149, 90, 0.1);
      color: var(--color-accent);
    }
  }

  .menu {
    flex: 1;
    padding: var(--spacing-3);
    display: grid;
    align-content: start;
    gap: 6px;

    .menu-item {
      width: 100%;
      min-height: 44px;
      border: 1px solid transparent;
      padding: 0 var(--spacing-3);
      border-radius: var(--radius-md);
      background: transparent;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 12px;
      font-family: var(--font-family-sans);
      font-size: 14px;
      font-weight: 500;
      color: var(--color-text-secondary);
      text-align: left;
      transition: background-color 160ms ease, border-color 160ms ease, color 160ms ease;

      .menu-icon {
        width: 18px;
        height: 18px;
        flex: 0 0 auto;
      }

      .menu-label {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      &:hover {
        background-color: rgba(255, 255, 255, 0.05);
        color: var(--color-text-primary);
      }

      &.active {
        border-color: rgba(200, 149, 90, 0.32);
        background-color: rgba(200, 149, 90, 0.12);
        color: var(--color-accent);
        font-weight: 700;
      }
    }
  }

  .bottom-action {
    padding: var(--spacing-3);
    border-top: 1px solid var(--color-border);
    display: grid;
    gap: var(--spacing-3);

    .account-card {
      min-width: 0;
      display: flex;
      align-items: center;
      gap: var(--spacing-2);
      padding: 4px 2px;
      border: 0;
      border-radius: 0;
      background: transparent;
    }

    .account-avatar {
      width: 36px;
      height: 36px;
      border-radius: var(--radius-sm);
      display: grid;
      place-items: center;
      background: rgba(255, 255, 255, 0.06);
      color: var(--color-accent);
      font-family: var(--font-family-sans);
      font-weight: 800;
    }

    .account-meta {
      min-width: 0;
      display: grid;
      gap: 2px;
      font-family: var(--font-family-sans);

      strong {
        overflow: hidden;
        color: var(--color-text-primary);
        font-size: 13px;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      span {
        color: var(--color-text-secondary);
        font-size: 11px;
      }
    }

    .utility-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--spacing-2);
    }

    .logout {
      min-height: 36px;
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      padding: 0 var(--spacing-2);
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: var(--color-text-secondary);
      cursor: pointer;
      font-family: var(--font-family-sans);
      transition: border-color 160ms ease, color 160ms ease;

      .logout-icon {
        width: 15px;
        height: 15px;
      }

      &:hover {
        border-color: rgba(224, 84, 84, 0.45);
        color: var(--color-error);
      }
    }
  }
}

.admin-layout.sidebar-collapsed {
  .sidebar {
    width: 72px;

    .brand-block {
      grid-template-columns: 1fr;
      justify-items: center;
      padding: 0 10px;
    }

    .brand-copy {
      display: none;
    }

    .menu {
      padding: var(--spacing-3) 10px;

      .menu-item {
        justify-content: center;
        padding: 0;
        gap: 0;
      }

      .menu-label {
        display: none;
      }
    }

    .bottom-action {
      padding: var(--spacing-3) 10px;
      justify-items: center;

      .account-card {
        justify-content: center;
        padding: 0;
      }

      .account-meta {
        display: none;
      }

      .utility-row {
        display: grid;
        justify-items: center;
        gap: var(--spacing-2);

        :deep(.language-switch) {
          display: none;
        }
      }

      .logout {
        width: 36px;
        justify-content: center;
        padding: 0;
      }

      .logout-label {
        display: none;
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
    min-height: 72px;
    border-bottom: 1px solid var(--color-border);
    display: flex;
    justify-content: flex-start;
    align-items: center;
    gap: var(--spacing-4);
    padding: 0 28px;
    background: rgba(8, 8, 8, 0.82);

    .breadcrumb {
      margin-bottom: 6px;
      font-family: var(--font-family-sans);
      font-size: 12px;
      color: var(--color-text-secondary);
    }

    h1 {
      font-family: var(--font-family-display);
      font-size: 26px;
      line-height: 1.2;
    }
  }

  .page-container {
    flex: 1;
    overflow-y: auto;
    padding: var(--spacing-5);
  }
}

@media (max-width: 920px) {
  .admin-layout {
    display: block;
    overflow: auto;
  }

  .sidebar {
    width: 100%;
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
    transition: none;

    .brand-block {
      min-height: 72px;
    }

    .menu {
      grid-auto-flow: column;
      grid-auto-columns: max-content;
      overflow-x: auto;
    }

    .bottom-action {
      display: none;
    }
  }

  .admin-layout.sidebar-collapsed {
    .sidebar {
      width: 100%;

      .brand-block {
        grid-template-columns: minmax(0, 1fr) 32px;
        justify-items: stretch;
        padding: 0 var(--spacing-4);
      }

      .brand-copy {
        display: grid;
      }

      .menu {
        padding: var(--spacing-3);

        .menu-item {
          justify-content: flex-start;
          padding: 0 var(--spacing-3);
          gap: 12px;
        }

        .menu-label {
          display: inline;
        }
      }
    }
  }

  .content-area {
    min-height: calc(100vh - 134px);

    .top-bar {
      min-height: 76px;
      padding: 0 var(--spacing-3);
    }

    .page-container {
      padding: var(--spacing-3);
    }
  }
}
</style>
