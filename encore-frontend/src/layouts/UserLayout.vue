<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t, locale } = useI18n()

const toggleLang = () => {
  locale.value = locale.value === 'en' ? 'zh' : 'en'
}
</script>

<template>
  <div class="user-layout">
    <!-- 极简导航，只保留必要入口 -->
    <header class="header">
      <div class="logo" @click="router.push('/')">ENCORE.</div>
      <nav class="nav-links">
        <a href="#" @click.prevent="router.push('/')">Shows</a>
        <a href="#" @click.prevent="router.push('/profile')">Profile</a>
        <a href="#" @click.prevent="toggleLang">{{ t('common.language') }}</a>
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

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
