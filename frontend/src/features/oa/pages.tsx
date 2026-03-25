import { startTransition } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { Loader2, Send } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ContextualCopilotEntry } from '@/features/ai/context-entry'
import { ProFormActions, ProFormSection, ProFormShell } from '@/features/shared/pro-form'
import { PageShell } from '@/features/shared/page-shell'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { ProTable, type ProTableBoardColumn } from '@/features/shared/pro-table'
import {
  createApprovalSheetColumns,
  formatApprovalSheetDateTime,
  resolveApprovalSheetAutomationStatusLabel,
  resolveApprovalSheetInstanceStatusLabel,
  summarizeApprovalSheets,
} from '@/features/workbench/approval-sheet-list'
import { WorkbenchTodoDetailPage } from '@/features/workbench/pages'
import { handleServerError } from '@/lib/handle-server-error'
import {
  createOACommonRequestBill,
  createOAExpenseBill,
  createOALeaveBill,
  type OALaunchResponse,
} from '@/lib/api/oa'
import {
  listApprovalSheets,
  type ApprovalSheetListItem,
} from '@/lib/api/workbench'

const leaveFormSchema = z.object({
  days: z
    .string()
    .trim()
    .min(1, '请填写请假天数')
    .refine((value) => Number.isInteger(Number(value)) && Number(value) > 0, {
      message: '请填写大于 0 的请假天数',
    }),
  reason: z.string().trim().min(2, '请填写请假原因'),
})

const expenseFormSchema = z.object({
  amount: z
    .string()
    .trim()
    .min(1, '请填写报销金额')
    .refine((value) => Number(value) > 0, {
      message: '请填写大于 0 的报销金额',
    }),
  reason: z.string().trim().min(2, '请填写报销事由'),
})

const commonFormSchema = z.object({
  title: z.string().trim().min(2, '请填写申请标题'),
  content: z.string().trim().min(2, '请填写申请内容'),
})

type LeaveFormValues = z.infer<typeof leaveFormSchema>
type ExpenseFormValues = z.infer<typeof expenseFormSchema>
type CommonFormValues = z.infer<typeof commonFormSchema>
type OABusinessType = 'OA_LEAVE' | 'OA_EXPENSE' | 'OA_COMMON'

type OABoardStatus = 'RUNNING' | 'APPROVED' | 'REJECTED' | 'REVOKED'

const leaveDetailRoute = getRouteApi('/_authenticated/oa/leave/$billId')
const expenseDetailRoute = getRouteApi('/_authenticated/oa/expense/$billId')
const commonDetailRoute = getRouteApi('/_authenticated/oa/common/$billId')
const leaveListRoute = getRouteApi('/_authenticated/oa/leave/list')
const expenseListRoute = getRouteApi('/_authenticated/oa/expense/list')
const commonListRoute = getRouteApi('/_authenticated/oa/common/list')
const oaQueryRoute = getRouteApi('/_authenticated/oa/query')

// 发起成功后优先回到首个待办，便于直接继续处理流程。
function navigateToFirstTask(
  navigate: ReturnType<typeof useNavigate>,
  response: OALaunchResponse
) {
  const taskId = response.activeTasks[0]?.taskId

  if (taskId) {
    startTransition(() => {
      navigate({
        to: '/workbench/todos/$taskId',
        params: { taskId },
      })
    })
    return
  }

  startTransition(() => {
    navigate({ to: '/workbench/todos/list' })
  })
}

function navigateToApprovalSheetDetail(
  navigate: ReturnType<typeof useNavigate>,
  response: OALaunchResponse,
  href: '/oa/leave/$billId' | '/oa/expense/$billId' | '/oa/common/$billId'
) {
  if (response.billId) {
    startTransition(() => {
      navigate({
        to: href,
        params: { billId: response.billId },
      })
    })
    return
  }

  navigateToFirstTask(navigate, response)
}

