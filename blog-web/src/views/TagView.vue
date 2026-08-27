<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'

import { getArticlePage } from '@/api/article'
import { getHotTags, getTagList } from '@/api/tag'
import ArticleRowSkeleton from '@/components/ArticleRowSkeleton.vue'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem, Tag } from '@/types'

const router = useRouter()

const tags = ref<Tag[]>([])
/** 标签 -> 已发布文章数（热门接口聚合），文章越多的标签渲染成越亮越大的星 */
const counts = ref<Map<string, number>>(new Map())
const tagsReady = ref(false)
const activeId = ref<string>('')
const articles = ref<ArticleListItem[]>([])
const loading = ref(false)

/** 视口是否移动端档：星海网格列数随屏宽变化，保证散落而不拥挤 */
const isMobile = ref(false)
const mobileMq = window.matchMedia('(max-width: 768px)')

function onMqChange(e: MediaQueryListEvent) {
  isMobile.value = e.matches
}

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

/**
 * mulberry32 伪随机（固定种子）：每颗星的坐标与节奏确定可复现，
 * 刷新/重渲染不会「跳位」，符合星海恒定感的直觉。
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

/** 星海中的一颗「关键词星」：位置/字号档/漂浮与闪烁节奏 */
interface StarTag extends Tag {
  x: number
  y: number
  size: 'sm' | 'md' | 'lg'
  floatDur: string
  floatDelay: string
  twDelay: string
}

/** 星海网格行数（画布高度也随行数展开，小标签集保持紧凑） */
const seaRows = computed(() => {
  const n = tags.value.length
  if (!n) return 1
  const cols = isMobile.value
    ? Math.max(2, Math.ceil(Math.sqrt(n)))
    : Math.max(3, Math.ceil(Math.sqrt(n * 3.2)))
  return Math.ceil(n / cols)
})

/**
 * 画布相对视口的放大倍数：标签散布在更大的「海」里，拖拽探索。
 * 少量标签只微扩张（初始几乎全可见，保留拖拽余量不显空洞）；
 * 标签多时按行数放大，探索空间随之增长（上限 220%）。
 */
const seaCanvasWidthPct = computed(() => (tags.value.length <= 6 ? 132 : 160))
const seaCanvasHeightPct = computed(() => {
  if (tags.value.length <= 6) return 122
  return Math.min(220, Math.max(140, seaRows.value * 46))
})
const seaCanvasWidth = computed(() => `${seaCanvasWidthPct.value}%`)
const seaCanvasHeight = computed(() => `${seaCanvasHeightPct.value}%`)

/**
 * 星海布局：网格打散 + cell 内伪随机漂移。
 * 先按等分网格保证任意数量标签互不重叠（可读性底线），
 * 再在各自 cell 内随机偏移打破均匀感 —— 视觉上「散落」，数学上不碰撞。
 *
 * 坐标系两步走：
 *   1. 先按「视口百分比」设计布局（初始全部落在可视区内、不贴边）；
 *   2. 再线性映射到「画布坐标」（画布比视口大）。初始居中滚动时
 *      视口恰好对准这片设计区 —— 标签全部可见；拖拽平移后星空
 *      滚出视野，形成「在海上航行探索」的效果。标签永远钳制在
 *      画布内部（一定范围内），不会漂出海域。
 */
const starTags = computed<StarTag[]>(() => {
  const n = tags.value.length
  if (!n) return []
  const cols = isMobile.value
    ? Math.max(2, Math.ceil(Math.sqrt(n)))
    : Math.max(3, Math.ceil(Math.sqrt(n * 3.2)))
  const rows = Math.ceil(n / cols)
  // 边缘安全区（视口百分比）：药丸标签以中心定位，太贴边会被圆角卡片裁剪
  // （移动端标签更宽，按半宽预留更大的百分比安全区）
  const edge = isMobile.value ? 22 : 11
  // 视口坐标 -> 画布坐标映射：初始滚动居中时视口对准画布正中的 100/W 区段
  const originX = ((seaCanvasWidthPct.value - 100) / 2) * (100 / seaCanvasWidthPct.value)
  const scaleX = 100 / seaCanvasWidthPct.value
  const originY = ((seaCanvasHeightPct.value - 100) / 2) * (100 / seaCanvasHeightPct.value)
  const scaleY = 100 / seaCanvasHeightPct.value
  return tags.value.map((t, i) => {
    const rnd = mulberry32(0x9e3779b9 ^ ((i + 1) * 2654435761))
    const col = i % cols
    const row = Math.floor(i / cols)
    const rawX = ((col + 0.16 + rnd() * 0.68) / cols) * 100
    const viewX = Math.min(100 - edge, Math.max(edge, rawX))
    const viewY = ((row + 0.16 + rnd() * 0.68) / rows) * 100
    const x = originX + viewX * scaleX
    const y = originY + viewY * scaleY
    const count = counts.value.get(t.id)
    const size: StarTag['size'] =
      count === undefined
        ? rnd() < 0.3
          ? 'lg'
          : rnd() < 0.65
            ? 'md'
            : 'sm' // 无统计数据时伪随机分档
        : count >= 5
          ? 'lg'
          : count >= 1
            ? 'md'
            : 'sm' // 产出越多的关键词是越亮的星
    return {
      ...t,
      x,
      y,
      size,
      floatDur: `${6 + ((rnd() * 6) | 0)}s`,
      floatDelay: `${-((rnd() * 8) | 0)}s`,
      twDelay: `${-((rnd() * 4) | 0)}s`,
    }
  })
})

