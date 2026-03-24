import { type Edge, type Node } from '@xyflow/react'

// 工作流设计器只使用这几种基础节点。
export type WorkflowNodeKind =
  | 'start'
  | 'approver'
  | 'subprocess'
  | 'dynamic-builder'
  | 'condition'
  | 'inclusive'
  | 'cc'
  | 'timer'
  | 'trigger'
  | 'parallel'
  | 'end'

// 节点 tone 只控制视觉语气，不承载业务语义。
export type WorkflowNodeTone = 'brand' | 'success' | 'warning' | 'neutral'

export type WorkflowApproverAssignmentMode =
  | 'USER'
  | 'ROLE'
  | 'DEPARTMENT'
  | 'DEPARTMENT_AND_CHILDREN'
  | 'FORM_FIELD'
  | 'FORMULA'

export type WorkflowApproverApprovalPolicyType =
  | 'SINGLE'
  | 'SEQUENTIAL'
  | 'PARALLEL'
  | 'OR_SIGN'
  | 'VOTE'

export type WorkflowReapprovePolicy = 'RESTART_ALL' | 'CONTINUE_PROGRESS'

export type WorkflowVoteWeight = {
  userId: string
  weight: number
}

export type WorkflowVoteRule = {
  thresholdPercent: number | null
  passCondition: string
  rejectCondition: string
  weights: WorkflowVoteWeight[]
}

export type WorkflowTimeoutApprovalAction = 'APPROVE' | 'REJECT'
export type WorkflowReminderChannel =
  | 'IN_APP'
  | 'EMAIL'
  | 'WEBHOOK'
  | 'SMS'
  | 'WECHAT'
  | 'DINGTALK'
export type WorkflowTimerScheduleType = 'ABSOLUTE_TIME' | 'RELATIVE_TO_ARRIVAL'
export type WorkflowTriggerMode = 'IMMEDIATE' | 'SCHEDULED'
export type WorkflowSubprocessVersionPolicy = 'LATEST_PUBLISHED' | 'FIXED_VERSION'
export type WorkflowSubprocessBusinessBindingMode = 'INHERIT_PARENT' | 'OVERRIDE'
export type WorkflowSubprocessTerminatePolicy =
  | 'TERMINATE_SUBPROCESS_ONLY'
  | 'TERMINATE_PARENT_AND_SUBPROCESS'
export type WorkflowSubprocessChildFinishPolicy =
  | 'RETURN_TO_PARENT'
  | 'TERMINATE_PARENT'
export type WorkflowSubprocessCallScope =
  | 'CHILD_ONLY'
  | 'CHILD_AND_DESCENDANTS'
export type WorkflowSubprocessJoinMode =
  | 'AUTO_RETURN'
  | 'WAIT_PARENT_CONFIRM'
export type WorkflowSubprocessChildStartStrategy =
  | 'LATEST_PUBLISHED'
  | 'FIXED_VERSION'
  | 'SCENE_BINDING'
export type WorkflowSubprocessParentResumeStrategy =
  | 'AUTO_RETURN'
  | 'WAIT_PARENT_CONFIRM'

export type WorkflowDynamicBuildMode = 'APPROVER_TASKS' | 'SUBPROCESS_CALLS'
export type WorkflowDynamicBuilderSourceMode = 'RULE' | 'MANUAL_TEMPLATE'
export type WorkflowDynamicBuilderExecutionStrategy =
  | 'RULE_FIRST'
  | 'RULE_ONLY'
  | 'TEMPLATE_FIRST'
  | 'TEMPLATE_ONLY'
export type WorkflowDynamicBuilderFallbackStrategy =
  | 'KEEP_CURRENT'
  | 'USE_RULE'
  | 'USE_TEMPLATE'
  | 'SKIP_GENERATION'
export type WorkflowDynamicBuilderAppendPolicy =
  | 'SERIAL_AFTER_CURRENT'
  | 'PARALLEL_WITH_CURRENT'
  | 'SERIAL_BEFORE_NEXT'
export type WorkflowDynamicBuilderTerminatePolicy =
  | 'TERMINATE_GENERATED_ONLY'
  | 'TERMINATE_PARENT_AND_GENERATED'

export type WorkflowGatewayDirection = 'SPLIT' | 'JOIN'
export type WorkflowInclusiveBranchMergePolicy =
  | 'ALL_SELECTED'
  | 'REQUIRED_COUNT'
  | 'DEFAULT_BRANCH'

export type WorkflowDynamicBuilderNodeConfig = {
  buildMode: WorkflowDynamicBuildMode
  sourceMode: WorkflowDynamicBuilderSourceMode
  sceneCode: string
  executionStrategy: WorkflowDynamicBuilderExecutionStrategy
  fallbackStrategy: WorkflowDynamicBuilderFallbackStrategy
  ruleExpression: string
  manualTemplateCode: string
  appendPolicy: WorkflowDynamicBuilderAppendPolicy
  maxGeneratedCount: number | null
  terminatePolicy: WorkflowDynamicBuilderTerminatePolicy
}

export type WorkflowCcTargetMode = 'USER' | 'ROLE' | 'DEPARTMENT'
export type WorkflowConditionExpressionMode =
  | 'EXPRESSION'
  | 'FIELD_COMPARE'
  | 'FORMULA'
export type WorkflowFormFieldValueType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'date'
  | 'datetime'

export type WorkflowFieldBindingSource = 'PROCESS_FORM' | 'NODE_FORM'

