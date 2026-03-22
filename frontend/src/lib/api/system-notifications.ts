import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

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

export type NotificationTemplateStatus = 'ENABLED' | 'DISABLED'
export type NotificationTemplateChannelType =
  | 'IN_APP'
  | 'EMAIL'
  | 'WEBHOOK'
  | 'SMS'
  | 'WECHAT'
  | 'DINGTALK'

export type NotificationTemplateRecord = {
  templateId: string
  templateCode: string
  templateName: string
  channelType: NotificationTemplateChannelType | string
  titleTemplate: string
  status: NotificationTemplateStatus
  createdAt: string
}

export type NotificationTemplatePageResponse = BasePageResponse<NotificationTemplateRecord>

export type NotificationTemplateDetail = NotificationTemplateRecord & {
  contentTemplate: string
  remark: string | null
  updatedAt: string
}

export type NotificationTemplateFormOptions = {
  channelTypes: Array<{
    value: NotificationTemplateChannelType | string
    label: string
  }>
  statusOptions: Array<{
    value: NotificationTemplateStatus
    label: string
  }>
}

export type SaveNotificationTemplatePayload = {
  templateCode: string
  templateName: string
  channelType: NotificationTemplateChannelType | string
  titleTemplate: string
  contentTemplate: string
  enabled: boolean
  remark?: string | null
}

export type NotificationRecordStatus = 'SUCCESS' | 'FAILED'

export type NotificationRecordRecord = {
  recordId: string
  channelId: string
  channelName: string
  channelCode: string
  channelType: string
  recipient: string
  title: string
  status: NotificationRecordStatus
  sentAt: string
}

export type NotificationRecordPageResponse = BasePageResponse<NotificationRecordRecord>

export type NotificationRecordDetail = NotificationRecordRecord & {
  channelEndpoint: string | null
  content: string
  providerName: string
  success: boolean
  responseMessage: string
  payload: Record<string, unknown>
}

export async function listNotificationTemplates(
  search: ListQuerySearch
): Promise<NotificationTemplatePageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<NotificationTemplatePageResponse>
  >('/system/notification-templates/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getNotificationTemplateDetail(
  templateId: string
): Promise<NotificationTemplateDetail> {
  const response = await apiClient.get<ApiSuccessResponse<NotificationTemplateDetail>>(
    `/system/notification-templates/${templateId}`
  )

  return unwrapResponse(response)
}

export async function getNotificationTemplateFormOptions(): Promise<NotificationTemplateFormOptions> {
  const response = await apiClient.get<
    ApiSuccessResponse<NotificationTemplateFormOptions>
  >('/system/notification-templates/options')

  return unwrapResponse(response)
}

export async function createNotificationTemplate(
  payload: SaveNotificationTemplatePayload
): Promise<{ templateId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ templateId: string }>>(
    '/system/notification-templates',
    payload
  )

  return unwrapResponse(response)
}

export async function updateNotificationTemplate(
  templateId: string,
  payload: SaveNotificationTemplatePayload
): Promise<{ templateId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ templateId: string }>>(
    `/system/notification-templates/${templateId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function listNotificationRecords(
  search: ListQuerySearch
): Promise<NotificationRecordPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<NotificationRecordPageResponse>>(
    '/system/notification-records/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getNotificationRecordDetail(
  recordId: string
): Promise<NotificationRecordDetail> {
  const response = await apiClient.get<ApiSuccessResponse<NotificationRecordDetail>>(
    `/system/notification-records/${recordId}`
  )

  return unwrapResponse(response)
}
