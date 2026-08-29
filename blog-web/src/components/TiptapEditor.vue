<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { StarterKit } from '@tiptap/starter-kit'
import { Image } from '@tiptap/extension-image'
import { Color, FontSize, TextStyle } from '@tiptap/extension-text-style'
import { Highlight } from '@tiptap/extension-highlight'
import { TableKit } from '@tiptap/extension-table'
import { CodeBlockLowlight } from '@tiptap/extension-code-block-lowlight'
import { Placeholder } from '@tiptap/extensions'
import { common, createLowlight } from 'lowlight'
import { ArrowDown, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { streamAiChat } from '@/api/ai'
import { uploadFile } from '@/api/file'

/**
 * 所见即所得富文本编辑器（Tiptap v3）。
 *
 * - v-model 绑定文章正文 HTML（Tiptap getHTML 产物，详情页直接 v-html 渲染）；
 * - 工具栏覆盖：撤销重做 / 标题层级 / 加粗斜体下划线删除线 / 高亮 / 文字颜色 /
 *   字号 / 列表 / 引用 / 分隔线 / 行内代码 / 代码块 / 链接 / 图片 / 表格；
 * - 图片三种入口统一走后端上传接口拿 OSS 外链：工具栏选择、Ctrl+V 粘贴、拖入；
 * - 明暗主题跟随全站 html.dark 变量，无需额外配置。
 */

const props = defineProps<{
  /** 编辑区空态占位文案 */
  placeholder?: string
}>()

/** 正文 HTML（空文档归一为空串，保证父级「正文不能为空」校验语义正确） */
const content = defineModel<string>({ default: '' })

const lowlight = createLowlight(common)

const editor = useEditor({
  content: content.value,
  extensions: [
    // 行内代码块交给 CodeBlockLowlight（带语法高亮），关闭 StarterKit 内置裸代码块
    StarterKit.configure({
      codeBlock: false,
      link: { openOnClick: false, autolink: true },
    }),
    CodeBlockLowlight.configure({ lowlight, defaultLanguage: 'plaintext' }),
    Image.configure({ allowBase64: false }),
    TextStyle,
    Color,
    FontSize,
    Highlight.configure({ multicolor: true }),
    TableKit.configure({ table: { resizable: false } }),
    Placeholder.configure({ placeholder: props.placeholder ?? '开始写作…' }),
  ],
  onUpdate: ({ editor }) => {
    content.value = editor.isEmpty ? '' : editor.getHTML()
  },
  editorProps: {
    handlePaste: (_view, event) => {
      const files = takeImageFiles(event.clipboardData?.files)
      if (!files.length) return false
      void insertImages(files)
      return true
    },
    handleDrop: (_view, event, _slice, moved) => {
      if (moved) return false
      const files = takeImageFiles(event.dataTransfer?.files)
      if (!files.length) return false
      void insertImages(files)
      return true
    },
  },
})

/** 编辑器内容/选区变化后由 onTransaction 刷新的工具栏状态 */
const state = reactive({
  h1: false,
  h2: false,
  h3: false,
  h4: false,
  bold: false,
  italic: false,
  underline: false,
  strike: false,
  code: false,
  codeBlock: false,
  blockquote: false,
  bulletList: false,
  orderedList: false,
  link: false,
  table: false,
  canUndo: false,
  canRedo: false,
  color: '',
  size: '',
  highlight: '',
  hasSelection: false,
})

function refreshState() {
  const e = editor.value
  if (!e) return
  state.h1 = e.isActive('heading', { level: 1 })
  state.h2 = e.isActive('heading', { level: 2 })
  state.h3 = e.isActive('heading', { level: 3 })
  state.h4 = e.isActive('heading', { level: 4 })
  state.bold = e.isActive('bold')
  state.italic = e.isActive('italic')
  state.underline = e.isActive('underline')
  state.strike = e.isActive('strike')
  state.code = e.isActive('code')
  state.codeBlock = e.isActive('codeBlock')
  state.blockquote = e.isActive('blockquote')
  state.bulletList = e.isActive('bulletList')
  state.orderedList = e.isActive('orderedList')
  state.link = e.isActive('link')
  state.table = e.isActive('table')
  state.hasSelection = !e.state.selection.empty
  state.canUndo = e.can().undo()
  state.canRedo = e.can().redo()
  state.color = e.getAttributes('textStyle').color ?? ''
  state.size = e.getAttributes('textStyle').fontSize ?? ''
  state.highlight = e.getAttributes('highlight').color ?? ''
}

watch(
  editor,
  (e, old) => {
    old?.off('transaction', refreshState)
    e?.on('transaction', refreshState)
    refreshState()
  },
  { immediate: true },
)

/** 外部（编辑模式回填）改内容时同步进编辑器，避免光标跳动的回环写入 */
watch(content, (v) => {
  const e = editor.value
  if (e && v !== e.getHTML()) {
    e.commands.setContent(v, { emitUpdate: false })
  }
})

// ---------- 工具栏命令 ----------
const headingLabel = computed(() =>
  state.h1 ? 'H1' : state.h2 ? 'H2' : state.h3 ? 'H3' : state.h4 ? 'H4' : '正文',
)

/**
 * el-dropdown 的 command 载荷恒为字符串（"1"~"4"/"p"），
 * toggleHeading 的 level 要求数字，需显式转换。
 */
function setHeading(level: string) {
  const c = editor.value?.chain().focus()
  if (!c) return
  if (level === 'p') c.setParagraph().run()
  else c.toggleHeading({ level: Number(level) as 1 | 2 | 3 | 4 }).run()
}

function applyColor(color?: string) {
  const c = editor.value?.chain().focus()
  if (!c) return
  if (color) c.setColor(color).run()
  else c.unsetColor().run()
}

function applySize(size?: string) {
  const c = editor.value?.chain().focus()
  if (!c) return
  if (size) c.setFontSize(size).run()
  else c.unsetFontSize().run()
}

function applyHighlight(color?: string) {
  const c = editor.value?.chain().focus()
  if (!c) return
  if (color) c.toggleHighlight({ color }).run()
  else c.unsetHighlight().run()
}

/**
 * 插入/编辑/移除链接：选中文字后点击直接弹输入框；输入留空确认 = 移除链接；
 * 无选中文本时把链接本身作为文字插入。
 * 弹窗聚焦输入框会清掉编辑器的原生选区，故在打开前先快照选区坐标，
 * 确认后恢复再操作（模态期间文档不会变化，坐标恒有效）。
 */
function setLink() {
  const e = editor.value
  if (!e) return
  const prev = e.getAttributes('link').href as string | undefined
  const { from, to } = e.state.selection
  const hadSelection = !e.state.selection.empty
  ElMessageBox.prompt('留空确认则移除链接', prev ? '编辑链接' : '插入链接', {
    inputValue: prev || 'https://',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValidator: (v: string) =>
      !v.trim() || /^https?:\/\/\S+$/.test(v.trim()) || '请输入合法的 http(s) 链接（或留空移除）',
  })
    .then(({ value }) => {
      const href = (value ?? '').trim()
      const c = e.chain().focus().setTextSelection({ from, to })
      if (!href) {
        c.extendMarkRange('link').unsetLink().run()
        return
      }
      if (!hadSelection && !prev) {
        c.insertContent({
          type: 'text',
          text: href,
          marks: [{ type: 'link', attrs: { href } }],
        }).run()
      } else {
        c.extendMarkRange('link').setLink({ href }).run()
      }
    })
    .catch(() => {
      /* 用户取消 */
    })
}

/** 表格下拉统一命令分发（el-dropdown-item 走 command 事件，原生 click 不可靠） */
function onTableCommand(command: string) {
  const c = editor.value?.chain().focus()
  if (!c) return
  switch (command) {
    case 'insert':
      c.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
      break
    case 'rowAfter':
      c.addRowAfter().run()
      break
    case 'colAfter':
      c.addColumnAfter().run()
      break
    case 'rowDel':
      c.deleteRow().run()
      break
    case 'colDel':
      c.deleteColumn().run()
      break
    case 'tableDel':
      c.deleteTable().run()
      break
  }
}

// ---------- 图片上传 ----------
const uploading = ref(0)

/** 从剪贴板/拖拽事件里筛出图片文件 */
function takeImageFiles(list?: FileList | null): File[] {
  return Array.from(list ?? []).filter((f) => f.type.startsWith('image/'))
}

async function insertImages(files: File[]) {
  for (const file of files) {
    uploading.value++
    try {
      const url = await uploadFile(file)
      editor.value?.chain().focus().setImage({ src: url, alt: file.name }).run()
    } catch {
      // 上传失败提示由请求层统一弹出
    } finally {
      uploading.value--
    }
  }
}

const fileInput = ref<HTMLInputElement | null>(null)

function pickImages() {
  fileInput.value?.click()
}

function onFilesPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length) void insertImages(files)
  input.value = ''
}

