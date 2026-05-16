<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { checkInTicket } from '../../api/checkin'
import type { CheckInResponse } from '../../api/checkin'

const { t } = useI18n()
const ticketCode = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

// 状态: 'idle' | 'success' | 'error'
const scanStatus = ref<'idle' | 'success' | 'error'>('idle')
const errorMessage = ref('')
const loading = ref(false)
const result = ref<CheckInResponse | null>(null)

const handleScan = async () => {
  const code = ticketCode.value.trim()
  if (!code || loading.value) return

  loading.value = true
  errorMessage.value = ''
  result.value = null
  try {
    result.value = await checkInTicket(code)
    scanStatus.value = 'success'
    setTimeout(() => {
      resetScanner()
    }, 2600)
  } catch (error) {
    scanStatus.value = 'error'
    errorMessage.value = error instanceof Error ? error.message : t('checkin.invalid')
    setTimeout(() => {
      resetScanner()
    }, 2400)
  } finally {
    loading.value = false
  }
}

const resetScanner = () => {
  scanStatus.value = 'idle'
  ticketCode.value = ''
  errorMessage.value = ''
  result.value = null
  nextTick(() => {
    inputRef.value?.focus()
  })
}

// 模拟离线状态
const isOffline = ref(false)
</script>

<template>
  <div 
    class="scanner-container" 
    :class="{ 
      'status-success': scanStatus === 'success',
      'status-error': scanStatus === 'error'
    }"
  >
    <div class="network-badge" :class="{ offline: isOffline }" @click="isOffline = !isOffline">
      {{ isOffline ? t('checkin.offline') : t('checkin.online') }}
    </div>

    <div class="main-content">
      <div class="status-icon" v-if="scanStatus !== 'idle'" aria-live="polite">
        <span v-if="scanStatus === 'success'">✓</span>
        <span v-if="scanStatus === 'error'">✕</span>
      </div>

      <div class="input-area" v-if="scanStatus === 'idle'">
        <label>{{ t('checkin.scanLabel') }}</label>
        <input 
          ref="inputRef"
          v-model="ticketCode" 
          @keyup.enter="handleScan"
          type="text" 
          autofocus 
          :placeholder="t('checkin.placeholder')"
          :disabled="loading"
        />
        <button class="scan-btn" type="button" :disabled="loading || !ticketCode.trim()" @click="handleScan">
          {{ loading ? t('common.processing') : t('checkin.verify') }}
        </button>
      </div>

      <div class="success-card" v-if="scanStatus === 'success' && result">
        <strong>{{ t('checkin.success') }}</strong>
        <span>{{ result.showTitle }}</span>
        <span>{{ result.theaterName }} · {{ result.seatId }}</span>
      </div>
      
      <div class="error-msg" v-if="scanStatus === 'error'">
        {{ errorMessage }}
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.scanner-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #000000;
  color: #FFFFFF;
  transition: background-color 150ms ease;

  &.status-success {
    background-color: var(--color-success);
  }

  &.status-error {
    background-color: var(--color-error);
    animation: shake 0.4s cubic-bezier(.36,.07,.19,.97) both;
  }
}

@keyframes shake {
  10%, 90% { transform: translate3d(-2px, 0, 0); }
  20%, 80% { transform: translate3d(4px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-8px, 0, 0); }
  40%, 60% { transform: translate3d(8px, 0, 0); }
}

.network-badge {
  position: absolute;
  top: var(--spacing-6);
  right: var(--spacing-6);
  font-family: monospace;
  font-size: 12px;
  padding: 4px 8px;
  border: 1px solid #FFFFFF;
  color: #FFFFFF;
  cursor: pointer;

  &.offline {
    color: var(--color-warning);
    border-color: var(--color-warning);
  }
}

.main-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 600px;
  padding: var(--spacing-6);
}

.status-icon {
  font-size: 40vh;
  line-height: 1;
  font-weight: 300;
}

.input-area {
  width: 100%;
  text-align: center;

  label {
    display: block;
    font-family: var(--font-family-sans);
    font-size: 16px;
    letter-spacing: 0.2em;
    color: var(--color-text-secondary);
    margin-bottom: var(--spacing-4);
  }

  input {
    width: 100%;
    background: transparent;
    border: none;
    border-bottom: 4px solid #FFFFFF;
    color: #FFFFFF;
    font-size: 64px;
    font-family: monospace;
    text-align: center;
    padding: var(--spacing-2) 0;
    outline: none;

    &::placeholder {
      color: rgba(255, 255, 255, 0.2);
    }

    &:disabled {
      opacity: 0.6;
      cursor: wait;
    }
  }

  .scan-btn {
    min-width: 160px;
    min-height: 44px;
    margin-top: var(--spacing-4);
    border: 1px solid #FFFFFF;
    background: #FFFFFF;
    color: #000000;
    font-family: var(--font-family-sans);
    font-size: 14px;
    font-weight: 700;
    letter-spacing: 0;
    cursor: pointer;
    transition: opacity 150ms ease, transform 150ms ease;

    &:hover:not(:disabled),
    &:focus-visible {
      transform: translateY(-1px);
    }

    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }
}

.success-card {
  display: grid;
  gap: var(--spacing-1);
  min-width: min(520px, calc(100vw - 48px));
  padding: var(--spacing-4);
  border: 1px solid rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.16);
  font-family: var(--font-family-sans);
  text-align: center;

  strong {
    font-size: 24px;
  }

  span {
    font-size: 16px;
  }
}

.error-msg {
  margin-top: var(--spacing-6);
  font-family: var(--font-family-sans);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-align: center;
}

@media (prefers-reduced-motion: reduce) {
  .scanner-container,
  .scan-btn {
    transition: none;
  }

  .scanner-container.status-error {
    animation: none;
  }
}
</style>
