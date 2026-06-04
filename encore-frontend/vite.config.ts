import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        // 把体积最大的三方库各自拆成独立 vendor chunk：three 仅选座 3D 预览用、
        // echarts 仅管理端看板用，拆分后可缩小首屏 index 体积并改善长期缓存命中。
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('/three/')) return 'vendor-three'
          if (id.includes('/echarts/') || id.includes('/zrender/') || id.includes('/vue-echarts/')) return 'vendor-echarts'
          if (id.includes('/element-plus/') || id.includes('/@element-plus/')) return 'vendor-element-plus'
        },
      },
    },
  },
})
