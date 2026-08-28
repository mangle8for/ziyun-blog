#!/usr/bin/env node
/**
 * 一次性迁移脚本：把存量文章正文从 Markdown 转成 HTML（Tiptap 编辑器格式）。
 *
 * 背景：博客编辑器从 md-editor-v3（Markdown）切换为 Tiptap（所见即所得，HTML 存储），
 * 前台详情页改为 v-html 直出。旧 Markdown 文章需转一次 HTML 才能正常渲染。
 *
 * 用法（在 blog-web 目录下）：
 *   预览模式（只打印转换计划，不写库）：
 *     node scripts/migrate-md-to-html.mjs --user=<账号> --pass=<密码>
 *   真正执行：
 *     node scripts/migrate-md-to-html.mjs --user=<账号> --pass=<密码> --execute
 *
 * 可选参数：
 *   --base=<URL>   后端地址，默认 http://localhost:8080（生产传 https://ziyun.fun）
 *   --page-size=N  每页拉取条数，默认 50
 *
 * 转换规则：
 *   - markdown-it（与 md-editor-v3 同源渲染器）html:true，与旧前台渲染观感一致；
 *   - 代码围栏输出 <pre><code class="language-x">，前台渲染时由 highlight.js 统一补高亮；
 *   - 已是 HTML 的文章（以块级标签开头）自动跳过，可安全重复执行；
 *   - mermaid/latex 等扩展语法不在转换范围（会保留为代码块/原文本）。
 */

import markdownit from 'markdown-it'
import taskLists from 'markdown-it-task-lists'

// ---------- 参数解析 ----------
const args = process.argv.slice(2)
function arg(name, fallback = undefined) {
  const hit = args.find((a) => a.startsWith(`--${name}=`))
  return hit ? hit.split('=').slice(1).join('=') : fallback
}
const BASE = (arg('base') || 'http://localhost:8080').replace(/\/$/, '')
const USER = arg('user')
const PASS = arg('pass')
const PAGE_SIZE = Number(arg('page-size') || 50)
const EXECUTE = args.includes('--execute')

if (!USER || !PASS) {
  console.error('缺少账号参数。用法：node scripts/migrate-md-to-html.mjs --user=<账号> --pass=<密码> [--execute] [--base=<URL>]')
  process.exit(1)
}

// ---------- 与旧前台同源的 Markdown 渲染器 ----------
const md = markdownit({ html: true, linkify: true, breaks: false })
md.use(taskLists)

/** 是否已是 HTML 内容（Tiptap/迁移产物以块级标签开头；Markdown 以文本或 # 等记号开头） */
function looksLikeHtml(content) {
  return /^\s*<(p|div|h[1-6]|ul|ol|blockquote|pre|figure|table|img|section|hr)\b/i.test(content)
}

/** 后端 Result<T> 解包 */
async function api(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, options)
  const body = await res.json().catch(() => ({}))
  if (body.code !== 200) {
    throw new Error(`${options.method || 'GET'} ${path} 失败: ${body.msg || res.status}`)
  }
  return body.data
}

async function main() {
  // 1. 登录拿 token
  const token = (
    await api('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USER, password: PASS }),
    })
  ).token
  const auth = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }

  // 2. 分页拉全量文章（含草稿）
  const all = []
  for (let page = 1; ; page++) {
    const result = await api(`/api/v1/articles/manage?page=${page}&size=${PAGE_SIZE}`, { headers: auth })
    all.push(...result.records)
    if (all.length >= Number(result.total) || result.records.length === 0) break
  }
  console.log(`共 ${all.length} 篇文章待检查\n`)

  // 3. 逐篇转换
  let converted = 0
  let skipped = 0
  for (const item of all) {
    const detail = await api(`/api/v1/articles/manage/${item.id}`, { headers: auth })
    const { title, content } = detail

    if (!content || !content.trim()) {
      console.log(`[跳过·空正文] ${title}`)
      skipped++
      continue
    }
    if (looksLikeHtml(content)) {
      console.log(`[跳过·已是HTML] ${title}`)
      skipped++
      continue
    }

    const html = md.render(content)
    console.log(
      `[转换] ${title}\n        markdown ${content.length} 字符 -> html ${html.length} 字符`,
    )
    converted++

    if (EXECUTE) {
      await api(`/api/v1/articles/${detail.id}`, {
        method: 'PUT',
        headers: auth,
        body: JSON.stringify({
          title: detail.title,
          summary: detail.summary || undefined,
          content: html,
          cover: detail.cover || undefined,
          categoryId: detail.categoryId || undefined,
          tagIds: (detail.tags ?? []).map((t) => t.id),
          status: detail.status,
        }),
      })
      console.log('        已写回')
    }
  }

  console.log(
    `\n完成：转换 ${converted} 篇，跳过 ${skipped} 篇${EXECUTE ? '（已写回）' : '（预览模式，加 --execute 真正执行）'}`,
  )
}

main().catch((err) => {
  console.error('\n迁移失败：', err.message)
  process.exit(1)
})
