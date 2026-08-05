import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MasterTypeListView from './MasterTypeListView.vue'

const metadataApi = vi.hoisted(() => ({
  listMasterTypes: vi.fn(), createMasterType: vi.fn(), assignDepartment: vi.fn()
}))
const systemApi = vi.hoisted(() => ({ listDepartments: vi.fn() }))

vi.mock('../../api/metadata', () => metadataApi)
vi.mock('../../api/system', () => systemApi)

const masterTypes = [{ id: 7, code: 'ASSET', name: 'Asset', status: 'ACTIVE' }]
const departments = [
  { id: 3, code: 'OPS', name: 'Operations', status: 'ACTIVE' },
  { id: 4, code: 'LEGACY', name: 'Legacy', status: 'DISABLED' }
]

function mountView() {
  return mount(MasterTypeListView, { global: { plugins: [ElementPlus] } })
}

describe('master-type template list', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    metadataApi.listMasterTypes.mockResolvedValue(masterTypes)
    metadataApi.createMasterType.mockResolvedValue(masterTypes[0])
    metadataApi.assignDepartment.mockResolvedValue(undefined)
    systemApi.listDepartments.mockResolvedValue(departments)
  })

  it('loads template rows and validates a new template before creating it', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Asset')
    await wrapper.get('[data-testid="master-type-create"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(metadataApi.createMasterType).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Code and name are required')

    await wrapper.get('[name="code"]').setValue('ORDER')
    await wrapper.get('[name="name"]').setValue('Order')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(metadataApi.createMasterType).toHaveBeenCalledWith({ code: 'ORDER', name: 'Order' })
    expect(metadataApi.listMasterTypes).toHaveBeenCalledTimes(2)
  })

  it('offers only active departments for a selected template assignment', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assign-department-7"]').trigger('click')

    expect(wrapper.text()).toContain('Assign Asset to department')
    expect(wrapper.text()).toContain('Operations')
    expect(wrapper.text()).not.toContain('Legacy')
  })

  it('assigns the selected department and refreshes templates after success', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assign-department-7"]').trigger('click')
    await wrapper.get('[name="departmentId"]').setValue('3')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(metadataApi.assignDepartment).toHaveBeenCalledWith(7, 3)
    expect(metadataApi.listMasterTypes).toHaveBeenCalledTimes(2)
  })

  it('shows duplicate assignment conflicts and leaves the list unrefreshed', async () => {
    metadataApi.assignDepartment.mockRejectedValueOnce({ message: 'Department already has a template', requestId: 'req-409', status: 409 })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="assign-department-7"]').trigger('click')
    await wrapper.get('[name="departmentId"]').setValue('3')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Department already has a template (Request ID: req-409)')
    expect(metadataApi.listMasterTypes).toHaveBeenCalledTimes(1)
  })
})
