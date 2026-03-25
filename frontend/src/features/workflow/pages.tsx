import { startTransition, useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  type Node,
  type OnConnect,
  useReactFlow,
  useViewport,
} from '@xyflow/react'
import {
  Bot,
  CircleDotDashed,
  LayoutGrid,
  MoveDiagonal2,
  Redo2,
  Save,
  Sparkles,
  Undo2,
  Waypoints,
} from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import {
  getProcessDefinitionDetail,
  listProcessDefinitions,
  publishProcessDefinition,
  saveProcessDefinition,
} from '@/lib/api/workflow'
import { getApiErrorMessage } from '@/lib/api/client'
import { cn } from '@/lib/utils'
import {
  findProcessRuntimeFormByProcessKey,
} from '@/features/forms/runtime/form-component-registry'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import {
  type ProcessDefinitionMeta,
  processDefinitionDetailToWorkflowSnapshot,
  workflowSnapshotToProcessDefinitionDsl,
} from './designer/dsl'
import {
  ProcessFormSelector,
} from './designer/form-selection'
import { WorkflowDesignerLayout } from './designer/designer-layout'
import {
  workflowNodeTemplates,
  type WorkflowNodeTemplate,
} from './designer/palette'
import { NodeConfigPanel } from './designer/node-config-panel'
import { useWorkflowDesignerStore } from './designer/store'
import {
  type WorkflowEdge,
  type WorkflowHelperLines,
  type WorkflowNode,
} from './designer/types'
import { WorkflowNodeCard } from './designer/workflow-node'
import { WorkflowQuickInsertEdge } from './designer/workflow-edge'

import '@xyflow/react/dist/style.css'

const definitionsRoute = getRouteApi('/_authenticated/workflow/definitions/list')
const designerRoute = getRouteApi('/_authenticated/workflow/designer')

type DefinitionRow = {
  processDefinitionId: string
  processName: string
  processKey: string
  version: string
  category: string
  createdAt: string
  status: '已发布' | '草稿'
}

type DesignerStructurePreset = {
  id: 'SUBPROCESS_CHAIN' | 'DYNAMIC_BUILDER_CHAIN' | 'INCLUSIVE_BRANCH'
  title: string
  description: string
}

const designerStructurePresets: DesignerStructurePreset[] = [
  {
    id: 'SUBPROCESS_CHAIN',
    title: '主子流程模板',
    description: '一键插入父流程审批、子流程调用和回传确认三段骨架。',
  },
  {
    id: 'DYNAMIC_BUILDER_CHAIN',
    title: '动态构建模板',
    description: '快速生成“规则触发 -> 动态构建 -> 汇总确认”的链路。',
  },
  {
    id: 'INCLUSIVE_BRANCH',
    title: '包容分支模板',
    description: '直接插入可命中多分支并汇聚的包容网关结构。',
  },
]

const paletteToneClassNames = {
  brand: 'bg-sky-500/12 text-sky-700 dark:text-sky-300',
  success: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
  warning: 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
  neutral: 'bg-slate-500/12 text-slate-700 dark:text-slate-300',
} satisfies Record<WorkflowNodeTemplate['tone'], string>

const paletteKindBadgeLabels = {
  start: '开始',
  approver: '审批',
  subprocess: '子流程',
  'dynamic-builder': '动态构建',
  condition: '排他',
  inclusive: '包容',
  parallel: '并行',
  cc: '抄送',
  timer: '定时',
  trigger: '触发',
  end: '结束',
} satisfies Record<WorkflowNodeTemplate['kind'], string>

const workflowNodeTypes = {
  workflow: WorkflowNodeCard,
}

const workflowEdgeTypes = {
  quickInsert: WorkflowQuickInsertEdge,
}

