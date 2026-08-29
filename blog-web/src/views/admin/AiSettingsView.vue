<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { MagicStick, Plus, Refresh, Star } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

import GlassCard from '@/components/GlassCard.vue'
import {
  createAiProvider,
  deleteAiProvider,
  getAiProviders,
  setDefaultAiProvider,
  testAiProvider,
  updateAiProvider,
} from '@/api/ai'
import type { AiProvider } from '@/types'

/**
 * AI 模型供应商设置页（管理端）。
 *
 * <p>布局：左列供应商卡片列表（点选切换），右列编辑表单 ——
 * 配置一个 OpenAI 兼容的 API 端点与模型列表，供编辑器 AI 助手使用。</p>
 *
 * <p>安全设计：API Key 后端加密存储、接口只回显掩码；
 * 表单留空提交 = 保留原 Key。</p>
 */

const providers = ref<AiProvider[]>([])
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

/** 当前编辑中的供应商 id（空 = 新建未保存） */
const editingId = ref<string>('')
/** 表单是否处于「新建」模式（与编辑已保存供应商区分：影响按钮文案与 Key 占位） */
const isNew = ref(true)

const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  baseUrl: '',
  apiKey: '',
  models: [] as string[],
  modelInput: '',
  enabled: true,
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  baseUrl: [
    { required: true, message: '请输入 Base URL', trigger: 'blur' },
    {
      pattern: /^https?:\/\/\S+$/,
      message: '必须以 http(s):// 开头',
      trigger: 'blur',
    },
  ],
}

/** 当前选中供应商（右侧表单顶部信息条用） */
const current = computed(() => providers.value.find((p) => p.id === editingId.value))

/** 表单 API Key 输入框占位：编辑已有供应商时提示留空保留 */
const apiKeyPlaceholder = computed(() =>
  isNew.value || !current.value ? '输入 API Key' : `已保存 ${current.value.apiKeyMasked}，留空不修改`,
)

async function load() {
  loading.value = true
  try {
    providers.value = await getAiProviders()
    // 列表刷新后若当前选中项已被删除，回落到第一项
    if (editingId.value && !providers.value.some((p) => p.id === editingId.value)) {
      select(providers.value[0])
    }
  } finally {
    loading.value = false
  }
}

/** 填充表单为指定供应商（编辑态）；不传 = 清空为新建态 */
function select(provider?: AiProvider) {
  editingId.value = provider?.id ?? ''
  isNew.value = !provider
  form.name = provider?.name ?? ''
  form.baseUrl = provider?.baseUrl ?? ''
  form.apiKey = ''
  form.models = [...(provider?.models ?? [])]
  form.modelInput = ''
  form.enabled = provider ? provider.enabled === 1 : true
  form.remark = provider?.remark ?? ''
  formRef.value?.clearValidate()
}

function startCreate() {
  select(undefined)
}

function addModel() {
  const id = form.modelInput.trim()
  if (!id) return
  if (form.models.includes(id)) {
    ElMessage.warning('该模型已存在')
  } else {
    form.models.push(id)
  }
  form.modelInput = ''
}

function removeModel(id: string) {
  form.models = form.models.filter((m) => m !== id)
}

function buildPayload() {
  const enabled: 0 | 1 = form.enabled ? 1 : 0
  return {
    name: form.name.trim(),
    baseUrl: form.baseUrl.trim(),
    apiKey: form.apiKey.trim() || undefined,
    models: form.models,
    enabled,
    remark: form.remark.trim() || undefined,
  }
}

async function save() {
  await formRef.value?.validate().catch(() => Promise.reject(new Error('invalid')))
  if (!form.models.length) {
    ElMessage.warning('请至少添加一个模型')
    return
  }
  if (isNew.value && !form.apiKey.trim()) {
    ElMessage.warning('请输入 API Key')
    return
  }
  saving.value = true
  try {
    if (isNew.value) {
      const id = await createAiProvider(buildPayload())
      ElMessage.success('供应商已添加')
      await load()
      select(providers.value.find((p) => p.id === id))
    } else {
      await updateAiProvider(editingId.value, buildPayload())
      ElMessage.success('已保存')
      await load()
      select(providers.value.find((p) => p.id === editingId.value))
    }
  } catch {
    // 错误提示由请求层统一弹出
  } finally {
    saving.value = false
  }
}

async function setDefault() {
  if (isNew.value) {
    ElMessage.warning('请先保存供应商')
    return
  }
  await setDefaultAiProvider(editingId.value)
  ElMessage.success('已设为写作默认模型')
  await load()
}

async function test() {
  if (isNew.value) {
    ElMessage.warning('请先保存供应商再测试')
    return
  }
  testing.value = true
  try {
    ElMessage.success(await testAiProvider(editingId.value))
  } catch {
    // 失败原因由请求层弹出
  } finally {
    testing.value = false
  }
}

