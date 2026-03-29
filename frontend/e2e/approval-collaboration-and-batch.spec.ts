import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test'

const APP_BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const API_BASE_URL = process.env.E2E_API_BASE_URL || 'http://127.0.0.1:8080/api/v1'
const ACCESS_TOKEN_COOKIE = 'west_flow_ai_access_token'

type LoginEnvelope = {
  data: {
    accessToken: string
  }
}

type PublishEnvelope = {
  data: {
    processDefinitionId: string
    processKey: string
  }
}

type StartProcessEnvelope = {
  data: {
    instanceId: string
    activeTasks: Array<{
      taskId: string
      nodeId: string
      nodeName: string
    }>
  }
}

type LeaveLaunchEnvelope = {
  data: {
    billId: string
    billNo: string
    processInstanceId: string
    firstActiveTask: {
      taskId: string
      nodeId: string
      nodeName: string
      status: string
    } | null
  }
}

type ApprovalSheetDetailEnvelope = {
  data: {
    instanceId?: string
    instanceStatus?: string | null
    taskId?: string | null
    nodeName?: string | null
    activeTaskIds?: string[]
    currentTaskId?: string | null
    currentNodeName?: string | null
    taskSemanticMode?: string | null
    instanceEvents?: Array<{
      eventType: string
      eventName?: string | null
    }>
    taskTrace?: Array<{
      taskId: string
      taskSemanticMode?: string | null
      nodeName: string
      status: string
    }>
  }
}

async function apiLogin(request: APIRequestContext, username: string, password: string) {
  const response = await request.post(`${API_BASE_URL}/auth/login`, {
    data: { username, password },
  })
  expect(response.ok()).toBeTruthy()
  const payload = (await response.json()) as LoginEnvelope
  return payload.data.accessToken
}

async function openAuthenticatedPage(
  browser: Browser,
  request: APIRequestContext,
  username: string,
  password: string
) {
  const accessToken = await apiLogin(request, username, password)
  const context = await browser.newContext()
  await context.addCookies([
    {
      name: ACCESS_TOKEN_COOKIE,
      value: accessToken,
      url: APP_BASE_URL,
    },
  ])
  const page = await context.newPage()
  return { accessToken, context, page }
}

