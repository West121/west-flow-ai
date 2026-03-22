import dagre from 'dagre'
import { Position } from '@xyflow/react'
import { type WorkflowNode, type WorkflowSnapshot } from './types'

const NODE_WIDTH = 220
const NODE_HEIGHT = 96

function resolveNodeWidth(node: WorkflowNode) {
  return node.width ?? NODE_WIDTH
}

function resolveNodeHeight(node: WorkflowNode) {
  return node.height ?? NODE_HEIGHT
}

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
