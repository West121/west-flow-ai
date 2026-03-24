import {
  addEdge,
  applyEdgeChanges,
  applyNodeChanges,
  type Connection,
  type EdgeChange,
  type NodeChange,
} from '@xyflow/react'
import { create } from 'zustand'
import {
  commitWorkflowSnapshot,
  createWorkflowHistoryState,
  redoWorkflowHistory,
  replaceWorkflowSnapshot,
  undoWorkflowHistory,
  type WorkflowHistoryState,
} from './history'
import { autoLayoutWorkflow } from './layout'
import {
  createWorkflowNode,
  workflowNodeTemplates,
  type WorkflowNodeTemplate,
} from './palette'
import {
  normalizeEdgeCondition,
  normalizeNodeConfig,
} from './config'
import {
  type WorkflowApproverNodeConfig,
  type WorkflowConditionNodeConfig,
  type WorkflowEdgeConditionType,
  type WorkflowEdge,
  type WorkflowHelperLines,
  type WorkflowNode,
  type WorkflowSnapshot,
} from './types'

function buildApproverConfig(
  overrides: Partial<WorkflowApproverNodeConfig>
): WorkflowApproverNodeConfig {
  return normalizeNodeConfig('approver', overrides) as WorkflowApproverNodeConfig
}

function buildConditionConfig(
  overrides: Partial<WorkflowConditionNodeConfig>
): WorkflowConditionNodeConfig {
  return normalizeNodeConfig('condition', overrides) as WorkflowConditionNodeConfig
}

function buildCondition(
  type: WorkflowEdgeConditionType,
  value: Record<string, unknown>
) {
  return normalizeEdgeCondition({ type, ...value })
}

