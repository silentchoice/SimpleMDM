import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import MetadataEditor from './MetadataEditor.vue'
import type { FieldDefinition } from '../../api/metadata'

const activeField: FieldDefinition = { id: 1, ownerTypeId: 41, code: 'SERIAL', displayName: 'Serial number', fieldType: 'TEXT', required: true, options: [], shared: false, sortOrder: 0, status: 'ACTIVE' }

function mountEditor(onSubmit = vi.fn().mockResolvedValue({ approvalTaskId: 701 })) {
  return mount(MetadataEditor, { props: { family: 'master-fields', ownerId: 41, activeItems: [activeField], onSubmit }, global: { plugins: [ElementPlus] } })
}

describe('metadata editor', () => {
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
    expect(wrapper.text()).toContain('Code must start with a letter')
    await wrapper.get('[name="code"]').setValue('NEW_FIELD')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Name is required')
    await wrapper.get('[name="displayName"]').setValue('New field')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Field type is required')
  })

  it('requires distinct select options and rejects duplicate codes or orders', async () => {
    const wrapper = mountEditor()
    await wrapper.get('[data-testid="add-item"]').trigger('click')
    await wrapper.get('[name="code"]').setValue('SERIAL')
    await wrapper.get('[name="displayName"]').setValue('Duplicate')
    await wrapper.get('[name="fieldType"]').setValue('SELECT')
    await wrapper.get('[name="options"]').setValue('A, A')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Options must be unique')
    await wrapper.get('[name="options"]').setValue('A, B')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Duplicate code')
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

    expect(onSubmit).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ code: 'SECOND', sortOrder: 0 })]))
    expect(wrapper.text()).toContain('Approval task #701')
  })

  it('rejects repeated sort orders before submitting a field family', async () => {
    const wrapper = mount(MetadataEditor, { props: { family: 'master-fields', ownerId: 41, activeItems: [activeField, { ...activeField, id: 2, code: 'SECOND', sortOrder: 0 }], onSubmit: vi.fn() }, global: { plugins: [ElementPlus] } })
    await wrapper.get('[data-testid="submit-master-fields"]').trigger('click')

    expect(wrapper.text()).toContain('Duplicate sort order')
  })
})
