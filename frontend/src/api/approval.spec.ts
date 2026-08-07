import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: { get: vi.fn(), post: vi.fn() }
}))

vi.mock('./http', () => ({ http }))

import {
  approveApprovalTask,
  getApprovalTask,
  listApprovalTasks,
  rejectApprovalTask,
  type ApprovalTask
} from './approval'

const task: ApprovalTask = {
  id: 91,
  taskType: 'METADATA',
  entityKind: 'MASTER_FIELDS',
  entityId: 41,
  status: 'PENDING',
  beforeSnapshot: '{"schemaVersion":1,"orderedDefinitions":[]}',
  afterSnapshot: '{"schemaVersion":1,"orderedDefinitions":[]}',
  submittedBy: 12,
  reviewedBy: null,
  reviewComment: null,
  submittedAt: '2026-08-04T09:30:00',
  reviewedAt: null
}

describe('metadata approval API client', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('lists current-department tasks using the selected status query', async () => {
    http.get.mockResolvedValue([task])

    await expect(listApprovalTasks('APPROVED')).resolves.toEqual([task])

    expect(http.get).toHaveBeenCalledWith('/metadata-approval?status=APPROVED')
  })

  it('loads one task by its authoritative path ID', async () => {
    http.get.mockResolvedValue(task)

    await expect(getApprovalTask(91)).resolves.toEqual(task)

    expect(http.get).toHaveBeenCalledWith('/metadata-approval/91')
  })

  it('passes an explicit record task type without changing the path authority', async () => {
    const recordTask = { ...task, id: 92, taskType: 'RECORD', entityKind: 'RECORD', entityId: 81 } as const
    http.get.mockResolvedValueOnce([recordTask]).mockResolvedValueOnce(recordTask)

    await expect(listApprovalTasks('PENDING', 'RECORD')).resolves.toEqual([recordTask])
    await expect(getApprovalTask(92, 'RECORD')).resolves.toEqual(recordTask)

    expect(http.get).toHaveBeenNthCalledWith(1, '/metadata-approval?status=PENDING&taskType=RECORD')
    expect(http.get).toHaveBeenNthCalledWith(2, '/metadata-approval/92?taskType=RECORD')
  })

  it('sends an optional approval comment as a nullable body field', async () => {
    http.post.mockResolvedValue(undefined)

    await approveApprovalTask(91, 'Looks correct')
    await approveApprovalTask(92)

    expect(http.post).toHaveBeenNthCalledWith(1, '/metadata-approval/91/approve', { comment: 'Looks correct' })
    expect(http.post).toHaveBeenNthCalledWith(2, '/metadata-approval/92/approve', { comment: null })
  })

  it('requires a nonblank rejection reason before making the request', async () => {
    await expect(rejectApprovalTask(91, '   ')).rejects.toMatchObject({ message: 'Rejection reason is required' })

    expect(http.post).not.toHaveBeenCalled()
  })

  it('sends the required rejection reason', async () => {
    http.post.mockResolvedValue(undefined)

    await rejectApprovalTask(91, 'Stale definition')

    expect(http.post).toHaveBeenCalledWith('/metadata-approval/91/reject', { reason: 'Stale definition' })
  })

  it.each([
    ['list', { code: 403, status: 403, message: 'Forbidden', requestId: 'req-403' }],
    ['detail', { code: 404, status: 404, message: 'Approval task not found', requestId: 'req-404' }],
    ['approve', { code: 409, status: 409, message: 'Approval task is not pending', requestId: 'req-409' }]
  ] as const)('preserves the %s error and request ID from the shared HTTP boundary', async (operation, error) => {
    http.get.mockRejectedValue(error)
    http.post.mockRejectedValue(error)

    const request = operation === 'list'
      ? listApprovalTasks('PENDING')
      : operation === 'detail'
        ? getApprovalTask(91)
        : approveApprovalTask(91)

    await expect(request).rejects.toEqual(error)
  })
})
