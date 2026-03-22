import { startTransition, useMemo } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { Loader2, Send } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
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
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { WorkbenchTodoDetailPage } from '@/features/workbench/pages'
import {
  formatApprovalSheetDateTime,
  resolveApprovalSheetInstanceStatusLabel,
} from '@/features/workbench/approval-sheet-list'
import { handleServerError } from '@/lib/handle-server-error'
import {
  createPLMECOExecution,
  createPLMECRRequest,
  createPLMMaterialChangeRequest,
  listPLMApprovalSheets,
  type PLMLaunchResponse,
} from '@/lib/api/plm'
import { type ApprovalSheetListItem } from '@/lib/api/workbench'

const ecrFormSchema = z.object({
  changeTitle: z.string().trim().min(2, '请填写变更标题'),
  changeReason: z.string().trim().min(2, '请填写变更原因'),
  impactLevel: z.enum(['LOW', 'MEDIUM', 'HIGH']),
})

const ecoFormSchema = z.object({
  changeTitle: z.string().trim().min(2, '请填写执行标题'),
  executionPlan: z.string().trim().min(2, '请填写执行说明'),
  owner: z.string().trim().min(2, '请填写责任人'),
})

const materialChangeFormSchema = z.object({
  materialCode: z.string().trim().min(2, '请填写物料编码'),
  materialName: z.string().trim().min(2, '请填写物料名称'),
  changeReason: z.string().trim().min(2, '请填写变更原因'),
})

type ECRFormValues = z.infer<typeof ecrFormSchema>
type ECOFormValues = z.infer<typeof ecoFormSchema>
type MaterialChangeFormValues = z.infer<typeof materialChangeFormSchema>

const ecrDetailRoute = getRouteApi('/_authenticated/plm/ecr/$billId')
const ecoDetailRoute = getRouteApi('/_authenticated/plm/eco/$billId')
const materialDetailRoute = getRouteApi('/_authenticated/plm/material-master/$billId')
const plmQueryRoute = getRouteApi('/_authenticated/plm/query')