// ==================== 星海画布拖拽平移 ====================
// 成熟方案：原生滚动容器 + Pointer Events 拖拽改写 scrollLeft/scrollTop。
// 边界钳制由浏览器滚动机制天然保证（画布 160%x N% 超出视口即有界），
// 无需手写 clamp；setPointerCapture 保证指针移出容器也持续跟踪。
const viewportEl = ref<HTMLElement | null>(null)
const dragging = ref(false)
const showHint = ref(true)
let panPointerId: number | null = null
let panLast = { x: 0, y: 0 }

/** 只有按下在空白处才进入拖拽：标签按钮的点击交互完全不受影响 */
function onPanDown(e: PointerEvent) {
  if ((e.target as HTMLElement).closest('button')) return
  const el = viewportEl.value
  if (!el) return
  panPointerId = e.pointerId
  panLast = { x: e.clientX, y: e.clientY }
  dragging.value = true
  el.setPointerCapture(e.pointerId)
}

function onPanMove(e: PointerEvent) {
  if (!dragging.value || e.pointerId !== panPointerId) return
  const el = viewportEl.value
  if (!el) return
  const dx = e.clientX - panLast.x
  const dy = e.clientY - panLast.y
  if (dx !== 0 || dy !== 0) showHint.value = false
  panLast = { x: e.clientX, y: e.clientY }
  el.scrollLeft -= dx
  el.scrollTop -= dy
}

function onPanUp(e: PointerEvent) {
  if (e.pointerId !== panPointerId) return
  dragging.value = false
  panPointerId = null
  const el = viewportEl.value
  if (el?.hasPointerCapture(e.pointerId)) el.releasePointerCapture(e.pointerId)
}

/** 初始定位到画布中心：向四周都有可探索的留白 */
function centerSea() {
  const el = viewportEl.value
  if (!el) return
  el.scrollLeft = (el.scrollWidth - el.clientWidth) / 2
  el.scrollTop = (el.scrollHeight - el.clientHeight) / 2
}

async function loadTags() {
  try {
    // 文章数并行拉取：失败不阻塞星海渲染（字号退化为伪随机档位）
    const [list, hot] = await Promise.all([getTagList(), getHotTags(50).catch(() => [])])
    tags.value = list
    counts.value = new Map(hot.map((h) => [h.id, h.articleCount]))
  } finally {
    tagsReady.value = true
  }
}

