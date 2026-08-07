import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// Element Plus 官方暗色变量：配合 html.dark class 切换（见 utils/theme.ts）
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router from './router'
import '@/assets/styles/global.css'
// 双主题设计令牌（亮色自然绿意 / 暗色星际拓荒）
import '@/assets/styles/theme.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// Element Plus 全量注册，并指定中文语言包（内置组件的默认文案随 locale 切换）
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
