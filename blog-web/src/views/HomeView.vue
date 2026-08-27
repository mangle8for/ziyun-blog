<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ArrowRight, ArrowUp, Search } from '@element-plus/icons-vue'

import { getArticlePage, getPinnedArticles } from '@/api/article'
import { getCategoryList, getHotCategories } from '@/api/category'
import { getHotTags, getTagList } from '@/api/tag'
import GlassCard from '@/components/GlassCard.vue'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import SkeletonCard from '@/components/SkeletonCard.vue'
import type { ArticleListItem, TagItem } from '@/types'

const router = useRouter()

// ==================== 首屏打字机 ====================
// 循环打出/擦除标语：打字 110ms/字 -> 停顿 -> 擦除 45ms/字 -> 下一句。
// prefers-reduced-motion 用户直接显示静态标语，不跑定时器。
const HERO_PHRASES = [
  '欢迎来到紫云博客',
  '星海拾遗 · 记录技术、思考与自然',
  '把每一次思考，写成夜空里的星光',
]
const typedText = ref('')
let typeTimer: number | undefined
let phraseIdx = 0
let charIdx = 0
let deleting = false

function typeTick() {
  const current = HERO_PHRASES[phraseIdx] ?? ''
  if (!deleting) {
    charIdx++
    typedText.value = current.slice(0, charIdx)
    if (charIdx === current.length) {
      deleting = true
      typeTimer = window.setTimeout(typeTick, 2400)
      return
    }
    typeTimer = window.setTimeout(typeTick, 110)
  } else {
    charIdx--
    typedText.value = current.slice(0, charIdx)
    if (charIdx === 0) {
      deleting = false
      phraseIdx = (phraseIdx + 1) % HERO_PHRASES.length
      typeTimer = window.setTimeout(typeTick, 600)
      return
    }
    typeTimer = window.setTimeout(typeTick, 45)
  }
}

function scrollToStream() {
  streamEl.value?.scrollIntoView({ behavior: 'smooth' })
}

// ==================== 首屏氛围动画 ====================
/** 首屏主标题（逐字入场动画用） */
const HERO_TITLE = '紫云博客'

/**
 * mulberry32 伪随机（固定种子）：光点位置/节奏确定可复现，
 * 刷新不跳变，符合星海恒定感的直觉。
 */
