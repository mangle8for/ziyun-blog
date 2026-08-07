<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

import { getArticleDetail } from '@/api/article'
import GlassCard from '@/components/GlassCard.vue'
import { useTheme } from '@/utils/theme'
import type { ArticleDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const { theme } = useTheme()

const article = ref<ArticleDetail | null>(null)
const loading = ref(true)
const notFound = ref(false)

/** MdPreview 的主题跟随全局（md-editor-v3 内置 dark 主题） */
function loadDetail(id: string) {
  loading.value = true
  notFound.value = false
  getArticleDetail(id)
    .then((data) => {
      article.value = data
    })
    .catch(() => {
      notFound.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

function formatDateTime(iso?: string) {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

function go(id: string) {
  router.push(`/article/${id}`)
}

onMounted(() => {
  loadDetail(route.params.id as string)
})
</script>

<template>
  <div class="article-detail" v-loading="loading">
    <!-- 404 态 -->
    <GlassCard v-if="notFound" padded="lg" class="not-found">
      <h2>这篇文章不存在或未发布</h2>
      <p class="muted">它可能已被移入草稿，或从未抵达这片星海。</p>
      <el-button type="primary" round @click="router.push('/')">返回首页</el-button>
    </GlassCard>

    <template v-else-if="article">
      <!-- 正文卡片：毛玻璃大留白 -->
      <GlassCard padded="lg" class="article-card">
        <!-- 头部：标题 + 元信息 -->
        <header class="article-header">
          <h1 class="article-title">{{ article.title }}</h1>
          <div class="article-meta">
            <span>{{ article.authorName }}</span>
            <span class="dot">·</span>
            <span>{{ formatDateTime(article.createTime) }}</span>
            <span class="dot">·</span>
            <span>{{ article.viewCount }} 阅读</span>
            <span class="dot" v-if="article.categoryName">·</span>
            <span v-if="article.categoryName" class="category">{{ article.categoryName }}</span>
          </div>
          <div class="article-tags" v-if="article.tags?.length">
            <span v-for="tag in article.tags" :key="tag.id" class="tag"># {{ tag.name }}</span>
          </div>
        </header>

        <!-- 封面（有则展示，懒加载） -->
        <div class="cover" v-if="article.cover">
          <img :src="article.cover" :alt="article.title" loading="lazy" />
        </div>

        <!-- Markdown 渲染：
             MdPreview 与后台编辑器同源（所见即所得），内置代码高亮、
             表格、图片等；动图(GIF)走图片语法，视频用 <video> 标签
             （md-editor-v3 默认渲染 HTML 块）。主题随全局切换。 -->
        <MdPreview
          class="article-content"
          :editor-id="'preview-' + article.id"
          :model-value="article.content"
          :theme="theme"
          preview-theme="default"
        />
      </GlassCard>

      <!-- 上一篇 / 下一篇导航 -->
      <nav class="article-nav" v-if="article.prevArticle || article.nextArticle">
        <GlassCard
          v-if="article.prevArticle"
          class="nav-card prev"
          padded="md"
          @click="go(article.prevArticle.id)"
        >
          <div class="nav-dir"><el-icon><ArrowLeft /></el-icon> 上一篇</div>
          <div class="nav-title">{{ article.prevArticle.title }}</div>
        </GlassCard>
        <div v-else class="nav-spacer"></div>

        <GlassCard
          v-if="article.nextArticle"
          class="nav-card next"
          padded="md"
          @click="go(article.nextArticle.id)"
        >
          <div class="nav-dir">下一篇 <el-icon><ArrowRight /></el-icon></div>
          <div class="nav-title">{{ article.nextArticle.title }}</div>
        </GlassCard>
        <div v-else class="nav-spacer"></div>
      </nav>
    </template>
  </div>
</template>

<style scoped>
.article-detail {
  max-width: 860px;
  margin: 0 auto;
}

.not-found {
  text-align: center;
  padding: 60px 40px;
}
.not-found .muted {
  color: var(--text-muted);
  margin-bottom: 20px;
}

.article-card {
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

.article-header {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.article-title {
  margin: 0 0 16px;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.35;
  color: var(--text-main);
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
  font-size: 13px;
  flex-wrap: wrap;
}

.dot {
  opacity: 0.6;
}

.category {
  color: var(--color-primary);
}

.article-tags {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  font-size: 12px;
  color: var(--color-primary);
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}

.cover {
  margin-bottom: 24px;
  border-radius: 12px;
  overflow: hidden;
}
.cover img {
  width: 100%;
  display: block;
  animation: fade-in 0.5s ease both;
}

@keyframes fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* Markdown 正文样式：穿透 MdPreview 内部，适配主题变量 */
.article-content {
  --md-theme-bg-color: transparent;
  background: transparent;
  color: var(--text-main);
  font-size: 15px;
  line-height: 1.85;
}

/* 阅读排版：标题间距、段落呼吸感 */
.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3) {
  color: var(--text-main);
  margin-top: 1.6em;
}
.article-content :deep(p) {
  color: var(--text-main);
}
.article-content :deep(a) {
  color: var(--color-primary);
}
.article-content :deep(blockquote) {
  border-left: 3px solid var(--color-accent);
  color: var(--text-secondary);
  background: var(--bg-card);
  border-radius: 0 8px 8px 0;
}
.article-content :deep(img) {
  border-radius: 10px;
  max-width: 100%;
}
.article-content :deep(video) {
  border-radius: 10px;
  max-width: 100%;
}

/* 上一篇/下一篇 */
.article-nav {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 20px;
}

.nav-card {
  cursor: pointer;
}

.nav-dir {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 12px;
  margin-bottom: 8px;
}

.nav-card.next .nav-dir {
  justify-content: flex-end;
}
.nav-card.next .nav-title {
  text-align: right;
}

.nav-title {
  color: var(--text-main);
  font-weight: 600;
  font-size: 15px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 768px) {
  .article-title {
    font-size: 24px;
  }
  .article-nav {
    grid-template-columns: 1fr;
  }
  .nav-card.next .nav-dir {
    justify-content: flex-start;
  }
  .nav-card.next .nav-title {
    text-align: left;
  }
}
</style>