function createInitialSnapshot(): WorkflowSnapshot {
  // 设计器默认直接带一条真实请假审批案例，覆盖角色/部门/字段/公式和排他网关。
  const start = createWorkflowNode(
    findTemplate('start'),
    'node-start',
    { x: 160, y: 72 }
  )
  const condition = createWorkflowNode(
    findTemplate('condition'),
    'node-condition',
    { x: 420, y: 72 }
  )
  const managerRole = createWorkflowNode(
    findTemplate('approver'),
    'node-manager-role',
    { x: 720, y: -24 }
  )
  const departmentLead = createWorkflowNode(
    findTemplate('approver'),
    'node-department-lead',
    { x: 720, y: 112 }
  )
  const hrSpecialist = createWorkflowNode(
    findTemplate('approver'),
    'node-hr-specialist',
    { x: 720, y: 248 }
  )
  const managerField = createWorkflowNode(
    findTemplate('approver'),
    'node-manager-field',
    { x: 1040, y: 72 }
  )
  const directorFormula = createWorkflowNode(
    findTemplate('approver'),
    'node-director-formula',
    { x: 1320, y: 168 }
  )
  const end = createWorkflowNode(
    findTemplate('end'),
    'node-end',
    { x: 1600, y: 168 }
  )

  start.data.label = '发起请假'
  start.data.description = '提交请假单并进入排他分流'

  condition.data.label = '排他网关'
  condition.data.description = '按请假天数和紧急程度决定审批路径'
  condition.data.config = buildConditionConfig({
    defaultEdgeId: 'edge-leave-short',
    expressionMode: 'FIELD_COMPARE',
    expressionFieldKey: 'leaveDays',
  })

  managerRole.data.label = '直属主管审批'
  managerRole.data.description = '短假默认由直属主管审批'
  managerRole.data.config = buildApproverConfig({
    assignment: {
      mode: 'ROLE',
      userIds: [],
      roleCodes: ['role_manager'],
      departmentRef: '',
      formFieldKey: '',
      formulaExpression: '',
    },
  })

  departmentLead.data.label = '部门负责人审批'
  departmentLead.data.description = '长假流转到部门负责人审批'
  departmentLead.data.config = buildApproverConfig({
    assignment: {
      mode: 'DEPARTMENT',
      userIds: [],
      roleCodes: [],
      departmentRef: 'dept_002',
      formFieldKey: '',
      formulaExpression: '',
    },
  })

  hrSpecialist.data.label = 'HR 备案'
  hrSpecialist.data.description = '紧急或长假场景追加 HR 备案'
  hrSpecialist.data.config = buildApproverConfig({
    assignment: {
      mode: 'ROLE',
      userIds: [],
      roleCodes: ['role_hr'],
      departmentRef: '',
      formFieldKey: '',
      formulaExpression: '',
    },
  })

  managerField.data.label = '负责人确认'
  managerField.data.description = '通过表单字段直接指定负责人'
  managerField.data.config = buildApproverConfig({
    assignment: {
      mode: 'FORM_FIELD',
      userIds: [],
      roleCodes: [],
      departmentRef: '',
      formFieldKey: 'managerUserId',
      formulaExpression: '',
    },
  })

  directorFormula.data.label = '总监确认'
  directorFormula.data.description = '使用自定义公式决定最终审批人'
  directorFormula.data.config = buildApproverConfig({
    assignment: {
      mode: 'FORMULA',
      userIds: [],
      roleCodes: [],
      departmentRef: '',
      formFieldKey: '',
      formulaExpression: `ifElse(leaveDays >= 5, "usr_005", managerUserId)`,
    },
  })

  end.data.label = '流程结束'
  end.data.description = '请假审批完成'

  return {
    nodes: [
      start,
      condition,
      managerRole,
      departmentLead,
      hrSpecialist,
      managerField,
      directorFormula,
      end,
    ],
    edges: [
      {
        id: 'edge-start-condition',
        source: start.id,
        target: condition.id,
        type: 'smoothstep',
        animated: true,
        label: '提交请假单',
      },
      {
        id: 'edge-leave-short',
        source: condition.id,
        target: managerRole.id,
        type: 'smoothstep',
        label: '短假默认路径',
      },
      {
        id: 'edge-leave-long',
        source: condition.id,
        target: departmentLead.id,
        type: 'smoothstep',
        label: '请假天数大于 3 天',
        data: {
          condition: buildCondition('FIELD', {
            fieldKey: 'leaveDays',
            operator: 'GT',
            value: '3',
          }),
        },
      },
      {
        id: 'edge-leave-urgent',
        source: condition.id,
        target: hrSpecialist.id,
        type: 'smoothstep',
        label: '紧急或长假',
        data: {
          condition: buildCondition('FORMULA', {
            formulaExpression: 'urgent == true || leaveDays >= 5',
          }),
        },
      },
      {
        id: 'edge-manager-to-field',
        source: managerRole.id,
        target: managerField.id,
        type: 'smoothstep',
        label: '直属确认',
      },
      {
        id: 'edge-dept-to-field',
        source: departmentLead.id,
        target: managerField.id,
        type: 'smoothstep',
        label: '直属确认',
      },
      {
        id: 'edge-hr-to-director',
        source: hrSpecialist.id,
        target: directorFormula.id,
        type: 'smoothstep',
        label: '总监确认',
      },
      {
        id: 'edge-field-to-director',
        source: managerField.id,
        target: directorFormula.id,
        type: 'smoothstep',
        label: '进入总监确认',
      },
      {
        id: 'edge-director-end',
        source: directorFormula.id,
        target: end.id,
        type: 'smoothstep',
        label: '审批完成',
      },
    ],
    selectedNodeId: condition.id,
  }
}

// 是否落历史栈由调用场景决定，这里只负责切换实现。
function withPresentSnapshot(
  history: WorkflowHistoryState,
  snapshot: WorkflowSnapshot,
  shouldCommit: boolean
) {
  return shouldCommit
    ? commitWorkflowSnapshot(history, snapshot)
    : replaceWorkflowSnapshot(history, snapshot)
}

