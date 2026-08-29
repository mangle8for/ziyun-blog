<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Brush, Cpu, Lock, MagicStick, Promotion, Search } from '@element-plus/icons-vue'

/**
 * 首页「设计思路」栏目：DeepSeek Harness 官网式的能力展示区。
 *
 * <p>左侧为可点击的能力清单（自动轮播 + 悬停暂停），右侧为对应能力的
 * 纯 CSS 模拟界面（非截图，双主题自适应），切换走淡入上浮过渡。</p>
 *
 * <p>内容全部来自本项目真实实现：架构 / AI 写作助手 / 安全 / 交付 / 体验。</p>
 */

const router = useRouter()

interface Feature {
  key: string
  icon: typeof Cpu
  title: string
  desc: string
  chips: string[]
}

const FEATURES: Feature[] = [
  {
    key: 'arch',
    icon: Cpu,
    title: '前后端分离，一切皆模块',
    desc: 'Spring Boot 3.5 + MyBatis-Plus + MySQL + Redis 打底，Vue 3 + Vite + TypeScript 驱动前端；Nginx 反向代理串联全站，统一 Result 契约与全局异常兜底——每一层职责清晰、有迹可循。',
    chips: ['Spring Boot 3.5', 'Vue 3', 'TypeScript', 'MyBatis-Plus', 'Redis', 'Nginx'],
  },
  {
    key: 'ai',
    icon: MagicStick,
    title: 'AI 写作助手，边写边续',
    desc: 'Tiptap 所见即所得编辑器内置 AI 助手：SSE 流式打字机实时上屏，多供应商一键热切换，截断自动接续、衔接去重、心跳保活——长文生成也稳。API Key 全程加密存储，绝不经过前端。',
    chips: ['SSE 流式', 'Tiptap', '多供应商', '截断自动接续', 'DeepSeek / 智谱'],
  },
  {
    key: 'security',
    icon: Lock,
    title: '安全设计，层层设防',
    desc: 'JWT + Redis 单会话白名单，新登录自动顶号；登录限流与锁定防暴力破解；RBAC 角色隔离管理面；敏感密钥 AES-GCM 加密落库；CSP 安全响应头层层收紧。',
    chips: ['JWT 单会话', '登录限流', 'AES-GCM', 'RBAC', 'CSP'],
  },
  {
    key: 'delivery',
    icon: Promotion,
    title: '推送即上线，失败即回滚',
    desc: 'git push 触发 GitHub Actions：前端构建、后端打包、rsync 上线一气呵成；容器健康检查失败自动回滚上一版本——部署这件事，不需要人守着。',
    chips: ['GitHub Actions', 'Docker Compose', '健康检查', '自动回滚'],
  },
  {
    key: 'experience',
    icon: Brush,
    title: '体验细节，星海氛围',
    desc: '星海 / 林间双主题一键切换，毛玻璃卡片与骨架屏，低清渐进式图片加载，打字机与流星氛围动画，以及 Sitemap + JSON-LD 结构化数据的 SEO 内功。',
    chips: ['双主题', '毛玻璃', '骨架屏', 'LQIP', 'SEO'],
  },
]

const active = ref(0)
const current = computed(() => FEATURES[active.value] ?? FEATURES[0]!)

function select(i: number) {
  active.value = i
  restartTimer()
}

function goArchives() {
  router.push('/archives')
}

function goSearch() {
  router.push('/search')
}

// ---------- 自动轮播：进入视口才启动，悬停暂停，离开恢复 ----------
const sectionEl = ref<HTMLElement | null>(null)
const hovered = ref(false)
let autoTimer: number | undefined
let inView = false

function restartTimer() {
  if (autoTimer) window.clearInterval(autoTimer)
  autoTimer = window.setInterval(() => {
    if (inView && !hovered.value && !document.hidden) {
      active.value = (active.value + 1) % FEATURES.length
    }
  }, 6500)
}

let revealObserver: IntersectionObserver | null = null

onMounted(() => {
  restartTimer()
  // 滚入视口后再开始入场动画与轮播，滚动体验更从容
  revealObserver = new IntersectionObserver(
    (entries) => {
      inView = entries[0]?.isIntersecting ?? false
      if (inView) sectionEl.value?.classList.add('in-view')
    },
    { threshold: 0.2 },
  )
  if (sectionEl.value) revealObserver.observe(sectionEl.value)
})

