import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type SystemFileStatus = 'ACTIVE' | 'DELETED'

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

export type SystemFileRecord = {
  fileId: string
  displayName: string
  originalFilename: string
  bucketName: string
  objectName: string
  contentType: string
  fileSize: number
  status: SystemFileStatus
  createdAt: string
}

export type SystemFilePageResponse = BasePageResponse<SystemFileRecord>

export type SystemFileDetail = SystemFileRecord & {
  remark: string | null
  downloadUrl: string
  previewUrl: string
  updatedAt: string
  deletedAt: string | null
}

export type SaveSystemFilePayload = {
  displayName: string
  remark?: string | null
}

export type UploadSystemFilePayload = {
  file: File
  displayName?: string | null
  remark?: string | null
}

export async function listSystemFiles(
  search: ListQuerySearch
): Promise<SystemFilePageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<SystemFilePageResponse>>(
    '/system/files/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getSystemFileDetail(fileId: string): Promise<SystemFileDetail> {
  const response = await apiClient.get<ApiSuccessResponse<SystemFileDetail>>(
    `/system/files/${fileId}`
  )

  return unwrapResponse(response)
}

export async function uploadSystemFile(
  payload: UploadSystemFilePayload
): Promise<{ fileId: string }> {
  const formData = new FormData()
  formData.append('file', payload.file)
  if (payload.displayName !== undefined && payload.displayName !== null) {
    formData.append('displayName', payload.displayName)
  }
  if (payload.remark !== undefined && payload.remark !== null) {
    formData.append('remark', payload.remark)
  }

  const response = await apiClient.post<ApiSuccessResponse<{ fileId: string }>>(
    '/system/files',
    formData
  )

  return unwrapResponse(response)
}

export async function updateSystemFile(
  fileId: string,
  payload: SaveSystemFilePayload
): Promise<{ fileId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ fileId: string }>>(
    `/system/files/${fileId}`,
    payload
  )

  return unwrapResponse(response)
}

export async function deleteSystemFile(fileId: string): Promise<{ fileId: string }> {
  const response = await apiClient.delete<ApiSuccessResponse<{ fileId: string }>>(
    `/system/files/${fileId}`
  )

  return unwrapResponse(response)
}

export function getSystemFileDownloadUrl(fileId: string) {
  return `/api/v1/system/files/${fileId}/download`
}

export function getSystemFilePreviewUrl(fileId: string) {
  return `/api/v1/system/files/${fileId}/preview`
}
