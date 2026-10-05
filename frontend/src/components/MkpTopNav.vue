<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { getUnreadCount } from '../api'
import '../assets/mkp/mkp.css'

/* ==========================================================================
   MKP 统一顶栏（交易市场 / 社交广场 / 我的交易 / 宠物服务 / 发布交易 共用）
   - 导航节点固定 5 个，不含「发布交易」；右侧「发布交易」按钮跳 /publish
   - 搜索框回车 → 去交易市场按关键词查（/market?q=）
   ========================================================================== */
const props = defineProps({
  active: { type: String, default: '' },
  /* 这三个开关给「我的交易 / 智能助手 / 宠物服务」用：只留 品牌 + 导航 + 头像 */
  showSearch: { type: Boolean, default: true },
  showAction: { type: Boolean, default: true },
  /* 宽版内容页（如智能助手 1440 宽）需要导航同宽对齐 */
  wide: { type: Boolean, default: false }
})

const router = useRouter()
const DEMO_AV = '/mkp/a04.png'

const me = ref({
  nickname: localStorage.getItem('nickname') || '我',
  avatar: localStorage.getItem('avatar') || ''
})

const nav = [
  { to: '/square', label: '社交广场' },
  { to: '/market', label: '交易市场' },
  { to: '/trades', label: '我的交易' },
  { to: '/assistant', label: '智能助手' },
  { to: '/pet-service', label: '宠物服务' }
]

const q = ref('')
const scrolled = ref(false)
const unread = ref(0)
const searchRef = ref(null)

function submitSearch () {
  const t = q.value.trim()
  router.push(t ? { path: '/market', query: { q: t } } : '/market')
}

function onKey (e) {
  const tag = (document.activeElement || {}).tagName
  if (e.key === '/' && props.showSearch && ['INPUT', 'TEXTAREA'].indexOf(tag) === -1) {
    e.preventDefault()
    if (searchRef.value) searchRef.value.focus()
  }
}

