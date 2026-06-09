<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useDocumentVisibility, useMediaQuery, usePreferredReducedMotion } from '@vueuse/core'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Hide, Lock, User, View } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import { setAppLocale } from '../../i18n'
import type { LocaleCode } from '../../i18n'
import type { UserRole } from '../../api/auth'
import { validateDisplayName, validatePassword, validateUsername } from '../../utils/credentialPolicy'
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
const preferredReducedMotion = usePreferredReducedMotion()
const documentVisibility = useDocumentVisibility()
const isCompactPosterViewport = useMediaQuery('(max-width: 640px)')
const prefersReducedMotion = computed(() => preferredReducedMotion.value === 'reduce')
const authRevealed = ref(prefersReducedMotion.value)
const usernameInputRef = ref<HTMLInputElement | null>(null)

const credentialMessages = computed(() => ({
  usernameRule: t('login.usernameRule'),
  usernameReserved: t('login.usernameReserved'),
  passwordLength: t('login.passwordLength'),
  passwordWhitespace: t('login.passwordWhitespace'),
  passwordLettersNumbers: t('login.passwordLettersNumbers'),
  passwordContainsUsername: t('login.passwordContainsUsername'),
  passwordCommon: t('login.passwordCommon'),
  displayNameRule: t('login.displayNameRule'),
  displayNameControl: t('login.displayNameControl')
}))

const usernameError = computed(() => (
  isRegister.value ? validateUsername(username.value, credentialMessages.value) : ''
))
const passwordError = computed(() => (
  isRegister.value ? validatePassword(password.value, username.value, credentialMessages.value) : ''
))
const nicknameError = computed(() => (
  isRegister.value ? validateDisplayName(nickname.value, credentialMessages.value) : ''
))
const passwordChecks = computed(() => {
  const value = password.value
  const normalizedUsername = username.value.trim().toLowerCase()
  return [
    { label: t('login.passwordCheckLength'), valid: value.length >= 8 && value.length <= 64 },
    { label: t('login.passwordCheckLettersNumbers'), valid: /[A-Za-z]/.test(value) && /\d/.test(value) },
    { label: t('login.passwordCheckNoSpace'), valid: value.length > 0 && !/\s/.test(value) },
    {
      label: t('login.passwordCheckNotUsername'),
      valid: !!normalizedUsername && !value.toLowerCase().includes(normalizedUsername)
    }
  ]
})

const posterImages = [
  'https://images.unsplash.com/photo-1507676184212-d0330a157088?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1540039155732-684736dd61dc?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1485846234645-a62644f84728?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1524712245354-2c4e5e7121c0?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?q=76&w=420&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1514533212735-5df27d970db0?q=76&w=420&auto=format&fit=crop'
]

const repeatPosterTrack = (base: string[], repeat = 3) => (
  Array.from({ length: repeat }, () => base).flat()
)
const extendPosterTrack = (base: string[], offset = 0) => {
  const shifted = [...base.slice(offset), ...base.slice(0, offset)]
  return [...shifted, ...shifted.slice(0, 6)]
}

const posterRows = [
  repeatPosterTrack(extendPosterTrack(posterImages, 0)),
  repeatPosterTrack(extendPosterTrack(posterImages, 6)),
  repeatPosterTrack(extendPosterTrack(posterImages, 3))
]
const visiblePosterRows = computed(() => (
  isCompactPosterViewport.value ? posterRows.slice(0, 2) : posterRows
))
const shouldAnimatePosters = computed(() => (
  documentVisibility.value === 'visible' && !prefersReducedMotion.value
))

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

const revealAuth = async () => {
  authRevealed.value = true
  await nextTick()
  window.setTimeout(() => usernameInputRef.value?.focus(), 280)
}

