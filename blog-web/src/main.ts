import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import '@/assets/styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// Element Plus 全量注册，并指定中文语言包（内置组件的默认文案随 locale 切换）
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
