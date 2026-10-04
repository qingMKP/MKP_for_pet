<template>
  <div class="mkp-desk">
    <a class="mkp-skip" href="#d-main">跳到主内容</a>

    <header class="d-top" :class="{ scrolled }" ref="topRef">
      <router-link class="d-brand" to="/desktop" aria-label="MKP 桌面">
        <span class="d-brand-mark" aria-hidden="true">MKP</span>
      </router-link>

      <nav class="d-nav" aria-label="主导航">
        <router-link v-for="n in nav" :key="n.to" :to="n.to">{{ n.label }}</router-link>
      </nav>

      <div class="d-actions">
        <router-link class="d-icon" to="/square" aria-label="去社交广场搜索">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        </router-link>
        <router-link class="d-icon" to="/messages" aria-label="私信（未读 {{ unread }}）">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a8 8 0 0 1-8 8H4l2.4-2.9A8 8 0 1 1 21 12Z"/></svg>
          <span v-if="unread > 0" class="d-dot" aria-hidden="true"></span>
        </router-link>
        <router-link class="d-icon" to="/profile" aria-label="个人中心：{{ user.nickname }}">
          <img class="mkp-avatar d-avatar" :src="user.avatar || demoAvatar" alt="" />
        </router-link>
      </div>
    </header>

    <main id="d-main">
      <!-- 主视觉：3D 弧形胶片画廊 -->
      <FilmWall />

      <!-- 主视觉下方：居中的两行主张 -->
      <section class="d-slogan" data-mkp-reveal style="--d:150ms">
        <span class="d-slogan-halo" aria-hidden="true"></span>
        <div class="d-slogan-inner">
          <p class="d-eyebrow">Curated for Pet Lovers</p>
          <h1>把治愈，收藏成一种习惯。</h1>
        </div>
      </section>

      <!-- 功能入口行（原四张大卡收敛为一行，保留全部入口） -->
      <section class="d-quick mkp-container" aria-label="功能入口" data-mkp-group>
        <router-link
          v-for="(q, i) in cards"
          :key="q.to"
          class="dq"
          :to="q.to"
          data-mkp-reveal
          :style="{ '--d': i * 60 + 'ms' }"
        >
          <img class="dq-img" :src="q.img" :alt="q.alt" loading="lazy" />
          <span class="dq-tx">
            <b>{{ q.label }}</b>
            <small>{{ q.sub }}</small>
          </span>
          <span class="dq-arr" aria-hidden="true">→</span>
        </router-link>
      </section>
    </main>

    <footer class="d-bar">
      <div class="d-bar-inner">
        <div class="d-bar-left">
          <span class="d-bar-mark" aria-hidden="true">MKP</span>
        </div>
        <div class="d-bar-right">
          <span class="d-bar-txt">欢迎回来，{{ user.nickname }}</span>
          <span class="d-bar-sep" aria-hidden="true"></span>
          <span class="d-stack" aria-label="社区示例成员">
            <img class="mkp-avatar" src="/mkp/home/a04.png" alt="社区成员头像" />
            <img class="mkp-avatar" src="/mkp/home/a05.jpg" alt="社区成员头像" />
            <img class="mkp-avatar" src="/mkp/home/a06.png" alt="社区成员头像" />
          </span>
          <span class="d-bar-txt">curated by MKP Team</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import '../assets/mkp/mkp.css'
import { initMkpReveal } from '../utils/mkpReveal'
import { getUnreadCount } from '../api'
import FilmWall from '../components/FilmWall.vue'

const demoAvatar = '/mkp/home/a04.png'
const user = ref({
  nickname: localStorage.getItem('nickname') || '用户',
  avatar: localStorage.getItem('avatar') || ''
})
const unread = ref(0)
const scrolled = ref(false)

const nav = [
  { to: '/square', label: '社交广场' },
  { to: '/market', label: '交易市场' },
  { to: '/trades', label: '我的交易' },
  { to: '/assistant', label: '智能助手' },
  { to: '/pet-service', label: '宠物服务' }
]

const cards = [
  { to: '/square', label: '社交广场', sub: '去广场逛逛', img: '/mkp/home/p06.jpg', alt: '夕阳下遛狗的温馨照片' },
  { to: '/market', label: '交易市场', sub: '寄养与出售信息', img: '/mkp/home/p08.jpg', alt: '可爱的宠物照片' },
  { to: '/assistant', label: '智能助手', sub: '向助手提问', img: '/mkp/home/p09.jpg', alt: '毛茸茸的宠物照片' },
  { to: '/pet-service', label: '宠物服务', sub: '预约服务', img: '/mkp/home/p05.jpg', alt: '与宠物在户外玩耍的照片' }
]

let dispose
let onScroll

onMounted(async () => {
  dispose = initMkpReveal(document.querySelector('.mkp-desk'))
  onScroll = () => { scrolled.value = window.scrollY > 6 }
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
  try {
    const n = await getUnreadCount()
    unread.value = n || 0
  } catch {
    /* 未读可失败，忽略 */
  }
})

onBeforeUnmount(() => {
  dispose && dispose()
  window.removeEventListener('scroll', onScroll)
})
</script>

<style>
/* ============ MKP Desktop（Samara 编辑风） ============ */
.mkp-desk {
  font-family: var(--mkp-font);
  background: var(--mkp-paper);
  color: var(--mkp-ink);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-size: 15px;
  line-height: 1.6;
}
.mkp-desk a { text-decoration: none; color: inherit; }
/* 黑色品牌条吸底：主内容区占满剩余高度 */
.mkp-desk > main { flex: 1 0 auto; }

