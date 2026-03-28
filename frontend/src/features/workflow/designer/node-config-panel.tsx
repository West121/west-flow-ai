import { useCallback, useEffect, useMemo, useRef } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch } from 'react-hook-form'
import { Check, Plus, Trash2 } from 'lucide-react'
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
import { WorkflowFieldSelector, WorkflowFormulaEditor } from './expression-tools'
import { WorkflowPrincipalPickerField } from './selection-picker'
import {
  isWorkflowCollaborationNodeKind,
  resolveWorkflowCollaborationNodeLabel,
} from './collaboration'
import {
  type WorkflowFieldBinding,
  type WorkflowConditionOperator,
  type WorkflowGatewayDirection,
  type WorkflowInclusiveBranchMergePolicy,
  type WorkflowApproverApprovalPolicyType,
  type WorkflowApproverAssignmentMode,
  type WorkflowCcTargetMode,
  type WorkflowDynamicBuilderAppendPolicy,
  type WorkflowDynamicBuilderExecutionStrategy,
  type WorkflowDynamicBuilderFallbackStrategy,
  type WorkflowDynamicBuilderNodeConfig,
  type WorkflowDynamicBuilderSourceMode,
  type WorkflowDynamicBuilderTerminatePolicy,
  type WorkflowDynamicBuildMode,
  type WorkflowEscalationTargetMode,
  type WorkflowEdge,
  type WorkflowNode,
  type WorkflowProcessFormField,
  type WorkflowReapprovePolicy,
  type WorkflowReminderChannel,
  type WorkflowSubprocessBusinessBindingMode,
  type WorkflowSubprocessCallScope,
  type WorkflowSubprocessChildFinishPolicy,
  type WorkflowSubprocessChildStartStrategy,
  type WorkflowSubprocessJoinMode,
  type WorkflowSubprocessTerminatePolicy,
  type WorkflowSubprocessParentResumeStrategy,
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
  { value: 'FORMULA', label: '自定义公式' },
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

const subprocessCallScopes = [
  { value: 'CHILD_ONLY', label: '仅子流程' },
  { value: 'CHILD_AND_DESCENDANTS', label: '子流程及其后代' },
] satisfies Array<{ value: WorkflowSubprocessCallScope; label: string }>

const subprocessJoinModes = [
  { value: 'AUTO_RETURN', label: '自动回传' },
  { value: 'WAIT_PARENT_CONFIRM', label: '等待父流程确认' },
] satisfies Array<{ value: WorkflowSubprocessJoinMode; label: string }>

const subprocessChildStartStrategies = [
  { value: 'LATEST_PUBLISHED', label: '最新已发布版本' },
  { value: 'FIXED_VERSION', label: '固定版本' },
  { value: 'SCENE_BINDING', label: '场景绑定' },
] satisfies Array<{ value: WorkflowSubprocessChildStartStrategy; label: string }>

const subprocessParentResumeStrategies = [
  { value: 'AUTO_RETURN', label: '自动回到父流程' },
  { value: 'WAIT_PARENT_CONFIRM', label: '等待父流程确认后恢复' },
] satisfies Array<{ value: WorkflowSubprocessParentResumeStrategy; label: string }>

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
  { value: 'MODEL_DRIVEN', label: '模型驱动' },
  { value: 'MANUAL_TEMPLATE', label: '人工模板' },
] satisfies Array<{ value: WorkflowDynamicBuilderSourceMode; label: string }>

const dynamicBuilderExecutionStrategies = [
  { value: 'RULE_FIRST', label: '规则优先' },
  { value: 'RULE_ONLY', label: '仅规则' },
  { value: 'TEMPLATE_FIRST', label: '模板优先' },
  { value: 'TEMPLATE_ONLY', label: '仅模板' },
] satisfies Array<{ value: WorkflowDynamicBuilderExecutionStrategy; label: string }>

const dynamicBuilderFallbackStrategies = [
  { value: 'KEEP_CURRENT', label: '保留当前节点' },
  { value: 'USE_RULE', label: '回退到规则结果' },
  { value: 'USE_TEMPLATE', label: '回退到模板结果' },
  { value: 'SKIP_GENERATION', label: '跳过生成' },
] satisfies Array<{ value: WorkflowDynamicBuilderFallbackStrategy; label: string }>

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

const inclusiveBranchMergePolicies = [
  { value: 'ALL_SELECTED', label: '所有命中的分支都保留' },
  { value: 'REQUIRED_COUNT', label: '命中指定数量后汇聚' },
  { value: 'DEFAULT_BRANCH', label: '优先默认分支汇聚' },
] satisfies Array<{ value: WorkflowInclusiveBranchMergePolicy; label: string }>

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

const escalationTargetModes = [
  { value: 'ROLE', label: '角色' },
  { value: 'USER', label: '指定人员' },
] satisfies Array<{ value: WorkflowEscalationTargetMode; label: string }>

const operationOptions = [
  { key: 'APPROVE', label: '通过' },
  { key: 'REJECT', label: '拒绝' },
  { key: 'RETURN', label: '退回' },
  { key: 'TRANSFER', label: '转办' },
] as const

const conditionTypeOptions = [
  { value: 'EXPRESSION', label: '手写表达式' },
  { value: 'FIELD', label: '字段比较' },
  { value: 'FORMULA', label: '安全公式' },
] satisfies Array<{ value: 'EXPRESSION' | 'FIELD' | 'FORMULA'; label: string }>

const branchSchema = z.object({
  edgeId: z.string(),
  label: z.string(),
  branchPriority: z.string(),
  conditionType: z.enum(['EXPRESSION', 'FIELD', 'FORMULA']),
  conditionExpression: z.string(),
  conditionFieldKey: z.string(),
  conditionOperator: z.enum(['EQ', 'NE', 'GT', 'GE', 'LT', 'LE']),
  conditionValue: z.string(),
  formulaExpression: z.string(),
})

