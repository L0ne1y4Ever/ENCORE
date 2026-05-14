<script setup lang="ts">
// 极简全屏布局
import LanguageSwitch from '../components/LanguageSwitch.vue'
</script>

<template>
  <div class="checkin-layout">
    <div class="lang-shell">
      <LanguageSwitch />
    </div>
    <router-view />
  </div>
</template>

<style scoped lang="scss">
.checkin-layout {
  height: 100vh;
  width: 100vw;
  background-color: #000000; /* 死黑背景 */
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.lang-shell {
  position: fixed;
  top: var(--spacing-6);
  left: var(--spacing-6);
  z-index: 20;
}
</style>
