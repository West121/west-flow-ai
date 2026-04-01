import type {
  ApprovalSheetPageResponse,
  WorkbenchDashboardSummary,
  WorkbenchTaskActionAvailability,
  WorkbenchTaskDetail,
  WorkbenchTaskPageResponse,
} from '@westflow/shared-types'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from '@/lib/api/client'

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

function toPaginationRequest(state: ListQuerySearch) {
  return {
    page: state.page,
    pageSize: state.pageSize,
    keyword: state.keyword,
    filters: state.filters ?? [],
    sorts: state.sorts ?? [],
    groups: state.groups ?? [],
  }
}

function resolveWorkbenchRuntimePath(...segments: Array<string | number | undefined>) {
  const normalized = segments
    .filter((value) => value !== undefined && `${value}` !== '')
    .map((value) => `${value}`.replace(/^\/+|\/+$/g, ''))
    .filter(Boolean)
  return `/process-runtime/${normalized.join('/')}`
}

export async function getWorkbenchDashboardSummary() {
  const response = await apiClient.get<ApiSuccessResponse<WorkbenchDashboardSummary>>(
    resolveWorkbenchRuntimePath('dashboard', 'summary')
  )
  return unwrapResponse(response)
}

export async function listWorkbenchTasks(search: ListQuerySearch) {
  const response = await apiClient.post<ApiSuccessResponse<WorkbenchTaskPageResponse>>(
    resolveWorkbenchRuntimePath('tasks', 'page'),
    toPaginationRequest(search)
  )
  return unwrapResponse(response)
}

export async function listApprovalSheets(
  search: ListQuerySearch & { view: ApprovalSheetListView; businessTypes?: string[] }
) {
  const response = await apiClient.post<ApiSuccessResponse<ApprovalSheetPageResponse>>(
    resolveWorkbenchRuntimePath('approval-sheets', 'page'),
    {
      ...toPaginationRequest(search),
      view: search.view,
      businessTypes: search.businessTypes ?? [],
    }
  )
  return unwrapResponse(response)
}

export async function getWorkbenchTaskDetail(taskId: string) {
  const response = await apiClient.get<ApiSuccessResponse<WorkbenchTaskDetail>>(
    resolveWorkbenchRuntimePath('tasks', taskId)
  )
  return unwrapResponse(response)
}

export async function getWorkbenchTaskActions(taskId: string) {
  const response = await apiClient.get<ApiSuccessResponse<WorkbenchTaskActionAvailability>>(
    resolveWorkbenchRuntimePath('tasks', taskId, 'actions')
  )
  return unwrapResponse(response)
}

export async function claimWorkbenchTask(taskId: string) {
  const response = await apiClient.post<ApiSuccessResponse<ClaimWorkbenchTaskResponse>>(
    resolveWorkbenchRuntimePath('tasks', taskId, 'claim'),
    {}
  )
  return unwrapResponse(response)
}

export async function completeWorkbenchTask(
  taskId: string,
  payload: CompleteWorkbenchTaskPayload
) {
  const response = await apiClient.post<ApiSuccessResponse<CompleteWorkbenchTaskResponse>>(
    resolveWorkbenchRuntimePath('tasks', taskId, 'complete'),
    payload
  )
  return unwrapResponse(response)
}

export async function rejectWorkbenchTask(
  taskId: string,
  payload: RejectWorkbenchTaskPayload
) {
  const response = await apiClient.post<ApiSuccessResponse<CompleteWorkbenchTaskResponse>>(
    resolveWorkbenchRuntimePath('tasks', taskId, 'reject'),
    payload
  )
  return unwrapResponse(response)
}
