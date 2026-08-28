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
