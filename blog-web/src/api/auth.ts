import { get, post } from '@/utils/request'
import type { LoginForm, LoginResult, RegisterForm, User } from '@/types'

/** 登录 */
export function login(data: LoginForm) {
  return post<LoginResult>('/api/v1/auth/login', data)
}

/** 注册 */
export function register(data: RegisterForm) {
  return post<LoginResult>('/api/v1/auth/register', data)
}

/** 退出登录 */
export function logout() {
  return post<void>('/api/v1/auth/logout')
}

/** 获取当前登录用户信息 */
export function getProfile() {
  return get<User>('/api/v1/auth/profile')
}
