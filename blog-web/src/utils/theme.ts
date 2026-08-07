import { ref, watchEffect } from 'vue'

/** 主题持久化键名 */
const THEME_KEY = 'ziyun-blog-theme'

/** 主题类型：dark-星际拓荒（默认）/ light-自然绿意 */
export type ThemeMode = 'dark' | 'light'

/** 当前主题（模块级单例，跨组件共享同一份响应式状态） */
const theme = ref<ThemeMode>((localStorage.getItem(THEME_KEY) as ThemeMode) || 'dark')

/**
 * 主题切换 Composable。
 *
 * 设计说明（为什么是模块级 ref 而非 Pinia）：
 * 主题是全站单例状态，无需 Pinia 的模块隔离；模块级 ref 更轻，
 * watchEffect 统一同步 html.dark class 与 localStorage。
 * Element Plus 的暗色适配由官方 html.dark css-vars 方案完成，
 * 我们只需维护这一个 class。
 */
export function useTheme() {
  watchEffect(() => {
    const isDark = theme.value === 'dark'
    document.documentElement.classList.toggle('dark', isDark)
    localStorage.setItem(THEME_KEY, theme.value)
  })

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  return { theme, toggleTheme }
}
