import { startTransition, useEffect, useMemo, useState } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { ClipboardList, FileText, Loader2, Send } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
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
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { ContextualCopilotEntry } from '@/features/ai/context-entry'
import {
  ProFormActions,
  ProFormLabel,
  ProFormSection,
  ProFormShell,
  UserPickerField,
} from '@/features/shared/pro-form'
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
  getOACommonRequestBillDetail,
  getOAExpenseBillDetail,
  getOALeaveBillDetail,
  listOACommonDrafts,
  listOAExpenseDrafts,
  listOALeaveDrafts,
  saveOACommonRequestDraft,
  saveOAExpenseDraft,
  saveOALeaveDraft,
  type OALaunchResponse,
  type OADraftListItem,
  submitOACommonRequestDraft,
  submitOAExpenseDraft,
  submitOALeaveDraft,
  updateOACommonRequestDraft,
  updateOAExpenseDraft,
  updateOALeaveDraft,
} from '@/lib/api/oa'
import {
  listApprovalSheets,
  type ApprovalSheetPageResponse,
  type ApprovalSheetListItem,
} from '@/lib/api/workbench'

const leaveFormSchema = z.object({
  leaveType: z.string().trim().min(1, '请选择请假类型'),
  days: z
    .string()
    .trim()
    .min(1, '请填写请假天数')
    .refine((value) => Number.isInteger(Number(value)) && Number(value) > 0, {
      message: '请填写大于 0 的请假天数',
    }),
  reason: z.string().trim().min(2, '请填写请假原因'),
  urgent: z.boolean(),
  managerUserId: z.string().trim().min(1, '请选择直属负责人'),
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

type OABoardStatus = 'DRAFT' | 'RUNNING' | 'APPROVED' | 'REJECTED' | 'REVOKED'

const leaveDetailRoute = getRouteApi('/_authenticated/oa/leave/$billId')
const expenseDetailRoute = getRouteApi('/_authenticated/oa/expense/$billId')
const commonDetailRoute = getRouteApi('/_authenticated/oa/common/$billId')
const leaveCreateRoute = getRouteApi('/_authenticated/oa/leave/create')
const expenseCreateRoute = getRouteApi('/_authenticated/oa/expense/create')
const commonCreateRoute = getRouteApi('/_authenticated/oa/common/create')
const leaveListRoute = getRouteApi('/_authenticated/oa/leave/list')
const expenseListRoute = getRouteApi('/_authenticated/oa/expense/list')
const commonListRoute = getRouteApi('/_authenticated/oa/common/list')
const oaQueryRoute = getRouteApi('/_authenticated/oa/query')

const OA_DRAFT_FETCH_SIZE = 100
const EMPTY_APPROVAL_SHEET_PAGE: ApprovalSheetPageResponse = {
  page: 1,
  pageSize: OA_DRAFT_FETCH_SIZE,
  total: 0,
  pages: 0,
  records: [],
  groups: [],
}

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
        <CardDescription>
          {response.processInstanceId
            ? '业务单据已经保存并自动进入流程中心。'
            : '业务单据已暂存为草稿，可继续编辑后再提交。'}
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-3 text-sm'>
        <Alert>
          <Send />
          <AlertTitle>{response.processInstanceId ? '提交成功' : '草稿已暂存'}</AlertTitle>
          <AlertDescription>
            单号 {response.billNo}
            {response.processInstanceId ? ` · 实例 ${response.processInstanceId}` : ''}
          </AlertDescription>
        </Alert>
        <p className='text-muted-foreground'>
          {response.processInstanceId
            ? `首个待办任务：${response.activeTasks[0]?.nodeName ?? '流程已结束或未产生待办'}`
            : '草稿尚未进入审批流程。'}
        </p>
      </CardContent>
    </Card>
  )
}

function matchesDraftKeyword(draft: OADraftListItem, keyword: string) {
  const normalizedKeyword = keyword.trim().toLowerCase()
  if (!normalizedKeyword) {
    return true
  }

  return [
    draft.billNo,
    draft.businessTitle,
    draft.creatorUserId,
    draft.sceneCode ?? '',
  ]
    .join(' ')
    .toLowerCase()
    .includes(normalizedKeyword)
}

