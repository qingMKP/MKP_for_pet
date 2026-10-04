import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  /* ===== 新版界面（Desktop / 社交广场 / 交易市场 / 我的交易 / 发布交易 / 宠物服务），独立全屏布局 ===== */
  {
    path: '/desktop',
    name: 'Desktop',
    component: () => import('../views/DesktopView.vue')
  },
  {
    path: '/square',
    name: 'Square',
    component: () => import('../views/SquareView.vue')
  },
  {
    path: '/market',
    name: 'Market',
    component: () => import('../views/PetMarket.vue')
  },
  {
    path: '/trades',
    name: 'Trades',
    component: () => import('../views/MyTrades.vue')
  },
  {
    path: '/publish',
    name: 'Publish',
    component: () => import('../views/Publish.vue')
  },
  {
    path: '/pet-service',
    name: 'PetService',
    component: () => import('../views/PetService.vue')
  },
  {
    path: '/assistant',
    name: 'Assistant',
    component: () => import('../views/AssistantView.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue')
  },
  {
    path: '/messages',
    name: 'Messages',
    component: () => import('../views/Friends.vue')
  },
  {
    path: '/post/:id',
    name: 'PostDetail',
    component: () => import('../views/PostDetail.vue')
  },
  /* ===== 旧版壳（含旧页面，陆续重制） ===== */
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/desktop',
    children: [
      { path: 'posts', name: 'Posts', component: () => import('../views/Posts.vue') },
    ]
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('../views/admin/UserManage.vue') },
      { path: 'posts', name: 'AdminPosts', component: () => import('../views/admin/PostManage.vue') },
      { path: 'comments', name: 'AdminComments', component: () => import('../views/admin/CommentManage.vue') },
      { path: 'knowledge', name: 'AdminKnowledge', component: () => import('../views/admin/KnowledgeManage.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userId = localStorage.getItem('userId')
  const role = localStorage.getItem('role')

  if (to.path !== '/login' && !userId) {
    return { path: '/login' }
  }
  if (to.path === '/login' && userId) {
    return { path: role === 'ADMIN' ? '/admin' : '/' }
  }
  if (to.matched.some((r) => r.meta.requiresAdmin) && role !== 'ADMIN') {
    return { path: '/' }
  }
  return true
})

export default router
