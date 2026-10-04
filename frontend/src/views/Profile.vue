<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import MkpTopNav from '../components/MkpTopNav.vue'
import '../assets/mkp/mkp.css'
import '../assets/mkp/mkp-pages.css'
import {
  getProfile,
  updateProfile,
  uploadAvatar,
  getUserReviews,
  getMyWallet,
  adminSetWalletBalance,
  getUnreadCount
} from '../api'
import { fmtTime, fmtMoney, initials, WALLET_BIZ, WALLET_BIZ_TONE } from '../utils/constants'
import { toastSuccess, toastError } from '../utils/toast'

/* ================================================================
   个人中心（桌面端）
   - 版式：形象卡 + 资料设置 + 我的钱包（余额 / 冻结 / 合计 + 流水）+ 收到的评价
   - 数据：/api/user/profile、/api/review/user/:id、/api/wallet/me
   ================================================================ */
const profile = ref(null)
const edit = ref({ nickname: '', phone: '', avatar: '' })
const saving = ref(false)
const uploading = ref(false)
const reviews = ref([])
const avatarInput = ref(null)

/* ready：数据到位前不渲染空态，避免进页面时「空态 → 内容」闪一下 */
const ready = ref(false)
const unread = ref(0)

const myId = Number(localStorage.getItem('userId'))

/* 钱包：余额 / 冻结 / 流水 */
const wallet = ref(null)
const walletLogs = ref([])
const setBalanceInput = ref('')
const settingBalance = ref(false)
const isAdmin = computed(() => profile.value?.role === 'ADMIN')

/* 后端 tone → 页面胶囊样式 */
const TONE = { success: 'st-green', primary: 'st-blue', warning: 'st-amber', danger: 'st-rose' }
function toneOf (map, v) { return TONE[map[v] || ''] || 'st-zinc' }

async function loadWallet () {
  try {
    const w = await getMyWallet()
    wallet.value = w
    walletLogs.value = w?.logs || []
  } catch {
    /* toast 已提示 */
  }
}

async function saveBalance () {
  const raw = String(setBalanceInput.value).trim()
  if (raw === '' || Number(raw) < 0) {
    toastError('请输入不小于 0 的金额')
    return
  }
  settingBalance.value = true
  try {
    await adminSetWalletBalance({ balance: Number(raw), remark: '个人中心调试设置' })
    toastSuccess('余额已设置')
    setBalanceInput.value = ''
    await loadWallet()
  } catch {
    /* toast 已提示 */
  } finally {
    settingBalance.value = false
  }
}

async function load () {
  try {
    const p = await getProfile()
    profile.value = p
    edit.value = { nickname: p.nickname || '', phone: p.phone || '', avatar: p.avatar || '' }
  } catch {
    /* toast 已提示 */
  }
  try {
    reviews.value = (await getUserReviews(myId)) || []
  } catch {
    reviews.value = []
  }
}

async function pickAvatar (e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const data = await uploadAvatar(file)
    edit.value.avatar = data?.url || ''
    localStorage.setItem('avatar', edit.value.avatar)
    toastSuccess('头像已上传')
  } catch {
    /* toast 已提示 */
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

async function save () {
  saving.value = true
  try {
    await updateProfile({
      nickname: edit.value.nickname.trim() || profile.value?.nickname,
      phone: edit.value.phone,
      avatar: edit.value.avatar
    })
    localStorage.setItem('nickname', edit.value.nickname.trim())
    localStorage.setItem('avatar', edit.value.avatar)
    toastSuccess('资料已保存')
    await load()
  } catch {
    /* toast 已提示 */
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(), loadWallet()])
  ready.value = true
  try {
    unread.value = (await getUnreadCount()) || 0
  } catch {
    /* ignore */
  }
})
</script>

