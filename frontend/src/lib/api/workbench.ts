import { apiClient, unwrapResponse } from '@/lib/api/client'
import { type ListQuerySearch, toPaginationRequest } from '@/features/shared/table/query-contract'
import { type WorkflowFieldBinding } from '@/features/workflow/designer/types'
import type { RuntimeStructureLink } from '@/features/workflow/runtime-structure-utils'

// 命名空间默认不带前缀，直接命中后端新 runtime 路径；如需切旧环境可通过环境变量覆盖。
const DEFAULT_RUNTIME_NAMESPACE = (import.meta.env.VITE_PROCESS_RUNTIME_NAMESPACE ?? '').trim()

type ProcessRuntimeNamespace = string

export const WORKBENCH_RUNTIME_NAMESPACE: ProcessRuntimeNamespace = DEFAULT_RUNTIME_NAMESPACE

export type WorkbenchRuntimeResource = 'tasks' | 'approval-sheets' | 'instances'

export type WorkbenchListPayload<T> = {
  page: T extends { page: number } ? number : never
}

export type WorkbenchProcessInstance = {
  instanceId: string
  processDefinitionId: string
  processKey: string
  processName: string
  instanceStatus: string
  createdAt: string
  updatedAt: string
  completedAt: string | null
  businessType?: string | null
  businessId?: string | null
}

