import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type TriggerAutomationStatus = 'ACTIVE' | 'DISABLED' | 'PAUSED'
export type TriggerEventType = 'TASK_CREATED' | 'TASK_COMPLETED' | 'INSTANCE_STARTED' | 'INSTANCE_COMPLETED'

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

export type TriggerRecord = {
  triggerId: string
  triggerName: string
  triggerKey: string
  triggerEvent: TriggerEventType | string
  automationStatus: TriggerAutomationStatus | string
  createdAt: string
}

export type TriggerPageResponse = BasePageResponse<TriggerRecord>

export type TriggerDetail = TriggerRecord & {
  businessType: string | null
  channelIds: string[]
  conditionExpression: string | null
  description: string | null
  enabled: boolean
  updatedAt: string
}

export type TriggerFormOptions = {
  triggerEvents: Array<{
    value: TriggerEventType | string
    label: string
  }>
}

export type SaveTriggerPayload = {
  triggerName: string
  triggerKey: string
  triggerEvent: TriggerEventType | string
  businessType?: string | null
  channelIds: string[]
  conditionExpression?: string | null
  description?: string | null
  enabled: boolean
}

export async function listTriggers(
  search: ListQuerySearch
): Promise<TriggerPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<TriggerPageResponse>>(
    '/system/triggers/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getTriggerDetail(
  triggerId: string
): Promise<TriggerDetail> {
  const response = await apiClient.get<ApiSuccessResponse<TriggerDetail>>(
    `/system/triggers/${triggerId}`
  )

  return unwrapResponse(response)
}

export async function getTriggerFormOptions(): Promise<TriggerFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<TriggerFormOptions>>(
    '/system/triggers/options'
  )

  return unwrapResponse(response)
}

export async function createTrigger(
  payload: SaveTriggerPayload
): Promise<{ triggerId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ triggerId: string }>>(
    '/system/triggers',
    payload
  )

  return unwrapResponse(response)
}

export async function updateTrigger(
  triggerId: string,
  payload: SaveTriggerPayload
): Promise<{ triggerId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ triggerId: string }>>(
    `/system/triggers/${triggerId}`,
    payload
  )

  return unwrapResponse(response)
}
