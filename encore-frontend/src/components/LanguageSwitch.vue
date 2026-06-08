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
  grid-template-columns: repeat(2, 36px);
  min-height: 34px;
  padding: 0 0 0 8px;
  border-left: 1px solid rgba(255, 255, 255, 0.12);
  background: transparent;
  column-gap: 2px;
}

.language-option {
  position: relative;
  min-width: 36px;
  min-height: 34px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-family-sans);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
  transition: background-color 180ms ease, color 180ms ease;

  &::after {
    content: '';
    position: absolute;
    left: 9px;
    right: 9px;
    bottom: 2px;
    height: 2px;
    border-radius: 2px;
    background: #e50914;
    opacity: 0;
    transform: scaleX(0.65);
    transition: opacity 180ms ease, transform 180ms ease;
  }

  &:hover,
  &:focus-visible {
    color: var(--color-text-primary);
    background: rgba(255, 255, 255, 0.055);
    outline: none;
  }

  &:focus-visible {
    box-shadow: 0 0 0 2px rgba(229, 9, 20, 0.28);
  }

  &.active {
    background: rgba(255, 255, 255, 0.055);
    color: var(--color-text-primary);
    cursor: default;

    &::after {
      opacity: 1;
      transform: scaleX(1);
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .language-option {
    transition: none;
  }
}
</style>
