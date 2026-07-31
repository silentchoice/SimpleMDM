// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TypedFieldInput from './TypedFieldInput.vue'

describe('TypedFieldInput', () => {
  it('keeps decimal input as an exact string', async () => {
    const wrapper = mount(TypedFieldInput, {
      props: { field: { data_type: 'DECIMAL' }, modelValue: null },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.find('input').setValue('12345678901234567890.1234567890')
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual(['12345678901234567890.1234567890'])
  })

  it('supports an unset boolean value', () => {
    const wrapper = mount(TypedFieldInput, {
      props: { field: { data_type: 'BOOLEAN' }, modelValue: null },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.props('modelValue')).toBeNull()
  })
})
