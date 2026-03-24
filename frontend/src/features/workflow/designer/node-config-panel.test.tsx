import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NodeConfigPanel } from './node-config-panel'
import { workflowNodeTemplates } from './palette'
import { type WorkflowApproverNodeConfig, type WorkflowEdge, type WorkflowNode } from './types'

vi.mock('./selection-api', () => ({
  searchPrincipalOptions: vi.fn(async (kind: string) => {
    if (kind === 'ROLE') {
      return [
        {
          id: 'role_manager',
          label: '部门负责人',
          description: 'OA 审批角色',
          kind: 'ROLE',
        },
        {
          id: 'OA_USER',
          label: 'OA 用户',
          description: 'OA 基础角色',
          kind: 'ROLE',
        },
      ]
    }

    if (kind === 'DEPARTMENT') {
      return [
        {
          id: 'dept_root',
          label: '总部',
          description: '西流科技',
          kind: 'DEPARTMENT',
          companyId: 'cmp_001',
          parentId: null,
          groupLabel: '西流科技',
        },
        {
          id: 'dept_child',
          label: '人事部',
          description: '西流科技',
          kind: 'DEPARTMENT',
          companyId: 'cmp_001',
          parentId: 'dept_root',
          groupLabel: '西流科技',
        },
      ]
    }

    return [
      {
        id: 'usr_002',
        label: '张三',
        description: 'zhangsan · 行政中心 · 部门负责人',
        kind: 'USER',
      },
      {
        id: 'usr_003',
        label: '李四',
        description: 'lisi · 行政中心 · 审批专员',
        kind: 'USER',
      },
    ]
  }),
}))

function buildTimerNode(): WorkflowNode {
  return {
    id: 'timer_1',
    type: 'workflow',
    position: { x: 100, y: 100 },
      data: {
      kind: 'timer',
      label: '定时等待',
      description: '到点后推进',
      tone: 'warning',
      config: {
        scheduleType: 'ABSOLUTE_TIME',
        delayMinutes: null,
        runAt: '2026-03-23T09:30:00+08:00',
        comment: '午休后执行',
      },
    },
  }
}