function resolveDraftProcessMeta(businessType: OABusinessType) {
  switch (businessType) {
    case 'OA_LEAVE':
      return { processKey: 'oa_leave', processName: '请假审批' }
    case 'OA_EXPENSE':
      return { processKey: 'oa_expense', processName: '报销审批' }
    case 'OA_COMMON':
      return { processKey: 'oa_common', processName: '通用申请' }
  }
}

function mapDraftToApprovalSheetItem(draft: OADraftListItem): ApprovalSheetListItem {
  const processMeta = resolveDraftProcessMeta(draft.businessType)
  return {
    instanceId: `draft:${draft.businessType}:${draft.billId}`,
    processDefinitionId: '',
    processKey: processMeta.processKey,
    processName: processMeta.processName,
    businessId: draft.billId,
    businessType: draft.businessType,
    billNo: draft.billNo,
    businessTitle: draft.businessTitle,
    initiatorUserId: draft.creatorUserId,
    currentNodeName: '草稿',
    currentTaskId: null,
    currentTaskStatus: null,
    currentAssigneeUserId: null,
    instanceStatus: 'DRAFT',
    latestAction: 'DRAFT',
    latestOperatorUserId: draft.creatorUserId,
    automationStatus: 'DISABLED',
    createdAt: draft.createdAt,
    updatedAt: draft.updatedAt,
    completedAt: null,
  }
}

function compareApprovalSheetRecords(
  left: ApprovalSheetListItem,
  right: ApprovalSheetListItem,
  search: ListQuerySearch
) {
  const [primarySort] = search.sorts
  const field = primarySort?.field ?? 'updatedAt'
  const direction = primarySort?.direction === 'asc' ? 1 : -1

  const pickValue = (record: ApprovalSheetListItem) => {
    const rawValue = record[field as keyof ApprovalSheetListItem]
    if (field.endsWith('At')) {
      return rawValue ? new Date(String(rawValue)).getTime() : 0
    }
    return String(rawValue ?? '')
  }

  const leftValue = pickValue(left)
  const rightValue = pickValue(right)

  if (typeof leftValue === 'number' && typeof rightValue === 'number') {
    return (leftValue - rightValue) * direction
  }

  return String(leftValue).localeCompare(String(rightValue), 'zh-CN') * direction
}

function buildMergedApprovalSheetRecords(
  approvalPage: ApprovalSheetPageResponse,
  drafts: OADraftListItem[],
  search: ListQuerySearch
) {
  const draftRecords = drafts
    .filter((draft) => matchesDraftKeyword(draft, search.keyword))
    .map(mapDraftToApprovalSheetItem)
  return [...draftRecords, ...approvalPage.records].sort((left, right) =>
    compareApprovalSheetRecords(left, right, search)
  )
}

function buildMergedApprovalSheetPage(
  approvalPage: ApprovalSheetPageResponse,
  drafts: OADraftListItem[],
  search: ListQuerySearch
): ApprovalSheetPageResponse {
  const mergedRecords = buildMergedApprovalSheetRecords(approvalPage, drafts, search)
  const startIndex = Math.max(search.page - 1, 0) * search.pageSize
  const endIndex = startIndex + search.pageSize
  const total = mergedRecords.length

  return {
    page: search.page,
    pageSize: search.pageSize,
    total,
    pages: total === 0 ? 0 : Math.ceil(total / search.pageSize),
    records: mergedRecords.slice(startIndex, endIndex),
    groups: approvalPage.groups,
  }
}