async function loadArticles(tagId: string) {
  loading.value = true
  articles.value = []
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

function tagTitle(s: StarTag) {
  const c = counts.value.get(s.id)
  return c === undefined ? `标签：${s.name}` : `${s.name}：${c} 篇文章`
}

onMounted(() => {
  isMobile.value = mobileMq.matches
  mobileMq.addEventListener('change', onMqChange)
  loadTags()
  nextTick(centerSea)
})

onBeforeUnmount(() => {
  mobileMq.removeEventListener('change', onMqChange)
})
</script>

<template>
  <div class="tag-view">
    <header class="page-header">
      <h1 class="page-title">标签</h1>
      <p class="page-sub">散落在星海中的关键词</p>
    </header>

    <!-- 星海：可拖拽画布。标签是散落其间的发光星点（网格打散防重叠 +
         伪随机漂移造「散落」感）；按住空白处拖动可平移探索整片海域 -->
    <GlassCard padded="lg" no-hover class="star-sea">
      <div
        ref="viewportEl"
        class="sea-viewport"
        :class="{ dragging }"
        @pointerdown="onPanDown"
        @pointermove="onPanMove"
        @pointerup="onPanUp"
        @pointercancel="onPanUp"
      >
        <div class="sea-canvas" :style="{ width: seaCanvasWidth, height: seaCanvasHeight }">
          <div class="sea-dust dust-a" aria-hidden="true"></div>
          <div class="sea-dust dust-b" aria-hidden="true"></div>

          <template v-if="starTags.length">
            <div
              v-for="(s, i) in starTags"
              :key="s.id"
              class="star-pos"
              :style="{ left: `${s.x}%`, top: `${s.y}%`, animationDelay: `${i * 70}ms` }"
            >
              <button
                type="button"
                class="sea-tag"
                :class="[`size-${s.size}`, { active: activeId === s.id }]"
                :style="{
                  '--float-dur': s.floatDur,
                  '--float-delay': s.floatDelay,
                  '--tw-delay': s.twDelay,
                }"
                :title="tagTitle(s)"
                @click="select(s.id)"
              >
                <i class="star-core" aria-hidden="true"></i>
                <span class="star-label"># {{ s.name }}</span>
              </button>
            </div>
          </template>
          <p v-else-if="tagsReady" class="sea-empty">星海静谧，还没有关键词散落于此</p>
        </div>
      </div>
      <transition name="hint-fade">
        <p v-if="showHint && starTags.length" class="sea-hint">✦ 按住空白处拖动 · 探索星海</p>
      </transition>
    </GlassCard>

    <!-- 该标签下的文章：骨架屏占位 -> 内容无缝替换（与首页一致的懒加载策略） -->
    <section v-if="activeId" class="tag-articles">
      <template v-if="loading">
        <p class="receiving">正在接收星际信号<i></i><i></i><i></i></p>
        <ArticleRowSkeleton v-for="n in 5" :key="n" :index="n - 1" />
      </template>
      <template v-else>
        <GlassCard
          v-for="(a, index) in articles"
          :key="a.id"
          class="article-row"
          :style="{ animationDelay: `${index * 60}ms` }"
          padded="sm"
          @click="goDetail(a.id)"
        >
          <div class="row-main">
            <span class="row-title">{{ a.title }}</span>
            <span class="row-summary">{{ a.summary }}</span>
          </div>
          <span class="row-date">{{ formatDate(a.createTime) }}</span>
        </GlassCard>
        <div v-if="articles.length === 0" class="empty">该标签下暂无已发布文章</div>
      </template>
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

/* ============================================================
   星海容器
   双主题意象：暗色「星际拓荒」= 深空星海（冷白星点 + 星云紫光晕）；
   亮色「自然绿意」= 林间萤海（萤绿光点 + 暖棕微光），同一布局只换色。
   ============================================================ */
.star-sea {
  --sea-dust: rgba(47, 125, 90, 0.32); /* 背景微尘：亮色 = 萤火 */
  --star-core: #2f7d5a; /* 星体：亮色 = 森林绿光点 */
  --star-glow: rgba(47, 125, 90, 0.45);
  position: relative;
  height: 360px;
  margin-bottom: 28px;
  overflow: hidden; /* 圆角裁剪拖拽画布的溢出内容 */
}
html.dark .star-sea {
  --sea-dust: rgba(226, 233, 255, 0.85);
  --star-core: #f2f5ff;
  --star-glow: rgba(148, 136, 245, 0.55);
}

/* 视口：原生滚动容器承载拖拽平移（成熟方案：改写 scrollLeft/scrollTop，
 * 边界钳制由浏览器滚动机制天然保证）。滚动条隐藏，触摸端 touch-action: pan-y
 * 让竖向滑动仍归页面滚动，横向拖拽才平移星海。 */
.sea-viewport {
  position: absolute;
  inset: 0;
  overflow: auto;
  scrollbar-width: none;
  cursor: grab;
  touch-action: pan-y;
  overscroll-behavior: contain;
}
.sea-viewport::-webkit-scrollbar {
  display: none;
}
.sea-viewport.dragging {
  cursor: grabbing;
}

/* 画布：比视口更大（宽 160%，高随行数 132%~220%），坐标相对画布散布 */
.sea-canvas {
  position: relative;
  min-height: 100%;
}

