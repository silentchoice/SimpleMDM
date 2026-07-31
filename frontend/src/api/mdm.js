import request from '../utils/request'
const objectPath = code => `/mdm/object-types/${encodeURIComponent(code)}/records`
export function listObjectTypes() { return request.get('/mdm/object-types') }
export function listRecords(code) { return request.get(objectPath(code)) }
export function createRecord(code, payload) { return request.post(objectPath(code), payload) }
export function updateRecord(code, payload) { return request.put(objectPath(code), payload) }
export function listChildRecords(recordId, code) { return request.get(`/mdm/records/${recordId}/children/${encodeURIComponent(code)}`) }
export function createChildRecord(recordId, code, payload) { return request.post(`/mdm/records/${recordId}/children/${encodeURIComponent(code)}`, payload) }
export function updateChildRecord(recordId, code, payload) { return request.put(`/mdm/records/${recordId}/children/${encodeURIComponent(code)}`, payload) }
