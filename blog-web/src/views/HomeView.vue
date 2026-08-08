<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ArrowRight, ArrowUp } from '@element-plus/icons-vue'

import { getArticlePage } from '@/api/article'
import { getCategoryList, getHotCategories } from '@/api/category'
import { getHotTags, getTagList } from '@/api/tag'
import GlassCard from '@/components/GlassCard.vue'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import SkeletonCard from '@/components/SkeletonCard.vue'
import type { ArticleListItem, TagItem } from '@/types'

const router = useRouter()

const articles = ref<ArticleListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(6)
const loading = ref(false)
/** 加载失败标记：与真实空态区分，失败时展示重试入口 */
const loadError = ref(false)

/** 热门筛选（默认展示，按已发布文章数倒序 TopN） */
const hotCategories = ref<FilterChip[]>([])
const hotTags = ref<FilterChip[]>([])
/** 全量筛选（展开时才拉取，避免无意义的全量加载） */
const allCategories = ref<FilterChip[]>([])
const allTags = ref<FilterChip[]>([])
const categoriesExpanded = ref(false)
const tagsExpanded = ref(false)

/** 当前筛选条件（分类/标签/关键词） */
const activeCategoryId = ref<string>('')
const activeTagId = ref<string>('')
const keyword = ref('')

/** 卡片内标签最多展示数，超出折叠为 +N（防止多标签撑高卡片） */
const MAX_CARD_TAGS = 3

/** 视口宽度是否处于移动端档（分页器紧凑模式用） */
const isMobile = ref(false)
const mobileMq = window.matchMedia('(max-width: 768px)')

function onMqChange(e: MediaQueryListEvent) {
  isMobile.value = e.matches
}

/** 筛选 chip 的展示形状：热门项带 articleCount，展开后的全量项没有 */
interface FilterChip {
  id: string
  name: string
  articleCount?: number
}

/**
 * 展示的筛选 chips：收起时只显示热门 TopN；
 * 已选中项若不在热门列表中（展开选择后收起）则保留在首位，
 * 避免「筛选生效中却看不到筛选项」的困惑。
 */
const displayCategories = computed<FilterChip[]>(() => {
  if (categoriesExpanded.value) return allCategories.value
  const list: FilterChip[] = hotCategories.value
  if (activeCategoryId.value && !list.some((c) => c.id === activeCategoryId.value)) {
    const active = allCategories.value.find((c) => c.id === activeCategoryId.value)
    if (active) list.unshift(active)
  }
  return list
})

const displayTags = computed<FilterChip[]>(() => {
  if (tagsExpanded.value) return allTags.value
  const list: FilterChip[] = hotTags.value
  if (activeTagId.value && !list.some((t) => t.id === activeTagId.value)) {
    const active = allTags.value.find((t) => t.id === activeTagId.value)
    if (active) list.unshift(active)
  }
  return list
})

/** 分页器布局：移动端精简（隐藏总数文案）并切换小尺寸，防止窄屏溢出 */
const paginationLayout = computed(() =>
  isMobile.value ? 'prev, pager, next' : 'prev, pager, next, total',
)

