import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createAppRouter } from './index'
import { useAuthStore } from '../stores/auth'
import { menuForRoles } from './menu'
import type { Session } from '../types'
import { i18n, setLocale } from '../i18n'

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
    localStorage.clear()
    setLocale('zh-CN')
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

  it('shows Chinese navigation labels by default and restores English labels after switching', () => {
    const labels = () => menuForRoles(['DEPT_EDITOR']).map((item) => i18n.global.t(item.labelKey))

    expect(labels()).toEqual(['仪表盘', '当前元数据', '提交变更', '业务数据'])

    setLocale('en-US')

    expect(labels()).toEqual(['Dashboard', 'Active Metadata', 'Submit Change', 'Business Data'])
  })

  it('keeps the read-only viewer menu free of change and system actions', () => {
    const labels = menuForRoles(['DEPT_VIEWER']).map((item) => i18n.global.t(item.labelKey))

    expect(labels).toContain('当前元数据')
    expect(labels).toContain('业务数据')
    expect(labels).not.toContain('提交变更')
    expect(labels).not.toContain('审批中心')
    expect(labels).not.toContain('用户管理')
    expect(labels).not.toContain('部门管理')
  })

  it('allows viewers to open the read-only record detail route but not the editor route', async () => {
    useAuthStore().setSession({ ...editorSession, roles: ['DEPT_VIEWER'] })
    const viewerRouter = createAppRouter()
    await viewerRouter.push('/records/81')
    await viewerRouter.isReady()

    expect(viewerRouter.currentRoute.value.name).toBe('record-detail')

    await viewerRouter.push('/records/drafts/91')
    await viewerRouter.isReady()
    expect(viewerRouter.currentRoute.value.path).toBe('/forbidden')
  })

  it('makes master-type templates available only to super administrators', async () => {
    useAuthStore().setSession({ ...editorSession, roles: ['SUPER_ADMIN'] })
    const superAdminRouter = createAppRouter()
    await superAdminRouter.push('/metadata/templates')
    await superAdminRouter.isReady()

    expect(superAdminRouter.currentRoute.value.name).toBe('master-type-templates')
    expect(menuForRoles(['SUPER_ADMIN']).map((item) => i18n.global.t(item.labelKey))).toContain('主数据类型模板')

    useAuthStore().setSession(editorSession)
    const editorRouter = createAppRouter()
    await editorRouter.push('/metadata/templates')
    await editorRouter.isReady()
    expect(editorRouter.currentRoute.value.path).toBe('/forbidden')
  })

  it('makes approval list, detail, and menu available only to department approvers', async () => {
    useAuthStore().setSession({ ...editorSession, roles: ['DEPT_APPROVER'] })
    const approverRouter = createAppRouter()
    await approverRouter.push('/metadata/approvals/91')
    await approverRouter.isReady()
    expect(approverRouter.currentRoute.value.name).toBe('approval-detail')
    expect(menuForRoles(['DEPT_APPROVER']).map((item) => i18n.global.t(item.labelKey))).toContain('审批中心')

    useAuthStore().setSession({ ...editorSession, roles: ['SUPER_ADMIN'] })
    const administratorRouter = createAppRouter()
    await administratorRouter.push('/metadata/approvals')
    await administratorRouter.isReady()
    expect(administratorRouter.currentRoute.value.path).toBe('/forbidden')
    expect(menuForRoles(['SUPER_ADMIN']).map((item) => i18n.global.t(item.labelKey))).not.toContain('审批中心')
  })
})
