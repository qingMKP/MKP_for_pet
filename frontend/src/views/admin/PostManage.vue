<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../../components/AppIcon.vue'
import { adminListPosts, adminDeletePost } from '../../api'
import { fmtDateTime } from '../../utils/constants'
import { toastSuccess } from '../../utils/toast'

const list = ref([])
const page = ref(1)
const size = 10
const total = ref(0)
const keyword = ref('')
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

function truncate(s, n = 60) {
  if (!s) return ''
  return s.length > n ? s.slice(0, n) + '…' : s
}

async function load() {
  loading.value = true
  try {
    const res = await adminListPosts(page.value, size, keyword.value || undefined)
    list.value = res?.records || []
    total.value = Number(res?.total) || 0
  } catch {
    /* toast 已提示 */
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function go(p) {
  page.value = p
  load()
}

async function remove(p) {
  if (!window.confirm('确定删除这篇帖子？')) return
  try {
    await adminDeletePost(p.id)
    toastSuccess('帖子已删除')
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
        <h1 class="page-header__title">帖子管理</h1>
        <p class="page-header__sub">共 {{ total }} 篇帖子</p>
      </div>
      <div class="page-header__actions">
        <div class="search" style="width: 220px">
          <span class="search__icon"><AppIcon name="search" :size="15" /></span>
          <input v-model="keyword" class="input" placeholder="搜索帖子内容" @keyup.enter="search" />
        </div>
        <button class="btn btn-secondary" @click="search">搜索</button>
      </div>
    </header>

    <div class="page__scroll">
    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>作者</th>
            <th>内容</th>
            <th>点赞</th>
            <th>浏览</th>
            <th>发布时间</th>
            <th style="text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7"><div class="empty" style="padding: 24px"><span class="spinner"></span></div></td>
          </tr>
          <tr v-else-if="!list.length">
            <td colspan="7"><div class="empty">暂无帖子</div></td>
          </tr>
          <tr v-for="p in list" v-else :key="p.id">
            <td class="mono">{{ p.id }}</td>
            <td>{{ p.author?.nickname || '匿名' }}</td>
            <td style="max-width: 320px">{{ truncate(p.content) }}</td>
            <td>{{ p.likeCount || 0 }}</td>
            <td>{{ p.viewCount || 0 }}</td>
            <td class="muted">{{ fmtDateTime(p.createTime) }}</td>
            <td>
              <div class="row" style="justify-content: flex-end">
                <button class="btn btn-sm btn-ghost" style="color: var(--danger)" @click="remove(p)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager">
      <span class="pager__info">第 {{ page }}/{{ totalPages }} 页 · 共 {{ total }} 条</span>
      <div class="row">
        <button class="btn btn-ghost btn-sm" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
        <button class="btn btn-ghost btn-sm" :disabled="page >= totalPages" @click="go(page + 1)">下一页</button>
      </div>
    </div>
    </div>
  </div>
</template>
