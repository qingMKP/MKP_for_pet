<script setup>
import { ref, onMounted } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import MkpTopNav from '../components/MkpTopNav.vue'
import '../assets/mkp/mkp.css'
import '../assets/mkp/mkp-pages.css'
import {
  getMyTradeOrders,
  payTradeOrder,
  startTradeOrder,
  finishTradeOrder,
  completeTradeOrder,
  cancelTradeOrder,
  refundTradeOrder,
  getReceivedApplies,
  getSentApplies,
  acceptApply,
  rejectApply,
  withdrawApply,
  submitReview
} from '../api'
import {
  CATEGORY,
  ORDER_STATUS,
  ORDER_STATUS_TONE,
  APPLY_STATUS,
  APPLY_STATUS_TONE,
  fmtPrice,
  fmtTime,
  fmtDateTime
} from '../utils/constants'
import { toastSuccess } from '../utils/toast'

/* ================================================================
   我的交易（桌面端）
   - 版式：Pill 胶囊标签页（订单 / 收到的申请 / 发出的申请）+ 订单卡列表 + 评价弹窗
   - 数据：/api/trade/my、/api/apply/received、/api/apply/sent 及各类状态流转接口
   ================================================================ */
const myId = Number(localStorage.getItem('userId'))

const tab = ref('orders')
const orders = ref([])
const received = ref([])
const sent = ref([])
const ready = ref(false)
/* 载入提示只在慢请求（>250ms）时才出现，避免本地接口快时内容闪一下 */
const loading = ref(false)

/* 正在进行中的操作（防连点：支付/退款这类按钮每点一次就是一次真实请求） */
const pendingKey = ref('')

const reviewTarget = ref(null)
const reviewForm = ref({ rating: 5, content: '' })

/* 状态色调：后端 tone → 页面胶囊样式 */
const TONE = { success: 'st-green', primary: 'st-blue', warning: 'st-amber', danger: 'st-rose' }
function toneOf (map, status) {
  return TONE[map[status] || ''] || 'st-zinc'
}
/* 缩略图底色：按订单状态冷暖区分 */
function thumbTone (status) {
  if (status === 4) return 'green'
  if (status === 5 || status === 6) return 'rose'
  if (status === 1 || status === 2) return 'blue'
  return 'amber'
}
function btnClass (tone) {
  if (tone === 'primary') return 'btn-dark'
  if (tone === 'danger') return 'btn-danger'
  if (tone === 'soft') return 'btn-soft'
  return 'btn-ghost'
}

function isPayer (o) {
  return o.payerId === myId
}

function orderActions (o) {
  const acts = []
  if (o.status === 0 && isPayer(o)) {
    acts.push({ label: '支付', tone: 'primary', fn: () => payTradeOrder(o.id) })
    acts.push({ label: '取消', tone: 'ghost', fn: () => cancelTradeOrder(o.id) })
  }
  if (o.status === 1) {
    if (o.category === 2 && !isPayer(o)) acts.push({ label: '开始履约', tone: 'primary', fn: () => startTradeOrder(o.id) })
    if (o.category === 2 && isPayer(o)) acts.push({ label: '退款', tone: 'danger', fn: () => refundTradeOrder(o.id) })
    if (o.category === 3 && isPayer(o)) acts.push({ label: '确认完成', tone: 'primary', fn: () => completeTradeOrder(o.id) })
  }
  if (o.status === 2 && !isPayer(o)) acts.push({ label: '完成履约', tone: 'primary', fn: () => finishTradeOrder(o.id) })
  if (o.status === 3 && isPayer(o)) acts.push({ label: '确认结算', tone: 'primary', fn: () => completeTradeOrder(o.id) })
  if (o.status === 4) acts.push({ label: '评价', tone: 'soft', fn: () => openReview(o) })
  return acts
}

async function act (a, key) {
  if (pendingKey.value) return
  pendingKey.value = key
  try {
    await a.fn()
    toastSuccess('操作成功')
    await loadAll()
  } catch {
    /* toast 已提示 */
  } finally {
    pendingKey.value = ''
  }
}

async function applyAct (fn, key) {
  if (pendingKey.value) return
  pendingKey.value = key
  try {
    await fn()
    toastSuccess('操作成功')
    await loadAll()
  } catch {
    /* toast 已提示 */
  } finally {
    pendingKey.value = ''
  }
}

function openReview (o) {
  reviewTarget.value = o
  reviewForm.value = { rating: 5, content: '' }
}

