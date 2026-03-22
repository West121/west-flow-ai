import { describe, expect, it } from 'vitest'
import { type WorkflowNode, type WorkflowSnapshot } from './types'
import {
  type ProcessDefinitionDetailResponse,
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
        config: {
          initiatorEditable: true,
        },
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
        config: {
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
        },
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
        config: {},
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
      formFields: [],
    })

    expect(dsl.processKey).toBe('oa_leave')
    expect(dsl.processName).toBe('请假审批')
    expect(dsl.category).toBe('OA')
    expect(dsl.nodes).toHaveLength(3)
    expect(dsl.nodes[1]?.name).toBe('审批')
    expect(dsl.nodes[1]?.description).toBe('部门负责人审批')
    expect(dsl.nodes[1]?.config).toMatchObject({
      assignment: expect.any(Object),
    })
    expect(dsl.edges).toHaveLength(2)
    expect(dsl.edges[0]?.source).toBe('start_1')
  })

  it('persists process form fields and node field bindings in the DSL', () => {
    const snapshotWithForms = {
      ...snapshot,
      nodes: snapshot.nodes.map((node) =>
        node.id === 'approve_1'
          ? ({
              ...node,
              data: {
                ...node.data,
                config: {
                  ...(node.data.config as object),
                  nodeFormKey: 'oa-leave-approve-form',
                  nodeFormVersion: '2.0.0',
                  fieldBindings: [
                    {
                      source: 'PROCESS_FORM',
                      sourceFieldKey: 'days',
                      targetFieldKey: 'approvedDays',
                    },
                  ],
                },
              },
            } as WorkflowNode)
          : node
      ),
    } satisfies WorkflowSnapshot

    const meta = {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      formKey: 'oa-leave-form',
      formVersion: '1.0.0',
      formFields: [
        { fieldKey: 'days', label: '请假天数', valueType: 'number' },
        { fieldKey: 'reason', label: '请假原因', valueType: 'string' },
      ],
    } satisfies Parameters<typeof workflowSnapshotToProcessDefinitionDsl>[1]

    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshotWithForms, meta)

    expect(dsl.formFields).toEqual([
      { fieldKey: 'days', label: '请假天数', valueType: 'number' },
      { fieldKey: 'reason', label: '请假原因', valueType: 'string' },
    ])
    expect(dsl.nodes[1]?.config).toMatchObject({
      nodeFormKey: 'oa-leave-approve-form',
      nodeFormVersion: '2.0.0',
      fieldBindings: [
        {
          source: 'PROCESS_FORM',
          sourceFieldKey: 'days',
          targetFieldKey: 'approvedDays',
        },
      ],
    })
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
        formFields: [],
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

  it('keeps persisted node names and config when hydrating the designer snapshot', () => {
    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:2',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 2,
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
        formFields: [],
        settings: {
          allowWithdraw: true,
          allowUrge: true,
          allowTransfer: true,
        },
        nodes: [
          {
            id: 'approve_manager',
            type: 'approver',
            name: '部门负责人审批',
            description: '审批金额超过 1000 的请假单',
            position: { x: 320, y: 100 },
            config: {
              assignment: {
                mode: 'ROLE',
                roleCodes: ['role_dept_manager'],
                userIds: [],
                departmentRef: '',
                formFieldKey: '',
              },
              approvalPolicy: {
                type: 'SEQUENTIAL',
                voteThreshold: null,
              },
              operations: ['APPROVE', 'REJECT', 'RETURN'],
              commentRequired: true,
            },
            ui: { width: 240, height: 88 },
          },
        ],
        edges: [],
      },
      bpmnXml: '<process />',
    })

    expect(hydrated.nodes[0]?.data.label).toBe('部门负责人审批')
    expect(hydrated.nodes[0]?.data.description).toBe(
      '审批金额超过 1000 的请假单'
    )
    expect(hydrated.nodes[0]?.data.config).toMatchObject({
      commentRequired: true,
      assignment: {
        mode: 'ROLE',
        roleCodes: ['role_dept_manager'],
      },
    })
  })

  it('round-trips condition branch expressions and default edges', () => {
    const conditionSnapshot: WorkflowSnapshot = {
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
            config: { initiatorEditable: true },
          },
        },
        {
          id: 'condition_1',
          type: 'workflow',
          position: { x: 320, y: 100 },
          data: {
            kind: 'condition',
            label: '条件',
            description: '条件分支',
            tone: 'warning',
            config: { defaultEdgeId: 'edge_default' },
          },
        },
        {
          id: 'approve_1',
          type: 'workflow',
          position: { x: 540, y: 60 },
          data: {
            kind: 'approver',
            label: '通过审批',
            description: '条件通过审批',
            tone: 'brand',
            config: {
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
            },
          },
        },
        {
          id: 'approve_2',
          type: 'workflow',
          position: { x: 540, y: 180 },
          data: {
            kind: 'approver',
            label: '驳回处理',
            description: '条件不通过审批',
            tone: 'brand',
            config: {
              assignment: {
                mode: 'USER',
                userIds: ['usr_003'],
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
            },
          },
        },
        {
          id: 'end_1',
          type: 'workflow',
          position: { x: 760, y: 120 },
          data: {
            kind: 'end',
            label: '结束',
            description: '流程结束',
            tone: 'neutral',
            config: {},
          },
        },
      ],
      edges: [
        { id: 'edge_start', source: 'start_1', target: 'condition_1', type: 'smoothstep' },
        { id: 'edge_default', source: 'condition_1', target: 'approve_1', type: 'smoothstep' },
        {
          id: 'edge_branch',
          source: 'condition_1',
          target: 'approve_2',
          type: 'smoothstep',
          data: {
            condition: {
              type: 'EXPRESSION',
              expression: 'amount > 1000',
            },
          },
        },
        { id: 'edge_end_1', source: 'approve_1', target: 'end_1', type: 'smoothstep' },
        { id: 'edge_end_2', source: 'approve_2', target: 'end_1', type: 'smoothstep' },
      ],
      selectedNodeId: 'condition_1',
    }

    const dsl = workflowSnapshotToProcessDefinitionDsl(conditionSnapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      formKey: 'oa-leave-form',
      formVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes.find((node) => node.id === 'condition_1')?.config).toMatchObject({
      defaultEdgeId: 'edge_default',
    })
    expect(dsl.edges.find((edge) => edge.id === 'edge_branch')).toMatchObject({
      condition: {
        type: 'EXPRESSION',
        expression: 'amount > 1000',
      },
    })

    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:3',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 3,
      status: 'PUBLISHED',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl,
      bpmnXml: '<process />',
    })

    expect(hydrated.edges.find((edge) => edge.id === 'edge_branch')?.data?.condition)
      .toMatchObject({
        type: 'EXPRESSION',
        expression: 'amount > 1000',
      })
  })

  it('hydrates condition field expressions and node form metadata from persisted DSL', () => {
    const detail = {
      processDefinitionId: 'oa_leave:4',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 4,
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
        formFields: [{ fieldKey: 'amount', label: '金额', valueType: 'number' }],
        settings: {
          allowWithdraw: true,
          allowUrge: true,
          allowTransfer: true,
        },
        nodes: [
          {
            id: 'condition_1',
            type: 'condition',
            name: '金额条件',
            description: '根据金额大小决定审批路径',
            position: { x: 320, y: 100 },
            config: {
              defaultEdgeId: 'edge_default',
              expressionMode: 'FIELD_COMPARE',
              expressionFieldKey: 'amount',
            },
            ui: { width: 240, height: 88 },
          },
          {
            id: 'approve_1',
            type: 'approver',
            name: '财务审批',
            description: '财务审批节点',
            position: { x: 540, y: 100 },
            config: {
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
              nodeFormKey: 'finance-approve-form',
              nodeFormVersion: '1.0.0',
              fieldBindings: [
                {
                  source: 'PROCESS_FORM',
                  sourceFieldKey: 'amount',
                  targetFieldKey: 'approvedAmount',
                },
              ],
            },
            ui: { width: 240, height: 88 },
          },
        ],
        edges: [
          {
            id: 'edge_default',
            source: 'condition_1',
            target: 'approve_1',
            priority: 1,
            label: '默认通过',
            condition: {
              type: 'EXPRESSION',
              expression: 'amount > 1000',
            },
          },
        ],
      },
      bpmnXml: '<process />',
    } satisfies ProcessDefinitionDetailResponse

    const hydrated = processDefinitionDetailToWorkflowSnapshot(detail)

    expect(hydrated.nodes[0]?.data.config).toMatchObject({
      defaultEdgeId: 'edge_default',
      expressionMode: 'FIELD_COMPARE',
      expressionFieldKey: 'amount',
    })
    expect(hydrated.nodes[1]?.data.config).toMatchObject({
      nodeFormKey: 'finance-approve-form',
      nodeFormVersion: '1.0.0',
      fieldBindings: [
        {
          source: 'PROCESS_FORM',
          sourceFieldKey: 'amount',
          targetFieldKey: 'approvedAmount',
        },
      ],
    })
  })
})
