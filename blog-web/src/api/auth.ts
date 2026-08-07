import { get, post } from '@/utils/request'
import type { LoginPayload, LoginResult, RegisterPayload, User } from '@/types'

/** 登录：成功返回 token + 用户信息 */
export function login(data: LoginPayload) {
  return post<LoginResult>('/api/v1/auth/login', data)
}

/** 注册：后端返回新用户 ID（非 token，注册后需再登录） */
export function register(data: RegisterPayload) {
  return post<string>('/api/v1/auth/register', data)
}

/** 退出登录（删 Redis 登录态） */
export function logout() {
  return post<void>('/api/v1/auth/logout')
}

/** 获取当前登录用户信息（/auth/me 需登录态） */
export function getProfile() {
  return get<User>('/api/v1/auth/me')
}
