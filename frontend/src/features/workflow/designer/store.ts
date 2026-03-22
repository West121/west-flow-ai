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
  type WorkflowEdge,
  type WorkflowHelperLines,
  type WorkflowNode,
  type WorkflowSnapshot,
} from './types'

function createInitialSnapshot(): WorkflowSnapshot {
  // 设计器默认先放一个最小可编辑链路，方便直接进入配置。
  const start = createWorkflowNode(
    workflowNodeTemplates[0],
    'node-start',
    { x: 220, y: 48 }
  )
  const approver = createWorkflowNode(
    workflowNodeTemplates[1],
    'node-approver',
    { x: 220, y: 220 }
  )
  const end = createWorkflowNode(
    workflowNodeTemplates[5],
    'node-end',
    { x: 220, y: 392 }
  )

  return {
    nodes: [start, approver, end],
    edges: [
      {
        id: 'edge-start-approver',
        source: start.id,
        target: approver.id,
        type: 'smoothstep',
        animated: true,
      },
      {
        id: 'edge-approver-end',
        source: approver.id,
        target: end.id,
        type: 'smoothstep',
      },
    ],
    selectedNodeId: approver.id,
  }
}

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
  autoLayout: () => void
  undo: () => void
  redo: () => void
}

const initialSnapshot = createInitialSnapshot()

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

            return {
              ...edge,
              label: edgePatch.label ?? edge.label,
              data: {
                ...edge.data,
                condition: normalizeEdgeCondition(
                  hasConditionPatch ? edgePatch.condition : edge.data?.condition
                ),
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