async function publishProcessDefinition(
  request: APIRequestContext,
  accessToken: string,
  payload: Record<string, unknown>
) {
  const response = await request.post(`${API_BASE_URL}/process-definitions/publish`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    data: payload,
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as PublishEnvelope
}

async function startRuntimeProcess(
  request: APIRequestContext,
  accessToken: string,
  processKey: string,
  businessKey: string
) {
  const response = await request.post(`${API_BASE_URL}/process-runtime/start`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      processKey,
      businessKey,
      businessType: 'OA_COMMON',
      formData: {
        billNo: `BILL-${businessKey}`,
      },
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as StartProcessEnvelope
}

async function createLeave(
  request: APIRequestContext,
  accessToken: string,
  reason: string
) {
  const response = await request.post(`${API_BASE_URL}/oa/leaves`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    data: {
      leaveType: 'PERSONAL',
      days: 2,
      urgent: false,
      reason,
      managerUserId: 'usr_002',
    },
  })

  expect(response.ok()).toBeTruthy()
  return (await response.json()) as LeaveLaunchEnvelope
}

async function fetchApprovalSheetDetailByBusiness(
  request: APIRequestContext,
  accessToken: string,
  businessType: string,
  businessId: string
) {
  const response = await request.get(`${API_BASE_URL}/process-runtime/approval-sheets/by-business`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    params: {
      businessType,
      businessId,
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as ApprovalSheetDetailEnvelope
}

async function openTodoDetail(page: Page, taskId: string) {
  await page.goto(`/workbench/todos/${taskId}`)
  await expect(page.getByText('统一审批单详情页')).toBeVisible()
}

async function confirmCurrentSemanticTask(params: {
  browser: Browser
  request: APIRequestContext
  applicantToken: string
  username: string
  businessKey: string
  semanticMode: string
  readButtonLabel: string
}) {
  const { browser, request, applicantToken, username, businessKey, semanticMode, readButtonLabel } =
    params
  const detail = await fetchApprovalSheetDetailByBusiness(
    request,
    applicantToken,
    'OA_COMMON',
    businessKey
  )

  expect(detail.data.taskSemanticMode).toBe(semanticMode)
  expect(detail.data.taskId).toBeTruthy()

  const session = await openAuthenticatedPage(browser, request, username, '123456')
  try {
    await openTodoDetail(session.page, detail.data.taskId!)
    const readButton = session.page.getByRole('button', { name: readButtonLabel })
    await expect(readButton).toBeVisible()
    await readButton.click()
    await expect(readButton).toHaveCount(0)
  } finally {
    await session.context.close()
  }
}

async function selectTodoRowByTaskId(page: Page, taskId: string) {
  const checkbox = page.getByLabel(`选择${taskId}`)
  await expect(checkbox).toBeVisible()
  await checkbox.click()
}

async function getTodoRowByTaskId(page: Page, taskId: string) {
  const checkbox = page.getByLabel(`选择${taskId}`)
  await expect(checkbox).toBeVisible()
  return checkbox.locator('xpath=ancestor::tr[1]')
}

function buildCollaborationProcessPayload(processKey: string, processName: string) {
  return {
    dslVersion: '1.0.0',
    processKey,
    processName,
    category: 'OA',
    processFormKey: 'oa_collaboration_form',
    processFormVersion: '1.0.0',
    settings: {
      allowWithdraw: true,
    },
    nodes: [
      {
        id: 'start_1',
        type: 'start',
        name: '开始',
        position: { x: 100, y: 100 },
        config: { initiatorEditable: true },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'supervise_1',
        type: 'supervise',
        name: '督办',
        position: { x: 320, y: 100 },
        config: {
          targets: { mode: 'USER', userIds: ['usr_002'], roleCodes: [], departmentRef: '' },
          readRequired: false,
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'meeting_1',
        type: 'meeting',
        name: '会办',
        position: { x: 540, y: 100 },
        config: {
          targets: { mode: 'USER', userIds: ['usr_003'], roleCodes: [], departmentRef: '' },
          readRequired: true,
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'read_1',
        type: 'read',
        name: '阅办',
        position: { x: 760, y: 100 },
        config: {
          targets: { mode: 'USER', userIds: ['usr_006'], roleCodes: [], departmentRef: '' },
          readRequired: true,
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'circulate_1',
        type: 'circulate',
        name: '传阅',
        position: { x: 980, y: 100 },
        config: {
          targets: { mode: 'USER', userIds: ['usr_001'], roleCodes: [], departmentRef: '' },
          readRequired: false,
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'end_1',
        type: 'end',
        name: '结束',
        position: { x: 1200, y: 100 },
        config: {},
        ui: { width: 240, height: 88 },
      },
    ],
    edges: [
      { id: 'edge_1', source: 'start_1', target: 'supervise_1', priority: 10, label: '提交' },
      { id: 'edge_2', source: 'supervise_1', target: 'meeting_1', priority: 10, label: '下一步' },
      { id: 'edge_3', source: 'meeting_1', target: 'read_1', priority: 10, label: '下一步' },
      { id: 'edge_4', source: 'read_1', target: 'circulate_1', priority: 10, label: '下一步' },
      { id: 'edge_5', source: 'circulate_1', target: 'end_1', priority: 10, label: '结束' },
    ],
  }
}

test.describe.serial('approval collaboration and batch actions', () => {
  test('supports semantic collaboration read flow through todo detail pages', async ({
    browser,
    request,
  }) => {
    const seed = Date.now()
    const processKey = `oa_collaboration_modes_e2e_${seed}`
    const processName = `协同审批模式前端E2E-${seed}`
    const businessKey = `collaboration_e2e_${seed}`

    const adminToken = await apiLogin(request, 'admin', 'admin123')
    const applicantToken = await apiLogin(request, 'zhangsan', '123456')

    await publishProcessDefinition(
      request,
      adminToken,
      buildCollaborationProcessPayload(processKey, processName)
    )
    await startRuntimeProcess(request, applicantToken, processKey, businessKey)

    await confirmCurrentSemanticTask({
      browser,
      request,
      applicantToken,
      username: 'lisi',
      businessKey,
      semanticMode: 'supervise',
      readButtonLabel: '督办已阅',
    })

    await confirmCurrentSemanticTask({
      browser,
      request,
      applicantToken,
      username: 'wangwu',
      businessKey,
      semanticMode: 'meeting',
      readButtonLabel: '会办已阅',
    })

    await confirmCurrentSemanticTask({
      browser,
      request,
      applicantToken,
      username: 'zhouba',
      businessKey,
      semanticMode: 'read',
      readButtonLabel: '阅办已阅',
    })

    await confirmCurrentSemanticTask({
      browser,
      request,
      applicantToken,
      username: 'zhangsan',
      businessKey,
      semanticMode: 'circulate',
      readButtonLabel: '传阅已阅',
    })

    const detail = await fetchApprovalSheetDetailByBusiness(
      request,
      applicantToken,
      'OA_COMMON',
      businessKey
    )
    expect(detail.data.instanceStatus).toBe('COMPLETED')
    expect(detail.data.currentTaskId ?? null).toBeNull()

    const eventText = JSON.stringify(detail.data.instanceEvents ?? [])
    expect(eventText).toContain('TASK_SUPERVISE_READ')
    expect(eventText).toContain('TASK_MEETING_READ')
    expect(eventText).toContain('TASK_READ_CONFIRM')
    expect(eventText).toContain('TASK_CIRCULATE_READ')
  })

  test('supports batch claim and batch approve from todo list', async ({ browser, request }) => {
    const seed = Date.now()
    const firstReason = `E2E批量动作-1-${seed}`
    const secondReason = `E2E批量动作-2-${seed}`
    const adminToken = await apiLogin(request, 'admin', 'admin123')

    const firstLeave = await createLeave(request, adminToken, firstReason)
    const secondLeave = await createLeave(request, adminToken, secondReason)
    const managerSession = await openAuthenticatedPage(browser, request, 'zhangsan', '123456')

    try {
      await managerSession.page.goto('/workbench/todos/list?pageSize=100')
      const firstTaskId = firstLeave.data.firstActiveTask!.taskId
      const secondTaskId = secondLeave.data.firstActiveTask!.taskId
      await selectTodoRowByTaskId(managerSession.page, firstTaskId)
      await selectTodoRowByTaskId(managerSession.page, secondTaskId)

      await managerSession.page.getByRole('button', { name: '批量认领' }).click()
      await managerSession.page.getByRole('button', { name: '确认认领' }).click()
      await expect(await getTodoRowByTaskId(managerSession.page, firstTaskId)).toContainText('待处理')
      await expect(await getTodoRowByTaskId(managerSession.page, secondTaskId)).toContainText('待处理')

      await managerSession.page.goto('/workbench/todos/list?pageSize=100')
      await selectTodoRowByTaskId(managerSession.page, firstTaskId)
      await selectTodoRowByTaskId(managerSession.page, secondTaskId)

      await managerSession.page.getByRole('button', { name: '批量同意' }).click()
      await managerSession.page.getByRole('button', { name: '确认同意' }).click()

      await expect
        .poll(
          async () => {
            const firstDetail = await fetchApprovalSheetDetailByBusiness(
              request,
              adminToken,
              'OA_LEAVE',
              firstLeave.data.billId
            )
            const secondDetail = await fetchApprovalSheetDetailByBusiness(
              request,
              adminToken,
              'OA_LEAVE',
              secondLeave.data.billId
            )
            const firstPendingNode = firstDetail.data.taskTrace?.find(
              (item) => item.status === 'PENDING'
            )?.nodeName
            const secondPendingNode = secondDetail.data.taskTrace?.find(
              (item) => item.status === 'PENDING'
            )?.nodeName
            return { firstPendingNode, secondPendingNode }
          },
          { timeout: 20_000 }
        )
        .toEqual({ firstPendingNode: '负责人确认', secondPendingNode: '负责人确认' })
    } finally {
      await managerSession.context.close()
    }
  })
})
