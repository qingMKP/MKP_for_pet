<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../../components/AppIcon.vue'
import {
  adminListUsers,
  adminUpdateUserRole,
  adminResetPassword
} from '../../api'
import { fmtDateTime } from '../../utils/constants'
import { toastSuccess } from '../../utils/toast'

const list = ref([])
const page = ref(1)
const size = 10
const total = ref(0)
const keyword = ref('')
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  try {
    const res = await adminListUsers(page.value, size, keyword.value || undefined)
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

async function toggleRole(u) {
  const next = u.role === 'ADMIN' ? 'USER' : 'ADMIN'
  if (!window.confirm(`确定将「${u.nickname || u.username}」设为${next === 'ADMIN' ? '管理员' : '普通用户'}？`)) return
  try {
    await adminUpdateUserRole(u.id, next)
    toastSuccess('角色已更新')
    await load()
  } catch {
    /* toast 已提示 */
  }
}

async function resetPwd(u) {
  if (!window.confirm(`确定重置「${u.nickname || u.username}」的密码为 123456？`)) return
  try {
    await adminResetPassword(u.id)
    toastSuccess('密码已重置为 123456')
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
        <h1 class="page-header__title">用户管理</h1>
        <p class="page-header__sub">共 {{ total }} 位用户</p>
      </div>
      <div class="page-header__actions">
        <div class="search" style="width: 220px">
          <span class="search__icon"><AppIcon name="search" :size="15" /></span>
          <input v-model="keyword" class="input" placeholder="搜索用户名或昵称" @keyup.enter="search" />
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
            <th>用户名</th>
            <th>昵称</th>
            <th>手机号</th>
            <th>角色</th>
            <th>加入时间</th>
            <th style="text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7">
              <div class="empty" style="padding: 24px"><span class="spinner"></span></div>
            </td>
          </tr>
          <tr v-else-if="!list.length">
            <td colspan="7"><div class="empty">暂无用户</div></td>
          </tr>
          <tr v-for="u in list" v-else :key="u.id">
            <td class="mono">{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>{{ u.nickname || '—' }}</td>
            <td>{{ u.phone || '—' }}</td>
            <td>
              <span class="pill" :class="u.role === 'ADMIN' ? 'pill--primary' : ''">{{ u.role === 'ADMIN' ? '管理员' : '用户' }}</span>
            </td>
            <td class="muted">{{ fmtDateTime(u.createTime) }}</td>
            <td>
              <div class="row" style="justify-content: flex-end">
                <button class="btn btn-sm btn-ghost" @click="toggleRole(u)">
                  {{ u.role === 'ADMIN' ? '设为用户' : '设为管理员' }}
                </button>
                <button class="btn btn-sm btn-ghost" @click="resetPwd(u)">重置密码</button>
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