function resolveSelectedNodeId(
  previousSnapshot: WorkflowSnapshot,
  nextNodes: WorkflowNode[]
) {
  if (
    previousSnapshot.selectedNodeId &&
    nextNodes.some((node) => node.id === previousSnapshot.selectedNodeId)
  ) {
    // 只要当前选中的节点还在，就保持选中状态。
    return previousSnapshot.selectedNodeId
  }

  // 如果节点被删掉了，就退回到最新的可见节点。
  return nextNodes[nextNodes.length - 1]?.id ?? null
}

// 新增节点时用当前节点列表推导下一个序号。
function resolveNextNodeSequence(nodes: WorkflowNode[]) {
  let nextSequence = nodes.length

  for (const node of nodes) {
    const match = /-(\d+)$/.exec(node.id)
    if (!match) {
      continue
    }

    nextSequence = Math.max(nextSequence, Number(match[1]))
  }

  return nextSequence + 1
}

// 按 kind 查模板，避免模板顺序变化后拿错默认节点。
function findTemplate(kind: WorkflowNodeTemplate['kind']) {
  const template = workflowNodeTemplates.find((item) => item.kind === kind)
  if (!template) {
    throw new Error(`Missing workflow node template: ${kind}`)
  }

  return template
}

type WorkflowDesignerState = {
  history: WorkflowHistoryState
  helperLines: WorkflowHelperLines
  nextNodeSequence: number
  resetDesigner: () => void
  hydrateSnapshot: (snapshot: WorkflowSnapshot) => void
  setSelectedNodeId: (selectedNodeId: string | null) => void
  updateNodeData: (
    nodeId: string,
    updater: (data: WorkflowNode['data']) => WorkflowNode['data']
  ) => void
  updateNodeDraft: (
    nodeId: string,
    patch: {
      label?: string
      description?: string
      config?: unknown
    },
    edgePatches?: Array<{
      edgeId: string
      label?: string
      condition?: unknown
      priority?: number
    }>
  ) => void
  setHelperLines: (helperLines: WorkflowHelperLines) => void
  applyNodeChanges: (changes: NodeChange<WorkflowNode>[]) => void
  applyEdgeChanges: (changes: EdgeChange<WorkflowEdge>[]) => void
  connectNodes: (connection: Connection) => void
  addNodeFromTemplate: (
    template: WorkflowNodeTemplate,
    position: { x: number; y: number }
  ) => void
  addStructurePreset: (
    preset: 'SUBPROCESS_CHAIN' | 'DYNAMIC_BUILDER_CHAIN' | 'INCLUSIVE_BRANCH'
  ) => void
  autoLayout: () => void
  undo: () => void
  redo: () => void
}

const initialSnapshot = createInitialSnapshot()

function resolveStructureOrigin(nodes: WorkflowNode[]) {
  const maxX = nodes.reduce((current, node) => Math.max(current, node.position.x), 120)
  const maxY = nodes.reduce((current, node) => Math.max(current, node.position.y), 80)

  return {
    x: maxX + 360,
    y: Math.max(80, maxY - 40),
  }
}

