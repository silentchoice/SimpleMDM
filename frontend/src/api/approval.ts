import { http } from './http'

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type ApprovalTaskType = 'METADATA' | 'RECORD'
export type MetadataEntityKind = 'MASTER_FIELDS' | 'SUB_TYPES' | 'SUB_FIELDS' | 'RECORD'

export interface ApprovalTask {
  id: number
  taskType: ApprovalTaskType
  entityKind: MetadataEntityKind
  entityId: number
  status: ApprovalStatus
  beforeSnapshot: string | null
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

function taskTypeQuery(taskType?: ApprovalTaskType): string {
  return taskType ? `&taskType=${encodeURIComponent(taskType)}` : ''
}

function normalizedTaskType(taskType: ApprovalTaskType = 'METADATA'): ApprovalTaskType {
  return taskType
}

function approvalActionBase(taskType: ApprovalTaskType): '/metadata-approval' | '/record-approval' {
  return taskType === 'RECORD' ? '/record-approval' : '/metadata-approval'
}

export function listApprovalTasks(status: ApprovalStatus = 'PENDING', taskType: ApprovalTaskType = 'METADATA'): Promise<ApprovalTask[]> {
  return http.get<ApprovalTask[]>(`/metadata-approval?status=${encodeURIComponent(status)}${taskTypeQuery(taskType === 'METADATA' ? undefined : taskType)}`)
}

export function getApprovalTask(taskId: number, taskType: ApprovalTaskType = 'METADATA'): Promise<ApprovalTask> {
  return http.get<ApprovalTask>(`/metadata-approval/${taskId}${taskType === 'METADATA' ? '' : `?taskType=${encodeURIComponent(taskType)}`}`)
}

export function approveApprovalTask(taskId: number, taskType: ApprovalTaskType = 'METADATA', comment?: string): Promise<void> {
  return http.post<void>(`${approvalActionBase(normalizedTaskType(taskType))}/${taskId}/approve`, { comment: comment ?? null })
}

export function rejectApprovalTask(taskId: number, taskType: ApprovalTaskType = 'METADATA', reason: string): Promise<void> {
  if (!reason.trim()) return Promise.reject({ message: 'Rejection reason is required' })
  return http.post<void>(`${approvalActionBase(normalizedTaskType(taskType))}/${taskId}/reject`, { reason })
}
