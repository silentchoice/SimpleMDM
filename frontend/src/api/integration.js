import request from '../utils/request'
export const listEndpoints=()=>request.get('/integration/endpoints')
export const createEndpoint=data=>request.post('/integration/endpoints',data)
export const listSubscriptions=()=>request.get('/integration/subscriptions')
export const createSubscription=data=>request.post('/integration/subscriptions',data)
export const listPushLogs=()=>request.get('/integration/logs')
