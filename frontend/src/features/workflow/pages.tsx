import { startTransition, useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  Panel,
  ReactFlow,
  ReactFlowProvider,
  type Node,
  type OnConnect,
  useReactFlow,
  useViewport,
} from '@xyflow/react'
import {
  AlarmClockCheck,
  Bot,
  BringToFront,
  CircleDotDashed,
  LayoutGrid,
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
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { listProcessDefinitions } from '@/lib/api/workflow'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import {
  workflowNodeTemplates,
  type WorkflowNodeTemplate,
} from './designer/palette'
import { useWorkflowDesignerStore } from './designer/store'
import {
  type WorkflowHelperLines,
  type WorkflowNode,
} from './designer/types'
import { WorkflowNodeCard } from './designer/workflow-node'

import '@xyflow/react/dist/style.css'

const definitionsRoute = getRouteApi('/_authenticated/workflow/definitions/list')

type DefinitionRow = {
  processDefinitionId: string
  processName: string
  processKey: string
  version: string
  category: string
  createdAt: string
  status: '已发布' | '草稿'
}

const workflowNodeTypes = {
  workflow: WorkflowNodeCard,
}

const helperLineThreshold = 16

function resolveStatusLabel(status: string): DefinitionRow['status'] {
  return status === 'PUBLISHED' ? '已发布' : '草稿'
}

const definitionColumns: ColumnDef<DefinitionRow>[] = [
  {
    accessorKey: 'processName',
    header: '流程名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.processName}</span>
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

function DesignerPalette({
  onAppendNode,
}: {
  onAppendNode: (template: WorkflowNodeTemplate) => void
}) {
  return (
    <Card className='h-full border-dashed bg-card/95'>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <Waypoints className='size-4' />
          节点面板
        </CardTitle>
        <CardDescription>
          支持拖拽进画布，也可以双击快速追加到当前设计稿。
        </CardDescription>
      </CardHeader>
      <CardContent className='flex flex-col gap-3'>
        {workflowNodeTemplates.map((template) => {
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
              className='group rounded-2xl border bg-background/80 p-4 text-left transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-sm'
            >
              <div className='flex items-start gap-3'>
                <div className='flex size-10 items-center justify-center rounded-2xl bg-primary/10 text-primary'>
                  <Icon className='size-4' />
                </div>
                <div className='flex flex-1 flex-col gap-1'>
                  <div className='flex items-center justify-between gap-2'>
                    <span className='font-medium'>{template.label}</span>
                    <span className='text-[11px] text-muted-foreground'>
                      拖入
                    </span>
                  </div>
                  <p className='text-xs leading-5 text-muted-foreground'>
                    {template.description}
                  </p>
                </div>
              </div>
            </button>
          )
        })}
      </CardContent>
    </Card>
  )
}

function WorkflowDesignerWorkspace() {
  const nodes = useWorkflowDesignerStore((state) => state.history.present.nodes)
  const edges = useWorkflowDesignerStore((state) => state.history.present.edges)
  const selectedNodeId = useWorkflowDesignerStore(
    (state) => state.history.present.selectedNodeId
  )
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
  const undo = useWorkflowDesignerStore((state) => state.undo)
  const redo = useWorkflowDesignerStore((state) => state.redo)
  const reactFlow = useReactFlow<WorkflowNode>()

  const selectedNode = useMemo(
    () => nodes.find((node) => node.id === selectedNodeId) ?? null,
    [nodes, selectedNodeId]
  )

  useEffect(() => {
    const timer = window.setTimeout(() => {
      reactFlow.fitView({ padding: 0.18, duration: 350 })
    }, 120)

    return () => window.clearTimeout(timer)
  }, [reactFlow])

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
      description='独立设计工作区已经接入 React Flow，支持节点拖入、画布拖拽、连线、自动布局、undo/redo、MiniMap、视图控制和基础对齐辅助线。'
      actions={
        <>
          <Button
            variant='outline'
            onClick={() => {
              startTransition(() => {
                void reactFlow.fitView({ padding: 0.18, duration: 280 })
              })
            }}
          >
            <BringToFront data-icon='inline-start' />
            适配视图
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
            onClick={() => {
              toast.success('流程草稿已保存到本地设计状态。')
            }}
          >
            <Save data-icon='inline-start' />
            保存草稿
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)_320px]'>
        <DesignerPalette onAppendNode={handleAppendNode} />

        <Card className='border-primary/10 bg-[radial-gradient(circle_at_top,_rgba(56,189,248,0.08),_transparent_46%)]'>
          <CardHeader className='flex flex-row items-start justify-between gap-4'>
            <div className='space-y-2'>
              <CardTitle>画布工作区</CardTitle>
              <CardDescription>
                已启用网格吸附、辅助线提示与流程骨架节点，适合继续接入 DSL 校验与节点表单。
              </CardDescription>
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
          <CardContent>
            <div
              className='relative h-[680px] overflow-hidden rounded-2xl border bg-background/85'
              onDragOver={(event) => {
                event.preventDefault()
                event.dataTransfer.dropEffect = 'move'
              }}
              onDrop={handleDrop}
            >
              <GuideLinesOverlay lines={helperLines} />
              <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={workflowNodeTypes}
                onNodesChange={applyNodeChanges}
                onEdgesChange={applyEdgeChanges}
                onConnect={handleConnect}
                onNodeClick={(_, node) => setSelectedNodeId(node.id)}
                onPaneClick={() => setSelectedNodeId(null)}
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
                fitView
                proOptions={{ hideAttribution: true }}
                defaultEdgeOptions={{
                  type: 'smoothstep',
                  animated: false,
                }}
              >
                <Panel
                  position='top-left'
                  className='rounded-2xl border bg-background/90 px-3 py-2 text-xs text-muted-foreground shadow-sm'
                >
                  从左侧拖入节点，双击节点模板可快速追加
                </Panel>
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

        <div className='flex flex-col gap-4'>
          <Card className='bg-card/95'>
            <CardHeader>
              <CardTitle>流程配置</CardTitle>
              <CardDescription>
                基础信息、发布校验、超时审批和 AI 设计入口都会落在这个右侧区域。
              </CardDescription>
            </CardHeader>
            <CardContent className='flex flex-col gap-4'>
              <div className='grid gap-3 sm:grid-cols-2 xl:grid-cols-1'>
                <div className='rounded-2xl border p-4'>
                  <p className='text-xs text-muted-foreground'>当前节点数</p>
                  <p className='mt-2 text-2xl font-semibold'>{nodes.length}</p>
                </div>
                <div className='rounded-2xl border p-4'>
                  <p className='text-xs text-muted-foreground'>当前连线数</p>
                  <p className='mt-2 text-2xl font-semibold'>{edges.length}</p>
                </div>
              </div>
              <Separator />
              <div className='space-y-3 rounded-2xl border p-4 text-sm'>
                <div className='flex items-center gap-2 font-medium'>
                  <AlarmClockCheck className='size-4 text-primary' />
                  M0 设计基线
                </div>
                <p className='text-muted-foreground'>
                  已提供设计器画布、拖放、撤销重做、自动布局和流程定义列表联调。
                </p>
              </div>
            </CardContent>
          </Card>

          <Card className='bg-card/95'>
            <CardHeader>
              <CardTitle>节点配置</CardTitle>
              <CardDescription>
                当前先展示选中节点概览，后续再接代码表单与规则表达式。
              </CardDescription>
            </CardHeader>
            <CardContent className='flex flex-col gap-4 text-sm'>
              {selectedNode ? (
                <>
                  <div className='rounded-2xl border p-4'>
                    <div className='flex items-center justify-between gap-3'>
                      <div>
                        <p className='font-semibold'>
                          {selectedNode.data.label}
                        </p>
                        <p className='text-xs text-muted-foreground'>
                          节点编码：{selectedNode.id}
                        </p>
                      </div>
                      <Badge variant='secondary'>
                        {selectedNode.data.kind}
                      </Badge>
                    </div>
                    <p className='mt-3 text-muted-foreground'>
                      {selectedNode.data.description}
                    </p>
                  </div>
                  <div className='rounded-2xl border p-4'>
                    <p className='text-xs text-muted-foreground'>画布坐标</p>
                    <p className='mt-2 font-medium'>
                      X {Math.round(selectedNode.position.x)} / Y{' '}
                      {Math.round(selectedNode.position.y)}
                    </p>
                  </div>
                  <Button
                    variant='outline'
                  onClick={() => {
                      const flowJson = reactFlow.toObject()
                      void navigator.clipboard
                        .writeText(JSON.stringify(flowJson, null, 2))
                        .then(() => {
                          toast.success('当前流程 JSON 已复制到剪贴板。')
                        })
                        .catch(() => {
                          toast.error('复制失败，请稍后重试。')
                        })
                    }}
                  >
                    <Sparkles data-icon='inline-start' />
                    复制当前 JSON
                  </Button>
                </>
              ) : (
                <div className='rounded-2xl border border-dashed p-4 text-muted-foreground'>
                  请选择画布中的节点，右侧会展示节点概览、位置与后续表单入口。
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </PageShell>
  )
}

export function WorkflowDesignerPage() {
  return (
    <ReactFlowProvider>
      <WorkflowDesignerWorkspace />
    </ReactFlowProvider>
  )
}
