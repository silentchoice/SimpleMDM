// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import Detail from './Detail.vue'
import { approve, getApproval } from '../../api/workflow'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '6' } }) }))
vi.mock('../../api/workflow', () => ({ approve: vi.fn(), getApproval: vi.fn() }))

const approval = {
  id: 6, record_id: 12, record_code: 'P012', department_id: 10, operation: 'UPDATE', status: 'PENDING', can_approve: false,
  changes: [{ field_key: 'name', field_name: '姓名', old_value: '张三', new_value: '张四' }],
  child_changes: [{ change_key: 'part_time-31', child_type_id: 20, child_type_name: '兼职信息', child_record_id: 31, operation: 'UPDATE', expected_version: 2,
    values: [{ field_key: 'company', field_name: '兼职单位', old_value: '甲公司', new_value: '乙公司' }] }],
}

describe('审批详情', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getApproval.mockResolvedValue({ data: approval })
    approve.mockResolvedValue({ data: { status: 'APPROVED' } })
    localStorage.setItem('permissions', '[]')
    localStorage.setItem('user', JSON.stringify({ department_id: 10, is_admin: false }))
  })

  it('groups projected master and child diffs with Chinese labels', async () => {
    const wrapper = mount(Detail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('主表变更')
    expect(wrapper.text()).toContain('子表变更')
    expect(wrapper.text()).toContain('姓名')
    expect(wrapper.text()).toContain('兼职单位')
    expect(wrapper.text()).toContain('兼职信息')
    expect(wrapper.text()).not.toContain('company')
    expect(wrapper.text()).toContain('待审批')
    expect(wrapper.text()).not.toContain('Approval details')
  })

  it('shows approval only when the server grants the capability', async () => {
    getApproval.mockResolvedValue({ data: { ...approval, can_approve: true } })
    const wrapper = mount(Detail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('[data-test="approve"]').exists()).toBe(true)
  })

  it('does not infer approval capability from local reviewer permissions', async () => {
    localStorage.setItem('permissions', JSON.stringify([{ code: 'APPROVAL_REVIEW', editable_department_ids: [10] }]))
    const wrapper = mount(Detail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('[data-test="approve"]').exists()).toBe(false)
  })
})
