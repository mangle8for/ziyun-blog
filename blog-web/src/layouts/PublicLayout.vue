<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Moon, Sunny, User } from '@element-plus/icons-vue'

import StarfieldBackground from '@/components/StarfieldBackground.vue'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/utils/theme'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { theme, toggleTheme } = useTheme()

/** 导航项（当前路由高亮） */
const navItems = [
  { path: '/', label: '首页' },
  { path: '/archives', label: '文章' },
  { path: '/categories', label: '分类' },
  { path: '/tags', label: '标签' },
  { path: '/search', label: '搜索' },
  { path: '/about', label: '关于' },
]

const activePath = computed(() => route.path)
/** 移动端导航折叠开关 */
const mobileMenuOpen = ref(false)

/**
 * 滚动方向感知导航：页面下滑超过一屏 1/4 时自动隐藏顶栏（阅读沉浸），
 * 向上滚动或回到顶部时立即显现。
 */
const navHidden = ref(false)
let lastY = 0
/** 方向累积器：同向滚动量累加、越过阈值才切换状态，
 *  吞掉滚动途中的微抖动（图片懒加载位移/平滑滚动中间帧），
 *  否则下滑途中 -2px 的回弹就会让导航反复弹出来。 */
let accum = 0

function onNavScroll() {
  const y = window.scrollY
  const delta = y - lastY
  lastY = y

  if (y <= 220) {
    navHidden.value = false
    accum = 0
    return
  }
  if (delta === 0) return

  if (delta > 0) {
    accum = accum > 0 ? accum + delta : delta
  } else {
    accum = accum < 0 ? accum + delta : delta
  }

  if (accum > 28) {
    navHidden.value = true
    accum = 0
  } else if (accum < -18) {
    navHidden.value = false
    accum = 0
  }
}

function go(path: string) {
  mobileMenuOpen.value = false
  router.push(path)
}

async function onLogout() {
  await userStore.logout()
  router.push('/')
}

onMounted(() => {
  window.addEventListener('scroll', onNavScroll, { passive: true })
  lastY = window.scrollY
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onNavScroll)
})
</script>

<template>
  <div class="public-layout">
    <!-- 星空粒子背景层（暗色主题可见） -->
    <StarfieldBackground />

    <!-- 顶部导航：毛玻璃吸顶 + 下滑隐藏/上滑显现 -->
    <header class="nav-bar" :class="{ 'nav-hidden': navHidden }">
      <div class="nav-inner">
        <div class="brand" @click="go('/')">
          <span class="brand-icon">✦</span>
          <span class="brand-name">紫云博客</span>
        </div>

        <!-- 桌面导航 -->
        <nav class="nav-links desktop-only">
          <a
            v-for="item in navItems"
            :key="item.path"
            class="nav-link"
            :class="{ active: activePath === item.path }"
            @click.prevent="go(item.path)"
            href="#"
          >
            {{ item.label }}
          </a>
        </nav>

        <div class="nav-actions">
          <!-- 主题切换：暗色星际拓荒 / 亮色自然绿意 -->
          <el-tooltip
            :content="theme === 'dark' ? '切换到自然绿意' : '切换到星际拓荒'"
            placement="bottom"
          >
            <el-button class="icon-btn" circle @click="toggleTheme">
              <el-icon><Moon v-if="theme === 'dark'" /><Sunny v-else /></el-icon>
            </el-button>
          </el-tooltip>

          <!-- 登录态入口 -->
          <template v-if="userStore.isLoggedIn">
            <el-dropdown trigger="click">
              <span class="user-chip">
                <el-icon><User /></el-icon>
                {{ userStore.userInfo?.nickname ?? userStore.userInfo?.username ?? '用户' }}
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="go('/profile')">个人中心</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" @click="go('/admin')"
                    >管理后台</el-dropdown-item
                  >
                  <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button
            v-else
            class="login-btn"
            type="primary"
            round
            size="small"
            @click="go('/login')"
          >
            登录
          </el-button>

          <!-- 移动端菜单按钮 -->
          <el-button class="icon-btn mobile-only" circle @click="mobileMenuOpen = !mobileMenuOpen">
            <span class="hamburger">≡</span>
          </el-button>
        </div>
      </div>

      <!-- 移动端折叠菜单 -->
      <transition name="slide-down">
        <nav v-if="mobileMenuOpen" class="nav-links-mobile">
          <a
            v-for="item in navItems"
            :key="item.path"
            class="nav-link-mobile"
            :class="{ active: activePath === item.path }"
            @click.prevent="go(item.path)"
            href="#"
          >
            {{ item.label }}
          </a>
        </nav>
      </transition>
    </header>

    <!-- 内容区：路由出口 -->
    <!-- 全出血路由（meta.fullBleed，如首页首屏）脱离限宽容器，由页面自管宽度 -->
    <main class="content" :class="{ 'content-full': route.meta.fullBleed === true }">
      <router-view />
    </main>

    <!-- 底部 -->
    <footer class="footer">
      <span>紫云博客 · 星海拾遗</span>
      <span class="footer-sub">Powered by Spring Boot 3 + Vue 3</span>
    </footer>
  </div>
