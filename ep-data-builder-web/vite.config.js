import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: true, // 同时监听 IPv4/IPv6，localhost 与 127.0.0.1 均可访问
    port: 7588,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:9599',
        changeOrigin: true
      },
      '/open-api': {
        target: 'http://127.0.0.1:9599',
        changeOrigin: true
      }
    }
  }
})
