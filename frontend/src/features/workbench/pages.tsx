import {
  useEffect,
  useMemo,
  startTransition,
  useState,
  type ReactNode,
} from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Loader2,
  Play,
  Sparkles,
  Undo2,
  UserCheck2,
  UserRoundPlus,
} from 'lucide-react'
import { useForm, useWatch } from 'react-hook-form'
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
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { ContextualCopilotEntry } from '@/features/ai/context-entry'
import { PageShell } from '@/features/shared/page-shell'
import { UserPickerField } from '@/features/shared/pro-form'
import { ApprovalSheetBusinessSection } from '@/features/oa/detail-sections'
import {
  ProcessCollaborationSection,
  ProcessTerminationSection,
  ProcessTimeTravelSection,
} from '@/features/workflow/advanced-runtime-sections'
import { ApprovalSheetGraph } from '@/features/workbench/approval-sheet-graph'
import {
  ApprovalTagList,
  ApprovalUserTag,
} from '@/features/workbench/approval-actor-tags'
import {
  ApprovalSheetAutomationActionTimeline,
  ApprovalSheetAutomationStatusCard,
  NotificationSendRecordSection,
} from '@/features/workbench/automation-sections'
import {
  formatApprovalSheetText,
  resolveCountersignMemberStatusLabel,
  resolveCountersignModeLabel,
  resolveApprovalSheetActingModeLabel,
  resolveApprovalSheetResultLabel,
} from '@/features/workbench/approval-sheet-helpers'
import {
  createApprovalSheetColumns,
  summarizeApprovalSheets,
} from '@/features/workbench/approval-sheet-list'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { handleServerError } from '@/lib/handle-server-error'
import {
  RuntimeStructureSection,
} from '@/features/workflow/runtime-structure'
import { InclusiveGatewaySection } from '@/features/workflow/inclusive-gateway-section'
import {
  mergeRuntimeStructureLinks,
  type RuntimeStructureLink,
} from '@/features/workflow/runtime-structure-utils'
import { NodeFormRenderer } from '@/features/forms/runtime/node-form-renderer'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import {
  WORKBENCH_RUNTIME_ENDPOINTS,
  getWorkbenchDashboardSummary,
  addSignWorkbenchTask,
  getApprovalSheetDetailByBusiness,
  claimWorkbenchTask,
  completeWorkbenchTask,
  delegateWorkbenchTask,
  getWorkbenchTaskActions,
  getWorkbenchTaskDetail,
  jumpWorkbenchTask,
  listApprovalSheets,
  listWorkbenchTasks,
  handoverWorkbenchTasks,
  readWorkbenchTask,
  removeSignWorkbenchTask,
  revokeWorkbenchTask,
  rejectWorkbenchTask,
  returnWorkbenchTask,
  takeBackWorkbenchTask,
  transferWorkbenchTask,
  wakeUpWorkbenchInstance,
  urgeWorkbenchTask,
  type ApprovalSheetListItem,
  type CompleteWorkbenchTaskPayload,
  type WorkbenchTaskDetail,
  type WorkbenchTaskListItem,
} from '@/lib/api/workbench'
import { getDepartmentUsers } from '@/lib/api/system-org'
import { getRoleUsers } from '@/lib/api/system-roles'
import { type SystemAssociatedUser } from '@/lib/api/system-users'
import {
  confirmProcessLinkParentResume,
  createProcessCollaborationEvent,
  getProcessTerminationSnapshot,
  listProcessCollaborationTrace,
  listProcessTerminationAuditTrail,
  listProcessTimeTravelTrace,
} from '@/lib/api/process-runtime-advanced'
import { useAuthStore } from '@/stores/auth-store'

const workbenchTodoListRoute = getRouteApi('/_authenticated/workbench/todos/list')
const workbenchDoneListRoute = getRouteApi('/_authenticated/workbench/done/list')
const workbenchInitiatedListRoute = getRouteApi('/_authenticated/workbench/initiated/list')
const workbenchCopiedListRoute = getRouteApi('/_authenticated/workbench/copied/list')

async function loadCandidateHandlerUsers(detail: {
  taskId: string | null
  candidateGroupIds: string[]
}): Promise<SystemAssociatedUser[]> {
  const requests: Array<Promise<SystemAssociatedUser[]>> = []

  for (const groupId of detail.candidateGroupIds ?? []) {
    if (groupId.startsWith('role_')) {
      requests.push(getRoleUsers(groupId))
      continue
    }
    if (groupId.startsWith('dept_')) {
      requests.push(getDepartmentUsers(groupId))
    }
  }

  if (requests.length === 0) {
    return []
  }

  const groups = await Promise.all(requests)
  const merged = new Map<string, SystemAssociatedUser>()

  for (const users of groups) {
    for (const user of users) {
      merged.set(user.userId, user)
    }
  }

  return Array.from(merged.values())
}

// 统一把流程相关时间转成中文可读格式。
function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function resolveCurrentHandlerContent(detail: WorkbenchTaskDetail) {
  if (
    detail.instanceStatus === 'REVOKED' ||
    detail.instanceStatus === 'COMPLETED' ||
    detail.instanceStatus === 'TERMINATED'
  ) {
    return '--'
  }
  if (detail.assigneeUserId) {
    return (
      <ApprovalUserTag
        userId={detail.assigneeUserId}
        displayNames={detail.userDisplayNames}
      />
    )
  }
  if (detail.candidateUserIds.length > 0 || (detail.candidateGroupIds?.length ?? 0) > 0) {
    return <Badge variant='secondary'>待认领</Badge>
  }
  return '--'
}

function resolveDisplayName(
  userId: string | null | undefined,
  displayNames: Record<string, string> | null | undefined
) {
  if (!userId) {
    return '--'
  }
  return displayNames?.[userId] ?? userId
}

function describePendingAddSignTask(
  detail: WorkbenchTaskDetail
): Array<{ taskId: string; label: string; hint: string }> {
  const activeTaskIds = new Set(detail.activeTaskIds ?? [])

  return (detail.taskTrace ?? [])
    .filter(
      (item) =>
        item.isAddSignTask &&
        Boolean(item.taskId) &&
        activeTaskIds.has(item.taskId) &&
        (item.status === 'PENDING' || item.status === 'PENDING_CLAIM')
    )
    .map((item) => ({
      taskId: item.taskId,
      label: item.nodeName || '加签任务',
      hint:
        resolveDisplayName(item.assigneeUserId, detail.userDisplayNames) +
        (item.taskId ? ` · ${item.taskId}` : ''),
    }))
}

// 待办状态在列表里只展示最直观的中文标签。
function resolveTaskStatusLabel(status: string) {
  switch (status) {
    case 'REVOKED':
      return '已撤销'
    case 'TAKEN_BACK':
      return '已拿回'
    case 'PENDING_CLAIM':
      return '待认领'
    case 'PENDING':
      return '待处理'
    case 'DELEGATED':
      return '委派任务'
    case 'HANDOVERED':
      return '离职转办'
    case 'TRANSFERRED':
      return '已转办'
    case 'RETURNED':
      return '已退回'
    case 'COMPLETED':
    default:
      return '已完成'
  }
}

// 不同状态复用同一套 badge 语义。
function resolveTaskStatusVariant(status: WorkbenchTaskListItem['status']) {
  return status === 'PENDING' ||
    status === 'PENDING_CLAIM' ||
    status === 'DELEGATED'
    ? 'destructive'
    : 'secondary'
}

// 列表顶部统计只看总数、待处理和已完成。
function summarizeTasks(records: WorkbenchTaskListItem[]) {
  return {
    total: records.length,
    pending: records.filter(
      (record) =>
        record.status === 'PENDING' || record.status === 'PENDING_CLAIM'
    ).length,
    completed: records.filter((record) => record.status === 'COMPLETED').length,
  }
}

// 请求失败时用空分页兜底，保证列表组件结构稳定。
function buildEmptyApprovalSheetPage(search: { page: number; pageSize: number }) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

const approvalSheetBusinessTypeOptions = [
  { label: '请假申请', value: 'OA_LEAVE' },
  { label: '报销申请', value: 'OA_EXPENSE' },
  { label: '通用申请', value: 'OA_COMMON' },
] as const

const WORKBENCH_START_ENTRIES = [
  ['请假申请', '/oa/leave/create'],
  ['报销申请', '/oa/expense/create'],
  ['通用申请', '/oa/common/create'],
  ['OA 流程查询', '/oa/query'],
] as const

// 流程中心列表统一走结构化筛选，避免页面自己拼各种 query。
// 从结构化筛选里读取当前过滤值。
function getApprovalSheetFilterValue(
  search: ListQuerySearch,
  field: string
) {
  const filter = (search.filters ?? []).find((item) => item.field === field)
  return typeof filter?.value === 'string' ? filter.value : undefined
}

// 统一更新审批单筛选条件，避免各页面自己拼参数。
function updateApprovalSheetFilter(
  search: ListQuerySearch,
  navigate: NavigateFn,
  field: string,
  value?: string
) {
  const nextFilters = (search.filters ?? []).filter((item) => item.field !== field)
  if (value) {
    nextFilters.push({
      field,
      operator: 'eq',
      value,
    })
  }

  navigate({
    search: (prev) => ({
      ...prev,
      page: undefined,
      filters: nextFilters.length > 0 ? nextFilters : undefined,
    }),
  })
}

// CC 列表用“已读/已完成”来区分待办状态，和普通待办口径不同。
// 抄送列表把已阅、已完成和普通待办区分开统计。
function isCcApprovalSheetRead(record: ApprovalSheetListItem) {
  const latestAction = record.latestAction?.toUpperCase()
  const currentTaskStatus = record.currentTaskStatus?.toUpperCase()

  return (
    latestAction === 'READ' ||
    latestAction === 'CC_READ' ||
    latestAction === 'READ_DONE' ||
    currentTaskStatus === 'READ'
  )
}

// 已完成抄送单独按实例或任务状态判断。
function isCcApprovalSheetCompleted(record: ApprovalSheetListItem) {
  return (
    record.instanceStatus === 'COMPLETED' ||
    record.currentTaskStatus?.toUpperCase() === 'COMPLETED'
  )
}

// CC 列表的统计口径单独算，避免和待办/已办混在一起。
// CC 列表的统计口径单独算，避免和待办/已办混在一起。
function summarizeCcApprovalSheets(records: ApprovalSheetListItem[]) {
  return {
    total: records.length,
    pending: records.filter(
      (record) => !isCcApprovalSheetRead(record) && !isCcApprovalSheetCompleted(record)
    ).length,
    read: records.filter((record) => isCcApprovalSheetRead(record)).length,
    completed: records.filter((record) => isCcApprovalSheetCompleted(record)).length,
  }
}

