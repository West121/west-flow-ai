import { useEffect } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, type Resolver } from 'react-hook-form'
import {
  ArrowLeft,
  ChevronDown,
  Loader2,
  RefreshCw,
  Save,
} from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import {
  createAiAgent,
  createAiMcp,
  createAiSkill,
  createAiTool,
  getAiAgentDetail,
  getAiAgentFormOptions,
  getAiMcpDetail,
  getAiMcpDiagnosticDetail,
  getAiMcpFormOptions,
  getAiSkillDetail,
  getAiSkillFormOptions,
  getAiToolDetail,
  getAiToolFormOptions,
  listAiAgents,
  listAiMcps,
  listAiSkills,
  listAiTools,
  updateAiAgent,
  updateAiMcp,
  updateAiSkill,
  updateAiTool,
  recheckAiMcpDiagnostic,
  type AiAgentDetail,
  type AiAgentRecord,
  type AiMcpDetail,
  type AiMcpDiagnosticStep,
  type AiMcpRecord,
  type AiRegistryOption,
  type AiRegistryStatus,
  type AiSkillDetail,
  type AiSkillRecord,
  type AiToolDetail,
  type AiToolRecord,
  type SaveAiAgentPayload,
  type SaveAiMcpPayload,
  type SaveAiSkillPayload,
  type SaveAiToolPayload,
} from '@/lib/api/ai-admin'
import {
  AiInfoCard,
  AiKeyValueGrid,
  AiJsonBlock,
  AiObservabilityGrid,
  AiPageErrorState,
  AiRegistryLinkList,
  AiStatusBadge,
} from './shared'
import { formatDateTime, renderTags } from './shared-formatters'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import { handleServerError } from '@/lib/handle-server-error'

type PageSearchProps = {
  search: ListQuerySearch
  navigate: NavigateFn
}

function resolveStatusLabel(status: AiRegistryStatus) {
  return status === 'ENABLED' ? '启用' : '停用'
}

function resolveStatusVariant(status: AiRegistryStatus) {
  return status === 'ENABLED' ? 'secondary' : 'outline'
}

function diagnosticStepVariant(status: AiMcpDiagnosticStep['status']) {
  switch (status) {
    case 'PASS':
    case 'INFO':
      return 'secondary' as const
    case 'FAIL':
      return 'destructive' as const
    default:
      return 'outline' as const
  }
}

function diagnosticStepLabel(status: AiMcpDiagnosticStep['status']) {
  switch (status) {
    case 'PASS':
      return '通过'
    case 'FAIL':
      return '失败'
    case 'WARN':
      return '警告'
    default:
      return '信息'
  }
}

function emptyPage(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

function listStatusSummary(search: ListQuerySearch, total: number) {
  return [
    {
      label: '总条数',
      value: `${total}`,
      hint: `当前查询页：${search.page} / ${search.pageSize}`,
    },
    {
      label: '筛选项',
      value: `${search.filters?.length ?? 0}`,
      hint: '支持按状态、关键字和排序条件组合查询',
    },
    {
      label: '分组项',
      value: `${search.groups?.length ?? 0}`,
      hint: '列表协议已预留分组能力',
    },
  ]
}

function RegistryDetailPage({
  title,
  description,
  detail: _detail,
  backHref,
  actions,
  children,
}: {
  title: string
  description: string
  detail: AiAgentDetail | AiToolDetail | AiMcpDetail | AiSkillDetail
  backHref: string
  actions?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <>
          <Button asChild variant='outline'>
            <Link to={backHref} search={{}}>
              <ArrowLeft />
              返回列表
            </Link>
          </Button>
          {actions}
        </>
      }
    >
      <div className='space-y-6'>
        {children}
      </div>
    </PageShell>
  )
}

function RegistryFormShell({
  title,
  description,
  backHref,
  submitLabel,
  loading,
  onSubmit,
  children,
}: {
  title: string
  description: string
  backHref: string
  submitLabel: string
  loading: boolean
  onSubmit: () => void
  children: React.ReactNode
}) {
  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <Button asChild variant='outline'>
          <Link to={backHref} search={{}}>
            <ArrowLeft />
            返回列表
          </Link>
        </Button>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent>
          {children}
          <div className='mt-6 flex items-center gap-2'>
            <Button onClick={onSubmit} disabled={loading}>
              {loading ? <Loader2 className='animate-spin' /> : <Save />}
              {submitLabel}
            </Button>
          </div>
        </CardContent>
      </Card>
    </PageShell>
  )
}

function parseMetadataJson(value: string) {
  try {
    return value.trim() ? JSON.stringify(JSON.parse(value), null, 2) : '{}'
  } catch {
    return value
  }
}

function buildOptionsMap(options: AiRegistryOption[]) {
  return options.map((option) => (
    <SelectItem key={option.value} value={option.value}>
      {option.label}
    </SelectItem>
  ))
}

const agentFormSchema = z.object({
  agentCode: z.string().trim().min(2, '编码至少 2 个字符'),
  agentName: z.string().trim().min(2, '名称至少 2 个字符'),
  capabilityCode: z.string().trim().min(1, '请选择能力码'),
  routeMode: z.string().trim().min(1, '请选择路由模式'),
  supervisor: z.boolean(),
  priority: z.coerce.number().int().min(0, '优先级不能小于 0'),
  contextTagsText: z.string().trim().default(''),
  systemPrompt: z.string().trim().min(2, '系统提示词至少 2 个字符'),
  metadataJson: z.string().trim().default('{}'),
  enabled: z.boolean(),
})

type AgentFormValues = z.infer<typeof agentFormSchema>

function agentToPayload(values: AgentFormValues): SaveAiAgentPayload {
  return {
    agentCode: values.agentCode,
    agentName: values.agentName,
    capabilityCode: values.capabilityCode,
    routeMode: values.routeMode,
    supervisor: values.supervisor,
    priority: values.priority,
    contextTags: values.contextTagsText
      .split(/[,\n]/)
      .map((item) => item.trim())
      .filter(Boolean),
    systemPrompt: values.systemPrompt,
    metadataJson: parseMetadataJson(values.metadataJson),
    enabled: values.enabled,
  }
}

function agentToFormValues(detail?: AiAgentDetail): AgentFormValues {
  return {
    agentCode: detail?.agentCode ?? '',
    agentName: detail?.agentName ?? '',
    capabilityCode: detail?.capabilityCode ?? '',
    routeMode: detail?.routeMode ?? 'SUPERVISOR',
    supervisor: detail?.supervisor ?? true,
    priority: detail?.priority ?? 100,
    contextTagsText: detail?.contextTags?.join(', ') ?? '',
    systemPrompt: detail?.systemPrompt ?? '',
    metadataJson: detail?.metadataJson ?? '{}',
    enabled: detail?.status ? detail.status === 'ENABLED' : true,
  }
}

