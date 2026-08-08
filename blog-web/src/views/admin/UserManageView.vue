<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Key, Plus, Refresh, Search, User } from '@element-plus/icons-vue'

import { createUser, deleteUser, getUserPage, resetUserPassword, updateUserStatus } from '@/api/user'
import GlassCard from '@/components/GlassCard.vue'
import type { CreateUserPayload, UserAdmin } from '@/types'

const list = ref<UserAdmin[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
})

// ==================== 新增用户 ====================

const createDialogVisible = ref(false)
const createLoading = ref(false)
const createForm = ref<CreateUserPayload>({ username: '', password: '', nickname: '' })
const createFormRef = ref()

const createRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '4-20 位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须在 6-32 位', trigger: 'blur' },
  ],
  nickname: [{ max: 50, message: '昵称不能超过 50 字', trigger: 'blur' }],
}

function openCreate() {
  createForm.value = { username: '', password: '', nickname: '' }
  createDialogVisible.value = true
}

async function onCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  createLoading.value = true
  try {
    await createUser(createForm.value)
    ElMessage.success(`用户「${createForm.value.nickname || createForm.value.username}」创建成功，默认普通角色`)
    createDialogVisible.value = false
    load()
  } finally {
    createLoading.value = false
  }
}

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
        <el-button :icon="Plus" type="primary" plain @click="openCreate">新增用户</el-button>
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

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新增用户" width="420px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="4-20 位字母、数字或下划线" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="6-32 位" maxlength="32" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" placeholder="选填，默认与用户名相同" maxlength="50" show-word-limit />
        </el-form-item>
      </el-form>
      <div class="create-tip">新用户默认为普通角色（游客），创建后可将账号告知对方登录</div>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>
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

.create-tip {
  font-size: 12px;
  color: var(--text-muted);
  padding: 0 2px;
}
</style>
