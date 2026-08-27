<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

import { getArticlePage } from '@/api/article'
import { getCategoryList } from '@/api/category'
import { getTagList } from '@/api/tag'
import ArticleRowSkeleton from '@/components/ArticleRowSkeleton.vue'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem, Category, Tag } from '@/types'

const route = useRoute()
const router = useRouter()

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const categoryId = ref<string>('')
const tagId = ref<string>('')
/** 时间范围 [begin, end]，yyyy-MM-dd（后端闭区间含端点日） */
const dateRange = ref<[string, string] | ''>('')

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const results = ref<ArticleListItem[]>([])
const total = ref(0)
const loading = ref(false)
/** 是否已执行过搜索（区分「还没搜」与「搜了没结果」） */
const searched = ref(false)

// SEO
useHead({
  title: '搜索',
  meta: () => [{ name: 'description', content: '按标题、分类、标签、时间搜索紫云博客文章。' }],
})

/**
 * 执行搜索：标题关键字 + 分类 + 标签 + 时间范围可任意组合。
 * 关键字同步到路由 query（?q=），刷新/分享后仍能还原结果。
 */
async function doSearch() {
  loading.value = true
  try {
    const data = await getArticlePage({
      page: 1,
      size: 20,
      keyword: keyword.value.trim() || undefined,
      categoryId: categoryId.value || undefined,
      tagId: tagId.value || undefined,
      beginDate: dateRange.value ? dateRange.value[0] : undefined,
      endDate: dateRange.value ? dateRange.value[1] : undefined,
    })
    results.value = data.records
    total.value = Number(data.total)
    searched.value = true
    // 关键字进 URL（replace 不留历史噪音；空串清掉参数）
    router.replace({ query: { q: keyword.value.trim() || undefined } })
  } finally {
    loading.value = false
  }
}

/** 输入防抖 400ms 自动搜索（筛选变化立即搜索） */
let debounceTimer: number | undefined
function onKeywordInput() {
  window.clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(doSearch, 400)
}

function onFilterChange() {
  window.clearTimeout(debounceTimer)
  doSearch()
}

function onRangeChange() {
  onFilterChange()
}

function goDetail(id: string) {
  router.push(`/article/${id}`)
}

function formatDate(iso: string) {
  return iso?.slice(0, 10) ?? ''
}

onMounted(async () => {
  // 分类/标签下拉数据量小，直接全量拉取
  const [cats, tgs] = await Promise.all([getCategoryList(), getTagList()])
  categories.value = cats
  tags.value = tgs
  // 带着(?q=)进来时自动搜一次
  if (keyword.value.trim()) doSearch()
})

onBeforeUnmount(() => {
  window.clearTimeout(debounceTimer)
})
</script>

