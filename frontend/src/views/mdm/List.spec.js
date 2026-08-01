// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import List from './List.vue'
import { listObjectTypes, listRecords } from '../../api/mdm'
import { distributeRecord } from '../../api/integration'
import { listSystems } from '../../api/systems'
import { getDepartmentTree } from '../../api/departments'

const route = { query: { system: 'HR', object: 'person', department: '10' } }
const router = { replace: vi.fn(), push: vi.fn() }
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))
vi.mock('../../api/mdm', () => ({ listObjectTypes: vi.fn(), listRecords: vi.fn() }))
vi.mock('../../api/integration', () => ({ distributeRecord: vi.fn() }))
vi.mock('../../api/systems', () => ({ listSystems: vi.fn() }))
vi.mock('../../api/departments', () => ({ getDepartmentTree: vi.fn() }))

describe('generic MDM list', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    localStorage.setItem('user', JSON.stringify({ system_id: 1, department_id: 10 }))
    localStorage.setItem('permissions', JSON.stringify([
      { code: 'MDM_RECORD_VIEW' },
      { code: 'MDM_RECORD_EDIT', can_edit: false, editable_department_ids: [] },
    ]))
    listSystems.mockResolvedValue({ data: [{ id: 1, code: 'HR', name: '人力资源' }] })
    getDepartmentTree.mockResolvedValue({ data: [{ id: 10, name: '总部', children: [] }] })
    listObjectTypes.mockResolvedValue({ data: [{ id: 2, code: 'person', name: '人员', fields: [
      { field_key: 'name', field_name: '姓名', data_type: 'STRING' },
      { field_key: 'private_note', field_name: '私密备注', data_type: 'STRING' },
    ] }] })
    listRecords.mockResolvedValue({ data: [{ id: 7, department_id: 10, record_code: 'P001', version: 1, can_distribute: false, data: { name: '张三' } }] })
    distributeRecord.mockResolvedValue({ data: { log_ids: [4] } })
  })

  it('reads metadata and scoped records from the URL context', async () => {
    const wrapper = mount(List, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(listObjectTypes).toHaveBeenCalled()
    expect(listRecords).toHaveBeenCalledWith('person')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).not.toContain('私密备注')
  })

  it('hides create and edit actions when can_edit is false', async () => {
    const wrapper = mount(List, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(wrapper.find('[data-test="create"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-7"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="distribute-7"]').exists()).toBe(false)
  })

  it('shows manual latest-snapshot distribution only with the row capability', async () => {
    listRecords.mockResolvedValue({ data: [{ id: 7, department_id: 10, record_code: 'P001', version: 1, can_distribute: true, data: { name: '张三' } }] })
    const wrapper = mount(List, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    await wrapper.get('[data-test="distribute-7"]').trigger('click')
    await flushPromises()
    expect(distributeRecord).toHaveBeenCalledWith(7, { reason: '管理界面手动分发最新快照' })
    expect(wrapper.text()).toContain('已创建分发任务')
  })

  it('does not infer distribution capability from local permissions', async () => {
    localStorage.setItem('permissions', JSON.stringify([{ code: 'INTEGRATION_MANUAL_PUSH', can_edit: true }]))
    const wrapper = mount(List, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(wrapper.find('[data-test="distribute-7"]').exists()).toBe(false)
  })

  it('shows a Chinese initialization error when metadata loading fails', async () => {
    listObjectTypes.mockRejectedValue(new Error('metadata offline'))
    const wrapper = mount(List, { global: { plugins: [ElementPlus, createPinia()] } })
    await flushPromises()
    expect(wrapper.text()).toContain('初始化主数据列表失败')
  })
})
