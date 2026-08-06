import { del, get, post, put } from '@/utils/request'
import type { Article, ArticlePayload, ArticleQuery, PageResult } from '@/types'

/** 分页查询文章列表 */
export function getArticlePage(params?: ArticleQuery) {
  return get<PageResult<Article>>('/api/v1/articles', { params })
}

/** 查询文章详情 */
export function getArticle(id: string) {
  return get<Article>(`/api/v1/articles/${id}`)
}

/** 新增文章 */
export function createArticle(data: ArticlePayload) {
  return post<Article>('/api/v1/articles', data)
}

/** 更新文章 */
export function updateArticle(id: string, data: ArticlePayload) {
  return put<Article>(`/api/v1/articles/${id}`, data)
}

/** 删除文章（逻辑删除） */
export function deleteArticle(id: string) {
  return del<void>(`/api/v1/articles/${id}`)
}