// 发起结果卡片只负责回显单号、实例和首个待办，不掺杂表单逻辑。
function LaunchSummaryCard({
  response,
}: {
  response: OALaunchResponse | null
}) {
  if (!response) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>发起结果</CardTitle>
          <CardDescription>提交后会显示单号和首个待办任务。</CardDescription>
        </CardHeader>
        <CardContent className='text-sm text-muted-foreground'>
          这里会回显最新发起结果，便于确认业务单据和流程实例已经生成。
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>发起结果</CardTitle>
        <CardDescription>业务单据已经保存并自动进入流程中心。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-3 text-sm'>
        <Alert>
          <Send />
          <AlertTitle>提交成功</AlertTitle>
          <AlertDescription>
            单号 {response.billNo} · 实例 {response.processInstanceId}
          </AlertDescription>
        </Alert>
        <p className='text-muted-foreground'>
          首个待办任务：
          {response.activeTasks[0]?.nodeName ?? '流程已结束或未产生待办'}
        </p>
      </CardContent>
    </Card>
  )
}

function resolveOABoardStatus(item: ApprovalSheetListItem): OABoardStatus {
  if (item.instanceStatus === 'RUNNING') {
    return 'RUNNING'
  }

  if (item.latestAction === 'REJECT' || item.latestAction === 'REJECT_ROUTE') {
    return 'REJECTED'
  }

  if (item.latestAction === 'REVOKE' || item.latestAction === 'WITHDRAW') {
    return 'REVOKED'
  }

  return 'APPROVED'
}

function buildOABoardColumns(
  records: ApprovalSheetListItem[]
): ProTableBoardColumn<ApprovalSheetListItem>[] {
  const grouped = {
    RUNNING: [] as ApprovalSheetListItem[],
    APPROVED: [] as ApprovalSheetListItem[],
    REJECTED: [] as ApprovalSheetListItem[],
    REVOKED: [] as ApprovalSheetListItem[],
  }

  for (const record of records) {
    grouped[resolveOABoardStatus(record)].push(record)
  }

  return [
    { key: 'RUNNING', title: '审批中', items: grouped.RUNNING },
    { key: 'APPROVED', title: '已通过', items: grouped.APPROVED },
    { key: 'REJECTED', title: '已驳回', items: grouped.REJECTED },
    { key: 'REVOKED', title: '已撤销', items: grouped.REVOKED },
  ]
}

function OABoardCard({ item }: { item: ApprovalSheetListItem }) {
  return (
    <div className='rounded-xl border bg-background p-4 shadow-sm'>
      <div className='space-y-1'>
        <div className='text-sm font-semibold'>{item.businessTitle ?? item.processName}</div>
        <div className='text-xs text-muted-foreground'>{item.billNo ?? item.instanceId}</div>
      </div>
      <div className='mt-3 grid gap-2 text-xs text-muted-foreground'>
        <div>发起人：{item.initiatorUserId}</div>
        <div>当前节点：{item.currentNodeName ?? '--'}</div>
        <div>实例状态：{resolveApprovalSheetInstanceStatusLabel(item.instanceStatus)}</div>
        <div>自动化：{resolveApprovalSheetAutomationStatusLabel(item.automationStatus)}</div>
        <div>更新时间：{formatApprovalSheetDateTime(item.updatedAt)}</div>
      </div>
    </div>
  )
}

function LeaveCreateForm() {
  const navigate = useNavigate()
  const form = useForm<LeaveFormValues>({
    resolver: zodResolver(leaveFormSchema),
    defaultValues: {
      days: '1',
      reason: '',
    },
  })
  const launchMutation = useMutation({
    mutationFn: createOALeaveBill,
    onSuccess: (response) => {
      navigateToApprovalSheetDetail(navigate, response, '/oa/leave/$billId')
    },
    onError: handleServerError,
  })

  return (
    <ProFormShell title='请假申请' description='先填写请假业务表单，提交后自动发起对应流程。'>
      <Form {...form}>
        <form
          className='space-y-6'
          onSubmit={form.handleSubmit((values) =>
            launchMutation.mutate({
              days: Number(values.days),
              reason: values.reason.trim(),
            })
          )}
        >
          <ProFormSection title='申请信息' columns={1}>
            <FormField
              control={form.control}
              name='days'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>请假天数</FormLabel>
                  <FormControl>
                    <Input type='number' min='1' placeholder='例如：3' {...field} />
                  </FormControl>
                  <FormDescription>填写整数天数。</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormSection title='申请说明' columns={1}>
            <FormField
              control={form.control}
              name='reason'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>请假原因</FormLabel>
                  <FormControl>
                    <Textarea className='min-h-28' placeholder='请输入请假原因' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormActions>
            <Button type='submit' disabled={launchMutation.isPending}>
              {launchMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  发起中
                </>
              ) : (
                '发起请假申请'
              )}
            </Button>
            <Button asChild type='button' variant='outline'>
              <Link to='/workbench/start'>返回业务入口</Link>
            </Button>
          </ProFormActions>
        </form>
      </Form>
    </ProFormShell>
  )
}

