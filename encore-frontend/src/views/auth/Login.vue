<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Hide, Lock, User, View } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import { setAppLocale } from '../../i18n'
import type { LocaleCode } from '../../i18n'
import type { UserRole } from '../../api/auth'
import { handlePosterError, posterImageSrc } from '../../utils/ticketing'

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
const showPassword = ref(false)

const posterImages = [
  'https://images.unsplash.com/photo-1507676184212-d0330a157088?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1540039155732-684736dd61dc?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1524712245354-2c4e5e7121c0?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?q=80&w=700&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1514533212735-5df27d970db0?q=80&w=700&auto=format&fit=crop'
]

const posterRows = [
  [...posterImages.slice(0, 6), ...posterImages.slice(0, 6)],
  [...posterImages.slice(6, 12), ...posterImages.slice(6, 12)],
  [...posterImages.slice(3, 9), ...posterImages.slice(3, 9)]
]

const homeForRole = (role?: UserRole | string) => {
  if (role === 'admin' || role === 'sysadmin') return '/admin'
  if (role === 'checker') return '/checkin'
  return '/'
}

const canUseRedirect = (redirect: string | undefined, role?: UserRole | string) => {
  if (!redirect || !redirect.startsWith('/') || redirect.startsWith('//')) return false
  if (role === 'admin' || role === 'sysadmin') return redirect === '/admin' || redirect.startsWith('/admin/')
  if (role === 'checker') return redirect === '/checkin' || redirect.startsWith('/checkin/')
  return redirect === '/' || (!redirect.startsWith('/admin') && !redirect.startsWith('/checkin'))
}

const toggleLang = () => {
  const nextLocale: LocaleCode = locale.value === 'en' ? 'zh' : 'en'
  setAppLocale(nextLocale)
}

