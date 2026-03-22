import { describe, expect, it } from 'vitest'
import { type WorkflowSnapshot } from './types'
import {
  processDefinitionDetailToWorkflowSnapshot,
  workflowSnapshotToProcessDefinitionDsl,
} from './dsl'

const snapshot: WorkflowSnapshot = {
  nodes: [
    {
      id: 'start_1',
      type: 'workflow',
      position: { x: 100, y: 100 },
      data: {
        kind: 'start',
        label: '开始',
        description: '发起流程',
        tone: 'success',
      },
    },
    {
      id: 'approve_1',
      type: 'workflow',
      position: { x: 320, y: 100 },
      data: {
        kind: 'approver',
        label: '审批',
        description: '部门负责人审批',
        tone: 'brand',
      },
    },
    {
      id: 'end_1',
      type: 'workflow',
      position: { x: 540, y: 100 },
      data: {
        kind: 'end',
        label: '结束',
        description: '流程结束',
        tone: 'neutral',
      },
    },
  ],
  edges: [
    {
      id: 'edge_1',
      source: 'start_1',
      target: 'approve_1',
      type: 'smoothstep',
    },
    {
      id: 'edge_2',
      source: 'approve_1',
      target: 'end_1',
      type: 'smoothstep',
    },
  ],
  selectedNodeId: 'approve_1',
}

describe('workflow designer dsl mapping', () => {
  it('maps the canvas snapshot to the frozen process definition DSL', () => {
    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      formKey: 'oa-leave-form',
      formVersion: '1.0.0',
    })

    expect(dsl.processKey).toBe('oa_leave')
    expect(dsl.processName).toBe('请假审批')
    expect(dsl.category).toBe('OA')
    expect(dsl.nodes).toHaveLength(3)
    expect(dsl.nodes[1]?.config).toMatchObject({
      assignment: expect.any(Object),
    })
    expect(dsl.edges).toHaveLength(2)
    expect(dsl.edges[0]?.source).toBe('start_1')
  })

  it('hydrates the designer snapshot from the persisted process definition detail', () => {
    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:1',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 1,
      status: 'PUBLISHED',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl: {
        dslVersion: '1.0.0',
        processKey: 'oa_leave',
        processName: '请假审批',
        category: 'OA',
        formKey: 'oa-leave-form',
        formVersion: '1.0.0',
        settings: {
          allowWithdraw: true,
          allowUrge: true,
          allowTransfer: true,
        },
        nodes: [],
        edges: [],
      },
      bpmnXml: '<process />',
    })

    expect(hydrated.selectedNodeId).toBeNull()
    expect(hydrated.nodes).toHaveLength(0)
    expect(hydrated.edges).toHaveLength(0)
  })
})
