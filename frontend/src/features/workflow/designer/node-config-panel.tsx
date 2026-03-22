import { useEffect, useMemo } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch } from 'react-hook-form'
import { Check, RotateCcw, Save } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import {
  buildConditionFormDefaults,
  joinListValue,
  parseListValue,
} from './config'
import {
  type WorkflowApproverApprovalPolicyType,
  type WorkflowApproverAssignmentMode,
  type WorkflowCcTargetMode,
  type WorkflowEdge,
  type WorkflowNode,
} from './types'

const assignmentModes = [
  { value: 'USER', label: '指定人员' },
  { value: 'ROLE', label: '角色' },
  { value: 'DEPARTMENT', label: '部门' },
  { value: 'DEPARTMENT_AND_CHILDREN', label: '部门及子部门' },
  { value: 'FORM_FIELD', label: '表单字段' },
] satisfies Array<{ value: WorkflowApproverAssignmentMode; label: string }>

const approvalPolicyTypes = [
  { value: 'SEQUENTIAL', label: '顺序会签' },
  { value: 'PARALLEL', label: '并行会签' },
  { value: 'VOTE', label: '票签' },
] satisfies Array<{ value: WorkflowApproverApprovalPolicyType; label: string }>

const ccTargetModes = [
  { value: 'USER', label: '指定人员' },
  { value: 'ROLE', label: '角色' },
  { value: 'DEPARTMENT', label: '部门' },
] satisfies Array<{ value: WorkflowCcTargetMode; label: string }>

const operationOptions = [
  { key: 'APPROVE', label: '通过' },
  { key: 'REJECT', label: '拒绝' },
  { key: 'RETURN', label: '退回' },
  { key: 'TRANSFER', label: '转办' },
] as const

const branchSchema = z.object({
  edgeId: z.string(),
  label: z.string(),
  conditionExpression: z.string(),
})

const nodeConfigFormSchema = z
  .object({
    kind: z.enum(['start', 'approver', 'condition', 'cc', 'parallel', 'end']),
    label: z.string().trim().min(1, '节点名称不能为空'),
    description: z.string().trim().min(1, '节点描述不能为空'),
    start: z.object({
      initiatorEditable: z.boolean(),
    }),
    approver: z.object({
      assignmentMode: z.enum([
        'USER',
        'ROLE',
        'DEPARTMENT',
        'DEPARTMENT_AND_CHILDREN',
        'FORM_FIELD',
      ]),
      userIds: z.string(),
      roleCodes: z.string(),
      departmentRef: z.string(),
      formFieldKey: z.string(),
      approvalPolicyType: z.enum(['SEQUENTIAL', 'PARALLEL', 'VOTE']),
      voteThreshold: z.string(),
      operations: z.object({
        APPROVE: z.boolean(),
        REJECT: z.boolean(),
        RETURN: z.boolean(),
        TRANSFER: z.boolean(),
      }),
      commentRequired: z.boolean(),
    }),
    cc: z.object({
      targetMode: z.enum(['USER', 'ROLE', 'DEPARTMENT']),
      userIds: z.string(),
      roleCodes: z.string(),
      departmentRef: z.string(),
      readRequired: z.boolean(),
    }),
    condition: z.object({
      defaultEdgeId: z.string(),
      branches: z.array(branchSchema),
    }),
    parallel: z.object({}),
    end: z.object({}),
  })
  .superRefine((values, ctx) => {
    if (values.kind === 'approver') {
      if (values.approver.assignmentMode === 'USER' && !values.approver.userIds.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择至少一个处理人',
          path: ['approver', 'userIds'],
        })
      }
      if (values.approver.assignmentMode === 'ROLE' && !values.approver.roleCodes.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择至少一个角色',
          path: ['approver', 'roleCodes'],
        })
      }
      if (
        ['DEPARTMENT', 'DEPARTMENT_AND_CHILDREN'].includes(values.approver.assignmentMode) &&
        !values.approver.departmentRef.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入部门编码',
          path: ['approver', 'departmentRef'],
        })
      }
      if (values.approver.assignmentMode === 'FORM_FIELD' && !values.approver.formFieldKey.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入表单字段编码',
          path: ['approver', 'formFieldKey'],
        })
      }

      const operations = Object.values(values.approver.operations).filter(Boolean)
      if (operations.length < 1) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '至少选择一种审批操作',
          path: ['approver', 'operations'],
        })
      }

      if (
        values.approver.approvalPolicyType === 'VOTE' &&
        !values.approver.voteThreshold.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '票签节点需要设置阈值',
          path: ['approver', 'voteThreshold'],
        })
      } else if (values.approver.approvalPolicyType === 'VOTE') {
        const threshold = Number(values.approver.voteThreshold)
        if (!Number.isFinite(threshold) || threshold < 1 || threshold > 100) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '票签阈值必须是 1 到 100 的数字',
            path: ['approver', 'voteThreshold'],
          })
        }
      }
    }

    if (values.kind === 'cc') {
      if (values.cc.targetMode === 'USER' && !values.cc.userIds.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择至少一个抄送人',
          path: ['cc', 'userIds'],
        })
      }
      if (values.cc.targetMode === 'ROLE' && !values.cc.roleCodes.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择至少一个抄送角色',
          path: ['cc', 'roleCodes'],
        })
      }
      if (values.cc.targetMode === 'DEPARTMENT' && !values.cc.departmentRef.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入抄送部门编码',
          path: ['cc', 'departmentRef'],
        })
      }
    }

    if (values.kind === 'condition') {
      if (!values.condition.defaultEdgeId.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择默认分支',
          path: ['condition', 'defaultEdgeId'],
        })
      }

      values.condition.branches.forEach((branch, index) => {
        if (branch.edgeId !== values.condition.defaultEdgeId && !branch.conditionExpression.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '非默认分支需要配置条件表达式',
            path: ['condition', 'branches', index, 'conditionExpression'],
          })
        }
      })
    }
  })

