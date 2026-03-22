import { describe, expect, it } from 'vitest'
import {
  commitWorkflowSnapshot,
  createWorkflowHistoryState,
  redoWorkflowHistory,
  undoWorkflowHistory,
} from './history'
import { type WorkflowSnapshot } from './types'

const initialSnapshot: WorkflowSnapshot = {
  nodes: [
    {
      id: 'start',
      type: 'workflow',
      position: { x: 120, y: 120 },
      data: {
        label: '开始',
        description: '发起人提交申请',
        kind: 'start' as const,
        tone: 'success' as const,
      },
    },
  ],
  edges: [],
  selectedNodeId: 'start',
}

describe('workflow designer history', () => {
  it('commits snapshots, supports undo/redo, and clears redo stack after new commits', () => {
    const initialState = createWorkflowHistoryState(initialSnapshot)
    const movedState = commitWorkflowSnapshot(initialState, {
      ...initialSnapshot,
      nodes: [
        {
          ...initialSnapshot.nodes[0],
          position: { x: 260, y: 200 },
        },
      ],
    })

    expect(movedState.past).toHaveLength(1)
    expect(movedState.present.nodes[0]?.position).toEqual({ x: 260, y: 200 })

    const undoneState = undoWorkflowHistory(movedState)
    expect(undoneState.present.nodes[0]?.position).toEqual({ x: 120, y: 120 })
    expect(undoneState.future).toHaveLength(1)

    const redoneState = redoWorkflowHistory(undoneState)
    expect(redoneState.present.nodes[0]?.position).toEqual({ x: 260, y: 200 })

    const branchState = commitWorkflowSnapshot(redoneState, {
      ...redoneState.present,
      selectedNodeId: null,
    })
    expect(branchState.future).toEqual([])
    expect(branchState.present.selectedNodeId).toBeNull()
  })

  it('ignores identical snapshots and trims history length to the configured limit', () => {
    const unchangedState = commitWorkflowSnapshot(
      createWorkflowHistoryState(initialSnapshot),
      initialSnapshot
    )

    expect(unchangedState.past).toEqual([])

    const limitedState = commitWorkflowSnapshot(
      commitWorkflowSnapshot(
        commitWorkflowSnapshot(createWorkflowHistoryState(initialSnapshot), {
          ...initialSnapshot,
          selectedNodeId: null,
        }, 2),
        {
          ...initialSnapshot,
          nodes: [
            {
              ...initialSnapshot.nodes[0],
              position: { x: 180, y: 160 },
            },
          ],
        },
        2
      ),
      {
        ...initialSnapshot,
        nodes: [
          {
            ...initialSnapshot.nodes[0],
            position: { x: 320, y: 240 },
          },
        ],
      },
      2
    )

    expect(limitedState.past).toHaveLength(2)
    expect(limitedState.past[0]?.selectedNodeId).toBeNull()
    expect(limitedState.present.nodes[0]?.position).toEqual({ x: 320, y: 240 })
  })
})
