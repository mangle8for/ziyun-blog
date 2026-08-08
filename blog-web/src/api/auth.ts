import { get, post, put } from '@/utils/request'
import type { LoginPayload, LoginResult, ProfilePayload, RegisterPayload, UpdatePasswordPayload, User } from '@/types'

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

/** 修改个人信息（本人）：返回更新后的用户信息 */
export function updateProfile(data: ProfilePayload) {
  return put<User>('/api/v1/auth/profile', data)
}

/**
 * 修改密码（本人）：校验原密码，成功后所有会话失效（需重新登录）。
 * 前端需在成功回调中清空登录态并跳转登录页。
 */
export function updatePassword(data: UpdatePasswordPayload) {
  return put<void>('/api/v1/auth/password', data)
}
