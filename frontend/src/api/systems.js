import request from '../utils/request'
export function listSystems() { return request.get('/systems') }