const conditionOperators = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' },
  { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LE', label: '小于等于' },
] satisfies Array<{ value: WorkflowConditionOperator; label: string }>

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
      'supervise',
      'meeting',
      'read',
      'circulate',
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
        'FORMULA',
      ]),
      userIds: z.string(),
      roleCodes: z.string(),
      departmentRef: z.string(),
      formFieldKey: z.string(),
      formulaExpression: z.string(),
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
      escalationPolicy: z.object({
        enabled: z.boolean(),
        afterMinutes: z.string(),
        targetMode: z.enum(['USER', 'ROLE']),
        targetUserIds: z.string(),
        targetRoleCodes: z.string(),
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
      callScope: z.enum(['CHILD_ONLY', 'CHILD_AND_DESCENDANTS']),
      joinMode: z.enum(['AUTO_RETURN', 'WAIT_PARENT_CONFIRM']),
      childStartStrategy: z.enum(['LATEST_PUBLISHED', 'FIXED_VERSION', 'SCENE_BINDING']),
      parentResumeStrategy: z.enum(['AUTO_RETURN', 'WAIT_PARENT_CONFIRM']),
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
      sourceMode: z.enum(['RULE', 'MODEL_DRIVEN', 'MANUAL_TEMPLATE']),
      sceneCode: z.string(),
      executionStrategy: z.enum([
        'RULE_FIRST',
        'RULE_ONLY',
        'TEMPLATE_FIRST',
        'TEMPLATE_ONLY',
      ]),
      fallbackStrategy: z.enum([
        'KEEP_CURRENT',
        'USE_RULE',
        'USE_TEMPLATE',
        'SKIP_GENERATION',
      ]),
      ruleExpression: z.string(),
      manualTemplateCode: z.string(),
      targetMode: z.enum([
        'USER',
        'ROLE',
        'DEPARTMENT',
        'DEPARTMENT_AND_CHILDREN',
        'FORM_FIELD',
        'FORMULA',
      ]),
      userIds: z.string(),
      roleCodes: z.string(),
      departmentRef: z.string(),
      formFieldKey: z.string(),
      formulaExpression: z.string(),
      calledProcessKey: z.string(),
      calledVersionPolicy: z.enum(['LATEST_PUBLISHED', 'FIXED_VERSION']),
      calledVersion: z.string(),
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
        expressionMode: z.enum(['EXPRESSION', 'FIELD_COMPARE', 'FORMULA']),
        expressionFieldKey: z.string(),
        branches: z.array(branchSchema),
      }),
    inclusive: z.object({
      gatewayDirection: z.enum(['SPLIT', 'JOIN']),
      defaultBranchId: z.string(),
      requiredBranchCount: z.string(),
      branchMergePolicy: z.enum(['ALL_SELECTED', 'REQUIRED_COUNT', 'DEFAULT_BRANCH']),
      branches: z.array(branchSchema),
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
          message: '请选择部门',
          path: ['approver', 'departmentRef'],
        })
      }
      if (values.approver.assignmentMode === 'FORM_FIELD' && !values.approver.formFieldKey.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择表单字段',
          path: ['approver', 'formFieldKey'],
        })
      }
      if (values.approver.assignmentMode === 'FORMULA' && !values.approver.formulaExpression.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请输入自定义公式',
          path: ['approver', 'formulaExpression'],
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
        if (
          values.approver.assignmentMode === 'USER' &&
          parseListValue(values.approver.userIds).length < 2
        ) {
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

      if (values.approver.escalationPolicy.enabled) {
        if (!values.approver.escalationPolicy.afterMinutes.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '请输入升级分钟数',
            path: ['approver', 'escalationPolicy', 'afterMinutes'],
          })
        } else {
          const afterMinutes = Number(values.approver.escalationPolicy.afterMinutes)
          if (!Number.isFinite(afterMinutes) || afterMinutes <= 0) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '升级分钟数必须是大于 0 的数字',
              path: ['approver', 'escalationPolicy', 'afterMinutes'],
            })
          }
        }

        if (
          values.approver.escalationPolicy.targetMode === 'USER'
          && parseListValue(values.approver.escalationPolicy.targetUserIds).length === 0
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '请选择至少一个升级人员',
            path: ['approver', 'escalationPolicy', 'targetUserIds'],
          })
        }

        if (
          values.approver.escalationPolicy.targetMode === 'ROLE'
          && parseListValue(values.approver.escalationPolicy.targetRoleCodes).length === 0
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '请选择至少一个升级角色',
            path: ['approver', 'escalationPolicy', 'targetRoleCodes'],
          })
        }

        if (values.approver.escalationPolicy.channels.length === 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '请至少选择一个升级渠道',
            path: ['approver', 'escalationPolicy', 'channels'],
          })
        }
      }
    }

    if (isWorkflowCollaborationNodeKind(values.kind)) {
      if (values.cc.targetMode === 'USER' && !values.cc.userIds.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择至少一个协同接收人',
          path: ['cc', 'userIds'],
        })
      }
      if (values.cc.targetMode === 'ROLE' && !values.cc.roleCodes.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择至少一个协同角色',
          path: ['cc', 'roleCodes'],
        })
      }
      if (values.cc.targetMode === 'DEPARTMENT' && !values.cc.departmentRef.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '请选择协同部门',
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
        values.dynamicBuilder.sourceMode === 'MODEL_DRIVEN' &&
        !values.dynamicBuilder.sceneCode.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '模型驱动需要填写场景编码',
          path: ['dynamicBuilder', 'sceneCode'],
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

      const hasDynamicTaskFallbackConfig =
        values.dynamicBuilder.userIds.trim() ||
        values.dynamicBuilder.roleCodes.trim() ||
        values.dynamicBuilder.departmentRef.trim() ||
        values.dynamicBuilder.formFieldKey.trim() ||
        values.dynamicBuilder.formulaExpression.trim()

      if (values.dynamicBuilder.buildMode === 'APPROVER_TASKS' && hasDynamicTaskFallbackConfig) {
        if (
          values.dynamicBuilder.targetMode === 'USER' &&
          parseListValue(values.dynamicBuilder.userIds).length === 0
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '默认任务目标需要至少一个人员',
            path: ['dynamicBuilder', 'userIds'],
          })
        }
        if (
          values.dynamicBuilder.targetMode === 'ROLE' &&
          parseListValue(values.dynamicBuilder.roleCodes).length === 0
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '默认任务目标需要至少一个角色',
            path: ['dynamicBuilder', 'roleCodes'],
          })
        }
        if (
          ['DEPARTMENT', 'DEPARTMENT_AND_CHILDREN'].includes(values.dynamicBuilder.targetMode) &&
          !values.dynamicBuilder.departmentRef.trim()
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '默认任务目标需要选择部门',
            path: ['dynamicBuilder', 'departmentRef'],
          })
        }
        if (
          values.dynamicBuilder.targetMode === 'FORM_FIELD' &&
          !values.dynamicBuilder.formFieldKey.trim()
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '默认任务目标需要选择表单字段',
            path: ['dynamicBuilder', 'formFieldKey'],
          })
        }
        if (
          values.dynamicBuilder.targetMode === 'FORMULA' &&
          !values.dynamicBuilder.formulaExpression.trim()
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '默认任务目标需要填写自定义公式',
            path: ['dynamicBuilder', 'formulaExpression'],
          })
        }
      }

      if (
        values.dynamicBuilder.buildMode === 'SUBPROCESS_CALLS' &&
        values.dynamicBuilder.calledVersionPolicy === 'FIXED_VERSION' &&
        values.dynamicBuilder.calledProcessKey.trim() &&
        !values.dynamicBuilder.calledVersion.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '固定版本模式需要填写默认子流程版本号',
          path: ['dynamicBuilder', 'calledVersion'],
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

      values.condition.branches.forEach((branch, index) => {
        if (branch.edgeId === values.condition.defaultEdgeId) {
          return
        }
        if (branch.conditionType === 'EXPRESSION' && !branch.conditionExpression.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '非默认分支需要配置条件表达式',
            path: ['condition', 'branches', index, 'conditionExpression'],
          })
        }
        if (branch.conditionType === 'FIELD') {
          if (!branch.conditionFieldKey.trim()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '字段分支需要选择表单字段',
              path: ['condition', 'branches', index, 'conditionFieldKey'],
            })
          }
          if (!branch.conditionValue.trim()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '字段分支需要填写比较值',
              path: ['condition', 'branches', index, 'conditionValue'],
            })
          }
        }
        if (branch.conditionType === 'FORMULA' && !branch.formulaExpression.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '公式分支需要填写公式表达式',
            path: ['condition', 'branches', index, 'formulaExpression'],
          })
        }
      })
    }

    if (values.kind === 'inclusive' && values.inclusive.gatewayDirection === 'SPLIT') {
      if (values.inclusive.branches.length < 2) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '包容网关至少需要两条出边',
          path: ['inclusive', 'branches'],
        })
      }

      if (
        values.inclusive.branchMergePolicy === 'REQUIRED_COUNT' &&
        !values.inclusive.requiredBranchCount.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '命中指定数量模式需要填写必选分支数',
          path: ['inclusive', 'requiredBranchCount'],
        })
      } else if (values.inclusive.branchMergePolicy === 'REQUIRED_COUNT') {
        const requiredBranchCount = Number(values.inclusive.requiredBranchCount)
        if (
          !Number.isFinite(requiredBranchCount) ||
          requiredBranchCount <= 0 ||
          requiredBranchCount > values.inclusive.branches.length
        ) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '必选分支数必须是介于 1 和当前分支数之间的数字',
            path: ['inclusive', 'requiredBranchCount'],
          })
        }
      }

      if (
        values.inclusive.branchMergePolicy === 'DEFAULT_BRANCH' &&
        !values.inclusive.defaultBranchId.trim()
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '优先默认分支模式需要选择默认分支',
          path: ['inclusive', 'defaultBranchId'],
        })
      }

      if (
        values.inclusive.defaultBranchId.trim() &&
        !values.inclusive.branches.some((branch) => branch.edgeId === values.inclusive.defaultBranchId.trim())
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: '默认分支必须选择当前节点的出边',
          path: ['inclusive', 'defaultBranchId'],
        })
      }

      values.inclusive.branches.forEach((branch, index) => {
        if (!branch.branchPriority.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '包容网关每条出边都需要填写分支优先级',
            path: ['inclusive', 'branches', index, 'branchPriority'],
          })
        } else {
          const branchPriority = Number(branch.branchPriority)
          if (!Number.isFinite(branchPriority) || branchPriority <= 0) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '分支优先级必须是正整数',
              path: ['inclusive', 'branches', index, 'branchPriority'],
            })
          }
        }

        if (branch.conditionType === 'EXPRESSION' && !branch.conditionExpression.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '包容网关每条出边都需要配置条件表达式',
            path: ['inclusive', 'branches', index, 'conditionExpression'],
          })
        }
        if (branch.conditionType === 'FIELD') {
          if (!branch.conditionFieldKey.trim()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '包容网关字段分支需要选择表单字段',
              path: ['inclusive', 'branches', index, 'conditionFieldKey'],
            })
          }
          if (!branch.conditionValue.trim()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: '包容网关字段分支需要填写比较值',
              path: ['inclusive', 'branches', index, 'conditionValue'],
            })
          }
        }
        if (branch.conditionType === 'FORMULA' && !branch.formulaExpression.trim()) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: '包容网关公式分支需要填写公式表达式',
            path: ['inclusive', 'branches', index, 'formulaExpression'],
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

