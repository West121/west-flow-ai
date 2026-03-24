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
            formulaExpression: '',
          },
          approvalPolicy: {
            type: 'SEQUENTIAL',
            voteThreshold: null,
          },
          timeoutPolicy: {
            enabled: false,
            durationMinutes: null,
            action: 'APPROVE',
          },
          reminderPolicy: {
            enabled: false,
            firstReminderAfterMinutes: null,
            repeatIntervalMinutes: null,
            maxTimes: null,
            channels: ['IN_APP'],
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

function buildDynamicBuilderNode(): WorkflowNode {
  return {
    id: 'dynamic_1',
    type: 'workflow',
    position: { x: 320, y: 100 },
    data: {
      kind: 'dynamic-builder',
      label: '动态构建',
      description: '运行时生成追加审批链路',
      tone: 'brand',
      config: {
        buildMode: 'SUBPROCESS_CALLS',
        sourceMode: 'MANUAL_TEMPLATE',
        sceneCode: 'purchase_review_scene',
        executionStrategy: 'TEMPLATE_FIRST',
        fallbackStrategy: 'USE_TEMPLATE',
        ruleExpression: '',
        manualTemplateCode: 'append_purchase_review',
        appendPolicy: 'PARALLEL_WITH_CURRENT',
        maxGeneratedCount: 2,
        terminatePolicy: 'TERMINATE_PARENT_AND_GENERATED',
      } as never,
    },
  }
}

function buildModelDrivenDynamicBuilderNode(): WorkflowNode {
  const node = buildDynamicBuilderNode()
  node.data.config = {
    ...(node.data.config as Record<string, unknown>),
    sourceMode: 'MODEL_DRIVEN',
    sceneCode: 'leave_auto_scene',
    executionStrategy: 'TEMPLATE_FIRST',
    fallbackStrategy: 'KEEP_CURRENT',
    manualTemplateCode: '',
  } as never
  return node
}

function buildSubprocessNode(): WorkflowNode {
  return {
    id: 'subprocess_1',
    type: 'workflow',
    position: { x: 320, y: 100 },
    data: {
      kind: 'subprocess',
      label: '子流程调用',
      description: '调用下游流程并回传',
      tone: 'brand',
      config: {
        calledProcessKey: 'plm_purchase_review',
        calledVersionPolicy: 'FIXED_VERSION',
        calledVersion: 3,
        callScope: 'CHILD_AND_DESCENDANTS',
        joinMode: 'WAIT_PARENT_CONFIRM',
        childStartStrategy: 'SCENE_BINDING',
        parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
        businessBindingMode: 'OVERRIDE',
        terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
        childFinishPolicy: 'TERMINATE_PARENT',
        inputMappings: [
          { source: 'billNo', target: 'sourceBillNo' },
          { source: 'leaveDays', target: 'leaveDays' },
        ],
        outputMappings: [{ source: 'approvedResult', target: 'purchaseResult' }],
      } as never,
    },
  }
}

function buildInclusiveNode(
  direction: 'SPLIT' | 'JOIN',
  config: Record<string, unknown> = {}
): WorkflowNode {
  return {
    id: `inclusive_${direction.toLowerCase()}`,
    type: 'workflow',
    position: { x: 320, y: 100 },
    data: {
      kind: 'inclusive',
      label: '包容分支',
      description: '支持多条条件同时命中',
      tone: 'warning',
      config: {
        gatewayDirection: direction,
        ...config,
      } as never,
    },
  }
}

describe('workflow designer dsl mapping', () => {
  it('maps the canvas snapshot to the frozen process definition DSL', () => {
    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
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
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
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

  it('persists countersign fields in the DSL config', () => {
    const snapshotWithCountersign = {
      ...snapshot,
      nodes: snapshot.nodes.map((node) =>
        node.id === 'approve_1'
          ? ({
              ...node,
              data: {
                ...node.data,
                config: {
                  ...(node.data.config as object),
                  approvalMode: 'VOTE',
                  voteRule: {
                    thresholdPercent: 60,
                    passCondition: 'THRESHOLD_REACHED',
                    rejectCondition: 'REJECT_THRESHOLD',
                    weights: [
                      { userId: 'usr_002', weight: 40 },
                      { userId: 'usr_003', weight: 60 },
                    ],
                  },
                  reapprovePolicy: 'CONTINUE_PROGRESS',
                  autoFinishRemaining: true,
                  approvalPolicy: {
                    type: 'VOTE',
                    voteThreshold: 60,
                  },
                  timeoutPolicy: {
                    enabled: false,
                    durationMinutes: null,
                    action: 'APPROVE',
                  },
                  reminderPolicy: {
                    enabled: false,
                    firstReminderAfterMinutes: null,
                    repeatIntervalMinutes: null,
                    maxTimes: null,
                    channels: ['IN_APP'],
                  },
                  operations: ['APPROVE', 'REJECT', 'RETURN'],
                  commentRequired: false,
                  assignment: {
                    mode: 'USER',
                    userIds: ['usr_002', 'usr_003'],
                    roleCodes: [],
                    departmentRef: '',
                    formFieldKey: '',
                  },
                },
              },
            } as unknown as WorkflowNode)
          : node
      ),
    } satisfies WorkflowSnapshot

    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshotWithCountersign, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes[1]?.config).toMatchObject({
      approvalMode: 'VOTE',
      voteRule: {
        thresholdPercent: 60,
        passCondition: 'THRESHOLD_REACHED',
        rejectCondition: 'REJECT_THRESHOLD',
        weights: [
          { userId: 'usr_002', weight: 40 },
          { userId: 'usr_003', weight: 60 },
        ],
      },
      reapprovePolicy: 'CONTINUE_PROGRESS',
      autoFinishRemaining: true,
    })
  })

  it('persists dynamic builder fields in the DSL config', () => {
    const snapshotWithDynamicBuilder = {
      ...snapshot,
      nodes: snapshot.nodes.map((node) =>
        node.id === 'approve_1' ? buildDynamicBuilderNode() : node
      ),
    } satisfies WorkflowSnapshot

    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshotWithDynamicBuilder, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes[1]?.type).toBe('dynamic_builder')
    expect(dsl.nodes[1]?.config).toMatchObject({
      buildMode: 'SUBPROCESS_CALLS',
      sourceMode: 'MANUAL_TEMPLATE',
      sceneCode: 'purchase_review_scene',
      executionStrategy: 'TEMPLATE_FIRST',
      fallbackStrategy: 'USE_TEMPLATE',
      manualTemplateCode: 'append_purchase_review',
      appendPolicy: 'PARALLEL_WITH_CURRENT',
      maxGeneratedCount: 2,
      terminatePolicy: 'TERMINATE_PARENT_AND_GENERATED',
    })
  })

  it('persists model driven dynamic builder fields in the DSL config', () => {
    const snapshotWithDynamicBuilder = {
      ...snapshot,
      nodes: snapshot.nodes.map((node) =>
        node.id === 'approve_1' ? buildModelDrivenDynamicBuilderNode() : node
      ),
    } satisfies WorkflowSnapshot

    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshotWithDynamicBuilder, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes[1]?.config).toMatchObject({
      sourceMode: 'MODEL_DRIVEN',
      sceneCode: 'leave_auto_scene',
      executionStrategy: 'TEMPLATE_FIRST',
      fallbackStrategy: 'KEEP_CURRENT',
      manualTemplateCode: '',
    })
  })

  it('persists inclusive gateway direction into the DSL node type', () => {
    const splitSnapshot: WorkflowSnapshot = {
      ...snapshot,
      nodes: [snapshot.nodes[0], buildInclusiveNode('SPLIT'), snapshot.nodes[2]],
      edges: [
        { id: 'edge_1', source: 'start_1', target: 'inclusive_split', type: 'smoothstep' },
        { id: 'edge_2', source: 'inclusive_split', target: 'end_1', type: 'smoothstep' },
      ],
      selectedNodeId: 'inclusive_split',
    }
    const joinSnapshot: WorkflowSnapshot = {
      ...snapshot,
      nodes: [snapshot.nodes[0], buildInclusiveNode('JOIN'), snapshot.nodes[2]],
      edges: [
        { id: 'edge_1', source: 'start_1', target: 'inclusive_join', type: 'smoothstep' },
        { id: 'edge_2', source: 'inclusive_join', target: 'end_1', type: 'smoothstep' },
      ],
      selectedNodeId: 'inclusive_join',
    }

    const splitDsl = workflowSnapshotToProcessDefinitionDsl(splitSnapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })
    const joinDsl = workflowSnapshotToProcessDefinitionDsl(joinSnapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(splitDsl.nodes[1]?.type).toBe('inclusive_split')
    expect(joinDsl.nodes[1]?.type).toBe('inclusive_join')
  })

  it('round-trips inclusive split branch expressions', () => {
    const inclusiveSnapshot: WorkflowSnapshot = {
      nodes: [
        snapshot.nodes[0]!,
        buildInclusiveNode('SPLIT', {
          defaultBranchId: 'edge_urgent',
          requiredBranchCount: 2,
          branchMergePolicy: 'REQUIRED_COUNT',
        }),
        {
          ...snapshot.nodes[1]!,
          id: 'approve_yes',
          data: {
            ...snapshot.nodes[1]!.data,
            label: '高金额审批',
          },
        },
        {
          ...snapshot.nodes[1]!,
          id: 'approve_urgent',
          data: {
            ...snapshot.nodes[1]!.data,
            label: '紧急审批',
          },
        },
        buildInclusiveNode('JOIN'),
        snapshot.nodes[2]!,
      ],
      edges: [
        { id: 'edge_start', source: 'start_1', target: 'inclusive_split', type: 'smoothstep' },
        {
          id: 'edge_yes',
          source: 'inclusive_split',
          target: 'approve_yes',
          type: 'smoothstep',
          data: {
            priority: 2,
            condition: {
              type: 'EXPRESSION',
              expression: 'amount > 10000',
            },
          },
        },
        {
          id: 'edge_urgent',
          source: 'inclusive_split',
          target: 'approve_urgent',
          type: 'smoothstep',
          data: {
            priority: 1,
            condition: {
              type: 'EXPRESSION',
              expression: 'urgent == true',
            },
          },
        },
        { id: 'edge_join_1', source: 'approve_yes', target: 'inclusive_join', type: 'smoothstep' },
        { id: 'edge_join_2', source: 'approve_urgent', target: 'inclusive_join', type: 'smoothstep' },
        { id: 'edge_end', source: 'inclusive_join', target: 'end_1', type: 'smoothstep' },
      ],
      selectedNodeId: 'inclusive_split',
    }

    const dsl = workflowSnapshotToProcessDefinitionDsl(inclusiveSnapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes.find((node) => node.id === 'inclusive_split')?.config).toMatchObject({
      gatewayDirection: 'SPLIT',
      defaultBranchId: 'edge_urgent',
      requiredBranchCount: 2,
      branchMergePolicy: 'REQUIRED_COUNT',
    })
    expect(dsl.edges.find((edge) => edge.id === 'edge_yes')?.priority).toBe(2)
    expect(dsl.edges.find((edge) => edge.id === 'edge_urgent')?.priority).toBe(1)
    expect(dsl.edges.find((edge) => edge.id === 'edge_yes')?.condition).toMatchObject({
      type: 'EXPRESSION',
      expression: 'amount > 10000',
    })

    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:inclusive-1',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 1,
      status: 'PUBLISHED',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl,
      bpmnXml: '<process />',
    })

    expect(hydrated.edges.find((edge) => edge.id === 'edge_urgent')?.data?.condition)
      .toMatchObject({
        type: 'EXPRESSION',
        expression: 'urgent == true',
      })
    expect(hydrated.edges.find((edge) => edge.id === 'edge_urgent')?.data?.priority).toBe(1)
    expect(hydrated.edges.find((edge) => edge.id === 'edge_yes')?.data?.priority).toBe(2)
  })

  it('round-trips formula branch expressions', () => {
    const formulaSnapshot: WorkflowSnapshot = {
      nodes: [
        snapshot.nodes[0]!,
        {
          ...snapshot.nodes[1]!,
          id: 'condition_1',
          data: {
            ...snapshot.nodes[1]!.data,
            kind: 'condition',
            label: '排他网关',
            description: '公式分支',
            config: {
              defaultEdgeId: 'edge_default',
              expressionMode: 'FORMULA',
              expressionFieldKey: '',
            },
          },
        },
        snapshot.nodes[2]!,
      ],
      edges: [
        { id: 'edge_default', source: 'start_1', target: 'condition_1', type: 'smoothstep' },
        {
          id: 'edge_formula',
          source: 'condition_1',
          target: 'end_1',
          type: 'smoothstep',
          label: '公式分支',
          data: {
            condition: {
              type: 'FORMULA',
              formulaExpression: 'ifElse(amount > 10000, "A", "B")',
            },
          },
        },
      ],
      selectedNodeId: 'condition_1',
    }

    const dsl = workflowSnapshotToProcessDefinitionDsl(formulaSnapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.edges.find((edge) => edge.id === 'edge_formula')?.condition).toMatchObject({
      type: 'FORMULA',
      formulaExpression: 'ifElse(amount > 10000, "A", "B")',
    })

    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:formula-1',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 1,
      status: 'PUBLISHED',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl,
      bpmnXml: '<process />',
    })

    expect(hydrated.edges.find((edge) => edge.id === 'edge_formula')?.data?.condition)
      .toMatchObject({
        type: 'FORMULA',
        formulaExpression: 'ifElse(amount > 10000, "A", "B")',
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
        processFormKey: 'oa-leave-start-form',
        processFormVersion: '1.0.0',
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
        processFormKey: 'oa-leave-start-form',
        processFormVersion: '1.0.0',
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
              timeoutPolicy: {
                enabled: false,
                durationMinutes: null,
                action: 'APPROVE',
              },
              reminderPolicy: {
                enabled: false,
                firstReminderAfterMinutes: null,
                repeatIntervalMinutes: null,
                maxTimes: null,
                channels: ['IN_APP'],
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

  it('persists subprocess strategy fields in the DSL config', () => {
    const snapshotWithSubprocess: WorkflowSnapshot = {
      ...snapshot,
      nodes: [snapshot.nodes[0]!, buildSubprocessNode(), snapshot.nodes[2]!],
      edges: [
        {
          id: 'edge_1',
          source: 'start_1',
          target: 'subprocess_1',
          type: 'smoothstep',
        },
        {
          id: 'edge_2',
          source: 'subprocess_1',
          target: 'end_1',
          type: 'smoothstep',
        },
      ],
      selectedNodeId: 'subprocess_1',
    }

    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshotWithSubprocess, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes).toHaveLength(3)
    expect(dsl.nodes[1]?.type).toBe('subprocess')
    expect(dsl.nodes[1]?.config).toMatchObject({
      calledProcessKey: 'plm_purchase_review',
      calledVersionPolicy: 'FIXED_VERSION',
      calledVersion: 3,
      callScope: 'CHILD_AND_DESCENDANTS',
      joinMode: 'WAIT_PARENT_CONFIRM',
      childStartStrategy: 'SCENE_BINDING',
      parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
      businessBindingMode: 'OVERRIDE',
      terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
      childFinishPolicy: 'TERMINATE_PARENT',
      inputMappings: [
        { source: 'billNo', target: 'sourceBillNo' },
        { source: 'leaveDays', target: 'leaveDays' },
      ],
      outputMappings: [{ source: 'approvedResult', target: 'purchaseResult' }],
    })
  })

  it('hydrates dynamic builder nodes from the persisted process definition detail', () => {
    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:3',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 3,
      status: 'PUBLISHED',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl: {
        dslVersion: '1.0.0',
        processKey: 'oa_leave',
        processName: '请假审批',
        category: 'OA',
        processFormKey: 'oa-leave-start-form',
        processFormVersion: '1.0.0',
        formFields: [],
        settings: {
          allowWithdraw: true,
          allowUrge: true,
          allowTransfer: true,
        },
        nodes: [
          {
            id: 'dynamic_1',
            type: 'dynamic_builder',
            name: '动态构建',
            description: '运行时生成追加审批链路',
            position: { x: 320, y: 100 },
            config: {
              buildMode: 'APPROVER_TASKS',
              sourceMode: 'RULE',
              sceneCode: 'leave_overtime_scene',
              executionStrategy: 'RULE_FIRST',
              fallbackStrategy: 'KEEP_CURRENT',
              ruleExpression: '${amount > 1000}',
              manualTemplateCode: '',
              appendPolicy: 'SERIAL_AFTER_CURRENT',
              maxGeneratedCount: 1,
              terminatePolicy: 'TERMINATE_GENERATED_ONLY',
            },
            ui: {
              width: 220,
              height: 96,
            },
          },
        ],
        edges: [],
      },
      bpmnXml: '<process />',
    })

    expect(hydrated.nodes).toHaveLength(1)
    expect(hydrated.nodes[0]?.data.kind).toBe('dynamic-builder')
    expect(hydrated.nodes[0]?.data.config).toMatchObject({
      buildMode: 'APPROVER_TASKS',
      sourceMode: 'RULE',
      sceneCode: 'leave_overtime_scene',
      executionStrategy: 'RULE_FIRST',
      fallbackStrategy: 'KEEP_CURRENT',
      ruleExpression: '${amount > 1000}',
      appendPolicy: 'SERIAL_AFTER_CURRENT',
      maxGeneratedCount: 1,
      terminatePolicy: 'TERMINATE_GENERATED_ONLY',
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
                formulaExpression: '',
              },
              approvalPolicy: {
                type: 'SEQUENTIAL',
                voteThreshold: null,
              },
              timeoutPolicy: {
                enabled: false,
                durationMinutes: null,
                action: 'APPROVE',
              },
              reminderPolicy: {
                enabled: false,
                firstReminderAfterMinutes: null,
                repeatIntervalMinutes: null,
                maxTimes: null,
                channels: ['IN_APP'],
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
                formulaExpression: '',
              },
              approvalPolicy: {
                type: 'SEQUENTIAL',
                voteThreshold: null,
              },
              timeoutPolicy: {
                enabled: false,
                durationMinutes: null,
                action: 'APPROVE',
              },
              reminderPolicy: {
                enabled: false,
                firstReminderAfterMinutes: null,
                repeatIntervalMinutes: null,
                maxTimes: null,
                channels: ['IN_APP'],
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
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
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
        processFormKey: 'oa-leave-start-form',
        processFormVersion: '1.0.0',
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

  it('keeps timer and trigger node configs when mapping between canvas and DSL', () => {
    const automationSnapshot: WorkflowSnapshot = {
      nodes: [
        {
          id: 'timer_1',
          type: 'workflow',
          position: { x: 100, y: 100 },
          data: {
            kind: 'timer',
            label: '定时等待',
            description: '到点后推进',
            tone: 'warning',
            config: {
              scheduleType: 'RELATIVE_TO_ARRIVAL',
              delayMinutes: 30,
              runAt: '',
              comment: '午休后执行',
            },
          },
        },
        {
          id: 'trigger_1',
          type: 'workflow',
          position: { x: 340, y: 100 },
          data: {
            kind: 'trigger',
            label: '业务触发',
            description: '调用外部触发器',
            tone: 'brand',
            config: {
              triggerMode: 'SCHEDULED',
              scheduleType: 'ABSOLUTE_TIME',
              runAt: '2026-03-23T09:30:00+08:00',
              delayMinutes: null,
              triggerKey: 'sync_invoice',
              retryTimes: 3,
              retryIntervalMinutes: 10,
              payloadTemplate: '{"source":"workflow"}',
            },
          },
        },
      ],
      edges: [],
      selectedNodeId: 'timer_1',
    }

    const dsl = workflowSnapshotToProcessDefinitionDsl(automationSnapshot, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes[0]?.type).toBe('timer')
    expect(dsl.nodes[0]?.config).toMatchObject({
      scheduleType: 'RELATIVE_TO_ARRIVAL',
      delayMinutes: 30,
      comment: '午休后执行',
    })
    expect(dsl.nodes[1]?.type).toBe('trigger')
    expect(dsl.nodes[1]?.config).toMatchObject({
      triggerMode: 'SCHEDULED',
      scheduleType: 'ABSOLUTE_TIME',
      triggerKey: 'sync_invoice',
      retryTimes: 3,
      retryIntervalMinutes: 10,
      payloadTemplate: '{"source":"workflow"}',
    })

    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:5',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 5,
      status: 'PUBLISHED',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl,
      bpmnXml: '<process />',
    })

    expect(hydrated.nodes[0]?.data.kind).toBe('timer')
    expect(hydrated.nodes[0]?.data.config).toMatchObject({
      scheduleType: 'RELATIVE_TO_ARRIVAL',
      delayMinutes: 30,
      comment: '午休后执行',
    })
    expect(hydrated.nodes[1]?.data.kind).toBe('trigger')
    expect(hydrated.nodes[1]?.data.config).toMatchObject({
      triggerMode: 'SCHEDULED',
      triggerKey: 'sync_invoice',
      retryTimes: 3,
      retryIntervalMinutes: 10,
    })
  })

  it('persists approver timeout and reminder policies in the DSL', () => {
    const snapshotWithAutomation = {
      ...snapshot,
      nodes: snapshot.nodes.map((node) =>
        node.id === 'approve_1'
          ? ({
              ...node,
              data: {
                ...node.data,
                config: {
                  ...(node.data.config as object),
                  timeoutPolicy: {
                    enabled: true,
                    durationMinutes: 45,
                    action: 'REJECT',
                  },
                  reminderPolicy: {
                    enabled: true,
                    firstReminderAfterMinutes: 10,
                    repeatIntervalMinutes: 15,
                    maxTimes: 3,
                    channels: ['IN_APP', 'EMAIL'],
                  },
                },
              },
            } as WorkflowNode)
          : node
      ),
    } satisfies WorkflowSnapshot

    const dsl = workflowSnapshotToProcessDefinitionDsl(snapshotWithAutomation, {
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
      formFields: [],
    })

    expect(dsl.nodes[1]?.config).toMatchObject({
      timeoutPolicy: {
        enabled: true,
        durationMinutes: 45,
        action: 'REJECT',
      },
      reminderPolicy: {
        enabled: true,
        firstReminderAfterMinutes: 10,
        repeatIntervalMinutes: 15,
        maxTimes: 3,
        channels: ['IN_APP', 'EMAIL'],
      },
    })

    const hydrated = processDefinitionDetailToWorkflowSnapshot({
      processDefinitionId: 'oa_leave:6',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 6,
      status: 'PUBLISHED',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl,
      bpmnXml: '<process />',
    })

    expect(hydrated.nodes[1]?.data.config).toMatchObject({
      timeoutPolicy: {
        enabled: true,
        durationMinutes: 45,
        action: 'REJECT',
      },
      reminderPolicy: {
        enabled: true,
        firstReminderAfterMinutes: 10,
        repeatIntervalMinutes: 15,
        maxTimes: 3,
        channels: ['IN_APP', 'EMAIL'],
      },
    })
  })
})
