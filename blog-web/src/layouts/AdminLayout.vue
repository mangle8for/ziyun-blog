<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, DocumentAdd, List, SwitchButton } from '@element-plus/icons-vue'

import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 登录后若 Pinia 还没用户信息（刷新场景），拉取一次恢复 */
onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchProfile()
  }
})

async function onLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <!-- 管理区用简洁实底顶栏（写作场景以效率为先，弱化氛围特效） -->
    <header class="admin-nav">
      <div class="nav-left">
        <span class="brand">✦ 紫云 · 管理后台</span>
      </div>
      <div class="nav-right">
        <el-button :icon="ArrowLeft" link @click="router.push('/')">返回博客</el-button>
        <span class="user-name">{{ userStore.userInfo?.nickname ?? userStore.userInfo?.username }}</span>
        <el-button :icon="SwitchButton" link @click="onLogout">退出</el-button>
      </div>
    </header>

    <div class="admin-body">
      <!-- 侧栏 -->
      <aside class="admin-side">
        <div class="side-item active">
          <el-icon><List /></el-icon>
          <span>文章管理</span>
        </div>
        <div class="side-item" @click="router.push('/admin/articles/new')">
          <el-icon><DocumentAdd /></el-icon>
          <span>写文章</span>
        </div>
      </aside>

      <!-- 内容区 -->
      <main class="admin-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}

.admin-nav {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--bg-nav);
  backdrop-filter: blur(var(--glass-blur));
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 100;
}

.brand {
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--text-main);
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--text-secondary);
}

.user-name {
  font-size: 14px;
  color: var(--text-main);
}

.admin-body {
  flex: 1;
  display: flex;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}

.admin-side {
  width: 200px;
  padding: 24px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 10px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
}

.side-item:hover {
  color: var(--text-main);
  background: var(--bg-card);
}

.side-item.active {
  color: var(--color-primary);
  background: var(--bg-card);
  font-weight: 600;
}

.admin-main {
  flex: 1;
  padding: 24px;
  min-width: 0;
}

@media (max-width: 768px) {
  .admin-side {
    width: 64px;
  }
  .side-item span {
    display: none;
  }
}
</style>
