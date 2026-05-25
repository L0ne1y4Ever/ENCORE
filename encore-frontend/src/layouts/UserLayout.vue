<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { onMounted } from 'vue'
import LanguageSwitch from '../components/LanguageSwitch.vue'

const router = useRouter()
const { t } = useI18n()

onMounted(() => {
  // Force dark mode globally by clearing theme attributes/cache
  document.documentElement.removeAttribute('data-theme')
  localStorage.removeItem('encore-theme')
})
</script>

<template>
  <div class="user-layout">
    <!-- 极简导航，只保留必要入口 -->
    <header class="header">
      <div class="logo" @click="router.push('/')">ENCORE.</div>
      <nav class="nav-links">
        <a href="#" @click.prevent="router.push('/')">{{ t('home.shows') }}</a>
        <a href="#" @click.prevent="router.push('/profile')">{{ t('home.profile') }}</a>
        <LanguageSwitch />
      </nav>
    </header>

    <main class="main-content">
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
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  height: 80px;
  padding: 0 var(--spacing-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-bg-base);
  position: sticky;
  top: 0;
  z-index: 100;

  .logo {
    font-family: var(--font-family-display);
    font-weight: 900;
    font-size: 24px;
    letter-spacing: 0.05em;
    cursor: pointer;
  }

  .nav-links {
    display: flex;
    align-items: center;
    gap: var(--spacing-5);

    a {
      font-family: var(--font-family-sans);
      font-size: 14px;
      font-weight: 500;
      color: var(--color-text-primary);
      text-transform: uppercase;
      letter-spacing: 0.05em;

      &:hover {
        color: var(--color-accent);
      }
    }
  }
}

@media (max-width: 640px) {
  .header {
    height: auto;
    min-height: 80px;
    padding: var(--spacing-3) var(--spacing-4);
    gap: var(--spacing-3);

    .nav-links {
      gap: var(--spacing-3);
    }
  }
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
