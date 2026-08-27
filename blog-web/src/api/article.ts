import { del, get, post, put } from '@/utils/request'
import type {
  ArticleDetail,
  ArticleListItem,
  ArticleManageQuery,
  ArticlePinnedPayload,
  ArticlePayload,
  ArticleQuery,
  ArticleStatusPayload,
  PageResult,
} from '@/types'

/** 公开分页查询（仅已发布；支持分类/标签/标题/时间过滤与 excludePinned） */
export function getArticlePage(params?: ArticleQuery) {
  return get<PageResult<ArticleListItem>>('/api/v1/articles', { params })
}

/** 置顶文章清单（公开，首页星耀推荐区消费） */
export function getPinnedArticles(limit = 5) {
  return get<ArticleListItem[]>('/api/v1/articles/pinned', { params: { limit } })
}

/** 管理端分页查询（含草稿，可状态过滤） */
export function getArticleManagePage(params?: ArticleManageQuery) {
  return get<PageResult<ArticleListItem>>('/api/v1/articles/manage', { params })
}

/** 文章详情（公开，含上一篇/下一篇；草稿对前台返回 404） */
export function getArticleDetail(id: string) {
  return get<ArticleDetail>(`/api/v1/articles/${id}`)
}

/** 管理端文章详情（含草稿，编辑器回填用；公开详情接口对草稿返回 404 故需此接口） */
export function getArticleManageDetail(id: string) {
  return get<ArticleDetail>(`/api/v1/articles/manage/${id}`)
}

/** 新增文章（管理端），返回新文章 ID */
export function createArticle(data: ArticlePayload) {
  return post<string>('/api/v1/articles', data)
}

/** 更新文章 */
export function updateArticle(id: string, data: ArticlePayload) {
  return put<void>(`/api/v1/articles/${id}`, data)
}

/** 切换文章状态（发布 <-> 草稿） */
export function updateArticleStatus(id: string, data: ArticleStatusPayload) {
  return put<void>(`/api/v1/articles/${id}/status`, data)
}

/** 切换文章置顶（管理端列表星标按钮） */
export function updateArticlePinned(id: string, data: ArticlePinnedPayload) {
  return put<void>(`/api/v1/articles/${id}/pinned`, data)
}

/** 删除文章（逻辑删除） */
export function deleteArticle(id: string) {
  return del<void>(`/api/v1/articles/${id}`)
}