export type WorkbenchTaskRuntimeSummary = {
  taskId: string
  instanceId: string
  instanceStatus: string
  nodeId: string
  nodeName: string
  status: WorkbenchTaskListItem['status']
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type WorkbenchDashboardSummary = {
  todoTodayCount: number
  doneApprovalCount: number
  highRiskTodoCount?: number
  overdueTodayCount?: number
  riskDistribution?: Record<string, number>
  overdueTrend?: Array<{ date: string; count: number }>
  bottleneckNodes?: Array<{
    nodeId: string
    nodeName: string
    totalCount: number
    highRiskCount: number
    medianRemainingDurationMinutes?: number | null
  }>
  topRiskProcesses?: Array<{
    processKey: string
    processName: string
    totalCount: number
    highRiskCount: number
    highRiskRate: number
  }>
}

export type WorkbenchReviewTicket = {
  ticket: string
  expiresAt: string
}

export type WorkbenchHistoryItem = WorkbenchTaskTraceItem

export type WorkbenchTaskListItem = {
  taskId: string
  instanceId: string
  processDefinitionId: string
  processKey: string
  processName: string
  businessKey: string | null
  businessType: string | null
  applicantUserId: string
  nodeId: string
  nodeName: string
  status:
    | 'PENDING_CLAIM'
    | 'PENDING'
    | 'COMPLETED'
    | 'TRANSFERRED'
    | 'RETURNED'
    | 'DELEGATED'
    | 'HANDOVERED'
  assignmentMode: string | null
  candidateUserIds: string[]
  candidateGroupIds?: string[]
  assigneeUserId: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
  prediction?: WorkbenchProcessPrediction | null
}

export type WorkbenchFlowNode = {
  id: string
  type: string
  name: string
  position: {
    x: number
    y: number
  }
  config?: Record<string, unknown> | null
  ui?: {
    width?: number | null
    height?: number | null
  } | null
}

export type WorkbenchFlowEdge = {
  id: string
  source: string
  target: string
  priority?: number | null
  label?: string | null
}

export type WorkbenchProcessInstanceEvent = {
  eventId: string
  instanceId: string
  taskId?: string | null
  nodeId?: string | null
  eventType: string
  eventName: string
  actionCategory?: string | null
  sourceTaskId?: string | null
  targetTaskId?: string | null
  targetUserId?: string | null
  operatorUserId?: string | null
  occurredAt: string
  details?: Record<string, unknown> | null
  targetStrategy?: string | null
  targetNodeId?: string | null
  reapproveStrategy?: string | null
  actingMode?: string | null
  actingForUserId?: string | null
  delegatedByUserId?: string | null
  handoverFromUserId?: string | null
  slaMetadata?: Record<string, unknown> | null
  signatureType?: string | null
  signatureStatus?: string | null
  signatureComment?: string | null
  signatureAt?: string | null
}

export type WorkbenchTaskTraceItem = {
  taskId: string
  nodeId: string
  nodeName: string
  taskKind?: string | null
  taskSemanticMode?: string | null
  status: string
  assigneeUserId?: string | null
  candidateUserIds: string[]
  candidateGroupIds?: string[]
  action?: string | null
  operatorUserId?: string | null
  comment?: string | null
  receiveTime?: string | null
  readTime?: string | null
  handleStartTime?: string | null
  handleEndTime?: string | null
  handleDurationSeconds?: number | null
  sourceTaskId?: string | null
  targetTaskId?: string | null
  targetUserId?: string | null
  targetStrategy?: string | null
  targetNodeId?: string | null
  targetNodeName?: string | null
  reapproveStrategy?: string | null
  actingMode?: string | null
  actingForUserId?: string | null
  delegatedByUserId?: string | null
  handoverFromUserId?: string | null
  isCcTask?: boolean
  isAddSignTask?: boolean
  isRevoked?: boolean
  isRejected?: boolean
  isJumped?: boolean
  isTakenBack?: boolean
  slaMetadata?: Record<string, unknown> | null
}

export type WorkbenchCountersignGroupMember = {
  memberId: string
  taskId: string | null
  assigneeUserId: string
  sequenceNo: number
  voteWeight: number | null
  memberStatus: string
}

export type WorkbenchCountersignGroup = {
  groupId: string
  instanceId: string
  nodeId: string
  nodeName: string
  approvalMode: string
  groupStatus: string
  totalCount: number
  completedCount: number
  activeCount: number
  waitingCount: number
  voteThresholdPercent: number | null
  approvedWeight: number | null
  rejectedWeight: number | null
  decisionStatus: string | null
  members: WorkbenchCountersignGroupMember[]
}

export type WorkbenchInclusiveGatewayHit = {
  splitNodeId: string
  splitNodeName: string
  joinNodeId: string | null
  joinNodeName: string | null
  defaultBranchId: string | null
  requiredBranchCount: number | null
  branchMergePolicy: string
  gatewayStatus: string
  totalTargetCount: number
  eligibleTargetCount: number
  activatedTargetCount: number
  activatedTargetNodeIds: string[]
  activatedTargetNodeNames: string[]
  skippedTargetNodeIds: string[]
  skippedTargetNodeNames: string[]
  branchPriorities: number[]
  branchLabels: string[]
  branchExpressions: string[]
  selectedEdgeIds: string[]
  selectedBranchLabels: string[]
  selectedBranchPriorities: number[]
  selectedDecisionReasons: string[]
  defaultBranchSelected: boolean
  decisionSummary: string
  firstActivatedAt: string | null
  finishedAt: string | null
}

export type WorkbenchProcessLink = {
  linkId: string
  rootInstanceId: string
  parentInstanceId: string
  childInstanceId: string
  parentNodeId: string
  calledProcessKey: string
  calledDefinitionId: string
  linkType: string
  status: string
  terminatePolicy: string | null
  childFinishPolicy: string | null
  createdAt: string | null
  finishedAt: string | null
} & RuntimeStructureLink

export type WorkbenchProcessPredictionNextNodeCandidate = {
  nodeId: string
  nodeName: string
  probability: number
  hitCount: number
  medianDurationMinutes?: number | null
  riskWeight?: number | null
  sortOrder?: number | null
  pathConfidence?: 'LOW' | 'MEDIUM' | 'HIGH' | string | null
}

export type WorkbenchProcessPredictionAutomationAction = {
  actionType: string
  mode: string
  status: string
  title: string
  detail: string
}

export type WorkbenchProcessPredictionFeatureSnapshot = {
  processKey?: string | null
  currentNodeId?: string | null
  businessType?: string | null
  assigneeUserId?: string | null
  organizationProfile?: string | null
  workingDayProfile?: string | null
  sampleTier?: string | null
  rawSampleSize: number
  filteredSampleSize: number
}

export type WorkbenchProcessPrediction = {
  predictedFinishTime?: string | null
  predictedRiskThresholdTime?: string | null
  remainingDurationMinutes?: number | null
  currentElapsedMinutes?: number | null
  currentNodeDurationP50Minutes?: number | null
  currentNodeDurationP75Minutes?: number | null
  currentNodeDurationP90Minutes?: number | null
  overdueRiskLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | string | null
  confidence?: 'LOW' | 'MEDIUM' | 'HIGH' | string | null
  historicalSampleSize: number
  outlierFilteredSampleSize?: number | null
  sampleProfile?: string | null
  sampleTier?: string | null
  workingDayProfile?: string | null
  organizationProfile?: string | null
  basisSummary?: string | null
  noPredictionReason?: string | null
  explanation?: string | null
  narrativeExplanation?: string | null
  bottleneckAttribution?: string | null
  topDelayReasons?: string[] | null
  recommendedActions?: string[] | null
  optimizationSuggestions?: string[] | null
  automationActions?: WorkbenchProcessPredictionAutomationAction[] | null
  featureSnapshot?: WorkbenchProcessPredictionFeatureSnapshot | null
  nextNodeCandidates?: WorkbenchProcessPredictionNextNodeCandidate[] | null
}

export type WorkbenchRuntimeAppendLink = RuntimeStructureLink & {
  appendType: 'TASK' | 'SUBPROCESS'
  runtimeLinkType: 'ADHOC_TASK' | 'ADHOC_SUBPROCESS'
  triggerMode: 'APPEND' | 'DYNAMIC_BUILD'
}

export type WorkbenchTaskDetail = WorkbenchTaskListItem & {
  action: string | null
  operatorUserId: string | null
  comment: string | null
  instanceStatus: string
  initiatorPostId?: string | null
  initiatorPostName?: string | null
  initiatorDepartmentId?: string | null
  initiatorDepartmentName?: string | null
  automationStatus?: string | null
  formData: Record<string, unknown>
  businessData?: Record<string, unknown> | null
  flowNodes?: WorkbenchFlowNode[] | null
  flowEdges?: WorkbenchFlowEdge[] | null
  instanceEvents?: WorkbenchProcessInstanceEvent[] | null
  taskTrace?: WorkbenchTaskTraceItem[] | null
  automationActionTrace?: WorkbenchAutomationActionTraceItem[] | null
  notificationSendRecords?: WorkbenchNotificationSendRecord[] | null
  taskKind?: string | null
  taskSemanticMode?: string | null
  actingMode?: string | null
  actingForUserId?: string | null
  delegatedByUserId?: string | null
  handoverFromUserId?: string | null
  receiveTime?: string | null
  readTime?: string | null
  handleStartTime?: string | null
  handleEndTime?: string | null
  handleDurationSeconds?: number | null
  processFormKey: string
  processFormVersion: string
  effectiveFormKey: string
  effectiveFormVersion: string
  nodeFormKey: string | null
  nodeFormVersion: string | null
  fieldBindings: WorkflowFieldBinding[]
  taskFormData: Record<string, unknown> | null
  countersignGroups?: WorkbenchCountersignGroup[] | null
  inclusiveGatewayHits?: WorkbenchInclusiveGatewayHit[] | null
  processLinks?: WorkbenchProcessLink[] | null
  runtimeStructureLinks?: WorkbenchRuntimeAppendLink[] | null
  activeTaskIds: string[]
  prediction?: WorkbenchProcessPrediction | null
  userDisplayNames?: Record<string, string> | null
  groupDisplayNames?: Record<string, string> | null
}

export type WorkbenchAutomationActionTraceItem = {
  traceId: string
  traceType: string
  traceName: string
  status: string
  operatorUserId?: string | null
  occurredAt: string
  detail?: string | null
  nodeId?: string | null
  slaMetadata?: Record<string, unknown> | null
}

export type WorkbenchNotificationSendRecord = {
  recordId: string
  channelName: string
  channelType: string
  target: string
  status: string
  attemptCount: number
  sentAt?: string | null
  errorMessage?: string | null
}

export type ApprovalSheetBusinessLocator = {
  businessType: string
  businessId: string
}

export type ApprovalSheetListView = 'TODO' | 'DONE' | 'INITIATED' | 'CC'

export type ApprovalSheetListItem = {
  instanceId: string
  processDefinitionId: string
  processKey: string
  processName: string
  businessId: string | null
  businessType: string | null
  billNo: string | null
  businessTitle: string | null
  initiatorUserId: string
  initiatorDisplayName?: string | null
  initiatorPostId?: string | null
  initiatorPostName?: string | null
  initiatorDepartmentId?: string | null
  initiatorDepartmentName?: string | null
  currentNodeName: string | null
  currentTaskId: string | null
  currentTaskStatus: string | null
  currentAssigneeUserId: string | null
  instanceStatus: string
  latestAction: string | null
  latestOperatorUserId: string | null
  automationStatus?: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type WorkbenchTaskActionAvailability = {
  canClaim: boolean
  canApprove: boolean
  canReject: boolean
  canRejectRoute: boolean
  canTransfer: boolean
  canDelegate: boolean
  canReturn: boolean
  canAddSign: boolean
  canRemoveSign: boolean
  canRevoke: boolean
  canUrge: boolean
  canRead: boolean
  canJump: boolean
  canTakeBack: boolean
  canWakeUp: boolean
  canSign: boolean
}

export type ApprovalSheetPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: ApprovalSheetListItem[]
  groups: Array<{ field: string; value: string }>
}

export type ClaimWorkbenchTaskPayload = {
  comment?: string | null
}

export type ClaimWorkbenchTaskResponse = {
  taskId: string
  status: 'PENDING'
  assigneeUserId: string
}

export type TransferWorkbenchTaskPayload = {
  targetUserId: string
  comment?: string | null
}

export type ReturnWorkbenchTaskPayload = {
  targetStrategy: 'PREVIOUS_USER_TASK' | 'INITIATOR' | 'ANY_USER_TASK'
  targetTaskId?: string | null
  targetNodeId?: string | null
  comment?: string | null
}

export type AddSignWorkbenchTaskPayload = {
  targetUserId: string
  comment?: string | null
}

export type RemoveSignWorkbenchTaskPayload = {
  targetTaskId: string
  comment?: string | null
}

export type RevokeWorkbenchTaskPayload = {
  comment?: string | null
}

export type UrgeWorkbenchTaskPayload = {
  comment?: string | null
}

export type RejectWorkbenchTaskPayload = {
  targetStrategy: 'PREVIOUS_USER_TASK' | 'INITIATOR' | 'ANY_USER_TASK'
  targetTaskId?: string | null
  targetNodeId?: string | null
  reapproveStrategy: 'CONTINUE' | 'RETURN_TO_REJECTED_NODE'
  comment?: string | null
}

export type JumpWorkbenchTaskPayload = {
  targetNodeId: string
  comment?: string | null
}

export type TakeBackWorkbenchTaskPayload = {
  comment?: string | null
}

export type WakeUpWorkbenchInstancePayload = {
  sourceTaskId: string
  comment?: string | null
}

export type DelegateWorkbenchTaskPayload = {
  targetUserId: string
  comment?: string | null
}

export type SignWorkbenchTaskPayload = {
  signatureType: string
  signatureComment?: string | null
}

export type SignWorkbenchTaskResponse = {
  taskId: string
  instanceId: string
  nodeId: string
  signatureType: string
  signatureStatus: string
  signatureComment?: string | null
  signatureAt: string
  operatorUserId: string
}

export type HandoverWorkbenchTasksPayload = {
  sourceUserId: string
  targetUserId: string
  comment?: string | null
}

export type HandoverWorkbenchTasksResponse = {
  sourceUserId: string
  targetUserId: string
  transferredCount: number
  transferredTaskIds: string[]
  status: string
}

export type BatchWorkbenchTaskPayload = {
  taskIds: string[]
  action?: string | null
  operatorUserId?: string | null
  comment?: string | null
  taskFormData?: Record<string, unknown> | null
  targetStrategy?: 'PREVIOUS_USER_TASK' | 'INITIATOR' | 'ANY_USER_TASK' | null
  targetTaskId?: string | null
  targetNodeId?: string | null
  reapproveStrategy?: 'CONTINUE' | 'RETURN_TO_REJECTED_NODE' | null
}

export type BatchWorkbenchTaskResponse = {
  action: string
  totalCount: number
  successCount: number
  failureCount: number
  items: Array<{
    taskId: string
    instanceId: string | null
    success: boolean
    code: string
    status: string
    message: string
    completedTaskId?: string | null
    assigneeUserId?: string | null
    targetStrategy?: string | null
    targetNodeId?: string | null
    reapproveStrategy?: string | null
    nextTasks: Array<{
      taskId: string
      nodeId: string
      nodeName: string
      status: string
      assignmentMode: string | null
      candidateUserIds: string[]
      candidateGroupIds?: string[]
      assigneeUserId?: string | null
    }>
  }>
}

export type StartWorkbenchProcessPayload = {
  processKey: string
  businessKey?: string | null
  formData?: Record<string, unknown> | null
}

export type StartWorkbenchProcessResponse = {
  processDefinitionId: string
  instanceId: string
  status: string
  activeTasks: Array<{
    taskId: string
    nodeId: string
    nodeName: string
    status: string
    assignmentMode: string | null
    candidateUserIds: string[]
    candidateGroupIds?: string[]
    assigneeUserId?: string | null
  }>
}

export type CompleteWorkbenchTaskPayload = {
  action: 'APPROVE' | 'REJECT' | string
  operatorUserId?: string | null
  comment?: string | null
  taskFormData?: Record<string, unknown> | null
}

export type CompleteWorkbenchTaskResponse = {
  instanceId: string
  completedTaskId: string
  status: string
  nextTasks: Array<{
    taskId: string
    nodeId: string
    nodeName: string
    status: string
    assignmentMode: string | null
    candidateUserIds: string[]
    candidateGroupIds?: string[]
    assigneeUserId?: string | null
  }>
}

export type WorkbenchTaskPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: WorkbenchTaskListItem[]
  groups: Array<{ field: string; value: string }>
}

