import { del, get, post, put } from '@/utils/request'
import type { PageResult, Tag } from '@/types'

/** 分页查询标签列表 */
export function getTagPage(params?: { page?: number; size?: number; keyword?: string }) {
  return get<PageResult<Tag>>('/api/v1/tags', { params })
}

/** 查询全部标签（下拉选择用） */
export function getTagList() {
  return get<Tag[]>('/api/v1/tags/all')
}

/** 新增标签 */
export function createTag(data: { name: string }) {
  return post<Tag>('/api/v1/tags', data)
}

/** 更新标签 */
export function updateTag(id: string, data: { name: string }) {
  return put<Tag>(`/api/v1/tags/${id}`, data)
}

/** 删除标签 */
export function deleteTag(id: string) {
  return del<void>(`/api/v1/tags/${id}`)
}
