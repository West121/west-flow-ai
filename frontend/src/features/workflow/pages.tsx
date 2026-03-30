import {
  startTransition,
  useEffect,
  useCallback,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
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
  Eye,
  LayoutGrid,
  MousePointer2,
  MoveDiagonal2,
  PlugZap,
  Redo2,
  Save,
  Sparkles,
  Undo2,
  Waypoints,
} from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { LongText } from '@/components/long-text'
import { useTheme } from '@/context/theme-provider'
import { useAuthStore } from '@/stores/auth-store'
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
  resolveRuntimeProcessFormFields,
} from '@/features/forms/runtime/form-component-registry'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import { normalizeListQuerySearch } from '@/features/shared/table/query-contract'
import {
  type ProcessDefinitionMeta,
  processDefinitionDetailToWorkflowSnapshot,
  workflowSnapshotToProcessDefinitionDsl,
} from './designer/dsl'
import {
  ProcessFormSelector,
} from './designer/form-selection'
import { WorkflowDesignerLayout } from './designer/designer-layout'
import { resolveWorkflowDesignerPeers, resolveWorkflowDesignerPeerColor } from './designer-collab/awareness'
import { createWorkflowDesignerCollaborationBinding } from './designer-collab/bindings'
import { createWorkflowDesignerCollaborationProvider } from './designer-collab/provider'
import {
  type WorkflowDesignerCollaborationMode,
  type WorkflowDesignerCollaborationPeer,
  type WorkflowDesignerCollaborationStatus,
} from './designer-collab/types'
import { createWorkflowDesignerYDoc } from './designer-collab/ydoc'
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
  supervise: '督办',
  meeting: '会办',
  read: '阅办',
  circulate: '传阅',
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
// 默认先挂一套请假流程表单，保证设计器打开后就能直接预览和发布。
const defaultProcessForm = findProcessRuntimeFormByProcessKey('oa_leave')
const defaultLeaveFormFields: ProcessDefinitionMeta['formFields'] =
  resolveRuntimeProcessFormFields('oa_leave')
function resolveWorkflowDesignerCollaborationUrl() {
  const explicitUrl = import.meta.env.VITE_WORKFLOW_COLLAB_URL?.trim()
  if (explicitUrl) {
    return explicitUrl
  }

  if (import.meta.env.DEV && typeof window !== 'undefined') {
    const { hostname, protocol } = window.location
    if (hostname === '127.0.0.1' || hostname === 'localhost') {
      return `${protocol === 'https:' ? 'wss' : 'ws'}://127.0.0.1:1235`
    }
  }

  return ''
}

