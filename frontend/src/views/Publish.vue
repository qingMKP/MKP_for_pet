<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import MkpTopNav from '../components/MkpTopNav.vue'
import '../assets/mkp/mkp.css'
import '../assets/mkp/mkp-pages.css'
import { createPosting, uploadPostingImages } from '../api'
import { PRICE_UNIT } from '../utils/constants'
import { toastError } from '../utils/toast'

/* ================================================================
   发布交易（桌面端）
   - 版式：左侧交易类型选择卡 + 右侧发布表单（液态玻璃 + 黄蓝色块）
   - 数据：POST /api/posting 创建发布单；POST /api/posting/image/upload 上传图片
   - 两类业务：出售(3) 卖家收钱；寄养(2) 主人出钱，两类都必须定价
   ================================================================ */
const router = useRouter()

const form = ref({
  category: 3,
  petName: '',
  species: '猫',
  breed: '',
  gender: 0,
  ageMonth: '',
  weightKg: '',
  price: '',
  priceUnit: 1,
  startDate: '',
  endDate: '',
  description: '',
  images: []
})

const uploading = ref(false)
const publishing = ref(false)
const fileRef = ref(null)

const isFoster = computed(() => form.value.category === 2)

/* 寄养开始时间至少要提前这么多分钟（与后端 mkp.trade.min-start-lead-minutes 同口径） */
const MIN_START_LEAD_MINUTES = 5

/** 返回 '' 表示通过，否则返回要提示给用户的原因 */
function checkStartLead () {
  if (!isFoster.value || !form.value.startDate) return ''
  const start = new Date(form.value.startDate)
  const diffMinutes = (start.getTime() - Date.now()) / 60000
  if (diffMinutes <= 0) return '开始寄养时间已过，请重新选择开始时间'
  if (diffMinutes <= MIN_START_LEAD_MINUTES) {
    return '开始寄养时间距现在不足 ' + MIN_START_LEAD_MINUTES + ' 分钟，请至少提前 ' + MIN_START_LEAD_MINUTES + ' 分钟发布'
  }
  return ''
}

const categoryHint = computed(() => (isFoster.value
  ? '寄养：我的宠物需要人照顾，我出钱请人照顾。请填写你愿意支付的寄养费。'
  : '出售：我要卖掉这只宠物，买家付款给我。售价必填。'))

const durationMinutes = computed(() => {
  if (!isFoster.value) return null
  if (!form.value.startDate || !form.value.endDate) return null
  const s = new Date(form.value.startDate)
  const e = new Date(form.value.endDate)
  const diff = Math.round((e - s) / 60000)
  return diff > 0 ? diff : null
})

function setCategory (c) {
  if (form.value.category === c) return
  form.value.category = c
  form.value.priceUnit = c === 2 ? 2 : 1
  if (c === 3) {
    form.value.startDate = ''
    form.value.endDate = ''
  }
}

function toDateTime (v) {
  if (!v) return null
  return /:\d{2}$/.test(v) ? v + ':00' : v
}

function pickImages () {
  if (fileRef.value) fileRef.value.click()
}

async function onPick (e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  uploading.value = true
  try {
    const urls = (await uploadPostingImages(files)) || []
    form.value.images.push(...urls)
  } catch {
    /* interceptor 已提示 */
  } finally {
    uploading.value = false
  }
}

function removeImage (i) {
  form.value.images.splice(i, 1)
}

