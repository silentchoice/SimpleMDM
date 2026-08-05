import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DepartmentDrawer from '../../components/system/DepartmentDrawer.vue'
import UserDrawer from '../../components/system/UserDrawer.vue'
import { createAppRouter } from '../../router'
import { useAuthStore } from '../../stores/auth'
import DepartmentListView from './DepartmentListView.vue'
import RoleListView from './RoleListView.vue'
import UserListView from './UserListView.vue'
import { i18n, setLocale } from '../../i18n'

const api = vi.hoisted(() => ({
  listDepartments: vi.fn(), getDepartment: vi.fn(), createDepartment: vi.fn(), updateDepartment: vi.fn(), setDepartmentStatus: vi.fn(), deleteDepartment: vi.fn(),
  listUsers: vi.fn(), createUser: vi.fn(), updateUser: vi.fn(), setUserStatus: vi.fn(), assignUserRoles: vi.fn(), listRoles: vi.fn()
}))

vi.mock('../../api/system', () => api)

describe('system administration views', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listDepartments.mockResolvedValue([{ id: 3, code: 'OPS', name: 'Operations', status: 'ACTIVE' }])
    api.listUsers.mockResolvedValue([])
    api.listRoles.mockResolvedValue(['SUPER_ADMIN', 'DEPT_EDITOR', 'DEPT_APPROVER', 'DEPT_VIEWER'])
    localStorage.clear()
    setLocale('zh-CN')
  })

  const globals = { plugins: [ElementPlus, i18n] }

  it('validates a department before saving, refreshes only after success, and resets on close', async () => {
    const saved = vi.fn()
    const wrapper = mount(DepartmentDrawer, { props: { open: true, department: null, saving: false, onSaved: saved }, global: globals })

    await wrapper.get('form').trigger('submit')
    expect(saved).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入代码和名称')

    await wrapper.get('[name="code"]').setValue('OPS')
    await wrapper.get('[name="name"]').setValue('Operations')
    await wrapper.get('form').trigger('submit')
    expect(saved).toHaveBeenCalledWith({ code: 'OPS', name: 'Operations' })

    await wrapper.get('[data-testid="department-cancel"]').trigger('click')
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    expect((wrapper.get('[name="code"]').element as HTMLInputElement).value).toBe('')
  })

  it('prevents duplicate user submits and passes selected fixed roles to its save handler', async () => {
    const saved = vi.fn((): Promise<void> => new Promise(() => undefined))
    const wrapper = mount(UserDrawer, { props: { open: true, user: null, departments: [{ id: 3, code: 'OPS', name: 'Operations', status: 'ACTIVE' }], saving: false, onSaved: saved }, global: globals })

    await wrapper.get('[name="username"]').setValue('jdoe')
    await wrapper.get('[name="password"]').setValue('secret-123')
    await wrapper.get('[name="displayName"]').setValue('Jane Doe')
    await wrapper.get('[name="departmentId"]').setValue('3')
    await wrapper.get('[name="roles"]').setValue(['DEPT_EDITOR'])
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(saved).toHaveBeenCalledTimes(1)
    expect(saved).toHaveBeenCalledWith({ username: 'jdoe', password: 'secret-123', displayName: 'Jane Doe', departmentId: 3, roles: ['DEPT_EDITOR'] })
  })

  it('requires reassignment before saving an existing user whose department is disabled', async () => {
    const saved = vi.fn()
    const wrapper = mount(UserDrawer, {
      props: {
        open: true,
        user: { id: 9, username: 'jdoe', displayName: 'Jane Doe', departmentId: 3, status: 'ACTIVE', roles: ['DEPT_EDITOR'] },
        departments: [
          { id: 3, code: 'OPS', name: 'Operations', status: 'DISABLED' },
          { id: 4, code: 'SALES', name: 'Sales', status: 'ACTIVE' }
        ],
        saving: false,
        onSaved: saved
      },
      global: globals
    })

    await wrapper.get('form').trigger('submit')

    expect(saved).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请选择启用的部门后再保存')
  })

  it('shows API errors with request IDs and refreshes departments only after a successful create', async () => {
    api.createDepartment.mockRejectedValueOnce({ message: 'Duplicate department code', requestId: 'req-409' })
    const wrapper = mount(DepartmentListView, { global: globals })
    await flushPromises()
    await wrapper.get('[data-testid="department-create"]').trigger('click')
    await wrapper.get('[name="code"]').setValue('OPS')
    await wrapper.get('[name="name"]').setValue('Operations')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.listDepartments).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Duplicate department code（请求 ID：req-409）')

    api.createDepartment.mockResolvedValueOnce({ id: 4, code: 'OPS', name: 'Operations', status: 'ACTIVE' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.listDepartments).toHaveBeenCalledTimes(2)
  })

  it('renders roles as a read-only list without create or edit controls', async () => {
    const wrapper = mount(RoleListView, { global: globals })
    await flushPromises()

    expect(wrapper.text()).toContain('SUPER_ADMIN')
    expect(wrapper.find('[data-testid="role-create"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="role-edit"]').exists()).toBe(false)
  })

  it('renders translated system headings, actions, statuses, and switches them to English', async () => {
    const department = mount(DepartmentListView, { global: globals })
    const user = mount(UserListView, { global: globals })
    const roles = mount(RoleListView, { global: globals })
    await flushPromises()

    expect(department.text()).toContain('部门管理')
    expect(department.text()).toContain('创建部门')
    expect(department.text()).toContain('启用')
    expect(user.text()).toContain('用户管理')
    expect(roles.text()).toContain('角色管理')

    setLocale('en-US')
    await flushPromises()

    expect(department.text()).toContain('Departments')
    expect(department.text()).toContain('Create department')
    expect(department.text()).toContain('Active')
    expect(user.text()).toContain('Users')
    expect(roles.text()).toContain('Roles')
  })

  it('prevents department-role users from navigating to every system administration route', async () => {
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 'token', user: { id: 2, username: 'editor', displayName: 'Editor' }, roles: ['DEPT_EDITOR'], department: null })
    const router = createAppRouter()

    for (const path of ['/system/departments', '/system/users', '/system/roles']) {
      await router.push(path)
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/forbidden')
    }
  })
})
