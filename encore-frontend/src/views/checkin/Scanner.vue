<script setup lang="ts">
import { computed, ref, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { checkInTicket, getCheckInSchedules } from '../../api/checkin'
import type { CheckInResponse, CheckInSchedule } from '../../api/checkin'

const { t, locale } = useI18n()
const SCHEDULE_STORAGE_KEY = 'encore.checkin.scheduleId'
const ticketCode = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

// 状态: 'idle' | 'success' | 'error'
const scanStatus = ref<'idle' | 'success' | 'error'>('idle')
const errorMessage = ref('')
const loading = ref(false)
const result = ref<CheckInResponse | null>(null)
const schedules = ref<CheckInSchedule[]>([])
const selectedScheduleId = ref('')
const scheduleLoading = ref(false)
const scheduleError = ref('')

const selectedSchedule = computed(() => {
  return schedules.value.find((schedule) => schedule.id === selectedScheduleId.value) || null
})

const scheduleGroups = computed(() => {
  const groups = new Map<string, CheckInSchedule[]>()
  const ordered = [...schedules.value].sort((left, right) => {
    if (left.checkInOpen !== right.checkInOpen) {
      return left.checkInOpen ? -1 : 1
    }
    const leftTime = left.startTime || ''
    const rightTime = right.startTime || ''
    return leftTime.localeCompare(rightTime)
  })
  for (const schedule of ordered) {
    const key = schedule.category || t('admin.uncategorized')
    const list = groups.get(key) || []
    list.push(schedule)
    groups.set(key, list)
  }
  return Array.from(groups.entries()).map(([category, rows]) => ({ category, rows }))
})

const canScan = computed(() => {
  return Boolean(ticketCode.value.trim()) &&
    Boolean(selectedScheduleId.value) &&
    schedules.value.length > 0 &&
    !loading.value &&
    !scheduleLoading.value
})

const loadSchedules = async () => {
  scheduleLoading.value = true
  scheduleError.value = ''
  try {
    const data = await getCheckInSchedules()
    schedules.value = data
    const savedScheduleId = localStorage.getItem(SCHEDULE_STORAGE_KEY)
    const savedSchedule = savedScheduleId
      ? data.find((schedule) => schedule.id === savedScheduleId)
      : null
    const defaultSchedule = savedSchedule || data.find((schedule) => schedule.checkInOpen) || data[0] || null
    selectedScheduleId.value = defaultSchedule?.id || ''
    persistScheduleSelection()
  } catch (error) {
    scheduleError.value = error instanceof Error ? error.message : t('checkin.scheduleLoadFailed')
  } finally {
    scheduleLoading.value = false
    nextTick(() => {
      inputRef.value?.focus()
    })
  }
}

const persistScheduleSelection = () => {
  if (selectedScheduleId.value) {
    localStorage.setItem(SCHEDULE_STORAGE_KEY, selectedScheduleId.value)
  } else {
    localStorage.removeItem(SCHEDULE_STORAGE_KEY)
  }
}

const formatDateTime = (value: string | null) => {
  if (!value) return '--'
  return new Intl.DateTimeFormat(locale.value === 'zh' ? 'zh-CN' : 'en-US', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

const formatScheduleRange = (schedule: CheckInSchedule) => {
  return `${formatDateTime(schedule.startTime)} - ${formatDateTime(schedule.endTime)}`
}

const handleScan = async () => {
  const code = ticketCode.value.trim()
  if (!code || !canScan.value) return

  loading.value = true
  errorMessage.value = ''
  result.value = null
  try {
    result.value = await checkInTicket(code, selectedScheduleId.value)
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

onMounted(() => {
  loadSchedules()
})
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

    <section class="schedule-panel" v-if="scanStatus === 'idle'">
      <label for="checkin-schedule">{{ t('checkin.currentSchedule') }}</label>
      <div class="schedule-controls">
        <select
          id="checkin-schedule"
          v-model="selectedScheduleId"
          :disabled="scheduleLoading || schedules.length === 0"
          @change="persistScheduleSelection"
        >
          <option value="" disabled>{{ t('checkin.selectSchedule') }}</option>
          <optgroup v-for="group in scheduleGroups" :key="group.category" :label="group.category">
            <option v-for="schedule in group.rows" :key="schedule.id" :value="schedule.id">
              {{ schedule.checkInOpen ? '●' : '○' }} {{ schedule.showTitle }} · {{ schedule.theaterName }} · {{ formatDateTime(schedule.startTime) }}
            </option>
          </optgroup>
        </select>
        <button
          class="reload-btn"
          type="button"
          :disabled="scheduleLoading"
          @click="loadSchedules"
        >
          {{ t('checkin.reloadSchedules') }}
        </button>
      </div>
      <div v-if="scheduleLoading" class="schedule-note">
        {{ t('checkin.loadingSchedules') }}
      </div>
      <div v-else-if="scheduleError" class="schedule-note error">
        {{ scheduleError }}
      </div>
      <div v-else-if="selectedSchedule" class="schedule-meta">
        <span class="schedule-state" :class="{ open: selectedSchedule.checkInOpen }">
          {{ selectedSchedule.checkInOpen ? t('checkin.scheduleOpen') : t('checkin.scheduleNotOpen') }}
        </span>
        <span>{{ selectedSchedule.status }}</span>
        <span>{{ formatScheduleRange(selectedSchedule) }}</span>
      </div>
      <div v-else class="schedule-note">
        {{ t('checkin.noSchedules') }}
      </div>
    </section>

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
        <button class="scan-btn" type="button" :disabled="!canScan" @click="handleScan">
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

.schedule-panel {
  position: absolute;
  top: var(--spacing-6);
  left: 50%;
  width: min(560px, calc(100vw - 240px));
  min-width: 360px;
  transform: translateX(-50%);
  padding: var(--spacing-3);
  border: 1px solid rgba(255, 255, 255, 0.24);
  background: rgba(0, 0, 0, 0.72);
  font-family: var(--font-family-sans);
  color: #FFFFFF;

  label {
    display: block;
    margin-bottom: var(--spacing-2);
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: rgba(255, 255, 255, 0.62);
  }
}

.schedule-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--spacing-2);

  select,
  button {
    min-height: 38px;
    border: 1px solid rgba(255, 255, 255, 0.44);
    border-radius: 0;
    font-family: var(--font-family-sans);
    font-size: 13px;
  }

  select {
    width: 100%;
    min-width: 0;
    padding: 0 var(--spacing-2);
    background: #070707;
    color: #FFFFFF;
  }

  button {
    padding: 0 var(--spacing-3);
    background: #FFFFFF;
    color: #000000;
    font-weight: 700;
    cursor: pointer;

    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }
}

.schedule-meta,
.schedule-note {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  margin-top: var(--spacing-2);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.schedule-note.error {
  color: var(--color-error);
}

.schedule-state {
  font-weight: 700;
  color: var(--color-warning);

  &.open {
    color: var(--color-success);
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

@media (max-width: 760px) {
  .schedule-panel {
    top: calc(var(--spacing-6) + 36px);
    width: calc(100vw - 32px);
    min-width: 0;
  }

  .schedule-controls {
    grid-template-columns: 1fr;
  }
}
</style>
