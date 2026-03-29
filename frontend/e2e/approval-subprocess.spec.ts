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

type LaunchEnvelope = {
  data: {
    instanceId: string
    activeTasks: Array<{
      taskId: string
      assigneeUserId?: string | null
      nodeName: string
      processInstanceId: string
    }>
  }
}

type RuntimeLinkEnvelope = {
  data: Array<{
    linkId: string
    parentInstanceId: string
    childInstanceId: string
    status: string
    parentResumeStrategy?: string | null
    joinMode?: string | null
  }>
}

type TaskPageEnvelope = {
  data: {
    records: Array<{
      taskId: string
      processName: string
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
  return (await response.json()) as LaunchEnvelope
}

async function fetchProcessLinks(
  request: APIRequestContext,
  accessToken: string,
  instanceId: string
) {
  const response = await request.get(`${API_BASE_URL}/process-runtime/instances/${instanceId}/links`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as RuntimeLinkEnvelope
}

async function fetchTodoTaskId(
  request: APIRequestContext,
  accessToken: string,
  keyword: string
) {
  const response = await request.post(`${API_BASE_URL}/process-runtime/tasks/page`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      page: 1,
      pageSize: 20,
      keyword,
      filters: [],
      sorts: [],
      groups: [],
    },
  })
  expect(response.ok()).toBeTruthy()
  const payload = (await response.json()) as TaskPageEnvelope
  const matchedTask = payload.data.records.find((item) => item.processName === keyword)
  expect(matchedTask?.taskId).toBeTruthy()
  return matchedTask!.taskId
}

function buildSubprocessChildPayload(processKey: string, processName: string) {
  return {
    dslVersion: '1.0.0',
    processKey,
    processName,
    category: 'OA',
    processFormKey: 'oa_sub_form',
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
        config: {
          initiatorEditable: true,
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'approve_1',
        type: 'approver',
        name: '子流程审批',
        position: { x: 320, y: 100 },
        config: {
          assignment: {
            mode: 'USER',
            userIds: ['usr_002'],
            roleCodes: [],
            departmentRef: '',
            formFieldKey: '',
          },
          approvalPolicy: {
            type: 'SEQUENTIAL',
          },
          operations: ['APPROVE', 'REJECT', 'RETURN'],
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'end_1',
        type: 'end',
        name: '结束',
        position: { x: 540, y: 100 },
        config: {},
        ui: { width: 240, height: 88 },
      },
    ],
    edges: [
      {
        id: 'edge_1',
        source: 'start_1',
        target: 'approve_1',
        priority: 10,
        label: '提交',
      },
      {
        id: 'edge_2',
        source: 'approve_1',
        target: 'end_1',
        priority: 10,
        label: '完成',
      },
    ],
  }
}

function buildParentWaitConfirmPayload(processKey: string, calledProcessKey: string) {
  return {
    dslVersion: '1.0.0',
    processKey,
    processName: '主流程带子流程前端E2E',
    category: 'OA',
    processFormKey: 'oa_parent_form',
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
        config: {
          initiatorEditable: true,
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'subprocess_1',
        type: 'subprocess',
        name: '子流程节点',
        position: { x: 320, y: 100 },
        config: {
          calledProcessKey,
          calledVersionPolicy: 'LATEST_PUBLISHED',
          businessBindingMode: 'INHERIT_PARENT',
          terminatePolicy: 'TERMINATE_SUBPROCESS_ONLY',
          childFinishPolicy: 'RETURN_TO_PARENT',
          callScope: 'CHILD_ONLY',
          joinMode: 'WAIT_PARENT_CONFIRM',
          childStartStrategy: 'LATEST_PUBLISHED',
          parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
        },
        ui: { width: 240, height: 88 },
      },
      {
        id: 'end_1',
        type: 'end',
        name: '结束',
        position: { x: 540, y: 100 },
        config: {},
        ui: { width: 240, height: 88 },
      },
    ],
    edges: [
      {
        id: 'edge_1',
        source: 'start_1',
        target: 'subprocess_1',
        priority: 10,
        label: '提交',
      },
      {
        id: 'edge_2',
        source: 'subprocess_1',
        target: 'end_1',
        priority: 10,
        label: '完成',
      },
    ],
  }
}

async function openTaskFromTodoList(page: Page, taskId: string, keyword: string) {
  await page.goto('/workbench/todos/list')
  await page.getByPlaceholder('搜索流程标题、节点名称、发起人或业务单号').fill(keyword)
  const row = page.locator('table tbody tr').filter({ hasText: keyword }).first()
  await expect(row).toBeVisible()
  await page.goto(`/workbench/todos/${taskId}`)
}

test.describe('approval subprocess runtime', () => {
  test('shows subprocess runtime structure in todo detail and waits parent confirm after child completes', async ({
    browser,
    request,
  }) => {
    const seed = Date.now()
    const childProcessKey = `oa_sub_review_e2e_${seed}`
    const childProcessName = `子流程审批前端E2E-${seed}`
    const parentProcessKey = `oa_parent_with_subprocess_e2e_${seed}`
    const businessKey = `parent_bill_e2e_${seed}`

    const adminToken = await apiLogin(request, 'admin', 'admin123')
    await publishProcessDefinition(
      request,
      adminToken,
      buildSubprocessChildPayload(childProcessKey, childProcessName)
    )
    await publishProcessDefinition(
      request,
      adminToken,
      buildParentWaitConfirmPayload(parentProcessKey, childProcessKey)
    )

    const applicantToken = await apiLogin(request, 'zhangsan', '123456')
    const startResult = await startRuntimeProcess(request, applicantToken, parentProcessKey, businessKey)
    const parentInstanceId = startResult.data.instanceId

    const linksBeforeHandle = await fetchProcessLinks(request, applicantToken, parentInstanceId)
    expect(linksBeforeHandle.data).toHaveLength(1)
    const childLink = linksBeforeHandle.data[0]
    expect(childLink.status).toBe('RUNNING')
    expect(childLink.parentResumeStrategy).toBe('WAIT_PARENT_CONFIRM')
    expect(childLink.joinMode).toBe('WAIT_PARENT_CONFIRM')

    const { context, page } = await openAuthenticatedPage(browser, request, 'lisi', '123456')

    try {
      const managerToken = await apiLogin(request, 'lisi', '123456')
      const childTaskId = await fetchTodoTaskId(request, managerToken, childProcessName)
      await openTaskFromTodoList(page, childTaskId, childProcessName)

      await expect(page.getByText('统一审批单详情页')).toBeVisible()
      const runtimeTab = page.getByRole('tab', { name: '运行态' })
      await runtimeTab.click()

      const body = page.locator('body')
      await expect(body).toContainText('运行态结构')
      await expect(body).toContainText(`根流程实例：${parentInstanceId}`)
      await expect(body).toContainText(`子流程实例：${childLink.childInstanceId}`)
      await expect(body).toContainText(`子流程编码：${childProcessKey}`)
      await expect(body).toContainText('汇合模式：WAIT_PARENT_CONFIRM')
      await expect(body).toContainText('父流程恢复策略：WAIT_PARENT_CONFIRM')

      await page.getByPlaceholder('请输入审批意见').fill('Playwright 子流程审批通过')
      await page.getByRole('button', { name: '完成任务' }).click()

      await expect
        .poll(
          async () => {
            const links = await fetchProcessLinks(request, applicantToken, parentInstanceId)
            return links.data[0]?.status ?? ''
          },
          { timeout: 20_000 }
        )
        .toBe('WAIT_PARENT_CONFIRM')
    } finally {
      await context.close()
    }
  })
})
