<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import MkpTopNav from '../components/MkpTopNav.vue'
import '../assets/mkp/mkp.css'
import {
  getChatbotConversations,
  getChatbotMessages,
  deleteChatbotConversation,
  askChatbotStream
} from '../api'
import { CATEGORY, fmtPrice } from '../utils/constants'

/* ================================================================
   智能助手（原「养宠助手」）· 桌面端整页对话
   - 布局：顶部导航 + 左侧历史对话 + 右侧对话流（思考 / 工具检索 / 结果卡片）
   - 数据：会话与消息走 /api/chatbot/*，问答走 SSE 流式接口（ReAct）
   - 外观：面板为「液态玻璃」（见样式末尾 .is-glass 段；去掉根节点 is-glass 即还原实心白）
   ================================================================ */
const router = useRouter()
const DEMO_AV = '/mkp/a04.png'

const user = ref({
  nickname: localStorage.getItem('nickname') || '铲屎官',
  avatar: localStorage.getItem('avatar') || '',
  role: localStorage.getItem('role') || 'USER'
})
const userLine = computed(() => (user.value.role === 'ADMIN' ? '管理员 · 智能助手常驻' : 'MKP 铲屎官 · 智能助手常驻'))

/* 快捷提问：点击后填入输入框，可再编辑后发送 */
const quickAsks = [
  { icon: '🩺', label: '症状自查', q: '我家宠物最近状态不太对，帮我做个症状自查' },
  { icon: '🏠', label: '寄养定制', q: '我过几天要出差，帮我定制一个寄养方案' },
  { icon: '📍', label: '寻找附近寄养', q: '帮我在交易广场找找附近合适的寄养需求' }
]

const conversations = ref([])
const messages = ref([])
const activeId = ref(null)
const input = ref('')
const streaming = ref(false)
const sideOpen = ref(true)
const showQuick = ref(true)
const scrollRef = ref(null)
const inputRef = ref(null)

const activeTitle = computed(() => {
  const c = conversations.value.find((x) => x.id === activeId.value)
  return c?.title || '新对话'
})

function truncate (s, n = 48) {
  if (!s) return ''
  return s.length > n ? s.slice(0, n) + '…' : s
}