onBeforeUnmount(() => {
  if (autoTimer) window.clearInterval(autoTimer)
  revealObserver?.disconnect()
})
</script>

<template>
  <section ref="sectionEl" class="showcase">
    <header class="sc-head">
      <span class="sc-badge">设计思路</span>
      <h2 class="sc-title">一切皆模块，运行有迹可循</h2>
      <p class="sc-sub">
        这个博客不只是一个写字的地方——它本身就是一个完整的全栈工程样本。
        点击左侧模块，看看每一层是怎么搭起来的。
      </p>
    </header>

    <div class="sc-layout" @mouseenter="hovered = true" @mouseleave="hovered = false">
      <!-- 左列：能力清单 -->
      <div class="sc-list" role="tablist" aria-label="项目能力模块">
        <button
          v-for="(f, i) in FEATURES"
          :key="f.key"
          class="sc-item"
          :class="{ active: i === active }"
          role="tab"
          :aria-selected="i === active"
          type="button"
          @click="select(i)"
        >
          <el-icon class="sc-item-icon"><component :is="f.icon" /></el-icon>
          <span class="sc-item-title">{{ f.title }}</span>
          <ArrowRight class="sc-item-arrow" />
        </button>
      </div>

      <!-- 右列：能力描述 + 模拟界面 -->
      <div class="sc-panel">
        <Transition name="sc-fade" mode="out-in">
          <div :key="active" class="sc-visual-wrap">
            <p class="sc-desc">{{ current.desc }}</p>
            <div class="sc-chips">
              <span v-for="c in current.chips" :key="c" class="sc-chip">{{ c }}</span>
            </div>

            <!-- ===== 模拟界面 1：分层架构 ===== -->
            <div v-if="current.key === 'arch'" class="mock mock-arch">
              <div class="arch-layer top">
                <span class="arch-chip">浏览器 · Vue 3 + Vite + TS</span>
                <i class="arch-arrow">↓</i>
                <span class="arch-chip mid">Nginx · 静态托管 / 反向代理</span>
                <i class="arch-arrow">↓</i>
                <span class="arch-chip core">Spring Boot 3 · REST API / SSE</span>
                <i class="arch-arrow">↓</i>
                <div class="arch-store">
                  <span class="arch-chip store">MySQL</span>
                  <span class="arch-chip store">Redis</span>
                  <span class="arch-chip store">阿里云 OSS</span>
                </div>
              </div>
              <div class="mock-line">$ git push origin main → Actions 构建 → 自动部署</div>
            </div>

            <!-- ===== 模拟界面 2：AI 编辑器 ===== -->
            <div v-else-if="current.key === 'ai'" class="mock mock-ai">
              <div class="ai-bar">
                <span v-for="t in ['B', 'I', 'U', 'S']" :key="t" class="ai-bar-btn">{{ t }}</span>
                <span class="ai-bar-sep" />
                <span class="ai-bar-btn wide">正文</span>
                <span class="ai-bar-btn wide ai-on">✦ AI</span>
                <span class="ai-pill"><i class="dot" />AI 生成中…</span>
              </div>
              <div class="ai-body">
                <p>
                  紫云博客的编辑器内置 AI 助手：<span class="hl">润色选中文字</span>、
                  <span class="hl">从光标处续写</span>，生成内容以打字机效果
                  <span class="hl">实时上屏</span>——
                </p>
                <p class="typing">
                  长文被截断时自动接续<span class="ai-caret" />
                </p>
              </div>
            </div>

            <!-- ===== 模拟界面 3：安全清单 ===== -->
            <div v-else-if="current.key === 'security'" class="mock mock-sec">
              <div v-for="(s, i) in ['JWT + Redis 单会话白名单，顶号即踢', '登录限流：15 分钟 5 次失败自动锁定', 'API Key AES-GCM 加密落库，接口只回显掩码', 'CSP / X-Frame-Options 等安全响应头']" :key="i" class="sec-row">
                <span class="sec-check">✓</span>
                <span class="sec-text">{{ s }}</span>
                <span class="sec-tag">已启用</span>
              </div>
            </div>

            <!-- ===== 模拟界面 4：交付流水线 ===== -->
            <div v-else-if="current.key === 'delivery'" class="mock mock-pipe">
              <div v-for="(step, i) in ['git push', 'Actions 构建', 'Docker 打包', 'rsync 上线', '健康检查 · 失败自动回滚']" :key="step" class="pipe-row" :style="{ animationDelay: `${i * 0.35}s` }">
                <span class="pipe-dot" />
                <span class="pipe-name">{{ step }}</span>
                <span v-if="i < 4" class="pipe-link" />
              </div>
              <div class="mock-line">运行 3m42s · 全部通过 · 已部署至 ziyun.fun</div>
            </div>

            <!-- ===== 模拟界面 5：主题与体验 ===== -->
            <div v-else class="mock mock-exp">
              <div class="exp-theme">
                <span class="theme-dot dark" />星际拓荒
                <span class="theme-dot light" />自然绿意
              </div>
              <div class="exp-skeleton">
                <span class="sk sk-cover" />
                <span class="sk sk-line w70" />
                <span class="sk sk-line w50" />
              </div>
              <div class="mock-line">骨架屏 · LQIP 渐进图片 · 打字机与流星氛围</div>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- 栏目尾：文章入口（首页不再直出文章流） -->
    <div class="sc-foot">
      <button class="sc-foot-btn" type="button" @click="goArchives">
        浏览全部文章 <ArrowRight />
      </button>
      <button class="sc-foot-btn ghost" type="button" @click="goSearch">
        <el-icon><Search /></el-icon> 搜索文章
      </button>
    </div>
  </section>
