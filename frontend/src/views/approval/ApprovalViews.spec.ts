import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApprovalDetailView from './ApprovalDetailView.vue'
import ApprovalListView from './ApprovalListView.vue'

const approvalApi = vi.hoisted(() => ({
  listApprovalTasks: vi.fn(), getApprovalTask: vi.fn(), approveApprovalTask: vi.fn(), rejectApprovalTask: vi.fn()
}))
const metadataApi = vi.hoisted(() => ({ invalidateActiveMetadata: vi.fn() }))
vi.mock('../../api/approval', () => approvalApi)
vi.mock('../../api/metadata', () => metadataApi)

function snapshot(definitions: unknown[] = []): string {
  return JSON.stringify({ schemaVersion: 1, departmentId: 3, templateId: 41, entityKind: 'MASTER_FIELDS', baseFingerprint: 'fp', orderedDefinitions: definitions })
}
function task(status = 'PENDING') {
  return { id: 91, entityKind: 'MASTER_FIELDS', entityId: 41, status, beforeSnapshot: snapshot(), afterSnapshot: snapshot([{ code: 'SERIAL', displayName: 'Serial' }]), submittedBy: 12, reviewedBy: null, reviewComment: null, submittedAt: '2026-08-04T09:30:00', reviewedAt: null }
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
    approvalApi.listApprovalTasks.mockResolvedValue([task()])
    approvalApi.getApprovalTask.mockResolvedValue(task())
    approvalApi.approveApprovalTask.mockResolvedValue(undefined)
    approvalApi.rejectApprovalTask.mockResolvedValue(undefined)
  })

  it('loads the pending list and refreshes it when the status filter changes', async () => {
    const router = await routerAt('/metadata/approvals')
    const wrapper = mount(ApprovalListView, { global: { plugins: [ElementPlus, router] } })
    await flushPromises()

    expect(approvalApi.listApprovalTasks).toHaveBeenNthCalledWith(1, 'PENDING')
    expect(wrapper.text()).toContain('MASTER_FIELDS')
    await wrapper.get('[name="status"]').setValue('APPROVED')
    await flushPromises()
    expect(approvalApi.listApprovalTasks).toHaveBeenNthCalledWith(2, 'APPROVED')
  })

  it('shows API request IDs in list failures', async () => {
    approvalApi.listApprovalTasks.mockRejectedValue({ status: 403, message: 'Forbidden', requestId: 'req-403' })
    const router = await routerAt('/metadata/approvals')
    const wrapper = mount(ApprovalListView, { global: { plugins: [ElementPlus, router] } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('req-403')
  })

  it('accepts an optional approval comment, disables actions in flight, invalidates ACTIVE data, and returns to the list', async () => {
    const pending = deferred<void>()
    approvalApi.approveApprovalTask.mockReturnValue(pending.promise)
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, router] } })
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
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, router] } })
    await flushPromises()

    expect(wrapper.get('[data-testid="reject-button"]').attributes()).toHaveProperty('disabled')
    await wrapper.get('[name="rejectReason"]').setValue('Stale definition')
    await wrapper.get('[data-testid="reject-button"]').trigger('click')
    await flushPromises()

    expect(approvalApi.rejectApprovalTask).toHaveBeenCalledWith(91, 'Stale definition')
    expect(approvalApi.getApprovalTask).toHaveBeenCalledTimes(2)
    expect(approvalApi.listApprovalTasks).toHaveBeenCalledWith('PENDING')
    expect(wrapper.text()).toContain('REJECTED')
  })

  it('refreshes task status and retains the request ID when an action returns 409', async () => {
    approvalApi.getApprovalTask.mockResolvedValueOnce(task()).mockResolvedValueOnce(task('APPROVED'))
    approvalApi.approveApprovalTask.mockRejectedValue({ status: 409, message: 'Approval task is not pending', requestId: 'req-409' })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, router] } })
    await flushPromises()
    await wrapper.get('[data-testid="approve-button"]').trigger('click')
    await flushPromises()

    expect(approvalApi.getApprovalTask).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('APPROVED')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-409')
  })

  it('renders a 404 detail response as a not-found state with request ID', async () => {
    approvalApi.getApprovalTask.mockRejectedValue({ status: 404, message: 'Approval task not found', requestId: 'req-404' })
    const router = await routerAt('/metadata/approvals/404')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, router] } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Approval task not found')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-404')
  })

  it('shows review audit metadata for a completed task', async () => {
    approvalApi.getApprovalTask.mockResolvedValue({
      ...task('APPROVED'), reviewedBy: 8, reviewComment: 'Verified metadata', reviewedAt: '2026-08-04T10:00:00'
    })
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Reviewed by')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('Verified metadata')
    expect(wrapper.text()).toContain('2026-08-04T10:00:00')
  })

  it('reloads the reused detail component when the route task ID changes', async () => {
    approvalApi.getApprovalTask.mockImplementation(async (taskId: number) => ({ ...task(), id: taskId }))
    const router = await routerAt('/metadata/approvals/91')
    const wrapper = mount(ApprovalDetailView, { global: { plugins: [ElementPlus, router] } })
    await flushPromises()

    await router.push('/metadata/approvals/92')
    await flushPromises()

    expect(approvalApi.getApprovalTask).toHaveBeenNthCalledWith(1, 91)
    expect(approvalApi.getApprovalTask).toHaveBeenNthCalledWith(2, 92)
    expect(wrapper.text()).toContain('Metadata approval #92')
  })
})
