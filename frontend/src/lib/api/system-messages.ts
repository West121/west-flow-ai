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

export type MessageStatus = 'DRAFT' | 'SENT' | 'CANCELLED'
export type MessageTargetType = 'ALL' | 'USER' | 'DEPARTMENT'
export type MessageReadStatus = 'UNREAD' | 'READ'

export type SystemMessageRecord = {
  messageId: string
  title: string
  status: MessageStatus
  targetType: MessageTargetType
  readStatus: MessageReadStatus
  sentAt: string
  createdAt: string
  targetUserIds: string[]
  targetDepartmentIds: string[]
}

export type SystemMessagePageResponse = BasePageResponse<SystemMessageRecord>

export type SystemMessageDetail = {
  messageId: string
  title: string
  content: string
  status: MessageStatus
  targetType: MessageTargetType
  readStatus: MessageReadStatus
  sentAt: string
  createdAt: string
  updatedAt: string
  senderUserId: string
  targetUserIds: string[]
  targetDepartmentIds: string[]
}

export type MessageSelectOption = {
  code: string
  name: string
}

export type SystemMessageFormOptions = {
  statusOptions: MessageSelectOption[]
  targetTypeOptions: MessageSelectOption[]
  readStatusOptions: MessageSelectOption[]
}

export type SaveSystemMessagePayload = {
  title: string
  content: string
  status: string
  targetType: MessageTargetType
  targetUserIds?: string[]
  targetDepartmentIds?: string[]
  sentAt?: string | null
}

export async function listSystemMessages(
  search: ListQuerySearch
): Promise<SystemMessagePageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<SystemMessagePageResponse>
  >('/system/messages/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getSystemMessageDetail(
  messageId: string
): Promise<SystemMessageDetail> {
  const response = await apiClient.get<ApiSuccessResponse<SystemMessageDetail>>(
    `/system/messages/${messageId}`
  )

  return unwrapResponse(response)
}

export async function getSystemMessageFormOptions(): Promise<SystemMessageFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<SystemMessageFormOptions>>(
    '/system/messages/options'
  )

  return unwrapResponse(response)
}

export async function createSystemMessage(
  payload: SaveSystemMessagePayload
): Promise<{ messageId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ messageId: string }>>(
    '/system/messages',
    payload
  )

  return unwrapResponse(response)
}

export async function updateSystemMessage(
  messageId: string,
  payload: SaveSystemMessagePayload
): Promise<{ messageId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ messageId: string }>>(
    `/system/messages/${messageId}`,
    payload
  )

  return unwrapResponse(response)
}
