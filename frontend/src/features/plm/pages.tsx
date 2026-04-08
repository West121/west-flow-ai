import { startTransition, useEffect, useMemo, useState } from 'react'
import { z } from 'zod'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { ArrowRight, Loader2, Send } from 'lucide-react'
import { toast } from 'sonner'
import {
  addPLMImplementationEvidence,
  cancelPLMECOExecution,
  cancelPLMECRRequest,
  cancelPLMMaterialChange,
  createPLMECOExecution,
  createPLMECRRequest,
  createPLMMaterialChangeRequest,
  closePLMBusinessBill,
  dispatchPLMConnectorTask,
  getPLMDashboardCockpit,
  getPLMDashboardSummary,
  getPLMECOExecutionDetail,
  getPLMECRRequestDetail,
  getPLMMaterialChangeDetail,
  releasePLMConfigurationBaseline,
  releasePLMDocumentAsset,
  listPLMBomNodes,
  listPLMConfigurationBaselines,
  listPLMDomainAcl,
  listPLMDocumentAssets,
  listPLMExternalIntegrations,
  listPLMExternalSyncEvents,
  listPLMConnectorTasks,
  getPLMImplementationWorkspace,
  performPLMImplementationTaskAction,
  listPLMObjectAcl,
  listPLMRoleAssignments,
  listPLMECOExecutions,
  listPLMECRRequests,
  listPLMMaterialChangeRequests,
  markPLMBusinessValidating,
  startPLMBusinessImplementation,
  submitPLMECODraft,
  submitPLMECRDraft,
  submitPLMMaterialDraft,
  retryPLMConnectorTask,
  updatePLMAcceptanceChecklist,
  type PLMAffectedItemChangeActionCode,
  type PLMAffectedItemPayload,
  type PLMAffectedItemTypeCode,
  type PLMBomNode,
  type PLMBillDetail,
  type PLMBillLifecycleAction,
  type PLMBillPage,
  type PLMBusinessTypeCode,
  type PLMConfigurationBaseline,
  type PLMDomainAcl,
  type PLMDashboardCockpit,
  type PLMDashboardSummary,
  type PLMDocumentAsset,
  type PLMConnectorTask,
  type PLMExternalIntegration,
  type PLMExternalSyncEventEnvelope,
  type PLMImplementationTask,
  type PLMImplementationTaskActionCode,
  type PLMImplementationWorkspace,
  type PLMObjectAcl,
  type PLMObjectLink,
  type PLMRoleAssignment,
  type PLMRevisionDiff,
  type PLMECOBillListItem,
  type PLMECOExecutionPayload,
  type PLMECRBillListItem,
  type PLMECRRequestPayload,
  type PLMLaunchResponse,
  type PLMMaterialChangeBillListItem,
  type PLMMaterialChangePayload,
} from '@/lib/api/plm'
import {
  getApprovalSheetDetailByBusiness,
  type ApprovalSheetListItem,
  type WorkbenchTaskDetail,
} from '@/lib/api/workbench'
import { handleServerError } from '@/lib/handle-server-error'
import { type NavigateFn } from '@/hooks/use-table-url-state'
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
import { Textarea } from '@/components/ui/textarea'
import { ContextualCopilotEntry } from '@/features/ai/context-entry'
import { PLMBaselinePanel } from '@/features/plm/components/plm-baseline-panel'
import { PLMBomTreePanel } from '@/features/plm/components/plm-bom-tree-panel'
import { PLMCockpitPanel } from '@/features/plm/components/plm-cockpit-panel'
import { PLMDashboardPanels } from '@/features/plm/components/plm-dashboard-panels'
import { PLMDocumentAssetPanel } from '@/features/plm/components/plm-document-asset-panel'
import { PLMConnectorTaskPanel } from '@/features/plm/components/plm-connector-task-panel'
import { PLMExternalIntegrationPanel } from '@/features/plm/components/plm-external-integration-panel'
import { PLMExternalSyncEventPanel } from '@/features/plm/components/plm-external-sync-event-panel'
import { PLMExecutionOverviewPanel } from '@/features/plm/components/plm-execution-overview-panel'
import { PLMImplementationTaskBoard } from '@/features/plm/components/plm-implementation-task-board'
import { PLMImplementationWorkspacePanel } from '@/features/plm/components/plm-implementation-workspace-panel'
import { PLMIntegrationBoundaryPanel } from '@/features/plm/components/plm-integration-boundary-panel'
import { PLMObjectAclPanel } from '@/features/plm/components/plm-object-acl-panel'
import { PLMObjectLinkTable } from '@/features/plm/components/plm-object-link-table'
import { PLMReleaseReadinessPanel } from '@/features/plm/components/plm-release-readiness-panel'
import { PLMRoleDomainAccessPanel } from '@/features/plm/components/plm-role-domain-access-panel'
import { PLMRoleMatrixPanel } from '@/features/plm/components/plm-role-matrix-panel'
import { PLMRevisionDiffPanel } from '@/features/plm/components/plm-revision-diff-panel'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import {
  normalizeListQuerySearch,
  type FilterItem,
  type ListQuerySearch,
} from '@/features/shared/table/query-contract'
import {
  formatApprovalSheetDateTime,
  resolveApprovalSheetInstanceStatusLabel,
} from '@/features/workbench/approval-sheet-list'

const plmAffectedItemFormSchema = z.object({
  itemType: z.enum(['PART', 'DOCUMENT', 'BOM', 'MATERIAL', 'PROCESS']),
  itemCode: z.string(),
  itemName: z.string(),
  beforeVersion: z.string(),
  afterVersion: z.string(),
  changeAction: z.enum(['ADD', 'UPDATE', 'REMOVE', 'REPLACE']),
  ownerUserId: z.string(),
  remark: z.string(),
})

type PLMAffectedItemFormValues = z.infer<typeof plmAffectedItemFormSchema>
type ECRFormValues = z.infer<typeof ecrFormSchema>
type ECOFormValues = z.infer<typeof ecoFormSchema>
type MaterialChangeFormValues = z.infer<typeof materialChangeFormSchema>

type PLMLifecycleAction = PLMBillLifecycleAction

const ecrFormSchema = z.object({
  changeTitle: z.string().trim().min(2, '请填写变更标题'),
  changeReason: z.string().trim().min(2, '请填写变更原因'),
  affectedProductCode: z.string().trim().optional(),
  priorityLevel: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  affectedItems: z.array(plmAffectedItemFormSchema),
})

const ecoFormSchema = z.object({
  executionTitle: z.string().trim().min(2, '请填写执行标题'),
  executionPlan: z.string().trim().min(2, '请填写执行说明'),
  effectiveDate: z.string().optional(),
  changeReason: z.string().trim().min(2, '请填写变更原因'),
  affectedItems: z.array(plmAffectedItemFormSchema),
})

const materialChangeFormSchema = z.object({
  materialCode: z.string().trim().min(2, '请填写物料编码'),
  materialName: z.string().trim().min(2, '请填写物料名称'),
  changeReason: z.string().trim().min(2, '请填写变更原因'),
  changeType: z.enum(['ATTRIBUTE_UPDATE', 'RENAME', 'CODE_UPDATE']),
  affectedItems: z.array(plmAffectedItemFormSchema),
})

function createEmptyAffectedItemFormValue(): PLMAffectedItemFormValues {
  return {
    itemType: 'PART',
    itemCode: '',
    itemName: '',
    beforeVersion: '',
    afterVersion: '',
    changeAction: 'UPDATE',
    ownerUserId: '',
    remark: '',
  }
}

function normalizeAffectedItems(
  items: PLMAffectedItemFormValues[]
): PLMAffectedItemPayload[] {
  return items
    .map((item) => ({
      itemType: item.itemType as PLMAffectedItemTypeCode,
      itemCode: item.itemCode.trim(),
      itemName: item.itemName.trim(),
      beforeVersion: item.beforeVersion.trim() || undefined,
      afterVersion: item.afterVersion.trim() || undefined,
      changeAction: item.changeAction as PLMAffectedItemChangeActionCode,
      ownerUserId: item.ownerUserId.trim() || undefined,
      remark: item.remark.trim() || undefined,
    }))
    .filter((item) =>
      [
        item.itemCode,
        item.itemName,
        item.beforeVersion,
        item.afterVersion,
        item.ownerUserId,
        item.remark,
      ].some((value) => Boolean(value))
    )
}

function normalizeObjectLinks(detail: PLMBillDetail): PLMObjectLink[] {
  if ((detail.objectLinks?.length ?? 0) > 0) {
    return detail.objectLinks ?? []
  }

  return (detail.affectedItems ?? []).map((item, index) => ({
    id: item.id,
    businessType: item.businessType,
    billId: item.billId,
    objectId: item.id,
    objectCode: item.itemCode,
    objectName: item.itemName,
    objectType: item.itemType,
    objectRevisionCode: item.afterVersion ?? item.beforeVersion ?? null,
    versionLabel: item.afterVersion ?? item.beforeVersion ?? null,
    roleCode: 'AFFECTED_OBJECT',
    roleLabel: '受影响对象',
    changeAction: item.changeAction,
    beforeRevisionCode: item.beforeVersion ?? null,
    afterRevisionCode: item.afterVersion ?? null,
    remark: item.remark ?? null,
    sortOrder: index,
  }))
}

