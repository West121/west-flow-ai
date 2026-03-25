import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type SystemUserStatus = 'ENABLED' | 'DISABLED'

export type SystemAssociatedUser = {
  userId: string
  displayName: string
  username: string
  departmentName: string
  postName: string
  status: SystemUserStatus
}

export type SystemUserRecord = {
  userId: string
  displayName: string
  username: string
  mobile: string
  email: string
  departmentName: string
  postName: string
  status: SystemUserStatus
  createdAt: string
}

export type SystemUserPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: SystemUserRecord[]
  groups: Array<{
    field: string
    value: string
  }>
}

export type SystemUserDetail = {
  userId: string
  displayName: string
  username: string
  mobile: string
  email: string
  companyId: string
  companyName: string
  departmentId: string
  departmentName: string
  postId: string
  postName: string
  roleIds: string[]
  enabled: boolean
}

export type SystemUserFormOptions = {
  companies: Array<{
    id: string
    name: string
  }>
  posts: Array<{
    id: string
    name: string
    departmentId: string
    departmentName: string
  }>
  roles: Array<{
    id: string
    name: string
    roleCode: string
    roleCategory: 'SYSTEM' | 'BUSINESS'
  }>
}

export type SaveSystemUserPayload = {
  displayName: string
  username: string
  mobile: string
  email: string
  companyId: string
  primaryPostId: string
  roleIds: string[]
  enabled: boolean
}

export async function listSystemUsers(
  search: ListQuerySearch
): Promise<SystemUserPageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<SystemUserPageResponse>
  >('/system/users/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getSystemUserDetail(
  userId: string
): Promise<SystemUserDetail> {
  const response = await apiClient.get<ApiSuccessResponse<SystemUserDetail>>(
    `/system/users/${userId}`
  )

  return unwrapResponse(response)
}

export async function getSystemUserFormOptions(): Promise<SystemUserFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<SystemUserFormOptions>>(
    '/system/users/options'
  )

  return unwrapResponse(response)
}

export async function createSystemUser(
  payload: SaveSystemUserPayload
): Promise<{ userId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ userId: string }>>(
    '/system/users',
    payload
  )

  return unwrapResponse(response)
}

export async function updateSystemUser(
  userId: string,
  payload: SaveSystemUserPayload
): Promise<{ userId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ userId: string }>>(
    `/system/users/${userId}`,
    payload
  )

  return unwrapResponse(response)
}
