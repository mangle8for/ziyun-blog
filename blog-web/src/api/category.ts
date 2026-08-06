import { del, get, post, put } from '@/utils/request'
import type { Category, PageResult } from '@/types'

/** 分页查询分类列表 */
export function getCategoryPage(params?: { page?: number; size?: number; keyword?: string }) {
  return get<PageResult<Category>>('/api/v1/categories', { params })
}

/** 查询全部分类（下拉选择用） */
export function getCategoryList() {
  return get<Category[]>('/api/v1/categories/all')
}

/** 新增分类 */
export function createCategory(data: { name: string; description?: string; sortOrder?: number }) {
  return post<Category>('/api/v1/categories', data)
}

/** 更新分类 */
export function updateCategory(id: string, data: { name: string; description?: string; sortOrder?: number }) {
  return put<Category>(`/api/v1/categories/${id}`, data)
}

/** 删除分类 */
export function deleteCategory(id: string) {
  return del<void>(`/api/v1/categories/${id}`)
}