// 审批单列表工具条负责高级筛选和抄送统计展示。
function ApprovalSheetListToolbar({
  view,
  search,
  navigate,
  records,
}: {
  view: 'DONE' | 'INITIATED' | 'CC'
  search: ListQuerySearch
  navigate: NavigateFn
  records: ApprovalSheetListItem[]
}) {
  const statusValue = getApprovalSheetFilterValue(search, 'instanceStatus')
  const businessTypeValue = getApprovalSheetFilterValue(search, 'businessType')
  const ccSummary = view === 'CC' ? summarizeCcApprovalSheets(records) : null

  return (
    <div className='space-y-4 rounded-lg border bg-muted/20 p-4'>
      <div className='flex flex-wrap items-start justify-between gap-3'>
        <div className='space-y-1'>
          <p className='text-sm font-medium'>高级筛选</p>
          <p className='text-sm text-muted-foreground'>
            按实例状态和业务类型快速收窄流程中心列表。
          </p>
        </div>
        {ccSummary ? (
          <div className='flex flex-wrap gap-2'>
            <Badge variant='secondary'>
              <span>抄送中</span>
              <span className='ml-2 text-xs tabular-nums text-muted-foreground'>
                {ccSummary.pending}
              </span>
            </Badge>
            <Badge variant='secondary'>
              <span>已阅</span>
              <span className='ml-2 text-xs tabular-nums text-muted-foreground'>
                {ccSummary.read}
              </span>
            </Badge>
            <Badge variant='secondary'>
              <span>已完成</span>
              <span className='ml-2 text-xs tabular-nums text-muted-foreground'>
                {ccSummary.completed}
              </span>
            </Badge>
          </div>
        ) : null}
      </div>

      <div className='space-y-3'>
        <div className='flex flex-wrap items-center gap-2'>
          <span className='text-xs font-medium uppercase tracking-wide text-muted-foreground'>
            状态
          </span>
          {[
            { label: '全部', value: undefined },
            { label: '进行中', value: 'RUNNING' },
            { label: '已完成', value: 'COMPLETED' },
          ].map((item) => (
            <Button
              key={item.label}
              type='button'
              variant={statusValue === item.value ? 'secondary' : 'outline'}
              size='sm'
              onClick={() =>
                updateApprovalSheetFilter(search, navigate, 'instanceStatus', item.value)
              }
            >
              {item.label}
            </Button>
          ))}
        </div>

        <div className='flex flex-wrap items-center gap-2'>
          <span className='text-xs font-medium uppercase tracking-wide text-muted-foreground'>
            业务类型
          </span>
          <Button
            type='button'
            variant={businessTypeValue ? 'outline' : 'secondary'}
            size='sm'
            onClick={() =>
              updateApprovalSheetFilter(search, navigate, 'businessType', undefined)
            }
          >
            全部
          </Button>
          {approvalSheetBusinessTypeOptions.map((item) => (
            <Button
              key={item.value}
              type='button'
              variant={businessTypeValue === item.value ? 'secondary' : 'outline'}
              size='sm'
              onClick={() =>
                updateApprovalSheetFilter(search, navigate, 'businessType', item.value)
              }
            >
              {item.label}
            </Button>
          ))}
        </div>
      </div>
    </div>
  )
}