const setMode = (nextRegister: boolean) => {
  isRegister.value = nextRegister
  errorMsg.value = ''
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
    const rawRedirect = Array.isArray(route.query.redirect)
      ? route.query.redirect[0]
      : route.query.redirect
    const redirect = typeof rawRedirect === 'string' ? rawRedirect : undefined
    if (redirect && canUseRedirect(redirect, role)) {
      router.push(redirect)
    } else {
      router.push(homeForRole(role))
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
    <div class="poster-backdrop" aria-hidden="true">
      <div
        v-for="(row, rowIndex) in posterRows"
        :key="rowIndex"
        class="poster-row"
        :class="{ reverse: rowIndex === 1 }"
      >
        <img
          v-for="(img, imgIndex) in row"
          :key="`${rowIndex}-${imgIndex}-${img}`"
          :src="posterImageSrc(img, 'ENCORE')"
          alt=""
          loading="lazy"
          @error="handlePosterError($event, 'ENCORE')"
        />
      </div>
    </div>
    <div class="gradient-overlay" aria-hidden="true" />

    <header class="login-topbar">
      <button class="brand" type="button" @click="router.push('/')">
        ENCORE<span>.</span>
      </button>
      <button class="language-btn" type="button" @click="toggleLang">
        {{ t('common.language') }}
      </button>
    </header>

    <main class="login-shell">
      <section class="login-hero-copy">
        <span class="eyebrow">{{ t('login.ticketingKicker') }}</span>
        <h1>{{ t('login.heroTitle') }}</h1>
        <p>{{ t('login.heroSubtitle') }}</p>
        <div class="trust-row">
          <span>{{ t('login.curatedShows') }}</span>
          <span>{{ t('login.secureCheckout') }}</span>
          <span>{{ t('login.instantTickets') }}</span>
        </div>
      </section>

      <section class="auth-panel">
        <div class="auth-card">
          <div class="auth-heading">
            <span>{{ t('login.memberAccess') }}</span>
            <h2>{{ isRegister ? t('login.createAccount') : t('login.signInTitle') }}</h2>
            <p>{{ t('login.heroPrompt') }}</p>
          </div>

          <div class="mode-switch" role="tablist" :aria-label="t('login.authMode')">
            <button type="button" :class="{ active: !isRegister }" @click="setMode(false)">
              {{ t('common.login') }}
            </button>
            <button type="button" :class="{ active: isRegister }" @click="setMode(true)">
              {{ t('common.register') }}
            </button>
          </div>

          <form class="auth-form" @submit.prevent="handleSubmit">
            <label class="field-group">
              <span>{{ t('common.username') }}</span>
              <span class="field-shell">
                <User class="field-icon" />
                <input
                  v-model="username"
                  type="text"
                  autocomplete="username"
                  required
                  :placeholder="t('login.usernamePlaceholder')"
                />
              </span>
            </label>

            <transition name="field-slide">
              <label v-if="isRegister" class="field-group">
                <span>{{ t('common.nickname') }}</span>
                <span class="field-shell">
                  <User class="field-icon" />
                  <input
                    v-model="nickname"
                    type="text"
                    autocomplete="nickname"
                    :required="isRegister"
                    :placeholder="t('login.nicknamePlaceholder')"
                  />
                </span>
              </label>
            </transition>

            <label class="field-group">
              <span>{{ t('common.password') }}</span>
              <span class="field-shell password-shell">
                <Lock class="field-icon" />
                <input
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                  required
                  :placeholder="t('login.passwordPlaceholder')"
                />
                <button
                  class="password-toggle"
                  type="button"
                  :aria-label="showPassword ? t('login.hidePassword') : t('login.showPassword')"
                  @click="showPassword = !showPassword"
                >
                  <Hide v-if="showPassword" />
                  <View v-else />
                </button>
              </span>
            </label>

            <p v-if="errorMsg" class="error-msg" role="alert">{{ errorMsg }}</p>

            <button class="submit-btn" type="submit" :disabled="loading">
              <span>{{ loading ? t('common.processing') : (isRegister ? t('login.createAccountCta') : t('common.getStarted')) }}</span>
              <ArrowRight v-if="!loading" />
            </button>
          </form>

          <p class="toggle-text">
            <template v-if="!isRegister">
              {{ t('common.newToEncore') }}
              <button type="button" @click="setMode(true)">{{ t('common.signUpNow') }}</button>
            </template>
            <template v-else>
              {{ t('common.alreadyHaveAccount') }}
              <button type="button" @click="setMode(false)">{{ t('common.signInNow') }}</button>
            </template>
          </p>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  width: 100%;
  min-height: 100vh;
  overflow-x: hidden;
  background:
    linear-gradient(180deg, rgba(200, 149, 90, 0.06) 0%, rgba(8, 8, 8, 0) 300px),
    var(--color-bg-base);
  color: var(--color-text-primary);
}

.login-topbar {
  height: 76px;
  padding: 0 clamp(20px, 4vw, 56px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 5;
}

.brand,
.language-btn {
  min-height: 44px;
  border: none;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
}

.brand {
  font-family: var(--font-family-display);
  font-size: 28px;
  font-weight: 900;
  letter-spacing: 0.06em;

  span {
    color: var(--color-accent);
  }
}

.language-btn {
  border: 1px solid var(--color-border-strong);
  border-radius: 4px;
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 13px;
  font-weight: 700;
  padding: 0 16px;
  transition: border-color 160ms ease, color 160ms ease;

  &:hover {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }
}

.login-shell {
  width: min(1180px, calc(100% - 40px));
  min-height: calc(100vh - 112px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: var(--spacing-5);
  align-items: center;
  padding: var(--spacing-4) 0 var(--spacing-7);
}

.visual-panel {
  min-height: 620px;
  position: relative;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-bg-elevated);
  isolation: isolate;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      linear-gradient(90deg, rgba(8, 8, 8, 0.92) 0%, rgba(8, 8, 8, 0.48) 48%, rgba(8, 8, 8, 0.18) 100%),
      linear-gradient(180deg, rgba(8, 8, 8, 0) 40%, var(--color-bg-base) 100%);
    z-index: -1;
  }
}

.cover-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.9) contrast(1.05);
  z-index: -2;
}

