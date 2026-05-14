<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { t, locale } = useI18n()

const username = ref('')
const password = ref('')
const isRegister = ref(false)
const errorMsg = ref('')

const toggleLang = () => {
  locale.value = locale.value === 'en' ? 'zh' : 'en'
}

const handleSubmit = () => {
  errorMsg.value = ''
  if (isRegister.value) {
    alert('Mock Registration Success! Please login.')
    isRegister.value = false
    return
  }
  
  const success = authStore.login(username.value, password.value)
  if (success) {
    const role = authStore.currentUser?.role
    const redirect = route.query.redirect as string
    if (redirect) {
      router.push(redirect)
    } else {
      if (role === 'admin') router.push('/admin')
      else if (role === 'checker') router.push('/checkin')
      else router.push('/')
    }
  } else {
    errorMsg.value = 'Invalid Credentials. Try user/123, admin/123'
  }
}
</script>

<template>
  <div class="login-page">
    <div class="top-nav">
      <div class="lang-toggle" @click="toggleLang">
        {{ t('common.language') }}
      </div>
    </div>
    
    <div class="auth-container">
      <div class="brand">ENCORE<span class="dot">.</span></div>
      
      <div class="form-wrapper">
        <div class="tabs">
          <span :class="{ active: !isRegister }" @click="isRegister = false">{{ t('common.login') }}</span>
          <span :class="{ active: isRegister }" @click="isRegister = true">{{ t('common.register') }}</span>
        </div>

        <form @submit.prevent="handleSubmit">
          <div class="input-group">
            <input type="text" v-model="username" :placeholder="t('common.username')" required />
          </div>
          <div class="input-group">
            <input type="password" v-model="password" :placeholder="t('common.password')" required />
          </div>
          
          <div class="error" v-if="errorMsg">{{ errorMsg }}</div>
          
          <button type="submit" class="submit-btn">{{ t('common.submit') }}</button>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg-base);
  position: relative;
}

.top-nav {
  position: absolute;
  top: var(--spacing-6);
  right: var(--spacing-6);
  
  .lang-toggle {
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    color: var(--color-text-secondary);
    transition: color 150ms ease;
    
    &:hover {
      color: var(--color-text-primary);
    }
  }
}

.auth-container {
  width: 100%;
  max-width: 400px;
  padding: var(--spacing-6);
}

.brand {
  font-family: var(--font-family-display);
  font-size: 48px;
  font-weight: 900;
  text-align: center;
  margin-bottom: var(--spacing-8);
  letter-spacing: 0.05em;

  .dot {
    color: var(--color-accent);
  }
}

.form-wrapper {
  .tabs {
    display: flex;
    gap: var(--spacing-6);
    margin-bottom: var(--spacing-8);
    border-bottom: 1px solid var(--color-border);
    padding-bottom: var(--spacing-2);

    span {
      font-family: var(--font-family-sans);
      font-size: 14px;
      font-weight: 500;
      color: var(--color-text-secondary);
      text-transform: uppercase;
      letter-spacing: 0.1em;
      cursor: pointer;
      transition: color 150ms ease;

      &.active {
        color: var(--color-text-primary);
        font-weight: 700;
      }
    }
  }

  .input-group {
    margin-bottom: var(--spacing-6);

    input {
      width: 100%;
      background: transparent;
      border: none;
      border-bottom: 1px solid var(--color-border-strong);
      padding: var(--spacing-3) 0;
      font-family: var(--font-family-sans);
      font-size: 16px;
      color: var(--color-text-primary);
      outline: none;
      transition: border-color 200ms ease;

      &:focus {
        border-bottom-color: var(--color-accent);
      }

      &::placeholder {
        color: var(--color-text-ghost);
      }
    }
  }

  .error {
    color: var(--color-error);
    font-size: 12px;
    margin-bottom: var(--spacing-4);
    font-family: var(--font-family-sans);
  }

  .submit-btn {
    width: 100%;
    padding: 16px;
    background-color: var(--color-text-primary);
    color: var(--color-bg-base);
    border: none;
    font-family: var(--font-family-sans);
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: background-color 150ms ease;

    &:hover {
      background-color: var(--color-accent);
    }
  }
}
</style>
