import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MasterTypeListView from './MasterTypeListView.vue'
import { i18n, setLocale } from '../../i18n'

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
  return mount(MasterTypeListView, { global: { plugins: [ElementPlus, i18n] } })
}

describe('master-type template list', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setLocale('zh-CN')
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
    expect(wrapper.text()).toContain('请输入代码和名称')

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

    expect(wrapper.text()).toContain('将 Asset 分配给部门')
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

    expect(wrapper.text()).toContain('Department already has a template（请求 ID：req-409）')
    expect(metadataApi.listMasterTypes).toHaveBeenCalledTimes(1)
  })

  it('localizes template, assignment, status, and action labels without changing submitted IDs', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('主数据类型模板')
    expect(wrapper.text()).toContain('创建主数据类型')
    expect(wrapper.text()).toContain('启用')
    expect(wrapper.text()).toContain('分配部门')
    await wrapper.get('[data-testid="assign-department-7"]').trigger('click')
    await wrapper.get('[name="departmentId"]').setValue('3')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(metadataApi.assignDepartment).toHaveBeenCalledWith(7, 3)

    setLocale('en-US')
    await flushPromises()
    expect(wrapper.text()).toContain('Master Type Templates')
    expect(wrapper.text()).toContain('Create master type')
    expect(wrapper.text()).toContain('Active')
  })
})
