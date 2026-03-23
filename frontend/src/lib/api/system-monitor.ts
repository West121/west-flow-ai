import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

// 后端分页列表的通用返回结构。
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

export type OrchestratorScanStatus = string
export type TriggerExecutionStatus = string
export type ChannelHealthStatus = string

export type OrchestratorScanListRecord = {
  executionId: string
  runId: string
  targetId: string
  targetName: string
  automationType: string
  status: OrchestratorScanStatus
  message: string
  executedAt: string
  scannedAt: string
}

export type OrchestratorScanListResponse = BasePageResponse<OrchestratorScanListRecord>

export type OrchestratorScanDetail = OrchestratorScanListRecord

export type TriggerExecutionListRecord = {
  executionId: string
  triggerId: string
  triggerName: string
  triggerKey: string
  triggerEvent: string
  action: string
  enabled: boolean | null
  operatorUserId: string
  status: TriggerExecutionStatus
  executedAt: string
}

export type TriggerExecutionListResponse = BasePageResponse<TriggerExecutionListRecord>

export type TriggerExecutionDetail = TriggerExecutionListRecord & {
  channelIds: string[]
  description: string
  conditionExpression: string
}

export type NotificationChannelHealthListRecord = {
  channelId: string
  channelCode: string
  channelName: string
  channelType: string
  status: ChannelHealthStatus
  latestStatus: string
  totalAttempts: number
  successAttempts: number
  failedAttempts: number
  successRate: number
  lastSentAt: string
  latestResponseMessage: string | null
}

export type NotificationChannelHealthListResponse = BasePageResponse<NotificationChannelHealthListRecord>

export type NotificationChannelHealthDetail = NotificationChannelHealthListRecord & {
  enabled: boolean | null
  createdAt: string
  updatedAt: string
  remark: string
  channelEndpoint: string | null
}

export async function listOrchestratorScans(
  search: ListQuerySearch
): Promise<OrchestratorScanListResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<OrchestratorScanListResponse>
  >('/system/monitor/orchestrator-scans/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getOrchestratorScanDetail(
  executionId: string
): Promise<OrchestratorScanDetail> {
  const response = await apiClient.get<ApiSuccessResponse<OrchestratorScanDetail>>(
    `/system/monitor/orchestrator-scans/${executionId}`
  )

  return unwrapResponse(response)
}

export async function listTriggerExecutions(
  search: ListQuerySearch
): Promise<TriggerExecutionListResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<TriggerExecutionListResponse>
  >('/system/monitor/trigger-executions/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getTriggerExecutionDetail(
  executionId: string
): Promise<TriggerExecutionDetail> {
  const response = await apiClient.get<ApiSuccessResponse<TriggerExecutionDetail>>(
    `/system/monitor/trigger-executions/${executionId}`
  )

  return unwrapResponse(response)
}

export async function listNotificationChannelHealths(
  search: ListQuerySearch
): Promise<NotificationChannelHealthListResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<NotificationChannelHealthListResponse>
  >('/system/monitor/notification-channels/health/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getNotificationChannelHealthDetail(
  channelId: string
): Promise<NotificationChannelHealthDetail> {
  const response = await apiClient.get<ApiSuccessResponse<NotificationChannelHealthDetail>>(
    `/system/monitor/notification-channels/health/${channelId}`
  )

  return unwrapResponse(response)
}

export async function recheckNotificationChannelHealth(
  channelId: string
): Promise<NotificationChannelHealthDetail> {
  const response = await apiClient.post<ApiSuccessResponse<NotificationChannelHealthDetail>>(
    `/system/monitor/notification-channels/health/${channelId}/recheck`
  )

  return unwrapResponse(response)
}
