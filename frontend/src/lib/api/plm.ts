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

export type PLMBomNode = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  parentNodeId?: string | null
  objectId?: string | null
  nodeCode: string
  nodeName: string
  nodeType: string
  quantity?: number | null
  unit?: string | null
  effectivity?: string | null
  changeAction?: string | null
  hierarchyLevel?: number | null
  sortOrder?: number | null
}

export type PLMDocumentAsset = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  objectId?: string | null
  documentCode: string
  documentName: string
  documentType: string
  versionLabel?: string | null
  vaultState: string
  fileName?: string | null
  fileType?: string | null
  sourceSystem?: string | null
  externalRef?: string | null
  changeAction?: string | null
  sortOrder?: number | null
}

export type PLMPublicationActionResponse = {
  businessType: PlmBusinessTypeCode
  billId: string
  targetType: string
  targetId: string
  targetName: string
  status: string
  message: string
  actedAt?: string | null
}

export type PLMConfigurationBaselineItem = {
  id: string
  objectId?: string | null
  objectCode: string
  objectName: string
  objectType: string
  beforeRevisionCode?: string | null
  afterRevisionCode?: string | null
  effectivity?: string | null
  sortOrder?: number | null
}

export type PLMConfigurationBaseline = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  baselineCode: string
  baselineName: string
  baselineType: string
  status: string
  releasedAt?: string | null
  summaryJson?: string | null
  items: PLMConfigurationBaselineItem[]
}

export type PLMObjectAcl = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  objectId?: string | null
  objectCode?: string | null
  objectName?: string | null
  subjectType: string
  subjectCode: string
  permissionCode: string
  accessScope: string
  inherited?: boolean | null
  sortOrder?: number | null
}

export type PLMDomainAcl = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  domainCode: string
  roleCode: string
  permissionCode: string
  accessScope: string
  policySource: string
  sortOrder?: number | null
}

export type PLMRoleAssignment = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  roleCode: string
  roleLabel: string
  assigneeUserId?: string | null
  assigneeDisplayName?: string | null
  assignmentScope: string
  required: boolean
  status: string
  sortOrder?: number | null
}

export type PLMExternalSyncEvent = {
  id: string
  integrationId: string
  eventType: string
  status: string
  payloadJson?: string | null
  errorMessage?: string | null
  happenedAt?: string | null
  sortOrder?: number | null
}

export type PLMExternalIntegration = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  objectId?: string | null
  systemCode: string
  systemName: string
  directionCode: string
  integrationType: string
  status: string
  endpointKey?: string | null
  externalRef?: string | null
  lastSyncAt?: string | null
  message?: string | null
  sortOrder?: number | null
  events: PLMExternalSyncEvent[]
}

export type PLMExternalSyncEventEnvelope = {
  id: string
  integrationId: string
  businessType: PlmBusinessTypeCode
  billId: string
  systemCode: string
  systemName: string
  directionCode: string
  eventType: string
  status: string
  payloadJson?: string | null
  errorMessage?: string | null
  happenedAt?: string | null
  sortOrder?: number | null
}

export type PLMConnectorTaskReceipt = {
  id: string
  connectorTaskId: string
  receiptType: string
  receiptStatus: string
  receiptNo?: string | null
  acknowledgedAt?: string | null
  payloadSummary?: string | null
  payloadDetails?: string[]
  errorMessage?: string | null
  sortOrder?: number | null
}

export type PLMConnectorDispatchLog = {
  id: string
  connectorTaskId: string
  actionType: string
  status: string
  requestPayload?: string | null
  responsePayload?: string | null
  requestSummary?: string | null
  requestDetails?: string[]
  responseSummary?: string | null
  responseDetails?: string[]
  errorMessage?: string | null
  happenedAt?: string | null
  sortOrder?: number | null
}

export type PLMConnectorDispatchProfile = {
  mode?: string | null
  transport?: string | null
  endpointUrl?: string | null
  endpointPath?: string | null
  description?: string | null
}

export type PLMConnectorTask = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  connectorCode: string
  connectorName: string
  targetSystem: string
  directionCode?: string | null
  taskType: string
  status: string
  ownerUserId?: string | null
  ownerDisplayName?: string | null
  requestedAt?: string | null
  completedAt?: string | null
  externalRef?: string | null
  payloadSummary?: string | null
  payloadDetails?: string[]
  dispatchProfile?: PLMConnectorDispatchProfile | null
  dispatchLogs: PLMConnectorDispatchLog[]
  receipts: PLMConnectorTaskReceipt[]
}

