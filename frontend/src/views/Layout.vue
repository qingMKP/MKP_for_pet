<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { toggleTheme, currentTheme } from '../utils/theme'
import { getUnreadCount } from '../api'
import { initials } from '../utils/constants'

const router = useRouter()
const route = useRoute()

const theme = ref(currentTheme())
const sidebarOpen = ref(false)
const unread = ref(0)
const user = ref({
  nickname: localStorage.getItem('nickname') || '用户',
  avatar: localStorage.getItem('avatar') || '',
  role: localStorage.getItem('role') || 'USER'
})

const isAdmin = computed(() => user.value.role === 'ADMIN')

const navGroups = [
  {
    label: '社区',
    items: [
      { path: '/desktop', label: '桌面', icon: 'home' },
      { path: '/square', label: '社交广场', icon: 'paw' }
    ]
  },
  {
    label: '交易',
    items: [
      { path: '/market', label: '交易市场', icon: 'grid' },
      { path: '/trades', label: '我的交易', icon: 'clipboard' },
      { path: '/publish', label: '发布', icon: 'plus-circle' }
    ]
  },
  {
    label: '智能',
    items: [
      { path: '/assistant', label: '智能助手', icon: 'sparkles' },
      { path: '/pet-service', label: '宠物服务', icon: 'heart' }
    ]
  },
  { label: '消息', items: [{ path: '/messages', label: '私信', icon: 'message' }] }
]

function isActive(path) {
  return route.path.startsWith(path)
}

function onToggleTheme() {
  theme.value = toggleTheme()
}

function logout() {
  localStorage.clear()
  router.push('/login')
}

function closeAndGo(path) {
  sidebarOpen.value = false
  router.push(path)
}

onMounted(async () => {
  try {
    const n = await getUnreadCount()
    unread.value = n || 0
  } catch {
    /* ignore */
  }
})
</script>

<template>
  <div class="shell">
    <!-- 移动端顶栏 -->
    <header class="mobile-bar">
      <button class="btn-icon" aria-label="打开菜单" @click="sidebarOpen = true">
        <AppIcon name="menu" />
      </button>
      <span class="mobile-bar__title">MKP</span>
      <button class="btn-icon" aria-label="切换深浅色" @click="onToggleTheme">
        <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" />
      </button>
    </header>

    <div v-if="sidebarOpen" class="sidebar-scrim" @click="sidebarOpen = false"></div>

    <aside class="sidebar" :class="{ 'is-open': sidebarOpen }">
      <div class="sidebar__brand">
        <div class="sidebar__brand-mark">
          <AppIcon name="paw" :size="18" />
        </div>
        <div class="sidebar__brand-text">
          <div class="sidebar__brand-title">MKP 宠物社区</div>
          <div class="sidebar__brand-sub">Pet Community</div>
        </div>
      </div>

      <nav class="sidebar__nav" aria-label="主导航">
        <div v-for="group in navGroups" :key="group.label" class="nav-group">
          <div class="nav-group__label">{{ group.label }}</div>
          <button
            v-for="item in group.items"
            :key="item.path"
            class="nav-item"
            :class="{ 'is-active': isActive(item.path) }"
            @click="closeAndGo(item.path)"
          >
            <span class="nav-item__icon"><AppIcon :name="item.icon" :size="17" /></span>
            <span>{{ item.label }}</span>
            <span
              v-if="item.path === '/messages' && unread > 0"
              class="nav-item__badge"
            >{{ unread > 99 ? '99+' : unread }}</span>
          </button>
        </div>

        <div v-if="isAdmin" class="nav-group">
          <div class="nav-group__label">管理</div>
          <button class="nav-item" @click="closeAndGo('/admin')">
            <span class="nav-item__icon"><AppIcon name="shield" :size="17" /></span>
            <span>管理后台</span>
          </button>
        </div>
      </nav>

      <div class="sidebar__footer">
        <button class="sidebar__user" @click="closeAndGo('/profile')">
          <span class="avatar">
            <img v-if="user.avatar" :src="user.avatar" alt="" />
            <template v-else>{{ initials(user.nickname) }}</template>
          </span>
          <span class="sidebar__user-name">{{ user.nickname }}</span>
        </button>
        <button class="btn-icon" aria-label="切换深浅色" @click="onToggleTheme">
          <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="17" />
        </button>
        <button class="btn-icon" aria-label="退出登录" @click="logout">
          <AppIcon name="logout" :size="17" />
        </button>
      </div>
    </aside>

    <main class="main">
      <div class="content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.mobile-bar {
  display: none;
}

.sidebar-scrim {
  display: none;
}

@media (max-width: 960px) {
  .mobile-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 52px;
    padding: 0 14px;
    border-bottom: 1px solid var(--border);
    background: var(--bg-app);
    position: sticky;
    top: 0;
    z-index: 40;
  }

  .mobile-bar__title {
    font-weight: 600;
    color: var(--text-primary);
  }

  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 60;
    transform: translateX(-100%);
    transition: transform 200ms ease;
    box-shadow: none;
  }

  .sidebar.is-open {
    transform: translateX(0);
    box-shadow: var(--shadow-modal);
  }

  .sidebar-scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 55;
    background: var(--scrim);
  }
}
</style>
