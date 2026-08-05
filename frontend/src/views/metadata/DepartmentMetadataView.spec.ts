import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'
import DepartmentMetadataView from './DepartmentMetadataView.vue'
import { i18n, setLocale } from '../../i18n'

const metadataApi = vi.hoisted(() => ({
  ACTIVE_METADATA_INVALIDATED_EVENT: 'mdm:active-metadata-invalidated',
  currentMasterType: vi.fn(), listMasterFields: vi.fn(), listSubTypes: vi.fn(), listSubFields: vi.fn(),
  submitMasterFields: vi.fn(), submitSubTypes: vi.fn(), submitSubFields: vi.fn()
}))
vi.mock('../../api/metadata', () => metadataApi)

const field = { id: 1, ownerTypeId: 41, code: 'SERIAL', displayName: 'Serial number', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }
const subType = { id: 55, masterTypeId: 41, code: 'ACCESSORY', name: 'Accessory', status: 'ACTIVE' }

function mountView(initialTab: 'active' | 'submit' = 'active') { return mount(DepartmentMetadataView, { props: { initialTab }, global: { plugins: [ElementPlus, i18n] } }) }

describe('department ACTIVE metadata workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setLocale('zh-CN')
    setActivePinia(createPinia())
    metadataApi.currentMasterType.mockResolvedValue({ id: 41, code: 'ASSET', name: 'Asset', status: 'ACTIVE' })
    metadataApi.listMasterFields.mockResolvedValue([field])
    metadataApi.listSubTypes.mockResolvedValue([subType])
    metadataApi.listSubFields.mockResolvedValue([field])
  })

  it('discovers the authenticated department assignment and opens the submit route on its editor tab', async () => {
    useAuthStore().setSession({ accessToken: 'token', user: { id: 1, username: 'editor', displayName: 'Editor' }, roles: ['DEPT_EDITOR'], department: { id: 3, code: 'OPS', name: 'Operations' } })
    const wrapper = mountView('submit')
    await flushPromises()

    expect(metadataApi.currentMasterType).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Asset')
    expect(wrapper.find('[name="subTypeId"]').exists()).toBe(true)
  })

  it('surfaces the current-assignment request ID when assignment discovery fails', async () => {
    metadataApi.currentMasterType.mockRejectedValueOnce({
      message: 'No master type assignment', requestId: 'req-assignment-404'
    })
    useAuthStore().setSession({ accessToken: 'token', user: { id: 1, username: 'viewer', displayName: 'Viewer' }, roles: ['DEPT_VIEWER'], department: { id: 3, code: 'OPS', name: 'Operations' } })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'No master type assignment（请求 ID：req-assignment-404）')
  })

  for (const role of ['DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'] as const) {
    it(`${role} can inspect the same read-only ACTIVE structure`, async () => {
      useAuthStore().setSession({ accessToken: 'token', user: { id: 1, username: role, displayName: role }, roles: [role], department: { id: 3, code: 'OPS', name: 'Operations' } })
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('Serial number')
      expect(wrapper.text()).toContain('Accessory')
      expect(wrapper.find('[data-testid="active-save"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="submit-changes-tab"]').exists()).toBe(role === 'DEPT_EDITOR')
    })
  }

  it('reloads every ACTIVE level only when refresh is explicitly requested', async () => {
    useAuthStore().setSession({ accessToken: 'token', user: { id: 1, username: 'viewer', displayName: 'Viewer' }, roles: ['DEPT_VIEWER'], department: { id: 3, code: 'OPS', name: 'Operations' } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="refresh-active"]').trigger('click')
    await flushPromises()

    expect(metadataApi.listMasterFields).toHaveBeenCalledTimes(2)
    expect(metadataApi.listSubTypes).toHaveBeenCalledTimes(2)
    expect(metadataApi.listSubFields).toHaveBeenCalledTimes(2)
  })

  it('localizes the metadata workspace immediately while preserving enum and owner identifiers', async () => {
    useAuthStore().setSession({ accessToken: 'token', user: { id: 1, username: 'editor', displayName: 'Editor' }, roles: ['DEPT_EDITOR'], department: { id: 3, code: 'OPS', name: 'Operations' } })
    const wrapper = mountView('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('部门元数据')
    expect(wrapper.text()).toContain('当前启用版本')
    expect(wrapper.text()).toContain('提交变更')
    expect(wrapper.text()).toContain('主字段')
    expect(wrapper.text()).toContain('子类型')
    expect(wrapper.text()).toContain('子字段')
    expect(wrapper.text()).toContain('刷新')

    await wrapper.get('[data-testid="submit-master-fields"]').trigger('click')
    await flushPromises()
    expect(metadataApi.submitMasterFields).toHaveBeenCalledWith(41, [expect.objectContaining({ code: 'SERIAL', fieldType: 'TEXT' })])

    setLocale('en-US')
    await flushPromises()
    expect(wrapper.text()).toContain('Department metadata')
    expect(wrapper.text()).toContain('Current active version')
    expect(wrapper.text()).toContain('Submit changes')
  })
})