function createPresetNodes(
  preset: 'SUBPROCESS_CHAIN' | 'DYNAMIC_BUILDER_CHAIN' | 'INCLUSIVE_BRANCH',
  nextSequence: number,
  origin: { x: number; y: number }
) {
  let sequence = nextSequence
  const consume = (kind: WorkflowNodeTemplate['kind'], x: number, y: number) => {
    sequence += 1
    return createWorkflowNode(findTemplate(kind), `node-${kind}-${sequence}`, {
      x: origin.x + x,
      y: origin.y + y,
    })
  }

  if (preset === 'SUBPROCESS_CHAIN') {
    const parentApprove = consume('approver', 0, 0)
    const subprocess = consume('subprocess', 0, 150)
    const callbackApprove = consume('approver', 0, 300)

    parentApprove.data.label = '父流程审批'
    parentApprove.data.description = '主流程进入子流程前的父级审批节点'
    subprocess.data.label = '销假子流程'
    subprocess.data.description = '调用已发布子流程处理销假与回传'
    subprocess.data.config = normalizeNodeConfig('subprocess', {
      calledProcessKey: 'oa_leave_cancel',
      calledVersionPolicy: 'LATEST_PUBLISHED',
      callScope: 'CHILD_ONLY',
      joinMode: 'AUTO_RETURN',
      childStartStrategy: 'LATEST_PUBLISHED',
      parentResumeStrategy: 'AUTO_RETURN',
      businessBindingMode: 'INHERIT_PARENT',
      terminatePolicy: 'TERMINATE_SUBPROCESS_ONLY',
      childFinishPolicy: 'RETURN_TO_PARENT',
      inputMappings: [{ source: 'leaveId', target: 'leaveId' }],
      outputMappings: [{ source: 'cancelResult', target: 'cancelResult' }],
    })
    callbackApprove.data.label = '子流程回传确认'
    callbackApprove.data.description = '子流程完成后回到父流程继续确认'

    return {
      nextSequence: sequence,
      nodes: [parentApprove, subprocess, callbackApprove],
      edges: [
        {
          id: `edge-${parentApprove.id}-${subprocess.id}`,
          source: parentApprove.id,
          target: subprocess.id,
          type: 'smoothstep',
          label: '进入子流程',
        },
        {
          id: `edge-${subprocess.id}-${callbackApprove.id}`,
          source: subprocess.id,
          target: callbackApprove.id,
          type: 'smoothstep',
          label: '回到父流程',
        },
      ],
      selectedNodeId: subprocess.id,
    }
  }

  if (preset === 'DYNAMIC_BUILDER_CHAIN') {
    const triggerApprove = consume('approver', 0, 0)
    const dynamicBuilder = consume('dynamic-builder', 0, 150)
    const generatedSummary = consume('approver', 0, 300)

    triggerApprove.data.label = '规则触发审批'
    triggerApprove.data.description = '触发动态构建规则前的上游审批'
    dynamicBuilder.data.label = '动态构建审批链'
    dynamicBuilder.data.description = '按请假类型与时长生成附加审批结构'
    dynamicBuilder.data.config = normalizeNodeConfig('dynamic-builder', {
      buildMode: 'APPROVER_TASKS',
      sourceMode: 'RULE',
      sceneCode: 'leave_overtime_approval',
      executionStrategy: 'RULE_FIRST',
      fallbackStrategy: 'KEEP_CURRENT',
      ruleExpression: 'ifElse(leaveDays >= 5, "DIRECTOR_CHAIN", "HR_RECORD")',
      appendPolicy: 'SERIAL_AFTER_CURRENT',
      maxGeneratedCount: 3,
      terminatePolicy: 'TERMINATE_GENERATED_ONLY',
    })
    generatedSummary.data.label = '生成结构汇总确认'
    generatedSummary.data.description = '动态生成链路完成后收口到汇总节点'

    return {
      nextSequence: sequence,
      nodes: [triggerApprove, dynamicBuilder, generatedSummary],
      edges: [
        {
          id: `edge-${triggerApprove.id}-${dynamicBuilder.id}`,
          source: triggerApprove.id,
          target: dynamicBuilder.id,
          type: 'smoothstep',
          label: '规则生成',
        },
        {
          id: `edge-${dynamicBuilder.id}-${generatedSummary.id}`,
          source: dynamicBuilder.id,
          target: generatedSummary.id,
          type: 'smoothstep',
          label: '收口确认',
        },
      ],
      selectedNodeId: dynamicBuilder.id,
    }
  }

  const inclusiveSplit = consume('inclusive', 0, 0)
  const financeApprove = consume('approver', -220, 170)
  const hrApprove = consume('approver', 220, 170)
  const inclusiveJoin = consume('inclusive', 0, 340)

  inclusiveSplit.data.label = '包容分支评估'
  inclusiveSplit.data.description = '按请假类型和紧急程度命中多条分支'
  inclusiveSplit.data.config = normalizeNodeConfig('inclusive', {
    gatewayDirection: 'SPLIT',
  })
  financeApprove.data.label = '财务复核'
  financeApprove.data.description = '长假或外出场景同步命中财务复核'
  hrApprove.data.label = 'HR 合规复核'
  hrApprove.data.description = '紧急场景同步命中 HR 合规复核'
  inclusiveJoin.data.label = '包容分支合流'
  inclusiveJoin.data.description = '所有命中分支完成后统一汇聚'
  inclusiveJoin.data.config = normalizeNodeConfig('inclusive', {
    gatewayDirection: 'JOIN',
  })

  return {
    nextSequence: sequence,
    nodes: [inclusiveSplit, financeApprove, hrApprove, inclusiveJoin],
    edges: [
      {
        id: `edge-${inclusiveSplit.id}-${financeApprove.id}`,
        source: inclusiveSplit.id,
        target: financeApprove.id,
        type: 'smoothstep',
        label: '请假 >= 3 天',
        data: {
          condition: normalizeEdgeCondition({
            type: 'FIELD',
            fieldKey: 'leaveDays',
            operator: 'GE',
            value: '3',
          }),
        },
      },
      {
        id: `edge-${inclusiveSplit.id}-${hrApprove.id}`,
        source: inclusiveSplit.id,
        target: hrApprove.id,
        type: 'smoothstep',
        label: '紧急或病假',
        data: {
          condition: normalizeEdgeCondition({
            type: 'FORMULA',
            formulaExpression: 'urgent == true || leaveType == "SICK"',
          }),
        },
      },
      {
        id: `edge-${financeApprove.id}-${inclusiveJoin.id}`,
        source: financeApprove.id,
        target: inclusiveJoin.id,
        type: 'smoothstep',
        label: '汇聚',
      },
      {
        id: `edge-${hrApprove.id}-${inclusiveJoin.id}`,
        source: hrApprove.id,
        target: inclusiveJoin.id,
        type: 'smoothstep',
        label: '汇聚',
      },
    ],
    selectedNodeId: inclusiveSplit.id,
  }
}

