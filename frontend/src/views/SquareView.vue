<template>
  <div class="mkp-square">
    <a class="mkp-skip" href="#s-main">跳到主内容</a>

    <!-- ===== 顶栏：纯白，与页面米白底色做区分 ===== -->
    <header class="s-top" :class="{ scrolled }">
      <div class="s-top-inner">
        <div class="s-top-left">
          <router-link class="s-brand" to="/desktop" aria-label="返回 MKP 桌面">
            <span class="s-mark" aria-hidden="true">MKP</span>
            <span class="s-word">PET COMMUNITY</span>
          </router-link>
          <nav class="s-nav" aria-label="主导航">
            <router-link
              v-for="n in nav"
              :key="n.to"
              :to="n.to"
              :class="{ 'is-active': n.to === '/square' }"
            >{{ n.label }}</router-link>
          </nav>
        </div>

        <div class="s-top-right">
          <div class="s-search">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input
              ref="searchRef"
              v-model="query"
              type="search"
              placeholder="搜索帖子、话题或作者…"
              autocomplete="off"
              aria-label="搜索帖子、话题或作者"
            />
            <kbd aria-hidden="true">/</kbd>
          </div>
          <button class="s-btn-post" type="button" @click="openComposer">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
            发动态
          </button>
          <router-link class="s-me" to="/profile" :aria-label="'个人中心：' + me.nickname">
            <img class="mkp-avatar" :src="me.avatar || DEMO_AV" alt="" width="34" height="34" />
          </router-link>
        </div>
      </div>
    </header>

    <main id="s-main" class="s-main">
      <!-- ===== Hero 文案卡 + 精选画廊 3D 轮播 ===== -->
      <section v-show="tab === 'community'" class="s-hero" aria-label="社区主视觉">
        <div class="s-hero-card" data-mkp-reveal>
          <div>
            <p class="s-eyebrow">Curated for pet lovers</p>
            <h1 class="s-hero-title">把治愈，<br>收藏成一种习惯。</h1>
            <p class="s-hero-desc">探索社区里的毛孩子萌照、养宠技巧与丰富的生活灵感记录。</p>
            <div class="s-stats">
              <div><b>{{ stats.posts }}</b><span>社区动态</span></div>
              <div><b>{{ stats.likes }}</b><span>累计点赞</span></div>
              <div><b>{{ stats.tags }}</b><span>热门话题</span></div>
            </div>
          </div>
          <div>
            <div class="s-tags-label">🔥 热门话题</div>
            <div class="s-tags">
              <button
                v-for="h in hotQueries"
                :key="h.q"
                class="s-tag"
                type="button"
                :aria-pressed="query === h.q"
                @click="quickSearch(h)"
              >{{ h.label }}</button>
            </div>
            <p class="s-note" :class="{ 'is-live': mode === 'live', 'is-error': mode === 'error' }">
              <span class="s-note-dot" aria-hidden="true"></span>{{ noteText }}
            </p>
          </div>
        </div>

        <GalleryWall />
      </section>

      <!-- ===== 帖子流 ===== -->
      <section class="s-section" aria-label="社区帖子">
        <div class="s-sec-head" data-mkp-reveal>
          <h2 class="s-sec-title">{{ secTitle }}</h2>
          <div class="s-tabs" role="tablist" aria-label="视图切换">
            <button
              v-for="t in tabs"
              :key="t.key"
              class="s-tab"
              role="tab"
              type="button"
              :id="'s-tab-' + t.key"
              :aria-selected="tab === t.key"
              :tabindex="tab === t.key ? 0 : -1"
              @click="selectTab(t.key)"
              @keydown="onTabKey"
            >{{ t.label }}</button>
          </div>
        </div>

        <div v-show="tab === 'community'" class="s-tools">
          <div class="s-kinds" role="group" aria-label="帖子类型筛选">
            <button
              v-for="k in kinds"
              :key="k.key"
              class="s-chip"
              type="button"
              :aria-pressed="kind === k.key"
              @click="setKind(k.key)"
            >{{ k.label }}</button>
          </div>
          <div class="s-sorts" role="group" aria-label="排序方式">
            <button class="s-sort" type="button" :aria-pressed="sort === 'latest'" @click="sort = 'latest'">最新</button>
            <button class="s-sort" type="button" :aria-pressed="sort === 'hot'" @click="sort = 'hot'">最热</button>
          </div>
        </div>

        <div :id="'s-panel-' + tab" role="tabpanel" :aria-labelledby="'s-tab-' + tab">
          <LoadingBlock v-if="loading" />

          <div v-else-if="mode === 'error'" class="s-empty">
            <div class="s-em-ico" aria-hidden="true">🐾</div>
            <h3>数据暂时加载不出来</h3>
            <p>{{ errorMsg }}</p>
            <button class="s-btn-ghost" type="button" @click="load">重新加载</button>
          </div>

          <EmptyState
            v-else-if="!list.length"
            :text="emptyText"
            :sub="emptySub"
            :action-label="tab === 'community' ? '清除筛选' : ''"
            @action="clearAll"
          />

          <div v-else class="s-grid" data-mkp-group>
            <PostCard
              v-for="(p, i) in list"
              :key="tab + '-' + p.id"
              :post="p"
              :index="i"
              :mine="tab === 'mine'"
              :draft="tab === 'drafts'"
              @open="openPost(p)"
              @like="toggleLike(p)"
              @del="removeMine(p)"
              @topic="onTopic"
            />
          </div>

          <div v-if="hasMore" class="s-more">
            <button class="s-btn-ghost" type="button" :disabled="searchLoading" @click="runSearch(true)">
              {{ searchLoading ? '加载中…' : '加载更多' }}
            </button>
          </div>
        </div>
      </section>
    </main>

    <!-- ===== 页脚 ===== -->
    <footer class="s-foot">
      <div class="s-foot-inner">
        <span>© 2026 MKP Pet Community · 社交广场</span>
      </div>
    </footer>

    <!-- ===== 发动态弹窗 ===== -->
    <div v-if="composerOpen" class="s-scrim" @click.self="closeComposer">
      <div class="s-modal" role="dialog" aria-modal="true" aria-labelledby="s-comp-title">
        <div class="s-modal-head">
          <h3 id="s-comp-title">发动态</h3>
          <button class="s-x" type="button" aria-label="关闭" @click="closeComposer">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
          </button>
        </div>
        <div class="s-modal-body">
          <textarea
            v-model="composer.content"
            class="s-input s-ta"
            rows="5"
            placeholder="分享你和宠物的故事…"
            aria-label="帖子内容"
          ></textarea>

          <div v-if="composer.imgs.length" class="s-comp-imgs">
            <div v-for="(im, i) in composer.imgs" :key="im + i" class="s-comp-img">
              <img :src="im" alt="" />
              <button class="s-comp-x" type="button" :aria-label="'移除第 ' + (i + 1) + ' 张图片'" @click="composer.imgs.splice(i, 1)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
              </button>
            </div>
          </div>

          <button class="s-upload" type="button" :disabled="uploading" @click="pickRef.click()">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="9" cy="9" r="1.6"/><path d="m21 15-4-4-7 7"/></svg>
            {{ uploading ? '上传中…' : '添加图片' }}
          </button>
          <input ref="pickRef" class="mkp-sr-only" type="file" accept="image/*" multiple @change="onPick" />
        </div>
        <div class="s-modal-foot">
          <button class="s-btn-ghost" type="button" @click="closeComposer">取消</button>
          <button class="s-btn-primary" type="button" :disabled="publishing || !composer.content.trim()" @click="publish">
            {{ publishing ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, h, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import '../assets/mkp/mkp.css'
import { initMkpReveal } from '../utils/mkpReveal'
import { getPostList, likePost, createPost, uploadPostImages, deletePost, searchPosts } from '../api'
import { fmtTime } from '../utils/constants'
import { toastSuccess } from '../utils/toast'
import GalleryWall from '../components/GalleryWall.vue'

/* ================================================================
   社交广场（桌面端）
   - 版式：Hero 文案卡 + 精选画廊 3D 轮播 + 暗色帖子流卡片
   - 数据：全部来自后端真实接口 /api/post（点赞 / 发布 / 删除 / 上传）
     没有本地演示数据；接口不可用时给出明确的错误态
   ================================================================ */
const route = useRoute()
const router = useRouter()
const DEMO_AV = '/mkp/a04.png'
const fmtNum = (n) => (n >= 1000 ? (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k' : String(n || 0))

/* ---------------- 当前用户 ---------------- */
const me = ref({
  nickname: localStorage.getItem('nickname') || '我',
  avatar: localStorage.getItem('avatar') || ''
})
const myId = Number(localStorage.getItem('userId')) || null

/* ---------------- 顶部导航（与桌面端一致） ---------------- */
const nav = [
  { to: '/square', label: '社交广场' },
  { to: '/market', label: '交易市场' },
  { to: '/trades', label: '我的交易' },
  { to: '/assistant', label: '智能助手' },
  { to: '/pet-service', label: '宠物服务' }
]

/* ---------------- 页面状态 ---------------- */
const tab = ref('community') // community | mine | drafts
const kind = ref('all')
const sort = ref('latest')
const query = ref('')
const loading = ref(true)
const mode = ref('loading') // loading | live | error
const errorMsg = ref('')
const scrolled = ref(false)

const tabs = [
  { key: 'community', label: '社区帖子' },
  { key: 'mine', label: '我的发布' },
  { key: 'drafts', label: '草稿箱' }
]
const kinds = [
  { key: 'all', label: '全部帖子' },
  { key: 'img', label: '有图分享' },
  { key: 'text', label: '文字随想' },
  { key: 'hot', label: '高赞精选' }
]
/* 热门话题：label 是展示文字，q 是点击后写进搜索框的关键词（按后端真实内容调整即可） */
const hotQueries = [
  { label: '# 治愈狗狗', q: '狗' },
  { label: '# 萌宠猫咪', q: '猫' },
  { label: '# 领养日记', q: '领养' },
  { label: '# 养宠避坑', q: '驱虫' }
]

/* ---------------- 数据加载（真实接口） ---------------- */
const posts = ref([])
const minePosts = ref([])

async function load () {
  loading.value = true
  errorMsg.value = ''
  try {
    const raw = (await getPostList()) || []
    const adapted = adaptPosts(raw)
    posts.value = adapted
    minePosts.value = myId ? adapted.filter((p) => p.uid === myId) : []
    mode.value = 'live'
  } catch (e) {
    posts.value = []
    minePosts.value = []
    mode.value = 'error'
    errorMsg.value = (e && e.response && e.response.status)
      ? `接口返回 ${e.response.status}，请稍后重试`
      : '请确认后端服务（/api）是否已启动'
  } finally {
    loading.value = false
  }
}

/* ---------------- 派生数据 ---------------- */
/* 后端 PostVO -> 卡片数据结构（列表与搜索共用） */
function adaptPosts (raw) {
  return (raw || []).map((p) => ({
    id: p.id,
    uid: p.userId ?? p.author?.id ?? null,
    content: p.content || '',
    imgs: Array.isArray(p.images) ? p.images : p.images ? [p.images] : [],
    nick: p.author?.nickname || '匿名用户',
    avatar: p.author?.avatar || '',
    timeRaw: p.createTime,
    likes: p.likeCount || 0,
    views: p.viewCount || 0,
    comments: typeof p.commentCount === 'number' ? p.commentCount : null,
    liked: !!p.liked
  }))
}

/* 搜索结果：额外带上后端返回的高亮片段（后端已转义，只含 mark 标记） */
function adaptSearchRecords (records) {
  const list = adaptPosts(records)
  const raw = records || []
  list.forEach((item, i) => {
    const src = raw[i] || {}
    item.hlContent = src.contentHighlight || null
    item.hlAuthor = src.authorHighlight || null
  })
  return list
}

const feedPosts = computed(() => {
  // 有关键词时用服务端检索结果（文案 + 作者昵称），否则用本地已加载的列表
  let arr = searchMode.value ? [...searchResults.value] : [...posts.value]
  if (kind.value === 'img') arr = arr.filter((p) => p.imgs.length > 0)
  else if (kind.value === 'text') arr = arr.filter((p) => !p.imgs.length)
  if (!searchMode.value) {
    const q = query.value.trim().replace(/^#/, '').toLowerCase()
    if (q) arr = arr.filter((p) => (p.content + ' ' + p.nick).toLowerCase().includes(q))
  }
  if (sort.value === 'hot' || kind.value === 'hot') arr = [...arr].sort((a, b) => b.likes - a.likes)
  return arr
})

/* 草稿箱：后端暂未提供草稿接口，接入后把 list 换成草稿数据即可 */
const list = computed(() => {
  if (tab.value === 'mine') return minePosts.value
  if (tab.value === 'drafts') return []
  return feedPosts.value
})

const secTitle = computed(() => {
  if (tab.value === 'mine') return '我的发布'
  if (tab.value === 'drafts') return '草稿箱'
  return kind.value === 'hot' ? '高赞精选' : '社区精选发布'
})

/* ---------------- 服务端搜索：帖子文案 + 作者昵称 ---------------- */
const searchMode = ref(false)
const searchLoading = ref(false)
const searchPage = ref(1)
const searchTotal = ref(0)
const searchResults = ref([])
let searchTimer = null

const hasMore = computed(() => searchMode.value && searchResults.value.length < searchTotal.value)

/* 帖子类型 chip -> 有图筛选：img=只看有图 / text=只看纯文字 / 其它=不限 */
function currentHasImage () {
  if (kind.value === 'img') return true
  if (kind.value === 'text') return false
  return null
}

async function runSearch (append = false) {
  const raw = query.value.trim()
  // 输入 #话题 时按话题精确筛，不再当全文关键词
  const topic = raw.startsWith('#') ? raw.slice(1).trim() : ''
  const keyword = raw.startsWith('#') ? '' : raw
  const hasImage = currentHasImage()
  // 关键词、图片筛选、话题、最热都没有时，退回本地已加载列表
  if (!keyword && !topic && hasImage === null && kind.value !== 'hot') {
    searchMode.value = false
    searchResults.value = []
    searchTotal.value = 0
    searchPage.value = 1
    return
  }
  searchLoading.value = true
  try {
    const page = append ? searchPage.value + 1 : 1
    const sortParam = (sort.value === 'hot' || kind.value === 'hot') ? 'hot' : 'relevance'
    const res = await searchPosts(keyword, page, 10, sortParam, hasImage, topic || null)
    const adapted = adaptSearchRecords((res && res.records) || [])
    searchResults.value = append ? searchResults.value.concat(adapted) : adapted
    searchPage.value = page
    searchTotal.value = (res && res.total) || 0
    searchMode.value = true
    mode.value = 'live'
  } catch (e) {
    if (!append) searchResults.value = []
    searchMode.value = true
  } finally {
    searchLoading.value = false
  }
}

function scheduleSearch (delay = 50) {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => runSearch(false), delay)
}

watch(query, () => scheduleSearch(300))
watch([sort, kind], () => scheduleSearch())

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

const stats = computed(() => ({
  posts: posts.value.length,
  likes: fmtNum(posts.value.reduce((sum, p) => sum + (p.likes || 0), 0)),
  tags: hotQueries.length
}))

const noteText = computed(() => {
  if (searchMode.value) return '找到 ' + searchTotal.value + ' 条与「' + query.value.trim() + '」相关的帖子'
  if (mode.value === 'live') return '已连接后端 · 数据来自 /api/post'
  if (mode.value === 'error') return '接口未连接 · /api/post 暂时不可用'
  return '正在读取社区数据…'
})

const emptyText = computed(() => {
  if (tab.value === 'mine') return '你还没有发布过帖子'
  if (tab.value === 'drafts') return '后端暂未提供草稿接口'
  return '没有找到相关帖子'
})
const emptySub = computed(() => {
  if (tab.value === 'mine') return '点击右上角「发动态」发布第一条帖子'
  if (tab.value === 'drafts') return '草稿接口上线后，未发布的帖子会出现在这里'
  return '换个关键词或主题试试，也可以成为第一个发布的人'
})

/* ---------------- 交互 ---------------- */
function setKind (k) {
  kind.value = k
  if (k === 'hot') sort.value = 'hot'
  else if (sort.value === 'hot') sort.value = 'latest'
}
function quickSearch (h) {
  query.value = query.value === h.q ? '' : h.q
}
/* 点帖子上的话题标签：搜索框写入 #话题，按话题精确筛 */
function onTopic (t) {
  const tag = '#' + t
  query.value = query.value === tag ? '' : tag
}
function clearAll () {
  query.value = ''
  kind.value = 'all'
  sort.value = 'latest'
}
function selectTab (key) {
  tab.value = key
  document.querySelectorAll('.s-tabs [role="tab"]').forEach((el) => {
    el.tabIndex = el.id === 's-tab-' + key ? 0 : -1
  })
}
function onTabKey (e) {
  const keys = tabs.map((t) => t.key)
  const idx = keys.indexOf(tab.value)
  let ni = null
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') ni = (idx + 1) % keys.length
  else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') ni = (idx - 1 + keys.length) % keys.length
  else if (e.key === 'Home') ni = 0
  else if (e.key === 'End') ni = keys.length - 1
  if (ni !== null) {
    e.preventDefault()
    selectTab(keys[ni])
    nextTick(() => document.getElementById('s-tab-' + keys[ni])?.focus())
  }
}

async function toggleLike (p) {
  const before = p.liked
  p.liked = !before
  p.likes = (p.likes || 0) + (p.liked ? 1 : -1)
  try {
    await likePost(p.id)
  } catch {
    p.liked = before
    p.likes = (p.likes || 0) + (p.liked ? 1 : -1)
  }
}

function openPost (p) {
  router.push(`/post/${p.id}`)
}

async function removeMine (p) {
  try {
    await deletePost(p.id)
    minePosts.value = minePosts.value.filter((x) => x.id !== p.id)
    posts.value = posts.value.filter((x) => x.id !== p.id)
    toastSuccess('已删除')
  } catch {
    /* interceptor 已提示 */
  }
}

/* ---------------- 发动态 ---------------- */
const composerOpen = ref(false)
const composer = ref({ content: '', imgs: [] })
const uploading = ref(false)
const publishing = ref(false)
const pickRef = ref(null)
const searchRef = ref(null)

function openComposer () {
  composerOpen.value = true
}
function closeComposer () {
  composerOpen.value = false
  composer.value = { content: '', imgs: [] }
}

async function onPick (e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  uploading.value = true
  try {
    const urls = (await uploadPostImages(files)) || []
    composer.value.imgs.push(...urls)
  } catch {
    /* interceptor 已提示 */
  } finally {
    uploading.value = false
  }
}

async function publish () {
  const content = composer.value.content.trim()
  if (!content || publishing.value) return
  publishing.value = true
  try {
    await createPost(content, composer.value.imgs)
    closeComposer()
    toastSuccess('发布成功')
    tab.value = 'community'
    await load()
  } catch {
    /* interceptor 已提示 */
  } finally {
    publishing.value = false
  }
}

/* ---------------- 键盘 / 滚动 ---------------- */
function onKey (e) {
  const tag = (document.activeElement || {}).tagName
  if (e.key === '/' && !['INPUT', 'TEXTAREA', 'BUTTON'].includes(tag)) {
    e.preventDefault()
    searchRef.value?.focus()
  }
}

let disposeReveal = null
let disposeScroll = null
onMounted(async () => {
  /* 从帖子详情的话题标签跳进来时，带上 ?q=#话题 直接筛 */
  if (route.query.q) query.value = String(route.query.q)

  await load()
  await nextTick()
  disposeReveal = initMkpReveal(document.querySelector('.mkp-square'))
  const onScroll = () => { scrolled.value = window.scrollY > 6 }
  window.addEventListener('scroll', onScroll, { passive: true })
  disposeScroll = () => window.removeEventListener('scroll', onScroll)
  onScroll()
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  disposeReveal?.()
  disposeScroll?.()
  document.removeEventListener('keydown', onKey)
})

/* ================= 内联小组件 ================= */
const LoadingBlock = {
  setup () {
    return () => h('div', { class: 's-empty' }, [h('span', { class: 's-spinner' }), h('p', '加载中…')])
  }
}

const EmptyState = {
  props: {
    text: { type: String, default: '没有找到相关帖子' },
    sub: { type: String, default: '' },
    actionLabel: { type: String, default: '' }
  },
  emits: ['action'],
  setup (props, { emit }) {
    return () => h('div', { class: 's-empty' }, [
      h('div', { class: 's-em-ico', 'aria-hidden': 'true' }, '🐾'),
      h('h3', props.text),
      h('p', props.sub),
      props.actionLabel
        ? h('button', { class: 's-btn-ghost', type: 'button', onClick: () => emit('action') }, props.actionLabel)
        : null
    ])
  }
}

const PostCard = {
  props: {
    post: { type: Object, required: true },
    index: { type: Number, default: 0 },
    mine: Boolean,
    draft: Boolean
  },
  emits: ['open', 'like', 'del', 'topic'],
  setup (props, { emit }) {
    /* 有高亮片段时用 innerHTML 渲染（片段由后端转义，只含 mark 标记）；没有就按纯文本渲染 */
    function rich (tag, cls, text, html) {
      const attrs = cls ? { class: cls } : {}
      if (html) {
        attrs.innerHTML = html
        return h(tag, attrs)
      }
      return h(tag, attrs, text || '')
    }
    /* 从文案里取 #话题，渲染成可点标签（点了按话题筛，规则与后端索引一致） */
    function topicsOf (text) {
      const out = []
      const re = /#([\p{L}\p{N}_]{1,20})/gu
      let m
      while ((m = re.exec(text || '')) !== null) {
        if (out.indexOf(m[1]) === -1) out.push(m[1])
      }
      return out.slice(0, 4)
    }
    return () => {
      const p = props.post
      const hasImg = !!(p.imgs && p.imgs.length)
      const kids = []

      /* 媒体区：有图 → 图；无图 → 文字卡 */
      if (hasImg) {
        kids.push(h('div', { class: 's-media' }, [
          h('img', { src: p.imgs[0], alt: '帖子配图：' + String(p.content || '').slice(0, 24), loading: 'lazy' }),
          props.draft ? h('span', { class: 's-stamp' }, '草稿') : null
        ]))
      } else {
        kids.push(h('div', { class: 's-textbody' }, [rich('p', '', p.content, p.hlContent)]))
      }

      /* 内容区 */
      const body = [rich('div', 's-title', p.content, p.hlContent)]
      const topics = topicsOf(p.content)
      if (topics.length) {
        body.push(h('div', { class: 's-topics' }, topics.map((t) => h('button', {
          class: 's-topic',
          type: 'button',
          onClick: (e) => { e.stopPropagation(); emit('topic', t) }
        }, '#' + t))))
      }
      if (!props.draft) {
        body.push(h('div', { class: 's-meta' }, [
          h('div', { class: 's-author' }, [
            h('img', { class: 'mkp-avatar', src: p.avatar || DEMO_AV, alt: '' }),
            rich('span', 's-author-name', p.nick || '匿名用户', p.hlAuthor)
          ]),
          h('span', { class: 's-date' }, p.timeRaw ? fmtTime(p.timeRaw) : '')
        ]))
      }

      const acts = []
      if (!props.draft) {
        acts.push(h('button', {
          class: ['s-like', { 'is-liked': p.liked }],
          type: 'button',
          'aria-pressed': p.liked ? 'true' : 'false',
          'aria-label': '点赞（' + (p.likes || 0) + '）',
          onClick: (e) => { e.stopPropagation(); emit('like') }
        }, [
          h('svg', {
            viewBox: '0 0 24 24',
            'aria-hidden': 'true',
            fill: p.liked ? 'currentColor' : 'none',
            stroke: 'currentColor',
            'stroke-width': 2,
            'stroke-linejoin': 'round'
          }, [h('path', { d: 'M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z' })]),
          h('span', fmtNum(p.likes || 0))
        ]))
        if (typeof p.comments === 'number') acts.push(h('span', { class: 's-view' }, p.comments + ' 评论'))
        acts.push(h('span', { class: 's-view' }, fmtNum(p.views || 0) + ' 浏览'))
      }
      if (props.mine) {
        acts.push(h('div', { class: 's-mini-group' }, [
          h('button', { class: 's-mini', type: 'button', onClick: (e) => { e.stopPropagation(); emit('del') } }, '删除'),
          h('span', { class: 's-mini is-ghost' }, '编辑')
        ]))
      }
      body.push(h('div', { class: 's-acts' }, acts))

      kids.push(h('div', { class: 's-body' }, body))

      return h('article', {
        class: ['s-card', { 'is-text': !hasImg }],
        style: { '--cd': Math.min(props.index * 45, 360) + 'ms' },
        onClick: () => { if (!props.draft) emit('open') }
      }, kids)
    }
  }
}
</script>

<style>
/* ============ MKP 社交广场（米白极简版式 + 暗色帖子卡） ============ */
.mkp-square {
  font-family: var(--mkp-font);
  background: var(--mkp-paper);
  color: var(--mkp-ink);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-size: 14px;
  line-height: 1.6;
}
.mkp-square > .s-main { flex: 1 0 auto; }
.mkp-square a { text-decoration: none; color: inherit; }
.mkp-square h1, .mkp-square h2, .mkp-square h3 { color: var(--mkp-ink); }
.mkp-square button { font-family: inherit; }

/* ---------- 1. 顶栏（纯白，与米白页面底色区分） ---------- */
.s-top {
  position: sticky;
  top: 0;
  z-index: 50;
  background: #fff;
  border-bottom: 1px solid var(--mkp-line);
  transition: box-shadow .2s var(--mkp-ease);
}
.s-top.scrolled { box-shadow: 0 8px 26px rgba(33, 29, 22, .07); }
.s-top-inner {
  width: min(1360px, 100% - 64px);
  margin-inline: auto;
  min-height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.s-top-left { display: flex; align-items: center; gap: 36px; min-width: 0; }
.s-brand { display: inline-flex; align-items: center; gap: 9px; flex: none; }
.s-mark {
  width: 30px; height: 30px; border-radius: 8px;
  background: var(--mkp-ink); color: #fff;
  display: grid; place-items: center;
  font-weight: 800; font-size: 12px; letter-spacing: .02em;
}
.s-word { font-weight: 800; font-size: 13px; letter-spacing: .06em; color: var(--mkp-ink); white-space: nowrap; }
.s-nav { display: flex; gap: 26px; }
.s-nav a {
  font-size: 14px; font-weight: 500; color: var(--mkp-ink3);
  position: relative; padding: 6px 0; white-space: nowrap;
  transition: color .2s var(--mkp-ease);
}
.s-nav a:hover, .s-nav a.is-active { color: var(--mkp-ink); font-weight: 600; }
.s-nav a.is-active::after {
  content: ""; position: absolute; left: 0; right: 0; bottom: 0;
  height: 2px; border-radius: 2px; background: var(--mkp-ink);
}
.s-top-right { display: flex; align-items: center; gap: 14px; flex: none; }
.s-search { position: relative; }
.s-search svg {
  position: absolute; left: 12px; top: 50%; transform: translateY(-50%);
  color: var(--mkp-ink3); pointer-events: none;
}
.s-search input {
  width: 230px; padding: 9px 34px;
  border: 1px solid var(--mkp-line); border-radius: 999px;
  background: #fff; font: inherit; font-size: 12.5px; color: var(--mkp-ink);
  outline: none;
  transition: width .22s var(--mkp-ease), border-color .2s var(--mkp-ease), box-shadow .2s var(--mkp-ease);
}
.s-search input::placeholder { color: var(--mkp-ink3); }
.s-search input:focus { width: 284px; border-color: var(--mkp-ink); box-shadow: 0 0 0 4px rgba(9, 9, 11, .06); }
.s-search kbd {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
  font: inherit; font-size: 10.5px; color: var(--mkp-ink3);
  border: 1px solid var(--mkp-line); background: var(--mkp-surface2);
  border-radius: 5px; padding: 1px 6px;
}
.s-btn-post {
  display: inline-flex; align-items: center; gap: 6px;
  background: var(--mkp-ink); color: #fff;
  border: none; padding: 9px 17px; border-radius: 999px;
  font-size: 12.5px; font-weight: 600; cursor: pointer;
  transition: opacity .2s var(--mkp-ease), transform .2s var(--mkp-ease);
}
.s-btn-post:hover { opacity: .88; transform: translateY(-1px); }
.s-me { display: grid; place-items: center; }
.s-me .mkp-avatar { width: 34px; height: 34px; border: 1px solid var(--mkp-line); }

/* ---------- 2. Hero 文案卡 + 3D 轮播 ---------- */
.s-hero {
  width: min(1360px, 100% - 64px);
  margin: 30px auto 0;
  display: grid;
  grid-template-columns: 4.5fr 7.5fr;
  gap: 24px;
  margin-bottom: 40px;
}
.s-hero-card {
  background: var(--mkp-surface);
  border: 1px solid var(--mkp-line);
  border-radius: 24px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 26px;
  box-shadow: var(--mkp-sh1);
}
.s-eyebrow {
  font-size: 11px; font-weight: 700; letter-spacing: .16em;
  text-transform: uppercase; color: var(--mkp-ink3);
}
.s-hero-title {
  font-size: 28px; font-weight: 800; line-height: 1.28;
  margin-top: 12px; letter-spacing: -.01em; color: var(--mkp-ink);
}
.s-hero-desc { font-size: 13px; color: var(--mkp-ink3); margin-top: 10px; }
.s-stats {
  display: flex; gap: 30px;
  margin-top: 24px; padding-top: 20px;
  border-top: 1px solid var(--mkp-line);
}
.s-stats > div { display: flex; flex-direction: column; gap: 3px; }
.s-stats b { font-size: 19px; font-weight: 800; letter-spacing: -.02em; line-height: 1.1; color: var(--mkp-ink); }
.s-stats span { font-size: 11px; color: var(--mkp-ink3); letter-spacing: .04em; }
.s-tags-label { font-size: 11px; font-weight: 600; color: var(--mkp-ink3); margin-bottom: 10px; }
.s-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.s-tag {
  font-size: 12px; color: #3f3f46;
  background: var(--mkp-paper);
  border: 1px solid rgba(0, 0, 0, .04);
  padding: 6px 14px; border-radius: 999px; cursor: pointer;
  transition: all .18s var(--mkp-ease);
}
.s-tag:hover { background: #e8e4dc; }
.s-tag[aria-pressed='true'] { background: var(--mkp-ink); border-color: var(--mkp-ink); color: #fff; }
.s-note {
  margin-top: 16px; font-size: 11.5px; color: var(--mkp-ink3);
  display: flex; align-items: center; gap: 7px;
}
.s-note-dot { width: 6px; height: 6px; border-radius: 50%; background: #d8b26a; flex: none; }
.s-note.is-live .s-note-dot { background: var(--mkp-online); }
.s-note.is-error .s-note-dot { background: var(--mkp-danger); }

/* ---------- 3. 区块头 + 工具条 ---------- */
.s-section { width: min(1360px, 100% - 64px); margin: 0 auto; }
.s-sec-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 18px; margin-bottom: 16px;
}
.s-sec-title { font-size: 18px; font-weight: 800; letter-spacing: -.01em; }
.s-tabs {
  display: flex; gap: 4px; padding: 4px;
  background: #fff; border: 1px solid var(--mkp-line); border-radius: 999px;
  flex: none;
}
.s-tab {
  padding: 6px 16px; border-radius: 999px; border: none;
  background: transparent; color: var(--mkp-ink3);
  font-size: 12px; font-weight: 500; cursor: pointer; white-space: nowrap;
  transition: all .18s var(--mkp-ease);
}
.s-tab:hover { color: var(--mkp-ink); }
.s-tab[aria-selected='true'] { background: #000; color: #fff; font-weight: 600; }
.s-tools {
  display: flex; align-items: center; justify-content: space-between;
  gap: 14px; margin-bottom: 18px; flex-wrap: wrap;
}
.s-kinds { display: flex; gap: 8px; flex-wrap: wrap; }
.s-chip {
  font-size: 12px; color: #3f3f46;
  background: #fff; border: 1px solid var(--mkp-line);
  padding: 6px 13px; border-radius: 999px; cursor: pointer;
  transition: all .18s var(--mkp-ease);
}
.s-chip:hover { border-color: #c9c2b4; }
.s-chip[aria-pressed='true'] { background: var(--mkp-ink); border-color: var(--mkp-ink); color: #fff; }
.s-sorts {
  display: flex; gap: 6px; padding: 3px;
  background: #fff; border: 1px solid var(--mkp-line); border-radius: 999px;
}
.s-sort {
  border: none; background: transparent; cursor: pointer;
  font-size: 12px; color: var(--mkp-ink3);
  padding: 5px 14px; border-radius: 999px;
  transition: all .18s var(--mkp-ease);
}
.s-sort:hover { color: var(--mkp-ink); }
.s-sort[aria-pressed='true'] { background: var(--mkp-paper); color: var(--mkp-ink); font-weight: 600; }

/* ---------- 4. 帖子网格（暗色卡片） ---------- */
.s-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.s-card {
  background: var(--mkp-dark);
  border-radius: 16px; overflow: hidden;
  display: flex; flex-direction: column;
  box-shadow: 0 4px 12px rgba(0, 0, 0, .06);
  cursor: pointer;
  animation: s-card-in .5s var(--mkp-ease) both;
  animation-delay: var(--cd, 0ms);
  transition: transform .2s var(--mkp-ease), box-shadow .2s var(--mkp-ease);
}
.s-card:hover { transform: translateY(-3px); box-shadow: 0 12px 30px rgba(28, 24, 19, .22); }
@keyframes s-card-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: none; }
}
.s-media { position: relative; height: 260px; background: var(--mkp-dark2); overflow: hidden; }
.s-media img { width: 100%; height: 100%; object-fit: cover; transition: transform .7s var(--mkp-ease); }
.s-card:hover .s-media img { transform: scale(1.05); }
.s-stamp {
  position: absolute; right: 12px; top: 12px;
  background: rgba(9, 9, 11, .72); color: #fff;
  font-size: 11px; letter-spacing: .06em; padding: 4px 10px; border-radius: 999px;
  -webkit-backdrop-filter: blur(4px); backdrop-filter: blur(4px);
}
.s-textbody {
  min-height: 260px; padding: 22px 20px;
  display: flex; align-items: center;
  background: radial-gradient(120% 100% at 20% 0%, rgba(228, 87, 46, .16), transparent 60%),
    linear-gradient(160deg, #241f19, #17140f);
}
.s-textbody p { color: #f4f4f5; font-size: 17px; font-weight: 600; line-height: 1.6; letter-spacing: -.01em; }
.s-body { padding: 16px; display: flex; flex-direction: column; gap: 12px; flex: 1; }
.s-title {
  font-size: 15px; font-weight: 700; color: #fff;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.s-meta {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; font-size: 12px; color: #a1a1aa;
}
.s-author { display: flex; align-items: center; gap: 8px; min-width: 0; }
.s-author .mkp-avatar { width: 22px; height: 22px; flex: none; }
.s-author-name { font-weight: 600; color: #e4e4e7; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.s-date { font-size: 11px; color: #71717a; white-space: nowrap; }
.s-acts { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: auto; }
.s-like {
  display: inline-flex; align-items: center; gap: 4px;
  background: rgba(255, 255, 255, .95); color: #000;
  border: none; padding: 3px 10px; border-radius: 6px;
  font-size: 11px; font-weight: 700; cursor: pointer;
  transition: transform .18s var(--mkp-ease), background .18s var(--mkp-ease), color .18s var(--mkp-ease);
}
.s-like:hover { transform: translateY(-1px); }
.s-like svg { width: 12px; height: 12px; }
.s-like.is-liked { background: var(--mkp-accent); color: #fff; }
.s-like.is-liked svg { fill: #fff; }
.s-view { font-size: 11px; color: #a1a1aa; }
.s-mini-group { margin-left: auto; display: flex; gap: 6px; }
.s-mini {
  font-size: 11px; font-weight: 600; padding: 3px 10px;
  border-radius: 6px; cursor: pointer;
  background: transparent; border: 1px solid rgba(255, 255, 255, .2); color: #e4e4e7;
  transition: all .18s var(--mkp-ease);
}
.s-mini:hover { border-color: #fff; color: #fff; }
.s-mini.is-ghost { border-style: dashed; color: #c6bbaa; }
.s-mini.is-solid { background: rgba(255, 255, 255, .95); border-color: transparent; color: #000; }

/* ---------- 空态 / 加载 ---------- */
.s-empty { text-align: center; padding: 54px 20px 64px; color: var(--mkp-ink3); }
.s-empty h3 { font-size: 16px; font-weight: 700; color: var(--mkp-ink); margin-bottom: 6px; }
.s-empty p { font-size: 13px; }
.s-empty .s-btn-ghost { margin-top: 16px; }
.s-em-ico {
  width: 84px; height: 84px; margin: 0 auto 16px; border-radius: 26px;
  display: grid; place-items: center; font-size: 34px;
  background: linear-gradient(135deg, var(--mkp-accent-soft), #fff);
  box-shadow: var(--mkp-sh1);
}
.s-spinner {
  width: 18px; height: 18px; margin: 0 auto 10px;
  border-radius: 50%;
  border: 2px solid var(--mkp-line2); border-top-color: var(--mkp-accent);
  animation: s-spin .7s linear infinite;
}
@keyframes s-spin { to { transform: rotate(360deg); } }
.s-btn-ghost {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 16px; border-radius: 11px;
  font-size: 13px; font-weight: 600; color: var(--mkp-ink2); cursor: pointer;
  border: 1px solid var(--mkp-line); background: #fff;
  transition: all .18s var(--mkp-ease);
}
.s-btn-ghost:hover { border-color: var(--mkp-ink); color: var(--mkp-ink); }

/* ---------- 页脚 ---------- */
.s-foot { border-top: 1px solid var(--mkp-line); background: #fff; margin-top: 44px; }
.s-foot-inner {
  width: min(1360px, 100% - 64px); margin-inline: auto;
  padding: 20px 0;
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px; flex-wrap: wrap;
  color: var(--mkp-ink3); font-size: 12.5px;
}

/* ---------- 发动态弹窗 ---------- */
.s-scrim {
  position: fixed; inset: 0; z-index: 200;
  background: rgba(28, 24, 19, .46);
  display: grid; place-items: center; padding: 24px;
  animation: s-fade .18s ease;
}
.s-modal {
  width: 100%; max-width: 560px; max-height: 86vh; overflow-y: auto;
  background: #fff; border-radius: 20px; padding: 20px;
  box-shadow: var(--mkp-sh2);
  animation: s-pop .2s var(--mkp-ease);
}
@keyframes s-fade { from { opacity: 0; } }
@keyframes s-pop { from { opacity: 0; transform: translateY(8px) scale(.985); } }
.s-modal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.s-modal-head h3 { font-size: 17px; font-weight: 700; }
.s-x {
  width: 32px; height: 32px; border-radius: 10px; border: none;
  background: transparent; color: var(--mkp-ink3); cursor: pointer;
  display: grid; place-items: center;
  transition: all .18s var(--mkp-ease);
}
.s-x:hover { background: var(--mkp-paper); color: var(--mkp-ink); }
.s-modal-body { display: flex; flex-direction: column; gap: 12px; }
.s-input {
  width: 100%; padding: 13px 14px; font: inherit; font-size: 14px;
  border: 1px solid var(--mkp-line); border-radius: 14px;
  background: var(--mkp-surface2); color: var(--mkp-ink);
  outline: none; transition: all .18s var(--mkp-ease);
}
.s-input:focus { border-color: rgba(228, 87, 46, .5); box-shadow: 0 0 0 3px var(--mkp-accent-soft); background: #fff; }
.s-ta { min-height: 118px; resize: vertical; line-height: 1.6; }
.s-comp-imgs { display: flex; flex-wrap: wrap; gap: 8px; }
.s-comp-img { position: relative; width: 96px; height: 96px; border-radius: 14px; overflow: hidden; }
.s-comp-img img { width: 100%; height: 100%; object-fit: cover; }
.s-comp-x {
  position: absolute; top: 4px; right: 4px; width: 22px; height: 22px;
  border-radius: 50%; border: none; cursor: pointer;
  background: rgba(0, 0, 0, .55); color: #fff;
  display: grid; place-items: center;
}
.s-upload {
  display: inline-flex; align-items: center; gap: 7px; width: fit-content;
  padding: 8px 14px; border-radius: 11px;
  font-size: 13px; font-weight: 600; color: var(--mkp-ink2); cursor: pointer;
  border: 1px dashed var(--mkp-line); background: #fff;
  transition: all .18s var(--mkp-ease);
}
.s-upload:hover:not(:disabled) { border-color: var(--mkp-accent); color: var(--mkp-accent-deep); }
.s-upload:disabled { opacity: .6; cursor: default; }
.s-modal-foot { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 18px; }
.s-btn-primary {
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--mkp-accent); color: #fff;
  border: none; padding: 9px 18px; border-radius: 12px;
  font-size: 13.5px; font-weight: 600; cursor: pointer;
  box-shadow: 0 6px 16px rgba(228, 87, 46, .26);
  transition: all .18s var(--mkp-ease);
}
.s-btn-primary:hover:not(:disabled) { background: var(--mkp-accent-deep); transform: translateY(-1px); }
.s-btn-primary:disabled { opacity: .5; cursor: not-allowed; transform: none; box-shadow: none; }

/* ---------- 响应式（桌面优先） ---------- */
@media (max-width: 1180px) {
  .s-hero { grid-template-columns: 1fr; }
  .s-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 1000px) {
  .s-nav { display: none; }
}
@media (max-width: 860px) {
  .s-top-inner, .s-hero, .s-section, .s-foot-inner { width: calc(100% - 36px); }
  .s-search input { width: 170px; }
  .s-search input:focus { width: 200px; }
  .s-grid { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .s-card { animation: none !important; opacity: 1 !important; }
}
.s-more { display: flex; justify-content: center; margin: 24px 0 10px; }
.s-more .s-btn-ghost { min-width: 132px; }
mark { background: #ffe58f; color: inherit; padding: 0 1px; border-radius: 2px; }
.s-topics { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0 0; }
.s-topic {
  border: 1px solid var(--mkp-line); background: var(--mkp-surface2, #faf9f7);
  color: var(--mkp-ink3); font: inherit; font-size: 11.5px;
  padding: 2px 9px; border-radius: 999px; cursor: pointer;
  transition: color .18s var(--mkp-ease), border-color .18s var(--mkp-ease);
}
.s-topic:hover { color: var(--mkp-ink); border-color: var(--mkp-ink); }
</style>
