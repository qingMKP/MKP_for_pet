<template>
  <div class="mkp-market">
    <a class="mkp-skip" href="#m-main">跳到主内容</a>

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
              :class="{ 'is-active': n.to === '/market' }"
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
              placeholder="搜索宠物、品种或关键词…"
              autocomplete="off"
              aria-label="搜索宠物、品种或关键词"
            />
            <kbd aria-hidden="true">/</kbd>
          </div>
          <button class="s-btn-post" type="button" @click="goPublish">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
            发布交易
          </button>
          <router-link class="s-me" to="/profile" :aria-label="'个人中心：' + me.nickname">
            <img class="mkp-avatar" :src="me.avatar || DEMO_AV" alt="" width="34" height="34" />
          </router-link>
        </div>
      </div>
    </header>

    <main id="m-main" class="m-main">
      <!-- ===== Hero / Banner：筛选之上的平台说明 ===== -->
      <section class="m-hero m-wrap">
        <div class="m-banner">
          <div>
            <span class="m-badge"><i></i>平台托管交易 · 履约完成才结算</span>
            <h1 class="m-title">把治愈流转，<em>让爱心不断延续。</em></h1>
            <p class="m-desc">同城寄养、闲置宠物出售。寄养由主人出资请人照顾，出售由买家付款给卖家 —— 资金全程托管，0 中介费。</p>
            <div class="m-tags">
              <span class="m-tags-label">热门推荐</span>
              <button
                v-for="t in hotTags"
                :key="t.label"
                class="m-tag"
                type="button"
                :aria-pressed="isHotActive(t)"
                @click="toggleHot(t)"
              >{{ t.label }}</button>
            </div>
          </div>
          <div class="m-stats">
            <div class="m-stats-row">
              <div><b>{{ stats.all }}</b><span>在售发布</span></div>
              <div class="m-stats-div"></div>
              <div><b>{{ stats.foster }}</b><span>找人寄养</span></div>
              <div class="m-stats-div"></div>
              <div><b>{{ stats.sale }}</b><span>找新主人</span></div>
            </div>
            <div class="m-stats-note"><i></i>{{ noteText }}</div>
          </div>
        </div>
      </section>

      <!-- ===== 交易列表 ===== -->
      <section class="m-section m-wrap">
        <div class="m-sec-head">
          <div>
            <h2 class="m-sec-title">社区精选交易</h2>
            <p class="m-sec-sub">{{ subLine }}</p>
          </div>
          <div class="m-tabs" role="tablist" aria-label="交易类型">
            <button
              v-for="t in catTabs"
              :key="t.key"
              class="m-tab"
              role="tab"
              type="button"
              :aria-selected="category === t.value"
              @click="setCategory(t.value)"
            >{{ t.label }}</button>
          </div>
        </div>

        <div class="m-tools">
          <div class="m-kinds" role="group" aria-label="宠物类型筛选">
            <button
              v-for="k in kinds"
              :key="k.key"
              class="m-chip"
              type="button"
              :aria-pressed="kind === k.key"
              @click="kind = k.key"
            >{{ k.label }}</button>
          </div>
          <div class="m-right-tools">
            <span class="m-live" :class="{ 'is-error': mode === 'error' }"><i></i>{{ liveText }}</span>
            <div class="m-sorts" role="group" aria-label="排序方式">
              <button
                v-for="st in sorts"
                :key="st.key"
                class="m-sort"
                type="button"
                :aria-pressed="sort === st.key"
                @click="sort = st.key"
              >{{ st.label }}</button>
            </div>
          </div>
        </div>

        <div v-if="loading" class="m-state">
          <div class="m-spinner"></div>
          <p>正在加载交易信息…</p>
        </div>

        <div v-else-if="mode === 'error'" class="m-state">
          <div class="m-em-ico" aria-hidden="true">🐾</div>
          <h3>数据暂时加载不出来</h3>
          <p>{{ errorMsg }}</p>
          <button class="m-btn-ghost" type="button" @click="load">重新加载</button>
        </div>

        <div v-else-if="!list.length" class="m-state">
          <div class="m-em-ico" aria-hidden="true">🐾</div>
          <h3>没有符合条件的发布</h3>
          <p>换个类型或关键词看看，也可以自己发一条</p>
          <button class="m-btn-ghost" type="button" @click="resetFilters">重置筛选</button>
        </div>

        <div v-else class="m-grid">
          <article
            v-for="(p, i) in list"
            :key="p.id"
            class="m-card"
            :style="{ '--cd': Math.min(i * 40, 360) + 'ms' }"
            tabindex="0"
            @click="openDetail(p)"
            @keyup.enter="openDetail(p)"
          >
            <div class="m-media">
              <img v-if="coverOf(p)" :src="coverOf(p)" :alt="p.petName || '宠物图片'" loading="lazy" />
              <div v-else class="m-media-empty" aria-hidden="true">🐾</div>
              <span v-if="imgsOf(p).length > 1" class="m-imgs">{{ imgsOf(p).length }} 图</span>
            </div>
            <div class="m-body">
              <div class="m-name">
                <span class="m-cat" :class="p.category === 2 ? 'is-foster' : 'is-sale'">{{ CATEGORY[p.category] }}</span>
                <span>{{ p.petName || '未命名' }}</span>
                <em>{{ p.breed || p.species || '' }}</em>
              </div>
              <span class="m-money">
                <b>{{ fmtPrice(p.price, p.priceUnit) }}</b>
                <u :class="flowOf(p).cls">{{ flowOf(p).label }}</u>
              </span>
              <div class="m-specs">
                <span v-for="sp in cardSpecs(p)" :key="sp" class="m-spec">{{ sp }}</span>
              </div>
              <div class="m-foot">
                <div class="m-owner">
                  <b>宠友 #{{ p.userId }}</b>
                  <time>{{ fmtTime(p.createTime) }}</time>
                </div>
                <div class="m-acts">
                  <button class="m-mini" type="button" @click.stop="openDetail(p)">申请</button>
                  <button class="m-mini is-solid" type="button" @click.stop="openDetail(p)">查看详情</button>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>
    </main>

    <!-- ===== 页脚 ===== -->
    <footer class="m-page-foot">
      <div class="m-page-foot-inner">
        <span>MKP PET COMMUNITY · 交易市场</span>
        <span>资金由平台托管 · 完成履约后结算</span>
      </div>
    </footer>

    <!-- ===== 详情 / 申请弹窗 ===== -->
    <div v-if="detail" class="s-scrim" @click.self="closeDetail">
      <div class="s-modal" role="dialog" aria-modal="true" aria-labelledby="d-title">
        <div class="s-modal-head">
          <h3 id="d-title">{{ detail.petName || '交易详情' }}{{ detail.breed ? ' · ' + detail.breed : '' }}</h3>
          <button class="s-x" type="button" aria-label="关闭" @click="closeDetail">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
          </button>
        </div>

        <div class="s-modal-body">
          <div
            v-if="imgsOf(detail).length"
            class="m-gallery"
            :class="'is-' + Math.min(imgsOf(detail).length, 3)"
          >
            <img v-for="(img, i) in imgsOf(detail).slice(0, 3)" :key="i" :src="img" alt="" />
          </div>

          <div class="m-d-tags">
            <span class="m-pill" :class="detail.category === 2 ? 'is-foster' : 'is-sale'">{{ CATEGORY[detail.category] }}</span>
            <span class="m-pill is-accent">{{ fmtPrice(detail.price, detail.priceUnit) }}{{ detail.category === 2 ? ' · 主人出资' : ' · 卖家收款' }}</span>
          </div>

          <div>
            <div class="m-d-name">{{ detail.petName || '未命名' }}</div>
            <div class="m-d-sub">{{ specLine(detail).join(' · ') || '—' }}</div>
          </div>

          <div class="m-flow" :class="flowOf(detail).cls">
            <span class="m-flow-ico" aria-hidden="true">{{ flowOf(detail).ico }}</span>
            <span v-if="detail.category === 2">这是<b>寄养</b>需求：发布者出钱请人照顾宠物，<b>你申请并被接受后可收到报酬</b>。</span>
            <span v-else>这是<b>出售</b>：卖家转让宠物，<b>你申请并被接受后需要付款</b>，款项由平台托管。</span>
          </div>

          <p v-if="detail.description" class="m-desc">{{ detail.description }}</p>

          <div class="m-meta">
            <div v-for="r in detailMeta" :key="r[0]" class="m-meta-row">
              <span>{{ r[0] }}</span>
              <span>{{ r[1] }}</span>
            </div>
          </div>

          <div class="m-owner-row">
            <div class="m-owner-l">
              <img class="mkp-avatar" :src="DEMO_AV" alt="" />
              <div>
                <b>宠友 #{{ detail.userId }}</b>
                <div class="m-owner-sub">{{ fmtTime(detail.createTime) }}发布</div>
              </div>
            </div>
            <span class="m-pill">{{ POSTING_STATUS[detail.status] || '' }}</span>
          </div>

          <div v-if="isMine" class="m-mine">这是你发布的单子，等待其他宠友申请</div>
          <div v-else class="m-form">
            <div class="m-field">
              <label>留言<span class="m-hint">介绍一下你的经验或时间安排</span></label>
              <textarea
                v-model="applyForm.message"
                class="m-input"
                :placeholder="detail.category === 2 ? '我家有养猫经验，可以每天上门…' : '我想给它一个稳定的家，可以约时间看狗…'"
              ></textarea>
            </div>
          </div>
        </div>

        <div class="s-modal-foot">
          <template v-if="isMine">
            <button class="m-btn-ghost2" type="button" :disabled="working" @click="offMine">下架这条</button>
          </template>
          <template v-else>
            <button class="m-btn-ghost2" type="button" @click="closeDetail">取消</button>
            <button class="m-btn-primary" type="button" :disabled="applying" @click="submitApply">{{ applying ? '提交中…' : '提交申请' }}</button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import '../assets/mkp/mkp.css'
