import { describe, expect, it } from 'vitest'
import {
  resolveApprovalSheetCollaborationEventTypeLabel,
  resolveApprovalSheetCollaborationNodeLabel,
  resolveApprovalSheetResultLabel,
} from './approval-sheet-helpers'

describe('approval-sheet helpers', () => {
  it('maps advanced runtime actions to human readable labels', () => {
    expect(
      resolveApprovalSheetResultLabel({
        action: 'REJECT_ROUTE',
        targetStrategy: 'ANY_USER_TASK',
        status: 'REJECTED',
      } as never)
    ).toBe('驳回到指定节点')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'REJECT_ROUTE',
        status: 'REJECTED',
      } as never)
    ).toBe('驳回到上一步人工节点')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'RETURN',
        targetStrategy: 'INITIATOR',
        status: 'RETURNED',
      } as never)
    ).toBe('退回发起人')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'READ',
        taskSemanticMode: 'supervise',
        status: 'COMPLETED',
      } as never)
    ).toBe('督办已阅')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'JUMP',
        status: 'JUMPED',
      } as never)
    ).toBe('跳转')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'TAKE_BACK',
        status: 'TAKEN_BACK',
      } as never)
    ).toBe('拿回')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'WAKE_UP',
        status: 'COMPLETED',
      } as never)
    ).toBe('唤醒')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'DELEGATE',
        status: 'DELEGATED',
      } as never)
    ).toBe('委派')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'PROXY',
        status: 'PENDING',
      } as never)
    ).toBe('代理代办')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'HANDOVER',
        status: 'HANDOVERED',
      } as never)
    ).toBe('离职转办')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'APPEND',
        status: 'COMPLETED',
      } as never)
    ).toBe('追加')

    expect(
      resolveApprovalSheetResultLabel({
        action: 'DYNAMIC_BUILD',
        status: 'COMPLETED',
      } as never)
    ).toBe('动态构建')
  })

  it('maps collaboration labels to explicit chinese names', () => {
    expect(resolveApprovalSheetCollaborationNodeLabel('cc')).toBe('抄送')
    expect(resolveApprovalSheetCollaborationNodeLabel('supervise')).toBe('督办')
    expect(resolveApprovalSheetCollaborationNodeLabel('meeting')).toBe('会办')
    expect(resolveApprovalSheetCollaborationNodeLabel('read')).toBe('阅办')
    expect(resolveApprovalSheetCollaborationNodeLabel('circulate')).toBe('传阅')

    expect(resolveApprovalSheetCollaborationEventTypeLabel('COMMENT')).toBe('批注')
    expect(resolveApprovalSheetCollaborationEventTypeLabel('SUPERVISE')).toBe('督办')
    expect(resolveApprovalSheetCollaborationEventTypeLabel('MEETING')).toBe('会办')
    expect(resolveApprovalSheetCollaborationEventTypeLabel('READ')).toBe('阅办')
    expect(resolveApprovalSheetCollaborationEventTypeLabel('CIRCULATE')).toBe('传阅')
  })
})
