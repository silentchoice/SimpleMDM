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

export function listMasterTypes(): Promise<MasterType[]> { return http.get<MasterType[]>('/master-type') }
export function createMasterType(body: MasterTypeInput): Promise<MasterType> { return http.post<MasterType>('/master-type', body) }
export function assignDepartment(masterTypeId: number, departmentId: number): Promise<void> {
  return http.put<void>(`/master-type/${masterTypeId}/departments/${departmentId}`)
}
