import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { sidebarData } from '@/components/layout/data/sidebar-data'
import {
  OACommonCreatePage,
  OAExpenseCreatePage,
  OAQueryPage,
  OALeaveCreatePage,
} from './pages'

const { navigateMock, oaApiMocks } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  oaApiMocks: {
    createOALeaveBill: vi.fn(),
    createOAExpenseBill: vi.fn(),
    createOACommonRequestBill: vi.fn(),
  },
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to?: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => navigateMock,
}))

vi.mock('@/features/shared/page-shell', () => ({
  PageShell: ({
    title,
    description,
    actions,
    children,
  }: {
    title: string
    description: string
    actions?: React.ReactNode
    children: React.ReactNode
  }) => (
    <div>
      <h1>{title}</h1>
      <p>{description}</p>
      {actions}
      {children}
    </div>
  ),
}))

vi.mock('@/lib/api/oa', () => oaApiMocks)

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

function mockLaunchResponse(taskId = 'task_001') {
  return {
    billId: 'bill_001',
    billNo: 'OA-20260322-001',
    processInstanceId: 'pi_001',
    activeTasks: [
      {
        taskId,
        nodeId: 'approve_manager',
        nodeName: '部门负责人审批',
        status: 'PENDING',
        assignmentMode: 'USER',
        candidateUserIds: ['usr_002'],
        assigneeUserId: 'usr_002',
      },
    ],
  }
}

describe('oa pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('submits 请假申请 and jumps to the first task', async () => {
    oaApiMocks.createOALeaveBill.mockResolvedValue(mockLaunchResponse())

    renderWithQuery(<OALeaveCreatePage />)

    fireEvent.change(screen.getByLabelText('请假天数'), {
      target: { value: '3' },
    })
    fireEvent.change(screen.getByLabelText('请假原因'), {
      target: { value: '外出处理事务' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起请假申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOALeaveBill).toHaveBeenCalledWith(
        {
          days: 3,
          reason: '外出处理事务',
        },
        expect.objectContaining({
          client: expect.any(Object),
        })
      )
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/workbench/todos/$taskId',
        params: { taskId: 'task_001' },
      })
    })
  })

  it('submits 报销申请 to the expense launch endpoint', async () => {
    oaApiMocks.createOAExpenseBill.mockResolvedValue(mockLaunchResponse('task_002'))

    renderWithQuery(<OAExpenseCreatePage />)

    fireEvent.change(screen.getByLabelText('报销金额'), {
      target: { value: '128.5' },
    })
    fireEvent.change(screen.getByLabelText('报销事由'), {
      target: { value: '客户接待' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起报销申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOAExpenseBill).toHaveBeenCalledWith(
        {
          amount: 128.5,
          reason: '客户接待',
        },
        expect.objectContaining({
          client: expect.any(Object),
        })
      )
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/workbench/todos/$taskId',
        params: { taskId: 'task_002' },
      })
    })
  })

  it('submits 通用申请 to the common launch endpoint', async () => {
    oaApiMocks.createOACommonRequestBill.mockResolvedValue(
      mockLaunchResponse('task_003')
    )

    renderWithQuery(<OACommonCreatePage />)

    fireEvent.change(screen.getByLabelText('申请标题'), {
      target: { value: '资产借用' },
    })
    fireEvent.change(screen.getByLabelText('申请内容'), {
      target: { value: '申请借用一台演示电脑' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起通用申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOACommonRequestBill).toHaveBeenCalledWith(
        {
          title: '资产借用',
          content: '申请借用一台演示电脑',
        },
        expect.objectContaining({
          client: expect.any(Object),
        })
      )
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/workbench/todos/$taskId',
        params: { taskId: 'task_003' },
      })
    })
  })

  it('renders OA query entry links to the process center', () => {
    renderWithQuery(<OAQueryPage />)

    expect(screen.getByText('OA 流程查询')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '前往待办列表' })
    ).toHaveAttribute('href', '/workbench/todos/list')
    expect(
      screen.getByRole('link', { name: '进入流程中心发起流程' })
    ).toHaveAttribute('href', '/workbench/start')
  })

  it('exposes OA and process-center entries in the sidebar data', () => {
    const oaGroup = sidebarData.navGroups.find(({ title }) => title === 'OA')
    const processGroup = sidebarData.navGroups.find(
      ({ title }) => title === '流程管理'
    )
    const processCenter = processGroup?.items.find(
      (item) => item.title === '流程中心'
    )

    expect(oaGroup?.items.map((item) => item.title)).toEqual([
      '请假申请',
      '报销申请',
      '通用申请',
      'OA 流程查询',
    ])
    expect(processCenter?.items?.map((item) => item.title)).toEqual([
      '待办列表',
      '发起流程',
    ])
  })
})
