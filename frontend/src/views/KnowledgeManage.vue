<script setup>
import { ref, onMounted, computed } from 'vue'
import { getKnowledgeList, addKnowledge, updateKnowledge, batchDeleteKnowledge } from '../api/index.js'

const list = ref([])
const loading = ref(false)
const showDialog = ref(false)
const editingId = ref(null)
const form = ref({ keyword: '', answer: '', category: '' })
const selectedIds = ref([])

const allSelected = computed(() => {
  return list.value.length > 0 && selectedIds.value.length === list.value.length
})

function toggleSelect(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) {
    selectedIds.value.push(id)
  } else {
    selectedIds.value.splice(idx, 1)
  }
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = list.value.map(item => item.id)
  }
}

async function loadList() {
  loading.value = true
  try {
    list.value = await getKnowledgeList()
    selectedIds.value = []
  } catch {}
  loading.value = false
}

function openAdd() {
  editingId.value = null
  form.value = { keyword: '', answer: '', category: '' }
  showDialog.value = true
}

function openEdit(item) {
  editingId.value = item.id
  form.value = { keyword: item.keyword, answer: item.answer, category: item.category || '' }
  showDialog.value = true
}

async function handleSubmit() {
  if (!form.value.keyword.trim() || !form.value.answer.trim()) {
    alert('关键词和回答不能为空')
    return
  }
  try {
    if (editingId.value) {
      await updateKnowledge(editingId.value, form.value)
    } else {
      await addKnowledge(form.value)
    }
    showDialog.value = false
    loadList()
  } catch {}
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) {
    alert('请先选择要删除的知识')
    return
  }
  if (!confirm(`确认删除选中的 ${selectedIds.value.length} 条知识？`)) return
  try {
    await batchDeleteKnowledge(selectedIds.value)
    loadList()
  } catch {}
}

onMounted(loadList)
</script>

<template>
  <div class="knowledge-manage">
    <div class="km-header">
      <h3>📚 宠物知识库管理</h3>
      <div class="km-actions">
        <button class="btn btn-danger" @click="handleBatchDelete" v-if="selectedIds.length > 0">
          删除选中 ({{ selectedIds.length }})
        </button>
        <button class="btn btn-primary" @click="openAdd">新增知识</button>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="list.length === 0" class="empty">🐾 暂无知识库数据，快来添加宠物知识吧</div>

    <table v-else class="km-table">
      <thead>
        <tr>
          <th><input type="checkbox" :checked="allSelected" @change="toggleSelectAll" /></th>
          <th>ID</th>
          <th>关键词</th>
          <th>回答</th>
          <th>分类</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in list" :key="item.id"
            :class="{ 'selected-row': selectedIds.includes(item.id) }">
          <td><input type="checkbox" :checked="selectedIds.includes(item.id)" @change="toggleSelect(item.id)" /></td>
          <td>{{ item.id }}</td>
          <td>{{ item.keyword }}</td>
          <td class="answer-cell">{{ item.answer }}</td>
          <td>{{ item.category || '-' }}</td>
          <td>
            <button class="btn btn-sm" @click="openEdit(item)">编辑</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
      <div class="dialog">
        <h4>{{ editingId ? '编辑知识' : '新增知识' }}</h4>
        <div class="form-group">
          <label>关键词（多个用逗号分隔）</label>
          <input v-model="form.keyword" placeholder="如：狗粮,喂食,饮食" />
        </div>
        <div class="form-group">
          <label>回答</label>
          <textarea v-model="form.answer" rows="4" placeholder="输入回答内容"></textarea>
        </div>
        <div class="form-group">
          <label>分类</label>
          <input v-model="form.category" placeholder="如：饮食、健康、美容" />
        </div>
        <div class="dialog-actions">
          <button class="btn" @click="showDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleSubmit">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.knowledge-manage { padding: 20px; }
.km-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.km-header h3 { margin: 0; }
.km-actions { display: flex; gap: 10px; }
.km-table { width: 100%; border-collapse: collapse; background: #fdfcfe; border-radius: 8px; overflow: hidden; }
.km-table th, .km-table td { padding: 12px; text-align: left; border-bottom: 1px solid #f4f2f4; }
.km-table th { background: #f4f2f4; font-weight: 400; }
.km-table input[type="checkbox"] { width: 16px; height: 16px; cursor: pointer; }
.selected-row { background: rgba(171, 159, 242, .10); }
.answer-cell { max-width: 300px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.loading, .empty { text-align: center; padding: 40px; color: #a9a6b0; }
.dialog-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.dialog { background: #fdfcfe; border-radius: 12px; padding: 24px; width: 480px; max-width: 90%; }
.dialog h4 { margin: 0 0 20px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 14px; color: #86848d; }
.form-group input, .form-group textarea { width: 100%; padding: 8px 12px; border: 1px solid #e9e8ea; border-radius: 6px; font-size: 14px; box-sizing: border-box; }
.form-group textarea { resize: vertical; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.btn-danger { color: #fff; background: #b4553f; }
.btn-danger:hover { background: #98432f; }
</style>