.visual-copy {
  width: min(520px, calc(100% - 40px));
  height: 100%;
  min-height: 620px;
  padding: clamp(32px, 5vw, 64px);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;

  .eyebrow {
    width: max-content;
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 3px;
    background: rgba(8, 8, 8, 0.55);
    color: rgba(255, 255, 255, 0.7);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.12em;
    padding: 8px 12px;
    text-transform: uppercase;
  }

  h1 {
    margin-top: var(--spacing-4);
    font-size: clamp(40px, 5vw, 68px);
    line-height: 1.02;
  }

  p {
    max-width: 430px;
    margin-top: var(--spacing-3);
    color: rgba(240, 237, 232, 0.78);
    font-family: var(--font-family-sans);
    font-size: 17px;
    line-height: 1.6;
  }
}

.trust-row {
  margin-top: var(--spacing-5);
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);

  span {
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 3px;
    background: transparent;
    color: rgba(255, 255, 255, 0.7);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 700;
    padding: 8px 12px;
  }
}

.poster-stack {
  position: absolute;
  right: clamp(20px, 4vw, 56px);
  top: 50%;
  transform: translateY(-50%);
  display: grid;
  grid-template-columns: repeat(2, 118px);
  gap: var(--spacing-3);

  img {
    width: 118px;
    aspect-ratio: 3 / 4;
    border: 1px solid rgba(240, 237, 232, 0.18);
    border-radius: var(--radius-md);
    box-shadow: 0 20px 42px rgba(0, 0, 0, 0.45);
    object-fit: cover;

    &:nth-child(even) {
      transform: translateY(28px);
    }
  }
}

.auth-panel {
  min-width: 0;
}

.auth-card {
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: rgba(17, 17, 17, 0.92);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.36);
  padding: clamp(24px, 4vw, 40px);
}

.auth-heading {
  span {
    color: var(--color-accent);
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  h2 {
    margin-top: var(--spacing-2);
    font-size: 34px;
    line-height: 1.12;
  }

  p {
    margin-top: var(--spacing-2);
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    line-height: 1.55;
  }
}

.mode-switch {
  height: 48px;
  margin-top: var(--spacing-5);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-bg-base);
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;

  button {
    border: none;
    border-radius: 3px;
    background: transparent;
    color: var(--color-text-secondary);
    cursor: pointer;
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 800;
    transition: background-color 160ms ease, color 160ms ease;

    &.active {
      background: #e50914;
      color: #fff;
    }
  }
}

.auth-form {
  margin-top: var(--spacing-5);
  display: grid;
  gap: var(--spacing-4);
}

.field-group {
  display: grid;
  gap: 8px;

  > span:first-child {
    color: var(--color-text-secondary);
    font-family: var(--font-family-sans);
    font-size: 13px;
    font-weight: 700;
  }
}

.field-shell {
  min-height: 52px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: rgba(8, 8, 8, 0.62);
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: 0 14px;
  transition: border-color 160ms ease, box-shadow 160ms ease;

  &:focus-within {
    border-color: rgba(229, 9, 20, 0.72);
    box-shadow: none;
  }

  input {
    min-width: 0;
    width: 100%;
    border: none;
    outline: none;
    background: transparent;
    color: var(--color-text-primary);
    font-family: var(--font-family-sans);
    font-size: 16px;

    &::placeholder {
      color: var(--color-text-ghost);
    }
  }
}

.field-icon {
  width: 18px;
  height: 18px;
  color: var(--color-text-secondary);
  flex: 0 0 auto;
}

.password-toggle {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: grid;
  place-items: center;

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover {
    color: var(--color-accent);
  }
}

