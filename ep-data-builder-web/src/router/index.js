import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/envs',
    children: [
      {
        path: 'envs',
        name: 'envs',
        component: () => import('../views/env/EnvList.vue'),
        meta: { title: '环境管理' }
      },
      {
        path: 'factories',
        name: 'factories',
        component: () => import('../views/factory/FactoryList.vue'),
        meta: { title: '工厂管理' }
      },
      {
        path: 'factories/:id',
        name: 'factory-editor',
        component: () => import('../views/factory/FactoryEditor.vue'),
        meta: { title: '工厂编辑器' }
      },
      {
        path: 'builds',
        name: 'builds',
        component: () => import('../views/build/BuildList.vue'),
        meta: { title: '造数记录' }
      },
      {
        path: 'builds/:id',
        name: 'build-detail',
        component: () => import('../views/build/BuildDetail.vue'),
        meta: { title: '执行详情' }
      },
      {
        path: 'system/tokens',
        name: 'system-tokens',
        component: () => import('../views/system/TokenList.vue'),
        meta: { title: '开放令牌', requiresAdmin: true }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('../views/system/UserList.vue'),
        meta: { title: '用户管理', requiresAdmin: true }
      },
      {
        path: 'system/logs',
        name: 'system-logs',
        component: () => import('../views/system/LogList.vue'),
        meta: { title: '操作日志', requiresAdmin: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/envs'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path === '/login') {
    return userStore.isLoggedIn ? '/' : true
  }
  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    return '/envs'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - EP 造数平台` : 'EP 造数平台'
})

export default router
