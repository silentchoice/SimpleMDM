// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import Form from './Form.vue'
import { createRecord, listChildRecords, listObjectTypes, listRecords, updateRecord } from '../../api/mdm'

const route = { params: {}, query: { system: 'HR', object: 'person', department: '10' }, meta: { mode: 'create' } }
const router = { push: vi.fn() }
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))
vi.mock('../../api/mdm', () => ({
  createRecord: vi.fn(), listChildRecords: vi.fn(), listObjectTypes: vi.fn(), listRecords: vi.fn(), updateRecord: vi.fn(),
}))

describe('generic MDM form', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.meta = { mode: 'create' }
    route.params = {}
    setActivePinia(createPinia())
    localStorage.setItem('permissions', JSON.stringify([{ code: 'MDM_RECORD_EDIT', can_edit: true }]))
    listObjectTypes.mockResolvedValue({ data: [{ code: 'person', fields: [
      { field_key: 'name', field_name: '姓名', data_type: 'STRING', required: true },
      { field_key: 'salary', field_name: '薪资', data_type: 'DECIMAL', precision_value: 12, scale_value: 2 },
      { field_key: 'active', field_name: '在职', data_type: 'BOOLEAN' },
      { field_key: 'manager', field_name: '上级', data_type: 'REFERENCE' },
    ], child_types: [{ id: 20, code: 'part_time', name: '兼职信息', status: 'active', fields: [
      { field_key: 'company', field_name: '兼职单位', data_type: 'STRING', required: true, status: 'active' },
      { field_key: 'start_date', field_name: '开始日期', data_type: 'DATE', status: 'active' },
      { field_key: 'inactive', field_name: '已停用字段', data_type: 'STRING', status: 'inactive' },
    ] }] }] })
    listChildRecords.mockResolvedValue({ data: [] })
    createRecord.mockResolvedValue({ data: { id: 8, status: 'PENDING' } })
  })

  it('never sends a department name as a relationship key', async () => {
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.form.record_code = 'P001'
    wrapper.vm.form.data.name = '张三'
    await wrapper.get('[data-test="save"]').trigger('click')
    await flushPromises()
    expect(createRecord).toHaveBeenCalledWith('person', expect.objectContaining({
      operation: 'CREATE',
      object_code: 'person',
      department_id: 10,
      record_code: 'P001',
      children: [],
    }))
    expect(createRecord.mock.calls[0][1]).not.toHaveProperty('owner_dept')
  })

  it('serializes decimal boolean and reference values by metadata type', async () => {
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    Object.assign(wrapper.vm.form, { record_code: 'P002' })
    Object.assign(wrapper.vm.form.data, { name: '李四', salary: '12.50', active: true, manager: '42' })
    await wrapper.get('[data-test="save"]').trigger('click')
    await flushPromises()
    expect(createRecord).toHaveBeenCalledWith('person', expect.objectContaining({
      data: { name: '李四', salary: '12.50', active: true, manager: 42 },
    }))
  })

  it('includes id and version when updating', async () => {
    route.meta = { mode: 'edit' }
    route.params = { id: '12' }
    listRecords.mockResolvedValue({ data: [{ id: 12, version: 3, department_id: 10, record_code: 'P012', data: { name: '王五' } }] })
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    await wrapper.get('[data-test="save"]').trigger('click')
    await flushPromises()
    expect(updateRecord).toHaveBeenCalledWith('person', expect.objectContaining({
      operation: 'UPDATE', record_id: 12, expected_version: 3,
    }))
  })

  it('does not block editing a legacy record that predates a newly required field', async () => {
    route.meta = { mode: 'edit' }
    route.params = { id: '12' }
    listObjectTypes.mockResolvedValue({ data: [{ code: 'person', fields: [
      { field_key: 'name', field_name: '姓名', data_type: 'STRING', required: true },
      { field_key: 'new_required', field_name: '新增必填项', data_type: 'STRING', required: true },
    ], child_types: [] }] })
    listRecords.mockResolvedValue({ data: [{ id: 12, version: 3, department_id: 10,
      record_code: 'P012', data: { name: '王五' } }] })
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.form.data.name = '王五（更新）'

    await wrapper.vm.save()

    expect(updateRecord).toHaveBeenCalledWith('person', expect.objectContaining({
      data: { name: '王五（更新）' },
    }))
    expect(wrapper.text()).not.toContain('新增必填项不能为空')
  })

  it('disables saving and shows an error when metadata loading fails', async () => {
    listObjectTypes.mockRejectedValue(new Error('metadata offline'))
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(wrapper.text()).toContain('加载记录失败')
    expect(wrapper.get('[data-test="save"]').attributes('disabled')).toBeDefined()
  })

  it('guards duplicate saves and exposes save failure', async () => {
    let rejectSave
    createRecord.mockReturnValue(new Promise((_resolve, reject) => { rejectSave = reject }))
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.form.record_code = 'P003'
    wrapper.vm.form.data.name = '失败记录'
    const first = wrapper.vm.save()
    wrapper.vm.save()
    expect(createRecord).toHaveBeenCalledTimes(1)
    rejectSave(new Error('save offline'))
    await first
    expect(wrapper.text()).toContain('提交审批失败')
  })

  it('selecting a child type renders every active field and excludes inactive fields', async () => {
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.selectedChildCode = 'part_time'
    await wrapper.vm.addChildGroup()
    await flushPromises()

    expect(wrapper.text()).toContain('兼职单位')
    expect(wrapper.text()).toContain('开始日期')
    expect(wrapper.text()).not.toContain('已停用字段')
  })

  it('serializes repeated child create update and delete rows with stable ids and versions', async () => {
    route.meta = { mode: 'edit' }
    route.params = { id: '12' }
    listRecords.mockResolvedValue({ data: [{ id: 12, version: 3, department_id: 10, record_code: 'P012', data: { name: '王五' } }] })
    listChildRecords.mockResolvedValue({ data: [
      { id: 31, version: 2, data: { company: '甲公司', start_date: '2026-01-01' } },
      { id: 32, version: 4, data: { company: '乙公司', start_date: '2026-02-01' } },
    ] })
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.selectedChildCode = 'part_time'
    await wrapper.vm.addChildGroup()
    wrapper.vm.childGroups[0].rows[0].data.company = '甲公司新'
    wrapper.vm.removeChildRow(wrapper.vm.childGroups[0], 1)
    wrapper.vm.addChildRow(wrapper.vm.childGroups[0])
    wrapper.vm.childGroups[0].rows[2].data.company = '丙公司'
    await wrapper.vm.save()

    expect(updateRecord).toHaveBeenCalledWith('person', expect.objectContaining({
      children: [{ child_code: 'part_time', rows: [
        { operation: 'UPDATE', id: 31, expected_version: 2, data: { company: '甲公司新', start_date: '2026-01-01' } },
        { operation: 'DELETE', id: 32, expected_version: 4 },
        { operation: 'CREATE', data: { company: '丙公司', start_date: null } },
      ] }],
    }))
  })

  it('states that a successful submission is pending approval instead of effective', async () => {
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.form.record_code = 'P004'
    wrapper.vm.form.data.name = '待审批记录'
    await wrapper.vm.save()
    await flushPromises()
    expect(wrapper.text()).toContain('已提交审批')
    expect(wrapper.text()).toContain('审批通过后才会生效')
    await wrapper.vm.save()
    expect(createRecord).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-test="save"]').attributes('disabled')).toBeDefined()
  })

  it('shows a Chinese fallback when projected child rows cannot be loaded', async () => {
    route.meta = { mode: 'edit' }
    route.params = { id: '12' }
    listRecords.mockResolvedValue({ data: [{ id: 12, version: 3, department_id: 10, record_code: 'P012', data: { name: '王五' } }] })
    listChildRecords.mockRejectedValue(new Error('child service offline'))
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.selectedChildCode = 'part_time'
    await expect(wrapper.vm.addChildGroup()).resolves.toBeUndefined()
    expect(wrapper.text()).toContain('加载子表失败')
  })

  it('explains when the backend omits child metadata instead of silently hiding the feature', async () => {
    listObjectTypes.mockResolvedValue({ data: [{ code: 'person', fields: [
      { field_key: 'name', field_name: '姓名', data_type: 'STRING', required: true },
    ] }] })
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(wrapper.text()).toContain('服务端未返回可用子表元数据')
  })

  it('treats an explicit empty child metadata array as a supported empty state', async () => {
    listObjectTypes.mockResolvedValue({ data: [{ code: 'person', fields: [], child_types: [] }] })
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(wrapper.text()).toContain('暂未添加子表')
    expect(wrapper.text()).not.toContain('服务端未返回可用子表元数据')
  })

  it('guards duplicate child-group loads while an existing child list is pending', async () => {
    route.meta = { mode: 'edit' }
    route.params = { id: '12' }
    listRecords.mockResolvedValue({ data: [{ id: 12, version: 3, department_id: 10, record_code: 'P012', data: { name: '王五' } }] })
    let resolveChildren
    listChildRecords.mockReturnValue(new Promise(resolve => { resolveChildren = resolve }))
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.selectedChildCode = 'part_time'
    const first = wrapper.vm.addChildGroup()
    wrapper.vm.addChildGroup()
    expect(listChildRecords).toHaveBeenCalledTimes(1)
    resolveChildren({ data: [] })
    await first
  })
})
