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
        affectedProductCode: 'PRD-001',
        priorityLevel: 'HIGH',
      })
    ).resolves.toMatchObject({
      billNo: 'PLM-ECR-001',
    })
    await expect(
      createPLMECOExecution({
        executionTitle: 'ECO 下发',
        executionPlan: '通知工厂执行',
        effectiveDate: '2026-04-01',
        changeReason: '量产切换',
      })
    ).resolves.toMatchObject({
      billNo: 'PLM-ECO-001',
    })
    await expect(
      createPLMMaterialChangeRequest({
        materialCode: 'MAT-001',
        materialName: '主板总成',
        changeReason: '替换物料编码',
        changeType: 'ATTRIBUTE_UPDATE',
      })
    ).resolves.toMatchObject({
      billNo: 'PLM-MATERIAL-001',
    })

    expect(postMock).toHaveBeenNthCalledWith(1, '/plm/ecrs', {
      changeTitle: '结构件变更',
      changeReason: '供应替代',
      affectedProductCode: 'PRD-001',
      priorityLevel: 'HIGH',
    })
    expect(postMock).toHaveBeenNthCalledWith(2, '/plm/ecos', {
      executionTitle: 'ECO 下发',
      executionPlan: '通知工厂执行',
      effectiveDate: '2026-04-01',
      changeReason: '量产切换',
    })
    expect(postMock).toHaveBeenNthCalledWith(
      3,
      '/plm/material-master-changes',
      {
        materialCode: 'MAT-001',
        materialName: '主板总成',
        changeReason: '替换物料编码',
        changeType: 'ATTRIBUTE_UPDATE',
      }
    )
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
    await expect(getPLMECOExecutionDetail('eco_001')).resolves.toMatchObject({
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

  it('loads PLM business list pages from dedicated endpoints', async () => {
    postMock
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              billId: 'ecr_001',
              billNo: 'PLM-ECR-001',
              sceneCode: 'default',
              changeTitle: '结构件替换',
              affectedProductCode: 'PRD-001',
              priorityLevel: 'HIGH',
              processInstanceId: 'pi_ecr_001',
              status: 'RUNNING',
              creatorUserId: 'usr_001',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              billId: 'eco_001',
              billNo: 'PLM-ECO-001',
              sceneCode: 'default',
              executionTitle: 'ECO 执行',
              effectiveDate: '2026-04-01',
              changeReason: '量产切换',
              processInstanceId: 'pi_eco_001',
              status: 'RUNNING',
              creatorUserId: 'usr_001',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              billId: 'material_001',
              billNo: 'PLM-MATERIAL-001',
              sceneCode: 'default',
              materialCode: 'MAT-001',
              materialName: '主板总成',
              changeType: 'ATTRIBUTE_UPDATE',
              changeReason: '替换物料编码',
              processInstanceId: 'pi_material_001',
              status: 'RUNNING',
              creatorUserId: 'usr_001',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )

    const {
      listPLMECRRequests,
      listPLMECOExecutions,
      listPLMMaterialChangeRequests,
    } = await import('./plm')

    await expect(
      listPLMECRRequests({
        page: 1,
        pageSize: 20,
        keyword: '结构件',
        filters: [],
        sorts: [],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ billNo: 'PLM-ECR-001', changeTitle: '结构件替换' }],
    })

    await expect(
      listPLMECOExecutions({
        page: 1,
        pageSize: 20,
        keyword: 'ECO',
        filters: [],
        sorts: [],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ billNo: 'PLM-ECO-001', executionTitle: 'ECO 执行' }],
    })

    await expect(
      listPLMMaterialChangeRequests({
        page: 1,
        pageSize: 20,
        keyword: 'MAT-001',
        filters: [],
        sorts: [],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ billNo: 'PLM-MATERIAL-001', materialCode: 'MAT-001' }],
    })

    expect(postMock).toHaveBeenNthCalledWith(1, '/plm/ecrs/page', {
      page: 1,
      pageSize: 20,
      keyword: '结构件',
      filters: [],
      sorts: [],
      groups: [],
    })
    expect(postMock).toHaveBeenNthCalledWith(2, '/plm/ecos/page', {
      page: 1,
      pageSize: 20,
      keyword: 'ECO',
      filters: [],
      sorts: [],
      groups: [],
    })
    expect(postMock).toHaveBeenNthCalledWith(
      3,
      '/plm/material-master-changes/page',
      {
        page: 1,
        pageSize: 20,
        keyword: 'MAT-001',
        filters: [],
        sorts: [],
        groups: [],
      }
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

  it('loads PLM dashboard analytics from the summary endpoint', async () => {
    getMock.mockResolvedValueOnce(
      okResponse({
        totalCount: 12,
        draftCount: 2,
        runningCount: 5,
        completedCount: 4,
        rejectedCount: 1,
        cancelledCount: 0,
        implementingCount: 3,
        validatingCount: 1,
        closedCount: 4,
        summary: {
          totalCount: 12,
          draftCount: 2,
          runningCount: 5,
          completedCount: 4,
          rejectedCount: 1,
          cancelledCount: 0,
          implementingCount: 3,
          validatingCount: 1,
          closedCount: 4,
        },
        typeDistribution: [],
        stageDistribution: [],
        trendSeries: [],
        taskAlerts: [],
        ownerRanking: [],
        recentBills: [],
        byBusinessType: [],
      })
    )

    const { getPLMDashboardSummary } = await import('./plm')

    await expect(getPLMDashboardSummary()).resolves.toMatchObject({
      summary: {
        totalCount: 12,
        implementingCount: 3,
      },
      closedCount: 4,
    })

    expect(getMock).toHaveBeenCalledWith('/plm/dashboard/summary')
  })

  it('posts implementation task actions to the v4 task endpoint', async () => {
    postMock.mockResolvedValueOnce(
      okResponse({
        billId: 'eco_001',
        billNo: 'ECO-20260407-0001',
        status: 'IMPLEMENTING',
        processInstanceId: 'pi_eco_001',
      })
    )

    const { performPLMImplementationTaskAction } = await import('./plm')

    await expect(
      performPLMImplementationTaskAction(
        'PLM_ECO',
        'eco_001',
        'task_001',
        'COMPLETE'
      )
    ).resolves.toMatchObject({
      billNo: 'ECO-20260407-0001',
    })

    expect(postMock).toHaveBeenCalledWith(
      '/plm/ecos/eco_001/implementation-tasks/task_001/complete',
      {}
    )
  })

  it('maps connector task payloads into business summaries', async () => {
    getMock.mockResolvedValueOnce(
      okResponse([
        {
          id: 'job_001',
          businessType: 'PLM_ECR',
          billId: 'bill_001',
          connectorCode: 'PLM_ERP_SYNC',
          systemCode: 'ERP',
          systemName: 'ERP 主数据',
          directionCode: 'DOWNSTREAM',
          jobType: 'BILL_SUBMITTED',
          status: 'DISPATCHED',
          requestPayloadJson: JSON.stringify({
            summaryMessage: '结构件替换准备同步',
            dispatchProfile: {
              mode: 'stub',
              transport: 'http-simulated',
              endpointUrl: 'http://localhost:18081',
              endpointPath: '/api/plm/erp/sync',
            },
            bill: {
              billNo: 'ECR-001',
              title: '结构件替换',
            },
            affectedData: {
              objectLinkCount: 2,
              baselineCount: 1,
              documentCount: 3,
            },
            implementation: {
              taskCount: 4,
              blockedTaskCount: 1,
            },
            systemPayload: {
              intent: 'MASTER_DATA_SYNC',
              summary: '同步主数据对象、基线摘要与实施状态到 ERP。',
            },
          }),
          externalRef: 'EXT-001',
          retryCount: 0,
          nextRunAt: '2026-04-08T10:00:00',
          lastDispatchedAt: '2026-04-08T10:05:00',
          lastAckAt: null,
          lastError: null,
          createdBy: 'usr_001',
          sortOrder: 1,
          dispatchLogs: [
            {
              id: 'dlog_001',
              jobId: 'job_001',
              actionType: 'DISPATCHED',
              status: 'DISPATCHED',
              requestPayloadJson: JSON.stringify({
                summaryMessage: '结构件替换准备同步',
                bill: { billNo: 'ECR-001' },
                dispatchProfile: {
                  transport: 'http-simulated',
                  endpointUrl: 'http://localhost:18081',
                  endpointPath: '/api/plm/erp/sync',
                },
              }),
              responsePayloadJson: JSON.stringify({
                message:
                  'ERP 主数据 已派发到 http://localhost:18081/api/plm/erp/sync（stub）。',
                mode: 'stub',
                transport: 'http-simulated',
                endpointUrl: 'http://localhost:18081',
                endpointPath: '/api/plm/erp/sync',
                handlerKey: 'plm.connector.erp.stub',
              }),
              errorMessage: null,
              happenedAt: '2026-04-08T10:05:00',
              sortOrder: 2,
            },
          ],
          acknowledgements: [
            {
              id: 'ack_001',
              jobId: 'job_001',
              ackStatus: 'ACKED',
              ackCode: '200',
              idempotencyKey: 'ack-001',
              externalRef: 'ERP-REF-001',
              message: 'ERP 已确认收单',
              payloadJson: null,
              sourceSystem: 'ERP',
              happenedAt: '2026-04-08T10:06:00',
              sortOrder: 1,
            },
          ],
        },
      ])
    )

    const { listPLMConnectorTasks } = await import('./plm')
    const tasks = await listPLMConnectorTasks('PLM_ECR', 'bill_001')

    expect(tasks).toHaveLength(1)
    expect(tasks[0]?.payloadSummary).toBe(
      '同步主数据对象、基线摘要与实施状态到 ERP。'
    )
    expect(tasks[0]?.payloadDetails).toEqual([
      '单据：结构件替换',
      '编号：ECR-001',
      '受影响对象 2 个',
      '基线 1 组',
      '文档 3 份',
      '实施任务 4 项',
      '阻塞 1 项',
    ])
    expect(tasks[0]?.dispatchProfile).toMatchObject({
      transport: 'http-simulated',
      endpointUrl: 'http://localhost:18081',
      endpointPath: '/api/plm/erp/sync',
    })
    expect(tasks[0]?.dispatchLogs[0]).toMatchObject({
      requestSummary: '结构件替换准备同步',
      responseSummary:
        'ERP 主数据 已派发到 http://localhost:18081/api/plm/erp/sync（stub）。',
      requestDetails: [
        '单据：ECR-001',
        '传输：http-simulated',
        '目标：http://localhost:18081/api/plm/erp/sync',
      ],
    })
    expect(tasks[0]?.receipts[0]).toMatchObject({
      payloadSummary: 'ERP 已确认收单',
      payloadDetails: ['来源：ERP', '回执码：200', '幂等键：ack-001'],
    })
  })
})
