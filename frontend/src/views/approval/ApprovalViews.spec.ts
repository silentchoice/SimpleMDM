import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApprovalDetailView from './ApprovalDetailView.vue'
import ApprovalListView from './ApprovalListView.vue'
import { i18n, setLocale } from '../../i18n'
import { useAuthStore } from '../../stores/auth'
import type { Session } from '../../types'

const approvalApi = vi.hoisted(() => ({
  listApprovalTasks: vi.fn(),
  getApprovalTask: vi.fn(),
  approveApprovalTask: vi.fn(),
  rejectApprovalTask: vi.fn()
}))
const metadataApi = vi.hoisted(() => ({ invalidateActiveMetadata: vi.fn() }))
vi.mock('../../api/approval', () => approvalApi)
vi.mock('../../api/metadata', () => metadataApi)

const session: Session = {
  accessToken: 'token',
  user: { id: 12, username: 'approver', displayName: 'Approver' },
  roles: ['DEPT_APPROVER'],
  department: { id: 7, code: 'OPS', name: 'Operations' }
}

function metadataSnapshot(definitions: unknown[] = []): string {
  return JSON.stringify({
    schemaVersion: 1,
    departmentId: 3,
    templateId: 41,
    entityKind: 'MASTER_FIELDS',
    baseFingerprint: 'a'.repeat(64),
    orderedDefinitions: definitions
  })
}

function metadataTask(status = 'PENDING') {
  const after = {
    id: 0,
    ownerTypeId: 41,
    code: 'SERIAL',
    displayName: 'Serial',
    fieldType: 'TEXT',
    required: false,
    options: [],
    shared: false,
    sortOrder: 0,
    status: 'ACTIVE'
  }
  return {
    id: 91,
    taskType: 'METADATA',
    entityKind: 'MASTER_FIELDS',
    entityId: 41,
    status,
    beforeSnapshot: metadataSnapshot(),
    afterSnapshot: metadataSnapshot([after]),
    submittedBy: 18,
    reviewedBy: null,
    reviewComment: null,
    submittedAt: '2026-08-04T09:30:00',
    reviewedAt: null
  }
}

function recordSnapshot(overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({
    schemaVersion: 1,
    departmentId: 7,
    masterTypeId: 41,
    recordId: 81,
    recordCode: 'AST-0001',
    action: 'UPDATE',
    baseVersion: 4,
    masterValues: { name: 'Laptop fleet', owner: 'Alice' },
    children: [
      {
        subTypeId: 301,
        rows: [{ recordId: 101, rowOrder: 0, values: { contact: 'Li', city: 'Shanghai' } }]
      }
    ],
    ...overrides
  })
}

function recordTask(status = 'PENDING', submittedBy = 18) {
  return {
    id: 92,
    taskType: 'RECORD',
    entityKind: 'RECORD',
    entityId: 701,
    status,
    beforeSnapshot: recordSnapshot(),
    afterSnapshot: recordSnapshot({
      masterValues: { name: 'Laptop fleet', owner: 'Bob' },
      children: [
        {
          subTypeId: 301,
          rows: [
            { recordId: 101, rowOrder: 1, values: { contact: 'Li', city: 'Suzhou' } },
            { recordId: null, rowOrder: 0, values: { contact: '<script>alert(1)</script>', city: 'Hangzhou' } }
          ]
        }
      ]
    }),
    submittedBy,
    reviewedBy: null,
    reviewComment: null,
    submittedAt: '2026-08-05T09:30:00',
    reviewedAt: null
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  return { promise: new Promise<T>((done) => { resolve = done }), resolve }
}

async function routerAt(path: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/metadata/approvals', name: 'approvals', component: ApprovalListView },
      { path: '/metadata/approvals/:taskId', name: 'approval-detail', component: ApprovalDetailView }
    ]
  })
  await router.push(path)
  await router.isReady()
  return router
}

function mountWithSession(component: typeof ApprovalListView | typeof ApprovalDetailView, router: Router) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession(session)
  return mount(component, { global: { plugins: [pinia, ElementPlus, i18n, router] } })
}