/* 会话时间：今日 HH:mm / 昨日 HH:mm / YYYY-MM-DD */
function fmtConvTime (t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  const sameDay = (a, b) =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  const now = new Date()
  if (sameDay(d, now)) return '今日 ' + p(d.getHours()) + ':' + p(d.getMinutes())
  const y = new Date(now)
  y.setDate(now.getDate() - 1)
  if (sameDay(d, y)) return '昨日 ' + p(d.getHours()) + ':' + p(d.getMinutes())
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function loadConversations () {
  try {
    conversations.value = (await getChatbotConversations()) || []
  } catch {
    /* interceptor 已提示 */
  }
}

function newConversation () {
  activeId.value = null
  messages.value = []
  nextTick(() => inputRef.value?.focus())
}

async function openConversation (c) {
  activeId.value = c.id
  streaming.value = false
  try {
    const list = (await getChatbotMessages(c.id)) || []
    messages.value = list.map((m) => ({
      role: m.role,
      content: m.content || '',
      thinking: '',
      tools: [],
      posts: parseJsonList(m.postsJson),
      postings: parseJsonList(m.postingsJson)
    }))
  } catch {
    messages.value = []
  }
  scrollBottom()
}

function parseJsonList (raw) {
  if (Array.isArray(raw)) return raw
  if (typeof raw !== 'string' || !raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

async function removeConversation (c) {
  if (!window.confirm('删除该对话？')) return
  try {
    await deleteChatbotConversation(c.id)
    if (activeId.value === c.id) newConversation()
    await loadConversations()
  } catch {
    /* interceptor 已提示 */
  }
}

function scrollBottom () {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

/* 逐字追加时自动跟随滚动 */
watch(
  () => {
    const last = messages.value[messages.value.length - 1]
    return last ? last.content.length + last.thinking.length : 0
  },
  () => scrollBottom()
)

function useQuick (q) {
  input.value = q.q
  nextTick(() => inputRef.value?.focus())
}

async function send () {
  const q = input.value.trim()
  if (!q || streaming.value) return
  input.value = ''

  messages.value.push({ role: 'user', content: q })

  const assistant = reactive({
    role: 'assistant',
    content: '',
    thinking: '',
    tools: [],
    posts: [],
    postings: []
  })
  messages.value.push(assistant)

  streaming.value = true
  scrollBottom()

  try {
    await askChatbotStream(q, activeId.value, {
      onThink: (p) => { assistant.thinking += p.content || '' },
      onToken: (p) => { assistant.content += p.content || '' },
      onTool: (p) => {
        if (p.step === 'action') {
          assistant.tools.push({ name: p.name, args: p.args, obs: '' })
        } else if (p.step === 'observation') {
          const last = assistant.tools[assistant.tools.length - 1]
          if (last) last.obs = p.content || ''
        }
      },
      onDone: (p) => {
        if (p.conversationId) activeId.value = p.conversationId
        assistant.posts = p.posts || []
        assistant.postings = p.postings || []
      },
      onError: (p) => {
        if (!assistant.content) assistant.content = p?.message || '出错了，请重试'
      }
    })
  } catch {
    if (!assistant.content) assistant.content = '请求失败，请重试'
  } finally {
    streaming.value = false
    scrollBottom()
    if (activeId.value) loadConversations()
  }
}

/* 结果卡片辅助 */
function firstImg (p) {
  const imgs = Array.isArray(p?.images) ? p.images : p?.images ? [p.images] : []
  return imgs[0] || ''
}
function postingBadge (pt) {
  return CATEGORY[pt.category] || '交易'
}
function toolText (t) {
  const obs = String(t.obs || '').replace(/\s+/g, ' ').trim()
  return obs ? `${t.name} · ${truncate(obs, 28)}` : `${t.name} · 检索中…`
}
function matchCount (m) {
  return (m.postings?.length || 0) + (m.posts?.length || 0)
}

onMounted(async () => {
  await loadConversations()
  const c = conversations.value[0]
  if (c) await openConversation(c)
  else nextTick(() => inputRef.value?.focus())
})
</script>

<template>
  <div class="mkp-assistant is-glass">
    <a class="mkp-skip" href="#a-main">跳到主内容</a>
    <span class="a-blob a-blob--1" aria-hidden="true"></span>
    <span class="a-blob a-blob--2" aria-hidden="true"></span>
    <span class="a-blob a-blob--3" aria-hidden="true"></span>
    <span class="a-blob a-blob--4" aria-hidden="true"></span>
    <span class="a-blob a-blob--5" aria-hidden="true"></span>
    <span class="a-blob a-blob--6" aria-hidden="true"></span>

    <!-- ===== 顶部导航（与「社交广场」统一：无搜索框 / 无发布按钮） ===== -->
    <MkpTopNav class="a-topbar" active="/assistant" :show-search="false" :show-action="false">
      <template #actions>
        <button
          class="a-icon-btn"
          type="button"
          :aria-expanded="sideOpen"
          aria-label="展开或收起历史对话"
          @click="sideOpen = !sideOpen"
        >
          <AppIcon name="menu" :size="18" />
        </button>
      </template>
    </MkpTopNav>

    <main id="a-main" class="a-main" :class="{ 'is-side-hidden': !sideOpen }">
      <!-- ===== 历史对话 ===== -->
      <aside class="a-side" aria-label="历史对话">
        <div class="a-side-head">
          <h2>历史对话</h2>
          <button class="a-new" type="button" @click="newConversation">
            <AppIcon name="plus" :size="13" />新对话
          </button>
        </div>

        <div class="a-convs">
          <button
            v-for="c in conversations"
            :key="c.id"
            class="a-conv"
            :class="{ 'is-active': c.id === activeId }"
            type="button"
            @click="openConversation(c)"
          >
            <span class="a-conv-title">{{ c.title || '未命名对话' }}</span>
            <span class="a-conv-time">{{ fmtConvTime(c.updateTime) }}</span>
            <span
              class="a-conv-del"
              role="button"
              tabindex="0"
              aria-label="删除对话"
              @click.stop="removeConversation(c)"
              @keyup.enter.stop="removeConversation(c)"
            >
              <AppIcon name="trash" :size="13" />
            </span>
          </button>
          <p v-if="!conversations.length" class="a-convs-empty">暂无历史对话</p>
        </div>

        <div class="a-user">
          <img class="mkp-avatar" :src="user.avatar || DEMO_AV" alt="" />
          <div class="a-user-tx">
            <b>{{ user.nickname }}</b>
            <span>{{ userLine }}</span>
          </div>
        </div>
      </aside>

      <!-- ===== 对话区 ===== -->
      <section class="a-chat" aria-label="智能助手对话">
        <header class="a-chat-head">
          <span class="a-bot" aria-hidden="true"><AppIcon name="paw" :size="16" /></span>
          <span class="a-chat-title">MKP 智能养宠顾问</span>
          <span class="a-status" :class="{ 'is-busy': streaming }">
            <i aria-hidden="true"></i>{{ streaming ? '正在思考…' : '在线诊疗中' }}
          </span>
        </header>

        <div v-show="showQuick" class="a-quick" aria-label="快捷提问">
          <button
            v-for="q in quickAsks"
            :key="q.label"
            class="a-chip"
            type="button"
            @click="useQuick(q)"
          >
            <span aria-hidden="true">{{ q.icon }}</span>{{ q.label }}
          </button>
        </div>

        <div ref="scrollRef" class="a-messages">
          <div v-if="!messages.length" class="a-welcome">
            <span class="a-welcome-mark" aria-hidden="true"><AppIcon name="bot" :size="26" /></span>
            <h3>{{ activeTitle }}</h3>
            <p>宠物健康、寄养、喂养、领养等问题都可以问我，试试上面的快捷提问。</p>
          </div>

          <div
            v-for="(m, i) in messages"
            :key="i"
            class="a-msg"
            :class="m.role === 'user' ? 'is-user' : 'is-bot'"
          >
            <span v-if="m.role !== 'user'" class="a-msg-avatar" aria-hidden="true">
              <AppIcon name="paw" :size="15" />
            </span>

            <div class="a-msg-body">
              <details v-if="m.thinking" class="a-think">
                <summary><AppIcon name="sparkles" :size="12" />思考过程</summary>
                <p>{{ m.thinking }}</p>
              </details>

              <div v-if="m.tools && m.tools.length" class="a-tools">
                <span v-for="(t, ti) in m.tools" :key="ti" class="a-tool">
                  <AppIcon name="search" :size="12" />{{ toolText(t) }}
                </span>
              </div>

              <div class="a-bubble">
                <span class="a-text">{{ m.content }}</span>
                <span v-if="streaming && i === messages.length - 1" class="a-caret"></span>
              </div>

              <div v-if="matchCount(m)" class="a-results">
                <p class="a-results-head">
                  <AppIcon name="search" :size="12" />
                  已检索交易广场 / 社区广场 · 找到 {{ matchCount(m) }} 条匹配
                </p>

                <article v-for="pt in m.postings" :key="'t' + pt.id" class="a-res">
                  <span class="a-res-thumb" aria-hidden="true">
                    <AppIcon name="paw" :size="22" />
                  </span>
                  <div class="a-res-body">
                    <span class="a-res-badge">{{ postingBadge(pt) }}</span>
                    <h4>{{ pt.petName || '未命名' }}</h4>
                    <p class="a-res-line">价格：{{ fmtPrice(pt.price, pt.priceUnit) }}</p>
                    <p v-if="pt.description" class="a-res-line">描述：{{ truncate(pt.description, 42) }}</p>
                    <div class="a-res-acts">
                      <button class="a-btn-dark" type="button" @click="router.push('/market')">联系卖家 →</button>
                      <button class="a-btn-light" type="button" @click="router.push('/market')">查看帖子</button>
                    </div>
                  </div>
                </article>

                <article v-for="p in m.posts" :key="'p' + p.id" class="a-res a-res--post">
                  <span v-if="firstImg(p)" class="a-res-thumb is-img">
                    <img :src="firstImg(p)" alt="" loading="lazy" />
                  </span>
                  <span v-else class="a-res-thumb" aria-hidden="true">
                    <AppIcon name="message" :size="22" />
                  </span>
                  <div class="a-res-body">
                    <span class="a-res-badge">社区动态</span>
                    <h4>{{ truncate(p.content, 36) }}</h4>
                    <div class="a-res-acts">
                      <button class="a-btn-dark" type="button" @click="router.push('/post/' + p.id)">查看帖子 →</button>
                    </div>
                  </div>
                </article>
              </div>
            </div>
          </div>
        </div>

        <div class="a-composer">
          <div class="a-input-row">
            <button
              class="a-plus"
              type="button"
              :aria-pressed="showQuick"
              title="快捷提问"
              @click="showQuick = !showQuick"
            >+</button>
            <textarea
              ref="inputRef"
              v-model="input"
              class="a-textarea"
              rows="1"
              placeholder="输入关于宠物健康、寄养或护理的问题…"
              aria-label="输入问题"
              @keydown.enter.exact.prevent="send"
            ></textarea>
            <button
              class="a-send"
              type="button"
              :disabled="streaming || !input.trim()"
              :aria-label="streaming ? '正在回答' : '发送'"
              @click="send"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
            </button>
          </div>
          <p class="a-hint">输入问题，<b>Enter</b> 发送，<b>Shift + Enter</b> 换行</p>
        </div>
      </section>
    </main>
  </div>
</template>

<style>
/* ============ 智能助手（原「养宠助手」）· 桌面端整页对话 ============ */
.mkp-assistant {
  position: relative;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--mkp-paper);
  color: var(--mkp-ink);
  font-family: var(--mkp-font);
  font-size: 14px;
  line-height: 1.6;
}
.mkp-assistant a { text-decoration: none; color: inherit; }
.mkp-assistant button { font-family: inherit; }

/* 背景装饰：参考图里的黄 / 蓝实心不规则色块
   全部位于面板之下（z-index:0），只在页边与环境底色处露出来 */
.a-blob {
  position: absolute;
  z-index: 0;
  pointer-events: none;
  will-change: transform;
}
.a-blob--1 {  /* 左上：黄 */
  left: -70px; top: 54px;
  width: 400px; height: 180px;
  background: #FFC93C;
  border-radius: 44% 56% 62% 38% / 80% 72% 28% 20%;
}
.a-blob--2 {  /* 右上：浅黄 */
  right: 30px; top: 56px;
  width: 260px; height: 150px;
  background: #FFD666;
  border-radius: 38% 62% 56% 44% / 76% 66% 34% 24%;
}
.a-blob--3 {  /* 左中：黄，贴左边缘的长块 */
  left: -84px; top: 320px;
  width: 210px; height: 430px;
  background: #FFC93C;
  border-radius: 52% 48% 44% 56% / 36% 40% 60% 64%;
}
.a-blob--4 {  /* 左下：蓝 */
  left: 10px; bottom: 46px;
  width: 170px; height: 170px;
  background: #2E5BFF;
  border-radius: 58% 42% 46% 54% / 54% 62% 38% 46%;
}
.a-blob--5 {  /* 右中：蓝，贴右边缘的长块 */
  right: -72px; top: 330px;
  width: 250px; height: 400px;
  background: #2E5BFF;
  border-radius: 46% 54% 50% 50% / 38% 44% 56% 62%;
}
.a-blob--6 {  /* 右下：蓝，铺到角落 */
  right: -100px; bottom: -76px;
  width: 500px; height: 250px;
  background: #2E5BFF;
  border-radius: 48% 52% 44% 56% / 62% 58% 42% 38%;
}

/* ---------- 顶部导航（统一使用 MkpTopNav 组件） ---------- */
.mkp-assistant .a-topbar { flex: none; }
.a-icon-btn {
  width: 36px; height: 36px; border-radius: 10px;
  border: none; background: transparent; color: var(--mkp-ink2);
  display: grid; place-items: center; cursor: pointer;
  transition: background .18s var(--mkp-ease), color .18s var(--mkp-ease);
}
.a-icon-btn:hover { background: var(--mkp-paper); color: var(--mkp-ink); }

/* ---------- 主区：历史对话 + 对话 ---------- */
.a-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  width: min(1360px, 100% - 64px);
  margin-inline: auto;
  padding: 36px 0 34px;
  display: grid;
  grid-template-columns: 312px minmax(0, 1fr);
  gap: 20px;
}
.a-main.is-side-hidden { grid-template-columns: minmax(0, 1fr); }
.a-main.is-side-hidden .a-side { display: none; }

/* 侧栏 */
.a-side {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  background: var(--mkp-surface);
  border: 1px solid var(--mkp-line);
  border-radius: 22px;
  box-shadow: var(--mkp-sh1);
}
.a-side-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.a-side-head h2 { font-size: 16px; font-weight: 800; letter-spacing: -.01em; }
.a-new {
  display: inline-flex; align-items: center; gap: 5px;
  background: var(--mkp-dark); color: #fff; border: none;
  padding: 7px 13px; border-radius: 999px;
  font-size: 12px; font-weight: 600; cursor: pointer; flex: none;
  transition: transform .18s var(--mkp-ease), opacity .18s var(--mkp-ease);
}
.a-new:hover { transform: translateY(-1px); opacity: .9; }

.a-convs {
  flex: 1; min-height: 0;
  overflow-y: auto;
  display: flex; flex-direction: column; gap: 8px;
  padding-right: 2px;
}
.a-conv {
  position: relative;
  display: flex; flex-direction: column; gap: 2px;
  width: 100%; padding: 10px 12px;
  border: none; border-radius: 12px;
  background: var(--mkp-dark); color: #fff;
  text-align: left; cursor: pointer;
  transition: transform .18s var(--mkp-ease), box-shadow .18s var(--mkp-ease), background .18s var(--mkp-ease);
}
.a-conv:hover { transform: translateY(-1px); box-shadow: 0 8px 20px rgba(28, 24, 19, .16); }
.a-conv.is-active { background: var(--mkp-dark2); box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, .34); }
.a-conv-title {
  font-size: 12.5px; font-weight: 600; color: #fff;
  padding-right: 22px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.a-conv-time { font-size: 11px; color: rgba(255, 255, 255, .58); }
.a-conv-del {
  position: absolute; right: 7px; top: 50%; transform: translateY(-50%);
  width: 24px; height: 24px; border-radius: 7px;
  display: grid; place-items: center;
  color: rgba(255, 255, 255, .6); opacity: 0;
  transition: opacity .18s var(--mkp-ease), background .18s var(--mkp-ease), color .18s var(--mkp-ease);
}
.a-conv:hover .a-conv-del, .a-conv-del:focus-visible { opacity: 1; }
.a-conv-del:hover { background: rgba(255, 255, 255, .16); color: #fff; }
.a-convs-empty { font-size: 12.5px; color: var(--mkp-ink3); text-align: center; padding: 24px 0; }

.a-user {
  display: flex; align-items: center; gap: 10px;
  padding-top: 14px; border-top: 1px solid var(--mkp-line);
}
.a-user .mkp-avatar { width: 36px; height: 36px; flex: none; border: 1px solid var(--mkp-line); }
.a-user-tx { display: flex; flex-direction: column; min-width: 0; }
.a-user-tx b {
  font-size: 13.5px; font-weight: 700;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.a-user-tx span { font-size: 11.5px; color: var(--mkp-ink3); }

/* 对话面板 */
.a-chat {
  min-height: 0;
  display: flex; flex-direction: column;
  background: var(--mkp-surface);
  border: 1px solid var(--mkp-line);
  border-radius: 22px;
  box-shadow: var(--mkp-sh1);
  overflow: hidden;
}
.a-chat-head {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 20px; border-bottom: 1px solid var(--mkp-line);
  flex: none;
}
.a-bot {
  width: 32px; height: 32px; border-radius: 50%; flex: none;
  background: var(--mkp-dark); color: #fff;
  display: grid; place-items: center;
}
.a-chat-title { font-size: 15px; font-weight: 700; }
.a-status {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 11.5px; color: var(--mkp-ink3);
  background: var(--mkp-surface2); border: 1px solid var(--mkp-line);
  padding: 4px 11px; border-radius: 999px;
}
.a-status i { width: 6px; height: 6px; border-radius: 50%; background: var(--mkp-online); display: block; }
.a-status.is-busy i { background: var(--mkp-star); animation: a-pulse 1.2s ease-in-out infinite; }
@keyframes a-pulse { 50% { opacity: .3; } }

.a-quick { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 20px 0; flex: none; }
.a-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12.5px; color: var(--mkp-ink2);
  background: var(--mkp-surface2);
  border: 1px solid var(--mkp-line);
  padding: 7px 13px; border-radius: 999px; cursor: pointer;
  transition: all .18s var(--mkp-ease);
}
.a-chip:hover { border-color: var(--mkp-ink); color: var(--mkp-ink); background: #fff; }

.a-messages {
  flex: 1; min-height: 0;
  overflow-y: auto;
  padding: 18px 20px 8px;
  display: flex; flex-direction: column; gap: 20px;
}

/* 欢迎 */
.a-welcome {
  margin: auto; max-width: 430px;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  text-align: center; padding: 28px 20px;
}
.a-welcome-mark {
  width: 56px; height: 56px; border-radius: 16px;
  background: var(--mkp-accent-soft); color: var(--mkp-accent-deep);
  display: grid; place-items: center;
}
.a-welcome h3 { font-size: 16px; font-weight: 800; }
.a-welcome p { font-size: 13px; color: var(--mkp-ink3); }

/* 消息 */
.a-msg { display: flex; gap: 10px; max-width: 100%; }
.a-msg.is-user { flex-direction: row-reverse; }
.a-msg-avatar {
  width: 30px; height: 30px; border-radius: 50%; flex: none;
  background: var(--mkp-dark); color: #fff;
  display: grid; place-items: center;
}
.a-msg-body {
  max-width: min(660px, 84%);
  min-width: 0;
  display: flex; flex-direction: column; gap: 10px;
}
.a-msg.is-user .a-msg-body { align-items: flex-end; max-width: min(560px, 80%); }
.a-bubble { border-radius: 16px; padding: 11px 15px; max-width: 100%; }
.a-msg.is-user .a-bubble {
  background: var(--mkp-dark); color: #fff;
  border-bottom-right-radius: 6px;
}
.a-msg.is-bot .a-bubble {
  background: var(--mkp-surface2); border: 1px solid var(--mkp-line);
  border-bottom-left-radius: 6px;
}
.a-text { font-size: 13.5px; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.a-caret {
  display: inline-block; width: 2px; height: 14px; margin-left: 2px;
  background: var(--mkp-accent); vertical-align: -2px;
  animation: a-blink .9s steps(1) infinite;
}
@keyframes a-blink { 50% { opacity: 0; } }

/* 思考过程 */
.a-think {
  max-width: 100%;
  padding: 9px 12px;
  border: 1px dashed var(--mkp-line);
  border-radius: 12px;
  background: var(--mkp-surface2);
}
.a-think summary {
  display: flex; align-items: center; gap: 5px;
  font-size: 11.5px; color: var(--mkp-ink3);
  cursor: pointer; list-style: none;
}
.a-think summary::-webkit-details-marker { display: none; }
.a-think p { margin-top: 6px; font-size: 12.5px; color: var(--mkp-ink2); white-space: pre-wrap; word-break: break-word; }

/* 工具检索 */
.a-tools { display: flex; flex-wrap: wrap; gap: 6px; }
.a-tool {
  display: inline-flex; align-items: center; gap: 5px;
  height: 24px; padding: 0 10px; border-radius: 999px;
  background: var(--mkp-surface2); border: 1px solid var(--mkp-line);
  color: var(--mkp-ink2); font-size: 11.5px;
}

/* 检索结果卡片 */
.a-results { display: flex; flex-direction: column; gap: 10px; width: 100%; }
.a-results-head { display: flex; align-items: center; gap: 6px; font-size: 11.5px; color: var(--mkp-ink3); }
.a-res {
  display: flex; gap: 14px;
  padding: 14px;
  background: #fff; border: 1px solid var(--mkp-line); border-radius: 16px;
  box-shadow: var(--mkp-sh1);
  transition: transform .18s var(--mkp-ease), box-shadow .18s var(--mkp-ease);
}
.a-res:hover { transform: translateY(-2px); box-shadow: var(--mkp-sh2); }
.a-res-thumb {
  width: 88px; height: 88px; border-radius: 12px; flex: none;
  background: var(--mkp-paper); color: var(--mkp-ink3);
  display: grid; place-items: center; overflow: hidden;
}
.a-res-thumb.is-img img { width: 100%; height: 100%; object-fit: cover; }
.a-res-body { min-width: 0; display: flex; flex-direction: column; gap: 5px; }
.a-res-badge {
  align-self: flex-start;
  font-size: 10.5px; font-weight: 700; letter-spacing: .04em;
  color: var(--mkp-accent-deep); background: var(--mkp-accent-soft);
  padding: 2px 8px; border-radius: 6px;
}
.a-res-body h4 {
  font-size: 15px; font-weight: 800; letter-spacing: -.01em;
  word-break: break-word;
}
.a-res-line { font-size: 12.5px; color: var(--mkp-ink2); }
.a-res-acts { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.a-btn-dark {
  display: inline-flex; align-items: center; gap: 5px;
  background: var(--mkp-dark); color: #fff; border: none;
  padding: 7px 14px; border-radius: 999px;
  font-size: 12.5px; font-weight: 600; cursor: pointer;
  transition: transform .18s var(--mkp-ease), opacity .18s var(--mkp-ease);
}
.a-btn-dark:hover { transform: translateY(-1px); opacity: .9; }
.a-btn-light {
  display: inline-flex; align-items: center;
  background: #fff; color: var(--mkp-ink2);
  border: 1px solid var(--mkp-line);
  padding: 7px 14px; border-radius: 999px;
  font-size: 12.5px; font-weight: 600; cursor: pointer;
  transition: all .18s var(--mkp-ease);
}
.a-btn-light:hover { border-color: var(--mkp-ink); color: var(--mkp-ink); }

/* 输入区 */
.a-composer { flex: none; padding: 12px 20px 16px; border-top: 1px solid var(--mkp-line); background: #fff; }
.a-input-row {
  display: flex; align-items: flex-end; gap: 10px;
  padding: 8px 8px 8px 12px;
  background: var(--mkp-surface2);
  border: 1px solid var(--mkp-line);
  border-radius: 18px;
  transition: border-color .2s var(--mkp-ease), box-shadow .2s var(--mkp-ease), background .2s var(--mkp-ease);
}
.a-input-row:focus-within {
  border-color: rgba(28, 24, 19, .32);
  box-shadow: 0 0 0 4px rgba(9, 9, 11, .05);
  background: #fff;
}
.a-plus {
  width: 30px; height: 30px; flex: none;
  border-radius: 50%; border: 1px solid var(--mkp-line); background: #fff;
  color: var(--mkp-ink2); font-size: 17px; line-height: 1; cursor: pointer;
  display: grid; place-items: center;
  transition: all .18s var(--mkp-ease);
}
.a-plus:hover { border-color: var(--mkp-ink); color: var(--mkp-ink); }
.a-plus[aria-pressed='true'] { background: var(--mkp-dark); border-color: var(--mkp-dark); color: #fff; }
.a-textarea {
  flex: 1; min-height: 30px; max-height: 132px;
  border: none; background: transparent; resize: none;
  font: inherit; font-size: 13.5px; line-height: 1.55; color: var(--mkp-ink);
  padding: 5px 0; outline: none;
}
.a-textarea::placeholder { color: var(--mkp-ink3); }
.a-send {
  width: 34px; height: 34px; flex: none; border-radius: 50%;
  border: none; background: var(--mkp-dark); color: #fff;
  display: grid; place-items: center; cursor: pointer;
  transition: transform .18s var(--mkp-ease), opacity .18s var(--mkp-ease);
}
.a-send:hover:not(:disabled) { transform: translateY(-1px); }
.a-send:disabled { opacity: .4; cursor: not-allowed; }
.a-hint { margin-top: 8px; padding-left: 2px; font-size: 11.5px; color: var(--mkp-ink3); }
.a-hint b { font-weight: 600; color: var(--mkp-ink2); }

/* ---------- 响应式（桌面优先） ---------- */
@media (max-width: 1180px) {
  .a-main { width: calc(100% - 40px); }
  .a-main { grid-template-columns: 264px minmax(0, 1fr); }
}
@media (max-width: 820px) {
  .a-msg-body { max-width: 92%; }
}
@media (prefers-reduced-motion: reduce) {
  .a-status.is-busy i, .a-caret { animation: none; }
}

/* ================================================================
   液态玻璃（Liquid Glass）覆盖层 —— 只在 .is-glass 下生效
   思路：面板变半透明 + backdrop blur/saturate，把背景的黄蓝柔光当"液体"折射；
        用 inset 高光做玻璃厚度、conic 描边做边缘色散、斜向高光做镜面感。
   ================================================================ */

/* 1) 页边的黄 / 蓝色块往面板下方延伸：玻璃只在边缘"吸"到颜色，
      面板中间保持干净通透（避免整块被染成牛奶彩虹） */
.is-glass .a-blob--1 { left: -70px; top: 54px; width: 420px; height: 190px; }
.is-glass .a-blob--3 { left: -110px; top: 300px; width: 300px; height: 500px; }
.is-glass .a-blob--2 { right: 16px; top: 52px; width: 168px; height: 118px; }
.is-glass .a-blob--5 { right: -76px; top: 300px; width: 200px; height: 380px; }
.is-glass .a-blob--6 { right: -210px; bottom: -170px; width: 430px; height: 230px; }
.is-glass .a-blob--4 { left: 6px; bottom: 40px; width: 180px; height: 180px; }

/* 2) 两块面板：玻璃本体 */
.is-glass .a-chat,
.is-glass .a-side {
  border-radius: 30px;
  border: 1px solid rgba(255, 255, 255, .62);
  background: linear-gradient(152deg, rgba(255, 255, 255, .60) 0%, rgba(255, 255, 255, .40) 52%, rgba(255, 255, 255, .50) 100%);
  -webkit-backdrop-filter: blur(34px) saturate(155%);
  backdrop-filter: blur(34px) saturate(155%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, .98),
    inset 0 0 0 1px rgba(255, 255, 255, .26),
    inset 16px 0 34px -28px rgba(255, 255, 255, .95),
    inset -20px 0 40px -32px rgba(255, 255, 255, .9),
    inset 0 -30px 48px -34px rgba(255, 255, 255, .95),
    0 28px 68px rgba(33, 29, 22, .18),
    0 3px 10px rgba(33, 29, 22, .06);
}
/* 镜面斜向高光 + 左上角反光（压在内容之下，不糊字） */
.is-glass .a-chat::before,
.is-glass .a-side::before {
  content: ""; position: absolute; inset: 0; z-index: -1;
  border-radius: inherit; pointer-events: none;
  background:
    radial-gradient(110% 80% at 2% -10%, rgba(255, 255, 255, .72), rgba(255, 255, 255, 0) 40%),
    linear-gradient(118deg, rgba(255, 255, 255, .42) 0%, rgba(255, 255, 255, 0) 20%,
      rgba(255, 255, 255, 0) 80%, rgba(255, 255, 255, .36) 100%);
}
/* 边缘色散描边（1px 彩色光圈，液态玻璃的标志性细节） */
.is-glass .a-chat::after,
.is-glass .a-side::after {
  content: ""; position: absolute; inset: 0; z-index: -1;
  border-radius: inherit; pointer-events: none; padding: 1px;
  background: conic-gradient(from 150deg,
    rgba(255, 255, 255, .95) 0deg, rgba(255, 201, 60, .55) 70deg, rgba(46, 91, 255, .45) 150deg,
    rgba(255, 255, 255, .9) 220deg, rgba(255, 214, 102, .5) 300deg, rgba(255, 255, 255, .95) 360deg);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  mask-composite: exclude;
  opacity: .82;
}

/* 3) 面板内部：头部 / 输入区 / 卡片也跟着玻璃化 */
.is-glass .a-chat-head {
  background: linear-gradient(180deg, rgba(255, 255, 255, .60), rgba(255, 255, 255, .18));
  border-bottom: 1px solid rgba(255, 255, 255, .6);
}
.is-glass .a-composer {
  background: linear-gradient(0deg, rgba(255, 255, 255, .66), rgba(255, 255, 255, .22));
  border-top: 1px solid rgba(255, 255, 255, .6);
}
.is-glass .a-input-row {
  background: rgba(255, 255, 255, .48);
  border-color: rgba(255, 255, 255, .72);
}
.is-glass .a-input-row:focus-within {
  background: rgba(255, 255, 255, .68);
  border-color: rgba(255, 255, 255, .95);
  box-shadow: 0 0 0 4px rgba(255, 255, 255, .3);
}
.is-glass .a-plus { background: rgba(255, 255, 255, .55); border-color: rgba(255, 255, 255, .75); }
.is-glass .a-plus[aria-pressed='true'] { background: var(--mkp-dark); border-color: var(--mkp-dark); color: #fff; }
.is-glass .a-chip {
  background: rgba(255, 255, 255, .62);
  border-color: rgba(255, 255, 255, .7);
  -webkit-backdrop-filter: blur(8px); backdrop-filter: blur(8px);
}
.is-glass .a-chip:hover { background: rgba(255, 255, 255, .78); border-color: rgba(255, 255, 255, .95); }
.is-glass .a-msg.is-bot .a-bubble {
  background: rgba(255, 255, 255, .66);
  border: 1px solid rgba(255, 255, 255, .72);
  -webkit-backdrop-filter: blur(8px); backdrop-filter: blur(8px);
}
.is-glass .a-think { background: rgba(255, 255, 255, .55); border-color: rgba(255, 255, 255, .62); }
.is-glass .a-tool { background: rgba(255, 255, 255, .58); border-color: rgba(255, 255, 255, .66); }
.is-glass .a-res {
  background: rgba(255, 255, 255, .68);
  border: 1px solid rgba(255, 255, 255, .75);
  -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .95), 0 14px 30px rgba(33, 29, 22, .09);
}
.is-glass .a-res-thumb { background: rgba(255, 255, 255, .45); }
.is-glass .a-btn-light { background: rgba(255, 255, 255, .6); border-color: rgba(255, 255, 255, .8); }
/* 会话卡保持深色（玻璃上更稳、字更清楚） */
.is-glass .a-conv { background: rgba(28, 24, 19, .88); -webkit-backdrop-filter: blur(6px); backdrop-filter: blur(6px); }
.is-glass .a-conv.is-active { background: rgba(42, 36, 28, .92); box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, .4); }
.is-glass .a-user { border-top-color: rgba(255, 255, 255, .5); }
.is-glass .a-welcome-mark { background: rgba(255, 255, 255, .6); }
.is-glass .a-status { background: rgba(255, 255, 255, .5); border-color: rgba(255, 255, 255, .7); }

/* 去掉根节点上的 is-glass 类即可整体还原为实心白面板 */
</style>
