export type Role = 'SUPER_ADMIN' | 'DEPT_EDITOR' | 'DEPT_APPROVER' | 'DEPT_VIEWER'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface LoginUser {
  id: number
  username: string
  displayName: string
}

export interface DepartmentRef {
  id: number
  code: string
  name: string
}

export interface Session {
  accessToken: string
  user: LoginUser
  roles: Role[]
  department: DepartmentRef | null
}

export interface ApiError {
  code?: number
  message: string
  requestId?: string
  status?: number
}

export interface AppRouteMeta {
  requiresAuth?: boolean
  roles?: Role[]
  titleKey?: string
}