const helperLineThreshold = 16
const defaultLeaveFormFields: ProcessDefinitionMeta['formFields'] = [
  { fieldKey: 'leaveType', label: '请假类型', valueType: 'string' },
  { fieldKey: 'leaveDays', label: '请假天数', valueType: 'number' },
  { fieldKey: 'urgent', label: '是否紧急', valueType: 'boolean' },
  { fieldKey: 'managerUserId', label: '直属负责人', valueType: 'string' },
]
// 默认先挂一套请假流程表单，保证设计器打开后就能直接预览和发布。
const defaultProcessForm = findProcessRuntimeFormByProcessKey('oa_leave')
const defaultDefinitionMeta: ProcessDefinitionMeta = {
  processKey: 'oa_leave',
  processName: '请假审批',
  category: 'OA',
  processFormKey: defaultProcessForm?.formKey ?? '',
  processFormVersion: defaultProcessForm?.formVersion ?? '',
  formFields: defaultLeaveFormFields,
}
const emptyDefinitionMeta: ProcessDefinitionMeta = {
  processKey: '',
  processName: '',
  category: '',
  processFormKey: '',
  processFormVersion: '',
  formFields: [],
}

type PersistedDesignerDraft = {
  snapshot: {
    nodes: WorkflowNode[]
    edges: WorkflowEdge[]
    selectedNodeId: string | null
  }
  viewport: {
    x: number
    y: number
    zoom: number
  } | null
}

function buildDesignerDraftKey(processDefinitionId?: string) {
  return `workflow-designer-draft:${processDefinitionId ?? 'new'}`
}

function readPersistedDesignerDraft(
  draftKey: string
): PersistedDesignerDraft | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const raw = window.sessionStorage.getItem(draftKey)
    return raw ? (JSON.parse(raw) as PersistedDesignerDraft) : null
  } catch {
    return null
  }
}

function writePersistedDesignerDraft(
  draftKey: string,
  value: PersistedDesignerDraft
) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.sessionStorage.setItem(draftKey, JSON.stringify(value))
  } catch {
    // 忽略浏览器存储异常，不阻塞设计器编辑。
  }
}

// 把后端状态统一映射成列表页中文状态。
function resolveStatusLabel(status: string): DefinitionRow['status'] {
  return status === 'PUBLISHED' ? '已发布' : '草稿'
}

const definitionColumns: ColumnDef<DefinitionRow>[] = [
  {
    accessorKey: 'processName',
    header: '流程名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <Link
          to='/workflow/designer'
          search={{ processDefinitionId: row.original.processDefinitionId }}
          className='font-medium text-primary hover:underline'
        >
          {row.original.processName}
        </Link>
        <span className='text-xs text-muted-foreground'>
          {row.original.processDefinitionId}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'processKey',
    header: '流程编码',
  },
  {
    accessorKey: 'version',
    header: '版本',
  },
  {
    accessorKey: 'category',
    header: '业务域',
  },
  {
    accessorKey: 'createdAt',
    header: '发布时间',
    cell: ({ row }) => (
      <span className='text-sm text-muted-foreground'>
        {row.original.createdAt}
      </span>
    ),
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={row.original.status === '已发布' ? 'secondary' : 'outline'}>
        {row.original.status}
      </Badge>
    ),
  },
]

export function WorkflowDefinitionsListPage() {
  const search = definitionsRoute.useSearch()
  const navigate = definitionsRoute.useNavigate()
  const query = useQuery({
    queryKey: ['process-definitions', search],
    queryFn: () => listProcessDefinitions(search),
    placeholderData: (previous) => previous,
  })

  const rows = useMemo<DefinitionRow[]>(
    () =>
      (query.data?.records ?? []).map((record) => ({
        processDefinitionId: record.processDefinitionId,
        processName: record.processName,
        processKey: record.processKey,
        version: `V${record.version}.0`,
        category: record.category || '-',
        createdAt: record.createdAt,
        status: resolveStatusLabel(record.status),
      })),
    [query.data?.records]
  )

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    const publishedCount = records.filter(
      (record) => resolveStatusLabel(record.status) === '已发布'
    ).length
    const categoryCount = new Set(
      records.map((record) => record.category).filter(Boolean)
    ).size

    return [
      {
        label: '流程总数',
        value: `${query.data?.total ?? 0}`,
        hint: '流程定义列表已接入真实分页接口，支持关键字与筛选联调。',
      },
      {
        label: '当前页已发布',
        value: `${publishedCount}`,
        hint: '首批按发布态流程定义支撑 OA 流程实例发起。',
      },
      {
        label: '当前页业务域',
        value: `${categoryCount}`,
        hint: '业务域后续会继续扩展到 OA、PLM 及行业模板。',
      },
    ]
  }, [query.data])

  return (
    <ResourceListPage<DefinitionRow>
      title='流程定义列表'
      description='流程定义列表页承接版本管理、发布状态和业务域归属，当前已对接后端分页接口，后续继续接通 DSL 转 BPMN 发布链路。'
      endpoint='/api/v1/process-definitions/page'
      searchPlaceholder='搜索流程名称、业务域或版本号'
      search={search}
      navigate={navigate}
      columns={definitionColumns}
      data={rows}
      summaries={summaries}
      createAction={{ label: '新建设计', href: '/workflow/designer' }}
    />
  )
}

