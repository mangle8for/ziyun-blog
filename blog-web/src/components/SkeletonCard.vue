<script setup lang="ts">
/**
 * 文章卡片骨架屏（懒加载类型 A：数据列表）。
 * 首页文章列表首次加载时占位，视觉与 GlassCard 对齐
 * （圆角/边框/内边距），加载完成后无缝替换，无布局跳变。
 * 内部微光块共用全局 .shimmer（主题变量驱动）。
 */
withDefaults(defineProps<{ index?: number }>(), { index: 0 })
</script>

<template>
  <div class="skeleton-card" :style="{ animationDelay: `${index * 60}ms` }">
    <div class="sk-cover shimmer"></div>
    <div class="sk-title shimmer"></div>
    <div class="sk-line shimmer"></div>
    <div class="sk-line short shimmer"></div>
    <div class="sk-meta shimmer"></div>
  </div>
</template>

<style scoped>
.skeleton-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 22px 26px;
  animation: sk-in 0.4s ease backwards;
}

@keyframes sk-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 封面占位：与真实封面同比例，保证布局稳定 */
.sk-cover {
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  margin-bottom: 16px;
}

.sk-title {
  height: 20px;
  width: 85%;
  border-radius: 6px;
  margin-bottom: 12px;
}

.sk-line {
  height: 14px;
  width: 100%;
  border-radius: 5px;
  margin-bottom: 8px;
}
.sk-line.short {
  width: 60%;
  margin-bottom: 16px;
}

.sk-meta {
  height: 12px;
  width: 40%;
  border-radius: 5px;
}
</style>
