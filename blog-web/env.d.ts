/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 基础路径：开发环境为 /api（Vite 代理），生产环境为网关地址 */
  readonly VITE_API_BASE_URL: string
}
