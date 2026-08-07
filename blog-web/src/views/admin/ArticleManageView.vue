<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'

import { deleteArticle, getArticleManagePage, updateArticleStatus } from '@/api/article'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem, ArticleStatus } from '@/types'

const router = useRouter()

const articles = ref<ArticleListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const statusFilter = ref<ArticleStatus | ''>('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await getArticleManagePage({
      page: page.value,
      size: size.value,
      status: statusFilter.value === '' ? undefined : statusFilter.value,
    })
    articles.value = data.records
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

function onStatusFilterChange() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function goEdit(id: string) {
  router.push(`/admin/articles/${id}/edit`)
}

function goNew() {
  router.push('/admin/articles/new')
}

/** 状态切换：发布 <-> 草稿（幂等，后端同状态短路） */
async function toggleStatus(row: ArticleListItem) {
  const next: ArticleStatus = row.status === 1 ? 0 : 1
  await updateArticleStatus(row.id, { status: next })
  ElMessage.success(next === 1 ? '已发布' : '已转为草稿')
  load()
}

async function onDelete(row: ArticleListItem) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？删除后可在数据库恢复（逻辑删除）。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteArticle(row.id)
  ElMessage.success('已删除')
  load()
}

function formatDateTime(iso: string) {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
</script>

<template>
  <div class="article-manage">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-radio-group v-model="statusFilter" @change="onStatusFilterChange">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button :value="1">已发布</el-radio-button>
          <el-radio-button :value="0">草稿</el-radio-button>
        </el-radio-group>
      </div>
      <div class="toolbar-right">
        <el-button :icon="Refresh" circle @click="load" />
        <el-button type="primary" :icon="Plus" @click="goNew">写文章</el-button>
      </div>
    </div>

    <GlassCard padded="sm">
      <el-table v-loading="loading" :data="articles" style="width: 100%">
        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <span class="cell-title" @click="goEdit(row.id)">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ row.categoryName || '未分类' }}</template>
        </el-table-column>
        <el-table-column label="标签" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="t in row.tags" :key="t.id" size="small" class="cell-tag">{{ t.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="阅读" width="80" prop="viewCount" />
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button :icon="Edit" link type="primary" @click="goEdit(row.id)">编辑</el-button>
            <el-button
              :icon="Refresh"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '转草稿' : '发布' }}
            </el-button>
            <el-button :icon="Delete" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">暂无文章，点击右上角「写文章」开始创作</div>
        </template>
      </el-table>

      <div class="pagination" v-if="total > size">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
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

.cell-title {
  color: var(--text-main);
  font-weight: 600;
  cursor: pointer;
}
.cell-title:hover {
  color: var(--color-primary);
}

.cell-tag {
  margin-right: 6px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.empty {
  color: var(--text-muted);
  padding: 40px 0;
}
</style>
