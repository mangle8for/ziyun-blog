<script setup lang="ts">
import { h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useHead } from '@unhead/vue'
import { Link, Message } from '@element-plus/icons-vue'

import { getArticlePage } from '@/api/article'
import { getCategoryList } from '@/api/category'
import { getTagList } from '@/api/tag'
import GlassCard from '@/components/GlassCard.vue'
import ProgressiveImage from '@/components/ProgressiveImage.vue'
import { profile, skillGroups } from '@/config/about'
import type { SocialIconKey } from '@/config/about'

/**
 * GitHub 品牌图标：@element-plus/icons-vue 不含品牌图标，
 * 用 h() 渲染内联 SVG（font-size 由 el-icon 控制，继承 currentColor）。
 */
function renderGithubIcon() {
  return h(
    'svg',
    {
      viewBox: '0 0 24 24',
      width: '1em',
      height: '1em',
      fill: 'currentColor',
      'aria-hidden': 'true',
    },
    [
      h('path', {
        d: 'M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.55 0-.27-.01-1.17-.02-2.12-3.2.7-3.88-1.36-3.88-1.36-.52-1.33-1.28-1.68-1.28-1.68-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.19 1.76 1.19 1.03 1.75 2.69 1.25 3.35.95.1-.74.4-1.25.72-1.53-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11.1 11.1 0 0 1 5.8 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.12 3.05.74.81 1.18 1.83 1.18 3.09 0 4.42-2.69 5.39-5.25 5.68.41.35.77 1.05.77 2.12 0 1.53-.01 2.76-.01 3.14 0 .3.2.67.8.55A11.51 11.51 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5z',
      }),
    ],
  )
}

/** 社交图标映射：品牌图标走内联 SVG，其余用 Element Plus 图标 */
const socialIcons: Record<SocialIconKey, unknown> = {
  github: renderGithubIcon,
  email: Message,
  link: Link,
}

const aboutRoot = ref<HTMLElement | null>(null)
/** 技术栈是否已滚动进视口（进度条从 0 展开的触发条件） */
const skillsVisible = ref(false)
/** 站点统计是否已滚动进视口（数字滚动动画的触发条件） */
const statsRevealed = ref(false)
/** 头像缺省：昵称首字渐变占位 */
const nicknameInitial = profile.nickname.slice(0, 1)

// SEO：关于页标题与描述
useHead({
  title: '关于',
  meta: [
    { name: 'description', content: `${profile.signature} —— ${profile.bio}` },
  ],
})

// ==================== 站点统计（现有公开接口实时取数） ====================

const statTargets = { articles: 0, categories: 0, tags: 0 }
const displayStats = reactive({ articles: 0, categories: 0, tags: 0 })
/** 统计数字加载中（数字位显示骨架条，见 stat-value 内 sk-stat） */
const statsLoading = ref(true)
const statItems = [
  { key: 'articles' as const, label: '文章' },
  { key: 'categories' as const, label: '分类' },
  { key: 'tags' as const, label: '标签' },
]

let statsAnimated = false
let countUpFrame = 0

async function loadStats() {
  statsLoading.value = true
  try {
    const [page, cats, tags] = await Promise.all([
      getArticlePage({ page: 1, size: 1 }),
      getCategoryList(),
      getTagList(),
    ])
    statTargets.articles = Number(page.total)
    statTargets.categories = cats.length
    statTargets.tags = tags.length
    // 若统计区已先于数据到达而被揭示，立即补播动画
    if (statsRevealed.value) animateStats()
  } catch {
    // 请求拦截器已弹错误提示，此处静默（数字保持 0）
  } finally {
    statsLoading.value = false
  }
}

/**
 * 数字滚动动画：rAF 驱动 easeOutCubic，1.2s 内从 0 滚到目标值。
 * 尊重 prefers-reduced-motion：直接置为目标值，不做动画。
 */