.error-msg {
  border: 1px solid rgba(224, 84, 84, 0.32);
  border-radius: var(--radius-md);
  background: rgba(224, 84, 84, 0.09);
  color: #ffb1a8;
  font-family: var(--font-family-sans);
  font-size: 13px;
  line-height: 1.45;
  padding: 10px 12px;
}

.submit-btn {
  min-height: 54px;
  border: none;
  border-radius: 4px;
  background: #e50914;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2);
  font-family: var(--font-family-sans);
  font-size: 16px;
  font-weight: 900;
  transition: background-color 160ms ease, transform 160ms ease, opacity 160ms ease;

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover:not(:disabled) {
    background: #f6121d;
  }

  &:active:not(:disabled) {
    transform: translateY(1px);
  }

  &:disabled {
    opacity: 0.58;
    cursor: not-allowed;
  }
}

.toggle-text {
  margin-top: var(--spacing-4);
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 14px;
  line-height: 1.5;

  button {
    border: none;
    background: transparent;
    color: var(--color-text-primary);
    cursor: pointer;
    font: inherit;
    font-weight: 800;
    padding: 0 0 0 4px;

    &:hover {
      color: var(--color-accent);
    }
  }
}

.field-slide-enter-active,
.field-slide-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.field-slide-enter-from,
.field-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .visual-panel {
    min-height: 440px;
  }

  .visual-copy {
    min-height: 440px;
  }

  .poster-stack {
    display: none;
  }
}

@media (max-width: 640px) {
  .login-topbar {
    height: 68px;
    padding: 0 var(--spacing-3);
  }

  .login-shell {
    width: min(100% - 24px, 1180px);
    padding-top: 0;
  }

  .visual-panel {
    min-height: 360px;
    border-radius: var(--radius-md);
  }

  .visual-copy {
    min-height: 360px;
    padding: var(--spacing-4);

    h1 {
      font-size: 36px;
    }
  }

  .auth-card {
    border-radius: var(--radius-md);
  }
}

.login-page {
  --ticket-red: #e50914;
  --ticket-red-hover: #f6121d;
  position: relative;
  min-height: 100dvh;
  overflow-x: hidden;
  overflow-y: auto;
  background: #0a0a0a;
}

.poster-backdrop,
.gradient-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.poster-backdrop {
  z-index: 0;
  display: grid;
  align-content: center;
  gap: 18px;
  opacity: 0.98;
  transform: rotate(-6deg) scale(1.12);
}

.poster-row {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 164px;
  gap: 18px;
  width: max-content;
  animation: poster-drift 48s linear infinite;

  &.reverse {
    animation-name: poster-drift-reverse;
    animation-duration: 56s;
  }

  img {
    width: 164px;
    aspect-ratio: 2 / 3;
    border-radius: 6px;
    object-fit: cover;
    opacity: 0.98;
    filter: saturate(1.1) contrast(1.02) brightness(1.1);
    box-shadow: 0 18px 44px rgba(0, 0, 0, 0.34);
  }
}

.gradient-overlay {
  z-index: 1;
  background:
    linear-gradient(90deg, rgba(0, 0, 0, 0.68) 0%, rgba(0, 0, 0, 0.42) 42%, rgba(0, 0, 0, 0.16) 100%),
    linear-gradient(180deg, rgba(0, 0, 0, 0.28) 0%, rgba(0, 0, 0, 0.04) 34%, rgba(5, 5, 5, 0.72) 100%);
}

.login-topbar,
.login-shell {
  position: relative;
  z-index: 2;
}

.login-topbar {
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.42), transparent);
}

.brand {
  color: var(--ticket-red);
  font-size: 32px;
  letter-spacing: 0.08em;
  text-shadow: none;

  span {
    color: inherit;
  }
}

.language-btn {
  border-color: rgba(255, 255, 255, 0.34);
  background: rgba(0, 0, 0, 0.46);
  color: #fff;
  border-radius: 4px;

  &:hover {
    border-color: #fff;
    color: #fff;
  }
}

