import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '../utils/request'
import { createRecord, updateRecord } from './mdm'
import { distributeRecord, getPushLog, retryPushLog } from './integration'

vi.mock('../utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

describe('master-child and distribution api contracts', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps snake-case master-child request bodies unchanged', () => {
    const body = { operation: 'UPDATE', object_code: 'person', record_id: 7, expected_version: 3, department_id: 10, data: {}, children: [] }
    updateRecord('person', body)
    expect(request.put).toHaveBeenCalledWith('/mdm/object-types/person/records', body)
  })

  it('posts create requests to the object-code route', () => {
    const body = { operation: 'CREATE', object_code: 'person', record_code: 'P001', department_id: 10, data: {}, children: [] }
    createRecord('person', body)
    expect(request.post).toHaveBeenCalledWith('/mdm/object-types/person/records', body)
  })

  it('uses the backend manual distribution, detail and retry routes', () => {
    distributeRecord(9, { reason: '手动同步最新快照' })
    getPushLog(11)
    retryPushLog(11, { reason: '失败重试' })
    expect(request.post).toHaveBeenCalledWith('/integration/records/9/distribute', { reason: '手动同步最新快照' })
    expect(request.get).toHaveBeenCalledWith('/integration/logs/11')
    expect(request.post).toHaveBeenCalledWith('/integration/logs/11/retry', { reason: '失败重试' })
  })
})
