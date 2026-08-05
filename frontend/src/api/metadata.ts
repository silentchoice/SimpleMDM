import { http } from './http'

export type MasterTypeStatus = 'ACTIVE' | 'DISABLED'

export interface MasterType {
  id: number
  code: string
  name: string
  status: MasterTypeStatus
}

export interface MasterTypeInput {
  code: string
  name: string
}

export type FieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'DATETIME' | 'SELECT' | 'RADIO' | 'MULTISELECT' | 'SWITCH'
export type MetadataStatus = 'ACTIVE' | 'DISABLED'

export interface FieldDefinition {
  id: number
  ownerTypeId: number
  code: string
  displayName: string
  fieldType: FieldType
  required: boolean
  options: string[]
  shared: boolean
  sortOrder: number
  status: MetadataStatus
}

export interface FieldSubmission {
  code: string
  displayName: string
  fieldType: FieldType
  required: boolean
  options: string[]
  shared: boolean
  sortOrder: number
}

export interface SubType {
  id: number
  masterTypeId: number
  code: string
  name: string
  status: MetadataStatus
}

export interface SubTypeSubmission {
  code: string
  name: string
}

export interface ApprovalSubmission {
  approvalTaskId: number
}

export function listMasterTypes(): Promise<MasterType[]> { return http.get<MasterType[]>('/master-type') }
export function createMasterType(body: MasterTypeInput): Promise<MasterType> { return http.post<MasterType>('/master-type', body) }
export function assignDepartment(masterTypeId: number, departmentId: number): Promise<void> {
  return http.put<void>(`/master-type/${masterTypeId}/departments/${departmentId}`)
}
export function listMasterFields(masterTypeId: number): Promise<FieldDefinition[]> {
  return http.get<FieldDefinition[]>(`/master-field/${masterTypeId}`)
}
export function submitMasterFields(masterTypeId: number, fields: FieldSubmission[]): Promise<ApprovalSubmission> {
  return http.post<ApprovalSubmission>(`/master-field/${masterTypeId}`, fields)
}
export function listSubTypes(masterTypeId: number): Promise<SubType[]> {
  return http.get<SubType[]>(`/sub-type/${masterTypeId}`)
}
export function submitSubTypes(masterTypeId: number, types: SubTypeSubmission[]): Promise<ApprovalSubmission> {
  return http.post<ApprovalSubmission>(`/sub-type/${masterTypeId}`, types)
}
export function listSubFields(subTypeId: number): Promise<FieldDefinition[]> {
  return http.get<FieldDefinition[]>(`/sub-field/${subTypeId}`)
}
export function submitSubFields(subTypeId: number, fields: FieldSubmission[]): Promise<ApprovalSubmission> {
  return http.post<ApprovalSubmission>(`/sub-field/${subTypeId}`, fields)
}
