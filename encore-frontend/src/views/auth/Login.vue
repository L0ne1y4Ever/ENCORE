<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useI18n } from 'vue-i18n'
import { setAppLocale } from '../../i18n'
import type { LocaleCode } from '../../i18n'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { t, locale } = useI18n()

const username = ref('')
const password = ref('')
const nickname = ref('')
const isRegister = ref(false)
const errorMsg = ref('')
const loading = ref(false)

// 海报网格背景图列表
const posterImages = [
  'https://images.unsplash.com/photo-1507676184212-d0330a157088?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1540039155732-684736dd61dc?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1514533212735-5df27d970db0?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1478147427282-58a87a120781?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1524712245354-2c4e5e7121c0?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1598387993441-a364f854c3e1?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?q=80&w=400&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?q=80&w=400&auto=format&fit=crop',
]

const toggleLang = () => {
  const nextLocale: LocaleCode = locale.value === 'en' ? 'zh' : 'en'
  setAppLocale(nextLocale)
}

const handleSubmit = async () => {
  errorMsg.value = ''
  loading.value = true

  try {
    if (isRegister.value) {
      if (!nickname.value.trim()) {
        errorMsg.value = t('login.nicknameRequired')
        return
      }
      await authStore.register(username.value, password.value, nickname.value)
    } else {
      await authStore.login(username.value, password.value)
    }
    const role = authStore.currentUser?.role
    const redirect = route.query.redirect as string
    if (redirect) {
      router.push(redirect)
    } else {
      if (role === 'admin' || role === 'sysadmin') router.push('/admin')
      else if (role === 'checker') router.push('/checkin')
      else router.push('/')
    }
  } catch (error) {
    errorMsg.value = error instanceof Error
      ? error.message
      : isRegister.value
        ? t('login.registerFailed')
        : t('login.loginFailed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- Netflix 风格：海报网格背景 -->
    <div class="poster-grid">
      <div class="poster-item" v-for="(img, idx) in posterImages" :key="idx">
        <img :src="img" alt="" loading="lazy" />
      </div>
    </div>

    <!-- 多层渐变蒙版：从上到下逐渐变暗，底部完全黑色 -->
    <div class="gradient-overlay"></div>

    <!-- 顶部导航 -->
    <header class="top-bar">
      <div class="brand" @click="router.push('/')">ENCORE<span class="dot">.</span></div>
      <div class="top-actions">
        <button class="lang-btn" @click="toggleLang">{{ t('common.language') }}</button>
        <button v-if="!isRegister" class="signin-btn" @click="isRegister = false">
          {{ t('common.login') }}
        </button>
      </div>
    </header>

    <!-- 中心内容 -->
    <div class="center-content">
      <h1 class="hero-title">{{ t('login.heroTitle') }}</h1>
      <p class="hero-subtitle">{{ t('login.heroSubtitle') }}</p>
      <p class="hero-prompt">{{ t('login.heroPrompt') }}</p>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <div class="form-row">
          <input
            type="text"
            v-model="username"
            :placeholder="t('common.username')"
            required
            class="form-input"
          />
          <div v-if="isRegister" class="extra-field">
            <input
              type="text"
              v-model="nickname"
              :placeholder="t('common.nickname')"
              :required="isRegister"
              class="form-input"
            />
          </div>
          <input
            type="password"
            v-model="password"
            :placeholder="t('common.password')"
            required
            class="form-input"
          />
          <button type="submit" class="cta-btn" :disabled="loading">
            {{ loading ? '...' : t('common.getStarted') }}
            <span class="arrow" v-if="!loading">›</span>
          </button>
        </div>
        <div class="error-msg" v-if="errorMsg">{{ errorMsg }}</div>
      </form>

      <div class="toggle-text">
        <template v-if="!isRegister">
          {{ t('common.newToEncore') }} <a href="#" @click.prevent="isRegister = true">{{ t('common.signUpNow') }}</a>
        </template>
        <template v-else>
          {{ t('common.alreadyHaveAccount') }} <a href="#" @click.prevent="isRegister = false">{{ t('common.signInNow') }}</a>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  width: 100vw;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background-color: #000;
  display: flex;
  flex-direction: column;
}

/* ===== 海报网格背景（类 Netflix） ===== */
.poster-grid {
  position: absolute;
  top: -5%;
  left: -5%;
  width: 110%;
  height: 80%;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(4, 1fr);
  gap: 6px;
  transform: rotate(-10deg) scale(1.3);
  transform-origin: center center;
  z-index: 0;

  .poster-item {
    overflow: hidden;
    border-radius: 4px;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }
}

/* ===== 渐变蒙版：上方半透明，往下逐渐变黑 ===== */
.gradient-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    /* 径向暗角 */
    radial-gradient(ellipse at 50% 30%, rgba(0,0,0,0.2) 0%, rgba(0,0,0,0.7) 70%),
    /* 从中间往下完全变黑 */
    linear-gradient(
      to bottom,
      rgba(0,0,0,0.3) 0%,
      rgba(0,0,0,0.5) 30%,
      rgba(0,0,0,0.85) 55%,
      #000 70%
    );
}

/* ===== 顶部导航 ===== */
.top-bar {
  position: relative;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 48px;

  @media (max-width: 768px) {
    padding: 16px 20px;
  }

  .brand {
    font-family: var(--font-family-display);
    font-size: 36px;
    font-weight: 900;
    color: #fff;
    letter-spacing: 0.04em;
    cursor: pointer;

    .dot {
      color: var(--color-accent);
    }
  }

  .top-actions {
    display: flex;
    align-items: center;
    gap: 16px;

    .lang-btn {
      background: transparent;
      border: 1px solid rgba(255, 255, 255, 0.4);
      color: #fff;
      padding: 6px 14px;
      font-family: var(--font-family-sans);
      font-size: 13px;
      cursor: pointer;
      border-radius: 4px;
      transition: all 150ms ease;

      &:hover {
        background: rgba(255, 255, 255, 0.1);
        border-color: #fff;
      }
    }

    .signin-btn {
      background-color: var(--color-accent);
      color: #fff;
      border: none;
      padding: 6px 18px;
      font-family: var(--font-family-sans);
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      border-radius: 4px;
    }
  }
}

/* ===== 主内容区（居中文字 + 表单） ===== */
.center-content {
  position: relative;
  z-index: 10;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  text-align: center;
  max-width: 900px;
  margin: 0 auto;
  /* 偏上一点，因为底部渐变已经是纯黑 */
  margin-top: -8vh;

  .hero-title {
    font-family: var(--font-family-display);
    font-size: clamp(32px, 5vw, 52px);
    font-weight: 900;
    color: #fff;
    line-height: 1.15;
    margin-bottom: 16px;
  }

  .hero-subtitle {
    font-family: var(--font-family-sans);
    font-size: 20px;
    color: rgba(255, 255, 255, 0.8);
    margin-bottom: 24px;
  }

  .hero-prompt {
    font-family: var(--font-family-sans);
    font-size: 18px;
    color: rgba(255, 255, 255, 0.7);
    margin-bottom: 20px;
  }
}

/* ===== 表单 ===== */
.auth-form {
  width: 100%;
  max-width: 680px;

  .form-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    justify-content: center;
  }

  .form-input {
    flex: 1 1 180px;
    min-width: 0;
    background-color: rgba(22, 22, 22, 0.7);
    border: 1px solid rgba(255, 255, 255, 0.25);
    border-radius: 4px;
    padding: 14px 16px;
    font-family: var(--font-family-sans);
    font-size: 16px;
    color: #fff;
    outline: none;
    transition: border-color 200ms ease;

    &:focus {
      border-color: #fff;
    }

    &::placeholder {
      color: rgba(255, 255, 255, 0.4);
    }
  }

  .extra-field {
    display: contents;
  }

  .cta-btn {
    flex: 0 0 auto;
    background-color: var(--color-accent);
    color: #fff;
    border: none;
    border-radius: 4px;
    padding: 14px 28px;
    font-family: var(--font-family-sans);
    font-size: 18px;
    font-weight: 700;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;
    transition: filter 150ms ease;
    white-space: nowrap;

    .arrow {
      font-size: 24px;
      line-height: 1;
    }

    &:hover:not(:disabled) {
      filter: brightness(1.15);
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }
}

.error-msg {
  margin-top: 12px;
  color: #e87c03;
  font-size: 14px;
  font-family: var(--font-family-sans);
}

.toggle-text {
  margin-top: 24px;
  font-family: var(--font-family-sans);
  font-size: 16px;
  color: rgba(255, 255, 255, 0.5);

  a {
    color: #fff;
    text-decoration: none;
    font-weight: 600;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