.d-top {
  position: sticky; top: 0; z-index: 50;
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  padding: 14px max(24px, calc((100vw - 1280px) / 2 + 20px));
  background: #FFFFFF;
  border-bottom: 1px solid var(--mkp-line);
  transition: background .18s var(--mkp-ease), box-shadow .18s var(--mkp-ease);
}
.d-top.scrolled {
  box-shadow: 0 1px 0 var(--mkp-line), 0 10px 28px rgba(33, 29, 22, .06);
}
.d-brand { display: inline-flex; align-items: center; gap: 11px; }
.d-brand-mark {
  width: 34px; height: 34px; border-radius: 9px;
  background: var(--mkp-ink); color: #fff;
  display: grid; place-items: center;
  font-weight: 800; font-size: 13px;
}

.d-nav { display: flex; gap: 26px; align-items: center; }
.d-nav a {
  font-size: 14.5px; color: var(--mkp-ink2); position: relative; padding: 6px 0;
  transition: color .18s var(--mkp-ease);
}
.d-nav a::after {
  content: ""; position: absolute; left: 0; bottom: 0; height: 1.5px; width: 0;
  background: var(--mkp-ink); transition: width .22s var(--mkp-ease);
}
.d-nav a:hover { color: var(--mkp-ink); }
.d-nav a:hover::after { width: 100%; }

.d-actions { display: flex; align-items: center; gap: 6px; }
.d-icon {
  width: 38px; height: 38px; border-radius: 12px;
  display: grid; place-items: center;
  color: var(--mkp-ink2); position: relative;
  transition: background .18s var(--mkp-ease), color .18s var(--mkp-ease);
}
.d-icon:hover { background: #fff; color: var(--mkp-ink); box-shadow: var(--mkp-sh1); }
.d-icon .d-dot {
  position: absolute; top: 7px; right: 8px;
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--mkp-accent);
  box-shadow: 0 0 0 2px var(--mkp-paper);
}
.d-avatar { width: 30px; height: 30px; }

.d-slogan {
  position: relative; text-align: center;
  padding: clamp(2px, 1.2vh, 12px) 24px clamp(20px, 3.6vh, 42px);
}
.d-slogan-halo {
  position: absolute; left: 50%; top: calc(50% - 6px); width: min(820px, 88vw); height: 260px;
  transform: translate(-50%, -50%); border-radius: 50%; pointer-events: none;
  background: radial-gradient(closest-side, rgba(255, 255, 255, .92), rgba(255, 255, 255, 0));
}
.d-slogan-inner { position: relative; }
.d-eyebrow {
  margin: 0; font-size: 12px; font-weight: 600; letter-spacing: .34em;
  text-transform: uppercase; color: var(--mkp-ink3);
}
.d-slogan h1 {
  margin: 14px 0 0; font-weight: 600; font-size: clamp(1.9rem, 3.9vw, 2.9rem);
  line-height: 1.18; letter-spacing: .015em; color: var(--mkp-ink); text-wrap: balance;
}

/* ---------- 功能入口行（主视觉下方的紧凑入口） ---------- */
.d-quick {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px;
  padding: 0 0 4px;
}
.dq {
  display: flex; align-items: center; gap: 12px;
  background: #fff; border: 1px solid var(--mkp-line);
  border-radius: 18px; padding: 10px 14px 10px 10px;
  box-shadow: var(--mkp-sh1);
  transition: transform .18s var(--mkp-ease), box-shadow .18s var(--mkp-ease), border-color .18s var(--mkp-ease);
}
.dq:hover { transform: translateY(-2px); box-shadow: var(--mkp-sh2); border-color: #D6CEC0; }
.dq-img { width: 52px; height: 52px; border-radius: 12px; object-fit: cover; flex: none; }
.dq-tx { display: flex; flex-direction: column; min-width: 0; line-height: 1.25; }
.dq-tx b { font-size: 14.5px; font-weight: 650; }
.dq-tx small { color: var(--mkp-ink3); font-size: 12px; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dq-arr { margin-left: auto; color: var(--mkp-ink3); transition: transform .18s var(--mkp-ease), color .18s var(--mkp-ease); }
.dq:hover .dq-arr { transform: translateX(3px); color: var(--mkp-accent-deep); }

.d-bar { background: var(--mkp-dark); color: #fff; margin-top: 18px; }
.d-bar-inner {
  width: min(1320px, 100% - 32px); margin-inline: auto;
  display: flex; align-items: center; justify-content: space-between; gap: 20px;
  padding: 22px 0;
}
.d-bar-left { display: flex; align-items: center; gap: 14px; }
.d-bar-mark {
  width: 40px; height: 40px; border-radius: 10px;
  background: #fff; color: var(--mkp-dark);
  display: grid; place-items: center;
  font-weight: 800; font-size: 15px;
}
.d-bar-right { display: flex; align-items: center; gap: 14px; color: rgba(255, 255, 255, .78); font-size: 13px; }
.d-stack { display: flex; }
.d-stack img {
  width: 30px; height: 30px; border: 2px solid var(--mkp-dark); margin-left: -9px;
}
.d-stack img:first-child { margin-left: 0; }
.d-bar-sep { width: 1px; height: 26px; background: rgba(255, 255, 255, .22); }

@media (max-width: 1180px) {
  .d-quick { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 900px) {
  .d-nav { display: none; }
}
@media (max-width: 620px) {
  .d-quick { grid-template-columns: 1fr; }
  .d-slogan h1 { font-size: clamp(1.7rem, 7vw, 2.2rem); }
  .d-bar-right .d-bar-txt, .d-bar-sep { display: none; }
  .d-top { padding: 12px 16px; }
}
</style>



