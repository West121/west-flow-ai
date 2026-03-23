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
import { NodeFormSelector } from './form-selection'
import {
  type WorkflowFieldBinding,
  type WorkflowGatewayDirection,
  type WorkflowApproverApprovalPolicyType,
  type WorkflowApproverAssignmentMode,
  type WorkflowCcTargetMode,
  type WorkflowDynamicBuilderAppendPolicy,
  type WorkflowDynamicBuilderSourceMode,
  type WorkflowDynamicBuilderTerminatePolicy,
  type WorkflowDynamicBuildMode,
  type WorkflowEdge,
  type WorkflowNode,
  type WorkflowReapprovePolicy,
  type WorkflowReminderChannel,
  type WorkflowSubprocessBusinessBindingMode,
  type WorkflowSubprocessChildFinishPolicy,
  type WorkflowSubprocessTerminatePolicy,
  type WorkflowSubprocessVariableMapping,
  type WorkflowSubprocessVersionPolicy,
  type WorkflowTimeoutApprovalAction,
  type WorkflowVoteWeight,
} from './types'

// 节点配置面板里的枚举选项集中放在这里。
const assignmentModes = [
  { value: 'USER', label: '指定人员' },
  { value: 'ROLE', label: '角色' },
  { value: 'DEPARTMENT', label: '部门' },
  { value: 'DEPARTMENT_AND_CHILDREN', label: '部门及子部门' },
  { value: 'FORM_FIELD', label: '表单字段' },
] satisfies Array<{ value: WorkflowApproverAssignmentMode; label: string }>

const approvalPolicyTypes = [
  { value: 'SINGLE', label: '单人审批' },
  { value: 'SEQUENTIAL', label: '顺序会签' },
  { value: 'PARALLEL', label: '并行会签' },
  { value: 'OR_SIGN', label: '或签' },
  { value: 'VOTE', label: '票签' },
] satisfies Array<{ value: WorkflowApproverApprovalPolicyType; label: string }>

const reapprovePolicies = [
  { value: 'RESTART_ALL', label: '重新走完整个会签' },
  { value: 'CONTINUE_PROGRESS', label: '从当前进度继续' },
] satisfies Array<{ value: WorkflowReapprovePolicy; label: string }>

const ccTargetModes = [
  { value: 'USER', label: '指定人员' },
  { value: 'ROLE', label: '角色' },
  { value: 'DEPARTMENT', label: '部门' },
] satisfies Array<{ value: WorkflowCcTargetMode; label: string }>

const timerScheduleTypes = [
  { value: 'RELATIVE_TO_ARRIVAL', label: '相对到达时间' },
  { value: 'ABSOLUTE_TIME', label: '绝对时间' },
] satisfies Array<{ value: 'RELATIVE_TO_ARRIVAL' | 'ABSOLUTE_TIME'; label: string }>

const triggerModes = [
  { value: 'IMMEDIATE', label: '立即执行' },
  { value: 'SCHEDULED', label: '定时执行' },
] satisfies Array<{ value: 'IMMEDIATE' | 'SCHEDULED'; label: string }>

const subprocessVersionPolicies = [
  { value: 'LATEST_PUBLISHED', label: '最新已发布版本' },
  { value: 'FIXED_VERSION', label: '固定版本' },
] satisfies Array<{ value: WorkflowSubprocessVersionPolicy; label: string }>

const subprocessBusinessBindingModes = [
  { value: 'INHERIT_PARENT', label: '继承父流程业务上下文' },
  { value: 'OVERRIDE', label: '覆盖为子流程独立业务上下文' },
] satisfies Array<{ value: WorkflowSubprocessBusinessBindingMode; label: string }>

const subprocessTerminatePolicies = [
  { value: 'TERMINATE_SUBPROCESS_ONLY', label: '仅终止子流程' },
  { value: 'TERMINATE_PARENT_AND_SUBPROCESS', label: '级联终止父子流程' },
] satisfies Array<{ value: WorkflowSubprocessTerminatePolicy; label: string }>

const subprocessChildFinishPolicies = [
  { value: 'RETURN_TO_PARENT', label: '子流程完成后回到父流程' },
  { value: 'TERMINATE_PARENT', label: '子流程完成后终止父流程' },
] satisfies Array<{ value: WorkflowSubprocessChildFinishPolicy; label: string }>

const dynamicBuilderBuildModes = [
  { value: 'APPROVER_TASKS', label: '生成审批任务' },
  { value: 'SUBPROCESS_CALLS', label: '生成子流程调用' },
] satisfies Array<{ value: WorkflowDynamicBuildMode; label: string }>

const dynamicBuilderSourceModes = [
  { value: 'RULE', label: '规则生成' },
  { value: 'MANUAL_TEMPLATE', label: '人工模板' },
] satisfies Array<{ value: WorkflowDynamicBuilderSourceMode; label: string }>

const dynamicBuilderAppendPolicies = [
  { value: 'SERIAL_AFTER_CURRENT', label: '当前节点后串行追加' },
  { value: 'PARALLEL_WITH_CURRENT', label: '与当前节点并行追加' },
  { value: 'SERIAL_BEFORE_NEXT', label: '当前节点前串行插入' },
] satisfies Array<{ value: WorkflowDynamicBuilderAppendPolicy; label: string }>

const dynamicBuilderTerminatePolicies = [
  { value: 'TERMINATE_GENERATED_ONLY', label: '仅终止生成结构' },
  { value: 'TERMINATE_PARENT_AND_GENERATED', label: '级联终止父节点与生成结构' },
] satisfies Array<{ value: WorkflowDynamicBuilderTerminatePolicy; label: string }>

const gatewayDirections = [
  { value: 'SPLIT', label: '分支' },
  { value: 'JOIN', label: '汇聚' },
] satisfies Array<{ value: WorkflowGatewayDirection; label: string }>

const reminderChannels = [
  { value: 'IN_APP', label: '站内信' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'WEBHOOK', label: 'Webhook' },
  { value: 'SMS', label: '短信' },
  { value: 'WECHAT', label: '企业微信' },
  { value: 'DINGTALK', label: '钉钉' },
] satisfies Array<{ value: WorkflowReminderChannel; label: string }>

const timeoutActions = [
  { value: 'APPROVE', label: '自动通过' },
  { value: 'REJECT', label: '自动拒绝' },
] satisfies Array<{ value: WorkflowTimeoutApprovalAction; label: string }>

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

