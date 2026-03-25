import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { type SystemAssociatedUser } from './system-users'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type OrganizationStatus = 'ENABLED' | 'DISABLED'

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

export type CompanyRecord = {
  companyId: string
  companyName: string
  companyCode?: string
  leaderName?: string
  contactPhone?: string
  status: OrganizationStatus
  createdAt: string
}

export type CompanyPageResponse = BasePageResponse<CompanyRecord>

export type CompanyDetail = {
  companyId: string
  companyName: string
  companyCode?: string
  leaderName?: string
  contactPhone?: string
  createdAt?: string
  updatedAt?: string
  enabled: boolean
}

export type CompanyFormOptions = {
  companies: Array<{
    id: string
    name: string
    enabled: boolean
  }>
}

export type SaveCompanyPayload = {
  companyName: string
  companyCode?: string
  leaderName?: string
  contactPhone?: string
  enabled: boolean
}

export type DepartmentRecord = {
  departmentId: string
  companyName: string
  parentDepartmentName: string | null
  departmentName: string
  departmentCode?: string
  leaderName?: string
  status: OrganizationStatus
  createdAt: string
}

export type DepartmentPageResponse = BasePageResponse<DepartmentRecord>

export type DepartmentTreeNode = {
  departmentId: string
  companyId: string
  companyName: string
  parentDepartmentId: string | null
  parentDepartmentName: string | null
  departmentName: string
  departmentCode?: string
  leaderName?: string
  status: OrganizationStatus
  createdAt: string
  children: DepartmentTreeNode[]
}

export type DepartmentDetail = {
  departmentId: string
  companyId: string
  companyName: string
  parentDepartmentId: string | null
  parentDepartmentName: string | null
  departmentName: string
  departmentCode?: string
  leaderName?: string
  contactPhone?: string
  createdAt?: string
  updatedAt?: string
  enabled: boolean
}

export type DepartmentFormOptions = {
  companies: Array<{
    id: string
    name: string
    enabled: boolean
  }>
  parentDepartments: Array<{
    id: string
    name: string
    companyId: string
    companyName: string
    parentDepartmentId: string | null
    enabled: boolean
  }>
}

export type SaveDepartmentPayload = {
  companyId: string
  parentDepartmentId: string | null
  departmentName: string
  departmentCode?: string
  leaderName?: string
  contactPhone?: string
  enabled: boolean
}

export type PostRecord = {
  postId: string
  companyName: string
  departmentName: string
  postName: string
  postCode?: string
  leaderName?: string
  status: OrganizationStatus
  createdAt: string
}

export type PostPageResponse = BasePageResponse<PostRecord>

export type PostDetail = {
  postId: string
  companyId: string
  companyName: string
  departmentId: string
  departmentName: string
  postName: string
  postCode?: string
  leaderName?: string
  contactPhone?: string
  createdAt?: string
  updatedAt?: string
  enabled: boolean
}

export type PostFormOptions = {
  departments: Array<{
    id: string
    name: string
    companyId: string
    companyName: string
    enabled: boolean
  }>
}

export type SavePostPayload = {
  companyId?: string
  departmentId: string
  postName: string
  postCode?: string
  leaderName?: string
  contactPhone?: string
  enabled: boolean
}

export async function listCompanies(
  search: ListQuerySearch
): Promise<CompanyPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<CompanyPageResponse>>(
    '/system/companies/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getCompanyDetail(
  companyId: string
): Promise<CompanyDetail> {
  const response = await apiClient.get<ApiSuccessResponse<CompanyDetail>>(
    `/system/companies/${companyId}`
  )

  return unwrapResponse(response)
}

export async function getCompanyFormOptions(): Promise<CompanyFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<CompanyFormOptions>>(
    '/system/companies/options'
  )

  return unwrapResponse(response)
}

export async function createCompany(
  payload: SaveCompanyPayload
): Promise<{ companyId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ companyId: string }>>(
    '/system/companies',
    payload
  )

  return unwrapResponse(response)
}

