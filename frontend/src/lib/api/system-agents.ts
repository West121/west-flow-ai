import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type SystemAgentStatus = 'ACTIVE' | 'DISABLED'

export type SystemAgentUserOption = {
  id: string
  displayName: string
  username: string
  departmentName?: string | null
  postName?: string | null
  enabled: boolean
}

export type SystemAgentRecord = {
  agentId: string
  sourceUserId: string
  sourceUserName: string
  targetUserId: string
  targetUserName: string
  description: string | null
  status: SystemAgentStatus
  createdAt: string
  updatedAt: string
}

export type SystemAgentPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: SystemAgentRecord[]
  groups: Array<{
    field: string
    value: string
  }>
}

export type SystemAgentDetail = SystemAgentRecord

export type SystemAgentFormOptions = {
  users: SystemAgentUserOption[]
}

export type SaveSystemAgentPayload = {
  sourceUserId: string
  targetUserId: string
  description?: string | null
  enabled: boolean
}

export type HandoverPreviewPayload = {
  sourceUserId: string
  targetUserId: string
  comment?: string | null
}

export type HandoverPreviewTask = {
  taskId: string
  instanceId: string
  processName: string
  businessTitle: string | null
  billNo: string | null
  currentNodeName: string | null
  currentTaskStatus: string
  assigneeUserId: string | null
  createdAt: string
  canTransfer: boolean
  reason?: string | null
}

export type HandoverPreviewResponse = {
  sourceUserId: string
  targetUserId: string
  transferableCount: number
  tasks: HandoverPreviewTask[]
}

export type HandoverExecuteResponse = {
  sourceUserId: string
  targetUserId: string
  transferredCount: number
  transferredTaskIds: string[]
  tasks: HandoverPreviewTask[]
}

type BasePageResponse<T> = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: T[]
  groups: Array<{
    field: string
    value: string
  }>
}

type BackendAgentRecord = {
  agentId: string
  principalUserId: string
  principalDisplayName: string
  principalUsername: string
  principalDepartmentName?: string | null
  principalPostName?: string | null
  delegateUserId: string
  delegateDisplayName: string
  delegateUsername: string
  delegateDepartmentName?: string | null
  delegatePostName?: string | null
  status: SystemAgentStatus
  remark: string | null
  createdAt: string
  updatedAt: string
}

type BackendAgentPageResponse = BasePageResponse<BackendAgentRecord>

type BackendAgentFormOptions = {
  principalUsers: BackendAgentUserOption[]
  delegateUsers: BackendAgentUserOption[]
  statusOptions: Array<{
    value: SystemAgentStatus
    label: string
  }>
}

type BackendAgentUserOption = {
  userId: string
  displayName: string
  username: string
  departmentName?: string | null
  postName?: string | null
  enabled: boolean
}

type BackendSaveSystemAgentPayload = {
  principalUserId: string
  delegateUserId: string
  status: SystemAgentStatus
  remark?: string | null
}

type BackendHandoverPreviewTask = {
  taskId: string
  instanceId: string
  processName: string
  businessTitle?: string | null
  billNo?: string | null
  currentNodeName?: string | null
  assigneeUserId?: string | null
  createdAt: string
  canTransfer: boolean
  reason?: string | null
}

type BackendHandoverPreviewResponse = {
  sourceUserId: string
  sourceDisplayName: string
  targetUserId: string
  targetDisplayName: string
  previewTaskCount: number
  previewTasks: BackendHandoverPreviewTask[]
}

type BackendHandoverExecuteTask = {
  sourceTaskId: string
  targetTaskId: string
  processName: string
  businessTitle?: string | null
  billNo?: string | null
  currentNodeName?: string | null
  assigneeUserId?: string | null
  executedAt: string
  status: string
  canTransfer: boolean
  reason?: string | null
  instanceId: string
}

type BackendHandoverExecuteResponse = {
  sourceUserId: string
  sourceDisplayName: string
  targetUserId: string
  targetDisplayName: string
  executedTaskCount: number
  executionTasks: BackendHandoverExecuteTask[]
}

function mapAgentRecord(record: BackendAgentRecord): SystemAgentRecord {
  // 前端页面统一使用 source/target 命名，避免把后端 principal/delegate 语义扩散到表单组件。
  return {
    agentId: record.agentId,
    sourceUserId: record.principalUserId,
    sourceUserName: record.principalDisplayName,
    targetUserId: record.delegateUserId,
    targetUserName: record.delegateDisplayName,
    description: record.remark,
    status: record.status,
    createdAt: record.createdAt,
    updatedAt: record.updatedAt,
  }
}

