import {
  ApprovalSheetPageResponse,
  WorkbenchDashboardSummary,
  WorkbenchTaskActionAvailability,
  WorkbenchTaskDetail,
  WorkbenchTaskPageResponse,
} from '@westflow/shared-types'
import { requestApi } from './client'

export type ListQuerySearch = {
  page: number
  pageSize: number
  keyword: string
  filters: Array<{
    field: string
    operator:
      | 'eq'
      | 'ne'
      | 'in'
      | 'not_in'
      | 'gt'
      | 'gte'
      | 'lt'
      | 'lte'
      | 'between'
      | 'like'
      | 'prefix_like'
      | 'suffix_like'
      | 'is_null'
      | 'is_not_null'
    value: unknown
  }>
  sorts: Array<{ field: string; direction: 'asc' | 'desc' }>
  groups: Array<{ field: string }>
}

export type ApprovalSheetListView = 'TODO' | 'DONE' | 'INITIATED' | 'CC'

export type CompleteWorkbenchTaskPayload = {
  action: 'APPROVE' | 'REJECT' | string
  comment?: string | null
  taskFormData?: Record<string, unknown> | null
}

export type RejectWorkbenchTaskPayload = {
  targetStrategy: 'PREVIOUS_USER_TASK' | 'INITIATOR' | 'ANY_USER_TASK'
  targetTaskId?: string | null
  targetNodeId?: string | null
  reapproveStrategy: 'CONTINUE' | 'RETURN_TO_REJECTED_NODE'
  comment?: string | null
}

export type ClaimWorkbenchTaskResponse = {
  taskId: string
  status: 'PENDING'
  assigneeUserId: string
}

export type WorkbenchReviewTicketResponse = {
  ticket: string
  expiresAt: string
}

export type CompleteWorkbenchTaskResponse = {
  instanceId: string
  completedTaskId: string
  status: string
  nextTasks: Array<{
    taskId: string
    nodeId: string
    nodeName: string
    status: string
    assignmentMode: string | null
    candidateUserIds: string[]
    candidateGroupIds?: string[]
    assigneeUserId?: string | null
  }>
}

const baseQuery: ListQuerySearch = {
  page: 1,
  pageSize: 10,
  keyword: '',
  filters: [],
  sorts: [],
  groups: [],
}

function resolveWorkbenchRuntimePath(...segments: Array<string | number | undefined>) {
  const normalized = segments
    .filter((value) => value !== undefined && `${value}` !== '')
    .map((value) => `${value}`.replace(/^\/+|\/+$/g, ''))
    .filter(Boolean)
  return `/process-runtime/${normalized.join('/')}`
}

export function getWorkbenchDashboardSummary() {
  return requestApi<WorkbenchDashboardSummary>({
    path: resolveWorkbenchRuntimePath('dashboard', 'summary'),
  })
}

export function listWorkbenchTasks(search: Partial<ListQuerySearch> = {}) {
  return requestApi<WorkbenchTaskPageResponse>({
    path: resolveWorkbenchRuntimePath('tasks', 'page'),
    method: 'POST',
    data: { ...baseQuery, ...search },
  })
}

export function listApprovalSheets(input: {
  view: ApprovalSheetListView
  businessTypes?: string[]
} & Partial<ListQuerySearch>) {
  return requestApi<ApprovalSheetPageResponse>({
    path: resolveWorkbenchRuntimePath('approval-sheets', 'page'),
    method: 'POST',
    data: {
      ...baseQuery,
      ...input,
      businessTypes: input.businessTypes ?? [],
    },
  })
}

export function getWorkbenchTaskDetail(taskId: string) {
  return requestApi<WorkbenchTaskDetail>({
    path: resolveWorkbenchRuntimePath('tasks', taskId),
  })
}

export function getWorkbenchTaskActions(taskId: string) {
  return requestApi<WorkbenchTaskActionAvailability>({
    path: resolveWorkbenchRuntimePath('tasks', taskId, 'actions'),
  })
}

export function createWorkbenchReviewTicket(taskId: string) {
  return requestApi<WorkbenchReviewTicketResponse>({
    path: resolveWorkbenchRuntimePath('tasks', taskId, 'review-ticket'),
    method: 'POST',
    data: {},
  })
}

export function claimWorkbenchTask(taskId: string) {
  return requestApi<ClaimWorkbenchTaskResponse>({
    path: resolveWorkbenchRuntimePath('tasks', taskId, 'claim'),
    method: 'POST',
    data: {},
  })
}

export function completeWorkbenchTask(taskId: string, payload: CompleteWorkbenchTaskPayload) {
  return requestApi<CompleteWorkbenchTaskResponse>({
    path: resolveWorkbenchRuntimePath('tasks', taskId, 'complete'),
    method: 'POST',
    data: payload,
  })
}

export function rejectWorkbenchTask(taskId: string, payload: RejectWorkbenchTaskPayload) {
  return requestApi<CompleteWorkbenchTaskResponse>({
    path: resolveWorkbenchRuntimePath('tasks', taskId, 'reject'),
    method: 'POST',
    data: payload,
  })
}
