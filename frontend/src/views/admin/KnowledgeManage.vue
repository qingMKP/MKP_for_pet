<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../../components/AppIcon.vue'
import {
  adminGetKnowledgeList,
  adminAddKnowledge,
  adminUpdateKnowledge,
  adminBatchDeleteKnowledge
} from '../../api'
import { fmtTime } from '../../utils/constants'
import { toastSuccess } from '../../utils/toast'

const list = ref([])
const keyword = ref('')
const loading = ref(false)
const selected = ref([])

const modalOpen = ref(false)
const editing = ref(null)
const form = ref({ keyword: '', category: '', answer: '' })
const saving = ref(false)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter(
    (x) =>
      (x.keyword || '').toLowerCase().includes(k) ||
      (x.category || '').toLowerCase().includes(k) ||
      (x.answer || '').toLowerCase().includes(k)
  )
})

const allChecked = computed(
  () => filtered.value.length > 0 && filtered.value.every((x) => selected.value.includes(x.id))
)

async function load() {
  loading.value = true
  try {
    list.value = (await adminGetKnowledgeList()) || []
  } catch {
    /* toast 已提示 */
  } finally {
    loading.value = false
  }
}

function toggleAll() {
  if (allChecked.value) {
    selected.value = selected.value.filter((id) => !filtered.value.some((x) => x.id === id))
  } else {
    const ids = new Set(selected.value)
    filtered.value.forEach((x) => ids.add(x.id))
    selected.value = Array.from(ids)
  }
}

function toggleOne(id) {
  const i = selected.value.indexOf(id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(id)
}

function openAdd() {
  editing.value = null
  form.value = { keyword: '', category: '', answer: '' }
  modalOpen.value = true
}

function openEdit(k) {
  editing.value = k
  form.value = { keyword: k.keyword, category: k.category, answer: k.answer }
  modalOpen.value = true
}

async function save() {
  if (!form.value.keyword.trim() || !form.value.answer.trim()) return
  saving.value = true
  try {
    if (editing.value) {
      await adminUpdateKnowledge(editing.value.id, form.value)
      toastSuccess('知识已更新')
    } else {
      await adminAddKnowledge(form.value)
      toastSuccess('知识已添加')
    }
    modalOpen.value = false
    await load()
  } catch {
    /* toast 已提示 */
  } finally {
    saving.value = false
  }
}

async function batchDelete() {
  if (!selected.value.length) return
  if (!window.confirm(`确定删除选中的 ${selected.value.length} 条知识？`)) return
  try {
    await adminBatchDeleteKnowledge(selected.value)
    toastSuccess('已批量删除')
    selected.value = []
    await load()
  } catch {
    /* toast 已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="page-header__title">知识库</h1>
        <p class="page-header__sub">供智能助手检索的知识条目</p>
      </div>
      <div class="page-header__actions">
        <div class="search" style="width: 220px">
          <span class="search__icon"><AppIcon name="search" :size="15" /></span>
          <input v-model="keyword" class="input" placeholder="搜索关键词或分类" />
        </div>
        <button v-if="selected.length" class="btn btn-ghost" style="color: var(--danger)" @click="batchDelete">
          删除选中 ({{ selected.length }})
        </button>
        <button class="btn btn-primary" @click="openAdd">
          <AppIcon name="plus" :size="16" />新增
        </button>
      </div>
    </header>

    <div class="page__scroll">
    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th style="width: 40px">
              <input type="checkbox" :checked="allChecked" @change="toggleAll" />
            </th>
            <th>ID</th>
            <th>关键词</th>
            <th>分类</th>
            <th>答案</th>
            <th>更新时间</th>
            <th style="text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7"><div class="empty" style="padding: 24px"><span class="spinner"></span></div></td>
          </tr>
          <tr v-else-if="!filtered.length">
            <td colspan="7"><div class="empty">暂无知识条目</div></td>
          </tr>
          <tr v-for="k in filtered" v-else :key="k.id">
            <td><input type="checkbox" :checked="selected.includes(k.id)" @change="toggleOne(k.id)" /></td>
            <td class="mono">{{ k.id }}</td>
            <td style="font-weight: 500">{{ k.keyword }}</td>
            <td>
              <span v-if="k.category" class="pill">{{ k.category }}</span>
              <span v-else class="muted">—</span>
            </td>
            <td style="max-width: 340px">
              <span class="muted">{{ k.answer?.length > 80 ? k.answer.slice(0, 80) + '…' : k.answer }}</span>
            </td>
            <td class="muted">{{ fmtTime(k.updateTime) }}</td>
            <td>
              <div class="row" style="justify-content: flex-end">
                <button class="btn btn-sm btn-ghost" @click="openEdit(k)">编辑</button>
                <button class="btn btn-sm btn-ghost" style="color: var(--danger)" @click="selected = [k.id]; batchDelete()">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <div v-if="modalOpen" class="modal-scrim" @click.self="modalOpen = false">
      <div class="modal">
        <div class="modal__head">
          <h2 class="modal__title">{{ editing ? '编辑知识' : '新增知识' }}</h2>
          <button class="btn-icon" aria-label="关闭" @click="modalOpen = false">
            <AppIcon name="x" :size="18" />
          </button>
        </div>
        <div class="modal__body">
          <div class="field">
            <label class="field__label">关键词</label>
            <input v-model="form.keyword" class="input" placeholder="用户可能问到的问题关键词" />
          </div>
          <div class="field">
            <label class="field__label">分类</label>
            <input v-model="form.category" class="input" placeholder="选填，如：喂养 / 健康 / 领养" />
          </div>
          <div class="field">
            <label class="field__label">答案</label>
            <textarea v-model="form.answer" class="textarea" rows="5" placeholder="对应的回答内容"></textarea>
          </div>
        </div>
        <div class="modal__foot">
          <button class="btn btn-ghost" @click="modalOpen = false">取消</button>
          <button class="btn btn-primary" :disabled="saving || !form.keyword.trim() || !form.answer.trim()" @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
