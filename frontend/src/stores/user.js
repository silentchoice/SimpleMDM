import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
  const permissions = ref(JSON.parse(localStorage.getItem('permissions') || '[]'))

  const isLoggedIn = computed(() => !!token.value)

  // New permission model: user object has is_admin, permissions come from permissions array
  const isAdmin = computed(() => user.value?.is_admin || false)

  // Permission check getters
  const hasEditPermission = computed(() =>
    isAdmin.value || permissions.value.some(p => p.perm_type === 'EDIT')
  )
  const hasViewPermission = computed(() =>
    isAdmin.value || permissions.value.some(p => p.perm_type === 'VIEW')
  )

  const canManageOwnDepartment = computed(() =>
    !isAdmin.value && permissions.value.some(permission =>
      permission.perm_type === 'EDIT' &&
      permission.scope_value === user.value?.department
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

  async function login(username, password) {
    const res = await request.post('/auth/login', { username, password })
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
