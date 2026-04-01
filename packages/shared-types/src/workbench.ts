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
  currentNodeName: string | null
  currentTaskId: string | null
  currentTaskStatus: string | null
  currentAssigneeUserId: string | null
  instanceStatus: string
  latestAction: string | null
  latestOperatorUserId: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

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
}

export type WorkbenchTaskDetail = WorkbenchTaskListItem & {
  action: string | null
  operatorUserId: string | null
  comment: string | null
  instanceStatus: string
  formData: Record<string, unknown>
  businessData?: Record<string, unknown> | null
  flowNodes?: WorkbenchFlowNode[] | null
  flowEdges?: WorkbenchFlowEdge[] | null
  instanceEvents?: WorkbenchProcessInstanceEvent[] | null
  taskTrace?: WorkbenchTaskTraceItem[] | null
  activeTaskIds: string[]
  userDisplayNames?: Record<string, string> | null
  groupDisplayNames?: Record<string, string> | null
}

export type WorkbenchDashboardSummary = {
  todoTodayCount: number
  doneApprovalCount: number
}

export type WorkbenchTaskPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: WorkbenchTaskListItem[]
  groups: Array<{ field: string; value: string }>
}

export type ApprovalSheetPageResponse = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: ApprovalSheetListItem[]
  groups: Array<{ field: string; value: string }>
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
