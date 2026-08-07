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
</style>
