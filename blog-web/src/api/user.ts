import { del, get, put } from '@/utils/request'
import type { PageResult, UserAdmin, UserManageQuery, UserStatusPayload } from '@/types'

/** 分页查询用户列表（管理员；支持用户名/昵称关键字搜索） */
export function getUserPage(params?: UserManageQuery) {
  return get<PageResult<UserAdmin>>('/api/v1/users', { params })
}

/** 启用/禁用用户（禁用立即踢下线，被禁账号无法登录） */
export function updateUserStatus(id: string, data: UserStatusPayload) {
  return put<void>(`/api/v1/users/${id}/status`, data)
}

/** 重置用户密码：返回新生成的随机密码（仅本次展示一次） */
export function resetUserPassword(id: string) {
  return put<string>(`/api/v1/users/${id}/password/reset`)
}

/** 删除用户（逻辑删除 + 踢下线；名下还有文章时后端返回 409） */
export function deleteUser(id: string) {
  return del<void>(`/api/v1/users/${id}`)
}
