import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type SystemRoleStatus = 'ENABLED' | 'DISABLED'
export type SystemRoleCategory = 'SYSTEM' | 'BUSINESS'
export type DataScopeType =
  | 'ALL'
  | 'SELF'
  | 'DEPARTMENT'
  | 'DEPARTMENT_AND_CHILDREN'
  | 'COMPANY'

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

export type SystemRoleRecord = {
  roleId: string
  roleCode: string
  roleName: string
  roleCategory: SystemRoleCategory
  dataScopeSummary: string
  menuCount: number
  status: SystemRoleStatus
  createdAt: string
}

export type SystemRolePageResponse = BasePageResponse<SystemRoleRecord>

export type RoleDataScope = {
  scopeType: DataScopeType
  scopeValue: string
}

export type SystemRoleDetail = {
  roleId: string
  roleCode: string
  roleName: string
  roleCategory: SystemRoleCategory
  description: string | null
  menuIds: string[]
  dataScopes: RoleDataScope[]
  enabled: boolean
}

export type SystemRoleFormOptions = {
  menus: Array<{
    id: string
    name: string
    menuType: 'DIRECTORY' | 'MENU' | 'BUTTON'
    parentMenuName: string | null
  }>
  scopeTypes: Array<{
    code: DataScopeType
    name: string
  }>
  companies: Array<{
    id: string
    name: string
  }>
  departments: Array<{
    id: string
    name: string
    companyId: string
    companyName: string
  }>
  users: Array<{
    id: string
    name: string
    departmentId: string
    departmentName: string
  }>
}

export type SaveSystemRolePayload = {
  roleName: string
  roleCode: string
  roleCategory: SystemRoleCategory
  description: string | null
  menuIds: string[]
  dataScopes: RoleDataScope[]
  enabled: boolean
}

export async function listRoles(
  search: ListQuerySearch
): Promise<SystemRolePageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<SystemRolePageResponse>
  >('/system/roles/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

export async function getRoleDetail(
  roleId: string
): Promise<SystemRoleDetail> {
  const response = await apiClient.get<ApiSuccessResponse<SystemRoleDetail>>(
    `/system/roles/${roleId}`
  )

  return unwrapResponse(response)
}

export async function getRoleFormOptions(): Promise<SystemRoleFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<SystemRoleFormOptions>>(
    '/system/roles/options'
  )

  return unwrapResponse(response)
}

export async function createRole(
  payload: SaveSystemRolePayload
): Promise<{ roleId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ roleId: string }>>(
    '/system/roles',
    payload
  )

  return unwrapResponse(response)
}

export async function updateRole(
  roleId: string,
  payload: SaveSystemRolePayload
): Promise<{ roleId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ roleId: string }>>(
    `/system/roles/${roleId}`,
    payload
  )

  return unwrapResponse(response)
}
