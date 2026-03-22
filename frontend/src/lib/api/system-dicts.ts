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

export type SystemDictStatus = 'ENABLED' | 'DISABLED'

export type SystemDictTypeRecord = {
  dictTypeId: string
  typeCode: string
  typeName: string
  description: string | null
  status: SystemDictStatus
  itemCount: number
  createdAt: string
}

export type SystemDictTypePageResponse = BasePageResponse<SystemDictTypeRecord>

export type SystemDictTypeDetail = {
  dictTypeId: string
  typeCode: string
  typeName: string
  description: string | null
  status: SystemDictStatus
  itemCount: number
  createdAt: string
  updatedAt: string
}

export type SystemDictTypeFormOption = {
  code: SystemDictStatus
  name: string
}

export type SystemDictTypeFormOptions = {
  statusOptions: SystemDictTypeFormOption[]
}

export type SaveSystemDictTypePayload = {
  typeCode: string
  typeName: string
  description: string | null
  enabled: boolean
}

export type SystemDictItemRecord = {
  dictItemId: string
  dictTypeId: string
  dictTypeCode: string
  dictTypeName: string
  itemCode: string
  itemLabel: string
  itemValue: string
  sortOrder: number
  status: SystemDictStatus
  createdAt: string
}

export type SystemDictItemPageResponse = BasePageResponse<SystemDictItemRecord>

export type SystemDictItemDetail = {
  dictItemId: string
  dictTypeId: string
  dictTypeCode: string
  dictTypeName: string
  itemCode: string
  itemLabel: string
  itemValue: string
  sortOrder: number
  remark: string | null
  status: SystemDictStatus
  createdAt: string
  updatedAt: string
}

export type SystemDictItemDictTypeOption = {
  dictTypeId: string
  typeCode: string
  typeName: string
}

export type SystemDictItemFormOption = {
  code: SystemDictStatus
  name: string
}

export type SystemDictItemFormOptions = {
  dictTypes: SystemDictItemDictTypeOption[]
  statusOptions: SystemDictItemFormOption[]
}

export type SaveSystemDictItemPayload = {
  dictTypeId: string
  itemCode: string
  itemLabel: string
  itemValue: string
  sortOrder: number
  remark: string | null
  enabled: boolean
}

export async function listSystemDictTypes(
  search: ListQuerySearch
): Promise<SystemDictTypePageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<SystemDictTypePageResponse>
  >('/system/dict-types/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getSystemDictTypeDetail(
  dictTypeId: string
): Promise<SystemDictTypeDetail> {
  const response = await apiClient.get<ApiSuccessResponse<SystemDictTypeDetail>>(
    `/system/dict-types/${dictTypeId}`
  )

  return unwrapResponse(response)
}

export async function getSystemDictTypeFormOptions(): Promise<SystemDictTypeFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<SystemDictTypeFormOptions>>(
    '/system/dict-types/options'
  )

  return unwrapResponse(response)
}

export async function createSystemDictType(
  payload: SaveSystemDictTypePayload
): Promise<{ dictTypeId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ dictTypeId: string }>>(
    '/system/dict-types',
    payload
  )

  return unwrapResponse(response)
}

export async function updateSystemDictType(
  dictTypeId: string,
  payload: SaveSystemDictTypePayload
): Promise<{ dictTypeId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ dictTypeId: string }>>(
    `/system/dict-types/${dictTypeId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function listSystemDictItems(
  search: ListQuerySearch
): Promise<SystemDictItemPageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<SystemDictItemPageResponse>
  >('/system/dict-items/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getSystemDictItemDetail(
  dictItemId: string
): Promise<SystemDictItemDetail> {
  const response = await apiClient.get<ApiSuccessResponse<SystemDictItemDetail>>(
    `/system/dict-items/${dictItemId}`
  )

  return unwrapResponse(response)
}

export async function getSystemDictItemFormOptions(): Promise<SystemDictItemFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<SystemDictItemFormOptions>>(
    '/system/dict-items/options'
  )

  return unwrapResponse(response)
}

export async function createSystemDictItem(
  payload: SaveSystemDictItemPayload
): Promise<{ dictItemId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ dictItemId: string }>>(
    '/system/dict-items',
    payload
  )

  return unwrapResponse(response)
}

export async function updateSystemDictItem(
  dictItemId: string,
  payload: SaveSystemDictItemPayload
): Promise<{ dictItemId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ dictItemId: string }>>(
    `/system/dict-items/${dictItemId}`,
    payload
  )

  return unwrapResponse(response)
}
