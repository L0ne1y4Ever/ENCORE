<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setAppLocale } from '../i18n'
import type { LocaleCode } from '../i18n'

const { t, locale } = useI18n()

const currentLocale = computed(() => locale.value as LocaleCode)

const options: Array<{ value: LocaleCode; label: string }> = [
  { value: 'en', label: 'EN' },
  { value: 'zh', label: '中' }
]

const changeLocale = (nextLocale: LocaleCode) => {
  if (nextLocale === currentLocale.value) return
  setAppLocale(nextLocale)
}
</script>

<template>
  <div class="language-switch" role="group" :aria-label="t('common.languageSwitch')">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="language-option"
      :class="{ active: currentLocale === option.value }"
      :aria-pressed="currentLocale === option.value"
      @click="changeLocale(option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped lang="scss">
.language-switch {
  display: inline-grid;
  grid-template-columns: repeat(2, 44px);
  min-height: 36px;
  padding: 3px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background-color: rgba(240, 237, 232, 0.03);
}

.language-option {
  min-width: 44px;
  min-height: 30px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
  transition: background-color 180ms ease, color 180ms ease;

  &:hover,
  &:focus-visible {
    color: var(--color-text-primary);
    outline: none;
  }

  &:focus-visible {
    box-shadow: 0 0 0 2px rgba(200, 149, 90, 0.45);
  }

  &.active {
    background-color: var(--color-text-primary);
    color: var(--color-bg-base);
    cursor: default;
  }
}

@media (prefers-reduced-motion: reduce) {
  .language-option {
    transition: none;
  }
}
</style>
