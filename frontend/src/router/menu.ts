import type { Role } from '../types'

export interface MenuItem {
  labelKey: string
  to: string
  roles: Role[]
}

const menu: MenuItem[] = [
  { labelKey: 'menu.dashboard', to: '/', roles: ['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] },
  { labelKey: 'menu.activeMetadata', to: '/metadata/active', roles: ['DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] },
  { labelKey: 'menu.submitChange', to: '/metadata/changes/new', roles: ['DEPT_EDITOR'] },
  { labelKey: 'menu.approvals', to: '/metadata/approvals', roles: ['DEPT_APPROVER'] },
  { labelKey: 'menu.masterTypeTemplates', to: '/metadata/templates', roles: ['SUPER_ADMIN'] },
  { labelKey: 'menu.users', to: '/system/users', roles: ['SUPER_ADMIN'] },
  { labelKey: 'menu.departments', to: '/system/departments', roles: ['SUPER_ADMIN'] },
  { labelKey: 'menu.roles', to: '/system/roles', roles: ['SUPER_ADMIN'] }
]

export function menuForRoles(roles: Role[]): MenuItem[] {
  return menu.filter((item) => item.roles.some((role) => roles.includes(role)))
}
