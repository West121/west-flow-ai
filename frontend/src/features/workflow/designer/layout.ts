import dagre from 'dagre'
import { Position } from '@xyflow/react'
import { type WorkflowNode, type WorkflowSnapshot } from './types'

const NODE_WIDTH = 220
const NODE_HEIGHT = 96

// 布局前先补齐节点宽度。
function resolveNodeWidth(node: WorkflowNode) {
  return node.width ?? NODE_WIDTH
}

// 布局前先补齐节点高度。
function resolveNodeHeight(node: WorkflowNode) {
  return node.height ?? NODE_HEIGHT
}

// 自动布局只重新计算节点坐标，不改节点配置。
export function autoLayoutWorkflow(
  snapshot: WorkflowSnapshot
): WorkflowSnapshot {
  const graph = new dagre.graphlib.Graph()

  graph.setDefaultEdgeLabel(() => ({}))
  graph.setGraph({
    rankdir: 'TB',
    nodesep: 48,
    ranksep: 84,
    marginx: 32,
    marginy: 32,
  })

  snapshot.nodes.forEach((node) => {
    graph.setNode(node.id, {
      width: resolveNodeWidth(node),
      height: resolveNodeHeight(node),
    })
  })

  snapshot.edges.forEach((edge) => {
    graph.setEdge(edge.source, edge.target)
  })

  dagre.layout(graph)

  return {
    ...snapshot,
    nodes: snapshot.nodes.map((node) => {
      const layoutNode = graph.node(node.id)
      const width = resolveNodeWidth(node)
      const height = resolveNodeHeight(node)

      if (!layoutNode) {
        return node
      }

      return {
        ...node,
        position: {
          x: layoutNode.x - width / 2,
          y: layoutNode.y - height / 2,
        },
        sourcePosition: node.data.kind === 'end' ? undefined : Position.Bottom,
        targetPosition:
          node.data.kind === 'start' ? undefined : Position.Top,
      }
    }),
  }
}
