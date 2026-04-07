import { apiClient, unwrapResponse } from '@/lib/api/client'
import { type ApprovalSheetListItem } from '@/lib/api/workbench'
import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'

export type PlmBusinessTypeCode = 'PLM_ECR' | 'PLM_ECO' | 'PLM_MATERIAL'
export type PLMAffectedItemTypeCode =
  | 'PART'
  | 'DOCUMENT'
  | 'BOM'
  | 'MATERIAL'
  | 'PROCESS'
export type PLMAffectedItemChangeActionCode =
  | 'ADD'
  | 'UPDATE'
  | 'REMOVE'
  | 'REPLACE'
export type PLMBillLifecycleAction =
  | 'SUBMIT'
  | 'CANCEL'
  | 'START_IMPLEMENTATION'
  | 'MARK_VALIDATING'
  | 'CLOSE'
export type PlmLifecycleStatus =
  | 'DRAFT'
  | 'RUNNING'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'IMPLEMENTING'
  | 'VALIDATING'
  | 'CLOSED'

export type PLMAffectedItemPayload = {
  itemType: PLMAffectedItemTypeCode
  itemCode: string
  itemName: string
  beforeVersion?: string
  afterVersion?: string
  changeAction: PLMAffectedItemChangeActionCode
  ownerUserId?: string
  remark?: string
}

export type PLMAffectedItemDetail = PLMAffectedItemPayload & {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
}

export type PlmObjectTypeCode =
  | 'PART'
  | 'BOM'
  | 'DOCUMENT'
  | 'DRAWING'
  | 'MATERIAL'
  | 'PROCESS'
export type PlmRevisionDiffKind =
  | 'ATTRIBUTE'
  | 'BOM_STRUCTURE'
  | 'DOCUMENT'
  | 'ROUTING'
export type PlmImplementationTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'BLOCKED'
  | 'COMPLETED'
  | 'CANCELLED'

export type PLMObjectLink = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  objectId: string
  objectCode: string
  objectName: string
  objectType: PlmObjectTypeCode
  objectRevisionId?: string | null
  objectRevisionCode?: string | null
  versionLabel?: string | null
  roleCode: string
  roleLabel?: string | null
  changeAction: PLMAffectedItemChangeActionCode | string
  beforeRevisionCode?: string | null
  afterRevisionCode?: string | null
  sourceSystem?: string | null
  externalRef?: string | null
  remark?: string | null
  sortOrder?: number | null
}

export type PLMRevisionDiff = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  objectId: string
  objectCode?: string | null
  objectName?: string | null
  beforeRevisionId?: string | null
  afterRevisionId?: string | null
  beforeRevisionCode?: string | null
  afterRevisionCode?: string | null
  diffKind: PlmRevisionDiffKind | string
  diffSummary: string
  diffPayloadJson?: Record<string, unknown> | null
  createdAt?: string | null
}

export type PLMImplementationTask = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  taskNo: string
  taskTitle: string
  taskType: string
  ownerUserId?: string | null
  ownerDisplayName?: string | null
  status: PlmImplementationTaskStatus | string
  plannedStartAt?: string | null
  plannedEndAt?: string | null
  startedAt?: string | null
  completedAt?: string | null
  resultSummary?: string | null
  verificationRequired?: boolean | null
  sortOrder?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type PLMLaunchTask = {
  taskId: string
  nodeId?: string
  nodeName?: string
  status: string
  assignmentMode: string | null
  candidateUserIds: string[]
  candidateGroupIds?: string[]
  assigneeUserId: string | null
}

export type PLMBillActionResponse = {
  billId: string
  billNo: string
  status?: string
  processInstanceId?: string | null
  activeTasks?: PLMLaunchTask[]
}

export type PLMLaunchResponse = PLMBillActionResponse & {
  processInstanceId: string
  activeTasks: PLMLaunchTask[]
}

export type PLMECRRequestPayload = {
  changeTitle: string
  changeReason: string
  affectedProductCode?: string
  priorityLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
  changeCategory?: string
  targetVersion?: string
  affectedObjectsText?: string
  impactScope?: string
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
  affectedItems?: PLMAffectedItemPayload[]
  implementationOwner?: string
  implementationSummary?: string
  implementationStartedAt?: string
  validationOwner?: string
  validationSummary?: string
  validatedAt?: string
  closedBy?: string
  closedAt?: string
  closeComment?: string
}

