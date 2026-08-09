<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'

import { getArticlePage } from '@/api/article'
import { getCategoryList } from '@/api/category'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem, Category } from '@/types'

const router = useRouter()

const categories = ref<Category[]>([])
const activeId = ref<string>('')
const articles = ref<ArticleListItem[]>([])
const loading = ref(false)

/** 当前选中的分类对象（用于标题/描述） */
const activeCategory = computed(() => categories.value.find((c) => c.id === activeId.value))

// SEO：未选中时用通用标题，选中后用分类名
useHead({
  title: () => (activeCategory.value ? `分类：${activeCategory.value.name}` : '分类'),
  meta: () => [
    {
      name: 'description',
      content: activeCategory.value?.description
        ? `分类「${activeCategory.value.name}」下的文章：${activeCategory.value.description}`
        : '按主题归档的星域 —— 紫云博客文章分类浏览。',
    },
  ],
})

async function loadCategories() {
  categories.value = await getCategoryList()
}

async function loadArticles(categoryId: string) {
  loading.value = true
  try {
    const data = await getArticlePage({ page: 1, size: 50, categoryId })
    articles.value = data.records
  } finally {
    loading.value = false
  }
}

function select(id: string) {
  activeId.value = id
  loadArticles(id)
}

function goDetail(id: string) {
  router.push(`/article/${id}`)
}

function formatDate(iso: string) {
  return iso?.slice(0, 10) ?? ''
}

onMounted(loadCategories)
</script>

<template>
  <div class="category-view">
    <header class="page-header">
      <h1 class="page-title">分类</h1>
      <p class="page-sub">按主题归档的星域</p>
    </header>

    <div class="category-grid">
      <GlassCard
        v-for="cat in categories"
        :key="cat.id"
        class="cat-card"
        :class="{ active: activeId === cat.id }"
        padded="md"
        @click="select(cat.id)"
      >
        <h3 class="cat-name">{{ cat.name }}</h3>
        <p class="cat-desc">{{ cat.description || '暂无简介' }}</p>
      </GlassCard>
    </div>

    <section v-if="activeId" v-loading="loading" class="cat-articles">
      <GlassCard
        v-for="a in articles"
        :key="a.id"
        class="article-row"
        padded="sm"
        @click="goDetail(a.id)"
      >
        <div class="row-main">
          <span class="row-title">{{ a.title }}</span>
          <span class="row-summary">{{ a.summary }}</span>
        </div>
        <span class="row-date">{{ formatDate(a.createTime) }}</span>
      </GlassCard>
      <div v-if="!loading && articles.length === 0" class="empty">该分类下暂无已发布文章</div>
    </section>
  </div>
</template>

<style scoped>
.page-header {
  text-align: center;
  padding: 32px 0 28px;
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

.category-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 28px;
}

.cat-card {
  cursor: pointer;
  text-align: center;
}
.cat-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary), var(--shadow-card);
}
.cat-name {
  margin: 0 0 8px;
  color: var(--text-main);
  font-size: 18px;
}
.cat-desc {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cat-articles {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.article-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
}
.row-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
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
.row-date {
  color: var(--text-muted);
  font-size: 12px;
  flex-shrink: 0;
}

.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 0;
}

@media (max-width: 768px) {
  .category-grid {
    grid-template-columns: 1fr;
  }
}
</style>