// 字段绑定描述流程表单和节点表单之间的映射。
export type WorkflowFieldBinding = {
  source: WorkflowFieldBindingSource
  sourceFieldKey: string
  targetFieldKey: string
}

export type WorkflowConditionOperator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE'
export type WorkflowFormulaFunctionName =
  | 'ifElse'
  | 'contains'
  | 'daysBetween'
  | 'isBlank'

export type WorkflowProcessFormField = {
  fieldKey: string
  label: string
  valueType: WorkflowFormFieldValueType
}

export type WorkflowEdgeConditionType = 'EXPRESSION' | 'FIELD' | 'FORMULA'

export type WorkflowStartNodeConfig = {
  initiatorEditable: boolean
}

export type WorkflowApproverNodeConfig = {
  assignment: {
    mode: WorkflowApproverAssignmentMode
    userIds: string[]
    roleCodes: string[]
    departmentRef: string
    formFieldKey: string
    formulaExpression: string
  }
  nodeFormKey?: string
  nodeFormVersion?: string
  fieldBindings?: WorkflowFieldBinding[]
  approvalMode?: WorkflowApproverApprovalPolicyType
  voteRule?: WorkflowVoteRule
  reapprovePolicy?: WorkflowReapprovePolicy
  autoFinishRemaining?: boolean
  approvalPolicy: {
    type: WorkflowApproverApprovalPolicyType
    voteThreshold: number | null
  }
  timeoutPolicy: {
    enabled: boolean
    durationMinutes: number | null
    action: WorkflowTimeoutApprovalAction
  }
  reminderPolicy: {
    enabled: boolean
    firstReminderAfterMinutes: number | null
    repeatIntervalMinutes: number | null
    maxTimes: number | null
    channels: WorkflowReminderChannel[]
  }
  operations: string[]
  commentRequired: boolean
}

export type WorkflowTimerNodeConfig = {
  scheduleType: WorkflowTimerScheduleType
  runAt: string
  delayMinutes: number | null
  comment: string
}

export type WorkflowTriggerNodeConfig = {
  triggerMode: WorkflowTriggerMode
  scheduleType: WorkflowTimerScheduleType
  runAt: string
  delayMinutes: number | null
  triggerKey: string
  retryTimes: number | null
  retryIntervalMinutes: number | null
  payloadTemplate: string
}

export type WorkflowSubprocessVariableMapping = {
  source: string
  target: string
}

export type WorkflowSubprocessNodeConfig = {
  calledProcessKey: string
  calledVersionPolicy: WorkflowSubprocessVersionPolicy
  calledVersion: number | null
  callScope: WorkflowSubprocessCallScope
  joinMode: WorkflowSubprocessJoinMode
  childStartStrategy: WorkflowSubprocessChildStartStrategy
  parentResumeStrategy: WorkflowSubprocessParentResumeStrategy
  businessBindingMode: WorkflowSubprocessBusinessBindingMode
  terminatePolicy: WorkflowSubprocessTerminatePolicy
  childFinishPolicy: WorkflowSubprocessChildFinishPolicy
  inputMappings: WorkflowSubprocessVariableMapping[]
  outputMappings: WorkflowSubprocessVariableMapping[]
}

export type WorkflowConditionNodeConfig = {
  defaultEdgeId: string
  expressionMode?: WorkflowConditionExpressionMode
  expressionFieldKey?: string
}

export type WorkflowCcNodeConfig = {
  targets: {
    mode: WorkflowCcTargetMode
    userIds: string[]
    roleCodes: string[]
    departmentRef: string
  }
  readRequired: boolean
}

export type WorkflowInclusiveGatewayNodeConfig = {
  gatewayDirection: WorkflowGatewayDirection
  defaultBranchId?: string
  requiredBranchCount?: number | null
  branchMergePolicy?: WorkflowInclusiveBranchMergePolicy
}

export type WorkflowParallelGatewayNodeConfig = {
  gatewayDirection: WorkflowGatewayDirection
}

// 每种节点类型都对应一套独立配置结构。
export type WorkflowNodeConfigMap = {
  start: WorkflowStartNodeConfig
  approver: WorkflowApproverNodeConfig
  subprocess: WorkflowSubprocessNodeConfig
  'dynamic-builder': WorkflowDynamicBuilderNodeConfig
  condition: WorkflowConditionNodeConfig
  inclusive: WorkflowInclusiveGatewayNodeConfig
  cc: WorkflowCcNodeConfig
  timer: WorkflowTimerNodeConfig
  trigger: WorkflowTriggerNodeConfig
  parallel: WorkflowParallelGatewayNodeConfig
  end: Record<string, never>
}

export type WorkflowNodeData<K extends WorkflowNodeKind = WorkflowNodeKind> = {
  kind: K
  label: string
  description: string
  tone: WorkflowNodeTone
  config: WorkflowNodeConfigMap[K]
}

export type WorkflowNode = Node<WorkflowNodeData, 'workflow'>
export type WorkflowEdgeData = {
  condition?: {
    type: WorkflowEdgeConditionType
    expression?: string
    fieldKey?: string
    operator?: WorkflowConditionOperator
    value?: string | number | boolean | null
    formulaExpression?: string
  }
  priority?: number
}

export type WorkflowEdge = Edge<WorkflowEdgeData>

// 画布快照是设计器历史、保存和发布的基础数据。
export type WorkflowSnapshot = {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  selectedNodeId: string | null
}

// 辅助线状态只保存横竖两条对齐线的位置。
export type WorkflowHelperLines = {
  vertical: number | null
  horizontal: number | null
}
