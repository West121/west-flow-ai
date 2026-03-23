import { apiClient, unwrapResponse } from '@/lib/api/client'
import { type ListQuerySearch, toPaginationRequest } from '@/features/shared/table/query-contract'
import { type ApprovalSheetListItem } from '@/lib/api/workbench'

export type PLMLaunchTask = {
  taskId: string
  nodeId?: string
  nodeName?: string
  status: string
  assignmentMode: string | null
  candidateUserIds: string[]
  assigneeUserId: string | null
}

export type PLMLaunchResponse = {
  billId: string
  billNo: string
  processInstanceId: string
  activeTasks: PLMLaunchTask[]
}

export type PLMECRRequestPayload = {
  changeTitle: string
  changeReason: string
  affectedProductCode?: string
  priorityLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
}

export type PLMECOExecutionPayload = {
  executionTitle: string
  executionPlan: string
  effectiveDate?: string
  changeReason: string
}

export type PLMMaterialChangePayload = {
  materialCode: string
  materialName: string
  changeReason: string
  changeType?: string
}

export type PLMBillDetail = {
  billId: string
  billNo: string
  [key: string]: unknown
}

export type PLMApprovalSheetPage = {
  page: number
  pageSize: number
  total: number
  pages: number
  groups: Array<Record<string, unknown>>
  records: ApprovalSheetListItem[]
}

async function postPLMLaunch<TPayload>(
  url: string,
  payload: TPayload
): Promise<PLMLaunchResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: PLMLaunchResponse
    requestId: string
  }>(url, payload)

  return unwrapResponse(response)
}

async function getPLMBillDetail(url: string): Promise<PLMBillDetail> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: PLMBillDetail
    requestId: string
  }>(url)

  return unwrapResponse(response)
}

async function getPLMApprovalSheetPage(
  search: ListQuerySearch
): Promise<PLMApprovalSheetPage> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: PLMApprovalSheetPage
    requestId: string
  }>('/plm/approval-sheets', {
    params: toPaginationRequest(search),
  })

  return unwrapResponse(response)
}

export async function createPLMECRRequest(
  payload: PLMECRRequestPayload
): Promise<PLMLaunchResponse> {
  return postPLMLaunch('/plm/ecrs', payload)
}

export async function createPLMECOExecution(
  payload: PLMECOExecutionPayload
): Promise<PLMLaunchResponse> {
  return postPLMLaunch('/plm/ecos', payload)
}

export async function createPLMMaterialChangeRequest(
  payload: PLMMaterialChangePayload
): Promise<PLMLaunchResponse> {
  return postPLMLaunch('/plm/material-master-changes', payload)
}

export async function getPLMECRRequestDetail(
  billId: string
): Promise<PLMBillDetail> {
  return getPLMBillDetail(`/plm/ecrs/${billId}`)
}

export async function getPLMECOExecutionDetail(
  billId: string
): Promise<PLMBillDetail> {
  return getPLMBillDetail(`/plm/ecos/${billId}`)
}

export async function getPLMMaterialChangeDetail(
  billId: string
): Promise<PLMBillDetail> {
  return getPLMBillDetail(`/plm/material-master-changes/${billId}`)
}

export async function listPLMApprovalSheets(
  search: ListQuerySearch
): Promise<PLMApprovalSheetPage> {
  return getPLMApprovalSheetPage(search)
}
