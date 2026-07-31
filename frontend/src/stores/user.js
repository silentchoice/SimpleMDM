import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginRequest } from '../api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
  const permissions = ref(JSON.parse(localStorage.getItem('permissions') || '[]'))

  const isLoggedIn = computed(() => !!token.value)

  // New permission model: user object has is_admin, permissions come from permissions array
  const isAdmin = computed(() => user.value?.is_admin || false)

  const permissionCodes = computed(() => new Set(
    permissions.value.map(permission => permission.code || permission.permission_code)
  ))

  const hasEditPermission = computed(() =>
    isAdmin.value || permissions.value.some(permission =>
      (permission.code === 'MDM_RECORD_EDIT' || permission.permission_code === 'MDM_RECORD_EDIT') &&
      permission.can_edit === true
    )
  )
  const hasViewPermission = computed(() =>
    isAdmin.value || permissionCodes.value.has('MDM_RECORD_VIEW')
  )

  const canManageOwnDepartment = computed(() =>
    !isAdmin.value && user.value?.department_id != null && permissions.value.some(permission =>
      (permission.code === 'MDM_RECORD_EDIT' || permission.permission_code === 'MDM_RECORD_EDIT') &&
      (Array.isArray(permission.editable_department_ids)
        ? permission.editable_department_ids.map(Number).includes(Number(user.value.department_id))
        : permission.can_edit === true)
    )
  )
  // Backward-compatible role helpers
  const role = computed(() => {
    if (isAdmin.value) return 'admin'
    return user.value?.role || ''
  })

  function isOperator() { return hasEditPermission.value }
  function isApprover() { return role.value === 'approver' }
  function isViewer() { return !hasEditPermission.value && hasViewPermission.value }

  async function login(systemCode, username, password) {
    const res = await loginRequest(systemCode, username, password)
    token.value = res.data.token
    user.value = res.data.user
    permissions.value = res.data.permissions || []
    localStorage.setItem('token', token.value)
    localStorage.setItem('user', JSON.stringify(user.value))
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
    return res
  }

  function logout() {
    token.value = ''
    user.value = null
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('permissions')
  }

  return {
    token, user, permissions, isLoggedIn, isAdmin, role,
    isOperator, isApprover, isViewer,
    hasEditPermission, hasViewPermission, canManageOwnDepartment,
    login, logout,
  }
})
