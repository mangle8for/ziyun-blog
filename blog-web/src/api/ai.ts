import { del, get, post, put } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import type { AiChatPayload, AiProvider, AiProviderPayload } from '@/types'

/**
 * AI 供应商管理接口（管理端 CRUD 走统一 axios 封装）。
 */
export function getAiProviders() {
  return get<AiProvider[]>('/api/v1/ai/providers')
}

export function createAiProvider(data: AiProviderPayload) {
  return post<string>('/api/v1/ai/providers', data)
}

export function updateAiProvider(id: string, data: AiProviderPayload) {
  return put<void>(`/api/v1/ai/providers/${id}`, data)
}

export function deleteAiProvider(id: string) {
  return del<void>(`/api/v1/ai/providers/${id}`)
}

export function setDefaultAiProvider(id: string) {
  return put<void>(`/api/v1/ai/providers/${id}/default`)
}

/** 连通性测试：成功返回「连接成功 · 耗时 · 模型」，失败由请求层弹错误 */
export function testAiProvider(id: string) {
  return post<string>(`/api/v1/ai/providers/${id}/test`)
}

/**
 * AI 写作任务（SSE 流式）。
 *
 * <p>为什么不用 request.ts 的 axios：EventSource 无法带 Authorization 头、
 * axios 的 15s 超时与 Result 解包拦截器都会干扰流式读取 ——
 * 故用原生 fetch + ReadableStream，token 手动注入。</p>
 *
 * @param payload 任务与上下文
 * @param onDelta 每收到一段增量文本的回调（打字机效果）
 * @param signal  取消信号（AbortController.signal），中断后已生成内容保留
 * @throws Error  前端解析到的业务错误消息（上游失败/未配置供应商等）
 */
export async function streamAiChat(
  payload: AiChatPayload,
  onDelta: (chunk: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUserStore()
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  const response = await fetch(`${baseURL}/api/v1/ai/chat/stream`, {
    method: 'POST',
    signal,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userStore.token}`,
    },
    body: JSON.stringify(payload),
  })

  // 进入流式前的错误（参数/配置问题）后端仍返回 Result JSON
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    let message = `请求失败（HTTP ${response.status}）`
    try {
      const body = await response.json()
      if (body?.msg) message = body.msg
    } catch {
      // 保留默认消息
    }
    throw new Error(message)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式读取')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  /** 处理一个完整的 SSE 事件块（event + data 行） */
  const handleBlock = (block: string) => {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5))
      }
    }
    if (!dataLines.length) return
    const data = dataLines.join('\n')
    if (event === 'delta') {
      // data 为 JSON {"t":"增量文本"}（转义换行，保证单行传输）
      try {
        const chunk = JSON.parse(data)?.t
        if (chunk) onDelta(chunk)
      } catch {
        // 单块解析失败直接丢弃，不影响后续块
      }
    } else if (event === 'error') {
      let message = 'AI 生成失败'
      try {
        message = JSON.parse(data)?.message || message
      } catch {
        // 保留默认消息
      }
      throw new Error(message)
    }
    // done：正常结束，外层循环读完自然返回
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sep: number
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      handleBlock(block)
    }
  }
}
