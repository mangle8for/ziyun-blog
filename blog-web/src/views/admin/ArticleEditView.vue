<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadUserFile } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

import { createArticle, getArticleManageDetail, updateArticle } from '@/api/article'
import { createCategory, getCategoryList } from '@/api/category'
import { createTag, getTagList } from '@/api/tag'
import { uploadFile } from '@/api/file'
import { useTheme } from '@/utils/theme'
import type { ArticlePayload, Category, Tag } from '@/types'

const route = useRoute()
const router = useRouter()
const { theme } = useTheme()

/** 编辑模式：路由带 id 为编辑，否则新建 */
const editId = computed(() => (route.params.id as string) || '')
const isEdit = computed(() => !!editId.value)

// ---------- 表单状态 ----------
const title = ref('')
const summary = ref('')
const content = ref('')
const cover = ref('')
const categoryId = ref<string>('')
const selectedTags = ref<string[]>([]) // 元素可能是已有标签 id，也可能是 allow-create 的新名字
const saving = ref(false)
const loading = ref(false)

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])

// ---------- 编辑器图片上传 ----------
/**
 * md-editor-v3 的图片上传回调：把所选图片传到对象存储，
 * 组件拿到返回的 URL 数组后自动插入 Markdown 图片语法。
 */
async function onUploadImg(files: File[], callback: (urls: string[]) => void) {
  try {
    const urls = await Promise.all(files.map((f) => uploadFile(f)))
    callback(urls)
  } catch {
    ElMessage.error('图片上传失败')
  }
}

// ---------- 封面上传 ----------
const coverFileList = ref<UploadUserFile[]>([])

async function onCoverChange(file: { raw?: File }) {
  if (!file.raw) return
  const url = await uploadFile(file.raw)
  cover.value = url
  ElMessage.success('封面已上传')
}

// ---------- 数据加载 ----------
async function loadOptions() {
  const [cats, tgs] = await Promise.all([getCategoryList(), getTagList()])
  categories.value = cats
  tags.value = tgs
}

/** 编辑模式：加载文章（含草稿）回填表单 */
async function loadArticle() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const data = await getArticleManageDetail(editId.value)
    title.value = data.title
    summary.value = data.summary ?? ''
    content.value = data.content
    cover.value = data.cover ?? ''
    categoryId.value = data.categoryId ?? ''
    selectedTags.value = data.tags?.map((t) => t.id) ?? []
  } finally {
    loading.value = false
  }
}

// ---------- 分类/标签的即时创建 ----------
const newCategoryName = ref('')
const newCategoryDialog = ref(false)

async function onCreateCategory() {
  if (!newCategoryName.value.trim()) return
  const id = await createCategory({ name: newCategoryName.value.trim() })
  ElMessage.success('分类已创建')
  newCategoryName.value = ''
  newCategoryDialog.value = false
  await loadOptions()
  categoryId.value = id
}

/**
 * 提交前处理标签：allow-create 产生的新名字先逐条建标签拿 id。
 * 区分方式：纯数字串视为已有 id，否则按新名字创建。
 */
async function resolveTagIds(): Promise<string[]> {
  const ids: string[] = []
  for (const item of selectedTags.value) {
    if (/^\d+$/.test(item)) {
      ids.push(item)
    } else {
      const id = await createTag({ name: item })
      ids.push(id)
    }
  }
  return ids
}

// ---------- 保存 ----------
function buildPayload(status: 0 | 1, tagIds: string[]): ArticlePayload {
  return {
    title: title.value.trim(),
    summary: summary.value.trim() || undefined,
    content: content.value,
    cover: cover.value || undefined,
    categoryId: categoryId.value || undefined,
    tagIds,
    status,
  }
}

async function save(status: 0 | 1) {
  if (!title.value.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('请填写正文')
    return
  }
  saving.value = true
  try {
    const tagIds = await resolveTagIds()
    const payload = buildPayload(status, tagIds)
    if (isEdit.value) {
      await updateArticle(editId.value, payload)
      ElMessage.success(status === 1 ? '已更新并发布' : '已保存草稿')
    } else {
      await createArticle(payload)
      ElMessage.success(status === 1 ? '已发布' : '已保存草稿')
    }
    router.push('/admin/articles')
  } finally {
    saving.value = false
  }
}

async function onPublish() {
  if (isEdit.value) {
    await save(1)
  } else {
    await ElMessageBox.confirm('确认发布？发布后前台立即可见。', '发布确认', {
      confirmButtonText: '发布',
      cancelButtonText: '再想想',
    })
    await save(1)
  }
}

onMounted(async () => {
  await loadOptions()
  await loadArticle()
})
</script>

<template>
  <div class="article-edit" v-loading="loading">
    <!-- 顶部：标题 + 操作 -->
    <div class="edit-header">
      <el-input
        v-model="title"
        placeholder="文章标题"
        size="large"
        class="title-input"
        maxlength="200"
        show-word-limit
      />
      <div class="edit-actions">
        <el-button @click="router.push('/admin/articles')">取消</el-button>
        <el-button :loading="saving" @click="save(0)">存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="onPublish">发布</el-button>
      </div>
    </div>

    <!-- 元信息：摘要/封面/分类/标签 -->
    <div class="meta-panel">
      <el-input
        v-model="summary"
        type="textarea"
        :rows="2"
        placeholder="摘要（选填，最多 500 字）"
        maxlength="500"
        show-word-limit
      />

      <div class="meta-row">
        <!-- 封面 -->
        <div class="meta-item">
          <span class="meta-label">封面</span>
          <el-upload
            :file-list="coverFileList"
            :show-file-list="false"
            accept="image/*"
            :auto-upload="false"
            :on-change="onCoverChange"
          >
            <el-button size="small">上传封面</el-button>
          </el-upload>
          <img v-if="cover" :src="cover" class="cover-preview" alt="封面预览" />
        </div>

        <!-- 分类 -->
        <div class="meta-item">
          <span class="meta-label">分类</span>
          <el-select v-model="categoryId" placeholder="选择分类" clearable size="small" style="width: 180px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-button size="small" link type="primary" @click="newCategoryDialog = true">新建</el-button>
        </div>

        <!-- 标签（可多选 + 即时创建） -->
        <div class="meta-item">
          <span class="meta-label">标签</span>
          <el-select
            v-model="selectedTags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入新标签"
            size="small"
            style="width: 320px"
          >
            <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- Markdown 编辑器 -->
    <MdEditor
      v-model="content"
      :theme="theme"
      class="md-editor"
      placeholder="开始写作... 支持 Markdown、图片（拖拽或工具栏上传）、代码块、表格等"
      :on-upload-img="onUploadImg"
    />

    <!-- 新建分类对话框 -->
    <el-dialog v-model="newCategoryDialog" title="新建分类" width="380px">
      <el-input v-model="newCategoryName" placeholder="分类名" maxlength="50" />
      <template #footer>
        <el-button @click="newCategoryDialog = false">取消</el-button>
        <el-button type="primary" @click="onCreateCategory">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.article-edit {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.edit-header {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.title-input {
  flex: 1;
  min-width: 280px;
}

.title-input :deep(.el-input__wrapper) {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  box-shadow: none;
  font-size: 18px;
  font-weight: 600;
}

.edit-actions {
  display: flex;
  gap: 10px;
}

.meta-panel {
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.meta-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  align-items: center;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-label {
  color: var(--text-muted);
  font-size: 13px;
}

.cover-preview {
  width: 72px;
  height: 46px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.md-editor {
  height: 70vh;
  border-radius: 12px;
  border: 1px solid var(--border-color);
}
</style>
