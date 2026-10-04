<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '../../components/AppIcon.vue'
import { toggleTheme, currentTheme } from '../../utils/theme'
import { initials } from '../../utils/constants'

const router = useRouter()
const route = useRoute()

const theme = ref(currentTheme())
const sidebarOpen = ref(false)
const user = ref({
  nickname: localStorage.getItem('nickname') || '管理员',
  avatar: localStorage.getItem('avatar') || ''
})

const nav = [
  { path: '/admin/dashboard', label: '概览', icon: 'grid' },
  { path: '/admin/users', label: '用户管理', icon: 'users' },
  { path: '/admin/posts', label: '帖子管理', icon: 'file-text' },
  { path: '/admin/comments', label: '评论管理', icon: 'message' },
  { path: '/admin/knowledge', label: '知识库', icon: 'database' }
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
</script>

<template>
  <div class="shell">
    <header class="mobile-bar">
      <button class="btn-icon" aria-label="打开菜单" @click="sidebarOpen = true">
        <AppIcon name="menu" />
      </button>
      <span class="mobile-bar__title">MKP 管理后台</span>
      <button class="btn-icon" aria-label="切换深浅色" @click="onToggleTheme">
        <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" />
      </button>
    </header>

    <div v-if="sidebarOpen" class="sidebar-scrim" @click="sidebarOpen = false"></div>

    <aside class="sidebar" :class="{ 'is-open': sidebarOpen }">
      <div class="sidebar__brand">
        <div class="sidebar__brand-mark">
          <AppIcon name="shield" :size="18" />
        </div>
        <div class="sidebar__brand-text">
          <div class="sidebar__brand-title">MKP 管理后台</div>
          <div class="sidebar__brand-sub">Admin Console</div>
        </div>
      </div>

      <nav class="sidebar__nav" aria-label="管理导航">
        <div class="nav-group">
          <div class="nav-group__label">管理</div>
          <button
            v-for="item in nav"
            :key="item.path"
            class="nav-item"
            :class="{ 'is-active': isActive(item.path) }"
            @click="closeAndGo(item.path)"
          >
            <span class="nav-item__icon"><AppIcon :name="item.icon" :size="17" /></span>
            <span>{{ item.label }}</span>
          </button>
        </div>
      </nav>

      <div class="sidebar__footer">
        <button class="btn-icon" aria-label="返回前台" @click="router.push('/')">
          <AppIcon name="arrow-left" :size="17" />
        </button>
        <button class="sidebar__user" style="flex: 1; min-width: 0" @click="router.push('/profile')">
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
