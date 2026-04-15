import { useEffect, useMemo, useState } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useFieldArray, useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { ArrowRight, Loader2, Plus, Save } from 'lucide-react'
import { toast } from 'sonner'
import {
  createPLMProject,
  cancelPLMProjectInitiation,
  getPLMProjectDetail,
  listPLMProjects,
  submitPLMProjectInitiation,
  transitionPLMProjectPhase,
  updatePLMProject,
  type PLMProjectDashboard,
  type PLMProjectDetail,
  type PLMProjectInitiationStatus,
  type PLMProjectLink,
  type PLMProjectListItem,
  type PLMProjectPayload,
  type PLMProjectPhaseTransitionPayload,
  type PLMProjectStatus,
  type PLMProjectUpdatePayload,
} from '@/lib/api/plm'
import { getApprovalSheetDetailByBusiness } from '@/lib/api/workbench'
import { handleServerError } from '@/lib/handle-server-error'
import { ContextualCopilotEntry } from '@/features/ai/context-entry'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import {
  normalizeListQuerySearch,
  type FilterItem,
  type ListQuerySearch,
} from '@/features/shared/table/query-contract'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { formatApprovalSheetDateTime } from '@/features/workbench/approval-sheet-list'
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
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'

const projectMemberSchema = z.object({
  userId: z.string().trim().min(1, '请填写成员用户 ID'),
  roleCode: z.string().trim().min(1, '请填写角色编码'),
  roleLabel: z.string().trim().min(1, '请填写角色名称'),
  responsibilitySummary: z.string().trim().optional(),
})

const projectMilestoneSchema = z.object({
  milestoneCode: z.string().trim().min(1, '请填写里程碑编码'),
  milestoneName: z.string().trim().min(1, '请填写里程碑名称'),
  status: z.string().trim().min(1, '请填写里程碑状态'),
  ownerUserId: z.string().trim().optional(),
  plannedAt: z.string().optional(),
  actualAt: z.string().optional(),
  summary: z.string().trim().optional(),
})

const projectLinkSchema = z.object({
  linkType: z.string().trim().min(1, '请填写关联类型'),
  targetBusinessType: z.string().trim().optional(),
  targetId: z.string().trim().min(1, '请填写关联对象 ID'),
  targetNo: z.string().trim().optional(),
  targetTitle: z.string().trim().optional(),
  targetStatus: z.string().trim().optional(),
  targetHref: z.string().trim().optional(),
  summary: z.string().trim().optional(),
})

const projectFormSchema = z.object({
  projectCode: z.string().trim().min(2, '请填写项目编码'),
  projectName: z.string().trim().min(2, '请填写项目名称'),
  projectType: z.string().trim().min(2, '请填写项目类型'),
  projectLevel: z.string().trim().optional(),
  ownerUserId: z.string().trim().optional(),
  sponsorUserId: z.string().trim().optional(),
  domainCode: z.string().trim().optional(),
  priorityLevel: z.string().trim().optional(),
  targetRelease: z.string().trim().optional(),
  startDate: z.string().optional(),
  targetEndDate: z.string().optional(),
  actualEndDate: z.string().optional(),
  summary: z.string().trim().optional(),
  businessGoal: z.string().trim().optional(),
  riskSummary: z.string().trim().optional(),
  members: z.array(projectMemberSchema),
  milestones: z.array(projectMilestoneSchema),
  links: z.array(projectLinkSchema),
})

type ProjectFormValues = z.infer<typeof projectFormSchema>

function emptyProjectFormValues(): ProjectFormValues {
  return {
    projectCode: '',
    projectName: '',
    projectType: 'NPI',
    projectLevel: 'L1',
    ownerUserId: '',
    sponsorUserId: '',
    domainCode: 'PRODUCT',
    priorityLevel: 'HIGH',
    targetRelease: '',
    startDate: '',
    targetEndDate: '',
    actualEndDate: '',
    summary: '',
    businessGoal: '',
    riskSummary: '',
    members: [
      {
        userId: '',
        roleCode: 'PM',
        roleLabel: '项目经理',
        responsibilitySummary: '',
      },
    ],
    milestones: [],
    links: [],
  }
}

function mapProjectDetailToForm(detail: PLMProjectDetail): ProjectFormValues {
  return {
    projectCode: detail.projectCode,
    projectName: detail.projectName,
    projectType: detail.projectType,
    projectLevel: detail.projectLevel ?? '',
    ownerUserId: detail.ownerUserId ?? '',
    sponsorUserId: detail.sponsorUserId ?? '',
    domainCode: detail.domainCode ?? '',
    priorityLevel: detail.priorityLevel ?? '',
    targetRelease: detail.targetRelease ?? '',
    startDate: detail.startDate ?? '',
    targetEndDate: detail.targetEndDate ?? '',
    actualEndDate: detail.actualEndDate ?? '',
    summary: detail.summary ?? '',
    businessGoal: detail.businessGoal ?? '',
    riskSummary: detail.riskSummary ?? '',
    members: detail.members.map((member) => ({
      userId: member.userId,
      roleCode: member.roleCode,
      roleLabel: member.roleLabel,
      responsibilitySummary: member.responsibilitySummary ?? '',
    })),
    milestones: detail.milestones.map((milestone) => ({
      milestoneCode: milestone.milestoneCode,
      milestoneName: milestone.milestoneName,
      status: milestone.status,
      ownerUserId: milestone.ownerUserId ?? '',
      plannedAt: milestone.plannedAt?.slice(0, 16) ?? '',
      actualAt: milestone.actualAt?.slice(0, 16) ?? '',
      summary: milestone.summary ?? '',
    })),
    links: detail.links.map((link) => ({
      linkType: link.linkType,
      targetBusinessType: link.targetBusinessType ?? '',
      targetId: link.targetId,
      targetNo: link.targetNo ?? '',
      targetTitle: link.targetTitle ?? '',
      targetStatus: link.targetStatus ?? '',
      targetHref: link.targetHref ?? '',
      summary: link.summary ?? '',
    })),
  }
}

