import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NodeConfigPanel } from './node-config-panel'
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

const edges: WorkflowEdge[] = []

describe('workflow designer node config panel', () => {
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
})