<template>
  <div class="mkp-page pg-profile">
    <div class="bg-layer" aria-hidden="true">
      <div class="blob b1"></div><div class="blob b2"></div><div class="blob b3"></div>
    </div>

    <MkpTopNav active="/profile" :show-search="false" :show-action="false" />

    <main id="pm-main" class="pm-main">
      <div class="pg-wrap">
        <!-- ===== 形象卡 ===== -->
        <section class="pf-hero glass">
          <div class="pf-avatar">
            <img v-if="edit.avatar" :src="edit.avatar" alt="" />
            <template v-else>{{ initials(edit.nickname) }}</template>
            <button class="pf-avatar-btn" type="button" aria-label="更换头像" @click="avatarInput.click()">
              <AppIcon name="image" :size="14" />
            </button>
            <input ref="avatarInput" type="file" accept="image/*" hidden @change="pickAvatar" />
          </div>

          <div class="pf-ident">
            <h1>{{ edit.nickname || '未命名用户' }}</h1>
            <p>
              <span class="mono">@{{ profile?.username || '—' }}</span>
              · 加入于 {{ profile ? fmtTime(profile.createTime) : '—' }}
            </p>
            <div class="pf-tags">
              <span class="badge" :class="isAdmin ? 'badge-blue' : 'badge-amber'">
                {{ isAdmin ? '管理员' : '普通用户' }}
              </span>
              <span class="badge badge-amber">虚拟钱包 · 与真实资金无关</span>
            </div>
          </div>

          <div class="pf-actions">
            <router-link class="btn btn-ghost pf-msg" to="/messages">
              <AppIcon name="message" :size="14" />私信
              <i v-if="unread" class="pf-msg-dot">{{ unread > 99 ? '99+' : unread }}</i>
            </router-link>
            <button class="btn btn-ghost" type="button" @click="avatarInput.click()">
              <AppIcon name="image" :size="14" />更换头像
            </button>
          </div>
        </section>

        <div class="pf-grid">
          <!-- ===== 左列 ===== -->
          <div class="pf-col">
            <section class="pf-card glass">
              <div class="pf-card-head">
                <h3>资料设置</h3>
                <span class="pf-note">昵称与头像会展示在交易与动态里</span>
              </div>

              <label class="fld"><span>昵称</span><input v-model="edit.nickname" placeholder="你的昵称" /></label>
              <label class="fld"><span>手机号</span><input v-model="edit.phone" placeholder="选填" /></label>
              <label class="fld">
                <span>加入时间</span>
                <input :value="profile ? fmtTime(profile.createTime) : '—'" readonly />
              </label>

              <button class="btn btn-dark" type="button" style="width: 100%" :disabled="saving || uploading" @click="save">
                <span v-if="saving" class="sp is-light"></span>
                {{ saving ? '保存中…' : '保存资料' }}
              </button>
            </section>

            <section class="pf-card glass">
              <div class="pf-card-head">
                <h3>我的钱包</h3>
                <span class="pf-note">{{ isAdmin ? '管理员可手动调整' : '交易托管资金' }}</span>
              </div>

              <div class="pf-stats">
                <div class="pf-stat is-blue">
                  <b>可用余额</b>
                  <strong>{{ wallet ? fmtMoney(wallet.balance) : '—' }}</strong>
                </div>
                <div class="pf-stat is-amber">
                  <b>冻结中（托管）</b>
                  <strong>{{ wallet ? fmtMoney(wallet.frozen) : '—' }}</strong>
                </div>
                <div class="pf-stat">
                  <b>合计</b>
                  <strong>{{ wallet ? fmtMoney(wallet.total) : '—' }}</strong>
                </div>
              </div>

              <div v-if="isAdmin" class="pf-setter">
                <label class="fld">
                  <span>手动设置可用余额（管理员调试）</span>
                  <div class="pf-setter-row">
                    <input v-model="setBalanceInput" type="number" min="0" step="0.01" placeholder="例如 1000" />
                    <button class="btn btn-soft" type="button" :disabled="settingBalance" @click="saveBalance">
                      <span v-if="settingBalance" class="sp"></span>
                      设置
                    </button>
                  </div>
                </label>
                <p class="fld-hint">余额由你手动设定（不接充值渠道）；每次调整都会写一条流水，说明这笔钱是人为设定的。</p>
              </div>
            </section>
          </div>

          <!-- ===== 右列 ===== -->
          <div class="pf-col">
            <section class="pf-card glass">
              <div class="pf-card-head">
                <h3>资金流水</h3>
                <span class="pf-count">{{ walletLogs.length }}</span>
              </div>

              <div v-if="ready && !walletLogs.length" class="pg-empty pf-empty">
                <div class="ico" aria-hidden="true"><AppIcon name="wallet" :size="22" /></div>
                <h4>还没有流水</h4>
                <p>支付、结算、退款都会在这里留下一条记录</p>
              </div>

              <div v-else class="pf-list">
                <article v-for="l in walletLogs" :key="l.id" class="pf-log">
                  <div class="pf-log-top">
                    <span class="st" :class="toneOf(WALLET_BIZ_TONE, l.bizType)">{{ WALLET_BIZ[l.bizType] || '其他' }}</span>
                    <b class="pf-log-amount">{{ fmtMoney(l.amount) }}</b>
                  </div>
                  <p v-if="l.remark" class="pf-log-desc">{{ l.remark }}</p>
                  <p class="pf-log-sub">
                    变动后 可用 {{ fmtMoney(l.balanceAfter) }} · 冻结 {{ fmtMoney(l.frozenAfter) }} · {{ fmtTime(l.createTime) }}
                  </p>
                </article>
              </div>
            </section>

            <section class="pf-card glass">
              <div class="pf-card-head">
                <h3>收到的评价</h3>
                <span class="pf-count">{{ reviews.length }}</span>
              </div>

              <div v-if="ready && !reviews.length" class="pg-empty pf-empty">
                <div class="ico" aria-hidden="true"><AppIcon name="star" :size="22" /></div>
                <h4>还没有评价</h4>
                <p>完成交易后，对方可以给你评价</p>
              </div>

              <div v-else class="pf-list">
                <article v-for="r in reviews" :key="r.id" class="pf-review">
                  <div class="pf-stars">
                    <span v-for="n in 5" :key="n" class="pf-star" :class="{ 'is-on': n <= r.rating }">
                      <AppIcon name="star" :size="14" />
                    </span>
                    <span class="pf-review-time">{{ fmtTime(r.createTime) }}</span>
                  </div>
                  <p v-if="r.content" class="pf-review-text">{{ r.content }}</p>
                  <p v-else class="pf-review-text is-muted">对方没有留下文字评价</p>
                </article>
              </div>
            </section>
          </div>
        </div>
      </div>
    </main>

    <footer class="m-page-foot">
      <div class="m-page-foot-inner">
        <span>MKP PET COMMUNITY · 个人中心</span>
        <span>资料仅用于社区展示，钱包为平台虚拟余额</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ---------- 形象卡 ---------- */
