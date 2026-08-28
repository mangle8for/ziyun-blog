import { createApp } from 'vue'
import { createPinia } from 'pinia'
// SEO：路由级动态 title/meta/JSON-LD（3.x 中 createHead 从 /client 子路径导出）
import { createHead } from '@unhead/vue/client'
// Element Plus 暗色变量：配合 html.dark class 切换（见 utils/theme.ts）。
// 按需引入模式下组件样式由 unplugin 注入，此处只需官方暗色变量全集。
import 'element-plus/theme-chalk/dark/css-vars.css'
// ElMessage/ElMessageBox 在 .ts 工具与多个组件中以编程方式调用，
// 其样式不在模板解析范围，需显式引入一次（全量 css 移除后的兜底）
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import App from './App.vue'
import router from './router'
import '@/assets/styles/global.css'
// 双主题设计令牌（亮色自然绿意 / 暗色星际拓荒）
import '@/assets/styles/theme.css'
// 代码语法高亮配色（亮色基线）：编辑器代码块与文章页共用；
// 暗色覆盖见 global.css 中 html.dark 作用域的 .hljs-* 变量
import 'highlight.js/styles/github.css'

const app = createApp(App)

// 仅开发环境：localhost 不可能进 OSS 防盗链白名单，开发时图片请求需
// 免 Referer（依赖 OSS「允许空 Referer」保持开启）。生产不带此注入，
// 正常携带页面 Referer 命中白名单（ziyun.fun / *.ziyun.fun）。
if (import.meta.env.DEV) {
  const meta = document.createElement('meta')
  meta.name = 'referrer'
  meta.content = 'no-referrer'
  document.head.appendChild(meta)
}

app.use(createPinia())
app.use(router)
// SEO：路由级动态 title/meta/JSON-LD 管理（各视图 useHead 设置）
app.use(createHead())

app.mount('#app')
