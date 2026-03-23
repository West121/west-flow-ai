import {
  descriptionForKind,
  labelForKind,
  normalizeEdgeCondition,
  normalizeNodeConfig,
} from './config'
import { type WorkflowProcessFormField, type WorkflowSnapshot } from './types'

export type ProcessDefinitionMeta = {
  processKey: string
  processName: string
  category: string
  processFormKey: string
  processFormVersion: string
  formFields: WorkflowProcessFormField[]
}

export type ProcessDefinitionDslNodeType =
  | 'start'
  | 'approver'
  | 'subprocess'
  | 'dynamic_builder'
  | 'cc'
  | 'timer'
  | 'trigger'
  | 'condition'
  | 'inclusive_split'
  | 'inclusive_join'
  | 'parallel_split'
  | 'parallel_join'
  | 'end'

export type ProcessDefinitionDslPayload = {
  dslVersion: string
  processKey: string
  processName: string
  category: string
  processFormKey: string
  processFormVersion: string
  formFields: WorkflowProcessFormField[]
  settings: {
    allowWithdraw: boolean
    allowUrge: boolean
    allowTransfer: boolean
  }
  nodes: Array<{
    id: string
    type: ProcessDefinitionDslNodeType
    name: string
    description: string
    position: {
      x: number
      y: number
    }
    config: Record<string, unknown>
    ui: {
      width: number
      height: number
    }
  }>
  edges: Array<{
    id: string
    source: string
    target: string
    priority: number
    label: string
    condition?: {
      type: string
      expression?: string
      fieldKey?: string
      operator?: string
      value?: string | number | boolean | null
      formulaExpression?: string
    }
  }>
}

export type ProcessDefinitionDetailResponse = {
  processDefinitionId: string
  processKey: string
  processName: string
  category: string
  version: number
  status: 'DRAFT' | 'PUBLISHED'
  createdAt: string
  updatedAt: string
  dsl: ProcessDefinitionDslPayload
  bpmnXml: string
}

const DEFAULT_NODE_WIDTH = 220
const DEFAULT_NODE_HEIGHT = 96

// 画布节点类型和流程定义节点类型不是完全一一对应，需要先做归一化。
// 画布节点种类和流程定义节点类型需要先做映射。
function nodeTypeFor(kind: string, config?: Record<string, unknown>): ProcessDefinitionDslNodeType {
  switch (kind) {
    case 'start':
    case 'approver':
    case 'subprocess':
    case 'cc':
    case 'timer':
    case 'trigger':
    case 'condition':
    case 'end':
      return kind
    case 'dynamic-builder':
      return 'dynamic_builder'
    case 'inclusive':
      return config?.gatewayDirection === 'JOIN' ? 'inclusive_join' : 'inclusive_split'
    case 'parallel':
      return config?.gatewayDirection === 'JOIN' ? 'parallel_join' : 'parallel_split'
    default:
      return 'approver'
  }
}

// 反向还原时，把定义里的节点类型映射回画布可编辑的节点种类。
// 还原流程定义时，把 DSL 节点类型映射回画布可编辑种类。
function nodeKindFor(type: ProcessDefinitionDslNodeType) {
  switch (type) {
    case 'parallel_split':
    case 'parallel_join':
      return 'parallel'
    case 'inclusive_split':
    case 'inclusive_join':
      return 'inclusive'
    case 'subprocess':
    case 'timer':
    case 'trigger':
      return type
    case 'dynamic_builder':
      return 'dynamic-builder'
    default:
      return type
  }
}

// 不同节点类型在画布里使用不同的视觉语气。
function toneFor(type: ProcessDefinitionDslNodeType) {
  switch (type) {
    case 'start':
      return 'success'
    case 'approver':
    case 'subprocess':
    case 'dynamic_builder':
      return 'brand'
    case 'condition':
    case 'inclusive_split':
    case 'inclusive_join':
      return 'warning'
    case 'timer':
      return 'warning'
    case 'trigger':
      return 'brand'
    default:
      return 'neutral'
  }
}

// DSL 负责在“画布快照”和“流程定义”之间做双向转换。
export function workflowSnapshotToProcessDefinitionDsl(
  snapshot: WorkflowSnapshot,
  meta: ProcessDefinitionMeta
): ProcessDefinitionDslPayload {
  return {
    dslVersion: '1.0.0',
    processKey: meta.processKey,
    processName: meta.processName,
    category: meta.category,
    processFormKey: meta.processFormKey,
    processFormVersion: meta.processFormVersion,
    formFields: meta.formFields,
    settings: {
      allowWithdraw: true,
      allowUrge: true,
      allowTransfer: true,
    },
    nodes: snapshot.nodes.map((node) => {
      const nodeType = nodeTypeFor(
        node.data.kind,
        (node.data.config as Record<string, unknown> | undefined) ?? undefined
      )
      return {
        id: node.id,
        type: nodeType,
        name: node.data.label.trim() || labelForKind(node.data.kind, node.data.label),
        description: node.data.description || descriptionForKind(node.data.kind),
        position: {
          x: node.position.x,
          y: node.position.y,
        },
        config: normalizeNodeConfig(node.data.kind, node.data.config),
        ui: {
          width: node.width ?? DEFAULT_NODE_WIDTH,
          height: node.height ?? DEFAULT_NODE_HEIGHT,
        },
      }
    }),
    edges: snapshot.edges.map((edge, index) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      priority: index + 1,
      label: typeof edge.label === 'string' ? edge.label : edge.id,
      condition: normalizeEdgeCondition(edge.data?.condition),
    })),
  }
}

export function processDefinitionDetailToWorkflowSnapshot(
  detail: ProcessDefinitionDetailResponse
): WorkflowSnapshot {
  return {
    nodes: detail.dsl.nodes.map((node) => {
      const kind = nodeKindFor(node.type)
      return {
        id: node.id,
        type: 'workflow',
        position: {
          x: node.position.x,
          y: node.position.y,
        },
        data: {
          kind,
          label: node.name || labelForKind(kind, node.name),
          description: node.description || descriptionForKind(kind),
          tone: toneFor(node.type),
          config: normalizeNodeConfig(kind, node.config),
        },
        width: node.ui?.width ?? DEFAULT_NODE_WIDTH,
        height: node.ui?.height ?? DEFAULT_NODE_HEIGHT,
      }
    }),
    edges: detail.dsl.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: 'smoothstep',
      label: edge.label,
      data: {
        condition: normalizeEdgeCondition(edge.condition),
      },
    })),
    selectedNodeId: null,
  }
}