// 节点配置表单使用一个大 schema 统一兜住校验。
const nodeConfigFormSchema = z
  .object({
    kind: z.enum([
      'start',
      'approver',
      'subprocess',
      'dynamic-builder',
      'condition',
      'inclusive',
      'cc',
      'timer',
      'trigger',
      'parallel',
      'end',
    ]),
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
      nodeFormKey: z.string(),
      nodeFormVersion: z.string(),
      fieldBindingsJson: z.string(),
      approvalPolicyType: z.enum(['SINGLE', 'SEQUENTIAL', 'PARALLEL', 'OR_SIGN', 'VOTE']),
      voteThreshold: z.string(),
      voteWeightsJson: z.string(),
      reapprovePolicy: z.enum(['RESTART_ALL', 'CONTINUE_PROGRESS']),
      autoFinishRemaining: z.boolean(),
      timeoutPolicy: z.object({
        enabled: z.boolean(),
        durationMinutes: z.string(),
        action: z.enum(['APPROVE', 'REJECT']),
      }),
      reminderPolicy: z.object({
        enabled: z.boolean(),
        firstReminderAfterMinutes: z.string(),
        repeatIntervalMinutes: z.string(),
        maxTimes: z.string(),
        channels: z.array(
          z.enum(['IN_APP', 'EMAIL', 'WEBHOOK', 'SMS', 'WECHAT', 'DINGTALK'])
        ),
      }),
      operations: z.object({
        APPROVE: z.boolean(),
        REJECT: z.boolean(),
        RETURN: z.boolean(),
        TRANSFER: z.boolean(),
      }),
      commentRequired: z.boolean(),
    }),
    subprocess: z.object({
      calledProcessKey: z.string(),
      calledVersionPolicy: z.enum(['LATEST_PUBLISHED', 'FIXED_VERSION']),
      calledVersion: z.string(),
      businessBindingMode: z.enum(['INHERIT_PARENT', 'OVERRIDE']),
      terminatePolicy: z.enum([
        'TERMINATE_SUBPROCESS_ONLY',
        'TERMINATE_PARENT_AND_SUBPROCESS',
      ]),
      childFinishPolicy: z.enum(['RETURN_TO_PARENT', 'TERMINATE_PARENT']),
      inputMappingsJson: z.string(),
      outputMappingsJson: z.string(),
    }),
    dynamicBuilder: z.object({
      buildMode: z.enum(['APPROVER_TASKS', 'SUBPROCESS_CALLS']),
      sourceMode: z.enum(['RULE', 'MANUAL_TEMPLATE']),
      ruleExpression: z.string(),
      manualTemplateCode: z.string(),
      appendPolicy: z.enum([
        'SERIAL_AFTER_CURRENT',
        'PARALLEL_WITH_CURRENT',
        'SERIAL_BEFORE_NEXT',
      ]),
      maxGeneratedCount: z.string(),
      terminatePolicy: z.enum([
        'TERMINATE_GENERATED_ONLY',
        'TERMINATE_PARENT_AND_GENERATED',
      ]),
    }),
    cc: z.object({
      targetMode: z.enum(['USER', 'ROLE', 'DEPARTMENT']),
      userIds: z.string(),
      roleCodes: z.string(),
      departmentRef: z.string(),
      readRequired: z.boolean(),
    }),
    timer: z.object({
      scheduleType: z.enum(['RELATIVE_TO_ARRIVAL', 'ABSOLUTE_TIME']),
      runAt: z.string(),
      delayMinutes: z.string(),
      comment: z.string(),
    }),
    trigger: z.object({
      triggerMode: z.enum(['IMMEDIATE', 'SCHEDULED']),
      scheduleType: z.enum(['RELATIVE_TO_ARRIVAL', 'ABSOLUTE_TIME']),
      runAt: z.string(),
      delayMinutes: z.string(),
      triggerKey: z.string(),
      retryTimes: z.string(),
      retryIntervalMinutes: z.string(),
      payloadTemplate: z.string(),
    }),
    condition: z.object({
      defaultEdgeId: z.string(),
      expressionMode: z.string(),
      expressionFieldKey: z.string(),
      branches: z.array(branchSchema),
    }),
    inclusive: z.object({
      gatewayDirection: z.enum(['SPLIT', 'JOIN']),
    }),
    parallel: z.object({
      gatewayDirection: z.enum(['SPLIT', 'JOIN']),
    }),
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
      if (!values.approver.nodeFormKey.trim() && values.approver.fieldBindingsJson.trim() !== '[]') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '配置字段绑定前请先填写节点表单编码',
          path: ['approver', 'nodeFormKey'],
        })
      }
      try {
        const parsedBindings = JSON.parse(values.approver.fieldBindingsJson)
        if (!Array.isArray(parsedBindings)) {
          throw new Error('not array')
        }
        parsedBindings.forEach((binding, index) => {
          const candidate = binding as Partial<WorkflowFieldBinding>
          if (
            !candidate ||
            !candidate.sourceFieldKey?.trim() ||
            !candidate.targetFieldKey?.trim()
          ) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '字段绑定里的 sourceFieldKey 和 targetFieldKey 不能为空',
              path: ['approver', 'fieldBindingsJson'],
            })
          }
          if (
            candidate.source !== 'PROCESS_FORM' &&
            candidate.source !== 'NODE_FORM'
          ) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '字段绑定来源必须是 PROCESS_FORM 或 NODE_FORM',
              path: ['approver', 'fieldBindingsJson'],
            })
          }
          void index
        })
      } catch {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '字段绑定必须是合法的 JSON 数组',
          path: ['approver', 'fieldBindingsJson'],
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
        ['SEQUENTIAL', 'PARALLEL', 'OR_SIGN', 'VOTE'].includes(values.approver.approvalPolicyType)
      ) {
        if (values.approver.assignmentMode !== 'USER') {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '当前阶段会签模式仅支持指定人员',
            path: ['approver', 'assignmentMode'],
          })
        } else if (parseListValue(values.approver.userIds).length < 2) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '会签模式至少需要 2 名处理人',
            path: ['approver', 'userIds'],
          })
        }
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
        try {
          const parsedWeights = JSON.parse(values.approver.voteWeightsJson)
          if (!Array.isArray(parsedWeights)) {
            throw new Error('not array')
          }
          const weights = parsedWeights
            .map((item) => item as Partial<WorkflowVoteWeight>)
            .filter(
              (item): item is Partial<WorkflowVoteWeight> =>
                Boolean(item?.userId) && Number(item.weight) > 0
            )
          if (weights.length !== parseListValue(values.approver.userIds).length) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '票签权重必须覆盖所有处理人',
              path: ['approver', 'voteWeightsJson'],
            })
          }
        } catch {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '票签权重必须是合法的 JSON 数组',
            path: ['approver', 'voteWeightsJson'],
          })
        }
      } else if (values.approver.voteWeightsJson.trim() !== '[]') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '非票签模式不允许配置票签权重',
          path: ['approver', 'voteWeightsJson'],
        })
      }

      if (
        values.approver.approvalPolicyType === 'OR_SIGN' &&
        !values.approver.autoFinishRemaining
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '或签必须启用自动结束剩余任务',
          path: ['approver', 'autoFinishRemaining'],
        })
      }

      if (values.approver.timeoutPolicy.enabled) {
        if (!values.approver.timeoutPolicy.durationMinutes.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '请输入超时分钟数',
            path: ['approver', 'timeoutPolicy', 'durationMinutes'],
          })
        } else {
          const duration = Number(values.approver.timeoutPolicy.durationMinutes)
          if (!Number.isFinite(duration) || duration <= 0) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '超时分钟数必须是大于 0 的数字',
              path: ['approver', 'timeoutPolicy', 'durationMinutes'],
            })
          }
        }
      }

      if (values.approver.reminderPolicy.enabled) {
        const reminderFields: Array<{
          path: ['approver', 'reminderPolicy', 'firstReminderAfterMinutes' | 'repeatIntervalMinutes' | 'maxTimes']
          value: string
          label: string
        }> = [
          {
            path: ['approver', 'reminderPolicy', 'firstReminderAfterMinutes'],
            value: values.approver.reminderPolicy.firstReminderAfterMinutes,
            label: '首次提醒分钟数',
          },
          {
            path: ['approver', 'reminderPolicy', 'repeatIntervalMinutes'],
            value: values.approver.reminderPolicy.repeatIntervalMinutes,
            label: '重复提醒间隔分钟数',
          },
          {
            path: ['approver', 'reminderPolicy', 'maxTimes'],
            value: values.approver.reminderPolicy.maxTimes,
            label: '提醒次数',
          },
        ]

        reminderFields.forEach((item) => {
          if (!item.value.trim()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: `请输入${item.label}`,
              path: item.path,
            })
            return
          }

          const amount = Number(item.value)
          if (!Number.isFinite(amount) || amount <= 0) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: `${item.label}必须是大于 0 的数字`,
              path: item.path,
            })
          }
        })

        if (values.approver.reminderPolicy.channels.length === 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '请至少选择一个提醒渠道',
            path: ['approver', 'reminderPolicy', 'channels'],
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

    if (values.kind === 'subprocess') {
      if (!values.subprocess.calledProcessKey.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入子流程 Key',
          path: ['subprocess', 'calledProcessKey'],
        })
      }

      if (
        values.subprocess.calledVersionPolicy === 'FIXED_VERSION' &&
        !values.subprocess.calledVersion.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '固定版本模式需要填写版本号',
          path: ['subprocess', 'calledVersion'],
        })
      }
    }

    if (values.kind === 'dynamic-builder') {
      if (values.dynamicBuilder.sourceMode === 'RULE' && !values.dynamicBuilder.ruleExpression.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '规则生成需要填写规则表达式',
          path: ['dynamicBuilder', 'ruleExpression'],
        })
      }

      if (
        values.dynamicBuilder.sourceMode === 'MANUAL_TEMPLATE' &&
        !values.dynamicBuilder.manualTemplateCode.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '人工模板需要填写模板编码',
          path: ['dynamicBuilder', 'manualTemplateCode'],
        })
      }

      if (!values.dynamicBuilder.maxGeneratedCount.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入最大生成数量',
          path: ['dynamicBuilder', 'maxGeneratedCount'],
        })
      } else {
        const maxGeneratedCount = Number(values.dynamicBuilder.maxGeneratedCount)
        if (!Number.isFinite(maxGeneratedCount) || maxGeneratedCount <= 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '最大生成数量必须是大于 0 的数字',
            path: ['dynamicBuilder', 'maxGeneratedCount'],
          })
        }
      }
    }

    if (values.kind === 'timer') {
      if (values.timer.scheduleType === 'ABSOLUTE_TIME' && !values.timer.runAt.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '绝对时间模式需要填写执行时间',
          path: ['timer', 'runAt'],
        })
      }
      if (
        values.timer.scheduleType === 'RELATIVE_TO_ARRIVAL' &&
        !values.timer.delayMinutes.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '相对时间模式需要填写延迟分钟数',
          path: ['timer', 'delayMinutes'],
        })
      }
    }

    if (values.kind === 'trigger') {
      if (!values.trigger.triggerKey.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入触发器编码',
          path: ['trigger', 'triggerKey'],
        })
      }

      if (values.trigger.triggerMode === 'SCHEDULED') {
        if (values.trigger.scheduleType === 'ABSOLUTE_TIME' && !values.trigger.runAt.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '定时触发需要填写执行时间',
            path: ['trigger', 'runAt'],
          })
        }
        if (
          values.trigger.scheduleType === 'RELATIVE_TO_ARRIVAL' &&
          !values.trigger.delayMinutes.trim()
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '定时触发需要填写延迟分钟数',
            path: ['trigger', 'delayMinutes'],
          })
        }
      }

      if (!values.trigger.retryTimes.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入重试次数',
          path: ['trigger', 'retryTimes'],
        })
      } else {
        const retryTimes = Number(values.trigger.retryTimes)
        if (!Number.isFinite(retryTimes) || retryTimes < 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '重试次数必须是非负数字',
            path: ['trigger', 'retryTimes'],
          })
        }
      }

      if (!values.trigger.retryIntervalMinutes.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入重试间隔分钟数',
          path: ['trigger', 'retryIntervalMinutes'],
        })
      } else {
        const retryInterval = Number(values.trigger.retryIntervalMinutes)
        if (!Number.isFinite(retryInterval) || retryInterval < 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '重试间隔分钟数必须是非负数字',
            path: ['trigger', 'retryIntervalMinutes'],
          })
        }
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

      if (
        values.condition.expressionMode === 'FIELD_COMPARE' &&
        !values.condition.expressionFieldKey.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '字段表达式模式需要填写表单字段编码',
          path: ['condition', 'expressionFieldKey'],
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
  const voteRule = (config.voteRule ?? {}) as Record<string, unknown>
  const timeoutPolicy = (config.timeoutPolicy ?? {}) as Record<string, unknown>
  const reminderPolicy = (config.reminderPolicy ?? {}) as Record<string, unknown>
  const assignment = (config.assignment ?? {}) as Record<string, unknown>
  const targets = (config.targets ?? {}) as Record<string, unknown>
  const timerConfig = config as Record<string, unknown>
  const triggerConfig = config as Record<string, unknown>
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
      nodeFormKey: String(config.nodeFormKey ?? ''),
      nodeFormVersion: String(config.nodeFormVersion ?? ''),
      fieldBindingsJson: JSON.stringify(
        Array.isArray(config.fieldBindings) ? config.fieldBindings : [],
        null,
        2
      ),
      approvalPolicyType:
        (config.approvalMode as WorkflowApproverApprovalPolicyType) ?? 'SINGLE',
      voteThreshold:
        voteRule.thresholdPercent === null || voteRule.thresholdPercent === undefined
          ? approvalPolicy.voteThreshold === null || approvalPolicy.voteThreshold === undefined
            ? ''
            : String(approvalPolicy.voteThreshold)
          : String(voteRule.thresholdPercent),
      voteWeightsJson: JSON.stringify(
        Array.isArray(voteRule.weights) ? voteRule.weights : [],
        null,
        2
      ),
      reapprovePolicy:
        config.reapprovePolicy === 'CONTINUE_PROGRESS'
          ? 'CONTINUE_PROGRESS'
          : 'RESTART_ALL',
      autoFinishRemaining: Boolean(config.autoFinishRemaining ?? false),
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
      timeoutPolicy: {
        enabled: Boolean(timeoutPolicy.enabled ?? false),
        durationMinutes:
          timeoutPolicy.durationMinutes === null || timeoutPolicy.durationMinutes === undefined
            ? ''
            : String(timeoutPolicy.durationMinutes),
        action: timeoutPolicy.action === 'REJECT' ? 'REJECT' : 'APPROVE',
      },
      reminderPolicy: {
        enabled: Boolean(reminderPolicy.enabled ?? false),
        firstReminderAfterMinutes:
          reminderPolicy.firstReminderAfterMinutes === null ||
          reminderPolicy.firstReminderAfterMinutes === undefined
            ? ''
            : String(reminderPolicy.firstReminderAfterMinutes),
        repeatIntervalMinutes:
          reminderPolicy.repeatIntervalMinutes === null ||
          reminderPolicy.repeatIntervalMinutes === undefined
            ? ''
            : String(reminderPolicy.repeatIntervalMinutes),
        maxTimes:
          reminderPolicy.maxTimes === null || reminderPolicy.maxTimes === undefined
            ? ''
            : String(reminderPolicy.maxTimes),
        channels: Array.isArray(reminderPolicy.channels)
          ? reminderPolicy.channels.filter(
              (channel): channel is WorkflowReminderChannel =>
                channel === 'IN_APP' ||
                channel === 'EMAIL' ||
                channel === 'WEBHOOK' ||
                channel === 'SMS' ||
                channel === 'WECHAT' ||
                channel === 'DINGTALK'
            )
          : ['IN_APP'],
      },
    },
    subprocess: {
      calledProcessKey: String(config.calledProcessKey ?? ''),
      calledVersionPolicy:
        config.calledVersionPolicy === 'FIXED_VERSION'
          ? 'FIXED_VERSION'
          : 'LATEST_PUBLISHED',
      calledVersion:
        config.calledVersion === null || config.calledVersion === undefined
          ? ''
          : String(config.calledVersion),
      businessBindingMode:
        config.businessBindingMode === 'OVERRIDE' ? 'OVERRIDE' : 'INHERIT_PARENT',
      terminatePolicy:
        config.terminatePolicy === 'TERMINATE_PARENT_AND_SUBPROCESS'
          ? 'TERMINATE_PARENT_AND_SUBPROCESS'
          : 'TERMINATE_SUBPROCESS_ONLY',
      childFinishPolicy:
        config.childFinishPolicy === 'TERMINATE_PARENT'
          ? 'TERMINATE_PARENT'
          : 'RETURN_TO_PARENT',
      inputMappingsJson: JSON.stringify(
        Array.isArray(config.inputMappings) ? config.inputMappings : [],
        null,
        2
      ),
      outputMappingsJson: JSON.stringify(
        Array.isArray(config.outputMappings) ? config.outputMappings : [],
        null,
        2
      ),
    },
    dynamicBuilder: {
      buildMode:
        config.buildMode === 'SUBPROCESS_CALLS' ? 'SUBPROCESS_CALLS' : 'APPROVER_TASKS',
      sourceMode: config.sourceMode === 'MANUAL_TEMPLATE' ? 'MANUAL_TEMPLATE' : 'RULE',
      ruleExpression: String(config.ruleExpression ?? ''),
      manualTemplateCode: String(config.manualTemplateCode ?? ''),
      appendPolicy:
        config.appendPolicy === 'PARALLEL_WITH_CURRENT' ||
        config.appendPolicy === 'SERIAL_BEFORE_NEXT'
          ? config.appendPolicy
          : 'SERIAL_AFTER_CURRENT',
      maxGeneratedCount:
        config.maxGeneratedCount === null || config.maxGeneratedCount === undefined
          ? '1'
          : String(config.maxGeneratedCount),
      terminatePolicy:
        config.terminatePolicy === 'TERMINATE_PARENT_AND_GENERATED'
          ? 'TERMINATE_PARENT_AND_GENERATED'
          : 'TERMINATE_GENERATED_ONLY',
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
      expressionMode:
        typeof config.expressionMode === 'string' && config.expressionMode.trim().length > 0
          ? String(config.expressionMode)
          : 'EXPRESSION',
      expressionFieldKey: String(config.expressionFieldKey ?? ''),
      branches: branchDefaults,
    },
    inclusive: {
      gatewayDirection: config.gatewayDirection === 'JOIN' ? 'JOIN' : 'SPLIT',
    },
    timer: {
      scheduleType:
        timerConfig.scheduleType === 'ABSOLUTE_TIME'
          ? 'ABSOLUTE_TIME'
          : 'RELATIVE_TO_ARRIVAL',
      runAt: String(timerConfig.runAt ?? ''),
      delayMinutes:
        timerConfig.delayMinutes === null || timerConfig.delayMinutes === undefined
          ? ''
          : String(timerConfig.delayMinutes),
      comment: String(timerConfig.comment ?? ''),
    },
    trigger: {
      triggerMode: triggerConfig.triggerMode === 'SCHEDULED' ? 'SCHEDULED' : 'IMMEDIATE',
      scheduleType:
        triggerConfig.scheduleType === 'ABSOLUTE_TIME'
          ? 'ABSOLUTE_TIME'
          : 'RELATIVE_TO_ARRIVAL',
      runAt: String(triggerConfig.runAt ?? ''),
      delayMinutes:
        triggerConfig.delayMinutes === null || triggerConfig.delayMinutes === undefined
          ? ''
          : String(triggerConfig.delayMinutes),
      triggerKey: String(triggerConfig.triggerKey ?? ''),
      retryTimes:
        triggerConfig.retryTimes === null || triggerConfig.retryTimes === undefined
          ? ''
          : String(triggerConfig.retryTimes),
      retryIntervalMinutes:
        triggerConfig.retryIntervalMinutes === null ||
        triggerConfig.retryIntervalMinutes === undefined
          ? ''
          : String(triggerConfig.retryIntervalMinutes),
      payloadTemplate: String(triggerConfig.payloadTemplate ?? ''),
    },
    parallel: {
      gatewayDirection: config.gatewayDirection === 'JOIN' ? 'JOIN' : 'SPLIT',
    },
    end: {},
  }
}

