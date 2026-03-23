import { apiClient, unwrapResponse, type ApiSuccessResponse } from '@/lib/api/client'

export type ProcessCollaborationEvent = {
  eventId: string
  instanceId: string
  taskId: string | null
  eventType: string
  subject: string | null
  content: string | null
  mentionedUserIds: string[]
  permissionCode: string | null
  actionType: string
  actionCategory: string
  operatorUserId: string | null
  occurredAt: string | null
  details: Record<string, unknown> | null
}

export type CreateProcessCollaborationEventPayload = {
  instanceId?: string | null
  taskId?: string | null
  eventType: string
  subject?: string | null
  content: string
  mentionedUserIds?: string[]
}

export type ProcessTimeTravelExecution = {
  executionId: string
  instanceId: string
  strategy: string
  taskId: string | null
  targetNodeId: string | null
  targetTaskId: string | null
  newInstanceId: string | null
  permissionCode: string | null
  actionType: string
  actionCategory: string
  operatorUserId: string | null
  occurredAt: string | null
  details: Record<string, unknown> | null
}

export type ExecuteProcessTimeTravelPayload = {
  instanceId: string
  strategy: 'BACK_TO_NODE' | 'REOPEN_INSTANCE'
  taskId?: string | null
  targetNodeId?: string | null
  reason: string
  variables?: Record<string, unknown> | null
}

export type ProcessTerminationNode = {
  nodeId: string
  targetId: string
  parentInstanceId: string | null
  parentNodeId: string | null
  targetKind: string
  linkType: string | null
  runtimeLinkType: string | null
  triggerMode: string | null
  appendType: string | null
  status: string | null
  terminatePolicy: string | null
  childFinishPolicy: string | null
  sourceTaskId: string | null
  sourceNodeId: string | null
  calledProcessKey: string | null
  calledDefinitionId: string | null
  targetUserId: string | null
  operatorUserId: string | null
  commentText: string | null
  createdAt: string | null
  finishedAt: string | null
  children: ProcessTerminationNode[]
}

export type ProcessTerminationSnapshot = {
  rootInstanceId: string
  scope: string
  propagationPolicy: string
  reason: string | null
  operatorUserId: string | null
  summary: string
  targetCount: number
  generatedAt: string | null
  nodes: ProcessTerminationNode[]
}

export type ProcessTerminationAudit = {
  auditId: string
  rootInstanceId: string
  targetInstanceId: string | null
  parentInstanceId: string | null
  targetKind: string | null
  terminateScope: string
  propagationPolicy: string
  eventType: string
  resultStatus: string
  reason: string | null
  operatorUserId: string | null
  detailJson: string | null
  createdAt: string | null
  finishedAt: string | null
}

export type ProcessTerminationRequestPayload = {
  rootInstanceId: string
  targetInstanceId?: string | null
  scope: 'ROOT' | 'CHILD' | 'CURRENT'
  propagationPolicy:
    | 'SELF_ONLY'
    | 'CASCADE_CHILDREN'
    | 'CASCADE_APPENDS'
    | 'CASCADE_DESCENDANTS'
    | 'CASCADE_ALL'
  reason: string
  operatorUserId?: string | null
}

export async function createProcessCollaborationEvent(
  payload: CreateProcessCollaborationEventPayload
) {
  const response = await apiClient.post<ApiSuccessResponse<ProcessCollaborationEvent>>(
    '/process-runtime/collaboration/events',
    payload
  )
  return unwrapResponse(response)
}

export async function listProcessCollaborationTrace(instanceId: string) {
  const response = await apiClient.get<ApiSuccessResponse<ProcessCollaborationEvent[]>>(
    `/process-runtime/collaboration/instances/${instanceId}/trace`
  )
  return unwrapResponse(response)
}

export async function executeProcessTimeTravel(
  payload: ExecuteProcessTimeTravelPayload
) {
  const response = await apiClient.post<ApiSuccessResponse<ProcessTimeTravelExecution>>(
    '/process-runtime/time-travel/execute',
    payload
  )
  return unwrapResponse(response)
}

export async function listProcessTimeTravelTrace(instanceId: string) {
  const response = await apiClient.get<ApiSuccessResponse<ProcessTimeTravelExecution[]>>(
    `/process-runtime/time-travel/instances/${instanceId}/trace`
  )
  return unwrapResponse(response)
}

export async function getProcessTerminationSnapshot(
  payload: ProcessTerminationRequestPayload
) {
  const response = await apiClient.post<ApiSuccessResponse<ProcessTerminationSnapshot>>(
    '/process-runtime/termination/snapshot',
    payload
  )
  return unwrapResponse(response)
}

export async function listProcessTerminationAuditTrail(rootInstanceId: string) {
  const response = await apiClient.get<ApiSuccessResponse<ProcessTerminationAudit[]>>(
    '/process-runtime/termination/audit-trail',
    {
      params: {
        rootInstanceId,
      },
    }
  )
  return unwrapResponse(response)
}