function animateStats() {
  if (statsAnimated) return
  statsAnimated = true
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduced) {
    Object.assign(displayStats, statTargets)
    return
  }
  const duration = 1200
  const start = performance.now()
  const tick = (now: number) => {
    const p = Math.min((now - start) / duration, 1)
    const eased = 1 - Math.pow(1 - p, 3)
    displayStats.articles = Math.round(statTargets.articles * eased)
    displayStats.categories = Math.round(statTargets.categories * eased)
    displayStats.tags = Math.round(statTargets.tags * eased)
    if (p < 1) {
      countUpFrame = requestAnimationFrame(tick)
    }
  }
  countUpFrame = requestAnimationFrame(tick)
}

// ==================== 滚动揭示（IntersectionObserver） ====================

let revealObserver: IntersectionObserver | null = null

onMounted(() => {
  const root = aboutRoot.value
  if (!root) return
  revealObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        entry.target.classList.add('is-visible')
        if (entry.target.classList.contains('skills-card')) {
          skillsVisible.value = true
        }
        if (entry.target.classList.contains('stats-card')) {
          statsRevealed.value = true
          animateStats()
        }
        revealObserver?.unobserve(entry.target)
      }
    },
    { threshold: 0.15 },
  )
  root.querySelectorAll('.reveal').forEach((el) => revealObserver?.observe(el))
  loadStats()
})

onBeforeUnmount(() => {
  revealObserver?.disconnect()
  revealObserver = null
  if (countUpFrame) cancelAnimationFrame(countUpFrame)
})
</script>

<template>
  <div ref="aboutRoot" class="about-view">
    <!-- ① 博主卡片 -->
    <GlassCard no-hover padded="lg" class="section profile-card reveal">
      <div class="profile-inner">
        <div class="avatar-wrap">
          <!-- 头像：低清预览 + 原图淡入；无头像时用昵称首字占位 -->
          <ProgressiveImage
            v-if="profile.avatar"
            :src="profile.avatar"
            :alt="profile.nickname"
            :lazy="false"
          />
          <div v-else class="avatar avatar-fallback" aria-hidden="true">{{ nicknameInitial }}</div>
        </div>
        <div class="profile-info">
          <h1 class="nickname">{{ profile.nickname }}</h1>
          <p class="signature">{{ profile.signature }}</p>
          <p class="bio">{{ profile.bio }}</p>
          <div class="socials" v-if="profile.socials.length">
            <a
              v-for="social in profile.socials"
              :key="social.name"
              :href="social.url"
              :title="social.name"
              :aria-label="social.name"
              target="_blank"
              rel="noopener noreferrer"
              class="social-btn"
            >
              <el-icon :size="18"><component :is="socialIcons[social.icon]" /></el-icon>
            </a>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- ② 技术栈 -->
    <GlassCard no-hover padded="lg" class="section skills-card reveal">
      <h2 class="section-title">技术栈</h2>
      <div class="skill-groups">
        <div v-for="group in skillGroups" :key="group.title" class="skill-group">
          <h3 class="group-title">{{ group.title }}</h3>
          <div class="skill-list">
            <div v-for="skill in group.items" :key="skill.name" class="skill-item">
              <div class="skill-head">
                <span class="skill-name">{{ skill.name }}</span>
                <span class="skill-level">{{ skill.level }}%</span>
              </div>
              <div class="skill-bar">
                <span
                  class="skill-bar-fill"
                  :style="{ width: skillsVisible ? `${skill.level}%` : '0%' }"
                ></span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- ③ 站点统计 -->
    <GlassCard no-hover padded="lg" class="section stats-card reveal">
      <h2 class="section-title">站点统计</h2>
      <div class="stats-grid">
        <div v-for="item in statItems" :key="item.key" class="stat-item">
          <div class="stat-value">
            <!-- 数字骨架：数据到达前占位（懒加载类型 B：单独的数字） -->
            <span v-if="statsLoading" class="sk-stat shimmer"></span>
            <template v-else>{{ displayStats[item.key] }}</template>
          </div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.about-view {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.section {
  /* 滚动揭示：进入视口前透明下沉，进入后淡入浮起 */
  opacity: 0;
  transform: translateY(24px);
  transition:
    opacity 0.6s ease,
    transform 0.6s ease;
}

/* 三区块交错入场（首屏直接进入视口时也有层次感） */
.section:nth-child(2) {
  transition-delay: 0.08s;
}
.section:nth-child(3) {
  transition-delay: 0.16s;
}

.section.is-visible {
  opacity: 1;
  transform: none;
}

/* 区块标题：左置主题色短标尺 + 流式字号 */
.section-title {
  margin: 0 0 20px;
  font-size: clamp(20px, 4vw, 24px);
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 10px;
}
.section-title::before {
  content: '';
  width: 4px;
  height: 1.1em;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--color-primary), var(--color-accent));
}