function formatTaskActionLabel(action: PLMImplementationTaskActionCode) {
  switch (action) {
    case 'START':
      return '开始'
    case 'COMPLETE':
      return '完成'
    case 'BLOCK':
      return '阻塞'
    case 'CANCEL':
      return '取消'
  }
  return action
}

const ecrDetailRoute = getRouteApi('/_authenticated/plm/ecr/$billId')
const ecoDetailRoute = getRouteApi('/_authenticated/plm/eco/$billId')
const materialDetailRoute = getRouteApi(
  '/_authenticated/plm/material-master/$billId'
)
const plmWorkspaceRoute = getRouteApi('/_authenticated/plm/query')

const PLM_BUSINESS_CONFIG: Record<
  PLMBusinessTypeCode,
  {
    label: string
    shortLabel: string
    listHref: string
    createHref: string
    buildDetailHref: (billId: string) => string
    titleField: string
    fields: Array<{
      key: string
      label: string
      section: 'overview' | 'content' | 'delivery'
      format?: (value: unknown) => string
    }>
  }
> = {
  PLM_ECR: {
    label: 'ECR 变更申请',
    shortLabel: 'ECR',
    listHref: '/plm/ecr',
    createHref: '/plm/ecr/create',
    buildDetailHref: (billId) => `/plm/ecr/${billId}`,
    titleField: 'changeTitle',
    fields: [
      { key: 'changeTitle', label: '变更标题', section: 'overview' },
      { key: 'changeReason', label: '变更原因', section: 'content' },
      {
        key: 'affectedProductCode',
        label: '影响产品编码',
        section: 'overview',
      },
      {
        key: 'priorityLevel',
        label: '优先级',
        section: 'overview',
        format: formatPriority,
      },
      { key: 'changeCategory', label: '变更分类', section: 'overview' },
      { key: 'targetVersion', label: '目标版本', section: 'delivery' },
      { key: 'affectedObjectsText', label: '受影响对象', section: 'content' },
      { key: 'impactScope', label: '影响范围', section: 'content' },
      {
        key: 'riskLevel',
        label: '风险等级',
        section: 'delivery',
        format: formatPriority,
      },
    ],
  },
  PLM_ECO: {
    label: 'ECO 变更执行',
    shortLabel: 'ECO',
    listHref: '/plm/eco',
    createHref: '/plm/eco/create',
    buildDetailHref: (billId) => `/plm/eco/${billId}`,
    titleField: 'executionTitle',
    fields: [
      { key: 'executionTitle', label: '执行标题', section: 'overview' },
      { key: 'changeReason', label: '变更原因', section: 'overview' },
      { key: 'executionPlan', label: '执行计划', section: 'content' },
      { key: 'effectiveDate', label: '生效日期', section: 'delivery' },
      { key: 'implementationOwner', label: '实施负责人', section: 'delivery' },
      { key: 'targetVersion', label: '目标版本', section: 'delivery' },
      { key: 'rolloutScope', label: '推广范围', section: 'content' },
      { key: 'validationPlan', label: '验证计划', section: 'content' },
      { key: 'rollbackPlan', label: '回退方案', section: 'delivery' },
    ],
  },
  PLM_MATERIAL: {
    label: '物料主数据变更',
    shortLabel: '物料变更',
    listHref: '/plm/material-master',
    createHref: '/plm/material-master/create',
    buildDetailHref: (billId) => `/plm/material-master/${billId}`,
    titleField: 'materialName',
    fields: [
      { key: 'materialCode', label: '物料编码', section: 'overview' },
      { key: 'materialName', label: '物料名称', section: 'overview' },
      {
        key: 'changeType',
        label: '变更类型',
        section: 'overview',
        format: formatMaterialChangeType,
      },
      { key: 'changeReason', label: '变更原因', section: 'content' },
      { key: 'specificationChange', label: '规格变更说明', section: 'content' },
      { key: 'oldValue', label: '原值', section: 'delivery' },
      { key: 'newValue', label: '新值', section: 'delivery' },
      { key: 'uom', label: '计量单位', section: 'delivery' },
      { key: 'affectedSystemsText', label: '影响系统', section: 'content' },
    ],
  },
}

function formatPriority(value: unknown) {
  switch (String(value ?? '').toUpperCase()) {
    case 'LOW':
      return '低'
    case 'MEDIUM':
      return '中'
    case 'HIGH':
      return '高'
    default:
      return formatPlmBusinessValue(value)
  }
}

function formatMaterialChangeType(value: unknown) {
  switch (String(value ?? '').toUpperCase()) {
    case 'ATTRIBUTE_UPDATE':
      return '属性更新'
    case 'RENAME':
      return '名称变更'
    case 'CODE_UPDATE':
      return '编码调整'
    default:
      return formatPlmBusinessValue(value)
  }
}

function formatPlmBusinessType(type: string | null | undefined) {
  if (!type) {
    return '--'
  }
  return PLM_BUSINESS_CONFIG[type as PLMBusinessTypeCode]?.label ?? type
}

function formatPlmLifecycleStatus(status: string | null | undefined) {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'RUNNING':
      return '审批中'
    case 'COMPLETED':
      return '已完成'
    case 'IMPLEMENTING':
      return '实施中'
    case 'VALIDATING':
      return '验证中'
    case 'CLOSED':
      return '已关闭'
    case 'REJECTED':
      return '已驳回'
    case 'CANCELLED':
      return '已取消'
    default:
      return status ?? '--'
  }
}

function formatPlmLifecycleStage(status: string | null | undefined) {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'RUNNING':
      return '审批中'
    case 'COMPLETED':
      return '审批完成'
    case 'IMPLEMENTING':
      return '实施中'
    case 'VALIDATING':
      return '验证中'
    case 'CLOSED':
      return '已关闭'
    case 'REJECTED':
      return '已驳回'
    case 'CANCELLED':
      return '已取消'
    default:
      return status ?? '--'
  }
}

function resolvePlmStatusVariant(status: string | null | undefined) {
  switch (status) {
    case 'COMPLETED':
      return 'secondary' as const
    case 'IMPLEMENTING':
    case 'VALIDATING':
      return 'default' as const
    case 'REJECTED':
    case 'CANCELLED':
      return 'destructive' as const
    case 'RUNNING':
      return 'default' as const
    default:
      return 'outline' as const
  }
}

function formatPlmLifecycleAction(action: PLMLifecycleAction) {
  switch (action) {
    case 'SUBMIT':
      return '提交草稿'
    case 'CANCEL':
      return '取消业务单'
    case 'START_IMPLEMENTATION':
      return '开始实施'
    case 'MARK_VALIDATING':
      return '标记验证中'
    case 'CLOSE':
      return '关闭单据'
  }
  return action
}

function formatPlmBusinessValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  if (Array.isArray(value)) {
    return value.length > 0 ? value.join('、') : '--'
  }
  return String(value)
}

function getBusinessConfig(businessType: PlmBusinessTypeCode) {
  return PLM_BUSINESS_CONFIG[businessType]
}

function resolvePlmBusinessDetailLink(
  businessType: string | null | undefined,
  billId: string | null | undefined
) {
  if (!businessType || !billId) {
    return null
  }
  const config = PLM_BUSINESS_CONFIG[businessType as PlmBusinessTypeCode]
  if (!config) {
    return null
  }
  return {
    to: config.listHref.includes('material-master')
      ? '/plm/material-master/$billId'
      : config.listHref.includes('/eco')
        ? '/plm/eco/$billId'
        : '/plm/ecr/$billId',
    params: { billId },
  } as const
}

function getRecentBillTitle(item: PLMDashboardSummary['recentBills'][number]) {
  return item.businessTitle || item.billNo
}

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
        <CardDescription>
          PLM 单据已经保存并自动进入统一审批流。
        </CardDescription>
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

