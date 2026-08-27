<script setup lang="ts">
/**
 * 文章行骨架屏（分类/标签页列表懒加载占位）。
 * 形状与列表行 GlassCard 对齐（左侧标题/摘要条 + 右侧日期条），
 * 加载完成后无缝替换，无布局跳变；微光块复用全局 .shimmer。
 */
withDefaults(defineProps<{ index?: number }>(), { index: 0 })
</script>

<template>
  <div class="row-skeleton" :style="{ animationDelay: `${index * 70}ms` }">
    <div class="lines">
      <div class="bar title shimmer"></div>
      <div class="bar sub shimmer"></div>
    </div>
    <div class="bar date shimmer"></div>
  </div>
</template>

<style scoped>
.row-skeleton {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 14px 18px;
  animation: rs-in 0.4s ease backwards;
}

@keyframes rs-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.lines {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.bar {
  border-radius: 5px;
}

.title {
  height: 15px;
  width: 55%;
}

.sub {
  height: 11px;
  width: 80%;
}

.date {
  height: 11px;
  width: 72px;
  flex-shrink: 0;
}

@media (prefers-reduced-motion: reduce) {
  .row-skeleton {
    animation: none;
  }
}
</style>
