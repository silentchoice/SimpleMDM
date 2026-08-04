import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Role, Session } from '../types'

const SESSION_KEY = 'mdm.session'

function restoreSession(): Session | null {
  const raw = sessionStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Session
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
