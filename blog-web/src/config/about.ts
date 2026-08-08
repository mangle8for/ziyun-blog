/**
 * 关于页内容配置（硬编码，改动需发版）。
 *
 * AboutView 只负责渲染，所有文案/链接/技能栈都维护在这里：
 *  - profile：博主卡片区（头像 / 昵称 / 签名 / 简介 / 社交链接）
 *  - skillGroups：技术栈可视化（分组 + 熟练度进度条，level 取 0-100）
 *
 * 站点统计（文章数/分类数/标签数）不在此配置 ——
 * 由 AboutView 调现有公开接口实时获取。
 */

/** 社交链接图标键：AboutView 中维护 key -> 图标组件的映射 */
export type SocialIconKey = 'github' | 'email' | 'link'

export interface SocialLink {
  /** 链接名称（tooltip / aria-label） */
  name: string
  /** 目标地址（邮箱用 mailto: 前缀） */
  url: string
  icon: SocialIconKey
}

export interface SkillItem {
  name: string
  /** 熟练度 0-100（进度条宽度） */
  level: number
}

export interface SkillGroup {
  title: string
  items: SkillItem[]
}

export const profile = {
  /** 昵称（头像缺省时取首字作为占位） */
  nickname: '紫云',
  /** 一句话签名 */
  signature: '仰望星空，也脚踏实地',
  /** 个人简介（1~2 句为宜，移动端阅读无压力） */
  bio: '热爱技术与写作，喜欢把复杂的问题讲简单。这个博客记录我在后端、前端与部署运维上的实践与思考，也偶尔收藏沿途的风景。',
  /** 头像 URL（留空则显示昵称首字渐变占位头像） */
  avatar: '',
  /** 社交链接（替换成你自己的地址；不想展示的项直接删除） */
  socials: [
    { name: 'GitHub', url: 'https://github.com/your-name', icon: 'github' },
    { name: '邮箱', url: 'mailto:hi@example.com', icon: 'email' },
  ] as SocialLink[],
}

export const skillGroups: SkillGroup[] = [
  {
    title: '后端',
    items: [
      { name: 'Java / Spring Boot', level: 90 },
      { name: 'MyBatis-Plus / MySQL', level: 85 },
      { name: 'Redis / 缓存设计', level: 75 },
    ],
  },
  {
    title: '前端',
    items: [
      { name: 'Vue 3 / TypeScript', level: 85 },
      { name: 'Vite / 前端工程化', level: 80 },
      { name: 'Element Plus', level: 82 },
    ],
  },
  {
    title: '部署运维',
    items: [
      { name: 'Nginx / Docker', level: 70 },
      { name: 'Linux / CI 流水线', level: 65 },
    ],
  },
]
