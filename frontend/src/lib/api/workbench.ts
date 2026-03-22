import { apiClient, unwrapResponse } from '@/lib/api/client'
import { type ListQuerySearch, toPaginationRequest } from '@/features/shared/table/query-contract'
import { type WorkflowFieldBinding } from '@/features/workflow/designer/types'

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
  assigneeUserId: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
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
}

export type WorkbenchTaskTraceItem = {
  taskId: string
  nodeId: string
  nodeName: string
  taskKind?: string | null
  status: string
  assigneeUserId?: string | null
  candidateUserIds: string[]
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

export type WorkbenchTaskDetail = WorkbenchTaskListItem & {
  action: string | null
  operatorUserId: string | null
  comment: string | null
  instanceStatus: string
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
  activeTaskIds: string[]
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
  targetStrategy: 'PREVIOUS_USER_TASK'
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
  tasksCreate: resolveWorkbenchRuntimePath('start'),
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

// 任务详情接口：实例+任务聚合模型读取。
export async function getWorkbenchTaskDetail(
  taskId: string
): Promise<WorkbenchTaskDetail> {
  const response = await apiClient.get<WorkbenchApiSuccess<WorkbenchTaskDetail>>(
    resolveWorkbenchRuntimePath('tasks', taskId)
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

export async function rejectWorkbenchTask(
  taskId: string,
  payload: RejectWorkbenchTaskPayload
): Promise<CompleteWorkbenchTaskResponse> {
  const response = await apiClient.post<
    WorkbenchApiSuccess<CompleteWorkbenchTaskResponse>
  >(resolveWorkbenchRuntimePath('tasks', taskId, 'reject'), payload)

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
