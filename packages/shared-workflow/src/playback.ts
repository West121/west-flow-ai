import {
  type WorkflowPlaybackEdge,
  type WorkflowPlaybackEvent,
  type WorkflowPlaybackInstanceEvent,
  type WorkflowPlaybackNode,
  type WorkflowPlaybackTaskTraceItem,
  type WorkflowTimelineEntry,
  type WorkflowTimelineTone,
  type WorkflowTraversalState,
  type WorkflowPlaybackMode,
  type WorkflowNodePlaybackState,
  type WorkflowEdgePlaybackState,
} from './types'

/**
 * 找到流程起点节点。
 */
export function findStartNodeId(nodes: readonly WorkflowPlaybackNode[]): string | null {
  return nodes.find((node) => node.type === 'start')?.id ?? nodes[0]?.id ?? null
}

/**
 * 判断是否是开始类回放事件。
 */
export function isStartPlaybackEvent(event: WorkflowPlaybackEvent): boolean {
  return event.eventType === 'START_PROCESS' || event.eventType === 'INSTANCE_STARTED'
}

/**
 * 判断是否是终态回放事件。
 */
export function isTerminalPlaybackEvent(event: WorkflowPlaybackEvent): boolean {
  return (
    event.eventType === 'INSTANCE_COMPLETED' ||
    event.eventType === 'INSTANCE_REVOKED' ||
    event.eventType === 'INSTANCE_TERMINATED'
  )
}

function normalizeTime(value: string | null | undefined): number {
  if (!value) {
    return Number.MAX_SAFE_INTEGER
  }
  const time = Date.parse(value)
  return Number.isNaN(time) ? Number.MAX_SAFE_INTEGER : time
}

function uniqueStable<T>(items: readonly T[], keyOf: (item: T) => string): T[] {
  const seen = new Set<string>()
  const output: T[] = []
  for (const item of items) {
    const key = keyOf(item)
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    output.push(item)
  }
  return output
}

/**
 * 先从实例事件构造播放序列，不够时回退到任务轨迹。
 */
export function buildPlaybackEvents(
  flowNodes: readonly WorkflowPlaybackNode[],
  instanceEvents: readonly WorkflowPlaybackInstanceEvent[],
  taskTrace: readonly WorkflowPlaybackTaskTraceItem[],
  instanceStatus?: string | null
): WorkflowPlaybackEvent[] {
  const startNodeId = findStartNodeId(flowNodes)
  const endNodeId = flowNodes.find((node) => node.type === 'end')?.id ?? null

  const directEvents = instanceEvents
    .filter((event) =>
      [
        'START_PROCESS',
        'INSTANCE_STARTED',
        'INSTANCE_COMPLETED',
        'INSTANCE_REVOKED',
        'INSTANCE_TERMINATED',
      ].includes(event.eventType)
    )
    .map<WorkflowPlaybackEvent>((event) => ({
      id: event.eventId,
      nodeId: (() => {
        if ((event.eventType === 'START_PROCESS' || event.eventType === 'INSTANCE_STARTED') && startNodeId) {
          return startNodeId
        }
        if (
          (event.eventType === 'INSTANCE_COMPLETED' ||
            event.eventType === 'INSTANCE_REVOKED' ||
            event.eventType === 'INSTANCE_TERMINATED') &&
          endNodeId
        ) {
          return endNodeId
        }
        return event.nodeId ?? ''
      })(),
      taskId:
        event.eventType === 'START_PROCESS' ||
        event.eventType === 'INSTANCE_STARTED' ||
        event.eventType === 'INSTANCE_COMPLETED' ||
        event.eventType === 'INSTANCE_REVOKED' ||
        event.eventType === 'INSTANCE_TERMINATED'
          ? null
          : event.taskId ?? null,
      label: event.eventName,
      eventType: event.eventType,
      occurredAt: event.occurredAt ?? null,
      operatorUserId: event.operatorUserId ?? null,
    }))

  const traceEvents = taskTrace.map<WorkflowPlaybackEvent>((item) => ({
    id: `task:${item.taskId}`,
    nodeId: item.nodeId,
    taskId: item.taskId,
    label: item.nodeName,
    eventType: 'TASK_TRACE',
    occurredAt: item.handleEndTime ?? item.handleStartTime ?? item.receiveTime ?? null,
    operatorUserId: item.operatorUserId ?? item.assigneeUserId ?? null,
  }))

  const merged = [...directEvents, ...traceEvents]

  if (
    instanceStatus === 'COMPLETED' &&
    endNodeId &&
    !merged.some((event) => event.nodeId === endNodeId)
  ) {
    const lastOccurredAt = merged[merged.length - 1]?.occurredAt ?? null
    const endOccurredAt = lastOccurredAt
      ? new Date(new Date(lastOccurredAt).getTime() + 1).toISOString()
      : null
    merged.push({
      id: 'instance:end',
      nodeId: endNodeId,
      taskId: null,
      label: '流程结束',
      eventType: 'INSTANCE_COMPLETED',
      occurredAt: endOccurredAt,
      operatorUserId: null,
    })
  }

  const eventPriority = (event: WorkflowPlaybackEvent) => {
    if (isStartPlaybackEvent(event)) {
      return 0
    }
    if (isTerminalPlaybackEvent(event)) {
      return 2
    }
    return 1
  }

  const sorted = merged
    .filter((event) => Boolean(event.nodeId))
    .sort((left, right) => {
      const leftPriority = eventPriority(left)
      const rightPriority = eventPriority(right)
      if (leftPriority !== rightPriority) {
        return leftPriority - rightPriority
      }
      const leftTime = normalizeTime(left.occurredAt)
      const rightTime = normalizeTime(right.occurredAt)
      if (leftTime !== rightTime) {
        return leftTime - rightTime
      }
      return left.label.localeCompare(right.label, 'zh-CN')
    })

  return uniqueStable(sorted, (event) => `${event.nodeId}:${event.occurredAt ?? ''}:${event.eventType}:${event.label}`)
}

