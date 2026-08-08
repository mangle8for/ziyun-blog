<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { Lock, User as UserIcon } from '@element-plus/icons-vue'

import { updatePassword, updateProfile, uploadAvatar } from '@/api/auth'
import GlassCard from '@/components/GlassCard.vue'
import { useUserStore } from '@/stores/user'
import type { User } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const user = userStore.userInfo as User

// ==================== 头像（左栏，独立卡片） ====================

const avatarUploading = ref(false)

/** 上传前校验：图片类型 + 5MB（与后端白名单一致，先拦截给友好提示） */
function beforeAvatarUpload(file: File) {
  const allowed = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!allowed.includes(file.type)) {
    ElMessage.error('仅支持 JPG / PNG / WebP / GIF 图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

/** 自定义上传：走头像专用接口（后端每月限 3 次，429 时拦截器会弹出真实提示） */
async function handleAvatarUpload(options: UploadRequestOptions) {
  avatarUploading.value = true
  try {
    const url = await uploadAvatar(options.file as File)
    userStore.userInfo = { ...userStore.userInfo!, avatar: url }
    ElMessage.success('头像已更新（本月剩余次数以后端为准）')
  } finally {
    avatarUploading.value = false
  }
}

// ==================== 个人资料 ====================

const profileRef = ref<FormInstance>()
const profileLoading = ref(false)
const profileForm = reactive({
  nickname: user.nickname ?? user.username,
  email: user.email ?? '',
  avatar: user.avatar ?? '',
})

const profileRules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

async function onSaveProfile() {
  if (!profileRef.value) return
  await profileRef.value.validate()
  profileLoading.value = true
  try {
    const updated = await updateProfile({
      nickname: profileForm.nickname.trim(),
      email: profileForm.email.trim(),
      avatar: profileForm.avatar.trim(),
    })
    // 同步 Pinia 中的用户信息（顶栏昵称/头像即时生效）
    userStore.userInfo = updated
    ElMessage.success('个人资料已更新')
  } finally {
    profileLoading.value = false
  }
}

// ==================== 修改密码 ====================

const passwordRef = ref<FormInstance>()
const passwordLoading = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须在 6-32 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '密码须同时包含字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function onChangePassword() {
  if (!passwordRef.value) return
  await passwordRef.value.validate()
  passwordLoading.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    // 后端已清除全部登录态（含当前会话），本地清态后跳转登录页
    ElMessage.success('密码已修改，请重新登录')
    userStore.resetState()
    router.push('/login')
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <div class="profile-view">
    <h2 class="page-title">个人中心</h2>

    <div class="profile-grid">
      <!-- 左栏：头像独立卡片 -->
      <aside class="profile-side">
        <GlassCard padded="md" class="avatar-card">
          <el-avatar :size="96" :src="userStore.userInfo?.avatar || undefined" :icon="UserIcon" class="big-avatar" />
          <div class="avatar-name">{{ user.nickname ?? user.username }}</div>
          <div class="avatar-username">@{{ user.username }}</div>

          <el-upload
            class="avatar-upload"
            :show-file-list="false"
            accept="image/jpeg,image/png,image/webp,image/gif"
            :before-upload="beforeAvatarUpload"
            :http-request="handleAvatarUpload"
          >
            <el-button type="primary" plain :loading="avatarUploading" size="small">
              {{ avatarUploading ? '上传中…' : '更换头像' }}
            </el-button>
          </el-upload>
          <p class="avatar-tip">从本地选择图片上传<br />每月限修改 3 次</p>
        </GlassCard>
      </aside>

      <!-- 右栏：资料 + 密码 -->
      <div class="profile-main">
        <!-- 个人资料 -->
        <GlassCard padded="md">
          <div class="card-header">
            <el-icon class="header-icon"><UserIcon /></el-icon>
            <span>个人资料</span>
          </div>
          <el-form
            ref="profileRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="80px"
            class="profile-form"
          >
            <el-form-item label="用户名">
              <el-input :model-value="user.username" disabled />
              <div class="form-tip">用户名不可修改（登录凭证）</div>
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" maxlength="50" show-word-limit />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="选填，用于后续通知" maxlength="100" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="profileLoading" @click="onSaveProfile">保存修改</el-button>
            </el-form-item>
          </el-form>
        </GlassCard>

        <!-- 修改密码 -->
        <GlassCard padded="md" class="password-card">
          <div class="card-header">
            <el-icon class="header-icon"><Lock /></el-icon>
            <span>修改密码</span>
          </div>
          <el-form
            ref="passwordRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="80px"
            class="profile-form"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="passwordLoading" @click="onChangePassword">修改密码</el-button>
              <div class="form-tip">修改成功后所有设备将退出登录，需使用新密码重新登录</div>
            </el-form-item>
          </el-form>
        </GlassCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-view {
  max-width: 900px;
  margin: 0 auto;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 20px;
}

/* 左头像右内容的两栏布局（窄屏纵向堆叠） */
.profile-grid {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 20px;
  align-items: start;
}

.avatar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-top: 28px;
}

.big-avatar {
  background: var(--bg-page);
  color: var(--color-primary);
  border: 2px solid var(--border-color);
}

.avatar-name {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
}

.avatar-username {
  font-size: 12px;
  color: var(--text-muted);
  margin: 2px 0 14px;
}

.avatar-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 8px;
}

.header-icon {
  color: var(--color-primary);
}

.password-card {
  margin-top: 20px;
}

.profile-form {
  margin-top: 16px;
  max-width: 460px;
}

.form-tip {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
