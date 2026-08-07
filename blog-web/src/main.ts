import { createApp } from 'vue'
import { createPinia } from 'pinia'
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

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
