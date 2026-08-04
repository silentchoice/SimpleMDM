import type { Role } from '../types'

export interface MenuItem {
  label: string
  to: string
  roles: Role[]
}

const menu: MenuItem[] = [
  { label: 'Dashboard', to: '/', roles: ['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] },
  { label: 'Active Metadata', to: '/metadata/active', roles: ['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] },
  { label: 'Submit Change', to: '/metadata/changes/new', roles: ['SUPER_ADMIN', 'DEPT_EDITOR'] },
  { label: 'Approvals', to: '/metadata/approvals', roles: ['SUPER_ADMIN', 'DEPT_APPROVER'] },
  { label: 'Users', to: '/system/users', roles: ['SUPER_ADMIN'] },
  { label: 'Departments', to: '/system/departments', roles: ['SUPER_ADMIN'] }
]

export function menuForRoles(roles: Role[]): MenuItem[] {
  return menu.filter((item) => item.roles.some((role) => roles.includes(role)))
}
