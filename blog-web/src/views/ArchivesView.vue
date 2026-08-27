<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'

import { getArticlePage } from '@/api/article'
import ArticleRowSkeleton from '@/components/ArticleRowSkeleton.vue'
import GlassCard from '@/components/GlassCard.vue'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import type { ArticleListItem } from '@/types'

const router = useRouter()

/** 每页条数：咸鱼式下滑翻页，一页 8 条卡片流刚好一屏半 */
const PAGE_SIZE = 8

const articles = ref<ArticleListItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
/** 是否已加载完最后一页（hasNext 为 false 后停止监听） */
const done = ref(false)
const loadError = ref(false)

// SEO
useHead({
  title: '文章',
  meta: () => [{ name: 'description', content: '按时间点排列的全部文章 —— 紫云博客航行日志。' }],
})

/** 按年份分组（保持时间倒序的既有顺序，仅按年聚拢渲染） */
const groups = computed(() => {
  const map = new Map<string, ArticleListItem[]>()
  for (const a of articles.value) {
    const year = a.createTime?.slice(0, 4) ?? '未知'
    if (!map.has(year)) map.set(year, [])
    map.get(year)!.push(a)
  }
  return [...map.entries()].map(([year, items]) => ({ year, items }))
})

/** 首屏立即拉第一页；之后由 IntersectionObserver 触发翻页 */
async function loadMore() {
  if (loading.value || done.value) return
  loading.value = true
  loadError.value = false
  try {
    const data = await getArticlePage({ page: page.value, size: PAGE_SIZE })
    articles.value.push(...data.records)
    total.value = Number(data.total)
    page.value++
    if (!data.hasNext) done.value = true
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
    // 内容追加后若哨兵仍在视口内（短屏/大屏场景），IntersectionObserver
    // 不会再发交叉事件 —— 主动补一次检查，保证连续加载
    checkSentinel()
  }
}

const sentinelEl = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

/** 哨兵进入视口（提前 600px 预加载）则翻下一页 */
function onIntersect(entries: IntersectionObserverEntry[]) {
  if (entries.some((e) => e.isIntersecting)) loadMore()
}

function checkSentinel() {
  const el = sentinelEl.value
  if (!el || done.value || loading.value) return
  const rect = el.getBoundingClientRect()
  if (rect.top < window.innerHeight + 600) loadMore()
}

function retry() {
  loadError.value = false
  loadMore()
}

function goDetail(id: string) {
  router.push(`/article/${id}`)
}

function formatDate(iso: string) {
  return iso?.slice(0, 10) ?? ''
}

