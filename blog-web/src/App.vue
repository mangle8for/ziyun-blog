<script setup lang="ts">
import { onMounted } from 'vue'

import { useUserStore } from '@/stores/user'
import { useTheme } from '@/utils/theme'

const userStore = useUserStore()
useTheme()

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
  <router-view />
</template>
