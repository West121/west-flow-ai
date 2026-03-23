import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type NotificationChannelStatus = 'ENABLED' | 'DISABLED'
export type NotificationChannelType = 'EMAIL' | 'WEBHOOK' | 'WECHAT_WORK' | 'DINGTALK'

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

export type NotificationChannelRecord = {
  channelId: string
  channelName: string
  channelType: NotificationChannelType | string
  endpoint: string
  status: NotificationChannelStatus
  createdAt: string
}

export type NotificationChannelPageResponse = BasePageResponse<NotificationChannelRecord>

export type NotificationChannelDetail = NotificationChannelRecord & {
  secret: string | null
  remark: string | null
  updatedAt: string
}

export type NotificationChannelDiagnostic = {
  channelId: string
  channelCode: string | null
  channelType: NotificationChannelType | string
  channelName: string
  enabled: boolean
  mockMode: boolean
  configurationComplete: boolean
  missingConfigFields: string[]
  healthStatus: string | null
  lastSentAt: string | null
  lastDispatchSuccess: boolean | null
  lastDispatchStatus: string | null
  lastProviderName: string | null
  lastResponseMessage: string | null
  lastDispatchAt: string | null
  lastFailureAt: string | null
  lastFailureMessage: string | null
}

export type NotificationChannelFormOptions = {
  channelTypes: Array<{
    value: NotificationChannelType | string
    label: string
  }>
}

export type SaveNotificationChannelPayload = {
  channelName: string
  channelType: NotificationChannelType | string
  endpoint: string
  secret?: string | null
  remark?: string | null
  enabled: boolean
}

export async function listNotificationChannels(
  search: ListQuerySearch
): Promise<NotificationChannelPageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<NotificationChannelPageResponse>
  >('/system/notification-channels/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getNotificationChannelDetail(
  channelId: string
): Promise<NotificationChannelDetail> {
  const response = await apiClient.get<ApiSuccessResponse<NotificationChannelDetail>>(
    `/system/notification-channels/${channelId}`
  )

  return unwrapResponse(response)
}

export async function getNotificationChannelDiagnostic(
  channelId: string
): Promise<NotificationChannelDiagnostic> {
  const response = await apiClient.get<
    ApiSuccessResponse<NotificationChannelDiagnostic>
  >(`/notification/channels/${channelId}/diagnostic`)

  return unwrapResponse(response)
}

export async function getNotificationChannelFormOptions(): Promise<NotificationChannelFormOptions> {
  const response = await apiClient.get<
    ApiSuccessResponse<NotificationChannelFormOptions>
  >('/system/notification-channels/options')

  return unwrapResponse(response)
}

export async function createNotificationChannel(
  payload: SaveNotificationChannelPayload
): Promise<{ channelId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ channelId: string }>>(
    '/system/notification-channels',
    payload
  )

  return unwrapResponse(response)
}

export async function updateNotificationChannel(
  channelId: string,
  payload: SaveNotificationChannelPayload
): Promise<{ channelId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ channelId: string }>>(
    `/system/notification-channels/${channelId}`,
    payload
  )

  return unwrapResponse(response)
}
