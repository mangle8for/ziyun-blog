<script setup lang="ts">
import { onMounted } from 'vue'
import { useHead } from '@unhead/vue'
// 中文语言包：按需引入模式下不再全量 app.use(ElementPlus)，
// 改用 el-config-provider 在根组件提供 locale（el-pagination 等内置文案生效）
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import { useUserStore } from '@/stores/user'
import { useTheme } from '@/utils/theme'

const userStore = useUserStore()
useTheme()

// 全局标题模板：各页面只设置自己的标题，统一追加站点名；
// 未设置标题的页面（如首页）使用默认文案
useHead({
  titleTemplate: (title?: string) =>
    title ? `${title} · 紫云博客` : '紫云博客 · 星海拾遗',
})

/**
 * 应用级初始化：
 * 1. 主题已在 theme.ts 模块加载时恢复，useTheme() 负责后续响应式同步；
 * 2. 若本地有 token 但 Pinia 没有用户信息（刷新场景），主动拉取一次，
 *    让公开区文章详情页等需要当前用户身份的地方能正确显示。
 */
onMounted(() => {
  if (userStore.token && !userStore.userInfo) {
    userStore.fetchProfile().catch(() => {
      // token 失效：fetchProfile 会触发 401，request 拦截器已统一清理并跳转登录
    })
  }
})
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
