import { expect, test, type APIRequestContext, type Browser } from '@playwright/test'

const APP_BASE_URL = process.env.E2E_BASE_URL || 'http://127.0.0.1:5173'
const API_BASE_URL = process.env.E2E_API_BASE_URL || 'http://127.0.0.1:8080/api/v1'
const ACCESS_TOKEN_COOKIE = 'west_flow_ai_access_token'

type LoginEnvelope = {
  data: {
    accessToken: string
  }
}

type LaunchEnvelope = {
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
    taskTrace?: Array<{
      taskId: string
      nodeName: string
      assigneeUserId?: string | null
      isAddSignTask?: boolean
      status: string
    }>
    instanceEvents?: Array<{
      eventType: string
      signatureStatus?: string | null
      signatureType?: string | null
    }>
    currentTaskId?: string | null
    currentNodeName?: string | null
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
  const payload = (await response.json()) as LaunchEnvelope
  expect(payload.data.firstActiveTask?.taskId).toBeTruthy()
  return payload.data
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

async function selectUserFromPicker(page: import('@playwright/test').Page, ariaLabel: string, keyword: string, displayName: string) {
  await page.getByLabel(ariaLabel).click()
  await page.getByPlaceholder('搜索姓名、账号、部门或岗位').fill(keyword)
  const option = page.locator('[cmdk-item]').filter({ hasText: displayName }).first()
  await expect(option).toBeVisible()
  await option.click()
}

async function fetchApprovalSheetDetail(
  request: APIRequestContext,
  accessToken: string,
  billId: string
) {
  const response = await request.get(`${API_BASE_URL}/process-runtime/approval-sheets/by-business`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    params: {
      businessType: 'OA_LEAVE',
      businessId: billId,
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as ApprovalSheetDetailEnvelope
}

async function fetchInitiatedApprovalSheet(
  request: APIRequestContext,
  accessToken: string,
  keyword: string
) {
  const response = await request.post(`${API_BASE_URL}/process-runtime/approval-sheets/page`, {
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
      view: 'INITIATED',
      businessTypes: [],
    },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as {
    data: {
      records: Array<{
        businessId: string | null
        currentNodeName: string | null
      }>
    }
  }
}

test.describe('approval workbench actions', () => {
  test('supports claim, add-sign and remove-sign on todo detail', async ({ browser, request }) => {
    const reason = `E2E-加减签-${Date.now()}`
    const adminToken = await apiLogin(request, 'admin', 'admin123')
    const created = await createLeave(request, adminToken, reason)
    const { context, page } = await openAuthenticatedPage(browser, request, 'zhangsan', '123456')

    try {
      await page.goto(`/workbench/todos/${created.firstActiveTask?.taskId}`)
      await expect(page.getByRole('button', { name: '认领任务' })).toBeVisible()
      await page.getByRole('button', { name: '认领任务' }).click()
      await expect(page.getByRole('button', { name: '认领任务' })).toHaveCount(0)

      await page.getByRole('button', { name: '加签' }).click()
      await selectUserFromPicker(page, '加签用户', '平台管理员', '平台管理员')
      await page.getByPlaceholder('请输入加签说明').fill('Playwright E2E 加签')
      await page.getByRole('button', { name: '确认加签' }).click()
      await expect(page.getByRole('button', { name: '确认加签' })).toHaveCount(0)

      await page.getByRole('button', { name: '减签' }).click()
      const addSignTaskSelect = page.locator('select').first()
      const removableTaskValue = await addSignTaskSelect
        .locator('option')
        .filter({ hasText: '平台管理员' })
        .first()
        .getAttribute('value')
      expect(removableTaskValue).toBeTruthy()
      await addSignTaskSelect.selectOption(removableTaskValue!)
      await expect(addSignTaskSelect).not.toHaveValue('')
      await page.getByPlaceholder('请输入减签说明').fill('Playwright E2E 减签')
      await page.getByRole('button', { name: '确认减签' }).click()
      await expect(page.getByRole('button', { name: '确认减签' })).toHaveCount(0)

      const detail = await fetchApprovalSheetDetail(request, adminToken, created.billId)
      const activeAddSignTasks =
        detail.data.taskTrace?.filter(
          (item) => item.isAddSignTask && (item.status === 'PENDING' || item.status === 'PENDING_CLAIM')
        ) ?? []
      expect(activeAddSignTasks).toHaveLength(0)
    } finally {
      await context.close()
    }
  })

  test('supports sign and approve on todo detail', async ({ browser, request }) => {
    const reason = `E2E-签章通过-${Date.now()}`
    const adminToken = await apiLogin(request, 'admin', 'admin123')
    const created = await createLeave(request, adminToken, reason)
    const { context, page } = await openAuthenticatedPage(browser, request, 'zhangsan', '123456')

    try {
      await page.goto(`/workbench/todos/${created.firstActiveTask?.taskId}`)
      await page.getByRole('button', { name: '认领任务' }).click()

      await page.getByRole('button', { name: '签章' }).click()
      await page.getByPlaceholder('请输入签章说明').fill('Playwright E2E 签章')
      await page.getByRole('button', { name: '确认签章' }).click()
      await expect(page.getByRole('button', { name: '确认签章' })).toHaveCount(0)

      await page.getByPlaceholder('请输入审批意见').fill('Playwright E2E 同意')
      await page.getByRole('button', { name: '完成任务' }).click()

      await expect
        .poll(
          async () => {
            const result = await fetchInitiatedApprovalSheet(request, adminToken, created.billNo)
            return result.data.records.find((record) => record.businessId === created.billId)?.currentNodeName ?? ''
          },
          { timeout: 20_000 }
        )
        .toBe('负责人确认')

      const detail = await fetchApprovalSheetDetail(request, adminToken, created.billId)
      expect(JSON.stringify(detail.data.instanceEvents ?? [])).toContain('SIGNED')
    } finally {
      await context.close()
    }
  })
})
