import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppRouter } from '../../router'
import { i18n, setLocale } from '../../i18n'
import { useAuthStore } from '../../stores/auth'

const metadataApi = vi.hoisted(() => ({
  currentMasterType: vi.fn(),
  listMasterFields: vi.fn(),
  listSubTypes: vi.fn(),
  listSubFields: vi.fn()
}))
const recordsApi = vi.hoisted(() => ({
  getRecord: vi.fn(),
  listRecordHistory: vi.fn()
}))

vi.mock('../../api/metadata', () => metadataApi)
vi.mock('../../api/records', () => recordsApi)

const RouterHost = { template: '<router-view />' }
const record = {
  id: 81,
  masterTypeId: 41,
  departmentId: 7,
  recordCode: 'AST-0001',
  masterValues: { name: 'Laptop fleet' },
  children: [],
  version: 4,
  status: 'ACTIVE'
}

async function mountDetail() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    accessToken: 'token',
    user: { id: 12, username: 'viewer', displayName: 'Viewer' },
    roles: ['DEPT_VIEWER'],
    department: { id: 7, code: 'OPS', name: 'Operations' }
  })
  const router = createAppRouter()
  await router.push('/records/81')
  await router.isReady()
  const wrapper = mount(RouterHost, { global: { plugins: [pinia, ElementPlus, i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('record history presentation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    setLocale('en-US')
    metadataApi.currentMasterType.mockResolvedValue({ id: 41, code: 'ASSET', name: 'Asset', status: 'ACTIVE' })
    metadataApi.listMasterFields.mockResolvedValue([{ id: 101, ownerTypeId: 41, code: 'name', displayName: 'Name', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }])
    metadataApi.listSubTypes.mockResolvedValue([])
    recordsApi.getRecord.mockResolvedValue(record)
    recordsApi.listRecordHistory.mockResolvedValue([
      record,
      { ...record, version: 3, status: 'ACTIVE', masterValues: { name: 'Laptop fleet v3' } },
      { ...record, version: 2, status: 'ACTIVE', masterValues: { name: 'Laptop fleet v2' } },
      { ...record, version: 1, status: 'DELETED', masterValues: { name: '<img src=x onerror=alert(1)>' } }
    ])
  })

  it('shows only the latest three versions and renders raw values as escaped text when history is opened', async () => {
    const wrapper = await mountDetail()

    await wrapper.get('[data-testid="detail-tab-history"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid^="history-version-"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('Version 4')
    expect(wrapper.text()).toContain('Version 3')
    expect(wrapper.text()).toContain('Version 2')
    expect(wrapper.text()).not.toContain('Version 1')
    expect(wrapper.html()).not.toContain('<img src=x onerror=alert(1)>')
  })
})
