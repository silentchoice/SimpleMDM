import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Role, Session } from '../types'

const SESSION_KEY = 'mdm.session'
const roles: Role[] = ['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER']

function isSession(value: unknown): value is Session {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  const user = candidate.user
  const department = candidate.department
  const validUser = Boolean(user && typeof user === 'object'
    && typeof (user as Record<string, unknown>).id === 'number'
    && typeof (user as Record<string, unknown>).username === 'string'
    && typeof (user as Record<string, unknown>).displayName === 'string')
  const validDepartment = department === null || Boolean(department && typeof department === 'object'
    && typeof (department as Record<string, unknown>).id === 'number'
    && typeof (department as Record<string, unknown>).code === 'string'
    && typeof (department as Record<string, unknown>).name === 'string')
  return typeof candidate.accessToken === 'string' && candidate.accessToken.length > 0
    && validUser && validDepartment
    && Array.isArray(candidate.roles) && candidate.roles.every((role) => typeof role === 'string' && roles.includes(role as Role))
}

function restoreSession(): Session | null {
  const raw = sessionStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw) as unknown
    if (isSession(session)) return session
    sessionStorage.removeItem(SESSION_KEY)
    return null
  } catch {
    sessionStorage.removeItem(SESSION_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const session = ref<Session | null>(restoreSession())
  const isAuthenticated = computed(() => Boolean(session.value?.accessToken))

  function setSession(nextSession: Session): void {
    session.value = nextSession
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(nextSession))
  }

  function clearSession(): void {
    session.value = null
    sessionStorage.removeItem(SESSION_KEY)
  }

  function hasAnyRole(requiredRoles: Role[]): boolean {
    return requiredRoles.length === 0 || requiredRoles.some((role) => session.value?.roles.includes(role))
  }

  return { session, isAuthenticated, setSession, clearSession, hasAnyRole }
})
