import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../utils/request'
import { useRouter } from 'vue-router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')

  function isOperator() { return role.value === 'operator' }
  function isApprover() { return role.value === 'approver' }
  function isViewer() { return role.value === 'viewer' }

  async function login(username, password) {
    const res = await request.post('/auth/login', { username, password })
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('token', token.value)
    localStorage.setItem('user', JSON.stringify(user.value))
    return res
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, isLoggedIn, role, isOperator, isApprover, isViewer, login, logout }
})