async function loadArticles() {
  loading.value = true
  loadError.value = false
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
  } catch {
    // 请求拦截器已弹错误提示，这里只切换错误态视图
    articles.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 默认只拉热门 TopN；全量列表延迟到「展开全部」时按需加载 */
async function loadFilters() {
  const [cats, tgs] = await Promise.all([getHotCategories(8), getHotTags(15)])
  hotCategories.value = cats
  hotTags.value = tgs
}

async function toggleCategories() {
  if (!categoriesExpanded.value && allCategories.value.length === 0) {
    allCategories.value = await getCategoryList()
  }
  categoriesExpanded.value = !categoriesExpanded.value
}

async function toggleTags() {
  if (!tagsExpanded.value && allTags.value.length === 0) {
    allTags.value = await getTagList()
  }
  tagsExpanded.value = !tagsExpanded.value
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

/** 卡片内可见标签（超出 MAX_CARD_TAGS 的以 +N 折叠） */
function visibleTags(tags: TagItem[]) {
  return tags.slice(0, MAX_CARD_TAGS)
}

onMounted(() => {
  isMobile.value = mobileMq.matches
  mobileMq.addEventListener('change', onMqChange)
  loadArticles()
  loadFilters()
})

onBeforeUnmount(() => {
  mobileMq.removeEventListener('change', onMqChange)
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

    <!-- 筛选区：分类 + 标签（默认热门 TopN，可展开全部） -->
    <section class="filters" v-if="displayCategories.length || displayTags.length">
      <div class="filter-row" v-if="displayCategories.length">
        <span class="filter-label">分类</span>
        <div class="filter-chips">
          <span
            v-for="cat in displayCategories"
            :key="cat.id"
            class="chip"
            :class="{ active: activeCategoryId === cat.id }"
            :title="cat.name"
            @click="onFilterCategory(cat.id)"
          >
            {{ cat.name }}
          </span>
          <button class="chip chip-toggle" type="button" @click="toggleCategories">
            {{ categoriesExpanded ? '收起' : '全部' }}
            <el-icon :size="12">
              <ArrowUp v-if="categoriesExpanded" />
              <ArrowDown v-else />
            </el-icon>
          </button>
        </div>
      </div>
      <div class="filter-row" v-if="displayTags.length">
        <span class="filter-label">标签</span>
        <div class="filter-chips">
          <span
            v-for="tag in displayTags"
            :key="tag.id"
            class="chip chip-tag"
            :class="{ active: activeTagId === tag.id }"
            :title="tag.name"
            @click="onFilterTag(tag.id)"
          >
            # {{ tag.name }}
          </span>
          <button class="chip chip-toggle" type="button" @click="toggleTags">
            {{ tagsExpanded ? '收起' : '全部' }}
            <el-icon :size="12">
              <ArrowUp v-if="tagsExpanded" />
              <ArrowDown v-else />
            </el-icon>
          </button>
        </div>
      </div>
    </section>

    <!-- 文章卡片网格：首次加载显示骨架屏，翻页时保留旧列表 + 加载遮罩 -->
    <section v-loading="loading && articles.length > 0" class="article-grid">
      <template v-if="loading && articles.length === 0">
        <SkeletonCard v-for="n in 6" :key="n" :index="n - 1" />
      </template>

      <template v-else>
        <GlassCard
          v-for="(article, index) in articles"
          :key="article.id"
          class="article-card"
          :style="{ animationDelay: `${index * 60}ms` }"
          padded="md"
        >
          <div class="card-body" @click="goDetail(article.id)">
            <!-- 封面：低清预览 + 原图淡入（懒加载类型 C） -->
            <div class="cover-wrap" v-if="article.cover">
              <ProgressiveImage :src="article.cover" :alt="article.title" hover-zoom />
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
                <span v-for="tag in visibleTags(article.tags)" :key="tag.id" class="tag-mini">
                  #{{ tag.name }}
                </span>
                <span v-if="article.tags.length > MAX_CARD_TAGS" class="tag-mini tag-more">
                  +{{ article.tags.length - MAX_CARD_TAGS }}
                </span>
              </div>
            </div>
            <div class="card-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </GlassCard>
      </template>

      <!-- 加载失败态：与真实空态区分，提供重试 -->
      <div v-if="!loading && loadError" class="empty-state">
        <p>星舰信号中断，文章加载失败</p>
        <el-button round @click="loadArticles">重新连接</el-button>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading && articles.length === 0" class="empty-state">
        <p>这片星海暂时没有文章</p>
      </div>
    </section>

    <!-- 分页 -->
    <div class="pagination" v-if="total > size">
      <el-pagination
        background
        :small="isMobile"
        :layout="paginationLayout"
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
  align-items: flex-start;
  gap: 12px;
}

.filter-label {
  color: var(--text-muted);
  font-size: 13px;
  min-width: 32px;
  /* 与首行 chip 垂直居中对齐 */
  line-height: 30px;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
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
  /* 超长名称截断：防止极端长度撑破布局（移动端尤甚） */
  max-width: 10em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

/* 展开/收起按钮：弱化样式，区别于筛选 chip */
.chip-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
  border-style: dashed;
  font-family: inherit;
}

/*
  文章卡片网格：Grid 自适应列数（repeat + minmax），
  替代原 CSS 多列瀑布流 —— column-count 会先把左列填满再填右列，
  时间倒序的文章流呈现「纵向蛇形」阅读顺序，且图片异步加载后
  列重排会导致卡片跳动；Grid 保持横向阅读序，列数随容器宽度
  自适应（宽屏 3 列 / 平板 2 列 / 手机 1 列），无需手写断点。
  align-items: start 让卡片按内容自然高度错落，不强制等高。
*/
.article-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  min-height: 200px;
  align-items: start;
}

.article-card {
  cursor: pointer;
  width: 100%;
  /* 入场浮起动画：fill: backwards 让动画结束后 transform 归 none，
     避免残留 matrix 干扰 fixed 定位与滚动偏移计算 */
  animation: card-in 0.5s ease backwards;
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
  position: relative;
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
  flex-wrap: wrap;
  gap: 6px 14px;
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

.tag-more {
  color: var(--text-muted);
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
  /* 网格容器中跨整行显示 */
  grid-column: 1 / -1;
  text-align: center;
  padding: 80px 0;
  color: var(--text-muted);
}

.empty-state p {
  margin: 0 0 16px;
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
}
</style>
