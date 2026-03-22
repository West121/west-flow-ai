import { type ListQuerySearch, toPaginationRequest } from '@/features/shared/table/query-contract'
import {
  type ProcessDefinitionDetailResponse,
  type ProcessDefinitionDslPayload,
} from '@/features/workflow/designer/dsl'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type ProcessDefinitionRecord = {
  processDefinitionId: string
  processKey: string
  processName: string
  category: string
  version: number
  status: string
  createdAt: string
}

export type ProcessDefinitionPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: ProcessDefinitionRecord[]
  groups: Array<{
    field: string
    value: string
  }>
}

export async function listProcessDefinitions(
  search: ListQuerySearch
): Promise<ProcessDefinitionPageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<ProcessDefinitionPageResponse>
  >('/process-definitions/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getProcessDefinitionDetail(
  processDefinitionId: string
): Promise<ProcessDefinitionDetailResponse> {
  const response = await apiClient.get<
    ApiSuccessResponse<ProcessDefinitionDetailResponse>
  >(`/process-definitions/${processDefinitionId}`)

  return unwrapResponse(response)
}

export async function saveProcessDefinition(
  payload: ProcessDefinitionDslPayload
): Promise<ProcessDefinitionDetailResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<ProcessDefinitionDetailResponse>
  >('/process-definitions/draft', payload)

  return unwrapResponse(response)
}

export async function publishProcessDefinition(
  payload: ProcessDefinitionDslPayload
): Promise<ProcessDefinitionDetailResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<ProcessDefinitionDetailResponse>
  >('/process-definitions/publish', payload)

  return unwrapResponse(response)
}
