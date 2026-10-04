<script setup>
import { ref } from 'vue'
import MkpTopNav from '../components/MkpTopNav.vue'
import '../assets/mkp/mkp.css'
import '../assets/mkp/mkp-pages.css'
import { getPetAdvice } from '../api'
import { toast, toastError } from '../utils/toast'

/* ================================================================
   宠物服务（桌面端）
   - 左侧：宠物信息 + 症状描述（可点快捷提问）
   - 右侧：大模型建议报告（POST /api/pet-service/advice）
   - 合作医院预约暂为占位（等后端接口）
   ================================================================ */
const form = ref({
  species: '猫',
  breed: '',
  ageMonth: '',
  weightKg: '',
  gender: 0,
  extra: ''
})

const result = ref('')
const loading = ref(false)

const suggestions = ['如何科学喂养', '疫苗与驱虫安排', '常见疾病预防', '绝育注意事项']

async function submit () {
  if (!form.value.extra.trim()) {
    toastError('请描述你想咨询的问题')
    return
  }
  loading.value = true
  result.value = ''
  try {
    const advice = await getPetAdvice({
      species: form.value.species,
      breed: form.value.breed,
      ageMonth: form.value.ageMonth === '' ? null : Number(form.value.ageMonth),
      weightKg: form.value.weightKg === '' ? null : Number(form.value.weightKg),
      gender: form.value.gender,
      extra: form.value.extra.trim()
    })
    result.value = advice || ''
  } catch {
    /* toast 已提示 */
  } finally {
    loading.value = false
  }
}

function useSuggestion (s) {
  form.value.extra = s
}

function notifyClinic () {
  toast('合作医院预约接口尚未开放，先按建议观察，必要时尽快到院')
}
</script>

<template>
  <div class="mkp-page pg-services">
    <div class="bg-layer" aria-hidden="true">
      <div class="blob b1"></div><div class="blob b2"></div><div class="blob b3"></div><div class="blob b4"></div>
    </div>

    <MkpTopNav active="/pet-service" :show-search="false" :show-action="false" />

    <main id="pm-main" class="pm-main">
      <div class="pg-wrap">
        <div class="svc-card glass">
          <div class="svc-head">
            <h2>MKP 智能养宠顾问</h2>
            <p>填写宠物信息与异常表现，获取大模型生成的喂养、健康与就医建议；需要到院时可直接联系合作医院。</p>
          </div>

          <div class="svc-grid">
            <!-- 左：问诊输入 -->
            <div class="svc-form glass-sub">
              <h4>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="8" width="18" height="12" rx="4"/><path d="M12 8V5M9 3h6M8 14h.01M16 14h.01M12 17v-3"/></svg>
                AI 智能症状自查
              </h4>

              <div class="form-grid">
                <label class="fld">
                  <span>物种</span>
                  <select v-model="form.species">
                    <option>猫</option><option>狗</option><option>仓鼠</option><option>兔子</option><option>其他</option>
                  </select>
                </label>
                <label class="fld"><span>品种</span><input v-model="form.breed" placeholder="选填，如：布偶猫" /></label>
                <label class="fld"><span>月龄</span><input v-model="form.ageMonth" type="number" min="0" placeholder="选填" /></label>
                <label class="fld"><span>体重（kg）</span><input v-model="form.weightKg" type="number" min="0" step="0.1" placeholder="选填" /></label>
                <label class="fld">
                  <span>性别</span>
                  <select v-model="form.gender">
                    <option :value="0">未知</option><option :value="1">公</option><option :value="2">母</option>
                  </select>
                </label>
              </div>

              <label class="fld">
                <span>想咨询什么？</span>
                <textarea v-model="form.extra" rows="4" placeholder="例如：布偶猫 1 岁半，最近食欲不振、软便，精神一般，需要怎么调理？"></textarea>
              </label>

              <div class="sug">
                <span>快捷提问</span>
                <button v-for="s in suggestions" :key="s" class="chip" type="button" @click="useSuggestion(s)">{{ s }}</button>
              </div>

              <button class="btn btn-dark" type="button" style="width: 100%" :disabled="loading" @click="submit">
                <span v-if="loading" class="sp is-light"></span>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M11 3l1.9 5.1L18 10l-5.1 1.9L11 17l-1.9-5.1L4 10l5.1-1.9z"/></svg>
                {{ loading ? '生成中…' : '获取建议' }}
              </button>
            </div>

            <!-- 右：建议报告 -->
            <div class="svc-result glass-sub">
              <div class="r-head">
                <span class="badge badge-amber">AI 建议报告</span>
                <span class="acc">由大模型生成 · 仅供参考</span>
              </div>

              <div class="r-body">
                <div v-if="loading" class="pg-empty">
                  <span class="sp"></span>
                  <p>正在为你生成建议…</p>
                </div>
                <p v-else-if="result" class="advice">{{ result }}</p>
                <div v-else class="pg-empty">
                  <div class="ico" aria-hidden="true">🐾</div>
                  <h4>还没有生成建议</h4>
                  <p>填写左侧信息并提交，建议会显示在这里</p>
                </div>
              </div>

              <div class="clinic">
                <div>
                  <b>附近 24h 合作诊所</b>
                  <small>瑞派宠物医院（距您 1.2km）</small>
                </div>
                <button class="btn btn-sm btn-blue" type="button" @click="notifyClinic">预约绿色通道</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <footer class="m-page-foot">
      <div class="m-page-foot-inner">
        <span>MKP PET COMMUNITY · 宠物服务</span>
        <span>建议由大模型生成，仅供参考，不能替代面诊</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.svc-card { padding: 28px; display: flex; flex-direction: column; gap: 22px; }
.svc-head { padding-bottom: 16px; border-bottom: 1px solid rgba(0, 0, 0, .06); max-width: 660px; }
.svc-head h2 { font-size: 20px; font-weight: 900; letter-spacing: -.01em; color: #18181b; }
.svc-head p { margin: 5px 0 0; font-size: 12px; color: #71717a; line-height: 1.7; }
.svc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; }
.svc-form { padding: 22px; display: flex; flex-direction: column; gap: 14px; }
.svc-form h4 { font-size: 13.5px; font-weight: 800; display: flex; align-items: center; gap: 8px; color: #18181b; }
.svc-form h4 svg { color: var(--pg-amber-2); }
.svc-result { padding: 22px; display: flex; flex-direction: column; gap: 16px; background: linear-gradient(135deg, rgba(255, 251, 235, .75), rgba(239, 246, 255, .75)); border-color: rgba(253, 230, 138, .6); }
.r-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.r-head .acc { font-size: 11px; color: #a1a1aa; }
.r-body { min-height: 150px; }
.advice { font-size: 12.5px; line-height: 1.8; color: #3f3f46; white-space: pre-wrap; word-break: break-word; margin: 0; }
.clinic { padding-top: 14px; border-top: 1px solid rgba(0, 0, 0, .06); display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.clinic b { display: block; font-size: 12.5px; color: #18181b; }
.clinic small { display: block; font-size: 11px; color: #a1a1aa; margin-top: 2px; }
@media (max-width: 1180px) { .svc-grid { grid-template-columns: 1fr; } }
</style>
