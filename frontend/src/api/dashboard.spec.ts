import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: { get: vi.fn() }
}))

vi.mock('./http', () => ({ http }))

import { getDashboardSummary, type DashboardSummary } from './dashboard'

const summary: DashboardSummary = {
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
    }
  ]
}

describe('dashboard API client', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('loads the single summary endpoint for role-scoped dashboard metrics and recent tasks', async () => {
    http.get.mockResolvedValue(summary)

    await expect(getDashboardSummary()).resolves.toEqual(summary)

    expect(http.get).toHaveBeenCalledWith('/dashboard/summary')
  })
})
