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

describe('plm api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('posts ECR ECO and material change payloads to dedicated endpoints', async () => {
    postMock
      .mockResolvedValueOnce(
        okResponse({
          billId: 'ecr_001',
          billNo: 'PLM-ECR-001',
          processInstanceId: 'pi_ecr_001',
          activeTasks: [{ taskId: 'task_ecr_001' }],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'eco_001',
          billNo: 'PLM-ECO-001',
          processInstanceId: 'pi_eco_001',
          activeTasks: [{ taskId: 'task_eco_001' }],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'material_001',
          billNo: 'PLM-MATERIAL-001',
          processInstanceId: 'pi_material_001',
          activeTasks: [{ taskId: 'task_material_001' }],
        })
      )

    const {
      createPLMECOExecution,
      createPLMECRRequest,
      createPLMMaterialChangeRequest,
    } = await import('./plm')

    await expect(
      createPLMECRRequest({
        changeTitle: '结构件变更',
        changeReason: '供应替代',
        impactLevel: 'HIGH',
      })
    ).resolves.toMatchObject({
      billNo: 'PLM-ECR-001',
    })
    await expect(
      createPLMECOExecution({
        changeTitle: 'ECO 下发',
        executionPlan: '通知工厂执行',
        owner: '研发部',
      })
    ).resolves.toMatchObject({
      billNo: 'PLM-ECO-001',
    })
    await expect(
      createPLMMaterialChangeRequest({
        materialCode: 'MAT-001',
        materialName: '主板总成',
        changeReason: '替换物料编码',
      })
    ).resolves.toMatchObject({
      billNo: 'PLM-MATERIAL-001',
    })

    expect(postMock).toHaveBeenNthCalledWith(1, '/plm/ecrs', {
      changeTitle: '结构件变更',
      changeReason: '供应替代',
      impactLevel: 'HIGH',
    })
    expect(postMock).toHaveBeenNthCalledWith(2, '/plm/ecos', {
      changeTitle: 'ECO 下发',
      executionPlan: '通知工厂执行',
      owner: '研发部',
    })
    expect(postMock).toHaveBeenNthCalledWith(3, '/plm/material-master-changes', {
      materialCode: 'MAT-001',
      materialName: '主板总成',
      changeReason: '替换物料编码',
    })
  })

  it('loads PLM detail records from dedicated endpoints', async () => {
    getMock
      .mockResolvedValueOnce(
        okResponse({
          billId: 'ecr_001',
          billNo: 'PLM-ECR-001',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'eco_001',
          billNo: 'PLM-ECO-001',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          billId: 'material_001',
          billNo: 'PLM-MATERIAL-001',
        })
      )

    const {
      getPLMECOExecutionDetail,
      getPLMECRRequestDetail,
      getPLMMaterialChangeDetail,
    } = await import('./plm')

    await expect(getPLMECRRequestDetail('ecr_001')).resolves.toMatchObject({
      billNo: 'PLM-ECR-001',
    })
    await expect(
      getPLMECOExecutionDetail('eco_001')
    ).resolves.toMatchObject({
      billNo: 'PLM-ECO-001',
    })
    await expect(
      getPLMMaterialChangeDetail('material_001')
    ).resolves.toMatchObject({
      billNo: 'PLM-MATERIAL-001',
    })

    expect(getMock).toHaveBeenNthCalledWith(1, '/plm/ecrs/ecr_001')
    expect(getMock).toHaveBeenNthCalledWith(2, '/plm/ecos/eco_001')
    expect(getMock).toHaveBeenNthCalledWith(
      3,
      '/plm/material-master-changes/material_001'
    )
  })

  it('loads PLM approval sheet records from dedicated endpoint', async () => {
    getMock.mockResolvedValueOnce(
      okResponse({
        page: 1,
        pageSize: 20,
        total: 1,
        pages: 1,
        groups: [],
        records: [
          {
            instanceId: 'pi_ecr_001',
            processDefinitionId: 'pd_ecr_001',
            processKey: 'plm_ecr',
            processName: 'ECR 变更申请',
            businessId: 'ecr_001',
            businessType: 'PLM_ECR',
            billNo: 'PLM-ECR-001',
            businessTitle: '结构件变更',
            initiatorUserId: 'usr_001',
            currentNodeName: '部门负责人审批',
            currentTaskId: 'task_ecr_001',
            currentTaskStatus: 'RUNNING',
            currentAssigneeUserId: 'usr_002',
            instanceStatus: 'RUNNING',
            latestAction: 'START',
            latestOperatorUserId: 'usr_001',
            createdAt: '2026-03-23T09:00:00+08:00',
            updatedAt: '2026-03-23T09:10:00+08:00',
            completedAt: null,
          },
        ],
      })
    )

    const { listPLMApprovalSheets } = await import('./plm')

    await expect(
      listPLMApprovalSheets({
        page: 1,
        pageSize: 20,
        keyword: '',
        filters: [],
        sorts: [],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
    })

    expect(getMock).toHaveBeenCalledWith('/plm/approval-sheets', {
      params: {
        page: 1,
        pageSize: 20,
        keyword: '',
        filters: [],
        sorts: [],
        groups: [],
      },
    })
  })
})
