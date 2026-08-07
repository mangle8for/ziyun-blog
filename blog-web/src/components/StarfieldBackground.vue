<script setup lang="ts">
/**
 * 星空背景组件：多层 CSS 粒子 + 缓慢漂移动画。
 * 轻量方案（零 JS 粒子引擎）：radial-gradient 叠加出星点，
 * animation 缓慢移动制造「星海呼吸」感。
 * 仅暗色主题可见（亮色由 theme.css 的自然渐变承担背景）。
 */
</script>

<template>
  <div class="starfield" aria-hidden="true">
    <div class="stars stars-1"></div>
    <div class="stars stars-2"></div>
    <div class="stars stars-3"></div>
  </div>
</template>

<style scoped>
/* 设计说明（CSS 粒子原理）：
 * 每个 .stars 层用 box-shadow 一次生成上百个微小圆点（比 DOM 节点
 * 高效得多），多层叠加不同密度/大小制造纵深；再用 transform
 * 平移动画让星层缓慢漂移，形成「视差星空」。
 * 性能：纯 CSS transform，走 GPU 合成层，不触发回流。
 */
.starfield {
  position: fixed;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.6s ease;
}

/* 仅暗色主题显示星空层 */
html.dark .starfield {
  opacity: 1;
}

.stars {
  position: absolute;
  top: 0;
  left: 0;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: transparent;
  /* 星点通过 box-shadow 批量生成（坐标随机散布） */
  animation: drift 200s linear infinite;
}

/* 第一层：小而密集的背景星 */
.stars-1 {
  box-shadow:
    1440px 220px #fff, 312px 98px #cfd8ff, 890px 1402px #fff, 1204px 834px #aab6ff,
    210px 640px #fff, 1722px 310px #e6ecff, 588px 1560px #fff, 1330px 560px #cfd8ff,
    760px 90px #fff, 1010px 1180px #aab6ff, 420px 480px #fff, 1560px 720px #e6ecff,
    80px 1240px #fff, 1190px 160px #cfd8ff, 640px 930px #fff, 1490px 1380px #aab6ff,
    300px 1760px #e6ecff, 980px 420px #fff, 550px 200px #cfd8ff, 1810px 1120px #fff,
    230px 1120px #fff, 1270px 1580px #cfd8ff, 780px 720px #fff, 1640px 260px #aab6ff;
  animation-duration: 180s;
}

/* 第二层：中等亮度、不同节奏 */
.stars-2 {
  width: 3px;
  height: 3px;
  box-shadow:
    700px 1200px #fff, 1500px 480px #ffd9a8, 260px 820px #fff, 1120px 1500px #cfd8ff,
    1800px 720px #fff, 540px 320px #ffd9a8, 960px 1660px #fff, 1380px 940px #cfd8ff,
    380px 1440px #fff, 1680px 180px #ffd9a8, 820px 620px #fff, 1240px 1080px #cfd8ff,
    160px 240px #fff, 1020px 560px #ffd9a8, 480px 1780px #fff, 1900px 1300px #cfd8ff;
  animation-duration: 120s;
  animation-direction: reverse;
}

/* 第三层：大而亮的「主星」，带轻微闪烁 */
.stars-3 {
  width: 4px;
  height: 4px;
  box-shadow:
    420px 560px #fff, 1180px 240px #ffe9c8, 1720px 1480px #fff, 660px 1080px #cfe0ff,
    1480px 780px #fff, 240px 1600px #ffe9c8, 940px 360px #fff;
  animation: drift 90s linear infinite, twinkle 4s ease-in-out infinite;
}

/* 星层缓慢漂移（视差纵深） */
@keyframes drift {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(-600px);
  }
}

/* 主星闪烁（呼吸感） */
@keyframes twinkle {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

/* 尊重「减少动态」系统偏好（无障碍） */
@media (prefers-reduced-motion: reduce) {
  .stars {
    animation: none;
  }
}
</style>