// OA 发起页只做业务表单，提交后由后端决定绑定到哪个流程。
function ExpenseCreateForm() {
  const navigate = useNavigate()
  const form = useForm<ExpenseFormValues>({
    resolver: zodResolver(expenseFormSchema),
    defaultValues: {
      amount: '',
      reason: '',
    },
  })
  const launchMutation = useMutation({
    mutationFn: createOAExpenseBill,
    onSuccess: (response) => {
      navigateToApprovalSheetDetail(navigate, response, '/oa/expense/$billId')
    },
    onError: handleServerError,
  })

  return (
    <ProFormShell title='报销申请' description='填写金额和事由，提交后自动匹配当前生效流程。'>
      <Form {...form}>
        <form
          className='space-y-6'
          onSubmit={form.handleSubmit((values) =>
            launchMutation.mutate({
              amount: Number(values.amount),
              reason: values.reason.trim(),
            })
          )}
        >
          <ProFormSection title='报销信息' columns={1}>
            <FormField
              control={form.control}
              name='amount'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>报销金额</FormLabel>
                  <FormControl>
                    <Input type='number' min='0' step='0.01' placeholder='例如：128.5' {...field} />
                  </FormControl>
                  <FormDescription>支持小数金额。</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormSection title='报销说明' columns={1}>
            <FormField
              control={form.control}
              name='reason'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>报销事由</FormLabel>
                  <FormControl>
                    <Textarea className='min-h-28' placeholder='请输入报销事由' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormActions>
            <Button type='submit' disabled={launchMutation.isPending}>
              {launchMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  发起中
                </>
              ) : (
                '发起报销申请'
              )}
            </Button>
            <Button asChild type='button' variant='outline'>
              <Link to='/workbench/start'>返回业务入口</Link>
            </Button>
          </ProFormActions>
        </form>
      </Form>
    </ProFormShell>
  )
}

function CommonCreateForm() {
  const navigate = useNavigate()
  const form = useForm<CommonFormValues>({
    resolver: zodResolver(commonFormSchema),
    defaultValues: {
      title: '',
      content: '',
    },
  })
  const launchMutation = useMutation({
    mutationFn: createOACommonRequestBill,
    onSuccess: (response) => {
      navigateToApprovalSheetDetail(navigate, response, '/oa/common/$billId')
    },
    onError: handleServerError,
  })

  return (
    <ProFormShell title='通用申请' description='用于标准 OA 之外的通用业务申请入口。'>
      <Form {...form}>
        <form
          className='space-y-6'
          onSubmit={form.handleSubmit((values) =>
            launchMutation.mutate({
              title: values.title.trim(),
              content: values.content.trim(),
            })
          )}
        >
          <ProFormSection title='申请信息' columns={1}>
            <FormField
              control={form.control}
              name='title'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>申请标题</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：资产借用' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormSection title='申请内容' columns={1}>
            <FormField
              control={form.control}
              name='content'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>申请内容</FormLabel>
                  <FormControl>
                    <Textarea className='min-h-28' placeholder='请输入申请内容' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormActions>
            <Button type='submit' disabled={launchMutation.isPending}>
              {launchMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  发起中
                </>
              ) : (
                '发起通用申请'
              )}
            </Button>
            <Button asChild type='button' variant='outline'>
              <Link to='/workbench/start'>返回业务入口</Link>
            </Button>
          </ProFormActions>
        </form>
      </Form>
    </ProFormShell>
  )
}