</template>

<style scoped>
/* 入场：滚入视口后整段淡入上浮 */
.showcase {
  opacity: 0;
  transform: translateY(26px);
  transition: opacity 0.7s ease, transform 0.7s ease;
}
.showcase.in-view {
  opacity: 1;
  transform: none;
}

/* 头部 */
.sc-head {
  margin-bottom: 30px;
}
.sc-badge {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 14px;
}
.sc-title {
  margin: 0 0 12px;
  font-size: clamp(24px, 3.4vw, 34px);
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: 0.5px;
}
.sc-sub {
  margin: 0;
  max-width: 640px;
  color: var(--text-secondary);
  font-size: 14.5px;
  line-height: 1.9;
}

/* 布局：左清单 + 右面板 */
.sc-layout {
  display: grid;
  grid-template-columns: minmax(280px, 380px) minmax(0, 1fr);
  gap: 26px;
  align-items: start;
}
@media (max-width: 900px) {
  .sc-layout {
    grid-template-columns: 1fr;
  }
}

/* 左列清单 */
.sc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: sticky;
  top: 84px;
}
@media (max-width: 900px) {
  .sc-list {
    position: static;
  }
}
.sc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 13px 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 14.5px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.25s ease, color 0.25s ease, transform 0.25s ease, background-color 0.25s ease;
}
.sc-item:hover {
  color: var(--text-main);
  border-color: color-mix(in srgb, var(--color-primary) 45%, transparent);
  transform: translateX(4px);
}
.sc-item.active {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, var(--bg-card));
  font-weight: 700;
}
.sc-item-icon {
  font-size: 17px;
}
.sc-item-title {
  flex: 1;
}
.sc-item-arrow {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  opacity: 0;
  transform: translateX(-6px);
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.sc-item.active .sc-item-arrow,
.sc-item:hover .sc-item-arrow {
  opacity: 1;
  transform: none;
}

/* 右面板 */
.sc-panel {
  min-height: 380px;
}
.sc-desc {
  margin: 0 0 14px;
  font-size: 15px;
  line-height: 1.95;
  color: var(--text-main);
}
.sc-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.sc-chip {
  padding: 3px 12px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
}

/* 面板切换过渡：淡入上浮 */
.sc-fade-enter-active,
.sc-fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.sc-fade-enter-from {
  opacity: 0;
  transform: translateY(14px);
}
.sc-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ---------- 模拟界面通用（深色面板质感，双主题取变量） ---------- */
.mock {
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-card);
  padding: 18px;
  box-shadow: var(--shadow-card);
}
.mock-line {
  margin-top: 14px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--bg-page);
  color: var(--text-muted);
  font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
  font-size: 12px;
}