function resolveTimelineTone(
  event: WorkflowPlaybackEvent,
  traceItem: WorkflowPlaybackTaskTraceItem | null
): WorkflowTimelineTone {
  const status = traceItem?.status ?? null

  if (status === 'PENDING' || status === 'PENDING_CLAIM' || traceItem?.handleStartTime || traceItem?.receiveTime) {
    return 'info'
  }
  if (status === 'REJECTED' || status === 'RETURNED' || status === 'TAKEN_BACK') {
    return 'warning'
  }
  if (status === 'REVOKED' || event.eventType === 'INSTANCE_REVOKED' || event.eventType === 'INSTANCE_TERMINATED') {
    return 'danger'
  }
  return 'success'
}

function resolveTimelineStatusLabel(
  event: WorkflowPlaybackEvent,
  traceItem: WorkflowPlaybackTaskTraceItem | null
): string {
  if (traceItem?.handleEndTime) {
    return '完成'
  }
  if (traceItem?.handleStartTime) {
    return '处理中'
  }
  if (traceItem?.receiveTime) {
    return '已接收'
  }
  if (isTerminalPlaybackEvent(event)) {
    return '结束'
  }
  if (isStartPlaybackEvent(event)) {
    return '发起'
  }
  return '待处理'
}

/**
 * 按时间顺序构造时间轴条目。
 */
export function buildTimelineEntries(
  playbackEvents: readonly WorkflowPlaybackEvent[],
  taskTrace: readonly WorkflowPlaybackTaskTraceItem[]
): WorkflowTimelineEntry[] {
  const startEvent =
    playbackEvents.find((event) => isStartPlaybackEvent(event)) ?? null
  const terminalEvent =
    [...playbackEvents].reverse().find((event) => isTerminalPlaybackEvent(event)) ?? null

  const taskEntries = [...taskTrace]
    .sort((left, right) => normalizeTime(left.receiveTime ?? left.handleStartTime ?? left.readTime) -
      normalizeTime(right.receiveTime ?? right.handleStartTime ?? right.readTime))
    .map<WorkflowTimelineEntry>((item) => {
      const event: WorkflowPlaybackEvent = {
        id: `task:${item.taskId}`,
        nodeId: item.nodeId,
        taskId: item.taskId,
        label: item.nodeName,
        eventType: 'TASK_TRACE',
        occurredAt: item.handleEndTime ?? item.handleStartTime ?? item.receiveTime ?? null,
        operatorUserId: item.operatorUserId ?? item.assigneeUserId ?? null,
      }
      return {
        id: `timeline-task:${item.taskId}`,
        event,
        traceItem: item,
        occurredAt: event.occurredAt,
        dotTone: resolveTimelineTone(event, item),
        statusLabel: resolveTimelineStatusLabel(event, item),
      }
    })

  const entries: WorkflowTimelineEntry[] = []
  if (startEvent) {
    entries.push({
      id: `timeline-event:${startEvent.id}`,
      event: startEvent,
      traceItem: null,
      occurredAt: startEvent.occurredAt,
      dotTone: resolveTimelineTone(startEvent, null),
      statusLabel: resolveTimelineStatusLabel(startEvent, null),
    })
  }
  entries.push(...taskEntries)
  if (terminalEvent) {
    entries.push({
      id: `timeline-event:${terminalEvent.id}`,
      event: terminalEvent,
      traceItem: null,
      occurredAt: terminalEvent.occurredAt,
      dotTone: resolveTimelineTone(terminalEvent, null),
      statusLabel: resolveTimelineStatusLabel(terminalEvent, null),
    })
  }
  return entries
}