const toolFormSchema = z.object({
  toolCode: z.string().trim().min(2, '编码至少 2 个字符'),
  toolName: z.string().trim().min(2, '名称至少 2 个字符'),
  toolCategory: z.string().trim().min(1, '请选择工具分类'),
  actionMode: z.string().trim().min(1, '请选择动作模式'),
  requiredCapabilityCode: z.string().trim().optional(),
  metadataJson: z.string().trim().default('{}'),
  enabled: z.boolean(),
})

type ToolFormValues = z.infer<typeof toolFormSchema>

function toolToPayload(values: ToolFormValues): SaveAiToolPayload {
  return {
    toolCode: values.toolCode,
    toolName: values.toolName,
    toolCategory: values.toolCategory,
    actionMode: values.actionMode as SaveAiToolPayload['actionMode'],
    requiredCapabilityCode: values.requiredCapabilityCode?.trim() || null,
    metadataJson: parseMetadataJson(values.metadataJson),
    enabled: values.enabled,
  }
}

function toolToFormValues(detail?: AiToolDetail): ToolFormValues {
  return {
    toolCode: detail?.toolCode ?? '',
    toolName: detail?.toolName ?? '',
    toolCategory: detail?.toolCategory ?? '',
    actionMode: detail?.actionMode ?? 'READ',
    requiredCapabilityCode: detail?.requiredCapabilityCode ?? '',
    metadataJson: detail?.metadataJson ?? '{}',
    enabled: detail?.status ? detail.status === 'ENABLED' : true,
  }
}

const mcpFormSchema = z.object({
  mcpCode: z.string().trim().min(2, '编码至少 2 个字符'),
  mcpName: z.string().trim().min(2, '名称至少 2 个字符'),
  endpointUrl: z.string().trim().optional(),
  transportType: z.string().trim().min(1, '请选择传输类型'),
  requiredCapabilityCode: z.string().trim().optional(),
  metadataJson: z.string().trim().default('{}'),
  enabled: z.boolean(),
})

type McpFormValues = z.infer<typeof mcpFormSchema>

function mcpToPayload(values: McpFormValues): SaveAiMcpPayload {
  return {
    mcpCode: values.mcpCode,
    mcpName: values.mcpName,
    endpointUrl: values.endpointUrl?.trim() || null,
    transportType: values.transportType as SaveAiMcpPayload['transportType'],
    requiredCapabilityCode: values.requiredCapabilityCode?.trim() || null,
    metadataJson: parseMetadataJson(values.metadataJson),
    enabled: values.enabled,
  }
}

function mcpToFormValues(detail?: AiMcpDetail): McpFormValues {
  return {
    mcpCode: detail?.mcpCode ?? '',
    mcpName: detail?.mcpName ?? '',
    endpointUrl: detail?.endpointUrl ?? '',
    transportType: detail?.transportType ?? 'INTERNAL',
    requiredCapabilityCode: detail?.requiredCapabilityCode ?? '',
    metadataJson: detail?.metadataJson ?? '{}',
    enabled: detail?.status ? detail.status === 'ENABLED' : true,
  }
}

const skillFormSchema = z.object({
  skillCode: z.string().trim().min(2, '编码至少 2 个字符'),
  skillName: z.string().trim().min(2, '名称至少 2 个字符'),
  skillPath: z.string().trim().optional(),
  requiredCapabilityCode: z.string().trim().optional(),
  metadataJson: z.string().trim().default('{}'),
  enabled: z.boolean(),
})

type SkillFormValues = z.infer<typeof skillFormSchema>

function skillToPayload(values: SkillFormValues): SaveAiSkillPayload {
  return {
    skillCode: values.skillCode,
    skillName: values.skillName,
    skillPath: values.skillPath?.trim() || null,
    requiredCapabilityCode: values.requiredCapabilityCode?.trim() || null,
    metadataJson: parseMetadataJson(values.metadataJson),
    enabled: values.enabled,
  }
}

function skillToFormValues(detail?: AiSkillDetail): SkillFormValues {
  return {
    skillCode: detail?.skillCode ?? '',
    skillName: detail?.skillName ?? '',
    skillPath: detail?.skillPath ?? '',
    requiredCapabilityCode: detail?.requiredCapabilityCode ?? '',
    metadataJson: detail?.metadataJson ?? '{}',
    enabled: detail?.status ? detail.status === 'ENABLED' : true,
  }
}

function registryLoadingPage(title: string, description: string) {
  return (
    <PageShell title={title} description={description}>
      <div className='space-y-4'>
        <Skeleton className='h-24 w-full' />
        <Skeleton className='h-[480px] w-full' />
      </div>
    </PageShell>
  )
}

function registryPageError(
  title: string,
  description: string,
  listHref: string
) {
  return (
    <AiPageErrorState title={title} description={description} listHref={listHref} />
  )
}

// ========================= Agent =========================

export function AiAgentListPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'agents', search],
    queryFn: () => listAiAgents(search),
  })

  const data = query.data ?? emptyPage(search)

  const columns: ColumnDef<AiAgentRecord, unknown>[] = [
    {
      accessorKey: 'agentCode',
      header: '编码',
    },
    {
      accessorKey: 'agentName',
      header: '名称',
    },
    {
      accessorKey: 'capabilityCode',
      header: '能力码',
    },
    {
      accessorKey: 'routeMode',
      header: '路由模式',
    },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => (
        <Badge variant={resolveStatusVariant(row.original.status)}>
          {resolveStatusLabel(row.original.status)}
        </Badge>
      ),
    },
    {
      accessorKey: 'updatedAt',
      header: '更新时间',
      cell: ({ row }) => formatDateTime(row.original.updatedAt),
    },
  ]

  return (
    <ResourceListPage
      title='Agent 注册表'
      description='统一维护 AI 智能体注册配置，支持分页、查询、筛选、排序和分组。'
      endpoint='/system/ai/agents/page'
      searchPlaceholder='搜索智能体编码、名称或能力码'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listStatusSummary(search, data.total)}
      createAction={{ label: '新建 Agent', href: '/system/ai/agents/create' }}
    />
  )
}

