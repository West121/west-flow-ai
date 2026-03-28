import {
  BellRing,
  BookOpenText,
  Clock3,
  Flag,
  GitBranch,
  GitMerge,
  Play,
  SendHorizontal,
  ShieldAlert,
  Zap,
  UserRoundCheck,
  UsersRound,
} from 'lucide-react'
import { Handle, Position, type NodeProps } from '@xyflow/react'
import { cn } from '@/lib/utils'
import {
  type WorkflowApproverNodeConfig,
  type WorkflowNode,
  type WorkflowNodeData,
} from './types'
import { resolveWorkflowCollaborationNodeLabel } from './collaboration'

const toneClassNames = {
  brand: {
    ring: 'ring-sky-500/30',
    badge: 'bg-sky-500/12 text-sky-700 dark:text-sky-300',
    icon: 'bg-sky-500/15 text-sky-700 dark:text-sky-200',
  },
  success: {
    ring: 'ring-emerald-500/30',
    badge: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    icon: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-200',
  },
  warning: {
    ring: 'ring-amber-500/30',
    badge: 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
    icon: 'bg-amber-500/15 text-amber-700 dark:text-amber-200',
  },
  neutral: {
    ring: 'ring-slate-500/25',
    badge: 'bg-slate-500/12 text-slate-700 dark:text-slate-300',
    icon: 'bg-slate-500/15 text-slate-700 dark:text-slate-200',
  },
} satisfies Record<string, Record<string, string>>

const previewStatusClassNames = {
  ACTIVE: {
    card: 'border-sky-300 bg-sky-50/80 shadow-sm dark:border-sky-800 dark:bg-sky-950/20',
    ring: 'ring-sky-500/30',
    badge: 'bg-sky-500/12 text-sky-700 dark:text-sky-300',
    icon: 'bg-sky-500/15 text-sky-700 dark:text-sky-200',
  },
  COMPLETED: {
    card: 'border-emerald-300 bg-emerald-50/80 shadow-sm dark:border-emerald-800 dark:bg-emerald-950/20',
    ring: 'ring-emerald-500/30',
    badge: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    icon: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-200',
  },
  VISITED: {
    card: 'border-emerald-300 bg-emerald-50/80 shadow-sm dark:border-emerald-800 dark:bg-emerald-950/20',
    ring: 'ring-emerald-500/30',
    badge: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    icon: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-200',
  },
} as const

const kindBadgeLabels = {
  start: '开始',
  approver: '审批',
  subprocess: '子流程',
  condition: '排他',
  inclusive: '包容',
  'dynamic-builder': '动态构建',
  parallel: '并行',
  cc: '抄送',
  supervise: '督办',
  meeting: '会办',
  read: '阅办',
  circulate: '传阅',
  timer: '定时',
  trigger: '触发',
  end: '结束',
} satisfies Record<WorkflowNodeData['kind'], string>

function resolveNodeMarker(data: WorkflowNodeData) {
  if (data.kind === 'approver') {
    const approverConfig = data.config as WorkflowApproverNodeConfig
    switch (approverConfig.approvalMode) {
      case 'PARALLEL':
        return { label: '并签', tone: 'brand' as const }
      case 'SEQUENTIAL':
        return { label: '串签', tone: 'neutral' as const }
      case 'OR_SIGN':
        return { label: '或签', tone: 'warning' as const }
      case 'VOTE':
        return { label: '票签', tone: 'success' as const }
      default:
        return null
    }
  }
  return null
}

// 不同节点种类用不同图标，帮助画布识别。
function renderIcon(kind: WorkflowNodeData['kind']) {
  switch (kind) {
    case 'start':
      return <Play className='size-5' />
    case 'approver':
      return <UserRoundCheck className='size-5' />
    case 'condition':
      return <GitBranch className='size-5' />
    case 'inclusive':
      return <GitMerge className='size-5' />
    case 'dynamic-builder':
      return <GitBranch className='size-5' />
    case 'parallel':
      return <GitMerge className='size-5' />
    case 'cc':
      return <BellRing className='size-5' />
    case 'supervise':
      return <ShieldAlert className='size-5' />
    case 'meeting':
      return <UsersRound className='size-5' />
    case 'read':
      return <BookOpenText className='size-5' />
    case 'circulate':
      return <SendHorizontal className='size-5' />
    case 'timer':
      return <Clock3 className='size-5' />
    case 'trigger':
      return <Zap className='size-5' />
    case 'end':
      return <Flag className='size-5' />
  }
}