export async function updateCompany(
  companyId: string,
  payload: SaveCompanyPayload
): Promise<{ companyId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ companyId: string }>>(
    `/system/companies/${companyId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function listDepartments(
  search: ListQuerySearch
): Promise<DepartmentPageResponse> {
  const response = await apiClient.post<
    ApiSuccessResponse<DepartmentPageResponse>
  >('/system/departments/page', toPaginationRequest(search))

  return unwrapResponse(response)
}

function normalizeDepartmentStatus(value: unknown): OrganizationStatus {
  if (value === 'ENABLED' || value === 'DISABLED') {
    return value
  }

  return value ? 'ENABLED' : 'DISABLED'
}

function normalizeDepartmentTreeNode(value: unknown): DepartmentTreeNode {
  const node = (value ?? {}) as Record<string, unknown>
  const children = Array.isArray(node.children) ? node.children : []

  return {
    departmentId: String(node.departmentId ?? ''),
    companyId: String(node.companyId ?? ''),
    companyName: String(node.companyName ?? ''),
    parentDepartmentId:
      node.parentDepartmentId === null || node.parentDepartmentId === undefined || node.parentDepartmentId === ''
        ? null
        : String(node.parentDepartmentId),
    parentDepartmentName:
      node.parentDepartmentName === null || node.parentDepartmentName === undefined || node.parentDepartmentName === ''
        ? null
        : String(node.parentDepartmentName),
    departmentName: String(node.departmentName ?? ''),
    departmentCode:
      node.departmentCode === undefined || node.departmentCode === null || node.departmentCode === ''
        ? undefined
        : String(node.departmentCode),
    leaderName:
      node.leaderName === undefined || node.leaderName === null || node.leaderName === ''
        ? undefined
        : String(node.leaderName),
    status: normalizeDepartmentStatus(node.status ?? node.enabled),
    createdAt:
      node.createdAt === undefined || node.createdAt === null
        ? ''
        : String(node.createdAt),
    children: normalizeDepartmentTreeResponse(children),
  }
}

function normalizeDepartmentTreeResponse(
  value: unknown
): DepartmentTreeNode[] {
  if (Array.isArray(value)) {
    return value.map(normalizeDepartmentTreeNode)
  }

  if (!value || typeof value !== 'object') {
    return []
  }

  const candidate = value as Record<string, unknown>
  const items =
    candidate.records ??
    candidate.departments ??
    candidate.items ??
    candidate.children ??
    candidate.data

  if (Array.isArray(items)) {
    return items.map(normalizeDepartmentTreeNode)
  }

  return []
}

export async function getDepartmentTree(): Promise<DepartmentTreeNode[]> {
  const response = await apiClient.get<ApiSuccessResponse<unknown>>(
    '/system/departments/tree'
  )

  return normalizeDepartmentTreeResponse(unwrapResponse(response))
}

export async function getDepartmentDetail(
  departmentId: string
): Promise<DepartmentDetail> {
  const response = await apiClient.get<ApiSuccessResponse<DepartmentDetail>>(
    `/system/departments/${departmentId}`
  )

  return unwrapResponse(response)
}

export async function getDepartmentFormOptions(
  companyId?: string
): Promise<DepartmentFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<DepartmentFormOptions>>(
    '/system/departments/options',
    {
      params: companyId ? { companyId } : undefined,
    }
  )

  return unwrapResponse(response)
}

export async function createDepartment(
  payload: SaveDepartmentPayload
): Promise<{ departmentId: string }> {
  const response = await apiClient.post<
    ApiSuccessResponse<{ departmentId: string }>
  >('/system/departments', payload)

  return unwrapResponse(response)
}

export async function updateDepartment(
  departmentId: string,
  payload: SaveDepartmentPayload
): Promise<{ departmentId: string }> {
  const response = await apiClient.put<
    ApiSuccessResponse<{ departmentId: string }>
  >(`/system/departments/${departmentId}`, payload)

  return unwrapResponse(response)
}

export async function getDepartmentUsers(
  departmentId: string
): Promise<SystemAssociatedUser[]> {
  const response = await apiClient.get<ApiSuccessResponse<SystemAssociatedUser[]>>(
    `/system/departments/${departmentId}/users`
  )

  return unwrapResponse(response)
}

export async function listPosts(
  search: ListQuerySearch
): Promise<PostPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<PostPageResponse>>(
    '/system/posts/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getPostDetail(postId: string): Promise<PostDetail> {
  const response = await apiClient.get<ApiSuccessResponse<PostDetail>>(
    `/system/posts/${postId}`
  )

  return unwrapResponse(response)
}

export async function getPostFormOptions(
  companyId?: string
): Promise<PostFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<PostFormOptions>>(
    '/system/posts/options',
    {
      params: companyId ? { companyId } : undefined,
    }
  )

  return unwrapResponse(response)
}

export async function createPost(
  payload: SavePostPayload
): Promise<{ postId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ postId: string }>>(
    '/system/posts',
    payload
  )

  return unwrapResponse(response)
}

export async function updatePost(
  postId: string,
  payload: SavePostPayload
): Promise<{ postId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ postId: string }>>(
    `/system/posts/${postId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function getPostUsers(
  postId: string
): Promise<SystemAssociatedUser[]> {
  const response = await apiClient.get<ApiSuccessResponse<SystemAssociatedUser[]>>(
    `/system/posts/${postId}/users`
  )

  return unwrapResponse(response)
}