.pf-hero { padding: 26px 28px; display: flex; align-items: center; gap: 22px; }
.pf-avatar {
  position: relative; width: 88px; height: 88px; border-radius: 50%; flex: none;
  display: grid; place-items: center; font-size: 30px; font-weight: 800; color: #52525b;
  background: linear-gradient(135deg, rgba(254, 243, 199, .95), rgba(219, 234, 254, .95));
  border: 1.5px solid rgba(255, 255, 255, .9);
}
.pf-avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.pf-avatar-btn {
  position: absolute; right: -2px; bottom: -2px; width: 30px; height: 30px; border-radius: 50%;
  border: 2px solid #fff; background: #18181b; color: #fff; display: grid; place-items: center;
  cursor: pointer; transition: transform .2s var(--mkp-ease);
}
.pf-avatar-btn:hover { transform: translateY(-1px); }
.pf-ident { flex: 1; min-width: 0; }
.pf-ident h1 { font-size: 24px; font-weight: 900; letter-spacing: -.02em; color: #18181b; }
.pf-ident p { margin: 6px 0 0; font-size: 12px; color: #71717a; }
.pf-ident .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.pf-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.pf-actions { display: flex; align-items: center; gap: 10px; flex: none; }
.pf-msg { position: relative; }
.pf-msg-dot {
  min-width: 18px; height: 18px; padding: 0 5px; border-radius: 999px;
  background: var(--pg-rose); color: #fff; font-size: 10.5px; font-weight: 700; font-style: normal;
  display: inline-grid; place-items: center;
}

/* ---------- 两列 ---------- */
.pf-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; margin-top: 18px; align-items: start; }
.pf-col { display: flex; flex-direction: column; gap: 18px; min-width: 0; }
.pf-card { padding: 22px; display: flex; flex-direction: column; gap: 16px; }
.pf-card-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; padding-bottom: 14px; border-bottom: 1px solid rgba(0, 0, 0, .06); }
.pf-card-head h3 { font-size: 15px; font-weight: 800; color: #18181b; }
.pf-note { font-size: 11px; color: #a1a1aa; }
.pf-count { font-size: 11.5px; font-weight: 700; color: #71717a; background: rgba(255, 255, 255, .8); border: 1px solid rgba(0, 0, 0, .06); border-radius: 999px; padding: 2px 10px; }

/* ---------- 钱包 ---------- */
.pf-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.pf-stat { padding: 13px 14px; border-radius: 16px; background: rgba(255, 255, 255, .72); border: 1px solid rgba(255, 255, 255, .9); }
.pf-stat b { display: block; font-size: 11px; font-weight: 600; color: #71717a; }
.pf-stat strong { display: block; margin-top: 7px; font-size: 18px; font-weight: 900; letter-spacing: -.02em; color: #18181b; font-variant-numeric: tabular-nums; }
.pf-stat.is-blue strong { color: var(--pg-blue-deep); }
.pf-stat.is-amber strong { color: #b45309; }
.pf-setter { padding-top: 14px; border-top: 1px solid rgba(0, 0, 0, .06); }
.pf-setter-row { display: flex; gap: 8px; }
.pf-setter-row input { flex: 1; }
.fld input[readonly] { background: rgba(255, 255, 255, .45); color: #a1a1aa; }

/* ---------- 列表 ---------- */
.pf-list { display: flex; flex-direction: column; gap: 10px; min-height: 132px; max-height: 320px; overflow-y: auto; padding-right: 2px; }
.pf-empty { padding: 26px 12px; }
.pf-empty .ico { width: 56px; height: 56px; border-radius: 18px; margin-bottom: 10px; font-size: 20px; }
.pf-empty h4 { font-size: 13px; }
.pf-empty p { font-size: 11.5px; }
.pf-log { padding: 13px 15px; border-radius: 16px; background: rgba(255, 255, 255, .72); border: 1px solid rgba(255, 255, 255, .9); }
.pf-log-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.pf-log-amount { font-size: 14px; font-weight: 900; color: #18181b; font-variant-numeric: tabular-nums; }
.pf-log-desc { margin: 8px 0 0; font-size: 12px; color: #52525b; line-height: 1.6; word-break: break-word; }
.pf-log-sub { margin: 6px 0 0; font-size: 11px; color: #a1a1aa; }
.pf-review { padding: 13px 15px; border-radius: 16px; background: rgba(255, 255, 255, .72); border: 1px solid rgba(255, 255, 255, .9); }
.pf-stars { display: flex; align-items: center; gap: 2px; }
.pf-star { color: #e4e4e7; line-height: 0; }
.pf-star.is-on { color: var(--pg-amber); }
.pf-review-time { margin-left: auto; font-size: 11px; color: #a1a1aa; }
.pf-review-text { margin: 8px 0 0; font-size: 12.5px; line-height: 1.65; color: #3f3f46; word-break: break-word; }
.pf-review-text.is-muted { color: #a1a1aa; }

/* ---------- 响应式 ---------- */
@media (max-width: 1080px) {
  .pf-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 860px) {
  .pf-hero { flex-wrap: wrap; }
  .pf-actions { width: 100%; }
  .pf-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
