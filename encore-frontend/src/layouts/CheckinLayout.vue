<script setup lang="ts">
// 极简全屏布局
</script>

<template>
  <div class="checkin-layout">
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
</style>