function buildApproverNode(
  assignmentMode:
    | 'USER'
    | 'ROLE'
    | 'DEPARTMENT'
    | 'DEPARTMENT_AND_CHILDREN'
    | 'FORM_FIELD'
    | 'FORMULA' = 'USER'
): WorkflowNode {
  return {
    id: 'approve_1',
    type: 'workflow',
    position: { x: 100, y: 100 },
    data: {
      kind: 'approver',
      label: '审批',
      description: '审批节点',
      tone: 'brand',
      config: {
        assignment: {
          mode: assignmentMode,
          userIds: ['usr_002', 'usr_003'],
          roleCodes: [],
          departmentRef: '',
          formFieldKey: '',
          formulaExpression: '',
        },
        approvalPolicy: {
          type: 'SEQUENTIAL',
          voteThreshold: null,
        },
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
        operations: ['APPROVE', 'REJECT', 'RETURN'],
        commentRequired: false,
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
  }
}

function buildSubprocessNode(): WorkflowNode {
  return {
    id: 'subprocess_1',
    type: 'workflow',
    position: { x: 100, y: 100 },
    data: {
      kind: 'subprocess',
      label: '采购子流程',
      description: '调用采购会签流程',
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
        inputMappings: [{ source: 'billNo', target: 'sourceBillNo' }],
        outputMappings: [{ source: 'approvedResult', target: 'purchaseResult' }],
      } as never,
    },
  }
}

function buildDynamicBuilderNode(): WorkflowNode {
  return {
    id: 'dynamic_1',
    type: 'workflow',
    position: { x: 100, y: 100 },
    data: {
      kind: 'dynamic-builder',
      label: '动态构建',
      description: '运行时生成追加审批链路',
      tone: 'brand',
      config: {
        buildMode: 'APPROVER_TASKS',
        sourceMode: 'MANUAL_TEMPLATE',
        sceneCode: 'leave_review',
        executionStrategy: 'TEMPLATE_FIRST',
        fallbackStrategy: 'USE_TEMPLATE',
        ruleExpression: '',
        manualTemplateCode: 'append_purchase_review',
        appendPolicy: 'SERIAL_AFTER_CURRENT',
        maxGeneratedCount: 1,
        terminatePolicy: 'TERMINATE_GENERATED_ONLY',
      } as never,
    },
  }
}

function buildInclusiveNode(
  direction: 'SPLIT' | 'JOIN' = 'JOIN',
  config: Record<string, unknown> = {}
): WorkflowNode {
  return {
    id: 'inclusive_1',
    type: 'workflow',
    position: { x: 100, y: 100 },
    data: {
      kind: 'inclusive',
      label: '包容分支',
      description: '命中多个条件分支',
      tone: 'warning',
      config: {
        gatewayDirection: direction,
        ...config,
      } as never,
    },
  }
}

const edges: WorkflowEdge[] = []
const inclusiveSplitEdges: WorkflowEdge[] = [
  {
    id: 'edge_yes',
    source: 'inclusive_1',
    target: 'approve_yes',
    type: 'smoothstep',
    label: '金额超标',
    data: {
      condition: {
        type: 'EXPRESSION',
        expression: 'amount > 10000',
      },
    },
  },
  {
    id: 'edge_urgent',
    source: 'inclusive_1',
    target: 'approve_urgent',
    type: 'smoothstep',
    label: '紧急场景',
    data: {
      condition: {
        type: 'EXPRESSION',
        expression: 'urgent == true',
      },
    },
  },
]

const conditionEdges: WorkflowEdge[] = [
  {
    id: 'edge_yes',
    source: 'condition_1',
    target: 'approve_yes',
    type: 'smoothstep',
    label: '正常审批',
    data: {
      condition: {
        type: 'EXPRESSION',
        expression: 'amount > 10000',
      },
    },
  },
  {
    id: 'edge_formula',
    source: 'condition_1',
    target: 'approve_formula',
    type: 'smoothstep',
    label: '公式审批',
    data: {
      condition: {
        type: 'FORMULA',
        formulaExpression: 'ifElse(amount > 10000, "A", "B")',
      },
    },
  },
]

describe('workflow designer node config panel', () => {
  it('exposes subprocess node template in the palette', () => {
    expect(workflowNodeTemplates.some((template) => template.kind === 'subprocess')).toBe(true)
    expect(
      workflowNodeTemplates.some((template) => template.kind === 'dynamic-builder')
    ).toBe(true)
    expect(workflowNodeTemplates.some((template) => template.kind === 'inclusive')).toBe(true)
  })

  it('submits timer node automation settings', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel node={buildTimerNode()} edges={edges} onApply={onApply} />
    )

    expect(screen.getByText('定时节点')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('textbox', { name: '执行时间' }), {
      target: { value: '2026-03-23T09:30:00+08:00' },
    })
    fireEvent.change(screen.getByRole('textbox', { name: '说明' }), {
      target: { value: '每天早上执行' },
    })

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'timer_1',
      expect.objectContaining({
        label: '定时等待',
        description: '到点后推进',
        config: expect.objectContaining({
          scheduleType: 'ABSOLUTE_TIME',
          runAt: '2026-03-23T09:30:00+08:00',
          comment: '每天早上执行',
        }),
      }),
      undefined
    )
  })

  it('hydrates approver timeout and reminder settings from config', () => {
    render(<NodeConfigPanel node={buildApproverNode()} edges={edges} onApply={vi.fn()} />)

    expect(screen.getByText('超时审批')).toBeInTheDocument()
    expect(screen.getByText('自动提醒')).toBeInTheDocument()
    expect(screen.getByText('重新审批策略')).toBeInTheDocument()
    expect(screen.getByDisplayValue('60')).toBeInTheDocument()
    expect(screen.getByDisplayValue('45')).toBeInTheDocument()
    expect(screen.getByDisplayValue('10')).toBeInTheDocument()
    expect(screen.getByDisplayValue('15')).toBeInTheDocument()
    expect(screen.getByDisplayValue('3')).toBeInTheDocument()
  })

  it('supports form field assignment with the field selector', async () => {
    const onApply = vi.fn()

    const node = buildApproverNode('FORM_FIELD')
    node.data.config = {
      ...node.data.config,
      approvalMode: 'SEQUENTIAL',
      approvalPolicy: {
        type: 'SEQUENTIAL',
        voteThreshold: null,
      },
      voteRule: {
        thresholdPercent: null,
        passCondition: 'THRESHOLD_REACHED',
        rejectCondition: 'REJECT_THRESHOLD',
        weights: [],
      },
    } as typeof node.data.config

    render(
      <NodeConfigPanel
        node={node}
        edges={edges}
        onApply={onApply}
      />
    )

    expect(screen.getByText('表单字段编码')).toBeInTheDocument()
    const fieldInput = screen.getByPlaceholderText('请选择或输入字段编码')
    fireEvent.change(fieldInput, { target: { value: 'departmentId' } })
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          assignment: expect.objectContaining({
            mode: 'FORM_FIELD',
            formFieldKey: 'departmentId',
          }),
        }),
      }),
      undefined
    )
  })

  it('supports department countersign assignment in or-sign mode', async () => {
    const onApply = vi.fn()

    const node = buildApproverNode('DEPARTMENT_AND_CHILDREN')
    const approverConfig = node.data.config as WorkflowApproverNodeConfig
    node.data.config = {
      ...approverConfig,
      assignment: {
        ...approverConfig.assignment,
        departmentRef: 'dept_root',
      },
      approvalMode: 'OR_SIGN',
      approvalPolicy: {
        type: 'OR_SIGN',
        voteThreshold: null,
      },
      voteRule: {
        thresholdPercent: null,
        passCondition: 'THRESHOLD_REACHED',
        rejectCondition: 'REJECT_THRESHOLD',
        weights: [],
      },
      autoFinishRemaining: true,
    } as typeof node.data.config

    render(<NodeConfigPanel node={node} edges={edges} onApply={onApply} />)

    expect(screen.getByText('部门编码')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByDisplayValue('总部')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: '选择' }))

    await waitFor(() => expect(screen.getByRole('button', { name: /人事部/ })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: /总部/ }))
    fireEvent.click(screen.getByRole('button', { name: /人事部/ }))
    fireEvent.click(screen.getByRole('button', { name: '确认' }))

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          approvalMode: 'OR_SIGN',
          autoFinishRemaining: true,
          assignment: expect.objectContaining({
            mode: 'DEPARTMENT_AND_CHILDREN',
            departmentRef: 'dept_child',
          }),
        }),
      }),
      undefined
    )
  })

  it('supports formula countersign assignment in sequential mode', async () => {
    const onApply = vi.fn()

    const node = buildApproverNode('FORMULA')
    const approverConfig = node.data.config as WorkflowApproverNodeConfig
    node.data.config = {
      ...approverConfig,
      approvalMode: 'SEQUENTIAL',
      approvalPolicy: {
        type: 'SEQUENTIAL',
        voteThreshold: null,
      },
      voteRule: {
        thresholdPercent: null,
        passCondition: 'THRESHOLD_REACHED',
        rejectCondition: 'REJECT_THRESHOLD',
        weights: [],
      },
      assignment: {
        ...approverConfig.assignment,
        formulaExpression: 'leaveDays >= 5 ? "usr_003" : managerUserId',
      },
    } as typeof node.data.config

    render(<NodeConfigPanel node={node} edges={edges} onApply={onApply} />)

    expect(screen.getByText('处理人公式')).toBeInTheDocument()
    const formulaInput = screen.getByDisplayValue('leaveDays >= 5 ? "usr_003" : managerUserId')
    fireEvent.change(formulaInput, {
      target: { value: 'ifElse(leaveDays >= 5, "usr_003", managerUserId)' },
    })
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          approvalMode: 'SEQUENTIAL',
          assignment: expect.objectContaining({
            mode: 'FORMULA',
            formulaExpression: 'ifElse(leaveDays >= 5, "usr_003", managerUserId)',
          }),
        }),
      }),
      undefined
    )
  })

  it('submits countersign fields back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(<NodeConfigPanel node={buildApproverNode()} edges={edges} onApply={onApply} />)

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          approvalMode: 'VOTE',
          voteRule: expect.objectContaining({
            thresholdPercent: 60,
            weights: [
              { userId: 'usr_002', weight: 40 },
              { userId: 'usr_003', weight: 60 },
            ],
          }),
          reapprovePolicy: 'CONTINUE_PROGRESS',
          autoFinishRemaining: true,
        }),
      }),
      undefined
    )
  })

  it('allows role based parallel countersign configuration', async () => {
    const onApply = vi.fn()
    const node = buildApproverNode('ROLE')
    const approverConfig = node.data.config as WorkflowApproverNodeConfig
    node.data.config = {
      ...approverConfig,
      approvalMode: 'PARALLEL',
      approvalPolicy: {
        type: 'PARALLEL',
        voteThreshold: null,
      },
      assignment: {
        ...approverConfig.assignment,
        userIds: [],
        roleCodes: ['OA_USER'],
      },
      voteRule: {
        thresholdPercent: null,
        passCondition: 'THRESHOLD_REACHED',
        rejectCondition: 'REJECT_THRESHOLD',
        weights: [],
      },
    } as typeof node.data.config

    render(<NodeConfigPanel node={node} edges={edges} onApply={onApply} />)

    await waitFor(() => expect(screen.getByDisplayValue('OA 用户')).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: '选择' }))
    await waitFor(() => expect(screen.getByText('部门负责人')).toBeInTheDocument())
    fireEvent.click(screen.getByText('部门负责人'))
    fireEvent.click(screen.getByRole('button', { name: '确认' }))

    await waitFor(() =>
      expect(screen.getByDisplayValue('OA 用户, 部门负责人')).toBeInTheDocument()
    )
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          approvalMode: 'PARALLEL',
          assignment: expect.objectContaining({
            mode: 'ROLE',
            roleCodes: ['OA_USER', 'role_manager'],
          }),
        }),
      }),
      undefined
    )
  })

  it('allows role based vote countersign configuration', async () => {
    const onApply = vi.fn()
    const node = buildApproverNode('ROLE')
    const approverConfig = node.data.config as WorkflowApproverNodeConfig
    node.data.config = {
      ...approverConfig,
      approvalMode: 'VOTE',
      approvalPolicy: {
        type: 'VOTE',
        voteThreshold: 60,
      },
      assignment: {
        ...approverConfig.assignment,
        userIds: [],
        roleCodes: ['OA_USER'],
      },
      voteRule: {
        thresholdPercent: 60,
        passCondition: 'THRESHOLD_REACHED',
        rejectCondition: 'REJECT_THRESHOLD',
        weights: [],
      },
    } as typeof node.data.config

    render(<NodeConfigPanel node={node} edges={edges} onApply={onApply} />)

    await waitFor(() => expect(screen.getByDisplayValue('OA 用户')).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: '选择' }))
    await waitFor(() => expect(screen.getByText('部门负责人')).toBeInTheDocument())
    fireEvent.click(screen.getByText('部门负责人'))
    fireEvent.click(screen.getByRole('button', { name: '确认' }))
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          approvalMode: 'VOTE',
          assignment: expect.objectContaining({
            mode: 'ROLE',
            roleCodes: ['OA_USER', 'role_manager'],
          }),
        }),
      }),
      undefined
    )
  })

  it('submits approver field bindings back to the canvas patch', async () => {
    const onApply = vi.fn()

    const node = buildApproverNode('USER')
    node.data.config = {
      ...node.data.config,
      approvalMode: 'SINGLE',
      approvalPolicy: {
        type: 'SINGLE',
        voteThreshold: null,
      },
      voteRule: {
        thresholdPercent: null,
        passCondition: 'THRESHOLD_REACHED',
        rejectCondition: 'REJECT_THRESHOLD',
        weights: [],
      },
      nodeFormKey: 'oa-leave-approve-form',
      nodeFormVersion: '1.0.0',
      fieldBindings: [
        {
          source: 'PROCESS_FORM',
          sourceFieldKey: 'leaveDays',
          targetFieldKey: 'approvedDays',
        },
      ],
    } as typeof node.data.config

    render(
      <NodeConfigPanel
        node={node}
        edges={edges}
        onApply={onApply}
        processFormFields={[
          { fieldKey: 'leaveDays', label: '请假天数', valueType: 'number' },
          { fieldKey: 'leaveType', label: '请假类型', valueType: 'string' },
        ]}
      />
    )

    expect(screen.getByText('字段绑定')).toBeInTheDocument()
    fireEvent.change(screen.getByDisplayValue('approvedDays'), {
      target: { value: 'approvedLeaveDays' },
    })
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          fieldBindings: [
            {
              source: 'PROCESS_FORM',
              sourceFieldKey: 'leaveDays',
              targetFieldKey: 'approvedLeaveDays',
            },
          ],
        }),
      }),
      undefined
    )
  })

  it('hydrates and submits subprocess fields back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(<NodeConfigPanel node={buildSubprocessNode()} edges={edges} onApply={onApply} />)

    expect(screen.getByText('子流程节点')).toBeInTheDocument()
    expect(screen.getByDisplayValue('plm_purchase_review')).toBeInTheDocument()
    expect(screen.getByDisplayValue('3')).toBeInTheDocument()

    fireEvent.change(screen.getByRole('textbox', { name: '子流程 Key' }), {
      target: { value: 'oa_common_subflow' },
    })
    fireEvent.change(screen.getByRole('spinbutton', { name: '固定版本号' }), {
      target: { value: '5' },
    })
    fireEvent.change(screen.getByDisplayValue('sourceBillNo'), {
      target: { value: 'leaveRequestNo' },
    })

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'subprocess_1',
      expect.objectContaining({
        config: expect.objectContaining({
          calledProcessKey: 'oa_common_subflow',
          calledVersionPolicy: 'FIXED_VERSION',
          calledVersion: 5,
          callScope: 'CHILD_AND_DESCENDANTS',
          joinMode: 'WAIT_PARENT_CONFIRM',
          childStartStrategy: 'SCENE_BINDING',
          parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
          businessBindingMode: 'OVERRIDE',
          terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
          childFinishPolicy: 'TERMINATE_PARENT',
          inputMappings: [
            { source: 'billNo', target: 'leaveRequestNo' },
          ],
          outputMappings: [
            { source: 'approvedResult', target: 'purchaseResult' },
          ],
        }),
      }),
      undefined
    )
  })

  it('hydrates and submits dynamic builder fields back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(<NodeConfigPanel node={buildDynamicBuilderNode()} edges={edges} onApply={onApply} />)

    expect(screen.getByText('动态构建节点')).toBeInTheDocument()
    expect(screen.getByDisplayValue('append_purchase_review')).toBeInTheDocument()
    expect(screen.getByDisplayValue('leave_review')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name: '最大生成数量' })).toHaveValue(1)

    fireEvent.change(screen.getByPlaceholderText('leave_overtime_approval'), {
      target: { value: 'leave_overtime_scene' },
    })
    fireEvent.change(screen.getByRole('spinbutton', { name: '最大生成数量' }), {
      target: { value: '2' },
    })
    fireEvent.change(screen.getByPlaceholderText('append_leave_audit'), {
      target: { value: 'append_leave_chain' },
    })

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'dynamic_1',
      expect.objectContaining({
        config: expect.objectContaining({
          buildMode: 'APPROVER_TASKS',
          sourceMode: 'MANUAL_TEMPLATE',
          sceneCode: 'leave_overtime_scene',
          executionStrategy: 'TEMPLATE_FIRST',
          fallbackStrategy: 'USE_TEMPLATE',
          ruleExpression: '',
          manualTemplateCode: 'append_leave_chain',
          appendPolicy: 'SERIAL_AFTER_CURRENT',
          maxGeneratedCount: 2,
          terminatePolicy: 'TERMINATE_GENERATED_ONLY',
        }),
      }),
      undefined
    )
  })

  it('hydrates and submits inclusive gateway direction back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel
        node={buildInclusiveNode()}
        edges={inclusiveSplitEdges}
        onApply={onApply}
      />
    )

    expect(screen.getByText('包容网关')).toBeInTheDocument()
    expect(screen.getAllByText('汇聚').length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('combobox'))
    const splitOptions = screen.getAllByText('分支')
    fireEvent.click(splitOptions[splitOptions.length - 1]!)
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'inclusive_1',
      expect.objectContaining({
        config: expect.objectContaining({
          gatewayDirection: 'SPLIT',
        }),
      }),
      [
        {
          edgeId: 'edge_yes',
          label: '金额超标',
          priority: 1,
          condition: {
            type: 'EXPRESSION',
            expression: 'amount > 10000',
          },
        },
        {
          edgeId: 'edge_urgent',
          label: '紧急场景',
          priority: 2,
          condition: {
            type: 'EXPRESSION',
            expression: 'urgent == true',
          },
        },
      ]
    )
  })

  it('submits inclusive split branch conditions back to the canvas edges', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel
        node={buildInclusiveNode('SPLIT')}
        edges={inclusiveSplitEdges}
        onApply={onApply}
      />
    )

    expect(screen.getByText('包容网关')).toBeInTheDocument()
    fireEvent.change(screen.getAllByRole('textbox', { name: '分支名称' })[0]!, {
      target: { value: '金额超标并会签' },
    })
    fireEvent.change(screen.getByDisplayValue('amount > 10000'), {
      target: { value: 'amount >= 20000' },
    })

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'inclusive_1',
      expect.objectContaining({
        config: expect.objectContaining({
          gatewayDirection: 'SPLIT',
        }),
      }),
      [
        {
          edgeId: 'edge_yes',
          label: '金额超标并会签',
          priority: 1,
          condition: {
            type: 'EXPRESSION',
            expression: 'amount >= 20000',
          },
        },
        {
          edgeId: 'edge_urgent',
          label: '紧急场景',
          priority: 2,
          condition: {
            type: 'EXPRESSION',
            expression: 'urgent == true',
          },
        },
      ]
    )
  })

  it('hydrates and submits inclusive split branch strategy fields', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel
        node={buildInclusiveNode('SPLIT', {
          defaultBranchId: 'edge_urgent',
          requiredBranchCount: 2,
          branchMergePolicy: 'REQUIRED_COUNT',
        })}
        edges={inclusiveSplitEdges}
        onApply={onApply}
      />
    )

    expect(screen.getByText('分支汇聚策略')).toBeInTheDocument()
    expect(screen.getByText('必选分支数')).toBeInTheDocument()
    expect(screen.getAllByLabelText('分支优先级')).toHaveLength(2)
    expect(screen.getAllByLabelText('必选分支数')[0]).toHaveValue('2')

    fireEvent.change(screen.getAllByLabelText('必选分支数')[0]!, { target: { value: '1' } })
    fireEvent.change(screen.getAllByLabelText('分支优先级')[0]!, {
      target: { value: '3' },
    })
    fireEvent.change(screen.getAllByLabelText('分支优先级')[1]!, {
      target: { value: '4' },
    })
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'inclusive_1',
      expect.objectContaining({
        config: expect.objectContaining({
          gatewayDirection: 'SPLIT',
          defaultBranchId: 'edge_urgent',
          requiredBranchCount: 1,
          branchMergePolicy: 'REQUIRED_COUNT',
        }),
      }),
      [
        expect.objectContaining({
          edgeId: 'edge_yes',
          priority: 3,
        }),
        expect.objectContaining({
          edgeId: 'edge_urgent',
          priority: 4,
        }),
      ]
    )
  })

  it('keeps parallel gateway simple without branch strategy fields', () => {
    render(
      <NodeConfigPanel
        node={{
          id: 'parallel_1',
          type: 'workflow',
          position: { x: 100, y: 100 },
          data: {
            kind: 'parallel',
            label: '并行网关',
            description: '并发分支',
            tone: 'neutral',
            config: {
              gatewayDirection: 'SPLIT',
            },
          },
        }}
        edges={edges}
        onApply={vi.fn()}
      />
    )

    expect(screen.getAllByText('并行网关')[0]).toBeInTheDocument()
    expect(screen.queryByText('分支汇聚策略')).not.toBeInTheDocument()
    expect(screen.queryByText('默认分支')).not.toBeInTheDocument()
    expect(screen.queryByText('必选分支数')).not.toBeInTheDocument()
  })

  it('supports formula mode for condition branches', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel
        node={{
          id: 'condition_1',
          type: 'workflow',
          position: { x: 100, y: 100 },
          data: {
            kind: 'condition',
            label: '排他网关',
            description: '条件分支',
            tone: 'warning',
            config: {
              defaultEdgeId: 'edge_yes',
              expressionMode: 'FORMULA',
              expressionFieldKey: '',
            },
          },
        }}
        edges={conditionEdges}
        onApply={onApply}
      />
    )

    fireEvent.click(screen.getAllByRole('combobox')[1]!)
    fireEvent.click(screen.getByRole('option', { name: '安全公式' }))

    const formulaInputs = screen.getAllByPlaceholderText(
      '请输入受控公式表达式，例如：ifElse(amount > 10000, "A", "B")'
    )
    fireEvent.change(formulaInputs[formulaInputs.length - 1]!, {
      target: { value: 'ifElse(amount > 20000, "A", "B")' },
    })

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'condition_1',
      expect.objectContaining({
        config: expect.objectContaining({
          defaultEdgeId: 'edge_yes',
          expressionMode: 'FORMULA',
        }),
      }),
      [
        {
          edgeId: 'edge_yes',
          label: '正常审批',
          condition: undefined,
        },
        {
          edgeId: 'edge_formula',
          label: '公式审批',
          condition: {
            type: 'FORMULA',
            expression: 'ifElse(amount > 20000, "A", "B")',
            formulaExpression: 'ifElse(amount > 20000, "A", "B")',
          },
        },
      ]
    )
  })
})
