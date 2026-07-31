import request from '../utils/request'
export function getDepartmentTree() { return request.get('/departments/tree') }
