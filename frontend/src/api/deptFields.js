import request from '../utils/request'

export function listFieldDefs(subType = '') {
  return request.get('/dept-fields', { params: { sub_type: subType } })
}

export function getFieldDefsByType(subType, department) {
  return request.get('/dept-fields/by-type', { params: { sub_type: subType, department } })
}

export function listSubTypes() {
  return request.get('/dept-fields/sub-types')
}

export function createFieldDef(data) {
  return request.post('/dept-fields', data)
}

export function updateFieldDef(id, data) {
  return request.put(`/dept-fields/${id}`, data)
}
