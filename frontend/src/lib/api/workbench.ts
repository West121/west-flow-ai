import { apiClient, unwrapResponse } from '@/lib/api/client'
import { type ListQuerySearch, toPaginationRequest } from '@/features/shared/table/query-contract'
import { type WorkflowFieldBinding } from '@/features/workflow/designer/types'

export type WorkbenchTaskListItem = {
  taskId: string
  instanceId: string
  processDefinitionId: string
  processKey: string
  processName: string
  businessKey: string | null
  applicantUserId: string
  nodeId: string
  nodeName: string
  status: 'PENDING_CLAIM' | 'PENDING' | 'COMPLETED' | 'TRANSFERRED' | 'RETURNED'
  assignmentMode: string | null
  candidateUserIds: string[]
  assigneeUserId: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type WorkbenchTaskDetail = WorkbenchTaskListItem & {
  action: string | null
  operatorUserId: string | null
  comment: string | null
  instanceStatus: string
  formData: Record<string, unknown>
  processFormKey: string
  processFormVersion: string
  effectiveFormKey: string
  effectiveFormVersion: string
  nodeFormKey: string | null
  nodeFormVersion: string | null
  fieldBindings: WorkflowFieldBinding[]
  taskFormData: Record<string, unknown> | null
  activeTaskIds: string[]
}

export type WorkbenchTaskActionAvailability = {
  canClaim: boolean
  canApprove: boolean
  canReject: boolean
  canTransfer: boolean
  canReturn: boolean
}

export type ClaimWorkbenchTaskPayload = {
  comment?: string | null
}

export type ClaimWorkbenchTaskResponse = {
  taskId: string
  status: 'PENDING'
  assigneeUserId: string
}

export type TransferWorkbenchTaskPayload = {
  targetUserId: string
  comment?: string | null
}

export type ReturnWorkbenchTaskPayload = {
  targetStrategy: 'PREVIOUS_USER_TASK'
  comment?: string | null
}

export type StartWorkbenchProcessPayload = {
  processKey: string
  businessKey?: string | null
  formData?: Record<string, unknown> | null
}

export type StartWorkbenchProcessResponse = {
  processDefinitionId: string
  instanceId: string
  status: string
  activeTasks: Array<{
    taskId: string
    nodeId: string
    nodeName: string
    status: string
    assignmentMode: string | null
    candidateUserIds: string[]
    assigneeUserId?: string | null
  }>
}

export type CompleteWorkbenchTaskPayload = {
  action: 'APPROVE' | 'REJECT' | string
  operatorUserId?: string | null
  comment?: string | null
  taskFormData?: Record<string, unknown> | null
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
    assigneeUserId?: string | null
  }>
}

export type WorkbenchTaskPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: WorkbenchTaskListItem[]
  groups: Array<{ field: string; value: string }>
}

export async function listWorkbenchTasks(
  search: ListQuerySearch
): Promise<WorkbenchTaskPageResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: WorkbenchTaskPageResponse
    requestId: string
  }>('/process-runtime/demo/tasks/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getWorkbenchTaskDetail(
  taskId: string
): Promise<WorkbenchTaskDetail> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: WorkbenchTaskDetail
    requestId: string
  }>(`/process-runtime/demo/tasks/${taskId}`)

  return unwrapResponse(response)
}

export async function getWorkbenchTaskActions(
  taskId: string
): Promise<WorkbenchTaskActionAvailability> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: WorkbenchTaskActionAvailability
    requestId: string
  }>(`/process-runtime/demo/tasks/${taskId}/actions`)

  return unwrapResponse(response)
}

export async function claimWorkbenchTask(
  taskId: string,
  payload: ClaimWorkbenchTaskPayload
): Promise<ClaimWorkbenchTaskResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: ClaimWorkbenchTaskResponse
    requestId: string
  }>(`/process-runtime/demo/tasks/${taskId}/claim`, payload)

  return unwrapResponse(response)
}

export async function transferWorkbenchTask(
  taskId: string,
  payload: TransferWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: CompleteWorkbenchTaskResponse
    requestId: string
  }>(`/process-runtime/demo/tasks/${taskId}/transfer`, payload)

  return unwrapResponse(response)
}

export async function returnWorkbenchTask(
  taskId: string,
  payload: ReturnWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: CompleteWorkbenchTaskResponse
    requestId: string
  }>(`/process-runtime/demo/tasks/${taskId}/return`, payload)

  return unwrapResponse(response)
}

export async function startWorkbenchProcess(
  payload: StartWorkbenchProcessPayload
): Promise<StartWorkbenchProcessResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: StartWorkbenchProcessResponse
    requestId: string
  }>('/process-runtime/demo/start', payload)

  return unwrapResponse(response)
}

export async function completeWorkbenchTask(
  taskId: string,
  payload: CompleteWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: CompleteWorkbenchTaskResponse
    requestId: string
  }>(`/process-runtime/demo/tasks/${taskId}/complete`, payload)

  return unwrapResponse(response)
}
