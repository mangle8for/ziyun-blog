<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 渐进式图片（懒加载类型 C：图片降分辨率小图预览 LQIP）。
 *
 * 三层结构，逐级「预演」最终画面：
 *  1. shimmer 骨架占位 —— 图片请求前先铺一个主题化微光底；
 *  2. 低分辨率模糊预览 —— 阿里云 OSS 图片自动追加 x-oss-process
 *     处理参数（resize 40px + blur），几十 KB 的缩略模糊图秒开，
 *     比纯骨架更接近真实画面；
 *  3. 原图 —— 加载完成后淡入覆盖，并可选 hover 微放大（首页卡片）。
 *
 * 本组件绝对铺满外层容器，尺寸由父级控制（aspect-ratio / 圆形头像），
 * 圆角通过 border-radius: inherit 继承父级。
 */
const props = withDefaults(
  defineProps<{
    src: string
    alt?: string
    /** 自定义低分辨率预览地址（默认按 OSS 地址自动生成） */
    lowSrc?: string
    /** hover 时原图轻微放大（首页文章卡片用） */
    hoverZoom?: boolean
    /** 懒加载：滚动进视口才请求原图（默认开，头像等首屏元素可关） */
    lazy?: boolean
  }>(),
  { alt: '', lowSrc: '', hoverZoom: false, lazy: true },
)

/** 原图加载完成 */
const loaded = ref(false)
/** 低清预览加载完成（淡入时机） */
const lowLoaded = ref(false)
/** 原图加载失败（保留骨架占位，避免破图） */
const failed = ref(false)
const imgEl = ref<HTMLImageElement | null>(null)

/** 阿里云 OSS 图片自动追加低分辨率处理参数；非 OSS 地址不做自动降级 */
const effectiveLowSrc = computed(() => {
  if (props.lowSrc) return props.lowSrc
  if (!/aliyuncs\.com/.test(props.src)) return ''
  const sep = props.src.includes('?') ? '&' : '?'
  return `${props.src}${sep}x-oss-process=image/resize,w_40,blur_r_12`
})

function onLoad() {
  loaded.value = true
}

function onError() {
  failed.value = true
}

watch(
  () => props.src,
  () => {
    loaded.value = false
    lowLoaded.value = false
    failed.value = false
  },
)

onMounted(() => {
  // 缓存图片不会再次触发 load 事件，complete + naturalWidth 兜底判定
  if (imgEl.value?.complete && imgEl.value.naturalWidth > 0) {
    loaded.value = true
  }
})

onBeforeUnmount(() => {
  imgEl.value?.removeEventListener('load', onLoad)
  imgEl.value?.removeEventListener('error', onError)
})
</script>

<template>
  <div class="progressive-img" :class="{ 'hover-zoom': hoverZoom }">
    <!-- 骨架占位：预览或原图到达后淡出 -->
    <span class="ph shimmer" :class="{ hidden: loaded || failed }"></span>

    <!-- 低分辨率模糊预览（LQIP） -->
    <img
      v-if="effectiveLowSrc && !failed"
      class="blur"
      :class="{ shown: lowLoaded }"
      :src="effectiveLowSrc"
      alt=""
      aria-hidden="true"
      @load="lowLoaded = true"
    />

    <!-- 原图：加载完成后淡入 -->
    <img
      v-show="!failed"
      ref="imgEl"
      class="real"
      :class="{ shown: loaded }"
      :src="src"
      :alt="alt"
      :loading="lazy ? 'lazy' : 'eager'"
      @load="onLoad"
      @error="onError"
    />
  </div>
</template>

<style scoped>
.progressive-img {
  position: absolute;
  inset: 0;
  overflow: hidden;
  border-radius: inherit;
}

/* 骨架占位 */
.ph {
  position: absolute;
  inset: 0;
  transition: opacity 0.4s ease;
}
.ph.hidden {
  opacity: 0;
}

/* 低清预览：模糊 + 微放大掩盖缩略图锯齿边缘 */
.blur {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: blur(14px);
  transform: scale(1.08);
  opacity: 0;
  transition: opacity 0.3s ease;
}
.blur.shown {
  opacity: 1;
}

/* 原图 */
.real {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition:
    opacity 0.5s ease,
    transform 0.4s ease;
}
.real.shown {
  opacity: 1;
}

/* hover 微放大（仅支持 hover 的设备生效） */
@media (hover: hover) {
  .progressive-img.hover-zoom:hover .real.shown {
    transform: scale(1.04);
  }
}

/* 减少动态偏好：直接呈现，不做淡入 */
@media (prefers-reduced-motion: reduce) {
  .blur,
  .real {
    transition: none;
  }
}
</style>