export function AiAgentDetailPage({ agentId }: { agentId: string }) {
  const query = useQuery({
    queryKey: ['ai-admin', 'agents', agentId],
    queryFn: () => getAiAgentDetail(agentId),
  })

  if (query.isLoading) {
    return registryLoadingPage('Agent 注册表', '查看 Agent 详情与注册信息。')
  }

  if (query.isError || !query.data) {
    return registryPageError('Agent 注册表', '查看 Agent 详情与注册信息。', '/system/ai/agents/list')
  }

  const detail = query.data

  return (
    <RegistryDetailPage
      title='Agent 注册表'
      description='查看智能体注册、路由模式、上下文标签和系统提示词。'
      detail={detail}
      backHref='/system/ai/agents/list'
    >
      <AiInfoCard title='基础信息' description='Agent 的核心注册字段。'>
        <AiKeyValueGrid
          items={[
            { label: '编码', value: detail.agentCode },
            { label: '名称', value: detail.agentName },
            { label: '能力码', value: detail.capabilityCode },
            { label: '路由模式', value: detail.routeMode },
            { label: '优先级', value: detail.priority },
            { label: '状态', value: <AiStatusBadge label={resolveStatusLabel(detail.status)} variant={resolveStatusVariant(detail.status)} /> },
            { label: 'Supervisor', value: detail.supervisor ? '是' : '否' },
            { label: '上下文标签', value: renderTags(detail.contextTags) },
            { label: '创建时间', value: formatDateTime(detail.createdAt) },
            { label: '更新时间', value: formatDateTime(detail.updatedAt) },
          ]}
        />
      </AiInfoCard>
      <AiInfoCard title='系统提示词' description='注册表对应的 Agent 系统提示词。'>
        <pre className='whitespace-pre-wrap rounded-lg border bg-muted/20 p-4 text-sm leading-6'>
          {detail.systemPrompt || '-'}
        </pre>
      </AiInfoCard>
      <AiInfoCard title='可观测性' description='查看该 Agent 实际参与 ToolCall 的运行态摘要。'>
        <AiObservabilityGrid value={detail.observability} />
      </AiInfoCard>
      <AiInfoCard title='关联资源' description='按能力码关联当前 Agent 可调度的 Tool / Skill / MCP。'>
        <div className='grid gap-4 md:grid-cols-3'>
          <AiRegistryLinkList title='Tool' links={detail.linkedTools} />
          <AiRegistryLinkList title='Skill' links={detail.linkedSkills} />
          <AiRegistryLinkList title='MCP' links={detail.linkedMcps} />
        </div>
      </AiInfoCard>
      <AiInfoCard title='元数据' description='保留扩展配置，方便后续技能编排。'>
        <AiJsonBlock value={detail.metadataJson} />
      </AiInfoCard>
    </RegistryDetailPage>
  )
}