// 拖拽节点时寻找附近对齐辅助线，帮助画布快速排版。
function findHelperLines(
  currentNode: Node,
  nodes: WorkflowNode[]
): WorkflowHelperLines {
  let vertical: number | null = null
  let horizontal: number | null = null

  for (const candidate of nodes) {
    if (candidate.id === currentNode.id) {
      continue
    }

    if (
      vertical === null &&
      Math.abs(candidate.position.x - currentNode.position.x) <=
        helperLineThreshold
    ) {
      vertical = candidate.position.x
    }

    if (
      horizontal === null &&
      Math.abs(candidate.position.y - currentNode.position.y) <=
        helperLineThreshold
    ) {
      horizontal = candidate.position.y
    }
  }

  return { vertical, horizontal }
}

// 把辅助线单独覆盖在画布上，不干扰 React Flow 的交互层。
function GuideLinesOverlay({ lines }: { lines: WorkflowHelperLines }) {
  const viewport = useViewport()

  return (
    <div className='pointer-events-none absolute inset-0 z-20 overflow-hidden'>
      {lines.vertical !== null ? (
        <div
          className='absolute top-0 bottom-0 w-px bg-primary/50'
          style={{
            left: lines.vertical * viewport.zoom + viewport.x,
          }}
        />
      ) : null}
      {lines.horizontal !== null ? (
        <div
          className='absolute left-0 right-0 h-px bg-primary/50'
          style={{
            top: lines.horizontal * viewport.zoom + viewport.y,
          }}
        />
      ) : null}
    </div>
  )
}

// 左侧节点面板负责提供可拖拽的流程节点模板。
function DesignerPalette({
  onAppendNode,
  onAppendPreset,
}: {
  onAppendNode: (template: WorkflowNodeTemplate) => void
  onAppendPreset: (preset: DesignerStructurePreset['id']) => void
}) {
  const advancedStructureKinds: WorkflowNodeTemplate['kind'][] = [
    'subprocess',
    'dynamic-builder',
    'inclusive',
    'parallel',
  ]
  const isAdvancedStructureTemplate = (kind: WorkflowNodeTemplate['kind']) =>
    advancedStructureKinds.includes(kind)
  const advancedTemplates = workflowNodeTemplates.filter((template) =>
    isAdvancedStructureTemplate(template.kind)
  )
  const baseTemplates = workflowNodeTemplates.filter(
    (template) => !isAdvancedStructureTemplate(template.kind)
  )
  const allNodeTemplates = [...baseTemplates, ...advancedTemplates]

  const renderTemplateButton = (
    template: WorkflowNodeTemplate,
    options?: { compact?: boolean }
  ) => {
    const Icon = template.icon

    return (
      <button
        key={template.kind}
        type='button'
        draggable
        onDoubleClick={() => onAppendNode(template)}
        onDragStart={(event) => {
          event.dataTransfer.effectAllowed = 'move'
          event.dataTransfer.setData(
            'application/west-flow-node',
            template.kind
          )
        }}
        className={cn(
          'w-full rounded-2xl border bg-background/90 text-left transition',
          'hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-sm',
          options?.compact ? 'p-3' : 'p-3.5'
        )}
      >
        <div className='flex items-center gap-3'>
          <div
            className={cn(
              'flex size-11 shrink-0 items-center justify-center rounded-2xl',
              paletteToneClassNames[template.tone]
            )}
          >
            <Icon className='size-5' />
          </div>
          <div className='min-w-0 flex-1'>
            <div className='flex items-center justify-between gap-2'>
              <span className='truncate text-sm font-semibold leading-5'>
                {template.label}
              </span>
              <div className='flex items-center gap-2'>
                <span className='rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground'>
                  {paletteKindBadgeLabels[template.kind]}
                </span>
                <MoveDiagonal2 className='size-3.5 shrink-0 text-muted-foreground' />
              </div>
            </div>
          </div>
        </div>
      </button>
    )
  }

  return (
    <Card className='h-full border-0 bg-transparent shadow-none'>
      <CardHeader className='space-y-2 px-1 pt-1'>
        <CardTitle className='flex items-center gap-2 text-base'>
          <Waypoints className='size-4' />
          节点面板
        </CardTitle>
      </CardHeader>
      <CardContent className='flex flex-col gap-4 px-1 pb-1'>
        <section className='space-y-3'>
          <div className='px-1'>
            <p className='text-sm font-semibold'>节点</p>
          </div>
          <div className='space-y-2'>
            {allNodeTemplates.map((template) =>
              renderTemplateButton(template, { compact: true })
            )}
          </div>
        </section>

        <Separator />

        <section className='space-y-3'>
          <div className='px-1'>
            <p className='text-sm font-semibold'>模板</p>
          </div>
          <div className='space-y-2'>
            {designerStructurePresets.map((preset) => (
              <button
                key={preset.id}
                type='button'
                className='w-full rounded-2xl border bg-background/95 px-3 py-3 text-left transition hover:border-primary/40 hover:bg-primary/[0.03]'
                onClick={() => onAppendPreset(preset.id)}
              >
                <div className='flex items-center justify-between gap-3'>
                  <p className='text-sm font-medium'>{preset.title}</p>
                  <Badge variant='outline'>模板</Badge>
                </div>
              </button>
            ))}
          </div>
        </section>
      </CardContent>
    </Card>
  )
}