function resolveOABoardStatus(item: ApprovalSheetListItem): OABoardStatus {
  if (item.instanceStatus === 'DRAFT') {
    return 'DRAFT'
  }
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
    DRAFT: [] as ApprovalSheetListItem[],
    RUNNING: [] as ApprovalSheetListItem[],
    APPROVED: [] as ApprovalSheetListItem[],
    REJECTED: [] as ApprovalSheetListItem[],
    REVOKED: [] as ApprovalSheetListItem[],
  }

  for (const record of records) {
    grouped[resolveOABoardStatus(record)].push(record)
  }

  return [
    { key: 'DRAFT', title: '草稿', items: grouped.DRAFT },
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

function FormSectionTitle({
  icon: Icon,
  title,
}: {
  icon: typeof ClipboardList
  title: string
}) {
  return (
    <div className='flex items-center gap-3 pt-1'>
      <span className='flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary shadow-sm'>
        <Icon className='size-4' />
      </span>
      <div className='text-lg font-semibold tracking-tight text-foreground'>{title}</div>
    </div>
  )
}

function LeaveCreateForm() {
  const search = leaveCreateRoute.useSearch()
  const navigate = useNavigate()
  const [latestResponse, setLatestResponse] = useState<OALaunchResponse | null>(null)
  const draftId = search.draftId
  const form = useForm<LeaveFormValues>({
    resolver: zodResolver(leaveFormSchema),
    defaultValues: {
      leaveType: 'ANNUAL',
      days: '1',
      reason: '',
      urgent: false,
      managerUserId: 'usr_002',
    },
  })
  const draftDetailQuery = useQuery({
    queryKey: ['oa', 'leave-draft', draftId],
    enabled: Boolean(draftId),
    queryFn: () => getOALeaveBillDetail(draftId!),
  })

  useEffect(() => {
    if (!draftDetailQuery.data) {
      return
    }
    form.reset({
      leaveType: draftDetailQuery.data.leaveType || 'ANNUAL',
      days: String(draftDetailQuery.data.days ?? 1),
      reason: draftDetailQuery.data.reason ?? '',
      urgent: Boolean(draftDetailQuery.data.urgent),
      managerUserId: draftDetailQuery.data.managerUserId ?? 'usr_002',
    })
  }, [draftDetailQuery.data, form])

  const saveDraftMutation = useMutation({
    mutationFn: (payload: LeaveFormValues) =>
      draftId
        ? updateOALeaveDraft(draftId, {
            leaveType: payload.leaveType,
            days: Number(payload.days),
            reason: payload.reason.trim(),
            urgent: payload.urgent,
            managerUserId: payload.managerUserId.trim(),
          })
        : saveOALeaveDraft({
            leaveType: payload.leaveType,
            days: Number(payload.days),
            reason: payload.reason.trim(),
            urgent: payload.urgent,
            managerUserId: payload.managerUserId.trim(),
          }),
    onSuccess: (response) => {
      setLatestResponse(response)
      toast.success(draftId ? '请假草稿已更新' : '请假草稿已暂存')
      startTransition(() => {
        navigate({
          to: '/oa/leave/create',
          search: { draftId: response.billId },
          replace: true,
        })
      })
    },
    onError: handleServerError,
  })

  const launchMutation = useMutation({
    mutationFn: (payload: LeaveFormValues) =>
      draftId
        ? submitOALeaveDraft(draftId, {
            leaveType: payload.leaveType,
            days: Number(payload.days),
            reason: payload.reason.trim(),
            urgent: payload.urgent,
            managerUserId: payload.managerUserId.trim(),
          })
        : createOALeaveBill({
            leaveType: payload.leaveType,
            days: Number(payload.days),
            reason: payload.reason.trim(),
            urgent: payload.urgent,
            managerUserId: payload.managerUserId.trim(),
          }),
    onSuccess: (response) => {
      setLatestResponse(response)
      navigateToApprovalSheetDetail(navigate, response, '/oa/leave/$billId')
    },
    onError: handleServerError,
  })

  return (
    <ProFormShell
      title='请假申请'
      description={draftId ? '继续编辑请假草稿，确认后再提交到当前生效流程。' : '填写请假信息后会自动提交到当前生效流程。'}
      className='max-w-4xl'
    >
      {draftDetailQuery.isError ? (
        <Alert variant='destructive'>
          <AlertTitle>草稿加载失败</AlertTitle>
          <AlertDescription>
            {draftDetailQuery.error instanceof Error
              ? draftDetailQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <Form {...form}>
        <form
          className='space-y-8'
          onSubmit={form.handleSubmit((values) =>
            launchMutation.mutate(values)
          )}
        >
          <div className='grid gap-5'>
            <FormSectionTitle icon={ClipboardList} title='申请信息' />

            <FormField
              control={form.control}
              name='leaveType'
              render={({ field }) => (
                <FormItem>
                  <ProFormLabel required>请假类型</ProFormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder='请选择请假类型' />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value='ANNUAL'>年假</SelectItem>
                      <SelectItem value='PERSONAL'>事假</SelectItem>
                      <SelectItem value='SICK'>病假</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='days'
              render={({ field }) => (
                <FormItem>
                  <ProFormLabel required>请假天数</ProFormLabel>
                  <FormControl>
                    <Input type='number' min='1' placeholder='请输入请假天数' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='urgent'
              render={({ field }) => (
                <FormItem className='flex items-center justify-between rounded-xl border px-4 py-3'>
                  <div className='space-y-0.5'>
                    <ProFormLabel>是否紧急</ProFormLabel>
                    <FormDescription>紧急请假会触发 HR 备案分支</FormDescription>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='managerUserId'
              render={({ field }) => (
                <FormItem>
                  <ProFormLabel required>直属负责人</ProFormLabel>
                  <FormControl>
                    <UserPickerField
                      id='oa-leave-manager-user'
                      value={field.value}
                      onChange={field.onChange}
                      placeholder='请选择直属负责人'
                      ariaLabel='直属负责人'
                    />
                  </FormControl>
                  <FormDescription>将作为负责人确认节点的默认办理人</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormSectionTitle icon={FileText} title='申请说明' />

            <FormField
              control={form.control}
              name='reason'
              render={({ field }) => (
                <FormItem>
                  <ProFormLabel required>请假原因</ProFormLabel>
                  <FormControl>
                    <Textarea className='min-h-28' placeholder='请输入请假原因' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>

          <ProFormActions>
            <Button
              type='button'
              variant='outline'
              disabled={saveDraftMutation.isPending || launchMutation.isPending}
              onClick={() => {
                void form.handleSubmit((values) => saveDraftMutation.mutate(values))()
              }}
            >
              {saveDraftMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  暂存中
                </>
              ) : (
                '暂存草稿'
              )}
            </Button>
            <Button type='submit' disabled={launchMutation.isPending}>
              {launchMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  提交中
                </>
              ) : (
                '提交请假申请'
              )}
            </Button>
            <Button asChild type='button' variant='outline'>
              <Link to='/workbench/start'>返回业务入口</Link>
            </Button>
          </ProFormActions>
        </form>
      </Form>
      <LaunchSummaryCard response={latestResponse} />
    </ProFormShell>
  )
}

// OA 发起页只做业务表单，提交后由后端决定绑定到哪个流程。
function ExpenseCreateForm() {
  const search = expenseCreateRoute.useSearch()
  const navigate = useNavigate()
  const [latestResponse, setLatestResponse] = useState<OALaunchResponse | null>(null)
  const draftId = search.draftId
  const form = useForm<ExpenseFormValues>({
    resolver: zodResolver(expenseFormSchema),
    defaultValues: {
      amount: '',
      reason: '',
    },
  })
  const draftDetailQuery = useQuery({
    queryKey: ['oa', 'expense-draft', draftId],
    enabled: Boolean(draftId),
    queryFn: () => getOAExpenseBillDetail(draftId!),
  })

  useEffect(() => {
    if (!draftDetailQuery.data) {
      return
    }
    form.reset({
      amount:
        draftDetailQuery.data.amount == null ? '' : String(draftDetailQuery.data.amount),
      reason: draftDetailQuery.data.reason ?? '',
    })
  }, [draftDetailQuery.data, form])

  const saveDraftMutation = useMutation({
    mutationFn: (payload: ExpenseFormValues) =>
      draftId
        ? updateOAExpenseDraft(draftId, {
            amount: Number(payload.amount),
            reason: payload.reason.trim(),
          })
        : saveOAExpenseDraft({
            amount: Number(payload.amount),
            reason: payload.reason.trim(),
          }),
    onSuccess: (response) => {
      setLatestResponse(response)
      toast.success(draftId ? '报销草稿已更新' : '报销草稿已暂存')
      startTransition(() => {
        navigate({
          to: '/oa/expense/create',
          search: { draftId: response.billId },
          replace: true,
        })
      })
    },
    onError: handleServerError,
  })

  const launchMutation = useMutation({
    mutationFn: (payload: ExpenseFormValues) =>
      draftId
        ? submitOAExpenseDraft(draftId, {
            amount: Number(payload.amount),
            reason: payload.reason.trim(),
          })
        : createOAExpenseBill({
            amount: Number(payload.amount),
            reason: payload.reason.trim(),
          }),
    onSuccess: (response) => {
      setLatestResponse(response)
      navigateToApprovalSheetDetail(navigate, response, '/oa/expense/$billId')
    },
    onError: handleServerError,
  })

  return (
    <ProFormShell
      title='报销申请'
      description={draftId ? '继续编辑报销草稿，确认后再提交到当前生效流程。' : '填写金额和事由，提交后自动匹配当前生效流程。'}
    >
      {draftDetailQuery.isError ? (
        <Alert variant='destructive'>
          <AlertTitle>草稿加载失败</AlertTitle>
          <AlertDescription>
            {draftDetailQuery.error instanceof Error
              ? draftDetailQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <Form {...form}>
        <form
          className='space-y-6'
          onSubmit={form.handleSubmit((values) => launchMutation.mutate(values))}
        >
          <ProFormSection title='报销信息' columns={1}>
            <FormField
              control={form.control}
              name='amount'
              render={({ field }) => (
                <FormItem>
                  <ProFormLabel required>报销金额</ProFormLabel>
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
                  <ProFormLabel required>报销事由</ProFormLabel>
                  <FormControl>
                    <Textarea className='min-h-28' placeholder='请输入报销事由' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormActions>
            <Button
              type='button'
              variant='outline'
              disabled={saveDraftMutation.isPending || launchMutation.isPending}
              onClick={() => {
                void form.handleSubmit((values) => saveDraftMutation.mutate(values))()
              }}
            >
              {saveDraftMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  暂存中
                </>
              ) : (
                '暂存草稿'
              )}
            </Button>
            <Button type='submit' disabled={launchMutation.isPending}>
              {launchMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  提交中
                </>
              ) : (
                '提交报销申请'
              )}
            </Button>
            <Button asChild type='button' variant='outline'>
              <Link to='/workbench/start'>返回业务入口</Link>
            </Button>
          </ProFormActions>
        </form>
      </Form>
      <LaunchSummaryCard response={latestResponse} />
    </ProFormShell>
  )
}

function CommonCreateForm() {
  const search = commonCreateRoute.useSearch()
  const navigate = useNavigate()
  const [latestResponse, setLatestResponse] = useState<OALaunchResponse | null>(null)
  const draftId = search.draftId
  const form = useForm<CommonFormValues>({
    resolver: zodResolver(commonFormSchema),
    defaultValues: {
      title: '',
      content: '',
    },
  })
  const draftDetailQuery = useQuery({
    queryKey: ['oa', 'common-draft', draftId],
    enabled: Boolean(draftId),
    queryFn: () => getOACommonRequestBillDetail(draftId!),
  })

  useEffect(() => {
    if (!draftDetailQuery.data) {
      return
    }
    form.reset({
      title: draftDetailQuery.data.title ?? '',
      content: draftDetailQuery.data.content ?? '',
    })
  }, [draftDetailQuery.data, form])

  const saveDraftMutation = useMutation({
    mutationFn: (payload: CommonFormValues) =>
      draftId
        ? updateOACommonRequestDraft(draftId, {
            title: payload.title.trim(),
            content: payload.content.trim(),
          })
        : saveOACommonRequestDraft({
            title: payload.title.trim(),
            content: payload.content.trim(),
          }),
    onSuccess: (response) => {
      setLatestResponse(response)
      toast.success(draftId ? '通用申请草稿已更新' : '通用申请草稿已暂存')
      startTransition(() => {
        navigate({
          to: '/oa/common/create',
          search: { draftId: response.billId },
          replace: true,
        })
      })
    },
    onError: handleServerError,
  })

  const launchMutation = useMutation({
    mutationFn: (payload: CommonFormValues) =>
      draftId
        ? submitOACommonRequestDraft(draftId, {
            title: payload.title.trim(),
            content: payload.content.trim(),
          })
        : createOACommonRequestBill({
            title: payload.title.trim(),
            content: payload.content.trim(),
          }),
    onSuccess: (response) => {
      setLatestResponse(response)
      navigateToApprovalSheetDetail(navigate, response, '/oa/common/$billId')
    },
    onError: handleServerError,
  })

  return (
    <ProFormShell
      title='通用申请'
      description={draftId ? '继续编辑通用申请草稿，确认后再提交。' : '用于标准 OA 之外的通用业务申请入口。'}
    >
      {draftDetailQuery.isError ? (
        <Alert variant='destructive'>
          <AlertTitle>草稿加载失败</AlertTitle>
          <AlertDescription>
            {draftDetailQuery.error instanceof Error
              ? draftDetailQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <Form {...form}>
        <form
          className='space-y-6'
          onSubmit={form.handleSubmit((values) => launchMutation.mutate(values))}
        >
          <ProFormSection title='申请信息' columns={1}>
            <FormField
              control={form.control}
              name='title'
              render={({ field }) => (
                <FormItem>
                  <ProFormLabel required>申请标题</ProFormLabel>
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
                  <ProFormLabel required>申请内容</ProFormLabel>
                  <FormControl>
                    <Textarea className='min-h-28' placeholder='请输入申请内容' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </ProFormSection>

          <ProFormActions>
            <Button
              type='button'
              variant='outline'
              disabled={saveDraftMutation.isPending || launchMutation.isPending}
              onClick={() => {
                void form.handleSubmit((values) => saveDraftMutation.mutate(values))()
              }}
            >
              {saveDraftMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  暂存中
                </>
              ) : (
                '暂存草稿'
              )}
            </Button>
            <Button type='submit' disabled={launchMutation.isPending}>
              {launchMutation.isPending ? (
                <>
                  <Loader2 className='animate-spin' />
                  提交中
                </>
              ) : (
                '提交通用申请'
              )}
            </Button>
            <Button asChild type='button' variant='outline'>
              <Link to='/workbench/start'>返回业务入口</Link>
            </Button>
          </ProFormActions>
        </form>
      </Form>
      <LaunchSummaryCard response={latestResponse} />
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
        page: 1,
        pageSize: OA_DRAFT_FETCH_SIZE,
        view: 'INITIATED',
        businessTypes: [businessType],
      }),
  })
  const draftListQuery = useQuery({
    queryKey: ['oa', 'business-drafts', businessType],
    queryFn: () => {
      switch (businessType) {
        case 'OA_LEAVE':
          return listOALeaveDrafts()
        case 'OA_EXPENSE':
          return listOAExpenseDrafts()
        case 'OA_COMMON':
          return listOACommonDrafts()
      }
    },
  })

  const pageData = useMemo(
    () => approvalSheetsQuery.data ?? EMPTY_APPROVAL_SHEET_PAGE,
    [approvalSheetsQuery.data]
  )
  const mergedRecords = useMemo(
    () => buildMergedApprovalSheetRecords(pageData, draftListQuery.data ?? [], search),
    [draftListQuery.data, pageData, search]
  )
  const mergedPageData = useMemo(
    () => buildMergedApprovalSheetPage(pageData, draftListQuery.data ?? [], search),
    [draftListQuery.data, pageData, search]
  )
  const summary = summarizeApprovalSheets(mergedRecords)

  return (
    <>
      {approvalSheetsQuery.isError || draftListQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>{title}加载失败</AlertTitle>
          <AlertDescription>
            {approvalSheetsQuery.error instanceof Error
              ? approvalSheetsQuery.error.message
              : draftListQuery.error instanceof Error
                ? draftListQuery.error.message
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
        data={mergedPageData.records}
        total={mergedPageData.total}
        supportsBoard
        resolveBoardColumns={buildOABoardColumns}
        renderBoardCard={(item) => <OABoardCard item={item} />}
        onRefresh={() => {
          void approvalSheetsQuery.refetch()
          void draftListQuery.refetch()
        }}
        summaries={[
          {
            label: '业务单总量',
            value: String(mergedPageData.total),
            hint: '当前登录人在该业务下的流程记录与草稿总量。',
          },
          {
            label: '草稿',
            value: String(summary.draft),
            hint: '尚未提交到流程的草稿数量。',
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
  const search = leaveCreateRoute.useSearch()
  return (
    <PageShell
      title={search.draftId ? '编辑请假草稿' : '请假申请'}
      description='业务发起页支持先暂存草稿，再按需提交到对应流程实例。'
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
      <LeaveCreateForm />
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
  const search = expenseCreateRoute.useSearch()
  return (
    <PageShell
      title={search.draftId ? '编辑报销草稿' : '报销申请'}
      description='报销单支持先暂存草稿，再提交到当前生效流程。'
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
      <ExpenseCreateForm />
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
  const search = commonCreateRoute.useSearch()
  return (
    <PageShell
      title={search.draftId ? '编辑通用申请草稿' : '通用申请'}
      description='通用申请支持先暂存草稿，再确认提交。'
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
      <CommonCreateForm />
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
        page: 1,
        pageSize: OA_DRAFT_FETCH_SIZE,
        view: 'INITIATED',
        businessTypes: ['OA_LEAVE', 'OA_EXPENSE', 'OA_COMMON'],
      }),
  })
  const leaveDraftQuery = useQuery({
    queryKey: ['oa', 'query-drafts', 'OA_LEAVE'],
    queryFn: listOALeaveDrafts,
  })
  const expenseDraftQuery = useQuery({
    queryKey: ['oa', 'query-drafts', 'OA_EXPENSE'],
    queryFn: listOAExpenseDrafts,
  })
  const commonDraftQuery = useQuery({
    queryKey: ['oa', 'query-drafts', 'OA_COMMON'],
    queryFn: listOACommonDrafts,
  })

  const pageData = useMemo(
    () => approvalSheetsQuery.data ?? EMPTY_APPROVAL_SHEET_PAGE,
    [approvalSheetsQuery.data]
  )
  const mergedDrafts = useMemo(
    () => [
      ...(leaveDraftQuery.data ?? []),
      ...(expenseDraftQuery.data ?? []),
      ...(commonDraftQuery.data ?? []),
    ],
    [commonDraftQuery.data, expenseDraftQuery.data, leaveDraftQuery.data]
  )
  const mergedRecords = useMemo(
    () => buildMergedApprovalSheetRecords(pageData, mergedDrafts, search),
    [mergedDrafts, pageData, search]
  )
  const mergedPageData = useMemo(
    () => buildMergedApprovalSheetPage(pageData, mergedDrafts, search),
    [mergedDrafts, pageData, search]
  )
  const summary = summarizeApprovalSheets(mergedRecords)

  return (
    <>
      {approvalSheetsQuery.isError ||
      leaveDraftQuery.isError ||
      expenseDraftQuery.isError ||
      commonDraftQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>OA 流程查询加载失败</AlertTitle>
          <AlertDescription>
            {approvalSheetsQuery.error instanceof Error
              ? approvalSheetsQuery.error.message
              : leaveDraftQuery.error instanceof Error
                ? leaveDraftQuery.error.message
                : expenseDraftQuery.error instanceof Error
                  ? expenseDraftQuery.error.message
                  : commonDraftQuery.error instanceof Error
                    ? commonDraftQuery.error.message
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
        data={mergedPageData.records}
        total={mergedPageData.total}
        supportsBoard
        resolveBoardColumns={buildOABoardColumns}
        renderBoardCard={(item) => <OABoardCard item={item} />}
        onRefresh={() => {
          void approvalSheetsQuery.refetch()
          void leaveDraftQuery.refetch()
          void expenseDraftQuery.refetch()
          void commonDraftQuery.refetch()
        }}
        summaries={[
          {
            label: 'OA 业务总量',
            value: String(mergedPageData.total),
            hint: '当前登录人发起的 OA 审批单与草稿总量。',
          },
          {
            label: '草稿',
            value: String(summary.draft),
            hint: '尚未提交到流程的 OA 草稿数量。',
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
