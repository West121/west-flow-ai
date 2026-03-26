import { apiClient, unwrapResponse } from '@/lib/api/client'

export type OALaunchTask = {
  taskId: string
  nodeId?: string
  nodeName?: string
  status: string
  assignmentMode: string | null
  candidateUserIds: string[]
  candidateGroupIds?: string[]
  assigneeUserId: string | null
}

export type OALaunchResponse = {
  billId: string
  billNo: string
  processInstanceId: string | null
  activeTasks: OALaunchTask[]
}

export type OADraftListItem = {
  billId: string
  billNo: string
  businessType: 'OA_LEAVE' | 'OA_EXPENSE' | 'OA_COMMON'
  businessTitle: string
  sceneCode?: string | null
  processInstanceId?: string | null
  status: 'DRAFT'
  creatorUserId: string
  creatorDisplayName?: string | null
  createdAt: string
  updatedAt: string
}

export type OALeaveBillPayload = {
  leaveType?: string
  days: number
  reason: string
  urgent?: boolean
  managerUserId?: string
}

export type OAExpenseBillPayload = {
  amount: number
  reason: string
}

export type OACommonRequestBillPayload = {
  title: string
  content: string
}

export type OABillDetail = {
  billId: string
  billNo: string
  [key: string]: unknown
}

export type OALeaveBillDetail = OABillDetail & {
  sceneCode: string
  leaveType: string
  days: number
  reason: string
  urgent: boolean
  managerUserId: string
  processInstanceId: string | null
  status: string
}

export type OAExpenseBillDetail = OABillDetail & {
  sceneCode: string
  amount: number
  reason: string
  processInstanceId: string | null
  status: string
}

export type OACommonRequestBillDetail = OABillDetail & {
  sceneCode: string
  title: string
  content: string
  processInstanceId: string | null
  status: string
}

async function requestOALaunch<TPayload>(
  method: 'post' | 'put',
  url: string,
  payload: TPayload
): Promise<OALaunchResponse> {
  const response = await apiClient[method]<{
    code: 'OK'
    message: string
    data: OALaunchResponse
    requestId: string
  }>(url, payload)

  return unwrapResponse(response)
}

async function getOABillDetail<TDetail extends OABillDetail>(
  url: string
): Promise<TDetail> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: TDetail
    requestId: string
  }>(url)

  return unwrapResponse(response)
}

export async function createOALeaveBill(
  payload: OALeaveBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', '/oa/leaves', payload)
}

export async function saveOALeaveDraft(
  payload: OALeaveBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', '/oa/leaves/draft', payload)
}

export async function updateOALeaveDraft(
  billId: string,
  payload: OALeaveBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('put', `/oa/leaves/${billId}/draft`, payload)
}

export async function submitOALeaveDraft(
  billId: string,
  payload: OALeaveBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', `/oa/leaves/${billId}/submit`, payload)
}

export async function createOAExpenseBill(
  payload: OAExpenseBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', '/oa/expenses', payload)
}

export async function saveOAExpenseDraft(
  payload: OAExpenseBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', '/oa/expenses/draft', payload)
}

export async function updateOAExpenseDraft(
  billId: string,
  payload: OAExpenseBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('put', `/oa/expenses/${billId}/draft`, payload)
}

export async function submitOAExpenseDraft(
  billId: string,
  payload: OAExpenseBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', `/oa/expenses/${billId}/submit`, payload)
}

export async function createOACommonRequestBill(
  payload: OACommonRequestBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', '/oa/common-requests', payload)
}

export async function saveOACommonRequestDraft(
  payload: OACommonRequestBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', '/oa/common-requests/draft', payload)
}

export async function updateOACommonRequestDraft(
  billId: string,
  payload: OACommonRequestBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('put', `/oa/common-requests/${billId}/draft`, payload)
}

export async function submitOACommonRequestDraft(
  billId: string,
  payload: OACommonRequestBillPayload
): Promise<OALaunchResponse> {
  return requestOALaunch('post', `/oa/common-requests/${billId}/submit`, payload)
}

export async function getOALeaveBillDetail(
  billId: string
): Promise<OALeaveBillDetail> {
  return getOABillDetail<OALeaveBillDetail>(`/oa/leaves/${billId}`)
}

export async function getOAExpenseBillDetail(
  billId: string
): Promise<OAExpenseBillDetail> {
  return getOABillDetail<OAExpenseBillDetail>(`/oa/expenses/${billId}`)
}

export async function getOACommonRequestBillDetail(
  billId: string
): Promise<OACommonRequestBillDetail> {
  return getOABillDetail<OACommonRequestBillDetail>(`/oa/common-requests/${billId}`)
}

async function getDraftList(url: string): Promise<OADraftListItem[]> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: OADraftListItem[]
    requestId: string
  }>(url)
  return unwrapResponse(response)
}

export async function listOALeaveDrafts(): Promise<OADraftListItem[]> {
  return getDraftList('/oa/leaves/drafts')
}

export async function listOAExpenseDrafts(): Promise<OADraftListItem[]> {
  return getDraftList('/oa/expenses/drafts')
}

export async function listOACommonDrafts(): Promise<OADraftListItem[]> {
  return getDraftList('/oa/common-requests/drafts')
}
