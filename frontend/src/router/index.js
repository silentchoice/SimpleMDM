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
    redirect: '/mdm',
    children: [
      {
        path: 'mdm',
        name: 'MdmList',
        component: () => import('../views/mdm/List.vue'),
        meta: { title: '通用主数据' },
      },
      {
        path: 'mdm/create',
        name: 'MdmCreate',
        component: () => import('../views/mdm/Form.vue'),
        meta: { title: '新增主数据', mode: 'create' },
      },
      {
        path: 'mdm/:id/edit',
        name: 'MdmEdit',
        component: () => import('../views/mdm/Form.vue'),
        meta: { title: '编辑主数据', mode: 'edit' },
      },
      { path: 'workflow/approvals', component: () => import('../views/workflow/List.vue'), meta: { title: '瀹℃壒涓績' } },
      { path: 'workflow/approvals/:id', component: () => import('../views/workflow/Detail.vue'), meta: { title: '瀹℃壒璇︽儏' } },
      { path: 'integration', component: () => import('../views/integration/Manager.vue'), meta: { title: '闆嗘垚绠＄悊' } },
      { path: 'integration/logs', component: () => import('../views/integration/Logs.vue'), meta: { title: 'Generic' } },
      {
        path: 'mdm-metadata',
        name: 'MdmMetadata',
        component: () => import('../views/mdm/MetadataManager.vue'),
        meta: { title: '元数据管理' },
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
      return next('/mdm')
    }
    return next()
  }
  if (!token) {
    return next('/login')
  }
  next()
})

export default router