export type PLMImplementationDependency = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  dependencyType: string
  upstreamTaskNo?: string | null
  upstreamTitle?: string | null
  status: string
  blocking?: boolean | null
  dueAt?: string | null
  note?: string | null
  sortOrder?: number | null
}

export type PLMImplementationEvidence = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  evidenceType: string
  title: string
  status: string
  ownerUserId?: string | null
  ownerDisplayName?: string | null
  collectedAt?: string | null
  externalRef?: string | null
  summary?: string | null
  sortOrder?: number | null
}

export type PLMAcceptanceCheckpoint = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  checkpointCode: string
  checkpointName: string
  status: string
  required: boolean
  ownerUserId?: string | null
  ownerDisplayName?: string | null
  completedAt?: string | null
  summary?: string | null
  sortOrder?: number | null
}

export type PLMImplementationWorkspace = {
  dependencies: PLMImplementationDependency[]
  evidences: PLMImplementationEvidence[]
  acceptanceCheckpoints: PLMAcceptanceCheckpoint[]
}

export type PLMImplementationEvidenceCreatePayload = {
  evidenceType: string
  evidenceName: string
  evidenceRef?: string
  evidenceSummary?: string
}

export type PLMAcceptanceChecklistUpdatePayload = {
  status: string
  resultSummary?: string
}

type RawPLMConnectorDispatchLog = {
  id: string
  jobId: string
  actionType: string
  status: string
  requestPayloadJson?: string | null
  responsePayloadJson?: string | null
  errorMessage?: string | null
  happenedAt?: string | null
  sortOrder?: number | null
}

type RawPLMConnectorAck = {
  id: string
  jobId: string
  ackStatus: string
  ackCode?: string | null
  externalRef?: string | null
  message?: string | null
  payloadJson?: string | null
  sourceSystem?: string | null
  happenedAt?: string | null
  sortOrder?: number | null
}

type RawPLMConnectorJob = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  connectorRegistryId?: string | null
  connectorCode: string
  systemCode: string
  systemName: string
  directionCode?: string | null
  jobType: string
  status: string
  requestPayloadJson?: string | null
  externalRef?: string | null
  retryCount?: number | null
  nextRunAt?: string | null
  lastDispatchedAt?: string | null
  lastAckAt?: string | null
  lastError?: string | null
  createdBy?: string | null
  sortOrder?: number | null
  dispatchLogs?: RawPLMConnectorDispatchLog[]
  acknowledgements?: RawPLMConnectorAck[]
}

type ConnectorPayloadEnvelope = {
  businessType?: string
  billId?: string
  jobType?: string
  summaryMessage?: string
  connectorCode?: string
  systemCode?: string
  systemName?: string
  endpointKey?: string
  dispatchProfile?: {
    mode?: string
    transport?: string
    endpointUrl?: string
    endpointPath?: string
    description?: string
  }
  bill?: {
    billNo?: string
    title?: string
    status?: string
    sceneCode?: string
    creatorUserId?: string
    effectiveDate?: string
  }
  affectedData?: {
    objectLinkCount?: number
    bomNodeCount?: number
    documentCount?: number
    baselineCount?: number
    objects?: Array<Record<string, unknown>>
    bomHighlights?: Array<Record<string, unknown>>
    documents?: Array<Record<string, unknown>>
    baselines?: Array<Record<string, unknown>>
  }
  implementation?: {
    taskCount?: number
    pendingTaskCount?: number
    blockedTaskCount?: number
    verificationTaskCount?: number
    requiredEvidenceCount?: number
    evidenceCount?: number
    acceptancePendingCount?: number
    tasks?: Array<Record<string, unknown>>
  }
  systemPayload?: {
    intent?: string
    summary?: string
    [key: string]: unknown
  }
}

type RawPLMImplementationEvidence = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  taskId: string
  evidenceType: string
  evidenceName: string
  evidenceRef?: string | null
  evidenceSummary?: string | null
  uploadedBy?: string | null
  createdAt?: string | null
}