function mapAgentUserOption(option: BackendAgentUserOption): SystemAgentUserOption {
  return {
    id: option.userId,
    displayName: option.displayName,
    username: option.username,
    departmentName: option.departmentName,
    postName: option.postName,
    enabled: option.enabled,
  }
}

function mapHandoverPreviewTask(
  task: BackendHandoverPreviewTask
): HandoverPreviewTask {
  // 预览和执行结果最终都收敛成同一份任务摘要，详情页和结果页可以直接复用。
  return {
    taskId: task.taskId,
    instanceId: task.instanceId,
    processName: task.processName,
    businessTitle: task.businessTitle ?? null,
    billNo: task.billNo ?? null,
    currentNodeName: task.currentNodeName ?? null,
    currentTaskStatus: 'PENDING',
    assigneeUserId: task.assigneeUserId ?? null,
    createdAt: task.createdAt,
    canTransfer: task.canTransfer,
    reason: task.reason ?? null,
  }
}

function mapHandoverExecuteTask(
  task: BackendHandoverExecuteTask
): HandoverPreviewTask {
  return {
    taskId: task.targetTaskId,
    instanceId: task.instanceId,
    processName: task.processName,
    businessTitle: task.businessTitle ?? null,
    billNo: task.billNo ?? null,
    currentNodeName: task.currentNodeName ?? null,
    currentTaskStatus: task.status,
    assigneeUserId: task.assigneeUserId ?? null,
    createdAt: task.executedAt,
    canTransfer: task.canTransfer,
    reason: task.reason ?? null,
  }
}

function toBackendSavePayload(
  payload: SaveSystemAgentPayload
): BackendSaveSystemAgentPayload {
  return {
    principalUserId: payload.sourceUserId,
    delegateUserId: payload.targetUserId,
    status: payload.enabled ? 'ACTIVE' : 'DISABLED',
    remark: payload.description?.trim() || null,
  }
}

export async function listSystemAgents(
  search: ListQuerySearch
): Promise<SystemAgentPageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<BackendAgentPageResponse>
  >('/system/agents/page', toPaginationRequest(search))
  const data = unwrapResponse(response)

  return {
    ...data,
    records: data.records.map(mapAgentRecord),
  }
}

export async function getSystemAgentDetail(
  agentId: string
): Promise<SystemAgentDetail> {
  const response = await apiClient.get<ApiSuccessResponse<BackendAgentRecord>>(
    `/system/agents/${agentId}`
  )

  return mapAgentRecord(unwrapResponse(response))
}

export async function getSystemAgentFormOptions(): Promise<SystemAgentFormOptions> {
  const response = await apiClient.get<
    ApiSuccessResponse<BackendAgentFormOptions>
  >('/system/agents/options')
  const data = unwrapResponse(response)
  const users = [...data.principalUsers, ...data.delegateUsers]
    .map(mapAgentUserOption)
    .reduce<SystemAgentUserOption[]>((accumulator, item) => {
      if (!accumulator.some((candidate) => candidate.id === item.id)) {
        accumulator.push(item)
      }
      return accumulator
    }, [])

  return { users }
}

export async function createSystemAgent(
  payload: SaveSystemAgentPayload
): Promise<{ agentId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ agentId: string }>>(
    '/system/agents',
    toBackendSavePayload(payload)
  )

  return unwrapResponse(response)
}

export async function updateSystemAgent(
  agentId: string,
  payload: SaveSystemAgentPayload
): Promise<{ agentId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ agentId: string }>>(
    `/system/agents/${agentId}`,
    toBackendSavePayload(payload)
  )

  return unwrapResponse(response)
}

export async function previewSystemHandover(
  payload: HandoverPreviewPayload
): Promise<HandoverPreviewResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<BackendHandoverPreviewResponse>
  >('/system/handover/preview', payload)
  const data = unwrapResponse(response)

  return {
    sourceUserId: data.sourceUserId,
    targetUserId: data.targetUserId,
    transferableCount: data.previewTaskCount,
    tasks: data.previewTasks.map(mapHandoverPreviewTask),
  }
}

export async function executeSystemHandover(
  payload: HandoverPreviewPayload
): Promise<HandoverExecuteResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<BackendHandoverExecuteResponse>
  >('/system/handover/execute', payload)
  const data = unwrapResponse(response)

  return {
    sourceUserId: data.sourceUserId,
    targetUserId: data.targetUserId,
    transferredCount: data.executedTaskCount,
    transferredTaskIds: data.executionTasks.map((item) => item.targetTaskId),
    tasks: data.executionTasks.map(mapHandoverExecuteTask),
  }
}