function mulberry32(a: number) {
  return function () {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * 首屏浮游光点：暗色主题是缓慢上浮的星尘，亮色主题是林间萤火。
 * 12 颗错峰漂移 + 闪烁（纯 CSS 动画，固定种子布点）。
 */
const heroMotes = computed(() => {
  const rnd = mulberry32(0xc0ffee)
  return Array.from({ length: 12 }, (_, i) => {
    const size = 2 + Math.round(rnd() * 3)
    return {
      id: i,
      style: {
        left: `${6 + rnd() * 88}%`,
        top: `${10 + rnd() * 72}%`,
        width: `${size}px`,
        height: `${size}px`,
        '--dur': `${7 + ((rnd() * 8) | 0)}s`,
        '--delay': `${-((rnd() * 10) | 0)}s`,
      },
    }
  })
})

// ==================== 数据 ====================
const articles = ref<ArticleListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(6)
const loading = ref(false)
/** 加载失败标记：与真实空态区分，失败时展示重试入口 */
const loadError = ref(false)

/** 置顶文章（星耀推荐区，可复数；首页文章流已排除它们避免重复） */
const pinned = ref<ArticleListItem[]>([])
/** 首篇置顶（模板类型收窄用；pinned 非空时必有值） */
const leadPinned = computed(() => pinned.value[0])

/** 热门筛选（默认展示，按已发布文章数倒序 TopN） */
const hotCategories = ref<FilterChip[]>([])
const hotTags = ref<FilterChip[]>([])
/** 全量筛选（展开时才拉取，避免无意义的全量加载） */
const allCategories = ref<FilterChip[]>([])
const allTags = ref<FilterChip[]>([])
const categoriesExpanded = ref(false)
const tagsExpanded = ref(false)

/** 当前筛选条件（分类/标签） */
const activeCategoryId = ref<string>('')
const activeTagId = ref<string>('')

/** 卡片内标签最多展示数，超出折叠为 +N（防止多标签撑高卡片） */
const MAX_CARD_TAGS = 3

/** 视口宽度是否处于移动端档（分页器紧凑模式用） */
const isMobile = ref(false)
const mobileMq = window.matchMedia('(max-width: 768px)')

const streamEl = ref<HTMLElement | null>(null)

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
      // 置顶文章由星耀区展示，文章流排除它们（后端 excludePinned）
      excludePinned: true,
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

/** 置顶清单拉取：失败静默（星耀区直接隐藏，不影响主流程） */
async function loadPinned() {
  try {
    pinned.value = await getPinnedArticles(5)
  } catch {
    pinned.value = []
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

function onPageChange(p: number) {
  page.value = p
  loadArticles()
  window.scrollTo({ top: window.innerHeight, behavior: 'smooth' })
}

function goDetail(id: string) {
  router.push(`/article/${id}`)
}

function goSearch() {
  router.push('/search')
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
  loadPinned()
  loadFilters()
  // 尊重「减少动态」：不跑打字机，直接显示静态标语
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    typedText.value = HERO_PHRASES[1] ?? ''
  } else {
    typeTick()
  }
})

onBeforeUnmount(() => {
  mobileMq.removeEventListener('change', onMqChange)
  if (typeTimer) window.clearTimeout(typeTimer)
})
</script>

<template>
  <div class="home-view">
    <!-- ============ 首屏：全视口英雄区（逐字标题 + 打字机 + 流星 + 地平线光带） ============ -->
    <section class="hero-screen">
      <div class="hero-grid" aria-hidden="true"></div>

      <!-- 浮游光点：暗色 = 上浮星尘 / 亮色 = 林间萤火 -->
      <span
        v-for="m in heroMotes"
        :key="m.id"
        class="hero-mote"
        :style="m.style"
        aria-hidden="true"
      ></span>

      <!-- 流星：错峰周期性划过 -->
      <span class="meteor meteor-a" aria-hidden="true"></span>
      <span class="meteor meteor-b" aria-hidden="true"></span>

      <h1 class="hero-title">
        <span
          v-for="(ch, i) in HERO_TITLE"
          :key="i"
          class="hero-char"
          :style="{
            animationDelay: `${300 + i * 130}ms`,
            backgroundPosition: `${(i * 100) / (HERO_TITLE.length - 1)}% 0`,
          }"
          >{{ ch }}</span
        >
      </h1>
      <p class="hero-typed" aria-live="polite">
        {{ typedText }}<span class="caret" aria-hidden="true"></span>
      </p>

      <button type="button" class="hero-search" @click="goSearch">
        <el-icon><Search /></el-icon>
        <span>搜索文章 / 分类 / 标签…</span>
      </button>

      <div class="horizon" aria-hidden="true"><i class="horizon-pulse"></i></div>

      <button
        type="button"
        class="scroll-cue"
        aria-label="向下滚动查看文章"
        @click="scrollToStream"
      >
        <el-icon :size="20"><ArrowDown /></el-icon>
      </button>
    </section>

    <!-- ============ 星耀推荐：置顶文章（可复数，图片优先展示） ============ -->
    <section v-if="pinned.length" class="pinned-section">
      <header class="sec-head">
        <h2 class="sec-title">星耀推荐</h2>
        <span class="sec-sub">✦ 站长置顶</span>
      </header>

      <div class="pinned-grid" v-if="leadPinned">
        <!-- 首篇置顶：大幅横向卡（封面左 45%，为图片留足展示空间） -->
        <GlassCard class="pin-card pin-lead" padded="md" @click="goDetail(leadPinned.id)">
          <div class="pin-cover" v-if="leadPinned.cover">
            <ProgressiveImage :src="leadPinned.cover" :alt="leadPinned.title" hover-zoom />
            <span class="pin-badge">★ 置顶</span>
          </div>
          <div class="pin-body">
            <h3 class="pin-title">{{ leadPinned.title }}</h3>
            <p class="pin-summary">{{ leadPinned.summary || '（暂无摘要）' }}</p>
            <div class="pin-meta">
              <span v-if="leadPinned.categoryName" class="meta-item">{{
                leadPinned.categoryName
              }}</span>
              <span class="meta-item">{{ formatDate(leadPinned.createTime) }}</span>
              <span class="meta-item">{{ leadPinned.viewCount }} 阅读</span>
            </div>
          </div>
        </GlassCard>

        <!-- 其余置顶：自适应网格（1 张自动铺满，多张并排），封面在上 -->
        <GlassCard
          v-for="a in pinned.slice(1)"
          :key="a.id"
          class="pin-card pin-side"
          padded="md"
          @click="goDetail(a.id)"
        >
          <div class="pin-cover cover-top" v-if="a.cover">
            <ProgressiveImage :src="a.cover" :alt="a.title" hover-zoom />
            <span class="pin-badge">★ 置顶</span>
          </div>
          <div class="pin-body">
            <h3 class="pin-title small">{{ a.title }}</h3>
            <p class="pin-summary clamp2">{{ a.summary || '（暂无摘要）' }}</p>
            <div class="pin-meta">
              <span v-if="a.categoryName" class="meta-item">{{ a.categoryName }}</span>
              <span class="meta-item">{{ formatDate(a.createTime) }}</span>
            </div>
          </div>
        </GlassCard>
      </div>
    </section>

    <!-- ============ 文章流 ============ -->
    <section ref="streamEl" class="stream-section">
      <header class="sec-head">
        <h2 class="sec-title">最新文章</h2>
        <a class="sec-more" href="/archives" @click.prevent="router.push('/archives')">
          全部文章 <el-icon :size="13"><ArrowRight /></el-icon>
        </a>
      </header>

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
                  <span class="meta-item" v-if="article.categoryName">{{
                    article.categoryName
                  }}</span>
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

        <!-- 空态：区分「被筛选空」与「全部文章都在星耀区」 -->
        <div v-else-if="!loading && articles.length === 0" class="empty-state">
          <p v-if="activeCategoryId || activeTagId">这片星域暂时没有匹配的文章</p>
          <p v-else-if="pinned.length">文章都在上方星耀区闪耀 —— 去文章页看完整清单吧</p>
          <p v-else>这片星海暂时没有文章</p>
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
    </section>
  </div>
</template>

<style scoped>
.home-view {
  /* 首屏 100vw 出血：剪裁横向溢出（clip 不产生滚动容器，不影响吸顶导航） */
  overflow-x: clip;
}

/* ============================================================
   首屏英雄区：全视口高度（扣除吸顶导航）+ 全视口宽度。
   出血公式：width 保持 auto，左右对称负 margin（calc(50% - 50vw)）
   把盒子拉伸到视口两缘 —— 此前「width:100vw + 单侧负 margin」
   会让右侧少一个出血量、被 overflow 裁掉（已修复的全宽 bug）。
   亮色 = 晨光营地网格，暗色 = 深空坐标网格。
   ============================================================ */
.hero-screen {
  position: relative;
  margin-inline: calc(50% - 50vw);
  margin-top: -32px;
  min-height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  text-align: center;
  overflow: hidden;
  padding: 24px;
}

/* 坐标网格：双色主题各自低透明度，径向蒙版向边缘淡出 */
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--hero-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--hero-grid-line) 1px, transparent 1px);
  background-size: 52px 52px;
  -webkit-mask-image: radial-gradient(ellipse 62% 58% at 50% 45%, #000 25%, transparent 76%);
  mask-image: radial-gradient(ellipse 62% 58% at 50% 45%, #000 25%, transparent 76%);
  --hero-grid-line: rgba(47, 125, 90, 0.1);
}
html.dark .hero-grid {
  --hero-grid-line: rgba(124, 108, 240, 0.09);
}

/* 浮游光点：暗色 = 深空星尘（冷白 + 星云紫辉光），亮色 = 林间萤火（绿光点）。
 * 双动画叠加：整体缓慢漂移（--dur 错峰）+ 透明度闪烁（半周期）。 */
.hero-mote {
  position: absolute;
  border-radius: 50%;
  background: var(--mote-color);
  box-shadow: 0 0 8px 1px var(--mote-glow);
  animation:
    mote-float var(--dur) ease-in-out var(--delay) infinite alternate,
    mote-twinkle calc(var(--dur) / 2) ease-in-out var(--delay) infinite;
  --mote-color: rgba(47, 125, 90, 0.5);
  --mote-glow: rgba(47, 125, 90, 0.35);
}
html.dark .hero-mote {
  --mote-color: rgba(226, 233, 255, 0.85);
  --mote-glow: rgba(148, 136, 245, 0.5);
}
@keyframes mote-float {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(14px, -34px);
  }
}
@keyframes mote-twinkle {
  0%,
  100% {
    opacity: 0.9;
  }
  50% {
    opacity: 0.2;
  }
}

