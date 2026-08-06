import { post } from '@/utils/request'
import type { UploadResult } from '@/types'

/** 通用文件上传（multipart，字段名 file） */
export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<UploadResult>('/api/v1/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 上传图片（md-editor-v3 的 uploadImage 回调用，直接返回 URL） */
export async function uploadImage(file: File): Promise<string> {
  const data = await uploadFile(file)
  return data.url
}