describe('approval views', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    setLocale('en-US')
    approvalApi.listApprovalTasks.mockResolvedValue([metadataTask()])
    approvalApi.getApprovalTask.mockResolvedValue(metadataTask())
    approvalApi.approveApprovalTask.mockResolvedValue(undefined)
    approvalApi.rejectApprovalTask.mockResolvedValue(undefined)
  })

  it('loads metadata tasks by default, switches to record tasks with task-type tabs, and keeps typed detail links', async () => {
    approvalApi.listApprovalTasks.mockResolvedValueOnce([metadataTask()]).mockResolvedValueOnce([recordTask()])
    const router = await routerAt('/metadata/approvals')
    const wrapper = mountWithSession(ApprovalListView, router)
    await flushPromises()

    expect(approvalApi.listApprovalTasks).toHaveBeenNthCalledWith(1, 'PENDING', 'METADATA')
    expect(wrapper.text()).toContain('Metadata approvals')
    expect(wrapper.text()).toContain('Master fields')

    await wrapper.get('[data-testid="approval-task-type-record"]').trigger('click')
    await flushPromises()

    expect(approvalApi.listApprovalTasks).toHaveBeenNthCalledWith(2, 'PENDING', 'RECORD')
    expect(router.currentRoute.value.query.taskType).toBe('RECORD')
    const link = wrapper.get('tbody a')
    expect(link.attributes('href')).toContain('/metadata/approvals/92')
    expect(link.attributes('href')).toContain('taskType=RECORD')
  })

  it('shows record-specific list copy for the RECORD tab and switches it live between English and Chinese', async () => {
    approvalApi.listApprovalTasks.mockResolvedValueOnce([recordTask()])
    const router = await routerAt('/metadata/approvals?taskType=RECORD')
    const wrapper = mountWithSession(ApprovalListView, router)
    await flushPromises()

    expect(wrapper.text()).toContain('Record approvals')
    expect(wrapper.text()).toContain('Review submitted business-data changes for your department.')
    expect(wrapper.text()).toContain('Record kind')

    setLocale('zh-CN')
    await flushPromises()
    expect(wrapper.text()).toContain('业务数据审批')
    expect(wrapper.text()).toContain('审核您所在部门提交的业务数据变更。')
    expect(wrapper.text()).toContain('记录类型')
  })

  it('keeps metadata list copy for METADATA tasks and uses the metadata empty state', async () => {
    approvalApi.listApprovalTasks.mockResolvedValueOnce([])
    const router = await routerAt('/metadata/approvals')
    const wrapper = mountWithSession(ApprovalListView, router)
    await flushPromises()

    expect(wrapper.text()).toContain('Metadata approvals')
    expect(wrapper.text()).toContain('Review metadata changes for your department.')
    expect(wrapper.text()).toContain('No metadata approval tasks match this status.')
  })

  it('does not let a slow previous status response replace the current filtered list', async () => {
    const slowPending = deferred<ReturnType<typeof metadataTask>[]>()
    approvalApi.listApprovalTasks.mockReturnValueOnce(slowPending.promise).mockResolvedValueOnce([{ ...metadataTask('APPROVED'), id: 93 }])
    const router = await routerAt('/metadata/approvals')
    const wrapper = mountWithSession(ApprovalListView, router)
    await flushPromises()

    await wrapper.get('[name="status"]').setValue('APPROVED')
    await flushPromises()
    expect(wrapper.text()).toContain('#93')

    slowPending.resolve([metadataTask('PENDING')])
    await flushPromises()
    expect(wrapper.text()).toContain('#93')
    expect(wrapper.text()).not.toContain('#91')
  })

  it('shows API request IDs in list failures', async () => {
    approvalApi.listApprovalTasks.mockRejectedValue({ status: 403, message: 'Forbidden', requestId: 'req-403' })
    const router = await routerAt('/metadata/approvals')
    const wrapper = mountWithSession(ApprovalListView, router)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('req-403')
  })

  it('accepts an optional metadata approval comment, disables actions in flight, invalidates ACTIVE data, and returns to the list', async () => {
    const pending = deferred<void>()
    approvalApi.approveApprovalTask.mockReturnValue(pending.promise)
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    await wrapper.get('[name="approveComment"]').setValue('Looks correct')
    await wrapper.get('[data-testid="approve-button"]').trigger('click')

    expect(approvalApi.approveApprovalTask).toHaveBeenCalledWith(91, 'METADATA', 'Looks correct')
    expect(wrapper.get('[data-testid="approve-button"]').attributes()).toHaveProperty('disabled')
    expect(wrapper.get('[data-testid="reject-button"]').attributes()).toHaveProperty('disabled')

    pending.resolve()
    await flushPromises()
    expect(metadataApi.invalidateActiveMetadata).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('approvals')
  })

  it('requires a nonblank rejection reason and refreshes the rejected metadata task and list', async () => {
    approvalApi.getApprovalTask.mockResolvedValueOnce(metadataTask()).mockResolvedValueOnce(metadataTask('REJECTED'))
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    expect(wrapper.get('[data-testid="reject-button"]').attributes()).toHaveProperty('disabled')
    await wrapper.get('[name="rejectReason"]').setValue('Stale definition')
    await wrapper.get('[data-testid="reject-button"]').trigger('click')
    await flushPromises()

    expect(approvalApi.rejectApprovalTask).toHaveBeenCalledWith(91, 'METADATA', 'Stale definition')
    expect(approvalApi.getApprovalTask).toHaveBeenCalledTimes(2)
    expect(approvalApi.listApprovalTasks).toHaveBeenCalledWith('PENDING', 'METADATA')
    expect(wrapper.text()).toContain('Rejected')
  })

  it('keeps stale metadata actions unavailable until the 409 status refresh resolves', async () => {
    const refreshed = deferred<ReturnType<typeof metadataTask>>()
    approvalApi.getApprovalTask.mockResolvedValueOnce(metadataTask()).mockReturnValueOnce(refreshed.promise)
    approvalApi.approveApprovalTask.mockRejectedValue({ status: 409, message: 'Approval task is not pending', requestId: 'req-409' })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    await wrapper.get('[data-testid="approve-button"]').trigger('click')
    await flushPromises()

    expect(approvalApi.getApprovalTask).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="approve-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reject-button"]').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('req-409')

    refreshed.resolve(metadataTask('APPROVED'))
    await flushPromises()
    expect(wrapper.text()).toContain('Approved')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-409')
  })

  it('renders a 404 detail response as a not-found state with request ID', async () => {
    approvalApi.getApprovalTask.mockRejectedValue({ status: 404, message: 'Approval task not found', requestId: 'req-404' })
    const router = await routerAt('/metadata/approvals/404')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Approval task not found')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-404')
  })

  it('shows review audit metadata for a completed metadata task', async () => {
    approvalApi.getApprovalTask.mockResolvedValue({
      ...metadataTask('APPROVED'),
      reviewedBy: 8,
      reviewComment: 'Verified metadata',
      reviewedAt: '2026-08-04T10:00:00'
    })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    expect(wrapper.text()).toContain('Reviewed by')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('Verified metadata')
    expect(wrapper.text()).toContain('2026-08-04T10:00:00')
  })

  it('binds metadata snapshot validation to the loaded task kind and entity', async () => {
    approvalApi.getApprovalTask.mockResolvedValue({ ...metadataTask(), entityKind: 'SUB_TYPES' })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Unable to display snapshot diff')
  })

  it('clears the old task and recreates a reset action form while a reused route loads', async () => {
    const nextTask = deferred<ReturnType<typeof metadataTask>>()
    approvalApi.getApprovalTask.mockResolvedValueOnce(metadataTask()).mockReturnValueOnce(nextTask.promise)
    approvalApi.rejectApprovalTask.mockRejectedValueOnce({ status: 500, message: 'Action failed', requestId: 'req-action' })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    await wrapper.get('[name="approveComment"]').setValue('comment for 91')
    await wrapper.get('[name="rejectReason"]').setValue('reason for 91')
    await wrapper.get('[data-testid="reject-button"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('req-action')

    await router.push('/metadata/approvals/92')
    await flushPromises()

    expect(approvalApi.getApprovalTask).toHaveBeenNthCalledWith(1, 91)
    expect(approvalApi.getApprovalTask).toHaveBeenNthCalledWith(2, 92)
    expect(wrapper.text()).not.toContain('Metadata approval #91')
    expect(wrapper.find('[data-testid="approve-button"]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    nextTask.resolve({ ...metadataTask(), id: 92 })
    await flushPromises()
    expect(wrapper.text()).toContain('Metadata approval #92')
    expect((wrapper.get('[name="approveComment"]').element as HTMLTextAreaElement).value).toBe('')
    expect((wrapper.get('[name="rejectReason"]').element as HTMLTextAreaElement).value).toBe('')
  })

  it('routes record tasks through the record tab, detail query, safe diff, and independent action endpoint branch', async () => {
    approvalApi.listApprovalTasks.mockResolvedValueOnce([recordTask()])
    approvalApi.getApprovalTask.mockResolvedValueOnce(recordTask())
    const router = await routerAt('/metadata/approvals?taskType=RECORD')
    const list = mountWithSession(ApprovalListView, router)
    await flushPromises()

    expect(approvalApi.listApprovalTasks).toHaveBeenCalledWith('PENDING', 'RECORD')

    const detailRouter = await routerAt('/metadata/approvals/92?taskType=RECORD')
    const detail = mountWithSession(ApprovalDetailView, detailRouter)
    await flushPromises()

    expect(approvalApi.getApprovalTask).toHaveBeenLastCalledWith(92, 'RECORD')
    expect(detail.text()).toContain('Record approval #92')
    expect(detail.find('[data-testid="record-snapshot-diff"]').exists()).toBe(true)
    expect(detail.find('script').exists()).toBe(false)

    await detail.get('[name="approveComment"]').setValue('Ship it')
    await detail.get('[data-testid="approve-button"]').trigger('click')
    await flushPromises()
    expect(approvalApi.approveApprovalTask).toHaveBeenCalledWith(92, 'RECORD', 'Ship it')
  })

  it('shows a self-approval error for record tasks without exposing action buttons to the submitter', async () => {
    approvalApi.getApprovalTask.mockResolvedValue(recordTask('PENDING', 12))
    const router = await routerAt('/metadata/approvals/92?taskType=RECORD')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('You cannot approve your own record submission')
    expect(wrapper.find('[data-testid="approve-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reject-button"]').exists()).toBe(false)
  })

  it('refreshes stale record tasks after 409 conflicts and keeps labels bilingual', async () => {
    const refreshed = deferred<ReturnType<typeof recordTask>>()
    approvalApi.getApprovalTask.mockResolvedValueOnce(recordTask()).mockReturnValueOnce(refreshed.promise)
    approvalApi.approveApprovalTask.mockRejectedValueOnce({ status: 409, message: 'Approval task is not pending', requestId: 'req-record-409' })
    const router = await routerAt('/metadata/approvals/92?taskType=RECORD')
    const wrapper = mountWithSession(ApprovalDetailView, router)
    await flushPromises()

    expect(wrapper.text()).toContain('Record approval #92')
    await wrapper.get('[data-testid="approve-button"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('req-record-409')
    expect(wrapper.find('[data-testid="approve-button"]').exists()).toBe(false)

    refreshed.resolve(recordTask('APPROVED'))
    await flushPromises()
    expect(wrapper.text()).toContain('Approved')

    setLocale('zh-CN')
    await flushPromises()
    expect(wrapper.text()).toContain('业务数据审批')
    expect(wrapper.text()).toContain('已批准')
  })
})