type RuntimePathPart = string | number | undefined

export type WorkbenchRuntimeQueryRequest = {
  namespace?: ProcessRuntimeNamespace
}

export function buildWorkRuntimePath(
  namespace: ProcessRuntimeNamespace = WORKBENCH_RUNTIME_NAMESPACE,
  ...segments: RuntimePathPart[]
) {
  const scope = namespace.trim()
  const normalized = segments
    .filter((value) => value !== undefined && `${value}` !== '')
    .map((value) => `${value}`.replace(/^\/+|\/+$/g, ''))
    .filter(Boolean)
  const merged = scope ? [scope, ...normalized] : normalized

  return `/process-runtime/${merged.join('/')}`
}

export function resolveWorkbenchRuntimePath(...segments: RuntimePathPart[]) {
  return buildWorkRuntimePath(WORKBENCH_RUNTIME_NAMESPACE, ...segments)
}

export const WORKBENCH_RUNTIME_ENDPOINTS = {
  tasksPage: resolveWorkbenchRuntimePath('tasks', 'page'),
  approvalSheetsPage: resolveWorkbenchRuntimePath('approval-sheets', 'page'),
  dashboardSummary: resolveWorkbenchRuntimePath('dashboard', 'summary'),
  tasksCreate: resolveWorkbenchRuntimePath('start'),
  createReviewTicket: (taskId: string) =>
    resolveWorkbenchRuntimePath('tasks', taskId, 'review-ticket'),
  reviewTicketDetail: (ticket: string) =>
    resolveWorkbenchRuntimePath('review-tickets', ticket),
} as const

