import { apiClient, unwrapResponse } from '@/lib/api/client'

export type OALaunchTask = {
  taskId: string
  nodeId?: string
  nodeName?: string
  status: string
  assignmentMode: string | null
  candidateUserIds: string[]
  assigneeUserId: string | null
}

export type OALaunchResponse = {
  billId: string
  billNo: string
  processInstanceId: string
  activeTasks: OALaunchTask[]
}

export type OALeaveBillPayload = {
  days: number
  reason: string
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

async function postOALaunch<TPayload>(
  url: string,
  payload: TPayload
): Promise<OALaunchResponse> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: OALaunchResponse
    requestId: string
  }>(url, payload)

  return unwrapResponse(response)
}

async function getOABillDetail(url: string): Promise<OABillDetail> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: OABillDetail
    requestId: string
  }>(url)

  return unwrapResponse(response)
}

export async function createOALeaveBill(
  payload: OALeaveBillPayload
): Promise<OALaunchResponse> {
  return postOALaunch('/oa/leaves', payload)
}

export async function createOAExpenseBill(
  payload: OAExpenseBillPayload
): Promise<OALaunchResponse> {
  return postOALaunch('/oa/expenses', payload)
}

export async function createOACommonRequestBill(
  payload: OACommonRequestBillPayload
): Promise<OALaunchResponse> {
  return postOALaunch('/oa/common-requests', payload)
}

export async function getOALeaveBillDetail(
  billId: string
): Promise<OABillDetail> {
  return getOABillDetail(`/oa/leaves/${billId}`)
}

export async function getOAExpenseBillDetail(
  billId: string
): Promise<OABillDetail> {
  return getOABillDetail(`/oa/expenses/${billId}`)
}

export async function getOACommonRequestBillDetail(
  billId: string
): Promise<OABillDetail> {
  return getOABillDetail(`/oa/common-requests/${billId}`)
}