let disposeScroll = null
onMounted(async () => {
  const onScroll = () => { scrolled.value = window.scrollY > 4 }
  window.addEventListener('scroll', onScroll, { passive: true })
  disposeScroll = () => window.removeEventListener('scroll', onScroll)
  onScroll()
  document.addEventListener('keydown', onKey)
  try {
    unread.value = (await getUnreadCount()) || 0
  } catch {
    /* 未读数拿不到就不显示红点 */
  }
})
onBeforeUnmount(() => {
  if (disposeScroll) disposeScroll()
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <header class="s-top" :class="{ scrolled, 'is-wide': wide }">
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
            :class="{ 'is-active': n.to === active }"
          >{{ n.label }}</router-link>
        </nav>
      </div>

      <div class="s-top-right">
        <div v-if="showSearch" class="s-search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
          <input
            ref="searchRef"
            v-model="q"
            type="search"
            placeholder="搜索宠物、品种或关键词…"
            autocomplete="off"
            aria-label="搜索宠物、品种或关键词"
            @keyup.enter="submitSearch"
          />
          <kbd aria-hidden="true">/</kbd>
        </div>
        <button v-if="showAction" class="s-btn-post" type="button" @click="router.push('/publish')">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
          发布交易
        </button>
        <slot name="actions" />
        <router-link class="s-msg" to="/messages" :aria-label="'私信' + (unread > 0 ? '（未读 ' + unread + '）' : '')">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a8 8 0 0 1-8 8H4l2.4-2.9A8 8 0 1 1 21 12Z"/></svg>
          <i v-if="unread > 0" class="s-msg-dot" aria-hidden="true">{{ unread > 99 ? '99+' : unread }}</i>
        </router-link>
        <router-link class="s-me" to="/profile" :aria-label="'个人中心：' + me.nickname">
          <img class="mkp-avatar" :src="me.avatar || DEMO_AV" alt="" width="34" height="34" />
        </router-link>
      </div>
    </div>
  </header>
</template>

<style scoped>
.s-top { position: sticky; top: 0; z-index: 50; background: #fff; border-bottom: 1px solid var(--mkp-line); transition: box-shadow .2s var(--mkp-ease); }
.s-top.scrolled { box-shadow: 0 8px 26px rgba(33, 29, 22, .07); }
.s-top.is-wide .s-top-inner { width: min(1440px, 100% - 64px); }
.s-top-inner { width: min(1360px, 100% - 64px); margin-inline: auto; min-height: 62px; display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.s-top-left { display: flex; align-items: center; gap: 36px; min-width: 0; }
.s-brand { display: inline-flex; align-items: center; gap: 9px; flex: none; text-decoration: none; color: inherit; }
.s-mark { width: 30px; height: 30px; border-radius: 8px; background: var(--mkp-ink); color: #fff; display: grid; place-items: center; font-weight: 800; font-size: 12px; letter-spacing: .02em; }
.s-word { font-weight: 800; font-size: 13px; letter-spacing: .06em; white-space: nowrap; }
.s-nav { display: flex; gap: 26px; }
.s-nav a { font-size: 14px; font-weight: 500; color: var(--mkp-ink3); position: relative; padding: 6px 0; white-space: nowrap; text-decoration: none; transition: color .2s var(--mkp-ease); }
.s-nav a:hover, .s-nav a.is-active { color: var(--mkp-ink); font-weight: 600; }
.s-nav a.is-active::after { content: ""; position: absolute; left: 0; right: 0; bottom: 0; height: 2px; border-radius: 2px; background: var(--mkp-ink); }
.s-top-right { display: flex; align-items: center; gap: 14px; flex: none; }
.s-search { position: relative; }
.s-search svg { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--mkp-ink3); pointer-events: none; }
.s-search input { width: 230px; padding: 9px 34px; border: 1px solid var(--mkp-line); border-radius: 999px; background: #fff; font: inherit; font-size: 12.5px; color: var(--mkp-ink); outline: none; transition: width .22s var(--mkp-ease), border-color .2s var(--mkp-ease), box-shadow .2s var(--mkp-ease); }
.s-search input::placeholder { color: var(--mkp-ink3); }
.s-search input:focus { width: 284px; border-color: var(--mkp-ink); box-shadow: 0 0 0 4px rgba(9, 9, 11, .06); }
.s-search kbd { position: absolute; right: 10px; top: 50%; transform: translateY(-50%); font: inherit; font-size: 10.5px; color: var(--mkp-ink3); border: 1px solid var(--mkp-line); background: var(--mkp-surface2); border-radius: 5px; padding: 1px 6px; }
.s-btn-post { display: inline-flex; align-items: center; gap: 6px; background: var(--mkp-ink); color: #fff; border: none; padding: 9px 17px; border-radius: 999px; font-size: 12.5px; font-weight: 600; cursor: pointer; transition: opacity .2s var(--mkp-ease), transform .2s var(--mkp-ease); }
.s-btn-post:hover { opacity: .88; transform: translateY(-1px); }
.s-msg { position: relative; width: 36px; height: 36px; border-radius: 12px; display: grid; place-items: center; color: var(--mkp-ink2); transition: background .18s var(--mkp-ease), color .18s var(--mkp-ease); }
.s-msg:hover { background: var(--mkp-surface2); color: var(--mkp-ink); }
.s-msg .s-msg-dot {
  position: absolute; top: 1px; right: 1px; min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: 999px; background: var(--mkp-danger); color: #fff; font-style: normal;
  font-size: 10px; font-weight: 700; line-height: 1; display: grid; place-items: center;
  box-shadow: 0 0 0 2px #fff;
}
.s-me { display: grid; place-items: center; text-decoration: none; }
.s-me .mkp-avatar { width: 34px; height: 34px; border: 1px solid var(--mkp-line); }
@media (max-width: 1180px) { .s-top.is-wide .s-top-inner { width: calc(100% - 40px); } }
@media (max-width: 1000px) { .s-nav { display: none; } }
@media (max-width: 860px) {
  .s-top-inner { width: calc(100% - 36px); }
  .s-search input { width: 160px; }
  .s-search input:focus { width: 190px; }
}
</style>
