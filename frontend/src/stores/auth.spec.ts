import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './auth'
import type { Session } from '../types'

const session: Session = {
  accessToken: 'token-123',
  user: { id: 7, username: 'editor', displayName: 'Editor User' },
  roles: ['DEPT_EDITOR'],
  department: { id: 2, code: 'SALES', name: 'Sales' }
}

describe('auth store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('persists a session and restores it for a new store instance', () => {
    const store = useAuthStore()
    store.setSession(session)

    setActivePinia(createPinia())
    const restoredStore = useAuthStore()

    expect(restoredStore.session).toEqual(session)
    expect(restoredStore.isAuthenticated).toBe(true)
  })

  it('clears a syntactically valid but malformed stored session', () => {
    sessionStorage.setItem('mdm.session', JSON.stringify({ accessToken: 'token', user: { id: '7' }, roles: 'DEPT_EDITOR' }))

    const store = useAuthStore()

    expect(store.session).toBeNull()
    expect(sessionStorage.getItem('mdm.session')).toBeNull()
  })

  it('clears its persisted session on logout', () => {
    const store = useAuthStore()
    store.setSession(session)
    store.clearSession()

    expect(store.session).toBeNull()
    expect(sessionStorage.getItem('mdm.session')).toBeNull()
  })

  it('allows access only when one of the required roles is present', () => {
    const store = useAuthStore()
    store.setSession(session)

    expect(store.hasAnyRole(['DEPT_EDITOR'])).toBe(true)
    expect(store.hasAnyRole(['SUPER_ADMIN', 'DEPT_APPROVER'])).toBe(false)
  })
})