function normalizeProjectPayload(values: ProjectFormValues): PLMProjectPayload {
  return {
    projectCode: values.projectCode.trim(),
    projectName: values.projectName.trim(),
    projectType: values.projectType.trim(),
    projectLevel: values.projectLevel.trim() || undefined,
    ownerUserId: values.ownerUserId.trim() || undefined,
    sponsorUserId: values.sponsorUserId.trim() || undefined,
    domainCode: values.domainCode.trim() || undefined,
    priorityLevel: values.priorityLevel.trim() || undefined,
    targetRelease: values.targetRelease.trim() || undefined,
    startDate: values.startDate || undefined,
    targetEndDate: values.targetEndDate || undefined,
    summary: values.summary.trim() || undefined,
    businessGoal: values.businessGoal.trim() || undefined,
    riskSummary: values.riskSummary.trim() || undefined,
    members: values.members
      .map((member) => ({
        userId: member.userId.trim(),
        roleCode: member.roleCode.trim(),
        roleLabel: member.roleLabel.trim(),
        responsibilitySummary: member.responsibilitySummary?.trim() || undefined,
      }))
      .filter((member) => member.userId && member.roleCode && member.roleLabel),
    milestones: values.milestones
      .map((milestone) => ({
        milestoneCode: milestone.milestoneCode.trim(),
        milestoneName: milestone.milestoneName.trim(),
        status: milestone.status.trim(),
        ownerUserId: milestone.ownerUserId?.trim() || undefined,
        plannedAt: milestone.plannedAt || undefined,
        actualAt: milestone.actualAt || undefined,
        summary: milestone.summary?.trim() || undefined,
      }))
      .filter(
        (milestone) =>
          milestone.milestoneCode && milestone.milestoneName && milestone.status
      ),
    links: values.links
      .map((link) => ({
        linkType: link.linkType.trim(),
        targetBusinessType: link.targetBusinessType?.trim() || undefined,
        targetId: link.targetId.trim(),
        targetNo: link.targetNo?.trim() || undefined,
        targetTitle: link.targetTitle?.trim() || undefined,
        targetStatus: link.targetStatus?.trim() || undefined,
        targetHref: link.targetHref?.trim() || undefined,
        summary: link.summary?.trim() || undefined,
      }))
      .filter((link) => link.linkType && link.targetId),
  }
}

function normalizeProjectUpdatePayload(
  values: ProjectFormValues,
  detail: PLMProjectDetail
): PLMProjectUpdatePayload {
  return {
    ...normalizeProjectPayload(values),
    status: detail.status as PLMProjectStatus,
    phaseCode: detail.phaseCode,
    actualEndDate: values.actualEndDate || undefined,
  }
}

function formatProjectStatus(status?: string | null) {
  switch ((status ?? '').toUpperCase()) {
    case 'PLANNING':
      return '筹备中'
    case 'ACTIVE':
      return '进行中'
    case 'ON_HOLD':
      return '已暂停'
    case 'COMPLETED':
      return '已完成'
    case 'CANCELLED':
      return '已取消'
    default:
      return status ?? '--'
  }
}

function formatProjectPhase(phase?: string | null) {
  switch ((phase ?? '').toUpperCase()) {
    case 'INITIATION':
      return '立项'
    case 'DESIGN':
      return '设计'
    case 'VALIDATION':
      return '验证'
    case 'RELEASE':
      return '发布'
    case 'CLOSED':
      return '关闭'
    case 'ON_HOLD':
      return '暂停'
    default:
      return phase ?? '--'
  }
}

function resolveProjectStatusVariant(status?: string | null) {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIVE':
    case 'VALIDATION':
    case 'RELEASE':
      return 'default' as const
    case 'PLANNING':
      return 'secondary' as const
    case 'COMPLETED':
      return 'outline' as const
    case 'ON_HOLD':
    case 'CANCELLED':
      return 'destructive' as const
    default:
      return 'outline' as const
  }
}

function formatProjectInitiationStatus(status?: string | null) {
  switch ((status ?? '').toUpperCase()) {
    case 'DRAFT':
      return '草稿'
    case 'PENDING_APPROVAL':
      return '审批中'
    case 'APPROVED':
      return '已通过'
    case 'REJECTED':
      return '已驳回'
    case 'CANCELLED':
      return '已撤回'
    default:
      return status ?? '--'
  }
}

function resolveProjectInitiationStatusVariant(status?: string | null) {
  switch ((status ?? '').toUpperCase()) {
    case 'APPROVED':
      return 'default' as const
    case 'PENDING_APPROVAL':
      return 'secondary' as const
    case 'REJECTED':
    case 'CANCELLED':
      return 'destructive' as const
    default:
      return 'outline' as const
  }
}