function OABusinessListPage({
  title,
  description,
  sourceRoute,
  businessType,
  createHref,
  createLabel,
  search,
  navigate,
}: {
  title: string
  description: string
  sourceRoute: string
  businessType: OABusinessType
  createHref: '/oa/leave/create' | '/oa/expense/create' | '/oa/common/create'
  createLabel: string
  search: ListQuerySearch
  navigate: NavigateFn
}) {
  const approvalSheetsQuery = useQuery({
    queryKey: ['oa', 'business-list', businessType, search],
    queryFn: () =>
      listApprovalSheets({
        ...search,
        view: 'INITIATED',
        businessTypes: [businessType],
      }),
  })

  const pageData = approvalSheetsQuery.data ?? {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
  const summary = summarizeApprovalSheets(pageData.records)

  return (
    <>
      {approvalSheetsQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>{title}加载失败</AlertTitle>
          <AlertDescription>
            {approvalSheetsQuery.error instanceof Error
              ? approvalSheetsQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ProTable
        title={title}
        description={description}
        searchPlaceholder='搜索流程标题、业务标题、单号或当前节点'
        search={search}
        navigate={navigate}
        columns={createApprovalSheetColumns('oa')}
        data={pageData.records}
        total={pageData.total}
        supportsBoard
        resolveBoardColumns={buildOABoardColumns}
        renderBoardCard={(item) => <OABoardCard item={item} />}
        onRefresh={() => void approvalSheetsQuery.refetch()}
        summaries={[
          {
            label: '审批单总量',
            value: String(pageData.total),
            hint: '当前登录人在该业务下发起的审批单总量。',
          },
          {
            label: '进行中',
            value: String(summary.running),
            hint: '仍在审批中的业务单据数量。',
          },
          {
            label: '已完成',
            value: String(summary.completed),
            hint: '已经结束的业务单据数量。',
          },
        ]}
        createActionNode={
          <Button asChild>
            <Link to={createHref} search={{}}>
              {createLabel}
            </Link>
          </Button>
        }
        extraActions={
          <ContextualCopilotEntry
            sourceRoute={sourceRoute}
            label='用 AI 解读当前业务列表'
          />
        }
      />
    </>
  )
}

export function OALeaveCreatePage() {
  return (
    <PageShell
      title='请假申请'
      description='业务发起页先保存业务单据，再自动发起对应流程实例。'
      actions={
        <>
          <ContextualCopilotEntry
            sourceRoute='/oa/leave/create'
            label='用 AI 辅助填写请假申请'
          />
          <Button asChild variant='outline'>
            <Link to='/workbench/start'>返回业务入口</Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <LeaveCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function OALeaveListPage() {
  const search = leaveListRoute.useSearch()
  const navigate = leaveListRoute.useNavigate()

  return (
    <OABusinessListPage
      title='请假申请列表'
      description='查看我发起的请假单据和对应审批进度，默认从列表进入再发起新申请。'
      sourceRoute='/oa/leave/list'
      businessType='OA_LEAVE'
      createHref='/oa/leave/create'
      createLabel='发起请假申请'
      search={search}
      navigate={navigate}
    />
  )
}

export function OAExpenseCreatePage() {
  return (
    <PageShell
      title='报销申请'
      description='报销单保存后会直接进入当前生效的审批流程。'
      actions={
        <>
          <ContextualCopilotEntry
            sourceRoute='/oa/expense/create'
            label='用 AI 辅助填写报销申请'
          />
          <Button asChild variant='outline'>
            <Link to='/workbench/start'>返回业务入口</Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <ExpenseCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function OAExpenseListPage() {
  const search = expenseListRoute.useSearch()
  const navigate = expenseListRoute.useNavigate()

  return (
    <OABusinessListPage
      title='报销申请列表'
      description='查看我发起的报销单据和审批进度，默认从列表进入再发起新申请。'
      sourceRoute='/oa/expense/list'
      businessType='OA_EXPENSE'
      createHref='/oa/expense/create'
      createLabel='发起报销申请'
      search={search}
      navigate={navigate}
    />
  )
}

export function OACommonCreatePage() {
  return (
    <PageShell
      title='通用申请'
      description='通用申请入口用于非固定场景的业务发起。'
      actions={
        <>
          <ContextualCopilotEntry
            sourceRoute='/oa/common/create'
            label='用 AI 辅助填写通用申请'
          />
          <Button asChild variant='outline'>
            <Link to='/workbench/start'>返回业务入口</Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <CommonCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function OACommonListPage() {
  const search = commonListRoute.useSearch()
  const navigate = commonListRoute.useNavigate()

  return (
    <OABusinessListPage
      title='通用申请列表'
      description='查看我发起的通用申请和审批进度，默认从列表进入再发起新申请。'
      sourceRoute='/oa/common/list'
      businessType='OA_COMMON'
      createHref='/oa/common/create'
      createLabel='发起通用申请'
      search={search}
      navigate={navigate}
    />
  )
}

export function OAQueryPage() {
  const search = oaQueryRoute.useSearch()
  const navigate = oaQueryRoute.useNavigate()
  const approvalSheetsQuery = useQuery({
    queryKey: ['oa', 'query-page', search],
    queryFn: () =>
      listApprovalSheets({
        ...search,
        view: 'INITIATED',
        businessTypes: ['OA_LEAVE', 'OA_EXPENSE', 'OA_COMMON'],
      }),
  })

  const pageData = approvalSheetsQuery.data ?? {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
  const summary = summarizeApprovalSheets(pageData.records)

  return (
    <>
      {approvalSheetsQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>OA 流程查询加载失败</AlertTitle>
          <AlertDescription>
            {approvalSheetsQuery.error instanceof Error
              ? approvalSheetsQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ProTable
        title='OA 流程查询'
        description='按审批单维度查看我发起的 OA 业务，详情页直接展示业务表单、流程轨迹和流程图回顾。'
        searchPlaceholder='搜索流程标题、业务标题、单号或当前节点'
        search={search}
        navigate={navigate}
        columns={createApprovalSheetColumns('oa')}
        data={pageData.records}
        total={pageData.total}
        supportsBoard
        resolveBoardColumns={buildOABoardColumns}
        renderBoardCard={(item) => <OABoardCard item={item} />}
        onRefresh={() => void approvalSheetsQuery.refetch()}
        summaries={[
          {
            label: 'OA 审批单总量',
            value: String(pageData.total),
            hint: '当前登录人发起的 OA 审批单总量。',
          },
          {
            label: '进行中',
            value: String(summary.running),
            hint: '仍在审批中的 OA 单据数量。',
          },
          {
            label: '已完成',
            value: String(summary.completed),
            hint: '已经结束的 OA 单据数量。',
          },
        ]}
        createActionNode={
          <Button asChild>
            <Link to='/workbench/start' search={{}}>
              发起 OA 申请
            </Link>
          </Button>
        }
        extraActions={
          <ContextualCopilotEntry
            sourceRoute='/oa/query'
            label='用 AI 解读 OA 查询结果'
          />
        }
      />
    </>
  )
}

export function OAApprovalSheetDetailPage({
  businessType,
  billId,
  backHref = '/oa/query',
  backLabel = '返回 OA 流程查询',
}: {
  businessType: OABusinessType
  billId: string
  backHref?:
    | '/oa/query'
    | '/oa/leave/list'
    | '/oa/expense/list'
    | '/oa/common/list'
    | '/workbench/todos/list'
  backLabel?: string
}) {
  return (
    <WorkbenchTodoDetailPage
      businessType={businessType}
      businessId={billId}
      backHref={backHref}
      backLabel={backLabel}
    />
  )
}

export function OALeaveBillDetailPage() {
  const { billId } = leaveDetailRoute.useParams()

  return (
    <OAApprovalSheetDetailPage
      businessType='OA_LEAVE'
      billId={billId}
      backHref='/oa/leave/list'
      backLabel='返回请假申请列表'
    />
  )
}

export function OAExpenseBillDetailPage() {
  const { billId } = expenseDetailRoute.useParams()

  return (
    <OAApprovalSheetDetailPage
      businessType='OA_EXPENSE'
      billId={billId}
      backHref='/oa/expense/list'
      backLabel='返回报销申请列表'
    />
  )
}

export function OACommonBillDetailPage() {
  const { billId } = commonDetailRoute.useParams()

  return (
    <OAApprovalSheetDetailPage
      businessType='OA_COMMON'
      billId={billId}
      backHref='/oa/common/list'
      backLabel='返回通用申请列表'
    />
  )
}
