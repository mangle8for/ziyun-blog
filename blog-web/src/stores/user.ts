import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { getProfile, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { LoginPayload, User } from '@/types'

/** token 持久化键名（勾选「记住密码」存 localStorage，否则存 sessionStorage） */
const TOKEN_KEY = 'ziyun-blog-token'
/** 记住的用户名键名（仅勾选「记住密码」时写入，下次打开自动回填） */
const USERNAME_KEY = 'ziyun-blog-username'

/**
 * 用户登录态 Store。
 * 职责：token 持久化、登录/登出、当前用户信息。
 *
 * 「记住密码」安全设计（不存密码明文）：
 *  - 勾选：token 持久化到 localStorage，关闭浏览器后仍保持登录（自动登录），
 *    并记住用户名用于下次回填；
 *  - 不勾选：token 仅存 sessionStorage，关闭浏览器（标签页会话结束）即失效，
 *    需重新登录。
 *  密码永远只存在于内存与登录请求中，不落盘 —— 避免 XSS 从存储中窃取明文密码。
 *  401 清理统一由 utils/request.ts 拦截器调用 resetState，组件内无需重复处理。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(
    localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY) ?? '',
  )
  const userInfo = ref<User | null>(null)

  const isLoggedIn = computed(() => token.value !== '')
  /** 是否管理员（管理端路由守卫与按钮显隐用） */
  const isAdmin = computed(() => userInfo.value?.role === 1)

  /** 登录：保存 token 与用户信息；remember 决定持久化位置 */
  async function login(form: LoginPayload, remember: boolean) {
    const data = await loginApi(form)
    token.value = data.token
    // 勾选 -> 持久化（关闭浏览器仍登录）；不勾选 -> 仅会话内（关闭即失效）
    if (remember) {
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USERNAME_KEY, form.username)
    } else {
      sessionStorage.setItem(TOKEN_KEY, data.token)
      // 不勾选视为「隐私模式」：清除之前记住的用户名
      localStorage.removeItem(USERNAME_KEY)
    }
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

  /** 清空登录态（登出 / 401 拦截器调用）。记住的用户名保留（下次回填） */
  function resetState() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(TOKEN_KEY)
  }

  return { token, userInfo, isLoggedIn, isAdmin, login, fetchProfile, logout, resetState }
})
