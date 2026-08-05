import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn() }
}))

vi.mock('./http', () => ({ http }))

import {
  assignDepartment, createMasterType, currentMasterType, listMasterFields, listMasterTypes, listSubFields, listSubTypes,
  submitMasterFields, submitSubFields, submitSubTypes, type FieldSubmission, type SubTypeSubmission
} from './metadata'

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

  it('reads the authenticated department assignment without a caller-supplied department ID', async () => {
    http.get.mockResolvedValue({ id: 41, code: 'ASSET', name: 'Asset', status: 'ACTIVE' })

    await currentMasterType()

    expect(http.get).toHaveBeenCalledWith('/master-type/current')
  })

  it('reads the ordered active structure from each owner path', async () => {
    http.get.mockResolvedValue([])

    await listMasterFields(41)
    await listSubTypes(41)
    await listSubFields(55)

    expect(http.get).toHaveBeenNthCalledWith(1, '/master-field/41')
    expect(http.get).toHaveBeenNthCalledWith(2, '/sub-type/41')
    expect(http.get).toHaveBeenNthCalledWith(3, '/sub-field/55')
  })

  it('submits each ordered entity family through its authoritative owner path', async () => {
    const field: FieldSubmission = {
      code: 'SERIAL', displayName: 'Serial number', fieldType: 'TEXT', required: true,
      options: [], shared: false, sortOrder: 0
    }
    const subType: SubTypeSubmission = { code: 'ACCESSORY', name: 'Accessory' }
    http.post.mockResolvedValue({ approvalTaskId: 701 })

    await submitMasterFields(41, [field])
    await submitSubTypes(41, [subType])
    await submitSubFields(55, [{ ...field, shared: true }])

    expect(http.post).toHaveBeenNthCalledWith(1, '/master-field/41', [field])
    expect(http.post).toHaveBeenNthCalledWith(2, '/sub-type/41', [subType])
    expect(http.post).toHaveBeenNthCalledWith(3, '/sub-field/55', [{ ...field, shared: true }])
  })
})
