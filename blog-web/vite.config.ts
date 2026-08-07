import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // Element Plus 按需引入：
    //  - Components：模板里的 <el-xxx> 组件自动注册并注入组件级样式
    //  - AutoImport：配合 resolver 让指令（v-loading 等）与组件 API 一并按需加载
    //  替代全量 app.use(ElementPlus)，主 chunk 从 ~766KB 显著缩小
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // 设计说明：前端请求 /api 开头路径时转发到后端 8080。
      // 后端接口自带 /api/v1 前缀，故不做 rewrite，路径原样透传。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
