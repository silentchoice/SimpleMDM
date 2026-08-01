import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { noAuth: true, title: '登录' } },
  {
    path: '/', component: () => import('../layout/MainLayout.vue'), redirect: '/mdm',
    children: [
      { path: 'mdm', name: 'MdmList', component: () => import('../views/mdm/List.vue'), meta: { title: '通用主数据' } },
      { path: 'mdm/create', name: 'MdmCreate', component: () => import('../views/mdm/Form.vue'), meta: { title: '新增主数据', mode: 'create' } },
      { path: 'mdm/:id/edit', name: 'MdmEdit', component: () => import('../views/mdm/Form.vue'), meta: { title: '编辑主数据', mode: 'edit' } },
      { path: 'mdm/:id', name: 'MdmDetail', component: () => import('../views/mdm/Detail.vue'), meta: { title: '主数据详情' } },
      { path: 'workflow/approvals', component: () => import('../views/workflow/List.vue'), meta: { title: '审批中心' } },
      { path: 'workflow/approvals/:id', component: () => import('../views/workflow/Detail.vue'), meta: { title: '审批详情' } },
      { path: 'integration', component: () => import('../views/integration/Manager.vue'), meta: { title: '集成管理' } },
      { path: 'integration/logs', component: () => import('../views/integration/Logs.vue'), meta: { title: '分发日志' } },
      { path: 'mdm-metadata', name: 'MdmMetadata', component: () => import('../views/mdm/MetadataManager.vue'), meta: { title: '元数据管理', capability: 'MDM_FIELD_MANAGE' } },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.noAuth) return token && to.path === '/login' ? next('/mdm') : next()
  return token ? next() : next('/login')
})

export default router
