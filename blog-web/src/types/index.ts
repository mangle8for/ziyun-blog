/**
 * 全局类型定义（与后端契约一一对应）。
 *
 * 约定：
 *  - 后端所有 Long 字段经 JacksonConfig 全局序列化为字符串
 *    （雪花 ID 19 位超出 JS Number 安全范围），id 一律 string；
 *  - 时间字段后端是 LocalDateTime（yyyy-MM-dd'T'HH:mm:ss），前端用 string 承载；
 *  - 枚举值（文章状态 / 用户角色）后端是 TINYINT 数字，前端同步用 number，
 *    展示文案在组件层映射，类型层不做字符串化（避免与后端脱节）。
 */

/** 统一 API 响应包装，对齐后端 common/Result */
export interface Result<T = unknown> {
  /** 业务码，200 为成功，见后端 ResultCode */
  code: number
  /** 提示信息 */
  msg: string
  /** 业务数据，失败时为 null */
  data: T
}

/** 分页查询结果包装，对齐后端 common/PageResult */
export interface PageResult<T> {
  /** 当前页数据列表 */
  records: T[]
  /** 总记录数（Long 序列化为 string，展示时按需 Number() 转换） */
  total: string
  /** 当前页码（string，对齐后端 Long） */
  page: string
  /** 每页大小（string，对齐后端 Long） */
  size: string
  /** 是否还有下一页 */
  hasNext: boolean
}

// ==================== 用户 ====================

/** 用户角色：0-普通用户 1-管理员（对齐后端 TINYINT） */
export type UserRole = 0 | 1

/** 登录用户信息，对齐后端 UserVO（不含 password；email 仅本人可见） */
export interface User {
  id: string
  username: string
  nickname?: string
  avatar?: string
  email?: string
  role: UserRole
}

/** 用户管理列表项，对齐后端 UserAdminVO（仅管理员接口返回） */
export interface UserAdmin {
  id: string
  username: string
  nickname?: string
  avatar?: string
  email?: string
  role: UserRole
  /** 状态：0-禁用 1-正常 */
  status: 0 | 1
  createTime: string
  updateTime: string
}

/** 用户管理分页查询参数 */
export interface UserManageQuery {
  page?: number
  size?: number
  keyword?: string
}

/** 新增用户（管理员代建）载荷，对齐后端 RegisterDTO */
export interface CreateUserPayload {
  username: string
  password: string
  nickname?: string
}

/** 用户状态切换载荷，对齐后端 StatusUpdateDTO */
export interface UserStatusPayload {
  status: 0 | 1
}

/** 修改个人信息载荷，对齐后端 UpdateProfileDTO */
export interface ProfilePayload {
  nickname?: string
  email?: string
  avatar?: string
}

/** 修改密码载荷，对齐后端 UpdatePasswordDTO */
export interface UpdatePasswordPayload {
  oldPassword: string
  newPassword: string
}

/** 登录表单 */
export interface LoginPayload {
  username: string
  password: string
}

/** 登录成功返回：token + 用户信息一次返回 */
export interface LoginResult {
  token: string
  user: User
}

// ==================== 分类 / 标签 ====================

/** 文章分类，对齐后端 Category 实体 */
export interface Category {
  id: string
  name: string
  description?: string
  createTime: string
  updateTime: string
}

/** 文章标签，对齐后端 Tag 实体 */
export interface Tag {
  id: string
  name: string
  createTime: string
  updateTime: string
}

/** 分类新增/更新载荷 */
export interface CategoryPayload {
  name: string
  description?: string
}

/** 热门分类项，对齐后端 CategoryHotVO（首页筛选区 TopN 展示） */
export interface CategoryHot {
  id: string
  name: string
  /** 已发布文章数（后端聚合为 Integer，JSON 中为 number） */
  articleCount: number
}

/** 热门标签项，对齐后端 TagHotVO（首页筛选区 TopN 展示） */
export interface TagHot {
  id: string
  name: string
  articleCount: number
}

/** 标签新增/更新载荷 */
export interface TagPayload {
  name: string
}

// ==================== 文章 ====================

/** 文章状态：0-草稿 1-发布（对齐后端 TINYINT） */
export type ArticleStatus = 0 | 1

/** 文章列表项，对齐后端 ArticleListItemVO（不含正文） */
export interface ArticleListItem {
  id: string
  title: string
  summary?: string
  cover?: string
  categoryId?: string
  categoryName?: string
  authorName?: string
  status: ArticleStatus
  viewCount: number
  likeCount: number
  createTime: string
  updateTime: string
  tags: TagItem[]
}

/** 上一篇/下一篇导航，对齐后端 ArticleNavVO */
export interface ArticleNav {
  id: string
  title: string
}

/** 文章详情，对齐后端 ArticleDetailVO（含正文与导航） */
export interface ArticleDetail {
  id: string
  title: string
  summary?: string
  content: string
  cover?: string
  categoryId?: string
  categoryName?: string
  authorName?: string
  status: ArticleStatus
  viewCount: number
  likeCount: number
  createTime: string
  updateTime: string
  tags: TagItem[]
  prevArticle?: ArticleNav | null
  nextArticle?: ArticleNav | null
}

/** 标签精简项（文章卡片/详情的标签列表元素），对齐后端 TagVO */
export interface TagItem {
  id: string
  name: string
}

/** 文章分页查询参数（公开列表） */
export interface ArticleQuery {
  page?: number
  size?: number
  keyword?: string
  categoryId?: string
  tagId?: string
}

/** 管理端分页查询参数（含状态过滤） */
export interface ArticleManageQuery {
  page?: number
  size?: number
  status?: ArticleStatus
}

/** 文章新增/更新载荷，对齐后端 ArticleDTO */
export interface ArticlePayload {
  title: string
  summary?: string
  content: string
  cover?: string
  categoryId?: string
  tagIds?: string[]
  status: ArticleStatus
}

/** 文章状态切换载荷，对齐后端 StatusUpdateDTO */
export interface ArticleStatusPayload {
  status: ArticleStatus
}

// ==================== 文件 ====================

/** 文件上传返回：后端直接返回可访问 URL 字符串 */
export type UploadResult = string
