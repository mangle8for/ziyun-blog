import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

import router from '@/router'
import { useUserStore } from '@/stores/user'
import type { Result } from '@/types'

/** 业务成功码，对齐后端 ResultCode.SUCCESS */
const SUCCESS_CODE = 200
/** 未登录/登录态失效码，对齐后端 ResultCode.UNAUTHORIZED */
const UNAUTHORIZED_CODE = 401

const instance = axios.create({
  // baseURL 读环境变量：开发环境走 /api 由 Vite 代理到后端，生产环境替换为真实网关地址
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

/**
 * 登录态失效统一处理：清空 Pinia 用户状态并跳转登录页。
 * 跳转时携带 redirect 参数，登录成功后回跳原页面。
 */
function handleUnauthorized() {
  const userStore = useUserStore()
  userStore.resetState()
  if (router.currentRoute.value.path !== '/login') {
    ElMessage.warning('登录已过期，请重新登录')
    router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
}

/**
 * 请求拦截器职责：
 * 1. 从 Pinia 读取 token，注入 Authorization: Bearer <token>；
 * 2. 未登录时保持匿名请求（放行白名单接口，由后端鉴权）。
 */
instance.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

/**
 * 响应拦截器职责：
 * 1. 解包 Result<T>：HTTP 2xx 但业务码非 200 时统一弹错误提示并 reject；
 * 2. 401（业务码或 HTTP 状态码）→ 清 Pinia 跳登录；
 * 3. HTTP/网络错误 → 提取后端 msg 或兜底文案统一提示。
 */
instance.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    if (res.code !== SUCCESS_CODE) {
      if (res.code === UNAUTHORIZED_CODE) {
        handleUnauthorized()
      } else {
        ElMessage.error(res.msg || '请求失败')
      }
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    // 已解包：返回 Result.data，调用方直接拿到业务数据。
    // axios 类型规定拦截器返回 AxiosResponse，故经 unknown 中转一次。
    return res.data as unknown as typeof response
  },
  (error) => {
    if (error.response?.status === UNAUTHORIZED_CODE) {
      handleUnauthorized()
    } else {
      const msg =
        error.response?.data?.msg ??
        (error.code === 'ECONNABORTED'
          ? '请求超时，请稍后重试'
          : error.message === 'Network Error'
            ? '网络异常，请检查网络或后端服务'
            : error.message || '请求失败')
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

/**
 * 类型化请求入口：响应拦截器已解包 Result<T>，这里直接返回 Promise<T>。
 * axios 1.19 的 request 返回 AxiosResponseResult<T, R> 条件类型，泛型 R 无法
 * 被 TS 直接解析，故在唯一入口处做一次断言（运行时不改变任何行为）。
 */
export function http<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return instance.request(config) as unknown as Promise<T>
}

/** GET 请求 */
export function get<T>(url: string, config?: Omit<AxiosRequestConfig, 'url' | 'method'>): Promise<T> {
  return http<T>({ ...config, url, method: 'GET' })
}

/** POST 请求（data 缺省为空对象，方便传 FormData） */
export function post<T>(
  url: string,
  data?: unknown,
  config?: Omit<AxiosRequestConfig, 'url' | 'method' | 'data'>,
): Promise<T> {
  return http<T>({ ...config, url, method: 'POST', data })
}

/** PUT 请求 */
export function put<T>(
  url: string,
  data?: unknown,
  config?: Omit<AxiosRequestConfig, 'url' | 'method' | 'data'>,
): Promise<T> {
  return http<T>({ ...config, url, method: 'PUT', data })
}

/** DELETE 请求 */
export function del<T>(url: string, config?: Omit<AxiosRequestConfig, 'url' | 'method'>): Promise<T> {
  return http<T>({ ...config, url, method: 'DELETE' })
}

export default instance