import { getPostingList, applyPosting, offPosting } from '../api'
import { CATEGORY, GENDER, POSTING_STATUS, fmtPrice, fmtTime } from '../utils/constants'
import { toastSuccess } from '../utils/toast'

/* ================================================================
   交易市场（桌面端）
   - 版式：Banner（平台说明 + 统计）+ 工具条 + 暗色交易卡墙 + 详情/申请弹窗
   - 数据：/api/posting/list（列表）、/api/apply/posting/{id}（申请）、
           /api/posting/{id}/off（下架），全部走后端真实接口
   - 分类（全部/出售/寄养）为后端筛选，宠物类型/关键词/排序为前端筛选
   ================================================================ */
const router = useRouter()
const route = useRoute()
const DEMO_AV = '/mkp/a04.png'

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

/* ---------------- 筛选 / 状态 ---------------- */
const category = ref(null)
const kind = ref('all')
const sort = ref('new')
const query = ref(String(route.query.q || ''))
const scrolled = ref(false)
const searchRef = ref(null)

/* 顶栏搜索带过来的关键词（/market?q=xxx） */
watch(() => route.query.q, (v) => {
  query.value = String(v || '')
})

const catTabs = [
  { key: 'all', value: null, label: '全部' },
  { key: 'sale', value: 3, label: '出售' },
  { key: 'foster', value: 2, label: '寄养' }
]
const kinds = [
  { key: 'all', label: '全部类型' },
  { key: 'dog', label: '狗狗' },
  { key: 'cat', label: '猫咪' },
  { key: 'rabbit', label: '兔子' }
]
const sorts = [
  { key: 'new', label: '最新' },
  { key: 'asc', label: '价格↑' },
  { key: 'desc', label: '价格↓' }
]
/* 热门推荐：点击写入 / 清空搜索框 */
const hotTags = [
  { label: '#柯基', q: '柯基' },
  { label: '#金毛', q: '金毛' },
  { label: '#猫咪', q: '猫' },
  { label: '#小兔子', q: '兔子' }
]

