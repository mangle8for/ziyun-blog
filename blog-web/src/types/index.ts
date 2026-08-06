/**
 * 全局类型定义。
 *
 * 约定：后端所有 Long 字段经 JacksonConfig 统一序列化为字符串
 * （雪花 ID 19 位超出 JS Number 安全范围），因此 id 一律 string，
 * 与后端实体一一对应（TS 侧照抄）。
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

/**
 * 分页查询结果包装，对齐后端 common/PageResult。
 * 注意：total/page/size 后端为 Long，序列化后是字符串，
 * 分页组件使用时需 Number() 转换。
 */
export interface PageResult<T> {
  /** 当前页数据列表 */
  records: T[]
  /** 总记录数 */
  total: string
  /** 当前页码（从 1 开始） */
  page: string
  /** 每页大小 */
  size: string
  /** 是否还有下一页 */
  hasNext: boolean
}

/** 用户角色 */
export type UserRole = 'ADMIN' | 'USER'

/** 用户，对齐后端 User 实体 */
export interface User {
  id: string
  username: string
  nickname?: string
  avatar?: string
  email?: string
  role: UserRole
  createdAt: string
  updatedAt: string
}

/** 文章分类，对齐后端 Category 实体 */
export interface Category {
  id: string
  name: string
  description?: string
  /** 排序权重（Integer，数字类型） */
  sortOrder?: number
  createdAt: string
  updatedAt: string
}

/** 文章标签，对齐后端 Tag 实体 */
export interface Tag {
  id: string
  name: string
  createdAt: string
  updatedAt: string
}

/** 文章发布状态 */
export type ArticleStatus = 'DRAFT' | 'PUBLISHED'

/** 文章，对齐后端 Article 实体 */
export interface Article {
  id: string
  title: string
  /** 摘要；列表接口可能不返回正文，只返回摘要 */
  summary?: string
  /** Markdown 原文（编辑/详情接口返回） */
  content?: string
  /** 封面图 URL */
  cover?: string
  categoryId?: string
  category?: Category
  tags?: Tag[]
  /** 浏览量（Integer） */
  viewCount: number
  /** 点赞量（Integer） */
  likeCount: number
  status: ArticleStatus
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

/** 登录表单 */
export interface LoginForm {
  username: string
  password: string
}

/** 注册表单 */
export interface RegisterForm {
  username: string
  password: string
  nickname?: string
  email?: string
}

/** 登录/注册成功返回 */
export interface LoginResult {
  token: string
  user: User
}

/** 文章分页查询参数 */
export interface ArticleQuery {
  page?: number
  size?: number
  keyword?: string
  categoryId?: string
  tagId?: string
  status?: ArticleStatus
}

/** 文章新增/编辑载荷 */
export interface ArticlePayload {
  title: string
  summary?: string
  content: string
  cover?: string
  categoryId?: string
  tagIds?: string[]
  status: ArticleStatus
}

/** 文件上传结果 */
export interface UploadResult {
  url: string
  name?: string
}
