<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'

import { createCategory, deleteCategory, getCategoryList, updateCategory } from '@/api/category'
import GlassCard from '@/components/GlassCard.vue'
import type { Category, CategoryPayload } from '@/types'

const list = ref<Category[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentId = ref('')
const form = ref<CategoryPayload>({ name: '', description: '' })
const formRef = ref()

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    list.value = await getCategoryList()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  currentId.value = ''
  form.value = { name: '', description: '' }
  dialogVisible.value = true
}

function openEdit(row: Category) {
  isEdit.value = true
  currentId.value = row.id
  form.value = { name: row.name, description: row.description }
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateCategory(currentId.value, form.value)
    ElMessage.success('分类已更新')
  } else {
    await createCategory(form.value)
    ElMessage.success('分类已创建')
  }
  dialogVisible.value = false
  load()
}

async function onDelete(row: Category) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？若该分类下还有文章将无法删除。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  load()
}

function formatDateTime(iso: string) {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
</script>

<template>
  <div class="category-manage">
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="page-title">分类管理</span>
      </div>
      <div class="toolbar-right">
        <el-button :icon="Refresh" circle @click="load" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增分类</el-button>
      </div>
    </div>

    <GlassCard padded="sm">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column label="分类名称" min-width="180" prop="name" />
        <el-table-column label="描述" min-width="260">
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Edit" link type="primary" @click="openEdit(row as Category)">编辑</el-button>
            <el-button :icon="Delete" link type="danger" @click="onDelete(row as Category)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">暂无分类，点击右上角「新增分类」创建</div>
        </template>
      </el-table>
    </GlassCard>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑分类' : '新增分类'" width="420px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="分类名称" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="分类描述（可选）" maxlength="128" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
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

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.empty {
  color: var(--text-muted);
  padding: 40px 0;
}
</style>