/* ---------------- 数据加载（真实接口） ---------------- */
const postings = ref([])
const loading = ref(true)
const mode = ref('loading')
const errorMsg = ref('')

const imgsOf = (p) => {
  const imgs = p && p.images
  if (Array.isArray(imgs)) return imgs
  return imgs ? [imgs] : []
}
const coverOf = (p) => imgsOf(p)[0] || ''

async function load () {
  loading.value = true
  errorMsg.value = ''
  try {
    const raw = (await getPostingList({ category: category.value })) || []
    postings.value = raw
    mode.value = 'live'
  } catch (e) {
    postings.value = []
    mode.value = 'error'
    const status = e && e.response && e.response.status
    errorMsg.value = status
      ? '接口返回 ' + status + '，请稍后重试'
      : '请确认后端服务（/api）是否已启动'
  } finally {
    loading.value = false
  }
}

/* ---------------- 展示格式化 ---------------- */
const genderShort = (g) => (g === 1 ? '公' : g === 2 ? '母' : '')
const fmtAge = (m) => {
  if (m == null || m === '') return ''
  if (m < 12) return m + '个月'
  const y = Math.floor(m / 12)
  const r = m % 12
  return r ? y + '岁' + r + '个月' : y + '岁'
}
/* 品种里已含物种时不重复展示 */
const speciesOf = (p) => {
  const sp = p.species || ''
  const br = p.breed || ''
  return sp && br && (br.indexOf(sp) > -1 || sp.indexOf(br) > -1) ? '' : sp
}
const cardSpecs = (p) => [
  speciesOf(p),
  genderShort(p.gender),
  fmtAge(p.ageMonth),
  p.weightKg != null ? p.weightKg + 'kg' : ''
].filter(Boolean)
const specLine = (p) => [
  speciesOf(p),
  p.breed,
  genderShort(p.gender),
  fmtAge(p.ageMonth),
  p.weightKg != null ? p.weightKg + 'kg' : ''
].filter(Boolean)
/* 资金方向：寄养 = 主人出钱（申请人收）；出售 = 卖家收钱（申请人付） */
const flowOf = (p) => (p.category === 2
  ? { cls: 'is-earn', label: '主人出资', ico: '💰' }
  : { cls: 'is-pay', label: '卖家收款', ico: '🛒' })

