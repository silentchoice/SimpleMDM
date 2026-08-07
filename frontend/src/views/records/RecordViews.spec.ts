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
      masterValues: { name: 'Laptop fleet', enabled: true },
      children: [{ subTypeId: 301, rows: [{ id: 900, rowOrder: 0, values: { email: 'ops@example.com' } }] }],
      version: 4,
      status: 'ACTIVE'
    }
  ],
  number: 0,
  size: 20,
  totalElements: 31,
  totalPages: 2
}
const record = page.content[0]
const history = [
  record,
  { ...record, version: 3, masterValues: { ...record.masterValues, name: 'Laptop fleet v3' } },
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
    expect(wrapper.get('[data-testid="detail-tab-current"]').text()).toContain('Current')
    expect(wrapper.get('[data-testid="detail-tab-diff"]').text()).toContain('Diff')
    expect(wrapper.get('[data-testid="detail-tab-history"]').text()).toContain('History')
    expect(wrapper.get('[data-testid="record-edit-81"]').text()).toContain('Edit')
    await wrapper.get('[data-testid="detail-tab-history"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid^="history-version-"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('Version 4')
    expect(wrapper.text()).toContain('Version 2')
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
})
