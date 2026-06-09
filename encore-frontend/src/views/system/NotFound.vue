<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const homePath = computed(() => {
  const role = authStore.currentUser?.role
  if (role === 'admin' || role === 'sysadmin') return '/admin'
  if (role === 'checker') return '/checkin'
  if (role === 'user') return '/'
  return '/login'
})
</script>

<template>
  <main class="system-page">
    <section class="system-panel">
      <span class="code">404</span>
      <h1>{{ t('system.notFoundTitle') }}</h1>
      <p>{{ t('system.notFoundCopy') }}</p>
      <button type="button" class="primary-action" @click="router.push(homePath)">
        {{ t('system.backHome') }}
      </button>
    </section>
  </main>
</template>

<style scoped lang="scss">
.system-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 30%, rgba(255, 255, 255, 0.06) 0 1px, transparent 2px),
    radial-gradient(circle at 68% 22%, rgba(255, 255, 255, 0.08) 0 1px, transparent 2px),
    radial-gradient(circle at 76% 78%, rgba(255, 255, 255, 0.05) 0 1px, transparent 2px),
    linear-gradient(180deg, #030305 0%, #050507 58%, #020203 100%);
  color: var(--color-text-primary);
}

.system-panel {
  width: min(520px, 100%);
  border: 1px solid rgba(240, 237, 232, 0.12);
  border-radius: 6px;
  padding: 34px;
  background: rgba(10, 10, 12, 0.86);

  .code {
    color: var(--color-accent);
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0.18em;
  }

  h1 {
    margin-top: 12px;
    font-size: clamp(30px, 6vw, 48px);
    line-height: 1.05;
  }

  p {
    margin: 14px 0 26px;
    color: var(--color-text-secondary);
    line-height: 1.65;
  }
}

.primary-action {
  min-height: 38px;
  border: 0;
  border-radius: 4px;
  padding: 0 18px;
  background: #e50914;
  color: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
</style>