// 画布节点只负责渲染节点外观和连接点。
export function WorkflowNodeCard({
  data,
  selected,
}: NodeProps<WorkflowNode>) {
  const workflowData = data as WorkflowNodeData
  const classes =
    toneClassNames[workflowData.tone as keyof typeof toneClassNames]
  const previewStatus = (workflowData as WorkflowNodeData & {
    previewStatus?: 'ACTIVE' | 'COMPLETED' | 'VISITED' | 'IDLE'
  }).previewStatus
  const previewClasses =
    previewStatus && previewStatus !== 'IDLE'
      ? previewStatusClassNames[previewStatus]
      : null
  const marker = resolveNodeMarker(workflowData)
  const markerClasses =
    marker && !previewClasses ? toneClassNames[marker.tone] : null
  const collaborationLabel = resolveWorkflowCollaborationNodeLabel(workflowData.kind)
  const showTarget = workflowData.kind !== 'start'
  const showSource = workflowData.kind !== 'end'
  const collaboration = workflowData.collaboration
  const editingPeer = collaboration?.editingBy[0] ?? null
  const selectedPeers = collaboration?.selectedBy ?? []

  return (
    <div
      className={cn(
        'relative w-[220px] rounded-2xl border bg-card/95 p-4 shadow-sm backdrop-blur',
        'transition-[box-shadow,transform] duration-200',
        selected && ['shadow-lg ring-2', previewClasses?.ring ?? classes.ring],
        previewClasses?.card,
        previewStatus === 'ACTIVE' && ['shadow-lg ring-2', previewClasses?.ring ?? classes.ring]
      )}
      style={
        !selected && (editingPeer || selectedPeers[0])
          ? {
              borderColor: editingPeer?.color ?? selectedPeers[0]?.color,
              boxShadow: `0 0 0 1px ${editingPeer?.color ?? selectedPeers[0]?.color} inset`,
            }
          : undefined
      }
    >
      {showTarget ? (
        <Handle
          type='target'
          position={Position.Top}
          isConnectableStart={false}
        />
      ) : null}

      <div className='flex items-start gap-3'>
        <div
          className={cn(
            'flex size-11 shrink-0 items-center justify-center rounded-2xl',
            previewClasses?.icon ?? classes.icon
          )}
        >
          {renderIcon(workflowData.kind)}
        </div>
        <div className='flex min-w-0 flex-1 flex-col gap-2'>
          <div className='flex items-center justify-between gap-2'>
            <span className='truncate text-sm font-semibold'>
              {workflowData.label}
            </span>
            <div className='flex items-center gap-1.5'>
              {marker ? (
                <span
                  className={cn(
                    'rounded-full px-2 py-0.5 text-[11px] font-medium',
                    previewClasses?.badge ?? markerClasses?.badge ?? classes.badge
                  )}
                >
                  {marker.label}
                </span>
              ) : null}
              <span
                className={cn(
                  'rounded-full px-2 py-0.5 text-[11px] font-medium',
                  previewClasses?.badge ?? classes.badge
                )}
              >
                {collaborationLabel !== workflowData.kind
                  ? collaborationLabel
                  : kindBadgeLabels[workflowData.kind]}
              </span>
            </div>
          </div>
          {editingPeer ? (
            <div className='flex items-center gap-2 text-[11px] font-medium'>
              <span
                className='inline-flex rounded-full px-2 py-0.5'
                style={{
                  backgroundColor: `${editingPeer.color}1a`,
                  color: editingPeer.color,
                }}
              >
                {editingPeer.displayName} 正在编辑
              </span>
            </div>
          ) : selectedPeers.length > 0 ? (
            <div className='flex flex-wrap items-center gap-2 text-[11px] font-medium'>
              {selectedPeers.slice(0, 2).map((peer) => (
                <span
                  key={peer.userId}
                  className='inline-flex rounded-full px-2 py-0.5'
                  style={{
                    backgroundColor: `${peer.color}1a`,
                    color: peer.color,
                  }}
                >
                  {peer.displayName} 已选中
                </span>
              ))}
            </div>
          ) : null}
        </div>
      </div>

      {showSource ? (
        <Handle
          type='source'
          position={Position.Bottom}
          isConnectableEnd={false}
        />
      ) : null}
    </div>
  )
}