// 设计器工作区把画布、右侧配置和保存发布动作串起来。
function WorkflowDesignerWorkspace({
  processDefinitionId,
}: {
  processDefinitionId?: string
}) {
  const navigate = designerRoute.useNavigate()
  const nodes = useWorkflowDesignerStore((state) => state.history.present.nodes)
  const edges = useWorkflowDesignerStore((state) => state.history.present.edges)
  const selectedNodeId = useWorkflowDesignerStore(
    (state) => state.history.present.selectedNodeId
  )
  const snapshot = useWorkflowDesignerStore((state) => state.history.present)
  const helperLines = useWorkflowDesignerStore((state) => state.helperLines)
  const canUndo = useWorkflowDesignerStore(
    (state) => state.history.past.length > 0
  )
  const canRedo = useWorkflowDesignerStore(
    (state) => state.history.future.length > 0
  )
  const setSelectedNodeId = useWorkflowDesignerStore(
    (state) => state.setSelectedNodeId
  )
  const updateNodeDraft = useWorkflowDesignerStore(
    (state) => state.updateNodeDraft
  )
  const setHelperLines = useWorkflowDesignerStore(
    (state) => state.setHelperLines
  )
  const applyNodeChanges = useWorkflowDesignerStore(
    (state) => state.applyNodeChanges
  )
  const applyEdgeChanges = useWorkflowDesignerStore(
    (state) => state.applyEdgeChanges
  )
  const connectNodes = useWorkflowDesignerStore((state) => state.connectNodes)
  const addNodeFromTemplate = useWorkflowDesignerStore(
    (state) => state.addNodeFromTemplate
  )
  const autoLayout = useWorkflowDesignerStore((state) => state.autoLayout)
  const addStructurePreset = useWorkflowDesignerStore(
    (state) => state.addStructurePreset
  )
  const hydrateSnapshot = useWorkflowDesignerStore(
    (state) => state.hydrateSnapshot
  )
  const resetDesigner = useWorkflowDesignerStore((state) => state.resetDesigner)
  const undo = useWorkflowDesignerStore((state) => state.undo)
  const redo = useWorkflowDesignerStore((state) => state.redo)
  const reactFlow = useReactFlow<WorkflowNode>()
  const queryClient = useQueryClient()
  const viewport = useViewport()
  const [activePropertyTab, setActivePropertyTab] = useState<'flow' | 'node'>(
    'flow'
  )
  const [definitionMetaOverrides, setDefinitionMetaOverrides] = useState<
    Partial<ProcessDefinitionMeta>
  >({})
  const designerHydratedRef = useRef(false)
  const draftKey = useMemo(
    () => buildDesignerDraftKey(processDefinitionId),
    [processDefinitionId]
  )
  const canvasEdges = useMemo(
    () => edges.map((edge) => ({ ...edge, type: 'quickInsert' })),
    [edges]
  )

  const detailQuery = useQuery({
    queryKey: ['process-definition-detail', processDefinitionId],
    queryFn: () => getProcessDefinitionDetail(processDefinitionId ?? ''),
    enabled: Boolean(processDefinitionId),
  })

  const definitionMeta = useMemo<ProcessDefinitionMeta>(() => {
    const baseMeta =
      processDefinitionId
        ? detailQuery.data === undefined
          ? emptyDefinitionMeta
          : {
              processKey: detailQuery.data.processKey,
              processName: detailQuery.data.processName,
              category: detailQuery.data.category,
              processFormKey: detailQuery.data.dsl.processFormKey,
              processFormVersion: detailQuery.data.dsl.processFormVersion,
              formFields: detailQuery.data.dsl.formFields ?? [],
            }
        : defaultDefinitionMeta

    return {
      ...baseMeta,
      ...definitionMetaOverrides,
    }
  }, [definitionMetaOverrides, detailQuery.data, processDefinitionId])
  const definitionMetaRef = useRef(definitionMeta)

  useEffect(() => {
    definitionMetaRef.current = definitionMeta
  }, [definitionMeta])

  const saveMutation = useMutation({
    mutationFn: async () =>
      saveProcessDefinition(
        workflowSnapshotToProcessDefinitionDsl(
          useWorkflowDesignerStore.getState().history.present,
          definitionMetaRef.current
        )
      ),
    onSuccess: (response) => {
      queryClient.setQueryData(
        ['process-definition-detail', response.processDefinitionId],
        response
      )
      hydrateSnapshot(processDefinitionDetailToWorkflowSnapshot(response))
      setDefinitionMetaOverrides({})
      void navigate({
        search: (previous) => ({
          ...previous,
          processDefinitionId: response.processDefinitionId,
        }),
      })
      toast.success('流程草稿已保存到数据库。')
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, '保存草稿失败，请稍后重试。'))
    },
  })

  const publishMutation = useMutation({
    mutationFn: async () =>
      publishProcessDefinition(
        workflowSnapshotToProcessDefinitionDsl(
          useWorkflowDesignerStore.getState().history.present,
          definitionMetaRef.current
        )
      ),
    onSuccess: (response) => {
      queryClient.setQueryData(
        ['process-definition-detail', response.processDefinitionId],
        response
      )
      hydrateSnapshot(processDefinitionDetailToWorkflowSnapshot(response))
      setDefinitionMetaOverrides({})
      void navigate({
        search: (previous) => ({
          ...previous,
          processDefinitionId: response.processDefinitionId,
        }),
      })
      toast.success(`流程已发布为 ${response.processDefinitionId}。`)
    },
    onError: (error) => {
      toast.error(getApiErrorMessage(error, '发布流程失败，请稍后重试。'))
    },
  })

  const selectedNode = useMemo(
    () => nodes.find((node) => node.id === selectedNodeId) ?? null,
    [nodes, selectedNodeId]
  )

  useEffect(() => {
    designerHydratedRef.current = false
    const persistedDraft = readPersistedDesignerDraft(draftKey)

    if (!processDefinitionId) {
      if (persistedDraft) {
        hydrateSnapshot(persistedDraft.snapshot)
      } else {
        resetDesigner()
      }
      designerHydratedRef.current = true
      const timer = window.setTimeout(() => {
        if (persistedDraft?.viewport) {
          reactFlow.setViewport(persistedDraft.viewport, { duration: 0 })
          return
        }
        reactFlow.fitView({ padding: 0.18, duration: 350 })
      }, 120)

      return () => window.clearTimeout(timer)
    }

    if (!detailQuery.data) {
      hydrateSnapshot({
        nodes: [],
        edges: [],
        selectedNodeId: null,
      })
      return
    }

    hydrateSnapshot(processDefinitionDetailToWorkflowSnapshot(detailQuery.data))
    designerHydratedRef.current = true
    const timer = window.setTimeout(() => {
      reactFlow.fitView({ padding: 0.18, duration: 350 })
    }, 120)

    return () => window.clearTimeout(timer)
  }, [
    detailQuery.data,
    draftKey,
    hydrateSnapshot,
    processDefinitionId,
    reactFlow,
    resetDesigner,
  ])

  useEffect(() => {
    if (!designerHydratedRef.current || processDefinitionId) {
      return
    }
    writePersistedDesignerDraft(draftKey, {
      snapshot,
      viewport: viewport
        ? {
            x: viewport.x,
            y: viewport.y,
            zoom: viewport.zoom,
          }
        : null,
    })
  }, [draftKey, processDefinitionId, snapshot, viewport])

  const handleConnect: OnConnect = (connection) => {
    connectNodes(connection)
  }

  function handleDrop(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault()
    const kind = event.dataTransfer.getData('application/west-flow-node')
    const template = workflowNodeTemplates.find(
      (item) => item.kind === kind
    )

    if (!template) {
      return
    }

    const position = reactFlow.screenToFlowPosition({
      x: event.clientX,
      y: event.clientY,
    })

    addNodeFromTemplate(template, position)
  }

  function handleAppendNode(template: WorkflowNodeTemplate) {
    const columnX = 120 + (nodes.length % 2) * 260
    const rowY = 80 + nodes.length * 110

    addNodeFromTemplate(template, { x: columnX, y: rowY })
  }

  return (
    <PageShell
      title='流程设计器'
      description='流程设计、布局调整和属性配置都在同一屏完成。'
      actions={
        <>
          <Button
            variant='outline'
            onClick={() => {
              void navigate({ to: '/workflow/definitions/list' })
            }}
          >
            返回流程定义
          </Button>
          <Button
            variant='outline'
            disabled={!canUndo}
            onClick={() => undo()}
          >
            <Undo2 data-icon='inline-start' />
            撤销
          </Button>
          <Button
            variant='outline'
            disabled={!canRedo}
            onClick={() => redo()}
          >
            <Redo2 data-icon='inline-start' />
            重做
          </Button>
          <Button
            variant='outline'
            onClick={() =>
              startTransition(() => {
                autoLayout()
                window.setTimeout(() => {
                  reactFlow.fitView({ padding: 0.18, duration: 260 })
                }, 80)
              })
            }
          >
            <LayoutGrid data-icon='inline-start' />
            自动整理
          </Button>
          <Button
            disabled={saveMutation.isPending}
            onClick={() => {
              saveMutation.mutate()
            }}
          >
            <Save data-icon='inline-start' />
            {saveMutation.isPending ? '保存中...' : '保存草稿'}
          </Button>
          <Button
            disabled={publishMutation.isPending}
            onClick={() => {
              publishMutation.mutate()
            }}
          >
            <Sparkles data-icon='inline-start' />
            {publishMutation.isPending ? '发布中...' : '发布流程'}
          </Button>
        </>
      }
    >
      <WorkflowDesignerLayout
        palette={
          <DesignerPalette
            onAppendNode={handleAppendNode}
            onAppendPreset={addStructurePreset}
          />
        }
        canvas={
          <Card className='flex h-full min-h-0 flex-col border-primary/10 bg-[radial-gradient(circle_at_top,_rgba(56,189,248,0.08),_transparent_46%)]'>
            <CardHeader className='flex flex-row items-start justify-between gap-4 space-y-0'>
              <div className='space-y-1'>
                <CardTitle>画布工作区</CardTitle>
              </div>
              <div className='flex flex-wrap gap-2'>
                <Badge variant='secondary'>
                  <CircleDotDashed />
                  网格吸附
                </Badge>
                <Badge variant='secondary'>
                  <Sparkles />
                  辅助线
                </Badge>
                <Badge variant='secondary'>
                  <Bot />
                  AI 设计预留
                </Badge>
              </div>
            </CardHeader>
            <CardContent className='flex min-h-0 flex-1'>
              <div
                className='relative h-full min-h-0 flex-1 overflow-hidden rounded-2xl border bg-background/85'
                onDragOver={(event) => {
                  event.preventDefault()
                  event.dataTransfer.dropEffect = 'move'
                }}
                onDrop={handleDrop}
              >
                <GuideLinesOverlay lines={helperLines} />
                <ReactFlow
                  nodes={nodes}
                  edges={canvasEdges}
                  nodeTypes={workflowNodeTypes}
                  edgeTypes={workflowEdgeTypes}
                  onNodesChange={applyNodeChanges}
                  onEdgesChange={applyEdgeChanges}
                  onConnect={handleConnect}
                  onNodeClick={(_, node) => {
                    setSelectedNodeId(node.id)
                    setActivePropertyTab('node')
                  }}
                  onPaneClick={() => {
                    setSelectedNodeId(null)
                  }}
                  onNodeDrag={(_, node) => {
                    setHelperLines(findHelperLines(node, nodes))
                  }}
                  onNodeDragStop={() =>
                    setHelperLines({ vertical: null, horizontal: null })
                  }
                  minZoom={0.45}
                  maxZoom={1.6}
                  snapToGrid
                  snapGrid={[20, 20]}
                  proOptions={{ hideAttribution: true }}
                  defaultEdgeOptions={{
                    type: 'quickInsert',
                    animated: false,
                  }}
                >
                  <MiniMap
                    pannable
                    zoomable
                    nodeBorderRadius={16}
                    className='rounded-xl border bg-background/90'
                  />
                  <Controls position='bottom-left' showInteractive={false} />
                  <Background
                    variant={BackgroundVariant.Dots}
                    gap={20}
                    size={1.2}
                  />
                </ReactFlow>
              </div>
            </CardContent>
          </Card>
        }
        flowSettings={
          <Card className='bg-card/95'>
            <CardHeader className='space-y-1'>
              <CardTitle>流程配置</CardTitle>
            </CardHeader>
            <CardContent className='flex flex-col gap-4'>
              <div className='grid gap-3'>
                <div className='grid gap-2'>
                  <Label htmlFor='processKey'>流程编码</Label>
                  <Input
                    id='processKey'
                    value={definitionMeta.processKey}
                    onChange={(event) =>
                      setDefinitionMetaOverrides((previous) => ({
                        ...previous,
                        processKey: event.target.value,
                      }))
                    }
                  />
                </div>
                <div className='grid gap-2'>
                  <Label htmlFor='processName'>流程名称</Label>
                  <Input
                    id='processName'
                    value={definitionMeta.processName}
                    onChange={(event) =>
                      setDefinitionMetaOverrides((previous) => ({
                        ...previous,
                        processName: event.target.value,
                      }))
                    }
                  />
                </div>
                <div className='grid gap-2'>
                  <Label htmlFor='category'>业务域</Label>
                  <Input
                    id='category'
                    value={definitionMeta.category}
                    onChange={(event) =>
                      setDefinitionMetaOverrides((previous) => ({
                        ...previous,
                        category: event.target.value,
                      }))
                    }
                  />
                </div>
              </div>
              <ProcessFormSelector
                label='流程默认表单'
                value={
                  definitionMeta.processFormKey && definitionMeta.processFormVersion
                    ? {
                        processFormKey: definitionMeta.processFormKey,
                        processFormVersion: definitionMeta.processFormVersion,
                      }
                    : null
                }
                onChange={(selection) =>
                  setDefinitionMetaOverrides((previous) => ({
                    ...previous,
                    processFormKey: selection?.processFormKey ?? '',
                    processFormVersion: selection?.processFormVersion ?? '',
                  }))
                }
              />
            </CardContent>
          </Card>
        }
        nodeSettings={
          <div className='text-sm'>
            <NodeConfigPanel
              node={selectedNode}
              edges={edges}
              processFormFields={definitionMeta.formFields}
              onApply={updateNodeDraft}
            />
          </div>
        }
        activeTab={activePropertyTab}
        onActiveTabChange={setActivePropertyTab}
      />
    </PageShell>
  )
}

// 页面入口只负责挂载 React Flow 容器和工作区组件。
export function WorkflowDesignerPage() {
  const search = designerRoute.useSearch()

  return (
    <ReactFlowProvider>
      <WorkflowDesignerWorkspace
        key={search.processDefinitionId ?? 'new'}
        processDefinitionId={search.processDefinitionId}
      />
    </ReactFlowProvider>
  )
}