function navigateToFirstTask(
  navigate: ReturnType<typeof useNavigate>,
  response: PLMLaunchResponse
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
  response: PLMLaunchResponse,
  href: '/plm/ecr/$billId' | '/plm/eco/$billId' | '/plm/material-master/$billId'
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
  response: PLMLaunchResponse | null
}) {
  if (!response) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>发起结果</CardTitle>
          <CardDescription>提交后会显示单号和首个待办任务。</CardDescription>
        </CardHeader>
        <CardContent className='text-sm text-muted-foreground'>
          这里会回显最新发起结果，便于确认 PLM 单据和流程实例已经生成。
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>发起结果</CardTitle>
        <CardDescription>PLM 单据已经保存并自动进入统一审批流。</CardDescription>
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

function ECRCreateForm() {
  const navigate = useNavigate()
  const form = useForm<ECRFormValues>({
    resolver: zodResolver(ecrFormSchema),
    defaultValues: {
      changeTitle: '',
      changeReason: '',
      impactLevel: 'MEDIUM',
    },
  })
  const launchMutation = useMutation({
    mutationFn: createPLMECRRequest,
    onSuccess: (response) => {
      navigateToApprovalSheetDetail(navigate, response, '/plm/ecr/$billId')
    },
    onError: handleServerError,
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>ECR 变更申请</CardTitle>
        <CardDescription>先填写变更申请单，再自动发起对应审批流程。</CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form
            className='space-y-6'
            onSubmit={form.handleSubmit((values) =>
              launchMutation.mutate({
                changeTitle: values.changeTitle.trim(),
                changeReason: values.changeReason.trim(),
                impactLevel: values.impactLevel,
              })
            )}
          >
            <FormField
              control={form.control}
              name='changeTitle'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>变更标题</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：结构件替换' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='changeReason'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>变更原因</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：供应替代' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='impactLevel'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>影响等级</FormLabel>
                  <FormControl>
                    <select
                      className='h-10 w-full rounded-md border border-input bg-background px-3 text-sm ring-offset-background'
                      {...field}
                    >
                      <option value='LOW'>低</option>
                      <option value='MEDIUM'>中</option>
                      <option value='HIGH'>高</option>
                    </select>
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
                  '发起 ECR 变更申请'
                )}
              </Button>
              <Button asChild type='button' variant='outline'>
                <Link to='/plm/start'>返回 PLM 发起中心</Link>
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

function ECOCreateForm() {
  const navigate = useNavigate()
  const form = useForm<ECOFormValues>({
    resolver: zodResolver(ecoFormSchema),
    defaultValues: {
      changeTitle: '',
      executionPlan: '',
      owner: '',
    },
  })
  const launchMutation = useMutation({
    mutationFn: createPLMECOExecution,
    onSuccess: (response) => {
      navigateToApprovalSheetDetail(navigate, response, '/plm/eco/$billId')
    },
    onError: handleServerError,
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>ECO 变更执行</CardTitle>
        <CardDescription>用于执行已批准的变更方案，提交后进入统一审批流。</CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form
            className='space-y-6'
            onSubmit={form.handleSubmit((values) =>
              launchMutation.mutate({
                changeTitle: values.changeTitle.trim(),
                executionPlan: values.executionPlan.trim(),
                owner: values.owner.trim(),
              })
            )}
          >
            <FormField
              control={form.control}
              name='changeTitle'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>执行标题</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：ECO 执行通知' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='executionPlan'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>执行说明</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：通知工厂按新版图纸执行' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='owner'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>责任人</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：研发部' {...field} />
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
                  '发起 ECO 变更执行'
                )}
              </Button>
              <Button asChild type='button' variant='outline'>
                <Link to='/plm/start'>返回 PLM 发起中心</Link>
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

function MaterialChangeCreateForm() {
  const navigate = useNavigate()
  const form = useForm<MaterialChangeFormValues>({
    resolver: zodResolver(materialChangeFormSchema),
    defaultValues: {
      materialCode: '',
      materialName: '',
      changeReason: '',
    },
  })
  const launchMutation = useMutation({
    mutationFn: createPLMMaterialChangeRequest,
    onSuccess: (response) => {
      navigateToApprovalSheetDetail(
        navigate,
        response,
        '/plm/material-master/$billId'
      )
    },
    onError: handleServerError,
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>物料主数据变更申请</CardTitle>
        <CardDescription>用于主数据修订、编码调整和名称变更的标准申请入口。</CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form
            className='space-y-6'
            onSubmit={form.handleSubmit((values) =>
              launchMutation.mutate({
                materialCode: values.materialCode.trim(),
                materialName: values.materialName.trim(),
                changeReason: values.changeReason.trim(),
              })
            )}
          >
            <FormField
              control={form.control}
              name='materialCode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>物料编码</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：MAT-001' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='materialName'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>物料名称</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：主板总成' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='changeReason'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>变更原因</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：替换供应商物料编码' {...field} />
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
                  '发起物料主数据变更申请'
                )}
              </Button>
              <Button asChild type='button' variant='outline'>
                <Link to='/plm/start'>返回 PLM 发起中心</Link>
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

function LaunchGrid({
  title,
  description,
  cards,
}: {
  title: string
  description: string
  cards: Array<{
    title: string
    description: string
    href: string
    cta: string
  }>
}) {
  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <Button asChild variant='outline'>
          <Link to='/plm/query'>进入 PLM 流程查询</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-3'>
        {cards.map((card) => (
          <Card key={card.title}>
            <CardHeader>
              <CardTitle>{card.title}</CardTitle>
              <CardDescription>{card.description}</CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild className='w-full' variant='secondary'>
                <Link to={card.href}>{card.cta}</Link>
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>
    </PageShell>
  )
}

export function PLMHomePage() {
  return (
    <LaunchGrid
      title='PLM 发起中心'
      description='面向研发与制造的 PLM 业务入口，统一接入审批流和审批单详情。'
      cards={[
        {
          title: 'ECR 变更申请',
          description: '提交工程变更请求，发起设计评审与审批流。',
          href: '/plm/ecr/create',
          cta: '发起 ECR',
        },
        {
          title: 'ECO 变更执行',
          description: '把已批准变更落到执行阶段，跟踪执行审批。',
          href: '/plm/eco/create',
          cta: '发起 ECO',
        },
        {
          title: '物料主数据变更申请',
          description: '统一管理物料编码、名称和主数据修订。',
          href: '/plm/material-master/create',
          cta: '发起物料变更',
        },
      ]}
    />
  )
}

export function PLMStartPage() {
  return <PLMHomePage />
}

export function PLMECRCreatePage() {
  return (
    <PageShell
      title='ECR 变更申请'
      description='ECR 申请页负责采集变更请求，保存后自动发起审批实例。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/plm/start'>返回 PLM 发起中心</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <ECRCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function PLMECOCreatePage() {
  return (
    <PageShell
      title='ECO 变更执行'
      description='ECO 入口用于执行已批准的变更方案并进入统一审批流。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/plm/start'>返回 PLM 发起中心</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <ECOCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

export function PLMMaterialChangeCreatePage() {
  return (
    <PageShell
      title='物料主数据变更申请'
      description='物料主数据入口用于编码、名称和基础属性修订。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/plm/start'>返回 PLM 发起中心</Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <MaterialChangeCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

function resolvePlmDetailHref(item: ApprovalSheetListItem) {
  if (item.businessType === 'PLM_ECR' && item.businessId) {
    return { to: '/plm/ecr/$billId', params: { billId: item.businessId } } as const
  }
  if (item.businessType === 'PLM_ECO' && item.businessId) {
    return { to: '/plm/eco/$billId', params: { billId: item.businessId } } as const
  }
  if (item.businessType === 'PLM_MATERIAL' && item.businessId) {
    return {
      to: '/plm/material-master/$billId',
      params: { billId: item.businessId },
    } as const
  }

  if (item.currentTaskId) {
    return { to: '/workbench/todos/$taskId', params: { taskId: item.currentTaskId } } as const
  }

  return null
}

function renderPlmDetailAction(item: ApprovalSheetListItem) {
  const href = resolvePlmDetailHref(item)
  if (!href) {
    return (
      <Button size='sm' variant='outline' disabled>
        无详情
      </Button>
    )
  }

  return (
    <Button asChild size='sm' variant='outline'>
      <Link to={href.to} params={href.params}>
        查看
      </Link>
    </Button>
  )
}

function summarizePlmApprovalSheets(records: ApprovalSheetListItem[]) {
  return {
    total: records.length,
    running: records.filter((record) => record.instanceStatus === 'RUNNING').length,
    completed: records.filter((record) => record.instanceStatus === 'COMPLETED').length,
  }
}

export function PLMQueryPage() {
  const search = plmQueryRoute.useSearch()
  const navigate = plmQueryRoute.useNavigate()
  const approvalSheetsQuery = useQuery({
    queryKey: ['plm', 'query-page', search],
    queryFn: () => listPLMApprovalSheets(search),
  })

  const pageData = approvalSheetsQuery.data ?? {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
  const summary = summarizePlmApprovalSheets(pageData.records)

  const columns = useMemo(
    () => [
      {
        accessorKey: 'processName',
        header: '流程标题',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) => (
          <div className='flex flex-col gap-1'>
            <span className='font-medium'>{row.original.processName}</span>
            <span className='text-xs text-muted-foreground'>
              {row.original.processKey}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'businessTitle',
        header: '业务标题',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) =>
          row.original.businessTitle ?? '--',
      },
      {
        accessorKey: 'billNo',
        header: '业务单号',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) =>
          row.original.billNo ?? '--',
      },
      {
        accessorKey: 'businessType',
        header: '业务类型',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) =>
          row.original.businessType ?? '--',
      },
      {
        accessorKey: 'currentNodeName',
        header: '当前节点',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) =>
          row.original.currentNodeName ?? '--',
      },
      {
        accessorKey: 'instanceStatus',
        header: '实例状态',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) => (
          <Badge variant={row.original.instanceStatus === 'COMPLETED' ? 'secondary' : 'destructive'}>
            {resolveApprovalSheetInstanceStatusLabel(row.original.instanceStatus)}
          </Badge>
        ),
      },
      {
        id: 'updatedAt',
        accessorKey: 'updatedAt',
        header: '最近更新时间',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) =>
          formatApprovalSheetDateTime(row.original.updatedAt),
      },
      {
        id: 'actions',
        header: '操作',
        cell: ({ row }: { row: { original: ApprovalSheetListItem } }) =>
          renderPlmDetailAction(row.original),
      },
    ],
    []
  )

  return (
    <>
      {approvalSheetsQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>PLM 流程查询加载失败</AlertTitle>
          <AlertDescription>
            {approvalSheetsQuery.error instanceof Error
              ? approvalSheetsQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ResourceListPage
        title='PLM 流程查询'
        description='按审批单维度查看 PLM 业务的发起记录，详情页直接展示业务表单、流程轨迹和流程图回顾。'
        endpoint='/api/v1/plm/approval-sheets'
        searchPlaceholder='搜索流程标题、业务标题、单号或当前节点'
        search={search}
        navigate={navigate}
        columns={columns as never}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: 'PLM 审批单总量',
            value: String(pageData.total),
            hint: '当前登录人发起的 PLM 审批单总量。',
          },
          {
            label: '进行中',
            value: String(summary.running),
            hint: '仍在审批中的 PLM 单据数量。',
          },
          {
            label: '已完成',
            value: String(summary.completed),
            hint: '已经结束的 PLM 单据数量。',
          },
        ]}
        createAction={{
          label: '发起 PLM 申请',
          href: '/plm/start',
        }}
      />
    </>
  )
}

export function PLMApprovalSheetDetailPage({
  businessType,
  billId,
  backHref = '/workbench/todos/list',
  backLabel = '返回待办列表',
}: {
  businessType: 'PLM_ECR' | 'PLM_ECO' | 'PLM_MATERIAL'
  billId: string
  backHref?: '/workbench/todos/list'
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

export function PLMECRRequestBillDetailPage({
  billId: billIdProp,
}: {
  billId?: string
}) {
  const routeParams = ecrDetailRoute.useParams()
  const billId = billIdProp ?? routeParams.billId

  return (
    <PLMApprovalSheetDetailPage
      businessType='PLM_ECR'
      billId={billId}
      backHref='/workbench/todos/list'
      backLabel='返回待办列表'
    />
  )
}

export function PLMECOExecutionBillDetailPage({
  billId: billIdProp,
}: {
  billId?: string
}) {
  const routeParams = ecoDetailRoute.useParams()
  const billId = billIdProp ?? routeParams.billId

  return (
    <PLMApprovalSheetDetailPage
      businessType='PLM_ECO'
      billId={billId}
      backHref='/workbench/todos/list'
      backLabel='返回待办列表'
    />
  )
}

export function PLMMaterialChangeBillDetailPage({
  billId: billIdProp,
}: {
  billId?: string
}) {
  const routeParams = materialDetailRoute.useParams()
  const billId = billIdProp ?? routeParams.billId

  return (
    <PLMApprovalSheetDetailPage
      businessType='PLM_MATERIAL'
      billId={billId}
      backHref='/workbench/todos/list'
      backLabel='返回待办列表'
    />
  )
}
