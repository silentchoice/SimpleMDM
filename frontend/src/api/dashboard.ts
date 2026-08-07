import { http } from './http'

export interface DashboardRecentTask {
  id: number
  taskType: 'METADATA' | 'RECORD' | string
  entityKind: string
  entityId: number
  status: string
  submittedBy: number
  submittedAt: string
}

export interface DashboardSummary {
  formalCount: number
  myDraftCount: number
  pendingApprovalCount: number
  activatedThisMonth: number
  recentTasks: DashboardRecentTask[]
}

export function getDashboardSummary(): Promise<DashboardSummary> {
  return http.get<DashboardSummary>('/dashboard/summary')
}
