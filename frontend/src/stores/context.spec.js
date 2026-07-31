// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useContextStore } from './context'
import { listSystems } from '../api/systems'
import { getDepartmentTree } from '../api/departments'

vi.mock('../api/systems', () => ({ listSystems: vi.fn() }))
vi.mock('../api/departments', () => ({ getDepartmentTree: vi.fn() }))

describe('generic MDM context', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listSystems.mockResolvedValue({ data: [{ id: 1, code: 'HR', name: '人力资源' }] })
    getDepartmentTree.mockResolvedValue({ data: [{ id: 10, children: [] }] })
  })

  it('defaults to the user system and primary department', async () => {
    const store = useContextStore()
    await store.initialize({ system_id: 1, department_id: 10 })
    expect(store.systemId).toBe(1)
    expect(store.departmentId).toBe(10)
  })

  it('uses valid URL codes and ids over user defaults', async () => {
    const store = useContextStore()
    await store.initialize(
      { system_id: 1, department_id: 10 },
      { system: 'HR', object: 'person', department: '20' },
    )
    expect(store.objectCode).toBe('person')
    expect(store.departmentId).toBe(10)
    expect(store.query).toEqual({ system: 'HR', object: 'person', department: '10' })
  })
})