function buildOutgoingEdgeMap(edges: readonly WorkflowPlaybackEdge[]) {
  return edges.reduce<Map<string, WorkflowPlaybackEdge[]>>((map, edge) => {
    const list = map.get(edge.source) ?? []
    list.push(edge)
    map.set(edge.source, list)
    return map
  }, new Map())
}

function findPathEdgeIds(
  flowEdges: readonly WorkflowPlaybackEdge[],
  sourceNodeId: string | null,
  targetNodeId: string | null
): string[] {
  if (!sourceNodeId || !targetNodeId || sourceNodeId === targetNodeId) {
    return []
  }

  const outgoingEdgeMap = buildOutgoingEdgeMap(flowEdges)
  const queue: Array<{ nodeId: string; edgeIds: string[] }> = [{ nodeId: sourceNodeId, edgeIds: [] }]
  const visited = new Set<string>([sourceNodeId])

  while (queue.length > 0) {
    const current = queue.shift()
    if (!current) {
      continue
    }

    const edges = outgoingEdgeMap.get(current.nodeId) ?? []
    for (const edge of edges) {
      const nextEdgeIds = [...current.edgeIds, edge.id]
      if (edge.target === targetNodeId) {
        return nextEdgeIds
      }
      if (!visited.has(edge.target)) {
        visited.add(edge.target)
        queue.push({ nodeId: edge.target, edgeIds: nextEdgeIds })
      }
    }
  }

  return []
}

/**
 * 根据回顾焦点计算已访问和当前高亮的边。
 */
export function buildTraversalState(
  flowNodes: readonly WorkflowPlaybackNode[],
  flowEdges: readonly WorkflowPlaybackEdge[],
  focusNodeIds: readonly string[],
  activeIndex: number
): WorkflowTraversalState {
  const startNodeId = findStartNodeId(flowNodes)
  const sequence = [startNodeId, ...focusNodeIds].filter(
    (value, index, list): value is string => Boolean(value) && list.indexOf(value) === index
  )
  const edgeById = new Map(flowEdges.map((edge) => [edge.id, edge]))
  const visitedNodeIds = new Set<string>()
  const visitedEdgeIds = new Set<string>()
  const activeEdgeIds = new Set<string>()

  if (sequence.length === 0) {
    return { visitedNodeIds: [], visitedEdgeIds: [], activeEdgeIds: [] }
  }

  visitedNodeIds.add(sequence[0]!)

  for (let index = 1; index < sequence.length; index += 1) {
    const pathEdgeIds = findPathEdgeIds(flowEdges, sequence[index - 1]!, sequence[index]!)
    const isActiveSegment = index - 1 === activeIndex

    for (const edgeId of pathEdgeIds) {
      visitedEdgeIds.add(edgeId)
      if (isActiveSegment) {
        activeEdgeIds.add(edgeId)
      }
      const edge = edgeById.get(edgeId)
      if (edge) {
        visitedNodeIds.add(edge.source)
        visitedNodeIds.add(edge.target)
      }
    }
  }

  return {
    visitedNodeIds: [...visitedNodeIds],
    visitedEdgeIds: [...visitedEdgeIds],
    activeEdgeIds: [...activeEdgeIds],
  }
}

/**
 * 按节点计算回顾状态。
 */
export function buildNodePlaybackStates(
  nodes: readonly WorkflowPlaybackNode[],
  context: {
    activeNodeId: string | null
    completedNodeIds: readonly string[]
    visitedNodeIds: readonly string[]
    mode: WorkflowPlaybackMode
    terminalStatuses?: readonly string[]
    instanceStatus?: string | null
  }
): Array<WorkflowPlaybackNode & WorkflowNodePlaybackState> {
  const completedSet = new Set(context.completedNodeIds)
  const visitedSet = new Set(context.visitedNodeIds)
  const terminalStatuses = new Set(context.terminalStatuses ?? ['COMPLETED', 'REVOKED', 'TERMINATED'])
  const isTerminal = context.instanceStatus ? terminalStatuses.has(context.instanceStatus) : false

  return nodes.map((node) => {
    const isActive =
      node.id === context.activeNodeId &&
      (context.mode === 'playing' || !isTerminal)
    const isCompleted = completedSet.has(node.id)
    const isVisited = visitedSet.has(node.id)
    const state: WorkflowNodePlaybackState['state'] = isActive
      ? 'active'
      : isCompleted
        ? 'completed'
        : isVisited
          ? 'visited'
          : 'idle'

    return {
      ...node,
      state,
      isActive,
      isCompleted,
      isVisited,
    }
  })
}