// 审批过程改成紧凑时间轴，强调顺序而不是平铺信息块。
function ApprovalSheetActionTimeline({
  taskTrace,
  userDisplayNames,
}: {
  taskTrace: WorkbenchTaskDetail['taskTrace']
  userDisplayNames?: Record<string, string> | null
}) {
  const items = taskTrace ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>审批过程</CardTitle>
        <CardDescription>
          按时间顺序查看节点流转、办理人和审批意见，快速定位流程推进过程。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {items.length ? (
          <ol className='space-y-0'>
            {items.map((item, index) => (
              <li
                key={`${item.taskId}:${item.nodeId}:${index}`}
                className='grid grid-cols-[120px_24px_minmax(0,1fr)] gap-3 pb-5 last:pb-0'
              >
                <div className='pt-0.5 text-right text-xs text-muted-foreground'>
                  <div className='font-medium text-foreground'>
                    {formatDateTime(item.handleEndTime ?? item.handleStartTime ?? item.receiveTime)}
                  </div>
                  <div className='mt-1'>
                    {item.handleEndTime
                      ? '完成'
                      : item.handleStartTime
                        ? '处理中'
                        : item.receiveTime
                          ? '已接收'
                          : '待处理'}
                  </div>
                </div>
                <div className='flex flex-col items-center'>
                  <span className='mt-1 size-3 rounded-full border-2 border-background bg-emerald-500 shadow-sm ring-4 ring-emerald-500/10' />
                  {index < items.length - 1 ? (
                    <span className='mt-2 h-full min-h-10 w-px bg-border' />
                  ) : null}
                </div>
                <div className='space-y-2 pb-1'>
                  <div className='flex flex-wrap items-center gap-2'>
                    <span className='font-medium leading-none'>{item.nodeName}</span>
                    <Badge variant='secondary'>{resolveApprovalSheetResultLabel(item)}</Badge>
                  </div>

                  <div className='flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground'>
                    <span className='flex items-center gap-1'>
                      办理人：
                      <ApprovalUserTag
                        userId={item.operatorUserId ?? item.assigneeUserId}
                        displayNames={userDisplayNames}
                      />
                    </span>
                    <span>接收：{formatDateTime(item.receiveTime)}</span>
                    <span>开始：{formatDateTime(item.handleStartTime)}</span>
                    <span>完成：{formatDateTime(item.handleEndTime)}</span>
                    <span>
                      时长：
                      {item.handleDurationSeconds === null ||
                      item.handleDurationSeconds === undefined
                        ? '--'
                        : `${item.handleDurationSeconds} 秒`}
                    </span>
                  </div>

                  {(item.actingMode || item.actingForUserId || item.delegatedByUserId || item.handoverFromUserId) ? (
                    <div className='flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground'>
                      <span>办理模式：{resolveApprovalSheetActingModeLabel(item.actingMode)}</span>
                      {item.actingForUserId ? (
                        <span className='flex items-center gap-1'>
                          代谁办理：
                          <ApprovalUserTag
                            userId={item.actingForUserId}
                            displayNames={userDisplayNames}
                          />
                        </span>
                      ) : null}
                      {item.delegatedByUserId ? (
                        <span className='flex items-center gap-1'>
                          委派来源：
                          <ApprovalUserTag
                            userId={item.delegatedByUserId}
                            displayNames={userDisplayNames}
                          />
                        </span>
                      ) : null}
                      {item.handoverFromUserId ? (
                        <span className='flex items-center gap-1'>
                          离职转办来源：
                          <ApprovalUserTag
                            userId={item.handoverFromUserId}
                            displayNames={userDisplayNames}
                          />
                        </span>
                      ) : null}
                    </div>
                  ) : null}

                  <p className='text-sm text-foreground/90'>
                    审批意见：{formatApprovalSheetText(item.comment)}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <div className='rounded-lg border border-dashed p-6 text-sm text-muted-foreground'>
            暂无动作轨迹。
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// 会签节点详情单独收口成一个区块，统一展示会签模式、人数进度和票签结果。
function ApprovalSheetCountersignSection({
  countersignGroups,
}: {
  countersignGroups: WorkbenchTaskDetail['countersignGroups']
}) {
  const groups = countersignGroups ?? []

  if (!groups.length) {
    return null
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>会签进度</CardTitle>
        <CardDescription>
          展示当前会签组的模式、成员状态、票权累计和自动结束结果。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        {groups.map((group) => (
          <div key={group.groupId} className='space-y-3 rounded-lg border p-4'>
            <div className='flex flex-wrap items-center gap-2'>
              <Badge variant='secondary'>
                {resolveCountersignModeLabel(group.approvalMode)}
              </Badge>
              <Badge variant='outline'>{group.groupStatus}</Badge>
              <span className='text-sm font-medium'>{group.nodeName}</span>
            </div>

            <div className='grid gap-2 text-sm text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
              <div>总人数：{group.totalCount}</div>
              <div>已完成：{group.completedCount}</div>
              <div>处理中：{group.activeCount}</div>
              <div>等待中：{group.waitingCount}</div>
            </div>

            {group.approvalMode === 'VOTE' ? (
              <div className='grid gap-2 rounded-md border bg-muted/20 p-3 text-sm text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
                <div>通过阈值：{group.voteThresholdPercent ?? '--'}%</div>
                <div>通过票权：{group.approvedWeight ?? '--'}</div>
                <div>拒绝票权：{group.rejectedWeight ?? '--'}</div>
                <div>当前决议：{formatApprovalSheetText(group.decisionStatus)}</div>
              </div>
            ) : null}

            <div className='space-y-2'>
              {group.members.map((member) => (
                <div
                  key={member.memberId}
                  className='grid gap-2 rounded-md border border-dashed px-3 py-2 text-sm md:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)]'
                >
                  <div className='flex flex-wrap items-center gap-2'>
                    <span className='font-medium'>{member.assigneeUserId}</span>
                    <Badge variant='outline'>第 {member.sequenceNo} 位</Badge>
                    {member.voteWeight !== null ? (
                      <Badge variant='secondary'>票权 {member.voteWeight}</Badge>
                    ) : null}
                  </div>
                  <div className='text-muted-foreground'>
                    状态：{resolveCountersignMemberStatusLabel(member.memberStatus)}
                  </div>
                  <div className='font-mono text-xs text-muted-foreground'>
                    任务：{formatApprovalSheetText(member.taskId)}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

// 运行态结构单独展示，便于在审批单详情里回查主子流程、追加和动态构建关系。
function ApprovalSheetProcessLinkSection({
  links,
  currentInstanceId,
  onConfirmParentResume,
  pendingLinkId,
}: {
  links: RuntimeStructureLink[]
  currentInstanceId: string
  onConfirmParentResume?: ((link: RuntimeStructureLink) => void) | undefined
  pendingLinkId?: string | null
}) {
  return (
    <RuntimeStructureSection
      title='运行态结构'
      description='展示当前审批单所在的主子流程、追加和动态构建关系，回查触发方式、终止策略和运行状态。'
      links={links}
      currentInstanceId={currentInstanceId}
      onConfirmParentResume={onConfirmParentResume}
      pendingLinkId={pendingLinkId}
    />
  )
}

// 列表页公共区块把查询、统计和空状态统一起来。
function ApprovalSheetListPageSection({
  title,
  description,
  view,
  search,
  navigate,
  renderTopContent,
}: {
  title: string
  description: string
  view: 'DONE' | 'INITIATED' | 'CC'
  search: ListQuerySearch
  navigate: NavigateFn
  renderTopContent?: (records: ApprovalSheetListItem[]) => ReactNode
}) {
  const approvalSheetsQuery = useQuery({
    queryKey: ['workbench', 'approval-sheet-page', view, search],
    queryFn: () =>
      listApprovalSheets({
        ...search,
        view,
      }),
  })

  const pageData = approvalSheetsQuery.data ?? buildEmptyApprovalSheetPage(search)
  const summary =
    view === 'CC'
      ? summarizeCcApprovalSheets(pageData.records)
      : summarizeApprovalSheets(pageData.records)
  const pendingSummary = 'pending' in summary ? summary.pending : summary.running
  const completedSummary = 'read' in summary ? summary.read : summary.completed

  return (
    <>
      {renderTopContent ? renderTopContent(pageData.records) : null}
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
      <ResourceListPage
        title={title}
        description={description}
        endpoint={WORKBENCH_RUNTIME_ENDPOINTS.approvalSheetsPage}
        searchPlaceholder='搜索流程标题、业务标题、单号或当前节点'
        search={search}
        navigate={navigate}
        columns={createApprovalSheetColumns('workbench')}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: view === 'CC' ? '抄送总量' : '审批单总量',
            value: String(pageData.total),
            hint:
              view === 'CC'
                ? '当前页抄送记录的真实总量。'
                : '实例维度聚合后的审批单数量。',
          },
          {
            label: view === 'CC' ? '待阅' : '进行中',
            value: String(pendingSummary),
            hint:
              view === 'CC'
                ? '尚未确认已阅的抄送记录。'
                : '当前页里仍在流转中的审批单。',
          },
          {
            label: view === 'CC' ? '已阅' : '已完成',
            value: String(completedSummary),
            hint:
              view === 'CC'
                ? '已确认已阅的抄送记录。'
                : '当前页已完成的审批单数量。',
          },
        ]}
        createAction={{
          label: '发起流程',
          href: '/workbench/start',
        }}
      />
    </>
  )
}

// 待办页使用紧凑动作按钮承载离职转办，避免占满顶部空间。
function WorkbenchTodoHandoverAction() {
  const queryClient = useQueryClient()
  const [handoverDialogOpen, setHandoverDialogOpen] = useState(false)
  const handoverForm = useForm<HandoverTaskFormValues>({
    resolver: zodResolver(handoverTaskSchema),
    defaultValues: {
      sourceUserId: '',
      targetUserId: '',
      comment: '',
    },
  })

  const handoverMutation = useMutation({
    mutationFn: (payload: HandoverTaskFormValues) =>
      handoverWorkbenchTasks({
        sourceUserId: payload.sourceUserId.trim(),
        targetUserId: payload.targetUserId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['workbench', 'todo-page'] })
      setHandoverDialogOpen(false)
      handoverForm.reset({
        sourceUserId: '',
        targetUserId: '',
        comment: '',
      })
    },
    onError: handleServerError,
  })

  const onHandoverSubmit = handoverForm.handleSubmit((values) => {
    handoverMutation.mutate(values)
  })

  return (
    <Dialog open={handoverDialogOpen} onOpenChange={setHandoverDialogOpen}>
      <DialogTrigger asChild>
        <Button type='button' variant='outline'>
          离职转办
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>离职转办</DialogTitle>
          <DialogDescription>
            选择来源用户和目标用户，系统会批量转移该来源用户的当前待办。
          </DialogDescription>
        </DialogHeader>
        <Form {...handoverForm}>
          <form className='space-y-4' onSubmit={onHandoverSubmit}>
            <FormField
              control={handoverForm.control}
              name='sourceUserId'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>来源用户</FormLabel>
                  <FormControl>
                    <UserPickerField
                      ariaLabel='来源用户'
                      value={field.value}
                      onChange={field.onChange}
                      placeholder='请选择来源用户'
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={handoverForm.control}
              name='targetUserId'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>目标用户</FormLabel>
                  <FormControl>
                    <UserPickerField
                      ariaLabel='目标用户'
                      value={field.value}
                      onChange={field.onChange}
                      placeholder='请选择目标用户'
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={handoverForm.control}
              name='comment'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>转办说明</FormLabel>
                  <FormControl>
                    <Textarea
                      className='min-h-24'
                      placeholder='请输入离职转办说明'
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button type='button' variant='outline' onClick={() => setHandoverDialogOpen(false)}>
                取消
              </Button>
              <Button type='submit' disabled={handoverMutation.isPending}>
                {handoverMutation.isPending ? '转办中' : '确认转办'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}

function TaskRuntimeFormCard({
  detail,
  hasNodeForm,
  showCompletionForm,
  onSubmit,
  isPending,
}: {
  detail: WorkbenchTaskDetail
  hasNodeForm: boolean
  showCompletionForm: boolean
  onSubmit: (payload: CompleteWorkbenchTaskPayload) => void
  isPending: boolean
}) {
  const form = useForm<TaskActionFormValues>({
    resolver: zodResolver(taskActionSchema),
    defaultValues: {
      action: 'APPROVE',
      comment: '',
    },
  })
  const [taskFormData, setTaskFormData] = useState<Record<string, unknown>>(
    detail.taskFormData ?? detail.formData ?? {}
  )
  const onSubmitForm = form.handleSubmit((payload) => {
    onSubmit({
      action:
        hasNodeForm && taskFormData.approved === false ? 'REJECT' : payload.action,
      comment:
        hasNodeForm && typeof taskFormData.comment === 'string'
          ? taskFormData.comment.trim() || undefined
          : payload.comment?.trim() || undefined,
      taskFormData,
    })
  })

  return (
    <div className='space-y-3 rounded-lg border bg-muted/20 p-4'>
      {hasNodeForm ? (
        <div className='flex flex-wrap items-center gap-2 text-xs text-muted-foreground'>
          <Badge variant='outline'>
            节点表单 {detail.effectiveFormKey} · {detail.effectiveFormVersion}
          </Badge>
        </div>
      ) : null}
      {hasNodeForm ? (
        <NodeFormRenderer
          nodeFormKey={detail.nodeFormKey ?? detail.effectiveFormKey}
          nodeFormVersion={detail.nodeFormVersion ?? detail.effectiveFormVersion}
          value={taskFormData}
          onChange={(nextValue) => {
            setTaskFormData(nextValue)
          }}
          fieldBindings={detail.fieldBindings}
          taskFormData={detail.taskFormData ?? undefined}
          disabled={detail.status === 'COMPLETED' || !showCompletionForm}
        />
      ) : (
        <div className='rounded-lg border border-dashed bg-background p-3 text-sm text-muted-foreground'>
          当前节点没有独立办理表单，请直接填写审批动作和审批意见。
        </div>
      )}
      {showCompletionForm ? (
        <Form {...form}>
          <form className='space-y-4 rounded-lg border bg-background p-4' onSubmit={onSubmitForm}>
            {!hasNodeForm ? (
              <>
                <FormField
                  control={form.control}
                  name='action'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>审批动作</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          {...field}
                        >
                          <option value='APPROVE'>同意通过</option>
                          <option value='REJECT'>驳回</option>
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='comment'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>审批意见</FormLabel>
                      <FormControl>
                        <Textarea
                          className='min-h-28'
                          placeholder='请输入审批意见'
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : (
              <div className='rounded-lg border border-dashed p-3 text-sm text-muted-foreground'>
                节点表单已接管审批结果与意见输入，提交时会自动根据表单值生成通过或驳回动作。
              </div>
            )}

            <div className='flex items-center gap-3'>
              <Button
                type='submit'
                disabled={isPending || detail.status === 'COMPLETED'}
              >
                {isPending ? (
                  <>
                    <Loader2 className='animate-spin' />
                    提交中
                  </>
                ) : (
                  '完成任务'
                )}
              </Button>
              <Button asChild type='button' variant='outline'>
                <Link to='/workbench/todos/list' search={{}}>返回列表</Link>
              </Button>
            </div>
          </form>
        </Form>
      ) : null}
    </div>
  )
}

const todoColumns: ColumnDef<WorkbenchTaskListItem>[] = [
  {
    accessorKey: 'processName',
    header: '流程标题',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.processName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.processKey}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'applicantUserId',
    header: '发起人',
  },
  {
    accessorKey: 'nodeName',
    header: '当前节点',
  },
  {
    accessorKey: 'businessKey',
    header: '业务单号',
    cell: ({ row }) => row.original.businessKey ?? '--',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveTaskStatusVariant(row.original.status)}>
        {resolveTaskStatusLabel(row.original.status)}
      </Badge>
    ),
  },
  {
    id: 'createdAt',
    accessorKey: 'createdAt',
    header: '创建时间',
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
  {
    id: 'actions',
    header: '操作',
    cell: ({ row }) => (
      <Button asChild size='sm' variant='outline'>
        <Link to='/workbench/todos/$taskId' params={{ taskId: row.original.taskId }} search={{}}>
          处理
        </Link>
      </Button>
    ),
  },
]

const taskActionSchema = z.object({
  action: z.enum(['APPROVE', 'REJECT']),
  comment: z.string().max(500, '审批意见最多 500 个字符').default(''),
})

type TaskActionFormValues = z.input<typeof taskActionSchema>

const claimTaskSchema = z.object({
  comment: z.string().max(500, '认领说明最多 500 个字符').default(''),
})

type ClaimTaskFormValues = z.input<typeof claimTaskSchema>

const transferTaskSchema = z.object({
  targetUserId: z.string().trim().min(1, '请选择目标用户'),
  comment: z.string().max(500, '转办说明最多 500 个字符').default(''),
})

type TransferTaskFormValues = z.input<typeof transferTaskSchema>

const delegateTaskSchema = z.object({
  targetUserId: z.string().trim().min(1, '请选择委派用户'),
  comment: z.string().max(500, '委派说明最多 500 个字符').default(''),
})

type DelegateTaskFormValues = z.input<typeof delegateTaskSchema>

const returnTaskSchema = z.object({
  comment: z.string().max(500, '退回说明最多 500 个字符').default(''),
})

type ReturnTaskFormValues = z.input<typeof returnTaskSchema>

const rejectTaskSchema = z.object({
  targetStrategy: z.enum([
    'PREVIOUS_USER_TASK',
    'INITIATOR',
    'ANY_USER_TASK',
  ]),
  targetTaskId: z.string().max(120, '驳回目标任务编号最多 120 个字符').default(''),
  targetNodeId: z.string().max(120, '驳回目标节点编号最多 120 个字符').default(''),
  reapproveStrategy: z.enum([
    'CONTINUE',
    'RETURN_TO_REJECTED_NODE',
  ]),
  comment: z.string().max(500, '驳回说明最多 500 个字符').default(''),
})

type RejectTaskFormValues = z.input<typeof rejectTaskSchema>

const jumpTaskSchema = z.object({
  targetNodeId: z.string().trim().min(1, '请输入目标节点编号'),
  comment: z.string().max(500, '跳转说明最多 500 个字符').default(''),
})

type JumpTaskFormValues = z.input<typeof jumpTaskSchema>

const takeBackTaskSchema = z.object({
  comment: z.string().max(500, '拿回说明最多 500 个字符').default(''),
})

type TakeBackTaskFormValues = z.input<typeof takeBackTaskSchema>

const wakeUpTaskSchema = z.object({
  sourceTaskId: z.string().trim().min(1, '请输入历史任务编号'),
  comment: z.string().max(500, '唤醒说明最多 500 个字符').default(''),
})

type WakeUpTaskFormValues = z.input<typeof wakeUpTaskSchema>

const addSignTaskSchema = z.object({
  targetUserId: z.string().trim().min(1, '请选择加签用户'),
  comment: z.string().max(500, '加签说明最多 500 个字符').default(''),
})

type AddSignTaskFormValues = z.input<typeof addSignTaskSchema>

const removeSignTaskSchema = z.object({
  targetTaskId: z.string().trim().min(1, '请选择待移除的加签任务'),
  comment: z.string().max(500, '减签说明最多 500 个字符').default(''),
})

type RemoveSignTaskFormValues = z.input<typeof removeSignTaskSchema>

const simpleActionCommentSchema = z.object({
  comment: z.string().max(500, '说明最多 500 个字符').default(''),
})

type SimpleActionCommentFormValues = z.input<typeof simpleActionCommentSchema>

const collaborationEventSchema = z.object({
  subject: z.string().max(120, '协同标题最多 120 个字符').default(''),
  content: z.string().trim().min(1, '请输入协同内容').max(500, '协同内容最多 500 个字符'),
  mentionedUserId: z.string().default(''),
})

type CollaborationEventFormValues = z.input<typeof collaborationEventSchema>

const handoverTaskSchema = z.object({
  sourceUserId: z.string().trim().min(1, '请选择来源用户'),
  targetUserId: z.string().trim().min(1, '请选择目标用户'),
  comment: z.string().max(500, '说明最多 500 个字符').default(''),
})

type HandoverTaskFormValues = z.input<typeof handoverTaskSchema>

// 工作台首页只负责把待办、已办和统计卡片串起来。
export function Dashboard() {
  const currentUser = useAuthStore((state) => state.currentUser)
  const summaryQuery = useQuery({
    queryKey: ['workbench', 'dashboard-summary'],
    queryFn: () => getWorkbenchDashboardSummary(),
  })
  const aiCapabilityCount = currentUser?.aiCapabilities?.length ?? 0
  const renderSummaryValue = (value: number) =>
    summaryQuery.isLoading ? '...' : summaryQuery.isError ? '--' : String(value)

  return (
    <PageShell
      title='平台总览'
      description='展示当前登录人的真实待办、已办和入口数量。'
      actions={
        <>
          <Button asChild>
            <Link to='/workbench/start' search={{}}>发起流程</Link>
          </Button>
          <Button asChild variant='outline'>
            <Link to='/workbench/todos/list' search={{}}>进入待办列表</Link>
          </Button>
        </>
      }
    >
      {summaryQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>平台总览加载失败</AlertTitle>
          <AlertDescription>
            {summaryQuery.error instanceof Error
              ? summaryQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <div className='grid gap-4 lg:grid-cols-4'>
        {[
          {
            title: '今日待办',
            value: renderSummaryValue(summaryQuery.data?.todoTodayCount ?? 0),
            description: '按今天 00:00 以来新增且当前仍处于待处理状态的任务统计。',
            icon: Clock3,
          },
          {
            title: '流程发起',
            value: String(WORKBENCH_START_ENTRIES.length),
            description: `当前工作台可直达的发起入口 ${WORKBENCH_START_ENTRIES.length} 个。`,
            icon: Play,
          },
          {
            title: '已完成审批',
            value: renderSummaryValue(summaryQuery.data?.doneApprovalCount ?? 0),
            description: '当前登录人已完成的审批单总数。',
            icon: CheckCircle2,
          },
          {
            title: 'AI 入口',
            value: String(aiCapabilityCount),
            description: '当前账号可用的 AI 能力数。',
            icon: Sparkles,
          },
        ].map((item) => (
          <Card key={item.title}>
            <CardHeader className='gap-3'>
              <div className='flex items-center justify-between gap-3'>
                <CardDescription>{item.title}</CardDescription>
                <item.icon className='text-muted-foreground' />
              </div>
              <CardTitle className='text-3xl'>{item.value}</CardTitle>
            </CardHeader>
            <CardContent className='text-sm text-muted-foreground'>
              {item.description}
            </CardContent>
          </Card>
        ))}
      </div>

      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>工作台入口</CardTitle>
            <CardDescription>
              左侧导航保持中文菜单，工作台只聚焦流程发起、待办处理和运行态回查。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            {[
              ['工作台待办列表', '/workbench/todos/list'],
              ['发起流程', '/workbench/start'],
              ['系统用户列表', '/system/users/list'],
              ['流程定义列表', '/workflow/definitions/list'],
            ].map(([label, href]) => (
              <Button key={href} asChild variant='outline' className='justify-start'>
                <Link to={href} search={{}}>{label}</Link>
              </Button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>运行态约束</CardTitle>
            <CardDescription>先打通最小闭环，再扩展认领、加签、回退、跳转等复杂动作。</CardDescription>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>1. 待办列表和处理页都走真实接口，不再使用静态占位数据。</p>
            <p>2. 流程发起页支持提交 JSON 表单数据，发起后直接跳到首个任务。</p>
            <p>3. 任务详情页会回显实例信息、审批意见和完成状态，便于调试闭环。</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

// 待办列表页承接任务查询和操作入口。
export function WorkbenchTodoListPage() {
  const search = workbenchTodoListRoute.useSearch()
  const navigate = workbenchTodoListRoute.useNavigate()
  const tasksQuery = useQuery({
    queryKey: ['workbench', 'todo-page', search],
    queryFn: () => listWorkbenchTasks(search),
  })

  const pageData = tasksQuery.data ?? {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
  const summary = summarizeTasks(pageData.records)

  return (
    <>
      {tasksQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>待办列表加载失败</AlertTitle>
          <AlertDescription>
            {tasksQuery.error instanceof Error
              ? tasksQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ResourceListPage
        title='工作台待办列表'
        description='展示当前运行态待办任务，支持关键词搜索、分页和跳转到独立处理页。'
        endpoint={WORKBENCH_RUNTIME_ENDPOINTS.tasksPage}
        searchPlaceholder='搜索流程标题、节点名称、发起人或业务单号'
        search={search}
        navigate={navigate}
        columns={todoColumns}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: '待办总量',
            value: String(pageData.total),
            hint: '后端分页接口返回的真实总量。',
          },
          {
            label: '当前页待处理',
            value: String(summary.pending),
            hint: '当前页里仍待审批的任务数量。',
          },
          {
            label: '当前页已完成',
            value: String(summary.completed),
            hint: '用于快速确认闭环任务是否已处理。',
          },
        ]}
        createAction={{
          label: '发起流程',
          href: '/workbench/start',
        }}
        extraActions={
          <>
            <WorkbenchTodoHandoverAction />
            <ContextualCopilotEntry
              sourceRoute='/workbench/todos/list'
              label='用 AI 解读当前待办'
            />
          </>
        }
      />
    </>
  )
}

// 发起页只展示业务入口和流程模板列表。
export function WorkbenchStartPage() {
  return (
    <PageShell
      title='发起流程'
      description='先选择业务入口，再进入对应的 OA 发起页。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute='/workbench/start'
            label='用 AI 推荐发起入口'
          />
          <Button asChild variant='outline'>
            <Link to='/workbench/todos/list' search={{}}>
              <ArrowLeft />
              返回待办列表
            </Link>
          </Button>
        </div>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>业务入口选择</CardTitle>
            <CardDescription>
              流程中心发起不再直接输入流程标识，而是先选业务入口。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            {WORKBENCH_START_ENTRIES.map(([label, href]) => (
              <Button key={href} asChild variant='outline' className='justify-start'>
                <Link to={href as '/oa/leave/create' | '/oa/expense/create' | '/oa/common/create' | '/oa/query'} search={{}}>
                  {label}
                </Link>
              </Button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>流程中心说明</CardTitle>
            <CardDescription>统一待办、发起和查询入口，避免拆成多套流程中心。</CardDescription>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>1. OA 业务入口先保存业务单据，再自动匹配流程绑定。</p>
            <p>2. 发起成功后优先进入业务审批单详情页；没有待办时也能查看流程轨迹。</p>
            <p>3. 流程中心待办列表仍然保留在工作台中，方便统一处理。</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

// 已办列表页复用审批单列表公共区块。
export function WorkbenchDoneListPage() {
  const search = workbenchDoneListRoute.useSearch()
  const navigate = workbenchDoneListRoute.useNavigate()

  return (
    <ApprovalSheetListPageSection
      title='流程中心已办'
      description='统一按审批单维度查看我已处理的流程，进入详情后仍然展示业务正文和流程轨迹。'
      view='DONE'
      search={search}
      navigate={navigate}
      renderTopContent={(records) => (
        <ApprovalSheetListToolbar
          view='DONE'
          search={search}
          navigate={navigate}
          records={records}
        />
      )}
    />
  )
}

// 我发起列表页复用审批单列表公共区块。
export function WorkbenchInitiatedListPage() {
  const search = workbenchInitiatedListRoute.useSearch()
  const navigate = workbenchInitiatedListRoute.useNavigate()

  return (
    <ApprovalSheetListPageSection
      title='流程中心我发起'
      description='查看当前登录人发起的审批单，支持模糊搜索、分页和回查审批单详情。'
      view='INITIATED'
      search={search}
      navigate={navigate}
      renderTopContent={(records) => (
        <ApprovalSheetListToolbar
          view='INITIATED'
          search={search}
          navigate={navigate}
          records={records}
        />
      )}
    />
  )
}

// 抄送列表页复用审批单列表公共区块。
export function WorkbenchCopiedListPage() {
  const search = workbenchCopiedListRoute.useSearch()
  const navigate = workbenchCopiedListRoute.useNavigate()

  return (
    <ApprovalSheetListPageSection
      title='流程中心抄送我'
      description='查看当前登录人的真实抄送记录，支持状态筛选、业务类型筛选和已阅轨迹回查。'
      view='CC'
      search={search}
      navigate={navigate}
      renderTopContent={(records) => (
        <ApprovalSheetListToolbar
          view='CC'
          search={search}
          navigate={navigate}
          records={records}
        />
      )}
    />
  )
}

type WorkbenchTodoDetailPageProps = {
  taskId?: string
  businessType?: string
  businessId?: string
  backHref?:
    | '/workbench/todos/list'
    | '/oa/query'
    | '/oa/leave/list'
    | '/oa/expense/list'
    | '/oa/common/list'
  backLabel?: string
}

function resolveBusinessBillHref(detail: WorkbenchTaskDetail) {
  if (!detail.businessKey) {
    return null
  }

  switch (detail.businessType) {
    case 'PLM_ECR':
      return { to: '/plm/ecr/$billId', params: { billId: detail.businessKey } } as const
    case 'PLM_ECO':
      return { to: '/plm/eco/$billId', params: { billId: detail.businessKey } } as const
    case 'PLM_MATERIAL':
      return {
        to: '/plm/material-master/$billId',
        params: { billId: detail.businessKey },
      } as const
    default:
      return null
  }
}

// 待办详情页统一承接任务详情、表单和动作办理。
export function WorkbenchTodoDetailPage({
  taskId,
  businessType,
  businessId,
  backHref = '/workbench/todos/list',
  backLabel = '返回待办列表',
}: WorkbenchTodoDetailPageProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const currentUserId = useAuthStore((state) => state.currentUser?.userId ?? null)
  const [addSignDialogOpen, setAddSignDialogOpen] = useState(false)
  const [removeSignDialogOpen, setRemoveSignDialogOpen] = useState(false)
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false)
  const [delegateDialogOpen, setDelegateDialogOpen] = useState(false)
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  const [jumpDialogOpen, setJumpDialogOpen] = useState(false)
  const [takeBackDialogOpen, setTakeBackDialogOpen] = useState(false)
  const [wakeUpDialogOpen, setWakeUpDialogOpen] = useState(false)
  const [transferDialogOpen, setTransferDialogOpen] = useState(false)
  const [urgeDialogOpen, setUrgeDialogOpen] = useState(false)
  const [returnDialogOpen, setReturnDialogOpen] = useState(false)
  const [collaborationDialogOpen, setCollaborationDialogOpen] = useState(false)
  const locator = useMemo(
    () =>
      taskId
        ? { mode: 'task' as const, taskId }
        : businessType && businessId
          ? {
              mode: 'business' as const,
              businessType,
              businessId,
            }
          : null,
    [businessId, businessType, taskId]
  )

  if (!locator) {
    throw new Error('审批单详情页需要 taskId 或 business locator')
  }

  const detailQueryKey = useMemo(
    () =>
      locator.mode === 'task'
        ? (['workbench', 'todo-detail', locator.taskId] as const)
        : ([
            'workbench',
            'approval-sheet-detail',
            locator.businessType,
            locator.businessId,
          ] as const),
    [locator.businessId, locator.businessType, locator.mode, locator.taskId]
  )

  const detailQuery = useQuery({
    queryKey: detailQueryKey,
    queryFn: () =>
      locator.mode === 'task'
        ? getWorkbenchTaskDetail(locator.taskId)
        : getApprovalSheetDetailByBusiness({
            businessType: locator.businessType,
            businessId: locator.businessId,
          }),
  })
  const detail = detailQuery.data
  const resolvedTaskId = useMemo(() => {
    if (locator.mode === 'task') {
      return locator.taskId
    }
    if (!detail?.activeTaskIds?.length) {
      return null
    }
    const currentUserTask = (detail.taskTrace ?? []).find(
      (item) =>
        detail.activeTaskIds.includes(item.taskId)
        && item.assigneeUserId === currentUserId
    )
    return currentUserTask?.taskId ?? detail.activeTaskIds[0]
  }, [currentUserId, detail, locator])
  const rootInstanceId =
    detail?.processLinks?.[0]?.rootInstanceId ?? detail?.instanceId ?? null
  const candidateHandlerTaskId = detail?.taskId ?? null
  const candidateHandlerGroupIds = detail?.candidateGroupIds ?? []
  const actionsQuery = useQuery({
    queryKey: ['workbench', 'todo-actions', resolvedTaskId ?? 'none'],
    queryFn: () => getWorkbenchTaskActions(resolvedTaskId as string),
    enabled: Boolean(resolvedTaskId),
  })
  const candidateHandlersQuery = useQuery({
    queryKey: [
      'workbench',
      'candidate-handlers',
      candidateHandlerTaskId ?? 'none',
      ...candidateHandlerGroupIds,
    ],
    queryFn: () =>
      loadCandidateHandlerUsers({
        taskId: candidateHandlerTaskId,
        candidateGroupIds: candidateHandlerGroupIds,
      }),
    enabled:
      Boolean(candidateHandlerTaskId)
      && candidateHandlerGroupIds.length > 0
      && !detail?.assigneeUserId,
  })
  const collaborationTraceQuery = useQuery({
    queryKey: ['workbench', 'collaboration-trace', detail?.instanceId ?? 'none'],
    queryFn: () => listProcessCollaborationTrace(detail?.instanceId as string),
    enabled: Boolean(detail?.instanceId),
  })
  const timeTravelTraceQuery = useQuery({
    queryKey: ['workbench', 'time-travel-trace', detail?.instanceId ?? 'none'],
    queryFn: () => listProcessTimeTravelTrace(detail?.instanceId as string),
    enabled: Boolean(detail?.instanceId),
  })
  const terminationSnapshotQuery = useQuery({
    queryKey: [
      'workbench',
      'termination-snapshot',
      rootInstanceId ?? 'none',
      detail?.instanceId ?? 'none',
      detail?.operatorUserId ?? 'none',
      detail?.assigneeUserId ?? 'none',
      detail?.applicantUserId ?? 'none',
    ],
    queryFn: () =>
      getProcessTerminationSnapshot({
        rootInstanceId: rootInstanceId as string,
        targetInstanceId:
          detail?.instanceId && rootInstanceId && detail.instanceId !== rootInstanceId
            ? detail.instanceId
            : undefined,
        scope:
          detail?.instanceId && rootInstanceId && detail.instanceId !== rootInstanceId
            ? 'CHILD'
            : 'ROOT',
        propagationPolicy: 'CASCADE_ALL',
        reason: '审批详情终止策略预览',
        operatorUserId:
          detail?.operatorUserId ?? detail?.assigneeUserId ?? detail?.applicantUserId ?? undefined,
      }),
    enabled: Boolean(detail?.instanceId && rootInstanceId),
  })
  const terminationAuditQuery = useQuery({
    queryKey: ['workbench', 'termination-audit', rootInstanceId ?? 'none'],
    queryFn: () => listProcessTerminationAuditTrail(rootInstanceId as string),
    enabled: Boolean(rootInstanceId),
  })
  const confirmParentResumeMutation = useMutation({
    mutationFn: ({ instanceId, linkId }: { instanceId: string; linkId: string }) =>
      confirmProcessLinkParentResume(instanceId, linkId),
    onSuccess: refreshWorkbenchQueries,
    onError: handleServerError,
  })

  function requireActionTaskId() {
    if (!resolvedTaskId) {
      throw new Error('当前审批单没有可操作任务')
    }

    return resolvedTaskId
  }

  function requireActionInstanceId() {
    if (!detail?.instanceId) {
      throw new Error('当前审批单没有实例编号')
    }

    return detail.instanceId
  }

  async function navigateAfterTaskMutation(response: {
    nextTasks: Array<{ taskId: string }>
  }) {
    const nextTask = response.nextTasks[0]
    if (locator?.mode === 'task') {
      if (nextTask) {
        startTransition(() => {
          navigate({
            to: '/workbench/todos/$taskId',
            params: { taskId: nextTask.taskId },
          })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/workbench/todos/list' })
      })
    }
  }

  const claimForm = useForm<ClaimTaskFormValues>({
    resolver: zodResolver(claimTaskSchema),
    defaultValues: {
      comment: '',
    },
  })
  const transferForm = useForm<TransferTaskFormValues>({
    resolver: zodResolver(transferTaskSchema),
    defaultValues: {
      targetUserId: '',
      comment: '',
    },
  })
  const delegateForm = useForm<DelegateTaskFormValues>({
    resolver: zodResolver(delegateTaskSchema),
    defaultValues: {
      targetUserId: '',
      comment: '',
    },
  })
  const returnForm = useForm<ReturnTaskFormValues>({
    resolver: zodResolver(returnTaskSchema),
    defaultValues: {
      comment: '',
    },
  })
  const addSignForm = useForm<AddSignTaskFormValues>({
    resolver: zodResolver(addSignTaskSchema),
    defaultValues: {
      targetUserId: '',
      comment: '',
    },
  })
  const removeSignForm = useForm<RemoveSignTaskFormValues>({
    resolver: zodResolver(removeSignTaskSchema),
    defaultValues: {
      targetTaskId: '',
      comment: '',
    },
  })
  const revokeForm = useForm<SimpleActionCommentFormValues>({
    resolver: zodResolver(simpleActionCommentSchema),
    defaultValues: {
      comment: '',
    },
  })
  const rejectForm = useForm<RejectTaskFormValues>({
    resolver: zodResolver(rejectTaskSchema),
    defaultValues: {
      targetStrategy: 'PREVIOUS_USER_TASK',
      targetTaskId: '',
      targetNodeId: '',
      reapproveStrategy: 'CONTINUE',
      comment: '',
    },
  })
  const jumpForm = useForm<JumpTaskFormValues>({
    resolver: zodResolver(jumpTaskSchema),
    defaultValues: {
      targetNodeId: '',
      comment: '',
    },
  })
  const takeBackForm = useForm<TakeBackTaskFormValues>({
    resolver: zodResolver(takeBackTaskSchema),
    defaultValues: {
      comment: '',
    },
  })
  const wakeUpForm = useForm<WakeUpTaskFormValues>({
    resolver: zodResolver(wakeUpTaskSchema),
    defaultValues: {
      sourceTaskId: '',
      comment: '',
    },
  })
  const urgeForm = useForm<SimpleActionCommentFormValues>({
    resolver: zodResolver(simpleActionCommentSchema),
    defaultValues: {
      comment: '',
    },
  })
  const collaborationForm = useForm<CollaborationEventFormValues>({
    resolver: zodResolver(collaborationEventSchema),
    defaultValues: {
      subject: '',
      content: '',
      mentionedUserId: '',
    },
  })

  useEffect(() => {
    if (!detail) {
      return
    }

    claimForm.reset({
      comment: '',
    })
    transferForm.reset({
      targetUserId: '',
      comment: '',
    })
    delegateForm.reset({
      targetUserId: '',
      comment: '',
    })
    returnForm.reset({
      comment: '',
    })
    addSignForm.reset({
      targetUserId: '',
      comment: '',
    })
    removeSignForm.reset({
      targetTaskId: '',
      comment: '',
    })
    revokeForm.reset({
      comment: '',
    })
    rejectForm.reset({
      targetStrategy: 'PREVIOUS_USER_TASK',
      targetTaskId: '',
      targetNodeId: '',
      reapproveStrategy: 'CONTINUE',
      comment: '',
    })
    jumpForm.reset({
      targetNodeId: '',
      comment: '',
    })
    takeBackForm.reset({
      comment: '',
    })
    wakeUpForm.reset({
      sourceTaskId: '',
      comment: '',
    })
    urgeForm.reset({
      comment: '',
    })
    collaborationForm.reset({
      subject: '',
      content: '',
      mentionedUserId: '',
    })
  }, [
    addSignForm,
    delegateForm,
    claimForm,
    detail,
    jumpForm,
    removeSignForm,
    rejectForm,
    returnForm,
    revokeForm,
    collaborationForm,
    takeBackForm,
    transferForm,
    urgeForm,
    wakeUpForm,
  ])

  async function refreshWorkbenchQueries() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['workbench', 'todo-page'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'todo-detail'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'approval-sheet-detail'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'todo-actions'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'collaboration-trace'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'time-travel-trace'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'termination-snapshot'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'termination-audit'] }),
      queryClient.invalidateQueries({ queryKey: ['oa'] }),
    ])
  }

  const completeMutation = useMutation({
    mutationFn: (payload: CompleteWorkbenchTaskPayload) =>
      completeWorkbenchTask(requireActionTaskId(), payload),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      if (locator.mode === 'task') {
        const nextTask = response.nextTasks[0]
        if (nextTask) {
          startTransition(() => {
            navigate({
              to: '/workbench/todos/$taskId',
              params: { taskId: nextTask.taskId },
            })
          })
          return
        }

        startTransition(() => {
          navigate({ to: '/workbench/todos/list' })
        })
      }
    },
    onError: handleServerError,
  })
  const claimMutation = useMutation({
    mutationFn: (payload: ClaimTaskFormValues) =>
      claimWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      claimForm.reset({
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const transferMutation = useMutation({
    mutationFn: (payload: TransferTaskFormValues) =>
      transferWorkbenchTask(requireActionTaskId(), {
        targetUserId: payload.targetUserId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setTransferDialogOpen(false)
      transferForm.reset({
        targetUserId: '',
        comment: '',
      })
      if (locator.mode === 'task') {
        startTransition(() => {
          navigate({ to: '/workbench/todos/list' })
        })
      }
    },
    onError: handleServerError,
  })
  const delegateMutation = useMutation({
    mutationFn: (payload: DelegateTaskFormValues) =>
      delegateWorkbenchTask(requireActionTaskId(), {
        targetUserId: payload.targetUserId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setDelegateDialogOpen(false)
      delegateForm.reset({
        targetUserId: '',
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const returnMutation = useMutation({
    mutationFn: (payload: ReturnTaskFormValues) =>
      returnWorkbenchTask(requireActionTaskId(), {
        targetStrategy: 'PREVIOUS_USER_TASK',
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setReturnDialogOpen(false)
      returnForm.reset({
        comment: '',
      })
      if (locator.mode === 'task') {
        startTransition(() => {
          navigate({ to: '/workbench/todos/list' })
        })
      }
    },
    onError: handleServerError,
  })
  const addSignMutation = useMutation({
    mutationFn: (payload: AddSignTaskFormValues) =>
      addSignWorkbenchTask(requireActionTaskId(), {
        targetUserId: payload.targetUserId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setAddSignDialogOpen(false)
      addSignForm.reset({
        targetUserId: '',
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const removeSignMutation = useMutation({
    mutationFn: (payload: RemoveSignTaskFormValues) =>
      removeSignWorkbenchTask(requireActionTaskId(), {
        targetTaskId: payload.targetTaskId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setRemoveSignDialogOpen(false)
      removeSignForm.reset({
        targetTaskId: '',
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const revokeMutation = useMutation({
    mutationFn: (payload: SimpleActionCommentFormValues) =>
      revokeWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setRevokeDialogOpen(false)
      revokeForm.reset({
        comment: '',
      })
      if (locator.mode === 'task') {
        startTransition(() => {
          navigate({ to: '/workbench/todos/list' })
        })
      }
    },
    onError: handleServerError,
  })
  const rejectMutation = useMutation({
    mutationFn: (payload: RejectTaskFormValues) => {
      const targetTaskId = payload.targetTaskId?.trim()
      const targetNodeId = payload.targetNodeId?.trim()

      return rejectWorkbenchTask(requireActionTaskId(), {
        targetStrategy: payload.targetStrategy,
        targetTaskId: targetTaskId || undefined,
        targetNodeId: targetNodeId || undefined,
        reapproveStrategy: payload.reapproveStrategy,
        comment: payload.comment?.trim() || undefined,
      })
    },
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setRejectDialogOpen(false)
      rejectForm.reset({
        targetStrategy: 'PREVIOUS_USER_TASK',
        targetTaskId: '',
        targetNodeId: '',
        reapproveStrategy: 'CONTINUE',
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const jumpMutation = useMutation({
    mutationFn: (payload: JumpTaskFormValues) =>
      jumpWorkbenchTask(requireActionTaskId(), {
        targetNodeId: payload.targetNodeId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setJumpDialogOpen(false)
      jumpForm.reset({
        targetNodeId: '',
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const takeBackMutation = useMutation({
    mutationFn: (payload: TakeBackTaskFormValues) =>
      takeBackWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setTakeBackDialogOpen(false)
      takeBackForm.reset({
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const wakeUpMutation = useMutation({
    mutationFn: (payload: WakeUpTaskFormValues) =>
      wakeUpWorkbenchInstance(requireActionInstanceId(), {
        sourceTaskId: payload.sourceTaskId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setWakeUpDialogOpen(false)
      wakeUpForm.reset({
        sourceTaskId: '',
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const urgeMutation = useMutation({
    mutationFn: (payload: SimpleActionCommentFormValues) =>
      urgeWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setUrgeDialogOpen(false)
      urgeForm.reset({
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const readMutation = useMutation({
    mutationFn: () => readWorkbenchTask(requireActionTaskId()),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
    },
    onError: handleServerError,
  })
  const collaborationMutation = useMutation({
    mutationFn: (payload: CollaborationEventFormValues) =>
      createProcessCollaborationEvent({
        instanceId: detail?.instanceId ?? null,
        taskId: detail?.taskId ?? null,
        eventType: 'COMMENT',
        subject: payload.subject?.trim() || null,
        content: payload.content.trim(),
        mentionedUserIds: payload.mentionedUserId?.trim()
          ? [payload.mentionedUserId.trim()]
          : [],
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setCollaborationDialogOpen(false)
      collaborationForm.reset({
        subject: '',
        content: '',
        mentionedUserId: '',
      })
    },
    onError: handleServerError,
  })

  const actionLabel = useMemo(() => {
    if (!detail) {
      return ''
    }

    return resolveTaskStatusLabel(detail.status)
  }, [detail])

  const onTransferSubmit = transferForm.handleSubmit((values) => {
    transferMutation.mutate(values)
  })
  const onDelegateSubmit = delegateForm.handleSubmit((values) => {
    delegateMutation.mutate(values)
  })
  const onReturnSubmit = returnForm.handleSubmit((values) => {
    returnMutation.mutate(values)
  })
  const onAddSignSubmit = addSignForm.handleSubmit((values) => {
    addSignMutation.mutate(values)
  })
  const onRemoveSignSubmit = removeSignForm.handleSubmit((values) => {
    removeSignMutation.mutate(values)
  })
  const onRevokeSubmit = revokeForm.handleSubmit((values) => {
    revokeMutation.mutate(values)
  })
  const onUrgeSubmit = urgeForm.handleSubmit((values) => {
    urgeMutation.mutate(values)
  })
  const showCompletionForm = Boolean(
    actionsQuery.data?.canApprove || actionsQuery.data?.canReject
  )
  const hasNodeForm = Boolean(detail?.nodeFormKey && detail?.nodeFormVersion)
  const hasMoreActions = Boolean(
    actionsQuery.data?.canAddSign ||
      actionsQuery.data?.canRemoveSign ||
      actionsQuery.data?.canRevoke ||
      actionsQuery.data?.canUrge ||
      actionsQuery.data?.canRead ||
      actionsQuery.data?.canDelegate ||
      actionsQuery.data?.canRejectRoute ||
      actionsQuery.data?.canJump ||
      actionsQuery.data?.canTakeBack ||
      actionsQuery.data?.canWakeUp
  )
  const showHeaderActionToolbar = Boolean(
    actionsQuery.isLoading ||
      actionsQuery.data?.canClaim ||
      actionsQuery.data?.canTransfer ||
      actionsQuery.data?.canReturn ||
      hasMoreActions
  )
  const rejectTargetStrategy =
    useWatch({
      control: rejectForm.control,
      name: 'targetStrategy',
    }) ?? 'PREVIOUS_USER_TASK'
  const businessBillHref = detail ? resolveBusinessBillHref(detail) : null
  const removableAddSignTasks = useMemo(
    () => (detail ? describePendingAddSignTask(detail) : []),
    [detail]
  )
  const detailRoutePath =
    locator.mode === 'task'
      ? `/workbench/todos/${locator.taskId}`
      : detail?.businessType === 'OA_LEAVE'
        ? `/oa/leave/${locator.businessId}`
        : detail?.businessType === 'OA_EXPENSE'
          ? `/oa/expense/${locator.businessId}`
          : detail?.businessType === 'OA_COMMON'
            ? `/oa/common/${locator.businessId}`
            : detail?.businessType === 'PLM_ECR'
              ? `/plm/ecr/${locator.businessId}`
              : detail?.businessType === 'PLM_ECO'
                ? `/plm/eco/${locator.businessId}`
                : detail?.businessType === 'PLM_MATERIAL'
                  ? `/plm/material-master/${locator.businessId}`
                  : '/workbench/todos/list'

  const detailActionToolbar: ReactNode = actionsQuery.isLoading ? (
    <div className='flex items-center gap-2'>
      <Skeleton className='h-9 w-20' />
      <Skeleton className='h-9 w-20' />
    </div>
  ) : showHeaderActionToolbar ? (
    <div className='flex flex-wrap items-center justify-end gap-2'>
      {hasMoreActions ? (
        <>
          {actionsQuery.data?.canDelegate ? (
            <Dialog open={delegateDialogOpen} onOpenChange={setDelegateDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>委派</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>委派</DialogTitle>
                  <DialogDescription>
                    当前办理人可以把任务委派给其他用户代办，同时保留原责任关系。
                  </DialogDescription>
                </DialogHeader>
                <Form {...delegateForm}>
                  <form className='space-y-4' onSubmit={onDelegateSubmit}>
                    <FormField
                      control={delegateForm.control}
                      name='targetUserId'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>委派用户</FormLabel>
                          <FormControl>
                            <UserPickerField
                              ariaLabel='委派用户'
                              value={field.value}
                              onChange={field.onChange}
                              placeholder='请选择委派用户'
                              displayNames={detail?.userDisplayNames}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={delegateForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>委派说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入委派说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setDelegateDialogOpen(false)}>
                        取消
                      </Button>
                      <Button type='submit' disabled={delegateMutation.isPending}>
                        {delegateMutation.isPending ? '委派中' : '确认委派'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canAddSign ? (
            <Dialog open={addSignDialogOpen} onOpenChange={setAddSignDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>加签</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>加签</DialogTitle>
                  <DialogDescription>为当前任务追加一位串行复核办理人。</DialogDescription>
                </DialogHeader>
                <Form {...addSignForm}>
                  <form className='space-y-4' onSubmit={onAddSignSubmit}>
                    <FormField
                      control={addSignForm.control}
                      name='targetUserId'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>加签用户</FormLabel>
                          <FormControl>
                            <UserPickerField
                              ariaLabel='加签用户'
                              value={field.value}
                              onChange={field.onChange}
                              placeholder='请选择加签用户'
                              displayNames={detail?.userDisplayNames}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={addSignForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>加签说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入加签说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setAddSignDialogOpen(false)}>
                        取消
                      </Button>
                      <Button type='submit' disabled={addSignMutation.isPending}>
                        {addSignMutation.isPending ? '加签中' : '确认加签'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canRemoveSign ? (
            <Dialog open={removeSignDialogOpen} onOpenChange={setRemoveSignDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>减签</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>减签</DialogTitle>
                  <DialogDescription>选择待移除的加签任务，系统会撤销该串行加签任务。</DialogDescription>
                </DialogHeader>
                <Form {...removeSignForm}>
                  <form className='space-y-4' onSubmit={onRemoveSignSubmit}>
                    <FormField
                      control={removeSignForm.control}
                      name='targetTaskId'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>加签任务</FormLabel>
                          <FormControl>
                            <select
                              className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                              value={field.value}
                              onChange={field.onChange}
                            >
                              <option value=''>请选择待移除的加签任务</option>
                              {removableAddSignTasks.map((task) => (
                                <option key={task.taskId} value={task.taskId}>
                                  {task.label} · {task.hint}
                                </option>
                              ))}
                            </select>
                          </FormControl>
                          {removableAddSignTasks.length === 0 ? (
                            <FormDescription>当前没有可减签的未完成加签任务。</FormDescription>
                          ) : null}
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={removeSignForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>减签说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入减签说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setRemoveSignDialogOpen(false)}>
                        取消
                      </Button>
                      <Button type='submit' disabled={removeSignMutation.isPending}>
                        {removeSignMutation.isPending ? '减签中' : '确认减签'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canRevoke ? (
            <Dialog open={revokeDialogOpen} onOpenChange={setRevokeDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>撤销</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>撤销流程</DialogTitle>
                  <DialogDescription>仅发起人可撤销，撤销后当前运行中的任务会一并终止。</DialogDescription>
                </DialogHeader>
                <Form {...revokeForm}>
                  <form className='space-y-4' onSubmit={onRevokeSubmit}>
                    <FormField
                      control={revokeForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>撤销说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入撤销说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setRevokeDialogOpen(false)}>
                        取消
                      </Button>
                      <Button type='submit' disabled={revokeMutation.isPending}>
                        {revokeMutation.isPending ? '撤销中' : '确认撤销'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canUrge ? (
            <Dialog open={urgeDialogOpen} onOpenChange={setUrgeDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>催办</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>催办</DialogTitle>
                  <DialogDescription>催办不会改变任务状态，但会写入实例动作轨迹。</DialogDescription>
                </DialogHeader>
                <Form {...urgeForm}>
                  <form className='space-y-4' onSubmit={onUrgeSubmit}>
                    <FormField
                      control={urgeForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>催办说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入催办说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setUrgeDialogOpen(false)}>
                        取消
                      </Button>
                      <Button type='submit' disabled={urgeMutation.isPending}>
                        {urgeMutation.isPending ? '催办中' : '确认催办'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canRead ? (
            <Button
              type='button'
              size='sm'
              variant='outline'
              disabled={readMutation.isPending}
              onClick={() => {
                readMutation.mutate()
              }}
            >
              {readMutation.isPending ? '处理中' : '已阅'}
            </Button>
          ) : null}

          {actionsQuery.data?.canRejectRoute ? (
            <Dialog open={rejectDialogOpen} onOpenChange={setRejectDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>驳回</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>驳回处理</DialogTitle>
                  <DialogDescription>选择驳回目标和重审策略，系统会通过专用驳回接口处理。</DialogDescription>
                </DialogHeader>
                <Form {...rejectForm}>
                  <form className='space-y-4' onSubmit={rejectForm.handleSubmit((values) => { rejectMutation.mutate(values) })}>
                    <FormField
                      control={rejectForm.control}
                      name='targetStrategy'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>驳回目标</FormLabel>
                          <FormControl>
                            <select className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm' {...field}>
                              <option value='PREVIOUS_USER_TASK'>上一步人工节点</option>
                              <option value='INITIATOR'>发起人</option>
                              <option value='ANY_USER_TASK'>任意节点</option>
                            </select>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    {rejectTargetStrategy === 'ANY_USER_TASK' ? (
                      <FormField
                        control={rejectForm.control}
                        name='targetNodeId'
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>目标节点编号</FormLabel>
                            <FormControl>
                              <Input placeholder='例如：approve_finance' {...field} />
                            </FormControl>
                            <FormDescription>当前版本通过节点编号指定任意驳回目标。</FormDescription>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    ) : (
                      <FormField
                        control={rejectForm.control}
                        name='targetTaskId'
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>目标任务编号</FormLabel>
                            <FormControl>
                              <Input placeholder='可选，系统会自动解析默认目标' {...field} />
                            </FormControl>
                            <FormDescription>上一步或发起人场景可留空，让系统自动回退。</FormDescription>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    )}
                    <FormField
                      control={rejectForm.control}
                      name='reapproveStrategy'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>重审策略</FormLabel>
                          <FormControl>
                            <select className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm' {...field}>
                              <option value='CONTINUE'>继续执行</option>
                              <option value='RETURN_TO_REJECTED_NODE'>退回驳回节点</option>
                            </select>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={rejectForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>驳回说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入驳回说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setRejectDialogOpen(false)}>取消</Button>
                      <Button type='submit' disabled={rejectMutation.isPending}>
                        {rejectMutation.isPending ? '驳回中' : '确认驳回'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canJump ? (
            <Dialog open={jumpDialogOpen} onOpenChange={setJumpDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>跳转</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>跳转</DialogTitle>
                  <DialogDescription>将当前任务强制路由到指定节点或结束节点。</DialogDescription>
                </DialogHeader>
                <Form {...jumpForm}>
                  <form className='space-y-4' onSubmit={jumpForm.handleSubmit((values) => { jumpMutation.mutate(values) })}>
                    <FormField
                      control={jumpForm.control}
                      name='targetNodeId'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>目标节点编号</FormLabel>
                          <FormControl>
                            <Input placeholder='例如：approve_finance' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={jumpForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>跳转说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入跳转说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setJumpDialogOpen(false)}>取消</Button>
                      <Button type='submit' disabled={jumpMutation.isPending}>
                        {jumpMutation.isPending ? '跳转中' : '确认跳转'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canTakeBack ? (
            <Dialog open={takeBackDialogOpen} onOpenChange={setTakeBackDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>拿回</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>拿回任务</DialogTitle>
                  <DialogDescription>在当前办理人未处理前，上一节点提交人可把任务拿回。</DialogDescription>
                </DialogHeader>
                <Form {...takeBackForm}>
                  <form className='space-y-4' onSubmit={takeBackForm.handleSubmit((values) => { takeBackMutation.mutate(values) })}>
                    <FormField
                      control={takeBackForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>拿回说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入拿回说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setTakeBackDialogOpen(false)}>取消</Button>
                      <Button type='submit' disabled={takeBackMutation.isPending}>
                        {takeBackMutation.isPending ? '拿回中' : '确认拿回'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}

          {actionsQuery.data?.canWakeUp ? (
            <Dialog open={wakeUpDialogOpen} onOpenChange={setWakeUpDialogOpen}>
              <DialogTrigger asChild>
                <Button type='button' size='sm' variant='outline'>唤醒</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>唤醒流程</DialogTitle>
                  <DialogDescription>从历史任务重新拉起终态实例，继续后续审批流程。</DialogDescription>
                </DialogHeader>
                <Form {...wakeUpForm}>
                  <form className='space-y-4' onSubmit={wakeUpForm.handleSubmit((values) => { wakeUpMutation.mutate(values) })}>
                    <FormField
                      control={wakeUpForm.control}
                      name='sourceTaskId'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>历史任务编号</FormLabel>
                          <FormControl>
                            <Input placeholder='例如：task_001' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={wakeUpForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>唤醒说明</FormLabel>
                          <FormControl>
                            <Textarea className='min-h-24' placeholder='请输入唤醒说明' {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <DialogFooter>
                      <Button type='button' variant='outline' onClick={() => setWakeUpDialogOpen(false)}>取消</Button>
                      <Button type='submit' disabled={wakeUpMutation.isPending}>
                        {wakeUpMutation.isPending ? '唤醒中' : '确认唤醒'}
                      </Button>
                    </DialogFooter>
                  </form>
                </Form>
              </DialogContent>
            </Dialog>
          ) : null}
        </>
      ) : null}

      {actionsQuery.data?.canClaim ? (
        <Button
          type='button'
          size='sm'
          disabled={claimMutation.isPending}
          onClick={() => claimMutation.mutate({ comment: '' })}
        >
          {claimMutation.isPending ? (
            <>
              <Loader2 className='animate-spin' />
              认领中
            </>
          ) : (
            <>
              <UserCheck2 />
              认领任务
            </>
          )}
        </Button>
      ) : null}

      {actionsQuery.data?.canTransfer ? (
        <Dialog open={transferDialogOpen} onOpenChange={setTransferDialogOpen}>
          <DialogTrigger asChild>
            <Button type='button' size='sm' variant='outline'>
              <UserRoundPlus />
              转办
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>转办任务</DialogTitle>
              <DialogDescription>选择目标用户后，当前任务会转到对方待办中。</DialogDescription>
            </DialogHeader>
            <Form {...transferForm}>
              <form className='space-y-4' onSubmit={onTransferSubmit}>
                <FormField
                  control={transferForm.control}
                  name='targetUserId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>目标用户</FormLabel>
                      <FormControl>
                        <UserPickerField
                          ariaLabel='目标用户'
                          value={field.value}
                          onChange={field.onChange}
                          placeholder='请选择目标用户'
                          displayNames={detail?.userDisplayNames}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={transferForm.control}
                  name='comment'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>转办说明</FormLabel>
                      <FormControl>
                        <Textarea className='min-h-24' placeholder='请输入转办说明' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <DialogFooter>
                  <Button type='button' variant='outline' onClick={() => setTransferDialogOpen(false)}>取消</Button>
                  <Button type='submit' disabled={transferMutation.isPending}>
                    {transferMutation.isPending ? (
                      <>
                        <Loader2 className='animate-spin' />
                        转办中
                      </>
                    ) : '确认转办'}
                  </Button>
                </DialogFooter>
              </form>
            </Form>
          </DialogContent>
        </Dialog>
      ) : null}

      {actionsQuery.data?.canReturn ? (
        <Dialog open={returnDialogOpen} onOpenChange={setReturnDialogOpen}>
          <DialogTrigger asChild>
            <Button type='button' size='sm' variant='outline'>
              <Undo2 />
              退回
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>退回上一步</DialogTitle>
              <DialogDescription>请填写退回说明，系统会把任务重新投递到上一步办理人。</DialogDescription>
            </DialogHeader>
            <Form {...returnForm}>
              <form className='space-y-4' onSubmit={onReturnSubmit}>
                <FormField
                  control={returnForm.control}
                  name='comment'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>退回说明</FormLabel>
                      <FormControl>
                        <Textarea className='min-h-24' placeholder='请输入退回说明' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <DialogFooter>
                  <Button type='button' variant='outline' onClick={() => setReturnDialogOpen(false)}>取消</Button>
                  <Button type='submit' disabled={returnMutation.isPending}>
                    {returnMutation.isPending ? (
                      <>
                        <Loader2 className='animate-spin' />
                        退回中
                      </>
                    ) : '确认退回'}
                  </Button>
                </DialogFooter>
              </form>
            </Form>
          </DialogContent>
        </Dialog>
      ) : null}
    </div>
  ) : null

  return (
    <PageShell
      title='审批单详情'
      description='统一审批单详情页，支持业务正文、流程回顾和运行态处理动作。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute={detailRoutePath}
            label='用 AI 解读当前审批单'
          />
          {businessBillHref ? (
            <Button asChild variant='secondary'>
              <Link to={businessBillHref.to} params={businessBillHref.params}>
                查看业务单
              </Link>
            </Button>
          ) : null}
          <Button asChild variant='outline'>
            <Link to={backHref} search={{}}>
              <ArrowLeft />
              {backLabel}
            </Link>
          </Button>
        </div>
      }
    >
      {detailQuery.isError ? (
        <Alert variant='destructive'>
          <AlertTitle>任务详情加载失败</AlertTitle>
          <AlertDescription>
            {detailQuery.error instanceof Error
              ? detailQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}

      {detailQuery.isLoading ? (
        <div className='grid gap-4'>
          <Card>
            <CardHeader>
              <Skeleton className='h-8 w-40' />
              <Skeleton className='h-4 w-80' />
            </CardHeader>
            <CardContent className='space-y-3'>
              <Skeleton className='h-10 w-full' />
              <Skeleton className='h-10 w-full' />
              <Skeleton className='h-10 w-full' />
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <Skeleton className='h-8 w-40' />
              <Skeleton className='h-4 w-60' />
            </CardHeader>
            <CardContent className='space-y-3'>
              <Skeleton className='h-32 w-full' />
            </CardContent>
          </Card>
        </div>
      ) : null}

      {detail ? (
        <div className='grid gap-4'>
          <Card>
            <CardHeader>
              <div className='flex items-center justify-between gap-3'>
                <div>
                  <CardTitle className='flex items-center gap-3'>
                    {detail.processName}
                    <Badge
                      variant={
                        detail.status === 'PENDING' ||
                        detail.status === 'PENDING_CLAIM'
                          ? 'destructive'
                          : 'secondary'
                      }
                    >
                      {actionLabel}
                    </Badge>
                  </CardTitle>
                  <CardDescription className='mt-2'>
                    {detail.processKey} · {detail.nodeName}
                  </CardDescription>
                </div>
                <Badge variant='outline'>{detail.instanceStatus}</Badge>
              </div>
            </CardHeader>
            <CardContent className='grid gap-4 md:grid-cols-2'>
              <div className='space-y-2 rounded-lg border bg-muted/30 p-4'>
                <p className='text-xs text-muted-foreground'>任务信息</p>
                <dl className='space-y-2 text-sm'>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>任务编号</dt>
                    <dd className='font-mono'>{detail.taskId}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>实例编号</dt>
                    <dd className='font-mono'>{detail.instanceId}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>业务单号</dt>
                    <dd>{detail.businessKey ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>发起人</dt>
                    <dd>
                      <ApprovalUserTag
                        userId={detail.applicantUserId}
                        displayNames={detail.userDisplayNames}
                      />
                    </dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>创建时间</dt>
                    <dd>{formatDateTime(detail.createdAt)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>完成时间</dt>
                    <dd>{formatDateTime(detail.completedAt)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>接收时间</dt>
                    <dd>{formatDateTime(detail.receiveTime)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>办理模式</dt>
                    <dd>{resolveApprovalSheetActingModeLabel(detail.actingMode)}</dd>
                  </div>
                  {detail.actingForUserId ? (
                    <div className='flex justify-between gap-3'>
                      <dt className='text-muted-foreground'>代谁办理</dt>
                      <dd>
                        <ApprovalUserTag
                          userId={detail.actingForUserId}
                          displayNames={detail.userDisplayNames}
                        />
                      </dd>
                    </div>
                  ) : null}
                  {detail.delegatedByUserId ? (
                    <div className='flex justify-between gap-3'>
                      <dt className='text-muted-foreground'>委派来源</dt>
                      <dd>
                        <ApprovalUserTag
                          userId={detail.delegatedByUserId}
                          displayNames={detail.userDisplayNames}
                        />
                      </dd>
                    </div>
                  ) : null}
                  {detail.handoverFromUserId ? (
                    <div className='flex justify-between gap-3'>
                      <dt className='text-muted-foreground'>离职转办来源</dt>
                      <dd>
                        <ApprovalUserTag
                          userId={detail.handoverFromUserId}
                          displayNames={detail.userDisplayNames}
                        />
                      </dd>
                    </div>
                  ) : null}
                </dl>
              </div>

              <div className='space-y-2 rounded-lg border bg-muted/30 p-4'>
                <p className='text-xs text-muted-foreground'>节点信息</p>
                <dl className='space-y-2 text-sm'>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>当前节点</dt>
                    <dd>{detail.nodeName}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>处理方式</dt>
                    <dd>{detail.assignmentMode ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>当前办理人</dt>
                    <dd>{resolveCurrentHandlerContent(detail)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>候选人</dt>
                    <dd>
                      <ApprovalTagList
                        ids={detail.candidateUserIds}
                        displayNames={detail.userDisplayNames}
                      />
                    </dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>候选组</dt>
                    <dd>
                      <ApprovalTagList
                        ids={detail.candidateGroupIds}
                        displayNames={detail.groupDisplayNames}
                      />
                    </dd>
                  </div>
                  {!detail.assigneeUserId && candidateHandlersQuery.data?.length ? (
                    <div className='flex justify-between gap-3'>
                      <dt className='text-muted-foreground'>可办理人员</dt>
                      <dd>
                        <div className='flex flex-wrap justify-end gap-2'>
                          {candidateHandlersQuery.data.map((user) => (
                            <ApprovalUserTag
                              key={user.userId}
                              userId={user.userId}
                              displayNames={{
                                ...detail.userDisplayNames,
                                [user.userId]: user.displayName,
                              }}
                            />
                          ))}
                        </div>
                      </dd>
                    </div>
                  ) : null}
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>最新动作</dt>
                    <dd>{detail.action ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>办理人</dt>
                    <dd>
                      <ApprovalUserTag
                        userId={detail.operatorUserId}
                        displayNames={detail.userDisplayNames}
                      />
                    </dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>当前待办</dt>
                    <dd>{detail.activeTaskIds.length}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>阅读时间</dt>
                    <dd>{formatDateTime(detail.readTime)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>办理开始</dt>
                    <dd>{formatDateTime(detail.handleStartTime)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>办理时长</dt>
                    <dd>
                      {detail.handleDurationSeconds === null ||
                      detail.handleDurationSeconds === undefined
                        ? '--'
                        : `${detail.handleDurationSeconds} 秒`}
                    </dd>
                  </div>
                </dl>
              </div>

              <div className='md:col-span-2 space-y-4'>
                <ApprovalSheetBusinessSection
                  detail={detail}
                  headerActions={detailActionToolbar}
                >
                  {showCompletionForm ? (
                    <TaskRuntimeFormCard
                      key={`${detail.taskId}:${detail.updatedAt}:${detail.effectiveFormKey}:${detail.effectiveFormVersion}`}
                      detail={detail}
                      hasNodeForm={hasNodeForm}
                      showCompletionForm={showCompletionForm}
                      onSubmit={(payload) => {
                        if (payload.action === 'REJECT') {
                          rejectMutation.mutate({
                            targetStrategy: 'PREVIOUS_USER_TASK',
                            targetTaskId: '',
                            targetNodeId: '',
                            reapproveStrategy: 'CONTINUE',
                            comment: payload.comment ?? undefined,
                          })
                          return
                        }

                        completeMutation.mutate(payload)
                      }}
                      isPending={completeMutation.isPending || rejectMutation.isPending}
                    />
                  ) : null}
                </ApprovalSheetBusinessSection>
                <Card className='border-dashed shadow-none'>
                  <CardContent className='p-4'>
                    <Tabs defaultValue='overview' className='gap-4'>
                      <TabsList className='grid w-full grid-cols-4'>
                        <TabsTrigger value='overview'>概览</TabsTrigger>
                        <TabsTrigger value='runtime'>运行态</TabsTrigger>
                        <TabsTrigger value='automation'>自动化</TabsTrigger>
                        <TabsTrigger value='trace'>轨迹</TabsTrigger>
                      </TabsList>
                      <TabsContent value='overview' className='space-y-4'>
                        <ApprovalSheetGraph
                          flowNodes={detail.flowNodes ?? []}
                          flowEdges={detail.flowEdges ?? []}
                          taskTrace={detail.taskTrace ?? []}
                          instanceEvents={detail.instanceEvents ?? []}
                          instanceStatus={detail.instanceStatus}
                          userDisplayNames={detail.userDisplayNames}
                        />
                        <ApprovalSheetCountersignSection
                          countersignGroups={detail.countersignGroups ?? []}
                        />
                        <InclusiveGatewaySection
                          hits={detail.inclusiveGatewayHits ?? []}
                        />
                      </TabsContent>
                      <TabsContent value='runtime' className='space-y-4'>
                        <ApprovalSheetProcessLinkSection
                          links={mergeRuntimeStructureLinks(
                            detail.processLinks ?? [],
                            detail.runtimeStructureLinks ?? []
                          )}
                          currentInstanceId={detail.instanceId}
                          onConfirmParentResume={(link) => {
                            confirmParentResumeMutation.mutate({
                              instanceId: detail.instanceId,
                              linkId: link.linkId,
                            })
                          }}
                          pendingLinkId={
                            confirmParentResumeMutation.isPending
                              ? confirmParentResumeMutation.variables?.linkId ?? null
                              : null
                          }
                        />
                        <ProcessTerminationSection
                          snapshot={terminationSnapshotQuery.data ?? null}
                          audits={terminationAuditQuery.data ?? []}
                        />
                        <ProcessCollaborationSection
                          items={collaborationTraceQuery.data ?? []}
                          actions={detail?.instanceId ? (
                            <Dialog
                              open={collaborationDialogOpen}
                              onOpenChange={setCollaborationDialogOpen}
                            >
                              <DialogTrigger asChild>
                                <Button type='button' size='sm' variant='outline'>
                                  发起协同
                                </Button>
                              </DialogTrigger>
                              <DialogContent>
                                <DialogHeader>
                                  <DialogTitle>发起协同</DialogTitle>
                                  <DialogDescription>
                                    发送一条协同知会、批注或提醒，记录会进入当前实例的协同轨迹。
                                  </DialogDescription>
                                </DialogHeader>
                                <Form {...collaborationForm}>
                                  <form
                                    className='space-y-4'
                                    onSubmit={collaborationForm.handleSubmit((values) => {
                                      collaborationMutation.mutate(values)
                                    })}
                                  >
                                    <FormField
                                      control={collaborationForm.control}
                                      name='subject'
                                      render={({ field }) => (
                                        <FormItem>
                                          <FormLabel>协同标题</FormLabel>
                                          <FormControl>
                                            <Input placeholder='例如：请协助补充背景说明' {...field} />
                                          </FormControl>
                                          <FormMessage />
                                        </FormItem>
                                      )}
                                    />
                                    <FormField
                                      control={collaborationForm.control}
                                      name='mentionedUserId'
                                      render={({ field }) => (
                                        <FormItem>
                                          <FormLabel>@提醒人员</FormLabel>
                                          <FormControl>
                                            <UserPickerField
                                              value={field.value}
                                              onChange={field.onChange}
                                              placeholder='可选，选择需要提醒的人员'
                                              ariaLabel='协同提醒人员'
                                              displayNames={detail.userDisplayNames}
                                            />
                                          </FormControl>
                                          <FormMessage />
                                        </FormItem>
                                      )}
                                    />
                                    <FormField
                                      control={collaborationForm.control}
                                      name='content'
                                      render={({ field }) => (
                                        <FormItem>
                                          <FormLabel>协同内容</FormLabel>
                                          <FormControl>
                                            <Textarea
                                              className='min-h-28'
                                              placeholder='请输入协同知会、备注或提醒内容'
                                              {...field}
                                            />
                                          </FormControl>
                                          <FormMessage />
                                        </FormItem>
                                      )}
                                    />
                                    <DialogFooter>
                                      <Button
                                        type='button'
                                        variant='outline'
                                        onClick={() => setCollaborationDialogOpen(false)}
                                      >
                                        取消
                                      </Button>
                                      <Button type='submit' disabled={collaborationMutation.isPending}>
                                        {collaborationMutation.isPending ? '发送中' : '发送协同'}
                                      </Button>
                                    </DialogFooter>
                                  </form>
                                </Form>
                              </DialogContent>
                            </Dialog>
                          ) : null}
                        />
                        <ProcessTimeTravelSection
                          items={timeTravelTraceQuery.data ?? []}
                        />
                      </TabsContent>
                      <TabsContent value='automation' className='space-y-4'>
                        <ApprovalSheetAutomationStatusCard
                          automationStatus={detail.automationStatus}
                          actionTraceCount={detail.automationActionTrace?.length ?? 0}
                          notificationCount={detail.notificationSendRecords?.length ?? 0}
                        />
                        <ApprovalSheetAutomationActionTimeline
                          automationActionTrace={detail.automationActionTrace ?? []}
                        />
                        <NotificationSendRecordSection
                          notificationSendRecords={detail.notificationSendRecords ?? []}
                        />
                      </TabsContent>
                      <TabsContent value='trace' className='space-y-4'>
                        <ApprovalSheetActionTimeline
                          taskTrace={detail.taskTrace ?? []}
                          userDisplayNames={detail.userDisplayNames}
                        />
                      </TabsContent>
                    </Tabs>
                  </CardContent>
                </Card>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}
    </PageShell>
  )
}