type RawPLMAcceptanceCheckpoint = {
  id: string
  businessType: PlmBusinessTypeCode
  billId: string
  checkCode: string
  checkName: string
  requiredFlag: boolean
  status: string
  resultSummary?: string | null
  checkedBy?: string | null
  checkedAt?: string | null
  sortOrder?: number | null
}

type RawPLMImplementationWorkspace = {
  dependencies: PLMImplementationDependency[]
  evidences: RawPLMImplementationEvidence[]
  acceptanceCheckpoints: RawPLMAcceptanceCheckpoint[]
}

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

export type PLMDashboardCockpit = {
  stuckSyncItems?: Array<{
    id: string
    billId?: string | null
    billNo?: string | null
    businessType?: PlmBusinessTypeCode | null
    businessTitle?: string | null
    systemCode?: string | null
    systemName?: string | null
    connectorName?: string | null
    status: string
    pendingCount?: number | null
    failedCount?: number | null
    ownerDisplayName?: string | null
    summary?: string | null
    updatedAt?: string | null
  }>
  closeBlockerItems?: Array<{
    id: string
    billId?: string | null
    billNo?: string | null
    businessType?: PlmBusinessTypeCode | null
    businessTitle?: string | null
    blockerType: string
    blockerTitle?: string | null
    blockerCount?: number | null
    ownerDisplayName?: string | null
    summary?: string | null
    dueAt?: string | null
  }>
  failedSystemHotspots?: Array<{
    systemCode: string
    systemName?: string | null
    failedCount?: number | null
    pendingCount?: number | null
    blockedBillCount?: number | null
    summary?: string | null
  }>
  objectTypeDistribution: Array<{
    code: string
    label?: string | null
    totalCount: number
  }>
  domainDistribution: Array<{
    code: string
    label?: string | null
    totalCount: number
  }>
  baselineStatusDistribution: Array<{
    code: string
    label?: string | null
    totalCount: number
  }>
  integrationSystemDistribution: Array<{
    code: string
    label?: string | null
    totalCount: number
  }>
  integrationStatusDistribution: Array<{
    code: string
    label?: string | null
    totalCount: number
  }>
  blockedTaskCount: number
  overdueTaskCount: number
  readyToCloseCount: number
  pendingIntegrationCount: number
  failedSyncEventCount: number
  roleCoverageRate: number
  averageClosureHours: number
  connectorTaskBacklogCount?: number
  pendingReceiptCount?: number
  implementationHealthyRate?: number
  acceptanceDueCount?: number
  connectorStatusDistribution?: Array<{
    code: string
    label?: string | null
    totalCount: number
  }>
  implementationHealthDistribution?: Array<{
    code: string
    label?: string | null
    totalCount: number
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

async function getPLMDashboardCockpitInternal(): Promise<PLMDashboardCockpit> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: PLMDashboardCockpit
    requestId: string
  }>('/plm/dashboard/cockpit')

  return unwrapResponse(response)
}

