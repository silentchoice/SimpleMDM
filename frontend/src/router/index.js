import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { noAuth: true },
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'personnel',
        name: 'PersonnelList',
        component: () => import('../views/personnel/List.vue'),
        meta: { title: '人员管理' },
      },
      {
        path: 'personnel/create',
        name: 'PersonnelCreate',
        component: () => import('../views/personnel/Form.vue'),
        meta: { title: '新增人员', mode: 'create' },
      },
      {
        path: 'personnel/:id',
        name: 'PersonnelView',
        component: () => import('../views/personnel/Form.vue'),
        meta: { title: '人员详情', mode: 'view' },
      },
      {
        path: 'personnel/:id/edit',
        name: 'PersonnelEdit',
        component: () => import('../views/personnel/Form.vue'),
        meta: { title: '编辑人员', mode: 'edit' },
      },
      {
        path: 'approvals',
        name: 'ApprovalList',
        component: () => import('../views/approval/List.vue'),
        meta: { title: '审批中心' },
      },
      {
        path: 'approvals/:id',
        name: 'ApprovalDetail',
        component: () => import('../views/approval/Detail.vue'),
        meta: { title: '审批详情' },
      },
      {
        path: 'push-logs',
        name: 'PushLogs',
        component: () => import('../views/push/Log.vue'),
        meta: { title: '推送日志' },
      },
      {
        path: 'push-apis',
        name: 'PushApiManager',
        component: () => import('../views/push/ApiManager.vue'),
        meta: { title: '推送API管理' },
      },
      {
        path: 'dept-fields',
        name: 'DeptFieldManager',
        component: () => import('../views/dept-fields/Manager.vue'),
        meta: { title: '字段定义管理' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.noAuth) {
    if (token && to.path === '/login') {
      return next('/dashboard')
    }
    return next()
  }
  if (!token) {
    return next('/login')
  }
  next()
})

export default router
