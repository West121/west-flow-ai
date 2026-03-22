import { useEffect, useMemo, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  MarkerType,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  type Edge,
  type Node,
} from '@xyflow/react'
import { Pause, Play, RotateCcw } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  type WorkbenchFlowEdge,
  type WorkbenchFlowNode,
  type WorkbenchProcessInstanceEvent,
  type WorkbenchTaskTraceItem,
} from '@/lib/api/workbench'
import {
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
}

type PlaybackEvent = {
  id: string
  nodeId: string
  label: string
  occurredAt: string | null
  operatorUserId: string | null
}

// 回顾优先使用实例事件；如果后端没有给足事件，再回退到任务轨迹。
// 先从实例事件构造播放序列，不够时再回退到任务轨迹。
function buildPlaybackEvents(
  instanceEvents: WorkbenchProcessInstanceEvent[],
  taskTrace: WorkbenchTaskTraceItem[]
): PlaybackEvent[] {
  const directEvents = instanceEvents
    .filter((event) => Boolean(event.nodeId))
    .map((event) => ({
      id: event.eventId,
      nodeId: event.nodeId ?? '',
      label: event.eventName,
      occurredAt: event.occurredAt,
      operatorUserId: event.operatorUserId ?? null,
    }))

  if (directEvents.length > 0) {
    return directEvents
  }

  return taskTrace.map((item) => ({
    id: item.taskId,
    nodeId: item.nodeId,
    label: resolveApprovalSheetResultLabel(item),
    occurredAt: item.handleEndTime ?? item.handleStartTime ?? item.receiveTime ?? null,
    operatorUserId: item.operatorUserId ?? item.assigneeUserId ?? null,
  }))
}

// 节点基础样式按类型区分，方便回顾时快速识别。
function baseNodeStyle(nodeType: string) {
  if (nodeType === 'start') {
    return {
      borderColor: 'rgb(34 197 94)',
      backgroundColor: 'rgba(34, 197, 94, 0.08)',
    }
  }

  if (nodeType === 'end') {
    return {
      borderColor: 'rgb(100 116 139)',
      backgroundColor: 'rgba(100, 116, 139, 0.08)',
    }
  }

  if (nodeType === 'approver') {
    return {
      borderColor: 'rgb(245 158 11)',
      backgroundColor: 'rgba(245, 158, 11, 0.08)',
    }
  }

  return {
    borderColor: 'rgb(59 130 246)',
    backgroundColor: 'rgba(59, 130, 246, 0.06)',
  }
}

