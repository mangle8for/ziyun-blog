<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'

import { getArticlePage } from '@/api/article'
import { getTagList } from '@/api/tag'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem, Tag } from '@/types'

const router = useRouter()

const tags = ref<Tag[]>([])
const activeId = ref<string>('')
const articles = ref<ArticleListItem[]>([])
const loading = ref(false)

/** 当前选中的标签对象（用于标题） */
const activeTag = computed(() => tags.value.find((t) => t.id === activeId.value))

// SEO：未选中时用通用标题，选中后用标签名
useHead({
  title: () => (activeTag.value ? `标签：#${activeTag.value.name}` : '标签'),
  meta: () => [
    {
      name: 'description',
      content: activeTag.value
        ? `标签「${activeTag.value.name}」下的文章汇总。`
        : '散落在星海中的关键词 —— 紫云博客文章标签浏览。',
    },
  ],
})

async function loadTags() {
  tags.value = await getTagList()
}

async function loadArticles(tagId: string) {
  loading.value = true
  try {
    const data = await getArticlePage({ page: 1, size: 50, tagId })
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

onMounted(loadTags)
</script>

<template>
  <div class="tag-view">
    <header class="page-header">
      <h1 class="page-title">标签</h1>
      <p class="page-sub">散落在星海中的关键词</p>
    </header>

    <GlassCard padded="lg" class="tag-cloud">
      <span
        v-for="tag in tags"
        :key="tag.id"
        class="cloud-tag"
        :class="{ active: activeId === tag.id }"
        @click="select(tag.id)"
      >
        # {{ tag.name }}
      </span>
    </GlassCard>

    <section v-if="activeId" v-loading="loading" class="tag-articles">
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
      <div v-if="!loading && articles.length === 0" class="empty">该标签下暂无已发布文章</div>
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

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 28px;
  justify-content: center;
}

.cloud-tag {
  padding: 8px 18px;
  border-radius: 999px;
  cursor: pointer;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  font-size: 14px;
  transition: all 0.25s ease;
  user-select: none;
}
.cloud-tag:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  transform: translateY(-2px);
}
.cloud-tag.active {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.tag-articles {
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
</style>
