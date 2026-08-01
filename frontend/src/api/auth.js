import request from '../utils/request'

export function login(systemCode, username, password) {
  return request.post('/auth/login', { system_code: systemCode, username, password })
}

export function getMe() {
  return request.get('/auth/me')
}
