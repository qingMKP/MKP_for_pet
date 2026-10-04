<script setup>
import { ref, computed } from 'vue'
import { createPosting, uploadPostingImages } from '../api/index.js'

const form = ref({
  petName: '',
  species: '猫',
  breed: '',
  gender: 0,
  ageMonth: '',
  weightKg: '',
  price: '',
  priceUnit: 2,
  startDate: '',
  endDate: '',
  description: ''
})
const type = ref('foster')  // 'foster' 寄养需求 | 'sale' 出售宠物
const selectedFiles = ref([])
const previewUrls = ref([])
const imageUrls = ref([])
const uploading = ref(false)
const publishing = ref(false)
const fileInput = ref(null)

const durationMinutes = computed(() => {
  if (!form.value.startDate || !form.value.endDate) return null
  const start = new Date(form.value.startDate)
  const end = new Date(form.value.endDate)
  const diff = Math.round((end - start) / 60000)
  return diff > 0 ? diff : null
})

function durationText(m) {
  if (m < 60) return m + ' 分钟'
  if (m < 1440) {
    const h = Math.floor(m / 60)
    const mm = m % 60
    return mm ? h + ' 小时 ' + mm + ' 分钟' : h + ' 小时'
  }
  const d = Math.floor(m / 1440)
  const h = Math.floor((m % 1440) / 60)
  return h ? d + ' 天 ' + h + ' 小时' : d + ' 天'
}

function triggerFileSelect() { fileInput.value.click() }

function handleFileChange(event) {
  const files = Array.from(event.target.files)
  if (files.length === 0) return
  for (const f of files) {
    if (!f.type.startsWith('image/')) { alert('仅支持图片文件'); return }
    if (f.size > 5 * 1024 * 1024) { alert('单张图片不能超过5MB'); return }
  }
  selectedFiles.value = files
  previewUrls.value = files.map(f => URL.createObjectURL(f))
}

async function uploadImages() {
  if (selectedFiles.value.length === 0) return
  uploading.value = true
  try {
    const urls = await uploadPostingImages(selectedFiles.value)
    imageUrls.value = [...imageUrls.value, ...urls]
    selectedFiles.value = []
    previewUrls.value = []
    fileInput.value.value = ''
  } catch { alert('图片上传失败') } finally { uploading.value = false }
}

function removeImage(i) { imageUrls.value.splice(i, 1) }

async function publish() {
  const isSale = type.value === 'sale'
  if (isSale && (form.value.price === '' || form.value.price == null)) {
    alert('出售必须填写售价')
    return
  }
  publishing.value = true
  try {
    await createPosting({
      direction: isSale ? 2 : 1,   // 出售=供给，寄养=需求
      category: isSale ? 3 : 2,    // 出售=3，寄养=2
      petName: form.value.petName,
      species: form.value.species,
      breed: form.value.breed,
      ageMonth: form.value.ageMonth === '' ? null : Number(form.value.ageMonth),
      weightKg: form.value.weightKg === '' ? null : Number(form.value.weightKg),
      gender: Number(form.value.gender),
      price: form.value.price === '' ? null : Number(form.value.price),
      priceUnit: isSale ? 1 : Number(form.value.priceUnit),
      startDate: isSale ? null : (form.value.startDate || null),
      endDate: isSale ? null : (form.value.endDate || null),
      description: form.value.description,
      images: imageUrls.value
    })
    alert('发布成功！')
    form.value = { petName: '', species: '猫', breed: '', gender: 0, ageMonth: '', weightKg: '', price: '', priceUnit: 2, startDate: '', endDate: '', description: '' }
    imageUrls.value = []
  } catch {} finally { publishing.value = false }
}
</script>