/* 流星：细长渐变线沿 -32° 方向周期性划过（rotate 后 translateX 即
 * 沿局部 X 轴移动，自然形成左下方向的轨迹），头亮尾淡。 */
.meteor {
  position: absolute;
  width: 150px;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(270deg, transparent, var(--meteor-color));
  transform: rotate(-32deg);
  opacity: 0;
  pointer-events: none;
  --meteor-color: rgba(47, 125, 90, 0.85);
}
html.dark .meteor {
  --meteor-color: rgba(235, 240, 255, 0.9);
}
.meteor-a {
  top: 15%;
  left: 68%;
  animation: meteor-fly 9s linear 3.2s infinite;
}
.meteor-b {
  top: 7%;
  left: 38%;
  animation: meteor-fly 13s linear 7.6s infinite;
}
@keyframes meteor-fly {
  0% {
    opacity: 0;
    transform: rotate(-32deg) translateX(0);
  }
  3% {
    opacity: 1;
  }
  11% {
    opacity: 0;
    transform: rotate(-32deg) translateX(-560px);
  }
  100% {
    opacity: 0;
    transform: rotate(-32deg) translateX(-560px);
  }
}

.hero-title {
  position: relative;
  margin: 0;
  font-size: 56px;
  font-weight: 800;
  letter-spacing: 10px;
}