type WorkbenchApiSuccess<T> = {
  code: 'OK'
  message: string
  data: T
  requestId: string
}

// 任务分页接口，适配实例/任务列表查询协议。
export async function listWorkbenchTasks(
  search: ListQuerySearch
): Promise<WorkbenchTaskPageResponse> {
  const response = await apiClient.post<WorkbenchApiSuccess<WorkbenchTaskPageResponse>>(
    resolveWorkbenchRuntimePath('tasks', 'page'),
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

// 审批单分页接口，按视图过滤。
export async function listApprovalSheets(
  search: ListQuerySearch & {
    view: ApprovalSheetListView
    businessTypes?: string[]
  }
): Promise<ApprovalSheetPageResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<ApprovalSheetPageResponse>
  >(resolveWorkbenchRuntimePath('approval-sheets', 'page'), {
    ...toPaginationRequest(search),
    view: search.view,
    businessTypes: search.businessTypes ?? [],
  })

  return unwrapResponse(response)
}

// 工作台首页概览统计，聚合当前登录人的真实待办和已办数据。
export async function getWorkbenchDashboardSummary(): Promise<WorkbenchDashboardSummary> {
  const response = await apiClient.get<
    WorkbenchApiSuccess<WorkbenchDashboardSummary>
  >(WORKBENCH_RUNTIME_ENDPOINTS.dashboardSummary)

  return unwrapResponse(response)
}

// 任务详情接口：实例+任务聚合模型读取。
export async function getWorkbenchTaskDetail(
  taskId: string
): Promise<WorkbenchTaskDetail> {
  const response = await apiClient.get<WorkbenchApiSuccess<WorkbenchTaskDetail>>(
    resolveWorkbenchRuntimePath('tasks', taskId)
  )

  return unwrapResponse(response)
}

export async function createWorkbenchReviewTicket(
  taskId: string
): Promise<WorkbenchReviewTicket> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<WorkbenchReviewTicket>
  >(WORKBENCH_RUNTIME_ENDPOINTS.createReviewTicket(taskId))

  return unwrapResponse(response)
}

