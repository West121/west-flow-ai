import { startTransition, type ElementType } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { Loader2, NotebookText, ReceiptText, Search, Send } from 'lucide-react'
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
import { PageShell } from '@/features/shared/page-shell'
import { WorkbenchTodoDetailPage } from '@/features/workbench/pages'
import { handleServerError } from '@/lib/handle-server-error'
import {
  createOACommonRequestBill,
  createOAExpenseBill,
  createOALeaveBill,
  type OALaunchResponse,
} from '@/lib/api/oa'

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

const leaveDetailRoute = getRouteApi('/_authenticated/oa/leave/$billId')
const expenseDetailRoute = getRouteApi('/_authenticated/oa/expense/$billId')
const commonDetailRoute = getRouteApi('/_authenticated/oa/common/$billId')

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
    <Card>
      <CardHeader>
        <CardTitle>请假申请</CardTitle>
        <CardDescription>先填写请假业务表单，提交后自动发起对应流程。</CardDescription>
      </CardHeader>
      <CardContent>
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

            <div className='flex flex-wrap items-center gap-3'>
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
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

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
    <Card>
      <CardHeader>
        <CardTitle>报销申请</CardTitle>
        <CardDescription>填写金额和事由，提交后自动匹配当前生效流程。</CardDescription>
      </CardHeader>
      <CardContent>
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

            <div className='flex flex-wrap items-center gap-3'>
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
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
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
    <Card>
      <CardHeader>
        <CardTitle>通用申请</CardTitle>
        <CardDescription>用于标准 OA 之外的通用业务申请入口。</CardDescription>
      </CardHeader>
      <CardContent>
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

            <div className='flex flex-wrap items-center gap-3'>
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
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

export function OALeaveCreatePage() {
  return (
    <PageShell
      title='请假申请'
      description='业务发起页先保存业务单据，再自动发起对应流程实例。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/workbench/start'>返回业务入口</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <LeaveCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function OAExpenseCreatePage() {
  return (
    <PageShell
      title='报销申请'
      description='报销单保存后会直接进入当前生效的审批流程。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/workbench/start'>返回业务入口</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <ExpenseCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function OACommonCreatePage() {
  return (
    <PageShell
      title='通用申请'
      description='通用申请入口用于非固定场景的业务发起。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/workbench/start'>返回业务入口</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <CommonCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function OAQueryPage() {
  const entryCards: Array<{
    label: string
    href: '/oa/leave/create' | '/oa/expense/create' | '/oa/common/create' | '/workbench/start'
    icon: ElementType
  }> = [
    { label: '请假申请', href: '/oa/leave/create', icon: NotebookText },
    { label: '报销申请', href: '/oa/expense/create', icon: ReceiptText },
    { label: '通用申请', href: '/oa/common/create', icon: Send },
    { label: '进入流程中心发起流程', href: '/workbench/start', icon: Search },
  ]

  return (
    <PageShell
      title='OA 流程查询'
      description='OA 流程查询入口复用统一流程中心页面，默认筛选 OA 业务域。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/workbench/todos/list'>前往待办列表</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>业务入口</CardTitle>
            <CardDescription>
              先选业务入口，再进入对应的发起页，不再直接输入流程标识。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            {entryCards.map(({ label, href, icon: Icon }) => (
              <Button key={href} asChild variant='outline' className='justify-start'>
                <Link to={href}>
                  <Icon />
                  {label}
                </Link>
              </Button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>统一查询入口</CardTitle>
            <CardDescription>OA 菜单和流程管理都收口到同一套运行态。</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3 text-sm text-muted-foreground'>
            <p>1. OA 下的流程查询入口与流程中心共享待办列表和详情页。</p>
            <p>2. 发起成功后优先进入业务审批单详情页，流程图和业务正文都在同一页查看。</p>
            <p>3. 即使当前没有待办任务，也可以继续查看实例轨迹和办理记录。</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
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
  backHref?: '/oa/query' | '/workbench/todos/list'
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
      backHref='/oa/query'
      backLabel='返回 OA 流程查询'
    />
  )
}

export function OAExpenseBillDetailPage() {
  const { billId } = expenseDetailRoute.useParams()

  return (
    <OAApprovalSheetDetailPage
      businessType='OA_EXPENSE'
      billId={billId}
      backHref='/oa/query'
      backLabel='返回 OA 流程查询'
    />
  )
}

export function OACommonBillDetailPage() {
  const { billId } = commonDetailRoute.useParams()

  return (
    <OAApprovalSheetDetailPage
      businessType='OA_COMMON'
      billId={billId}
      backHref='/oa/query'
      backLabel='返回 OA 流程查询'
    />
  )
}
