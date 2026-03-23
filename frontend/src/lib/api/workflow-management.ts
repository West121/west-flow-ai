import { type ListQuerySearch, toPaginationRequest } from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type WorkflowVersionRecord = {
  processDefinitionId: string
  processKey: string
  processName: string
  category: string | null
  version: number
  status: string
  latestVersion: boolean
  deploymentId: string | null
  flowableDefinitionId: string | null
  publisherUserId: string | null
  publishedAt: string | null
}

export type WorkflowVersionDetail = WorkflowVersionRecord & {
  createdAt: string | null
  updatedAt: string | null
  bpmnXml: string
}

export type WorkflowPublishRecord = {
  processDefinitionId: string
  processKey: string
  processName: string
  version: number
  category: string | null
  deploymentId: string | null
  flowableDefinitionId: string | null
  publisherUserId: string | null
  publishedAt: string | null
}

export type WorkflowPublishRecordDetail = WorkflowPublishRecord & {
  createdAt: string | null
  bpmnXml: string
}

export type WorkflowInstanceRecord = {
  processInstanceId: string
  processDefinitionId: string | null
  flowableDefinitionId: string | null
  processKey: string | null
  processName: string | null
  businessType: string | null
  businessId: string | null
  startUserId: string | null
  status: string
  suspended: boolean
  currentTaskNames: string[]
  startedAt: string | null
  finishedAt: string | null
}

export type WorkflowProcessLink = {
  linkId: string
  rootInstanceId: string
  parentInstanceId: string
  childInstanceId: string
  parentNodeId: string
  calledProcessKey: string
  calledDefinitionId: string
  linkType: string
  status: string
  terminatePolicy: string | null
  childFinishPolicy: string | null
  createdAt: string | null
  finishedAt: string | null
}

export type WorkflowInstanceDetail = WorkflowInstanceRecord & {
  variables: Record<string, unknown>
  processLinks: WorkflowProcessLink[]
}

export type WorkflowOperationLogRecord = {
  logId: string
  processInstanceId: string | null
  businessType: string | null
  businessId: string | null
  actionType: string
  actionName: string
  actionCategory: string | null
  operatorUserId: string | null
  targetUserId: string | null
  createdAt: string
}

export type WorkflowOperationLogDetail = WorkflowOperationLogRecord & {
  processDefinitionId: string | null
  flowableDefinitionId: string | null
  taskId: string | null
  nodeId: string | null
  sourceTaskId: string | null
  targetTaskId: string | null
  commentText: string | null
  details: Record<string, unknown>
}

export type WorkflowBindingRecord = {
  bindingId: string
  businessType: string
  sceneCode: string
  processKey: string
  processDefinitionId: string
  processName: string
  enabled: boolean
  priority: number
  updatedAt: string
}

export type WorkflowBindingDetail = WorkflowBindingRecord & {
  createdAt: string
}

export type WorkflowBindingFormOptions = {
  businessTypes: Array<{ value: string; label: string }>
  processDefinitions: Array<{
    processDefinitionId: string
    processKey: string
    processName: string
    version: number
  }>
}

export type SaveWorkflowBindingPayload = {
  businessType: string
  sceneCode: string
  processKey: string
  processDefinitionId: string
  enabled: boolean
  priority: number
}

export type ApprovalOpinionConfigRecord = {
  configId: string
  configCode: string
  configName: string
  status: string
  quickOpinionCount: number
  updatedAt: string
}

export type ApprovalOpinionConfigDetail = {
  configId: string
  configCode: string
  configName: string
  status: string
  quickOpinions: string[]
  toolbarActions: string[]
  buttonStrategies: Array<{ actionType: string; requireOpinion: boolean }>
  remark: string | null
  createdAt: string
  updatedAt: string
}

export type ApprovalOpinionConfigFormOptions = {
  actionTypes: Array<{ value: string; label: string }>
  toolbarActions: Array<{ value: string; label: string }>
}

export type SaveApprovalOpinionConfigPayload = {
  configCode: string
  configName: string
  enabled: boolean
  quickOpinions: string[]
  toolbarActions: string[]
  buttonStrategies: Array<{ actionType: string; requireOpinion: boolean }>
  remark?: string
}

type PageResponse<T> = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: T[]
  groups: Array<{ field: string; value: string }>
}

export async function listWorkflowVersions(search: ListQuerySearch) {
  const response = await apiClient.post<ApiSuccessResponse<PageResponse<WorkflowVersionRecord>>>(
    '/workflow-management/versions/page',
    toPaginationRequest(search)
  )
  return unwrapResponse(response)
}

