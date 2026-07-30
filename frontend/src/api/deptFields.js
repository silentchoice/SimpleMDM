import request from '../utils/request'

export function listFieldDefs(subType = '', tableType = '') {
  return request.get('/dept-fields', { params: { sub_type: subType, table_type: tableType } })
}

export function getFieldDefsByType(subType, department) {
  return request.get('/dept-fields/by-type', { params: { sub_type: subType, department } })
}

export function listSubTypes(tableType = 'sub') {
  return request.get('/dept-fields/sub-types', { params: { table_type: tableType } })
}

export function createFieldDef(data) {
  return request.post('/dept-fields', data)
}

export function updateFieldDef(id, data) {
  return request.put(`/dept-fields/${id}`, data)
}