type NodeConfigFormValues = z.infer<typeof nodeConfigFormSchema>

function parseNumber(value: string) {
  const nextValue = Number(value)
  return Number.isFinite(nextValue) ? nextValue : null
}

function buildFormValues(node: WorkflowNode, edges: WorkflowEdge[]): NodeConfigFormValues {
  const outgoingEdges = edges.filter((edge) => edge.source === node.id)
  const config = node.data.config as Record<string, unknown>
  const approvalPolicy = (config.approvalPolicy ?? {}) as Record<string, unknown>
  const assignment = (config.assignment ?? {}) as Record<string, unknown>
  const targets = (config.targets ?? {}) as Record<string, unknown>
  const branchDefaults = outgoingEdges.map((edge) => ({
    ...buildConditionFormDefaults(
      edge.id,
      edge.data?.condition?.expression ?? '',
      typeof edge.label === 'string' ? edge.label : edge.id
    ),
  }))

  return {
    kind: node.data.kind,
    label: node.data.label,
    description: node.data.description,
    start: {
      initiatorEditable:
        typeof config.initiatorEditable === 'boolean'
          ? config.initiatorEditable
          : true,
    },
    approver: {
      assignmentMode: (assignment.mode as WorkflowApproverAssignmentMode) ?? 'USER',
      userIds: joinListValue(
        Array.isArray(assignment.userIds) ? assignment.userIds.map(String) : ['usr_002']
      ),
      roleCodes: joinListValue(
        Array.isArray(assignment.roleCodes) ? assignment.roleCodes.map(String) : []
      ),
      departmentRef: String(assignment.departmentRef ?? ''),
      formFieldKey: String(assignment.formFieldKey ?? ''),
      approvalPolicyType:
        (approvalPolicy.type as WorkflowApproverApprovalPolicyType) ?? 'SEQUENTIAL',
      voteThreshold:
        approvalPolicy.voteThreshold === null || approvalPolicy.voteThreshold === undefined
          ? ''
          : String(approvalPolicy.voteThreshold),
      operations: {
        APPROVE: Array.isArray(config.operations)
          ? config.operations.includes('APPROVE')
          : true,
        REJECT: Array.isArray(config.operations) ? config.operations.includes('REJECT') : true,
        RETURN: Array.isArray(config.operations) ? config.operations.includes('RETURN') : true,
        TRANSFER: Array.isArray(config.operations)
          ? config.operations.includes('TRANSFER')
          : false,
      },
      commentRequired: Boolean(config.commentRequired ?? false),
    },
    cc: {
      targetMode: (targets.mode as WorkflowCcTargetMode) ?? 'USER',
      userIds: joinListValue(
        Array.isArray(targets.userIds) ? targets.userIds.map(String) : ['usr_003']
      ),
      roleCodes: joinListValue(
        Array.isArray(targets.roleCodes) ? targets.roleCodes.map(String) : []
      ),
      departmentRef: String(targets.departmentRef ?? ''),
      readRequired: Boolean(config.readRequired ?? false),
    },
    condition: {
      defaultEdgeId:
        typeof config.defaultEdgeId === 'string' && config.defaultEdgeId.trim().length > 0
          ? config.defaultEdgeId
          : outgoingEdges[0]?.id ?? '',
      branches: branchDefaults,
    },
    parallel: {},
    end: {},
  }
}