const handleSubmit = async () => {
  errorMsg.value = ''
  loading.value = true

  try {
    if (isRegister.value) {
      const validationError = usernameError.value || passwordError.value || nicknameError.value
      if (validationError) {
        errorMsg.value = validationError
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
    <div class="poster-backdrop" :class="{ paused: !shouldAnimatePosters }" aria-hidden="true">
      <div
        v-for="(row, rowIndex) in visiblePosterRows"
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
          decoding="async"
          fetchpriority="low"
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

      <section class="auth-panel" :class="{ revealed: authRevealed, registering: isRegister }">
        <div class="auth-card">
          <button
            class="auth-reveal-btn"
            :class="{ hidden: authRevealed }"
            type="button"
            :aria-hidden="authRevealed"
            :tabindex="authRevealed ? -1 : 0"
            @click="revealAuth"
          >
            <span class="auth-reveal-text">{{ t('common.getStarted') }}</span>
          </button>

          <div class="auth-card-content" :inert="!authRevealed" :aria-hidden="!authRevealed">
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
                  ref="usernameInputRef"
                  v-model="username"
                  type="text"
                  autocomplete="username"
                  maxlength="20"
                  required
                  :disabled="!authRevealed"
                  :tabindex="authRevealed ? 0 : -1"
                  :placeholder="t('login.usernamePlaceholder')"
                />
              </span>
              <small
                class="field-hint reveal-line"
                :class="{ active: isRegister, invalid: usernameError }"
                :aria-hidden="!isRegister"
              >
                {{ usernameError || t('login.usernameHint') }}
              </small>
            </label>

            <div class="register-field" :class="{ active: isRegister }" :aria-hidden="!isRegister">
              <label class="field-group">
                <span>{{ t('common.nickname') }}</span>
                <span class="field-shell">
                  <User class="field-icon" />
                  <input
                    v-model="nickname"
                    type="text"
                    autocomplete="nickname"
                    maxlength="32"
                    :disabled="!authRevealed || !isRegister"
                    :required="isRegister"
                    :tabindex="authRevealed && isRegister ? 0 : -1"
                    :placeholder="t('login.nicknamePlaceholder')"
                  />
                </span>
                <small class="field-hint" :class="{ invalid: nicknameError }">
                  {{ nicknameError || t('login.nicknameHint') }}
                </small>
              </label>
            </div>

            <label class="field-group">
              <span>{{ t('common.password') }}</span>
              <span class="field-shell password-shell">
                <Lock class="field-icon" />
                <input
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  :autocomplete="isRegister ? 'new-password' : 'current-password'"
                  maxlength="64"
                  required
                  :disabled="!authRevealed"
                  :tabindex="authRevealed ? 0 : -1"
                  :placeholder="t('login.passwordPlaceholder')"
                />
                <button
                  class="password-toggle"
                  type="button"
                  :disabled="!authRevealed"
                  :tabindex="authRevealed ? 0 : -1"
                  :aria-label="showPassword ? t('login.hidePassword') : t('login.showPassword')"
                  @click="showPassword = !showPassword"
                >
                  <Hide v-if="showPassword" />
                  <View v-else />
                </button>
              </span>
              <div
                class="password-rules"
                :class="{ active: isRegister }"
                :aria-hidden="!isRegister"
                aria-live="polite"
              >
                <span
                  v-for="rule in passwordChecks"
                  :key="rule.label"
                  :class="{ passed: rule.valid }"
                >
                  {{ rule.label }}
                </span>
              </div>
            </label>

            <p v-if="errorMsg" class="error-msg" role="alert">{{ errorMsg }}</p>

            <button class="submit-btn" type="submit" :disabled="loading || !authRevealed">
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

.field-hint {
  display: block;
  color: rgba(255, 255, 255, 0.5);
  font-family: var(--font-family-sans);
  font-size: 12px;
  line-height: 1.45;
  transition: color 160ms ease, opacity 190ms ease, transform 190ms ease;

  &.invalid {
    color: #ffb1a8;
  }
}

.reveal-line {
  height: 0;
  opacity: 0;
  overflow: hidden;
  transform: translateY(-4px);

  &.active {
    height: auto;
    opacity: 1;
    overflow: visible;
    transform: translateY(0);
  }
}

.register-field {
  display: grid;
  grid-template-rows: 0fr;
  opacity: 0;
  transform: translateY(-6px);
  transition: grid-template-rows 220ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 180ms ease,
    transform 220ms cubic-bezier(0.22, 1, 0.36, 1);

  > .field-group {
    min-height: 0;
    overflow: hidden;
  }

  &.active {
    grid-template-rows: 1fr;
    opacity: 1;
    transform: translateY(0);
  }
}

.password-rules {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 12px;
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  transform: translateY(-4px);
  transition: max-height 220ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 180ms ease,
    transform 220ms cubic-bezier(0.22, 1, 0.36, 1);

  &.active {
    max-height: 64px;
    opacity: 1;
    overflow: visible;
    transform: translateY(0);
  }

  span {
    color: rgba(255, 255, 255, 0.48);
    font-family: var(--font-family-sans);
    font-size: 12px;
    line-height: 1.35;

    &::before {
      content: '';
      display: inline-block;
      width: 6px;
      height: 6px;
      margin-right: 7px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.24);
      vertical-align: 1px;
    }

    &.passed {
      color: rgba(255, 255, 255, 0.76);

      &::before {
        background: #e50914;
      }
    }
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

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: 1fr;
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

  .auth-card {
    border-radius: var(--radius-md);
  }

  .password-rules {
    grid-template-columns: 1fr;
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
  gap: 16px;
  overflow: hidden;
  opacity: 0.92;
  transform: rotate(-5deg) scale(1.08);
  transform-origin: center;
}

.poster-row {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 164px;
  gap: 16px;
  width: max-content;
  animation: poster-drift 162s linear infinite;
  backface-visibility: hidden;
  contain: paint;
  will-change: transform;

  &:nth-child(1) {
    margin-left: -84px;
  }

  &:nth-child(2) {
    margin-left: 42px;
  }

  &:nth-child(3) {
    margin-left: -144px;
    opacity: 0.88;
  }

  &.reverse {
    animation-name: poster-drift-reverse;
    animation-duration: 186s;
  }

  img {
    width: 164px;
    aspect-ratio: 2 / 3;
    border-radius: 6px;
    object-fit: cover;
    opacity: 0.94;
    filter: saturate(1.04) brightness(1.03);
    box-shadow: 0 10px 22px rgba(0, 0, 0, 0.24);
  }
}

.poster-backdrop.paused .poster-row {
  animation-play-state: paused;
}

.gradient-overlay {
  z-index: 1;
  background:
    linear-gradient(90deg, rgba(0, 0, 0, 0.96) 0%, rgba(0, 0, 0, 0.78) 9%, rgba(0, 0, 0, 0.38) 24%, rgba(0, 0, 0, 0) 43%),
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
  grid-template-columns: minmax(0, 1fr) minmax(380px, 468px);
  gap: clamp(28px, 5vw, 72px);
  align-items: center;
  padding: 18px 0 54px;
}

.login-hero-copy {
  max-width: 760px;
  animation: hero-copy-in 620ms ease-out both;
  translate: 0 -34px;

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
    font-size: clamp(58px, 7.4vw, 92px);
    line-height: 0.94;
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
  --auth-card-login-height: 590px;
  --auth-card-register-height: 760px;
  --auth-card-max-height: 920px;
  --auth-card-active-height: var(--auth-card-login-height);
  --auth-card-width: min(100%, 468px);
  --auth-reveal-width: min(260px, calc(100vw - 48px));
  --auth-reveal-height: 58px;
  --auth-reveal-ease: cubic-bezier(0.16, 1, 0.3, 1);
  animation: auth-panel-in 720ms 90ms ease-out both;
  align-self: center;
  width: 100%;
  min-height: min(calc(var(--auth-card-active-height) + 72px), calc(100dvh - 96px));
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  isolation: isolate;
}

.auth-panel.registering {
  --auth-card-active-height: var(--auth-card-register-height);
}

.auth-reveal-btn {
  position: absolute;
  inset: 0;
  z-index: 5;
  width: 100%;
  min-height: 100%;
  border: none;
  border-radius: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.92);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-family-sans);
  font-size: clamp(21px, 2.3vw, 23px);
  font-weight: 900;
  letter-spacing: 0.07em;
  line-height: 1;
  padding: 0;
  text-transform: uppercase;
  transition: color 140ms ease, letter-spacing 160ms ease, opacity 190ms ease;
  white-space: nowrap;

  &:hover {
    color: #fff;
    letter-spacing: 0.09em;
  }

  &:focus-visible {
    outline: 2px solid rgba(255, 255, 255, 0.9);
    outline-offset: 4px;
  }

  &.hidden {
    opacity: 0;
    pointer-events: none;
  }
}

.auth-reveal-text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 1em;
  line-height: 1;
  max-width: calc(100% - 24px);
  overflow: hidden;
  padding: 0 8px;
  position: relative;
  text-align: center;
  text-shadow:
    0 1px 0 rgba(0, 0, 0, 0.9),
    0 -1px 0 rgba(0, 0, 0, 0.9),
    1px 0 0 rgba(0, 0, 0, 0.9),
    -1px 0 0 rgba(0, 0, 0, 0.9),
    0 0 18px rgba(0, 0, 0, 0.86);
  transform: translateY(0.5px);
  white-space: nowrap;
  z-index: 1;
}

.auth-card {
  width: var(--auth-reveal-width);
  min-height: var(--auth-reveal-height);
  max-height: var(--auth-reveal-height);
  border: 1.5px solid rgba(255, 255, 255, 0.9);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(0) saturate(1);
  -webkit-backdrop-filter: blur(0) saturate(1);
  box-shadow: none;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  overscroll-behavior: contain;
  padding: 0;
  position: relative;
  z-index: 4;
  contain: layout paint;
  opacity: 1;
  pointer-events: auto;
  transform: translate3d(0, 0, 0) scale(0.995);
  transform-origin: center;
  transition: width 640ms var(--auth-reveal-ease),
    max-height 640ms var(--auth-reveal-ease),
    min-height 640ms var(--auth-reveal-ease),
    padding 640ms var(--auth-reveal-ease),
    transform 640ms var(--auth-reveal-ease),
    backdrop-filter 420ms 100ms ease,
    -webkit-backdrop-filter 420ms 100ms ease,
    background-color 420ms 90ms ease,
    box-shadow 420ms 120ms ease,
    border-color 260ms 180ms ease;
  will-change: width, height, transform;
}

.auth-card-content {
  width: 100%;
  min-height: 0;
  max-height: 0;
  overflow: hidden;
  overscroll-behavior: contain;
  opacity: 0;
  pointer-events: none;
  transform: translate3d(0, 14px, 0);
  transition: max-height 0ms 260ms linear,
    opacity 300ms 260ms ease,
    transform 420ms 240ms var(--auth-reveal-ease);
}

.auth-panel.revealed {
  .auth-card {
    width: var(--auth-card-width);
    min-height: var(--auth-card-active-height);
    max-height: var(--auth-card-max-height);
    border-color: rgba(255, 255, 255, 0.18);
    background: rgba(8, 8, 8, 0.84);
    backdrop-filter: blur(16px) saturate(1.05);
    -webkit-backdrop-filter: blur(16px) saturate(1.05);
    box-shadow: 0 28px 80px rgba(0, 0, 0, 0.54);
    padding: 34px clamp(26px, 3vw, 42px) 28px;
    transform: translate3d(0, 0, 0) scale(1);

    .auth-card-content {
      max-height: none;
      overflow: visible;
      opacity: 1;
      pointer-events: auto;
      transform: translate3d(0, 0, 0);
      transition: max-height 0ms linear,
        opacity 300ms 260ms ease,
        transform 420ms 240ms var(--auth-reveal-ease);
    }
  }
}

.auth-heading {
  span {
    color: var(--color-accent);
    font-size: 12px;
    letter-spacing: 0.08em;
  }

  h2 {
    color: #fff;
    margin-top: 12px;
    font-size: clamp(34px, 3vw, 40px);
    line-height: 1.06;
  }

  p {
    margin-top: 10px;
    font-size: 16px;
    line-height: 1.45;
  }
}

.mode-switch {
  height: 46px;
  margin-top: 30px;
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

.auth-form {
  margin-top: 28px;
  gap: 18px;
}

.field-group {
  gap: 7px;

  > span:first-child {
    font-size: 14px;
  }
}

.field-hint {
  font-size: 12px;
  line-height: 1.32;
}

.password-rules {
  gap: 6px 14px;

  &.active {
    max-height: 48px;
  }

  span {
    font-size: 12px;
    line-height: 1.25;
  }
}

.field-shell {
  min-height: 48px;
  border-color: rgba(255, 255, 255, 0.18);
  border-radius: 4px;
  background: rgba(22, 22, 22, 0.9);
  padding: 0 14px;

  &:focus-within {
    border-color: rgba(229, 9, 20, 0.78);
    box-shadow: none;
  }
}

.submit-btn {
  min-height: 50px;
  margin-top: 2px;
  border-radius: 4px;
  background: #e50914;
  color: #fff;

  &:hover:not(:disabled) {
    background: #f6121d;
  }
}

.toggle-text {
  margin-top: 18px;
}

.toggle-text button:hover,
.password-toggle:hover {
  color: var(--color-accent);
}

@keyframes poster-drift {
  from {
    transform: translate3d(0, 0, 0);
  }

  to {
    transform: translate3d(-3240px, 0, 0);
  }
}

@keyframes poster-drift-reverse {
  from {
    transform: translate3d(-3240px, 0, 0);
  }

  to {
    transform: translate3d(0, 0, 0);
  }
}

@keyframes poster-drift-mobile {
  from {
    transform: translate3d(0, 0, 0);
  }

  to {
    transform: translate3d(-2268px, 0, 0);
  }
}

@keyframes poster-drift-mobile-reverse {
  from {
    transform: translate3d(-2268px, 0, 0);
  }

  to {
    transform: translate3d(0, 0, 0);
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
    width: min(100% - 32px, 760px);
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 34px;
    padding: 24px 0 48px;
  }

  .login-hero-copy {
    max-width: 760px;
    text-align: left;
    translate: 0 0;

    h1 {
      max-width: 620px;
      font-size: clamp(42px, 9vw, 64px);
    }
  }

  .auth-panel {
    --auth-card-login-height: 590px;
    --auth-card-register-height: 760px;
    --auth-card-width: min(100%, 560px);
    min-height: 0;
    align-self: auto;
    padding: 0;
  }

  .auth-card {
    min-height: var(--auth-reveal-height);
    max-height: var(--auth-reveal-height);
  }

  .auth-panel.revealed .auth-card {
    min-height: var(--auth-card-active-height);
    max-height: var(--auth-card-max-height);
  }
}

@media (max-width: 640px) {
  .poster-backdrop {
    gap: 10px;
    opacity: 0.86;
    transform: rotate(-4deg) scale(1.12);
  }

  .poster-row {
    grid-auto-columns: 116px;
    gap: 10px;
    animation-name: poster-drift-mobile;
    animation-duration: 144s;

    &:nth-child(1),
    &:nth-child(2) {
      margin-left: -50px;
    }

    &.reverse {
      animation-name: poster-drift-mobile-reverse;
      animation-duration: 168s;
    }

    img {
      width: 116px;
      box-shadow: 0 8px 16px rgba(0, 0, 0, 0.22);
    }
  }

  .login-shell {
    width: min(100% - 24px, 1220px);
    gap: 28px;
    padding: 10px 0 34px;
  }

  .auth-panel {
    --auth-card-login-height: 560px;
    --auth-card-register-height: 760px;
    --auth-card-max-height: 980px;
    --auth-card-width: min(100%, 420px);
    padding-top: 0;
  }

  .auth-reveal-btn {
    font-size: 18px;
    letter-spacing: 0.06em;
    max-width: calc(100vw - 24px);
  }

  .auth-panel.revealed .auth-card {
    padding: 30px 22px 24px;
    background: rgba(8, 8, 8, 0.9);
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

@media (max-height: 760px) and (min-width: 981px) {
  .login-shell {
    min-height: auto;
    padding: 8px 0 34px;
  }

  .auth-panel {
    --auth-card-login-height: 560px;
    --auth-card-register-height: 700px;
    --auth-card-max-height: 900px;
    min-height: calc(var(--auth-card-active-height) + 24px);
  }

  .auth-panel.revealed .auth-card {
    padding-top: 28px;
    padding-bottom: 24px;
  }

  .login-hero-copy h1 {
    font-size: clamp(42px, 5.6vw, 66px);
  }
}

@media (max-width: 430px) {
  .login-hero-copy {
    .eyebrow {
      min-height: 28px;
      font-size: 11px;
    }

    h1 {
      font-size: 36px;
    }

    p {
      font-size: 15px;
    }
  }

  .trust-row {
    gap: 8px;
  }

  .auth-heading h2 {
    font-size: 31px;
  }

  .mode-switch {
    margin-top: 24px;
  }

  .auth-form {
    margin-top: 24px;
    gap: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .poster-row,
  .login-hero-copy,
  .auth-panel {
    animation: none;
  }

  .register-field,
  .password-rules,
  .field-hint,
  .auth-card,
  .auth-reveal-btn {
    transition: none;
  }

  .auth-card {
    width: var(--auth-card-width);
    min-height: var(--auth-card-active-height);
    max-height: var(--auth-card-max-height);
    transform: none;
    border-color: rgba(255, 255, 255, 0.18);
    background: rgba(8, 8, 8, 0.84);
    backdrop-filter: blur(16px) saturate(1.05);
    -webkit-backdrop-filter: blur(16px) saturate(1.05);
    padding: 34px clamp(26px, 3vw, 42px) 28px;

    .auth-card-content {
      max-height: none;
      overflow: visible;
      opacity: 1;
      pointer-events: auto;
      transform: none;
    }
  }
}
</style>
