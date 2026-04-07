import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { getWorkbenchReviewTicketDetail } from '@/lib/api/workbench'
import { formatApprovalSheetDateTime } from '@/features/workbench/approval-sheet-helpers'

export const Route = createFileRoute('/review/weapp/$ticket')({
  component: ReviewWeappPlayerRoute,
})

type ReviewDetail = Awaited<ReturnType<typeof getWorkbenchReviewTicketDetail>>

type PlaybackEvent = {
  id: string
  nodeId: string
  taskId: string | null
  label: string
  occurredAt: string | null
  eventType: string
}

function ReviewWeappPlayerRoute() {
  const { ticket } = Route.useParams()
  const [mode, setMode] = useState<'idle' | 'playing' | 'paused'>('idle')
  const [activeIndex, setActiveIndex] = useState(0)

  const detailQuery = useQuery({
    queryKey: ['review-weapp-player', ticket],
    queryFn: () => getWorkbenchReviewTicketDetail(ticket),
  })

  const detail = detailQuery.data ?? null
  const playbackEvents = useMemo(
    () => (detail ? buildPlaybackEvents(detail) : []),
    [detail]
  )

  useEffect(() => {
    setActiveIndex(0)
    setMode('idle')
  }, [ticket])

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
    }, 900)
    return () => window.clearInterval(timer)
  }, [mode, playbackEvents.length])

  if (detailQuery.isLoading) {
    return (
      <div style={styles.page}>
        <div style={styles.loadingPill}>正在加载流程回顾...</div>
      </div>
    )
  }

  if (detailQuery.isError || !detail) {
    return (
      <div style={styles.page}>
        <div style={styles.errorCard}>
          <div style={styles.errorTitle}>流程回顾不可用</div>
          <div style={styles.errorText}>当前票据已失效或对应任务不存在。请回到小程序审批详情页重新进入。</div>
        </div>
      </div>
    )
  }

  const graph = buildCompatGraph(detail, playbackEvents, activeIndex, mode)
  const timeline = buildTimeline(detail)

  return (
    <div style={styles.page}>
      <div style={styles.shell}>
        <div style={styles.headerCard}>
          <div style={styles.kicker}>流程回顾</div>
          <div style={styles.titleRow}>
            <div style={styles.title}>{detail.processName}</div>
            <div style={styles.badge}>只读回放</div>
          </div>
          <div style={styles.description}>通过小程序只读播放器查看节点高亮、时间轴与动态回顾。</div>
        </div>

        <div style={styles.playerCard}>
          <div style={styles.playerCaption}>{detail.processName} · 只读播放器</div>
          <div style={styles.canvasOuter}>
            <div style={styles.canvasInner}>
              <svg
                width={graph.width}
                height={graph.height}
                viewBox={`0 0 ${graph.width} ${graph.height}`}
                style={styles.graphSvg}
              >
                {graph.edges.map((edge) => (
                  <path
                    key={edge.id}
                    d={edge.d}
                    stroke={edge.color}
                    strokeWidth={edge.width}
                    strokeLinecap='round'
                    fill='none'
                  />
                ))}
              </svg>
              {graph.nodes.map((node) => (
                <div
                  key={node.id}
                  style={{
                    ...styles.graphNode,
                    ...(node.previewStatus === 'active'
                      ? styles.graphNodeActive
                      : node.previewStatus === 'completed'
                        ? styles.graphNodeCompleted
                        : styles.graphNodeIdle),
                    left: node.left,
                    top: node.top,
                    width: node.width,
                    minHeight: node.height,
                  }}
                >
                  <div style={styles.graphNodeTitle}>{node.name}</div>
                  <div style={styles.graphNodeMeta}>{node.badgeLabel}</div>
                </div>
              ))}
            </div>
          </div>

          <div style={styles.controlRow}>
            <button
              type='button'
              style={styles.playButton}
              onClick={() => {
                setActiveIndex(0)
                setMode('playing')
              }}
            >
              ▶
            </button>
            <div style={styles.progressText}>
              {String(Math.min(activeIndex + 1, playbackEvents.length || 0)).padStart(2, '0')} / {playbackEvents.length} 节点
            </div>
            <button
              type='button'
              style={styles.ghostButton}
              onClick={() => setMode(mode === 'playing' ? 'paused' : 'playing')}
            >
              {mode === 'playing' ? '暂停' : '继续'}
            </button>
            <div style={styles.speedBadge}>1x</div>
          </div>
        </div>

        <div style={styles.timelineCard}>
          <div style={styles.timelineTitle}>时间轴</div>
          <div style={styles.timelineList}>
            {timeline.map((item) => (
              <div key={item.id} style={styles.timelineItem}>
                <div style={styles.timelineTime}>{formatApprovalSheetDateTime(item.occurredAt)}</div>
                <div style={styles.timelineBody}>
                  <div style={styles.timelineItemTitle}>{item.title}</div>
                  <div
                    style={{
                      ...styles.timelineStatus,
                      ...(item.tone === 'completed' ? styles.timelineStatusDone : styles.timelineStatusActive),
                    }}
                  >
                    {item.status}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function buildPlaybackEvents(detail: ReviewDetail): PlaybackEvent[] {
  const flowNodes = detail.flowNodes ?? []
  const taskTrace = detail.taskTrace ?? []
  const instanceEvents = detail.instanceEvents ?? []
  const startNodeId = flowNodes.find((node) => node.type === 'start')?.id ?? flowNodes[0]?.id ?? ''

  const startEvent = instanceEvents.find(
    (event) => event.eventType === 'START_PROCESS' || event.eventType === 'INSTANCE_STARTED'
  )

  const events: PlaybackEvent[] = []
  if (startEvent && startNodeId) {
    events.push({
      id: startEvent.eventId,
      nodeId: startNodeId,
      taskId: null,
      label: startEvent.eventName,
      occurredAt: startEvent.occurredAt,
      eventType: startEvent.eventType,
    })
  }

  for (const item of taskTrace) {
    events.push({
      id: `task:${item.taskId}`,
      nodeId: item.nodeId,
      taskId: item.taskId,
      label: item.nodeName,
      occurredAt: item.handleEndTime ?? item.handleStartTime ?? item.receiveTime ?? null,
      eventType: 'TASK_TRACE',
    })
  }

  return events
    .filter((event) => Boolean(event.nodeId))
    .sort((left, right) => {
      const leftTime = left.occurredAt ? new Date(left.occurredAt).getTime() : Number.MAX_SAFE_INTEGER
      const rightTime = right.occurredAt ? new Date(right.occurredAt).getTime() : Number.MAX_SAFE_INTEGER
      return leftTime - rightTime
    })
}

function buildTimeline(detail: ReviewDetail) {
  const entries = (detail.taskTrace ?? []).map((item) => ({
    id: item.taskId,
    title: item.nodeName,
    occurredAt: item.handleEndTime ?? item.handleStartTime ?? item.receiveTime ?? null,
    status: item.status === 'COMPLETED' ? '已完成' : '进行中',
    tone: item.status === 'COMPLETED' ? 'completed' : 'active',
  }))

  const startEvent = (detail.instanceEvents ?? []).find(
    (event) => event.eventType === 'START_PROCESS' || event.eventType === 'INSTANCE_STARTED'
  )
  if (startEvent) {
    entries.unshift({
      id: startEvent.eventId,
      title: startEvent.eventName,
      occurredAt: startEvent.occurredAt,
      status: '已发起',
      tone: 'completed',
    })
  }
  return entries
}

function buildCompatGraph(
  detail: ReviewDetail,
  playbackEvents: PlaybackEvent[],
  activeIndex: number,
  mode: 'idle' | 'playing' | 'paused'
) {
  const flowNodes = detail.flowNodes ?? []
  const flowEdges = detail.flowEdges ?? []
  const completedNodeIds = new Set((detail.taskTrace ?? []).filter((item) => item.handleEndTime).map((item) => item.nodeId))
  const focusNodeIds = playbackEvents.slice(0, mode === 'idle' ? 1 : activeIndex + 1).map((item) => item.nodeId)
  const activeNodeId = playbackEvents[Math.min(activeIndex, Math.max(playbackEvents.length - 1, 0))]?.nodeId ?? null

  const positionedNodes = flowNodes.map((node) => {
    const width = Math.max(node.ui?.width ?? 220, 220)
    const height = Math.max(node.ui?.height ?? 88, 88)
    const isActive = node.id === activeNodeId && mode !== 'idle'
    const isCompleted = completedNodeIds.has(node.id) || focusNodeIds.includes(node.id)
    return {
      ...node,
      width,
      height,
      previewStatus: isActive ? 'active' : isCompleted ? 'completed' : 'idle',
      badgeLabel: resolveNodeBadgeLabel(node.type),
    }
  })

  const minX = Math.min(...positionedNodes.map((node) => node.position.x), 0)
  const minY = Math.min(...positionedNodes.map((node) => node.position.y), 0)
  const maxX = Math.max(...positionedNodes.map((node) => node.position.x + node.width), 0)
  const maxY = Math.max(...positionedNodes.map((node) => node.position.y + node.height), 0)
  const padding = 28
  const width = maxX - minX + padding * 2
  const height = maxY - minY + padding * 2

  const nodes = positionedNodes.map((node) => ({
    ...node,
    left: node.position.x - minX + padding,
    top: node.position.y - minY + padding,
  }))

  const nodeMap = new Map(nodes.map((node) => [node.id, node]))
  const completedEdgeIds = new Set<string>()
  for (const edge of flowEdges) {
    if (focusNodeIds.includes(edge.source) && focusNodeIds.includes(edge.target)) {
      completedEdgeIds.add(edge.id)
    }
  }

  const edges = flowEdges
    .map((edge) => {
      const source = nodeMap.get(edge.source)
      const target = nodeMap.get(edge.target)
      if (!source || !target) {
        return null
      }
      const startX = source.left + source.width / 2
      const startY = source.top + source.height
      const endX = target.left + target.width / 2
      const endY = target.top
      const midY = startY + (endY - startY) / 2
      const isCompleted = completedEdgeIds.has(edge.id)
      return {
        id: edge.id,
        d: `M ${startX} ${startY} C ${startX} ${midY}, ${endX} ${midY}, ${endX} ${endY}`,
        color: isCompleted ? '#2563eb' : '#cbd5e1',
        width: isCompleted ? 2.8 : 1.6,
      }
    })
    .filter(Boolean) as Array<{ id: string; d: string; color: string; width: number }>

  return { width, height, nodes, edges }
}

function resolveNodeBadgeLabel(type: string) {
  switch (type) {
    case 'start':
      return '开始'
    case 'end':
      return '结束'
    case 'condition':
      return '排他'
    case 'inclusive':
      return '包容'
    case 'parallel':
      return '并行'
    default:
      return '审批'
  }
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    minHeight: '100vh',
    margin: 0,
    padding: '20px 14px 28px',
    boxSizing: 'border-box',
    background: 'radial-gradient(circle at top, rgba(226,232,240,0.9), rgba(248,250,252,1) 52%)',
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Helvetica Neue", sans-serif',
    color: '#0f172a',
  },
  shell: {
    maxWidth: 430,
    margin: '0 auto',
    display: 'flex',
    flexDirection: 'column',
    gap: 14,
  },
  loadingPill: {
    margin: '120px auto 0',
    padding: '14px 20px',
    borderRadius: 999,
    background: 'rgba(255,255,255,0.92)',
    border: '1px solid rgba(226,232,240,0.9)',
    boxShadow: '0 16px 40px rgba(15,23,42,0.08)',
    fontSize: 14,
    fontWeight: 600,
  },
  errorCard: {
    margin: '80px auto 0',
    maxWidth: 520,
    background: 'rgba(255,255,255,0.96)',
    border: '1px solid rgba(251,191,36,0.38)',
    borderRadius: 28,
    padding: '24px 22px',
    boxShadow: '0 20px 44px rgba(15,23,42,0.08)',
  },
  errorTitle: {
    fontSize: 20,
    fontWeight: 800,
    marginBottom: 10,
  },
  errorText: {
    fontSize: 14,
    lineHeight: '22px',
    color: '#64748b',
  },
  headerCard: {
    background: 'rgba(255,255,255,0.92)',
    border: '1px solid rgba(226,232,240,0.9)',
    borderRadius: 34,
    padding: '24px 22px 18px',
    boxShadow: '0 18px 44px rgba(148,163,184,0.11)',
  },
  kicker: {
    fontSize: 12,
    fontWeight: 700,
    color: '#64748b',
    marginBottom: 12,
  },
  titleRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    flexWrap: 'wrap',
  },
  title: {
    fontSize: 32,
    lineHeight: '36px',
    fontWeight: 900,
    letterSpacing: '-0.03em',
  },
  badge: {
    padding: '7px 12px',
    borderRadius: 999,
    background: 'rgba(255,255,255,0.88)',
    border: '1px solid rgba(226,232,240,0.9)',
    color: '#475569',
    fontSize: 12,
    fontWeight: 700,
  },
  description: {
    marginTop: 10,
    fontSize: 14,
    lineHeight: '22px',
    color: '#64748b',
  },
  playerCard: {
    background: 'rgba(255,255,255,0.88)',
    border: '1px solid rgba(226,232,240,0.85)',
    borderRadius: 34,
    padding: 16,
    boxShadow: '0 18px 44px rgba(148,163,184,0.12)',
  },
  playerCaption: {
    fontSize: 14,
    fontWeight: 700,
    color: '#64748b',
    marginBottom: 10,
  },
  canvasOuter: {
    borderRadius: 30,
    border: '1px solid rgba(226,232,240,0.85)',
    background: 'linear-gradient(180deg, rgba(255,255,255,0.96), rgba(246,249,252,0.9))',
    padding: 14,
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.9)',
    overflowX: 'auto',
  },
  canvasInner: {
    position: 'relative',
    minHeight: 360,
  },
  graphSvg: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  graphNode: {
    position: 'absolute',
    borderRadius: 22,
    border: '1px solid rgba(226,232,240,0.92)',
    padding: '14px 14px 12px',
    boxSizing: 'border-box',
    background: 'rgba(255,255,255,0.96)',
    boxShadow: '0 10px 24px rgba(148,163,184,0.14)',
  },
  graphNodeIdle: {
    background: 'rgba(255,255,255,0.96)',
  },
  graphNodeCompleted: {
    background: 'rgba(239,246,255,0.96)',
    border: '1px solid rgba(96,165,250,0.45)',
  },
  graphNodeActive: {
    background: 'rgba(219,234,254,0.98)',
    border: '1px solid rgba(59,130,246,0.7)',
    boxShadow: '0 12px 28px rgba(59,130,246,0.16)',
  },
  graphNodeTitle: {
    fontSize: 15,
    lineHeight: '21px',
    fontWeight: 800,
    color: '#0f172a',
    marginBottom: 6,
  },
  graphNodeMeta: {
    fontSize: 12,
    lineHeight: '18px',
    fontWeight: 700,
    color: '#64748b',
  },
  controlRow: {
    marginTop: 14,
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  playButton: {
    width: 44,
    height: 44,
    borderRadius: 999,
    background: '#0f172a',
    color: '#fff',
    border: 'none',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 20,
    fontWeight: 800,
    boxShadow: '0 12px 24px rgba(15,23,42,0.18)',
  },
  ghostButton: {
    height: 38,
    borderRadius: 999,
    border: '1px solid rgba(226,232,240,0.9)',
    background: 'rgba(255,255,255,0.88)',
    color: '#334155',
    padding: '0 14px',
    fontSize: 13,
    fontWeight: 700,
  },
  progressText: {
    fontSize: 18,
    fontWeight: 700,
    color: '#334155',
  },
  speedBadge: {
    marginLeft: 'auto',
    padding: '8px 12px',
    borderRadius: 999,
    background: 'rgba(255,255,255,0.9)',
    border: '1px solid rgba(226,232,240,0.9)',
    fontSize: 12,
    fontWeight: 700,
    color: '#475569',
  },
  timelineCard: {
    background: 'rgba(255,255,255,0.88)',
    border: '1px solid rgba(226,232,240,0.85)',
    borderRadius: 34,
    padding: '18px 16px',
    boxShadow: '0 18px 44px rgba(148,163,184,0.12)',
  },
  timelineTitle: {
    fontSize: 18,
    lineHeight: '24px',
    fontWeight: 900,
    marginBottom: 14,
  },
  timelineList: {
    display: 'flex',
    flexDirection: 'column',
    gap: 10,
  },
  timelineItem: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    borderRadius: 20,
    border: '1px solid rgba(226,232,240,0.8)',
    background: 'rgba(248,250,252,0.92)',
    padding: '14px 14px',
  },
  timelineTime: {
    fontSize: 13,
    color: '#64748b',
    flexShrink: 0,
  },
  timelineBody: {
    minWidth: 0,
    flex: 1,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  timelineItemTitle: {
    fontSize: 15,
    fontWeight: 800,
    color: '#0f172a',
  },
  timelineStatus: {
    flexShrink: 0,
    padding: '7px 10px',
    borderRadius: 999,
    fontSize: 12,
    fontWeight: 800,
  },
  timelineStatusDone: {
    background: 'rgba(16,185,129,0.12)',
    color: '#047857',
  },
  timelineStatusActive: {
    background: 'rgba(59,130,246,0.12)',
    color: '#1d4ed8',
  },
}
