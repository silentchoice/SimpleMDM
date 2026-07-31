// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import Form from './Form.vue'
import { createRecord, listObjectTypes, listRecords, updateRecord } from '../../api/mdm'

const route = { params: {}, query: { system: 'HR', object: 'person', department: '10' }, meta: { mode: 'create' } }
const router = { push: vi.fn() }
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))
vi.mock('../../api/mdm', () => ({
  createRecord: vi.fn(), listObjectTypes: vi.fn(), listRecords: vi.fn(), updateRecord: vi.fn(),
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
    ] }] })
    createRecord.mockResolvedValue({ data: { id: 8 } })
  })

  it('never sends a department name as a relationship key', async () => {
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    wrapper.vm.form.record_code = 'P001'
    wrapper.vm.form.data.name = '张三'
    await wrapper.get('[data-test="save"]').trigger('click')
    await flushPromises()
    expect(createRecord).toHaveBeenCalledWith('person', expect.objectContaining({
      department_id: 10,
      record_code: 'P001',
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
    expect(updateRecord).toHaveBeenCalledWith('person', expect.objectContaining({ id: 12, version: 3 }))
  })

  it('disables saving and shows an error when metadata loading fails', async () => {
    listObjectTypes.mockRejectedValue(new Error('metadata offline'))
    const wrapper = mount(Form, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(wrapper.text()).toContain('metadata offline')
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
    expect(wrapper.text()).toContain('save offline')
  })
})
