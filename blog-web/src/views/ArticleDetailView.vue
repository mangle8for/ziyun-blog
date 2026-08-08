<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, List } from '@element-plus/icons-vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

import { getArticleDetail } from '@/api/article'
import GlassCard from '@/components/GlassCard.vue'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import { useTheme } from '@/utils/theme'
import type { ArticleDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const { theme } = useTheme()

const article = ref<ArticleDetail | null>(null)
const loading = ref(true)
const notFound = ref(false)

// ==================== 目录（TOC） ====================

interface TocItem {
  /** 标题级别 1-4 */
  level: number
  /** 标题文本 */
  text: string
}

const toc = ref<TocItem[]>([])
const activeIndex = ref(-1)
/** 移动端目录抽屉开关（宽屏目录为右侧吸顶栏，窄屏改抽屉） */
const tocDrawerVisible = ref(false)
/** 渲染后的标题 DOM 元素（与 toc 按文本对齐；个别标题被 MdPreview 吞掉时为 undefined） */
let headingElements: (HTMLElement | undefined)[] = []

/** 从 Markdown 提取 h1-h4 标题：先剥离围栏代码块，避免 ``` 内 # 被误识别 */
function parseToc(markdown: string): TocItem[] {
  const withoutCode = markdown.replace(/```[\s\S]*?```/g, '')
  const items: TocItem[] = []
  const regex = /^(#{1,4})\s+(.+?)\s*$/gm
  let match: RegExpExecArray | null
  while ((match = regex.exec(withoutCode)) !== null) {
    // 正则捕获组必然存在，?? 仅用于满足 TS 严格索引检查
    const hashes = match[1] ?? ''
    const title = match[2] ?? ''
    items.push({ level: hashes.length, text: title.trim() })
  }
  return items
}

/** 文章渲染完成后收集标题 DOM，与 toc 按文本一一对齐。
 *  MdPreview 可能吞掉个别标题（如 HTML 块内的 #），导致 DOM 数量
 *  与正则解析数量错位；按文本匹配可避免「点第 N 项跳到别的标题」。 */
async function buildToc() {
  if (!article.value) return
  toc.value = parseToc(article.value.content)
  activeIndex.value = -1
  headingElements = []
  if (toc.value.length === 0) return
  await nextTick()
  // MdPreview 渲染在 .article-content 容器内（class 唯一）
  const root = document.querySelector('.article-content')
  if (!root) return
  const headings = Array.from(root.querySelectorAll('h1, h2, h3, h4')) as HTMLElement[]
  const remaining = headings.slice()
  // 数组长度与 toc 保持一一对应；找不到文本时退回按序取下一个
  headingElements = toc.value.map((item) => {
    const idx = remaining.findIndex((el) => el.textContent?.trim() === item.text)
    if (idx >= 0) return remaining.splice(idx, 1)[0]
    return remaining.shift()
  })
}

/** 点击目录项：滚动到对应标题。
 *  用 scrollIntoView + 标题的 scroll-margin-top（见样式）定位，
 *  相比手算 window.scrollTo 偏移更可靠 —— 不受滚动容器与
 *  页面布局（图片加载后的高度变化）影响。 */
function jumpTo(index: number) {
  const el = headingElements[index]
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  activeIndex.value = index
}

/** 移动端抽屉目录点击：先关抽屉，等 body 滚动锁释放后再跳转。
 *  el-drawer 打开期间会锁定 body 滚动（overflow: hidden），
 *  若在关闭动画完成前发起平滑滚动会被静默取消 —— 表现为
 *  「只象征性移动一点点」，故延迟到 @closed 回调再跳。 */
const pendingTocJump = ref<number | null>(null)

function jumpToFromDrawer(index: number) {
  pendingTocJump.value = index
  tocDrawerVisible.value = false
}

function onDrawerClosed() {
  if (pendingTocJump.value !== null) {
    jumpTo(pendingTocJump.value)
    pendingTocJump.value = null
  }
}

/** 滚动监听：高亮当前阅读位置（视口内最靠上的标题） */
function onScroll() {
  let current = -1
  headingElements.forEach((el, index) => {
    if (el && el.getBoundingClientRect().top <= 80) current = index
  })
  activeIndex.value = current
}

/** MdPreview 的主题跟随全局（md-editor-v3 内置 dark 主题） */
function loadDetail(id: string) {
  loading.value = true
  article.value = null
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

/**
 * 上一篇/下一篇跳转时路由只是参数变化（/article/:id 同一路由记录），
 * Vue Router 会复用组件实例、onMounted 不重跑，必须监听 :id 变化重新加载。
 */
watch(
  () => route.params.id,
  (id) => {
    loadDetail(id as string)
  },
)

/** 文章内容变化（加载/切换）后重建目录 */
watch(article, () => {
  buildToc()
})

function formatDateTime(iso?: string) {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

function go(id: string) {
  router.push(`/article/${id}`)
}

onMounted(() => {
  loadDetail(route.params.id as string)
  window.addEventListener('scroll', onScroll, { passive: true })
  // 文章内图片全部加载完成后重算目录对齐（图片加载会改变标题布局）
  window.addEventListener('load', buildToc)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('load', buildToc)
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
      <!-- 两栏布局：正文为主（minmax(0,1fr) 主列），目录为次（220px 次列跨两行）。
           用显式 grid-template-areas 定位 —— 此前依赖 DOM 顺序自动布局，
           导致目录抢占主列、正文被挤进窄列（主次错位）。 -->
      <div class="article-layout" :class="{ 'has-toc': toc.length > 0 }">
        <!-- 正文卡片：毛玻璃大留白；no-hover 去掉 hover 微动，专注阅读 -->
        <GlassCard no-hover padded="lg" class="article-card">
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

        <!-- 封面（有则展示，低清预览 + 原图淡入） -->
        <div class="cover" v-if="article.cover">
          <ProgressiveImage :src="article.cover" :alt="article.title" />
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
          no-hover
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
          no-hover
          @click="go(article.nextArticle.id)"
        >
          <div class="nav-dir">下一篇 <el-icon><ArrowRight /></el-icon></div>
          <div class="nav-title">{{ article.nextArticle.title }}</div>
        </GlassCard>
        <div v-else class="nav-spacer"></div>
      </nav>

        <!-- 右侧目录：长文快速跳转（宽屏吸顶显示；窄屏由下方抽屉替代） -->
        <aside class="article-toc" v-if="toc.length">
          <div class="toc-title">目录</div>
          <div
            v-for="(item, index) in toc"
            :key="index"
            class="toc-item"
            :class="{ active: activeIndex === index, [`lv-${item.level}`]: true }"
            @click="jumpTo(index)"
          >
            {{ item.text }}
          </div>
        </aside>
      </div>

      <!-- 移动端目录：右下角悬浮按钮 + 抽屉（窄屏隐藏侧栏目录后的替代入口） -->
      <button
        v-if="toc.length"
        class="toc-fab"
        type="button"
        aria-label="打开目录"
        @click="tocDrawerVisible = true"
      >
        <el-icon :size="20"><List /></el-icon>
      </button>
      <el-drawer
        v-model="tocDrawerVisible"
        title="目录"
        direction="rtl"
        size="min(78vw, 320px)"
        class="toc-drawer"
        @closed="onDrawerClosed"
      >
        <div
          v-for="(item, index) in toc"
          :key="index"
          class="toc-item"
          :class="{ active: activeIndex === index, [`lv-${item.level}`]: true }"
          @click="jumpToFromDrawer(index)"
        >
          {{ item.text }}
        </div>
      </el-drawer>
    </template>
  </div>
</template>

<style scoped>
.article-detail {
  max-width: 1080px;
  margin: 0 auto;
}

/*
  正文 + 右侧目录布局：默认单栏（无目录时正文占满），
  宽屏且有目录时切换为「正文主列 + 目录次列」两栏。
  显式 grid-template-areas 定位，杜绝 DOM 顺序导致的主次错位；
  minmax(0, 1fr) 允许主列收缩，防止长代码块把 grid 撑破。
*/
.article-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-areas:
    'content'
    'nav';
  gap: 24px;
  align-items: start;
}

@media (min-width: 1081px) {
  .article-layout.has-toc {
    /* 目录列加宽，长标题两行内可完整展示（配合 toc-item 换行） */
    grid-template-columns: minmax(0, 1fr) 260px;
    grid-template-areas:
      'content toc'
      'nav toc';
  }
}

.article-card {
  grid-area: content;
}

.article-nav {
  grid-area: nav;
}

/* 右侧目录：吸顶、可滚动、跟随阅读位置高亮 */
.article-toc {
  grid-area: toc;
  position: sticky;
  top: 76px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  padding: 14px 16px;
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
}

.toc-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-main);
  padding-bottom: 8px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--border-color);
}

.toc-item {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  padding: 5px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease;
  /* 长标题允许换行（最多两行），不再单行省略 —— 保证完整可读 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.toc-item:hover {
  color: var(--text-main);
  background: var(--bg-page);
}

.toc-item.active {
  color: var(--color-primary);
  background: var(--bg-page);
  font-weight: 600;
}

/* 不同级别缩进 */
.toc-item.lv-2 {
  padding-left: 16px;
}
.toc-item.lv-3 {
  padding-left: 28px;
  font-size: 12px;
}
.toc-item.lv-4 {
  padding-left: 40px;
  font-size: 12px;
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
  position: relative;
  margin-bottom: 24px;
  border-radius: 12px;
  overflow: hidden;
  /* 低清预览与骨架占位期的最小高度，防止布局跳动 */
  aspect-ratio: 16 / 9;
  background: var(--bg-page);
}

/* Markdown 正文样式：穿透 MdPreview 内部，适配主题变量 */
.article-content {
  --md-theme-bg-color: transparent;
  background: transparent;
  color: var(--text-main);
  font-size: 15px;
  line-height: 1.85;
}

/* 阅读排版：标题间距、段落呼吸感。
   scroll-margin-top 与 scrollIntoView 配合：吸顶导航(60px) + 呼吸间隙 */
.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4) {
  color: var(--text-main);
  margin-top: 1.6em;
  scroll-margin-top: 76px;
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
/* 长代码块/表格横向滚动，防止撑破移动端窄栏 */
.article-content :deep(pre) {
  overflow-x: auto;
  max-width: 100%;
}
.article-content :deep(table) {
  display: block;
  overflow-x: auto;
  max-width: 100%;
}

/* 移动端目录悬浮按钮（宽屏隐藏，侧栏目录已可见） */
.toc-fab {
  display: none;
  position: fixed;
  right: 20px;
  bottom: 28px;
  z-index: 90;
  /* 44px 以上触控区（移动端可点性） */
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  color: var(--color-primary);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  align-items: center;
  justify-content: center;
}

/* 上一篇/下一篇 */
.article-nav {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
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

/* 抽屉目录条目：复用侧栏目录视觉，加大行高便于触屏点击 */
.toc-drawer .toc-item {
  padding: 10px 12px;
  font-size: 14px;
}
.toc-drawer .toc-item.lv-2 {
  padding-left: 22px;
}
.toc-drawer .toc-item.lv-3 {
  padding-left: 34px;
}
.toc-drawer .toc-item.lv-4 {
  padding-left: 46px;
}

/* 窄屏：隐藏右侧吸顶目录（正文单栏），改为悬浮按钮 + 抽屉目录 */
@media (max-width: 1080px) {
  .article-toc {
    display: none;
  }
  .toc-fab {
    display: flex;
  }
}
</style>