export async function getWorkbenchReviewTicketDetail(
  ticket: string
): Promise<WorkbenchTaskDetail> {
  const response = await apiClient.get<WorkbenchApiSuccess<WorkbenchTaskDetail>>(
    WORKBENCH_RUNTIME_ENDPOINTS.reviewTicketDetail(ticket)
  )

  return unwrapResponse(response)
}

export async function getApprovalSheetDetailByBusiness(
  locator: ApprovalSheetBusinessLocator
): Promise<WorkbenchTaskDetail> {
  const response = await apiClient.get<WorkbenchApiSuccess<WorkbenchTaskDetail>>(
    resolveWorkbenchRuntimePath('approval-sheets', 'by-business'),
    {
      params: locator,
    }
  )

  return unwrapResponse(response)
}

// 选项/能力检查接口，供页面按钮渲染与交互鉴权。
export async function getWorkbenchTaskActionOptions(
  taskId: string
): Promise<WorkbenchTaskActionAvailability> {
  const response = await apiClient.get<
    WorkbenchApiSuccess<WorkbenchTaskActionAvailability>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'actions'))

  return unwrapResponse(response)
}

export async function getWorkbenchTaskActions(
  taskId: string
): Promise<WorkbenchTaskActionAvailability> {
  return getWorkbenchTaskActionOptions(taskId)
}

