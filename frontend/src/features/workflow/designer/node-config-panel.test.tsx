import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render as rtlRender, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NodeConfigPanel } from './node-config-panel'
import { workflowNodeTemplates } from './palette'
import {
  type WorkflowApproverNodeConfig,
  type WorkflowEdge,
  type WorkflowNode,
  type WorkflowReminderChannel,
} from './types'

vi.mock('@monaco-editor/react', () => ({
  default: ({
    value,
    onChange,
  }: {
    value?: string
    onChange?: (value: string) => void
  }) => (
    <textarea
      data-testid='workflow-rule-editor'
      value={value ?? ''}
      onChange={(event) => onChange?.(event.target.value)}
    />
  ),
}))

vi.mock('@/lib/api/workflow', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api/workflow')>('@/lib/api/workflow')
  return {
    ...actual,
    getProcessRuleMetadata: vi.fn(async () => ({
      variables: [
        {
          key: 'days',
          label: '请假天数',
          scope: 'FORM',
          valueType: 'number',
          description: '流程表单字段：请假天数',
        },
      ],
      functions: [
        {
          name: 'ifElse',
          label: '条件函数',
          signature: 'ifElse(condition, whenTrue, whenFalse)',
          description: '按条件返回不同结果',
          category: '条件',
          snippet: 'ifElse(condition, whenTrue, whenFalse)',
        },
      ],
      snippets: [
        {
          key: 'and',
          label: '并且',
          description: '组合两个条件',
          template: '(leftCondition) && (rightCondition)',
        },
      ],
    })),
    validateProcessRule: vi.fn(async ({ expression }: { expression: string }) => ({
      valid: Boolean(expression?.trim()),
      normalizedExpression: expression?.trim() || null,
      summary: expression?.trim() ? '规则校验通过' : null,
      errors: expression?.trim()
        ? []
        : [{ message: '请输入规则表达式', line: 1, column: 1 }],
    })),
  }
})

function renderWithQueryClient(node: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })

  return rtlRender(
    <QueryClientProvider client={queryClient}>
      {node}
    </QueryClientProvider>
  )
}

const render = renderWithQueryClient

const defaultEscalationPolicy = {
  enabled: false,
  afterMinutes: null,
  targetMode: 'ROLE' as const,
  targetUserIds: [] as string[],
  targetRoleCodes: [] as string[],
  channels: ['IN_APP'] as WorkflowReminderChannel[],
}

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

