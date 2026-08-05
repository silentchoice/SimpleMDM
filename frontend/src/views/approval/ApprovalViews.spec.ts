import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApprovalDetailView from './ApprovalDetailView.vue'
import ApprovalListView from './ApprovalListView.vue'
import { i18n, setLocale } from '../../i18n'

const approvalApi = vi.hoisted(() => ({
  listApprovalTasks: vi.fn(), getApprovalTask: vi.fn(), approveApprovalTask: vi.fn(), rejectApprovalTask: vi.fn()
}))
const metadataApi = vi.hoisted(() => ({ invalidateActiveMetadata: vi.fn() }))
vi.mock('../../api/approval', () => approvalApi)
vi.mock('../../api/metadata', () => metadataApi)

function snapshot(definitions: unknown[] = []): string {
  return JSON.stringify({ schemaVersion: 1, departmentId: 3, templateId: 41, entityKind: 'MASTER_FIELDS', baseFingerprint: 'a'.repeat(64), orderedDefinitions: definitions })
}
function task(status = 'PENDING') {
  const after = {
    id: 0, ownerTypeId: 41, code: 'SERIAL', displayName: 'Serial', fieldType: 'TEXT',
    required: false, options: [], shared: false, sortOrder: 0, status: 'ACTIVE'
  }
  return { id: 91, entityKind: 'MASTER_FIELDS', entityId: 41, status, beforeSnapshot: snapshot(), afterSnapshot: snapshot([after]), submittedBy: 12, reviewedBy: null, reviewComment: null, submittedAt: '2026-08-04T09:30:00', reviewedAt: null }
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

describe('metadata approval views', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setLocale('zh-CN')
    approvalApi.listApprovalTasks.mockResolvedValue([task()])
    approvalApi.getApprovalTask.mockResolvedValue(task())
    approvalApi.approveApprovalTask.mockResolvedValue(undefined)
    approvalApi.rejectApprovalTask.mockResolvedValue(undefined)
  })

  it('loads the pending list and refreshes it when the status filter changes', async () => {
    const router = await routerAt('/metadata/approvals')
    const wrapper = mount(ApprovalListView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    expect(approvalApi.listApprovalTasks).toHaveBeenNthCalledWith(1, 'PENDING')
    expect(wrapper.text()).toContain('主字段')
    expect(wrapper.text()).toContain('元数据审批')
    expect(wrapper.get('[name="status"] option').text()).toBe('待审批')
    expect(wrapper.get('[name="status"] option').attributes('value')).toBe('PENDING')
    await wrapper.get('[name="status"]').setValue('APPROVED')
    await flushPromises()
    expect(approvalApi.listApprovalTasks).toHaveBeenNthCalledWith(2, 'APPROVED')
  })

  it('does not let a slow previous status response replace the current filtered list', async () => {
    const slowPending = deferred<ReturnType<typeof task>[]>()
    approvalApi.listApprovalTasks.mockReturnValueOnce(slowPending.promise).mockResolvedValueOnce([{ ...task('APPROVED'), id: 92 }])
    const router = await routerAt('/metadata/approvals')
    const wrapper = mount(ApprovalListView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    await wrapper.get('[name="status"]').setValue('APPROVED')
    await flushPromises()
    expect(wrapper.text()).toContain('#92')

    slowPending.resolve([task('PENDING')])
    await flushPromises()
    expect(wrapper.text()).toContain('#92')
    expect(wrapper.text()).not.toContain('#91')
  })

  it('shows API request IDs in list failures', async () => {
    approvalApi.listApprovalTasks.mockRejectedValue({ status: 403, message: 'Forbidden', requestId: 'req-403' })
    const router = await routerAt('/metadata/approvals')
    const wrapper = mount(ApprovalListView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('req-403')
  })

  it('accepts an optional approval comment, disables actions in flight, invalidates ACTIVE data, and returns to the list', async () => {
    const pending = deferred<void>()
    approvalApi.approveApprovalTask.mockReturnValue(pending.promise)
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()
    await wrapper.get('[name="approveComment"]').setValue('Looks correct')
    await wrapper.get('[data-testid="approve-button"]').trigger('click')

    expect(approvalApi.approveApprovalTask).toHaveBeenCalledWith(91, 'Looks correct')
    expect(wrapper.get('[data-testid="approve-button"]').attributes()).toHaveProperty('disabled')
    expect(wrapper.get('[data-testid="reject-button"]').attributes()).toHaveProperty('disabled')
    pending.resolve()
    await flushPromises()
    expect(metadataApi.invalidateActiveMetadata).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('approvals')
  })

  it('requires a nonblank rejection reason and refreshes the rejected task and list', async () => {
    approvalApi.getApprovalTask.mockResolvedValueOnce(task()).mockResolvedValueOnce(task('REJECTED'))
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    expect(wrapper.get('[data-testid="reject-button"]').attributes()).toHaveProperty('disabled')
    await wrapper.get('[name="rejectReason"]').setValue('Stale definition')
    await wrapper.get('[data-testid="reject-button"]').trigger('click')
    await flushPromises()

    expect(approvalApi.rejectApprovalTask).toHaveBeenCalledWith(91, 'Stale definition')
    expect(approvalApi.getApprovalTask).toHaveBeenCalledTimes(2)
    expect(approvalApi.listApprovalTasks).toHaveBeenCalledWith('PENDING')
    expect(wrapper.text()).toContain('已拒绝')
  })

  it('keeps stale actions unavailable until the 409 status refresh resolves', async () => {
    const refreshed = deferred<ReturnType<typeof task>>()
    approvalApi.getApprovalTask.mockResolvedValueOnce(task()).mockReturnValueOnce(refreshed.promise)
    approvalApi.approveApprovalTask.mockRejectedValue({ status: 409, message: 'Approval task is not pending', requestId: 'req-409' })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()
    await wrapper.get('[data-testid="approve-button"]').trigger('click')
    await flushPromises()

    expect(approvalApi.getApprovalTask).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="approve-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reject-button"]').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('req-409')

    refreshed.resolve(task('APPROVED'))
    await flushPromises()
    expect(wrapper.text()).toContain('已批准')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-409')
  })

  it('renders a 404 detail response as a not-found state with request ID', async () => {
    approvalApi.getApprovalTask.mockRejectedValue({ status: 404, message: 'Approval task not found', requestId: 'req-404' })
    const router = await routerAt('/metadata/approvals/404')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Approval task not found')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-404')
  })

  it('shows review audit metadata for a completed task', async () => {
    approvalApi.getApprovalTask.mockResolvedValue({
      ...task('APPROVED'), reviewedBy: 8, reviewComment: 'Verified metadata', reviewedAt: '2026-08-04T10:00:00'
    })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('审核人')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('Verified metadata')
    expect(wrapper.text()).toContain('2026-08-04T10:00:00')
  })

  it('binds snapshot validation to the loaded task kind and entity', async () => {
    approvalApi.getApprovalTask.mockResolvedValue({ ...task(), entityKind: 'SUB_TYPES' })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('无法显示快照差异')
  })

  it('clears the old task and recreates a reset action form while a reused route loads', async () => {
    const nextTask = deferred<ReturnType<typeof task>>()
    approvalApi.getApprovalTask.mockResolvedValueOnce(task()).mockReturnValueOnce(nextTask.promise)
    approvalApi.rejectApprovalTask.mockRejectedValueOnce({ status: 500, message: 'Action failed', requestId: 'req-action' })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
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
    expect(wrapper.text()).not.toContain('元数据审批 #91')
    expect(wrapper.find('[data-testid="approve-button"]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    nextTask.resolve({ ...task(), id: 92 })
    await flushPromises()
    expect(wrapper.text()).toContain('元数据审批 #92')
    expect((wrapper.get('[name="approveComment"]').element as HTMLTextAreaElement).value).toBe('')
    expect((wrapper.get('[name="rejectReason"]').element as HTMLTextAreaElement).value).toBe('')
  })

  it('switches list, audit, action, and status labels to English without changing filter payloads', async () => {
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, i18n, router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('元数据审批 #91')
    expect(wrapper.text()).toContain('批准')
    expect(wrapper.text()).toContain('拒绝原因')
    expect(wrapper.text()).toContain('待审批')

    setLocale('en-US')
    await flushPromises()
    expect(wrapper.text()).toContain('Metadata approval #91')
    expect(wrapper.text()).toContain('Approve')
    expect(wrapper.text()).toContain('Rejection reason')
    expect(wrapper.text()).toContain('Pending')

    const listRouter = await routerAt('/metadata/approvals')
    const list = mount(ApprovalListView, { global: { plugins: [ElementPlus, i18n, listRouter] } })
    await flushPromises()
    await list.get('[name="status"]').setValue('APPROVED')
    await flushPromises()
    expect(approvalApi.listApprovalTasks).toHaveBeenLastCalledWith('APPROVED')
  })
})