// 任务实例创建接口，前端先做最小可用封装。
export async function createWorkbenchProcess(
  payload: StartWorkbenchProcessPayload
): Promise<StartWorkbenchProcessResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<StartWorkbenchProcessResponse>
  >(resolveWorkbenchRuntimePath('start'), payload)

  return unwrapResponse(response)
}

export async function startWorkbenchProcess(
  payload: StartWorkbenchProcessPayload
): Promise<StartWorkbenchProcessResponse> {
  return createWorkbenchProcess(payload)
}

export async function claimWorkbenchTask(
  taskId: string,
  payload: ClaimWorkbenchTaskPayload
): Promise<ClaimWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<ClaimWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'claim'), payload)

  return unwrapResponse(response)
}

export async function batchClaimWorkbenchTasks(
  payload: BatchWorkbenchTaskPayload
): Promise<BatchWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<BatchWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', 'batch', 'claim'), payload)

  return unwrapResponse(response)
}

export async function transferWorkbenchTask(
  taskId: string,
  payload: TransferWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'transfer'), payload)

  return unwrapResponse(response)
}

export async function delegateWorkbenchTask(
  taskId: string,
  payload: DelegateWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'delegate'), payload)

  return unwrapResponse(response)
}

export async function handoverWorkbenchTasks(
  payload: HandoverWorkbenchTasksPayload
): Promise<HandoverWorkbenchTasksResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<HandoverWorkbenchTasksResponse>
  >(resolveWorkbenchRuntimePath('users', payload.sourceUserId, 'handover'), {
    targetUserId: payload.targetUserId,
    comment: payload.comment,
  })

  return unwrapResponse(response)
}