export type PLMECOExecutionPayload = {
  executionTitle: string
  executionPlan: string
  effectiveDate?: string
  changeReason: string
  implementationOwner?: string
  implementationSummary?: string
  implementationStartedAt?: string
  validationOwner?: string
  validationSummary?: string
  validatedAt?: string
  closedBy?: string
  closedAt?: string
  closeComment?: string
  targetVersion?: string
  rolloutScope?: string
  validationPlan?: string
  rollbackPlan?: string
  affectedItems?: PLMAffectedItemPayload[]
}

export type PLMMaterialChangePayload = {
  materialCode: string
  materialName: string
  changeReason: string
  changeType?: string
  specificationChange?: string
  oldValue?: string
  newValue?: string
  uom?: string
  affectedSystemsText?: string
  affectedItems?: PLMAffectedItemPayload[]
  implementationOwner?: string
  implementationSummary?: string
  implementationStartedAt?: string
  validationOwner?: string
  validationSummary?: string
  validatedAt?: string
  closedBy?: string
  closedAt?: string
  closeComment?: string
}

export type PLMBillDetail = {
  billId: string
  billNo: string
  businessType?: PlmBusinessTypeCode
  sceneCode?: string | null
  status?: string
  detailSummary?: string
  approvalSummary?: string
  creatorUserId?: string
  creatorDisplayName?: string | null
  createdAt?: string
  updatedAt?: string
  processInstanceId?: string | null
  affectedItems?: PLMAffectedItemDetail[]
  objectLinks?: PLMObjectLink[]
  revisionDiffs?: PLMRevisionDiff[]
  implementationTasks?: PLMImplementationTask[]
  implementationOwner?: string | null
  implementationSummary?: string | null
  implementationStartedAt?: string | null
  validationOwner?: string | null
  validationSummary?: string | null
  validatedAt?: string | null
  closedBy?: string | null
  closedAt?: string | null
  closeComment?: string | null
  availableActions?: PLMBillLifecycleAction[]
  [key: string]: unknown
}

export type PLMECRBillListItem = {
  billId: string
  billNo: string
  sceneCode: string
  changeTitle: string
  affectedProductCode: string | null
  priorityLevel: string | null
  processInstanceId: string | null
  status: string
  detailSummary?: string
  approvalSummary?: string
  creatorUserId: string
  creatorDisplayName?: string | null
  createdAt: string
  updatedAt: string
  changeCategory?: string | null
  targetVersion?: string | null
  impactScope?: string | null
  riskLevel?: string | null
}

export type PLMECOBillListItem = {
  billId: string
  billNo: string
  sceneCode: string
  executionTitle: string
  effectiveDate: string | null
  changeReason: string
  processInstanceId: string | null
  status: string
  detailSummary?: string
  approvalSummary?: string
  creatorUserId: string
  creatorDisplayName?: string | null
  createdAt: string
  updatedAt: string
  implementationOwner?: string | null
  targetVersion?: string | null
  rolloutScope?: string | null
}

export type PLMMaterialChangeBillListItem = {
  billId: string
  billNo: string
  sceneCode: string
  materialCode: string
  materialName: string
  changeType: string | null
  changeReason: string
  processInstanceId: string | null
  status: string
  detailSummary?: string
  approvalSummary?: string
  creatorUserId: string
  creatorDisplayName?: string | null
  createdAt: string
  updatedAt: string
  specificationChange?: string | null
  oldValue?: string | null
  newValue?: string | null
  uom?: string | null
}

export type PLMBillPage<T> = {
  page: number
  pageSize: number
  total: number
  pages: number
  groups: Array<Record<string, unknown>>
  records: T[]
}

export type PLMApprovalSheetPage = {
  page: number
  pageSize: number
  total: number
  pages: number
  groups: Array<Record<string, unknown>>
  records: ApprovalSheetListItem[]
}

export type PLMDashboardSummaryMetrics = {
  totalCount: number
  draftCount: number
  runningCount: number
  completedCount: number
  rejectedCount: number
  cancelledCount: number
  implementingCount?: number
  validatingCount?: number
  closedCount?: number
}