/* 逐字入场：每字独立渐变裁剪（background-position 按序取渐变切片，
 * 四字拼出与整体渐变一致的连续过渡），模糊 + 上浮 + 缩放依次浮现。 */
.hero-char {
  display: inline-block;
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  background-size: 400% 100%;
  background-repeat: no-repeat;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: char-in 0.7s cubic-bezier(0.2, 0.7, 0.3, 1) backwards;
}
@keyframes char-in {
  from {
    opacity: 0;
    transform: translateY(26px) scale(0.86);
    filter: blur(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
    filter: blur(0);
  }
}

/* 打字机行：光标竖线呼吸闪烁 */
.hero-typed {
  position: relative;
  margin: 0;
  min-height: 1.6em;
  color: var(--text-secondary);
  font-size: 17px;
  letter-spacing: 2px;
  animation: hero-in 0.9s 0.15s ease backwards;
}
.caret {
  display: inline-block;
  width: 2px;
  height: 1.05em;
  margin-left: 3px;
  vertical-align: -0.15em;
  background: var(--color-primary);
  animation: caret-blink 1s step-end infinite;
}
@keyframes caret-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

/* 搜索入口：幽灵输入框样式，点击跳独立搜索页 */
.hero-search {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 11px 22px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 14px;
  color: var(--text-muted);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(var(--glass-blur));
  cursor: pointer;
  transition:
    color 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
  animation: hero-in 0.9s 0.3s ease backwards;
}
.hero-search:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-hover);
}

/* 地平线光带：入场从中心向两侧展开，随后常驻呼吸辉光；
 * 一枚扫光脉冲沿线往返滑过（transform: translateX，不触发回流）。 */
.horizon {
  position: absolute;
  left: 3%;
  right: 3%;
  bottom: 24%;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(
    90deg,
    transparent,
    var(--color-primary) 30%,
    var(--color-accent) 70%,
    transparent
  );
  box-shadow: 0 0 26px 3px var(--hero-horizon-glow);
  animation:
    horizon-grow 1.1s 0.55s cubic-bezier(0.2, 0.7, 0.3, 1) backwards,
    horizon-breathe 4s 1.9s ease-in-out infinite;
  --hero-horizon-glow: rgba(47, 125, 90, 0.4);
}
html.dark .horizon {
  --hero-horizon-glow: rgba(124, 108, 240, 0.45);
}
@keyframes horizon-grow {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}
@keyframes horizon-breathe {
  0%,
  100% {
    opacity: 0.75;
    box-shadow: 0 0 18px 2px var(--hero-horizon-glow);
  }
  50% {
    opacity: 1;
    box-shadow: 0 0 34px 6px var(--hero-horizon-glow);
  }
}
.horizon-pulse {
  position: absolute;
  top: -2px;
  left: 0;
  width: 90px;
  height: 6px;
  border-radius: 6px;
  background: linear-gradient(90deg, transparent, var(--pulse-color), transparent);
  filter: blur(1px);
  opacity: 0;
  animation: pulse-sweep 5.5s 2.4s ease-in-out infinite;
  --pulse-color: rgba(47, 125, 90, 0.9);
}
html.dark .horizon-pulse {
  --pulse-color: rgba(205, 196, 255, 0.95);
}
@keyframes pulse-sweep {
  0% {
    left: 0;
    opacity: 0;
  }
  12% {
    opacity: 1;
  }
  55% {
    opacity: 0.9;
  }
  70%,
  100% {
    left: calc(100% - 90px);
    opacity: 0;
  }
}

