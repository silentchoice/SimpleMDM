// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from './user'

describe('user store relational permission contract', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('uses permission codes and department ids without legacy permission fields', () => {
    localStorage.setItem('user', JSON.stringify({ is_admin: false, department_id: 10 }))
    localStorage.setItem('permissions', JSON.stringify([
      { code: 'MDM_RECORD_VIEW' },
      { permission_code: 'MDM_RECORD_EDIT', can_edit: true },
    ]))
    setActivePinia(createPinia())

    const store = useUserStore()
    expect(store.hasViewPermission).toBe(true)
    expect(store.hasEditPermission).toBe(true)
    expect(store.canManageOwnDepartment).toBe(true)
  })

  it('does not grant UI edit capability from an edit code with can_edit false', () => {
    localStorage.setItem('user', JSON.stringify({ is_admin: false, department_id: 10 }))
    localStorage.setItem('permissions', JSON.stringify([
      { code: 'MDM_RECORD_EDIT', can_edit: false },
    ]))
    setActivePinia(createPinia())

    expect(useUserStore().hasEditPermission).toBe(false)
  })
})
