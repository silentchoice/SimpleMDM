import { createMemoryHistory, createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { AppRouteMeta } from '../types'
import AppLayout from '../layouts/AppLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import LoginView from '../views/LoginView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import DepartmentListView from '../views/system/DepartmentListView.vue'
import RoleListView from '../views/system/RoleListView.vue'
import UserListView from '../views/system/UserListView.vue'
import MasterTypeListView from '../views/metadata/MasterTypeListView.vue'
import DepartmentMetadataView from '../views/metadata/DepartmentMetadataView.vue'
import ApprovalListView from '../views/approval/ApprovalListView.vue'
import ApprovalDetailView from '../views/approval/ApprovalDetailView.vue'
import RecordListView from '../views/records/RecordListView.vue'
import RecordDetailView from '../views/records/RecordDetailView.vue'
import RecordEditorView from '../views/records/RecordEditorView.vue'

declare module 'vue-router' {
  interface RouteMeta extends AppRouteMeta {}
}

const allRoles = ['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] as const
const departmentMetadataRoles = ['DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] as const
const businessDataRoles = ['DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] as const

export function createAppRouter(history: RouterHistory = import.meta.env.MODE === 'test' ? createMemoryHistory() : createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', name: 'login', component: LoginView, meta: { titleKey: 'routes.login' } },
      {
        path: '/', component: AppLayout, meta: { requiresAuth: true }, children: [
          { path: '', name: 'dashboard', component: DashboardView, meta: { titleKey: 'routes.dashboard', roles: [...allRoles] } },
          { path: 'metadata/active', name: 'active-metadata', component: DepartmentMetadataView, meta: { titleKey: 'routes.activeMetadata', roles: [...departmentMetadataRoles] } },
          { path: 'metadata/changes/new', name: 'submit-change', component: DepartmentMetadataView, props: { initialTab: 'submit' }, meta: { titleKey: 'routes.submitChange', roles: ['DEPT_EDITOR'] } },
          { path: 'records', name: 'records', component: RecordListView, meta: { titleKey: 'routes.records', roles: [...businessDataRoles] } },
          { path: 'records/:recordId', name: 'record-detail', component: RecordDetailView, meta: { titleKey: 'routes.recordDetail', roles: [...businessDataRoles] } },
          { path: 'records/drafts/:draftId', name: 'record-draft', component: RecordEditorView, meta: { titleKey: 'routes.recordDraft', roles: ['DEPT_EDITOR'] } },
          { path: 'metadata/approvals', name: 'approvals', component: ApprovalListView, meta: { titleKey: 'routes.approvals', roles: ['DEPT_APPROVER'] } },
          { path: 'metadata/approvals/:taskId', name: 'approval-detail', component: ApprovalDetailView, meta: { titleKey: 'routes.approvalDetail', roles: ['DEPT_APPROVER'] } },
          { path: 'metadata/templates', name: 'master-type-templates', component: MasterTypeListView, meta: { titleKey: 'routes.masterTypeTemplates', roles: ['SUPER_ADMIN'] } },
          { path: 'system/users', name: 'users', component: UserListView, meta: { titleKey: 'routes.users', roles: ['SUPER_ADMIN'] } },
          { path: 'system/departments', name: 'departments', component: DepartmentListView, meta: { titleKey: 'routes.departments', roles: ['SUPER_ADMIN'] } },
          { path: 'system/roles', name: 'roles', component: RoleListView, meta: { titleKey: 'routes.roles', roles: ['SUPER_ADMIN'] } }
        ]
      },
      { path: '/forbidden', name: 'forbidden', component: ForbiddenView, meta: { requiresAuth: true, titleKey: 'routes.forbidden' } },
      { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { titleKey: 'routes.notFound' } }
    ]
  })

  router.beforeEach((to) => {
    const auth = useAuthStore()
    if (to.name === 'login' && auth.isAuthenticated) return { name: 'dashboard' }
    if (to.meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
    const roles = to.meta.roles
    if (roles?.length && !auth.hasAnyRole(roles)) return { name: 'forbidden' }
    return true
  })

  return router
}

export const router = createAppRouter()