async function submitReviewForm () {
  if (!reviewTarget.value) return
  try {
    await submitReview({
      orderId: reviewTarget.value.id,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    toastSuccess('评价已提交')
    reviewTarget.value = null
  } catch {
    /* toast 已提示 */
  }
}

function amountText (o) {
  const n = Number(o.amount)
  if (!n) return '免费'
  return fmtPrice(o.amount, 1)
}

async function loadAll () {
  const slow = setTimeout(() => { loading.value = true }, 250)
  try {
    const [o, r, s] = await Promise.all([getMyTradeOrders(), getReceivedApplies(), getSentApplies()])
    orders.value = o || []
    received.value = r || []
    sent.value = s || []
  } catch {
    /* toast 已提示 */
  } finally {
    clearTimeout(slow)
    loading.value = false
    ready.value = true
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="mkp-page pg-trades">
    <div class="bg-layer" aria-hidden="true">
      <div class="blob b1"></div><div class="blob b2"></div><div class="blob b3"></div>
    </div>

    <MkpTopNav active="/trades" :show-search="false" :show-action="false" />

    <main id="pm-main" class="pm-main">
      <div class="pg-wrap">
        <div class="orders-card glass">
          <div class="orders-head">
            <div>
              <h2>我的交易</h2>
              <p>订单、申请与履约进度，资金由平台托管、履约完成后结算</p>
            </div>
            <div class="pill-tabs" role="tablist" aria-label="交易视图">
              <button class="pill" :class="{ 'is-on': tab === 'orders' }" type="button" @click="tab = 'orders'">订单 {{ orders.length }}</button>
              <button class="pill" :class="{ 'is-on': tab === 'received' }" type="button" @click="tab = 'received'">收到的申请 {{ received.length }}</button>
              <button class="pill" :class="{ 'is-on': tab === 'sent' }" type="button" @click="tab = 'sent'">发出的申请 {{ sent.length }}</button>
            </div>
          </div>

          <div v-if="loading" class="pg-empty">
            <div class="ico" aria-hidden="true"><span class="sp"></span></div>
            <h4>正在同步交易数据…</h4>
            <p>订单、申请与履约进度马上就好</p>
          </div>

          <!-- 快请求：用与空态同尺寸的隐形占位，避免循环“空态→转圈→内容”的闪动 -->
          <div v-else-if="!ready" class="pg-empty pg-hold" aria-hidden="true">
            <div class="ico">🐾</div>
            <h4>暂无订单</h4>
            <p>去交易市场逛逛，或发布需求等待申请</p>
          </div>

          <template v-else>
            <!-- ===== 订单 ===== -->
            <div v-if="tab === 'orders'" class="order-list">
              <div v-if="!orders.length" class="pg-empty">
                <div class="ico" aria-hidden="true">🐾</div>
                <h4>暂无订单</h4>
                <p>去交易市场逛逛，或发布需求等待申请</p>
              </div>

              <article v-for="o in orders" :key="o.id" class="order-sub">
                <div class="thumb" :class="thumbTone(o.status)">
                  <img v-if="o.postingImage" :src="o.postingImage" alt="" />
                  <span v-else aria-hidden="true">🐾</span>
                </div>

                <div class="order-main">
                  <div class="order-title">
                    <b>{{ o.postingPetName || '未命名' }}</b>
                    <span class="st" :class="toneOf(ORDER_STATUS_TONE, o.status)">{{ ORDER_STATUS[o.status] }}</span>
                  </div>
                  <p class="order-meta">
                    {{ CATEGORY[o.category] }} · {{ isPayer(o) ? '我支付' : '我收款' }} · 订单号 <span class="mono">{{ o.orderNo }}</span>
                  </p>
                  <p class="order-meta">
                    创建于 {{ fmtDateTime(o.createTime) }}<template v-if="o.payTime"> · 支付于 {{ fmtDateTime(o.payTime) }}</template>
                  </p>
                  <p v-if="o.paymentNo || o.refundNo" class="order-meta">
                    <template v-if="o.paymentNo">支付单 <span class="mono">{{ o.paymentNo }}</span></template>
                    <template v-if="o.refundNo"> · 退款单 <span class="mono">{{ o.refundNo }}</span></template>
                  </p>
                </div>

                <div class="order-side">
                  <span class="amount" :class="{ amber: o.status === 0 }">{{ amountText(o) }}</span>
                  <button
                    v-for="a in orderActions(o)"
                    :key="a.label"
                    class="btn btn-sm"
                    :class="btnClass(a.tone)"
                    type="button"
                    :disabled="!!pendingKey"
                    @click="act(a, o.id + ':' + a.label)"
                  >
                    <span v-if="pendingKey === o.id + ':' + a.label" class="sp"></span>
                    {{ a.label }}
                  </button>
                </div>
              </article>
            </div>

            <!-- ===== 收到的申请 ===== -->
            <div v-else-if="tab === 'received'" class="order-list">
              <div v-if="!received.length" class="pg-empty">
                <div class="ico" aria-hidden="true">🐾</div>
                <h4>暂无收到的申请</h4>
                <p>发布交易单后，申请会出现在这里</p>
              </div>

              <article v-for="a in received" :key="a.id" class="order-sub">
                <div class="thumb" :class="a.status === 1 ? 'green' : a.status === 0 ? 'amber' : 'zinc'">
                  <img v-if="a.postingImage" :src="a.postingImage" alt="" />
                  <span v-else aria-hidden="true">🐾</span>
                </div>

                <div class="order-main">
                  <div class="order-title">
                    <b>{{ a.postingPetName || '未命名' }}</b>
                    <span class="st" :class="toneOf(APPLY_STATUS_TONE, a.status)">{{ APPLY_STATUS[a.status] }}</span>
                  </div>
                  <p class="order-meta">
                    {{ CATEGORY[a.postingCategory] }} · 申请人 #{{ a.applicantId }} · {{ fmtTime(a.createTime) }}
                  </p>
                  <p v-if="a.message" class="order-msg">{{ a.message }}</p>
                </div>

                <div class="order-side">
                  <span class="amount" :class="{ zinc: a.postingPrice == null }">{{ fmtPrice(a.postingPrice, a.postingPriceUnit) }}</span>
                  <template v-if="a.status === 0">
                    <button
                      class="btn btn-sm btn-dark"
                      type="button"
                      :disabled="!!pendingKey"
                      @click="applyAct(() => acceptApply(a.id), a.id + ':accept')"
                    >
                      <span v-if="pendingKey === a.id + ':accept'" class="sp"></span>
                      接受
                    </button>
                    <button
                      class="btn btn-sm btn-ghost"
                      type="button"
                      :disabled="!!pendingKey"
                      @click="applyAct(() => rejectApply(a.id), a.id + ':reject')"
                    >
                      拒绝
                    </button>
                  </template>
                </div>
              </article>
            </div>

            <!-- ===== 发出的申请 ===== -->
            <div v-else class="order-list">
              <div v-if="!sent.length" class="pg-empty">
                <div class="ico" aria-hidden="true">🐾</div>
                <h4>暂无发出的申请</h4>
                <p>在交易市场看到合适的单子，就去申请吧</p>
              </div>

              <article v-for="a in sent" :key="a.id" class="order-sub">
                <div class="thumb" :class="a.status === 1 ? 'green' : a.status === 0 ? 'amber' : 'zinc'">
                  <img v-if="a.postingImage" :src="a.postingImage" alt="" />
                  <span v-else aria-hidden="true">🐾</span>
                </div>

                <div class="order-main">
                  <div class="order-title">
                    <b>{{ a.postingPetName || '未命名' }}</b>
                    <span class="st" :class="toneOf(APPLY_STATUS_TONE, a.status)">{{ APPLY_STATUS[a.status] }}</span>
                  </div>
                  <p class="order-meta">
                    {{ CATEGORY[a.postingCategory] }} · 我的申请 · {{ fmtTime(a.createTime) }}
                  </p>
                  <p v-if="a.message" class="order-msg">{{ a.message }}</p>
                </div>

                <div class="order-side">
                  <span class="amount" :class="{ zinc: a.postingPrice == null }">{{ fmtPrice(a.postingPrice, a.postingPriceUnit) }}</span>
                  <button
                    v-if="a.status === 0"
                    class="btn btn-sm btn-ghost"
                    type="button"
                    :disabled="!!pendingKey"
                    @click="applyAct(() => withdrawApply(a.id), a.id + ':withdraw')"
                  >
                    <span v-if="pendingKey === a.id + ':withdraw'" class="sp"></span>
                    撤回
                  </button>
                </div>
              </article>
            </div>
          </template>
        </div>
      </div>
    </main>

    <!-- ===== 评价弹窗 ===== -->
    <div v-if="reviewTarget" class="pg-scrim" @click.self="reviewTarget = null">
      <div class="pg-modal" role="dialog" aria-modal="true" aria-labelledby="rv-title">
        <h3 id="rv-title">评价订单</h3>
        <div class="fld">
          <span>评分</span>
          <div class="stars">
            <button
              v-for="n in 5"
              :key="n"
              class="star"
              :class="{ 'is-on': n <= reviewForm.rating }"
              type="button"
              aria-label="评分"
              @click="reviewForm.rating = n"
            >
              <AppIcon name="star" :size="22" />
            </button>
          </div>
        </div>
        <div class="fld mt">
          <span>评价内容</span>
          <textarea v-model="reviewForm.content" rows="3" placeholder="说说这次交易的体验…"></textarea>
        </div>
        <div class="pg-modal-foot">
          <button class="btn btn-soft" type="button" @click="reviewTarget = null">取消</button>
          <button class="btn btn-dark" type="button" @click="submitReviewForm">提交评价</button>
        </div>
      </div>
    </div>

    <footer class="m-page-foot">
      <div class="m-page-foot-inner">
        <span>MKP PET COMMUNITY · 我的交易</span>
        <span>资金由平台托管 · 完成履约后结算</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.orders-card { padding: 28px; display: flex; flex-direction: column; gap: 20px; }
.orders-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; padding-bottom: 18px; border-bottom: 1px solid rgba(0, 0, 0, .06); }
.orders-head h2 { font-size: 20px; font-weight: 900; letter-spacing: -.01em; color: #18181b; }
.orders-head p { margin: 4px 0 0; font-size: 12px; color: #71717a; }
.order-list { display: flex; flex-direction: column; gap: 14px; }
.order-sub { display: flex; align-items: center; gap: 16px; padding: 18px; border-radius: 16px; background: rgba(255, 255, 255, .75); border: 1px solid rgba(255, 255, 255, .9); box-shadow: 0 4px 16px rgba(0, 0, 0, .02); transition: box-shadow .2s var(--mkp-ease); }
.order-sub:hover { box-shadow: 0 10px 26px rgba(0, 0, 0, .06); }
.thumb { width: 64px; height: 64px; border-radius: 16px; display: grid; place-items: center; font-size: 26px; flex: none; border: 1px solid rgba(0, 0, 0, .05); overflow: hidden; }
.thumb.amber { background: rgba(254, 243, 199, .9); border-color: rgba(253, 230, 138, .9); }
.thumb.blue { background: rgba(219, 234, 254, .9); border-color: rgba(191, 219, 254, .9); }
.thumb.rose { background: rgba(255, 228, 230, .9); border-color: rgba(254, 205, 211, .9); }
.thumb.green { background: rgba(220, 252, 231, .9); border-color: rgba(187, 247, 208, .9); }
.thumb.zinc { background: rgba(244, 244, 245, .9); border-color: rgba(228, 228, 231, .9); }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.order-main { min-width: 0; flex: 1; }
.order-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.order-title b { font-size: 15px; font-weight: 800; color: #18181b; }
.order-meta { margin: 6px 0 0; font-size: 11.5px; color: #71717a; }
.order-meta .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; color: #a1a1aa; }
.order-msg { margin: 8px 0 0; font-size: 12px; color: #52525b; background: rgba(255, 255, 255, .7); border-left: 2px solid rgba(0, 0, 0, .12); border-radius: 0 8px 8px 0; padding: 8px 12px; }
.order-side { display: flex; align-items: center; gap: 10px; flex: none; }
.amount { font-size: 20px; font-weight: 900; letter-spacing: -.02em; color: #18181b; }
.amount.amber { color: #b45309; }
.amount.zinc { color: #3f3f46; }
.pg-empty .sp { margin-bottom: 10px; }
.pg-empty .ico .sp { margin-bottom: 0; }
.pg-hold { visibility: hidden; }
.stars { display: flex; gap: 4px; }
.star { border: none; background: transparent; color: #d4d4d8; cursor: pointer; padding: 2px; line-height: 0; transition: color .18s var(--mkp-ease); }
.star.is-on { color: #f59e0b; }
.fld.mt { margin-top: 14px; }
@media (max-width: 860px) {
  .order-sub { flex-direction: column; align-items: flex-start; }
  .order-side { width: 100%; justify-content: space-between; }
}
</style>
