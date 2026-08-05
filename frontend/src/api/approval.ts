import { http } from './http'

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type MetadataEntityKind = 'MASTER_FIELDS' | 'SUB_TYPES' | 'SUB_FIELDS'

export interface ApprovalTask {
  id: number
  entityKind: MetadataEntityKind
  entityId: number
  status: ApprovalStatus
  beforeSnapshot: string
  afterSnapshot: string
  submittedBy: number
  reviewedBy: number | null
  reviewComment: string | null
  submittedAt: string
  reviewedAt: string | null
}

export interface SnapshotEnvelope {
  schemaVersion: number
  departmentId: number
  templateId: number
  entityKind: MetadataEntityKind
  baseFingerprint: string
  orderedDefinitions: unknown[]
}

export function listApprovalTasks(status: ApprovalStatus = 'PENDING'): Promise<ApprovalTask[]> {
  return http.get<ApprovalTask[]>(`/metadata-approval?status=${encodeURIComponent(status)}`)
}

export function getApprovalTask(taskId: number): Promise<ApprovalTask> {
  return http.get<ApprovalTask>(`/metadata-approval/${taskId}`)
}

export function approveApprovalTask(taskId: number, comment?: string): Promise<void> {
  return http.post<void>(`/metadata-approval/${taskId}/approve`, { comment: comment ?? null })
}

export function rejectApprovalTask(taskId: number, reason: string): Promise<void> {
  if (!reason.trim()) return Promise.reject({ message: 'Rejection reason is required' })
  return http.post<void>(`/metadata-approval/${taskId}/reject`, { reason })
}
