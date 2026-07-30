// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DynamicFieldInput from './DynamicFieldInput.vue'

const stubs = {
  'el-input': { template: '<input data-control="input" />' },
  'el-input-number': { template: '<input data-control="number" />' },
  'el-date-picker': { template: '<input data-control="date" />' },
  'el-select': { template: '<select data-control="select"><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-radio-group': { template: '<div data-control="radio"><slot /></div>' },
  'el-radio': { template: '<label />' },
}

describe('DynamicFieldInput', () => {
  it.each([
    ['string', 'input'],
    ['number', 'number'],
    ['date', 'date'],
    ['select', 'select'],
    ['radio', 'radio'],
  ])('renders %s definitions with the matching control', (fieldType, control) => {
    const wrapper = mount(DynamicFieldInput, {
      props: {
        modelValue: '',
        definition: {
          field_key: 'value',
          field_name: '值',
          field_type: fieldType,
          options: ['A', 'B'],
        },
      },
      global: { stubs },
    })

    expect(wrapper.find(`[data-control="${control}"]`).exists()).toBe(true)
  })
})