function buildNodePatch(values: NodeConfigFormValues) {
  switch (values.kind) {
    case 'start':
      return {
        config: {
          initiatorEditable: values.start.initiatorEditable,
        },
      }
    case 'approver':
      return {
        config: {
          assignment: {
            mode: values.approver.assignmentMode,
            userIds: parseListValue(values.approver.userIds),
            roleCodes: parseListValue(values.approver.roleCodes),
            departmentRef: values.approver.departmentRef.trim(),
            formFieldKey: values.approver.formFieldKey.trim(),
          },
          approvalPolicy: {
            type: values.approver.approvalPolicyType,
            voteThreshold:
              values.approver.approvalPolicyType === 'VOTE'
                ? parseNumber(values.approver.voteThreshold)
                : null,
          },
          operations: Object.entries(values.approver.operations)
            .filter(([, checked]) => checked)
            .map(([operation]) => operation),
          commentRequired: values.approver.commentRequired,
        },
      }
    case 'cc':
      return {
        config: {
          targets: {
            mode: values.cc.targetMode,
            userIds: parseListValue(values.cc.userIds),
            roleCodes: parseListValue(values.cc.roleCodes),
            departmentRef: values.cc.departmentRef.trim(),
          },
          readRequired: values.cc.readRequired,
        },
      }
    case 'condition':
      return {
        config: {
          defaultEdgeId: values.condition.defaultEdgeId,
        },
      }
    default:
      return {
        config: {},
      }
  }
}