function buildProjectListSearch(
  search: ListQuerySearch,
  values: {
    status?: string
    phaseCode?: string
    ownerUserId?: string
    domainCode?: string
    targetEndDateFrom?: string
    targetEndDateTo?: string
  }
): ListQuerySearch {
  const nextFilters = (search.filters ?? []).filter(
    (item) =>
      !(
        item.field === 'status' ||
        item.field === 'phaseCode' ||
        item.field === 'ownerUserId' ||
        item.field === 'domainCode' ||
        (item.field === 'targetEndDate' &&
          (item.operator === 'gte' || item.operator === 'lte'))
      )
  )
  if (values.status) {
    nextFilters.push({ field: 'status', operator: 'eq', value: values.status })
  }
  if (values.phaseCode) {
    nextFilters.push({
      field: 'phaseCode',
      operator: 'eq',
      value: values.phaseCode,
    })
  }
  if (values.ownerUserId) {
    nextFilters.push({
      field: 'ownerUserId',
      operator: 'eq',
      value: values.ownerUserId,
    })
  }
  if (values.domainCode) {
    nextFilters.push({
      field: 'domainCode',
      operator: 'eq',
      value: values.domainCode,
    })
  }
  if (values.targetEndDateFrom) {
    nextFilters.push({
      field: 'targetEndDate',
      operator: 'gte',
      value: values.targetEndDateFrom,
    })
  }
  if (values.targetEndDateTo) {
    nextFilters.push({
      field: 'targetEndDate',
      operator: 'lte',
      value: values.targetEndDateTo,
    })
  }
  return { ...search, page: 1, filters: nextFilters }
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

function ProjectFilterPanel({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: NavigateFn
}) {
  const [status, setStatus] = useState(extractSingleFilter(search, 'status'))
  const [phaseCode, setPhaseCode] = useState(
    extractSingleFilter(search, 'phaseCode')
  )
  const [ownerUserId, setOwnerUserId] = useState(
    extractSingleFilter(search, 'ownerUserId')
  )
  const [domainCode, setDomainCode] = useState(
    extractSingleFilter(search, 'domainCode')
  )
  const [targetEndDateFrom, setTargetEndDateFrom] = useState(
    extractSingleFilter(search, 'targetEndDate', 'gte')
  )
  const [targetEndDateTo, setTargetEndDateTo] = useState(
    extractSingleFilter(search, 'targetEndDate', 'lte')
  )

  useEffect(() => {
    setStatus(extractSingleFilter(search, 'status'))
    setPhaseCode(extractSingleFilter(search, 'phaseCode'))
    setOwnerUserId(extractSingleFilter(search, 'ownerUserId'))
    setDomainCode(extractSingleFilter(search, 'domainCode'))
    setTargetEndDateFrom(extractSingleFilter(search, 'targetEndDate', 'gte'))
    setTargetEndDateTo(extractSingleFilter(search, 'targetEndDate', 'lte'))
  }, [search])

  return (
    <Card>
      <CardHeader>
        <CardTitle>项目过滤</CardTitle>
        <CardDescription>
          按状态、阶段、负责人、领域和交付日期收窄项目台账。
        </CardDescription>
      </CardHeader>
      <CardContent className='grid gap-4 md:grid-cols-2 xl:grid-cols-6'>
        <Input
          placeholder='状态，例如 ACTIVE'
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        />
        <Input
          placeholder='阶段，例如 DESIGN'
          value={phaseCode}
          onChange={(event) => setPhaseCode(event.target.value)}
        />
        <Input
          placeholder='负责人，例如 usr_001'
          value={ownerUserId}
          onChange={(event) => setOwnerUserId(event.target.value)}
        />
        <Input
          placeholder='领域，例如 PRODUCT'
          value={domainCode}
          onChange={(event) => setDomainCode(event.target.value)}
        />
        <Input
          type='date'
          value={targetEndDateFrom}
          onChange={(event) => setTargetEndDateFrom(event.target.value)}
        />
        <Input
          type='date'
          value={targetEndDateTo}
          onChange={(event) => setTargetEndDateTo(event.target.value)}
        />
      </CardContent>
      <CardContent className='flex gap-2 pt-0'>
        <Button
          type='button'
          onClick={() =>
            navigate({
              search: () =>
                buildProjectListSearch(search, {
                  status: status.trim() || undefined,
                  phaseCode: phaseCode.trim() || undefined,
                  ownerUserId: ownerUserId.trim() || undefined,
                  domainCode: domainCode.trim() || undefined,
                  targetEndDateFrom: targetEndDateFrom || undefined,
                  targetEndDateTo: targetEndDateTo || undefined,
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
            setStatus('')
            setPhaseCode('')
            setOwnerUserId('')
            setDomainCode('')
            setTargetEndDateFrom('')
            setTargetEndDateTo('')
            navigate({
              search: () =>
                normalizeListQuerySearch({
                  keyword: search.keyword,
                  pageSize: search.pageSize,
                }),
            })
          }}
        >
          清空
        </Button>
      </CardContent>
    </Card>
  )
}

function ProjectSummaryItems(total: number, search: ListQuerySearch) {
  return [
    {
      label: '项目总数',
      value: String(total),
      hint: `当前页 ${search.page} / 每页 ${search.pageSize}`,
    },
    {
      label: '过滤项',
      value: String(search.filters?.length ?? 0),
      hint: '支持状态、阶段、负责人、领域和交付日期。',
    },
    {
      label: '交付链路',
      value: '项目 / 变更 / BOM',
      hint: '项目详情会聚合里程碑、变更单和实施任务。',
    },
  ]
}

function ProjectMembersEditor({
  form,
  disabled = false,
}: {
  form: ReturnType<typeof useForm<ProjectFormValues>>
  disabled?: boolean
}) {
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: 'members',
  })
  return (
    <Card>
      <CardHeader className='flex flex-row items-center justify-between space-y-0'>
        <div className='space-y-1'>
          <CardTitle>项目团队</CardTitle>
          <CardDescription>定义项目负责人、研发和制造等角色。</CardDescription>
        </div>
        <Button
          type='button'
          size='sm'
          variant='outline'
          disabled={disabled}
          onClick={() =>
            append({
              userId: '',
              roleCode: '',
              roleLabel: '',
              responsibilitySummary: '',
            })
          }
        >
          <Plus className='mr-1 size-4' />
          添加成员
        </Button>
      </CardHeader>
      <CardContent className='space-y-3'>
        {fields.map((field, index) => (
          <div
            key={field.id}
            className='grid gap-3 rounded-lg border p-3 md:grid-cols-4'
          >
            <Input
              placeholder='成员 userId'
              disabled={disabled}
              {...form.register(`members.${index}.userId`)}
            />
            <Input
              placeholder='角色编码'
              disabled={disabled}
              {...form.register(`members.${index}.roleCode`)}
            />
            <Input
              placeholder='角色名称'
              disabled={disabled}
              {...form.register(`members.${index}.roleLabel`)}
            />
            <div className='flex gap-2'>
              <Input
                placeholder='职责说明'
                disabled={disabled}
                {...form.register(`members.${index}.responsibilitySummary`)}
              />
              <Button
                type='button'
                variant='ghost'
                disabled={disabled}
                onClick={() => remove(index)}
              >
                删除
              </Button>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

function ProjectMilestonesEditor({
  form,
  disabled = false,
}: {
  form: ReturnType<typeof useForm<ProjectFormValues>>
  disabled?: boolean
}) {
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: 'milestones',
  })
  return (
    <Card>
      <CardHeader className='flex flex-row items-center justify-between space-y-0'>
        <div className='space-y-1'>
          <CardTitle>里程碑</CardTitle>
          <CardDescription>管理项目阶段性目标和计划时间。</CardDescription>
        </div>
        <Button
          type='button'
          size='sm'
          variant='outline'
          disabled={disabled}
          onClick={() =>
            append({
              milestoneCode: '',
              milestoneName: '',
              status: 'PENDING',
              ownerUserId: '',
              plannedAt: '',
              actualAt: '',
              summary: '',
            })
          }
        >
          <Plus className='mr-1 size-4' />
          添加里程碑
        </Button>
      </CardHeader>
      <CardContent className='space-y-3'>
        {fields.map((field, index) => (
          <div
            key={field.id}
            className='grid gap-3 rounded-lg border p-3 md:grid-cols-3'
          >
            <Input
              placeholder='里程碑编码'
              disabled={disabled}
              {...form.register(`milestones.${index}.milestoneCode`)}
            />
            <Input
              placeholder='里程碑名称'
              disabled={disabled}
              {...form.register(`milestones.${index}.milestoneName`)}
            />
            <Input
              placeholder='状态'
              disabled={disabled}
              {...form.register(`milestones.${index}.status`)}
            />
            <Input
              placeholder='负责人 userId'
              disabled={disabled}
              {...form.register(`milestones.${index}.ownerUserId`)}
            />
            <Input
              type='datetime-local'
              placeholder='计划时间'
              disabled={disabled}
              {...form.register(`milestones.${index}.plannedAt`)}
            />
            <Input
              type='datetime-local'
              placeholder='实际时间'
              disabled={disabled}
              {...form.register(`milestones.${index}.actualAt`)}
            />
            <div className='md:col-span-3 flex gap-2'>
              <Textarea
                placeholder='里程碑说明'
                disabled={disabled}
                {...form.register(`milestones.${index}.summary`)}
              />
              <Button
                type='button'
                variant='ghost'
                disabled={disabled}
                onClick={() => remove(index)}
              >
                删除
              </Button>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

function ProjectLinksEditor({
  form,
  disabled = false,
}: {
  form: ReturnType<typeof useForm<ProjectFormValues>>
  disabled?: boolean
}) {
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: 'links',
  })
  return (
    <Card>
      <CardHeader className='flex flex-row items-center justify-between space-y-0'>
        <div className='space-y-1'>
          <CardTitle>关联对象</CardTitle>
          <CardDescription>把项目与变更单、对象、文档和实施任务关联起来。</CardDescription>
        </div>
        <Button
          type='button'
          size='sm'
          variant='outline'
          disabled={disabled}
          onClick={() =>
            append({
              linkType: 'PLM_BILL',
              targetBusinessType: '',
              targetId: '',
              targetNo: '',
              targetTitle: '',
              targetStatus: '',
              targetHref: '',
              summary: '',
            })
          }
        >
          <Plus className='mr-1 size-4' />
          添加关联
        </Button>
      </CardHeader>
      <CardContent className='space-y-3'>
        {fields.map((field, index) => (
          <div
            key={field.id}
            className='grid gap-3 rounded-lg border p-3 md:grid-cols-4'
          >
            <Input
              placeholder='关联类型'
              disabled={disabled}
              {...form.register(`links.${index}.linkType`)}
            />
            <Input
              placeholder='业务类型'
              disabled={disabled}
              {...form.register(`links.${index}.targetBusinessType`)}
            />
            <Input
              placeholder='目标 ID'
              disabled={disabled}
              {...form.register(`links.${index}.targetId`)}
            />
            <Input
              placeholder='目标编号'
              disabled={disabled}
              {...form.register(`links.${index}.targetNo`)}
            />
            <Input
              placeholder='目标标题'
              disabled={disabled}
              {...form.register(`links.${index}.targetTitle`)}
            />
            <Input
              placeholder='目标状态'
              disabled={disabled}
              {...form.register(`links.${index}.targetStatus`)}
            />
            <Input
              placeholder='跳转地址'
              disabled={disabled}
              {...form.register(`links.${index}.targetHref`)}
            />
            <div className='flex gap-2'>
              <Input
                placeholder='摘要'
                disabled={disabled}
                {...form.register(`links.${index}.summary`)}
              />
              <Button
                type='button'
                variant='ghost'
                disabled={disabled}
                onClick={() => remove(index)}
              >
                删除
              </Button>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

function ProjectEditorForm({
  mode,
  detail,
  onSuccess,
}: {
  mode: 'create' | 'edit'
  detail?: PLMProjectDetail
  onSuccess?: (project: PLMProjectDetail) => void
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const form = useForm<ProjectFormValues>({
    resolver: zodResolver(projectFormSchema),
    defaultValues: detail ? mapProjectDetailToForm(detail) : emptyProjectFormValues(),
  })
  const initiationStatus = (detail?.initiationStatus ?? 'DRAFT').toUpperCase() as PLMProjectInitiationStatus
  const formLocked = mode === 'edit' && initiationStatus === 'PENDING_APPROVAL'
  const canSubmitInitiation =
    mode === 'create' ||
    ['DRAFT', 'REJECTED', 'CANCELLED'].includes(initiationStatus)

  useEffect(() => {
    if (detail) {
      form.reset(mapProjectDetailToForm(detail))
    }
  }, [detail, form])

  const persistProject = async (values: ProjectFormValues) => {
    if (mode === 'create') {
      return createPLMProject(normalizeProjectPayload(values))
    }
    return updatePLMProject(
      detail!.projectId,
      normalizeProjectUpdatePayload(values, detail!)
    )
  }

  const saveMutation = useMutation({
    mutationFn: persistProject,
    onSuccess: (project) => {
      toast.success(mode === 'create' ? '项目草稿已保存' : '项目已更新')
      void queryClient.invalidateQueries({ queryKey: ['plm', 'project-list'] })
      void queryClient.invalidateQueries({ queryKey: ['plm', 'workspace-summary'] })
      onSuccess?.(project)
      if (mode === 'create') {
        navigate({ to: '/plm/projects/$projectId', params: { projectId: project.projectId } })
      }
    },
    onError: handleServerError,
  })
  const submitMutation = useMutation({
    mutationFn: async (values: ProjectFormValues) => {
      const project = await persistProject(values)
      return submitPLMProjectInitiation(project.projectId)
    },
    onSuccess: (project) => {
      toast.success('项目已提交立项审批')
      void queryClient.invalidateQueries({ queryKey: ['plm', 'project-list'] })
      void queryClient.invalidateQueries({ queryKey: ['plm', 'workspace-summary'] })
      onSuccess?.(project)
      navigate({ to: '/plm/projects/$projectId', params: { projectId: project.projectId } })
    },
    onError: handleServerError,
  })
  const pending = saveMutation.isPending || submitMutation.isPending

  return (
    <form
      className='space-y-4'
      onSubmit={form.handleSubmit((values) => saveMutation.mutate(values))}
    >
      <Card>
        <CardHeader>
          <CardTitle>{mode === 'create' ? '项目立项信息' : '项目维护'}</CardTitle>
          <CardDescription>
            {mode === 'create'
              ? '先保存项目草稿，再提交立项审批；审批通过后进入正式项目管理。'
              : '统一维护项目主信息、成员、里程碑和关联对象。'}
          </CardDescription>
        </CardHeader>
        <CardContent className='grid gap-4 md:grid-cols-2 xl:grid-cols-4'>
          <Input placeholder='项目编码' disabled={mode === 'edit' || formLocked} {...form.register('projectCode')} />
          <Input placeholder='项目名称' disabled={formLocked} {...form.register('projectName')} />
          <Input placeholder='项目类型，例如 NPI' disabled={formLocked} {...form.register('projectType')} />
          <Input placeholder='项目级别，例如 L1' disabled={formLocked} {...form.register('projectLevel')} />
          <Input placeholder='负责人 userId' disabled={formLocked} {...form.register('ownerUserId')} />
          <Input placeholder='发起/赞助人 userId' disabled={formLocked} {...form.register('sponsorUserId')} />
          <Input placeholder='领域，例如 PRODUCT' disabled={formLocked} {...form.register('domainCode')} />
          <Input placeholder='优先级，例如 HIGH' disabled={formLocked} {...form.register('priorityLevel')} />
          <Input placeholder='目标发布，例如 2026-Q3' disabled={formLocked} {...form.register('targetRelease')} />
          <Input type='date' disabled={formLocked} {...form.register('startDate')} />
          <Input type='date' disabled={formLocked} {...form.register('targetEndDate')} />
          <Input type='date' disabled={mode === 'create' || formLocked} {...form.register('actualEndDate')} />
          <div className='md:col-span-2 xl:col-span-4'>
            <Textarea placeholder='项目摘要' disabled={formLocked} {...form.register('summary')} />
          </div>
          <div className='md:col-span-2'>
            <Textarea placeholder='业务目标' disabled={formLocked} {...form.register('businessGoal')} />
          </div>
          <div className='md:col-span-2'>
            <Textarea placeholder='风险摘要' disabled={formLocked} {...form.register('riskSummary')} />
          </div>
        </CardContent>
      </Card>

      {formLocked ? (
        <Alert>
          <AlertTitle>立项审批进行中</AlertTitle>
          <AlertDescription>
            当前项目已提交立项审批，字段暂时锁定；如需调整，请先撤回立项审批。
          </AlertDescription>
        </Alert>
      ) : null}

      <ProjectMembersEditor form={form} disabled={formLocked} />
      <ProjectMilestonesEditor form={form} disabled={formLocked} />
      <ProjectLinksEditor form={form} disabled={formLocked} />

      <div className='flex flex-wrap justify-end gap-2'>
        <Button type='submit' disabled={pending || formLocked}>
          {saveMutation.isPending ? <Loader2 className='animate-spin' /> : <Save />}
          {mode === 'create' ? '保存草稿' : '保存项目'}
        </Button>
        {canSubmitInitiation ? (
          <Button
            type='button'
            disabled={pending || formLocked}
            onClick={form.handleSubmit((values) => submitMutation.mutate(values))}
          >
            {submitMutation.isPending ? <Loader2 className='animate-spin' /> : <ArrowRight />}
            提交立项审批
          </Button>
        ) : null}
      </div>
    </form>
  )
}

function ProjectDashboardCard({ dashboard }: { dashboard: PLMProjectDashboard }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>项目驾驶舱</CardTitle>
        <CardDescription>从成员、里程碑和关联对象三个层面看项目健康度。</CardDescription>
      </CardHeader>
      <CardContent className='grid gap-4 md:grid-cols-3'>
        <div className='rounded-lg border p-4'>
          <p className='text-xs text-muted-foreground'>团队规模</p>
          <p className='mt-2 text-2xl font-semibold'>{dashboard.memberCount}</p>
        </div>
        <div className='rounded-lg border p-4'>
          <p className='text-xs text-muted-foreground'>开放里程碑 / 超期</p>
          <p className='mt-2 text-2xl font-semibold'>
            {dashboard.openMilestoneCount} / {dashboard.overdueMilestoneCount}
          </p>
        </div>
        <div className='rounded-lg border p-4'>
          <p className='text-xs text-muted-foreground'>业务单 / 对象 / 任务</p>
          <p className='mt-2 text-2xl font-semibold'>
            {dashboard.billLinkCount} / {dashboard.objectLinkCount} / {dashboard.taskLinkCount}
          </p>
        </div>
        <div className='md:col-span-3 grid gap-3 xl:grid-cols-2'>
          <div className='rounded-lg border p-4'>
            <p className='text-sm font-medium'>关联类型分布</p>
            <div className='mt-3 space-y-2 text-sm'>
              {dashboard.linkTypeDistribution.map((item) => (
                <div key={item.code} className='flex items-center justify-between gap-3'>
                  <span>{item.label ?? item.code}</span>
                  <Badge variant='outline'>{item.totalCount}</Badge>
                </div>
              ))}
            </div>
          </div>
          <div className='rounded-lg border p-4'>
            <p className='text-sm font-medium'>里程碑状态</p>
            <div className='mt-3 space-y-2 text-sm'>
              {dashboard.milestoneStatusDistribution.map((item) => (
                <div key={item.code} className='flex items-center justify-between gap-3'>
                  <span>{item.label ?? item.code}</span>
                  <Badge variant='outline'>{item.totalCount}</Badge>
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className='md:col-span-3 rounded-lg border p-4'>
          <p className='text-sm font-medium'>近期风险</p>
          <div className='mt-3 space-y-2'>
            {dashboard.recentRisks.length === 0 ? (
              <p className='text-sm text-muted-foreground'>当前没有高优先级项目风险。</p>
            ) : (
              dashboard.recentRisks.map((risk) => (
                <div key={risk.id} className='rounded-lg bg-muted/30 p-3'>
                  <div className='flex items-center justify-between gap-3'>
                    <p className='font-medium'>{risk.title}</p>
                    <Badge variant='outline'>{risk.status}</Badge>
                  </div>
                  <p className='mt-2 text-sm text-muted-foreground'>{risk.hint}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function ProjectLinksCard({ links }: { links: PLMProjectLink[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>关联对象</CardTitle>
        <CardDescription>统一查看项目所绑定的变更单、文档和实施对象。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-3'>
        {links.length === 0 ? (
          <p className='text-sm text-muted-foreground'>当前还没有关联对象。</p>
        ) : (
          links.map((link) => (
            <div key={link.id} className='rounded-lg border p-3'>
              <div className='flex items-center justify-between gap-3'>
                <div>
                  <p className='font-medium'>{link.targetTitle ?? link.targetNo ?? link.targetId}</p>
                  <p className='text-sm text-muted-foreground'>
                    {link.linkType}
                    {link.targetBusinessType ? ` · ${link.targetBusinessType}` : ''}
                  </p>
                </div>
                <Badge variant='outline'>{link.targetStatus ?? 'UNKNOWN'}</Badge>
              </div>
              {link.summary ? (
                <p className='mt-2 text-sm text-muted-foreground'>{link.summary}</p>
              ) : null}
              {link.targetHref ? (
                <Button asChild size='sm' variant='ghost' className='mt-2 px-0'>
                  <a href={link.targetHref}>
                    打开关联对象
                    <ArrowRight />
                  </a>
                </Button>
              ) : null}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  )
}

function ProjectInitiationCard({
  detail,
}: {
  detail: PLMProjectDetail
}) {
  const queryClient = useQueryClient()
  const approvalDetailQuery = useQuery({
    queryKey: ['workbench', 'approval-by-business', 'PLM_PROJECT', detail.projectId],
    queryFn: () =>
      getApprovalSheetDetailByBusiness({
        businessType: 'PLM_PROJECT',
        businessId: detail.projectId,
      }),
    enabled: detail.initiationStatus !== 'DRAFT',
    retry: false,
  })
  const cancelMutation = useMutation({
    mutationFn: () => cancelPLMProjectInitiation(detail.projectId),
    onSuccess: (project) => {
      toast.success('项目立项审批已撤回')
      queryClient.setQueryData(['plm', 'project-detail', detail.projectId], project)
      void queryClient.invalidateQueries({ queryKey: ['plm', 'project-list'] })
    },
    onError: handleServerError,
  })
  const taskId = approvalDetailQuery.data?.taskId

  return (
    <Card>
      <CardHeader>
        <CardTitle>立项审批</CardTitle>
        <CardDescription>项目先走立项审批，审批通过后再进入正式阶段推进。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-3 text-sm'>
        <div className='flex items-center justify-between gap-3'>
          <span className='text-muted-foreground'>审批状态</span>
          <Badge variant={resolveProjectInitiationStatusVariant(detail.initiationStatus)}>
            {formatProjectInitiationStatus(detail.initiationStatus)}
          </Badge>
        </div>
        <div className='flex items-center justify-between gap-3'>
          <span className='text-muted-foreground'>审批场景</span>
          <span className='font-medium'>{detail.initiationSceneCode ?? 'default'}</span>
        </div>
        <div className='flex items-center justify-between gap-3'>
          <span className='text-muted-foreground'>提交流程</span>
          <span className='font-medium'>{detail.initiationProcessInstanceId ?? '--'}</span>
        </div>
        <div className='flex items-center justify-between gap-3'>
          <span className='text-muted-foreground'>提交时间</span>
          <span className='font-medium'>
            {detail.initiationSubmittedAt
              ? formatApprovalSheetDateTime(detail.initiationSubmittedAt)
              : '--'}
          </span>
        </div>
        <div className='flex items-center justify-between gap-3'>
          <span className='text-muted-foreground'>完成时间</span>
          <span className='font-medium'>
            {detail.initiationDecidedAt
              ? formatApprovalSheetDateTime(detail.initiationDecidedAt)
              : '--'}
          </span>
        </div>

        {detail.initiationStatus === 'PENDING_APPROVAL' ? (
          <Alert>
            <AlertTitle>审批进行中</AlertTitle>
            <AlertDescription>
              立项审批完成前，项目阶段不能推进。需要修改项目信息时请先撤回。
            </AlertDescription>
          </Alert>
        ) : null}
        {detail.initiationStatus === 'REJECTED' ? (
          <Alert variant='destructive'>
            <AlertTitle>立项审批已驳回</AlertTitle>
            <AlertDescription>
              请调整项目方案后重新提交立项审批。
            </AlertDescription>
          </Alert>
        ) : null}

        <div className='flex flex-wrap gap-2'>
          {taskId ? (
            <Button asChild size='sm' variant='outline'>
              <Link to='/workbench/todos/$taskId' params={{ taskId }}>
                打开审批详情
              </Link>
            </Button>
          ) : null}
          {detail.initiationStatus === 'PENDING_APPROVAL' ? (
            <Button
              type='button'
              size='sm'
              variant='destructive'
              disabled={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate()}
            >
              {cancelMutation.isPending ? <Loader2 className='animate-spin' /> : null}
              撤回立项
            </Button>
          ) : null}
        </div>

        {approvalDetailQuery.isError ? (
          <p className='text-xs text-muted-foreground'>
            审批详情暂时不可用，稍后可从工作台按业务单重新进入。
          </p>
        ) : null}
      </CardContent>
    </Card>
  )
}

function ProjectStageTimeline({ detail }: { detail: PLMProjectDetail }) {
  const queryClient = useQueryClient()
  const initiationApproved = detail.initiationStatus === 'APPROVED'
  const phaseMutation = useMutation({
    mutationFn: (payload: PLMProjectPhaseTransitionPayload) =>
      transitionPLMProjectPhase(detail.projectId, payload),
    onSuccess: (project) => {
      toast.success('项目阶段已推进')
      queryClient.setQueryData(['plm', 'project-detail', detail.projectId], project)
      void queryClient.invalidateQueries({ queryKey: ['plm', 'project-list'] })
    },
    onError: handleServerError,
  })
  const quickActions: Array<{ label: string; phase: string; actionCode: string }> = [
    { label: '推进到设计', phase: 'DESIGN', actionCode: 'ADVANCE' },
    { label: '推进到验证', phase: 'VALIDATION', actionCode: 'ADVANCE' },
    { label: '推进到发布', phase: 'RELEASE', actionCode: 'ADVANCE' },
    { label: '推进到关闭', phase: 'CLOSED', actionCode: 'CLOSE' },
  ]
  return (
    <Card>
      <CardHeader>
        <CardTitle>阶段推进</CardTitle>
        <CardDescription>项目阶段流转、挂起和关闭都在这里统一处理。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        {!initiationApproved ? (
          <Alert>
            <AlertTitle>阶段推进受限</AlertTitle>
            <AlertDescription>
              项目立项审批通过后，才能推进到设计、验证、发布和关闭阶段。
            </AlertDescription>
          </Alert>
        ) : null}
        <div className='flex flex-wrap gap-2'>
          {quickActions.map((action) => (
            <Button
              key={action.phase}
              type='button'
              variant='outline'
              disabled={
                phaseMutation.isPending ||
                detail.phaseCode === action.phase ||
                !initiationApproved
              }
              onClick={() =>
                phaseMutation.mutate({
                  toPhaseCode: action.phase,
                  actionCode: action.actionCode,
                  comment: `项目阶段推进到 ${action.phase}`,
                })
              }
            >
              {action.label}
            </Button>
          ))}
          <Button
            type='button'
            variant='secondary'
            disabled={phaseMutation.isPending || !initiationApproved}
            onClick={() =>
              phaseMutation.mutate({
                toPhaseCode: 'ON_HOLD',
                actionCode: 'PAUSE',
                status: 'ON_HOLD',
                comment: '项目临时挂起',
              })
            }
          >
            挂起项目
          </Button>
        </div>
        <div className='space-y-3'>
          {detail.stageEvents.map((event) => (
            <div key={event.id} className='rounded-lg border p-3'>
              <div className='flex items-center justify-between gap-3'>
                <p className='font-medium'>
                  {formatProjectPhase(event.fromPhaseCode)} → {formatProjectPhase(event.toPhaseCode)}
                </p>
                <Badge variant='outline'>{event.actionCode}</Badge>
              </div>
              <p className='mt-1 text-sm text-muted-foreground'>
                {event.changedByDisplayName ?? event.changedBy} · {formatApprovalSheetDateTime(event.changedAt)}
              </p>
              {event.comment ? (
                <p className='mt-2 text-sm text-muted-foreground'>{event.comment}</p>
              ) : null}
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

export function PLMProjectListPage({
  search,
  navigate,
}: {
  search: ListQuerySearch
  navigate: NavigateFn
}) {
  const query = useQuery({
    queryKey: ['plm', 'project-list', search],
    queryFn: () => listPLMProjects(search),
  })
  const pageData = query.data ?? { page: search.page, pageSize: search.pageSize, total: 0, pages: 0, groups: [], records: [] }
  const columns = useMemo<ColumnDef<PLMProjectListItem, unknown>[]>(
    () => [
      {
        accessorKey: 'projectNo',
        header: '项目号',
        cell: ({ row }) => (
          <Link
            className='font-medium text-primary hover:underline'
            to='/plm/projects/$projectId'
            params={{ projectId: row.original.projectId }}
          >
            {row.original.projectNo}
          </Link>
        ),
      },
      { accessorKey: 'projectName', header: '项目名称' },
      { accessorKey: 'projectType', header: '项目类型' },
      {
        accessorKey: 'initiationStatus',
        header: '立项审批',
        cell: ({ row }) => (
          <Badge variant={resolveProjectInitiationStatusVariant(row.original.initiationStatus)}>
            {formatProjectInitiationStatus(row.original.initiationStatus)}
          </Badge>
        ),
      },
      {
        accessorKey: 'phaseCode',
        header: '当前阶段',
        cell: ({ row }) => formatProjectPhase(row.original.phaseCode),
      },
      {
        accessorKey: 'ownerDisplayName',
        header: '负责人',
        cell: ({ row }) => row.original.ownerDisplayName ?? row.original.ownerUserId ?? '--',
      },
      {
        accessorKey: 'targetEndDate',
        header: '目标截止',
        cell: ({ row }) => row.original.targetEndDate ?? '--',
      },
      {
        accessorKey: 'status',
        header: '状态',
        cell: ({ row }) => (
          <Badge variant={resolveProjectStatusVariant(row.original.status)}>
            {formatProjectStatus(row.original.status)}
          </Badge>
        ),
      },
      {
        accessorKey: 'updatedAt',
        header: '更新时间',
        cell: ({ row }) => formatApprovalSheetDateTime(row.original.updatedAt),
      },
    ],
    []
  )

  return (
    <ResourceListPage
      title='PLM 项目台账'
      description='按项目维度汇总变更、里程碑和交付链路，形成项目工作区。'
      endpoint='/api/v1/plm/projects/page'
      searchPlaceholder='搜索项目号、项目编码或项目名称'
      search={search}
      navigate={navigate}
      columns={columns}
      data={pageData.records}
      total={pageData.total}
      summaries={ProjectSummaryItems(pageData.total, search)}
      topContent={<ProjectFilterPanel search={search} navigate={navigate} />}
      createAction={{ label: '创建项目', href: '/plm/projects/create' }}
      extraActions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry sourceRoute='/plm/projects' label='用 AI 分析项目台账' />
          <Button asChild variant='outline'>
            <Link to='/plm/query'>返回 PLM 工作台</Link>
          </Button>
        </div>
      }
    />
  )
}

export function PLMProjectCreatePage() {
  return (
    <PageShell
      title='创建 PLM 项目'
      description='先保存项目草稿，再提交立项审批；审批通过后进入正式项目管理。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry sourceRoute='/plm/projects/create' label='用 AI 总结项目方案' />
          <Button asChild variant='outline'>
            <Link to='/plm/projects'>返回项目台账</Link>
          </Button>
        </div>
      }
    >
      <ProjectEditorForm mode='create' />
    </PageShell>
  )
}

export function PLMProjectDetailPage({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['plm', 'project-detail', projectId],
    queryFn: () => getPLMProjectDetail(projectId),
  })

  if (query.isLoading) {
    return (
      <PageShell title='项目详情' description='正在加载项目工作区。'>
        <div className='flex items-center gap-2 text-sm text-muted-foreground'>
          <Loader2 className='size-4 animate-spin' />
          正在加载项目详情…
        </div>
      </PageShell>
    )
  }

  if (query.isError || !query.data) {
    return (
      <PageShell title='项目详情' description='项目工作区加载失败。'>
        <Alert variant='destructive'>
          <AlertTitle>项目详情加载失败</AlertTitle>
          <AlertDescription>
            {query.error instanceof Error ? query.error.message : '请稍后重试'}
          </AlertDescription>
        </Alert>
      </PageShell>
    )
  }

  const detail = query.data

  return (
    <PageShell
      title={detail.projectName}
      description={`${formatProjectPhase(detail.phaseCode)} · ${formatProjectStatus(detail.status)} · 立项${formatProjectInitiationStatus(detail.initiationStatus)} · 负责人 ${detail.ownerDisplayName ?? detail.ownerUserId ?? '--'}`}
      actions={
        <div className='flex flex-wrap gap-2'>
          <ContextualCopilotEntry
            sourceRoute={`/plm/projects/${projectId}`}
            label='用 AI 总结当前项目'
          />
          <Button asChild variant='outline'>
            <Link to='/plm/projects'>返回项目台账</Link>
          </Button>
        </div>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]'>
        <div className='space-y-4'>
          <ProjectEditorForm
            mode='edit'
            detail={detail}
            onSuccess={(project) => {
              queryClient.setQueryData(['plm', 'project-detail', projectId], project)
            }}
          />
          <ProjectLinksCard links={detail.links} />
        </div>
        <div className='space-y-4'>
          <ProjectInitiationCard detail={detail} />
          <Card>
            <CardHeader>
              <CardTitle>项目摘要</CardTitle>
              <CardDescription>项目主信息、目标和风险一屏查看。</CardDescription>
            </CardHeader>
            <CardContent className='space-y-3 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>项目号</span>
                <span className='font-medium'>{detail.projectNo}</span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>项目编码</span>
                <span className='font-medium'>{detail.projectCode}</span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>发起人</span>
                <span className='font-medium'>{detail.creatorDisplayName ?? detail.creatorUserId}</span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>目标发布</span>
                <span className='font-medium'>{detail.targetRelease ?? '--'}</span>
              </div>
              <div className='space-y-1'>
                <p className='text-muted-foreground'>业务目标</p>
                <p>{detail.businessGoal || '--'}</p>
              </div>
              <div className='space-y-1'>
                <p className='text-muted-foreground'>风险摘要</p>
                <p>{detail.riskSummary || '--'}</p>
              </div>
            </CardContent>
          </Card>
          <ProjectDashboardCard dashboard={detail.dashboard} />
          <ProjectStageTimeline detail={detail} />
        </div>
      </div>
    </PageShell>
  )
}
