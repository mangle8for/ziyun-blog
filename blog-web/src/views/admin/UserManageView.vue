<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Key, Refresh, Search, User } from '@element-plus/icons-vue'

import { deleteUser, getUserPage, resetUserPassword, updateUserStatus } from '@/api/user'
import GlassCard from '@/components/GlassCard.vue'
import type { UserAdmin } from '@/types'

const list = ref<UserAdmin[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
})

async function load() {
  loading.value = true
  try {
    const result = await getUserPage({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    list.value = result.records
    total.value = Number(result.total)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

async function onToggleStatus(row: UserAdmin, status: 0 | 1) {
  // 禁用前二次确认：会立即踢下线，误操作影响用户使用
  if (status === 0) {
    try {
      await ElMessageBox.confirm(
        `确认禁用用户「${row.nickname ?? row.username}」？禁用后该用户立即被强制下线且无法登录。`,
        '禁用确认',
        { type: 'warning', confirmButtonText: '禁用', cancelButtonText: '取消' },
      )
    } catch {
      return // 取消：不调用接口
    }
  }
  await updateUserStatus(row.id, { status })
  ElMessage.success(status === 1 ? '已启用' : '已禁用')
  load()
}

async function onResetPassword(row: UserAdmin) {
  await ElMessageBox.confirm(
    `确认重置用户「${row.nickname ?? row.username}」的密码？重置后旧密码失效，该用户将被强制下线。`,
    '重置密码',
    { type: 'warning', confirmButtonText: '重置', cancelButtonText: '取消' },
  )
  const newPassword = await resetUserPassword(row.id)
  // 随机密码只展示这一次，提醒管理员妥善转达
  await ElMessageBox.alert(
    `用户「${row.nickname ?? row.username}」的新密码为：\n\n${newPassword}\n\n请立即告知用户，关闭后不再展示。`,
    '新密码（仅此一次）',
    { confirmButtonText: '我已复制', type: 'success' },
  )
  load()
}

async function onDelete(row: UserAdmin) {
  await ElMessageBox.confirm(
    `确认删除用户「${row.nickname ?? row.username}」？删除为逻辑删除，用户名下仍有文章时将无法删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteUser(row.id)
  ElMessage.success('已删除')
  // 删除后当前页可能空，回退一页
  if (list.value.length === 1 && query.page > 1) {
    query.page -= 1
  }
  load()
}

function formatDateTime(iso: string) {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
</script>

<template>
  <div class="user-manage">
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="page-title">用户管理</span>
      </div>
      <div class="toolbar-right">
        <el-input
          v-model="query.keyword"
          class="search-input"
          placeholder="搜索用户名/昵称"
          clearable
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Refresh" circle @click="load" />
      </div>
    </div>

    <GlassCard padded="sm">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" :src="row.avatar || undefined" :icon="User" />
              <div class="user-meta">
                <span class="user-name">{{ row.nickname ?? row.username }}</span>
                <span class="user-username">@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="180">
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 1 ? 'danger' : 'info'" effect="plain" size="small">
              {{ row.role === 1 ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="onToggleStatus(row as UserAdmin, (row as UserAdmin).status === 1 ? 0 : 1)"
            />
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Key" link type="primary" @click="onResetPassword(row as UserAdmin)">重置密码</el-button>
            <el-button :icon="Delete" link type="danger" @click="onDelete(row as UserAdmin)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">暂无用户</div>
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.search-input {
  width: 220px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.user-name {
  color: var(--text-main);
  font-size: 14px;
}

.user-username {
  color: var(--text-muted);
  font-size: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.empty {
  color: var(--text-muted);
  padding: 40px 0;
}
</style>
