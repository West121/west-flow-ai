import {
  BellRing,
  Clock3,
  Flag,
  GitBranch,
  GitPullRequestArrow,
  GitMerge,
  Play,
  Zap,
  UserRoundCheck,
} from 'lucide-react'
import { Position } from '@xyflow/react'
import { defaultNodeConfig } from './config'
import {
  type WorkflowNode,
  type WorkflowNodeKind,
  type WorkflowNodeTone,
} from './types'

export type WorkflowNodeTemplate = {
  kind: WorkflowNodeKind
  label: string
  description: string
  tone: WorkflowNodeTone
  accent: string
  icon: typeof Play
}

export const workflowNodeTemplates: WorkflowNodeTemplate[] = [
  {
    kind: 'start',
    label: '开始',
    description: '流程发起与表单提交入口',
    tone: 'success',
    accent: 'from-emerald-500/20 to-emerald-500/5',
    icon: Play,
  },
  {
    kind: 'approver',
    label: '审批',
    description: '支持会签、或签、主办、转办',
    tone: 'brand',
    accent: 'from-sky-500/20 to-sky-500/5',
    icon: UserRoundCheck,
  },
  {
    kind: 'subprocess',
    label: '子流程',
    description: '调用已发布流程作为子流程节点',
    tone: 'brand',
    accent: 'from-cyan-500/20 to-cyan-500/5',
    icon: GitPullRequestArrow,
  },
  {
    kind: 'dynamic-builder',
    label: '动态构建',
    description: '运行时按规则生成追加审批链路',
    tone: 'brand',
    accent: 'from-fuchsia-500/20 to-fuchsia-500/5',
    icon: GitBranch,
  },
  {
    kind: 'condition',
    label: '条件分支',
    description: '金额、部门、字段表达式路由',
    tone: 'warning',
    accent: 'from-amber-500/20 to-amber-500/5',
    icon: GitBranch,
  },
  {
    kind: 'parallel',
    label: '并行分支',
    description: '并发任务和汇聚节点编排',
    tone: 'neutral',
    accent: 'from-slate-500/20 to-slate-500/5',
    icon: GitMerge,
  },
  {
    kind: 'cc',
    label: '抄送',
    description: '知会、已阅、协同提醒',
    tone: 'neutral',
    accent: 'from-violet-500/20 to-violet-500/5',
    icon: BellRing,
  },
  {
    kind: 'timer',
    label: '定时',
    description: '到点自动推进流程',
    tone: 'warning',
    accent: 'from-amber-500/20 to-amber-500/5',
    icon: Clock3,
  },
  {
    kind: 'trigger',
    label: '触发',
    description: '立即或定时执行外部触发器',
    tone: 'brand',
    accent: 'from-sky-500/20 to-sky-500/5',
    icon: Zap,
  },
  {
    kind: 'end',
    label: '结束',
    description: '流程结束、触发后置动作',
    tone: 'neutral',
    accent: 'from-rose-500/20 to-rose-500/5',
    icon: Flag,
  },
]

// 节点模板决定连线锚点方向。
function targetPositionFor(kind: WorkflowNodeKind) {
  return kind === 'start' ? undefined : Position.Top
}

// 节点模板决定出线锚点方向。
function sourcePositionFor(kind: WorkflowNodeKind) {
  return kind === 'end' ? undefined : Position.Bottom
}

// 从模板创建画布节点，保持默认尺寸和配置一致。
export function createWorkflowNode(
  template: WorkflowNodeTemplate,
  id: string,
  position: { x: number; y: number }
): WorkflowNode {
  return {
    id,
    type: 'workflow',
    position,
    data: {
      kind: template.kind,
      label: template.label,
      description: template.description,
      tone: template.tone,
      config: defaultNodeConfig(template.kind),
    },
    width: 220,
    height: 96,
    sourcePosition: sourcePositionFor(template.kind),
    targetPosition: targetPositionFor(template.kind),
  }
}
