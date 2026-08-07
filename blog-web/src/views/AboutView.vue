<script setup lang="ts">
import GlassCard from '@/components/GlassCard.vue'
import { useTheme } from '@/utils/theme'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

const { theme } = useTheme()

/** 关于页内容：Markdown 维护，改动只动这里 */
const aboutContent = `# 关于本站

**紫云博客** —— 一个前后端分离的个人博客，用于记录技术、思考与自然。

## 技术栈

- **后端**：Spring Boot 3 + MyBatis-Plus + MySQL + Redis + 阿里云 OSS
- **前端**：Vue 3 + Vite + TypeScript + Element Plus + md-editor-v3
- **部署**：Nginx 托管静态资源并反向代理 /api

## 设计理念

界面取意《星际拓荒》的静谧星海与大刘笔下的冷峻宇宙，
辅以自然绿意作为亮色主题 —— 仰望星空，也脚踏实地。

## 联系我

- GitHub / 邮箱等联系方式待补充
`
</script>

<template>
  <div class="about-view">
    <GlassCard padded="lg">
      <MdPreview
        editor-id="about-preview"
        :model-value="aboutContent"
        :theme="theme"
        preview-theme="default"
      />
    </GlassCard>
  </div>
</template>

<style scoped>
.about-view {
  max-width: 760px;
  margin: 0 auto;
}

/* md-editor-v3 在暗色主题下的文字/背景适配 */
:deep(.md-editor-preview-wrapper) {
  background: transparent;
}

:deep(.md-editor-preview) {
  color: var(--text-main);
  background: transparent;
  /* 文本块与卡片背景边界之间留出呼吸空间，避免内容贴边 */
  padding: 24px 28px;
  line-height: 1.9;
  font-size: 15px;
}

:deep(.md-editor-preview h1),
:deep(.md-editor-preview h2),
:deep(.md-editor-preview h3),
:deep(.md-editor-preview p),
:deep(.md-editor-preview li) {
  color: var(--text-main);
}

:deep(.md-editor-preview h1) {
  font-size: 26px;
  margin-top: 4px;
  padding-bottom: 14px;
}

:deep(.md-editor-preview h2) {
  font-size: 20px;
  margin-top: 32px;
  padding-bottom: 10px;
}

:deep(.md-editor-preview p) {
  margin: 12px 0;
}

:deep(.md-editor-preview a) {
  color: var(--color-primary);
}

:deep(.md-editor-preview code) {
  background: var(--bg-page);
  color: var(--color-accent);
}

:deep(.md-editor-preview pre) {
  background: var(--bg-page);
  border: 1px solid var(--border-color);
}

:deep(.md-editor-preview blockquote) {
  border-left-color: var(--color-primary);
  background: var(--bg-page);
  color: var(--text-secondary);
}
</style>