async function remove() {
  if (isNew.value) return
  await ElMessageBox.confirm(
    `确定删除供应商「${form.name}」？删除后编辑器 AI 助手将不可用（若它是默认模型）。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteAiProvider(editingId.value)
  ElMessage.success('已删除')
  await load()
  select(providers.value[0])
}

onMounted(load)
</script>

<template>
  <div class="ai-settings">
    <!-- 顶部：标题 + 说明 + 刷新 -->
    <div class="toolbar">
      <div>
        <h2 class="page-title">模型设置</h2>
        <p class="page-desc">
          管理编辑器 AI 助手使用的模型供应商（仅支持 OpenAI 兼容接口：智谱 GLM / DeepSeek /
          通义 / Moonshot / OpenAI 等）。API Key 加密存储，不会明文回显。
        </p>
      </div>
      <el-button :icon="Refresh" circle @click="load" />
    </div>

    <div class="layout">
      <!-- 左列：供应商列表 -->
      <GlassCard padded="sm" class="provider-list">
        <div class="group-label">供应商</div>
        <button
          v-for="p in providers"
          :key="p.id"
          class="provider-item"
          :class="{ active: p.id === editingId }"
          type="button"
          @click="select(p)"
        >
          <span class="p-icon"><el-icon><MagicStick /></el-icon></span>
          <span class="p-name">{{ p.name }}</span>
          <el-tooltip v-if="p.isDefault === 1" content="写作默认" placement="top">
            <el-icon class="p-badge default"><Star /></el-icon>
          </el-tooltip>
          <span v-else class="p-badge" :class="p.enabled === 1 ? 'on' : 'off'" />
        </button>
        <button class="provider-item add" type="button" @click="startCreate">
          <el-icon><Plus /></el-icon>
          添加供应商
        </button>
      </GlassCard>

      <!-- 右列：编辑表单 -->
      <GlassCard padded="md" class="provider-form-wrap">
        <template v-if="!current && !isNew">
          <el-empty description="左侧选择供应商，或点击「添加供应商」开始配置" />
        </template>
        <template v-else>
          <div class="form-head">
            <h3 class="form-title">{{ isNew ? '添加模型供应商' : '编辑供应商' }}</h3>
            <span class="form-sub">配置一个 OpenAI 兼容的 API 端点和模型列表</span>
          </div>

          <el-form
            ref="formRef"
            class="provider-form"
            :model="form"
            :rules="rules"
            label-position="top"
            @submit.prevent
          >
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="如：智谱 GLM" maxlength="50" />
            </el-form-item>

            <el-form-item label="Base URL" prop="baseUrl">
              <el-input
                v-model="form.baseUrl"
                placeholder="https://open.bigmodel.cn/api/paas/v4"
                maxlength="255"
              />
            </el-form-item>

            <el-form-item label="API Key" :prop="isNew ? 'apiKey' : undefined">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="apiKeyPlaceholder"
                autocomplete="new-password"
              />
            </el-form-item>

            <el-form-item label="模型列表">
              <div class="model-input-row">
                <el-input
                  v-model="form.modelInput"
                  placeholder="模型 ID，如 glm-4.6"
                  @keydown.enter.prevent="addModel"
                >
                  <template #append>
                    <el-button :icon="Plus" @click="addModel" />
                  </template>
                </el-input>
              </div>
              <div v-if="form.models.length" class="model-tags">
                <el-tag
                  v-for="m in form.models"
                  :key="m"
                  closable
                  :type="m === form.modelInput ? 'info' : 'primary'"
                  @close="removeModel(m)"
                >
                  {{ m }}
                </el-tag>
              </div>
              <div v-else class="model-empty">当前没有配置模型，添加后编辑器 AI 助手才能使用。</div>
            </el-form-item>

            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" maxlength="255" />
            </el-form-item>

            <div class="form-switch-row">
              <span class="switch-label">启用</span>
              <el-switch v-model="form.enabled" />
            </div>
          </el-form>

          <div class="form-actions">
            <div class="actions-left">
              <el-button
                :icon="Star"
                :disabled="isNew || current?.isDefault === 1"
                @click="setDefault"
              >
                {{ current?.isDefault === 1 ? '写作默认' : '设为默认' }}
              </el-button>
              <el-button :loading="testing" :disabled="isNew" @click="test">测试连接</el-button>
            </div>
            <div class="actions-right">
              <el-button type="danger" plain :disabled="isNew" @click="remove">删除</el-button>
              <el-button type="primary" :loading="saving" @click="save">
                {{ isNew ? '添加供应商' : '保存' }}
              </el-button>
            </div>
          </div>
        </template>
      </GlassCard>
    </div>
  </div>
</template>

<style scoped>
.ai-settings {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
  max-width: 640px;
  line-height: 1.7;
}

.layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

/* ---------- 左列供应商列表 ---------- */
.provider-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-label {
  font-size: 12px;
  color: var(--text-muted);
  padding: 2px 4px 6px;
}

.provider-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.provider-item:hover {
  background: var(--bg-page);
  color: var(--text-main);
}

.provider-item.active {
  background: var(--bg-page);
  color: var(--color-primary);
  font-weight: 600;
}

.p-icon {
  display: inline-flex;
  font-size: 15px;
}

.p-name {
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 状态角标：默认=星标；普通=启用绿点/停用灰点 */
.p-badge {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.p-badge.on {
  background: #46a758;
}

.p-badge.off {
  background: var(--text-muted);
  opacity: 0.5;
}

.p-badge.default {
  width: auto;
  height: auto;
  background: none;
  color: var(--color-accent);
  font-size: 15px;
}

.provider-item.add {
  border: 1px dashed var(--border-color);
  justify-content: center;
  color: var(--text-muted);
  margin-top: 4px;
}

.provider-item.add:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

/* ---------- 右列表单 ---------- */
.form-head {
  margin-bottom: 18px;
}

.form-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.form-sub {
  font-size: 13px;
  color: var(--text-muted);
}

.provider-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: 13px;
  padding-bottom: 4px;
}

.model-input-row {
  width: 100%;
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.model-empty {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.form-switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.switch-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
  flex-wrap: wrap;
}

.actions-left,
.actions-right {
  display: flex;
  gap: 10px;
}
</style>
