<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import '../assets/mkp/mkp.css'
import { login, register } from '../api'
import { toast, toastError } from '../utils/toast'

/* ================================================================
   登录 / 注册（入口页）
   - 版式：右侧 3D 等距画板堆叠（MKP 网站展示）+ 左侧暖色玻璃登录面板
   - 配色：暖米底 #F6F3ED / 炭黑 #2D2D2D / 赭石橙 #C87A3E
   - 数据：/api/auth/login、/api/auth/register
   ================================================================ */
const router = useRouter()

const mode = ref('login')
const form = ref({ username: '', password: '', nickname: '' })
const showPwd = ref(false)
const loading = ref(false)
const error = ref('')

/* 展示画板里的宠物卡片（用项目内的本地图，避免外链） */
const cards = [
  { no: 'NO.01 BEAGLE', img: '/mkp/wall/w1-beagle.jpg', title: '比格犬 · 治愈系小贝', sub: 'MKP 官方认证宠友', likes: '2.4k', accent: false },
  { no: 'NO.02 RETRIEVER', img: '/mkp/wall/w2-retriever.jpg', title: '金毛 · 阳光暖男', sub: '高赞热帖创作者', likes: '4.8k', accent: true },
  { no: 'NO.03 TABBY', img: '/mkp/wall/w7-tabby.jpg', title: '狸花猫 · 午后糯米', sub: '精选宠物日志', likes: '3.1k', accent: false }
]

function setMode (m) {
  mode.value = m
  error.value = ''
  showPwd.value = false
}

function saveSession (data) {
  localStorage.setItem('token', data.token)
  localStorage.setItem('userId', data.userId)
  localStorage.setItem('username', data.username || '')
  localStorage.setItem('nickname', data.nickname || '')
  localStorage.setItem('avatar', data.avatar || '')
  localStorage.setItem('role', data.role)
}

