<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import '../assets/mkp/mkp.css'
import { login, register } from '../api'

/* ================================================================
   登录 / 注册（入口页）
   - 版式：左侧品牌与卖点（大标题 + 勾选清单 + 黑色 CTA），右侧白色表单卡
   - 配色：暖米底 + 墨黑 + 琥珀橙，右侧蓝色渐变呼应站点黄蓝主色
   ================================================================ */
const router = useRouter()

const mode = ref('login')
const form = ref({ username: '', password: '', nickname: '' })
const loading = ref(false)
const error = ref('')

const points = [
  '交易资金由平台托管，履约完成才结算',
  '社交广场分享毛孩子日常，随时找同好',
  '智能养宠顾问，喂养与健康问题随时问'
]

function setMode (m) {
  mode.value = m
  error.value = ''
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
    error.value = '请输入用户名和密码'
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
      router.push(data.role === 'ADMIN' ? '/admin' : '/')
    } else {
      await register(form.value.username, form.value.password, form.value.nickname)
      const data = await login(form.value.username, form.value.password)
      saveSession(data)
      router.push('/')
    }
  } catch (e) {
    error.value = e?.message || '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="lg">
    <!-- 背景：暖米底 + 环形水印 + 右侧蓝色渐层 -->
    <div class="lg-bg" aria-hidden="true">
      <span class="lg-swirl"></span>
      <span class="lg-amber"></span>
      <span class="lg-blue"></span>
      <span class="lg-dot"></span>
      <span class="lg-leaf"></span>
    </div>

    <div class="lg-wrap">
      <!-- ===== 左：品牌与卖点 ===== -->
      <section class="lg-intro">
        <div class="lg-brand">
          <span class="lg-mark">MKP</span>
          <span class="lg-word">PET COMMUNITY</span>
        </div>

        <h1 class="lg-title">宠物社区<br><em>登录 · 注册</em></h1>
        <p class="lg-sub">同城寄养、闲置交易、社交广场与智能养宠顾问，一个账号全都能用。</p>

        <ul class="lg-points">
          <li v-for="t in points" :key="t">
            <span class="lg-check"><AppIcon name="check" :size="12" /></span>{{ t }}
          </li>
        </ul>

        <button class="lg-cta" type="button" @click="setMode('register')">
          免费注册
          <AppIcon name="chevron-right" :size="16" />
        </button>
        <p class="lg-foot">
          已有账号？
          <button type="button" @click="setMode('login')">直接登录</button>
        </p>
      </section>

      <!-- ===== 右：表单卡 ===== -->
      <section class="lg-panel">
        <div class="lg-card">
          <div class="lg-tabs" role="tablist" aria-label="登录或注册">
            <button
              role="tab"
              type="button"
              :aria-selected="mode === 'login'"
              :class="{ 'is-on': mode === 'login' }"
              @click="setMode('login')"
            >登录</button>
            <button
              role="tab"
              type="button"
              :aria-selected="mode === 'register'"
              :class="{ 'is-on': mode === 'register' }"
              @click="setMode('register')"
            >注册</button>
          </div>

          <h2 class="lg-card-title">{{ mode === 'login' ? '欢迎回来' : '创建你的账号' }}</h2>
          <p class="lg-card-sub">
            {{ mode === 'login' ? '登录后继续你的领养、交易与陪伴。' : '注册即可发布动态、上架宠物与申请寄养。' }}
          </p>

          <form class="lg-form" @submit.prevent="submit">
            <label class="lg-fld">
              <span>用户名</span>
              <input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
            </label>

            <label v-if="mode === 'register'" class="lg-fld">
              <span>昵称</span>
              <input v-model="form.nickname" placeholder="选填，默认同用户名" />
            </label>

            <label class="lg-fld">
              <span>密码</span>
              <input
                v-model="form.password"
                type="password"
                :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                :placeholder="mode === 'login' ? '请输入密码' : '至少 6 位'"
              />
            </label>

            <p v-if="error" class="lg-error">
              <AppIcon name="x" :size="13" />{{ error }}
            </p>

            <button class="lg-submit" type="submit" :disabled="loading">
              <span v-if="loading" class="lg-sp"></span>
              {{ loading ? '请稍候…' : (mode === 'login' ? '登录' : '注册并登录') }}
            </button>
          </form>

          <p class="lg-agree">继续即表示同意 <b>《社区公约》</b> 与 <b>《交易规则》</b></p>
        </div>

        <p class="lg-tip">演示账号：admin / 123456</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 页面与背景 ---------- */
.lg {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(102deg, #FAF1DC 0%, #F7E8C6 42%, #E8EDFA 68%, #C6D7F8 100%);
  color: #1A1712;
  font-family: var(--mkp-font, "Inter", "PingFang SC", "Microsoft YaHei", sans-serif);
}
.lg-bg { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.lg-swirl {
  position: absolute; left: -18%; top: -16%; width: 820px; height: 820px; border-radius: 50%;
  background: repeating-radial-gradient(circle at 50% 50%, rgba(255, 176, 32, .17) 0 26px, rgba(255, 176, 32, 0) 26px 54px);
  -webkit-mask-image: radial-gradient(circle at 50% 50%, #000 32%, transparent 68%);
  mask-image: radial-gradient(circle at 50% 50%, #000 32%, transparent 68%);
  transform: rotate(-10deg);
}
.lg-amber {
  position: absolute; left: -150px; bottom: -190px; width: 480px; height: 480px; opacity: .45;
  background: #FFC53D; border-radius: 46% 54% 62% 38% / 52% 44% 56% 48%;
}
.lg-blue {
  position: absolute; top: -10%; right: -12%; bottom: -10%; width: 46%;
  background:
    radial-gradient(120% 66% at 100% 0%, rgba(255, 255, 255, .38), transparent 62%),
    linear-gradient(90deg, rgba(59, 107, 240, 0) 0%, rgba(59, 107, 240, .26) 32%, rgba(37, 99, 235, .46) 70%, rgba(29, 78, 216, .32) 100%);
}
.lg-dot {
  position: absolute; right: 30%; top: 18%; width: 16px; height: 16px; border-radius: 50%;
  background: #F0A22E; opacity: .9;
}
.lg-leaf {
  position: absolute; right: 21%; bottom: 9%; width: 46px; height: 26px;
  background: #7FA86B; opacity: .75;
  border-radius: 60% 40% 55% 45% / 65% 60% 40% 35%;
  transform: rotate(-24deg);
}

/* ---------- 两列骨架 ---------- */
.lg-wrap {
  position: relative; z-index: 1;
  width: min(1200px, 100% - 80px);
  display: grid; grid-template-columns: minmax(0, 1fr) 428px;
  align-items: center; gap: 64px;
  padding: 56px 0;
}

/* ---------- 左：品牌与卖点 ---------- */
.lg-intro { max-width: 470px; }
.lg-brand { display: flex; align-items: center; gap: 13px; }
.lg-mark {
  width: 48px; height: 48px; border-radius: 50%; flex: none;
  background: #1A1712; color: #fff; display: grid; place-items: center;
  font-size: 13px; font-weight: 800; letter-spacing: .01em;
}
.lg-word { font-size: 15px; font-weight: 800; letter-spacing: .14em; color: #1A1712; }
.lg-title { margin: 34px 0 0; font-size: 48px; line-height: 1.12; font-weight: 900; letter-spacing: -.025em; color: #17140F; }
.lg-title em { font-style: normal; color: #E08A00; }
.lg-sub { margin: 18px 0 0; max-width: 430px; font-size: 13px; line-height: 1.85; color: #6B6353; }
.lg-points { list-style: none; margin: 28px 0 0; padding: 0; display: flex; flex-direction: column; gap: 13px; }
.lg-points li { display: flex; align-items: center; gap: 11px; font-size: 13px; font-weight: 600; color: #3F3A2E; }
.lg-check { width: 22px; height: 22px; border-radius: 50%; flex: none; background: #1A1712; color: #fff; display: grid; place-items: center; }
.lg-cta {
  margin-top: 32px; display: inline-flex; align-items: center; gap: 8px;
  padding: 15px 32px; border: none; border-radius: 14px; cursor: pointer;
  background: #1A1712; color: #fff; font-size: 14px; font-weight: 700;
  box-shadow: 0 14px 30px rgba(26, 23, 18, .22);
  transition: transform .18s cubic-bezier(.2, .8, .2, 1), box-shadow .18s;
}
.lg-cta:hover { transform: translateY(-2px); box-shadow: 0 18px 36px rgba(26, 23, 18, .26); }
.lg-foot { margin: 16px 0 0; font-size: 12.5px; color: #6B6353; }
.lg-foot button {
  border: none; background: none; padding: 0; cursor: pointer;
  font: inherit; font-weight: 700; color: #C97A00; text-decoration: underline; text-underline-offset: 3px;
}

/* ---------- 右：表单卡 ---------- */
.lg-panel { display: flex; flex-direction: column; align-items: center; gap: 14px; }
.lg-card {
  width: 100%; padding: 28px 30px 22px; border-radius: 26px;
  background: #fff; border: 1px solid rgba(255, 255, 255, .9);
  box-shadow: 0 34px 70px rgba(48, 36, 10, .18), 0 2px 6px rgba(0, 0, 0, .04);
}
.lg-tabs {
  display: inline-flex; gap: 4px; padding: 4px; border-radius: 999px;
  background: #F5EFE1; border: 1px solid rgba(0, 0, 0, .04);
}
.lg-tabs button {
  border: none; background: transparent; border-radius: 999px; cursor: pointer;
  padding: 8px 24px; font-size: 12.5px; font-weight: 700; color: #7A7059;
  transition: all .22s cubic-bezier(.4, 0, .2, 1);
}
.lg-tabs button:hover { color: #1A1712; }
.lg-tabs button.is-on { background: #1A1712; color: #fff; box-shadow: 0 4px 12px rgba(0, 0, 0, .14); }
.lg-card-title { margin: 22px 0 0; font-size: 21px; font-weight: 900; letter-spacing: -.02em; color: #17140F; }
.lg-card-sub { margin: 8px 0 0; font-size: 12.5px; line-height: 1.7; color: #857C69; }
.lg-form { display: flex; flex-direction: column; gap: 14px; margin-top: 22px; }
.lg-fld { display: block; }
.lg-fld > span { display: block; margin-bottom: 7px; font-size: 11.5px; font-weight: 700; color: #4A4438; }
.lg-fld input {
  width: 100%; padding: 13px 15px; border-radius: 14px;
  border: 1px solid #E7DFCC; background: #FBF8F1;
  font: inherit; font-size: 13px; color: #1A1712; outline: none;
  transition: border-color .2s, box-shadow .2s, background .2s;
}
.lg-fld input::placeholder { color: #A79E8A; }
.lg-fld input:focus { border-color: #FFB020; background: #fff; box-shadow: 0 0 0 4px rgba(255, 176, 32, .18); }
.lg-error {
  display: flex; align-items: center; gap: 6px; margin: 0;
  padding: 9px 12px; border-radius: 12px;
  background: rgba(201, 79, 79, .1); color: #A93226; font-size: 12px; font-weight: 600;
}
.lg-submit {
  margin-top: 2px; width: 100%; display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  padding: 14px 20px; border: none; border-radius: 999px; cursor: pointer;
  background: linear-gradient(180deg, #FFC23D, #F0A22E); color: #241B06;
  font-size: 13.5px; font-weight: 800; letter-spacing: .01em;
  box-shadow: 0 12px 26px rgba(240, 162, 46, .34);
  transition: transform .18s cubic-bezier(.2, .8, .2, 1), filter .18s, opacity .18s;
}
.lg-submit:hover:not(:disabled) { transform: translateY(-1px); filter: brightness(1.03); }
.lg-submit:disabled { opacity: .62; cursor: not-allowed; }
.lg-sp {
  width: 14px; height: 14px; border-radius: 50%;
  border: 2px solid rgba(36, 27, 6, .28); border-top-color: #241B06;
  animation: lg-spin .7s linear infinite;
}
@keyframes lg-spin { to { transform: rotate(360deg); } }
.lg-agree { margin: 18px 0 0; font-size: 11px; color: #A79E8A; }
.lg-agree b { font-weight: 700; color: #7A7059; }
.lg-tip { font-size: 11.5px; color: #7A7059; background: rgba(255, 255, 255, .6); border-radius: 999px; padding: 6px 14px; }

/* ---------- 响应式 ---------- */
@media (max-width: 1080px) {
  .lg-wrap { grid-template-columns: minmax(0, 1fr) 400px; gap: 44px; width: min(1000px, 100% - 56px); }
  .lg-title { font-size: 40px; }
}
@media (max-width: 900px) {
  .lg { align-items: flex-start; }
  .lg-wrap { grid-template-columns: minmax(0, 1fr); gap: 32px; padding: 44px 0 56px; width: min(560px, 100% - 40px); }
  .lg-intro { max-width: none; }
  .lg-title { font-size: 34px; }
  .lg-points { display: none; }
  .lg-blue { width: 60%; opacity: .6; }
}
</style>