/* ---------------- 派生列表与文案 ---------------- */
const list = computed(() => {
  let arr = postings.value.slice()
  if (kind.value === 'dog') arr = arr.filter((p) => (p.species || '').indexOf('狗') > -1)
  else if (kind.value === 'cat') arr = arr.filter((p) => (p.species || '').indexOf('猫') > -1)
  else if (kind.value === 'rabbit') arr = arr.filter((p) => (p.species || '').indexOf('兔') > -1)
  const q = query.value.trim().replace(/^#/, '').toLowerCase()
  if (q) {
    arr = arr.filter((p) =>
      [p.petName, p.species, p.breed, p.description].join(' ').toLowerCase().indexOf(q) > -1
    )
  }
  if (sort.value === 'asc') {
    arr.sort((a, b) => a.price - b.price)
  } else if (sort.value === 'desc') {
    arr.sort((a, b) => b.price - a.price)
  } else {
    arr.sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
  }
  return arr
})

const stats = computed(() => ({
  all: postings.value.length,
  foster: postings.value.filter((p) => p.category === 2).length,
  sale: postings.value.filter((p) => p.category === 3).length
}))

const subLine = computed(() => {
  const filtered = list.value.length !== postings.value.length
  return '共 ' + list.value.length + ' 条在售发布' +
    (filtered ? '（已筛选，全部 ' + postings.value.length + ' 条）' : '')
})

const liveText = computed(() => {
  if (mode.value === 'error') return '接口未连接'
  if (mode.value === 'live') return '实时更新'
  return '正在读取…'
})
const noteText = computed(() => {
  if (mode.value === 'error') return '接口未连接 · /api/posting 暂时不可用'
  if (mode.value === 'live') return '新发布自动出现在列表顶部 · 状态实时同步'
  return '正在读取交易数据…'
})

/* ---------------- 交互 ---------------- */
function setCategory (v) {
  if (category.value === v) return
  category.value = v
  load()
}
function toggleHot (t) {
  query.value = query.value === t.q ? '' : t.q
}
function isHotActive (t) {
  return query.value === t.q
}
function resetFilters () {
  kind.value = 'all'
  query.value = ''
  sort.value = 'new'
  if (category.value === null) {
    load()
    return
  }
  category.value = null
  load()
}
function goPublish () {
  router.push('/publish')
}

/* ---------------- 详情 / 申请 / 下架 ---------------- */
const detail = ref(null)
const applying = ref(false)
const working = ref(false)
const applyForm = ref({ message: '' })

const isMine = computed(() => !!detail.value && detail.value.userId === myId)

const detailMeta = computed(() => {
  const p = detail.value
  if (!p) return []
  const rows = [
    ['品种', [speciesOf(p), p.breed].filter(Boolean).join(' · ') || '—'],
    ['性别', GENDER[p.gender] || '未知'],
    ['年龄', fmtAge(p.ageMonth) || '—'],
    ['体重', p.weightKg != null ? p.weightKg + ' kg' : '—']
  ]
  if (p.category === 2 && p.durationMinutes) {
    rows.push(['寄养时长', Math.ceil(p.durationMinutes / 1440) + ' 天'])
  }
  rows.push(['发布时间', fmtTime(p.createTime)])
  return rows
})

function openDetail (p) {
  detail.value = p
  applyForm.value = { message: '' }
}
function closeDetail () {
  detail.value = null
}

async function submitApply () {
  const p = detail.value
  if (!p || applying.value) return
  applying.value = true
  try {
    await applyPosting(p.id, {
      requestId: crypto.randomUUID(),
      message: applyForm.value.message
    })
    toastSuccess('申请已提交')
    closeDetail()
  } catch {
    /* interceptor 已提示 */
  } finally {
    applying.value = false
  }
}

async function offMine () {
  const p = detail.value
  if (!p || working.value) return
  working.value = true
  try {
    await offPosting(p.id)
    toastSuccess('已下架')
    closeDetail()
    await load()
  } catch {
    /* interceptor 已提示 */
  } finally {
    working.value = false
  }
}

/* ---------------- 键盘 / 滚动 ---------------- */
function onKey (e) {
  const tag = (document.activeElement || {}).tagName
  if (e.key === '/' && ['INPUT', 'TEXTAREA', 'BUTTON'].indexOf(tag) === -1) {
    e.preventDefault()
    if (searchRef.value) searchRef.value.focus()
  }
  if (e.key === 'Escape' && detail.value) closeDetail()
}

let disposeScroll = null
onMounted(() => {
  load()
  const onScroll = () => { scrolled.value = window.scrollY > 4 }
  window.addEventListener('scroll', onScroll, { passive: true })
  disposeScroll = () => window.removeEventListener('scroll', onScroll)
  onScroll()
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  if (disposeScroll) disposeScroll()
  document.removeEventListener('keydown', onKey)
})
</script>

<style scoped>
/* ==========================================================================
   MKP 交易市场（桌面端）
   视觉语言与 DesktopView / SquareView 一致：--mkp-* 变量 + s-* / m-* 类
   命名空间：s-* 复用于顶栏与弹窗，m-* 为本页交易区自定义
   ========================================================================== */
.mkp-market {
  font-family: var(--mkp-font);
  background: var(--mkp-paper);
  color: var(--mkp-ink);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-size: 14px;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}
.mkp-market a { text-decoration: none; color: inherit; }
.mkp-market h1, .mkp-market h2, .mkp-market h3 { margin: 0; color: var(--mkp-ink); }
.mkp-market button { font-family: inherit; }
.mkp-market > .m-main { flex: 1 0 auto; }

/* ---------- 1. 顶栏（纯白，与页面米白底色做区分） ---------- */
.s-top {
  position: sticky; top: 0; z-index: 50; background: #fff; border-bottom: 1px solid var(--mkp-line);
  transition: box-shadow .2s var(--mkp-ease);
}
.s-top.scrolled { box-shadow: 0 8px 26px rgba(33, 29, 22, .07); }
.s-top-inner {
  width: min(1360px, 100% - 64px); margin-inline: auto; min-height: 62px;
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
}
.s-top-left { display: flex; align-items: center; gap: 36px; min-width: 0; }
.s-brand { display: inline-flex; align-items: center; gap: 9px; flex: none; }
.s-mark {
  width: 30px; height: 30px; border-radius: 8px; background: var(--mkp-ink); color: #fff;
  display: grid; place-items: center; font-weight: 800; font-size: 12px; letter-spacing: .02em;
}
.s-word { font-weight: 800; font-size: 13px; letter-spacing: .06em; white-space: nowrap; }
.s-nav { display: flex; gap: 26px; }
.s-nav a {
  font-size: 14px; font-weight: 500; color: var(--mkp-ink3); position: relative;
  padding: 6px 0; white-space: nowrap; transition: color .2s var(--mkp-ease);
}
.s-nav a:hover, .s-nav a.is-active { color: var(--mkp-ink); font-weight: 600; }
.s-nav a.is-active::after {
  content: ""; position: absolute; left: 0; right: 0; bottom: 0;
  height: 2px; border-radius: 2px; background: var(--mkp-ink);
}
.s-top-right { display: flex; align-items: center; gap: 14px; flex: none; }
.s-search { position: relative; }
.s-search svg { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--mkp-ink3); pointer-events: none; }
.s-search input {
  width: 230px; padding: 9px 34px; border: 1px solid var(--mkp-line); border-radius: 999px;
  background: #fff; font: inherit; font-size: 12.5px; color: var(--mkp-ink); outline: none;
  transition: width .22s var(--mkp-ease), border-color .2s var(--mkp-ease), box-shadow .2s var(--mkp-ease);
}
.s-search input::placeholder { color: var(--mkp-ink3); }
.s-search input:focus { width: 284px; border-color: var(--mkp-ink); box-shadow: 0 0 0 4px rgba(9, 9, 11, .06); }
.s-search kbd {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%); font: inherit;
  font-size: 10.5px; color: var(--mkp-ink3); border: 1px solid var(--mkp-line);
  background: var(--mkp-surface2); border-radius: 5px; padding: 1px 6px;
}
.s-btn-post {
  display: inline-flex; align-items: center; gap: 6px; background: var(--mkp-ink); color: #fff;
  border: none; padding: 9px 17px; border-radius: 999px; font-size: 12.5px; font-weight: 600;
  cursor: pointer; transition: opacity .2s var(--mkp-ease), transform .2s var(--mkp-ease);
}
.s-btn-post:hover { opacity: .88; transform: translateY(-1px); }
.s-me { display: grid; place-items: center; }
.s-me .mkp-avatar { width: 34px; height: 34px; border: 1px solid var(--mkp-line); }

