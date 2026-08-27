import { createRouter, createWebHistory } from 'vue-router'

import { useUserStore } from '@/stores/user'

/**
 * 路由设计：
 *  - 公开区套 PublicLayout（导航/星空背景/毛玻璃内容区）；
 *  - 管理区套 AdminLayout，路由守卫要求登录 + 管理员；
 *  - 登录页独立（无布局包裹，沉浸式表单）。
 * 懒加载：除首页外路由组件用动态 import，首屏只加载必要 chunk。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/PublicLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        {
          path: 'article/:id',
          name: 'article-detail',
          component: () => import('@/views/ArticleDetailView.vue'),
        },
        // 独立文章页：时间轴 + 卡片流 + 下滑无限加载
        { path: 'archives', name: 'archives', component: () => import('@/views/ArchivesView.vue') },
        // 独立搜索页：标题/分类/标签/时间组合搜索
        { path: 'search', name: 'search', component: () => import('@/views/SearchView.vue') },
        {
          path: 'categories',
          name: 'categories',
          component: () => import('@/views/CategoryView.vue'),
        },
        { path: 'tags', name: 'tags', component: () => import('@/views/TagView.vue') },
        { path: 'about', name: 'about', component: () => import('@/views/AboutView.vue') },
        // 个人中心：需登录（任意角色），独立于管理后台
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { requiresAuth: true },
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', redirect: '/admin/articles' },
        {
          path: 'articles',
          name: 'admin-articles',
          component: () => import('@/views/admin/ArticleManageView.vue'),
        },
        {
          path: 'articles/new',
          name: 'admin-article-new',
          component: () => import('@/views/admin/ArticleEditView.vue'),
        },
        {
          path: 'articles/:id/edit',
          name: 'admin-article-edit',
          component: () => import('@/views/admin/ArticleEditView.vue'),
        },
        {
          path: 'categories',
          name: 'admin-categories',
          component: () => import('@/views/admin/CategoryManageView.vue'),
        },
        {
          path: 'tags',
          name: 'admin-tags',
          component: () => import('@/views/admin/TagManageView.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/UserManageView.vue'),
        },
      ],
    },
    // 兜底：未知路径回首页
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  // 路由切换回滚到顶部（长文阅读体验）
  scrollBehavior: () => ({ top: 0 }),
})

/**
 * 全局前置守卫：
 *  - 未登录访问管理区 -> 跳登录页并携带回跳地址；
 *  - 已登录但非管理员 -> 跳首页并提示（普通用户无管理入口）。
 */
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    return { path: '/' }
  }
  return true
})

export default router
