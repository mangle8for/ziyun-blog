import { del, get, post, put } from '@/utils/request'
import type { Tag, TagPayload } from '@/types'

/** 全量标签列表（后端无 /all，GET /api/v1/tags 即全量） */
export function getTagList() {
  return get<Tag[]>('/api/v1/tags')
}

/** 新增标签 */
export function createTag(data: TagPayload) {
  return post<string>('/api/v1/tags', data)
}

/** 更新标签 */
export function updateTag(id: string, data: TagPayload) {
  return put<void>(`/api/v1/tags/${id}`, data)
}

/** 删除标签（自动清理文章-标签关联） */
export function deleteTag(id: string) {
  return del<void>(`/api/v1/tags/${id}`)
}
