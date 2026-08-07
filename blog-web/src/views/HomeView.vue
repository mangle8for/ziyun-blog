<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'

import { getArticlePage } from '@/api/article'
import { getCategoryList } from '@/api/category'
import { getTagList } from '@/api/tag'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem, Category, Tag } from '@/types'

const router = useRouter()

const articles = ref<ArticleListItem[]>([])
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(6)
const loading = ref(false)

/** 当前筛选条件（分类/标签/关键词） */
const activeCategoryId = ref<string>('')
const activeTagId = ref<string>('')
const keyword = ref('')

async function loadArticles() {
  loading.value = true
  try {
    const data = await getArticlePage({
      page: page.value,
      size: size.value,
      categoryId: activeCategoryId.value || undefined,
      tagId: activeTagId.value || undefined,
      keyword: keyword.value || undefined,
    })
    articles.value = data.records
    // total 是 string（Long 序列化），分页组件需要 number
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

async function loadFilters() {
  const [cats, tgs] = await Promise.all([getCategoryList(), getTagList()])
  categories.value = cats
  tags.value = tgs
}

function onFilterCategory(id: string) {
  activeCategoryId.value = activeCategoryId.value === id ? '' : id
  page.value = 1
  loadArticles()
}

function onFilterTag(id: string) {
  activeTagId.value = activeTagId.value === id ? '' : id
  page.value = 1
  loadArticles()
}

function onSearch() {
  page.value = 1
  loadArticles()
}

function onPageChange(p: number) {
  page.value = p
  loadArticles()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goDetail(id: string) {
  router.push(`/article/${id}`)
}

function formatDate(iso: string) {
  return iso?.slice(0, 10) ?? ''
}

onMounted(() => {
  loadArticles()
  loadFilters()
})
</script>

<template>
  <div class="home-view">
    <!-- 英雄区：站点标题 + 搜索 -->
    <section class="hero">
      <h1 class="hero-title">紫云博客</h1>
      <p class="hero-subtitle">星海拾遗 · 记录技术、思考与自然</p>
      <div class="hero-search">
        <el-input
          v-model="keyword"
          placeholder="搜索文章标题..."
          clearable
          size="large"
          class="search-input"
          @keyup.enter="onSearch"
          @clear="onSearch"
        >
          <template #append>
            <el-button @click="onSearch">搜索</el-button>
          </template>
        </el-input>
      </div>
    </section>

    <!-- 筛选区：分类 + 标签 -->
    <section class="filters" v-if="categories.length || tags.length">
      <div class="filter-row" v-if="categories.length">
        <span class="filter-label">分类</span>
        <div class="filter-chips">
          <span
            v-for="cat in categories"
            :key="cat.id"
            class="chip"
            :class="{ active: activeCategoryId === cat.id }"
            @click="onFilterCategory(cat.id)"
          >
            {{ cat.name }}
          </span>
        </div>
      </div>
      <div class="filter-row" v-if="tags.length">
        <span class="filter-label">标签</span>
        <div class="filter-chips">
          <span
            v-for="tag in tags"
            :key="tag.id"
            class="chip chip-tag"
            :class="{ active: activeTagId === tag.id }"
            @click="onFilterTag(tag.id)"
          >
            # {{ tag.name }}
          </span>
        </div>
      </div>
    </section>

    <!-- 文章卡片网格 -->
    <section v-loading="loading" class="article-grid">
      <GlassCard
        v-for="(article, index) in articles"
        :key="article.id"
        class="article-card"
        :style="{ animationDelay: `${index * 60}ms` }"
        padded="md"
      >
        <div class="card-body" @click="goDetail(article.id)">
          <!-- 封面：懒加载 + 渐显 -->
          <div class="cover-wrap" v-if="article.cover">
            <img :src="article.cover" :alt="article.title" loading="lazy" class="cover" />
          </div>
          <div class="card-text">
            <h2 class="card-title">{{ article.title }}</h2>
            <p class="card-summary">{{ article.summary || '（暂无摘要）' }}</p>
            <div class="card-meta">
              <span class="meta-item">{{ formatDate(article.createTime) }}</span>
              <span class="meta-item" v-if="article.categoryName">{{ article.categoryName }}</span>
              <span class="meta-item">{{ article.viewCount }} 阅读</span>
            </div>
            <div class="card-tags" v-if="article.tags?.length">
              <span v-for="tag in article.tags" :key="tag.id" class="tag-mini">#{{ tag.name }}</span>
            </div>
          </div>
          <div class="card-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </GlassCard>

      <!-- 空态 -->
      <div v-if="!loading && articles.length === 0" class="empty-state">
        <p>这片星海暂时没有文章</p>
      </div>
    </section>

    <!-- 分页 -->
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
  </div>
</template>

<style scoped>
.hero {
  text-align: center;
  padding: 48px 0 32px;
}

.hero-title {
  font-size: 42px;
  font-weight: 800;
  letter-spacing: 6px;
  margin: 0;
  /* 标题渐变色（科幻感） */
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-subtitle {
  margin: 12px 0 24px;
  color: var(--text-secondary);
  font-size: 15px;
  letter-spacing: 2px;
}

.hero-search {
  max-width: 520px;
  margin: 0 auto;
}

/* 搜索框半透明融合背景 */
.hero-search :deep(.el-input__wrapper) {
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur));
  border-radius: 999px 0 0 999px;
  box-shadow: none;
  border: 1px solid var(--border-color);
  padding-left: 20px;
}
.hero-search :deep(.el-input-group__append) {
  border-radius: 0 999px 999px 0;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: #fff;
}

.filters {
  margin-bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-label {
  color: var(--text-muted);
  font-size: 13px;
  min-width: 32px;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 5px 14px;
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(var(--glass-blur));
  transition: all 0.25s ease;
  user-select: none;
}

.chip:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  transform: translateY(-1px);
}

.chip.active {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

/* 文章卡片瀑布流：CSS 多列布局实现自适应错落，不强制等高管齐。
   列内卡片 break-inside 防断裂 + 底部间距，封面高度不一的卡片
   按内容自然堆积（如左列两张矮卡、右列一张带图高卡），
   比 Grid 等高网格更贴合「图片 + 摘要」类卡片场景。 */
.article-grid {
  column-count: 2;
  column-gap: 20px;
  min-height: 200px;
}

.article-card {
  cursor: pointer;
  width: 100%;
  break-inside: avoid;
  margin-bottom: 20px;
  /* 卡片入场浮起动画 */
  animation: card-in 0.5s ease both;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
}

.cover-wrap {
  border-radius: 10px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: var(--bg-page);
  flex-shrink: 0;
}

.card-text {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  /* 封面渐显 + hover 微放大 */
  animation: fade-in 0.5s ease both;
  transition: transform 0.4s ease;
}

.article-card:hover .cover {
  transform: scale(1.03);
}

@keyframes fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.card-title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: var(--text-main);
  /* 标题最多两行 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-summary {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  gap: 14px;
  color: var(--text-muted);
  font-size: 12px;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-mini {
  font-size: 12px;
  color: var(--color-primary);
}

.card-arrow {
  position: absolute;
  right: 0;
  bottom: 0;
  color: var(--text-muted);
  opacity: 0;
  transform: translateX(-6px);
  transition: all 0.25s ease;
}

.article-card:hover .card-arrow {
  opacity: 1;
  transform: translateX(0);
  color: var(--color-primary);
}

.empty-state {
  /* 多列容器中跨整行显示 */
  column-span: all;
  text-align: center;
  padding: 80px 0;
  color: var(--text-muted);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

/* 分页组件主题适配 */
.pagination :deep(.el-pagination) {
  --el-pagination-bg-color: var(--bg-card);
  --el-pagination-button-color: var(--text-secondary);
  --el-pagination-hover-color: var(--color-primary);
}
.pagination :deep(.el-pager li),
.pagination :deep(.btn-prev),
.pagination :deep(.btn-next) {
  backdrop-filter: blur(var(--glass-blur));
  border-radius: 8px;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 30px;
    letter-spacing: 3px;
  }
  .article-grid {
    column-count: 1;
  }
}
</style>
