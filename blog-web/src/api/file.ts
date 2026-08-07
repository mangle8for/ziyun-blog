import { post } from '@/utils/request'
import type { UploadResult } from '@/types'

/**
 * 通用文件上传（multipart，字段名 file）。
 * 后端直接返回可访问 URL 字符串。
 */
export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<UploadResult>('/api/v1/files', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** md-editor-v3 的图片上传回调适配：组件要求返回 URL 数组 */
export async function uploadImage(file: File): Promise<string> {
  return uploadFile(file)
}
