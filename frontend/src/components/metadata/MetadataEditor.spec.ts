import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MetadataEditor from './MetadataEditor.vue'
import type { FieldDefinition } from '../../api/metadata'
import { i18n, setLocale } from '../../i18n'

const activeField: FieldDefinition = { id: 1, ownerTypeId: 41, code: 'SERIAL', displayName: 'Serial number', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }

function mountEditor(onSubmit = vi.fn().mockResolvedValue({ approvalTaskId: 701 })) {
  return mount(MetadataEditor, { props: { family: 'master-fields', ownerId: 41, activeItems: [activeField], onSubmit }, global: { plugins: [ElementPlus, i18n] } })
}

describe('metadata editor', () => {
  beforeEach(() => {
    localStorage.clear()
    setLocale('zh-CN')
  })

  it('deep-copies ACTIVE fields before editing and leaves props untouched', async () => {
    const wrapper = mountEditor()
    await wrapper.get('[data-testid="edit-0"]').trigger('click')
    await wrapper.get('[name="displayName"]').setValue('Changed')
    await wrapper.get('form').trigger('submit')

    expect(activeField.displayName).toBe('Serial number')
    expect(wrapper.emitted('update:activeItems')).toBeUndefined()
  })

  it('rejects invalid code and missing field name or type', async () => {
    const wrapper = mountEditor()
    await wrapper.get('[data-testid="add-item"]').trigger('click')
    await wrapper.get('[name="code"]').setValue('1bad')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('代码必须以字母开头')
    await wrapper.get('[name="code"]').setValue('NEW_FIELD')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('名称为必填项')
    await wrapper.get('[name="displayName"]').setValue('New field')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('字段类型为必填项')
  })

  it('requires distinct select options and rejects duplicate codes or orders', async () => {
    const wrapper = mountEditor()
    await wrapper.get('[data-testid="add-item"]').trigger('click')
    await wrapper.get('[name="code"]').setValue('SERIAL')
    await wrapper.get('[name="displayName"]').setValue('Duplicate')
    await wrapper.get('[name="fieldType"]').setValue('SELECT')
    await wrapper.get('[name="options"]').setValue('A, A')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('选项不能重复')
    await wrapper.get('[name="options"]').setValue('A, B')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('代码重复')
  })

  it('reorders drafts and displays the approval task ID without reloading ACTIVE', async () => {
    const onSubmit = vi.fn().mockResolvedValue({ approvalTaskId: 701 })
    const wrapper = mountEditor(onSubmit)
    await wrapper.get('[data-testid="add-item"]').trigger('click')
    await wrapper.get('[name="code"]').setValue('SECOND')
    await wrapper.get('[name="displayName"]').setValue('Second')
    await wrapper.get('[name="fieldType"]').setValue('TEXT')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('[data-testid="move-up-1"]').trigger('click')
    await wrapper.get('[data-testid="submit-master-fields"]').trigger('click')

    expect(onSubmit).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ code: 'SECOND', fieldType: 'TEXT', sortOrder: 0 })]))
    expect(wrapper.text()).toContain('审批任务 #701')
  })

  it('rejects repeated sort orders before submitting a field family', async () => {
    const wrapper = mount(MetadataEditor, { props: { family: 'master-fields', ownerId: 41, activeItems: [activeField, { ...activeField, id: 2, code: 'SECOND', sortOrder: 0 }], onSubmit: vi.fn() }, global: { plugins: [ElementPlus, i18n] } })
    await wrapper.get('[data-testid="submit-master-fields"]').trigger('click')

    expect(wrapper.text()).toContain('排序序号重复')
  })

  it('removes a draft item so the submitted replacement snapshot can delete ACTIVE metadata', async () => {
    const onSubmit = vi.fn().mockResolvedValue({ approvalTaskId: 702 })
    const wrapper = mount(MetadataEditor, { props: { family: 'master-fields', ownerId: 41, activeItems: [activeField, { ...activeField, id: 2, code: 'SECOND', displayName: 'Second', sortOrder: 1 }], onSubmit }, global: { plugins: [ElementPlus, i18n] } })
    await wrapper.get('[data-testid="remove-0"]').trigger('click')
    await wrapper.get('[data-testid="submit-master-fields"]').trigger('click')

    expect(onSubmit).toHaveBeenCalledWith([expect.objectContaining({ code: 'SECOND', sortOrder: 0 })])
  })

  it('prevents duplicate concurrent submissions and retains request IDs in failures', async () => {
    let resolve!: (value: { approvalTaskId: number }) => void
    const onSubmit = vi.fn(() => new Promise<{ approvalTaskId: number }>((done) => { resolve = done }))
    const wrapper = mountEditor(onSubmit)
    const submit = wrapper.get('[data-testid="submit-master-fields"]')
    await submit.trigger('click')
    await submit.trigger('click')
    expect(onSubmit).toHaveBeenCalledTimes(1)
    resolve({ approvalTaskId: 703 })
    await flushPromises()

    const failed = mountEditor(vi.fn().mockRejectedValue({ message: 'Pending task', requestId: 'req-pending' }))
    await failed.get('[data-testid="submit-master-fields"]').trigger('click')
    await flushPromises()
    expect(failed.text()).toContain('Pending task（请求 ID：req-pending）')
  })

  it('switches editor actions, field-type labels, and feedback to English without translating payload enums', async () => {
    const onSubmit = vi.fn().mockResolvedValue({ approvalTaskId: 704 })
    const wrapper = mountEditor(onSubmit)
    expect(wrapper.text()).toContain('主字段')
    expect(wrapper.text()).toContain('添加')
    await wrapper.get('[data-testid="add-item"]').trigger('click')
    expect(wrapper.get('[name="fieldType"]').text()).toContain('文本')
    expect(wrapper.get('[name="fieldType"]').find('option[value="TEXT"]').exists()).toBe(true)
    await wrapper.get('[name="code"]').setValue('NEW_FIELD')
    await wrapper.get('[name="displayName"]').setValue('New field')
    await wrapper.get('[name="fieldType"]').setValue('TEXT')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('[data-testid="submit-master-fields"]').trigger('click')
    await flushPromises()
    expect(onSubmit).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ fieldType: 'TEXT' })]))

    setLocale('en-US')
    await flushPromises()
    expect(wrapper.text()).toContain('Master fields')
    expect(wrapper.text()).toContain('Add')
    expect(wrapper.text()).toContain('Approval task #704')
  })
})
