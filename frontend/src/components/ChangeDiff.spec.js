// @vitest-environment jsdom
import { expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import ChangeDiff from './ChangeDiff.vue'

it('displays current field names for stable diff keys', () => {
  const wrapper = shallowMount(ChangeDiff, {
    props: {
      changeData: {
        owner_dept: { old: '工程部', new: '产品部' },
        employee_code: { old: 'EMP001', new: 'EMP002' },
      },
      definitions: [
        { field_key: 'employee_code', field_name: '员工编号' },
      ],
    },
    global: {
      stubs: {
        'el-table': { template: '<div />' },
        'el-table-column': { template: '<div />' },
      },
    },
  })

  expect(wrapper.vm.rows.map(row => row.field)).toEqual(['所属部门', '员工编号'])
})
