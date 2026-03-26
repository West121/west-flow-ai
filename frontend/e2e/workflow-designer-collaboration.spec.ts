import { expect, test, type Page } from '@playwright/test'

async function login(page: Page, username: string, password: string) {
  await page.goto('/sign-in')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL((url) => !url.pathname.endsWith('/sign-in'))
}

async function openDesigner(page: Page, processDefinitionId: string, mode?: 'view') {
  const params = new URLSearchParams({ processDefinitionId })
  if (mode) {
    params.set('mode', mode)
  }
  await page.goto(`/workflow/designer?${params.toString()}`)
  await expect(page.getByRole('heading', { name: '流程设计器' })).toBeVisible()
  await expect(page.getByTestId('workflow-designer-layout')).toBeVisible()
}

test.describe('workflow designer collaboration', () => {
  test('syncs process name changes across two authenticated editors', async ({ browser }) => {
    const adminContext = await browser.newContext()
    const reviewerContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    const reviewerPage = await reviewerContext.newPage()

    try {
      await login(adminPage, 'admin', 'admin123')
      await login(reviewerPage, 'zhangsan', '123456')

      await openDesigner(adminPage, 'oa_leave:1')
      await openDesigner(reviewerPage, 'oa_leave:1')

      await expect(adminPage.getByText(/协同在线|本地协同|协同连接中|协同重连中/)).toBeVisible()
      await expect(reviewerPage.getByText(/协同在线|本地协同|协同连接中|协同重连中/)).toBeVisible()

      const processName = `请假审批-E2E-${Date.now()}`
      const adminProcessNameInput = adminPage.getByLabel('流程名称')
      await adminProcessNameInput.click()
      await adminProcessNameInput.fill(processName)

      await expect(reviewerPage.getByLabel('流程名称')).toHaveValue(processName, {
        timeout: 15_000,
      })
    } finally {
      await adminContext.close()
      await reviewerContext.close()
    }
  })

  test('shows readonly spectator mode without editing actions', async ({ page }) => {
    await login(page, 'admin', 'admin123')
    await openDesigner(page, 'oa_leave:1', 'view')

    await expect(page.getByText('只读观摩')).toBeVisible()
    await expect(page.getByRole('button', { name: '保存草稿' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '发布流程' })).toHaveCount(0)
  })
})