function AffectedItemsEditor({ form }: { form: any }) {
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: 'affectedItems',
  })

  return (
    <Card className='border-dashed'>
      <CardHeader className='space-y-3'>
        <div className='flex flex-wrap items-center justify-between gap-3'>
          <div className='space-y-1'>
            <CardTitle>受影响对象</CardTitle>
            <CardDescription>
              支持逐行增删，提交前会自动清理空白行。
            </CardDescription>
          </div>
          <Button
            type='button'
            variant='outline'
            size='sm'
            onClick={() => append(createEmptyAffectedItemFormValue())}
          >
            添加受影响对象
          </Button>
        </div>
      </CardHeader>
      <CardContent className='space-y-4'>
        {fields.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前还没有受影响对象，点击上方按钮添加一行。
          </div>
        ) : null}
        {fields.map((field, index) => {
          const baseId = `affectedItems-${index}`

          return (
            <div
              key={field.id}
              role='group'
              aria-label={`受影响对象 ${index + 1}`}
              className='space-y-4 rounded-lg border bg-muted/20 p-4'
            >
              <div className='flex flex-wrap items-center justify-between gap-3'>
                <div className='text-sm font-medium'>
                  受影响对象 {index + 1}
                </div>
                <Button
                  type='button'
                  variant='ghost'
                  size='sm'
                  onClick={() => remove(index)}
                >
                  删除受影响对象
                </Button>
              </div>
              <div className='grid gap-4 md:grid-cols-2 xl:grid-cols-4'>
                <div className='space-y-2'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-itemType`}
                  >
                    对象类型
                  </label>
                  <select
                    id={`${baseId}-itemType`}
                    className='h-10 w-full rounded-md border border-input bg-background px-3 text-sm ring-offset-background'
                    {...form.register(`affectedItems.${index}.itemType`)}
                  >
                    <option value='PART'>零部件</option>
                    <option value='DOCUMENT'>文档</option>
                    <option value='BOM'>BOM</option>
                    <option value='MATERIAL'>物料</option>
                    <option value='PROCESS'>工艺</option>
                  </select>
                </div>
                <div className='space-y-2'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-itemCode`}
                  >
                    对象编码
                  </label>
                  <Input
                    id={`${baseId}-itemCode`}
                    placeholder='例如：PART-001'
                    {...form.register(`affectedItems.${index}.itemCode`)}
                  />
                </div>
                <div className='space-y-2'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-itemName`}
                  >
                    对象名称
                  </label>
                  <Input
                    id={`${baseId}-itemName`}
                    placeholder='例如：机壳'
                    {...form.register(`affectedItems.${index}.itemName`)}
                  />
                </div>
                <div className='space-y-2'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-changeAction`}
                  >
                    变更动作
                  </label>
                  <select
                    id={`${baseId}-changeAction`}
                    className='h-10 w-full rounded-md border border-input bg-background px-3 text-sm ring-offset-background'
                    {...form.register(`affectedItems.${index}.changeAction`)}
                  >
                    <option value='ADD'>新增</option>
                    <option value='UPDATE'>更新</option>
                    <option value='REMOVE'>移除</option>
                    <option value='REPLACE'>替换</option>
                  </select>
                </div>
                <div className='space-y-2'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-beforeVersion`}
                  >
                    前版本
                  </label>
                  <Input
                    id={`${baseId}-beforeVersion`}
                    placeholder='例如：A.01'
                    {...form.register(`affectedItems.${index}.beforeVersion`)}
                  />
                </div>
                <div className='space-y-2'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-afterVersion`}
                  >
                    后版本
                  </label>
                  <Input
                    id={`${baseId}-afterVersion`}
                    placeholder='例如：A.02'
                    {...form.register(`affectedItems.${index}.afterVersion`)}
                  />
                </div>
                <div className='space-y-2'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-ownerUserId`}
                  >
                    责任人
                  </label>
                  <Input
                    id={`${baseId}-ownerUserId`}
                    placeholder='例如：usr_001'
                    {...form.register(`affectedItems.${index}.ownerUserId`)}
                  />
                </div>
                <div className='space-y-2 md:col-span-2 xl:col-span-1'>
                  <label
                    className='text-sm font-medium'
                    htmlFor={`${baseId}-remark`}
                  >
                    备注
                  </label>
                  <Textarea
                    id={`${baseId}-remark`}
                    placeholder='补充说明或校验信息'
                    className='min-h-24'
                    {...form.register(`affectedItems.${index}.remark`)}
                  />
                </div>
              </div>
            </div>
          )
        })}
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
      affectedProductCode: '',
      priorityLevel: 'MEDIUM',
      affectedItems: [createEmptyAffectedItemFormValue()],
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
        <CardDescription>
          先填写变更申请单，再自动发起对应审批流程。
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form
            className='space-y-6'
            onSubmit={form.handleSubmit((values) =>
              launchMutation.mutate({
                changeTitle: values.changeTitle.trim(),
                changeReason: values.changeReason.trim(),
                affectedProductCode:
                  values.affectedProductCode?.trim() || undefined,
                priorityLevel: values.priorityLevel,
                affectedItems: normalizeAffectedItems(values.affectedItems),
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
              name='affectedProductCode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>影响产品编码</FormLabel>
                  <FormControl>
                    <Input placeholder='例如：PRD-001' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='priorityLevel'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>优先级</FormLabel>
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

            <AffectedItemsEditor form={form} />

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
      executionTitle: '',
      executionPlan: '',
      effectiveDate: '',
      changeReason: '',
      affectedItems: [createEmptyAffectedItemFormValue()],
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
        <CardDescription>
          用于执行已批准的变更方案，提交后进入统一审批流。
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form
            className='space-y-6'
            onSubmit={form.handleSubmit((values) =>
              launchMutation.mutate({
                executionTitle: values.executionTitle.trim(),
                executionPlan: values.executionPlan.trim(),
                effectiveDate: values.effectiveDate?.trim() || undefined,
                changeReason: values.changeReason.trim(),
                affectedItems: normalizeAffectedItems(values.affectedItems),
              })
            )}
          >
            <FormField
              control={form.control}
              name='executionTitle'
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
                    <Input
                      placeholder='例如：通知工厂按新版图纸执行'
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='effectiveDate'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>生效日期</FormLabel>
                  <FormControl>
                    <Input type='date' {...field} />
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
                    <Input placeholder='例如：量产切换' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <AffectedItemsEditor form={form} />

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
      changeType: 'ATTRIBUTE_UPDATE',
      affectedItems: [createEmptyAffectedItemFormValue()],
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
        <CardDescription>
          用于主数据修订、编码调整和名称变更的标准申请入口。
        </CardDescription>
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
                changeType: values.changeType,
                affectedItems: normalizeAffectedItems(values.affectedItems),
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
            <FormField
              control={form.control}
              name='changeType'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>变更类型</FormLabel>
                  <FormControl>
                    <select
                      className='h-10 w-full rounded-md border border-input bg-background px-3 text-sm ring-offset-background'
                      {...field}
                    >
                      <option value='ATTRIBUTE_UPDATE'>属性更新</option>
                      <option value='RENAME'>名称变更</option>
                      <option value='CODE_UPDATE'>编码调整</option>
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <AffectedItemsEditor form={form} />

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
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute='/plm/start'
            label='用 AI 推荐 PLM 入口'
          />
          <Button asChild variant='outline'>
            <Link to='/plm/query'>进入 PLM 工作台</Link>
          </Button>
        </div>
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

function PLMRecentBillsCard({
  items,
}: {
  items: PLMDashboardSummary['recentBills']
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>最近更新单据</CardTitle>
        <CardDescription>
          从业务视角快速打开最近处理、最近完成或最近更新的变更单。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-3'>
        {items.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有最近更新记录。
          </div>
        ) : (
          items.map((item) => {
            const href = resolvePlmBusinessDetailLink(
              item.businessType,
              item.billId
            )
            return (
              <div
                key={`${item.businessType}-${item.billId}`}
                className='rounded-lg border bg-muted/20 p-4'
              >
                <div className='flex flex-wrap items-center justify-between gap-3'>
                  <div className='space-y-1'>
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='font-medium'>
                        {getRecentBillTitle(item)}
                      </span>
                      <Badge variant={resolvePlmStatusVariant(item.status)}>
                        {formatPlmLifecycleStatus(item.status)}
                      </Badge>
                    </div>
                    <p className='text-xs text-muted-foreground'>
                      {formatPlmBusinessType(item.businessType)} · {item.billNo}{' '}
                      · 最近更新 {formatApprovalSheetDateTime(item.updatedAt)}
                    </p>
                    {item.detailSummary ? (
                      <p className='text-sm text-muted-foreground'>
                        {item.detailSummary}
                      </p>
                    ) : null}
                  </div>
                  {href ? (
                    <Button asChild size='sm' variant='ghost'>
                      <Link to={href.to} params={href.params}>
                        打开单据
                        <ArrowRight />
                      </Link>
                    </Button>
                  ) : null}
                </div>
              </div>
            )
          })
        )}
      </CardContent>
    </Card>
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

export function PLMQueryPage() {
  const summaryQuery = useQuery({
    queryKey: ['plm', 'workspace-summary'],
    queryFn: getPLMDashboardSummary,
  })
  const cockpitQuery = useQuery({
    queryKey: ['plm', 'workspace-cockpit'],
    queryFn: getPLMDashboardCockpit,
  })
  const byBusinessType = summaryQuery.data?.byBusinessType ?? []

  return (
    <PageShell
      title='PLM 工作台'
      description='把三类变更单从审批视角提升为业务台账视角，聚合查看状态、最近单据和跳转入口。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute='/plm/query'
            label='用 AI 总结当前 PLM 工作台'
          />
          <Button asChild variant='outline'>
            <Link to='/plm/start'>发起 PLM 申请</Link>
          </Button>
        </div>
      }
    >
      {summaryQuery.isError ? (
        <Alert variant='destructive'>
          <AlertTitle>PLM 工作台加载失败</AlertTitle>
          <AlertDescription>
            {summaryQuery.error instanceof Error
              ? summaryQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}

      <PLMDashboardPanels
        summary={summaryQuery.data}
        isLoading={summaryQuery.isLoading}
      />
      <PLMCockpitPanel
        cockpit={cockpitQuery.data as PLMDashboardCockpit | undefined}
        summary={summaryQuery.data as PLMDashboardSummary | undefined}
      />

      <div className='grid gap-4 xl:grid-cols-3'>
        {(Object.keys(PLM_BUSINESS_CONFIG) as PlmBusinessTypeCode[]).map(
          (businessType) => {
            const config = getBusinessConfig(businessType)
            const summary = byBusinessType.find(
              (item) => item.businessType === businessType
            )

            return (
              <Card key={businessType}>
                <CardHeader className='space-y-3'>
                  <div className='flex items-start justify-between gap-3'>
                    <div className='space-y-1'>
                      <CardTitle>{config.label}</CardTitle>
                      <CardDescription>
                        {summary
                          ? `总量 ${summary.totalCount} · 审批中 ${summary.runningCount ?? 0} · 草稿 ${summary.draftCount ?? 0}`
                          : '汇总接口未返回该业务域统计。'}
                      </CardDescription>
                    </div>
                    <Badge variant='outline'>{config.shortLabel}</Badge>
                  </div>
                </CardHeader>
                <CardContent className='flex flex-wrap gap-2'>
                  <Button asChild size='sm'>
                    <Link to={config.listHref}>进入台账</Link>
                  </Button>
                  <Button asChild size='sm' variant='outline'>
                    <Link to={config.createHref}>发起新单</Link>
                  </Button>
                </CardContent>
              </Card>
            )
          }
        )}
      </div>

      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]'>
        <PLMRecentBillsCard items={summaryQuery.data?.recentBills ?? []} />
      </div>
    </PageShell>
  )
}

export function PLMECRCreatePage() {
  return (
    <PageShell
      title='ECR 变更申请'
      description='ECR 申请页负责采集变更请求，保存后自动发起审批实例。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute='/plm/ecr/create'
            label='用 AI 辅助填写 ECR'
          />
          <Button asChild variant='outline'>
            <Link to='/plm/start'>返回 PLM 发起中心</Link>
          </Button>
        </div>
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
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute='/plm/eco/create'
            label='用 AI 辅助填写 ECO'
          />
          <Button asChild variant='outline'>
            <Link to='/plm/start'>返回 PLM 发起中心</Link>
          </Button>
        </div>
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
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute='/plm/material-master/create'
            label='用 AI 辅助填写物料变更'
          />
          <Button asChild variant='outline'>
            <Link to='/plm/start'>返回 PLM 发起中心</Link>
          </Button>
        </div>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <MaterialChangeCreateForm />
        <LaunchSummaryCard response={null} />
      </div>
    </PageShell>
  )
}

function resolveApprovalDetailHref(detail: WorkbenchTaskDetail | undefined) {
  const taskId = detail?.activeTaskIds[0]
  if (!taskId) {
    return null
  }

  return { to: '/workbench/todos/$taskId', params: { taskId } } as const
}

function extractSingleFilter(
  search: ListQuerySearch,
  field: string,
  operator: FilterItem['operator'] = 'eq'
) {
  const match = (search.filters ?? []).find(
    (item) => item.field === field && item.operator === operator
  )
  return typeof match?.value === 'string' ? match.value : ''
}

function buildListSearchWithFilters(
  search: ListQuerySearch,
  values: {
    status?: string
    sceneCode?: string
    creatorUserId?: string
    createdFrom?: string
    createdTo?: string
  }
): ListQuerySearch {
  const nextFilters = (search.filters ?? []).filter(
    (item) =>
      !(
        item.field === 'status' ||
        item.field === 'sceneCode' ||
        item.field === 'creatorUserId' ||
        (item.field === 'createdAt' &&
          (item.operator === 'gte' || item.operator === 'lte'))
      )
  )

  if (values.status) {
    nextFilters.push({ field: 'status', operator: 'eq', value: values.status })
  }
  if (values.sceneCode) {
    nextFilters.push({
      field: 'sceneCode',
      operator: 'eq',
      value: values.sceneCode,
    })
  }
  if (values.creatorUserId) {
    nextFilters.push({
      field: 'creatorUserId',
      operator: 'eq',
      value: values.creatorUserId,
    })
  }
  if (values.createdFrom) {
    nextFilters.push({
      field: 'createdAt',
      operator: 'gte',
      value: values.createdFrom,
    })
  }
  if (values.createdTo) {
    nextFilters.push({
      field: 'createdAt',
      operator: 'lte',
      value: values.createdTo,
    })
  }

  return {
    ...search,
    page: 1,
    filters: nextFilters,
  }
}

function withStatusFilter(
  search: ListQuerySearch,
  status: string | null
): ListQuerySearch {
  return buildListSearchWithFilters(search, {
    status: status ?? undefined,
    sceneCode: extractSingleFilter(search, 'sceneCode'),
    creatorUserId: extractSingleFilter(search, 'creatorUserId'),
    createdFrom: extractSingleFilter(search, 'createdAt', 'gte'),
    createdTo: extractSingleFilter(search, 'createdAt', 'lte'),
  })
}

function PLMBillFilterPanel({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: NavigateFn
}) {
  const [sceneCode, setSceneCode] = useState(
    extractSingleFilter(search, 'sceneCode')
  )
  const [creatorUserId, setCreatorUserId] = useState(
    extractSingleFilter(search, 'creatorUserId')
  )
  const [createdFrom, setCreatedFrom] = useState(
    extractSingleFilter(search, 'createdAt', 'gte')
  )
  const [createdTo, setCreatedTo] = useState(
    extractSingleFilter(search, 'createdAt', 'lte')
  )

  useEffect(() => {
    setSceneCode(extractSingleFilter(search, 'sceneCode'))
    setCreatorUserId(extractSingleFilter(search, 'creatorUserId'))
    setCreatedFrom(extractSingleFilter(search, 'createdAt', 'gte'))
    setCreatedTo(extractSingleFilter(search, 'createdAt', 'lte'))
  }, [search])

  return (
    <Card>
      <CardHeader>
        <CardTitle>高级过滤</CardTitle>
        <CardDescription>
          按场景码、创建人和起止日期收窄业务台账范围。
        </CardDescription>
      </CardHeader>
      <CardContent className='grid gap-4 md:grid-cols-2 xl:grid-cols-5'>
        <div className='space-y-2'>
          <label className='text-sm font-medium'>场景码</label>
          <Input
            placeholder='例如：DEFAULT'
            value={sceneCode}
            onChange={(event) => setSceneCode(event.target.value)}
          />
        </div>
        <div className='space-y-2'>
          <label className='text-sm font-medium'>创建人</label>
          <Input
            placeholder='例如：usr_001'
            value={creatorUserId}
            onChange={(event) => setCreatorUserId(event.target.value)}
          />
        </div>
        <div className='space-y-2'>
          <label className='text-sm font-medium'>起始日期</label>
          <Input
            type='date'
            value={createdFrom}
            onChange={(event) => setCreatedFrom(event.target.value)}
          />
        </div>
        <div className='space-y-2'>
          <label className='text-sm font-medium'>结束日期</label>
          <Input
            type='date'
            value={createdTo}
            onChange={(event) => setCreatedTo(event.target.value)}
          />
        </div>
        <div className='flex items-end gap-2'>
          <Button
            type='button'
            className='flex-1'
            onClick={() =>
              navigate({
                search: () =>
                  buildListSearchWithFilters(search, {
                    status: extractSingleFilter(search, 'status'),
                    sceneCode: sceneCode.trim() || undefined,
                    creatorUserId: creatorUserId.trim() || undefined,
                    createdFrom: createdFrom || undefined,
                    createdTo: createdTo || undefined,
                  }),
              })
            }
          >
            应用筛选
          </Button>
          <Button
            type='button'
            variant='outline'
            onClick={() => {
              setSceneCode('')
              setCreatorUserId('')
              setCreatedFrom('')
              setCreatedTo('')
              navigate({
                search: () =>
                  buildListSearchWithFilters(search, {
                    status: extractSingleFilter(search, 'status') || undefined,
                  }),
              })
            }}
          >
            清空
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

function buildPlmQuickActions(search: ListQuerySearch, navigate: NavigateFn) {
  const selectedStatus = (search.filters ?? []).find(
    (item) => item.field === 'status' && item.operator === 'eq'
  )?.value
  const activeStatus =
    typeof selectedStatus === 'string' ? selectedStatus : undefined

  return (
    <div className='flex flex-wrap gap-2'>
      <Button
        type='button'
        variant={activeStatus === undefined ? 'default' : 'outline'}
        onClick={() =>
          navigate({ search: () => withStatusFilter(search, null) })
        }
      >
        全部状态
      </Button>
      <Button
        type='button'
        variant={activeStatus === 'DRAFT' ? 'default' : 'outline'}
        onClick={() =>
          navigate({ search: () => withStatusFilter(search, 'DRAFT') })
        }
      >
        草稿
      </Button>
      <Button
        type='button'
        variant={activeStatus === 'RUNNING' ? 'default' : 'outline'}
        onClick={() =>
          navigate({ search: () => withStatusFilter(search, 'RUNNING') })
        }
      >
        审批中
      </Button>
      <Button
        type='button'
        variant={activeStatus === 'COMPLETED' ? 'default' : 'outline'}
        onClick={() =>
          navigate({ search: () => withStatusFilter(search, 'COMPLETED') })
        }
      >
        已完成
      </Button>
    </div>
  )
}

function plmSummaryItems(total: number, search: ListQuerySearch) {
  return [
    {
      label: '总条数',
      value: String(total),
      hint: `当前查询页：${search.page} / ${search.pageSize}`,
    },
    {
      label: '业务过滤项',
      value: String(search.filters?.length ?? 0),
      hint: '支持状态、场景码、创建人和起止日期。',
    },
    {
      label: '快速入口',
      value: '2',
      hint: '可在发起中心与工作台之间快速切换。',
    },
  ]
}

function PlmBillListPage<T extends object>({
  title,
  description,
  search,
  navigate,
  endpoint,
  columns,
  records,
  total,
  createHref,
  createLabel,
}: {
  title: string
  description: string
  search: ListQuerySearch
  navigate: NavigateFn
  endpoint: string
  columns: ColumnDef<T, unknown>[]
  records: T[]
  total: number
  createHref: string
  createLabel: string
}) {
  return (
    <ResourceListPage
      title={title}
      description={description}
      endpoint={endpoint}
      searchPlaceholder='搜索业务标题、单号、物料编码或摘要'
      search={search}
      navigate={navigate}
      columns={columns}
      data={records}
      total={total}
      summaries={plmSummaryItems(total, search)}
      topContent={<PLMBillFilterPanel search={search} navigate={navigate} />}
      extraActions={
        <div className='flex flex-wrap items-center gap-2'>
          {buildPlmQuickActions(search, navigate)}
          <Button asChild variant='outline'>
            <Link to='/plm/query'>返回 PLM 工作台</Link>
          </Button>
        </div>
      }
      createAction={{
        label: createLabel,
        href: createHref,
      }}
    />
  )
}

type PlmListSearchProps = {
  search: ListQuerySearch
  navigate: NavigateFn
}

function emptyPlmPage<T>(search: ListQuerySearch): PLMBillPage<T> {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    groups: [],
    records: [],
  }
}

function createCommonListColumns<
  T extends { status: string; updatedAt: string },
>() {
  return {
    statusColumn: {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }: { row: { original: T } }) => (
        <Badge variant={resolvePlmStatusVariant(row.original.status)}>
          {formatPlmLifecycleStatus(row.original.status)}
        </Badge>
      ),
    } satisfies ColumnDef<T, unknown>,
    updatedAtColumn: {
      accessorKey: 'updatedAt',
      header: '更新时间',
      cell: ({ row }: { row: { original: T } }) =>
        formatApprovalSheetDateTime(row.original.updatedAt),
    } satisfies ColumnDef<T, unknown>,
  }
}

export function PLMECRListPage({ search, navigate }: PlmListSearchProps) {
  const query = useQuery({
    queryKey: ['plm', 'ecr-list', search],
    queryFn: () => listPLMECRRequests(search),
  })

  const pageData = query.data ?? emptyPlmPage<PLMECRBillListItem>(search)
  const { statusColumn, updatedAtColumn } =
    createCommonListColumns<PLMECRBillListItem>()
  const columns = useMemo<ColumnDef<PLMECRBillListItem, unknown>[]>(
    () => [
      {
        accessorKey: 'billNo',
        header: '业务单号',
        cell: ({ row }) => (
          <Link
            className='font-medium text-primary hover:underline'
            to='/plm/ecr/$billId'
            params={{ billId: row.original.billId }}
          >
            {row.original.billNo}
          </Link>
        ),
      },
      { accessorKey: 'changeTitle', header: '变更标题' },
      {
        accessorKey: 'changeCategory',
        header: '变更分类',
        cell: ({ row }) => row.original.changeCategory || '--',
      },
      { accessorKey: 'affectedProductCode', header: '影响产品' },
      {
        accessorKey: 'targetVersion',
        header: '目标版本',
        cell: ({ row }) => row.original.targetVersion || '--',
      },
      {
        accessorKey: 'priorityLevel',
        header: '优先级',
        cell: ({ row }) => formatPriority(row.original.priorityLevel),
      },
      statusColumn,
      updatedAtColumn,
    ],
    [statusColumn, updatedAtColumn]
  )

  return (
    <PlmBillListPage
      title='ECR 变更申请台账'
      description='按业务台账查看 ECR 申请，支持状态、场景码、创建人和起止日期过滤。'
      search={search}
      navigate={navigate}
      endpoint='/api/v1/plm/ecrs'
      columns={columns}
      records={pageData.records}
      total={pageData.total}
      createHref='/plm/ecr/create'
      createLabel='发起 ECR'
    />
  )
}

export function PLMECOListPage({ search, navigate }: PlmListSearchProps) {
  const query = useQuery({
    queryKey: ['plm', 'eco-list', search],
    queryFn: () => listPLMECOExecutions(search),
  })

  const pageData = query.data ?? emptyPlmPage<PLMECOBillListItem>(search)
  const { statusColumn, updatedAtColumn } =
    createCommonListColumns<PLMECOBillListItem>()
  const columns = useMemo<ColumnDef<PLMECOBillListItem, unknown>[]>(
    () => [
      {
        accessorKey: 'billNo',
        header: '业务单号',
        cell: ({ row }) => (
          <Link
            className='font-medium text-primary hover:underline'
            to='/plm/eco/$billId'
            params={{ billId: row.original.billId }}
          >
            {row.original.billNo}
          </Link>
        ),
      },
      { accessorKey: 'executionTitle', header: '执行标题' },
      {
        accessorKey: 'implementationOwner',
        header: '实施负责人',
        cell: ({ row }) => row.original.implementationOwner || '--',
      },
      {
        accessorKey: 'effectiveDate',
        header: '生效日期',
        cell: ({ row }) => row.original.effectiveDate || '--',
      },
      {
        accessorKey: 'targetVersion',
        header: '目标版本',
        cell: ({ row }) => row.original.targetVersion || '--',
      },
      statusColumn,
      updatedAtColumn,
    ],
    [statusColumn, updatedAtColumn]
  )

  return (
    <PlmBillListPage
      title='ECO 变更执行台账'
      description='按业务台账查看 ECO 执行单，突出生效日期、实施负责人和推广范围。'
      search={search}
      navigate={navigate}
      endpoint='/api/v1/plm/ecos'
      columns={columns}
      records={pageData.records}
      total={pageData.total}
      createHref='/plm/eco/create'
      createLabel='发起 ECO'
    />
  )
}

export function PLMMaterialChangeListPage({
  search,
  navigate,
}: PlmListSearchProps) {
  const query = useQuery({
    queryKey: ['plm', 'material-list', search],
    queryFn: () => listPLMMaterialChangeRequests(search),
  })

  const pageData =
    query.data ?? emptyPlmPage<PLMMaterialChangeBillListItem>(search)
  const { statusColumn, updatedAtColumn } =
    createCommonListColumns<PLMMaterialChangeBillListItem>()
  const columns = useMemo<ColumnDef<PLMMaterialChangeBillListItem, unknown>[]>(
    () => [
      {
        accessorKey: 'billNo',
        header: '业务单号',
        cell: ({ row }) => (
          <Link
            className='font-medium text-primary hover:underline'
            to='/plm/material-master/$billId'
            params={{ billId: row.original.billId }}
          >
            {row.original.billNo}
          </Link>
        ),
      },
      { accessorKey: 'materialCode', header: '物料编码' },
      { accessorKey: 'materialName', header: '物料名称' },
      {
        accessorKey: 'changeType',
        header: '变更类型',
        cell: ({ row }) => formatMaterialChangeType(row.original.changeType),
      },
      {
        accessorKey: 'specificationChange',
        header: '规格变更',
        cell: ({ row }) => row.original.specificationChange || '--',
      },
      statusColumn,
      updatedAtColumn,
    ],
    [statusColumn, updatedAtColumn]
  )

  return (
    <PlmBillListPage
      title='物料主数据变更台账'
      description='按业务台账查看物料变更，突出规格、原值/新值与影响系统。'
      search={search}
      navigate={navigate}
      endpoint='/api/v1/plm/material-master-changes'
      columns={columns}
      records={pageData.records}
      total={pageData.total}
      createHref='/plm/material-master/create'
      createLabel='发起物料变更'
    />
  )
}

type DetailSection = {
  title: string
  description: string
  items: Array<{ label: string; value: string }>
}

function buildDetailSections(
  businessType: PlmBusinessTypeCode,
  detail: PLMBillDetail
): DetailSection[] {
  const config = getBusinessConfig(businessType)
  const grouped: Record<
    DetailSection['title'],
    Array<{ label: string; value: string }>
  > = {
    业务概览: [],
    业务正文: [],
    影响与实施: [],
  }

  for (const field of config.fields) {
    const rawValue = detail[field.key]
    if (
      rawValue === undefined ||
      rawValue === null ||
      rawValue === '' ||
      (Array.isArray(rawValue) && rawValue.length === 0)
    ) {
      continue
    }
    const value = field.format
      ? field.format(rawValue)
      : formatPlmBusinessValue(rawValue)
    const targetSection =
      field.section === 'overview'
        ? '业务概览'
        : field.section === 'content'
          ? '业务正文'
          : '影响与实施'
    grouped[targetSection].push({
      label: field.label,
      value,
    })
  }

  const commonOverview = [
    { label: '业务单号', value: formatPlmBusinessValue(detail.billNo) },
    { label: '场景码', value: formatPlmBusinessValue(detail.sceneCode) },
    {
      label: '状态',
      value: formatPlmLifecycleStatus(
        detail.status as string | null | undefined
      ),
    },
    {
      label: '创建人',
      value: formatPlmBusinessValue(
        detail.creatorDisplayName ?? detail.creatorUserId
      ),
    },
    { label: '创建时间', value: formatPlmBusinessValue(detail.createdAt) },
    { label: '更新时间', value: formatPlmBusinessValue(detail.updatedAt) },
  ]
  grouped['业务概览'] = [...commonOverview, ...grouped['业务概览']]

  return [
    {
      title: '业务概览',
      description: '确认业务单身份、状态和变更核心对象。',
      items: grouped['业务概览'],
    },
    {
      title: '业务正文',
      description: '聚焦当前变更目的、原因与影响对象说明。',
      items: grouped['业务正文'],
    },
    {
      title: '影响与实施',
      description: '查看实施、回退、版本和其他交付相关信息。',
      items: grouped['影响与实施'],
    },
  ].filter((section) => section.items.length > 0)
}

function resolveLifecycleActions(detail: PLMBillDetail): PLMLifecycleAction[] {
  const declared = detail.availableActions
  if (declared && declared.length > 0) {
    return declared
  }
  switch (detail.status) {
    case 'DRAFT':
      return ['SUBMIT', 'CANCEL']
    case 'RUNNING':
      return ['START_IMPLEMENTATION', 'CANCEL']
    case 'COMPLETED':
      return ['START_IMPLEMENTATION']
    case 'IMPLEMENTING':
      return ['MARK_VALIDATING', 'CANCEL']
    case 'VALIDATING':
      return ['CLOSE', 'START_IMPLEMENTATION']
    default:
      return []
  }
}

function resolveApprovalDetailLink(detail: WorkbenchTaskDetail | undefined) {
  const href = resolveApprovalDetailHref(detail)
  return href
}

function DetailSectionCard({ section }: { section: DetailSection }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{section.title}</CardTitle>
        <CardDescription>{section.description}</CardDescription>
      </CardHeader>
      <CardContent className='grid gap-3 md:grid-cols-2'>
        {section.items.map((item) => (
          <div
            key={`${section.title}-${item.label}`}
            className='rounded-lg border bg-muted/20 p-4'
          >
            <p className='text-xs text-muted-foreground'>{item.label}</p>
            <p className='mt-2 text-sm leading-6 font-medium'>{item.value}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

function resolveLifecycleActionVariant(action: PLMLifecycleAction) {
  switch (action) {
    case 'CANCEL':
      return 'outline' as const
    case 'CLOSE':
      return 'destructive' as const
    default:
      return 'default' as const
  }
}

function PLMLifecycleProgressCard({
  detail,
  businessType,
  onAction,
  actions,
  pending,
}: {
  detail: PLMBillDetail
  businessType: PlmBusinessTypeCode
  onAction: (action: PLMLifecycleAction) => void
  actions: PLMLifecycleAction[]
  pending: boolean
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>实施 / 验证 / 关闭</CardTitle>
        <CardDescription>
          跟踪审批后的执行阶段、责任人与关闭结果。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4 text-sm'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <dl className='grid gap-2 md:grid-cols-2 xl:grid-cols-3'>
            <div className='space-y-1'>
              <dt className='text-xs text-muted-foreground'>当前阶段</dt>
              <dd className='font-medium'>
                {formatPlmLifecycleStage(
                  detail.status as string | null | undefined
                )}
              </dd>
            </div>
            <div className='space-y-1'>
              <dt className='text-xs text-muted-foreground'>业务单号</dt>
              <dd className='font-medium'>
                {formatPlmBusinessValue(detail.billNo)}
              </dd>
            </div>
            <div className='space-y-1'>
              <dt className='text-xs text-muted-foreground'>业务类型</dt>
              <dd className='font-medium'>
                {formatPlmBusinessType(businessType)}
              </dd>
            </div>
          </dl>
        </div>

        <div className='grid gap-3 xl:grid-cols-3'>
          <div className='rounded-lg border bg-muted/20 p-4'>
            <div className='mb-3 flex items-center justify-between gap-2'>
              <p className='text-sm font-medium'>实施</p>
              <Badge variant='outline'>
                {formatPlmLifecycleStage(
                  detail.status as string | null | undefined
                )}
              </Badge>
            </div>
            <dl className='space-y-2 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <dt className='text-muted-foreground'>负责人</dt>
                <dd>{formatPlmBusinessValue(detail.implementationOwner)}</dd>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <dt className='text-muted-foreground'>开始时间</dt>
                <dd>
                  {formatPlmBusinessValue(detail.implementationStartedAt)}
                </dd>
              </div>
              <div className='space-y-1'>
                <dt className='text-muted-foreground'>实施摘要</dt>
                <dd className='leading-6'>
                  {formatPlmBusinessValue(detail.implementationSummary)}
                </dd>
              </div>
            </dl>
          </div>

          <div className='rounded-lg border bg-muted/20 p-4'>
            <div className='mb-3 flex items-center justify-between gap-2'>
              <p className='text-sm font-medium'>验证</p>
              <Badge variant='outline'>
                {detail.validationSummary ? '已填写' : '待填写'}
              </Badge>
            </div>
            <dl className='space-y-2 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <dt className='text-muted-foreground'>负责人</dt>
                <dd>{formatPlmBusinessValue(detail.validationOwner)}</dd>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <dt className='text-muted-foreground'>验证时间</dt>
                <dd>{formatPlmBusinessValue(detail.validatedAt)}</dd>
              </div>
              <div className='space-y-1'>
                <dt className='text-muted-foreground'>验证摘要</dt>
                <dd className='leading-6'>
                  {formatPlmBusinessValue(detail.validationSummary)}
                </dd>
              </div>
            </dl>
          </div>

          <div className='rounded-lg border bg-muted/20 p-4'>
            <div className='mb-3 flex items-center justify-between gap-2'>
              <p className='text-sm font-medium'>关闭</p>
              <Badge variant='outline'>
                {detail.closedAt ? '已关闭' : '未关闭'}
              </Badge>
            </div>
            <dl className='space-y-2 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <dt className='text-muted-foreground'>关闭人</dt>
                <dd>{formatPlmBusinessValue(detail.closedBy)}</dd>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <dt className='text-muted-foreground'>关闭时间</dt>
                <dd>{formatPlmBusinessValue(detail.closedAt)}</dd>
              </div>
              <div className='space-y-1'>
                <dt className='text-muted-foreground'>关闭说明</dt>
                <dd className='leading-6'>
                  {formatPlmBusinessValue(detail.closeComment)}
                </dd>
              </div>
            </dl>
          </div>
        </div>

        <div className='flex flex-wrap gap-2'>
          {actions.length === 0 ? (
            <Button type='button' disabled variant='outline'>
              当前状态无可执行生命周期操作
            </Button>
          ) : (
            actions.map((action) => (
              <Button
                key={action}
                type='button'
                variant={resolveLifecycleActionVariant(action)}
                disabled={pending}
                onClick={() => onAction(action)}
              >
                {pending ? <Loader2 className='animate-spin' /> : null}
                {pending ? '处理中' : formatPlmLifecycleAction(action)}
              </Button>
            ))
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function PLMBusinessBillDetailPage({
  billId,
  businessType,
  title,
  description,
}: {
  billId: string
  businessType: PlmBusinessTypeCode
  title: string
  description: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const config = getBusinessConfig(businessType)
  const businessDetailQuery = useQuery({
    queryKey: ['plm', 'business-detail', businessType, billId],
    queryFn: () => {
      switch (businessType) {
        case 'PLM_ECR':
          return getPLMECRRequestDetail(billId)
        case 'PLM_ECO':
          return getPLMECOExecutionDetail(billId)
        case 'PLM_MATERIAL':
          return getPLMMaterialChangeDetail(billId)
      }
    },
  })
  const approvalDetailQuery = useQuery({
    queryKey: ['plm', 'approval-detail', businessType, billId],
    queryFn: () =>
      getApprovalSheetDetailByBusiness({
        businessType,
        businessId: billId,
      }),
  })
  const bomNodesQuery = useQuery({
    queryKey: ['plm', 'bom-nodes', businessType, billId],
    queryFn: () => listPLMBomNodes(businessType, billId),
  })
  const documentAssetsQuery = useQuery({
    queryKey: ['plm', 'document-assets', businessType, billId],
    queryFn: () => listPLMDocumentAssets(businessType, billId),
  })
  const baselinesQuery = useQuery({
    queryKey: ['plm', 'baselines', businessType, billId],
    queryFn: () => listPLMConfigurationBaselines(businessType, billId),
  })
  const objectAclQuery = useQuery({
    queryKey: ['plm', 'object-acl', businessType, billId],
    queryFn: () => listPLMObjectAcl(businessType, billId),
  })
  const domainAclQuery = useQuery({
    queryKey: ['plm', 'domain-acl', businessType, billId],
    queryFn: () => listPLMDomainAcl(businessType, billId),
  })
  const roleAssignmentsQuery = useQuery({
    queryKey: ['plm', 'role-matrix', businessType, billId],
    queryFn: () => listPLMRoleAssignments(businessType, billId),
  })
  const externalIntegrationsQuery = useQuery({
    queryKey: ['plm', 'external-integrations', businessType, billId],
    queryFn: () => listPLMExternalIntegrations(businessType, billId),
  })
  const externalSyncEventsQuery = useQuery({
    queryKey: ['plm', 'external-sync-events', businessType, billId],
    queryFn: () => listPLMExternalSyncEvents(businessType, billId),
  })
  const connectorTasksQuery = useQuery({
    queryKey: ['plm', 'connector-tasks', businessType, billId],
    queryFn: () => listPLMConnectorTasks(businessType, billId),
  })
  const implementationWorkspaceQuery = useQuery({
    queryKey: ['plm', 'implementation-workspace', businessType, billId],
    queryFn: () => getPLMImplementationWorkspace(businessType, billId),
  })
  const approvalDetail = approvalDetailQuery.data
  const businessDetail =
    businessDetailQuery.data ??
    ((approvalDetail?.businessData ?? {}) as Record<string, unknown>)
  const detail = {
    businessType,
    ...businessDetail,
    status:
      businessDetail?.status ??
      approvalDetail?.businessData?.status ??
      approvalDetail?.instanceStatus,
  } as PLMBillDetail
  const objectLinks = normalizeObjectLinks(detail)
  const revisionDiffs = (detail.revisionDiffs ?? []) as PLMRevisionDiff[]
  const implementationTasks = (detail.implementationTasks ??
    []) as PLMImplementationTask[]
  const bomNodes = (bomNodesQuery.data ?? []) as PLMBomNode[]
  const documentAssets = (documentAssetsQuery.data ?? []) as PLMDocumentAsset[]
  const baselines = (baselinesQuery.data ?? []) as PLMConfigurationBaseline[]
  const objectAcl = (objectAclQuery.data ?? []) as PLMObjectAcl[]
  const domainAcl = (domainAclQuery.data ?? []) as PLMDomainAcl[]
  const roleAssignments =
    (roleAssignmentsQuery.data ?? []) as PLMRoleAssignment[]
  const externalIntegrations =
    (externalIntegrationsQuery.data ?? []) as PLMExternalIntegration[]
  const externalSyncEvents =
    (externalSyncEventsQuery.data ?? []) as PLMExternalSyncEventEnvelope[]
  const connectorTasks = (connectorTasksQuery.data ?? []) as PLMConnectorTask[]
  const implementationWorkspace =
    (implementationWorkspaceQuery.data ?? {
      dependencies: [],
      evidences: [],
      acceptanceCheckpoints: [],
    }) as PLMImplementationWorkspace
  const refreshQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: ['plm', 'business-detail', businessType, billId],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm', 'approval-detail', businessType, billId],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm', 'workspace-summary'],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm', 'workspace-cockpit'],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm', 'connector-tasks', businessType, billId],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm', 'external-integrations', businessType, billId],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm', 'external-sync-events', businessType, billId],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm', 'implementation-workspace', businessType, billId],
      }),
      queryClient.invalidateQueries({
        queryKey: ['plm'],
      }),
    ])
  }
  const actionMutation = useMutation({
    mutationFn: async (action: PLMLifecycleAction) => {
      if (action === 'SUBMIT') {
        if (businessType === 'PLM_ECR') {
          return submitPLMECRDraft(billId)
        }
        if (businessType === 'PLM_ECO') {
          return submitPLMECODraft(billId)
        }
        return submitPLMMaterialDraft(billId)
      }
      if (action === 'CANCEL') {
        if (businessType === 'PLM_ECR') {
          return cancelPLMECRRequest(billId)
        }
        if (businessType === 'PLM_ECO') {
          return cancelPLMECOExecution(billId)
        }
        return cancelPLMMaterialChange(billId)
      }
      if (action === 'START_IMPLEMENTATION') {
        return startPLMBusinessImplementation(businessType, billId)
      }
      if (action === 'MARK_VALIDATING') {
        return markPLMBusinessValidating(businessType, billId)
      }
      return closePLMBusinessBill(businessType, billId)
    },
    onSuccess: async (response, action) => {
      const actionLabel =
        action === 'SUBMIT'
          ? '已提交'
          : action === 'CANCEL'
            ? '已取消'
            : action === 'START_IMPLEMENTATION'
              ? '已开始实施'
              : action === 'MARK_VALIDATING'
                ? '已标记验证中'
                : '已关闭'
      toast.success(`${actionLabel} ${response.billNo ?? detail.billNo}。`)
      await refreshQueries()
    },
    onError: handleServerError,
  })
  const taskActionMutation = useMutation({
    mutationFn: async ({
      task,
      action,
    }: {
      task: PLMImplementationTask
      action: PLMImplementationTaskActionCode
    }) =>
      performPLMImplementationTaskAction(businessType, billId, task.id, action),
    onSuccess: async (response, variables) => {
      toast.success(
        `${formatTaskActionLabel(variables.action)} ${response.billNo ?? detail.billNo}。`
      )
      await refreshQueries()
    },
    onError: handleServerError,
  })
  const connectorTaskMutation = useMutation({
    mutationFn: async ({
      task,
      action,
    }: {
      task: PLMConnectorTask
      action: 'dispatch' | 'retry'
    }) =>
      action === 'dispatch'
        ? dispatchPLMConnectorTask(task.id)
        : retryPLMConnectorTask(task.id),
    onSuccess: async (_, variables) => {
      toast.success(
        `${variables.action === 'dispatch' ? '已派发' : '已重试'} ${variables.task.connectorName}。`
      )
      await refreshQueries()
    },
    onError: handleServerError,
  })
  const publicationMutation = useMutation({
    mutationFn: async (
      variables:
        | { kind: 'baseline'; baseline: PLMConfigurationBaseline }
        | { kind: 'asset'; asset: PLMDocumentAsset }
    ) => {
      if (variables.kind === 'baseline') {
        return releasePLMConfigurationBaseline(
          businessType,
          billId,
          variables.baseline.id
        )
      }
      return releasePLMDocumentAsset(
        businessType,
        billId,
        variables.asset.id
      )
    },
    onSuccess: async (response, variables) => {
      toast.success(
        `${variables.kind === 'baseline' ? '已发布基线' : '已受控发布'} ${response.targetName}。`
      )
      await refreshQueries()
    },
    onError: handleServerError,
  })
  const evidenceMutation = useMutation({
    mutationFn: async ({
      task,
      payload,
    }: {
      task: PLMImplementationTask
      payload: {
        evidenceType: string
        evidenceName: string
        evidenceRef?: string
        evidenceSummary?: string
      }
    }) =>
      addPLMImplementationEvidence(businessType, billId, task.id, payload),
    onSuccess: async (_, variables) => {
      toast.success(`已补充 ${variables.task.taskTitle} 的验证证据。`)
      await refreshQueries()
    },
    onError: handleServerError,
  })
  const acceptanceMutation = useMutation({
    mutationFn: async ({
      checklistId,
      payload,
    }: {
      checklistId: string
      payload: {
        status: string
        resultSummary?: string
      }
    }) =>
      updatePLMAcceptanceChecklist(
        businessType,
        billId,
        checklistId,
        payload
      ),
    onSuccess: async (_, variables) => {
      toast.success(
        `${variables.payload.status === 'ACCEPTED' ? '已完成' : '已回退'}验收检查。`
      )
      await refreshQueries()
    },
    onError: handleServerError,
  })
  const detailRoutePath = config.buildDetailHref(billId)
  const lifecycleActions = resolveLifecycleActions(detail)
  const approvalHref = resolveApprovalDetailLink(approvalDetail)
  const detailTitle =
    formatPlmBusinessValue(detail[config.titleField]) !== '--'
      ? formatPlmBusinessValue(detail[config.titleField])
      : formatPlmBusinessValue(detail.billNo)

  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute={detailRoutePath}
            label='用 AI 解读当前 PLM 单据'
          />
          <Button asChild variant='outline'>
            <Link to={config.listHref}>返回业务台账</Link>
          </Button>
        </div>
      }
    >
      {approvalDetailQuery.isError || businessDetailQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>业务单详情加载失败</AlertTitle>
          <AlertDescription>
            {approvalDetailQuery.error instanceof Error
              ? approvalDetailQuery.error.message
              : businessDetailQuery.error instanceof Error
                ? businessDetailQuery.error.message
                : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}

      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]'>
        <div className='space-y-4'>
          <Card>
            <CardHeader className='space-y-3'>
              <div className='flex flex-wrap items-center gap-3'>
                <Badge variant='outline'>{config.shortLabel}</Badge>
                <Badge
                  variant={resolvePlmStatusVariant(
                    detail.status as string | null | undefined
                  )}
                >
                  {formatPlmLifecycleStatus(
                    detail.status as string | null | undefined
                  )}
                </Badge>
                {detail.sceneCode ? (
                  <Badge variant='secondary'>
                    {formatPlmBusinessValue(detail.sceneCode)}
                  </Badge>
                ) : null}
              </div>
              <CardTitle>{detailTitle}</CardTitle>
              <CardDescription>
                {formatPlmBusinessValue(detail.detailSummary)}
              </CardDescription>
            </CardHeader>
            <CardContent className='space-y-3 text-sm text-muted-foreground'>
              <div className='rounded-lg border bg-muted/20 p-4'>
                <p>{formatPlmBusinessValue(detail.approvalSummary)}</p>
              </div>
            </CardContent>
          </Card>

          {buildDetailSections(businessType, detail).map((section) => (
            <DetailSectionCard key={section.title} section={section} />
          ))}
          <PLMBomTreePanel nodes={bomNodes} />
          <PLMDocumentAssetPanel
            assets={documentAssets}
            pendingAssetId={
              publicationMutation.isPending &&
              publicationMutation.variables?.kind === 'asset'
                ? publicationMutation.variables.asset.id
                : null
            }
            onReleaseAsset={(asset) =>
              publicationMutation.mutate({
                kind: 'asset',
                asset,
              })
            }
          />
          <PLMBaselinePanel
            baselines={baselines}
            pendingBaselineId={
              publicationMutation.isPending &&
              publicationMutation.variables?.kind === 'baseline'
                ? publicationMutation.variables.baseline.id
                : null
            }
            onReleaseBaseline={(baseline) =>
              publicationMutation.mutate({
                kind: 'baseline',
                baseline,
              })
            }
          />
          <PLMObjectLinkTable objectLinks={objectLinks} />
          <PLMRevisionDiffPanel revisionDiffs={revisionDiffs} />
        </div>

        <div className='space-y-4'>
          <PLMExecutionOverviewPanel
            baselines={baselines}
            documentAssets={documentAssets}
            integrations={externalIntegrations}
            syncEvents={externalSyncEvents}
            connectorTasks={connectorTasks}
            workspace={implementationWorkspace}
          />
          <PLMReleaseReadinessPanel
            baselines={baselines}
            documentAssets={documentAssets}
            integrations={externalIntegrations}
            syncEvents={externalSyncEvents}
            connectorTasks={connectorTasks}
            workspace={implementationWorkspace}
          />
          <PLMRoleMatrixPanel roles={roleAssignments} domainAcl={domainAcl} />
          <PLMRoleDomainAccessPanel
            entries={objectAcl}
            sceneCode={detail.sceneCode}
          />
          <PLMObjectAclPanel entries={objectAcl} />
          <PLMConnectorTaskPanel
            tasks={connectorTasks}
            pendingTaskAction={
              connectorTaskMutation.isPending
                ? {
                    taskId: connectorTaskMutation.variables?.task.id ?? '__pending__',
                    action: connectorTaskMutation.variables?.action ?? 'dispatch',
                  }
                : null
            }
            onDispatchTask={(task) =>
              connectorTaskMutation.mutate({
                task,
                action: 'dispatch',
              })
            }
            onRetryTask={(task) =>
              connectorTaskMutation.mutate({
                task,
                action: 'retry',
              })
            }
          />
          <PLMExternalIntegrationPanel integrations={externalIntegrations} />
          <PLMExternalSyncEventPanel events={externalSyncEvents} />
          <PLMIntegrationBoundaryPanel
            objectLinks={objectLinks}
            documentAssets={documentAssets}
            baselines={baselines}
          />
          <PLMImplementationWorkspacePanel
            workspace={implementationWorkspace}
            pendingAcceptanceId={
              acceptanceMutation.isPending
                ? (acceptanceMutation.variables?.checklistId ?? '__pending__')
                : null
            }
            onUpdateAcceptance={(checkpoint, payload) =>
              acceptanceMutation.mutate({
                checklistId: checkpoint.id,
                payload,
              })
            }
          />
          <PLMImplementationTaskBoard
            tasks={implementationTasks}
            pendingTaskId={
              taskActionMutation.isPending
                ? (taskActionMutation.variables?.task.id ?? '__pending__')
                : null
            }
            evidencePendingTaskId={
              evidenceMutation.isPending
                ? (evidenceMutation.variables?.task.id ?? '__pending__')
                : null
            }
            onTaskAction={(task, action) =>
              taskActionMutation.mutate({
                task,
                action,
              })
            }
            onAddEvidence={(task, payload) =>
              evidenceMutation.mutate({
                task,
                payload,
              })
            }
          />
          <PLMLifecycleProgressCard
            detail={detail}
            businessType={businessType}
            onAction={(action) => actionMutation.mutate(action)}
            actions={lifecycleActions}
            pending={actionMutation.isPending}
          />

          <Card>
            <CardHeader>
              <CardTitle>审批联查</CardTitle>
              <CardDescription>
                在业务视角和审批视角之间快速切换，查看节点与流程轨迹。
              </CardDescription>
            </CardHeader>
            <CardContent className='space-y-4 text-sm'>
              <div className='rounded-lg border bg-muted/20 p-4'>
                <dl className='space-y-2'>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>流程名称</dt>
                    <dd>{approvalDetail?.processName ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>当前节点</dt>
                    <dd>{approvalDetail?.nodeName ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>流程状态</dt>
                    <dd>
                      {approvalDetail?.instanceStatus
                        ? resolveApprovalSheetInstanceStatusLabel(
                            approvalDetail.instanceStatus
                          )
                        : '--'}
                    </dd>
                  </div>
                </dl>
              </div>

              <div className='flex flex-wrap gap-2'>
                {approvalHref ? (
                  <Button asChild>
                    <Link to={approvalHref.to} params={approvalHref.params}>
                      查看审批单
                    </Link>
                  </Button>
                ) : (
                  <Button type='button' disabled>
                    当前没有可打开的待办审批单
                  </Button>
                )}
                <Button asChild variant='outline'>
                  <Link to={config.listHref}>返回业务台账</Link>
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </PageShell>
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
    <PLMBusinessBillDetailPage
      businessType='PLM_ECR'
      billId={billId}
      title='ECR 变更申请详情'
      description='从业务工作区视角查看 ECR 单据，聚焦业务摘要、影响范围和流程联查。'
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
    <PLMBusinessBillDetailPage
      businessType='PLM_ECO'
      billId={billId}
      title='ECO 变更执行详情'
      description='从业务工作区视角查看 ECO 执行单，重点展示实施、回退和审批联查。'
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
    <PLMBusinessBillDetailPage
      businessType='PLM_MATERIAL'
      billId={billId}
      title='物料主数据变更详情'
      description='从业务工作区视角查看物料变更，确认规格、原值/新值和影响系统。'
    />
  )
}
