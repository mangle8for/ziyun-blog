<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Aim, Search } from '@element-plus/icons-vue'

import { getArticlePage } from '@/api/article'
import ArticleRowSkeleton from '@/components/ArticleRowSkeleton.vue'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem } from '@/types'

const route = useRoute()
const router = useRouter()

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
/** 时间预设范围（后端 beginDate 闭区间；endDate 缺省 = 至今） */
type RangeKey = 'all' | 'week' | 'month' | 'year'
const rangeKey = ref<RangeKey>('all')

const RANGE_OPTIONS: Array<{ key: RangeKey; label: string }> = [
  { key: 'all', label: '全部时间' },
  { key: 'week', label: '本周内' },
  { key: 'month', label: '本月内' },
  { key: 'year', label: '本年内' },
]

const results = ref<ArticleListItem[]>([])
const total = ref(0)
const loading = ref(false)
/** 是否已执行过一次有效搜索（区分「还没搜」与「搜了没结果」） */
const searched = ref(false)

// SEO
useHead({
  title: '搜索',
  meta: () => [{ name: 'description', content: '输入关键字，按时间范围快速定位紫云博客文章。' }],
})

function fmtDate(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 预设范围 -> 开始日期（本周以周一为起点） */
function rangeBeginDate(key: RangeKey) {
  const now = new Date()
  if (key === 'week') {
    const weekday = now.getDay() || 7
    return fmtDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - weekday + 1))
  }
  if (key === 'month') return fmtDate(new Date(now.getFullYear(), now.getMonth(), 1))
  if (key === 'year') return fmtDate(new Date(now.getFullYear(), 0, 1))
  return undefined
}

/**
 * 执行定位：关键字必填（标题模糊匹配），可叠加时间预设范围收紧。
 * 关键字同步到路由 query（?q=），刷新/分享后仍能还原结果。
 */
async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('请先输入关键字，再开始定位')
    searched.value = false
    results.value = []
    total.value = 0
    router.replace({ query: {} })
    return
  }
  loading.value = true
  try {
    const data = await getArticlePage({
      page: 1,
      size: 20,
      keyword: kw,
      beginDate: rangeBeginDate(rangeKey.value),
    })
    results.value = data.records
    total.value = Number(data.total)
    searched.value = true
    router.replace({
      query: { q: kw, range: rangeKey.value === 'all' ? undefined : rangeKey.value },
    })
  } finally {
    loading.value = false
  }
}

/** 输入防抖 400ms 自动定位（关键字为空时仅提示不请求） */
let debounceTimer: number | undefined
function onKeywordInput() {
  window.clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(doSearch, 400)
}

function onRangeChange(key: RangeKey) {
  rangeKey.value = key
  window.clearTimeout(debounceTimer)
  doSearch()
}

function goDetail(id: string) {
  router.push(`/article/${id}`)
}

function formatDate(iso: string) {
  return iso?.slice(0, 10) ?? ''
}

onMounted(() => {
  // 带 (?q=) 进来时自动定位一次（时间范围从路由还原）
  const qr = route.query.range
  if (qr === 'week' || qr === 'month' || qr === 'year') rangeKey.value = qr
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

    <!-- 高科技感定位器面板：HUD 角标 + 状态读数 + 扫描线 -->
    <GlassCard padded="lg" class="locator-panel" no-hover>
      <i class="hud-corner c-tl" aria-hidden="true"></i>
      <i class="hud-corner c-tr" aria-hidden="true"></i>
      <i class="hud-corner c-bl" aria-hidden="true"></i>
      <i class="hud-corner c-br" aria-hidden="true"></i>
      <div class="scanline" aria-hidden="true"></div>

      <div class="hud-status">
        <span class="hud-led" aria-hidden="true"></span>
        <span class="hud-label">TARGET · SCAN 星海定位器</span>
        <span class="hud-id">{{ searched ? `LOCKED · ${total}` : 'STANDBY' }}</span>
      </div>

      <div class="search-bar">
        <el-input
          v-model="keyword"
          placeholder="输入文章标题关键字…"
          size="large"
          clearable
          @input="onKeywordInput"
          @keyup.enter="doSearch"
          @clear="doSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="doSearch">定位</el-button>
          </template>
        </el-input>
      </div>

      <div class="range-bar">
        <button
          v-for="opt in RANGE_OPTIONS"
          :key="opt.key"
          type="button"
          class="range-chip"
          :class="{ active: rangeKey === opt.key }"
          @click="onRangeChange(opt.key)"
        >
          <el-icon v-if="rangeKey === opt.key && opt.key !== 'all'" :size="11"><Aim /></el-icon>
          {{ opt.label }}
        </button>
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
          定位到 <strong>{{ total }}</strong> 篇相关文章
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
          未捕捉到信号 —— 换个关键字或放宽时间范围再试
        </div>
        <div v-if="!searched && !loading" class="empty">
          输入关键字开始定位 —— 可叠加 本周 / 本月 / 本年 时间范围
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

/* ============================================================
   定位器面板（高科技 HUD 感）：
   四角框线角标 + 顶部状态读数（LED 呼吸灯 + 等宽字标签 + 锁定计数）
   + 缓慢下扫的扫描线。颜色全部走主题变量，双主题自适应。
   ============================================================ */
.locator-panel {
  --hud-line: rgba(47, 125, 90, 0.55);
  position: relative;
  max-width: 760px;
  margin: 0 auto 28px;
  overflow: hidden;
}
html.dark .locator-panel {
  --hud-line: rgba(148, 136, 245, 0.55);
}

.hud-corner {
  position: absolute;
  width: 16px;
  height: 16px;
  pointer-events: none;
  border-color: var(--hud-line);
  border-style: solid;
  border-width: 0;
}
.c-tl {
  top: 8px;
  left: 8px;
  border-top-width: 2px;
  border-left-width: 2px;
}
.c-tr {
  top: 8px;
  right: 8px;
  border-top-width: 2px;
  border-right-width: 2px;
}
.c-bl {
  bottom: 8px;
  left: 8px;
  border-bottom-width: 2px;
  border-left-width: 2px;
}
.c-br {
  bottom: 8px;
  right: 8px;
  border-bottom-width: 2px;
  border-right-width: 2px;
}

/* 状态行：LED 呼吸 + 等宽字读数（暗号式大写增强仪器感） */
.hud-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10.5px;
  letter-spacing: 2px;
  color: var(--text-muted);
  text-transform: uppercase;
}
.hud-led {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 6px var(--color-primary);
  animation: led-breathe 1.6s ease-in-out infinite;
}
@keyframes led-breathe {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
.hud-label {
  flex: 1;
}
.hud-id {
  color: var(--color-primary);
}

/* 扫描线：自上而下缓慢掠过，低透明度不干扰阅读 */
.scanline {
  position: absolute;
  left: 0;
  right: 0;
  height: 48px;
  background: linear-gradient(
    180deg,
    transparent,
    color-mix(in srgb, var(--color-primary) 12%, transparent),
    transparent
  );
  animation: scan-sweep 5.5s ease-in-out infinite;
  pointer-events: none;
}
@keyframes scan-sweep {
  0% {
    top: -12%;
  }
  100% {
    top: 112%;
  }
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

/* 时间预设：等宽药丸档位（选中态点亮瞄准意象） */
.range-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
.range-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 14px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 12.5px;
  letter-spacing: 1px;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition:
    color 0.25s ease,
    border-color 0.25s ease,
    background-color 0.3s ease;
}
.range-chip:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.range-chip.active {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
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
  .receiving i,
  .hud-led,
  .scanline {
    animation: none;
  }
}
</style>