/**
 * 按边计算回顾状态。
 */
export function buildEdgePlaybackStates(
  edges: readonly WorkflowPlaybackEdge[],
  context: {
    visitedEdgeIds: readonly string[]
    activeEdgeIds: readonly string[]
    mode: WorkflowPlaybackMode
  }
): Array<WorkflowPlaybackEdge & WorkflowEdgePlaybackState> {
  const visitedSet = new Set(context.visitedEdgeIds)
  const activeSet = new Set(context.activeEdgeIds)

  return edges.map((edge) => {
    const isActive = context.mode === 'playing' && activeSet.has(edge.id)
    const isTraversed = visitedSet.has(edge.id)
    const state: WorkflowEdgePlaybackState['state'] = isActive
      ? 'active'
      : isTraversed
        ? 'traversed'
        : 'idle'

    return {
      ...edge,
      state,
      isActive,
      isTraversed,
      animated: context.mode === 'playing' && (isActive || isTraversed),
    }
  })
}

/**
 * 解析当前应展示的任务轨迹项。
 */
export function resolveCurrentTaskTraceItem(
  taskTrace: readonly WorkflowPlaybackTaskTraceItem[]
): WorkflowPlaybackTaskTraceItem | null {
  const pendingItems = taskTrace.filter(
    (item) => item.status === 'PENDING' || item.status === 'PENDING_CLAIM'
  )

  if (pendingItems.length === 0) {
    return null
  }

  const pendingAddSignItems = pendingItems.filter((item) => item.isAddSignTask)
  const focusItems = pendingAddSignItems.length > 0 ? pendingAddSignItems : pendingItems

  return [...focusItems].sort((left, right) => normalizeTime(right.receiveTime ?? right.handleStartTime ?? right.readTime) -
    normalizeTime(left.receiveTime ?? left.handleStartTime ?? left.readTime))[0] ?? null
}

/**
 * 解析当前回顾应该聚焦的节点序列。
 */
export function resolvePlaybackFocusNodeIds(
  playbackEvents: readonly WorkflowPlaybackEvent[],
  taskTrace: readonly WorkflowPlaybackTaskTraceItem[],
  activeIndex: number,
  mode: WorkflowPlaybackMode,
  instanceStatus?: string | null
): string[] {
  const activePathNodeIds = playbackEvents
    .slice(0, activeIndex + 1)
    .map((event) => event.nodeId)
    .filter(Boolean)

  if (mode === 'playing') {
    return activePathNodeIds
  }

  if (instanceStatus === 'COMPLETED' || instanceStatus === 'REVOKED' || instanceStatus === 'TERMINATED') {
    return playbackEvents.map((event) => event.nodeId).filter(Boolean)
  }

  const tracePathNodeIds = taskTrace
    .filter((item) => item.handleEndTime || item.status === 'PENDING' || item.status === 'PENDING_CLAIM')
    .map((item) => item.nodeId)
    .filter(Boolean)

  return tracePathNodeIds.length > 0 ? tracePathNodeIds : activePathNodeIds
}

/**
 * 解析当前回顾应该高亮的事件。
 */
export function resolveActivePlaybackEvent(
  playbackEvents: readonly WorkflowPlaybackEvent[],
  taskTrace: readonly WorkflowPlaybackTaskTraceItem[],
  activeIndex: number,
  mode: WorkflowPlaybackMode,
  instanceStatus?: string | null
): WorkflowPlaybackEvent | null {
  const currentTask = resolveCurrentTaskTraceItem(taskTrace)

  if (mode === 'playing') {
    return playbackEvents[activeIndex] ?? null
  }

  if (instanceStatus === 'COMPLETED' || instanceStatus === 'REVOKED' || instanceStatus === 'TERMINATED') {
    return playbackEvents[activeIndex] ?? playbackEvents[playbackEvents.length - 1] ?? null
  }

  if (currentTask) {
    return (
      playbackEvents.find((event) => event.taskId === currentTask.taskId) ??
      playbackEvents.find((event) => event.nodeId === currentTask.nodeId) ??
      null
    )
  }

  return playbackEvents[activeIndex] ?? null
}