function parseFieldBindingsJson(json: string): WorkflowFieldBinding[] {
  try {
    const parsed = JSON.parse(json)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .map((binding) => binding as Partial<WorkflowFieldBinding>)
      .filter((binding): binding is Partial<WorkflowFieldBinding> => Boolean(binding))
      .map<WorkflowFieldBinding>((binding) => ({
        source: binding.source === 'NODE_FORM' ? 'NODE_FORM' : 'PROCESS_FORM',
        sourceFieldKey: binding.sourceFieldKey?.trim() ?? '',
        targetFieldKey: binding.targetFieldKey?.trim() ?? '',
      }))
      .filter((binding) => binding.sourceFieldKey && binding.targetFieldKey)
  } catch {
    return []
  }
}

function parseVariableMappingsJson(json: string): WorkflowSubprocessVariableMapping[] {
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

function serializeVariableMappings(bindings: WorkflowSubprocessVariableMapping[]) {
  return JSON.stringify(bindings, null, 2)
}

function VariableMappingEditor({
  label,
  description,
  value,
  onChange,
  sourceOptions,
  targetOptions,
  sourcePlaceholder,
  targetPlaceholder,
}: {
  label: string
  description?: string
  value: string
  onChange: (value: string) => void
  sourceOptions?: WorkflowProcessFormField[]
  targetOptions?: WorkflowProcessFormField[]
  sourcePlaceholder?: string
  targetPlaceholder?: string
}) {
  const mappings = useMemo(
    () => parseVariableMappingsJson(value),
    [value]
  )
  const sourceFieldOptions = useMemo(
    () =>
      sourceOptions?.map((field) => ({
        fieldKey: field.fieldKey,
        label: field.label,
        valueType: field.valueType,
      })) ?? [],
    [sourceOptions]
  )
  const targetFieldOptions = useMemo(
    () =>
      targetOptions?.map((field) => ({
        fieldKey: field.fieldKey,
        label: field.label,
        valueType: field.valueType,
      })) ?? [],
    [targetOptions]
  )

  function updateMappings(
    updater: (current: WorkflowSubprocessVariableMapping[]) => WorkflowSubprocessVariableMapping[]
  ) {
    onChange(serializeVariableMappings(updater(mappings)))
  }

  return (
    <div className='grid gap-3 rounded-xl border border-dashed p-3'>
      <div className='flex items-start justify-between gap-3'>
        <div className='grid gap-1'>
          <Label>{label}</Label>
          {description ? (
            <p className='text-xs text-muted-foreground'>{description}</p>
          ) : null}
        </div>
        <Button
          type='button'
          variant='secondary'
          size='sm'
          onClick={() =>
            updateMappings((current) => [
              ...current,
              { source: '', target: '' },
            ])
          }
        >
          <Plus className='mr-1 size-4' />
          新增映射
        </Button>
      </div>

      {mappings.length === 0 ? (
        <div className='rounded-lg bg-muted/40 px-3 py-2 text-xs text-muted-foreground'>
          暂未配置变量映射，子流程会默认继承当前流程变量。
        </div>
      ) : null}

      <div className='grid gap-3'>
        {mappings.map((mapping, index) => (
          <div
            key={`${label}-${index}`}
            className='grid gap-3 rounded-xl border bg-background p-3 md:grid-cols-[1fr_1fr_auto]'
          >
            <WorkflowFieldSelector
              label='来源字段'
              description='支持从流程表单字段直接映射。'
              value={mapping.source}
              onChange={(next) =>
                updateMappings((current) =>
                  current.map((item, itemIndex) =>
                    itemIndex === index ? { ...item, source: next } : item
                  )
                )
              }
              options={sourceFieldOptions}
              placeholder={sourcePlaceholder ?? '请输入来源变量或字段编码'}
            />
            <WorkflowFieldSelector
              label='目标字段'
              description='子流程变量名或父流程回填字段编码。'
              value={mapping.target}
              onChange={(next) =>
                updateMappings((current) =>
                  current.map((item, itemIndex) =>
                    itemIndex === index ? { ...item, target: next } : item
                  )
                )
              }
              options={targetFieldOptions}
              placeholder={targetPlaceholder ?? '请输入目标变量或字段编码'}
            />
            <div className='flex items-end justify-end'>
              <Button
                type='button'
                variant='ghost'
                size='icon'
                onClick={() =>
                  updateMappings((current) =>
                    current.filter((_, itemIndex) => itemIndex !== index)
                  )
                }
                aria-label={`删除映射 ${index + 1}`}
              >
                <Trash2 className='size-4' />
              </Button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function buildFormValues(node: WorkflowNode, edges: WorkflowEdge[]): NodeConfigFormValues {
  const outgoingEdges = edges.filter((edge) => edge.source === node.id)
  const config = node.data.config as Record<string, unknown>
  const dynamicBuilderConfig = config as Partial<WorkflowDynamicBuilderNodeConfig>
  const dynamicTargets = (dynamicBuilderConfig.targets ?? {}) as Partial<
    WorkflowDynamicBuilderNodeConfig['targets']
  >
  const approvalPolicy = (config.approvalPolicy ?? {}) as Record<string, unknown>
  const voteRule = (config.voteRule ?? {}) as Record<string, unknown>
  const timeoutPolicy = (config.timeoutPolicy ?? {}) as Record<string, unknown>
  const reminderPolicy = (config.reminderPolicy ?? {}) as Record<string, unknown>
  const escalationPolicy = (config.escalationPolicy ?? {}) as Record<string, unknown>
  const assignment = (config.assignment ?? {}) as Record<string, unknown>
  const targets = (config.targets ?? {}) as Record<string, unknown>
  const timerConfig = config as Record<string, unknown>
  const triggerConfig = config as Record<string, unknown>
  const branchDefaults = outgoingEdges.map((edge, index) => ({
    ...buildConditionFormDefaults(
      edge.id,
      typeof edge.label === 'string' ? edge.label : edge.id,
      edge.data?.condition
    ),
    branchPriority:
      typeof edge.data?.priority === 'number' && Number.isFinite(edge.data.priority)
        ? String(edge.data.priority)
        : String(index + 1),
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
      formulaExpression: String(assignment.formulaExpression ?? ''),
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
      escalationPolicy: {
        enabled: Boolean(escalationPolicy.enabled ?? false),
        afterMinutes:
          escalationPolicy.afterMinutes === null || escalationPolicy.afterMinutes === undefined
            ? ''
            : String(escalationPolicy.afterMinutes),
        targetMode: escalationPolicy.targetMode === 'USER' ? 'USER' : 'ROLE',
        targetUserIds: joinListValue(
          Array.isArray(escalationPolicy.targetUserIds)
            ? escalationPolicy.targetUserIds.map(String)
            : []
        ),
        targetRoleCodes: joinListValue(
          Array.isArray(escalationPolicy.targetRoleCodes)
            ? escalationPolicy.targetRoleCodes.map(String)
            : []
        ),
        channels: Array.isArray(escalationPolicy.channels)
          ? escalationPolicy.channels.filter(
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
      callScope: config.callScope === 'CHILD_AND_DESCENDANTS' ? 'CHILD_AND_DESCENDANTS' : 'CHILD_ONLY',
      joinMode: config.joinMode === 'WAIT_PARENT_CONFIRM' ? 'WAIT_PARENT_CONFIRM' : 'AUTO_RETURN',
      childStartStrategy:
        config.childStartStrategy === 'FIXED_VERSION' ||
        config.childStartStrategy === 'SCENE_BINDING'
          ? config.childStartStrategy
          : 'LATEST_PUBLISHED',
      parentResumeStrategy:
        config.parentResumeStrategy === 'WAIT_PARENT_CONFIRM'
          ? 'WAIT_PARENT_CONFIRM'
          : 'AUTO_RETURN',
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
      sourceMode:
        config.sourceMode === 'MANUAL_TEMPLATE' || config.sourceMode === 'MODEL_DRIVEN'
          ? config.sourceMode
          : 'RULE',
      sceneCode: String(config.sceneCode ?? ''),
      executionStrategy:
        config.executionStrategy === 'RULE_ONLY' ||
        config.executionStrategy === 'TEMPLATE_FIRST' ||
        config.executionStrategy === 'TEMPLATE_ONLY'
          ? config.executionStrategy
          : config.sourceMode === 'MANUAL_TEMPLATE' || config.sourceMode === 'MODEL_DRIVEN'
            ? 'TEMPLATE_FIRST'
            : 'RULE_FIRST',
      fallbackStrategy:
        config.fallbackStrategy === 'USE_RULE' ||
        config.fallbackStrategy === 'USE_TEMPLATE' ||
        config.fallbackStrategy === 'SKIP_GENERATION'
          ? config.fallbackStrategy
          : 'KEEP_CURRENT',
      ruleExpression: String(config.ruleExpression ?? ''),
      manualTemplateCode: String(config.manualTemplateCode ?? ''),
      targetMode:
        dynamicTargets.mode === 'ROLE' ||
        dynamicTargets.mode === 'DEPARTMENT' ||
        dynamicTargets.mode === 'DEPARTMENT_AND_CHILDREN' ||
        dynamicTargets.mode === 'FORM_FIELD' ||
        dynamicTargets.mode === 'FORMULA'
          ? dynamicTargets.mode
          : 'USER',
      userIds: joinListValue(
        Array.isArray(dynamicTargets.userIds) ? dynamicTargets.userIds.map(String) : []
      ),
      roleCodes: joinListValue(
        Array.isArray(dynamicTargets.roleCodes) ? dynamicTargets.roleCodes.map(String) : []
      ),
      departmentRef: String(dynamicTargets.departmentRef ?? ''),
      formFieldKey: String(dynamicTargets.formFieldKey ?? ''),
      formulaExpression: String(dynamicTargets.formulaExpression ?? ''),
      calledProcessKey: String(dynamicBuilderConfig.calledProcessKey ?? ''),
      calledVersionPolicy:
        dynamicBuilderConfig.calledVersionPolicy === 'FIXED_VERSION'
          ? 'FIXED_VERSION'
          : 'LATEST_PUBLISHED',
      calledVersion:
        dynamicBuilderConfig.calledVersion === null || dynamicBuilderConfig.calledVersion === undefined
          ? ''
          : String(dynamicBuilderConfig.calledVersion),
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
          ? (config.expressionMode as 'EXPRESSION' | 'FIELD_COMPARE' | 'FORMULA')
          : 'EXPRESSION',
      expressionFieldKey: String(config.expressionFieldKey ?? ''),
      branches: branchDefaults,
    },
    inclusive: {
      gatewayDirection: config.gatewayDirection === 'JOIN' ? 'JOIN' : 'SPLIT',
      defaultBranchId: String(config.defaultBranchId ?? ''),
      requiredBranchCount:
        config.requiredBranchCount === null || config.requiredBranchCount === undefined
          ? ''
          : String(config.requiredBranchCount),
      branchMergePolicy:
        config.branchMergePolicy === 'REQUIRED_COUNT' ||
        config.branchMergePolicy === 'DEFAULT_BRANCH'
          ? config.branchMergePolicy
          : 'ALL_SELECTED',
      branches: branchDefaults,
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
            formulaExpression: values.approver.formulaExpression.trim(),
          },
          nodeFormKey: values.approver.nodeFormKey.trim(),
          nodeFormVersion: values.approver.nodeFormVersion.trim(),
          fieldBindings: parseFieldBindingsJson(values.approver.fieldBindingsJson),
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
          escalationPolicy: {
            enabled: values.approver.escalationPolicy.enabled,
            afterMinutes: values.approver.escalationPolicy.enabled
              ? parseNumber(values.approver.escalationPolicy.afterMinutes)
              : null,
            targetMode: values.approver.escalationPolicy.targetMode,
            targetUserIds:
              values.approver.escalationPolicy.enabled
              && values.approver.escalationPolicy.targetMode === 'USER'
                ? parseListValue(values.approver.escalationPolicy.targetUserIds)
                : [],
            targetRoleCodes:
              values.approver.escalationPolicy.enabled
              && values.approver.escalationPolicy.targetMode === 'ROLE'
                ? parseListValue(values.approver.escalationPolicy.targetRoleCodes)
                : [],
            channels: values.approver.escalationPolicy.channels,
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
          callScope: values.subprocess.callScope,
          joinMode: values.subprocess.joinMode,
          childStartStrategy: values.subprocess.childStartStrategy,
          parentResumeStrategy: values.subprocess.parentResumeStrategy,
          businessBindingMode: values.subprocess.businessBindingMode,
          terminatePolicy: values.subprocess.terminatePolicy,
          childFinishPolicy: values.subprocess.childFinishPolicy,
          inputMappings: parseVariableMappingsJson(values.subprocess.inputMappingsJson),
          outputMappings: parseVariableMappingsJson(values.subprocess.outputMappingsJson),
        },
      }
    case 'dynamic-builder':
      return {
        config: {
          buildMode: values.dynamicBuilder.buildMode,
          sourceMode: values.dynamicBuilder.sourceMode,
          sceneCode: values.dynamicBuilder.sceneCode.trim(),
          executionStrategy: values.dynamicBuilder.executionStrategy,
          fallbackStrategy: values.dynamicBuilder.fallbackStrategy,
          ruleExpression: values.dynamicBuilder.ruleExpression.trim(),
          manualTemplateCode: values.dynamicBuilder.manualTemplateCode.trim(),
          targets: {
            mode: values.dynamicBuilder.targetMode,
            userIds: parseListValue(values.dynamicBuilder.userIds),
            roleCodes: parseListValue(values.dynamicBuilder.roleCodes),
            departmentRef: values.dynamicBuilder.departmentRef.trim(),
            formFieldKey: values.dynamicBuilder.formFieldKey.trim(),
            formulaExpression: values.dynamicBuilder.formulaExpression.trim(),
          },
          calledProcessKey: values.dynamicBuilder.calledProcessKey.trim(),
          calledVersionPolicy: values.dynamicBuilder.calledVersionPolicy,
          calledVersion:
            values.dynamicBuilder.calledVersionPolicy === 'FIXED_VERSION'
              ? parseNumber(values.dynamicBuilder.calledVersion)
              : null,
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
          defaultBranchId: values.inclusive.defaultBranchId.trim(),
          requiredBranchCount:
            values.inclusive.branchMergePolicy === 'REQUIRED_COUNT'
              ? parseNumber(values.inclusive.requiredBranchCount)
              : null,
          branchMergePolicy: values.inclusive.branchMergePolicy,
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
  processFormFields = [],
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
        priority?: number
      }>
    ) => void
  processFormFields?: WorkflowProcessFormField[]
}) {
  const outgoingEdges = useMemo(
    () => edges.filter((edge) => edge.source === node?.id),
    [edges, node?.id]
  )
  const processFieldOptions = useMemo(
    () =>
      processFormFields.map((field) => ({
        fieldKey: field.fieldKey,
        label: field.label,
        valueType: field.valueType,
      })),
    [processFormFields]
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
            formulaExpression: '',
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
            escalationPolicy: {
              enabled: false,
              afterMinutes: '',
              targetMode: 'ROLE',
              targetUserIds: '',
              targetRoleCodes: '',
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
            callScope: 'CHILD_ONLY',
            joinMode: 'AUTO_RETURN',
            childStartStrategy: 'LATEST_PUBLISHED',
            parentResumeStrategy: 'AUTO_RETURN',
            businessBindingMode: 'INHERIT_PARENT',
            terminatePolicy: 'TERMINATE_SUBPROCESS_ONLY',
            childFinishPolicy: 'RETURN_TO_PARENT',
            inputMappingsJson: '[]',
            outputMappingsJson: '[]',
          },
          dynamicBuilder: {
            buildMode: 'APPROVER_TASKS',
            sourceMode: 'RULE',
            sceneCode: '',
            executionStrategy: 'RULE_FIRST',
            fallbackStrategy: 'KEEP_CURRENT',
            ruleExpression: '',
            manualTemplateCode: '',
            targetMode: 'USER',
            userIds: '',
            roleCodes: '',
            departmentRef: '',
            formFieldKey: '',
            formulaExpression: '',
            calledProcessKey: '',
            calledVersionPolicy: 'LATEST_PUBLISHED',
            calledVersion: '',
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
            defaultBranchId: '',
            requiredBranchCount: '',
            branchMergePolicy: 'ALL_SELECTED',
            branches: [],
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
  const selectedEscalationEnabled = useWatch({
    control: form.control,
    name: 'approver.escalationPolicy.enabled',
  })
  const selectedEscalationTargetMode = useWatch({
    control: form.control,
    name: 'approver.escalationPolicy.targetMode',
  })
  const selectedApproverUserIds = useWatch({
    control: form.control,
    name: 'approver.userIds',
  })
  const selectedApproverRoleCodes = useWatch({
    control: form.control,
    name: 'approver.roleCodes',
  })
  const selectedApproverDepartmentRef = useWatch({
    control: form.control,
    name: 'approver.departmentRef',
  })
  const selectedDynamicBuilderSourceMode = useWatch({
    control: form.control,
    name: 'dynamicBuilder.sourceMode',
  })
  const selectedDynamicBuilderBuildMode = useWatch({
    control: form.control,
    name: 'dynamicBuilder.buildMode',
  })
  const selectedDynamicBuilderTargetMode = useWatch({
    control: form.control,
    name: 'dynamicBuilder.targetMode',
  })
  const selectedCcMode = useWatch({ control: form.control, name: 'cc.targetMode' })
  const selectedCcUserIds = useWatch({ control: form.control, name: 'cc.userIds' })
  const selectedCcRoleCodes = useWatch({ control: form.control, name: 'cc.roleCodes' })
  const selectedCcDepartmentRef = useWatch({ control: form.control, name: 'cc.departmentRef' })
  const selectedBranches = useWatch({
    control: form.control,
    name: 'condition.branches',
  })
  const selectedInclusiveDirection = useWatch({
    control: form.control,
    name: 'inclusive.gatewayDirection',
  })
  const selectedInclusiveMergePolicy = useWatch({
    control: form.control,
    name: 'inclusive.branchMergePolicy',
  })
  const selectedInclusiveBranches = useWatch({
    control: form.control,
    name: 'inclusive.branches',
  })
  const watchedValues = useWatch({ control: form.control })
  const selectedTriggerMode = useWatch({
    control: form.control,
    name: 'trigger.triggerMode',
  })
  const lastAppliedSignatureRef = useRef<string>('')
  const operationsError =
    (form.formState.errors.approver?.operations as { message?: string } | undefined)
      ?.message ?? ''

  useEffect(() => {
    if (!node) {
      lastAppliedSignatureRef.current = ''
      return
    }

    const nextValues = buildFormValues(node, edges)
    form.reset(nextValues)
    lastAppliedSignatureRef.current = JSON.stringify(nextValues)
  }, [edges, form, node])

  const applyValues = useCallback((values: NodeConfigFormValues) => {
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
                : branch.conditionType === 'FIELD'
                  ? {
                      type: 'FIELD' as const,
                      fieldKey: branch.conditionFieldKey.trim(),
                      operator: branch.conditionOperator,
                      value: branch.conditionValue.trim(),
                    }
                  : branch.conditionType === 'FORMULA'
                    ? {
                        type: 'FORMULA' as const,
                        expression: branch.formulaExpression.trim(),
                        formulaExpression: branch.formulaExpression.trim(),
                      }
                    : {
                        type: 'EXPRESSION' as const,
                        expression: branch.conditionExpression.trim(),
                      },
          }))
        : values.kind === 'inclusive' && values.inclusive.gatewayDirection === 'SPLIT'
          ? values.inclusive.branches.map((branch) => ({
              edgeId: branch.edgeId,
              label: branch.label.trim() || branch.edgeId,
              priority: parseNumber(branch.branchPriority) ?? undefined,
              condition:
                branch.conditionType === 'FIELD'
                  ? {
                      type: 'FIELD' as const,
                      fieldKey: branch.conditionFieldKey.trim(),
                      operator: branch.conditionOperator,
                      value: branch.conditionValue.trim(),
                    }
                  : branch.conditionType === 'FORMULA'
                    ? {
                        type: 'FORMULA' as const,
                        expression: branch.formulaExpression.trim(),
                        formulaExpression: branch.formulaExpression.trim(),
                      }
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
  }, [node, onApply])

  useEffect(() => {
    if (!node || !watchedValues || !form.formState.isDirty) {
      return
    }

    const nextSignature = JSON.stringify(watchedValues)
    if (nextSignature === lastAppliedSignatureRef.current) {
      return
    }

    const timer = window.setTimeout(async () => {
      const valid = await form.trigger(undefined, { shouldFocus: false })
      if (!valid) {
        return
      }
      const values = form.getValues()
      const signature = JSON.stringify(values)
      if (signature === lastAppliedSignatureRef.current) {
        return
      }
      applyValues(values)
      lastAppliedSignatureRef.current = signature
    }, 180)

    return () => window.clearTimeout(timer)
  }, [applyValues, form, node, watchedValues])

  if (!node) {
    return <div className='py-6' />
  }

  return (
    <Form {...form}>
      <form className='flex flex-col gap-4'>
        <section className='space-y-3'>
          <div className='flex items-center justify-between gap-3'>
            <div>
              <p className='text-sm font-semibold'>{node.data.label}</p>
              <p className='text-xs text-muted-foreground'>节点编码：{node.id}</p>
            </div>
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
        </section>

        {selectedKind === 'start' ? (
          <section className='space-y-3 border-t pt-4'>
            <div className='text-sm font-medium'>发起节点</div>
            <FormField
              control={form.control}
              name='start.initiatorEditable'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between gap-4 py-2'>
                  <div>
                    <FormLabel>发起人可编辑</FormLabel>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </section>
        ) : null}

        {selectedKind === 'subprocess' ? (
          <section className='space-y-4 border-t pt-4'>
            <div className='text-sm font-medium'>子流程节点</div>

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
                name='subprocess.callScope'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>调用范围</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择调用范围' />
                        </SelectTrigger>
                        <SelectContent>
                          {subprocessCallScopes.map((item) => (
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
                name='subprocess.joinMode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>父子协同方式</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择协同方式' />
                        </SelectTrigger>
                        <SelectContent>
                          {subprocessJoinModes.map((item) => (
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

            <div className='grid gap-4 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='subprocess.childStartStrategy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>子流程启动策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择启动策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {subprocessChildStartStrategies.map((item) => (
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
                name='subprocess.parentResumeStrategy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>父流程恢复策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择恢复策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {subprocessParentResumeStrategies.map((item) => (
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
                  <FormControl>
                    <VariableMappingEditor
                      label='输入映射'
                      description='把父流程字段按需映射到子流程变量，避免手写 JSON。'
                      value={field.value}
                      onChange={field.onChange}
                      sourceOptions={processFormFields}
                      targetOptions={processFormFields}
                      sourcePlaceholder='请选择父流程字段或输入变量编码'
                      targetPlaceholder='请输入子流程变量编码'
                    />
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
                  <FormControl>
                    <VariableMappingEditor
                      label='输出映射'
                      description='子流程完成后，把结果字段回填到父流程上下文。'
                      value={field.value}
                      onChange={field.onChange}
                      sourceOptions={processFormFields}
                      targetOptions={processFormFields}
                      sourcePlaceholder='请输入子流程结果字段编码'
                      targetPlaceholder='请选择父流程字段或输入变量编码'
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </section>
        ) : null}

        {selectedKind === 'dynamic-builder' ? (
          <section className='space-y-4 border-t pt-4'>
            <div className='text-sm font-medium'>动态构建节点</div>

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

            <div className='grid gap-4 rounded-xl border border-dashed p-3 md:grid-cols-2'>
              <FormField
                control={form.control}
                name='dynamicBuilder.sceneCode'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>场景编码</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder='leave_overtime_approval' />
                    </FormControl>
                    <p className='text-xs text-muted-foreground'>
                      用于区分不同动态构建场景，后续可映射成规则模板、模型能力或执行编排。
                    </p>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name='dynamicBuilder.executionStrategy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>执行策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择执行策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {dynamicBuilderExecutionStrategies.map((item) => (
                            <SelectItem key={item.value} value={item.value}>
                              {item.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </FormControl>
                    <p className='text-xs text-muted-foreground'>
                      决定优先走规则、模板还是预留的更深层策略。
                    </p>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name='dynamicBuilder.fallbackStrategy'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>回退策略</FormLabel>
                    <FormControl>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className='w-full'>
                          <SelectValue placeholder='请选择回退策略' />
                        </SelectTrigger>
                        <SelectContent>
                          {dynamicBuilderFallbackStrategies.map((item) => (
                            <SelectItem key={item.value} value={item.value}>
                              {item.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </FormControl>
                    <p className='text-xs text-muted-foreground'>
                      上游规则命中失败或模板缺失时的默认处理方式。
                    </p>
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
                    <FormControl>
                      <div className='grid gap-3 rounded-xl border border-dashed p-3'>
                        <WorkflowFormulaEditor
                          label='规则表达式'
                          description='根据表单字段和公式函数，决定运行时生成哪种附属结构。'
                          value={field.value}
                          onChange={field.onChange}
                          fieldOptions={processFieldOptions}
                          placeholder='例如：ifElse(days >= 5, "DIRECTOR_CHAIN", "HR_RECORD")'
                        />
                        <div className='flex flex-wrap gap-2'>
                          {[
                            'ifElse(days >= 3, "FINANCE_AND_HR", "HR_ONLY")',
                            'ifElse(leaveType == "ANNUAL", "DIRECTOR_CHAIN", "MANAGER_ONLY")',
                            'ifElse(amount >= 5000, "PURCHASE_REVIEW", "DIRECT_PASS")',
                          ].map((snippet, index) => (
                            <Button
                              key={snippet}
                              type='button'
                              variant='secondary'
                              size='sm'
                              onClick={() => field.onChange(snippet)}
                            >
                              规则模板 {index + 1}
                            </Button>
                          ))}
                        </div>
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedDynamicBuilderSourceMode === 'MODEL_DRIVEN' ? (
              <div className='grid gap-3 rounded-xl border border-dashed p-3'>
                <p className='text-sm font-medium'>模型驱动配置</p>
                <p className='text-xs text-muted-foreground'>
                  使用场景编码命中模型模板或知识驱动规则，执行策略建议优先走模板优先或仅模板。
                </p>
                <div className='flex flex-wrap gap-2'>
                  {[
                    'leave_auto_scene',
                    'expense_auto_scene',
                    'plm_change_scene',
                  ].map((sceneCode) => (
                    <Button
                      key={sceneCode}
                      type='button'
                      variant='secondary'
                      size='sm'
                      onClick={() =>
                        form.setValue('dynamicBuilder.sceneCode', sceneCode, {
                          shouldDirty: true,
                          shouldTouch: true,
                          shouldValidate: true,
                        })
                      }
                    >
                      {sceneCode}
                    </Button>
                  ))}
                </div>
              </div>
            ) : null}

            {selectedDynamicBuilderSourceMode === 'MANUAL_TEMPLATE' ? (
              <FormField
                control={form.control}
                name='dynamicBuilder.manualTemplateCode'
                render={({ field }) => (
                  <FormItem>
                    <FormControl>
                      <div className='grid gap-3 rounded-xl border border-dashed p-3'>
                        <div className='grid gap-2'>
                          <Label>模板编码</Label>
                          <Input {...field} placeholder='append_leave_audit' />
                          <p className='text-xs text-muted-foreground'>
                            人工模板适合固定附加链路，例如请假超过三天时追加 HR 备案。
                          </p>
                        </div>
                        <div className='flex flex-wrap gap-2'>
                          {[
                            'append_leave_chain',
                            'append_finance_review',
                            'append_hr_record',
                            'append_subprocess_leave_cancel',
                          ].map((templateCode) => (
                            <Button
                              key={templateCode}
                              type='button'
                              variant='secondary'
                              size='sm'
                              onClick={() => field.onChange(templateCode)}
                            >
                              {templateCode}
                            </Button>
                          ))}
                        </div>
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedDynamicBuilderBuildMode === 'APPROVER_TASKS' ? (
              <div className='grid gap-4 rounded-xl border border-dashed p-3'>
                <div className='grid gap-1'>
                  <p className='text-sm font-medium'>默认任务目标</p>
                  <p className='text-xs text-muted-foreground'>
                    规则未命中、保留当前或回退到规则时，按这里的目标生成附属任务。
                  </p>
                </div>

                <FormField
                  control={form.control}
                  name='dynamicBuilder.targetMode'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>目标来源</FormLabel>
                      <FormControl>
                        <Select value={field.value} onValueChange={field.onChange}>
                          <SelectTrigger className='w-full'>
                            <SelectValue placeholder='请选择目标来源' />
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

                {selectedDynamicBuilderTargetMode === 'USER' ? (
                  <FormField
                    control={form.control}
                    name='dynamicBuilder.userIds'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <WorkflowPrincipalPickerField
                            kind='USER'
                            label='默认人员'
                            description='附属任务默认发给这些人员。'
                            value={parseListValue(field.value)}
                            onChange={(next) => field.onChange(joinListValue(next))}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ) : null}

                {selectedDynamicBuilderTargetMode === 'ROLE' ? (
                  <FormField
                    control={form.control}
                    name='dynamicBuilder.roleCodes'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <WorkflowPrincipalPickerField
                            kind='ROLE'
                            label='默认角色'
                            description='运行时会按角色解析成真实用户。'
                            value={parseListValue(field.value)}
                            onChange={(next) => field.onChange(joinListValue(next))}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ) : null}

                {selectedDynamicBuilderTargetMode === 'DEPARTMENT' ||
                selectedDynamicBuilderTargetMode === 'DEPARTMENT_AND_CHILDREN' ? (
                  <FormField
                    control={form.control}
                    name='dynamicBuilder.departmentRef'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <WorkflowPrincipalPickerField
                            kind='DEPARTMENT'
                            label='默认部门'
                            description='运行时会按部门解析成启用中的用户。'
                            value={field.value ? [field.value] : []}
                            onChange={(next) => field.onChange(next[0] ?? '')}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ) : null}

                {selectedDynamicBuilderTargetMode === 'FORM_FIELD' ? (
                  <FormField
                    control={form.control}
                    name='dynamicBuilder.formFieldKey'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <WorkflowFieldSelector
                            label='默认任务字段来源'
                            description='从流程表单字段中解析附属任务目标。'
                            value={field.value}
                            onChange={field.onChange}
                            options={processFieldOptions}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ) : null}

                {selectedDynamicBuilderTargetMode === 'FORMULA' ? (
                  <FormField
                    control={form.control}
                    name='dynamicBuilder.formulaExpression'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <WorkflowFormulaEditor
                            label='默认任务公式'
                            description='公式返回用户 ID 或用户 ID 列表。'
                            value={field.value}
                            onChange={field.onChange}
                            fieldOptions={processFieldOptions}
                            placeholder='例如：ifElse(days >= 5, "usr_005", managerUserId)'
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ) : null}
              </div>
            ) : null}

            {selectedDynamicBuilderBuildMode === 'SUBPROCESS_CALLS' ? (
              <div className='grid gap-4 rounded-xl border border-dashed p-3 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='dynamicBuilder.calledProcessKey'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>默认子流程 Key</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder='oa_sub_review' />
                      </FormControl>
                      <p className='text-xs text-muted-foreground'>
                        规则或模板未命中时，用这个子流程作为默认附属链路。
                      </p>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='dynamicBuilder.calledVersionPolicy'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>默认子流程版本策略</FormLabel>
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
                  name='dynamicBuilder.calledVersion'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>默认子流程固定版本号</FormLabel>
                      <FormControl>
                        <Input
                          {...field}
                          type='number'
                          min='1'
                          placeholder='3'
                          disabled={form.getValues('dynamicBuilder.calledVersionPolicy') !== 'FIXED_VERSION'}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
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
          </section>
        ) : null}

        {selectedKind === 'approver' ? (
          <section className='space-y-4 border-t pt-4'>
            <div className='text-sm font-medium'>审批节点</div>

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
              <WorkflowPrincipalPickerField
                kind='USER'
                label='人员编码'
                value={parseListValue(selectedApproverUserIds)}
                onChange={(next) =>
                  form.setValue('approver.userIds', next.join(', '), {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  })
                }
              />
            ) : null}

            {selectedApproverMode === 'ROLE' ? (
              <WorkflowPrincipalPickerField
                kind='ROLE'
                label='角色编码'
                value={parseListValue(selectedApproverRoleCodes)}
                onChange={(next) =>
                  form.setValue('approver.roleCodes', next.join(', '), {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  })
                }
              />
            ) : null}

            {selectedApproverMode === 'DEPARTMENT' ||
            selectedApproverMode === 'DEPARTMENT_AND_CHILDREN' ? (
              <WorkflowPrincipalPickerField
                kind='DEPARTMENT'
                label='部门编码'
                value={parseListValue(selectedApproverDepartmentRef)}
                onChange={(next) =>
                  form.setValue('approver.departmentRef', next[0] ?? '', {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  })
                }
              />
            ) : null}

            {selectedApproverMode === 'FORM_FIELD' ? (
              <FormField
                control={form.control}
                name='approver.formFieldKey'
                render={({ field }) => (
                  <FormItem>
                    <WorkflowFieldSelector
                      label='表单字段编码'
                      value={field.value}
                      onChange={field.onChange}
                      options={processFieldOptions}
                    />
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : null}

            {selectedApproverMode === 'FORMULA' ? (
              <FormField
                control={form.control}
                name='approver.formulaExpression'
                render={({ field }) => (
                  <FormItem>
                    <WorkflowFormulaEditor
                      label='处理人公式'
                      value={field.value}
                      onChange={field.onChange}
                      fieldOptions={processFieldOptions}
                      placeholder='例如：days >= 5 ? "usr_004" : managerUserId'
                    />
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
                  <FormItem className='flex flex-row items-center justify-between gap-4 py-2'>
                    <div>
                      <FormLabel>自动结束剩余任务</FormLabel>
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
                <FormItem className='flex flex-row items-center justify-between gap-4 py-2'>
                  <div>
                    <FormLabel>超时审批</FormLabel>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            {selectedTimeoutEnabled ? (
              <div className='grid gap-4 border-l-2 border-muted pl-4'>
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
                <FormItem className='flex flex-row items-center justify-between gap-4 py-2'>
                  <div>
                    <FormLabel>自动提醒</FormLabel>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            {selectedReminderEnabled ? (
              <div className='grid gap-4 border-l-2 border-muted pl-4'>
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

            <FormField
              control={form.control}
              name='approver.escalationPolicy.enabled'
              render={({ field }) => (
                <FormItem className='flex flex-row items-center justify-between gap-4 py-2'>
                  <div>
                    <FormLabel>SLA 升级</FormLabel>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            {selectedEscalationEnabled ? (
              <div className='grid gap-4 border-l-2 border-muted pl-4'>
                <div className='grid gap-4 md:grid-cols-2'>
                  <FormField
                    control={form.control}
                    name='approver.escalationPolicy.afterMinutes'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>升级分钟数</FormLabel>
                        <FormControl>
                          <Input {...field} inputMode='numeric' placeholder='60' />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name='approver.escalationPolicy.targetMode'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>升级目标类型</FormLabel>
                        <FormControl>
                          <Select value={field.value} onValueChange={field.onChange}>
                            <SelectTrigger className='w-full'>
                              <SelectValue placeholder='请选择升级目标类型' />
                            </SelectTrigger>
                            <SelectContent>
                              {escalationTargetModes.map((item) => (
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
                {selectedEscalationTargetMode === 'USER' ? (
                  <FormField
                    control={form.control}
                    name='approver.escalationPolicy.targetUserIds'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <WorkflowPrincipalPickerField
                            kind='USER'
                            label='升级人员'
                            description='节点超时后，把升级提醒发送给这些人员。'
                            value={parseListValue(field.value)}
                            onChange={(next) => field.onChange(joinListValue(next))}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ) : (
                  <FormField
                    control={form.control}
                    name='approver.escalationPolicy.targetRoleCodes'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <WorkflowPrincipalPickerField
                            kind='ROLE'
                            label='升级角色'
                            description='节点超时后，会按角色解析成启用中的用户。'
                            value={parseListValue(field.value)}
                            onChange={(next) => field.onChange(joinListValue(next))}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                )}
                <div className='grid gap-2'>
                  <Label>升级渠道</Label>
                  <div className='grid gap-2 md:grid-cols-2'>
                    {reminderChannels.map((item) => (
                      <FormField
                        key={`escalation-${item.value}`}
                        control={form.control}
                        name='approver.escalationPolicy.channels'
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
                      <FormItem className='flex flex-row items-center gap-3 py-2'>
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
                <FormItem className='flex flex-row items-center justify-between gap-4 py-2'>
                  <div>
                    <FormLabel>强制填写审批意见</FormLabel>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />
          </section>
        ) : null}

        {isWorkflowCollaborationNodeKind(selectedKind) ? (
          <div className='flex flex-col gap-4 rounded-2xl border p-4'>
            <div className='flex items-center gap-2 text-sm font-medium'>
              <Check className='size-4 text-primary' />
              {resolveWorkflowCollaborationNodeLabel(selectedKind)}
            </div>

            <FormField
              control={form.control}
              name='cc.targetMode'
              render={({ field }) => (
                <FormItem>
                  <FormLabel>协同对象</FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className='w-full'>
                        <SelectValue placeholder='请选择协同对象' />
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
              <WorkflowPrincipalPickerField
                kind='USER'
                label='人员编码'
                description='支持多选，作为协同接收人。'
                value={parseListValue(selectedCcUserIds)}
                onChange={(next) =>
                  form.setValue('cc.userIds', next.join(', '), {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  })
                }
              />
            ) : null}

            {selectedCcMode === 'ROLE' ? (
              <WorkflowPrincipalPickerField
                kind='ROLE'
                label='角色编码'
                description='支持多选，选择角色后会自动展开为接收人。'
                value={parseListValue(selectedCcRoleCodes)}
                onChange={(next) =>
                  form.setValue('cc.roleCodes', next.join(', '), {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  })
                }
              />
            ) : null}

            {selectedCcMode === 'DEPARTMENT' ? (
              <WorkflowPrincipalPickerField
                kind='DEPARTMENT'
                label='部门编码'
                description='选择要协同的部门。'
                value={parseListValue(selectedCcDepartmentRef)}
                onChange={(next) =>
                  form.setValue('cc.departmentRef', next[0] ?? '', {
                    shouldDirty: true,
                    shouldTouch: true,
                    shouldValidate: true,
                  })
                }
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
                      协同后要求接收人确认已阅。
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
              排他网关
            </div>

            <div className='rounded-xl border bg-muted/20 px-3 py-2 text-xs text-muted-foreground'>
              排他网关支持按分支分别配置手写表达式、字段比较和安全公式，默认分支不需要填写条件。
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

                  {branch.edgeId !== form.getValues('condition.defaultEdgeId') ? (
                    <FormField
                      control={form.control}
                      name={`condition.branches.${index}.conditionType`}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>条件类型</FormLabel>
                          <FormControl>
                            <Select value={field.value} onValueChange={field.onChange}>
                              <SelectTrigger className='w-full'>
                                <SelectValue placeholder='请选择条件类型' />
                              </SelectTrigger>
                              <SelectContent>
                                {conditionTypeOptions.map((item) => (
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

                  <FormField
                    control={form.control}
                    name={`condition.branches.${index}.conditionExpression`}
                    render={({ field }) => (
                      <FormItem>
                        {branch.edgeId === form.getValues('condition.defaultEdgeId') ? (
                          <div className='rounded-xl border border-dashed p-3 text-xs text-muted-foreground'>
                            默认分支不需要配置条件，未命中其他分支时会自动进入这条路径。
                          </div>
                        ) : branch.conditionType === 'FIELD' ? (
                          <div className='grid gap-3'>
                            <FormLabel>比较值</FormLabel>
                            <Input
                              value={branch.conditionValue}
                              onChange={(event) =>
                                form.setValue(
                                  `condition.branches.${index}.conditionValue`,
                                  event.target.value,
                                  { shouldDirty: true, shouldTouch: true, shouldValidate: true }
                                )
                              }
                              placeholder='例如：10000'
                            />
                            <div className='grid gap-2 md:grid-cols-[1fr_160px]'>
                              <WorkflowFieldSelector
                                label='字段覆盖'
                                description='如需覆盖全局字段，可在这里重新选择。'
                                value={branch.conditionFieldKey}
                                onChange={(next) =>
                                  form.setValue(
                                    `condition.branches.${index}.conditionFieldKey`,
                                    next,
                                    { shouldDirty: true, shouldTouch: true, shouldValidate: true }
                                  )
                                }
                                options={processFieldOptions}
                              />
                              <FormField
                                control={form.control}
                                name={`condition.branches.${index}.conditionOperator`}
                                render={({ field: operatorField }) => (
                                  <FormItem>
                                    <FormLabel>比较符</FormLabel>
                                    <FormControl>
                                      <Select
                                        value={operatorField.value}
                                        onValueChange={operatorField.onChange}
                                      >
                                        <SelectTrigger className='w-full'>
                                          <SelectValue placeholder='请选择比较符' />
                                        </SelectTrigger>
                                        <SelectContent>
                                          {conditionOperators.map((item) => (
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
                          </div>
                        ) : branch.conditionType === 'FORMULA' ? (
                          <WorkflowFormulaEditor
                            label='安全公式'
                            description='支持字段引用、常量、比较/逻辑运算以及内置函数提示。'
                            value={branch.formulaExpression}
                            onChange={(next) =>
                              form.setValue(
                                `condition.branches.${index}.formulaExpression`,
                                next,
                                { shouldDirty: true, shouldTouch: true, shouldValidate: true }
                              )
                            }
                            fieldOptions={processFieldOptions}
                          />
                        ) : (
                          <FormControl>
                            <Textarea
                              {...field}
                              rows={3}
                              placeholder='例如：amount > 10000 && department == "FINANCE"'
                            />
                          </FormControl>
                        )}
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              ))}
              {selectedBranches.length === 0 ? (
                <div className='rounded-xl border border-dashed p-4 text-sm text-muted-foreground'>
                  当前排他网关没有出边，请先在画布上连出至少两条分支。
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
                    {selectedKind === 'inclusive' ? '包容网关' : '并行网关'}
                  </h3>
                  <p className='text-xs text-muted-foreground'>
                    {selectedKind === 'inclusive'
                      ? '包容网关分支时可命中多条条件路径，汇聚时等待已命中的路径回流。'
                      : '并行网关按 BPMN 语义只负责并发分支与汇聚，不支持条件表达式。'}
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

                {selectedKind === 'inclusive' && selectedInclusiveDirection === 'SPLIT' ? (
                  <>
                    <Separator />
                    <div className='grid gap-4 rounded-xl border bg-muted/20 p-4'>
                      <div className='grid gap-4 md:grid-cols-2'>
                        <FormField
                          control={form.control}
                          name='inclusive.branchMergePolicy'
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>分支汇聚策略</FormLabel>
                              <FormControl>
                                <Select value={field.value} onValueChange={field.onChange}>
                                  <SelectTrigger className='w-full'>
                                    <SelectValue placeholder='请选择汇聚策略' />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {inclusiveBranchMergePolicies.map((item) => (
                                      <SelectItem key={item.value} value={item.value}>
                                        {item.label}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              </FormControl>
                              <p className='text-xs text-muted-foreground'>
                                包容网关分支命中后如何回收与汇聚。
                              </p>
                              <FormMessage />
                            </FormItem>
                          )}
                        />

                        <FormField
                          control={form.control}
                          name='inclusive.defaultBranchId'
                          render={({ field }) => (
                            <FormItem>
                              <FormLabel>默认分支</FormLabel>
                              <FormControl>
                                <Select value={field.value} onValueChange={field.onChange}>
                                  <SelectTrigger className='w-full'>
                                    <SelectValue placeholder='请选择默认分支' />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {selectedInclusiveBranches.map((branch) => (
                                      <SelectItem key={branch.edgeId} value={branch.edgeId}>
                                        {branch.label || branch.edgeId}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              </FormControl>
                              <p className='text-xs text-muted-foreground'>
                                仅在默认分支汇聚策略下启用。
                              </p>
                              {field.value ? (
                                <Button
                                  type='button'
                                  variant='ghost'
                                  size='sm'
                                  className='px-0 text-left'
                                  onClick={() => field.onChange('')}
                                >
                                  清除默认分支
                                </Button>
                              ) : null}
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                      </div>

                      {selectedInclusiveMergePolicy === 'REQUIRED_COUNT' ? (
                        <div className='grid gap-4 md:grid-cols-2'>
                          <FormField
                            control={form.control}
                            name='inclusive.requiredBranchCount'
                            render={({ field }) => (
                              <FormItem>
                                <FormLabel>必选分支数</FormLabel>
                                <FormControl>
                                  <Input {...field} inputMode='numeric' placeholder='2' />
                                </FormControl>
                                <p className='text-xs text-muted-foreground'>
                                  命中指定数量后即可进入汇聚节点。
                                </p>
                                <FormMessage />
                              </FormItem>
                            )}
                          />
                          <div className='rounded-xl border border-dashed p-3 text-xs text-muted-foreground'>
                            当前分支总数：{selectedInclusiveBranches.length}
                          </div>
                        </div>
                      ) : null}
                    </div>

                    <div className='flex flex-col gap-4'>
                      {selectedInclusiveBranches.map((branch, index) => (
                        <div
                          key={branch.edgeId}
                          className='flex flex-col gap-3 rounded-xl border p-3 text-foreground'
                        >
                          <div className='flex items-start justify-between gap-3'>
                            <div>
                              <p className='text-sm font-medium'>条件分支</p>
                              <p className='text-xs text-muted-foreground'>连线：{branch.edgeId}</p>
                            </div>
                            <FormField
                              control={form.control}
                              name={`inclusive.branches.${index}.branchPriority`}
                              render={({ field }) => (
                                <FormItem className='w-32'>
                                  <FormLabel>分支优先级</FormLabel>
                                  <FormControl>
                                    <Input {...field} inputMode='numeric' placeholder='1' />
                                  </FormControl>
                                  <FormMessage />
                                </FormItem>
                              )}
                            />
                          </div>

                          <FormField
                            control={form.control}
                            name={`inclusive.branches.${index}.label`}
                            render={({ field }) => (
                              <FormItem>
                                <FormLabel>分支名称</FormLabel>
                                <FormControl>
                                  <Input {...field} placeholder='例如：金额超标且需要会签' />
                                </FormControl>
                                <FormMessage />
                              </FormItem>
                            )}
                          />

                          <FormField
                            control={form.control}
                            name={`inclusive.branches.${index}.conditionType`}
                            render={({ field }) => (
                              <FormItem>
                                <FormLabel>条件类型</FormLabel>
                                <FormControl>
                                  <Select value={field.value} onValueChange={field.onChange}>
                                    <SelectTrigger className='w-full'>
                                      <SelectValue placeholder='请选择条件类型' />
                                    </SelectTrigger>
                                    <SelectContent>
                                      {conditionTypeOptions.map((item) => (
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
                            name={`inclusive.branches.${index}.conditionExpression`}
                            render={({ field }) => (
                              <FormItem>
                                {branch.conditionType === 'FIELD' ? (
                                  <div className='grid gap-3'>
                                    <FormLabel>字段比较</FormLabel>
                                    <Input
                                      value={branch.conditionValue}
                                      onChange={(event) =>
                                        form.setValue(
                                          `inclusive.branches.${index}.conditionValue`,
                                          event.target.value,
                                          { shouldDirty: true, shouldTouch: true, shouldValidate: true }
                                        )
                                      }
                                      placeholder='例如：10000'
                                    />
                                    <div className='grid gap-2 md:grid-cols-[1fr_160px]'>
                                      <WorkflowFieldSelector
                                        label='表单字段'
                                        description='从流程表单里选择一个条件字段。'
                                        value={branch.conditionFieldKey}
                                        onChange={(next) =>
                                          form.setValue(
                                            `inclusive.branches.${index}.conditionFieldKey`,
                                            next,
                                            { shouldDirty: true, shouldTouch: true, shouldValidate: true }
                                          )
                                        }
                                        options={processFieldOptions}
                                      />
                                      <FormField
                                        control={form.control}
                                        name={`inclusive.branches.${index}.conditionOperator`}
                                        render={({ field: operatorField }) => (
                                          <FormItem>
                                            <FormLabel>比较符</FormLabel>
                                            <FormControl>
                                              <Select
                                                value={operatorField.value}
                                                onValueChange={operatorField.onChange}
                                              >
                                                <SelectTrigger className='w-full'>
                                                  <SelectValue placeholder='请选择比较符' />
                                                </SelectTrigger>
                                                <SelectContent>
                                                  {conditionOperators.map((item) => (
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
                                  </div>
                                ) : branch.conditionType === 'FORMULA' ? (
                                  <WorkflowFormulaEditor
                                    label='安全公式'
                                    description='支持字段引用、常量、比较/逻辑运算以及内置函数提示。'
                                    value={branch.formulaExpression}
                                    onChange={(next) =>
                                      form.setValue(
                                        `inclusive.branches.${index}.formulaExpression`,
                                        next,
                                        { shouldDirty: true, shouldTouch: true, shouldValidate: true }
                                      )
                                    }
                                    fieldOptions={processFieldOptions}
                                  />
                                ) : (
                                  <>
                                    <FormLabel>条件表达式</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        {...field}
                                        rows={3}
                                        placeholder='例如：amount > 10000 || urgent == true'
                                      />
                                    </FormControl>
                                  </>
                                )}
                                <FormMessage />
                              </FormItem>
                            )}
                          />
                        </div>
                      ))}
                      {selectedInclusiveBranches.length === 0 ? (
                        <div className='rounded-xl border border-dashed p-4 text-sm text-muted-foreground'>
                          当前包容网关没有出边，请先在画布上连出至少两条分支。
                        </div>
                      ) : null}
                    </div>
                  </>
                ) : null}
              </div>
            )}
          </div>
        ) : null}

      </form>
    </Form>
  )
}
