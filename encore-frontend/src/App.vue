<script setup lang="ts">
import { onMounted } from 'vue'

onMounted(() => {
  // 注入无衬线品牌字体
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = 'https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,400..1000;1,9..40,400..1000&display=swap'
  document.head.appendChild(link)

  // 强制 element-plus 开启暗色模式
  document.documentElement.classList.add('dark')
})
</script>

<template>
  <router-view v-slot="{ Component }">
    <transition name="page-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<style>
/* 确保 App 挂载点撑满全屏 */
#app {
  width: 100vw;
  height: 100vh;
  overflow-x: hidden;
  background-color: var(--color-bg-base);
}
</style>
