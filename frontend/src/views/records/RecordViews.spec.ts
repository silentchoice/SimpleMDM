import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppRouter } from '../../router'
import { i18n, setLocale } from '../../i18n'
import { useAuthStore } from '../../stores/auth'
import type { Session } from '../../types'

const metadataApi = vi.hoisted(() => ({
  currentMasterType: vi.fn(),
  listMasterFields: vi.fn(),
  listSubTypes: vi.fn(),
  listSubFields: vi.fn()
}))
const recordsApi = vi.hoisted(() => ({
  listRecords: vi.fn(),
  listRecordDrafts: vi.fn(),
  copyRecordDraft: vi.fn(),
  getRecord: vi.fn(),
  listRecordHistory: vi.fn(),
  createRecordDraft: vi.fn(),
  requestRecordDeletion: vi.fn()
}))

vi.mock('../../api/metadata', () => metadataApi)
vi.mock('../../api/records', () => recordsApi)

const session: Session = {
  accessToken: 'token',
  user: { id: 12, username: 'editor', displayName: 'Editor' },
  roles: ['DEPT_EDITOR'],
  department: { id: 7, code: 'OPS', name: 'Operations' }
}

const masterType = { id: 41, code: 'ASSET', name: 'Asset', status: 'ACTIVE' }
const masterFields = [
  { id: 101, ownerTypeId: 41, code: 'name', displayName: 'Name', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' },
  { id: 102, ownerTypeId: 41, code: 'enabled', displayName: 'Enabled', fieldType: 'SWITCH', required: false, options: [], shared: false, sortOrder: 1, status: 'ACTIVE' }
]
const subTypes = [{ id: 301, masterTypeId: 41, code: 'CONTACT', name: 'Contacts', status: 'ACTIVE' }]
const subFields = [{ id: 401, ownerTypeId: 301, code: 'email', displayName: 'Email', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }]
const page = {
  content: [
    {
      id: 81,
      masterTypeId: 41,
      departmentId: 7,
      recordCode: 'AST-0001',
      masterValues: { name: 'Laptop fleet', enabled: true, retiredField: 'legacy master' },
      children: [{ subTypeId: 301, rows: [{ id: 900, rowOrder: 0,
        values: { email: 'ops@example.com', retiredChild: 'legacy child' } }] }],
      version: 4,
      status: 'ACTIVE'
    }
  ],
  page: 0,
  size: 20,
  totalElements: 31,
  totalPages: 2
}
const record = page.content[0]
const history = [
  record,
  { ...record, version: 3, masterValues: { ...record.masterValues, name: 'Laptop fleet v3' },
    children: [{ subTypeId: 301, rows: [{ id: 900, rowOrder: 0,
      values: { email: 'old@example.com', retiredChild: 'old child' } }] }] },
  { ...record, version: 2, masterValues: { ...record.masterValues, name: 'Laptop fleet v2' } },
  { ...record, version: 1, masterValues: { ...record.masterValues, name: 'Laptop fleet v1' } }
]

const RouterHost = { template: '<router-view />' }

async function mountAt(path: string, currentSession: Session = session) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession(currentSession)
  const router = createAppRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(RouterHost, { global: { plugins: [pinia, ElementPlus, i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('record list and detail views', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    setLocale('en-US')
    metadataApi.currentMasterType.mockResolvedValue(masterType)
    metadataApi.listMasterFields.mockResolvedValue(masterFields)
    metadataApi.listSubTypes.mockResolvedValue(subTypes)
    metadataApi.listSubFields.mockResolvedValue(subFields)
    recordsApi.listRecords.mockResolvedValue(page)
    recordsApi.listRecordDrafts.mockResolvedValue([
      { id: 91, recordId: null, masterTypeId: 41, departmentId: 7,
        recordCode: 'AST-DRAFT', action: 'CREATE', baseVersion: 0,
        masterValues: { name: 'Draft asset' }, children: [], status: 'DRAFT',
        createdBy: 12, deleteReason: null },
      { id: 92, recordId: 81, masterTypeId: 41, departmentId: 7,
        recordCode: 'AST-0001', action: 'UPDATE', baseVersion: 4,
        masterValues: { name: 'Rejected asset' }, children: [], status: 'REJECTED',
        createdBy: 12, deleteReason: null }
    ])
    recordsApi.copyRecordDraft.mockResolvedValue({ id: 93 })
    recordsApi.getRecord.mockResolvedValue(record)
    recordsApi.listRecordHistory.mockResolvedValue(history)
    recordsApi.createRecordDraft.mockResolvedValue({ id: 91 })
    recordsApi.requestRecordDeletion.mockResolvedValue({ id: 92 })
  })

  it('renders metadata-driven columns, submits filters and pagination, and exposes editor actions only to editors', async () => {
    const { wrapper } = await mountAt('/records')

    expect(metadataApi.currentMasterType).toHaveBeenCalledTimes(1)
    expect(metadataApi.listMasterFields).toHaveBeenCalledWith(41)
    expect(recordsApi.listRecords).toHaveBeenNthCalledWith(1, {
      masterTypeId: 41,
      includeDeleted: false,
      page: 0,
      size: 20,
      sortBy: 'updatedAt',
      sortDirection: 'desc'
    })
    expect(wrapper.text()).toContain('Business Data')
    expect(wrapper.text()).toContain('Record code')
    expect(wrapper.text()).toContain('Keyword')
    expect(wrapper.text()).toContain('Status')
    expect(wrapper.text()).toContain('All')
    expect(wrapper.text()).toContain('Include deleted')
    expect(wrapper.text()).toContain('Search')
    expect(wrapper.text()).toContain('Name')
    expect(wrapper.text()).toContain('Enabled')
    expect(wrapper.text()).toContain('AST-0001')
    expect(wrapper.get('[name="recordCode"]').attributes('value')).toBe('')
    await wrapper.get('[name="recordCode"]').setValue('AST')
    await wrapper.get('[name="keyword"]').setValue('Laptop')
    await wrapper.get('[name="status"]').setValue('ACTIVE')
    await wrapper.get('[name="includeDeleted"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(recordsApi.listRecords).toHaveBeenNthCalledWith(2, {
      masterTypeId: 41,
      recordCode: 'AST',
      keyword: 'Laptop',
      status: 'ACTIVE',
      includeDeleted: true,
      page: 0,
      size: 20,
      sortBy: 'updatedAt',
      sortDirection: 'desc'
    })

    await wrapper.get('[data-testid="page-next"]').trigger('click')
    await flushPromises()
    expect(recordsApi.listRecords).toHaveBeenNthCalledWith(3, {
      masterTypeId: 41,
      recordCode: 'AST',
      keyword: 'Laptop',
      status: 'ACTIVE',
      includeDeleted: true,
      page: 1,
      size: 20,
      sortBy: 'updatedAt',
      sortDirection: 'desc'
    })
    expect(wrapper.get('[data-testid="record-create"]').text()).toContain('Create draft')
    expect(wrapper.get('[data-testid="record-view-81"]').text()).toContain('View')

    const viewer = await mountAt('/records', { ...session, roles: ['DEPT_VIEWER'] })
    expect(viewer.wrapper.find('[data-testid="record-create"]').exists()).toBe(false)
    expect(viewer.wrapper.find('[data-testid="record-delete-81"]').exists()).toBe(false)
    expect(viewer.wrapper.get('[data-testid="record-view-81"]').text()).toContain('View')
    expect(recordsApi.listRecordDrafts).toHaveBeenCalledTimes(1)
  })

  it('localizes record filter labels and actions in Chinese by default and switches them back to English', async () => {
    setLocale('zh-CN')
    const { wrapper } = await mountAt('/records')

    expect(wrapper.text()).toContain('业务数据')
    expect(wrapper.text()).toContain('编码')
    expect(wrapper.text()).toContain('关键字')
    expect(wrapper.text()).toContain('状态')
    expect(wrapper.text()).toContain('全部')
    expect(wrapper.text()).toContain('包含已删除')
    expect(wrapper.text()).toContain('查询')
    expect(wrapper.get('[name="status"]').findAll('option').map((option) => option.text()))
      .toEqual(['全部', '启用', '已删除'])

    setLocale('en-US')
    await flushPromises()
    expect(wrapper.text()).toContain('Business Data')
    expect(wrapper.text()).toContain('Record code')
    expect(wrapper.text()).toContain('Keyword')
    expect(wrapper.text()).toContain('Status')
    expect(wrapper.text()).toContain('All')
    expect(wrapper.text()).toContain('Include deleted')
    expect(wrapper.text()).toContain('Search')
    expect(wrapper.get('[name="status"]').findAll('option').map((option) => option.text()))
      .toEqual(['All', 'Active', 'Deleted'])
  })

  it('opens an unpersisted create editor without posting placeholder field values', async () => {
    const { wrapper, router } = await mountAt('/records')

    await wrapper.get('[data-testid="record-create"]').trigger('click')
    await flushPromises()

    expect(recordsApi.createRecordDraft).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/records/new')
  })

  it('lists current-user drafts for resume and copies a rejected draft into a safe new route', async () => {
    const { wrapper, router } = await mountAt('/records')

    expect(recordsApi.listRecordDrafts).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="draft-resume-91"]').attributes('href'))
        .toContain('/records/drafts/91')
    expect(wrapper.text()).toContain('AST-DRAFT')
    await wrapper.get('[data-testid="draft-copy-92"]').trigger('click')
    await flushPromises()

    expect(recordsApi.copyRecordDraft).toHaveBeenCalledWith(92)
    expect(router.currentRoute.value.fullPath).toBe('/records/drafts/93')
  })

  it('shows loading, empty, and request-id error states for the list without leaking stale results', async () => {
    let resolve!: (value: typeof page) => void
    recordsApi.listRecords.mockReturnValueOnce(new Promise((done) => { resolve = done })).mockResolvedValueOnce({ ...page, content: [], totalElements: 0, totalPages: 0 })
    const { wrapper } = await mountAt('/records')

    expect(wrapper.text()).toContain('Loading')
    resolve(page)
    await flushPromises()
    expect(wrapper.text()).toContain('AST-0001')

    await wrapper.get('[name="keyword"]').setValue('none')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('No data')

    recordsApi.listRecords.mockRejectedValueOnce({ status: 500, message: 'List failed', requestId: 'req-list' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('req-list')
  })

  it('renders current, diff, and history tabs on detail pages, keeps history to three versions, and keeps delete behind a reasoned action', async () => {
    const { wrapper, router } = await mountAt('/records/81')

    expect(recordsApi.getRecord).toHaveBeenCalledWith(81)
    expect(recordsApi.listRecordHistory).toHaveBeenCalledWith(81)
    expect(wrapper.text()).toContain('AST-0001')
    expect(wrapper.text()).toContain('Laptop fleet')
    expect(wrapper.text()).toContain('Contacts')
    expect(wrapper.text()).toContain('ops@example.com')
    expect(wrapper.text()).toContain('retiredField')
    expect(wrapper.text()).toContain('legacy master')
    expect(wrapper.text()).toContain('retiredChild')
    expect(wrapper.text()).toContain('legacy child')
    expect(wrapper.get('[data-testid="detail-tab-current"]').text()).toContain('Current')
    expect(wrapper.get('[data-testid="detail-tab-diff"]').text()).toContain('Diff')
    expect(wrapper.get('[data-testid="detail-tab-history"]').text()).toContain('History')
    expect(wrapper.get('[data-testid="record-edit-81"]').text()).toContain('Edit')
    await wrapper.get('[data-testid="detail-tab-diff"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('old@example.com')
    expect(wrapper.text()).toContain('ops@example.com')
    expect(wrapper.text()).toContain('old child')
    expect(wrapper.text()).toContain('legacy child')
    await wrapper.get('[data-testid="detail-tab-history"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid^="history-version-"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('Version 4')
    expect(wrapper.text()).toContain('Version 2')
    expect(wrapper.text()).toContain('old@example.com')
    expect(wrapper.text()).toContain('retiredChild')
    expect(wrapper.text()).not.toContain('Version 1')

    await wrapper.get('[name="deleteReason"]').setValue('Duplicate record')
    await wrapper.get('[data-testid="record-delete-81"]').trigger('click')
    await flushPromises()
    expect(recordsApi.requestRecordDeletion).toHaveBeenCalledWith(81, 'Duplicate record')
    expect(router.currentRoute.value.fullPath).toBe('/records/drafts/92')
  })

  it('renders detail request ids and empty fallbacks when loading the current record fails or no active metadata is assigned', async () => {
    recordsApi.getRecord.mockRejectedValueOnce({ status: 404, message: 'Record not found', requestId: 'req-404' })
    const missing = await mountAt('/records/81')
    expect(missing.wrapper.get('[role="alert"]').text()).toContain('req-404')

    metadataApi.currentMasterType.mockRejectedValueOnce({ status: 404, message: 'No active metadata', requestId: 'req-meta' })
    const list = await mountAt('/records')
    expect(list.wrapper.get('[role="alert"]').text()).toContain('req-meta')
  })

  it('hides mutation actions for deleted records and records owned by another department', async () => {
    recordsApi.getRecord.mockResolvedValueOnce({ ...record, status: 'DELETED' })
    const deleted = await mountAt('/records/81')
    expect(deleted.wrapper.find('[data-testid="record-edit-81"]').exists()).toBe(false)
    expect(deleted.wrapper.find('[data-testid="record-delete-81"]').exists()).toBe(false)

    recordsApi.getRecord.mockResolvedValueOnce({ ...record, departmentId: 8 })
    const foreign = await mountAt('/records/81')
    expect(foreign.wrapper.find('[data-testid="record-edit-81"]').exists()).toBe(false)
    expect(foreign.wrapper.find('[data-testid="record-delete-81"]').exists()).toBe(false)
  })

  it('uses safe source keys instead of viewer-department metadata labels for shared records', async () => {
    metadataApi.listMasterFields.mockResolvedValueOnce([
      { ...masterFields[0], displayName: 'Viewer department label' }
    ])
    recordsApi.getRecord.mockResolvedValueOnce({ ...record, departmentId: 8 })

    const foreign = await mountAt('/records/81')

    expect(metadataApi.listMasterFields).not.toHaveBeenCalled()
    expect(foreign.wrapper.text()).toContain('name')
    expect(foreign.wrapper.text()).not.toContain('Viewer department label')
  })

  it('shows request-id errors on create draft failures instead of leaving rejected promises unhandled', async () => {
    recordsApi.createRecordDraft.mockRejectedValueOnce({ status: 500, message: 'Create failed', requestId: 'req-create' })
    const { wrapper, router } = await mountAt('/records')

    await wrapper.get('[data-testid="record-create"]').trigger('click')
    await flushPromises()
    await wrapper.get('[name="field-name"]').setValue('New asset')
    await wrapper.get('[data-testid="record-save"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Create failed')
    expect(wrapper.get('[role="alert"]').text()).toContain('req-create')
    expect(router.currentRoute.value.fullPath).toBe('/records/new')
  })

  it('shows request-id errors when edit or delete draft creation fails from the detail view', async () => {
    recordsApi.createRecordDraft.mockRejectedValueOnce({ status: 409, message: 'Draft conflict', requestId: 'req-edit' })
    const edit = await mountAt('/records/81')

    await edit.wrapper.get('[data-testid="record-edit-81"]').trigger('click')
    await flushPromises()
    expect(edit.wrapper.get('[role="alert"]').text()).toContain('req-edit')
    expect(edit.router.currentRoute.value.fullPath).toBe('/records/81')

    recordsApi.requestRecordDeletion.mockRejectedValueOnce({ status: 500, message: 'Delete failed', requestId: 'req-delete' })
    const deletion = await mountAt('/records/81')
    await deletion.wrapper.get('[name="deleteReason"]').setValue('Duplicate record')
    await deletion.wrapper.get('[data-testid="record-delete-81"]').trigger('click')
    await flushPromises()
    expect(deletion.wrapper.get('[role="alert"]').text()).toContain('Delete failed')
    expect(deletion.wrapper.get('[role="alert"]').text()).toContain('req-delete')
    expect(deletion.router.currentRoute.value.fullPath).toBe('/records/81')
  })
})
