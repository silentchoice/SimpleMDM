import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

vi.mock('./http', () => ({ http }))

import {
  acquireRecordLock,
  copyRecordDraft,
  createRecordDraft,
  getRecord,
  getRecordDraft,
  listRecordHistory,
  listRecords,
  releaseRecordLock,
  renewRecordLock,
  requestRecordDeletion,
  submitRecordDraft,
  updateRecordDraft,
  type EditLock,
  type Paged,
  type RecordDetail,
  type RecordDraft,
  type RecordDraftCommand,
  type RecordSummary
} from './records'

const record: RecordDetail = {
  id: 81,
  masterTypeId: 9,
  departmentId: 7,
  recordCode: 'CUS-20260805-0001',
  masterValues: { name: 'North' },
  children: [{ subTypeId: 55, rows: [{ id: 301, rowOrder: 0, values: { lineName: 'Primary' } }] }],
  version: 3,
  status: 'ACTIVE'
}

const draftCommand: RecordDraftCommand = {
  recordId: null,
  masterTypeId: 9,
  baseVersion: 0,
  action: 'CREATE',
  masterValues: { name: 'North' },
  children: [{ subTypeId: 55, rows: [{ recordId: null, rowOrder: 0, values: { lineName: 'Primary' } }] }],
  deleteReason: null
}

const draft: RecordDraft = {
  id: 91,
  recordId: null,
  masterTypeId: 9,
  departmentId: 7,
  recordCode: 'CUS-20260805-0001',
  action: 'CREATE',
  baseVersion: 0,
  masterValues: { name: 'North' },
  children: [{ subTypeId: 55, rows: [{ recordId: null, rowOrder: 0, values: { lineName: 'Primary' } }] }],
  status: 'DRAFT',
  createdBy: 12,
  deleteReason: null
}

const page: Paged<RecordSummary> = {
  content: [record],
  number: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1
}

const lock: EditLock = {
  recordId: 81,
  departmentId: 7,
  userId: 12,
  displayName: 'Editor',
  token: 'owner-token',
  expiresAt: '2026-08-05T08:30:00Z'
}

describe('record API client', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('lists records with the exact paged query contract', async () => {
    http.get.mockResolvedValue(page)

    await expect(listRecords({
      masterTypeId: 9,
      recordCode: 'CUS',
      keyword: 'North',
      status: 'ACTIVE',
      updatedFrom: '2026-08-01T00:00:00',
      updatedTo: '2026-08-31T23:59:59',
      includeDeleted: true,
      page: 2,
      size: 50,
      sortBy: 'recordCode',
      sortDirection: 'asc'
    })).resolves.toEqual(page)

    expect(http.get).toHaveBeenCalledWith('/master-record?masterTypeId=9&recordCode=CUS&keyword=North&status=ACTIVE&updatedFrom=2026-08-01T00%3A00%3A00&updatedTo=2026-08-31T23%3A59%3A59&includeDeleted=true&page=2&size=50&sortBy=recordCode&sortDirection=asc')
  })

  it('loads one record and its bounded history by authoritative path ID', async () => {
    http.get.mockResolvedValueOnce(record).mockResolvedValueOnce([record, { ...record, version: 2 }, { ...record, version: 1 }])

    await expect(getRecord(81)).resolves.toEqual(record)
    await expect(listRecordHistory(81)).resolves.toEqual([record, { ...record, version: 2 }, { ...record, version: 1 }])

    expect(http.get).toHaveBeenNthCalledWith(1, '/master-record/81')
    expect(http.get).toHaveBeenNthCalledWith(2, '/master-record/81/history')
  })

  it('creates, updates, reads, copies, submits, and logically deletes drafts through their exact endpoints', async () => {
    http.post.mockResolvedValueOnce(draft).mockResolvedValueOnce({ ...draft, id: 92 }).mockResolvedValueOnce(undefined).mockResolvedValueOnce({ ...draft, id: 93, action: 'DELETE', deleteReason: 'Duplicate supplier' })
    http.put.mockResolvedValue(draft)
    http.get.mockResolvedValue(draft)

    await expect(createRecordDraft(draftCommand)).resolves.toEqual(draft)
    await expect(updateRecordDraft(91, draftCommand)).resolves.toEqual(draft)
    await expect(getRecordDraft(91)).resolves.toEqual(draft)
    await expect(copyRecordDraft(91)).resolves.toEqual({ ...draft, id: 92 })
    await expect(submitRecordDraft(91)).resolves.toBeUndefined()
    await expect(requestRecordDeletion(81, 'Duplicate supplier')).resolves.toEqual({ ...draft, id: 93, action: 'DELETE', deleteReason: 'Duplicate supplier' })

    expect(http.post).toHaveBeenNthCalledWith(1, '/master-record-draft', draftCommand)
    expect(http.put).toHaveBeenCalledWith('/master-record-draft/91', draftCommand)
    expect(http.get).toHaveBeenCalledWith('/master-record-draft/91')
    expect(http.post).toHaveBeenNthCalledWith(2, '/master-record-draft/91/copy')
    expect(http.post).toHaveBeenNthCalledWith(3, '/master-record-draft/91/submit')
    expect(http.post).toHaveBeenNthCalledWith(4, '/master-record/81/delete-request', { reason: 'Duplicate supplier' })
  })

  it('acquires, renews, and releases edit locks using the path record ID and token-only body', async () => {
    http.post.mockResolvedValue(lock)
    http.put.mockResolvedValue(lock)
    http.delete.mockResolvedValue(undefined)

    await expect(acquireRecordLock(81)).resolves.toEqual(lock)
    await expect(renewRecordLock(81, 'owner-token')).resolves.toEqual(lock)
    await expect(releaseRecordLock(81, 'owner-token')).resolves.toBeUndefined()

    expect(http.post).toHaveBeenCalledWith('/master-record/81/lock')
    expect(http.put).toHaveBeenCalledWith('/master-record/81/lock', { token: 'owner-token' })
    expect(http.delete).toHaveBeenCalledWith('/master-record/81/lock', { token: 'owner-token' })
  })
})
