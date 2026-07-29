import request from '../utils/request'

export function listPushLogs(params) {
  return request.get('/push-logs', { params })
}

export function retryPush(id) {
  return request.post(`/push-logs/${id}/retry`)
}
