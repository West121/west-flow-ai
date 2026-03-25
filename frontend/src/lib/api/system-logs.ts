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

export type AuditLogStatus = 'SUCCESS' | 'FAILED' | string
export type LoginLogStatus = 'SUCCESS' | 'FAILED' | string
export type LogNotificationStatus = 'SUCCESS' | 'FAILED' | string

export type AuditLogListRecord = {
  logId: string
  requestId: string
  module: string
  path: string
  method: string
  status: AuditLogStatus
  statusCode: number
  loginId: string
  username: string
  clientIp: string
  createdAt: string
}

export type AuditLogListResponse = BasePageResponse<AuditLogListRecord>

export type AuditLogDetail = AuditLogListRecord & {
  ipRegion: string
  userAgent: string | null
  errorMessage: string | null
  durationMs: number
}

export type LoginLogListRecord = {
  logId: string
  username: string
  status: LoginLogStatus
  statusCode: number
  userId: string
  clientIp: string
  createdAt: string
}

export type LoginLogListResponse = BasePageResponse<LoginLogListRecord>

export type LoginLogDetail = LoginLogListRecord & {
  requestId: string
  path: string
  resultMessage: string | null
  ipRegion: string
  userAgent: string | null
  durationMs: number
}

export type NotificationLogListRecord = {
  recordId: string
  channelId: string
  channelName: string
  channelCode: string
  channelType: string
  recipient: string
  title: string
  status: LogNotificationStatus
  sentAt: string
}

export type NotificationLogListResponse = BasePageResponse<NotificationLogListRecord>

export type NotificationLogDetail = NotificationLogListRecord & {
  channelEndpoint: string | null
  providerName: string
  success: boolean
  responseMessage: string | null
  payload: Record<string, unknown> | null
  content: string
}

export async function listAuditLogs(
  search: ListQuerySearch
): Promise<AuditLogListResponse> {
  const response = await apiClient.post<ApiSuccessResponse<AuditLogListResponse>>(
    '/system/logs/audit/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getAuditLogDetail(logId: string): Promise<AuditLogDetail> {
  const response = await apiClient.get<ApiSuccessResponse<AuditLogDetail>>(
    `/system/logs/audit/${logId}`
  )

  return unwrapResponse(response)
}

export async function listLoginLogs(
  search: ListQuerySearch
): Promise<LoginLogListResponse> {
  const response = await apiClient.post<ApiSuccessResponse<LoginLogListResponse>>(
    '/system/logs/login/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getLoginLogDetail(logId: string): Promise<LoginLogDetail> {
  const response = await apiClient.get<ApiSuccessResponse<LoginLogDetail>>(
    `/system/logs/login/${logId}`
  )

  return unwrapResponse(response)
}

export async function listNotificationLogs(
  search: ListQuerySearch
): Promise<NotificationLogListResponse> {
  const response = await apiClient.post<ApiSuccessResponse<NotificationLogListResponse>>(
    '/system/logs/notifications/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getNotificationLogDetail(
  recordId: string
): Promise<NotificationLogDetail> {
  const response = await apiClient.get<ApiSuccessResponse<NotificationLogDetail>>(
    `/system/logs/notifications/${recordId}`
  )

  return unwrapResponse(response)
}
