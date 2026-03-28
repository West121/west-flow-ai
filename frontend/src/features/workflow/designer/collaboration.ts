export type WorkflowCollaborationNodeKind =
  | 'cc'
  | 'supervise'
  | 'meeting'
  | 'read'
  | 'circulate'

export type WorkflowCollaborationEventType =
  | 'COMMENT'
  | 'SUPERVISE'
  | 'MEETING'
  | 'READ'
  | 'CIRCULATE'

export const workflowCollaborationNodeKinds: WorkflowCollaborationNodeKind[] = [
  'cc',
  'supervise',
  'meeting',
  'read',
  'circulate',
]

export const workflowCollaborationEventTypeOptions: Array<{
  value: WorkflowCollaborationEventType
  label: string
}> = [
  { value: 'COMMENT', label: '批注' },
  { value: 'SUPERVISE', label: '督办' },
  { value: 'MEETING', label: '会办' },
  { value: 'READ', label: '阅办' },
  { value: 'CIRCULATE', label: '传阅' },
]

const collaborationNodeLabels: Record<WorkflowCollaborationNodeKind, string> = {
  cc: '抄送',
  supervise: '督办',
  meeting: '会办',
  read: '阅办',
  circulate: '传阅',
}

const collaborationNodeDescriptions: Record<WorkflowCollaborationNodeKind, string> = {
  cc: '知会、已阅、协同提醒',
  supervise: '督促相关人员尽快处理并回报进度',
  meeting: '组织多人协同办理并汇总结果',
  read: '以阅读确认为主，记录已阅轨迹',
  circulate: '按顺序或范围传阅，作为知会分发',
}

const collaborationEventTypeLabels: Record<WorkflowCollaborationEventType, string> = {
  COMMENT: '批注',
  SUPERVISE: '督办',
  MEETING: '会办',
  READ: '阅办',
  CIRCULATE: '传阅',
}

export function isWorkflowCollaborationNodeKind(value: string): value is WorkflowCollaborationNodeKind {
  return workflowCollaborationNodeKinds.includes(value as WorkflowCollaborationNodeKind)
}

export function resolveWorkflowCollaborationNodeLabel(kind: string | null | undefined) {
  if (!kind) {
    return '--'
  }

  return collaborationNodeLabels[kind as WorkflowCollaborationNodeKind] ?? kind
}

export function resolveWorkflowCollaborationNodeDescription(kind: string | null | undefined) {
  if (!kind) {
    return '--'
  }

  return collaborationNodeDescriptions[kind as WorkflowCollaborationNodeKind] ?? kind
}

export function resolveWorkflowCollaborationEventTypeLabel(
  eventType: string | null | undefined
) {
  if (!eventType) {
    return '--'
  }

  return (
    collaborationEventTypeLabels[eventType as WorkflowCollaborationEventType]
    ?? eventType
  )
}
