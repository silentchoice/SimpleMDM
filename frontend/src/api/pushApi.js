import request from '../utils/request'

export function listPushApis(params) {
  return request.get('/push-apis', { params })
}

export function getPushApi(id) {
  return request.get(`/push-apis/${id}`)
}

export function createPushApi(data) {
  return request.post('/push-apis', data)
}

export function updatePushApi(id, data) {
  return request.put(`/push-apis/${id}`, data)
}

export function deletePushApi(id) {
  return request.delete(`/push-apis/${id}`)
}

export function testPushApi(id) {
  return request.post(`/push-apis/${id}/test`)
}

export function getActiveApis() {
  return request.get('/push-apis/active')
}