// 流程图回顾组件负责高亮当前节点和已走过的路径。
function ApprovalSheetGraphInner({
  flowNodes,
  flowEdges,
  taskTrace,
  instanceEvents,
}: ApprovalSheetGraphProps) {
  const [mode, setMode] = useState<'idle' | 'playing' | 'paused'>('idle')
  const [activeIndex, setActiveIndex] = useState(0)

  // 播放态只负责按时间顺序推进高亮，不影响原始流程数据。
  const playbackEvents = useMemo(
    () => buildPlaybackEvents(instanceEvents, taskTrace),
    [instanceEvents, taskTrace]
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

  const activePlaybackEvent = playbackEvents[activeIndex] ?? null
  const activeNodeId = activePlaybackEvent?.nodeId
    ?? taskTrace.find((item) => item.status === 'PENDING' || item.status === 'PENDING_CLAIM')?.nodeId
    ?? taskTrace[taskTrace.length - 1]?.nodeId
    ?? null

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

  const visitedNodeIds = useMemo(
    () =>
      new Set(
        playbackEvents
          .slice(0, activeIndex + 1)
          .map((event) => event.nodeId)
          .filter(Boolean)
      ),
    [activeIndex, playbackEvents]
  )

  const nodes = useMemo<Node[]>(
    () =>
      flowNodes.map((node) => {
        const baseStyle = baseNodeStyle(node.type)
        const isActive = node.id === activeNodeId
        const isCompleted = completedNodeIds.has(node.id)
        const isVisited = visitedNodeIds.has(node.id)

        // 节点样式只表达三件事：当前、已完成、已经过。
        return {
          id: node.id,
          position: node.position,
          data: {
            label: `${node.name}\n${isActive ? '当前节点' : isCompleted ? '已完成' : isVisited ? '已到达' : '未到达'}`,
          },
          draggable: false,
          selectable: false,
          style: {
            width: Math.max(node.ui?.width ?? 180, 180),
            minHeight: Math.max(node.ui?.height ?? 72, 72),
            borderWidth: isActive ? 2 : 1,
            borderStyle: 'solid',
            borderColor: isActive
              ? 'rgb(59 130 246)'
              : isCompleted
                ? 'rgb(16 185 129)'
                : baseStyle.borderColor,
            backgroundColor: isActive
              ? 'rgba(59, 130, 246, 0.12)'
              : isCompleted
                ? 'rgba(16, 185, 129, 0.10)'
                : baseStyle.backgroundColor,
            borderRadius: 18,
            boxShadow: isActive
              ? '0 0 0 4px rgba(59, 130, 246, 0.12)'
              : '0 8px 24px rgba(15, 23, 42, 0.06)',
            whiteSpace: 'pre-line',
            fontSize: 13,
            lineHeight: 1.5,
            padding: 12,
          },
        }
      }),
    [activeNodeId, completedNodeIds, flowNodes, visitedNodeIds]
  )

  const edges = useMemo<Edge[]>(
    () =>
      flowEdges.map((edge) => {
        const targetCompleted = completedNodeIds.has(edge.target)
        const targetVisited = visitedNodeIds.has(edge.target)
        const isActive = edge.target === activeNodeId

        // 边的颜色跟随目标节点状态变化，便于看出流转路径。
        return {
          id: edge.id,
          source: edge.source,
          target: edge.target,
          label: edge.label ?? undefined,
          animated: isActive,
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: isActive || targetCompleted || targetVisited ? '#2563eb' : '#94a3b8',
          },
          style: {
            strokeWidth: isActive ? 2.5 : 1.5,
            stroke: isActive
              ? '#2563eb'
              : targetCompleted || targetVisited
                ? '#16a34a'
                : '#94a3b8',
          },
          labelStyle: {
            fill: '#475569',
            fontSize: 12,
          },
        }
      }),
    [activeNodeId, completedNodeIds, flowEdges, visitedNodeIds]
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

        <div className='grid gap-4 xl:grid-cols-[minmax(0,1.25fr)_360px]'>
          <div className='h-[440px] overflow-hidden rounded-2xl border bg-background/80'>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              fitView
              minZoom={0.5}
              maxZoom={1.5}
              nodesDraggable={false}
              nodesConnectable={false}
              elementsSelectable={false}
              panOnDrag
              zoomOnDoubleClick={false}
              proOptions={{ hideAttribution: true }}
            >
              <MiniMap
                pannable
                zoomable
                className='rounded-xl border bg-background/90'
              />
              <Controls showInteractive={false} position='bottom-left' />
              <Background variant={BackgroundVariant.Dots} gap={18} size={1.1} />
            </ReactFlow>
          </div>

          <div className='space-y-4'>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-sm font-medium'>当前播放事件</p>
              <p className='mt-2 text-sm'>
                {activePlaybackEvent
                  ? `${activePlaybackEvent.label} · ${formatApprovalSheetText(activePlaybackEvent.operatorUserId)}`
                  : '暂无实例事件'}
              </p>
              <p className='mt-1 text-xs text-muted-foreground'>
                {activePlaybackEvent
                  ? formatApprovalSheetDateTime(activePlaybackEvent.occurredAt)
                  : '等待回顾数据'}
              </p>
            </div>

            <div className='max-h-[440px] space-y-3 overflow-y-auto pr-1'>
              {taskTrace.length ? (
                taskTrace.map((item, index) => {
                  const active = item.nodeId === activeNodeId

                  return (
                    <div
                      key={`${item.taskId}:${item.nodeId}:${index}`}
                      className={`rounded-lg border p-4 ${
                        active ? 'border-primary bg-primary/5' : 'bg-background'
                      }`}
                    >
                      <div className='flex flex-wrap items-center justify-between gap-3'>
                        <div>
                          <p className='font-medium'>{item.nodeName}</p>
                          <p className='text-xs text-muted-foreground'>节点 ID：{item.nodeId}</p>
                        </div>
                        <Badge variant={active ? 'default' : 'secondary'}>
                          {resolveApprovalSheetResultLabel(item)}
                        </Badge>
                      </div>

                      <dl className='mt-4 grid gap-2 text-sm'>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>办理人</dt>
                          <dd>{formatApprovalSheetText(item.assigneeUserId ?? item.operatorUserId)}</dd>
                        </div>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>读取时间</dt>
                          <dd>{formatApprovalSheetDateTime(item.readTime)}</dd>
                        </div>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>接收时间</dt>
                          <dd>{formatApprovalSheetDateTime(item.receiveTime)}</dd>
                        </div>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>办理开始时间</dt>
                          <dd>{formatApprovalSheetDateTime(item.handleStartTime)}</dd>
                        </div>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>办理完成时间</dt>
                          <dd>{formatApprovalSheetDateTime(item.handleEndTime)}</dd>
                        </div>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>办理时长</dt>
                          <dd>{formatApprovalSheetDuration(item.handleDurationSeconds)}</dd>
                        </div>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>是否超时</dt>
                          <dd>{formatApprovalSheetBoolean(false)}</dd>
                        </div>
                        <div className='flex justify-between gap-3'>
                          <dt className='text-muted-foreground'>审批意见摘要</dt>
                          <dd>{formatApprovalSheetText(item.comment)}</dd>
                        </div>
                      </dl>
                    </div>
                  )
                })
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
