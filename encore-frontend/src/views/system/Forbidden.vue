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

const logout = async () => {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <main class="system-page">
    <section class="system-panel">
      <span class="code">403</span>
      <h1>{{ t('system.forbiddenTitle') }}</h1>
      <p>{{ t('system.forbiddenCopy') }}</p>
      <div class="actions">
        <button type="button" class="primary-action" @click="router.push(homePath)">
          {{ t('system.backHome') }}
        </button>
        <button v-if="authStore.currentUser" type="button" class="text-action" @click="logout">
          {{ t('common.logout') }}
        </button>
      </div>
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
    radial-gradient(circle at 20% 24%, rgba(255, 255, 255, 0.06) 0 1px, transparent 2px),
    radial-gradient(circle at 72% 34%, rgba(255, 255, 255, 0.08) 0 1px, transparent 2px),
    radial-gradient(circle at 46% 72%, rgba(255, 255, 255, 0.05) 0 1px, transparent 2px),
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
    margin-top: 14px;
    color: var(--color-text-secondary);
    line-height: 1.65;
  }
}

.actions {
  margin-top: 26px;
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.primary-action,
.text-action {
  min-height: 38px;
  border-radius: 4px;
  font: inherit;
  cursor: pointer;
}

.primary-action {
  border: 0;
  padding: 0 18px;
  background: #e50914;
  color: #fff;
  font-weight: 700;
}

.text-action {
  border: 0;
  padding: 0;
  background: none;
  color: var(--color-text-secondary);
  text-decoration: underline;
  text-decoration-color: rgba(255, 255, 255, 0.25);
  text-underline-offset: 3px;

  &:hover {
    color: var(--color-text-primary);
  }
}
</style>
