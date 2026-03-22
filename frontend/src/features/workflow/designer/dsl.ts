import { type WorkflowSnapshot } from './types'

export type ProcessDefinitionMeta = {
  processKey: string
  processName: string
  category: string
  formKey: string
  formVersion: string
}

export type ProcessDefinitionDslNodeType =
  | 'start'
  | 'approver'
  | 'cc'
  | 'condition'
  | 'parallel_split'
  | 'parallel_join'
  | 'end'

export type ProcessDefinitionDslPayload = {
  dslVersion: string
  processKey: string
  processName: string
  category: string
  formKey: string
  formVersion: string
  settings: {
    allowWithdraw: boolean
    allowUrge: boolean
    allowTransfer: boolean
  }
  nodes: Array<{
    id: string
    type: ProcessDefinitionDslNodeType
    name: string
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
      expression: string
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

function nodeTypeFor(kind: string): ProcessDefinitionDslNodeType {
  switch (kind) {
    case 'start':
    case 'approver':
    case 'cc':
    case 'condition':
    case 'end':
      return kind
    case 'parallel':
      return 'parallel_split'
    default:
      return 'approver'
  }
}

function nodeKindFor(type: ProcessDefinitionDslNodeType) {
  switch (type) {
    case 'parallel_split':
    case 'parallel_join':
      return 'parallel'
    default:
      return type
  }
}

function toneFor(type: ProcessDefinitionDslNodeType) {
  switch (type) {
    case 'start':
      return 'success'
    case 'approver':
      return 'brand'
    case 'condition':
      return 'warning'
    default:
      return 'neutral'
  }
}

function descriptionFor(type: ProcessDefinitionDslNodeType) {
  switch (type) {
    case 'start':
      return '流程发起与表单提交入口'
    case 'approver':
      return '审批节点'
    case 'cc':
      return '抄送节点'
    case 'condition':
      return '条件分支节点'
    case 'parallel_split':
      return '并行分支节点'
    case 'parallel_join':
      return '并行汇聚节点'
    case 'end':
      return '流程结束节点'
  }
}

function labelFor(type: ProcessDefinitionDslNodeType, fallback: string) {
  switch (type) {
    case 'start':
      return '开始'
    case 'approver':
      return '审批'
    case 'cc':
      return '抄送'
    case 'condition':
      return '条件'
    case 'parallel_split':
      return '并行分支'
    case 'parallel_join':
      return '并行汇聚'
    case 'end':
      return '结束'
    default:
      return fallback
  }
}

function assignmentConfig() {
  return {
    assignment: {
      mode: 'USER',
      userIds: ['usr_002'],
      roleCodes: [],
      departmentRef: '',
      formFieldKey: '',
    },
    approvalPolicy: {
      type: 'SEQUENTIAL',
      voteThreshold: null,
    },
    operations: ['APPROVE', 'REJECT', 'RETURN'],
    commentRequired: false,
  }
}

function conditionConfig(edges: WorkflowSnapshot['edges'], nodeId: string) {
  const outgoingEdges = edges.filter((edge) => edge.source === nodeId)
  return outgoingEdges[0]?.id
    ? { defaultEdgeId: outgoingEdges[0].id }
    : {}
}

export function workflowSnapshotToProcessDefinitionDsl(
  snapshot: WorkflowSnapshot,
  meta: ProcessDefinitionMeta
): ProcessDefinitionDslPayload {
  return {
    dslVersion: '1.0.0',
    processKey: meta.processKey,
    processName: meta.processName,
    category: meta.category,
    formKey: meta.formKey,
    formVersion: meta.formVersion,
    settings: {
      allowWithdraw: true,
      allowUrge: true,
      allowTransfer: true,
    },
    nodes: snapshot.nodes.map((node) => {
      const nodeType = nodeTypeFor(node.data.kind)
      return {
        id: node.id,
        type: nodeType,
        name: node.data.label,
        position: {
          x: node.position.x,
          y: node.position.y,
        },
        config:
          nodeType === 'start'
            ? { initiatorEditable: true }
            : nodeType === 'approver'
              ? assignmentConfig()
              : nodeType === 'condition'
                ? conditionConfig(snapshot.edges, node.id)
                : nodeType === 'cc'
                  ? {
                      targets: {
                        mode: 'USER',
                        userIds: ['usr_003'],
                      },
                      readRequired: false,
                    }
                  : {},
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
      label: edge.id,
    })),
  }
}

export function processDefinitionDetailToWorkflowSnapshot(
  detail: ProcessDefinitionDetailResponse
): WorkflowSnapshot {
  return {
    nodes: detail.dsl.nodes.map((node) => ({
      id: node.id,
      type: 'workflow',
      position: {
        x: node.position.x,
        y: node.position.y,
      },
      data: {
        kind: nodeKindFor(node.type),
        label: labelFor(node.type, node.name),
        description: descriptionFor(node.type),
        tone: toneFor(node.type),
      },
      width: node.ui?.width ?? DEFAULT_NODE_WIDTH,
      height: node.ui?.height ?? DEFAULT_NODE_HEIGHT,
    })),
    edges: detail.dsl.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: 'smoothstep',
    })),
    selectedNodeId: null,
  }
}