export async function getWorkflowVersionDetail(processDefinitionId: string) {
  const response = await apiClient.get<ApiSuccessResponse<WorkflowVersionDetail>>(
    `/workflow-management/versions/${processDefinitionId}`
  )
  return unwrapResponse(response)
}

export async function listWorkflowPublishRecords(search: ListQuerySearch) {
  const response = await apiClient.post<ApiSuccessResponse<PageResponse<WorkflowPublishRecord>>>(
    '/workflow-management/publish-records/page',
    toPaginationRequest(search)
  )
  return unwrapResponse(response)
}

export async function getWorkflowPublishRecordDetail(processDefinitionId: string) {
  const response = await apiClient.get<ApiSuccessResponse<WorkflowPublishRecordDetail>>(
    `/workflow-management/publish-records/${processDefinitionId}`
  )
  return unwrapResponse(response)
}

export async function listWorkflowInstances(search: ListQuerySearch) {
  const response = await apiClient.post<ApiSuccessResponse<PageResponse<WorkflowInstanceRecord>>>(
    '/workflow-management/instances/page',
    toPaginationRequest(search)
  )
  return unwrapResponse(response)
}

export async function getWorkflowInstanceDetail(instanceId: string) {
  const response = await apiClient.get<ApiSuccessResponse<WorkflowInstanceDetail>>(
    `/workflow-management/instances/${instanceId}`
  )
  return unwrapResponse(response)
}

export async function listWorkflowOperationLogs(search: ListQuerySearch) {
  const response = await apiClient.post<ApiSuccessResponse<PageResponse<WorkflowOperationLogRecord>>>(
    '/workflow-management/operation-logs/page',
    toPaginationRequest(search)
  )
  return unwrapResponse(response)
}

export async function getWorkflowOperationLogDetail(logId: string) {
  const response = await apiClient.get<ApiSuccessResponse<WorkflowOperationLogDetail>>(
    `/workflow-management/operation-logs/${logId}`
  )
  return unwrapResponse(response)
}

export async function listWorkflowBindings(search: ListQuerySearch) {
  const response = await apiClient.post<ApiSuccessResponse<PageResponse<WorkflowBindingRecord>>>(
    '/workflow-management/bindings/page',
    toPaginationRequest(search)
  )
  return unwrapResponse(response)
}

export async function getWorkflowBindingDetail(bindingId: string) {
  const response = await apiClient.get<ApiSuccessResponse<WorkflowBindingDetail>>(
    `/workflow-management/bindings/${bindingId}`
  )
  return unwrapResponse(response)
}

export async function getWorkflowBindingFormOptions() {
  const response = await apiClient.get<ApiSuccessResponse<WorkflowBindingFormOptions>>(
    '/workflow-management/bindings/options'
  )
  return unwrapResponse(response)
}

export async function createWorkflowBinding(payload: SaveWorkflowBindingPayload) {
  const response = await apiClient.post<ApiSuccessResponse<{ bindingId: string }>>(
    '/workflow-management/bindings',
    payload
  )
  return unwrapResponse(response)
}

export async function updateWorkflowBinding(bindingId: string, payload: SaveWorkflowBindingPayload) {
  const response = await apiClient.put<ApiSuccessResponse<{ bindingId: string }>>(
    `/workflow-management/bindings/${bindingId}`,
    payload
  )
  return unwrapResponse(response)
}

export async function listApprovalOpinionConfigs(search: ListQuerySearch) {
  const response = await apiClient.post<ApiSuccessResponse<PageResponse<ApprovalOpinionConfigRecord>>>(
    '/workflow-management/opinion-configs/page',
    toPaginationRequest(search)
  )
  return unwrapResponse(response)
}

export async function getApprovalOpinionConfigDetail(configId: string) {
  const response = await apiClient.get<ApiSuccessResponse<ApprovalOpinionConfigDetail>>(
    `/workflow-management/opinion-configs/${configId}`
  )
  return unwrapResponse(response)
}

export async function getApprovalOpinionConfigFormOptions() {
  const response = await apiClient.get<ApiSuccessResponse<ApprovalOpinionConfigFormOptions>>(
    '/workflow-management/opinion-configs/options'
  )
  return unwrapResponse(response)
}

export async function createApprovalOpinionConfig(payload: SaveApprovalOpinionConfigPayload) {
  const response = await apiClient.post<ApiSuccessResponse<{ configId: string }>>(
    '/workflow-management/opinion-configs',
    payload
  )
  return unwrapResponse(response)
}

export async function updateApprovalOpinionConfig(
  configId: string,
  payload: SaveApprovalOpinionConfigPayload
) {
  const response = await apiClient.put<ApiSuccessResponse<{ configId: string }>>(
    `/workflow-management/opinion-configs/${configId}`,
    payload
  )
  return unwrapResponse(response)
}