export function AiAgentCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'agents', 'options'],
    queryFn: getAiAgentFormOptions,
  })

  const form = useForm<AgentFormValues>({
    resolver: zodResolver(agentFormSchema) as Resolver<AgentFormValues>,
    defaultValues: agentToFormValues(),
  })

  const mutation = useMutation({
    mutationFn: (values: AgentFormValues) => createAiAgent(agentToPayload(values)),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'agents'] })
      toast.success('Agent 已创建')
      navigate({
        to: '/system/ai/agents/$agentId',
        params: { agentId: result.agentId },
      })
    },
    onError: (error) => handleServerError(error),
  })

  if (optionsQuery.isLoading) {
    return registryLoadingPage('新建 Agent', '创建新的 AI 智能体注册信息。')
  }

  if (optionsQuery.isError || !optionsQuery.data) {
    return registryPageError('新建 Agent', '创建新的 AI 智能体注册信息。', '/system/ai/agents/list')
  }

  const options = optionsQuery.data

  return (
    <RegistryFormShell
      title='新建 Agent'
      description='维护智能体注册信息，并用于 AI Copilot 的 supervisor / routing 能力。'
      backHref='/system/ai/agents/list'
      submitLabel='保存 Agent'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField
            control={form.control}
            name='agentCode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>编码</FormLabel>
                <FormControl>
                  <Input {...field} placeholder='workflow-router' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='agentName'
            render={({ field }) => (
              <FormItem>
                <FormLabel>名称</FormLabel>
                <FormControl>
                  <Input {...field} placeholder='流程路由智能体' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='capabilityCode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>能力码</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder='请选择能力码' />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>{buildOptionsMap(options.capabilityOptions)}</SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='routeMode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>路由模式</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder='请选择路由模式' />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>{buildOptionsMap(options.routeModeOptions)}</SelectContent>
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
                  <Input type='number' {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='contextTagsText'
            render={({ field }) => (
              <FormItem>
                <FormLabel>上下文标签</FormLabel>
                <FormControl>
                  <Input {...field} placeholder='流程, 审批, OA' />
                </FormControl>
                <FormDescription>使用中文逗号或英文逗号分隔。</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField
            control={form.control}
            name='systemPrompt'
            render={({ field }) => (
              <FormItem>
                <FormLabel>系统提示词</FormLabel>
                <FormControl>
                  <Textarea {...field} rows={6} placeholder='你是流程路由智能体...' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='metadataJson'
            render={({ field }) => (
              <FormItem>
                <FormLabel>元数据 JSON</FormLabel>
                <FormControl>
                  <Textarea {...field} rows={8} className='font-mono text-xs' />
                </FormControl>
                <FormDescription>可用于保存 supervisor、domain、priority 等扩展字段。</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className='flex items-center gap-3 rounded-lg border bg-muted/20 p-4'>
            <FormField
              control={form.control}
              name='supervisor'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between gap-4'>
                  <div>
                    <FormLabel>Supervisor</FormLabel>
                    <FormDescription>是否作为 supervisor 节点参与多智能体编排。</FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='enabled'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between gap-4'>
                  <div>
                    <FormLabel>启用</FormLabel>
                    <FormDescription>禁用后不进入 AI 编排推荐。</FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        </div>
      </Form>
    </RegistryFormShell>
  )
}

export function AiAgentEditPage({ agentId }: { agentId: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['ai-admin', 'agents', agentId],
    queryFn: () => getAiAgentDetail(agentId),
  })
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'agents', 'options'],
    queryFn: getAiAgentFormOptions,
  })

  const form = useForm<AgentFormValues>({
    resolver: zodResolver(agentFormSchema) as Resolver<AgentFormValues>,
    defaultValues: agentToFormValues(),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(agentToFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const mutation = useMutation({
    mutationFn: (values: AgentFormValues) => updateAiAgent(agentId, agentToPayload(values)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'agents'] })
      toast.success('Agent 已更新')
      navigate({
        to: '/system/ai/agents/$agentId',
        params: { agentId },
      })
    },
    onError: (error) => handleServerError(error),
  })

  if (detailQuery.isLoading || optionsQuery.isLoading) {
    return registryLoadingPage('编辑 Agent', '修改智能体注册信息。')
  }

  if (detailQuery.isError || optionsQuery.isError || !detailQuery.data || !optionsQuery.data) {
    return registryPageError('编辑 Agent', '修改智能体注册信息。', '/system/ai/agents/list')
  }

  const options = optionsQuery.data

  return (
    <RegistryFormShell
      title='编辑 Agent'
      description='修改 Agent 注册信息后，会立即影响 AI Copilot 的编排推荐。'
      backHref='/system/ai/agents/list'
      submitLabel='保存修改'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField
            control={form.control}
            name='agentCode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>编码</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='agentName'
            render={({ field }) => (
              <FormItem>
                <FormLabel>名称</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='capabilityCode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>能力码</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>{buildOptionsMap(options.capabilityOptions)}</SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='routeMode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>路由模式</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>{buildOptionsMap(options.routeModeOptions)}</SelectContent>
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
                  <Input type='number' {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='contextTagsText'
            render={({ field }) => (
              <FormItem>
                <FormLabel>上下文标签</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField
            control={form.control}
            name='systemPrompt'
            render={({ field }) => (
              <FormItem>
                <FormLabel>系统提示词</FormLabel>
                <FormControl>
                  <Textarea {...field} rows={6} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='metadataJson'
            render={({ field }) => (
              <FormItem>
                <FormLabel>元数据 JSON</FormLabel>
                <FormControl>
                  <Textarea {...field} rows={8} className='font-mono text-xs' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className='flex items-center gap-3 rounded-lg border bg-muted/20 p-4'>
            <FormField
              control={form.control}
              name='supervisor'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between gap-4'>
                  <div>
                    <FormLabel>Supervisor</FormLabel>
                    <FormDescription>是否作为 supervisor 节点参与多智能体编排。</FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='enabled'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between gap-4'>
                  <div>
                    <FormLabel>启用</FormLabel>
                    <FormDescription>禁用后不进入 AI 编排推荐。</FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        </div>
      </Form>
    </RegistryFormShell>
  )
}

// ========================= Tool =========================

export function AiToolListPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'tools', search],
    queryFn: () => listAiTools(search),
  })

  const data = query.data ?? emptyPage(search)

  const columns: ColumnDef<AiToolRecord, unknown>[] = [
    { accessorKey: 'toolCode', header: '编码' },
    { accessorKey: 'toolName', header: '名称' },
    { accessorKey: 'toolCategory', header: '分类' },
    {
      accessorKey: 'actionMode',
      header: '动作模式',
      cell: ({ row }) => (
        <Badge variant='outline'>{row.original.actionMode === 'READ' ? '读' : '写'}</Badge>
      ),
    },
    {
      accessorKey: 'requiredCapabilityCode',
      header: '能力码',
      cell: ({ row }) => row.original.requiredCapabilityCode || '-',
    },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => (
        <Badge variant={resolveStatusVariant(row.original.status)}>
          {resolveStatusLabel(row.original.status)}
        </Badge>
      ),
    },
    {
      accessorKey: 'updatedAt',
      header: '更新时间',
      cell: ({ row }) => formatDateTime(row.original.updatedAt),
    },
  ]

  return (
    <ResourceListPage
      title='Tool 注册表'
      description='统一维护 AI 工具注册信息，支持读写工具、能力码和分类配置。'
      endpoint='/system/ai/tools/page'
      searchPlaceholder='搜索工具编码、名称或分类'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listStatusSummary(search, data.total)}
      createAction={{ label: '新建 Tool', href: '/system/ai/tools/create' }}
    />
  )
}

export function AiToolDetailPage({ toolId }: { toolId: string }) {
  const query = useQuery({
    queryKey: ['ai-admin', 'tools', toolId],
    queryFn: () => getAiToolDetail(toolId),
  })

  if (query.isLoading) {
    return registryLoadingPage('Tool 注册表', '查看工具注册与动作模式。')
  }

  if (query.isError || !query.data) {
    return registryPageError('Tool 注册表', '查看工具注册与动作模式。', '/system/ai/tools/list')
  }

  const detail = query.data

  return (
    <RegistryDetailPage
      title='Tool 注册表'
      description='查看 AI 工具注册、动作模式与扩展元数据。'
      detail={detail}
      backHref='/system/ai/tools/list'
    >
      <AiInfoCard title='基础信息'>
        <AiKeyValueGrid
          items={[
            { label: '编码', value: detail.toolCode },
            { label: '名称', value: detail.toolName },
            { label: '分类', value: detail.toolCategory },
            { label: '动作模式', value: detail.actionMode === 'READ' ? '读取' : '写入' },
            { label: '能力码', value: detail.requiredCapabilityCode || '-' },
            { label: '状态', value: <AiStatusBadge label={resolveStatusLabel(detail.status)} variant={resolveStatusVariant(detail.status)} /> },
            { label: '创建时间', value: formatDateTime(detail.createdAt) },
            { label: '更新时间', value: formatDateTime(detail.updatedAt) },
          ]}
        />
      </AiInfoCard>
      <AiInfoCard title='说明'>
        <p className='text-sm leading-6'>{detail.description || '-'}</p>
      </AiInfoCard>
      <AiInfoCard title='可观测性' description='查看该 Tool 在真实运行态里的调用结果和失败分布。'>
        <AiObservabilityGrid value={detail.observability} />
      </AiInfoCard>
      <AiInfoCard title='关联链路' description='确认当前 Tool 被哪些 Agent 使用，以及是否映射到 Skill / MCP。'>
        <div className='grid gap-4 md:grid-cols-3'>
          <AiRegistryLinkList title='Agent' links={detail.linkedAgents} />
          <AiRegistryLinkList title='Skill' links={detail.linkedSkill ? [detail.linkedSkill] : []} />
          <AiRegistryLinkList title='MCP' links={detail.linkedMcp ? [detail.linkedMcp] : []} />
        </div>
      </AiInfoCard>
      <AiInfoCard title='元数据'>
        <AiJsonBlock value={detail.metadataJson} />
      </AiInfoCard>
    </RegistryDetailPage>
  )
}

export function AiToolCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'tools', 'options'],
    queryFn: getAiToolFormOptions,
  })

  const form = useForm<ToolFormValues>({
    resolver: zodResolver(toolFormSchema) as Resolver<ToolFormValues>,
    defaultValues: toolToFormValues(),
  })

  const mutation = useMutation({
    mutationFn: (values: ToolFormValues) => createAiTool(toolToPayload(values)),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'tools'] })
      toast.success('Tool 已创建')
      navigate({ to: '/system/ai/tools/$toolId', params: { toolId: result.toolId } })
    },
    onError: (error) => handleServerError(error),
  })

  if (optionsQuery.isLoading) {
    return registryLoadingPage('新建 Tool', '创建新的 AI 工具注册信息。')
  }

  if (optionsQuery.isError || !optionsQuery.data) {
    return registryPageError('新建 Tool', '创建新的 AI 工具注册信息。', '/system/ai/tools/list')
  }

  const options = optionsQuery.data

  return (
    <RegistryFormShell
      title='新建 Tool'
      description='维护 AI 工具注册、动作模式和能力约束。'
      backHref='/system/ai/tools/list'
      submitLabel='保存 Tool'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField
            control={form.control}
            name='toolCode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>编码</FormLabel>
                <FormControl>
                  <Input {...field} placeholder='task-summary' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='toolName'
            render={({ field }) => (
              <FormItem>
                <FormLabel>名称</FormLabel>
                <FormControl>
                  <Input {...field} placeholder='待办总结' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='toolCategory'
            render={({ field }) => (
              <FormItem>
                <FormLabel>分类</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder='请选择分类' />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>{buildOptionsMap(options.toolCategoryOptions)}</SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='actionMode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>动作模式</FormLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder='请选择动作模式' />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>{buildOptionsMap(options.actionModeOptions)}</SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name='requiredCapabilityCode'
            render={({ field }) => (
              <FormItem>
                <FormLabel>能力码</FormLabel>
                <Select value={field.value || ''} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder='可选' />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value=''>不限制</SelectItem>
                    {buildOptionsMap(options.capabilityOptions)}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField
            control={form.control}
            name='metadataJson'
            render={({ field }) => (
              <FormItem>
                <FormLabel>元数据 JSON</FormLabel>
                <FormControl>
                  <Textarea {...field} rows={8} className='font-mono text-xs' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className='flex items-center gap-3 rounded-lg border bg-muted/20 p-4'>
            <FormField
              control={form.control}
              name='enabled'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between gap-4'>
                  <div>
                    <FormLabel>启用</FormLabel>
                    <FormDescription>启用后可被 AI Copilot 调用。</FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        </div>
      </Form>
    </RegistryFormShell>
  )
}

export function AiToolEditPage({ toolId }: { toolId: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['ai-admin', 'tools', toolId],
    queryFn: () => getAiToolDetail(toolId),
  })
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'tools', 'options'],
    queryFn: getAiToolFormOptions,
  })

  const form = useForm<ToolFormValues>({
    resolver: zodResolver(toolFormSchema) as Resolver<ToolFormValues>,
    defaultValues: toolToFormValues(),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(toolToFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const mutation = useMutation({
    mutationFn: (values: ToolFormValues) => updateAiTool(toolId, toolToPayload(values)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'tools'] })
      toast.success('Tool 已更新')
      navigate({ to: '/system/ai/tools/$toolId', params: { toolId } })
    },
    onError: (error) => handleServerError(error),
  })

  if (detailQuery.isLoading || optionsQuery.isLoading) {
    return registryLoadingPage('编辑 Tool', '修改 AI 工具注册信息。')
  }

  if (detailQuery.isError || optionsQuery.isError || !detailQuery.data || !optionsQuery.data) {
    return registryPageError('编辑 Tool', '修改 AI 工具注册信息。', '/system/ai/tools/list')
  }

  const options = optionsQuery.data

  return (
    <RegistryFormShell
      title='编辑 Tool'
      description='修改 AI 工具注册信息。'
      backHref='/system/ai/tools/list'
      submitLabel='保存修改'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField control={form.control} name='toolCode' render={({ field }) => (
            <FormItem>
              <FormLabel>编码</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='toolName' render={({ field }) => (
            <FormItem>
              <FormLabel>名称</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='toolCategory' render={({ field }) => (
            <FormItem>
              <FormLabel>分类</FormLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                <SelectContent>{buildOptionsMap(options.toolCategoryOptions)}</SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='actionMode' render={({ field }) => (
            <FormItem>
              <FormLabel>动作模式</FormLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                <SelectContent>{buildOptionsMap(options.actionModeOptions)}</SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='requiredCapabilityCode' render={({ field }) => (
            <FormItem>
              <FormLabel>能力码</FormLabel>
              <FormControl><Input {...field} placeholder='可选' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField control={form.control} name='metadataJson' render={({ field }) => (
            <FormItem>
              <FormLabel>元数据 JSON</FormLabel>
              <FormControl><Textarea {...field} rows={8} className='font-mono text-xs' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='enabled' render={({ field }) => (
            <FormItem className='flex flex-row items-center justify-between gap-4 rounded-lg border bg-muted/20 p-4'>
              <div>
                <FormLabel>启用</FormLabel>
                <FormDescription>启用后可被 Copilot 调用。</FormDescription>
              </div>
              <FormControl><Switch checked={field.value} onCheckedChange={field.onChange} /></FormControl>
            </FormItem>
          )} />
        </div>
      </Form>
    </RegistryFormShell>
  )
}

// ========================= MCP =========================

export function AiMcpListPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'mcps', search],
    queryFn: () => listAiMcps(search),
  })

  const data = query.data ?? emptyPage(search)

  const columns: ColumnDef<AiMcpRecord, unknown>[] = [
    { accessorKey: 'mcpCode', header: '编码' },
    { accessorKey: 'mcpName', header: '名称' },
    { accessorKey: 'transportType', header: '传输类型' },
    { accessorKey: 'endpointUrl', header: '地址', cell: ({ row }) => row.original.endpointUrl || '-' },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => (
        <Badge variant={resolveStatusVariant(row.original.status)}>
          {resolveStatusLabel(row.original.status)}
        </Badge>
      ),
    },
    { accessorKey: 'updatedAt', header: '更新时间', cell: ({ row }) => formatDateTime(row.original.updatedAt) },
  ]

  return (
    <ResourceListPage
      title='MCP 注册表'
      description='统一维护 MCP 服务连接信息，支持内部桥接、Streamable HTTP 和 STDIO。'
      endpoint='/system/ai/mcps/page'
      searchPlaceholder='搜索 MCP 编码、名称或地址'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listStatusSummary(search, data.total)}
      createAction={{ label: '新建 MCP', href: '/system/ai/mcps/create' }}
    />
  )
}

export function AiMcpDetailPage({ mcpId }: { mcpId: string }) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['ai-admin', 'mcps', mcpId],
    queryFn: () => getAiMcpDetail(mcpId),
  })
  const diagnosticQueryKey = ['ai-admin', 'mcps', 'diagnostics', mcpId] as const
  const diagnosticQuery = useQuery({
    queryKey: diagnosticQueryKey,
    queryFn: () => getAiMcpDiagnosticDetail(mcpId),
  })
  const recheckMutation = useMutation({
    mutationFn: () => recheckAiMcpDiagnostic(mcpId),
    onSuccess: (result) => {
      queryClient.setQueryData(diagnosticQueryKey, result)
      toast.success('已重新检测 MCP 连通性')
    },
    onError: (error) => handleServerError(error),
  })

  if (query.isLoading) {
    return registryLoadingPage('MCP 注册表', '查看 MCP 服务注册信息。')
  }

  if (query.isError || !query.data) {
    return registryPageError('MCP 注册表', '查看 MCP 服务注册信息。', '/system/ai/mcps/list')
  }

  const detail = query.data

  return (
    <RegistryDetailPage
      title='MCP 注册表'
      description='查看 MCP 服务连接、传输方式和扩展元数据。'
      detail={detail}
      backHref='/system/ai/mcps/list'
      actions={
        <Button
          variant='outline'
          onClick={() => recheckMutation.mutate()}
          disabled={recheckMutation.isPending}
        >
          {recheckMutation.isPending ? <Loader2 className='animate-spin' /> : <RefreshCw />}
          重新检测
        </Button>
      }
    >
      <AiInfoCard title='基础信息'>
        <AiKeyValueGrid
          items={[
            { label: '编码', value: detail.mcpCode },
            { label: '名称', value: detail.mcpName },
            { label: '传输类型', value: detail.transportType },
            { label: '地址', value: detail.endpointUrl || '-' },
            { label: '能力码', value: detail.requiredCapabilityCode || '-' },
            { label: '状态', value: <AiStatusBadge label={resolveStatusLabel(detail.status)} variant={resolveStatusVariant(detail.status)} /> },
            { label: '创建时间', value: formatDateTime(detail.createdAt) },
            { label: '更新时间', value: formatDateTime(detail.updatedAt) },
          ]}
        />
      </AiInfoCard>
      <AiInfoCard title='说明'>
        <p className='text-sm leading-6'>{detail.description || '-'}</p>
      </AiInfoCard>
      <AiInfoCard title='可观测性' description='补充 MCP 实际承接 ToolCall 的运行态数据。'>
        <AiObservabilityGrid value={detail.observability} />
      </AiInfoCard>
      <AiInfoCard title='关联链路' description='查看当前 MCP 绑定的 Agent、Tool 和 Skill。'>
        <div className='grid gap-4 md:grid-cols-3'>
          <AiRegistryLinkList title='Agent' links={detail.linkedAgents} />
          <AiRegistryLinkList title='Tool' links={detail.linkedTools} />
          <AiRegistryLinkList title='Skill' links={detail.linkedSkills} />
        </div>
      </AiInfoCard>
      <AiInfoCard title='元数据'>
        <AiJsonBlock value={detail.metadataJson} />
      </AiInfoCard>
      <AiInfoCard
        title='连通性诊断'
        description='展示最近一次真实连通检测结果、失败阶段和可展开的诊断步骤。'
      >
        {diagnosticQuery.isLoading ? (
          <div className='space-y-3'>
            <div className='h-8 w-1/3 rounded bg-muted' />
            <div className='h-24 rounded bg-muted' />
          </div>
        ) : diagnosticQuery.isError || !diagnosticQuery.data ? (
          <p className='text-sm text-muted-foreground'>暂无法获取连通性诊断结果。</p>
        ) : (
          <div className='space-y-4'>
            <AiKeyValueGrid
              items={[
                {
                  label: '连通状态',
                  value: (
                    <AiStatusBadge
                      label={diagnosticQuery.data.connectionStatus}
                      variant={diagnosticQuery.data.connectionStatus === 'DOWN' ? 'destructive' : 'secondary'}
                    />
                  ),
                },
                { label: '工具数', value: diagnosticQuery.data.toolCount ?? '-' },
                { label: '耗时', value: diagnosticQuery.data.responseTimeMillis ? `${diagnosticQuery.data.responseTimeMillis} ms` : '-' },
                { label: '检查时间', value: formatDateTime(diagnosticQuery.data.checkedAt) },
                { label: '注册状态', value: diagnosticQuery.data.registryStatus },
                { label: '失败阶段', value: diagnosticQuery.data.failureStage || '-' },
                { label: '累计 ToolCall', value: diagnosticQuery.data.observability?.totalToolCalls ?? '-' },
                { label: '最近失败', value: diagnosticQuery.data.observability?.latestFailureReason || '-' },
              ]}
            />

            {diagnosticQuery.data.failureReason || diagnosticQuery.data.failureDetail ? (
              <Collapsible>
                <div className='rounded-lg border bg-muted/20 p-4'>
                  <div className='flex items-center justify-between gap-4'>
                    <div className='space-y-1'>
                      <div className='text-sm font-medium'>失败原因展开</div>
                      <div className='text-xs text-muted-foreground'>
                        查看失败摘要、详细上下文和错误定位信息。
                      </div>
                    </div>
                    <CollapsibleTrigger asChild>
                      <Button variant='ghost' size='sm'>
                        <ChevronDown />
                      </Button>
                    </CollapsibleTrigger>
                  </div>
                  <CollapsibleContent>
                    <div className='mt-4 grid gap-4'>
                      <AiKeyValueGrid
                        items={[
                          { label: '失败原因', value: diagnosticQuery.data.failureReason || '-' },
                          { label: '失败阶段', value: diagnosticQuery.data.failureStage || '-' },
                        ]}
                      />
                      <pre className='whitespace-pre-wrap rounded-lg border bg-background p-4 text-xs leading-6'>
                        {diagnosticQuery.data.failureDetail || '-'}
                      </pre>
                    </div>
                  </CollapsibleContent>
                </div>
              </Collapsible>
            ) : (
              <p className='text-sm text-muted-foreground'>
                当前诊断已通过，无失败详情可展开。
              </p>
            )}

            <div className='space-y-3'>
              <div>
                <div className='text-sm font-medium'>诊断步骤</div>
                <div className='text-xs text-muted-foreground'>
                  按执行顺序展示记录读取、客户端创建、初始化和工具列表读取结果。
                </div>
              </div>
              <div className='space-y-3'>
                {(diagnosticQuery.data.diagnosticSteps ?? []).length > 0 ? (
                  (diagnosticQuery.data.diagnosticSteps ?? []).map((step) => (
                    <div key={`${step.step}-${step.status}-${step.message}`} className='rounded-lg border p-4'>
                      <div className='flex flex-wrap items-center gap-2'>
                        <AiStatusBadge label={diagnosticStepLabel(step.status)} variant={diagnosticStepVariant(step.status)} />
                        <div className='text-sm font-medium'>{step.step}</div>
                        {step.durationMillis != null ? (
                          <div className='text-xs text-muted-foreground'>
                            {step.durationMillis} ms
                          </div>
                        ) : null}
                      </div>
                      <div className='mt-2 text-sm leading-6 text-muted-foreground'>
                        {step.message}
                      </div>
                    </div>
                  ))
                ) : (
                  <p className='text-sm text-muted-foreground'>暂无诊断步骤。</p>
                )}
              </div>
            </div>
          </div>
        )}
      </AiInfoCard>
    </RegistryDetailPage>
  )
}

export function AiMcpCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'mcps', 'options'],
    queryFn: getAiMcpFormOptions,
  })

  const form = useForm<McpFormValues>({
    resolver: zodResolver(mcpFormSchema) as Resolver<McpFormValues>,
    defaultValues: mcpToFormValues(),
  })

  const mutation = useMutation({
    mutationFn: (values: McpFormValues) => createAiMcp(mcpToPayload(values)),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'mcps'] })
      toast.success('MCP 已创建')
      navigate({ to: '/system/ai/mcps/$mcpId', params: { mcpId: result.mcpId } })
    },
    onError: (error) => handleServerError(error),
  })

  if (optionsQuery.isLoading) {
    return registryLoadingPage('新建 MCP', '创建新的 MCP 注册信息。')
  }

  if (optionsQuery.isError || !optionsQuery.data) {
    return registryPageError('新建 MCP', '创建新的 MCP 注册信息。', '/system/ai/mcps/list')
  }

  const options = optionsQuery.data

  return (
    <RegistryFormShell
      title='新建 MCP'
      description='维护 MCP 注册、传输方式和外部连接配置。'
      backHref='/system/ai/mcps/list'
      submitLabel='保存 MCP'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField control={form.control} name='mcpCode' render={({ field }) => (
            <FormItem>
              <FormLabel>编码</FormLabel>
              <FormControl><Input {...field} placeholder='internal-mcp' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='mcpName' render={({ field }) => (
            <FormItem>
              <FormLabel>名称</FormLabel>
              <FormControl><Input {...field} placeholder='平台内置 MCP' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='transportType' render={({ field }) => (
            <FormItem>
              <FormLabel>传输类型</FormLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <FormControl><SelectTrigger><SelectValue placeholder='请选择传输类型' /></SelectTrigger></FormControl>
                <SelectContent>{buildOptionsMap(options.transportTypeOptions)}</SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='endpointUrl' render={({ field }) => (
            <FormItem>
              <FormLabel>地址</FormLabel>
              <FormControl><Input {...field} placeholder='http://localhost:8088/mcp' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='requiredCapabilityCode' render={({ field }) => (
            <FormItem>
              <FormLabel>能力码</FormLabel>
              <FormControl><Input {...field} placeholder='ai:copilot:external' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField control={form.control} name='metadataJson' render={({ field }) => (
            <FormItem>
              <FormLabel>元数据 JSON</FormLabel>
              <FormControl><Textarea {...field} rows={8} className='font-mono text-xs' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='enabled' render={({ field }) => (
            <FormItem className='flex flex-row items-center justify-between gap-4 rounded-lg border bg-muted/20 p-4'>
              <div>
                <FormLabel>启用</FormLabel>
                <FormDescription>启用后允许 Spring AI 运行时接入该 MCP。</FormDescription>
              </div>
              <FormControl><Switch checked={field.value} onCheckedChange={field.onChange} /></FormControl>
            </FormItem>
          )} />
        </div>
      </Form>
    </RegistryFormShell>
  )
}

export function AiMcpEditPage({ mcpId }: { mcpId: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['ai-admin', 'mcps', mcpId],
    queryFn: () => getAiMcpDetail(mcpId),
  })
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'mcps', 'options'],
    queryFn: getAiMcpFormOptions,
  })

  const form = useForm<McpFormValues>({
    resolver: zodResolver(mcpFormSchema) as Resolver<McpFormValues>,
    defaultValues: mcpToFormValues(),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(mcpToFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const mutation = useMutation({
    mutationFn: (values: McpFormValues) => updateAiMcp(mcpId, mcpToPayload(values)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'mcps'] })
      toast.success('MCP 已更新')
      navigate({ to: '/system/ai/mcps/$mcpId', params: { mcpId } })
    },
    onError: (error) => handleServerError(error),
  })

  if (detailQuery.isLoading || optionsQuery.isLoading) {
    return registryLoadingPage('编辑 MCP', '修改 MCP 注册信息。')
  }

  if (detailQuery.isError || optionsQuery.isError || !detailQuery.data || !optionsQuery.data) {
    return registryPageError('编辑 MCP', '修改 MCP 注册信息。', '/system/ai/mcps/list')
  }

  const options = optionsQuery.data

  return (
    <RegistryFormShell
      title='编辑 MCP'
      description='修改 MCP 注册信息。'
      backHref='/system/ai/mcps/list'
      submitLabel='保存修改'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField control={form.control} name='mcpCode' render={({ field }) => (
            <FormItem>
              <FormLabel>编码</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='mcpName' render={({ field }) => (
            <FormItem>
              <FormLabel>名称</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='transportType' render={({ field }) => (
            <FormItem>
              <FormLabel>传输类型</FormLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                <SelectContent>{buildOptionsMap(options.transportTypeOptions)}</SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='endpointUrl' render={({ field }) => (
            <FormItem>
              <FormLabel>地址</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='requiredCapabilityCode' render={({ field }) => (
            <FormItem>
              <FormLabel>能力码</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField control={form.control} name='metadataJson' render={({ field }) => (
            <FormItem>
              <FormLabel>元数据 JSON</FormLabel>
              <FormControl><Textarea {...field} rows={8} className='font-mono text-xs' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='enabled' render={({ field }) => (
            <FormItem className='flex flex-row items-center justify-between gap-4 rounded-lg border bg-muted/20 p-4'>
              <div>
                <FormLabel>启用</FormLabel>
                <FormDescription>启用后允许运行时接入。</FormDescription>
              </div>
              <FormControl><Switch checked={field.value} onCheckedChange={field.onChange} /></FormControl>
            </FormItem>
          )} />
        </div>
      </Form>
    </RegistryFormShell>
  )
}

// ========================= Skill =========================

export function AiSkillListPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'skills', search],
    queryFn: () => listAiSkills(search),
  })

  const data = query.data ?? emptyPage(search)

  const columns: ColumnDef<AiSkillRecord, unknown>[] = [
    { accessorKey: 'skillCode', header: '编码' },
    { accessorKey: 'skillName', header: '名称' },
    { accessorKey: 'skillPath', header: '路径', cell: ({ row }) => row.original.skillPath || '-' },
    { accessorKey: 'requiredCapabilityCode', header: '能力码', cell: ({ row }) => row.original.requiredCapabilityCode || '-' },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => <Badge variant={resolveStatusVariant(row.original.status)}>{resolveStatusLabel(row.original.status)}</Badge>,
    },
    { accessorKey: 'updatedAt', header: '更新时间', cell: ({ row }) => formatDateTime(row.original.updatedAt) },
  ]

  return (
    <ResourceListPage
      title='Skill 注册表'
      description='统一维护 Skill 注册信息，支持本地技能与扩展技能路径配置。'
      endpoint='/system/ai/skills/page'
      searchPlaceholder='搜索 Skill 编码、名称或路径'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listStatusSummary(search, data.total)}
      createAction={{ label: '新建 Skill', href: '/system/ai/skills/create' }}
    />
  )
}

export function AiSkillDetailPage({ skillId }: { skillId: string }) {
  const query = useQuery({
    queryKey: ['ai-admin', 'skills', skillId],
    queryFn: () => getAiSkillDetail(skillId),
  })

  if (query.isLoading) {
    return registryLoadingPage('Skill 注册表', '查看 Skill 注册信息。')
  }

  if (query.isError || !query.data) {
    return registryPageError('Skill 注册表', '查看 Skill 注册信息。', '/system/ai/skills/list')
  }

  const detail = query.data

  return (
    <RegistryDetailPage
      title='Skill 注册表'
      description='查看 Skill 路径、能力码和扩展元数据。'
      detail={detail}
      backHref='/system/ai/skills/list'
    >
      <AiInfoCard title='基础信息'>
        <AiKeyValueGrid
          items={[
            { label: '编码', value: detail.skillCode },
            { label: '名称', value: detail.skillName },
            { label: '路径', value: detail.skillPath || '-' },
            { label: '能力码', value: detail.requiredCapabilityCode || '-' },
            { label: '状态', value: <AiStatusBadge label={resolveStatusLabel(detail.status)} variant={resolveStatusVariant(detail.status)} /> },
            { label: '创建时间', value: formatDateTime(detail.createdAt) },
            { label: '更新时间', value: formatDateTime(detail.updatedAt) },
          ]}
        />
      </AiInfoCard>
      <AiInfoCard title='说明'>
        <p className='text-sm leading-6'>{detail.description || '-'}</p>
      </AiInfoCard>
      <AiInfoCard title='可观测性' description='查看该 Skill 在实际 ToolCall 链路中的命中次数和失败情况。'>
        <AiObservabilityGrid value={detail.observability} />
      </AiInfoCard>
      <AiInfoCard title='关联链路' description='确认当前 Skill 对应的 Agent、Tool 和 MCP。'>
        <div className='grid gap-4 md:grid-cols-3'>
          <AiRegistryLinkList title='Agent' links={detail.linkedAgents} />
          <AiRegistryLinkList title='Tool' links={detail.linkedTool ? [detail.linkedTool] : []} />
          <AiRegistryLinkList title='MCP' links={detail.linkedMcp ? [detail.linkedMcp] : []} />
        </div>
      </AiInfoCard>
      <AiInfoCard title='元数据'>
        <AiJsonBlock value={detail.metadataJson} />
      </AiInfoCard>
    </RegistryDetailPage>
  )
}

export function AiSkillCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'skills', 'options'],
    queryFn: getAiSkillFormOptions,
  })

  const form = useForm<SkillFormValues>({
    resolver: zodResolver(skillFormSchema) as Resolver<SkillFormValues>,
    defaultValues: skillToFormValues(),
  })

  const mutation = useMutation({
    mutationFn: (values: SkillFormValues) => createAiSkill(skillToPayload(values)),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'skills'] })
      toast.success('Skill 已创建')
      navigate({ to: '/system/ai/skills/$skillId', params: { skillId: result.skillId } })
    },
    onError: (error) => handleServerError(error),
  })

  if (optionsQuery.isLoading) {
    return registryLoadingPage('新建 Skill', '创建新的 Skill 注册信息。')
  }

  if (optionsQuery.isError || !optionsQuery.data) {
    return registryPageError('新建 Skill', '创建新的 Skill 注册信息。', '/system/ai/skills/list')
  }

  const options = optionsQuery.data

  return (
    <RegistryFormShell
      title='新建 Skill'
      description='维护本地技能与外部技能的路径、能力码和扩展元数据。'
      backHref='/system/ai/skills/list'
      submitLabel='保存 Skill'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField control={form.control} name='skillCode' render={({ field }) => (
            <FormItem>
              <FormLabel>编码</FormLabel>
              <FormControl><Input {...field} placeholder='workflow-design-skill' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='skillName' render={({ field }) => (
            <FormItem>
              <FormLabel>名称</FormLabel>
              <FormControl><Input {...field} placeholder='流程设计技能' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='skillPath' render={({ field }) => (
            <FormItem>
              <FormLabel>路径</FormLabel>
              <FormControl><Input {...field} placeholder='classpath:ai/skills/workflow-design-skill.md' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='requiredCapabilityCode' render={({ field }) => (
            <FormItem>
              <FormLabel>能力码</FormLabel>
              <Select value={field.value || ''} onValueChange={field.onChange}>
                <FormControl><SelectTrigger><SelectValue placeholder='可选' /></SelectTrigger></FormControl>
                <SelectContent>
                  <SelectItem value=''>不限制</SelectItem>
                  {buildOptionsMap(options.capabilityOptions)}
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )} />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField control={form.control} name='metadataJson' render={({ field }) => (
            <FormItem>
              <FormLabel>元数据 JSON</FormLabel>
              <FormControl><Textarea {...field} rows={8} className='font-mono text-xs' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='enabled' render={({ field }) => (
            <FormItem className='flex flex-row items-center justify-between gap-4 rounded-lg border bg-muted/20 p-4'>
              <div>
                <FormLabel>启用</FormLabel>
                <FormDescription>启用后允许在 Copilot 中被检索和调用。</FormDescription>
              </div>
              <FormControl><Switch checked={field.value} onCheckedChange={field.onChange} /></FormControl>
            </FormItem>
          )} />
        </div>
      </Form>
    </RegistryFormShell>
  )
}

export function AiSkillEditPage({ skillId }: { skillId: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['ai-admin', 'skills', skillId],
    queryFn: () => getAiSkillDetail(skillId),
  })
  const optionsQuery = useQuery({
    queryKey: ['ai-admin', 'skills', 'options'],
    queryFn: getAiSkillFormOptions,
  })

  const form = useForm<SkillFormValues>({
    resolver: zodResolver(skillFormSchema) as Resolver<SkillFormValues>,
    defaultValues: skillToFormValues(),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(skillToFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const mutation = useMutation({
    mutationFn: (values: SkillFormValues) => updateAiSkill(skillId, skillToPayload(values)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['ai-admin', 'skills'] })
      toast.success('Skill 已更新')
      navigate({ to: '/system/ai/skills/$skillId', params: { skillId } })
    },
    onError: (error) => handleServerError(error),
  })

  if (detailQuery.isLoading || optionsQuery.isLoading) {
    return registryLoadingPage('编辑 Skill', '修改 Skill 注册信息。')
  }

  if (detailQuery.isError || optionsQuery.isError || !detailQuery.data || !optionsQuery.data) {
    return registryPageError('编辑 Skill', '修改 Skill 注册信息。', '/system/ai/skills/list')
  }

  return (
    <RegistryFormShell
      title='编辑 Skill'
      description='修改 Skill 注册信息。'
      backHref='/system/ai/skills/list'
      submitLabel='保存修改'
      loading={mutation.isPending}
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
    >
      <Form {...form}>
        <div className='grid gap-4 md:grid-cols-2'>
          <FormField control={form.control} name='skillCode' render={({ field }) => (
            <FormItem>
              <FormLabel>编码</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='skillName' render={({ field }) => (
            <FormItem>
              <FormLabel>名称</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='skillPath' render={({ field }) => (
            <FormItem>
              <FormLabel>路径</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='requiredCapabilityCode' render={({ field }) => (
            <FormItem>
              <FormLabel>能力码</FormLabel>
              <FormControl><Input {...field} /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
        </div>
        <div className='mt-4 grid gap-4'>
          <FormField control={form.control} name='metadataJson' render={({ field }) => (
            <FormItem>
              <FormLabel>元数据 JSON</FormLabel>
              <FormControl><Textarea {...field} rows={8} className='font-mono text-xs' /></FormControl>
              <FormMessage />
            </FormItem>
          )} />
          <FormField control={form.control} name='enabled' render={({ field }) => (
            <FormItem className='flex flex-row items-center justify-between gap-4 rounded-lg border bg-muted/20 p-4'>
              <div>
                <FormLabel>启用</FormLabel>
                <FormDescription>启用后允许在 Copilot 中被检索和调用。</FormDescription>
              </div>
              <FormControl><Switch checked={field.value} onCheckedChange={field.onChange} /></FormControl>
            </FormItem>
          )} />
        </div>
      </Form>
    </RegistryFormShell>
  )
}