/* 架构分层图 */
.arch-layer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.arch-chip {
  width: 100%;
  text-align: center;
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--bg-page);
  color: var(--text-main);
  font-size: 13px;
}
.arch-chip.core {
  border-color: color-mix(in srgb, var(--color-primary) 55%, transparent);
  color: var(--color-primary);
  font-weight: 600;
}
.arch-chip.store {
  width: auto;
  flex: 1;
}
.arch-arrow {
  color: var(--text-muted);
  font-style: normal;
  font-size: 12px;
}
.arch-store {
  display: flex;
  gap: 8px;
  width: 100%;
}

/* AI 编辑器模拟 */
.ai-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-page);
  flex-wrap: wrap;
}
.ai-bar-btn {
  min-width: 26px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  border: 1px solid transparent;
}
.ai-bar-btn.wide {
  padding: 0 8px;
}
.ai-bar-btn.ai-on {
  color: var(--color-primary);
  border-color: color-mix(in srgb, var(--color-primary) 45%, transparent);
  font-weight: 700;
}
.ai-bar-sep {
  width: 1px;
  height: 16px;
  background: var(--border-color);
  margin: 0 4px;
}
.ai-pill {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 45%, transparent);
  background: color-mix(in srgb, var(--color-primary) 12%, var(--bg-card));
  color: var(--text-main);
  font-size: 11px;
}
.ai-pill .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: ai-dot-blink 1.1s ease-in-out infinite;
}
@keyframes ai-dot-blink {
  50% {
    opacity: 0.25;
  }
}
.ai-body {
  padding: 14px 6px 4px;
  color: var(--text-main);
  font-size: 14px;
  line-height: 2;
}
.ai-body p {
  margin: 0 0 8px;
}
.hl {
  color: var(--color-primary);
  font-weight: 600;
}
.typing {
  color: var(--text-secondary);
}
.ai-caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -0.15em;
  background: var(--color-primary);
  animation: ai-caret-blink 0.9s steps(1) infinite;
}
@keyframes ai-caret-blink {
  50% {
    opacity: 0;
  }
}

/* 安全清单 */
.sec-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 10px;
  background: var(--bg-page);
  margin-bottom: 8px;
}
.sec-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-primary) 18%, transparent);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.sec-text {
  flex: 1;
  color: var(--text-main);
  font-size: 13.5px;
}
.sec-tag {
  font-size: 11px;
  color: var(--color-primary);
  border: 1px solid color-mix(in srgb, var(--color-primary) 40%, transparent);
  padding: 1px 8px;
  border-radius: 999px;
}

/* 交付流水线 */
.pipe-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 4px;
  opacity: 0;
  animation: pipe-in 0.5s ease forwards;
}
@keyframes pipe-in {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.pipe-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-primary) 15%, transparent);
}
.pipe-name {
  color: var(--text-main);
  font-size: 13.5px;
  font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
}
.pipe-link {
  flex: 1;
  height: 1px;
  background: var(--border-color);
  margin-left: 6px;
}

/* 体验：主题色板 + 骨架 */
.exp-theme {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 12px;
}
.theme-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid var(--bg-card);
  box-shadow: 0 0 0 1px var(--border-color);
  margin-right: 2px;
}
.theme-dot.dark {
  background: linear-gradient(135deg, #0b1220, #7c6cf0);
}
.theme-dot.light {
  background: linear-gradient(135deg, #f4f1ea, #2f7d5a);
}
.exp-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-radius: 10px;
  background: var(--bg-page);
}
.sk {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--skeleton-base) 25%, var(--skeleton-shine) 50%, var(--skeleton-base) 75%);
  background-size: 200% 100%;
  animation: sk-slide 1.4s ease infinite;
}
@keyframes sk-slide {
  to {
    background-position: -200% 0;
  }
}
.sk-cover {
  height: 56px;
  border-radius: 8px;
}
.sk-line.w70 {
  width: 70%;
}
.sk-line.w50 {
  width: 50%;
}

/* 栏目尾按钮 */
.sc-foot {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 34px;
}
.sc-foot-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  padding: 10px 22px;
  border-radius: 999px;
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.sc-foot-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-hover);
}
.sc-foot-btn.ghost {
  background: transparent;
  color: var(--color-primary);
}
.sc-foot-btn.ghost:hover {
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
}

/* 减少动态偏好 */
@media (prefers-reduced-motion: reduce) {
  .showcase {
    opacity: 1;
    transform: none;
    transition: none;
  }
  .pipe-row {
    animation: none;
    opacity: 1;
  }
  .sk {
    animation: none;
  }
}
</style>