</template>

<style scoped>
.public-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 顶部导航：毛玻璃 + 吸顶 */
.nav-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--bg-nav);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border-bottom: 1px solid var(--border-color);
  /* 下滑隐藏 / 上滑显现的位移动画 */
  transition: transform 0.32s cubic-bezier(0.2, 0.7, 0.3, 1);
}

.nav-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.brand-icon {
  font-size: 22px;
  color: var(--color-accent);
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--text-main);
}

.nav-bar.nav-hidden {
  transform: translateY(-100%);
}

.nav-links {
  display: flex;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

.nav-link {
  position: relative;
  padding: 8px 14px;
  color: var(--text-secondary);
  font-size: 14.5px;
  font-weight: 500;
  letter-spacing: 0.5px;
  cursor: pointer;
  transition: color 0.25s ease;
}

/* 悬停/激活：底部下划线从中心展开，替代旧的底色胶囊 */
.nav-link::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 2px;
  height: 2px;
  border-radius: 2px;
  background: var(--color-primary);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.28s cubic-bezier(0.2, 0.7, 0.3, 1);
}

.nav-link:hover {
  color: var(--text-main);
}

.nav-link:hover::after {
  transform: scaleX(0.55);
}

.nav-link.active {
  color: var(--color-primary);
  font-weight: 700;
}

.nav-link.active::after {
  transform: scaleX(1);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-btn {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-main);
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-main);
  cursor: pointer;
  font-size: 14px;
}

.hamburger {
  font-size: 18px;
  line-height: 1;
}

/* 内容区：限宽居中，留白给星空呼吸 */
.content {
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 48px;
}

/* 全出血模式：解除限宽与内边距，交给页面自行约束内部区块宽度。
 * 用于带全视口首屏的页面 —— 首屏天然铺满视口，不存在负 margin
 * 出血被祖先 overflow 裁剪的问题。 */
.content-full {
  max-width: none;
  padding: 0 0 48px;
}

.footer {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
  font-size: 13px;
  border-top: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.footer-sub {
  font-size: 12px;
  opacity: 0.8;
}

/* 移动端折叠菜单 */
.nav-links-mobile {
  display: none;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.25s ease;
}
.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 响应式：桌面显示横向导航，移动端折叠 */
.desktop-only {
  display: flex;
}
.mobile-only {
  display: none;
}

@media (max-width: 768px) {
  .desktop-only {
    display: none;
  }
  .mobile-only {
    display: inline-flex;
  }
  .nav-links-mobile {
    display: flex;
    flex-direction: column;
    padding: 8px 24px 16px;
    gap: 4px;
  }
  .nav-link-mobile {
    padding: 10px 12px;
    border-radius: 10px;
    color: var(--text-secondary);
  }
  .nav-link-mobile.active {
    color: var(--color-primary);
    background: var(--bg-card);
    font-weight: 600;
  }
  .content {
    padding: 20px 16px 36px;
  }
}
</style>