/* ---------- 2. Hero / Banner ---------- */
.m-wrap { width: min(1360px, 100% - 64px); margin-inline: auto; }
.m-hero { margin: 26px auto 0; }
.m-banner {
  background: var(--mkp-surface); border: 1px solid var(--mkp-line); border-radius: 24px;
  padding: 26px 30px; box-shadow: var(--mkp-sh1);
  display: grid; grid-template-columns: 1.6fr 1fr; gap: 32px; align-items: stretch;
}
.m-badge {
  display: inline-flex; align-items: center; gap: 8px; background: var(--mkp-surface2);
  border: 1px solid var(--mkp-line2); padding: 5px 13px; border-radius: 999px;
  font-size: 11.5px; font-weight: 600; color: var(--mkp-ink2);
}
.m-badge i { width: 6px; height: 6px; border-radius: 50%; background: var(--mkp-online); }
.m-title { font-size: 28px; font-weight: 800; letter-spacing: -.01em; line-height: 1.28; margin-top: 12px; }
.m-title em { font-style: normal; color: var(--mkp-accent); }
.m-desc { font-size: 13px; color: var(--mkp-ink3); margin-top: 10px; max-width: 52ch; }
.m-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 20px; }
.m-tags-label { font-size: 11.5px; color: var(--mkp-ink3); font-weight: 600; margin-right: 2px; }
.m-tag {
  font-size: 12px; color: #3f3f46; background: var(--mkp-paper);
  border: 1px solid rgba(0, 0, 0, .04); padding: 6px 14px; border-radius: 999px;
  cursor: pointer; transition: all .18s var(--mkp-ease);
}
.m-tag:hover { background: #e8e4dc; }
.m-tag[aria-pressed='true'] { background: var(--mkp-ink); border-color: var(--mkp-ink); color: #fff; }
.m-stats {
  background: var(--mkp-surface2); border: 1px solid var(--mkp-line2); border-radius: 20px;
  padding: 20px 22px; display: flex; flex-direction: column; justify-content: space-between; gap: 16px;
}
.m-stats-row { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.m-stats-row > div { display: flex; flex-direction: column; gap: 2px; }
.m-stats b { font-size: 23px; font-weight: 800; letter-spacing: -.02em; line-height: 1.1; }
.m-stats span { font-size: 11px; color: var(--mkp-ink3); letter-spacing: .03em; }
.m-stats-div { width: 1px; height: 36px; background: var(--mkp-line); }
.m-stats-note {
  display: flex; align-items: center; gap: 8px; font-size: 11.5px; color: var(--mkp-ink3);
  border-top: 1px dashed var(--mkp-line); padding-top: 12px;
}
.m-stats-note i { width: 6px; height: 6px; border-radius: 50%; background: var(--mkp-online); }

/* ---------- 3. 区块头 + 工具条 ---------- */
.m-section { margin-top: 28px; }
.m-sec-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; flex-wrap: wrap; }
.m-sec-title { font-size: 19px; font-weight: 800; letter-spacing: -.01em; }
.m-sec-sub { font-size: 12px; color: var(--mkp-ink3); margin-top: 4px; }
.m-tabs {
  display: flex; gap: 4px; padding: 4px; background: #fff;
  border: 1px solid var(--mkp-line); border-radius: 999px; flex: none;
}
.m-tab {
  padding: 6px 16px; border-radius: 999px; border: none; background: transparent;
  color: var(--mkp-ink3); font-size: 12px; font-weight: 500; cursor: pointer;
  white-space: nowrap; transition: all .18s var(--mkp-ease);
}
.m-tab:hover { color: var(--mkp-ink); }
.m-tab[aria-selected='true'] { background: #000; color: #fff; font-weight: 600; }
.m-tools { display: flex; align-items: center; gap: 12px; margin: 16px 0 18px; flex-wrap: wrap; }
.m-kinds { display: flex; gap: 8px; flex-wrap: wrap; }
.m-chip {
  font-size: 12px; color: #3f3f46; background: #fff; border: 1px solid var(--mkp-line);
  padding: 6px 13px; border-radius: 999px; cursor: pointer; transition: all .18s var(--mkp-ease);
}
.m-chip:hover { border-color: #c9c2b4; }
.m-chip[aria-pressed='true'] { background: var(--mkp-ink); border-color: var(--mkp-ink); color: #fff; }
.m-right-tools { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.m-sorts { display: flex; gap: 6px; padding: 3px; background: #fff; border: 1px solid var(--mkp-line); border-radius: 999px; }
.m-sort {
  border: none; background: transparent; cursor: pointer; font-size: 12px; color: var(--mkp-ink3);
  padding: 5px 14px; border-radius: 999px; transition: all .18s var(--mkp-ease);
}
.m-sort:hover { color: var(--mkp-ink); }
.m-sort[aria-pressed='true'] { background: var(--mkp-paper); color: var(--mkp-ink); font-weight: 600; }
.m-live { display: inline-flex; align-items: center; gap: 7px; font-size: 11.5px; color: var(--mkp-ink3); }
.m-live i { width: 7px; height: 7px; border-radius: 50%; background: var(--mkp-online); box-shadow: 0 0 0 3px rgba(79, 138, 102, .16); }
.m-live.is-error i { background: var(--mkp-danger); box-shadow: 0 0 0 3px rgba(201, 79, 79, .16); }

/* ---------- 4. 交易卡（暗色，与广场帖子卡同一语言） ---------- */
.m-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; padding-bottom: 10px; }
.m-card {
  background: var(--mkp-dark); border-radius: 16px; overflow: hidden;
  display: flex; flex-direction: column; box-shadow: 0 4px 12px rgba(0, 0, 0, .06);
  cursor: pointer; animation: m-in .5s var(--mkp-ease) both; animation-delay: var(--cd, 0ms);
  transition: transform .2s var(--mkp-ease), box-shadow .2s var(--mkp-ease);
}
.m-card:hover { transform: translateY(-3px); box-shadow: 0 12px 30px rgba(28, 24, 19, .22); }
@keyframes m-in { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: none; } }
.m-media { position: relative; aspect-ratio: 4 / 3; background: var(--mkp-dark2); overflow: hidden; }
.m-media img { width: 100%; height: 100%; object-fit: cover; transition: transform .7s var(--mkp-ease); }
.m-card:hover .m-media img { transform: scale(1.05); }
.m-media-empty { width: 100%; height: 100%; display: grid; place-items: center; font-size: 30px; color: #4b463d; }
/* 业务类型标签（出售/寄养）与图片上的角标复用同一组样式 */
.m-cat { flex: none; font-size: 10.5px; font-weight: 700; letter-spacing: .02em; padding: 2px 8px; border-radius: 6px; }
.m-cat.is-sale { background: rgba(79, 138, 102, .22); color: #93D6B4; }
.m-cat.is-foster { background: rgba(228, 87, 46, .24); color: #F8AE90; }
.m-imgs {
  position: absolute; right: 12px; top: 12px; background: rgba(9, 9, 11, .6); color: #fff;
  font-size: 10.5px; padding: 3px 8px; border-radius: 999px;
  -webkit-backdrop-filter: blur(4px); backdrop-filter: blur(4px);
}
.m-body { padding: 14px 16px 15px; display: flex; flex-direction: column; gap: 9px; flex: 1; }
.m-name { font-size: 15px; font-weight: 700; color: #fff; display: flex; align-items: center; gap: 8px; min-width: 0; }
.m-name em { font-style: normal; font-size: 11.5px; font-weight: 500; color: #a1a1aa; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.m-money { display: flex; align-items: baseline; gap: 8px; }
.m-money b { font-size: 20px; font-weight: 800; letter-spacing: -.02em; color: #fff; }
.m-money u { text-decoration: none; font-size: 11.5px; padding: 2px 8px; border-radius: 999px; font-weight: 600; }
.m-money .is-pay { background: rgba(228, 87, 46, .18); color: #F7A98B; }
.m-money .is-earn { background: rgba(79, 138, 102, .2); color: #8FD0AE; }
.m-specs { display: flex; flex-wrap: wrap; gap: 6px; }
.m-spec { font-size: 11px; color: #d4d4d8; background: rgba(255, 255, 255, .07); border: 1px solid rgba(255, 255, 255, .08); padding: 2px 8px; border-radius: 6px; }
.m-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: auto; padding-top: 10px; border-top: 1px solid rgba(255, 255, 255, .08); }
.m-owner { display: flex; align-items: center; gap: 8px; min-width: 0; font-size: 11.5px; color: #a1a1aa; }
.m-owner b { font-weight: 600; color: #e4e4e7; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.m-owner time { color: #71717a; }
.m-acts { display: flex; gap: 6px; flex: none; }
.m-mini {
  font-size: 11px; font-weight: 600; padding: 4px 11px; border-radius: 7px; cursor: pointer;
  background: transparent; border: 1px solid rgba(255, 255, 255, .2); color: #e4e4e7;
  transition: all .18s var(--mkp-ease);
}
.m-mini:hover { border-color: #fff; color: #fff; }
.m-mini.is-solid { background: rgba(255, 255, 255, .95); border-color: transparent; color: #000; }
.m-mini.is-solid:hover { background: #fff; }

/* ---------- 5. 加载 / 空态 / 错误态 ---------- */
.m-state { text-align: center; padding: 60px 20px 70px; color: var(--mkp-ink3); }
.m-state h3 { font-size: 16px; font-weight: 700; margin-bottom: 6px; }
.m-state p { font-size: 13px; margin: 0; }
.m-em-ico {
  width: 84px; height: 84px; margin: 0 auto 16px; border-radius: 26px;
  display: grid; place-items: center; font-size: 34px;
  background: linear-gradient(135deg, var(--mkp-accent-soft), #fff); box-shadow: var(--mkp-sh1);
}
.m-spinner {
  width: 18px; height: 18px; margin: 0 auto 10px; border-radius: 50%;
  border: 2px solid var(--mkp-line2); border-top-color: var(--mkp-accent);
  animation: m-spin .7s linear infinite;
}
@keyframes m-spin { to { transform: rotate(360deg); } }
.m-btn-ghost {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 16px; border-radius: 11px; font-size: 13px; font-weight: 600;
  color: var(--mkp-ink2); cursor: pointer; border: 1px solid var(--mkp-line);
  background: #fff; transition: all .18s var(--mkp-ease); margin-top: 16px;
}
.m-btn-ghost:hover { border-color: var(--mkp-ink); color: var(--mkp-ink); }

/* ---------- 6. 详情 / 申请弹窗 ---------- */
.s-scrim {
  position: fixed; inset: 0; z-index: 200; background: rgba(28, 24, 19, .46);
  display: grid; place-items: center; padding: 24px; animation: m-fade .18s ease;
}
@keyframes m-fade { from { opacity: 0; } }
.s-modal {
  width: 100%; max-width: 600px; max-height: 88vh; overflow-y: auto; background: #fff;
  border-radius: 20px; padding: 20px; box-shadow: var(--mkp-sh2); animation: m-pop .2s var(--mkp-ease);
}
@keyframes m-pop { from { opacity: 0; transform: translateY(8px) scale(.985); } }
.s-modal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.s-modal-head h3 { font-size: 17px; font-weight: 700; }
.s-x {
  width: 32px; height: 32px; border-radius: 10px; border: none; background: transparent;
  color: var(--mkp-ink3); cursor: pointer; display: grid; place-items: center;
  transition: all .18s var(--mkp-ease);
}
.s-x:hover { background: var(--mkp-paper); color: var(--mkp-ink); }
.m-gallery { display: grid; gap: 6px; border-radius: 16px; overflow: hidden; }
.m-gallery.is-2 { grid-template-columns: 1fr 1fr; }
.m-gallery.is-3 { grid-template-columns: repeat(3, 1fr); }
.m-gallery img { width: 100%; height: 100%; object-fit: cover; aspect-ratio: 1 / 1; background: var(--mkp-line2); }
.m-gallery.is-1 img { aspect-ratio: 16 / 9; }
.m-gallery.is-2 img { aspect-ratio: 4 / 3; }
.m-d-tags { display: flex; flex-wrap: wrap; gap: 6px; margin: 14px 0 12px; }
.m-pill {
  display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; font-weight: 600;
  padding: 4px 10px; border-radius: 999px; background: var(--mkp-surface2);
  border: 1px solid var(--mkp-line2); color: var(--mkp-ink2);
}
.m-pill.is-foster { background: #FDF1EC; border-color: #F3D9CC; color: #A93D18; }
.m-pill.is-sale { background: #EDF5F0; border-color: #D3E6DA; color: #2F6B4F; }
.m-pill.is-accent { background: var(--mkp-accent-soft); border-color: #F3D9CC; color: var(--mkp-accent-deep); }
.m-d-name { font-size: 19px; font-weight: 800; letter-spacing: -.01em; }
.m-d-sub { font-size: 12.5px; color: var(--mkp-ink3); margin-top: 3px; }
.m-flow {
  display: flex; align-items: center; gap: 10px; margin-top: 14px; padding: 12px 14px;
  border-radius: 14px; font-size: 12.5px; line-height: 1.5; border: 1px solid;
}
.m-flow.is-pay { background: #FDF3EF; border-color: #F3D9CC; color: #8A3A18; }
.m-flow.is-earn { background: #F0F7F3; border-color: #D3E6DA; color: #28573F; }
.m-flow .m-flow-ico { font-size: 15px; }
.m-desc {
  font-size: 13.5px; line-height: 1.7; color: var(--mkp-ink2); white-space: pre-wrap;
  word-break: break-word; background: var(--mkp-surface2); border: 1px solid var(--mkp-line2);
  border-radius: 14px; padding: 13px 15px; margin-top: 14px;
}
.m-meta { display: grid; grid-template-columns: repeat(2, 1fr); gap: 0 18px; margin-top: 14px; }
.m-meta-row { display: flex; gap: 10px; font-size: 13px; padding: 8px 0; border-bottom: 1px dashed var(--mkp-line); }
.m-meta-row span:first-child { width: 58px; flex: none; color: var(--mkp-ink3); }

.m-owner-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--mkp-line2);
}
.m-owner-l { display: flex; align-items: center; gap: 9px; font-size: 12.5px; }
.m-owner-l .mkp-avatar { width: 30px; height: 30px; }
.m-owner-l b { font-weight: 700; }
.m-owner-sub { font-size: 11.5px; color: var(--mkp-ink3); }
.m-form {
  margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--mkp-line2);
  display: flex; flex-direction: column; gap: 12px;
}
.m-field label { display: block; font-size: 12.5px; font-weight: 600; margin-bottom: 6px; }
.m-field .m-hint { font-size: 11.5px; color: var(--mkp-ink3); font-weight: 400; margin-left: 6px; }
.m-input {
  width: 100%; padding: 12px 14px; font: inherit; font-size: 13.5px;
  border: 1px solid var(--mkp-line); border-radius: 14px; background: var(--mkp-surface2);
  color: var(--mkp-ink); outline: none; transition: all .18s var(--mkp-ease);
}
.m-input:focus { border-color: rgba(228, 87, 46, .5); box-shadow: 0 0 0 3px var(--mkp-accent-soft); background: #fff; }
textarea.m-input { min-height: 96px; resize: vertical; line-height: 1.6; }
.m-mine {
  padding: 14px; border-radius: 14px; background: var(--mkp-accent-soft);
  color: var(--mkp-accent-deep); font-size: 13px; text-align: center; margin-top: 14px;
}
.s-modal-foot {
  position: sticky; bottom: -20px; display: flex; align-items: center; justify-content: flex-end;
  gap: 10px; margin: 18px -20px -20px; padding: 14px 20px; background: #fff;
  border-top: 1px solid var(--mkp-line2); border-radius: 0 0 20px 20px;
}
.m-btn-primary {
  display: inline-flex; align-items: center; justify-content: center; background: var(--mkp-accent);
  color: #fff; border: none; padding: 10px 20px; border-radius: 12px; font-size: 13.5px;
  font-weight: 600; cursor: pointer; box-shadow: 0 6px 16px rgba(228, 87, 46, .26);
  transition: all .18s var(--mkp-ease);
}
.m-btn-primary:hover:not(:disabled) { background: var(--mkp-accent-deep); transform: translateY(-1px); }
.m-btn-primary:disabled { opacity: .6; cursor: not-allowed; }
.m-btn-ghost2 {
  display: inline-flex; align-items: center; padding: 10px 18px; border-radius: 12px;
  font-size: 13.5px; font-weight: 600; color: var(--mkp-ink2); cursor: pointer;
  border: 1px solid var(--mkp-line); background: #fff; transition: all .18s var(--mkp-ease);
}
.m-btn-ghost2:hover { border-color: var(--mkp-ink); color: var(--mkp-ink); }
.m-btn-ghost2:disabled { opacity: .6; cursor: not-allowed; }

/* ---------- 7. 页脚（页面级，与卡片内 .m-foot 区分命名） ---------- */
.m-page-foot { border-top: 1px solid var(--mkp-line); margin-top: 46px; }
.m-page-foot-inner {
  width: min(1360px, 100% - 64px); margin-inline: auto; min-height: 58px;
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px; font-size: 12px; color: var(--mkp-ink3);
}

/* ---------- 8. 响应式（桌面优先） ---------- */
@media (max-width: 1180px) {
  .m-banner { grid-template-columns: 1fr; }
  .m-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 1000px) {
  .s-nav { display: none; }
}
@media (max-width: 860px) {
  .s-top-inner, .m-wrap, .m-page-foot-inner { width: calc(100% - 36px); }
  .s-search input { width: 170px; }
  .s-search input:focus { width: 200px; }
  .m-grid { grid-template-columns: 1fr; }
  .m-meta { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .m-card { animation: none !important; opacity: 1 !important; }
  .m-card, .m-media img { transition: none !important; }
}
</style>
