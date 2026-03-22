import { describe, expect, it } from 'vitest'
import { resolveApprovalSheetResultLabel } from './approval-sheet-helpers'

describe('approval-sheet helpers', () => {
  it('maps advanced runtime actions to human readable labels', () => {
    expect(
      resolveApprovalSheetResultLabel({
        action: 'REJECT_ROUTE',
        status: 'REJECTED',
      } as never)
    ).toBe('驳回到上一步人工节点')

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
  })
})
