import { del, get, post, put } from '@/utils/request'
import type { Category, CategoryHot, CategoryPayload } from '@/types'

/** 全量分类列表（下拉/导航用；后端无 /all，GET /api/v1/categories 即全量） */
export function getCategoryList() {
  return get<Category[]>('/api/v1/categories')
}

/** 热门分类（按已发布文章数倒序 TopN，首页筛选区默认展示） */
export function getHotCategories(limit = 8) {
  return get<CategoryHot[]>('/api/v1/categories/hot', { params: { limit } })
}

/** 新增分类 */
export function createCategory(data: CategoryPayload) {
  return post<string>('/api/v1/categories', data)
}

/** 更新分类 */
export function updateCategory(id: string, data: CategoryPayload) {
  return put<void>(`/api/v1/categories/${id}`, data)
}

/** 删除分类（被文章引用时后端返回 409） */
export function deleteCategory(id: string) {
  return del<void>(`/api/v1/categories/${id}`)
}
