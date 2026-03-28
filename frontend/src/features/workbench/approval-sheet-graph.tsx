import { useEffect, useMemo, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  MarkerType,
  ReactFlow,
  ReactFlowProvider,
  type ReactFlowInstance,
  type Edge,
  type Node,
} from '@xyflow/react'
import { Pause, Play, RotateCcw } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useTheme } from '@/context/theme-provider'
import { ApprovalTagList, ApprovalUserTag } from './approval-actor-tags'
import {
  type WorkbenchFlowEdge,
  type WorkbenchFlowNode,
  type WorkbenchProcessInstanceEvent,
  type WorkbenchTaskTraceItem,
} from '@/lib/api/workbench'
import { WorkflowNodeCard } from '@/features/workflow/designer/workflow-node'
import { type WorkflowNodeData } from '@/features/workflow/designer/types'
import {
  resolveApprovalSheetActingModeLabel,
  resolveApprovalSheetCollaborationNodeLabel,
  formatApprovalSheetBoolean,
  formatApprovalSheetDateTime,
  formatApprovalSheetDuration,
  formatApprovalSheetText,
  resolveApprovalSheetResultLabel,
} from './approval-sheet-helpers'

import '@xyflow/react/dist/style.css'

type ApprovalSheetGraphProps = {
  flowNodes: WorkbenchFlowNode[]
  flowEdges: WorkbenchFlowEdge[]
  taskTrace: WorkbenchTaskTraceItem[]
  instanceEvents: WorkbenchProcessInstanceEvent[]
  instanceStatus?: string | null
  userDisplayNames?: Record<string, string> | null
}

type PlaybackEvent = {
  id: string
  nodeId: string
  taskId: string | null
  label: string
  eventType: string
  occurredAt: string | null
  operatorUserId: string | null
}

type TimelineEntry = {
  id: string
  event: PlaybackEvent
  traceItem: WorkbenchTaskTraceItem | null
}

type TraversalState = {
  visitedNodeIds: Set<string>
  visitedEdgeIds: Set<string>
  activeEdgeIds: Set<string>
}

