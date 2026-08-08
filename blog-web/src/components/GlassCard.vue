<script setup lang="ts">
/**
 * 毛玻璃卡片：内容承载容器（星际拓荒 HUD 风格）。
 * backdrop-filter 模糊背景 + 半透明底色 + 微光边框，
 * 让文字在动态背景上保持可读性。
 */
defineProps<{
  /** 内边距档位：卡片用于文章详情用大 padding，列表卡片用小 */
  padded?: 'sm' | 'md' | 'lg'
  /** 禁用 hover 效果：长文阅读容器（如文章详情）hover 微动会干扰阅读 */
  noHover?: boolean
}>()
</script>

<template>
  <div class="glass-card" :class="[`pad-${padded ?? 'md'}`, { 'no-hover': noHover }]">
    <slot />
  </div>
</template>

<style scoped>
.glass-card {
  background: var(--bg-card);
  /* 毛玻璃：模糊卡片背后的星空/渐变，文字浮在柔焦背景上 */
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border-color);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  /* 轻微入场浮起 + hover 微光（科幻界面质感） */
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease,
    border-color 0.3s ease,
    background-color 0.4s ease;
}

.glass-card:not(.no-hover):hover {
  border-color: var(--color-primary);
  box-shadow: 0 12px 40px rgba(124, 108, 240, 0.18);
  transform: translateY(-2px);
}

html:not(.dark) .glass-card:not(.no-hover):hover {
  box-shadow: 0 12px 40px rgba(47, 125, 90, 0.14);
}

.pad-sm {
  padding: 14px 18px;
}
.pad-md {
  padding: 22px 26px;
}
.pad-lg {
  padding: 34px 42px;
}

/* 移动端收窄内边距 */
@media (max-width: 768px) {
  .pad-md {
    padding: 16px 18px;
  }
  .pad-lg {
    padding: 22px 20px;
  }
}
</style>