/* ==================== ① 博主卡片 ==================== */

.profile-inner {
  display: flex;
  align-items: center;
  gap: 32px;
}

/* 头像容器：固定尺寸 + 主题色描边 + 呼吸光晕。
   描边/动画放容器上 —— 真实头像由 ProgressiveImage 铺满（border-radius 继承圆形），
   占位头像（昵称首字）直接填充容器。 */
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid var(--color-primary);
  animation: avatar-breathe 4s ease-in-out infinite;
}

/* 无头像时的昵称首字渐变占位 */
.avatar-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  user-select: none;
}

@keyframes avatar-breathe {
  0%,
  100% {
    box-shadow: 0 0 0 4px var(--border-color);
  }
  50% {
    box-shadow:
      0 0 0 4px var(--border-color),
      0 0 22px var(--color-primary);
  }
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.nickname {
  margin: 0 0 6px;
  font-size: clamp(24px, 5vw, 32px);
  font-weight: 800;
  color: var(--text-main);
}

.signature {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--color-primary);
  letter-spacing: 1px;
}

.bio {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.socials {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.social-btn {
  /* 44px 触控区（移动端可点性标准） */
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  background: var(--bg-page);
  border: 1px solid var(--border-color);
  transition:
    color 0.25s ease,
    border-color 0.25s ease,
    transform 0.25s ease,
    background-color 0.25s ease;
}

/* hover 反馈只在真正支持 hover 的设备生效（移动端无 hover 概念） */
@media (hover: hover) {
  .social-btn:hover {
    color: #fff;
    background: var(--color-primary);
    border-color: var(--color-primary);
    transform: translateY(-2px);
  }
}

/* ==================== ② 技术栈 ==================== */

.skill-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 28px 32px;
}

.group-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-secondary);
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skill-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}

.skill-name {
  font-size: 14px;
  color: var(--text-main);
}

.skill-level {
  font-size: 12px;
  color: var(--text-muted);
}

/* 进度条轨道：主题边框色（双主题都协调），不抢内容注意力 */
.skill-bar {
  height: 8px;
  border-radius: 999px;
  background: var(--border-color);
  overflow: hidden;
}

/* 填充条：主色 -> 点缀色渐变（双主题各自消费变量，天然适配） */
.skill-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
  transition: width 1s ease 0.15s;
}

/* ==================== ③ 站点统计 ==================== */

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.stat-item {
  text-align: center;
  padding: 20px 8px;
  border-radius: 12px;
  background: var(--bg-page);
  border: 1px solid var(--border-color);
}

.stat-value {
  /* 与首页 hero 标题同款的渐变数字 */
  font-size: clamp(26px, 6vw, 36px);
  font-weight: 800;
  line-height: 1.2;
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-variant-numeric: tabular-nums;
}

/* 统计数字骨架条（懒加载类型 B：单独的数字；1.1em 跟随字号缩放） */
.sk-stat {
  display: inline-block;
  width: 56px;
  height: 1.1em;
  border-radius: 8px;
  vertical-align: middle;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

/* ==================== 移动端 ==================== */

@media (max-width: 768px) {
  .profile-inner {
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 20px;
  }
  .avatar-wrap {
    width: 88px;
    height: 88px;
  }
  .avatar-fallback {
    font-size: 34px;
  }
  .socials {
    justify-content: center;
  }
  .skill-groups {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }
  .stat-item {
    padding: 16px 4px;
  }
}

/* 尊重「减少动态」系统偏好：关动画直接呈现（无障碍） */
@media (prefers-reduced-motion: reduce) {
  .section {
    opacity: 1;
    transform: none;
    transition: none;
  }
  .skill-bar-fill {
    transition: none;
  }
  .avatar-wrap {
    animation: none;
  }
}
</style>