function buildCollaborationNode(kind: 'cc' | 'supervise' | 'meeting' | 'read' | 'circulate'): WorkflowNode {
  return {
    id: `${kind}_1`,
    type: 'workflow',
    position: { x: 100, y: 100 },
    data: {
      kind,
      label: '协同',
      description: '协同节点',
      tone: 'neutral',
      config: {
        targets: {
          mode: 'USER',
          userIds: ['usr_002'],
          roleCodes: [],
          departmentRef: '',
        },
        readRequired: kind === 'read',
      } as never,
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
        escalationPolicy: defaultEscalationPolicy,
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

function buildRoleFallbackDynamicBuilderNode(): WorkflowNode {
  const node = buildDynamicBuilderNode()
  node.data.config = {
    ...(node.data.config as Record<string, unknown>),
    targets: {
      mode: 'ROLE',
      userIds: [],
      roleCodes: ['role_manager'],
      departmentRef: '',
      formFieldKey: '',
      formulaExpression: '',
    },
  } as never
  return node
}

function buildFallbackSubprocessDynamicBuilderNode(): WorkflowNode {
  const node = buildDynamicBuilderNode()
  node.data.config = {
    ...(node.data.config as Record<string, unknown>),
    buildMode: 'SUBPROCESS_CALLS',
    calledProcessKey: 'oa_sub_review',
    calledVersionPolicy: 'FIXED_VERSION',
    calledVersion: 3,
  } as never
  return node
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

function buildConditionNode(config: Record<string, unknown> = {}): WorkflowNode {
  return {
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
    expect(workflowNodeTemplates.some((template) => template.kind === 'cc')).toBe(true)
    expect(workflowNodeTemplates.some((template) => template.kind === 'supervise')).toBe(true)
    expect(workflowNodeTemplates.some((template) => template.kind === 'meeting')).toBe(true)
    expect(workflowNodeTemplates.some((template) => template.kind === 'read')).toBe(true)
    expect(workflowNodeTemplates.some((template) => template.kind === 'circulate')).toBe(true)
  })

  it('renders collaboration node sections with explicit labels', () => {
    render(
      <NodeConfigPanel
        node={buildCollaborationNode('supervise')}
        edges={edges}
        onApply={vi.fn()}
      />
    )

    expect(screen.getByText('督办')).toBeInTheDocument()
    expect(screen.getByText('协同对象')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '选择' }))
    expect(
      screen.getByText('支持多选，确认后会同步回节点配置。')
    ).toBeInTheDocument()
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
    expect(screen.getByText('SLA 升级')).toBeInTheDocument()
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

  it('submits approver escalation policy back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(<NodeConfigPanel node={buildApproverNode()} edges={edges} onApply={onApply} />)

    fireEvent.click(screen.getByRole('switch', { name: 'SLA 升级' }))
    fireEvent.change(screen.getByPlaceholderText('60'), {
      target: { value: '90' },
    })
    const selectButtons = screen.getAllByRole('button', { name: '选择' })
    fireEvent.click(selectButtons[selectButtons.length - 1]!)
    await waitFor(() => expect(screen.getByText('部门负责人')).toBeInTheDocument())
    fireEvent.click(screen.getByText('部门负责人'))
    fireEvent.click(screen.getByRole('button', { name: '确认' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenLastCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          escalationPolicy: expect.objectContaining({
            enabled: true,
            afterMinutes: 90,
            targetMode: 'ROLE',
            targetRoleCodes: expect.arrayContaining(['role_manager']),
            channels: expect.arrayContaining(['IN_APP']),
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

    fireEvent.change(screen.getByDisplayValue('60'), {
      target: { value: '61' },
    })

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'approve_1',
      expect.objectContaining({
        config: expect.objectContaining({
          approvalMode: 'VOTE',
          voteRule: expect.objectContaining({
            thresholdPercent: 61,
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

  it('hides node form and field binding controls for approver nodes', () => {
    render(
      <NodeConfigPanel
        node={buildApproverNode('USER')}
        edges={edges}
        onApply={vi.fn()}
        processFormFields={[
          { fieldKey: 'leaveDays', label: '请假天数', valueType: 'number' },
          { fieldKey: 'leaveType', label: '请假类型', valueType: 'string' },
        ]}
      />
    )

    expect(screen.queryByText('节点覆盖表单')).not.toBeInTheDocument()
    expect(screen.queryByText('字段绑定')).not.toBeInTheDocument()
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

  it('hydrates and submits model driven dynamic builder fields back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel node={buildModelDrivenDynamicBuilderNode()} edges={edges} onApply={onApply} />
    )

    expect(screen.getByDisplayValue('leave_auto_scene')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('leave_overtime_approval'), {
      target: { value: 'leave_model_scene' },
    })
    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'dynamic_1',
      expect.objectContaining({
        config: expect.objectContaining({
          sourceMode: 'MODEL_DRIVEN',
          sceneCode: 'leave_model_scene',
          executionStrategy: 'TEMPLATE_FIRST',
          fallbackStrategy: 'KEEP_CURRENT',
        }),
      }),
      undefined
    )
  })

  it('hydrates and submits dynamic builder fallback role targets back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel node={buildRoleFallbackDynamicBuilderNode()} edges={edges} onApply={onApply} />
    )

    await waitFor(() => {
      expect(screen.getByText('部门负责人')).toBeInTheDocument()
    })
    fireEvent.change(screen.getByPlaceholderText('leave_overtime_approval'), {
      target: { value: 'role_fallback_scene' },
    })
    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'dynamic_1',
      expect.objectContaining({
        config: expect.objectContaining({
          sceneCode: 'role_fallback_scene',
          targets: expect.objectContaining({
            mode: 'ROLE',
            roleCodes: ['role_manager'],
          }),
        }),
      }),
      undefined
    )
  })

  it('hydrates and submits dynamic builder fallback subprocess fields back to the canvas patch', async () => {
    const onApply = vi.fn()

    render(
      <NodeConfigPanel
        node={buildFallbackSubprocessDynamicBuilderNode()}
        edges={edges}
        onApply={onApply}
      />
    )

    expect(screen.getByDisplayValue('oa_sub_review')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('oa_sub_review'), {
      target: { value: 'oa_sub_review_leaf' },
    })
    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'dynamic_1',
      expect.objectContaining({
        config: expect.objectContaining({
          buildMode: 'SUBPROCESS_CALLS',
          calledProcessKey: 'oa_sub_review_leaf',
          calledVersionPolicy: 'FIXED_VERSION',
          calledVersion: 3,
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

  it('keeps inclusive split node panel focused on gateway-level settings', () => {
    render(
      <NodeConfigPanel
        node={buildInclusiveNode('SPLIT')}
        edges={inclusiveSplitEdges}
        onApply={vi.fn()}
        onAddBranch={vi.fn()}
      />
    )

    expect(screen.getByText('包容网关')).toBeInTheDocument()
    expect(screen.getByText('分支汇聚策略')).toBeInTheDocument()
    expect(
      screen.getByText('当前共有 2 条包容分支。请直接点击画布中的分支连线编辑条件和优先级；选中网关卡片底部按钮可继续新增条件分支。')
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '新增条件分支' })).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '分支名称' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('分支优先级')).not.toBeInTheDocument()
  })

  it('submits inclusive split branch conditions from edge config panel', async () => {
    const onApplyEdge = vi.fn()

    render(
      <NodeConfigPanel
        node={null}
        edge={inclusiveSplitEdges[0]}
        nodes={[
          buildInclusiveNode('SPLIT', {
            defaultBranchId: 'edge_urgent',
          }),
        ]}
        edges={inclusiveSplitEdges}
        onApply={vi.fn()}
        onApplyEdge={onApplyEdge}
      />
    )

    expect(screen.getByText('分支配置')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('textbox', { name: '分支名称' }), {
      target: { value: '金额超标并会签' },
    })
    fireEvent.change(screen.getByLabelText('分支优先级'), {
      target: { value: '3' },
    })
    fireEvent.click(screen.getByRole('button', { name: '编辑规则' }))
    fireEvent.change(screen.getByTestId('workflow-rule-editor'), {
      target: { value: 'amount >= 20000' },
    })
    fireEvent.click(screen.getByRole('button', { name: '应用规则' }))

    await waitFor(() =>
      expect(onApplyEdge).toHaveBeenCalledWith('edge_yes', {
        condition: {
          type: 'FORMULA',
          expression: 'amount >= 20000',
          formulaExpression: 'amount >= 20000',
        },
      })
    )
    await waitFor(() =>
      expect(onApplyEdge).toHaveBeenCalledWith('edge_yes', {
        label: '金额超标并会签',
        priority: 3,
      })
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
    expect(screen.getAllByLabelText('必选分支数')[0]).toHaveValue('2')

    fireEvent.change(screen.getAllByLabelText('必选分支数')[0]!, { target: { value: '1' } })
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
          priority: 1,
        }),
        expect.objectContaining({
          edgeId: 'edge_urgent',
          priority: 2,
        }),
      ]
    )
  })

  it('shows default branch guidance when editing the default condition edge', () => {
    render(
      <NodeConfigPanel
        node={null}
        edge={conditionEdges[0]}
        nodes={[buildConditionNode({ defaultEdgeId: 'edge_yes' })]}
        edges={conditionEdges}
        onApply={vi.fn()}
        onApplyEdge={vi.fn()}
      />
    )

    expect(screen.getByText('分支配置')).toBeInTheDocument()
    expect(
      screen.getByText('默认分支不配置规则，当前面所有分支都未命中时会自动进入。')
    ).toBeInTheDocument()
    expect(screen.queryByText('编辑规则')).not.toBeInTheDocument()
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

  it('edits condition edge rules through the unified rule dialog', async () => {
    const onApplyEdge = vi.fn()

    render(
      <NodeConfigPanel
        node={null}
        edge={conditionEdges[1]}
        nodes={[buildConditionNode()]}
        edges={conditionEdges}
        onApply={vi.fn()}
        onApplyEdge={onApplyEdge}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: '编辑规则' }))
    fireEvent.change(screen.getByTestId('workflow-rule-editor'), {
      target: { value: 'ifElse(amount > 20000, "A", "B")' },
    })
    fireEvent.click(screen.getByRole('button', { name: '应用规则' }))

    await waitFor(() =>
      expect(onApplyEdge).toHaveBeenCalledWith('edge_formula', {
        condition: {
          type: 'FORMULA',
          expression: 'ifElse(amount > 20000, "A", "B")',
          formulaExpression: 'ifElse(amount > 20000, "A", "B")',
        },
      })
    )
  })

  it('re-hydrates condition type when switching between different branch edges', async () => {
    const branchGatewayNode = buildConditionNode({ defaultEdgeId: 'edge_fallback' })
    const initialRender = render(
      <NodeConfigPanel
        node={null}
        edge={conditionEdges[0]}
        nodes={[branchGatewayNode]}
        edges={conditionEdges}
        onApply={vi.fn()}
        onApplyEdge={vi.fn()}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: '编辑规则' }))
    expect(screen.getByTestId('workflow-rule-editor')).toHaveValue('amount > 10000')

    initialRender.unmount()
    render(
      <NodeConfigPanel
        node={null}
        edge={conditionEdges[1]}
        nodes={[branchGatewayNode]}
        edges={conditionEdges}
        onApply={vi.fn()}
        onApplyEdge={vi.fn()}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: '编辑规则' }))
    await waitFor(() =>
      expect(screen.getByTestId('workflow-rule-editor')).toHaveValue(
        'ifElse(amount > 10000, "A", "B")'
      )
    )
  })
})
