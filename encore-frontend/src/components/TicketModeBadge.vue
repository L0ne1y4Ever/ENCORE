<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  mode: 'SEATED' | 'ZONED' | 'MIXED' | string
}>()

const { t } = useI18n()

const badgeText = computed(() => {
  switch (props.mode?.toUpperCase()) {
    case 'SEATED':
      return t('ticketMode.seated')
    case 'ZONED':
      return t('ticketMode.zoned')
    case 'MIXED':
      return t('ticketMode.mixed')
    default:
      return props.mode
  }
})

const badgeClass = computed(() => {
  return `badge-${props.mode?.toLowerCase()}`
})
</script>

<template>
  <span :class="['ticket-mode-badge', badgeClass]">
    <span class="pulse-dot" v-if="props.mode === 'MIXED' || props.mode === 'ZONED'"></span>
    {{ badgeText }}
  </span>
</template>

<style scoped lang="scss">
.ticket-mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-family-sans);
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);

  &.badge-seated {
    color: var(--color-accent);
    background-color: rgba(200, 149, 90, 0.1);
    border: 1px solid rgba(200, 149, 90, 0.3);
  }

  &.badge-zoned {
    color: #4fc3f7;
    background-color: rgba(79, 195, 247, 0.1);
    border: 1px solid rgba(79, 195, 247, 0.3);
  }

  &.badge-mixed {
    color: #b388ff;
    background-color: rgba(179, 136, 255, 0.1);
    border: 1px solid rgba(179, 136, 255, 0.3);
  }

  .pulse-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: currentColor;
    animation: pulse 1.8s infinite ease-in-out;
  }
}

@keyframes pulse {
  0% {
    transform: scale(0.85);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
  100% {
    transform: scale(0.85);
    opacity: 0.5;
  }
}
</style>