async function getPLMBillResource<T>(url: string): Promise<T> {
  const response = await apiClient.get<{
    code: 'OK'
    message: string
    data: T
    requestId: string
  }>(url)

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

export async function getPLMDashboardCockpit(): Promise<PLMDashboardCockpit> {
  return getPLMDashboardCockpitInternal()
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

function parseConnectorPayloadEnvelope(
  value?: string | null
): ConnectorPayloadEnvelope | null {
  if (!value) {
    return null
  }
  try {
    return JSON.parse(value) as ConnectorPayloadEnvelope
  } catch {
    return null
  }
}

function compactDetails(values: Array<string | null | undefined>): string[] {
  return values.filter((value): value is string => Boolean(value && value.trim()))
}

function formatConnectorIntent(intent?: string | null) {
  switch (intent) {
    case 'MASTER_DATA_SYNC':
      return '主数据同步'
    case 'ROLL_OUT_SYNC':
      return '实施下发'
    case 'DOCUMENT_RELEASE':
      return '文档发布'
    case 'DRAWING_PUBLISH':
      return '图档发布'
    default:
      return intent ?? '系统同步'
  }
}

function buildConnectorTaskOverview(job: RawPLMConnectorJob) {
  const envelope = parseConnectorPayloadEnvelope(job.requestPayloadJson)
  const profile = envelope?.dispatchProfile
  const bill = envelope?.bill
  const affectedData = envelope?.affectedData
  const implementation = envelope?.implementation
  const systemPayload = envelope?.systemPayload

  const payloadSummary =
    systemPayload?.summary ??
    envelope?.summaryMessage ??
    `${job.systemName} ${formatConnectorIntent(systemPayload?.intent)}`

  const payloadDetails = compactDetails([
    bill?.title ? `单据：${bill.title}` : null,
    bill?.billNo ? `编号：${bill.billNo}` : null,
    affectedData?.objectLinkCount != null
      ? `受影响对象 ${affectedData.objectLinkCount} 个`
      : null,
    affectedData?.baselineCount != null
      ? `基线 ${affectedData.baselineCount} 组`
      : null,
    affectedData?.documentCount != null
      ? `文档 ${affectedData.documentCount} 份`
      : null,
    implementation?.taskCount != null
      ? `实施任务 ${implementation.taskCount} 项`
      : null,
    implementation?.blockedTaskCount
      ? `阻塞 ${implementation.blockedTaskCount} 项`
      : null,
  ])

  return {
    payloadSummary,
    payloadDetails,
    dispatchProfile: profile
      ? {
          mode: profile.mode ?? null,
          transport: profile.transport ?? null,
          endpointUrl: profile.endpointUrl ?? null,
          endpointPath: profile.endpointPath ?? null,
          description: profile.description ?? null,
        }
      : null,
  }
}

function buildDispatchLogOverview(log: RawPLMConnectorDispatchLog) {
  const requestEnvelope = parseConnectorPayloadEnvelope(log.requestPayloadJson)
  const responsePayload = parseConnectorPayloadEnvelope(log.responsePayloadJson)
  const responseRecord = parseConnectorPayloadEnvelope(log.responsePayloadJson) as
    | Record<string, unknown>
    | null

  const requestSummary =
    requestEnvelope?.systemPayload?.summary ??
    requestEnvelope?.summaryMessage ??
    null
  const requestDetails = compactDetails([
    requestEnvelope?.bill?.billNo
      ? `单据：${requestEnvelope.bill.billNo}`
      : null,
    requestEnvelope?.dispatchProfile?.transport
      ? `传输：${requestEnvelope.dispatchProfile.transport}`
      : null,
    requestEnvelope?.dispatchProfile?.endpointUrl
      ? `目标：${requestEnvelope.dispatchProfile.endpointUrl}${requestEnvelope.dispatchProfile.endpointPath ?? ''}`
      : null,
  ])

  const responseSummary =
    typeof responseRecord?.message === 'string'
      ? responseRecord.message
      : responsePayload?.systemPayload?.summary ?? null
  const responseDetails = compactDetails([
    typeof responseRecord?.mode === 'string' ? `模式：${responseRecord.mode}` : null,
    typeof responseRecord?.transport === 'string'
      ? `链路：${responseRecord.transport}`
      : null,
    typeof responseRecord?.endpointUrl === 'string'
      ? `端点：${responseRecord.endpointUrl}${typeof responseRecord?.endpointPath === 'string' ? responseRecord.endpointPath : ''}`
      : null,
    typeof responseRecord?.handlerKey === 'string'
      ? `处理器：${responseRecord.handlerKey}`
      : null,
  ])

  return {
    requestSummary,
    requestDetails,
    responseSummary,
    responseDetails,
  }
}

function buildReceiptOverview(ack: RawPLMConnectorAck) {
  const payload = parseConnectorPayloadEnvelope(ack.payloadJson) as
    | Record<string, unknown>
    | null
  const payloadSummary =
    ack.message ??
    (typeof payload?.message === 'string' ? payload.message : null) ??
    ack.payloadJson
  const payloadDetails = compactDetails([
    ack.sourceSystem ? `来源：${ack.sourceSystem}` : null,
    ack.ackCode ? `回执码：${ack.ackCode}` : null,
    ack.idempotencyKey ? `幂等键：${ack.idempotencyKey}` : null,
  ])
  return {
    payloadSummary,
    payloadDetails,
  }
}

function mapConnectorJob(job: RawPLMConnectorJob): PLMConnectorTask {
  const taskOverview = buildConnectorTaskOverview(job)
  return {
    id: job.id,
    businessType: job.businessType,
    billId: job.billId,
    connectorCode: job.connectorCode,
    connectorName: job.systemName,
    targetSystem: job.systemCode,
    directionCode: job.directionCode,
    taskType: job.jobType,
    status: job.status,
    ownerUserId: job.createdBy,
    ownerDisplayName: job.createdBy,
    requestedAt: job.nextRunAt,
    completedAt: job.lastAckAt ?? job.lastDispatchedAt,
    externalRef: job.externalRef,
    payloadSummary: taskOverview.payloadSummary,
    payloadDetails: taskOverview.payloadDetails,
    dispatchProfile: taskOverview.dispatchProfile,
    dispatchLogs: (job.dispatchLogs ?? []).map((log) => {
      const logOverview = buildDispatchLogOverview(log)
      return {
        id: log.id,
        connectorTaskId: log.jobId,
        actionType: log.actionType,
        status: log.status,
        requestPayload: log.requestPayloadJson,
        responsePayload: log.responsePayloadJson,
        requestSummary: logOverview.requestSummary,
        requestDetails: logOverview.requestDetails,
        responseSummary: logOverview.responseSummary,
        responseDetails: logOverview.responseDetails,
        errorMessage: log.errorMessage,
        happenedAt: log.happenedAt,
        sortOrder: log.sortOrder,
      }
    }),
    receipts: (job.acknowledgements ?? []).map((ack) => {
      const overview = buildReceiptOverview(ack)
      return {
        id: ack.id,
        connectorTaskId: ack.jobId,
        receiptType: ack.ackCode ?? ack.sourceSystem ?? 'ACK',
        receiptStatus: ack.ackStatus,
        receiptNo: ack.externalRef,
        acknowledgedAt: ack.happenedAt,
        payloadSummary: overview.payloadSummary,
        payloadDetails: overview.payloadDetails,
        errorMessage: ack.ackStatus === 'FAILED' ? ack.message : undefined,
        sortOrder: ack.sortOrder,
      }
    }),
  }
}

export async function dispatchPLMConnectorTask(
  jobId: string
): Promise<PLMConnectorTask> {
  const job = await requestPLMAction<RawPLMConnectorJob>(
    'post',
    `/plm/connector-jobs/${jobId}/dispatch`,
    {}
  )

  return mapConnectorJob(job)
}

export async function retryPLMConnectorTask(
  jobId: string
): Promise<PLMConnectorTask> {
  const job = await requestPLMAction<RawPLMConnectorJob>(
    'post',
    `/plm/connector-jobs/${jobId}/retry`,
    {}
  )

  return mapConnectorJob(job)
}

export async function addPLMImplementationEvidence(
  businessType: PlmBusinessTypeCode,
  billId: string,
  taskId: string,
  payload: PLMImplementationEvidenceCreatePayload
): Promise<PLMImplementationEvidence> {
  const item = await requestPLMAction<RawPLMImplementationEvidence>(
    'post',
    `${getPLMBillEndpoint(businessType)}/${billId}/implementation-tasks/${taskId}/evidence`,
    payload
  )

  return {
    id: item.id,
    businessType: item.businessType,
    billId: item.billId,
    evidenceType: item.evidenceType,
    title: item.evidenceName,
    status: 'COLLECTED',
    ownerUserId: item.uploadedBy,
    ownerDisplayName: item.uploadedBy,
    collectedAt: item.createdAt,
    externalRef: item.evidenceRef,
    summary: item.evidenceSummary,
    sortOrder: item.sortOrder ?? null,
  }
}

export async function updatePLMAcceptanceChecklist(
  businessType: PlmBusinessTypeCode,
  billId: string,
  checklistId: string,
  payload: PLMAcceptanceChecklistUpdatePayload
): Promise<PLMAcceptanceCheckpoint> {
  const item = await requestPLMAction<RawPLMAcceptanceCheckpoint>(
    'put',
    `${getPLMBillEndpoint(businessType)}/${billId}/acceptance-checklist/${checklistId}`,
    payload
  )

  return {
    id: item.id,
    businessType: item.businessType,
    billId: item.billId,
    checkpointCode: item.checkCode,
    checkpointName: item.checkName,
    status: item.status === 'ACCEPTED' ? 'COMPLETED' : item.status,
    required: item.requiredFlag,
    ownerUserId: item.checkedBy,
    ownerDisplayName: item.checkedBy,
    completedAt: item.checkedAt,
    summary: item.resultSummary,
    sortOrder: item.sortOrder ?? null,
  }
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

export async function listPLMBomNodes(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMBomNode[]> {
  return getPLMBillResource(
    `/plm/bills/${businessType}/${billId}/bom-nodes`
  )
}

export async function listPLMDocumentAssets(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMDocumentAsset[]> {
  return getPLMBillResource(
    `/plm/bills/${businessType}/${billId}/document-assets`
  )
}

export async function listPLMConfigurationBaselines(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMConfigurationBaseline[]> {
  return getPLMBillResource(`/plm/bills/${businessType}/${billId}/baselines`)
}

export async function releasePLMConfigurationBaseline(
  businessType: PlmBusinessTypeCode,
  billId: string,
  baselineId: string,
  payload?: { summaryMessage?: string }
): Promise<PLMPublicationActionResponse> {
  const { data } = await apiClient.put<ApiResponse<PLMPublicationActionResponse>>(
    `/plm/bills/${businessType}/${billId}/baselines/${baselineId}/release`,
    payload ?? {}
  )
  return data.data
}

export async function releasePLMDocumentAsset(
  businessType: PlmBusinessTypeCode,
  billId: string,
  assetId: string,
  payload?: { summaryMessage?: string }
): Promise<PLMPublicationActionResponse> {
  const { data } = await apiClient.put<ApiResponse<PLMPublicationActionResponse>>(
    `/plm/bills/${businessType}/${billId}/document-assets/${assetId}/release`,
    payload ?? {}
  )
  return data.data
}

export async function listPLMObjectAcl(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMObjectAcl[]> {
  return getPLMBillResource(`/plm/bills/${businessType}/${billId}/acl`)
}

export async function listPLMDomainAcl(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMDomainAcl[]> {
  return getPLMBillResource(`/plm/bills/${businessType}/${billId}/domain-acl`)
}

export async function listPLMRoleAssignments(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMRoleAssignment[]> {
  return getPLMBillResource(`/plm/bills/${businessType}/${billId}/role-matrix`)
}

export async function listPLMExternalIntegrations(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMExternalIntegration[]> {
  return getPLMBillResource(
    `/plm/bills/${businessType}/${billId}/external-integrations`
  )
}

export async function listPLMExternalSyncEvents(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMExternalSyncEventEnvelope[]> {
  return getPLMBillResource(
    `/plm/bills/${businessType}/${billId}/external-sync-events`
  )
}

export async function listPLMConnectorTasks(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMConnectorTask[]> {
  const jobs = await getPLMBillResource<RawPLMConnectorJob[]>(
    `/plm/bills/${businessType}/${billId}/connector-tasks`
  )
  return jobs.map(mapConnectorJob)
}

export async function getPLMImplementationWorkspace(
  businessType: PlmBusinessTypeCode,
  billId: string
): Promise<PLMImplementationWorkspace> {
  const workspace = await getPLMBillResource<RawPLMImplementationWorkspace>(
    `/plm/bills/${businessType}/${billId}/implementation-workspace`
  )
  return {
    dependencies: workspace.dependencies ?? [],
    evidences: (workspace.evidences ?? []).map((item) => ({
      id: item.id,
      businessType: item.businessType,
      billId: item.billId,
      evidenceType: item.evidenceType,
      title: item.evidenceName,
      status: 'COLLECTED',
      ownerUserId: item.uploadedBy,
      ownerDisplayName: item.uploadedBy,
      collectedAt: item.createdAt,
      externalRef: item.evidenceRef,
      summary: item.evidenceSummary,
      sortOrder: item.sortOrder ?? null,
    })),
    acceptanceCheckpoints: (workspace.acceptanceCheckpoints ?? []).map(
      (item) => ({
        id: item.id,
        businessType: item.businessType,
        billId: item.billId,
        checkpointCode: item.checkCode,
        checkpointName: item.checkName,
        status: item.status === 'ACCEPTED' ? 'COMPLETED' : item.status,
        required: item.requiredFlag,
        ownerUserId: item.checkedBy,
        ownerDisplayName: item.checkedBy,
        completedAt: item.checkedAt,
        summary: item.resultSummary,
        sortOrder: item.sortOrder ?? null,
      })
    ),
  }
}