async function submit () {
  if (!form.value.petName.trim()) {
    toastError('请填写宠物名称')
    return
  }
  if (form.value.price === '') {
    toastError(isFoster.value ? '请填写寄养费' : '出售需要填写售价')
    return
  }
  if (isFoster.value && !form.value.startDate) {
    toastError('请选择开始寄养时间')
    return
  }
  if (isFoster.value && !form.value.endDate) {
    toastError('请选择结束寄养时间')
    return
  }
  const leadError = checkStartLead()
  if (leadError) {
    toastError(leadError)
    return
  }
  publishing.value = true
  try {
    await createPosting({
      category: form.value.category,
      petName: form.value.petName.trim(),
      species: form.value.species,
      breed: form.value.breed,
      gender: form.value.gender,
      ageMonth: form.value.ageMonth === '' ? null : Number(form.value.ageMonth),
      weightKg: form.value.weightKg === '' ? null : Number(form.value.weightKg),
      price: form.value.price === '' ? null : Number(form.value.price),
      priceUnit: form.value.priceUnit,
      startDate: toDateTime(form.value.startDate),
      endDate: toDateTime(form.value.endDate),
      durationMinutes: durationMinutes.value,
      description: form.value.description,
      images: form.value.images
    })
    router.push('/market')
  } catch {
    /* interceptor 已提示 */
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="mkp-page pg-publish">
    <div class="bg-layer" aria-hidden="true">
      <div class="blob b1"></div><div class="blob b2"></div><div class="blob b3"></div><div class="blob b4"></div>
    </div>

    <MkpTopNav active="/market" />

    <main id="pm-main" class="pm-main">
      <div class="pg-wrap">
        <section class="pub-grid">
          <!-- 左：交易类型 -->
          <div class="pub-left glass">
            <div>
              <span class="badge badge-amber">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l7 3v6c0 4.2-2.8 7.6-7 9-4.2-1.4-7-4.8-7-9V6z"/><path d="m9 12 2 2 4-4"/></svg>
                平台资金担保托管
              </span>
              <h1 class="pub-title">发布社区交易</h1>
              <p class="pub-sub">支持宠物转让与家庭寄养委托：寄养由主人出资请人照顾，出售由买家付款给卖家，资金全程托管、履约完成后结算。</p>

              <div class="opt-list">
                <button
                  v-for="c in [3, 2]"
                  :key="c"
                  class="opt"
                  :class="{ 'is-on': form.category === c }"
                  type="button"
                  @click="setCategory(c)"
                >
                  <span class="opt-l">
                    <span class="opt-ico" :class="c === 3 ? 'amber' : 'blue'">
                      <svg v-if="c === 3" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 7h12l-1 13H7z"/><path d="M9 7a3 3 0 0 1 6 0"/></svg>
                      <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M4 11l8-7 8 7v8a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z"/></svg>
                    </span>
                    <span class="opt-tx">
                      <b>{{ c === 3 ? '出售宠物' : '家庭寄养' }}</b>
                      <small>{{ c === 3 ? '转让给买家，买家付款给我' : '出差 / 假期，出钱请宠友照顾' }}</small>
                    </span>
                  </span>
                  <span class="opt-arr" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m9 6 6 6-6 6"/></svg>
                  </span>
                </button>
              </div>

              <p class="fld-hint">{{ categoryHint }}</p>
            </div>

            <div class="pub-foot">
              <span>遵守 MKP 社区公约</span>
              <a href="#" @click.prevent>查看交易规则</a>
            </div>
          </div>

          <!-- 右：发布表单 -->
          <div class="pub-right glass">
            <div class="card-head">
              <h3>填写交易详情</h3>
              <span class="hint">* 必填项</span>
            </div>

            <div class="form-grid">
              <label class="fld"><span>宠物名称 *</span><input v-model="form.petName" placeholder="如：小橘 / 豆豆" /></label>
              <label class="fld">
                <span>物种</span>
                <select v-model="form.species">
                  <option>猫</option><option>狗</option><option>仓鼠</option><option>兔子</option><option>其他</option>
                </select>
              </label>
              <label class="fld"><span>品种</span><input v-model="form.breed" placeholder="选填，如：布偶猫" /></label>
              <label class="fld">
                <span>性别</span>
                <select v-model="form.gender">
                  <option :value="0">未知</option><option :value="1">公</option><option :value="2">母</option>
                </select>
              </label>
              <label class="fld"><span>月龄</span><input v-model="form.ageMonth" type="number" min="0" placeholder="选填" /></label>
              <label class="fld"><span>体重（kg）</span><input v-model="form.weightKg" type="number" min="0" step="0.1" placeholder="选填" /></label>
            </div>

            <div class="divider"></div>

            <div class="form-grid">
              <label class="fld">
                <span>{{ isFoster ? '寄养费（元）*' : '售价（元）*' }}</span>
                <input
                  v-model="form.price"
                  type="number"
                  min="0"
                  step="0.01"
                  :placeholder="isFoster ? '如 60' : '如 800'"
                />
              </label>
              <label v-if="isFoster" class="fld">
                <span>计价单位</span>
                <select v-model="form.priceUnit">
                  <option v-for="(label, u) in PRICE_UNIT" :key="u" :value="Number(u)">{{ label }}</option>
                </select>
              </label>
              <label v-if="isFoster" class="fld">
                <span>开始时间 *<span class="fld-tip">需至少提前 {{ MIN_START_LEAD_MINUTES }} 分钟</span></span>
                <input v-model="form.startDate" type="datetime-local" />
              </label>
              <label v-if="isFoster" class="fld">
                <span>结束时间 *<span class="fld-tip">需晚于开始时间</span></span>
                <input v-model="form.endDate" type="datetime-local" />
              </label>
              <label v-if="isFoster && durationMinutes" class="fld">
                <span>预计时长</span>
                <input :value="Math.ceil(durationMinutes / 1440) + ' 天'" readonly />
              </label>
            </div>

            <label class="fld">
              <span>描述</span>
              <textarea v-model="form.description" rows="4" placeholder="补充宠物的性格、健康状况、期望等…"></textarea>
            </label>

            <div class="fld">
              <span>图片</span>
              <div v-if="form.images.length" class="thumbs">
                <div v-for="(img, i) in form.images" :key="i" class="thumb-item">
                  <img :src="img" alt="" />
                  <button class="thumb-x" type="button" aria-label="移除图片" @click="removeImage(i)">
                    <AppIcon name="x" :size="12" />
                  </button>
                </div>
              </div>
              <div class="uploader" role="button" tabindex="0" @click="pickImages" @keyup.enter="pickImages">
                <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="9" cy="9" r="1.6"/><path d="m21 15-4-4-7 7"/><path d="M17 18h6M20 15v6"/></svg>
                <p>{{ uploading ? '上传中…' : '点击上传图片（支持 JPG / PNG，可多选）' }}</p>
              </div>
              <input ref="fileRef" class="mkp-sr-only" type="file" accept="image/*" multiple @change="onPick" />
            </div>

            <div class="card-actions">
              <button class="btn btn-soft" type="button" @click="router.back()">取消</button>
              <button class="btn btn-dark" type="button" :disabled="publishing" @click="submit">
                <span v-if="publishing" class="sp is-light"></span>
                {{ publishing ? '发布中…' : '确认发布' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>

    <footer class="m-page-foot">
      <div class="m-page-foot-inner">
        <span>MKP PET COMMUNITY · 发布交易</span>
        <span>资金由平台托管 · 完成履约后结算</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.pub-grid { display: grid; grid-template-columns: repeat(12, 1fr); gap: 24px; }
.pub-left { grid-column: span 4; padding: 28px; display: flex; flex-direction: column; justify-content: space-between; gap: 24px; min-height: 540px; }
.fld-tip { font-weight: 400; color: #a1a1aa; margin-left: 6px; }
.pub-title { font-size: 24px; font-weight: 900; letter-spacing: -.02em; margin: 16px 0 6px; color: #18181b; }
.pub-sub { font-size: 12px; color: #71717a; line-height: 1.7; margin: 0; }
.opt-list { display: flex; flex-direction: column; gap: 12px; margin-top: 22px; }
.opt { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; text-align: left; padding: 14px 16px; border-radius: 16px; cursor: pointer; font-family: inherit; background: rgba(255, 255, 255, .75); border: 2px solid transparent; box-shadow: 0 4px 16px rgba(0, 0, 0, .02); transition: all .2s var(--mkp-ease); }
.opt:hover { border-color: rgba(0, 0, 0, .08); }
.opt.is-on { border-color: rgba(251, 191, 36, .9); }
.opt-l { display: flex; align-items: center; gap: 14px; min-width: 0; }
.opt-ico { width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center; flex: none; }
.opt-ico.amber { background: rgba(251, 191, 36, .2); color: #92400e; }
.opt-ico.blue { background: rgba(59, 130, 246, .18); color: #1d4ed8; }
.opt-tx b { display: block; font-size: 13.5px; font-weight: 700; color: #18181b; }
.opt-tx small { display: block; font-size: 11.5px; color: #71717a; margin-top: 2px; }
.opt-arr { color: #a1a1aa; flex: none; }
.pub-foot { padding-top: 20px; border-top: 1px solid rgba(0, 0, 0, .06); display: flex; align-items: center; justify-content: space-between; font-size: 11.5px; color: #a1a1aa; }
.pub-foot a { text-decoration: underline; }
.pub-right { grid-column: span 8; padding: 28px; display: flex; flex-direction: column; gap: 18px; }
.card-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid rgba(0, 0, 0, .06); }
.card-head h3 { font-size: 16px; font-weight: 800; color: #18181b; }
.card-head .hint { font-size: 11.5px; color: #a1a1aa; }
.card-actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding-top: 6px; }
@media (max-width: 1180px) {
  .pub-left, .pub-right { grid-column: span 12; }
  .pub-left { min-height: 0; }
}
</style>