.sea-hint {
  position: absolute;
  bottom: 12px;
  left: 0;
  right: 0;
  margin: 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 2px;
  pointer-events: none;
  opacity: 0.75;
}
.hint-fade-leave-active {
  transition: opacity 0.6s ease;
}
.hint-fade-leave-to {
  opacity: 0;
}

/* 背景星尘：box-shadow 批量撒点（零 DOM 成本），双层不同节奏闪烁 */
.sea-dust {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.sea-dust::before {
  content: '';
  position: absolute;
  top: 12%;
  left: 4%;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: transparent;
  box-shadow:
    60px 40px var(--sea-dust),
    220px 180px var(--sea-dust),
    390px 60px var(--sea-dust),
    540px 240px var(--sea-dust),
    700px 120px var(--sea-dust),
    860px 300px var(--sea-dust),
    980px 80px var(--sea-dust),
    60px 300px var(--sea-dust),
    460px 150px var(--sea-dust),
    820px 200px var(--sea-dust);
  animation: dust-twinkle 5s ease-in-out infinite;
}
.sea-dust.dust-b::before {
  top: 55%;
  left: 12%;
  animation-duration: 7s;
  animation-delay: -2s;
}
@keyframes dust-twinkle {
  0%,
  100% {
    opacity: 0.9;
  }
  50% {
    opacity: 0.35;
  }
}

/* 星点定位层：入场像星体浮现（模糊 -> 清晰），transform 同时承担居中 */
.star-pos {
  position: absolute;
  transform: translate(-50%, -50%);
  animation: star-in 0.7s ease backwards;
}
@keyframes star-in {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.4);
    filter: blur(8px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
    filter: blur(0);
  }
}

/* 关键词星：星体圆点 + 药丸标签，整体缓慢漂浮（各星时长/相位错开） */
.sea-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 15px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 14px;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  max-width: 240px;
  animation: star-float var(--float-dur) ease-in-out var(--float-delay) infinite alternate;
  transition:
    opacity 0.3s ease,
    color 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}
@keyframes star-float {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(7px, -10px);
  }
}

/* 星体：常驻微弱闪烁（呼吸感），大小随字号档位 */
.star-core {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--star-core);
  box-shadow: 0 0 6px 1px var(--star-glow);
  animation: dust-twinkle 3.2s ease-in-out var(--tw-delay) infinite;
}

.size-sm .star-core {
  width: 5px;
  height: 5px;
}
.size-sm .star-label {
  font-size: 12.5px;
}
.size-lg .star-core {
  width: 9px;
  height: 9px;
}
.size-lg .star-label {
  font-size: 16.5px;
  font-weight: 600;
}

.star-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 悬停聚焦：仅当指针真正悬在「某颗星」上时，其余星体才黯淡退后。
 * 用 :has 判断容器内存在被悬停的星 —— 直接用容器 :hover 会在
 * 鼠标扫过空白区域时误黯淡全部标签（已修复的旧问题）。
 * 旧浏览器不支持 :has 时优雅退化为不黯淡，不影响功能。 */
@media (hover: hover) {
  .star-sea:has(.sea-tag:hover) .sea-tag:not(:hover):not(.active) {
    opacity: 0.4;
  }
}
.sea-tag:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-hover);
}
.sea-tag.active {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: 0 0 16px 2px var(--star-glow);
}
.sea-tag.active .star-core {
  background: #fff;
  box-shadow: 0 0 8px 2px rgba(255, 255, 255, 0.85);
  animation: none;
}

.sea-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--text-muted);
  letter-spacing: 2px;
}

/* ---------- 文章列表 ---------- */
.tag-articles {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

/* 接收信号提示：三点渐次上浮（加载主题化，替代白板转圈） */
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

.article-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
  /* 入场逐行浮起（backwards + 逐条 delay，见模板） */
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

/* 移动端：星海拉高容纳近方形网格，标签防溢出 */
@media (max-width: 768px) {
  .star-sea {
    height: 430px;
  }
  .sea-tag {
    max-width: 150px;
    padding: 6px 12px;
  }
  .size-lg .star-label {
    font-size: 14.5px;
  }
}

/* 尊重「减少动态」系统偏好：停掉漂浮/闪烁/入场（无障碍） */
@media (prefers-reduced-motion: reduce) {
  .sea-tag,
  .star-core,
  .sea-dust::before,
  .star-pos,
  .article-row,
  .receiving i {
    animation: none;
  }
}
</style>