.login-shell {
  width: min(1220px, calc(100% - 48px));
  min-height: calc(100dvh - 76px);
  grid-template-columns: minmax(0, 1fr) minmax(360px, 430px);
  gap: clamp(28px, 6vw, 84px);
  align-items: center;
  padding: 0 0 64px;
}

.login-hero-copy {
  max-width: 680px;
  animation: hero-copy-in 620ms ease-out both;

  .eyebrow {
    display: inline-flex;
    min-height: 32px;
    align-items: center;
    border-radius: 4px;
    border: 1px solid rgba(255, 255, 255, 0.18);
    background: rgba(0, 0, 0, 0.38);
    color: #fff;
    font-family: var(--font-family-sans);
    font-size: 12px;
    font-weight: 900;
    letter-spacing: 0.08em;
    padding: 0 10px;
    text-transform: uppercase;

    &::before {
      content: '';
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--ticket-red);
      margin-right: 8px;
    }
  }

  h1 {
    margin-top: 18px;
    color: #fff;
    font-size: clamp(44px, 7vw, 78px);
    line-height: 0.96;
    text-shadow: 0 18px 48px rgba(0, 0, 0, 0.68);
  }

  p {
    max-width: 560px;
    margin-top: 18px;
    color: rgba(255, 255, 255, 0.82);
    font-family: var(--font-family-sans);
    font-size: 19px;
    line-height: 1.55;
  }
}

.trust-row {
  margin-top: 24px;

  span {
    border-color: rgba(255, 255, 255, 0.22);
    border-radius: 4px;
    background: rgba(0, 0, 0, 0.34);
    color: rgba(255, 255, 255, 0.86);
  }
}

.auth-panel {
  animation: auth-panel-in 720ms 90ms ease-out both;
}

.auth-card {
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 4px;
  background: rgba(10, 10, 10, 0.66);
  box-shadow: 0 28px 80px rgba(0, 0, 0, 0.54);
}

.auth-heading {
  span {
    color: var(--color-accent);
  }

  h2 {
    color: #fff;
  }
}

.mode-switch {
  border-color: rgba(255, 255, 255, 0.14);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.06);

  button {
    border-radius: 3px;

    &.active {
      background: #e50914;
      color: #fff;
    }
  }
}

.field-shell {
  border-color: rgba(255, 255, 255, 0.18);
  border-radius: 4px;
  background: rgba(22, 22, 22, 0.9);

  &:focus-within {
    border-color: rgba(229, 9, 20, 0.78);
    box-shadow: none;
  }
}

.submit-btn {
  border-radius: 4px;
  background: #e50914;
  color: #fff;

  &:hover:not(:disabled) {
    background: #f6121d;
  }
}

.toggle-text button:hover,
.password-toggle:hover {
  color: var(--color-accent);
}

@keyframes poster-drift {
  from {
    transform: translateX(-18px);
  }

  to {
    transform: translateX(-1110px);
  }
}

@keyframes poster-drift-reverse {
  from {
    transform: translateX(-1100px);
  }

  to {
    transform: translateX(-8px);
  }
}

@keyframes hero-copy-in {
  from {
    opacity: 0;
    transform: translateY(22px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes auth-panel-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 980px) {
  .login-shell {
    min-height: auto;
    grid-template-columns: 1fr;
    padding: 28px 0 56px;
  }

  .login-hero-copy {
    max-width: 760px;
  }
}

@media (max-width: 640px) {
  .poster-backdrop {
    gap: 12px;
    transform: rotate(-6deg) scale(1.22);
  }

  .poster-row {
    grid-auto-columns: 112px;
    gap: 12px;

    img {
      width: 112px;
    }
  }

  .login-shell {
    width: min(100% - 24px, 1220px);
    padding: 12px 0 40px;
  }

  .login-hero-copy {
    h1 {
      font-size: 42px;
    }

    p {
      font-size: 16px;
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .poster-row,
  .login-hero-copy,
  .auth-panel {
    animation: none;
  }
}
</style>