<template>
  <div>
    <div class="ph-page-head">
      <div>
        <h1 class="ph-page-title">{{ type === 'sale' ? '发布出售宠物' : '发布寄养需求' }}</h1>
        <p class="ph-page-sub">{{ type === 'sale' ? '描述你的宠物，等待买家选购' : '描述你的宠物与寄养时段，等待照顾者接单' }}</p>
      </div>
    </div>

    <div class="card publish-card">
      <div class="type-tabs">
        <button :class="{ active: type === 'foster' }" @click="type = 'foster'">寄养需求</button>
        <button :class="{ active: type === 'sale' }" @click="type = 'sale'">出售宠物</button>
      </div>
      <div class="section-label">宠物信息</div>
      <div class="form-grid">
        <div class="form-group">
          <label>宠物名字</label>
          <input v-model="form.petName" placeholder="如：豆豆" />
        </div>
        <div class="form-group">
          <label>品种</label>
          <input v-model="form.breed" placeholder="如：金毛 / 布偶猫" />
        </div>
        <div class="form-group">
          <label>物种</label>
          <select v-model="form.species">
            <option>猫</option><option>狗</option><option>兔</option><option>鸟</option><option>其他</option>
          </select>
        </div>
        <div class="form-group">
          <label>性别</label>
          <select v-model="form.gender">
            <option :value="0">未知</option><option :value="1">公</option><option :value="2">母</option>
          </select>
        </div>
        <div class="form-group">
          <label>月龄</label>
          <input v-model="form.ageMonth" type="number" min="0" placeholder="如：12" />
        </div>
        <div class="form-group">
          <label>体重(kg)</label>
          <input v-model="form.weightKg" type="number" min="0" step="0.1" placeholder="如：4.5" />
        </div>
      </div>

      <template v-if="type === 'foster'">
        <div class="section-label">寄养安排</div>
        <div class="form-grid">
          <div class="form-group">
            <label>开始时间</label>
            <input v-model="form.startDate" type="datetime-local" />
          </div>
          <div class="form-group">
            <label>结束时间</label>
            <input v-model="form.endDate" type="datetime-local" />
          </div>
          <div class="form-group">
            <label>愿意支付的价格（可留空，留空为面议）</label>
            <input v-model="form.price" type="number" min="0" step="0.01" placeholder="如：30" />
          </div>
          <div class="form-group">
            <label>计价方式</label>
            <select v-model="form.priceUnit">
              <option :value="2">每天</option>
              <option :value="3">每小时</option>
              <option :value="4">每分钟</option>
              <option :value="1">总价</option>
            </select>
          </div>
        </div>
        <p v-if="durationMinutes" class="days-hint">寄养时长约 {{ durationText(durationMinutes) }}</p>
      </template>

      <template v-else>
        <div class="section-label">出售价格</div>
        <div class="form-grid">
          <div class="form-group">
            <label>售价（元，必填）</label>
            <input v-model="form.price" type="number" min="0" step="0.01" placeholder="如：800" />
          </div>
        </div>
      </template>

      <div class="section-label">补充说明</div>
      <div class="form-group">
        <textarea v-model="form.description" placeholder="介绍宠物的性格、习惯、注意事项等"></textarea>
      </div>

      <div class="form-group">
        <label>图片（多张，可选）</label>
        <div v-if="imageUrls.length" class="img-grid">
          <div v-for="(url, i) in imageUrls" :key="i" class="img-item">
            <img :src="url" alt="图片" />
            <span class="img-remove" @click="removeImage(i)">×</span>
          </div>
        </div>
        <div v-if="previewUrls.length" class="img-grid">
          <div v-for="(url, i) in previewUrls" :key="'p'+i" class="img-item">
            <img :src="url" alt="预览" />
            <span class="img-badge">待上传</span>
          </div>
        </div>
        <input ref="fileInput" type="file" accept="image/*" multiple style="display:none" @change="handleFileChange" />
        <button class="btn btn-ghost btn-sm" @click="triggerFileSelect">选择图片</button>
        <button v-if="selectedFiles.length" class="btn btn-primary btn-sm" style="margin-left:8px" :disabled="uploading" @click="uploadImages">
          {{ uploading ? '上传中...' : `上传(${selectedFiles.length})` }}
        </button>
      </div>

      <button class="btn btn-primary" :disabled="publishing" @click="publish">
        {{ publishing ? '发布中...' : (type === 'sale' ? '发布出售' : '发布需求') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.publish-card { max-width: 760px; }
.type-tabs { display: inline-flex; gap: 6px; background: var(--bg-alt); border-radius: 100px; padding: 5px; margin-bottom: 20px; }
.type-tabs button { padding: 9px 24px; border: none; background: transparent; font-size: 14px; font-weight: 400; cursor: pointer; color: var(--text-muted); border-radius: 100px; transition: all var(--transition); font-family: inherit; letter-spacing: -0.025em; }
.type-tabs button.active { background: var(--color-ghost-lavender); color: var(--color-aubergine); box-shadow: var(--glow); }
.section-label {
  font-size: 14px;
  font-weight: 400;
  color: var(--color-aubergine);
  letter-spacing: -0.025em;
  margin: 4px 0 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-light);
}
.section-label:not(:first-child) { margin-top: 22px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.days-hint { font-size: 13px; color: var(--text-muted); margin: -4px 0 0; }

select, textarea { width: 100%; padding: 12px 16px; border: 1px solid var(--border); border-radius: 16px; font-size: 14px; outline: none; transition: all .22s; font-family: inherit; background: #fff; color: var(--text); font-weight: 400; }
select:focus, textarea:focus { border-color: var(--color-periwinkle); box-shadow: 0 0 0 3px rgba(171, 159, 242, .12); }
textarea { resize: vertical; min-height: 84px; }

.img-grid { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.img-item { position: relative; width: 88px; height: 88px; }
.img-item img { width: 100%; height: 100%; object-fit: cover; border-radius: 16px; border: 1px solid var(--border); }
.img-remove { position: absolute; top: -6px; right: -6px; width: 20px; height: 20px; background: var(--danger); color: #fff; border-radius: 100px; display: flex; align-items: center; justify-content: center; font-size: 13px; cursor: pointer; }
.img-badge { position: absolute; bottom: 4px; right: 4px; background: var(--color-aubergine); color: #fff; font-size: 10px; padding: 2px 10px; border-radius: 100px; font-weight: 400; }

@media (max-width: 900px) { .form-grid { grid-template-columns: 1fr; } }
</style>
