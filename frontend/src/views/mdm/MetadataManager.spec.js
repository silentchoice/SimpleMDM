// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElOption } from 'element-plus'
import MetadataManager from './MetadataManager.vue'
import {
  createChildField, createChildType, createMasterField, deactivateChildField,
  deactivateChildType, deactivateMasterField, listObjectTypes, updateChildField,
  updateChildType, updateMasterField, updateObjectType, deactivateObjectType,
  reactivateChildField, reactivateChildType, reactivateMasterField, reactivateObjectType,
} from '../../api/mdm'

vi.mock('../../api/mdm', () => ({
  listObjectTypes: vi.fn(), createMasterField: vi.fn(), updateMasterField: vi.fn(), deactivateMasterField: vi.fn(),
  createChildType: vi.fn(), updateChildType: vi.fn(), deactivateChildType: vi.fn(),
  createChildField: vi.fn(), updateChildField: vi.fn(), deactivateChildField: vi.fn(),
  updateObjectType: vi.fn(), deactivateObjectType: vi.fn(),
  reactivateMasterField: vi.fn(), reactivateChildType: vi.fn(), reactivateChildField: vi.fn(), reactivateObjectType: vi.fn(),
}))

const metadata = [{
  id: 1, code: 'person', name: '人员', fields: [{ id: 11, field_key: 'name', field_name: '姓名', data_type: 'STRING', required: true, unique_value: false, searchable: true, sort_order: 1, status: 'active' }],
  child_types: [{ id: 21, code: 'job', name: '任职信息', sort_order: 1, status: 'active', fields: [{ id: 31, field_key: 'title', field_name: '岗位', data_type: 'STRING', shared: false, required: false, unique_value: false, searchable: true, sort_order: 1, status: 'active' }] }],
}]

