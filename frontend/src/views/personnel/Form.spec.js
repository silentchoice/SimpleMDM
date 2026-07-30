// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import Form from './Form.vue'
import { useUserStore } from '../../stores/user'
import { getDepartments, getPersonnel } from '../../api/personnel'
import { listSub, updateSub } from '../../api/personnelSub'
import { getFieldDefsByType, listFieldDefs, listSubTypes } from '../../api/deptFields'

const route = { params: { id: '12' }, query: {}, meta: { mode: 'view' } }
const router = { back: vi.fn(), push: vi.fn() }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

vi.mock('../../api/personnel', () => ({
  createPersonnel: vi.fn(),
  getDepartments: vi.fn(),
  getPersonnel: vi.fn(),
  updatePersonnel: vi.fn(),
}))

vi.mock('../../api/personnelSub', () => ({
  createSub: vi.fn(),
  listSub: vi.fn(),
  updateSub: vi.fn(),
}))

vi.mock('../../api/deptFields', () => ({
  getFieldDefsByType: vi.fn(),
  listFieldDefs: vi.fn(),
  listSubTypes: vi.fn(),
}))

function mountForm({
  ownerDepartment = '工程部',
  mode = 'view',
  query = {},
} = {}) {
  route.meta = { mode }
  route.query = query
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.user = { id: 7, department: '工程部', is_admin: false }
  store.permissions = [{ perm_type: 'EDIT', scope_value: '工程部', scope_type: 'DEPT' }]
  getPersonnel.mockResolvedValue({
    data: { id: 12, owner_dept: ownerDepartment, data: {}, version: 1 },
  })
  return mount(Form, { global: { plugins: [pinia, ElementPlus] } })
}

describe('personnel form navigation and department ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDepartments.mockResolvedValue({ data: ['工程部', '产品部'] })
    listFieldDefs.mockImplementation((_department, tableType) => Promise.resolve({
      data: tableType === 'sub'
        ? [{
            id: 9, department: '工程部', table_type: 'sub', sub_type: '项目',
            field_key: 'project_name', field_name: '项目名称', field_type: 'string',
            required: false, sort_order: 1, system_field: false,
          }]
        : [],
    }))
    listSubTypes.mockResolvedValue({ data: ['项目'] })
    listSub.mockResolvedValue({
      data: [{
        id: 21, sub_type: '项目', owner_dept: '工程部',
        data: { project_name: '主数据项目' }, visibility: 'private', version: 2,
      }],
    })
    getFieldDefsByType.mockResolvedValue({ data: [] })
    updateSub.mockResolvedValue({})
  })

  it('returns to the source department', async () => {
    const wrapper = mountForm({ query: { from_department: '产品部' } })
    await flushPromises()

    await wrapper.get('[data-test="back"]').trigger('click')

    expect(router.push).toHaveBeenCalledWith({
      path: '/personnel',
      query: { department: '产品部' },
    })
  })

  it('does not show or submit record visibility', async () => {
    const wrapper = mountForm({ mode: 'edit' })
    await flushPromises()
    expect(wrapper.text()).not.toContain('可见性')

    const record = {
      id: 21, sub_type: '项目', owner_dept: '工程部',
      data: { project_name: '主数据项目' }, visibility: 'private', version: 2,
    }
    wrapper.vm.showSubDialog('项目', record)
    await wrapper.vm.saveSubRecord()

    expect(updateSub).toHaveBeenCalledWith(
      '12',
      21,
      expect.not.objectContaining({ visibility: expect.anything() }),
    )
  })

  it('hides every mutation control for a foreign department', async () => {
    const wrapper = mountForm({ ownerDepartment: '产品部', mode: 'edit' })
    await flushPromises()

    const buttonLabels = wrapper.findAll('button').map(button => button.text())
    expect(buttonLabels).not.toContain('提交审批')
    expect(buttonLabels).not.toContain('添加记录')
    expect(buttonLabels).not.toContain('编辑')
  })
})
