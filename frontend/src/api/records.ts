import { http } from './http'

export type RecordAction = 'CREATE' | 'UPDATE' | 'DELETE'
export type FormalRecordStatus = 'ACTIVE' | 'DELETED'
export type DraftStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED'

export interface ChildRows<T> {
  subTypeId: number
  rows: T[]
}

export interface ChildRowDetail {
  id: number
  rowOrder: number
  values: Record<string, unknown>
}

export interface ChildRowDraft {
  recordId: number | null
  rowOrder: number
  values: Record<string, unknown>
}

export interface RecordSummary {
  id: number
  masterTypeId: number
  departmentId: number
  recordCode: string
  masterValues: Record<string, unknown>
  children: ChildRows<ChildRowDetail>[]
  version: number
  status: FormalRecordStatus | string
}

export type RecordDetail = RecordSummary
export type HistorySnapshot = RecordDetail

export interface RecordDraft {
  id: number
  recordId: number | null
  masterTypeId: number
  departmentId: number
  recordCode: string
  action: RecordAction
  baseVersion: number
  masterValues: Record<string, unknown>
  children: ChildRows<ChildRowDraft>[]
  status: DraftStatus | string
  createdBy: number
  deleteReason: string | null
}

export interface RecordDraftCommand {
  recordId: number | null
  masterTypeId: number
  baseVersion: number
  action: RecordAction
  masterValues: Record<string, unknown>
  children: ChildRows<ChildRowDraft>[]
  deleteReason: string | null
}

export interface SubmissionResponse {
  approvalTaskId: number
}

export interface EditLock {
  recordId: number
  departmentId: number
  userId: number
  displayName: string
  token: string
  expiresAt: string
}

export interface Paged<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RecordListQuery {
  masterTypeId?: number
  recordCode?: string
  keyword?: string
  status?: string
  updatedFrom?: string
  updatedTo?: string
  includeDeleted?: boolean
  page?: number
  size?: number
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}

function queryString(query: RecordListQuery): string {
  const params = new URLSearchParams()
  if (query.masterTypeId !== undefined) params.set('masterTypeId', String(query.masterTypeId))
  if (query.recordCode !== undefined) params.set('recordCode', query.recordCode)
  if (query.keyword !== undefined) params.set('keyword', query.keyword)
  if (query.status !== undefined) params.set('status', query.status)
  if (query.updatedFrom !== undefined) params.set('updatedFrom', query.updatedFrom)
  if (query.updatedTo !== undefined) params.set('updatedTo', query.updatedTo)
  if (query.includeDeleted !== undefined) params.set('includeDeleted', String(query.includeDeleted))
  if (query.page !== undefined) params.set('page', String(query.page))
  if (query.size !== undefined) params.set('size', String(query.size))
  if (query.sortBy !== undefined) params.set('sortBy', query.sortBy)
  if (query.sortDirection !== undefined) params.set('sortDirection', query.sortDirection)
  const encoded = params.toString()
  return encoded ? `?${encoded}` : ''
}

export function listRecords(query: RecordListQuery = {}): Promise<Paged<RecordSummary>> {
  return http.get<Paged<RecordSummary>>(`/master-record${queryString(query)}`)
}

export function getRecord(recordId: number): Promise<RecordDetail> {
  return http.get<RecordDetail>(`/master-record/${recordId}`)
}

export function listRecordHistory(recordId: number): Promise<HistorySnapshot[]> {
  return http.get<HistorySnapshot[]>(`/master-record/${recordId}/history`)
}

export function createRecordDraft(body: RecordDraftCommand): Promise<RecordDraft> {
  return http.post<RecordDraft>('/master-record-draft', body)
}

export function updateRecordDraft(draftId: number, body: RecordDraftCommand): Promise<RecordDraft> {
  return http.put<RecordDraft>(`/master-record-draft/${draftId}`, body)
}

export function getRecordDraft(draftId: number): Promise<RecordDraft> {
  return http.get<RecordDraft>(`/master-record-draft/${draftId}`)
}

export function listRecordDrafts(): Promise<RecordDraft[]> {
  return http.get<RecordDraft[]>('/master-record-draft')
}

export function copyRecordDraft(draftId: number): Promise<RecordDraft> {
  return http.post<RecordDraft>(`/master-record-draft/${draftId}/copy`)
}

export function submitRecordDraft(draftId: number, token: string | null): Promise<SubmissionResponse> {
  return http.post<SubmissionResponse>(`/master-record-draft/${draftId}/submit`, { token })
}

export function requestRecordDeletion(recordId: number, reason: string): Promise<RecordDraft> {
  return http.post<RecordDraft>(`/master-record/${recordId}/delete-request`, { reason })
}

export function acquireRecordLock(recordId: number): Promise<EditLock> {
  return http.post<EditLock>(`/master-record/${recordId}/lock`)
}

export function renewRecordLock(recordId: number, token: string): Promise<EditLock> {
  return http.put<EditLock>(`/master-record/${recordId}/lock`, { token })
}

export function releaseRecordLock(recordId: number, token: string): Promise<void> {
  return http.delete<void>(`/master-record/${recordId}/lock`, { token })
}