export function NodeConfigPanel({
  node,
  edges,
  onApply,
  onReset,
}: {
  node: WorkflowNode | null
  edges: WorkflowEdge[]
  onApply: (
    nodeId: string,
    patch: {
      label: string
      description: string
      config: unknown
    },
    edgePatches?: Array<{
      edgeId: string
      label?: string
      condition?: unknown
    }>
  ) => void
  onReset?: () => void
}) {
  const outgoingEdges = useMemo(
    () => edges.filter((edge) => edge.source === node?.id),
    [edges, node?.id]
  )

  const form = useForm<NodeConfigFormValues>({
    resolver: zodResolver(nodeConfigFormSchema),
    defaultValues: node
      ? buildFormValues(node, edges)
      : {
          kind: 'start',
          label: '',
          description: '',
          start: { initiatorEditable: true },
          approver: {
            assignmentMode: 'USER',
            userIds: '',
            roleCodes: '',
            departmentRef: '',
            formFieldKey: '',
            approvalPolicyType: 'SEQUENTIAL',
            voteThreshold: '',
            operations: {
              APPROVE: true,
              REJECT: true,
              RETURN: true,
              TRANSFER: false,
            },
            commentRequired: false,
          },
          cc: {
            targetMode: 'USER',
            userIds: '',
            roleCodes: '',
            departmentRef: '',
            readRequired: false,
          },
          condition: {
            defaultEdgeId: '',
            branches: [],
          },
          parallel: {},
          end: {},
        },
  })

  const selectedKind = useWatch({ control: form.control, name: 'kind' })
  const selectedApproverMode = useWatch({
    control: form.control,
    name: 'approver.assignmentMode',
  })
  const selectedApproverPolicy = useWatch({
    control: form.control,
    name: 'approver.approvalPolicyType',
  })
  const selectedCcMode = useWatch({ control: form.control, name: 'cc.targetMode' })
  const selectedBranches = useWatch({
    control: form.control,
    name: 'condition.branches',
  })
  const operationsError =
    (form.formState.errors.approver?.operations as { message?: string } | undefined)
      ?.message ?? ''

  useEffect(() => {
    if (!node) {
      return
    }

    form.reset(buildFormValues(node, edges))
  }, [edges, form, node])

  function handleReset() {
    if (!node) {
      return
    }

    form.reset(buildFormValues(node, edges))
    onReset?.()
  }

  function submit(values: NodeConfigFormValues) {
    if (!node) {
      return
    }

    const patch = buildNodePatch(values)
    const edgePatches =
      values.kind === 'condition'
        ? values.condition.branches.map((branch) => ({
            edgeId: branch.edgeId,
            label: branch.label.trim() || branch.edgeId,
            condition:
              branch.edgeId === values.condition.defaultEdgeId
                ? undefined
                : {
                    type: 'EXPRESSION' as const,
                    expression: branch.conditionExpression.trim(),
                  },
          }))
        : undefined

    onApply(
      node.id,
      {
        label: values.label.trim(),
        description: values.description.trim(),
        config: patch.config,
      },
      edgePatches
    )

    toast.success('节点配置已应用到画布。')
  }

  if (!node) {
    return (
      <div className='rounded-2xl border border-dashed p-4 text-sm text-muted-foreground'>
        请选择一个节点后在这里编辑名称、描述和关键配置。
      </div>
    )
  }

  return (
    <Form {...form}>
      <form
        className='flex flex-col gap-4'
        onSubmit={form.handleSubmit(submit)}
      >
        <div className='flex flex-col gap-3 rounded-2xl border p-4'>
          <div className='flex items-center justify-between gap-3'>
            <div>
              <p className='text-sm font-semibold'>{node.data.label}</p>
              <p className='text-xs text-muted-foreground'>节点编码：{node.id}</p>
            </div>
            <Button type='button' variant='ghost' size='sm' onClick={handleReset}>
              <RotateCcw data-icon='inline-start' />
              重置
            </Button>
          </div>

          <FormField
            control={form.control}
            name='label'
            render={({ field }) => (
              <FormItem>
                <FormLabel>节点名称</FormLabel>
                <FormControl>
                  <Input {...field} placeholder='请输入节点名称' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name='description'
            render={({ field }) => (
              <FormItem>
                <FormLabel>节点描述</FormLabel>
                <FormControl>
                  <Textarea {...field} rows={3} placeholder='请输入节点描述' />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {selectedKind === 'start' ? (
          <div className='flex flex-col gap-3 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              发起节点
            </div>
            <FormField
              control={form.control}
              name='start.initiatorEditable'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between rounded-xl border px-3 py-2'>
                  <div className='space-y-1'>
                    <FormLabel>发起人可编辑</FormLabel>
                    <p className='text-xs text-muted-foreground'>
                      允许发起流程时修改发起人信息。
                    </p>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        ) : null}

        {selectedKind === 'approver' ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              审批节点
            </div>

            <FormField
              control={form.control}
              name='approver.assignmentMode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>处理人分配方式</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择分配方式' />
                      </SelectTrigger>
                      <SelectContent>
                        {assignmentModes.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {selectedApproverMode === 'USER' ? (
              <FormField
                control={form.control}
                name='approver.userIds'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>人员编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='usr_001, usr_002' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedApproverMode === 'ROLE' ? (
              <FormField
                control={form.control}
                name='approver.roleCodes'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>角色编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='role_manager, role_hr' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedApproverMode === 'DEPARTMENT' ||
            selectedApproverMode === 'DEPARTMENT_AND_CHILDREN' ? (
              <FormField
                control={form.control}
                name='approver.departmentRef'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>部门编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='dept_finance' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedApproverMode === 'FORM_FIELD' ? (
              <FormField
                control={form.control}
                name='approver.formFieldKey'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>表单字段编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='applicant.managerCode' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            <FormField
              control={form.control}
              name='approver.approvalPolicyType'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>审批策略</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择审批策略' />
                      </SelectTrigger>
                      <SelectContent>
                        {approvalPolicyTypes.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {selectedApproverPolicy === 'VOTE' ? (
              <FormField
                control={form.control}
                name='approver.voteThreshold'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>票签阈值</FormLabel>
                    <FormControl>
                      <Input {...field} inputMode='numeric' placeholder='50' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            <div className='flex flex-col gap-2'>
              <Label>可执行操作</Label>
              <div className='grid gap-2 md:grid-cols-2'>
                {operationOptions.map((option) => (
                  <FormField
                    key={option.key}
                    control={form.control}
                    name={`approver.operations.${option.key}`}
                    render={({ field }) => (
                      <FormItem className='flex flex-row items-center gap-3 rounded-xl border px-3 py-2'>
                        <FormControl>
                          <Checkbox
                            checked={field.value}
                            onCheckedChange={(checked) => field.onChange(Boolean(checked))}
                          />
                        </FormControl>
                        <FormLabel className='font-normal'>{option.label}</FormLabel>
                      </FormItem>
                    )}
                  />
                ))}
              </div>
              {operationsError ? (
                <p className='text-sm text-destructive'>{operationsError}</p>
              ) : null}
            </div>

            <FormField
              control={form.control}
              name='approver.commentRequired'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between rounded-xl border px-3 py-2'>
                  <div className='space-y-1'>
                    <FormLabel>强制填写审批意见</FormLabel>
                    <p className='text-xs text-muted-foreground'>
                      通过弹框、表单或工具栏约束审批意见必填。
                    </p>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        ) : null}

        {selectedKind === 'cc' ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              抄送节点
            </div>

            <FormField
              control={form.control}
              name='cc.targetMode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>抄送对象</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择抄送对象' />
                      </SelectTrigger>
                      <SelectContent>
                        {ccTargetModes.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {selectedCcMode === 'USER' ? (
              <FormField
                control={form.control}
                name='cc.userIds'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>人员编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='usr_003, usr_004' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedCcMode === 'ROLE' ? (
              <FormField
                control={form.control}
                name='cc.roleCodes'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>角色编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='role_manager' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedCcMode === 'DEPARTMENT' ? (
              <FormField
                control={form.control}
                name='cc.departmentRef'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>部门编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='dept_finance' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            <FormField
              control={form.control}
              name='cc.readRequired'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between rounded-xl border px-3 py-2'>
                  <div className='space-y-1'>
                    <FormLabel>已阅确认</FormLabel>
                    <p className='text-xs text-muted-foreground'>
                      抄送后要求接收人确认已阅。
                    </p>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        ) : null}

        {selectedKind === 'condition' ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              条件节点
            </div>

            <FormField
              control={form.control}
              name='condition.defaultEdgeId'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>默认分支</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择默认分支' />
                      </SelectTrigger>
                      <SelectContent>
                        {outgoingEdges.map((edge) => (
                          <SelectItem key={edge.id} value={edge.id}>
                            {edge.label ?? edge.id}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <Separator />

            <div className='flex flex-col gap-4'>
              {selectedBranches.map((branch, index) => (
                <div key={branch.edgeId} className='flex flex-col gap-3 rounded-xl border p-3'>
                  <div className='flex items-center justify-between gap-3'>
                    <div>
                      <p className='text-sm font-medium'>
                        {branch.edgeId === form.getValues('condition.defaultEdgeId')
                          ? '默认分支'
                          : '条件分支'}
                      </p>
                      <p className='text-xs text-muted-foreground'>连线：{branch.edgeId}</p>
                    </div>
                    <Button
                      type='button'
                      variant='ghost'
                      size='sm'
                      onClick={() =>
                        form.setValue(
                          'condition.defaultEdgeId',
                          branch.edgeId,
                          { shouldDirty: true, shouldTouch: true, shouldValidate: true }
                        )
                      }
                    >
                      设为默认
                    </Button>
                  </div>

                  <FormField
                    control={form.control}
                    name={`condition.branches.${index}.label`}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>分支名称</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder='例如：金额大于 1 万' />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name={`condition.branches.${index}.conditionExpression`}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>条件表达式</FormLabel>
                        <FormControl>
                          <Textarea
                            {...field}
                            rows={3}
                            placeholder='例如：amount > 10000 && department == "FINANCE"'
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              ))}
              {selectedBranches.length === 0 ? (
                <div className='rounded-xl border border-dashed p-4 text-sm text-muted-foreground'>
                  当前条件节点没有出边，请先在画布上连出至少两条分支。
                </div>
              ) : null}
            </div>
          </div>
        ) : null}

        {selectedKind === 'parallel' || selectedKind === 'end' ? (
          <div className='rounded-2xl border p-4 text-sm text-muted-foreground'>
            {selectedKind === 'parallel'
              ? '并行节点当前只保留名称和描述配置，后续会继续扩展分支/汇聚条件。'
              : '结束节点当前只保留名称和描述配置。'}
          </div>
        ) : null}

        <div className='flex flex-wrap gap-2'>
          <Button type='submit'>
            <Save data-icon='inline-start' />
            应用到画布
          </Button>
          <Button type='button' variant='outline' onClick={handleReset}>
            <RotateCcw data-icon='inline-start' />
            恢复默认
          </Button>
        </div>
      </form>
    </Form>
  )
}
