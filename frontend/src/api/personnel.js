import request from '../utils/request'

export function listPersonnel(params) {
  return request.get('/personnel', { params })
}

export function getPersonnel(id) {
  return request.get(`/personnel/${id}`)
}

export function createPersonnel(data) {
  return request.post('/personnel', data)
}

export function updatePersonnel(id, data) {
  return request.put(`/personnel/${id}`, data)
}

export function getDepartments() {
  return request.get('/personnel/departments')
}