const workflowDesignerCollaborationUrl = resolveWorkflowDesignerCollaborationUrl()
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
      <div className='flex min-w-0 flex-col gap-1'>
        <LongText className='max-w-full'>
          <Link
            to='/workflow/designer'
            search={{ processDefinitionId: row.original.processDefinitionId }}
            className='font-medium text-primary hover:underline'
          >
            {row.original.processName}
          </Link>
        </LongText>
        <LongText className='max-w-full text-xs text-muted-foreground'>
          {row.original.processDefinitionId}
        </LongText>
      </div>
    ),
  },
  {
    accessorKey: 'processKey',
    header: '流程编码',
    cell: ({ row }) => (
      <LongText className='max-w-full'>{row.original.processKey}</LongText>
    ),
  },
  {
    accessorKey: 'version',
    header: '版本',
  },
  {
    accessorKey: 'category',
    header: '业务域',
    cell: ({ row }) => (
      <LongText className='max-w-full'>{row.original.category}</LongText>
    ),
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
  const search = normalizeListQuerySearch(definitionsRoute.useSearch())
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
      total={query.data?.total}
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

function RemoteCursorOverlay({
  peers,
}: {
  peers: WorkflowDesignerCollaborationPeer[]
}) {
  const viewport = useViewport()
  const cursorPeers = peers.filter((peer) => peer.cursor)

  return (
    <div className='pointer-events-none absolute inset-0 z-30 overflow-hidden'>
      {cursorPeers.map((peer) => {
        const cursor = peer.cursor
        if (!cursor) {
          return null
        }
        const left = cursor.x * viewport.zoom + viewport.x
        const top = cursor.y * viewport.zoom + viewport.y
        return (
          <div
            key={`cursor-${peer.clientId}`}
            className='absolute'
            style={{
              left,
              top,
              transform: 'translate(10px, -4px)',
            }}
          >
            <div
              className='flex items-start gap-2'
              style={{ color: peer.color }}
            >
              <MousePointer2 className='size-4 fill-current' />
              <span
                className='rounded-full px-2 py-0.5 text-[11px] font-medium text-white shadow-sm'
                style={{ backgroundColor: peer.color }}
              >
                {peer.displayName}
              </span>
            </div>
          </div>
        )
      })}
    </div>
  )
}

function buildAvatarFallback(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((segment) => segment[0]?.toUpperCase() ?? '')
    .join('')
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
  mode = 'edit',
}: {
  processDefinitionId?: string
  mode?: 'edit' | 'view'
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
  const { resolvedTheme } = useTheme()
  const queryClient = useQueryClient()
  const viewport = useViewport()
  const accessToken = useAuthStore((state) => state.accessToken)
  const currentUser = useAuthStore((state) => state.currentUser)
  const isReadOnly = mode === 'view'
  const [activePropertyTab, setActivePropertyTab] = useState<'flow' | 'node'>(
    'flow'
  )
  const [definitionMetaOverrides, setDefinitionMetaOverrides] = useState<
    Partial<ProcessDefinitionMeta>
  >({})
  const designerHydratedRef = useRef(false)
  const cursorRef = useRef<{ x: number; y: number } | null>(null)
  const collaborationProviderRef = useRef<ReturnType<
    typeof createWorkflowDesignerCollaborationProvider
  > | null>(null)
  const collaborationBindingRef = useRef<ReturnType<
    typeof createWorkflowDesignerCollaborationBinding
  > | null>(null)
  const [collaborationStatus, setCollaborationStatus] =
    useState<WorkflowDesignerCollaborationStatus>('local')
  const [collaborationOfflineNoticeVisible, setCollaborationOfflineNoticeVisible] =
    useState(false)
  const [collaborationPeers, setCollaborationPeers] = useState<
    WorkflowDesignerCollaborationPeer[]
  >([])
  const collaborationPeersRef = useRef<WorkflowDesignerCollaborationPeer[]>([])
  const draftKey = useMemo(
    () => buildDesignerDraftKey(processDefinitionId),
    [processDefinitionId]
  )
  const collaborationRoomId = useMemo(
    () =>
      processDefinitionId
        ? `workflow-designer:${processDefinitionId}`
        : `workflow-designer:draft:${draftKey}`,
    [draftKey, processDefinitionId]
  )
  const collaborationMode: WorkflowDesignerCollaborationMode =
    workflowDesignerCollaborationUrl ? 'websocket' : 'broadcast'

  const detailQuery = useQuery({
    queryKey: ['process-definition-detail', processDefinitionId],
    queryFn: () => getProcessDefinitionDetail(processDefinitionId ?? ''),
    enabled: Boolean(processDefinitionId),
  })
  const collaborationReady = !processDefinitionId || Boolean(detailQuery.data)
  const canvasNodes = useMemo(
    () =>
      nodes.map((node) => {
        const selectedBy = collaborationPeers.filter(
          (peer) => peer.selectedNodeId === node.id
        )
        const editingBy = collaborationPeers.filter(
          (peer) => peer.editingNodeId === node.id
        )

        return {
          ...node,
          data: {
            ...node.data,
            collaboration: {
              selectedBy,
              editingBy,
            },
          },
        }
      }),
    [collaborationPeers, nodes]
  )
  const canvasEdges = useMemo(
    () => edges.map((edge) => ({ ...edge, type: 'quickInsert' })),
    [edges]
  )

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

  useEffect(() => {
    collaborationPeersRef.current = collaborationPeers
  }, [collaborationPeers])

  useEffect(() => {
    const shouldDelayShow =
      collaborationMode === 'websocket' &&
      collaborationStatus !== 'connected' &&
      collaborationStatus !== 'connecting'

    const timer = window.setTimeout(
      () => {
        setCollaborationOfflineNoticeVisible(shouldDelayShow)
      },
      shouldDelayShow ? 1500 : 0
    )

    return () => window.clearTimeout(timer)
  }, [collaborationMode, collaborationStatus])

  useEffect(() => {
    if (!collaborationReady) {
      return
    }

    const doc = createWorkflowDesignerYDoc()
    const provider = createWorkflowDesignerCollaborationProvider(
      collaborationRoomId,
      doc,
      {
        serverUrl: workflowDesignerCollaborationUrl,
        authToken: accessToken,
      }
    )
    const binding = createWorkflowDesignerCollaborationBinding({
      doc,
      getSnapshot: () => useWorkflowDesignerStore.getState().history.present,
      getDefinitionMeta: () => definitionMetaRef.current,
      applyRemoteSnapshot: (sharedSnapshot) => {
        useWorkflowDesignerStore.getState().applyRemoteSnapshot(sharedSnapshot)
      },
      applyRemoteDefinitionMeta: (meta) => {
        setDefinitionMetaOverrides(meta)
      },
    })
    const removeStatusListener = provider.onStatusChange(setCollaborationStatus)
    const updatePeers = () => {
      const nextPeers = resolveWorkflowDesignerPeers(
        provider.awareness,
        currentUser?.userId ?? null
      )
      const previousPeers = collaborationPeersRef.current
      const unchanged =
        previousPeers.length === nextPeers.length &&
        previousPeers.every(
          (peer, index) =>
            peer.userId === nextPeers[index]?.userId &&
            peer.clientId === nextPeers[index]?.clientId &&
            peer.selectedNodeId === nextPeers[index]?.selectedNodeId &&
            peer.editingNodeId === nextPeers[index]?.editingNodeId &&
            peer.color === nextPeers[index]?.color &&
            peer.displayName === nextPeers[index]?.displayName &&
            peer.cursor?.x === nextPeers[index]?.cursor?.x &&
            peer.cursor?.y === nextPeers[index]?.cursor?.y
        )

      if (!unchanged) {
        setCollaborationPeers(nextPeers)
      }
    }

    provider.awareness.on('change', updatePeers)
    updatePeers()

    collaborationProviderRef.current = provider
    collaborationBindingRef.current = binding

    return () => {
      provider.awareness.off('change', updatePeers)
      removeStatusListener()
      binding.destroy()
      provider.destroy()
      doc.destroy()
      collaborationProviderRef.current = null
      collaborationBindingRef.current = null
      collaborationPeersRef.current = []
    }
  }, [accessToken, collaborationReady, collaborationRoomId, currentUser?.userId])

  useEffect(() => {
    collaborationBindingRef.current?.syncLocalState()
  }, [snapshot, definitionMeta])

  const pushLocalAwareness = useCallback(() => {
    const provider = collaborationProviderRef.current
    if (!provider || !currentUser?.userId) {
      return
    }

    provider.setLocalState({
      userId: currentUser.userId,
      displayName: currentUser.displayName,
      color: resolveWorkflowDesignerPeerColor(currentUser.userId),
      selectedNodeId,
      editingNodeId: !isReadOnly && activePropertyTab === 'node' ? selectedNodeId : null,
      cursor: cursorRef.current,
    })
  }, [
    activePropertyTab,
    currentUser,
    isReadOnly,
    selectedNodeId,
  ])

  useEffect(() => {
    pushLocalAwareness()
  }, [pushLocalAwareness])

  const collaborationStatusBadge = useMemo(() => {
    const label = (() => {
      if (collaborationMode === 'broadcast') {
        return collaborationPeers.length > 0
          ? `本地协同 ${collaborationPeers.length + 1}`
          : '本地协同'
      }
      return collaborationStatus === 'connected'
        ? `协同在线 ${collaborationPeers.length + 1}`
        : collaborationStatus === 'connecting'
          ? '协同连接中'
          : collaborationStatus === 'reconnecting' ||
              collaborationStatus === 'disconnected'
            ? '协同离线'
            : '本地协同'
    })()
    return <Badge variant='secondary'>{label}</Badge>
  }, [collaborationMode, collaborationPeers.length, collaborationStatus])

  const selectedNodeEditingPeers = useMemo(
    () =>
      selectedNodeId
        ? collaborationPeers.filter((peer) => peer.editingNodeId === selectedNodeId)
        : [],
    [collaborationPeers, selectedNodeId]
  )

  const collaborationSummary = useMemo(() => {
    const peers = collaborationPeers.slice(0, 4)

    return (
      <div className='flex flex-wrap items-center justify-end gap-2'>
        {peers.length > 0 ? (
          <div className='flex items-center -space-x-2'>
            {peers.map((peer) => (
              <Avatar
                key={peer.userId}
                className='size-7 border-2 border-background'
                style={{
                  boxShadow: `0 0 0 1px ${peer.color} inset`,
                }}
              >
                <AvatarFallback
                  style={{
                    backgroundColor: `${peer.color}20`,
                    color: peer.color,
                  }}
                >
                  {buildAvatarFallback(peer.displayName)}
                </AvatarFallback>
              </Avatar>
            ))}
          </div>
        ) : null}
        {collaborationStatusBadge}
        {isReadOnly ? (
          <Badge variant='outline'>
            <Eye />
            只读观摩
          </Badge>
        ) : null}
      </div>
    )
  }, [collaborationPeers, collaborationStatusBadge, isReadOnly])

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

  useLayoutEffect(() => {
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
            className={isReadOnly ? 'hidden' : undefined}
          >
            <Undo2 data-icon='inline-start' />
            撤销
          </Button>
          <Button
            variant='outline'
            disabled={!canRedo}
            onClick={() => redo()}
            className={isReadOnly ? 'hidden' : undefined}
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
            className={isReadOnly ? 'hidden' : undefined}
          >
            <LayoutGrid data-icon='inline-start' />
            自动整理
          </Button>
          <Button
            disabled={saveMutation.isPending}
            onClick={() => {
              saveMutation.mutate()
            }}
            className={isReadOnly ? 'hidden' : undefined}
          >
            <Save data-icon='inline-start' />
            {saveMutation.isPending ? '保存中...' : '保存草稿'}
          </Button>
          <Button
            disabled={publishMutation.isPending}
            onClick={() => {
              publishMutation.mutate()
            }}
            className={isReadOnly ? 'hidden' : undefined}
          >
            <Sparkles data-icon='inline-start' />
            {publishMutation.isPending ? '发布中...' : '发布流程'}
          </Button>
        </>
      }
    >
      <WorkflowDesignerLayout
        palette={
          <div className={isReadOnly ? 'relative' : undefined}>
            {isReadOnly ? (
              <div className='absolute inset-0 z-10 rounded-2xl bg-background/40 backdrop-blur-[1px]' />
            ) : null}
            <DesignerPalette
              onAppendNode={isReadOnly ? (_template) => undefined : handleAppendNode}
              onAppendPreset={isReadOnly ? (_preset) => undefined : addStructurePreset}
            />
          </div>
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
                onDragOver={
                  isReadOnly
                    ? undefined
                    : (event) => {
                        event.preventDefault()
                        event.dataTransfer.dropEffect = 'move'
                      }
                }
                onDrop={isReadOnly ? undefined : handleDrop}
                onMouseMove={(event) => {
                  const position = reactFlow.screenToFlowPosition({
                    x: event.clientX,
                    y: event.clientY,
                  })
                  cursorRef.current = position
                  pushLocalAwareness()
                }}
                onMouseLeave={() => {
                  cursorRef.current = null
                  pushLocalAwareness()
                }}
              >
                <GuideLinesOverlay lines={helperLines} />
                <RemoteCursorOverlay peers={collaborationPeers} />
                {collaborationMode === 'websocket' &&
                collaborationOfflineNoticeVisible ? (
                  <div className='absolute inset-x-4 top-4 z-40 rounded-2xl border border-amber-300 bg-amber-50/95 px-4 py-3 text-sm text-amber-900 shadow-sm dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200'>
                    <div className='flex items-center justify-between gap-4'>
                      <div>
                        <div className='flex items-center gap-2 font-medium'>
                          <PlugZap className='size-4' />
                          协同连接异常
                        </div>
                        <p className='mt-1 text-xs text-amber-800/80 dark:text-amber-200/80'>
                          当前仍可继续本地编辑，系统正在尝试恢复协同连接。
                        </p>
                      </div>
                      <Button
                        size='sm'
                        variant='outline'
                        className='border-amber-300 bg-transparent text-amber-900 hover:bg-amber-100 dark:border-amber-700 dark:text-amber-100 dark:hover:bg-amber-900/40'
                        onClick={() => collaborationProviderRef.current?.reconnect()}
                      >
                        <PlugZap className='size-4' />
                        立即重试
                      </Button>
                    </div>
                  </div>
                ) : null}
                <ReactFlow
                  nodes={canvasNodes}
                  edges={canvasEdges}
                  nodeTypes={workflowNodeTypes}
                  edgeTypes={workflowEdgeTypes}
                  onNodesChange={isReadOnly ? undefined : applyNodeChanges}
                  onEdgesChange={isReadOnly ? undefined : applyEdgeChanges}
                  onConnect={isReadOnly ? undefined : handleConnect}
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
                  nodesDraggable={!isReadOnly}
                  nodesConnectable={!isReadOnly}
                  elementsSelectable
                  snapToGrid
                  snapGrid={[20, 20]}
                  colorMode={resolvedTheme}
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
          <div className={isReadOnly ? 'relative' : undefined}>
            {isReadOnly ? (
              <div className='absolute inset-0 z-10 rounded-2xl bg-background/40 backdrop-blur-[1px]' />
            ) : null}
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
          </div>
        }
        nodeSettings={
          <div className={cn('text-sm', isReadOnly ? 'relative' : undefined)}>
            {selectedNodeEditingPeers.length > 0 ? (
              <div className='mb-3 rounded-2xl border border-amber-300 bg-amber-50/90 px-3 py-2 text-xs text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200'>
                {selectedNodeEditingPeers.map((peer) => peer.displayName).join('、')}
                正在编辑当前节点，保存时可能覆盖对方改动。
              </div>
            ) : null}
            {isReadOnly ? (
              <div className='absolute inset-0 z-10 rounded-2xl bg-background/40 backdrop-blur-[1px]' />
            ) : null}
            <NodeConfigPanel
              node={selectedNode}
              edges={edges}
              processFormFields={definitionMeta.formFields}
              onApply={isReadOnly ? () => undefined : updateNodeDraft}
            />
          </div>
        }
        collaborationStatus={collaborationSummary}
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
        key={`${search.processDefinitionId ?? 'new'}:${search.mode ?? 'edit'}`}
        processDefinitionId={search.processDefinitionId}
        mode={search.mode}
      />
    </ReactFlowProvider>
  )
}
