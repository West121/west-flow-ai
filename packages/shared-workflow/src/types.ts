/**
 * 流程图回顾播放器共用的图节点基础结构。
 */
export type WorkflowPlaybackNode = {
  id: string
  type: string
  name?: string | null
  width?: number | null
}

/**
 * 流程图回顾播放器共用的图边基础结构。
 */
export type WorkflowPlaybackEdge = {
  id: string
  source: string
  target: string
  label?: string | null
}

/**
 * 后端实例事件的最小字段集合。
 */
export type WorkflowPlaybackInstanceEvent = {
  eventId: string
  eventType: string
  eventName: string
  nodeId?: string | null
  taskId?: string | null
  occurredAt?: string | null
  operatorUserId?: string | null
}

/**
 * 任务轨迹的最小字段集合。
 */
export type WorkflowPlaybackTaskTraceItem = {
  taskId: string
  nodeId: string
  nodeName: string
  status?: string | null
  receiveTime?: string | null
  readTime?: string | null
  handleStartTime?: string | null
  handleEndTime?: string | null
  handleDurationSeconds?: number | null
  operatorUserId?: string | null
  assigneeUserId?: string | null
  candidateUserIds?: readonly string[] | null
  isAddSignTask?: boolean | null
  actingMode?: string | null
  actingForUserId?: string | null
  delegatedByUserId?: string | null
  handoverFromUserId?: string | null
  comment?: string | null
}

/**
 * 从实例事件和任务轨迹构造出的回放事件。
 */
export type WorkflowPlaybackEvent = {
  id: string
  nodeId: string
  taskId: string | null
  label: string
  eventType: string
  occurredAt: string | null
  operatorUserId: string | null
}

/**
 * 时间轴条目。
 */
export type WorkflowTimelineEntry = {
  id: string
  event: WorkflowPlaybackEvent
  traceItem: WorkflowPlaybackTaskTraceItem | null
  occurredAt: string | null
  dotTone: WorkflowTimelineTone
  statusLabel: string
}

/**
 * 时间轴圆点语义色彩。
 */
export type WorkflowTimelineTone = 'info' | 'warning' | 'danger' | 'success'

/**
 * 节点的回顾状态。
 */
export type WorkflowNodePlaybackState = {
  state: 'active' | 'completed' | 'visited' | 'idle'
  isActive: boolean
  isCompleted: boolean
  isVisited: boolean
}

/**
 * 边的回顾状态。
 */
export type WorkflowEdgePlaybackState = {
  state: 'active' | 'traversed' | 'idle'
  isActive: boolean
  isTraversed: boolean
  animated: boolean
}

/**
 * 回顾遍历状态。
 */
export type WorkflowTraversalState = {
  visitedNodeIds: string[]
  visitedEdgeIds: string[]
  activeEdgeIds: string[]
}

/**
 * 播放器模式。
 */
export type WorkflowPlaybackMode = 'idle' | 'playing' | 'paused'

/**
 * 播放器状态。
 */
export type WorkflowPlaybackState = {
  mode: WorkflowPlaybackMode
  activeIndex: number
  totalEvents: number
}

/**
 * 播放器动作。
 */
export type WorkflowPlaybackAction =
  | { type: 'play'; startIndex?: number }
  | { type: 'pause' }
  | { type: 'reset' }
  | { type: 'seek'; index: number }
  | { type: 'tick' }
  | { type: 'sync'; totalEvents: number }