export type PLMDashboardTypeDistributionItem = {
  businessType: PlmBusinessTypeCode
  totalCount: number
  draftCount?: number
  runningCount?: number
  completedCount?: number
  implementingCount?: number
  validatingCount?: number
  closedCount?: number
}

export type PLMDashboardStageDistributionItem = {
  stage: string
  stageLabel?: string | null
  totalCount: number
  percent?: number | null
}

export type PLMDashboardTrendItem = {
  day: string
  totalCount: number
  draftCount?: number
  runningCount?: number
  completedCount?: number
  rejectedCount?: number
  cancelledCount?: number
  implementingCount?: number
  validatingCount?: number
  closedCount?: number
}

export type PLMDashboardTaskAlertItem = {
  id: string
  alertType: string
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | string
  billId: string
  billNo: string
  businessType: PlmBusinessTypeCode
  businessTitle?: string | null
  ownerUserId?: string | null
  ownerDisplayName?: string | null
  dueAt?: string | null
  message?: string | null
  linkHref?: string | null
}

export type PLMDashboardOwnerRankingItem = {
  ownerUserId: string
  ownerDisplayName?: string | null
  totalCount: number
  pendingCount?: number
  blockedCount?: number
  overdueTaskCount?: number
  completedCount?: number
}

export type PLMDashboardSummary = PLMDashboardSummaryMetrics & {
  summary?: PLMDashboardSummaryMetrics
  typeDistribution?: PLMDashboardTypeDistributionItem[]
  stageDistribution?: PLMDashboardStageDistributionItem[]
  trendSeries?: PLMDashboardTrendItem[]
  taskAlerts?: PLMDashboardTaskAlertItem[]
  ownerRanking?: PLMDashboardOwnerRankingItem[]
  recentBills: Array<{
    billId: string
    billNo: string
    businessType: PlmBusinessTypeCode
    businessTitle: string
    sceneCode?: string | null
    status: string
    detailSummary?: string | null
    creatorUserId?: string | null
    creatorDisplayName?: string | null
    updatedAt: string
  }>
  byBusinessType?: Array<{
    businessType: PlmBusinessTypeCode
    totalCount: number
    draftCount?: number
    runningCount?: number
    completedCount?: number
    implementingCount?: number
    validatingCount?: number
    closedCount?: number
  }>
}

export type PLMImplementationTaskActionCode =
  | 'START'
  | 'COMPLETE'
  | 'BLOCK'
  | 'CANCEL'

export type PLMImplementationTaskActionResponse = PLMBillActionResponse

export type PLMDashboardSummaryResponse = PLMDashboardSummary

