import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn() }
}))

vi.mock('./http', () => ({ http }))

import { assignDepartment, createMasterType, listMasterTypes } from './metadata'

describe('metadata API client', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('uses the exact master-type collection endpoints', async () => {
    http.get.mockResolvedValue([])
    http.post.mockResolvedValue({ id: 7, code: 'ASSET', name: 'Asset', status: 'ACTIVE' })

    await listMasterTypes()
    await createMasterType({ code: 'ASSET', name: 'Asset' })

    expect(http.get).toHaveBeenCalledWith('/master-type')
    expect(http.post).toHaveBeenCalledWith('/master-type', { code: 'ASSET', name: 'Asset' })
  })

  it('keeps assignment IDs authoritative in the URL and sends no body', async () => {
    http.put.mockResolvedValue(undefined)

    await assignDepartment(7, 3)

    expect(http.put).toHaveBeenCalledWith('/master-type/7/departments/3')
  })
})