export const useWorkflowDesignerStore = create<WorkflowDesignerState>()(
  (set) => ({
    history: createWorkflowHistoryState(initialSnapshot),
    helperLines: { vertical: null, horizontal: null },
    nextNodeSequence: 1,
    resetDesigner: () =>
      set({
        history: createWorkflowHistoryState(initialSnapshot),
        helperLines: { vertical: null, horizontal: null },
        nextNodeSequence: 1,
      }),
    hydrateSnapshot: (snapshot) =>
      set({
        history: createWorkflowHistoryState(snapshot),
        helperLines: { vertical: null, horizontal: null },
        nextNodeSequence: resolveNextNodeSequence(snapshot.nodes),
      }),
    setSelectedNodeId: (selectedNodeId) =>
      set((state) => ({
        // 这里只改当前快照，不写入历史栈，避免单纯点选污染撤销记录。
        history: replaceWorkflowSnapshot(state.history, {
          ...state.history.present,
          selectedNodeId,
        }),
      })),
    updateNodeData: (nodeId, updater) =>
      set((state) => ({
        // 节点内容变更要进入历史，保证撤销/重做可用。
        history: commitWorkflowSnapshot(state.history, {
          ...state.history.present,
          nodes: state.history.present.nodes.map((node) =>
            node.id === nodeId
              ? {
                  ...node,
                  data: updater(node.data),
                }
              : node
          ),
        }),
      })),
    updateNodeDraft: (nodeId, patch, edgePatches = []) =>
      set((state) => ({
        // 节点表单和边条件会一起落盘，保持设计器预览一致。
        history: commitWorkflowSnapshot(state.history, {
          ...state.history.present,
          nodes: state.history.present.nodes.map((node) => {
            if (node.id !== nodeId) {
              return node
            }

            return {
              ...node,
              data: {
                ...node.data,
                label: patch.label ?? node.data.label,
                description: patch.description ?? node.data.description,
                config: normalizeNodeConfig(
                  node.data.kind,
                  patch.config ?? node.data.config
                ),
              },
            }
          }),
          edges: state.history.present.edges.map((edge) => {
            const edgePatch = edgePatches.find((item) => item.edgeId === edge.id)
            if (!edgePatch) {
              return edge
            }

            const hasConditionPatch = Object.prototype.hasOwnProperty.call(
              edgePatch,
              'condition'
            )
            const hasPriorityPatch = Object.prototype.hasOwnProperty.call(
              edgePatch,
              'priority'
            )

            return {
              ...edge,
              label: edgePatch.label ?? edge.label,
              data: {
                ...edge.data,
                condition: normalizeEdgeCondition(
                  hasConditionPatch ? edgePatch.condition : edge.data?.condition
                ),
                priority: hasPriorityPatch
                  ? edgePatch.priority
                  : edge.data?.priority,
              },
            }
          }),
          selectedNodeId: nodeId,
        }),
      })),
    setHelperLines: (helperLines) => set({ helperLines }),
    applyNodeChanges: (changes) =>
      set((state) => {
        const nextNodes = applyNodeChanges(changes, state.history.present.nodes)
        const shouldCommit = changes.some(
          (change) =>
            change.type === 'add' ||
            change.type === 'remove' ||
            (change.type === 'position' && !change.dragging)
        )
        // 拖拽中的位移只刷新当前快照，真正落点才写进历史。
        const nextSnapshot = {
          ...state.history.present,
          nodes: nextNodes,
          selectedNodeId: resolveSelectedNodeId(state.history.present, nextNodes),
        }

        return {
          history: withPresentSnapshot(state.history, nextSnapshot, shouldCommit),
        }
      }),
    applyEdgeChanges: (changes) =>
      set((state) => {
        const nextEdges = applyEdgeChanges(changes, state.history.present.edges)
        const shouldCommit = changes.some(
          (change) => change.type !== 'select'
        )

        return {
          // 选中不算结构变化，删除或重连才需要进入历史。
          history: withPresentSnapshot(
            state.history,
            {
              ...state.history.present,
              edges: nextEdges,
            },
            shouldCommit
          ),
        }
      }),
    connectNodes: (connection) =>
      set((state) => ({
        // 连线属于结构调整，直接写入历史栈。
        history: commitWorkflowSnapshot(state.history, {
          ...state.history.present,
          edges: addEdge(
            {
              ...connection,
              id: `edge-${connection.source}-${connection.target}-${Date.now()}`,
              type: 'smoothstep',
              animated: false,
            },
            state.history.present.edges
          ),
        }),
      })),
    addNodeFromTemplate: (template, position) =>
      set((state) => {
        const nodeId = `node-${template.kind}-${state.nextNodeSequence + 1}`
        const nextNode = createWorkflowNode(template, nodeId, position)

        return {
          nextNodeSequence: state.nextNodeSequence + 1,
          history: commitWorkflowSnapshot(state.history, {
            ...state.history.present,
            nodes: [...state.history.present.nodes, nextNode],
            selectedNodeId: nextNode.id,
          }),
        }
      }),
    addStructurePreset: (preset) =>
      set((state) => {
        const origin = resolveStructureOrigin(state.history.present.nodes)
        const presetSnapshot = createPresetNodes(
          preset,
          state.nextNodeSequence,
          origin
        )

        return {
          nextNodeSequence: presetSnapshot.nextSequence,
          history: commitWorkflowSnapshot(state.history, {
            ...state.history.present,
            nodes: [...state.history.present.nodes, ...presetSnapshot.nodes],
            edges: [...state.history.present.edges, ...presetSnapshot.edges],
            selectedNodeId: presetSnapshot.selectedNodeId,
          }),
        }
      }),
    autoLayout: () =>
      set((state) => ({
        history: commitWorkflowSnapshot(
          state.history,
          autoLayoutWorkflow(state.history.present)
        ),
        helperLines: { vertical: null, horizontal: null },
      })),
    undo: () =>
      set((state) => ({
        history: undoWorkflowHistory(state.history),
        helperLines: { vertical: null, horizontal: null },
      })),
    redo: () =>
      set((state) => ({
        history: redoWorkflowHistory(state.history),
        helperLines: { vertical: null, horizontal: null },
      })),
  })
)
