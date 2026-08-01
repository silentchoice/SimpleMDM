import request from '../utils/request'
const objectPath = code => `/mdm/object-types/${encodeURIComponent(code)}/records`
export function listObjectTypes(includeInactive = false) { return request.get('/mdm/object-types', { params: { include_inactive: includeInactive } }) }
export function listRecords(code) { return request.get(objectPath(code)) }
export function getRecord(code, id) { return request.get(`${objectPath(code)}/${id}`) }
export function createRecord(code, payload) { return request.post(objectPath(code), payload) }
export function updateRecord(code, payload) { return request.put(objectPath(code), payload) }
export function listChildRecords(recordId, code) { return request.get(`/mdm/records/${recordId}/children/${encodeURIComponent(code)}`) }
export function createChildRecord(recordId, code, payload) { return request.post(`/mdm/records/${recordId}/children/${encodeURIComponent(code)}`, payload) }
export function updateChildRecord(recordId, code, payload) { return request.put(`/mdm/records/${recordId}/children/${encodeURIComponent(code)}`, payload) }
const metadataPath = code => `/mdm/object-types/${encodeURIComponent(code)}`
export function updateObjectType(code, payload) { return request.patch(metadataPath(code), payload) }
export function deactivateObjectType(code) { return request.post(`${metadataPath(code)}/deactivate`) }
export function reactivateObjectType(code) { return request.post(`${metadataPath(code)}/reactivate`) }
export function createMasterField(code, payload) { return request.post(`${metadataPath(code)}/fields`, payload) }
export function updateMasterField(code, id, payload) { return request.patch(`${metadataPath(code)}/fields/${id}`, payload) }
export function deactivateMasterField(code, id) { return request.post(`${metadataPath(code)}/fields/${id}/deactivate`) }
export function reactivateMasterField(code, id) { return request.post(`${metadataPath(code)}/fields/${id}/reactivate`) }
export function createChildType(code, payload) { return request.post(`${metadataPath(code)}/child-types`, payload) }
export function updateChildType(code, id, payload) { return request.patch(`${metadataPath(code)}/child-types/${id}`, payload) }
export function deactivateChildType(code, id) { return request.post(`${metadataPath(code)}/child-types/${id}/deactivate`) }
export function reactivateChildType(code, id) { return request.post(`${metadataPath(code)}/child-types/${id}/reactivate`) }
export function createChildField(code, childTypeId, payload) { return request.post(`${metadataPath(code)}/child-types/${childTypeId}/fields`, payload) }
export function updateChildField(code, childTypeId, id, payload) { return request.patch(`${metadataPath(code)}/child-types/${childTypeId}/fields/${id}`, payload) }
export function deactivateChildField(code, childTypeId, id) { return request.post(`${metadataPath(code)}/child-types/${childTypeId}/fields/${id}/deactivate`) }
export function reactivateChildField(code, childTypeId, id) { return request.post(`${metadataPath(code)}/child-types/${childTypeId}/fields/${id}/reactivate`) }