async function requestPLMAction<TPayload>(
  method: 'post' | 'put',
  url: string,
  payload?: TPayload
): Promise<PLMBillActionResponse> {
  const response = await apiClient[method]<{
    code: 'OK'
    message: string
    data: PLMBillActionResponse
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

async function postPLMBillPage<TRecord>(
  url: string,
  search: ListQuerySearch
): Promise<PLMBillPage<TRecord>> {
  const response = await apiClient.post<{
    code: 'OK'
    message: string
    data: PLMBillPage<TRecord>
    requestId: string
  }>(url, toPaginationRequest(search))

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

async function getPLMDashboard(): Promise<PLMDashboardSummary> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: PLMDashboardSummary
    requestId: string
  }>('/plm/dashboard/summary')

  return unwrapResponse(response)
}

const PLM_BILL_ENDPOINTS: Record<PlmBusinessTypeCode, string> = {
  PLM_ECR: '/plm/ecrs',
  PLM_ECO: '/plm/ecos',
  PLM_MATERIAL: '/plm/material-master-changes',
}

function getPLMBillEndpoint(businessType: PlmBusinessTypeCode) {
  return PLM_BILL_ENDPOINTS[businessType]
}

function getPLMImplementationTaskActionPath(
  action: PLMImplementationTaskActionCode
) {
  switch (action) {
    case 'START':
      return 'start'
    case 'COMPLETE':
      return 'complete'
    case 'BLOCK':
      return 'block'
    case 'CANCEL':
      return 'cancel'
  }
}

function getPLMImplementationTaskEndpoint(
  businessType: PlmBusinessTypeCode,
  billId: string,
  taskId: string,
  action: PLMImplementationTaskActionCode
) {
  return `${getPLMBillEndpoint(businessType)}/${billId}/implementation-tasks/${taskId}/${getPLMImplementationTaskActionPath(action)}`
}

async function requestPLMImplementationTaskAction(
  businessType: PlmBusinessTypeCode,
  billId: string,
  taskId: string,
  action: PLMImplementationTaskActionCode
): Promise<PLMImplementationTaskActionResponse> {
  return requestPLMAction(
    'post',
    getPLMImplementationTaskEndpoint(businessType, billId, taskId, action),
    {}
  )
}

export async function createPLMECRRequest(
  payload: PLMECRRequestPayload
): Promise<PLMLaunchResponse> {
  return requestPLMAction(
    'post',
    '/plm/ecrs',
    payload
  ) as Promise<PLMLaunchResponse>
}

export async function createPLMECOExecution(
  payload: PLMECOExecutionPayload
): Promise<PLMLaunchResponse> {
  return requestPLMAction(
    'post',
    '/plm/ecos',
    payload
  ) as Promise<PLMLaunchResponse>
}

export async function createPLMMaterialChangeRequest(
  payload: PLMMaterialChangePayload
): Promise<PLMLaunchResponse> {
  return requestPLMAction(
    'post',
    '/plm/material-master-changes',
    payload
  ) as Promise<PLMLaunchResponse>
}

export async function getPLMDashboardSummary(): Promise<PLMDashboardSummary> {
  return getPLMDashboard()
}

export async function submitPLMECRDraft(
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction('post', `/plm/ecrs/${billId}/submit`, {})
}

export async function cancelPLMECRRequest(
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction('post', `/plm/ecrs/${billId}/cancel`, {})
}

export async function submitPLMECODraft(
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction('post', `/plm/ecos/${billId}/submit`, {})
}

export async function cancelPLMECOExecution(
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction('post', `/plm/ecos/${billId}/cancel`, {})
}

export async function submitPLMMaterialDraft(
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction(
    'post',
    `/plm/material-master-changes/${billId}/submit`,
    {}
  )
}

export async function cancelPLMMaterialChange(
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction(
    'post',
    `/plm/material-master-changes/${billId}/cancel`,
    {}
  )
}

export async function startPLMBusinessImplementation(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction(
    'post',
    `${getPLMBillEndpoint(businessType)}/${billId}/implementation`,
    {}
  )
}

export async function markPLMBusinessValidating(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction(
    'post',
    `${getPLMBillEndpoint(businessType)}/${billId}/validation`,
    {}
  )
}

export async function closePLMBusinessBill(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMBillActionResponse> {
  return requestPLMAction(
    'post',
    `${getPLMBillEndpoint(businessType)}/${billId}/close`,
    {}
  )
}

export async function performPLMImplementationTaskAction(
  businessType: PlmBusinessTypeCode,
  billId: string,
  taskId: string,
  action: PLMImplementationTaskActionCode
): Promise<PLMImplementationTaskActionResponse> {
  return requestPLMImplementationTaskAction(
    businessType,
    billId,
    taskId,
    action
  )
}

export async function getPLMECRRequestDetail(
  billId: string
): Promise<PLMBillDetail> {
  return getPLMBillDetail(`/plm/ecrs/${billId}`)
}

export async function listPLMECRRequests(
  search: ListQuerySearch
): Promise<PLMBillPage<PLMECRBillListItem>> {
  return postPLMBillPage('/plm/ecrs/page', search)
}

export async function getPLMECOExecutionDetail(
  billId: string
): Promise<PLMBillDetail> {
  return getPLMBillDetail(`/plm/ecos/${billId}`)
}

export async function listPLMECOExecutions(
  search: ListQuerySearch
): Promise<PLMBillPage<PLMECOBillListItem>> {
  return postPLMBillPage('/plm/ecos/page', search)
}

export async function getPLMMaterialChangeDetail(
  billId: string
): Promise<PLMBillDetail> {
  return getPLMBillDetail(`/plm/material-master-changes/${billId}`)
}

export async function listPLMMaterialChangeRequests(
  search: ListQuerySearch
): Promise<PLMBillPage<PLMMaterialChangeBillListItem>> {
  return postPLMBillPage('/plm/material-master-changes/page', search)
}

export async function listPLMApprovalSheets(
  search: ListQuerySearch
): Promise<PLMApprovalSheetPage> {
  return getPLMApprovalSheetPage(search)
}