// 回顾优先使用实例事件；如果后端没有给足事件，再回退到任务轨迹。
// 先从实例事件构造播放序列，不够时再回退到任务轨迹。
function buildPlaybackEvents(
  flowNodes: WorkbenchFlowNode[],
  instanceEvents: WorkbenchProcessInstanceEvent[],
  taskTrace: WorkbenchTaskTraceItem[],
  instanceStatus?: string | null
): PlaybackEvent[] {
  const startNodeId = findStartNodeId(flowNodes)
  const endNodeId = flowNodes.find((node) => node.type === 'end')?.id ?? null
  const directEvents: PlaybackEvent[] = instanceEvents
    .filter(
      (event) =>
        event.eventType === 'START_PROCESS' ||
        event.eventType === 'INSTANCE_STARTED' ||
        event.eventType === 'INSTANCE_COMPLETED' ||
        event.eventType === 'INSTANCE_REVOKED' ||
        event.eventType === 'INSTANCE_TERMINATED'
    )
    .map((event) => ({
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
      occurredAt: event.occurredAt,
      operatorUserId: event.operatorUserId ?? null,
    }))

  const traceEvents: PlaybackEvent[] = taskTrace.map((item) => ({
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

  const eventPriority = (event: PlaybackEvent) => {
    if (event.eventType === 'START_PROCESS' || event.eventType === 'INSTANCE_STARTED') {
      return 0
    }
    if (
      event.eventType === 'INSTANCE_COMPLETED' ||
      event.eventType === 'INSTANCE_REVOKED' ||
      event.eventType === 'INSTANCE_TERMINATED'
    ) {
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
      const leftTime = left.occurredAt ? new Date(left.occurredAt).getTime() : Number.MAX_SAFE_INTEGER
      const rightTime = right.occurredAt ? new Date(right.occurredAt).getTime() : Number.MAX_SAFE_INTEGER
      if (leftTime !== rightTime) {
        return leftTime - rightTime
      }
      return left.label.localeCompare(right.label, 'zh-CN')
    })

  const deduped = new Map<string, PlaybackEvent>()
  for (const event of sorted) {
    const key = `${event.nodeId}:${event.occurredAt ?? ''}:${event.eventType}:${event.label}`
    if (!deduped.has(key)) {
      deduped.set(key, event)
    }
  }

  return Array.from(deduped.values())
}

function eventOccurredAt(event: PlaybackEvent, traceItem: WorkbenchTaskTraceItem | null) {
  return (
    traceItem?.handleEndTime
    ?? traceItem?.handleStartTime
    ?? traceItem?.receiveTime
    ?? event.occurredAt
  )
}

function buildTimelineEntries(
  playbackEvents: PlaybackEvent[],
  taskTrace: WorkbenchTaskTraceItem[]
): TimelineEntry[] {
  const startEvent = playbackEvents.find(
    (event) => event.eventType === 'START_PROCESS' || event.eventType === 'INSTANCE_STARTED'
  ) ?? null
  const terminalEvent = [...playbackEvents].reverse().find(
    (event) =>
      event.eventType === 'INSTANCE_COMPLETED'
      || event.eventType === 'INSTANCE_REVOKED'
      || event.eventType === 'INSTANCE_TERMINATED'
  ) ?? null

  const taskEntries = [...taskTrace]
    .sort((left, right) => {
      const leftTime = new Date(
        left.receiveTime ?? left.handleStartTime ?? left.readTime ?? 0
      ).getTime()
      const rightTime = new Date(
        right.receiveTime ?? right.handleStartTime ?? right.readTime ?? 0
      ).getTime()
      return leftTime - rightTime
    })
    .map<TimelineEntry>((item) => ({
      id: `timeline-task:${item.taskId}`,
      event: {
        id: `task:${item.taskId}`,
        nodeId: item.nodeId,
        taskId: item.taskId,
        label: item.nodeName,
        eventType: 'TASK_TRACE',
        occurredAt: item.handleEndTime ?? item.handleStartTime ?? item.receiveTime ?? null,
        operatorUserId: item.operatorUserId ?? item.assigneeUserId ?? null,
      },
      traceItem: item,
    }))

  const entries: TimelineEntry[] = []
  if (startEvent) {
    entries.push({
      id: `timeline-event:${startEvent.id}`,
      event: startEvent,
      traceItem: null,
    })
  }
  entries.push(...taskEntries)
  if (terminalEvent) {
    entries.push({
      id: `timeline-event:${terminalEvent.id}`,
      event: terminalEvent,
      traceItem: null,
    })
  }
  return entries
}

const previewNodeTypes = {
  workflow: WorkflowNodeCard,
}

function resolvePreviewNodeKind(nodeType: string): WorkflowNodeData['kind'] {
  switch (nodeType) {
    case 'start':
    case 'approver':
    case 'subprocess':
    case 'dynamic-builder':
    case 'condition':
    case 'inclusive':
    case 'cc':
    case 'timer':
    case 'trigger':
    case 'parallel':
    case 'end':
      return nodeType
    default:
      return 'approver'
  }
}

function resolvePreviewNodeTone(nodeType: string): WorkflowNodeData['tone'] {
  if (nodeType === 'start') {
    return 'success'
  }
  if (nodeType === 'end') {
    return 'neutral'
  }
  if (nodeType === 'approver') {
    return 'brand'
  }
  return 'warning'
}

function findStartNodeId(flowNodes: WorkbenchFlowNode[]) {
  return flowNodes.find((node) => node.type === 'start')?.id ?? flowNodes[0]?.id ?? null
}

function buildOutgoingEdgeMap(flowEdges: WorkbenchFlowEdge[]) {
  return flowEdges.reduce<Map<string, WorkbenchFlowEdge[]>>((map, edge) => {
    const list = map.get(edge.source) ?? []
    list.push(edge)
    map.set(edge.source, list)
    return map
  }, new Map())
}

function findPathEdgeIds(
  flowEdges: WorkbenchFlowEdge[],
  sourceNodeId: string | null,
  targetNodeId: string | null
) {
  if (!sourceNodeId || !targetNodeId || sourceNodeId === targetNodeId) {
    return [] as string[]
  }

  const outgoingEdgeMap = buildOutgoingEdgeMap(flowEdges)
  const queue: Array<{ nodeId: string; edgeIds: string[] }> = [
    { nodeId: sourceNodeId, edgeIds: [] },
  ]
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

  return [] as string[]
}

function buildTraversalState(
  flowNodes: WorkbenchFlowNode[],
  flowEdges: WorkbenchFlowEdge[],
  focusNodeIds: string[],
  activeIndex: number
): TraversalState {
  const startNodeId = findStartNodeId(flowNodes)
  const sequence = [startNodeId, ...focusNodeIds].filter(
    (value, index, list): value is string => Boolean(value) && list.indexOf(value) === index
  )
  const edgeById = new Map(flowEdges.map((edge) => [edge.id, edge]))
  const visitedNodeIds = new Set<string>()
  const visitedEdgeIds = new Set<string>()
  const activeEdgeIds = new Set<string>()

  if (sequence.length === 0) {
    return { visitedNodeIds, visitedEdgeIds, activeEdgeIds }
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

  return { visitedNodeIds, visitedEdgeIds, activeEdgeIds }
}

function ApprovalSheetGraphInner({
  flowNodes,
  flowEdges,
  taskTrace,
  instanceEvents,
  instanceStatus,
  userDisplayNames,
}: ApprovalSheetGraphProps) {
  const [mode, setMode] = useState<'idle' | 'playing' | 'paused'>('idle')
  const [activeIndex, setActiveIndex] = useState(0)
  const [flowInstance, setFlowInstance] = useState<ReactFlowInstance<Node, Edge> | null>(null)
  const { resolvedTheme } = useTheme()

  // 播放态只负责按时间顺序推进高亮，不影响原始流程数据。
  const playbackEvents = useMemo(
    () => buildPlaybackEvents(flowNodes, instanceEvents, taskTrace, instanceStatus),
    [flowNodes, instanceEvents, instanceStatus, taskTrace]
  )

  useEffect(() => {
    if (mode !== 'playing' || playbackEvents.length === 0) {
      return undefined
    }

    const timer = window.setInterval(() => {
      setActiveIndex((current) => {
        if (current >= playbackEvents.length - 1) {
          window.clearInterval(timer)
          setMode('paused')
          return current
        }
        return current + 1
      })
    }, 1000)

    return () => window.clearInterval(timer)
  }, [mode, playbackEvents.length])

  const resolveCurrentTaskTraceItem = useMemo(() => {
    const pendingItems = taskTrace.filter(
      (item) => item.status === 'PENDING' || item.status === 'PENDING_CLAIM'
    )

    if (pendingItems.length === 0) {
      return null
    }

    const pendingAddSignItems = pendingItems.filter((item) => item.isAddSignTask)
    const focusItems = pendingAddSignItems.length > 0 ? pendingAddSignItems : pendingItems

    return [...focusItems].sort((left, right) => {
      const leftTime = new Date(
        left.receiveTime ?? left.handleStartTime ?? left.readTime ?? 0
      ).getTime()
      const rightTime = new Date(
        right.receiveTime ?? right.handleStartTime ?? right.readTime ?? 0
      ).getTime()
      return rightTime - leftTime
    })[0] ?? null
  }, [taskTrace])

  const timelineItems = useMemo(
    () => buildTimelineEntries(playbackEvents, taskTrace),
    [playbackEvents, taskTrace]
  )
  const flowNodeMap = useMemo(
    () => new Map(flowNodes.map((node) => [node.id, node])),
    [flowNodes]
  )

  const activePlaybackEvent = useMemo(() => {
    if (mode === 'playing') {
      return playbackEvents[activeIndex] ?? null
    }

    if (
      instanceStatus === 'COMPLETED' ||
      instanceStatus === 'REVOKED' ||
      instanceStatus === 'TERMINATED'
    ) {
      return playbackEvents[activeIndex] ?? playbackEvents[playbackEvents.length - 1] ?? null
    }

    if (resolveCurrentTaskTraceItem) {
      return (
        playbackEvents.find((event) => event.taskId === resolveCurrentTaskTraceItem.taskId)
        ?? playbackEvents.find((event) => event.nodeId === resolveCurrentTaskTraceItem.nodeId)
        ?? null
      )
    }

    return playbackEvents[activeIndex] ?? null
  }, [activeIndex, instanceStatus, mode, playbackEvents, resolveCurrentTaskTraceItem])

  const isStartPlaybackEvent = (event: PlaybackEvent) =>
    event.eventType === 'START_PROCESS' || event.eventType === 'INSTANCE_STARTED'

  const isTerminalPlaybackEvent = (event: PlaybackEvent) =>
    event.eventType === 'INSTANCE_COMPLETED' ||
    event.eventType === 'INSTANCE_REVOKED' ||
    event.eventType === 'INSTANCE_TERMINATED'
  const playbackFocusNodeIds = useMemo(() => {
    const activePathNodeIds = playbackEvents
      .slice(0, activeIndex + 1)
      .map((event) => event.nodeId)
      .filter(Boolean)

    if (mode === 'playing') {
      return activePathNodeIds
    }

    if (
      instanceStatus === 'COMPLETED' ||
      instanceStatus === 'REVOKED' ||
      instanceStatus === 'TERMINATED'
    ) {
      return playbackEvents.map((event) => event.nodeId).filter(Boolean)
    }

    const tracePathNodeIds = taskTrace
      .filter((item) => item.handleEndTime || item.status === 'PENDING' || item.status === 'PENDING_CLAIM')
      .map((item) => item.nodeId)
      .filter(Boolean)

    return tracePathNodeIds.length > 0 ? tracePathNodeIds : activePathNodeIds
  }, [activeIndex, instanceStatus, mode, playbackEvents, taskTrace])
  const activeNodeId = useMemo(() => {
    if (
      instanceStatus === 'REVOKED' ||
      instanceStatus === 'COMPLETED' ||
      instanceStatus === 'TERMINATED'
    ) {
      return activePlaybackEvent?.nodeId ?? null
    }
      return activePlaybackEvent?.nodeId
      ?? resolveCurrentTaskTraceItem?.nodeId
      ?? taskTrace[taskTrace.length - 1]?.nodeId
      ?? null
  }, [activePlaybackEvent?.nodeId, instanceStatus, resolveCurrentTaskTraceItem?.nodeId, taskTrace])

  const traversalState = useMemo(
    () => buildTraversalState(flowNodes, flowEdges, playbackFocusNodeIds, activeIndex),
    [activeIndex, flowEdges, flowNodes, playbackFocusNodeIds]
  )

  // 已完成节点用于标记流程跑过的路径。
  const completedNodeIds = useMemo(
    () =>
      new Set(
        taskTrace
          .filter((item) => Boolean(item.handleEndTime))
          .map((item) => item.nodeId)
      ),
    [taskTrace]
  )

  const visitedNodeIds = useMemo(() => {
    const next = new Set<string>(traversalState.visitedNodeIds)
    if (activeNodeId) {
      next.add(activeNodeId)
    }
    return next
  }, [activeNodeId, traversalState.visitedNodeIds])

  const nodes = useMemo<Node[]>(
    () =>
      flowNodes.map((node) => {
        const highlightActiveNode =
          node.id === activeNodeId &&
          (mode === 'playing' ||
            (instanceStatus !== 'COMPLETED' &&
              instanceStatus !== 'REVOKED' &&
              instanceStatus !== 'TERMINATED'))
        const isCompleted = completedNodeIds.has(node.id)
        const isVisited = visitedNodeIds.has(node.id)

        return {
          id: node.id,
          type: 'workflow',
          position: node.position,
          data: {
            kind: resolvePreviewNodeKind(node.type),
            label: node.name,
            description: '',
            tone: resolvePreviewNodeTone(node.type),
            config: {},
            previewStatus: highlightActiveNode
              ? 'ACTIVE'
              : isCompleted
                ? 'COMPLETED'
                : isVisited
                  ? 'VISITED'
                  : 'IDLE',
          } as WorkflowNodeData & {
            previewStatus: 'ACTIVE' | 'COMPLETED' | 'VISITED' | 'IDLE'
          },
          draggable: false,
          selectable: false,
          style: {
            width: Math.max(node.ui?.width ?? 220, 220),
          },
        }
      }),
    [activeNodeId, completedNodeIds, flowNodes, instanceStatus, mode, visitedNodeIds]
  )

  useEffect(() => {
    if (!flowInstance || !activeNodeId || mode !== 'playing') {
      return
    }

    const node = flowNodes.find((item) => item.id === activeNodeId)
    if (!node) {
      return
    }

    const width = Math.max(node.ui?.width ?? 220, 220)
    flowInstance.setCenter(node.position.x + width / 2, node.position.y + 44, {
      zoom: 0.82,
      duration: 450,
    })
  }, [activeNodeId, flowInstance, flowNodes, mode])

  const edges = useMemo<Edge[]>(
    () =>
      flowEdges.map((edge) => {
        const isActive = mode === 'playing' && traversalState.activeEdgeIds.has(edge.id)
        const isTraversed = traversalState.visitedEdgeIds.has(edge.id)
        const isPlaying = mode === 'playing'
        const edgeColor = isActive
          ? '#2563eb'
          : isTraversed
            ? '#16a34a'
            : '#94a3b8'

        return {
          id: edge.id,
          source: edge.source,
          target: edge.target,
          type: 'smoothstep',
          pathOptions: {
            borderRadius: 18,
            offset: 18,
          },
          animated: isPlaying && (isActive || isTraversed),
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: edgeColor,
          },
          style: {
            strokeWidth: isActive ? 2.6 : isTraversed ? 2 : 1.5,
            strokeDasharray: isPlaying && (isActive || isTraversed) ? '7 5' : undefined,
            strokeLinecap: 'round',
            stroke: edgeColor,
          },
        }
      }),
    [flowEdges, mode, traversalState.activeEdgeIds, traversalState.visitedEdgeIds]
  )

  return (
    <Card>
      <CardHeader>
        <CardTitle>流程图回顾</CardTitle>
        <CardDescription>实例详情内直接预览流程图，支持动画回顾和节点时序信息查看。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='flex flex-wrap items-center gap-2'>
          <Button
            type='button'
            variant='outline'
            onClick={() => {
              setActiveIndex(0)
              setMode('playing')
            }}
            disabled={playbackEvents.length === 0}
          >
            <Play />
            播放回顾
          </Button>
          <Button
            type='button'
            variant='outline'
            onClick={() => setMode('paused')}
            disabled={playbackEvents.length === 0}
          >
            <Pause />
            暂停回顾
          </Button>
          <Button
            type='button'
            variant='outline'
            onClick={() => {
              if (playbackEvents.length === 0) {
                return
              }
              setMode('playing')
            }}
            disabled={playbackEvents.length === 0}
          >
            <RotateCcw />
            继续回顾
          </Button>
          <Badge variant='secondary'>
            {mode === 'playing' ? '播放中' : mode === 'paused' ? '已暂停' : '待开始'}
          </Badge>
          <Badge variant='outline'>事件数 {playbackEvents.length}</Badge>
        </div>

        <div className='space-y-4'>
          <div className='min-w-0 overflow-hidden rounded-2xl border bg-background/80'>
            <div className='h-[460px] min-w-0'>
              <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={previewNodeTypes}
                onInit={setFlowInstance}
                fitView
                fitViewOptions={{ padding: 0.18 }}
                minZoom={0.45}
                maxZoom={1.5}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable={false}
                panOnDrag
                zoomOnDoubleClick={false}
                colorMode={resolvedTheme}
                proOptions={{ hideAttribution: true }}
              >
                <Controls showInteractive={false} position='bottom-left' />
                <Background variant={BackgroundVariant.Dots} gap={18} size={1.1} />
              </ReactFlow>
            </div>
          </div>

          <div className='min-w-0 rounded-lg border bg-muted/10 p-4'>
            <div className='mb-4 flex flex-wrap items-center justify-between gap-3'>
              <div>
                <p className='text-sm font-medium'>回顾时间轴</p>
                <p className='mt-1 text-xs text-muted-foreground'>
                  {activePlaybackEvent
                    ? `当前播放：${activePlaybackEvent.label} · ${formatApprovalSheetDateTime(activePlaybackEvent.occurredAt)}`
                    : '等待回顾数据'}
                </p>
              </div>
              {activePlaybackEvent ? (
                <ApprovalUserTag
                  userId={activePlaybackEvent.operatorUserId}
                  displayNames={userDisplayNames}
                  fallback='系统触发'
                />
              ) : null}
            </div>

            <div className='max-h-[360px] min-w-0 overflow-auto pr-2'>
              {timelineItems.length ? (
                <ol className='space-y-0'>
                  {timelineItems.map(({ id, event, traceItem }, index) => {
                    const active = traceItem?.taskId
                      ? traceItem.taskId === activePlaybackEvent?.taskId
                      : event.id === activePlaybackEvent?.id
                    const flowNode = flowNodeMap.get(traceItem?.nodeId ?? event.nodeId)
                    const collaborationNodeLabel = flowNode
                      ? resolveApprovalSheetCollaborationNodeLabel(flowNode.type)
                      : '--'
                    const showCollaborationNodeLabel =
                      Boolean(flowNode) && collaborationNodeLabel !== (flowNode?.type ?? '')

                    return (
                      <li
                        key={`${id}:${event.nodeId}:${index}`}
                        className='grid grid-cols-[124px_24px_minmax(0,1fr)] gap-3 pb-5 last:pb-0'
                      >
                        <div className='pt-0.5 text-right text-xs text-muted-foreground'>
                          <div className='font-medium text-foreground'>
                            {formatApprovalSheetDateTime(eventOccurredAt(event, traceItem))}
                          </div>
                          <div className='mt-1'>
                            {traceItem?.handleEndTime
                              ? '完成'
                              : traceItem?.handleStartTime
                                ? '处理中'
                                : traceItem?.receiveTime
                                  ? '已接收'
                                  : isTerminalPlaybackEvent(event)
                                    ? '结束'
                                    : isStartPlaybackEvent(event)
                                      ? '发起'
                                      : '待处理'}
                          </div>
                        </div>
                        <div className='flex flex-col items-center'>
                          <span
                            className={`mt-1 size-3 rounded-full border-2 border-background shadow-sm ring-4 ${
                              active
                                ? 'bg-sky-500 ring-sky-500/15'
                                : 'bg-emerald-500 ring-emerald-500/10'
                            }`}
                          />
                          {index < timelineItems.length - 1 ? (
                            <span className='mt-2 h-full min-h-10 w-px bg-border' />
                          ) : null}
                        </div>
                        <div
                          className={`space-y-2 border-l pl-4 ${
                            active ? 'border-sky-300' : 'border-border'
                          }`}
                        >
                          <div className='flex flex-wrap items-center gap-2'>
                            <span className='font-medium leading-none'>
                              {traceItem?.nodeName ?? event.label}
                            </span>
                            {showCollaborationNodeLabel ? (
                              <Badge variant='outline'>
                                {collaborationNodeLabel}
                              </Badge>
                            ) : null}
                            <Badge variant={active ? 'default' : 'secondary'}>
                              {traceItem
                                ? resolveApprovalSheetResultLabel(traceItem)
                                : isTerminalPlaybackEvent(event)
                                  ? event.eventType === 'INSTANCE_REVOKED'
                                    ? '已撤销'
                                    : event.eventType === 'INSTANCE_TERMINATED'
                                      ? '已终止'
                                      : '已完成'
                                  : isStartPlaybackEvent(event)
                                    ? '发起'
                                    : '事件'}
                            </Badge>
                          </div>

                          <div className='flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground'>
                            <span className='flex items-center gap-1'>
                              办理人：
                              <ApprovalUserTag
                                userId={
                                  traceItem?.assigneeUserId
                                  ?? traceItem?.operatorUserId
                                  ?? event.operatorUserId
                                }
                                displayNames={userDisplayNames}
                                fallback={isStartPlaybackEvent(event) ? '发起人' : '--'}
                              />
                            </span>
                            {traceItem?.candidateUserIds?.length ? (
                              <span className='flex items-center gap-1'>
                                候选人：
                                <ApprovalTagList
                                  ids={traceItem.candidateUserIds}
                                  displayNames={userDisplayNames}
                                />
                              </span>
                            ) : null}
                            {traceItem ? (
                              <>
                                <span>接收：{formatApprovalSheetDateTime(traceItem.receiveTime)}</span>
                                <span>读取：{formatApprovalSheetDateTime(traceItem.readTime)}</span>
                                <span>开始：{formatApprovalSheetDateTime(traceItem.handleStartTime)}</span>
                                <span>完成：{formatApprovalSheetDateTime(traceItem.handleEndTime)}</span>
                                <span>时长：{formatApprovalSheetDuration(traceItem.handleDurationSeconds)}</span>
                                <span>超时：{formatApprovalSheetBoolean(false)}</span>
                              </>
                            ) : isStartPlaybackEvent(event) ? (
                              <span>发起时间：{formatApprovalSheetDateTime(event.occurredAt)}</span>
                            ) : isTerminalPlaybackEvent(event) ? (
                              <span>结束时间：{formatApprovalSheetDateTime(event.occurredAt)}</span>
                            ) : (
                              <span>发生时间：{formatApprovalSheetDateTime(event.occurredAt)}</span>
                            )}
                          </div>

                          {(traceItem?.actingMode ||
                            traceItem?.actingForUserId ||
                            traceItem?.delegatedByUserId ||
                            traceItem?.handoverFromUserId) ? (
                            <div className='flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground'>
                              <span>办理模式：{resolveApprovalSheetActingModeLabel(traceItem?.actingMode)}</span>
                              {traceItem?.actingForUserId ? (
                                <span className='flex items-center gap-1'>
                                  代谁办理：
                                  <ApprovalUserTag
                                    userId={traceItem.actingForUserId}
                                    displayNames={userDisplayNames}
                                  />
                                </span>
                              ) : null}
                              {traceItem?.delegatedByUserId ? (
                                <span className='flex items-center gap-1'>
                                  委派来源：
                                  <ApprovalUserTag
                                    userId={traceItem.delegatedByUserId}
                                    displayNames={userDisplayNames}
                                  />
                                </span>
                              ) : null}
                              {traceItem?.handoverFromUserId ? (
                                <span className='flex items-center gap-1'>
                                  离职转办来源：
                                  <ApprovalUserTag
                                    userId={traceItem.handoverFromUserId}
                                    displayNames={userDisplayNames}
                                  />
                                </span>
                              ) : null}
                            </div>
                          ) : null}

                          <p className='text-sm text-foreground/90'>
                            审批意见：
                            {traceItem
                              ? formatApprovalSheetText(traceItem.comment)
                              : isStartPlaybackEvent(event)
                                ? '流程发起'
                                : isTerminalPlaybackEvent(event)
                                  ? '流程结束'
                                  : '--'}
                          </p>
                        </div>
                      </li>
                    )
                  })}
                </ol>
              ) : (
                <div className='rounded-lg border border-dashed p-6 text-sm text-muted-foreground'>
                  当前实例还没有审批轨迹。
                </div>
              )}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

export function ApprovalSheetGraph(props: ApprovalSheetGraphProps) {
  return (
    <ReactFlowProvider>
      <ApprovalSheetGraphInner {...props} />
    </ReactFlowProvider>
  )
}