export async function returnWorkbenchTask(
  taskId: string,
  payload: ReturnWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'return'), payload)

  return unwrapResponse(response)
}

export async function addSignWorkbenchTask(
  taskId: string,
  payload: AddSignWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'add-sign'), payload)

  return unwrapResponse(response)
}

export async function signWorkbenchTask(
  taskId: string,
  payload: SignWorkbenchTaskPayload
): Promise<SignWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<SignWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'sign'), payload)

  return unwrapResponse(response)
}

export async function removeSignWorkbenchTask(
  taskId: string,
  payload: RemoveSignWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'remove-sign'), payload)

  return unwrapResponse(response)
}

export async function revokeWorkbenchTask(
  taskId: string,
  payload: RevokeWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'revoke'), payload)

  return unwrapResponse(response)
}

export async function urgeWorkbenchTask(
  taskId: string,
  payload: UrgeWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'urge'), payload)

  return unwrapResponse(response)
}

export async function readWorkbenchTask(
  taskId: string
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'read'))

  return unwrapResponse(response)
}

export async function batchReadWorkbenchTasks(
  payload: BatchWorkbenchTaskPayload
): Promise<BatchWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<BatchWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', 'batch', 'read'), payload)

  return unwrapResponse(response)
}

export async function rejectWorkbenchTask(
  taskId: string,
  payload: RejectWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'reject'), payload)

  return unwrapResponse(response)
}

export async function batchRejectWorkbenchTasks(
  payload: BatchWorkbenchTaskPayload
): Promise<BatchWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<BatchWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', 'batch', 'reject'), payload)

  return unwrapResponse(response)
}

export async function jumpWorkbenchTask(
  taskId: string,
  payload: JumpWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'jump'), payload)

  return unwrapResponse(response)
}

export async function takeBackWorkbenchTask(
  taskId: string,
  payload: TakeBackWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'take-back'), payload)

  return unwrapResponse(response)
}

export async function wakeUpWorkbenchInstance(
  instanceId: string,
  payload: WakeUpWorkbenchInstancePayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('instances', instanceId, 'wake-up'), payload)

  return unwrapResponse(response)
}

export async function completeWorkbenchTask(
  taskId: string,
  payload: CompleteWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'complete'), payload)

  return unwrapResponse(response)
}

export async function batchCompleteWorkbenchTasks(
  payload: BatchWorkbenchTaskPayload
): Promise<BatchWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<BatchWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', 'batch', 'complete'), payload)

  return unwrapResponse(response)
}
