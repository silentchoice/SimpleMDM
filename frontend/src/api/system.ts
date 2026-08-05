import type { Role } from '../types'
import { http } from './http'

export type EntityStatus = 'ACTIVE' | 'DISABLED'

export interface Department {
  id: number
  code: string
  name: string
  status: EntityStatus
}

export interface DepartmentInput {
  code: string
  name: string
}

export interface SystemUser {
  id: number
  username: string
  displayName: string
  departmentId: number | null
  status: EntityStatus
  roles: Role[]
}

export interface CreateUserInput {
  username: string
  password: string
  displayName: string
  departmentId: number | null
  roles: Role[]
}

export interface UpdateUserInput {
  displayName: string
  departmentId: number | null
}

export function listDepartments(): Promise<Department[]> { return http.get<Department[]>('/department') }
export function getDepartment(id: number): Promise<Department> { return http.get<Department>(`/department/${id}`) }
export function createDepartment(body: DepartmentInput): Promise<Department> { return http.post<Department>('/department', body) }
export function updateDepartment(id: number, body: DepartmentInput): Promise<Department> { return http.put<Department>(`/department/${id}`, body) }
export function setDepartmentStatus(id: number, status: EntityStatus): Promise<void> { return http.patch<void>(`/department/${id}/status?status=${status}`) }
export function deleteDepartment(id: number): Promise<void> { return http.delete<void>(`/department/${id}`) }

export function listUsers(): Promise<SystemUser[]> { return http.get<SystemUser[]>('/user') }
export function createUser(body: CreateUserInput): Promise<SystemUser> { return http.post<SystemUser>('/user', body) }
export function updateUser(id: number, body: UpdateUserInput): Promise<SystemUser> { return http.put<SystemUser>(`/user/${id}`, body) }
export function setUserStatus(id: number, status: EntityStatus): Promise<void> { return http.patch<void>(`/user/${id}/status?status=${status}`) }
export function assignUserRoles(id: number, roles: Role[]): Promise<void> { return http.put<void>(`/user/${id}/roles`, roles) }

export function listRoles(): Promise<Role[]> { return http.get<Role[]>('/role') }
