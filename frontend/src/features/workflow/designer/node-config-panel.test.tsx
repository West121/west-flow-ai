import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NodeConfigPanel } from './node-config-panel'
import { workflowNodeTemplates } from './palette'
import { type WorkflowEdge, type WorkflowNode } from './types'

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

function buildApproverNode(): WorkflowNode {
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
          mode: 'USER',
          userIds: ['usr_002', 'usr_003'],
          roleCodes: [],
          departmentRef: '',
          formFieldKey: '',
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
        businessBindingMode: 'OVERRIDE',
        terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
        childFinishPolicy: 'TERMINATE_PARENT',
        inputMappingsJson: '[{"source":"billNo","target":"sourceBillNo"}]',
        outputMappingsJson: '[{"source":"approvedResult","target":"purchaseResult"}]',
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
        ruleExpression: '',
        manualTemplateCode: 'append_purchase_review',
        appendPolicy: 'SERIAL_AFTER_CURRENT',
        maxGeneratedCount: 1,
        terminatePolicy: 'TERMINATE_GENERATED_ONLY',
      } as never,
    },
  }
}

function buildInclusiveNode(): WorkflowNode {
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
        gatewayDirection: 'JOIN',
      } as never,
    },
  }
}

const edges: WorkflowEdge[] = []

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

    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'subprocess_1',
      expect.objectContaining({
        config: expect.objectContaining({
          calledProcessKey: 'oa_common_subflow',
          calledVersionPolicy: 'FIXED_VERSION',
          calledVersion: 5,
          businessBindingMode: 'OVERRIDE',
          terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
          childFinishPolicy: 'TERMINATE_PARENT',
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
    expect(screen.getByRole('spinbutton', { name: '最大生成数量' })).toHaveValue(1)

    fireEvent.change(screen.getByRole('spinbutton', { name: '最大生成数量' }), {
      target: { value: '2' },
    })
    fireEvent.change(screen.getByRole('textbox', { name: '模板编码' }), {
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

    render(<NodeConfigPanel node={buildInclusiveNode()} edges={edges} onApply={onApply} />)

    expect(screen.getByText('包容分支节点')).toBeInTheDocument()
    expect(screen.getAllByText('汇聚').length).toBeGreaterThan(0)

    fireEvent.click(screen.getByRole('combobox'))
    fireEvent.click(screen.getAllByText('分支').at(-1)!)
    fireEvent.click(screen.getByRole('button', { name: '应用到画布' }))

    await waitFor(() => expect(onApply).toHaveBeenCalled())
    expect(onApply).toHaveBeenCalledWith(
      'inclusive_1',
      expect.objectContaining({
        config: expect.objectContaining({
          gatewayDirection: 'SPLIT',
        }),
      }),
      undefined
    )
  })
})
