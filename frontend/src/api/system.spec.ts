import { beforeEach, describe, expect, it, vi } from 'vitest'

const { http } = vi.hoisted(() => ({
  http: {
    get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()
  }
}))

vi.mock('./http', () => ({ http }))

import {
  assignUserRoles, createDepartment, createUser, deleteDepartment, getDepartment,
  listDepartments, listRoles, listUsers, setDepartmentStatus, setUserStatus,
  updateDepartment, updateUser, type CreateUserInput
} from './system'

describe('system API client', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('uses the department endpoints with exact verbs, paths, and status query values', async () => {
    http.get.mockResolvedValue([])
    http.post.mockResolvedValue({ id: 7 })
    http.put.mockResolvedValue({ id: 7 })
    http.patch.mockResolvedValue(undefined)
    http.delete.mockResolvedValue(undefined)

    await listDepartments()
    await getDepartment(7)
    await createDepartment({ code: 'OPS', name: 'Operations' })
    await updateDepartment(7, { code: 'OPS', name: 'Operations' })
    await setDepartmentStatus(7, 'DISABLED')
    await deleteDepartment(7)

    expect(http.get).toHaveBeenNthCalledWith(1, '/department')
    expect(http.get).toHaveBeenNthCalledWith(2, '/department/7')
    expect(http.post).toHaveBeenCalledWith('/department', { code: 'OPS', name: 'Operations' })
    expect(http.put).toHaveBeenCalledWith('/department/7', { code: 'OPS', name: 'Operations' })
    expect(http.patch).toHaveBeenCalledWith('/department/7/status?status=DISABLED')
    expect(http.delete).toHaveBeenCalledWith('/department/7')
  })

  it('uses the user endpoints with create, update, status, and fixed-role bodies', async () => {
    http.get.mockResolvedValue([])
    http.post.mockResolvedValue({ id: 9 })
    http.put.mockResolvedValue({ id: 9 })
    http.patch.mockResolvedValue(undefined)
    const user: CreateUserInput = { username: 'jdoe', password: 'secret-123', displayName: 'Jane Doe', departmentId: 3, roles: ['DEPT_EDITOR'] }

    await listUsers()
    await createUser(user)
    await updateUser(9, { displayName: 'Jane D.', departmentId: null })
    await setUserStatus(9, 'ACTIVE')
    await assignUserRoles(9, ['DEPT_APPROVER', 'DEPT_VIEWER'])

    expect(http.get).toHaveBeenCalledWith('/user')
    expect(http.post).toHaveBeenCalledWith('/user', user)
    expect(http.put).toHaveBeenNthCalledWith(1, '/user/9', { displayName: 'Jane D.', departmentId: null })
    expect(http.patch).toHaveBeenCalledWith('/user/9/status?status=ACTIVE')
    expect(http.put).toHaveBeenNthCalledWith(2, '/user/9/roles', ['DEPT_APPROVER', 'DEPT_VIEWER'])
  })

  it('lists fixed roles from the read-only role endpoint', async () => {
    http.get.mockResolvedValue(['SUPER_ADMIN'])

    await expect(listRoles()).resolves.toEqual(['SUPER_ADMIN'])

    expect(http.get).toHaveBeenCalledWith('/role')
  })
})
