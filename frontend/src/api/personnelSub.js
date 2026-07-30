import request from '../utils/request'

export function listSub(personnelId) {
  return request.get(`/personnel/${personnelId}/sub`)
}

export function createSub(personnelId, data) {
  return request.post(`/personnel/${personnelId}/sub`, data)
}

export function updateSub(personnelId, subId, data) {
  return request.put(`/personnel/${personnelId}/sub/${subId}`, data)
}
