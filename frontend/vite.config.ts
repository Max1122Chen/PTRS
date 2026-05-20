import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 后端统一以 /api 开头，直接代理到 SpringBoot 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 管理端异步采集轮询 + 长任务，避免代理过早断开导致 502
        timeout: 0,
      },
      // 日记附件静态资源回传路径
      '/media': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
