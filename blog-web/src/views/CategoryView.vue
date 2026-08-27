<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter } from 'vue-router'

import { getArticlePage } from '@/api/article'
import { getCategoryList, getHotCategories } from '@/api/category'
import ArticleRowSkeleton from '@/components/ArticleRowSkeleton.vue'
import GlassCard from '@/components/GlassCard.vue'
import type { ArticleListItem, Category } from '@/types'

const router = useRouter()

const categories = ref<Category[]>([])
/** 分类 -> 已发布文章数（热门接口聚合）：星域里「行星」的数量 */
const counts = ref<Map<string, number>>(new Map())
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

/** 渲染用星域卡片：编号档案 + 行星数，形成「星域归档」意象 */
interface Sector extends Category {
  code: string
  count: number
}

/** 分类列表 -> 星域档案（编号按列表序生成，稳定不跳变） */
const sectors = computed<Sector[]>(() =>
  categories.value.map((c, i) => ({
    ...c,
    code: `SECTOR-${String(i + 1).padStart(2, '0')}`,
    count: counts.value.get(c.id) ?? 0,
  })),
)

async function loadCategories() {
  const [list, hot] = await Promise.all([getCategoryList(), getHotCategories(50).catch(() => [])])
  categories.value = list
  counts.value = new Map(hot.map((h) => [h.id, h.articleCount]))
}

async function loadArticles(categoryId: string) {
  loading.value = true
  articles.value = []
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

    <!-- 星域档案：每个分类是一片有恒星、轨道与行星的主题星区 -->
    <div class="category-grid">
      <button
        v-for="(s, i) in sectors"
        :key="s.id"
        type="button"
        class="sector"
        :class="{ active: activeId === s.id }"
        :style="{ animationDelay: `${i * 90}ms` }"
        @click="select(s.id)"
      >
        <span class="sector-code" aria-hidden="true">{{ s.code }}</span>
        <span class="sector-stars" aria-hidden="true"></span>

        <span class="sector-core" aria-hidden="true">
          <i class="orbit o1"><i class="moon"></i></i>
          <i class="orbit o2"></i>
          <i class="planet"></i>
        </span>

        <span class="sector-name">{{ s.name }}</span>
        <span class="sector-desc">{{ s.description || '暂无简介' }}</span>
        <span class="sector-count">{{ s.count }} 篇文章</span>
      </button>
    </div>

    <!-- 该分类下的文章：骨架屏占位 -> 内容无缝替换（与首页一致的懒加载策略） -->
    <section v-if="activeId" class="cat-articles">
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
        <div v-if="articles.length === 0" class="empty">该分类下暂无已发布文章</div>
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
   星域卡片：button 语义（整卡可点），样式与 GlassCard 同源
   （毛玻璃底 + 微光边框），内部是「恒星核 + 双轨道 + 卫星」的星区。
   双主题：亮色 = 白日营地望远镜中的绿意星域；暗色 = 深空霓虹星区。
   颜色全部走主题变量，组件层不写死色值。
   ============================================================ */
.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}

.sector {
  --sector-glow: rgba(47, 125, 90, 0.5); /* 恒星光晕（亮色：森林绿） */
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 20px 22px;
  font-family: inherit;
  text-align: center;
  overflow: hidden;
  cursor: pointer;
  user-select: none;
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border-color);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease,
    border-color 0.3s ease,
    background-color 0.4s ease;
  /* 入场逐卡浮现 */
  animation: sector-in 0.5s ease backwards;
}
html.dark .sector {
  --sector-glow: rgba(148, 136, 245, 0.6); /* 暗色：星云紫 */
}
@keyframes sector-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.sector:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-hover);
  transform: translateY(-3px);
}
.sector.active {
  border-color: var(--color-primary);
  box-shadow:
    0 0 0 1px var(--color-primary),
    0 0 18px 2px var(--sector-glow),
    var(--shadow-card);
}

/* 档案编号：星域归档的「坐标」注记 */
.sector-code {
  position: absolute;
  top: 12px;
  left: 14px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--text-muted);
  opacity: 0.7;
}

/* 卡内装饰星点：box-shadow 撒点，赋予每片星域「有居民」的质感 */
.sector-stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.sector-stars::before {
  content: '';
  position: absolute;
  top: 18%;
  right: 8%;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: transparent;
  box-shadow:
    -30px 26px var(--sector-glow),
    22px 52px var(--sector-glow),
    -46px 66px var(--sector-glow),
    40px 20px var(--sector-glow),
    -8px 88px var(--sector-glow);
  opacity: 0.5;
  animation: sector-twinkle 4.5s ease-in-out infinite;
}
@keyframes sector-twinkle {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 0.2;
  }
}

/* 恒星核 + 双轨道 + 卫星：星域的「核心天体系统」 */
.sector-core {
  position: relative;
  width: 72px;
  height: 72px;
  display: grid;
  place-items: center;
  margin-bottom: 6px;
  flex-shrink: 0;
}
.planet {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: radial-gradient(
    circle at 32% 30%,
    var(--color-primary-hover),
    var(--color-primary) 55%,
    color-mix(in srgb, var(--color-accent) 45%, var(--color-primary))
  );
  box-shadow: 0 0 14px 2px var(--sector-glow);
  transition: box-shadow 0.3s ease;
}
.sector:hover .planet {
  box-shadow: 0 0 22px 6px var(--sector-glow);
}
.orbit {
  position: absolute;
  inset: 0;
  border: 1px dashed var(--border-color);
  border-radius: 50%;
}
.orbit.o2 {
  inset: 8px;
  border-style: solid;
  opacity: 0.5;
}
.orbit.o1 {
  animation: orbit-spin 26s linear infinite;
}
.moon {
  position: absolute;
  top: -2.5px;
  left: 50%;
  width: 5px;
  height: 5px;
  margin-left: -2.5px;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 6px var(--color-accent);
}
@keyframes orbit-spin {
  to {
    transform: rotate(360deg);
  }
}

.sector-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
  transition: color 0.25s ease;
}
.sector.active .sector-name {
  color: var(--color-primary);
}
.sector-desc {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sector-count {
  margin-top: 2px;
  padding: 2px 10px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 11.5px;
  letter-spacing: 1px;
}

/* ---------- 文章列表 ---------- */
.cat-articles {
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

@media (max-width: 768px) {
  .sector {
    padding: 22px 16px 18px;
  }
}

/* 尊重「减少动态」系统偏好：停掉轨道/闪烁/入场（无障碍） */
@media (prefers-reduced-motion: reduce) {
  .sector,
  .orbit.o1,
  .sector-stars::before,
  .article-row,
  .receiving i {
    animation: none;
  }
}
</style>
