// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import List from './List.vue'
import { useUserStore } from '../../stores/user'
import { getDepartments, listPersonnel } from '../../api/personnel'
import { listFieldDefs } from '../../api/deptFields'

const route = { query: {} }
const router = { push: vi.fn(), replace: vi.fn() }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

vi.mock('../../api/personnel', () => ({
  getDepartments: vi.fn(),
  listPersonnel: vi.fn(),
}))

vi.mock('../../api/deptFields', () => ({
  listFieldDefs: vi.fn(),
}))

function mountList({ userDepartment = '工程部', routeQuery = {} } = {}) {
  route.query = routeQuery
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.user = { id: 7, department: userDepartment, is_admin: false }
  store.permissions = [{ perm_type: 'EDIT', scope_value: userDepartment, scope_type: 'DEPT' }]
  return mount(List, { global: { plugins: [pinia, ElementPlus] } })
}

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

describe('department personnel list', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
    getDepartments.mockResolvedValue({ data: ['工程部', '产品部'] })
    listFieldDefs.mockResolvedValue({ data: [] })
    listPersonnel.mockResolvedValue({ data: { items: [], total: 0 } })
  })

  it('defaults to the user department and never requests all departments', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(listPersonnel).toHaveBeenCalledWith(expect.objectContaining({ department: '工程部' }))
    expect(wrapper.text()).not.toContain('全部')
  })

  it('stores another department in the URL and hides create and edit actions', async () => {
    const wrapper = mountList()
    await flushPromises()

    await wrapper.vm.selectDepartment('产品部')
    await flushPromises()

    expect(router.replace).toHaveBeenCalledWith({
      query: expect.objectContaining({ department: '产品部' }),
    })
    expect(wrapper.text()).not.toContain('新增')
    expect(wrapper.text()).not.toContain('编辑')
  })

  it('keeps the newest department response when requests finish out of order', async () => {
    const first = deferred()
    const second = deferred()
    listPersonnel.mockReset()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const wrapper = mountList()
    await flushPromises()
    const switchPromise = wrapper.vm.selectDepartment('产品部')
    second.resolve({ data: { items: [{ id: 2, owner_dept: '产品部', status: 'active' }], total: 1 } })
    await switchPromise
    first.resolve({ data: { items: [{ id: 1, owner_dept: '工程部', status: 'active' }], total: 1 } })
    await flushPromises()

    expect(wrapper.text()).toContain('产品部')
    expect(wrapper.text()).not.toContain('工程部')
  })
})
