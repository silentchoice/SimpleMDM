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

declare module 'vue-router' {
  interface RouteMeta extends AppRouteMeta {}
}

const allRoles = ['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] as const
const contentView = (title: string) => ({ template: `<section class="content-view"><h1>${title}</h1><p>This view will be completed in the next delivery.</p></section>` })

export function createAppRouter(history: RouterHistory = import.meta.env.MODE === 'test' ? createMemoryHistory() : createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', name: 'login', component: LoginView, meta: { title: 'Sign in' } },
      {
        path: '/', component: AppLayout, meta: { requiresAuth: true }, children: [
          { path: '', name: 'dashboard', component: DashboardView, meta: { title: 'Dashboard', roles: [...allRoles] } },
          { path: 'metadata/active', name: 'active-metadata', component: contentView('Active Metadata'), meta: { title: 'Active Metadata', roles: [...allRoles] } },
          { path: 'metadata/changes/new', name: 'submit-change', component: contentView('Submit Change'), meta: { title: 'Submit Change', roles: ['SUPER_ADMIN', 'DEPT_EDITOR'] } },
          { path: 'metadata/approvals', name: 'approvals', component: contentView('Approvals'), meta: { title: 'Approvals', roles: ['SUPER_ADMIN', 'DEPT_APPROVER'] } },
          { path: 'metadata/templates', name: 'master-type-templates', component: MasterTypeListView, meta: { title: 'Master Type Templates', roles: ['SUPER_ADMIN'] } },
          { path: 'system/users', name: 'users', component: UserListView, meta: { title: 'Users', roles: ['SUPER_ADMIN'] } },
          { path: 'system/departments', name: 'departments', component: DepartmentListView, meta: { title: 'Departments', roles: ['SUPER_ADMIN'] } },
          { path: 'system/roles', name: 'roles', component: RoleListView, meta: { title: 'Roles', roles: ['SUPER_ADMIN'] } }
        ]
      },
      { path: '/forbidden', name: 'forbidden', component: ForbiddenView, meta: { requiresAuth: true, title: 'Access denied' } },
      { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { title: 'Page not found' } }
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