@keyframes hero-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 下滑指示：底部居中，上下浮动引导滚动 */
.scroll-cue {
  position: absolute;
  bottom: 26px;
  left: 50%;
  transform: translateX(-50%);
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  cursor: pointer;
  animation: cue-float 2.2s ease-in-out infinite;
  transition:
    color 0.25s ease,
    border-color 0.25s ease;
}
.scroll-cue:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
@keyframes cue-float {
  0%,
  100% {
    transform: translate(-50%, 0);
  }
  50% {
    transform: translate(-50%, 8px);
  }
}

/* 波纹环：以滚动按钮为圆心周期性扩散，强化「向下」的引导感 */
.scroll-cue::after {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: 50%;
  border: 1px solid var(--color-primary);
  opacity: 0;
  animation: cue-ring 2.4s ease-out infinite;
  pointer-events: none;
}
@keyframes cue-ring {
  0% {
    transform: scale(1);
    opacity: 0.5;
  }
  70%,
  100% {
    transform: scale(1.8);
    opacity: 0;
  }
}

/* ============================================================
   区块标题（星耀推荐 / 最新文章共用）
   ============================================================ */
.sec-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 6px 0 18px;
}
.sec-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 3px;
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.sec-sub {
  color: var(--text-muted);
  font-size: 13px;
  letter-spacing: 1px;
}
.sec-more {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 13.5px;
  transition: color 0.25s ease;
}
.sec-more:hover {
  color: var(--color-primary);
}

.pinned-section {
  margin-bottom: 36px;
  animation: hero-in 0.7s ease backwards;
}

/* 星耀网格：首篇横向大卡铺满整行，其余自适应并排 */
.pinned-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.pin-card {
  cursor: pointer;
}
.pin-lead {
  grid-column: 1 / -1;
  /* 首篇大卡：封面左 + 内容右（宽屏），移动端自动换行为纵向 */
  display: grid;
  grid-template-columns: minmax(0, 11fr) minmax(0, 9fr);
  gap: 20px;
  align-items: center;
}

.pin-cover {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: var(--bg-page);
}
.pin-title {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-main);
  transition: color 0.25s ease;
}
.pin-card:hover .pin-title {
  color: var(--color-primary);
}
.pin-title.small {
  font-size: 17px;
}
.pin-summary {
  margin: 0 0 12px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.pin-summary.clamp2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pin-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  color: var(--text-muted);
  font-size: 12px;
}
.meta-item {
  white-space: nowrap;
}

/* 置顶徽章：封面左上角，毛玻璃底 + 点缀色 */
.pin-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  letter-spacing: 1px;
  color: var(--color-accent);
  background: color-mix(in srgb, var(--bg-page) 78%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-accent) 45%, transparent);
  backdrop-filter: blur(6px);
  z-index: 1;
}

.stream-section {
  animation: hero-in 0.7s 0.1s ease backwards;
}

/* ============================================================
   筛选区（沿用原首页交互：热门 TopN + 展开全部）
   ============================================================ */
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
  .hero-screen {
    margin-top: -20px;
    min-height: calc(100vh - 60px);
  }
  .hero-title {
    font-size: 34px;
    letter-spacing: 5px;
  }
  .hero-typed {
    font-size: 14.5px;
  }
  /* 小屏光点收敛数量感（隐藏后半，保留氛围即可） */
  .hero-mote:nth-of-type(n + 7) {
    display: none;
  }
  .pin-lead {
    grid-template-columns: 1fr;
  }
  .pin-title {
    font-size: 18px;
  }
}

/* 尊重「减少动态」系统偏好：停掉全部首屏装饰动画（无障碍）。
 * 打字机的静态回退由脚本侧判断处理（见 onMounted）。 */
@media (prefers-reduced-motion: reduce) {
  .hero-char,
  .hero-mote,
  .meteor,
  .horizon,
  .horizon-pulse,
  .scroll-cue,
  .scroll-cue::after,
  .hero-typed,
  .hero-search,
  .pinned-section,
  .stream-section,
  .article-card {
    animation: none;
  }
}
</style>