function buildNodePatch(values: NodeConfigFormValues) {
  function parseBindings(json: string): WorkflowFieldBinding[] {
    try {
      const parsed = JSON.parse(json)
      if (!Array.isArray(parsed)) {
        return []
      }
      return parsed
        .map((binding) => binding as Partial<WorkflowFieldBinding>)
        .filter(
          (binding): binding is Partial<WorkflowFieldBinding> =>
            Boolean(binding)
        )
        .map<WorkflowFieldBinding>((binding) => ({
          source:
            binding.source === 'NODE_FORM' ? 'NODE_FORM' : 'PROCESS_FORM',
          sourceFieldKey: binding.sourceFieldKey?.trim() ?? '',
          targetFieldKey: binding.targetFieldKey?.trim() ?? '',
        }))
        .filter((binding) => binding.sourceFieldKey && binding.targetFieldKey)
    } catch {
      return []
    }
  }

  function parseVoteWeights(json: string): WorkflowVoteWeight[] {
    try {
      const parsed = JSON.parse(json)
      if (!Array.isArray(parsed)) {
        return []
      }
      return parsed
        .map((item) => item as Partial<WorkflowVoteWeight>)
        .filter((item): item is Partial<WorkflowVoteWeight> => Boolean(item))
        .map<WorkflowVoteWeight>((item) => ({
          userId: item.userId?.trim() ?? '',
          weight: Number(item.weight),
        }))
        .filter((item) => item.userId && Number.isFinite(item.weight) && item.weight > 0)
    } catch {
      return []
    }
  }

  function parseVariableMappings(json: string): WorkflowSubprocessVariableMapping[] {
    try {
      const parsed = JSON.parse(json)
      if (!Array.isArray(parsed)) {
        return []
      }
      return parsed
        .map((item) => item as Partial<WorkflowSubprocessVariableMapping>)
        .filter((item): item is Partial<WorkflowSubprocessVariableMapping> => Boolean(item))
        .map<WorkflowSubprocessVariableMapping>((item) => ({
          source: item.source?.trim() ?? '',
          target: item.target?.trim() ?? '',
        }))
        .filter((item) => item.source && item.target)
    } catch {
      return []
    }
  }

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
          nodeFormKey: values.approver.nodeFormKey.trim(),
          nodeFormVersion: values.approver.nodeFormVersion.trim(),
          fieldBindings: parseBindings(values.approver.fieldBindingsJson),
          approvalMode: values.approver.approvalPolicyType,
          voteRule: {
            thresholdPercent:
              values.approver.approvalPolicyType === 'VOTE'
                ? parseNumber(values.approver.voteThreshold)
                : null,
            passCondition: 'THRESHOLD_REACHED',
            rejectCondition: 'REJECT_THRESHOLD',
            weights:
              values.approver.approvalPolicyType === 'VOTE'
                ? parseVoteWeights(values.approver.voteWeightsJson)
                : [],
          },
          reapprovePolicy: values.approver.reapprovePolicy,
          autoFinishRemaining: values.approver.autoFinishRemaining,
          approvalPolicy: {
            type:
              values.approver.approvalPolicyType === 'SINGLE'
                ? 'SEQUENTIAL'
                : values.approver.approvalPolicyType,
            voteThreshold:
              values.approver.approvalPolicyType === 'VOTE'
                ? parseNumber(values.approver.voteThreshold)
                : null,
          },
          timeoutPolicy: {
            enabled: values.approver.timeoutPolicy.enabled,
            durationMinutes: values.approver.timeoutPolicy.enabled
              ? parseNumber(values.approver.timeoutPolicy.durationMinutes)
              : null,
            action: values.approver.timeoutPolicy.action,
          },
          reminderPolicy: {
            enabled: values.approver.reminderPolicy.enabled,
            firstReminderAfterMinutes: values.approver.reminderPolicy.enabled
              ? parseNumber(values.approver.reminderPolicy.firstReminderAfterMinutes)
              : null,
            repeatIntervalMinutes: values.approver.reminderPolicy.enabled
              ? parseNumber(values.approver.reminderPolicy.repeatIntervalMinutes)
              : null,
            maxTimes: values.approver.reminderPolicy.enabled
              ? parseNumber(values.approver.reminderPolicy.maxTimes)
              : null,
            channels: values.approver.reminderPolicy.channels,
          },
          operations: Object.entries(values.approver.operations)
            .filter(([, checked]) => checked)
            .map(([operation]) => operation),
          commentRequired: values.approver.commentRequired,
        },
      }
    case 'subprocess':
      return {
        config: {
          calledProcessKey: values.subprocess.calledProcessKey.trim(),
          calledVersionPolicy: values.subprocess.calledVersionPolicy,
          calledVersion:
            values.subprocess.calledVersionPolicy === 'FIXED_VERSION'
              ? parseNumber(values.subprocess.calledVersion)
              : null,
          businessBindingMode: values.subprocess.businessBindingMode,
          terminatePolicy: values.subprocess.terminatePolicy,
          childFinishPolicy: values.subprocess.childFinishPolicy,
          inputMappings: parseVariableMappings(values.subprocess.inputMappingsJson),
          outputMappings: parseVariableMappings(values.subprocess.outputMappingsJson),
        },
      }
    case 'dynamic-builder':
      return {
        config: {
          buildMode: values.dynamicBuilder.buildMode,
          sourceMode: values.dynamicBuilder.sourceMode,
          ruleExpression: values.dynamicBuilder.ruleExpression.trim(),
          manualTemplateCode: values.dynamicBuilder.manualTemplateCode.trim(),
          appendPolicy: values.dynamicBuilder.appendPolicy,
          maxGeneratedCount: parseNumber(values.dynamicBuilder.maxGeneratedCount),
          terminatePolicy: values.dynamicBuilder.terminatePolicy,
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
          expressionMode: values.condition.expressionMode,
          expressionFieldKey: values.condition.expressionFieldKey.trim(),
        },
      }
    case 'inclusive':
      return {
        config: {
          gatewayDirection: values.inclusive.gatewayDirection,
        },
      }
    case 'timer':
      return {
        config: {
          scheduleType: values.timer.scheduleType,
          runAt: values.timer.runAt.trim(),
          delayMinutes:
            values.timer.scheduleType === 'RELATIVE_TO_ARRIVAL'
              ? parseNumber(values.timer.delayMinutes)
              : null,
          comment: values.timer.comment.trim(),
        },
      }
    case 'trigger':
      return {
        config: {
          triggerMode: values.trigger.triggerMode,
          scheduleType: values.trigger.scheduleType,
          runAt:
            values.trigger.triggerMode === 'SCHEDULED'
              ? values.trigger.runAt.trim()
              : '',
          delayMinutes:
            values.trigger.triggerMode === 'SCHEDULED' &&
            values.trigger.scheduleType === 'RELATIVE_TO_ARRIVAL'
              ? parseNumber(values.trigger.delayMinutes)
              : null,
          triggerKey: values.trigger.triggerKey.trim(),
          retryTimes: parseNumber(values.trigger.retryTimes),
          retryIntervalMinutes: parseNumber(values.trigger.retryIntervalMinutes),
          payloadTemplate: values.trigger.payloadTemplate.trim(),
        },
      }
    case 'parallel':
      return {
        config: {
          gatewayDirection: values.parallel.gatewayDirection,
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
            nodeFormKey: '',
            nodeFormVersion: '',
            fieldBindingsJson: '[]',
            approvalPolicyType: 'SINGLE',
            voteThreshold: '',
            voteWeightsJson: '[]',
            reapprovePolicy: 'RESTART_ALL',
            autoFinishRemaining: false,
            timeoutPolicy: {
              enabled: false,
              durationMinutes: '',
              action: 'APPROVE',
            },
            reminderPolicy: {
              enabled: false,
              firstReminderAfterMinutes: '',
              repeatIntervalMinutes: '',
              maxTimes: '',
              channels: ['IN_APP'],
            },
            operations: {
              APPROVE: true,
              REJECT: true,
              RETURN: true,
              TRANSFER: false,
            },
            commentRequired: false,
          },
          subprocess: {
            calledProcessKey: '',
            calledVersionPolicy: 'LATEST_PUBLISHED',
            calledVersion: '',
            businessBindingMode: 'INHERIT_PARENT',
            terminatePolicy: 'TERMINATE_SUBPROCESS_ONLY',
            childFinishPolicy: 'RETURN_TO_PARENT',
            inputMappingsJson: '[]',
            outputMappingsJson: '[]',
          },
          dynamicBuilder: {
            buildMode: 'APPROVER_TASKS',
            sourceMode: 'RULE',
            ruleExpression: '',
            manualTemplateCode: '',
            appendPolicy: 'SERIAL_AFTER_CURRENT',
            maxGeneratedCount: '1',
            terminatePolicy: 'TERMINATE_GENERATED_ONLY',
          },
          cc: {
            targetMode: 'USER',
            userIds: '',
            roleCodes: '',
            departmentRef: '',
            readRequired: false,
          },
          timer: {
            scheduleType: 'RELATIVE_TO_ARRIVAL',
            runAt: '',
            delayMinutes: '',
            comment: '',
          },
          trigger: {
            triggerMode: 'IMMEDIATE',
            scheduleType: 'RELATIVE_TO_ARRIVAL',
            runAt: '',
            delayMinutes: '',
            triggerKey: '',
            retryTimes: '',
            retryIntervalMinutes: '',
            payloadTemplate: '',
          },
          condition: {
            defaultEdgeId: '',
            expressionMode: 'EXPRESSION',
            expressionFieldKey: '',
            branches: [],
          },
          inclusive: {
            gatewayDirection: 'SPLIT',
          },
          parallel: {
            gatewayDirection: 'SPLIT',
          },
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
  const selectedTimeoutEnabled = useWatch({
    control: form.control,
    name: 'approver.timeoutPolicy.enabled',
  })
  const selectedReminderEnabled = useWatch({
    control: form.control,
    name: 'approver.reminderPolicy.enabled',
  })
  const selectedNodeFormKey = useWatch({
    control: form.control,
    name: 'approver.nodeFormKey',
  })
  const selectedNodeFormVersion = useWatch({
    control: form.control,
    name: 'approver.nodeFormVersion',
  })
  const selectedDynamicBuilderSourceMode = useWatch({
    control: form.control,
    name: 'dynamicBuilder.sourceMode',
  })
  const selectedCcMode = useWatch({ control: form.control, name: 'cc.targetMode' })
  const selectedBranches = useWatch({
    control: form.control,
    name: 'condition.branches',
  })
  const selectedConditionMode = useWatch({
    control: form.control,
    name: 'condition.expressionMode',
  })
  const selectedTriggerMode = useWatch({
    control: form.control,
    name: 'trigger.triggerMode',
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

        {selectedKind === 'subprocess' ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              子流程节点
            </div>

            <FormField
              control={form.control}
              name='subprocess.calledProcessKey'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>子流程 Key</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder='oa_leave_subflow' />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className='grid gap-4 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='subprocess.calledVersionPolicy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>版本策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择版本策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {subprocessVersionPolicies.map((item) => (
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

              <FormField
                control={form.control}
                name='subprocess.calledVersion'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>固定版本号</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        type='number'
                        min='1'
                        placeholder='3'
                        disabled={form.getValues('subprocess.calledVersionPolicy') !== 'FIXED_VERSION'}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className='grid gap-4 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='subprocess.businessBindingMode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>业务绑定策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择业务绑定策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {subprocessBusinessBindingModes.map((item) => (
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

              <FormField
                control={form.control}
                name='subprocess.childFinishPolicy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>子流程完成策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择完成策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {subprocessChildFinishPolicies.map((item) => (
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
            </div>

            <FormField
              control={form.control}
              name='subprocess.terminatePolicy'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>终止策略</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择终止策略' />
                      </SelectTrigger>
                      <SelectContent>
                        {subprocessTerminatePolicies.map((item) => (
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

            <FormField
              control={form.control}
              name='subprocess.inputMappingsJson'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>输入映射 JSON</FormLabel>
                  <FormControl>
                    <Textarea {...field} rows={4} placeholder='[{\"source\":\"billNo\",\"target\":\"sourceBillNo\"}]' />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='subprocess.outputMappingsJson'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>输出映射 JSON</FormLabel>
                  <FormControl>
                    <Textarea {...field} rows={4} placeholder='[{\"source\":\"approvedResult\",\"target\":\"purchaseResult\"}]' />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        ) : null}

        {selectedKind === 'dynamic-builder' ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              动态构建节点
            </div>

            <div className='grid gap-4 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='dynamicBuilder.buildMode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>构建模式</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择构建模式' />
                        </SelectTrigger>
                        <SelectContent>
                          {dynamicBuilderBuildModes.map((item) => (
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

              <FormField
                control={form.control}
                name='dynamicBuilder.sourceMode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>来源模式</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择来源模式' />
                        </SelectTrigger>
                        <SelectContent>
                          {dynamicBuilderSourceModes.map((item) => (
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
            </div>

            {selectedDynamicBuilderSourceMode === 'RULE' ? (
              <FormField
                control={form.control}
                name='dynamicBuilder.ruleExpression'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>规则表达式</FormLabel>
                    <FormControl>
                      <Textarea {...field} rows={4} placeholder='${amount > 1000}' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedDynamicBuilderSourceMode === 'MANUAL_TEMPLATE' ? (
              <FormField
                control={form.control}
                name='dynamicBuilder.manualTemplateCode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>模板编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='append_leave_audit' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            <div className='grid gap-4 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='dynamicBuilder.appendPolicy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>追加策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择追加策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {dynamicBuilderAppendPolicies.map((item) => (
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

              <FormField
                control={form.control}
                name='dynamicBuilder.maxGeneratedCount'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>最大生成数量</FormLabel>
                    <FormControl>
                      <Input {...field} type='number' min='1' placeholder='1' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name='dynamicBuilder.terminatePolicy'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>终止策略</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择终止策略' />
                      </SelectTrigger>
                      <SelectContent>
                        {dynamicBuilderTerminatePolicies.map((item) => (
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
              name='approver.nodeFormKey'
              render={({ field }) => (
                <FormItem>
                  <NodeFormSelector
                    label='节点覆盖表单'
                    description='审批节点可覆盖流程默认表单，覆盖后任务页优先渲染这里选择的代码组件。'
                    value={
                      selectedNodeFormKey || selectedNodeFormVersion
                        ? {
                            nodeFormKey: selectedNodeFormKey,
                            nodeFormVersion: selectedNodeFormVersion,
                          }
                        : null
                    }
                    onChange={(selection) => {
                      field.onChange(selection?.nodeFormKey ?? '')
                      form.setValue(
                        'approver.nodeFormVersion',
                        selection?.nodeFormVersion ?? '',
                        {
                          shouldDirty: true,
                          shouldTouch: true,
                          shouldValidate: true,
                        }
                      )
                    }}
                  />
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name='approver.fieldBindingsJson'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>字段绑定 JSON</FormLabel>
                  <FormControl>
                    <Textarea
                      {...field}
                      rows={4}
                      className='font-mono text-xs'
                      placeholder='[
  {"source":"PROCESS_FORM","sourceFieldKey":"days","targetFieldKey":"approvedDays"}
]'
                    />
                  </FormControl>
                  <p className='text-xs text-muted-foreground'>
                    目前用 JSON 数组承接，后续再拆成更细的可视化绑定编辑器。
                  </p>
                  <FormMessage />
                </FormItem>
              )}
            />

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

            {selectedApproverPolicy !== 'SINGLE' ? (
              <FormField
                control={form.control}
                name='approver.reapprovePolicy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>重新审批策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择重新审批策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {reapprovePolicies.map((item) => (
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
            ) : null}

            {selectedApproverPolicy === 'VOTE' ? (
              <>
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

                <FormField
                  control={form.control}
                  name='approver.voteWeightsJson'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>票签权重 JSON</FormLabel>
                      <FormControl>
                        <Textarea
                          {...field}
                          rows={4}
                          className='font-mono text-xs'
                          placeholder='[
  {"userId":"usr_002","weight":40},
  {"userId":"usr_003","weight":60}
]'
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : null}

            {selectedApproverPolicy === 'OR_SIGN' ? (
              <FormField
                control={form.control}
                name='approver.autoFinishRemaining'
                render={({ field }) => (
                  <FormItem className='flex flex-row items-center justify-between rounded-xl border px-3 py-2'>
                    <div className='space-y-1'>
                      <FormLabel>自动结束剩余任务</FormLabel>
                      <p className='text-xs text-muted-foreground'>
                        任意一人处理完成后，自动结束同组其他未处理任务。
                      </p>
                    </div>
                    <FormControl>
                      <Switch checked={field.value} onCheckedChange={field.onChange} />
                    </FormControl>
                  </FormItem>
                )}
              />
            ) : null}

            <FormField
              control={form.control}
              name='approver.timeoutPolicy.enabled'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between rounded-xl border px-3 py-2'>
                  <div className='space-y-1'>
                    <FormLabel>超时审批</FormLabel>
                    <p className='text-xs text-muted-foreground'>
                      人工审批节点超时后可自动通过或自动拒绝。
                    </p>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            {selectedTimeoutEnabled ? (
              <div className='grid gap-4 rounded-xl border bg-muted/20 p-4'>
                <FormField
                  control={form.control}
                  name='approver.timeoutPolicy.durationMinutes'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>超时分钟数</FormLabel>
                      <FormControl>
                        <Input {...field} inputMode='numeric' placeholder='45' />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='approver.timeoutPolicy.action'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>超时后动作</FormLabel>
                      <FormControl>
                        <Select value={field.value} onValueChange={field.onChange}>
                          <SelectTrigger className='w-full' aria-label='超时后动作'>
                            <SelectValue placeholder='请选择超时动作' />
                          </SelectTrigger>
                          <SelectContent>
                            {timeoutActions.map((item) => (
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
              </div>
            ) : null}

            <FormField
              control={form.control}
              name='approver.reminderPolicy.enabled'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between rounded-xl border px-3 py-2'>
                  <div className='space-y-1'>
                    <FormLabel>自动提醒</FormLabel>
                    <p className='text-xs text-muted-foreground'>
                      按设定节奏向待办审批人发送提醒。
                    </p>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            {selectedReminderEnabled ? (
              <div className='grid gap-4 rounded-xl border bg-muted/20 p-4'>
                <div className='grid gap-4 md:grid-cols-3'>
                  <FormField
                    control={form.control}
                    name='approver.reminderPolicy.firstReminderAfterMinutes'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>首次提醒分钟数</FormLabel>
                        <FormControl>
                          <Input {...field} inputMode='numeric' placeholder='10' />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name='approver.reminderPolicy.repeatIntervalMinutes'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>重复提醒间隔分钟数</FormLabel>
                        <FormControl>
                          <Input {...field} inputMode='numeric' placeholder='15' />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name='approver.reminderPolicy.maxTimes'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>提醒次数</FormLabel>
                        <FormControl>
                          <Input {...field} inputMode='numeric' placeholder='3' />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='grid gap-2'>
                  <Label>提醒渠道</Label>
                  <div className='grid gap-2 md:grid-cols-2'>
                    {reminderChannels.map((item) => (
                      <FormField
                        key={item.value}
                        control={form.control}
                        name='approver.reminderPolicy.channels'
                        render={({ field }) => {
                          const checked = field.value.includes(item.value)
                          return (
                            <FormItem className='flex flex-row items-center gap-3 rounded-xl border px-3 py-2'>
                              <FormControl>
                                <Checkbox
                                  checked={checked}
                                  onCheckedChange={(nextChecked) => {
                                    const nextValues = nextChecked
                                      ? [...field.value, item.value]
                                      : field.value.filter((value) => value !== item.value)
                                    field.onChange(nextValues)
                                  }}
                                />
                              </FormControl>
                              <FormLabel className='font-normal'>{item.label}</FormLabel>
                            </FormItem>
                          )
                        }}
                      />
                    ))}
                  </div>
                  <FormMessage />
                </div>
              </div>
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
              name='condition.expressionMode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>表达式模式</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择表达式模式' />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value='EXPRESSION'>手写表达式</SelectItem>
                        <SelectItem value='FIELD_COMPARE'>字段表达式</SelectItem>
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {selectedConditionMode === 'FIELD_COMPARE' ? (
              <FormField
                control={form.control}
                name='condition.expressionFieldKey'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>表单字段编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='applicant.amount' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

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

        {selectedKind === 'timer' ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              定时节点
            </div>

            <FormField
              control={form.control}
              name='timer.scheduleType'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>调度类型</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full' aria-label='调度类型'>
                        <SelectValue placeholder='请选择调度类型' />
                      </SelectTrigger>
                      <SelectContent>
                        {timerScheduleTypes.map((item) => (
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

            <div className='grid gap-4 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='timer.runAt'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>执行时间</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='2026-03-23T09:30:00+08:00' />
                    </FormControl>
                    <p className='text-xs text-muted-foreground'>
                      绝对时间模式下使用此值。
                    </p>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='timer.delayMinutes'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>延迟分钟数</FormLabel>
                    <FormControl>
                      <Input {...field} inputMode='numeric' placeholder='30' />
                    </FormControl>
                    <p className='text-xs text-muted-foreground'>
                      相对到达时间模式下使用此值。
                    </p>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name='timer.comment'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>说明</FormLabel>
                  <FormControl>
                    <Textarea {...field} rows={3} placeholder='定时节点用途说明' />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        ) : null}

        {selectedKind === 'trigger' ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              触发节点
            </div>

            <FormField
              control={form.control}
              name='trigger.triggerMode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>触发方式</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full' aria-label='触发方式'>
                        <SelectValue placeholder='请选择触发方式' />
                      </SelectTrigger>
                      <SelectContent>
                        {triggerModes.map((item) => (
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

            {selectedTriggerMode === 'SCHEDULED' ? (
              <div className='grid gap-4 rounded-xl border bg-muted/20 p-4'>
                <FormField
                  control={form.control}
                  name='trigger.scheduleType'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>调度类型</FormLabel>
                      <FormControl>
                        <Select value={field.value} onValueChange={field.onChange}>
                          <SelectTrigger className='w-full' aria-label='调度类型'>
                            <SelectValue placeholder='请选择调度类型' />
                          </SelectTrigger>
                          <SelectContent>
                            {timerScheduleTypes.map((item) => (
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
                <div className='grid gap-4 md:grid-cols-2'>
                  <FormField
                    control={form.control}
                    name='trigger.runAt'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>执行时间</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder='2026-03-23T09:30:00+08:00' />
                        </FormControl>
                        <p className='text-xs text-muted-foreground'>
                          绝对时间模式下使用此值。
                        </p>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name='trigger.delayMinutes'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>延迟分钟数</FormLabel>
                        <FormControl>
                          <Input {...field} inputMode='numeric' placeholder='30' />
                        </FormControl>
                        <p className='text-xs text-muted-foreground'>
                          相对到达时间模式下使用此值。
                        </p>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
            ) : null}

            <FormField
              control={form.control}
              name='trigger.triggerKey'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>触发器编码</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder='sync_invoice' />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className='grid gap-4 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='trigger.retryTimes'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>重试次数</FormLabel>
                    <FormControl>
                      <Input {...field} inputMode='numeric' placeholder='3' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='trigger.retryIntervalMinutes'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>重试间隔分钟数</FormLabel>
                    <FormControl>
                      <Input {...field} inputMode='numeric' placeholder='10' />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name='trigger.payloadTemplate'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>负载模板</FormLabel>
                  <FormControl>
                    <Textarea {...field} rows={3} placeholder='{"source":"workflow"}' />
                  </FormControl>
                  <p className='text-xs text-muted-foreground'>
                    触发时将把这里的模板内容作为执行上下文。
                  </p>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        ) : null}

        {selectedKind === 'inclusive' || selectedKind === 'parallel' || selectedKind === 'end' ? (
          <div className='rounded-2xl border p-4 text-sm text-muted-foreground'>
            {selectedKind === 'end' ? (
              '结束节点当前只保留名称和描述配置。'
            ) : (
              <div className='flex flex-col gap-4'>
                <div>
                  <h3 className='text-sm font-medium text-foreground'>
                    {selectedKind === 'inclusive' ? '包容分支节点' : '并行分支节点'}
                  </h3>
                  <p className='text-xs text-muted-foreground'>
                    使用同一节点种类表达分支与汇聚，方向由下面的配置决定。
                  </p>
                </div>
                <FormField
                  control={form.control}
                  name={
                    selectedKind === 'inclusive'
                      ? 'inclusive.gatewayDirection'
                      : 'parallel.gatewayDirection'
                  }
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>节点方向</FormLabel>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder='请选择节点方向' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {gatewayDirections.map((item) => (
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
              </div>
            )}
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
