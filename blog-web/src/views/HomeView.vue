<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, Search } from '@element-plus/icons-vue'

import { getPinnedArticles } from '@/api/article'
import GlassCard from '@/components/GlassCard.vue'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import DesignShowcase from '@/components/DesignShowcase.vue'
import type { ArticleListItem } from '@/types'

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

/**
 * 流星：4 颗按固定种子随机的位置/周期/相位/长度。多颗互质的
 * 节奏叠加后，「何时出现」的间隔不重复 —— 比固定 delay 更像自然星空。
 */
const heroMeteors = computed(() => {
  const rnd = mulberry32(0x5eed)
  return Array.from({ length: 4 }, (_, i) => ({
    id: i,
    style: {
      top: `${4 + rnd() * 30}%`,
      left: `${36 + rnd() * 52}%`,
      '--dur': `${(6.5 + rnd() * 8).toFixed(1)}s`,
      '--delay': `${(rnd() * 11).toFixed(1)}s`,
      '--len': `${110 + Math.round(rnd() * 90)}px`,
    },
  }))
})

// ==================== 数据 ====================
/** 置顶文章（星耀推荐区，可复数） */
const pinned = ref<ArticleListItem[]>([])
/** 首篇置顶（模板类型收窄用；pinned 非空时必有值） */
const leadPinned = computed(() => pinned.value[0])

const streamEl = ref<HTMLElement | null>(null)

/** 置顶清单拉取：失败静默（星耀区直接隐藏，不影响主流程） */
async function loadPinned() {
  try {
    pinned.value = await getPinnedArticles(5)
  } catch {
    pinned.value = []
  }
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

onMounted(() => {
  loadPinned()
  // 尊重「减少动态」：不跑打字机，直接显示静态标语
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    typedText.value = HERO_PHRASES[1] ?? ''
  } else {
    typeTick()
  }
})

onBeforeUnmount(() => {
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

      <!-- 流星：种子随机的数量/轨迹/节奏，出现频率不固定 -->
      <span
        v-for="m in heroMeteors"
        :key="m.id"
        class="meteor"
        :style="m.style"
        aria-hidden="true"
      ></span>

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

    <!-- ============ 设计思路：项目架构与亮点展示（点击/自动轮播切换） ============ -->
    <DesignShowcase />

  </div>
</template>

<style scoped>
.home-view {
  /* 首屏 100vw 出血：剪裁横向溢出（clip 不产生滚动容器，不影响吸顶导航） */
  overflow-x: clip;
}

/* ============================================================
   首屏英雄区：首页路由启用 fullBleed（PublicLayout.content-full）
   解除限宽，此处天然铺满全视口 —— 不再依赖负 margin 出血，
   从结构上杜绝「出血被祖先 overflow 裁剪」的回归。
   ============================================================ */
.hero-screen {
  position: relative;
  /* svh：移动端地址栏收展时以小视口为准，首屏始终恰好满屏 */
  min-height: calc(100svh - 60px);
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
 * 沿局部 X 轴移动，自然形成左下方向的轨迹），头亮尾淡。
 * 数量/位置/周期/相位由脚本按固定种子随机生成 —— 多颗不同节奏
 * 叠加出「出现频率不固定」的自然观感。 */
.meteor {
  position: absolute;
  width: var(--len, 150px);
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(270deg, transparent, var(--meteor-color));
  transform: rotate(-32deg);
  opacity: 0;
  pointer-events: none;
  animation: meteor-fly var(--dur, 9s) linear var(--delay, 3s) infinite;
  --meteor-color: rgba(47, 125, 90, 0.85);
}
html.dark .meteor {
  --meteor-color: rgba(235, 240, 255, 0.9);
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
}
/* 桌面端首屏恰好满屏时，24% 会让亮条悬在半空 —— 宽屏下贴到首屏底部
   （滚动提示按钮上方），移动端首屏被内容撑高保持原节奏 */
@media (min-width: 900px) {
  .horizon {
    bottom: 52px;
  }
}
.horizon {
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

/* fullBleed 布局下内容区块自行恢复限宽（等效原 .content 约束），
 * 首屏以外的阅读宽度与全站一致 */
.pinned-section {
  max-width: 1200px;
  margin-inline: auto;
  padding-inline: 24px;
  /* 三段式留白：首屏 / 星耀 / 设计思路各自成章，不再紧凑 */
  padding-top: 96px;
}

.pinned-section {
  padding-top: 8px;
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



@media (max-width: 768px) {
  .hero-screen {
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
  .pinned-section {
    padding-inline: 16px;
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
  .pinned-section {
    animation: none;
  }
}
</style>