// ---------- AI 助手（流式写入，见 api/ai.ts 的 SSE 协议） ----------

const aiRunning = ref(false)
let aiAbortController: AbortController | null = null

/** 流式插入的位置游标：pos 为下一次插入点；polish 首块前需先删掉选区 */
interface AiInsertCursor {
  pos: number
  deleteFrom?: number
  deleteTo?: number
  deleted: boolean
  carry: string
}

/**
 * 把一段增量文本写进编辑器（单事务，高频 flush 靠 history 的
 * newGroupDelay 聚合成一步撤销）。
 * 换行语义：\n → 段内硬换行；\n\n → 拆分新段落（列表项内同样成立）。
 * AI 偶尔输出的行内 Markdown 记号降级为纯文本（星号/反引号移除、链接取文字）。
 */
function insertAiChunk(cursor: AiInsertCursor, rawChunk: string, isFinal: boolean) {
  const e = editor.value
  if (!e) return
  let text = cursor.carry + rawChunk
  cursor.carry = ''
  // 尾部悬挂换行先扣下（最多 2 个）：区分「段内换行」还是「新段落」要等后续字符，
  // 否则 "\n\n" 会被拆成两次硬换行而不是一次段落拆分
  if (!isFinal) {
    const trailing = text.match(/\n{1,2}$/)?.[0] ?? ''
    if (trailing) {
      cursor.carry = trailing
      text = text.slice(0, -trailing.length)
    }
  }
  text = text
    .replace(/\[([^\]]*)\]\(([^)]*)\)/g, '$1')
    .replace(/\*\*|__|`/g, '')
  if (!text) return

  e.commands.command(({ tr, dispatch }) => {
    if (dispatch) {
      // 位置策略：cursor.pos 是「AI 区域写入锚点」，每步结构操作后用
      // tr.mapping 映射到新坐标（手算块边界开合位置在 split 场景必错）。
      // assoc=1 表示锚点落在插入/拆分内容的后侧。
      let target = cursor.pos
      if (cursor.deleteFrom !== undefined && !cursor.deleted) {
        tr.delete(cursor.deleteFrom, cursor.deleteTo!)
        target = tr.mapping.map(cursor.pos, 1)
        cursor.deleted = true
      }
      for (const part of text.split(/(\n\n|\n)/)) {
        if (part === "\n\n") {
          tr.split(target)
          target = tr.mapping.map(cursor.pos, 1)
        } else if (part === "\n") {
          const hardBreak = e.schema.nodes.hardBreak?.create()
          if (hardBreak) {
            tr.insert(target, hardBreak)
            target = tr.mapping.map(cursor.pos, 1)
          }
        } else if (part) {
          tr.insertText(part, target)
          target = tr.mapping.map(cursor.pos, 1)
        }
      }
      cursor.pos = tr.mapping.map(cursor.pos, 1)
      dispatch(tr)
    }
    return true
  })
}

/** 通用流式执行：节流 flush + 中断保留已生成内容 + 统一错误提示 */
async function runAiStream(
  payload: Parameters<typeof streamAiChat>[0],
  cursor: AiInsertCursor,
) {
  aiRunning.value = true
  aiAbortController = new AbortController()
  let buffer = ''
  let deltaCount = 0
  let timer: number | null = null
  const flush = (isFinal: boolean) => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
    if (buffer) {
      const chunk = buffer
      buffer = ''
      insertAiChunk(cursor, chunk, isFinal)
    }
  }
  try {
    await streamAiChat(
      payload,
      (chunk) => {
        deltaCount++

        buffer += chunk
        // ~60ms 合并一次写入，避免高频事务拖慢编辑器
        if (timer === null) {
          timer = window.setTimeout(() => flush(false), 60)
        }
      },
      aiAbortController.signal,
    )
    flush(true)
    // 流正常结束但一个字都没产出：多为思考模型把输出额度耗在了推理上
    if (deltaCount === 0) {
      ElMessage.warning('AI 未返回内容（推理可能耗尽了输出额度），请重试')
    }
  } catch (e) {
    flush(true)
    if (e instanceof DOMException && e.name === 'AbortError') {
      ElMessage.info('已取消，已生成内容保留（Ctrl+Z 可回退）')
    } else if (deltaCount > 0) {
      // 内容已生成但连接中断：按完成处理，不再惊扰
      ElMessage.info('连接中断，已生成内容已保留')
    } else {
      ElMessage.error(e instanceof Error ? e.message : 'AI 生成失败')
    }
  } finally {
    if (timer !== null) clearTimeout(timer)
    aiRunning.value = false
    aiAbortController = null
  }
}

function cancelAi() {
  aiAbortController?.abort()
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && aiRunning.value) cancelAi()
}
watch(aiRunning, (running) => {
  if (running) window.addEventListener('keydown', onGlobalKeydown)
  else window.removeEventListener('keydown', onGlobalKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  aiAbortController?.abort()
})

async function runAiPolish() {
  const e = editor.value
  if (!e || aiRunning.value) return
  const { from, to, empty } = e.state.selection
  if (empty) {
    ElMessage.warning('请先选中要润色的文字')
    return
  }
  const selected = e.state.doc.textBetween(from, to, '\n')
  await runAiStream({ task: 'polish', text: selected }, {
    pos: from,
    deleteFrom: from,
    deleteTo: to,
    deleted: false,
    carry: '',
  })
}

async function runAiContinue() {
  const e = editor.value
  if (!e || aiRunning.value) return
  if (e.isEmpty) {
    ElMessage.warning("正文为空，没有可续写的上下文，请先写一点内容")
    return
  }
  const pos = e.state.selection.to
  // 取光标前文做续写上下文（2000 字符足够模型接住语气）
  const before = e.state.doc.textBetween(Math.max(0, pos - 2000), pos, "\n")
  await runAiStream({ task: "continue", text: before }, { pos, deleted: true, carry: "" })
}

async function runAiCustom() {
  const e = editor.value
  if (!e || aiRunning.value) return
  const { from, to, empty } = e.state.selection
  if (empty) {
    ElMessage.warning('自定义指令作用于选中文本，请先选中')
    return
  }
  const selected = e.state.doc.textBetween(from, to, '\n')
  try {
    const { value } = await ElMessageBox.prompt(
      '例如：改写成更口语化的表达 / 压缩为一半篇幅 / 列出要点',
      'AI 自定义指令',
      {
        confirmButtonText: '生成',
        cancelButtonText: '取消',
        inputPattern: /\S/,
        inputErrorMessage: '请输入指令',
      },
    )
    await runAiStream(
      { task: 'custom', text: selected, instruction: value.trim() },
      { pos: from, deleteFrom: from, deleteTo: to, deleted: false, carry: '' },
    )
  } catch {
    // 用户取消输入框
  }
}

function onAiCommand(command: string) {
  if (command === 'polish') void runAiPolish()
  else if (command === 'continue') void runAiContinue()
  else if (command === 'custom') void runAiCustom()
}

/** 供父组件获取正文纯文本（AI 摘要/标题/标签的上下文来源） */
function getText(): string {
  return editor.value?.getText() ?? ''
}

defineExpose({ getText })

// ---------- 色板/字号预设 ----------
const TEXT_COLORS = [
  { label: '红', value: '#e5484d' },
  { label: '橙', value: '#f76b15' },
  { label: '黄', value: '#e2b93b' },
  { label: '绿', value: '#46a758' },
  { label: '青', value: '#12a594' },
  { label: '蓝', value: '#0090ff' },
  { label: '紫', value: '#8e4ec6' },
  { label: '灰', value: '#8a9199' },
]

const HIGHLIGHT_COLORS = [
  { label: '黄', value: '#fef08a' },
  { label: '绿', value: '#bbf7d0' },
  { label: '蓝', value: '#bfdbfe' },
  { label: '紫', value: '#e9d5ff' },
  { label: '红', value: '#fecaca' },
  { label: '橙', value: '#fed7aa' },
]

const FONT_SIZES = ['13px', '15px', '17px', '20px', '24px', '30px']
</script>

<template>
  <div class="zte-editor">
    <!-- 工具栏 -->
    <div class="zte-toolbar" role="toolbar" aria-label="文章格式工具栏" @mousedown.prevent>
      <div class="tb-group">
        <button
          class="tb-btn"
          title="撤销"
          :disabled="!state.canUndo"
          @click.prevent="editor?.chain().focus().undo().run()"
        >
          <svg viewBox="0 0 24 24"><path d="M3 7v6h6" /><path d="M21 17a9 9 0 0 0-15.4-6.4L3 13" /></svg>
        </button>
        <button
          class="tb-btn"
          title="重做"
          :disabled="!state.canRedo"
          @click.prevent="editor?.chain().focus().redo().run()"
        >
          <svg viewBox="0 0 24 24"><path d="M21 7v6h-6" /><path d="M3 17a9 9 0 0 1 15.4-6.4L21 13" /></svg>
        </button>
      </div>

      <span class="tb-divider" />

      <el-dropdown trigger="click" @command="setHeading">
        <button class="tb-btn tb-wide" title="标题层级" @click.prevent>
          {{ headingLabel }}
          <el-icon class="tb-caret"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="p" :class="{ 'is-on': !(state.h1 || state.h2 || state.h3 || state.h4) }">正文</el-dropdown-item>
            <el-dropdown-item command="1" :class="{ 'is-on': state.h1 }">标题 1</el-dropdown-item>
            <el-dropdown-item command="2" :class="{ 'is-on': state.h2 }">标题 2</el-dropdown-item>
            <el-dropdown-item command="3" :class="{ 'is-on': state.h3 }">标题 3</el-dropdown-item>
            <el-dropdown-item command="4" :class="{ 'is-on': state.h4 }">标题 4</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <div class="tb-group">
        <button class="tb-btn" :class="{ 'is-on': state.bold }" title="加粗" @click.prevent="editor?.chain().focus().toggleBold().run()">
          <svg viewBox="0 0 24 24"><path d="M7 4h7a4 4 0 0 1 0 8H7z" /><path d="M7 12h8a4 4 0 0 1 0 8H7z" /></svg>
        </button>
        <button class="tb-btn" :class="{ 'is-on': state.italic }" title="斜体" @click.prevent="editor?.chain().focus().toggleItalic().run()">
          <svg viewBox="0 0 24 24"><path d="M19 4h-9" /><path d="M14 20H5" /><path d="M15 4 9 20" /></svg>
        </button>
        <button class="tb-btn" :class="{ 'is-on': state.underline }" title="下划线" @click.prevent="editor?.chain().focus().toggleUnderline().run()">
          <svg viewBox="0 0 24 24"><path d="M6 3v7a6 6 0 0 0 12 0V3" /><path d="M4 21h16" /></svg>
        </button>
        <button class="tb-btn" :class="{ 'is-on': state.strike }" title="删除线" @click.prevent="editor?.chain().focus().toggleStrike().run()">
          <svg viewBox="0 0 24 24"><path d="M16 4H9a3 3 0 0 0-2.83 4" /><path d="M14 12a4 4 0 0 1 0 8H6" /><path d="M4 12h16" /></svg>
        </button>
      </div>

      <span class="tb-divider" />

      <!-- 高亮 -->
      <el-dropdown trigger="click" @command="applyHighlight">
        <button
          class="tb-btn"
          :class="{ 'is-on': !!state.highlight }"
          title="高亮"
          @click.prevent
        >
          <svg viewBox="0 0 24 24"><path d="m9 11-6 6v3h9l3-3" /><path d="m22 12-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4Z" /></svg>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="c in HIGHLIGHT_COLORS" :key="c.value" :command="c.value">
              <span class="tb-swatch" :style="{ background: c.value }" />
              {{ c.label }}
            </el-dropdown-item>
            <el-dropdown-item divided command="">
              <span class="tb-swatch tb-swatch-none" />
              清除高亮
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 文字颜色 -->
      <el-dropdown trigger="click" @command="applyColor">
        <button class="tb-btn tb-color" title="文字颜色" @click.prevent>
          <span class="tb-a">A</span>
          <span class="tb-color-bar" :style="{ background: state.color || 'var(--text-muted)' }" />
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="c in TEXT_COLORS" :key="c.value" :command="c.value">
              <span class="tb-swatch" :style="{ background: c.value }" />
              {{ c.label }}
            </el-dropdown-item>
            <el-dropdown-item divided command="">
              <span class="tb-swatch tb-swatch-none" />
              默认颜色
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 字号 -->
      <el-dropdown trigger="click" @command="applySize">
        <button class="tb-btn tb-wide" title="字号" @click.prevent>
          字号
          <el-icon class="tb-caret"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="s in FONT_SIZES" :key="s" :command="s" :class="{ 'is-on': state.size === s }">
              {{ s }}
            </el-dropdown-item>
            <el-dropdown-item divided command="" :class="{ 'is-on': !state.size }">默认字号</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <span class="tb-divider" />

      <div class="tb-group">
        <button class="tb-btn" :class="{ 'is-on': state.bulletList }" title="无序列表" @click.prevent="editor?.chain().focus().toggleBulletList().run()">
          <svg viewBox="0 0 24 24"><path d="M9 6h12M9 12h12M9 18h12" /><circle cx="4" cy="6" r="1.2" fill="currentColor" stroke="none" /><circle cx="4" cy="12" r="1.2" fill="currentColor" stroke="none" /><circle cx="4" cy="18" r="1.2" fill="currentColor" stroke="none" /></svg>
        </button>
        <button class="tb-btn" :class="{ 'is-on': state.orderedList }" title="有序列表" @click.prevent="editor?.chain().focus().toggleOrderedList().run()">
          <svg viewBox="0 0 24 24"><path d="M10 6h11M10 12h11M10 18h11" /><path d="M4.8 5.4 6.3 4.5V10" /><path d="M4.3 16.1c0-1.3 2.6-1.7 2.6-.2 0 1.1-2.6 1.6-2.6 3.1h2.9" /></svg>
        </button>
        <button class="tb-btn" :class="{ 'is-on': state.blockquote }" title="引用" @click.prevent="editor?.chain().focus().toggleBlockquote().run()">
          <svg viewBox="0 0 24 24"><path d="M10 11H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v7a3 3 0 0 1-3 3" /><path d="M19 11h-4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v7a3 3 0 0 1-3 3" /></svg>
        </button>
        <button class="tb-btn" title="分隔线" @click.prevent="editor?.chain().focus().setHorizontalRule().run()">
          <svg viewBox="0 0 24 24"><path d="M5 12h14" /></svg>
        </button>
      </div>

      <span class="tb-divider" />

      <div class="tb-group">
        <button class="tb-btn" :class="{ 'is-on': state.code }" title="行内代码" @click.prevent="editor?.chain().focus().toggleCode().run()">
          <svg viewBox="0 0 24 24"><path d="m16 18 6-6-6-6" /><path d="m8 6-6 6 6 6" /></svg>
        </button>
        <button class="tb-btn" :class="{ 'is-on': state.codeBlock }" title="代码块" @click.prevent="editor?.chain().focus().toggleCodeBlock().run()">
          <svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m10 10-2 2 2 2" /><path d="m14 10 2 2-2 2" /></svg>
        </button>
      </div>

      <span class="tb-divider" />

      <button class="tb-btn" :class="{ 'is-on': state.link }" title="链接（选中文字后点击）" @click="setLink">
        <svg viewBox="0 0 24 24"><path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7" /><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7" /></svg>
      </button>

      <button class="tb-btn" title="插入图片（也可直接粘贴/拖入）" @click="pickImages">
        <svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" /><path d="m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21" /></svg>
      </button>

      <el-dropdown trigger="click" @command="onTableCommand">
        <button class="tb-btn" :class="{ 'is-on': state.table }" title="表格" @click.prevent>
          <svg viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" /><path d="M3 9h18" /><path d="M3 15h18" /><path d="M12 3v18" /></svg>
          <el-icon class="tb-caret"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="insert" :disabled="state.table">插入表格（3×3）</el-dropdown-item>
            <el-dropdown-item command="rowAfter" :disabled="!state.table">下方插入行</el-dropdown-item>
            <el-dropdown-item command="colAfter" :disabled="!state.table">右侧插入列</el-dropdown-item>
            <el-dropdown-item command="rowDel" :disabled="!state.table">删除当前行</el-dropdown-item>
            <el-dropdown-item command="colDel" :disabled="!state.table">删除当前列</el-dropdown-item>
            <el-dropdown-item command="tableDel" divided :disabled="!state.table">删除表格</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- AI 助手 -->
      <el-dropdown trigger="click" :disabled="aiRunning" @command="onAiCommand">
        <button
          class="tb-btn tb-wide"
          :class="{ 'is-on': aiRunning }"
          title="AI 助手"
          @click.prevent
        >
          <svg viewBox="0 0 24 24"><path d="M12 3l1.9 5.4L19.5 10l-5.6 1.6L12 17l-1.9-5.4L4.5 10l5.6-1.6z" /><path d="M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9z" /></svg>
          AI
          <el-icon class="tb-caret"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="polish" :disabled="aiRunning || !state.hasSelection">
              润色选中文字
            </el-dropdown-item>
            <el-dropdown-item command="continue" :disabled="aiRunning">从光标处续写</el-dropdown-item>
            <el-dropdown-item command="custom" divided :disabled="aiRunning || !state.hasSelection">
              自定义指令…
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <button
        v-if="aiRunning"
        class="tb-uploading tb-cancel"
        type="button"
        title="取消 AI 生成"
        @click="cancelAi"
      >
        <el-icon class="is-loading"><Loading /></el-icon>
        AI 生成中… 点击或按 ESC 取消
      </button>
      <span v-else-if="uploading > 0" class="tb-uploading">
        <el-icon class="is-loading"><Loading /></el-icon>
        图片上传中…
      </span>
    </div>

    <!-- 编辑区 -->
    <EditorContent :editor="editor" class="zte-body" />

    <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFilesPicked" />
  </div>
</template>

<!-- 非 scoped：编辑区 DOM 由 Tiptap 动态生成，需以 .zte- 前缀圈定作用范围 -->
<style>
.zte-editor {
  background: var(--bg-card);
  backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

/* ---------- 工具栏 ---------- */
.zte-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-color);
}

.zte-toolbar .tb-group {
  display: flex;
  gap: 2px;
}

.zte-toolbar .tb-divider {
  width: 1px;
  height: 18px;
  margin: 0 6px;
  background: var(--border-color);
}

.zte-toolbar .tb-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-width: 30px;
  height: 30px;
  padding: 0 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.zte-toolbar .tb-btn:hover:not(:disabled) {
  background: var(--bg-page);
  color: var(--text-main);
}

.zte-toolbar .tb-btn.is-on {
  background: var(--bg-page);
  color: var(--color-primary);
  font-weight: 700;
}

.zte-toolbar .tb-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.zte-toolbar .tb-btn svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.zte-toolbar .tb-wide {
  font-weight: 600;
}

.zte-toolbar .tb-caret {
  font-size: 11px;
  margin-left: 2px;
}

/* 文字颜色按钮：A 字母 + 底部当前色条 */
.zte-toolbar .tb-color {
  flex-direction: column;
  gap: 1px;
  padding: 2px 6px 3px;
}

.zte-toolbar .tb-a {
  font-size: 13px;
  font-weight: 800;
  line-height: 1;
  font-family: Georgia, serif;
}

.zte-toolbar .tb-color-bar {
  width: 14px;
  height: 3px;
  border-radius: 2px;
}

.zte-toolbar .tb-swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 8px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  vertical-align: -2px;
}

.zte-toolbar .tb-swatch-none {
  background: transparent;
}

.zte-toolbar .tb-uploading {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  font-size: 12px;
  color: var(--color-primary);
}

.zte-toolbar button.tb-cancel {
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 0 10px;
  height: 24px;
}

/* ---------- 编辑区排版（所见即所得，与前台文章页观感一致） ---------- */
.zte-body .tiptap {
  min-height: 60vh;
  max-height: 72vh;
  overflow-y: auto;
  padding: 18px 22px 28px;
  outline: none;
  font-size: 15px;
  line-height: 1.85;
  color: var(--text-main);
  caret-color: var(--color-primary);
  word-break: break-word;
}

.zte-body .tiptap > * + * {
  margin-top: 0.8em;
}

.zte-body .tiptap p.is-editor-empty:first-child::before {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  color: var(--text-muted);
  pointer-events: none;
}

.zte-body .tiptap h1,
.zte-body .tiptap h2,
.zte-body .tiptap h3,
.zte-body .tiptap h4 {
  margin: 1.4em 0 0.5em;
  font-weight: 800;
  line-height: 1.4;
  color: var(--text-main);
}

.zte-body .tiptap h1 {
  font-size: 1.85em;
}

.zte-body .tiptap h2 {
  font-size: 1.5em;
}

.zte-body .tiptap h3 {
  font-size: 1.25em;
}

.zte-body .tiptap h4 {
  font-size: 1.1em;
}

.zte-body .tiptap ul,
.zte-body .tiptap ol {
  padding-left: 1.6em;
}

.zte-body .tiptap blockquote {
  margin: 0.8em 0;
  padding: 8px 16px;
  border-left: 3px solid var(--color-accent);
  border-radius: 0 8px 8px 0;
  background: var(--bg-page);
  color: var(--text-secondary);
}

.zte-body .tiptap code {
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--bg-page);
  border: 1px solid var(--border-color);
  font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
  font-size: 0.88em;
}

.zte-body .tiptap pre {
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--bg-page);
  border: 1px solid var(--border-color);
  overflow-x: auto;
  line-height: 1.7;
  font-size: 13.5px;
}

.zte-body .tiptap pre code {
  padding: 0;
  border: none;
  background: transparent;
  font-size: inherit;
}

.zte-body .tiptap img {
  max-width: 100%;
  border-radius: 10px;
}

.zte-body .tiptap img.ProseMirror-selectednode {
  outline: 2px solid var(--color-primary);
}

.zte-body .tiptap a {
  color: var(--color-primary);
  text-decoration: underline;
}

.zte-body .tiptap mark {
  padding: 0 3px;
  border-radius: 4px;
}

.zte-body .tiptap hr {
  border: none;
  border-top: 1px dashed var(--border-color);
  margin: 1.4em 0;
}

.zte-body .tiptap table {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
  overflow: hidden;
  margin: 0.8em 0;
}

.zte-body .tiptap table td,
.zte-body .tiptap table th {
  border: 1px solid var(--border-color);
  padding: 6px 12px;
  vertical-align: top;
  position: relative;
}

.zte-body .tiptap table th {
  background: var(--bg-page);
  font-weight: 700;
  text-align: left;
}

.zte-body .tiptap table .selectedCell::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--color-primary);
  opacity: 0.12;
  pointer-events: none;
}

/* 代码块语法高亮配色（前台文章页与编辑器共用，见 main.ts 引入的 hljs 主题） */
html.dark .zte-body .tiptap pre {
  background: rgba(2, 6, 23, 0.65);
}
</style>