describe('中文元数据编辑器', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('user', JSON.stringify({ is_admin: false }))
    localStorage.setItem('permissions', JSON.stringify([{ code: 'MDM_FIELD_MANAGE', can_edit: true }]))
    listObjectTypes.mockResolvedValue({ data: metadata })
    ;[createMasterField, updateMasterField, deactivateMasterField, createChildType, updateChildType, deactivateChildType, createChildField, updateChildField, deactivateChildField, updateObjectType, deactivateObjectType, reactivateMasterField, reactivateChildType, reactivateChildField, reactivateObjectType].forEach(fn => fn.mockResolvedValue({ data: {} }))
  })

  it('lets a field manager add and edit master fields with Chinese controls', async () => {
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.get('[data-test="add-master-field"]').text()).toContain('新增主字段')
    await wrapper.get('[data-test="add-master-field"]').trigger('click')
    wrapper.vm.masterField.field_key = 'mobile'
    wrapper.vm.masterField.field_name = '手机号'
    await wrapper.get('[data-test="save-master-field"]').trigger('click')
    await flushPromises()
    expect(createMasterField).toHaveBeenCalledWith('person', expect.objectContaining({ field_key: 'mobile', field_name: '手机号', data_type: 'STRING' }))
    await wrapper.get('[data-test="edit-master-11"]').trigger('click')
    wrapper.vm.masterField.field_name = '姓名（常用）'
    await wrapper.get('[data-test="save-master-field"]').trigger('click')
    await flushPromises()
    expect(updateMasterField).toHaveBeenCalledWith('person', 11, expect.objectContaining({ field_name: '姓名（常用）' }))
    expect(updateMasterField.mock.calls[0][2]).not.toHaveProperty('field_key')

    await wrapper.get('[data-test="add-master-field"]').trigger('click')
    wrapper.vm.masterField.field_key = 'note'
    wrapper.vm.masterField.field_name = '备注'
    await wrapper.get('[data-test="save-master-field"]').trigger('click')
    await flushPromises()
    expect(createMasterField).toHaveBeenLastCalledWith('person', expect.objectContaining({ field_key: 'note', field_name: '备注' }))
  })

  it('offers TEXT and requires a selectable reference object type for reference fields', async () => {
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.get('[data-test="add-master-field"]').trigger('click')
    wrapper.vm.masterField.data_type = 'REFERENCE'
    await wrapper.vm.$nextTick()
    expect(wrapper.findAllComponents(ElOption).some(option => option.props('value') === 'TEXT')).toBe(true)
    expect(wrapper.find('[data-test="reference-object-type"]').exists()).toBe(true)
    wrapper.vm.masterField.field_key = 'manager'
    wrapper.vm.masterField.field_name = '直属主管'
    await wrapper.get('[data-test="save-master-field"]').trigger('click')
    expect(createMasterField).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请选择引用对象')
    wrapper.vm.masterField.reference_object_type_id = 1
    await wrapper.get('[data-test="save-master-field"]').trigger('click')
    await flushPromises()
    expect(createMasterField).toHaveBeenCalledWith('person', expect.objectContaining({ data_type: 'REFERENCE', reference_object_type_id: 1 }))
  })

  it('configures length decimal default and validation metadata', async () => {
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.get('[data-test="add-master-field"]').trigger('click')
    expect(wrapper.get('[data-test="field-max-length"]').exists()).toBe(true)
    expect(wrapper.get('[data-test="field-default-value"]').exists()).toBe(true)
    expect(wrapper.get('[data-test="field-validation-rule"]').exists()).toBe(true)
    wrapper.vm.masterField.field_key = 'amount'
    wrapper.vm.masterField.field_name = '金额'
    wrapper.vm.masterField.data_type = 'DECIMAL'
    wrapper.vm.masterField.precision_value = 12
    wrapper.vm.masterField.scale_value = 2
    wrapper.vm.masterField.default_value = '0.00'
    wrapper.vm.masterField.validation_rule = 'min:0'
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-test="field-precision"]').exists()).toBe(true)
    expect(wrapper.get('[data-test="field-scale"]').exists()).toBe(true)
    await wrapper.get('[data-test="save-master-field"]').trigger('click')
    await flushPromises()
    expect(createMasterField).toHaveBeenCalledWith('person', expect.objectContaining({
      precision_value: 12, scale_value: 2, default_value: '0.00', validation_rule: 'min:0',
    }))
  })

  it('clears a stale reference object when an existing field changes to a non-reference type', async () => {
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.get('[data-test="edit-master-11"]').trigger('click')
    wrapper.vm.masterField.data_type = 'STRING'
    wrapper.vm.masterField.reference_object_type_id = 1
    await wrapper.get('[data-test="save-master-field"]').trigger('click')
    await flushPromises()
    expect(updateMasterField).toHaveBeenCalledWith('person', 11, expect.objectContaining({ data_type: 'STRING', reference_object_type_id: null }))
  })

  it('creates child types and child fields with a shared checkbox', async () => {
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '新增子表').trigger('click')
    wrapper.vm.childType.code = 'education'
    wrapper.vm.childType.name = '教育经历'
    await wrapper.get('[data-test="save-child-type"]').trigger('click')
    await flushPromises()
    expect(createChildType).toHaveBeenCalledWith('person', { code: 'education', name: '教育经历', sort_order: 0 })
    await wrapper.get('[data-test="add-child-field-21"]').trigger('click')
    wrapper.vm.childField.field_key = 'school'
    wrapper.vm.childField.field_name = '学校'
    wrapper.vm.childField.shared = true
    await wrapper.get('[data-test="save-child-field"]').trigger('click')
    await flushPromises()
    expect(createChildField).toHaveBeenCalledWith('person', 21, expect.objectContaining({ field_key: 'school', field_name: '学校', shared: true }))
  })

  it('hides all mutations and shows read-only state for a viewer', async () => {
    localStorage.setItem('permissions', JSON.stringify([{ code: 'MDM_FIELD_MANAGE', can_edit: false }]))
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('只读')
    expect(wrapper.find('[data-test="add-master-field"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-master-11"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="add-child-field-21"]').exists()).toBe(false)
  })

  it('uses Chinese controls to update and deactivate an object type', async () => {
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.get('[data-test="edit-object-person"]').text()).toContain('编辑对象')
    await wrapper.get('[data-test="edit-object-person"]').trigger('click')
    wrapper.vm.objectType.name = '人员主数据'
    wrapper.vm.objectType.approval_required = true
    wrapper.vm.objectType.department_scoped = false
    await wrapper.get('[data-test="save-object-type"]').trigger('click')
    await flushPromises()
    expect(updateObjectType).toHaveBeenCalledWith('person', {
      name: '人员主数据', approval_required: true, department_scoped: false,
    })
    await wrapper.get('[data-test="deactivate-object-person"]').trigger('click')
    await flushPromises()
    expect(deactivateObjectType).toHaveBeenCalledWith('person')
  })

  it('shows inactive metadata only when requested and lets a manager reactivate it', async () => {
    listObjectTypes.mockResolvedValue({ data: [{
      ...metadata[0], status: 'inactive', fields: [{ ...metadata[0].fields[0], status: 'inactive' }],
      child_types: [{ ...metadata[0].child_types[0], status: 'inactive', fields: [{ ...metadata[0].child_types[0].fields[0], status: 'inactive' }] }],
    }] })
    const wrapper = mount(MetadataManager, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('人员')
    await wrapper.get('[data-test="show-inactive"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-test="reactivate-object-person"]').text()).toContain('重新启用对象')
    expect(wrapper.find('.inactive-item').exists()).toBe(true)
    await wrapper.get('[data-test="reactivate-object-person"]').trigger('click')
    await flushPromises()
    expect(reactivateObjectType).toHaveBeenCalledWith('person')
  })
})
