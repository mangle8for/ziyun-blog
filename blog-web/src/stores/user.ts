import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { getProfile, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { LoginPayload, User } from '@/types'

/** token 持久化键名 */
const TOKEN_KEY = 'ziyun-blog-token'

/**
 * 用户登录态 Store。
 * 职责：token 持久化（localStorage）、登录/登出、当前用户信息。
 * 401 清理统一由 utils/request.ts 拦截器调用 resetState，组件内无需重复处理。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const userInfo = ref<User | null>(null)

  const isLoggedIn = computed(() => token.value !== '')
  /** 是否管理员（管理端路由守卫与按钮显隐用） */
  const isAdmin = computed(() => userInfo.value?.role === 1)

  /** 登录：保存 token 与用户信息 */
  async function login(form: LoginPayload) {
    const data = await loginApi(form)
    token.value = data.token
    localStorage.setItem(TOKEN_KEY, data.token)
    userInfo.value = data.user
  }

  /** 拉取当前用户信息（刷新页面后恢复登录态） */
  async function fetchProfile() {
    userInfo.value = await getProfile()
  }

  /** 登出：通知后端失效，无论如何都清空本地登录态 */
  async function logout() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      resetState()
    }
  }

  /** 清空登录态（登出 / 401 拦截器调用） */
  function resetState() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, userInfo, isLoggedIn, isAdmin, login, fetchProfile, logout, resetState }
})
