<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import StarfieldBackground from '@/components/StarfieldBackground.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
/** 记住密码：默认勾选（token 持久化自动登录 + 记住用户名回填） */
const remember = ref(true)

const form = reactive({
  username: '',
  password: '',
})

// 回填上次勾选「记住密码」时记住的用户名（未记住则为空串）
const rememberedUsername = localStorage.getItem('ziyun-blog-username') ?? ''
form.username = rememberedUsername

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须在 6-32 位', trigger: 'blur' },
  ],
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password }, remember.value)
    ElMessage.success('欢迎回来')
    // 登录成功回跳来源页，缺省进管理后台
    const redirect = (route.query.redirect as string) || '/admin'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-view">
    <StarfieldBackground />
    <div class="login-panel">
      <div class="login-brand">
        <span class="brand-icon">✦</span>
        <span class="brand-name">紫云博客</span>
      </div>
      <p class="login-future">后续将支持手机号验证码注册游客账号、评论与 @Async 异步通知</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <!--
          记住密码（安全实现：不存密码明文）：
          - 勾选：登录态持久化（关闭浏览器仍保持登录）+ 记住用户名下次回填
          - 不勾选：仅本次会话有效（关闭浏览器即退出），浏览器公共设备建议不勾选
        -->
        <div class="remember-row">
          <el-checkbox v-model="remember">记住密码</el-checkbox>
        </div>
        <el-button class="submit-btn" type="primary" :loading="loading" round @click="onSubmit">
          登 录
        </el-button>
      </el-form>

      <el-button class="back-link" link @click="router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.login-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login-panel {
  width: 100%;
  max-width: 380px;
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border-color);
  border-radius: 18px;
  box-shadow: var(--shadow-card);
  padding: 40px 36px;
  text-align: center;
  animation: panel-in 0.5s ease both;
}

@keyframes panel-in {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.login-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
}
.brand-icon {
  font-size: 26px;
  color: var(--color-accent);
}
.brand-name {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 3px;
  color: var(--text-main);
}

.login-future {
  color: var(--text-muted);
  margin: 0 0 24px;
  font-size: 12px;
  line-height: 1.6;
  opacity: 0.85;
}

/* 记住密码行：左对齐，与表单同宽 */
.remember-row {
  display: flex;
  justify-content: flex-start;
  margin: -4px 0 12px;
}

.remember-row :deep(.el-checkbox__label) {
  color: var(--text-secondary);
  font-size: 13px;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  letter-spacing: 6px;
}

.back-link {
  margin-top: 18px;
  color: var(--text-muted);
}
</style>
