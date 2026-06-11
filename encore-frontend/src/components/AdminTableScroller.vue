<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{
  label?: string
}>(), {
  label: 'Table horizontal scroll'
})

const rootRef = ref<HTMLElement | null>(null)
const topScrollRef = ref<HTMLElement | null>(null)
const bodyRef = ref<HTMLElement | null>(null)
const spacerWidth = ref(0)
const hasOverflow = ref(false)
const maxScroll = ref(0)
const scrollLeft = ref(0)

let activeScrollEl: HTMLElement | null = null
let resizeObserver: ResizeObserver | null = null
let mutationObserver: MutationObserver | null = null
let rafId = 0
let syncing = false

const findInnerScrollEl = () => {
  const root = rootRef.value
  if (!root) return null
  const selectors = [
    '.el-table__body-wrapper .el-scrollbar__wrap',
    '.el-table__inner-wrapper .el-scrollbar__wrap',
    '.el-table__body-wrapper'
  ]
  for (const selector of selectors) {
    const candidate = root.querySelector<HTMLElement>(selector)
    if (candidate && candidate.scrollWidth - candidate.clientWidth > 1) {
      return candidate
    }
  }
  return null
}

const setActiveScrollEl = (element: HTMLElement | null) => {
  if (activeScrollEl === element) return
  activeScrollEl?.removeEventListener('scroll', handleTableScroll)
  activeScrollEl = element
  activeScrollEl?.addEventListener('scroll', handleTableScroll, { passive: true })
}

function handleTableScroll() {
  if (!activeScrollEl || syncing) return
  syncing = true
  scrollLeft.value = activeScrollEl.scrollLeft
  if (topScrollRef.value) {
    topScrollRef.value.scrollLeft = activeScrollEl.scrollLeft
  }
  requestAnimationFrame(() => {
    syncing = false
  })
}

const measure = () => {
  const root = rootRef.value
  const top = topScrollRef.value
  const body = bodyRef.value
  if (!root || !body) return

  const bodyOverflows = body.scrollWidth - body.clientWidth > 1
  const scrollEl = bodyOverflows ? body : findInnerScrollEl()
  setActiveScrollEl(scrollEl)

  if (!scrollEl) {
    hasOverflow.value = false
    maxScroll.value = 0
    spacerWidth.value = 0
    scrollLeft.value = 0
    return
  }

  const targetMax = Math.max(0, scrollEl.scrollWidth - scrollEl.clientWidth)
  const topClientWidth = top?.clientWidth || root.clientWidth || scrollEl.clientWidth
  hasOverflow.value = targetMax > 1
  maxScroll.value = targetMax
  spacerWidth.value = topClientWidth + targetMax
  scrollLeft.value = Math.min(scrollEl.scrollLeft, targetMax)

  if (top) {
    top.scrollLeft = scrollLeft.value
  }
}

const queueMeasure = () => {
  if (rafId) {
    cancelAnimationFrame(rafId)
  }
  rafId = requestAnimationFrame(() => {
    rafId = 0
    measure()
  })
}

const handleTopScroll = () => {
  const top = topScrollRef.value
  if (!top || !activeScrollEl || syncing) return
  syncing = true
  const nextLeft = Math.min(top.scrollLeft, maxScroll.value)
  activeScrollEl.scrollLeft = nextLeft
  scrollLeft.value = nextLeft
  requestAnimationFrame(() => {
    syncing = false
  })
}

const handleTopKeydown = (event: KeyboardEvent) => {
  const top = topScrollRef.value
  if (!top || !hasOverflow.value) return
  const step = 56
  const pageStep = Math.max(120, top.clientWidth * 0.82)
  const deltaByKey: Record<string, number> = {
    ArrowLeft: -step,
    ArrowRight: step,
    PageUp: -pageStep,
    PageDown: pageStep,
    Home: -maxScroll.value,
    End: maxScroll.value
  }
  const delta = deltaByKey[event.key]
  if (delta === undefined) return
  event.preventDefault()
  top.scrollLeft = Math.max(0, Math.min(maxScroll.value, top.scrollLeft + delta))
}

onMounted(() => {
  void nextTick(measure)
  resizeObserver = new ResizeObserver(queueMeasure)
  if (rootRef.value) {
    resizeObserver.observe(rootRef.value)
  }
  if (bodyRef.value) {
    resizeObserver.observe(bodyRef.value)
  }
  mutationObserver = new MutationObserver(queueMeasure)
  if (rootRef.value) {
    mutationObserver.observe(rootRef.value, { childList: true, subtree: true })
  }
  window.addEventListener('resize', queueMeasure, { passive: true })
})

onBeforeUnmount(() => {
  if (rafId) {
    cancelAnimationFrame(rafId)
  }
  setActiveScrollEl(null)
  resizeObserver?.disconnect()
  mutationObserver?.disconnect()
  window.removeEventListener('resize', queueMeasure)
})
</script>

<template>
  <div ref="rootRef" class="admin-table-scroller" :class="{ 'is-overflowing': hasOverflow }">
    <div
      v-show="hasOverflow"
      ref="topScrollRef"
      class="admin-table-scroller__top"
      role="scrollbar"
      tabindex="0"
      :aria-label="props.label"
      aria-orientation="horizontal"
      :aria-valuemin="0"
      :aria-valuemax="maxScroll"
      :aria-valuenow="scrollLeft"
      @scroll="handleTopScroll"
      @keydown="handleTopKeydown"
    >
      <div class="admin-table-scroller__spacer" :style="{ width: `${spacerWidth}px` }"></div>
    </div>
    <div ref="bodyRef" class="admin-table-scroller__body">
      <slot />
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-table-scroller {
  min-width: 0;
  position: relative;
}

.admin-table-scroller__top {
  position: sticky;
  top: 0;
  z-index: 6;
  height: 14px;
  margin-bottom: 8px;
  overflow-x: auto;
  overflow-y: hidden;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.035);
  scrollbar-width: thin;
  scrollbar-color: rgba(200, 149, 90, 0.62) rgba(255, 255, 255, 0.035);
  outline: none;

  &:focus-visible {
    box-shadow: 0 0 0 1px rgba(200, 149, 90, 0.58);
  }

  &::-webkit-scrollbar {
    height: 10px;
  }

  &::-webkit-scrollbar-track {
    border-radius: 4px;
    background: rgba(255, 255, 255, 0.035);
  }

  &::-webkit-scrollbar-thumb {
    border-radius: 4px;
    background: rgba(200, 149, 90, 0.56);
  }
}

.admin-table-scroller__spacer {
  height: 1px;
}

.admin-table-scroller__body {
  min-width: 0;
  overflow-x: auto;
  overflow-y: visible;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    width: 0;
    height: 0;
  }
}

.admin-table-scroller :deep(.el-table__body-wrapper .el-scrollbar__bar.is-horizontal),
.admin-table-scroller :deep(.el-table__inner-wrapper .el-scrollbar__bar.is-horizontal) {
  display: none;
}

@media (prefers-reduced-motion: reduce) {
  .admin-table-scroller__top {
    scroll-behavior: auto;
  }
}
</style>
