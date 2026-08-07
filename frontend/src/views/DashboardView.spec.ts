import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppRouter } from '../router'
import DashboardView from './DashboardView.vue'
import { i18n, setLocale } from '../i18n'
import { useAuthStore } from '../stores/auth'
import type { Session } from '../types'

const dashboardApi = vi.hoisted(() => ({ getDashboardSummary: vi.fn() }))

vi.mock('../api/dashboard', () => dashboardApi)

const editorSession: Session = {
  accessToken: 'token',
  user: { id: 12, username: 'editor', displayName: 'Editor' },
  roles: ['DEPT_EDITOR'],
  department: { id: 7, code: 'OPS', name: 'Operations' }
}

const approverSession: Session = {
  ...editorSession,
  roles: ['DEPT_APPROVER']
}

const adminSession: Session = {
  accessToken: 'token',
  user: { id: 1, username: 'admin', displayName: 'Admin' },
  roles: ['SUPER_ADMIN'],
  department: null
}

const summary = {
  formalCount: 17,
  myDraftCount: 3,
  pendingApprovalCount: 5,
  activatedThisMonth: 4,
  recentTasks: [
    {
      id: 91,
      taskType: 'RECORD',
      entityKind: 'RECORD',
      entityId: 81,
      status: 'PENDING',
      submittedBy: 12,
      submittedAt: '2026-08-05T09:00:00'
    },
    {
      id: 92,
      taskType: 'METADATA',
      entityKind: 'MASTER_FIELDS',
      entityId: 41,
      status: 'APPROVED',
      submittedBy: 8,
      submittedAt: '2026-08-04T18:30:00'
    }
  ]
}

async function mountDashboard(session: Session = editorSession) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession(session)
  const router = createAppRouter()
  await router.push('/')
  await router.isReady()
  const wrapper = mount(DashboardView, { global: { plugins: [pinia, ElementPlus, i18n, router] } })
  await flushPromises()
  return { wrapper }
}

describe('dashboard view', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    setLocale('en-US')
    dashboardApi.getDashboardSummary.mockResolvedValue(summary)
  })

  it('loads one summary endpoint and renders four metrics, recent tasks, and editor shortcuts without embedding workflow forms', async () => {
    const { wrapper } = await mountDashboard()

    expect(dashboardApi.getDashboardSummary).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Formal records')
    expect(wrapper.text()).toContain('17')
    expect(wrapper.text()).toContain('My drafts')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('Pending approvals')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('Activated this month')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('Recent tasks')
    expect(wrapper.text()).toContain('Business Data')
    expect(wrapper.text()).toContain('Submit Change')
    expect(wrapper.text()).toContain('#91')
    expect(wrapper.text()).toContain('#92')
    expect(wrapper.text()).toContain('RECORD')
    expect(wrapper.find('[name="approveComment"]').exists()).toBe(false)
    expect(wrapper.find('[name="rejectReason"]').exists()).toBe(false)
  })

  it('shows approver and administrator shortcuts from the same summary payload', async () => {
    const approver = await mountDashboard(approverSession)
    expect(approver.wrapper.text()).toContain('Approvals')
    expect(approver.wrapper.text()).not.toContain('Master Type Templates')

    const admin = await mountDashboard(adminSession)
    expect(admin.wrapper.text()).toContain('Master Type Templates')
    expect(admin.wrapper.text()).toContain('Users')
    expect(admin.wrapper.text()).toContain('Departments')
    expect(admin.wrapper.text()).toContain('Roles')
    expect(admin.wrapper.text()).not.toContain('Submit Change')
  })

  it('shows loading, empty, and request-id error states from the single summary request', async () => {
    let resolve!: (value: typeof summary) => void
    dashboardApi.getDashboardSummary
      .mockReturnValueOnce(new Promise((done) => { resolve = done }))
      .mockResolvedValueOnce({ ...summary, recentTasks: [] })
      .mockRejectedValueOnce({ status: 500, message: 'Dashboard failed', requestId: 'req-dashboard' })

    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().setSession(editorSession)
    const router = createAppRouter()
    await router.push('/')
    await router.isReady()
    const wrapper = mount(DashboardView, { global: { plugins: [pinia, ElementPlus, i18n, router] } })

    expect(wrapper.text()).toContain('Loading')
    resolve(summary)
    await flushPromises()
    expect(wrapper.text()).toContain('#91')

    await wrapper.get('[data-testid="dashboard-refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No recent tasks')

    await wrapper.get('[data-testid="dashboard-refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Dashboard failed')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-dashboard')
  })

  it('switches dashboard metrics, shortcuts, and recent-task labels between English and Chinese live', async () => {
    const { wrapper } = await mountDashboard()

    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Pending approvals')
    expect(wrapper.text()).toContain('Recent tasks')

    setLocale('zh-CN')
    await flushPromises()
    expect(wrapper.text()).toContain('仪表盘')
    expect(wrapper.text()).toContain('待审批')
    expect(wrapper.text()).toContain('最近任务')

    setLocale('en-US')
    await flushPromises()
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Pending approvals')
  })
})
