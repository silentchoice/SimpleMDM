import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'
import DepartmentMetadataView from './DepartmentMetadataView.vue'

const metadataApi = vi.hoisted(() => ({
  listMasterFields: vi.fn(), listSubTypes: vi.fn(), listSubFields: vi.fn(),
  submitMasterFields: vi.fn(), submitSubTypes: vi.fn(), submitSubFields: vi.fn()
}))
vi.mock('../../api/metadata', () => metadataApi)

const field = { id: 1, ownerTypeId: 41, code: 'SERIAL', displayName: 'Serial number', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }
const subType = { id: 55, masterTypeId: 41, code: 'ACCESSORY', name: 'Accessory', status: 'ACTIVE' }

function mountView() { return mount(DepartmentMetadataView, { props: { masterTypeId: 41 }, global: { plugins: [ElementPlus] } }) }

describe('department ACTIVE metadata workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    metadataApi.listMasterFields.mockResolvedValue([field])
    metadataApi.listSubTypes.mockResolvedValue([subType])
    metadataApi.listSubFields.mockResolvedValue([field])
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
})
