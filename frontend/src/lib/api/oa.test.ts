import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}))

vi.mock('@/lib/api/client', () => ({
  apiClient: {
    get: getMock,
    post: postMock,
  },
  unwrapResponse: <T>(response: { data: { data: T } }) => response.data.data,
}))

function okResponse<T>(data: T) {
  return {
    data: {
      code: 'OK',
      message: 'success',
      data,
      requestId: 'req_001',
    },
  }
}

describe('oa api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('posts leave expense and common bill payloads to dedicated endpoints', async () => {
    postMock
      .mockResolvedValueOnce(
        okResponse({
          billId: 'bill_leave_001',
          billNo: 'OA-L-001',
          processInstanceId: 'pi_leave_001',
          activeTasks: [{ taskId: 'task_leave_001' }],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'bill_expense_001',
          billNo: 'OA-E-001',
          processInstanceId: 'pi_expense_001',
          activeTasks: [{ taskId: 'task_expense_001' }],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'bill_common_001',
          billNo: 'OA-C-001',
          processInstanceId: 'pi_common_001',
          activeTasks: [{ taskId: 'task_common_001' }],
        })
      )

    const {
      createOACommonRequestBill,
      createOAExpenseBill,
      createOALeaveBill,
    } = await import('./oa')

    await expect(
      createOALeaveBill({
        days: 3,
        reason: '外出处理事务',
      })
    ).resolves.toMatchObject({
      billNo: 'OA-L-001',
    })
    await expect(
      createOAExpenseBill({
        amount: 128.5,
        reason: '客户接待',
      })
    ).resolves.toMatchObject({
      billNo: 'OA-E-001',
    })
    await expect(
      createOACommonRequestBill({
        title: '资产借用',
        content: '申请借用一台演示电脑',
      })
    ).resolves.toMatchObject({
      billNo: 'OA-C-001',
    })

    expect(postMock).toHaveBeenNthCalledWith(1, '/oa/leaves', {
      days: 3,
      reason: '外出处理事务',
    })
    expect(postMock).toHaveBeenNthCalledWith(2, '/oa/expenses', {
      amount: 128.5,
      reason: '客户接待',
    })
    expect(postMock).toHaveBeenNthCalledWith(3, '/oa/common-requests', {
      title: '资产借用',
      content: '申请借用一台演示电脑',
    })
  })

  it('loads OA detail records from dedicated endpoints', async () => {
    getMock
      .mockResolvedValueOnce(
        okResponse({
          billId: 'bill_leave_001',
          billNo: 'OA-L-001',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'bill_expense_001',
          billNo: 'OA-E-001',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'bill_common_001',
          billNo: 'OA-C-001',
        })
      )

    const {
      getOACommonRequestBillDetail,
      getOAExpenseBillDetail,
      getOALeaveBillDetail,
    } = await import('./oa')

    await expect(getOALeaveBillDetail('bill_leave_001')).resolves.toMatchObject({
      billNo: 'OA-L-001',
    })
    await expect(
      getOAExpenseBillDetail('bill_expense_001')
    ).resolves.toMatchObject({
      billNo: 'OA-E-001',
    })
    await expect(
      getOACommonRequestBillDetail('bill_common_001')
    ).resolves.toMatchObject({
      billNo: 'OA-C-001',
    })

    expect(getMock).toHaveBeenNthCalledWith(1, '/oa/leaves/bill_leave_001')
    expect(getMock).toHaveBeenNthCalledWith(2, '/oa/expenses/bill_expense_001')
    expect(getMock).toHaveBeenNthCalledWith(
      3,
      '/oa/common-requests/bill_common_001'
    )
  })
})
