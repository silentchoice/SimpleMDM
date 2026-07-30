// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessageBox } from 'element-plus'
import Manager from './Manager.vue'
import { useUserStore } from '../../stores/user'
import { deleteFieldDef, listFieldDefs, listSubTypes } from '../../api/deptFields'

vi.mock('../../api/deptFields', () => ({
  listFieldDefs: vi.fn(),
  listSubTypes: vi.fn(),
  createFieldDef: vi.fn(),
  updateFieldDef: vi.fn(),
  deleteFieldDef: vi.fn(),
}))

vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal()
  return { ...actual, ElMessageBox: { confirm: vi.fn() } }
})

function mountManager({ isAdmin = false, departmentAdmin = true } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.user = { id: 7, department: '工程部', is_admin: isAdmin }
  store.permissions = departmentAdmin
    ? [{ perm_type: 'EDIT', scope_value: '工程部', scope_type: 'DEPT' }]
    : []
  return mount(Manager, { global: { plugins: [pinia, ElementPlus] } })
}

describe('department field manager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listFieldDefs.mockResolvedValue({ data: [{
      id: 9, table_type: 'sub', sub_type: 'project', field_key: 'engineering_project_name',
      field_name: '项目名称', field_type: 'string', required: true, sort_order: 1,
      system_field: false, shared: true,
    }] })
    listSubTypes.mockResolvedValue({ data: ['project'] })
    deleteFieldDef.mockResolvedValue({})
    ElMessageBox.confirm.mockResolvedValue('confirm')
  })

  it('shows shared and delete only for sub fields', async () => {
    const wrapper = mountManager()
    await flushPromises()
    expect(wrapper.text()).toContain('是否共享')
    expect(wrapper.text()).toContain('删除')
    wrapper.vm.activeTab = 'master'
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).not.toContain('是否共享')
    expect(wrapper.text()).not.toContain('删除')
  })

  it('hides delete from main administrators', async () => {
    const wrapper = mountManager({ isAdmin: true, departmentAdmin: false })
    await flushPromises()
    expect(wrapper.text()).not.toContain('删除')
  })

  it('confirms permanent cleanup before deleting a sub field', async () => {
    const wrapper = mountManager()
    await flushPromises()
    const button = wrapper.findAll('button').find(item => item.text().includes('删除'))
    await button.trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      '删除后，该字段定义以及所有历史子表记录中的对应数据都会永久清除。',
      '确认删除字段',
      expect.any(Object),
    )
    expect(deleteFieldDef).toHaveBeenCalledWith(9)
  })
})