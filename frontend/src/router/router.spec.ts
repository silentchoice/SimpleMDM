import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createAppRouter } from './index'
import { useAuthStore } from '../stores/auth'
import { menuForRoles } from './menu'
import type { Session } from '../types'

const editorSession: Session = {
  accessToken: 'token',
  user: { id: 2, username: 'editor', displayName: 'Editor' },
  roles: ['DEPT_EDITOR'],
  department: { id: 3, code: 'OPS', name: 'Operations' }
}

describe('authenticated router and menu', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('redirects an unauthenticated user to login with their intended destination', async () => {
    const router = createAppRouter()
    await router.push('/metadata/active')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/login?redirect=/metadata/active')
  })

  it('redirects an authenticated visitor away from login', async () => {
    useAuthStore().setSession(editorSession)
    const router = createAppRouter()
    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('sends a user without a route role to forbidden', async () => {
    useAuthStore().setSession(editorSession)
    const router = createAppRouter()
    await router.push('/system/users')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/forbidden')
  })

  it('includes active verification and change submission in the editor menu', () => {
    const labels = menuForRoles(['DEPT_EDITOR']).map((item) => item.label)

    expect(labels).toContain('Active Metadata')
    expect(labels).toContain('Submit Change')
  })

  it('keeps the read-only viewer menu free of change and system actions', () => {
    const labels = menuForRoles(['DEPT_VIEWER']).map((item) => item.label)

    expect(labels).toContain('Active Metadata')
    expect(labels).not.toContain('Submit Change')
    expect(labels).not.toContain('Approvals')
    expect(labels).not.toContain('Users')
    expect(labels).not.toContain('Departments')
  })

  it('makes master-type templates available only to super administrators', async () => {
    useAuthStore().setSession({ ...editorSession, roles: ['SUPER_ADMIN'] })
    const superAdminRouter = createAppRouter()
    await superAdminRouter.push('/metadata/templates')
    await superAdminRouter.isReady()

    expect(superAdminRouter.currentRoute.value.name).toBe('master-type-templates')
    expect(menuForRoles(['SUPER_ADMIN']).map((item) => item.label)).toContain('Master Type Templates')

    useAuthStore().setSession(editorSession)
    const editorRouter = createAppRouter()
    await editorRouter.push('/metadata/templates')
    await editorRouter.isReady()
    expect(editorRouter.currentRoute.value.path).toBe('/forbidden')
  })
})
