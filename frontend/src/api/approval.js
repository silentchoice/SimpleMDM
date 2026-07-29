import request from '../utils/request'

export function listApprovals(params) {
  return request.get('/approvals', { params })
}

export function getApproval(id) {
  return request.get(`/approvals/${id}`)
}

export function approve(id, comment) {
  return request.post(`/approvals/${id}/approve`, { comment })
}

export function reject(id, comment) {
  return request.post(`/approvals/${id}/reject`, { comment })
}

export function withdraw(id) {
  return request.post(`/approvals/${id}/withdraw`)
}