async function submit () {
  error.value = ''
  if (!form.value.username || !form.value.password) {
    error.value = '请输入账号和密码'
    return
  }
  if (mode.value === 'register' && form.value.password.length < 6) {
    error.value = '密码至少 6 位'
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      const data = await login(form.value.username, form.value.password)
      saveSession(data)
      toast('登录成功！欢迎回到 MKP 社区', 'success')
      router.push(data.role === 'ADMIN' ? '/admin' : '/')
    } else {
      await register(form.value.username, form.value.password, form.value.nickname)
      const data = await login(form.value.username, form.value.password)
      saveSession(data)
      toast('注册成功！正在进入 MKP 宠物社区', 'success')
      router.push('/')
    }
  } catch (e) {
    error.value = e?.message || '操作失败'
    toastError(error.value)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="lg">
    <!-- ================= 背景：3D 等距画板堆叠 ================= -->
    <div class="scene" aria-hidden="true">
      <div class="stack">
        <!-- 背层画板 -->
        <div class="board board--back">
          <div class="board-head board-head--plain">
            <div class="dots dots--grey"><i></i><i></i><i></i></div>
            <div class="board-tag"><b>MKP</b><span>精选社交集锦</span></div>
          </div>
          <div class="back-grid"><i></i><i></i><i></i></div>
          <div class="back-bar"></div>
        </div>

        <!-- 主画板 -->
        <div class="board board--main">
          <div class="board-head">
            <div class="dots"><i class="is-red"></i><i class="is-amber"></i><i class="is-green"></i></div>
            <div class="board-tag"><b>MKP</b><span>CURATED FOR PET LOVERS</span></div>
          </div>

          <div class="board-body">
            <div class="board-hero">
              <p>CURATED FOR PET LOVERS</p>
              <h2>把治愈，收藏成一种习惯。</h2>
            </div>

            <div class="pet-grid">
              <article v-for="c in cards" :key="c.no" class="pet-card">
                <div class="pet-media">
                  <img :src="c.img" :alt="c.title" loading="lazy" />
                  <span class="pet-badge" :class="{ 'is-accent': c.accent }">{{ c.no }}</span>
                </div>
                <div class="pet-meta">
                  <div>
                    <h4>{{ c.title }}</h4>
                    <p>{{ c.sub }}</p>
                  </div>
                  <span class="pet-likes"><AppIcon name="heart" :size="12" />{{ c.likes }}</span>
                </div>
              </article>
            </div>

            <div class="board-note">探索社区里的毛孩子萌照 · 开启高品质宠物生活社区</div>
          </div>

          <div class="board-foot">
            <span>MKP Pet Community Interactive Showcase Frame</span>
            <div><span>社区规范</span><span>隐私政策</span></div>
          </div>
        </div>
      </div>
    </div>

    <!-- ================= 前景：左侧暖色玻璃面板 ================= -->
    <section class="panel">
      <header class="panel-brand">
        <h1>欢迎回到 MKP 宠物社区</h1>
        <p class="brand-sub">把治愈，收藏成一种习惯</p>
      </header>

      <div class="panel-mid">
        <div class="form-card">
          <!-- 切换 -->
          <div class="tabs" role="tablist" aria-label="登录或注册">
            <button
              type="button"
              role="tab"
              :aria-selected="mode === 'login'"
              :class="{ 'is-on': mode === 'login' }"
              @click="setMode('login')"
            >账号登录</button>
            <button
              type="button"
              role="tab"
              :aria-selected="mode === 'register'"
              :class="{ 'is-on': mode === 'register' }"
              @click="setMode('register')"
            >快速注册</button>
          </div>

          <!-- 登录 -->
          <form v-if="mode === 'login'" class="pane" @submit.prevent="submit">
            <label class="fld">
              <span>账号 / 用户名</span>
              <span class="fld-wrap">
                <i class="fld-ico"><AppIcon name="user" :size="15" /></i>
                <input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
              </span>
            </label>

            <label class="fld">
              <span>登录密码</span>
              <span class="fld-wrap">
                <i class="fld-ico">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><rect x="4.5" y="10.5" width="15" height="10" rx="2.6"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/></svg>
                </i>
                <input
                  v-model="form.password"
                  :type="showPwd ? 'text' : 'password'"
                  autocomplete="current-password"
                  placeholder="••••••••"
                />
                <button class="fld-eye" type="button" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
                  <svg v-if="!showPwd" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3l18 18"/><path d="M10.6 6.1A9.9 9.9 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-3.3 4"/><path d="M6.2 7.7A16.6 16.6 0 0 0 2.5 12S6 18 12 18a9.7 9.7 0 0 0 3.3-.6"/></svg>
                </button>
              </span>
            </label>

            <p v-if="error" class="err"><AppIcon name="x" :size="13" />{{ error }}</p>

            <button class="submit submit--dark" type="submit" :disabled="loading">
              <span>{{ loading ? '登录中…' : '安全登录' }}</span>
              <AppIcon name="chevron-right" :size="14" />
            </button>
          </form>

          <!-- 注册 -->
          <form v-else class="pane" @submit.prevent="submit">
            <label class="fld">
              <span>用户名</span>
              <span class="fld-wrap">
                <i class="fld-ico"><AppIcon name="user" :size="15" /></i>
                <input v-model="form.username" autocomplete="username" placeholder="设置登录用户名" />
              </span>
            </label>

            <label class="fld">
              <span>昵称</span>
              <span class="fld-wrap">
                <i class="fld-ico"><AppIcon name="paw" :size="15" /></i>
                <input v-model="form.nickname" placeholder="选填，默认同用户名" />
              </span>
            </label>

            <label class="fld">
              <span>设置密码</span>
              <span class="fld-wrap">
                <i class="fld-ico">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><rect x="4.5" y="10.5" width="15" height="10" rx="2.6"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/></svg>
                </i>
                <input
                  v-model="form.password"
                  :type="showPwd ? 'text' : 'password'"
                  autocomplete="new-password"
                  placeholder="至少 6 位"
                />
                <button class="fld-eye" type="button" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
                  <svg v-if="!showPwd" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3l18 18"/><path d="M10.6 6.1A9.9 9.9 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-3.3 4"/><path d="M6.2 7.7A16.6 16.6 0 0 0 2.5 12S6 18 12 18a9.7 9.7 0 0 0 3.3-.6"/></svg>
                </button>
              </span>
            </label>

            <p v-if="error" class="err"><AppIcon name="x" :size="13" />{{ error }}</p>

            <button class="submit submit--accent" type="submit" :disabled="loading">
              <span>{{ loading ? '注册中…' : '同意协议并注册' }}</span>
            </button>
          </form>
        </div>
      </div>

      <footer class="panel-foot">
        <span class="online"><i></i>10w+ 宠友在线互动</span>
        <span class="brand-foot">MKP PET COMMUNITY</span>
      </footer>
    </section>
  </div>
</template>

<style scoped>
/* ================= 页面底色 ================= */
.lg {
  --w-bg: #F6F3ED;
  --w-card: #FCFAF6;
  --w-dark: #2D2D2D;
  --w-dark-hover: #1E1E1E;
  --w-accent: #C87A3E;
  --w-accent-hover: #B2672D;
  --w-muted: #8E8A83;
  --w-line: #E8E2D8;
  --w-chip: #EFECE5;
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: var(--w-bg);
  color: var(--w-dark);
  font-family: var(--mkp-font, 'Plus Jakarta Sans', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif);
}

/* ================= 背景：等距画板堆叠 ================= */
.scene {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  perspective: 1400px; perspective-origin: 65% 50%;
}
.stack {
  position: relative; flex: none; left: 12%; top: 1%;
  width: 1180px; height: 760px;
  transform: rotateX(22deg) rotateY(-18deg) rotateZ(10deg) scale(.92);
  transform-style: preserve-3d;
  transition: transform .8s cubic-bezier(.16, 1, .3, 1);
}
.scene:hover .stack { transform: rotateX(16deg) rotateY(-12deg) rotateZ(6deg) scale(.95); }

.board { position: absolute; border-radius: 16px; }
.board--back {
  top: -112px; right: 48px; width: 85%; height: 90%;
  transform: translateZ(-20px);
  background: var(--w-card); border: 1px solid var(--w-line); opacity: .75;
  box-shadow: -20px 25px 40px rgba(45, 45, 45, .08), 0 0 0 1px rgba(255, 255, 255, .8) inset;
  padding: 24px; display: flex; flex-direction: column; justify-content: space-between;
}
.board--main {
  inset: 0; display: flex; flex-direction: column; overflow: hidden;
  background: var(--w-bg); border: 1px solid #fff;
  box-shadow: -25px 35px 50px rgba(45, 45, 45, .12), -10px 15px 25px rgba(200, 122, 62, .08), 0 0 0 1px rgba(255, 255, 255, .9) inset;
}
.board-head {
  flex: none; display: flex; align-items: center; justify-content: space-between;
  padding: 14px 24px; background: var(--w-card); border-bottom: 1px solid var(--w-line);
}
.board-head--plain { padding: 0 0 16px; border-bottom: 1px solid var(--w-line); background: transparent; }
.dots { display: flex; align-items: center; gap: 8px; }
.dots i { width: 12px; height: 12px; border-radius: 50%; background: #E2DDD3; display: block; }
.dots .is-red { background: #F87171; }
.dots .is-amber { background: #FBBF24; }
.dots .is-green { background: #34D399; }
.board-tag { display: flex; align-items: center; gap: 8px; font-size: 12px; font-weight: 700; letter-spacing: .04em; color: var(--w-dark); }
.board-tag b { background: var(--w-dark); color: #fff; font-size: 10px; font-weight: 900; padding: 2px 6px; border-radius: 4px; }

.back-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin: auto 0; }
.back-grid i { display: block; height: 128px; border-radius: 12px; background: var(--w-chip); }
.back-bar { height: 56px; border-radius: 12px; background: var(--w-dark); }

.board-body {
  flex: 1; min-height: 0; display: flex; flex-direction: column; justify-content: space-between;
  padding: 40px; background: var(--w-bg); overflow: hidden;
}
.board-hero { text-align: center; margin: 8px 0; }
.board-hero p { margin: 0 0 4px; font-size: 12px; font-weight: 700; letter-spacing: .2em; text-transform: uppercase; color: var(--w-muted); }
.board-hero h2 { margin: 0; font-size: 30px; font-weight: 800; letter-spacing: -.02em; color: var(--w-dark); }

.pet-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; margin: auto 0; padding: 0 16px; }
.pet-card {
  background: var(--w-card); border: 1px solid #fff; border-radius: 16px; padding: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, .1); transition: box-shadow .3s, transform .3s;
}
.pet-card:nth-child(2) { transform: translateY(-8px); }
.pet-card:hover { box-shadow: 0 20px 25px -5px rgba(0, 0, 0, .14); }
.pet-media { position: relative; height: 256px; border-radius: 12px; overflow: hidden; background: #F1F5F9; margin-bottom: 12px; }
.pet-media img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform .5s; }
.pet-card:hover .pet-media img { transform: scale(1.05); }
.pet-badge {
  position: absolute; top: 12px; left: 12px; padding: 4px 10px; border-radius: 999px;
  font-size: 10px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase;
  background: rgba(255, 255, 255, .82); color: var(--w-dark);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
}
.pet-badge.is-accent { background: var(--w-accent); color: #fff; }
.pet-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 0 4px; }
.pet-meta h4 { margin: 0; font-size: 13px; font-weight: 700; color: var(--w-dark); }
.pet-meta p { margin: 2px 0 0; font-size: 11px; color: var(--w-muted); }
.pet-likes { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 700; color: var(--w-accent); flex: none; }
.board-note { text-align: center; font-size: 12px; color: var(--w-muted); }
.board-foot {
  flex: none; display: flex; align-items: center; justify-content: space-between;
  padding: 14px 24px; background: var(--w-dark); color: #fff; font-size: 12px;
}
.board-foot > span { color: #A8A29E; font-size: 11px; }
.board-foot div { display: flex; align-items: center; gap: 16px; font-size: 11px; color: #D6D3D1; }

/* ================= 前景玻璃面板 ================= */
.panel {
  position: absolute; left: 0; top: 0; bottom: 0; z-index: 30;
  width: 520px; padding: 40px;
  display: flex; flex-direction: column; justify-content: space-between;
  overflow-y: auto;
  background: rgba(246, 243, 237, .72);
  backdrop-filter: blur(24px) saturate(130%);
  -webkit-backdrop-filter: blur(24px) saturate(130%);
  border-right: 1px solid rgba(255, 255, 255, .6);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, .25);
}
.panel::-webkit-scrollbar { width: 4px; }
.panel::-webkit-scrollbar-thumb { background: rgba(200, 122, 62, .2); border-radius: 4px; }

.panel-brand { margin-bottom: 16px; padding-top: 8px; }
.panel-brand h1 { margin: 0; font-size: 24px; font-weight: 900; letter-spacing: -.02em; color: var(--w-dark); }
.brand-sub { margin: 4px 0 0; font-size: 12px; color: var(--w-muted); }

.panel-mid { margin: auto 0; padding: 8px 0; }
.form-card {
  background: rgba(252, 250, 246, .9);
  backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  border: 1px solid #fff; border-radius: 16px; padding: 28px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, .1), 0 8px 10px -6px rgba(0, 0, 0, .06);
}

.tabs { display: flex; gap: 24px; padding-bottom: 12px; margin-bottom: 20px; border-bottom: 1px solid var(--w-line); }
.tabs button {
  border: none; background: none; padding: 0 0 6px; cursor: pointer;
  font-size: 15px; font-weight: 700; color: var(--w-muted);
  border-bottom: 2px solid transparent; transition: color .2s, border-color .2s;
}
.tabs button:hover { color: var(--w-dark); }
.tabs button.is-on { color: var(--w-dark); border-bottom-color: var(--w-accent); }

.pane { display: flex; flex-direction: column; gap: 14px; animation: paneIn .25s ease; }
@keyframes paneIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }

.fld { display: block; }
.fld > span:first-child { display: block; margin-bottom: 6px; font-size: 12px; font-weight: 700; color: var(--w-dark); }
.fld-wrap { position: relative; display: block; }
.fld-ico { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: var(--w-muted); display: grid; place-items: center; }
.fld-wrap input {
  width: 100%; padding: 11px 14px 11px 40px;
  background: var(--w-bg); border: 1px solid var(--w-line); border-radius: 12px;
  font: inherit; font-size: 13.5px; color: var(--w-dark); outline: none;
  transition: border-color .2s, background .2s, box-shadow .2s;
}
.fld-wrap input::placeholder { color: var(--w-muted); }
.fld-wrap input:focus { border-color: var(--w-accent); background: #fff; box-shadow: 0 0 0 3px rgba(200, 122, 62, .12); }
.fld-eye {
  position: absolute; right: 14px; top: 50%; transform: translateY(-50%);
  border: none; background: none; padding: 0; cursor: pointer;
  color: var(--w-muted); display: grid; place-items: center; transition: color .18s;
}
.fld-eye:hover { color: var(--w-dark); }

.err {
  display: flex; align-items: center; gap: 6px; margin: 0;
  padding: 9px 12px; border-radius: 12px;
  background: rgba(200, 79, 79, .1); color: #A93226; font-size: 12px; font-weight: 600;
}

.submit {
  width: 100%; margin-top: 8px; padding: 13px 20px;
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  border: none; border-radius: 12px; cursor: pointer;
  font-size: 14px; font-weight: 700; color: #fff;
  transition: background .2s, transform .15s, box-shadow .2s;
}
.submit:active:not(:disabled) { transform: scale(.985); }
.submit:disabled { opacity: .6; cursor: not-allowed; }
.submit--dark { background: var(--w-dark); box-shadow: 0 10px 18px -6px rgba(45, 45, 45, .45); }
.submit--dark:hover:not(:disabled) { background: var(--w-dark-hover); }
.submit--accent { background: var(--w-accent); box-shadow: 0 10px 18px -6px rgba(200, 122, 62, .5); }
.submit--accent:hover:not(:disabled) { background: var(--w-accent-hover); }

.panel-foot {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding-top: 12px; border-top: 1px solid var(--w-line);
  font-size: 12px; color: var(--w-muted);
}
.online { display: inline-flex; align-items: center; gap: 8px; font-weight: 500; }
.online i { width: 8px; height: 8px; border-radius: 50%; background: #10B981; display: block; }
.brand-foot { font-weight: 700; color: var(--w-dark); }

/* ================= 响应式 ================= */
@media (max-width: 1440px) {
  .stack { width: 1000px; height: 680px; }
  .pet-media { height: 216px; }
}
@media (max-width: 1180px) {
  .panel { width: 480px; padding: 32px; }
}
@media (max-width: 1024px) {
  .scene { display: none; }
  .panel { width: 100%; border-right: none; }
}
</style>
