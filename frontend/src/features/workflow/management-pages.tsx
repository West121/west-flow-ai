import { startTransition, useEffect, type ReactNode } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { useForm, useWatch } from 'react-hook-form'
import {
  ArrowLeft,
  FilePenLine,
  Loader2,
  Save,
} from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import {
  createApprovalOpinionConfig,
  createWorkflowBinding,
  getApprovalOpinionConfigDetail,
  getApprovalOpinionConfigFormOptions,
  getWorkflowBindingDetail,
  getWorkflowBindingFormOptions,
  getWorkflowInstanceDetail,
  getWorkflowOperationLogDetail,
  getWorkflowPublishRecordDetail,
  getWorkflowVersionDetail,
  listApprovalOpinionConfigs,
  listWorkflowBindings,
  listWorkflowInstances,
  listWorkflowOperationLogs,
  listWorkflowPublishRecords,
  listWorkflowVersions,
  updateApprovalOpinionConfig,
  updateWorkflowBinding,
  type ApprovalOpinionConfigDetail,
  type WorkflowBindingDetail,
} from '@/lib/api/workflow-management'
import { getApiErrorResponse } from '@/lib/api/client'
import { handleServerError } from '@/lib/handle-server-error'

function formatDateTime(value: string | null | undefined) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function DetailSkeleton() {
  return (
    <div className='grid gap-4 lg:grid-cols-2'>
      {Array.from({ length: 4 }).map((_, index) => (
        <Card key={index}>
          <CardHeader>
            <Skeleton className='h-6 w-36' />
            <Skeleton className='h-4 w-56' />
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            {Array.from({ length: 4 }).map((__, itemIndex) => (
              <Skeleton key={itemIndex} className='h-20 w-full' />
            ))}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function StatusBadge({ text }: { text: string }) {
  const variant =
    text === 'PUBLISHED' || text === 'ENABLED' || text === 'COMPLETED'
      ? 'secondary'
      : text === 'RUNNING'
        ? 'default'
        : 'outline'
  return <Badge variant={variant}>{text}</Badge>
}

function DetailSection({
  title,
  description,
  items,
}: {
  title: string
  description: string
  items: Array<{ label: string; value: string }>
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className='grid gap-3 sm:grid-cols-2'>
        {items.map((item) => (
          <div key={item.label} className='rounded-lg border p-4'>
            <p className='text-sm text-muted-foreground'>{item.label}</p>
            <p className='mt-2 text-sm font-medium break-all'>{item.value}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

function DetailPage({
  title,
  description,
  listHref,
  editHref,
  badges,
  sections,
  children,
}: {
  title: string
  description: string
  listHref: string
  editHref?: string
  badges: string[]
  sections: Array<{ title: string; description: string; items: Array<{ label: string; value: string }> }>
  children?: ReactNode
}) {
  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <>
          {editHref ? (
            <Button asChild>
              <Link to={editHref} search={{}}>
                <FilePenLine data-icon='inline-start' />
                编辑
              </Link>
            </Button>
          ) : null}
          <Button asChild variant='ghost'>
            <Link to={listHref} search={{}}>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='flex flex-wrap gap-2'>
        {badges.map((badge) => (
          <StatusBadge key={badge} text={badge} />
        ))}
      </div>

      <div className='grid gap-4 lg:grid-cols-2'>
        {sections.map((section) => (
          <DetailSection key={section.title} {...section} />
        ))}
      </div>
      {children}
    </PageShell>
  )
}

function WorkflowProcessLinkSection({
  processLinks,
  currentInstanceId,
}: {
  processLinks: Awaited<ReturnType<typeof getWorkflowInstanceDetail>>['processLinks']
  currentInstanceId: string
}) {
  if (!processLinks.length) {
    return null
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>主子流程关系</CardTitle>
        <CardDescription>
          这里按调用链展示主流程与子流程实例关系，便于监控联动终止和回查执行状态。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-3'>
        {processLinks.map((link) => (
          <div key={link.linkId} className='rounded-lg border p-4'>
            <div className='mb-2 flex flex-wrap items-center gap-2'>
              <StatusBadge text={link.status} />
              <Badge variant='outline'>{link.linkType}</Badge>
              {link.parentInstanceId === currentInstanceId ? <Badge variant='secondary'>当前主流程</Badge> : null}
              {link.childInstanceId === currentInstanceId ? <Badge variant='secondary'>当前子流程</Badge> : null}
            </div>
            <div className='grid gap-2 text-sm text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
              <div>主流程实例：{link.parentInstanceId}</div>
              <div>子流程实例：{link.childInstanceId}</div>
              <div>调用节点：{link.parentNodeId}</div>
              <div>子流程编码：{link.calledProcessKey}</div>
              <div>终止策略：{link.terminatePolicy || '-'}</div>
              <div>子流程完成策略：{link.childFinishPolicy || '-'}</div>
              <div>创建时间：{formatDateTime(link.createdAt)}</div>
              <div>结束时间：{formatDateTime(link.finishedAt)}</div>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

const bindingFormSchema = z.object({
  businessType: z.string().trim().min(1, '请选择业务类型'),
  sceneCode: z.string().trim().min(1, '请输入场景码'),
  processDefinitionId: z.string().trim().min(1, '请选择流程版本'),
  priority: z.string().trim().min(1, '请输入优先级').refine((value) => !Number.isNaN(Number(value)) && Number(value) >= 0, {
    message: '优先级不能小于 0',
  }),
  enabled: z.boolean(),
})

type BindingFormValues = z.infer<typeof bindingFormSchema>

const opinionConfigFormSchema = z.object({
  configCode: z.string().trim().min(2, '配置编码至少 2 个字符'),
  configName: z.string().trim().min(2, '配置名称至少 2 个字符'),
  enabled: z.boolean(),
  quickOpinionsText: z.string(),
  toolbarActions: z.array(z.string()),
  buttonStrategies: z.record(z.string(), z.boolean()),
  remark: z.string().max(500, '备注最多 500 个字符').optional(),
})

type OpinionConfigFormValues = z.infer<typeof opinionConfigFormSchema>

function toBindingFormValues(detail?: WorkflowBindingDetail): BindingFormValues {
  return {
    businessType: detail?.businessType ?? '',
    sceneCode: detail?.sceneCode ?? 'default',
    processDefinitionId: detail?.processDefinitionId ?? '',
    priority: `${detail?.priority ?? 10}`,
    enabled: detail?.enabled ?? true,
  }
}

function toOpinionConfigFormValues(detail?: ApprovalOpinionConfigDetail): OpinionConfigFormValues {
  return {
    configCode: detail?.configCode ?? '',
    configName: detail?.configName ?? '',
    enabled: detail?.status ? detail.status === 'ENABLED' : true,
    quickOpinionsText: detail?.quickOpinions?.join('\n') ?? '',
    toolbarActions: detail?.toolbarActions ?? [],
    buttonStrategies: Object.fromEntries(
      (detail?.buttonStrategies ?? []).map((item) => [item.actionType, item.requireOpinion])
    ),
    remark: detail?.remark ?? '',
  }
}

export function WorkflowVersionsListPage({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: unknown
}) {
  const query = useQuery({
    queryKey: ['workflow-management-versions', search],
    queryFn: () => listWorkflowVersions(search),
    placeholderData: (previous) => previous,
  })

  const columns: ColumnDef<Awaited<ReturnType<typeof listWorkflowVersions>>['records'][number]>[] = [
    {
      accessorKey: 'processName',
      header: '流程名称',
      cell: ({ row }) => (
        <div className='flex flex-col gap-1'>
          <Link
            to='/workflow/versions/$processDefinitionId'
            params={{ processDefinitionId: row.original.processDefinitionId }}
            className='font-medium text-primary hover:underline'
          >
            {row.original.processName}
          </Link>
          <span className='text-xs text-muted-foreground'>{row.original.processKey}</span>
        </div>
      ),
    },
    { accessorKey: 'version', header: '版本' },
    { accessorKey: 'category', header: '业务域', cell: ({ row }) => row.original.category || '-' },
    {
      accessorKey: 'latestVersion',
      header: '最新版本',
      cell: ({ row }) => <StatusBadge text={row.original.latestVersion ? '是' : '否'} />,
    },
    {
      accessorKey: 'publishedAt',
      header: '发布时间',
      cell: ({ row }) => formatDateTime(row.original.publishedAt),
    },
  ]

  return (
    <ResourceListPage
      title='流程版本'
      description='统一查看每个流程键的正式版本、引擎定义和发布元数据。'
      endpoint='/api/v1/workflow-management/versions/page'
      searchPlaceholder='搜索流程名称、流程编码或发布人'
      search={search}
      navigate={navigate as never}
      columns={columns}
      data={query.data?.records ?? []}
      summaries={[
        {
          label: '版本总数',
          value: `${query.data?.total ?? 0}`,
          hint: '只统计已发布版本，用于上线追溯和版本审计。',
        },
      ]}
    />
  )
}

export function WorkflowVersionDetailPage({ processDefinitionId }: { processDefinitionId: string }) {
  const query = useQuery({
    queryKey: ['workflow-management-version-detail', processDefinitionId],
    queryFn: () => getWorkflowVersionDetail(processDefinitionId),
  })

  if (query.isLoading) {
    return <DetailSkeleton />
  }

  if (query.isError || !query.data) {
    throw query.error
  }

  const detail = query.data
  return (
    <DetailPage
      title={`流程版本：${detail.processName}`}
      description='流程版本详情用于确认 BPMN 发布结果、引擎定义和版本归档。'
      listHref='/workflow/versions/list'
      badges={[detail.status, `V${detail.version}`]}
      sections={[
        {
          title: '版本信息',
          description: '平台版本号与发布主体。',
          items: [
            { label: '流程定义 ID', value: detail.processDefinitionId },
            { label: '流程编码', value: detail.processKey },
            { label: '流程名称', value: detail.processName },
            { label: '发布人', value: detail.publisherUserId || '-' },
            { label: '创建时间', value: formatDateTime(detail.createdAt) },
            { label: '更新时间', value: formatDateTime(detail.updatedAt) },
          ],
        },
        {
          title: '引擎信息',
          description: 'Flowable 部署元数据。',
          items: [
            { label: '部署 ID', value: detail.deploymentId || '-' },
            { label: 'Flowable 定义 ID', value: detail.flowableDefinitionId || '-' },
            { label: '业务域', value: detail.category || '-' },
            { label: 'BPMN 长度', value: `${detail.bpmnXml.length} 字符` },
          ],
        },
      ]}
    />
  )
}

export function WorkflowPublishRecordsListPage({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: unknown
}) {
  const query = useQuery({
    queryKey: ['workflow-management-publish-records', search],
    queryFn: () => listWorkflowPublishRecords(search),
    placeholderData: (previous) => previous,
  })

  const columns: ColumnDef<Awaited<ReturnType<typeof listWorkflowPublishRecords>>['records'][number]>[] = [
    {
      accessorKey: 'processName',
      header: '流程名称',
      cell: ({ row }) => (
        <Link
          to='/workflow/publish-records/$processDefinitionId'
          params={{ processDefinitionId: row.original.processDefinitionId }}
          className='font-medium text-primary hover:underline'
        >
          {row.original.processName}
        </Link>
      ),
    },
    { accessorKey: 'version', header: '版本' },
    { accessorKey: 'publisherUserId', header: '发布人', cell: ({ row }) => row.original.publisherUserId || '-' },
    { accessorKey: 'deploymentId', header: '部署 ID', cell: ({ row }) => row.original.deploymentId || '-' },
    { accessorKey: 'publishedAt', header: '发布时间', cell: ({ row }) => formatDateTime(row.original.publishedAt) },
  ]

  return (
    <ResourceListPage
      title='流程发布记录'
      description='发布记录页用于追踪每次正式发布的版本号、部署号和发布人。'
      endpoint='/api/v1/workflow-management/publish-records/page'
      searchPlaceholder='搜索流程名称、流程编码或部署 ID'
      search={search}
      navigate={navigate as never}
      columns={columns}
      data={query.data?.records ?? []}
      summaries={[
        {
          label: '发布记录数',
          value: `${query.data?.total ?? 0}`,
          hint: '每次正式发布都会沉淀一条可追踪的部署记录。',
        },
      ]}
    />
  )
}

export function WorkflowPublishRecordDetailPage({ processDefinitionId }: { processDefinitionId: string }) {
  const query = useQuery({
    queryKey: ['workflow-management-publish-record-detail', processDefinitionId],
    queryFn: () => getWorkflowPublishRecordDetail(processDefinitionId),
  })
  if (query.isLoading) return <DetailSkeleton />
  if (query.isError || !query.data) throw query.error
  const detail = query.data
  return (
    <DetailPage
      title={`发布记录：${detail.processName}`}
      description='发布记录详情用于核对流程版本上线时刻和部署产物。'
      listHref='/workflow/publish-records/list'
      badges={[`V${detail.version}`]}
      sections={[
        {
          title: '发布摘要',
          description: '这次发布的主数据。',
          items: [
            { label: '流程定义 ID', value: detail.processDefinitionId },
            { label: '流程编码', value: detail.processKey },
            { label: '流程名称', value: detail.processName },
            { label: '发布人', value: detail.publisherUserId || '-' },
            { label: '发布时间', value: formatDateTime(detail.createdAt) },
          ],
        },
        {
          title: '部署信息',
          description: '引擎部署元数据。',
          items: [
            { label: '部署 ID', value: detail.deploymentId || '-' },
            { label: 'Flowable 定义 ID', value: detail.flowableDefinitionId || '-' },
            { label: '业务域', value: detail.category || '-' },
            { label: 'BPMN 长度', value: `${detail.bpmnXml.length} 字符` },
          ],
        },
      ]}
    />
  )
}

export function WorkflowInstancesListPage({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: unknown
}) {
  const query = useQuery({
    queryKey: ['workflow-management-instances', search],
    queryFn: () => listWorkflowInstances(search),
    placeholderData: (previous) => previous,
  })
  const columns: ColumnDef<Awaited<ReturnType<typeof listWorkflowInstances>>['records'][number]>[] = [
    {
      accessorKey: 'processName',
      header: '流程',
      cell: ({ row }) => (
        <div className='flex flex-col gap-1'>
          <Link
            to='/workflow/instances/$instanceId'
            params={{ instanceId: row.original.processInstanceId }}
            className='font-medium text-primary hover:underline'
          >
            {row.original.processName || '未命名流程'}
          </Link>
          <span className='text-xs text-muted-foreground'>{row.original.processInstanceId}</span>
        </div>
      ),
    },
    { accessorKey: 'businessType', header: '业务类型', cell: ({ row }) => row.original.businessType || '-' },
    { accessorKey: 'businessId', header: '业务单据', cell: ({ row }) => row.original.businessId || '-' },
    { accessorKey: 'status', header: '状态', cell: ({ row }) => <StatusBadge text={row.original.status} /> },
    {
      accessorKey: 'currentTaskNames',
      header: '当前节点',
      cell: ({ row }) => (row.original.currentTaskNames.length ? row.original.currentTaskNames.join('、') : '-'),
    },
    { accessorKey: 'startedAt', header: '发起时间', cell: ({ row }) => formatDateTime(row.original.startedAt) },
  ]

  return (
    <ResourceListPage
      title='流程实例监控'
      description='实例监控页面向流程管理员，用于查看真实 Flowable 实例状态、当前节点和业务绑定。'
      endpoint='/api/v1/workflow-management/instances/page'
      searchPlaceholder='搜索流程名称、流程实例号、业务类型或业务单据'
      search={search}
      navigate={navigate as never}
      columns={columns}
      data={query.data?.records ?? []}
      summaries={[
        {
          label: '实例总数',
          value: `${query.data?.total ?? 0}`,
          hint: '这里聚合运行中和历史结束实例，用于统一监控。',
        },
      ]}
    />
  )
}

export function WorkflowInstanceDetailPage({ instanceId }: { instanceId: string }) {
  const query = useQuery({
    queryKey: ['workflow-management-instance-detail', instanceId],
    queryFn: () => getWorkflowInstanceDetail(instanceId),
  })
  if (query.isLoading) return <DetailSkeleton />
  if (query.isError || !query.data) throw query.error
  const detail = query.data
  return (
    <DetailPage
      title={`实例监控：${detail.processName || detail.processInstanceId}`}
      description='实例详情聚合了业务绑定、实例状态、当前节点和运行变量。'
      listHref='/workflow/instances/list'
      badges={[detail.status, detail.suspended ? 'SUSPENDED' : 'ACTIVE']}
      sections={[
        {
          title: '实例摘要',
          description: '流程实例运行关键信息。',
          items: [
            { label: '流程实例 ID', value: detail.processInstanceId },
            { label: '平台流程定义 ID', value: detail.processDefinitionId || '-' },
            { label: 'Flowable 定义 ID', value: detail.flowableDefinitionId || '-' },
            { label: '流程编码', value: detail.processKey || '-' },
            { label: '发起人', value: detail.startUserId || '-' },
            { label: '发起时间', value: formatDateTime(detail.startedAt) },
            { label: '结束时间', value: formatDateTime(detail.finishedAt) },
            { label: '当前节点', value: detail.currentTaskNames.length ? detail.currentTaskNames.join('、') : '-' },
          ],
        },
        {
          title: '业务绑定与变量',
          description: '业务主键和核心运行变量快照。',
          items: [
            { label: '业务类型', value: detail.businessType || '-' },
            { label: '业务单据', value: detail.businessId || '-' },
            { label: '变量数量', value: `${Object.keys(detail.variables ?? {}).length}` },
            { label: '变量预览', value: JSON.stringify(detail.variables ?? {}, null, 2) },
          ],
        },
      ]}
    >
      <WorkflowProcessLinkSection
        processLinks={detail.processLinks}
        currentInstanceId={detail.processInstanceId}
      />
    </DetailPage>
  )
}

export function WorkflowOperationLogsListPage({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: unknown
}) {
  const query = useQuery({
    queryKey: ['workflow-management-operation-logs', search],
    queryFn: () => listWorkflowOperationLogs(search),
    placeholderData: (previous) => previous,
  })
  const columns: ColumnDef<Awaited<ReturnType<typeof listWorkflowOperationLogs>>['records'][number]>[] = [
    {
      accessorKey: 'actionName',
      header: '动作',
      cell: ({ row }) => (
        <div className='flex flex-col gap-1'>
          <Link
            to='/workflow/operation-logs/$logId'
            params={{ logId: row.original.logId }}
            className='font-medium text-primary hover:underline'
          >
            {row.original.actionName}
          </Link>
          <span className='text-xs text-muted-foreground'>{row.original.actionType}</span>
        </div>
      ),
    },
    { accessorKey: 'processInstanceId', header: '流程实例', cell: ({ row }) => row.original.processInstanceId || '-' },
    { accessorKey: 'operatorUserId', header: '操作人', cell: ({ row }) => row.original.operatorUserId || '-' },
    { accessorKey: 'targetUserId', header: '目标人', cell: ({ row }) => row.original.targetUserId || '-' },
    { accessorKey: 'createdAt', header: '操作时间', cell: ({ row }) => formatDateTime(row.original.createdAt) },
  ]

  return (
    <ResourceListPage
      title='流程操作日志'
      description='流程操作日志记录发起、认领、转办、驳回、跳转等关键平台动作。'
      endpoint='/api/v1/workflow-management/operation-logs/page'
      searchPlaceholder='搜索动作、流程实例、业务单据或操作人'
      search={search}
      navigate={navigate as never}
      columns={columns}
      data={query.data?.records ?? []}
      summaries={[
        {
          label: '日志总数',
          value: `${query.data?.total ?? 0}`,
          hint: '日志由流程运行时和流程发布动作统一沉淀。',
        },
      ]}
    />
  )
}

export function WorkflowOperationLogDetailPage({ logId }: { logId: string }) {
  const query = useQuery({
    queryKey: ['workflow-management-operation-log-detail', logId],
    queryFn: () => getWorkflowOperationLogDetail(logId),
  })
  if (query.isLoading) return <DetailSkeleton />
  if (query.isError || !query.data) throw query.error
  const detail = query.data
  return (
    <DetailPage
      title={`流程操作日志：${detail.actionName}`}
      description='操作日志详情用于审计每次流程动作的上下文和影响目标。'
      listHref='/workflow/operation-logs/list'
      badges={[detail.actionType, detail.actionCategory || 'GENERAL']}
      sections={[
        {
          title: '动作上下文',
          description: '动作发生时的实例、任务和人员信息。',
          items: [
            { label: '日志 ID', value: detail.logId },
            { label: '流程实例 ID', value: detail.processInstanceId || '-' },
            { label: '流程定义 ID', value: detail.processDefinitionId || '-' },
            { label: '任务 ID', value: detail.taskId || '-' },
            { label: '节点 ID', value: detail.nodeId || '-' },
            { label: '操作人', value: detail.operatorUserId || '-' },
            { label: '目标人', value: detail.targetUserId || '-' },
            { label: '操作时间', value: formatDateTime(detail.createdAt) },
          ],
        },
        {
          title: '业务与明细',
          description: '业务绑定和动作补充参数。',
          items: [
            { label: '业务类型', value: detail.businessType || '-' },
            { label: '业务单据', value: detail.businessId || '-' },
            { label: '来源任务', value: detail.sourceTaskId || '-' },
            { label: '目标任务', value: detail.targetTaskId || '-' },
            { label: '审批意见', value: detail.commentText || '-' },
            { label: '明细 JSON', value: JSON.stringify(detail.details ?? {}, null, 2) },
          ],
        },
      ]}
    />
  )
}

export function WorkflowBindingsListPage({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: unknown
}) {
  const query = useQuery({
    queryKey: ['workflow-management-bindings', search],
    queryFn: () => listWorkflowBindings(search),
    placeholderData: (previous) => previous,
  })
  const columns: ColumnDef<Awaited<ReturnType<typeof listWorkflowBindings>>['records'][number]>[] = [
    {
      accessorKey: 'businessType',
      header: '业务类型',
      cell: ({ row }) => (
        <div className='flex flex-col gap-1'>
          <Link
            to='/workflow/bindings/$bindingId'
            params={{ bindingId: row.original.bindingId }}
            className='font-medium text-primary hover:underline'
          >
            {row.original.businessType}
          </Link>
          <span className='text-xs text-muted-foreground'>{row.original.sceneCode}</span>
        </div>
      ),
    },
    { accessorKey: 'processName', header: '流程版本' },
    { accessorKey: 'priority', header: '优先级' },
    {
      accessorKey: 'enabled',
      header: '状态',
      cell: ({ row }) => <StatusBadge text={row.original.enabled ? 'ENABLED' : 'DISABLED'} />,
    },
    { accessorKey: 'updatedAt', header: '更新时间', cell: ({ row }) => formatDateTime(row.original.updatedAt) },
  ]

  return (
    <ResourceListPage
      title='业务流程绑定'
      description='业务流程绑定用于决定业务发起时到底命中哪个流程定义版本。'
      endpoint='/api/v1/workflow-management/bindings/page'
      searchPlaceholder='搜索业务类型、场景码、流程编码或流程版本'
      search={search}
      navigate={navigate as never}
      columns={columns}
      data={query.data?.records ?? []}
      summaries={[
        {
          label: '绑定总数',
          value: `${query.data?.total ?? 0}`,
          hint: '同一业务类型和场景码只允许维护一条有效绑定。',
        },
      ]}
      createAction={{ label: '新建绑定', href: '/workflow/bindings/create' }}
    />
  )
}

export function WorkflowBindingDetailPage({ bindingId }: { bindingId: string }) {
  const query = useQuery({
    queryKey: ['workflow-management-binding-detail', bindingId],
    queryFn: () => getWorkflowBindingDetail(bindingId),
  })
  if (query.isLoading) return <DetailSkeleton />
  if (query.isError || !query.data) throw query.error
  const detail = query.data
  return (
    <DetailPage
      title={`业务流程绑定：${detail.businessType}`}
      description='绑定详情用于核对业务场景与具体流程版本的关系。'
      listHref='/workflow/bindings/list'
      editHref='/workflow/bindings/$bindingId/edit'
      badges={[detail.enabled ? 'ENABLED' : 'DISABLED']}
      sections={[
        {
          title: '绑定信息',
          description: '业务入口与流程版本关系。',
          items: [
            { label: '绑定 ID', value: detail.bindingId },
            { label: '业务类型', value: detail.businessType },
            { label: '场景码', value: detail.sceneCode },
            { label: '流程编码', value: detail.processKey },
            { label: '流程版本', value: detail.processName },
            { label: '流程定义 ID', value: detail.processDefinitionId },
            { label: '优先级', value: `${detail.priority}` },
            { label: '更新时间', value: formatDateTime(detail.updatedAt) },
          ],
        },
      ]}
    />
  )
}

function WorkflowBindingFormPage({
  mode,
  bindingId,
}: {
  mode: 'create' | 'edit'
  bindingId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['workflow-management-binding-form-detail', bindingId],
    queryFn: () => getWorkflowBindingDetail(bindingId!),
    enabled: mode === 'edit' && Boolean(bindingId),
  })
  const optionsQuery = useQuery({
    queryKey: ['workflow-management-binding-options'],
    queryFn: getWorkflowBindingFormOptions,
  })

  const form = useForm<BindingFormValues>({
    resolver: zodResolver(bindingFormSchema),
    defaultValues: toBindingFormValues(),
  })

  useEffect(() => {
    if (detailQuery.data) {
      startTransition(() => {
        form.reset(toBindingFormValues(detailQuery.data))
      })
    }
  }, [detailQuery.data, form])

  const mutation = useMutation({
    mutationFn: async (values: BindingFormValues) => {
      const definition = optionsQuery.data?.processDefinitions.find(
        (item) => item.processDefinitionId === values.processDefinitionId
      )
      const payload = {
        businessType: values.businessType,
        sceneCode: values.sceneCode,
        processDefinitionId: values.processDefinitionId,
        processKey: definition?.processKey ?? '',
        enabled: values.enabled,
        priority: Number(values.priority),
      }
      return mode === 'create'
        ? createWorkflowBinding(payload)
        : updateWorkflowBinding(bindingId!, payload)
    },
    onSuccess: async (result) => {
      toast.success(mode === 'create' ? '业务流程绑定已创建' : '业务流程绑定已更新')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['workflow-management-bindings'] }),
        queryClient.invalidateQueries({ queryKey: ['workflow-management-binding-detail'] }),
      ])
      navigate({
        to: '/workflow/bindings/$bindingId',
        params: { bindingId: result.bindingId },
      })
    },
    onError: (error) => {
      const apiError = getApiErrorResponse(error)
      apiError?.fieldErrors?.forEach((field) => {
        if (field.field === 'businessType' || field.field === 'sceneCode' || field.field === 'processDefinitionId') {
          form.setError(field.field, { type: 'server', message: field.message })
        }
        if (field.field === 'priority') {
          form.setError('priority', { type: 'server', message: field.message })
        }
      })
      handleServerError(error)
    },
  })

  return (
    <PageShell
      title={mode === 'create' ? '新建业务流程绑定' : '编辑业务流程绑定'}
      description='每个业务类型和场景码都需要显式绑定到一个已发布流程版本。'
      actions={
        <>
          <Button onClick={form.handleSubmit((values) => mutation.mutate(values))} disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className='animate-spin' /> : <Save data-icon='inline-start' />}
            保存并返回详情
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/workflow/bindings/list' search={{}}>
              取消返回列表
            </Link>
          </Button>
        </>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>绑定表单</CardTitle>
          <CardDescription>流程中心发起和业务发起都会基于这里的绑定关系解析流程版本。</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form className='grid gap-6 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='businessType'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>业务类型</FormLabel>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder='请选择业务类型' />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {(optionsQuery.data?.businessTypes ?? []).map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='sceneCode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>场景码</FormLabel>
                    <FormControl>
                      <Input placeholder='例如 default、after-sale' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='processDefinitionId'
                render={({ field }) => (
                  <FormItem className='md:col-span-2'>
                    <FormLabel>流程版本</FormLabel>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder='请选择已发布流程版本' />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {(optionsQuery.data?.processDefinitions ?? []).map((item) => (
                          <SelectItem key={item.processDefinitionId} value={item.processDefinitionId}>
                            {item.processName} ({item.processKey} / V{item.version})
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='priority'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>优先级</FormLabel>
                    <FormControl>
                      <Input type='number' min={0} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='enabled'
                render={({ field }) => (
                  <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4'>
                    <div className='space-y-1'>
                      <FormLabel>启用状态</FormLabel>
                      <p className='text-sm text-muted-foreground'>关闭后该绑定不会参与业务发起解析。</p>
                    </div>
                    <FormControl>
                      <Switch checked={field.value} onCheckedChange={field.onChange} />
                    </FormControl>
                  </FormItem>
                )}
              />
            </form>
          </Form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function WorkflowBindingCreatePage() {
  return <WorkflowBindingFormPage mode='create' />
}

export function WorkflowBindingEditPage({ bindingId }: { bindingId: string }) {
  return <WorkflowBindingFormPage mode='edit' bindingId={bindingId} />
}

export function ApprovalOpinionConfigsListPage({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: unknown
}) {
  const query = useQuery({
    queryKey: ['workflow-management-opinion-configs', search],
    queryFn: () => listApprovalOpinionConfigs(search),
    placeholderData: (previous) => previous,
  })
  const columns: ColumnDef<Awaited<ReturnType<typeof listApprovalOpinionConfigs>>['records'][number]>[] = [
    {
      accessorKey: 'configName',
      header: '配置名称',
      cell: ({ row }) => (
        <div className='flex flex-col gap-1'>
          <Link
            to='/workflow/opinion-configs/$configId'
            params={{ configId: row.original.configId }}
            className='font-medium text-primary hover:underline'
          >
            {row.original.configName}
          </Link>
          <span className='text-xs text-muted-foreground'>{row.original.configCode}</span>
        </div>
      ),
    },
    { accessorKey: 'quickOpinionCount', header: '快捷意见数' },
    { accessorKey: 'status', header: '状态', cell: ({ row }) => <StatusBadge text={row.original.status} /> },
    { accessorKey: 'updatedAt', header: '更新时间', cell: ({ row }) => formatDateTime(row.original.updatedAt) },
  ]

  return (
    <ResourceListPage
      title='审批意见配置'
      description='审批意见配置负责快捷意见模板、意见工具栏和按钮必填策略。'
      endpoint='/api/v1/workflow-management/opinion-configs/page'
      searchPlaceholder='搜索配置编码、配置名称或备注'
      search={search}
      navigate={navigate as never}
      columns={columns}
      data={query.data?.records ?? []}
      summaries={[
        {
          label: '配置总数',
          value: `${query.data?.total ?? 0}`,
          hint: '运行态意见录入会参考这里的模板和按钮规则。',
        },
      ]}
      createAction={{ label: '新建配置', href: '/workflow/opinion-configs/create' }}
    />
  )
}

export function ApprovalOpinionConfigDetailPage({ configId }: { configId: string }) {
  const query = useQuery({
    queryKey: ['workflow-management-opinion-config-detail', configId],
    queryFn: () => getApprovalOpinionConfigDetail(configId),
  })
  if (query.isLoading) return <DetailSkeleton />
  if (query.isError || !query.data) throw query.error
  const detail = query.data
  return (
    <DetailPage
      title={`审批意见配置：${detail.configName}`}
      description='审批意见配置详情用于确认快捷意见、工具栏和按钮必填策略。'
      listHref='/workflow/opinion-configs/list'
      editHref='/workflow/opinion-configs/$configId/edit'
      badges={[detail.status]}
      sections={[
        {
          title: '配置摘要',
          description: '基础标识和状态。',
          items: [
            { label: '配置 ID', value: detail.configId },
            { label: '配置编码', value: detail.configCode },
            { label: '配置名称', value: detail.configName },
            { label: '状态', value: detail.status },
            { label: '快捷意见', value: detail.quickOpinions.join('、') || '-' },
            { label: '工具栏动作', value: detail.toolbarActions.join('、') || '-' },
            { label: '更新时间', value: formatDateTime(detail.updatedAt) },
          ],
        },
      ]}
    />
  )
}

function ApprovalOpinionConfigFormPage({
  mode,
  configId,
}: {
  mode: 'create' | 'edit'
  configId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['workflow-management-opinion-config-form-detail', configId],
    queryFn: () => getApprovalOpinionConfigDetail(configId!),
    enabled: mode === 'edit' && Boolean(configId),
  })
  const optionsQuery = useQuery({
    queryKey: ['workflow-management-opinion-config-options'],
    queryFn: getApprovalOpinionConfigFormOptions,
  })
  const form = useForm<OpinionConfigFormValues>({
    resolver: zodResolver(opinionConfigFormSchema),
    defaultValues: toOpinionConfigFormValues(),
  })
  const toolbarActions = useWatch({
    control: form.control,
    name: 'toolbarActions',
    defaultValue: [],
  })
  const buttonStrategies = useWatch({
    control: form.control,
    name: 'buttonStrategies',
    defaultValue: {},
  })

  useEffect(() => {
    if (detailQuery.data) {
      startTransition(() => {
        form.reset(toOpinionConfigFormValues(detailQuery.data))
      })
    }
  }, [detailQuery.data, form])

  const mutation = useMutation({
    mutationFn: async (values: OpinionConfigFormValues) => {
      const payload = {
        configCode: values.configCode,
        configName: values.configName,
        enabled: values.enabled,
        quickOpinions: values.quickOpinionsText
          .split('\n')
          .map((item) => item.trim())
          .filter(Boolean),
        toolbarActions: values.toolbarActions,
        buttonStrategies: (optionsQuery.data?.actionTypes ?? []).map((item) => ({
          actionType: item.value,
          requireOpinion: Boolean(values.buttonStrategies[item.value]),
        })),
        remark: values.remark,
      }
      return mode === 'create'
        ? createApprovalOpinionConfig(payload)
        : updateApprovalOpinionConfig(configId!, payload)
    },
    onSuccess: async (result) => {
      toast.success(mode === 'create' ? '审批意见配置已创建' : '审批意见配置已更新')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['workflow-management-opinion-configs'] }),
        queryClient.invalidateQueries({ queryKey: ['workflow-management-opinion-config-detail'] }),
      ])
      navigate({
        to: '/workflow/opinion-configs/$configId',
        params: { configId: result.configId },
      })
    },
    onError: (error) => {
      const apiError = getApiErrorResponse(error)
      apiError?.fieldErrors?.forEach((field) => {
        if (field.field === 'configCode' || field.field === 'configName' || field.field === 'remark') {
          form.setError(field.field, { type: 'server', message: field.message })
        }
      })
      handleServerError(error)
    },
  })

  return (
    <PageShell
      title={mode === 'create' ? '新建审批意见配置' : '编辑审批意见配置'}
      description='审批意见配置决定快捷意见、工具栏功能和按钮是否强制填写意见。'
      actions={
        <>
          <Button onClick={form.handleSubmit((values) => mutation.mutate(values))} disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className='animate-spin' /> : <Save data-icon='inline-start' />}
            保存并返回详情
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/workflow/opinion-configs/list' search={{}}>
              取消返回列表
            </Link>
          </Button>
        </>
      }
    >
      <Form {...form}>
        <form className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
          <div className='flex flex-col gap-4'>
            <Card>
              <CardHeader>
                <CardTitle>基础信息</CardTitle>
                <CardDescription>配置编码与启停状态。</CardDescription>
              </CardHeader>
              <CardContent className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='configCode'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>配置编码</FormLabel>
                      <FormControl>
                        <Input placeholder='DEFAULT_APPROVAL' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='configName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>配置名称</FormLabel>
                      <FormControl>
                        <Input placeholder='默认审批意见配置' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='remark'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>备注</FormLabel>
                      <FormControl>
                        <Textarea rows={3} placeholder='说明当前配置适用的流程范围' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='enabled'
                  render={({ field }) => (
                    <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4 md:col-span-2'>
                      <div className='space-y-1'>
                        <FormLabel>启用状态</FormLabel>
                        <p className='text-sm text-muted-foreground'>关闭后该配置不会在流程管理后台中作为默认配置使用。</p>
                      </div>
                      <FormControl>
                        <Switch checked={field.value} onCheckedChange={field.onChange} />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>快捷意见与工具栏</CardTitle>
                <CardDescription>快捷意见按行录入，工具栏功能按需勾选。</CardDescription>
              </CardHeader>
              <CardContent className='grid gap-4'>
                <FormField
                  control={form.control}
                  name='quickOpinionsText'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>快捷意见</FormLabel>
                      <FormControl>
                        <Textarea rows={6} placeholder={'同意\n请补充材料后再提交'} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='toolbarActions'
                  render={() => (
                    <FormItem>
                      <FormLabel>工具栏动作</FormLabel>
                      <div className='grid gap-3 md:grid-cols-2'>
                        {(optionsQuery.data?.toolbarActions ?? []).map((item) => (
                          <label key={item.value} className='flex items-center gap-3 rounded-lg border p-3 text-sm'>
                            <Checkbox
                              checked={toolbarActions.includes(item.value)}
                              onCheckedChange={(checked) => {
                                const current = form.getValues('toolbarActions')
                                form.setValue(
                                  'toolbarActions',
                                  checked
                                    ? [...current, item.value]
                                    : current.filter((value) => value !== item.value),
                                  { shouldDirty: true }
                                )
                              }}
                            />
                            <span>{item.label}</span>
                          </label>
                        ))}
                      </div>
                    </FormItem>
                  )}
                />
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>按钮策略</CardTitle>
              <CardDescription>按动作控制是否强制填写审批意见。</CardDescription>
            </CardHeader>
            <CardContent className='grid gap-3'>
              {(optionsQuery.data?.actionTypes ?? []).map((item) => (
                <div key={item.value} className='flex items-center justify-between rounded-lg border p-3'>
                  <div>
                    <p className='text-sm font-medium'>{item.label}</p>
                    <p className='text-xs text-muted-foreground'>{item.value}</p>
                  </div>
                  <Switch
                    checked={Boolean(buttonStrategies[item.value])}
                    onCheckedChange={(checked) => {
                      form.setValue(
                        'buttonStrategies',
                        {
                          ...form.getValues('buttonStrategies'),
                          [item.value]: checked,
                        },
                        { shouldDirty: true }
                      )
                    }}
                  />
                </div>
              ))}
            </CardContent>
          </Card>
        </form>
      </Form>
    </PageShell>
  )
}

export function ApprovalOpinionConfigCreatePage() {
  return <ApprovalOpinionConfigFormPage mode='create' />
}

export function ApprovalOpinionConfigEditPage({ configId }: { configId: string }) {
  return <ApprovalOpinionConfigFormPage mode='edit' configId={configId} />
}