onMounted(() => {
  observer = new IntersectionObserver(onIntersect, { rootMargin: '600px 0px' })
  if (sentinelEl.value) observer.observe(sentinelEl.value)
  loadMore()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div class="archives-view">
    <header class="page-header">
      <h1 class="page-title">文章</h1>
      <p class="page-sub">
        按时间点排列的航行日志<template v-if="total"> · 共 {{ total }} 篇</template>
      </p>
    </header>

    <!-- 时间轴：左侧轨道线 + 年份大节点 + 文章卡片流（高度随内容/封面自然变化） -->
    <div class="timeline">
      <section v-for="g in groups" :key="g.year" class="tl-group">
        <div class="tl-year">
          <i class="tl-dot year" aria-hidden="true"></i>
          <h2 class="tl-year-text">{{ g.year }}</h2>
          <span class="tl-year-count">{{ g.items.length }} 篇</span>
        </div>

        <article v-for="(a, i) in g.items" :key="a.id" class="tl-item">
          <i class="tl-dot item" aria-hidden="true"></i>
          <GlassCard
            class="tl-card"
            padded="md"
            :style="{ animationDelay: `${(i % PAGE_SIZE) * 60}ms` }"
            @click="goDetail(a.id)"
          >
            <div class="tl-inner">
              <!-- 封面：懒加载（低清预览 + 原图淡入），无封面时退化为标题卡 -->
              <div class="tl-cover" v-if="a.cover">
                <ProgressiveImage :src="a.cover" :alt="a.title" />
              </div>
              <div class="tl-body">
                <span class="tl-date">{{ formatDate(a.createTime) }}</span>
                <h3 class="tl-title">{{ a.title }}</h3>
                <p class="tl-summary" v-if="a.summary">{{ a.summary }}</p>
                <div class="tl-meta">
                  <span v-if="a.categoryName" class="tl-chip">{{ a.categoryName }}</span>
                  <span v-for="t in a.tags" :key="t.id" class="tl-chip tag"># {{ t.name }}</span>
                  <span class="tl-views">{{ a.viewCount }} 阅读</span>
                </div>
              </div>
            </div>
          </GlassCard>
        </article>
      </section>

      <!-- 加载中：骨架卡片占位（与卡片同形，加载完无缝替换） -->
      <template v-if="loading">
        <div class="tl-loading">
          <ArticleRowSkeleton v-for="n in 3" :key="n" :index="n - 1" />
        </div>
      </template>

      <!-- 加载失败重试 -->
      <div v-if="loadError && !loading" class="tl-end">
        <p>星舰信号中断，加载失败</p>
        <el-button round @click="retry">重新连接</el-button>
      </div>

      <!-- 终点 -->
      <p v-else-if="done && articles.length" class="tl-end">✦ 已抵达星海尽头</p>
      <p v-else-if="done && !articles.length && !loading" class="tl-end">这片星海还没有文章</p>

      <!-- 无限滚动哨兵 -->
      <div ref="sentinelEl" class="sentinel" aria-hidden="true"></div>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  text-align: center;
  padding: 12px 0 26px;
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

/* ---------- 时间轴 ---------- */
.timeline {
  position: relative;
  padding-left: 30px;
  max-width: 860px;
  margin: 0 auto;
}
/* 轨道线：主题边框色渐变淡入淡出 */
.timeline::before {
  content: '';
  position: absolute;
  left: 8px;
  top: 6px;
  bottom: 0;
  width: 2px;
  border-radius: 2px;
  background: linear-gradient(
    180deg,
    transparent,
    var(--border-color) 3%,
    var(--border-color) 97%,
    transparent
  );
}

.tl-year {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 26px 0 16px;
}
.tl-year-text {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 3px;
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.tl-year-count {
  color: var(--text-muted);
  font-size: 12.5px;
  letter-spacing: 1px;
}

/* 节点圆点：年份大节点空心发光环，条目小实心点 */
.tl-dot {
  position: absolute;
  border-radius: 50%;
  background: var(--bg-page);
  border: 2px solid var(--color-primary);
}
.tl-dot.year {
  left: -30px;
  width: 14px;
  height: 14px;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-primary) 18%, transparent);
}
.tl-dot.item {
  left: -26px;
  top: 26px;
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border: none;
  box-shadow: 0 0 6px color-mix(in srgb, var(--color-primary) 55%, transparent);
}

.tl-item {
  position: relative;
  margin-bottom: 16px;
}

/* 卡片：封面左 + 内容右（宽屏），内容高度决定卡高（不固定大小的卡片） */
.tl-card {
  cursor: pointer;
  animation: tl-in 0.5s ease backwards;
}
@keyframes tl-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.tl-inner {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}
.tl-cover {
  flex: 0 0 240px;
  border-radius: 10px;
  overflow: hidden;
  aspect-ratio: 16 / 10;
  background: var(--bg-page);
}
.tl-body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tl-date {
  color: var(--text-muted);
  font-size: 12.5px;
  letter-spacing: 1px;
}
.tl-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
  transition: color 0.25s ease;
}
.tl-card:hover .tl-title {
  color: var(--color-primary);
}
.tl-summary {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13.5px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tl-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  margin-top: 2px;
}
.tl-chip {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}
.tl-chip.tag {
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}
.tl-views {
  color: var(--text-muted);
  font-size: 12px;
  margin-left: auto;
}

.tl-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 10px 0;
}

.tl-end {
  text-align: center;
  color: var(--text-muted);
  letter-spacing: 2px;
  padding: 34px 0 10px;
}
.tl-end p {
  margin: 0 0 14px;
}

.sentinel {
  height: 1px;
}

@media (max-width: 768px) {
  .timeline {
    padding-left: 24px;
  }
  .timeline::before {
    left: 5px;
  }
  .tl-dot.year {
    left: -24px;
  }
  .tl-dot.item {
    left: -21px;
  }
  .tl-inner {
    flex-direction: column;
  }
  .tl-cover {
    flex: none;
    width: 100%;
  }
  .tl-views {
    margin-left: 0;
  }
}
</style>