<template>
  <div class="search-view">
    <header class="page-header">
      <h1 class="page-title">搜索</h1>
      <p class="page-sub">在星海中定位每一篇文章</p>
    </header>

    <!-- 搜索面板：大输入框 + 分类/标签/时间筛选 -->
    <GlassCard padded="lg" class="search-panel" no-hover>
      <div class="search-bar">
        <el-input
          v-model="keyword"
          placeholder="输入文章标题关键字…"
          size="large"
          clearable
          @input="onKeywordInput"
          @keyup.enter="onFilterChange"
          @clear="onFilterChange"
        >
          <template #append>
            <el-button :icon="Search" @click="onFilterChange">搜索</el-button>
          </template>
        </el-input>
      </div>

      <div class="filter-bar">
        <el-select
          v-model="categoryId"
          placeholder="全部分类"
          clearable
          filterable
          class="filter-select"
          @change="onFilterChange"
        >
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>

        <el-select
          v-model="tagId"
          placeholder="全部标签"
          clearable
          filterable
          class="filter-select"
          @change="onFilterChange"
        >
          <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>

        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="filter-date"
          @change="onRangeChange"
        />
      </div>
    </GlassCard>

    <!-- 结果列表：简单行卡，点击直达文章 -->
    <section class="result-list" v-loading="loading && searched && results.length > 0">
      <template v-if="loading && results.length === 0">
        <p class="receiving">正在扫描星海<i></i><i></i><i></i></p>
        <ArticleRowSkeleton v-for="n in 5" :key="n" :index="n - 1" />
      </template>

      <template v-else>
        <div v-if="searched" class="result-stat">
          共找到 <strong>{{ total }}</strong> 篇相关文章
        </div>

        <GlassCard
          v-for="a in results"
          :key="a.id"
          class="result-row"
          padded="sm"
          @click="goDetail(a.id)"
        >
          <div class="row-main">
            <span class="row-title">{{ a.title }}</span>
            <span class="row-summary">{{ a.summary }}</span>
            <span class="row-chips">
              <span v-if="a.categoryName" class="mini-chip">{{ a.categoryName }}</span>
              <span v-for="t in a.tags" :key="t.id" class="mini-chip tag"># {{ t.name }}</span>
            </span>
          </div>
          <span class="row-date">{{ formatDate(a.createTime) }}</span>
        </GlassCard>

        <div v-if="searched && !loading && results.length === 0" class="empty">
          这片星域没有找到相关文章，换个关键词试试
        </div>
        <div v-if="!searched && !loading" class="empty">
          输入关键字开始搜索 —— 支持标题 / 分类 / 标签 / 时间
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.page-header {
  text-align: center;
  padding: 20px 0 24px;
}
.page-title {
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 4px;
  margin: 0;
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.page-sub {
  color: var(--text-secondary);
  margin-top: 10px;
  letter-spacing: 2px;
}

.search-panel {
  max-width: 760px;
  margin: 0 auto 28px;
}

.search-bar :deep(.el-input__wrapper) {
  background: var(--bg-card);
  border-radius: 999px 0 0 999px;
  box-shadow: none;
  border: 1px solid var(--border-color);
  padding-left: 20px;
}
.search-bar :deep(.el-input-group__append) {
  border-radius: 0 999px 999px 0;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: #fff;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}
.filter-select {
  width: 170px;
}
.filter-date {
  flex: 1;
  min-width: 240px;
  max-width: 320px;
}
/* 日期/选择器控件融合主题 */
.filter-bar :deep(.el-select__wrapper),
.filter-bar :deep(.el-date-editor) {
  background: var(--bg-card);
  box-shadow: 0 0 0 1px var(--border-color) inset;
}
.filter-bar :deep(.el-date-editor.el-input__wrapper),
.filter-bar :deep(.el-range-editor.el-input__wrapper) {
  background: var(--bg-card);
  box-shadow: 0 0 0 1px var(--border-color) inset;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 160px;
  max-width: 760px;
  margin: 0 auto;
}

.result-stat {
  color: var(--text-secondary);
  font-size: 13px;
  letter-spacing: 1px;
  padding-left: 4px;
}
.result-stat strong {
  color: var(--color-primary);
}

/* 接收信号提示：三点渐次上浮（与分类/标签页同款加载语言） */
.receiving {
  display: flex;
  align-items: center;
  margin: 0 0 10px;
  padding-left: 4px;
  color: var(--text-muted);
  font-size: 12.5px;
  letter-spacing: 1px;
}
.receiving i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  margin-left: 5px;
  background: var(--color-primary);
  animation: rec-ping 1.2s ease-in-out infinite;
}
.receiving i:nth-of-type(2) {
  animation-delay: 0.2s;
}
.receiving i:nth-of-type(3) {
  animation-delay: 0.4s;
}
@keyframes rec-ping {
  0%,
  100% {
    opacity: 0.15;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
  animation: row-in 0.45s ease backwards;
}
@keyframes row-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.row-main {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}
.row-title {
  color: var(--text-main);
  font-weight: 600;
}
.row-summary {
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.row-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.mini-chip {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}
.mini-chip.tag {
  color: var(--color-primary);
  border-color: color-mix(in srgb, var(--color-primary) 35%, transparent);
}
.row-date {
  color: var(--text-muted);
  font-size: 12px;
  flex-shrink: 0;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 46px 0;
  letter-spacing: 1px;
}

@media (prefers-reduced-motion: reduce) {
  .result-row,
  .receiving i {
    animation: none;
  }
}
</style>
