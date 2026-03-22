import { type ListQuerySearch, toPaginationRequest } from '@/features/shared/table/query-contract'
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
