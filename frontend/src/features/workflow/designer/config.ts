import {
  type WorkflowApproverNodeConfig,
  type WorkflowCcNodeConfig,
  type WorkflowConditionNodeConfig,
  type WorkflowEdgeConditionType,
  type WorkflowFieldBinding,
  type WorkflowGatewayDirection,
  type WorkflowGatewayNodeConfig,
  type WorkflowReapprovePolicy,
  type WorkflowVoteWeight,
  type WorkflowNodeConfigMap,
  type WorkflowNodeKind,
  type WorkflowNodeTone,
  type WorkflowDynamicBuilderAppendPolicy,
  type WorkflowDynamicBuilderNodeConfig,
  type WorkflowDynamicBuilderSourceMode,
  type WorkflowDynamicBuilderTerminatePolicy,
  type WorkflowDynamicBuildMode,
  type WorkflowStartNodeConfig,
  type WorkflowReminderChannel,
  type WorkflowSubprocessNodeConfig,
  type WorkflowTimerNodeConfig,
  type WorkflowTriggerNodeConfig,
} from './types'

export type WorkflowEdgeCondition = {
  type: WorkflowEdgeConditionType
  expression: string
}

export type WorkflowNodeConfigRecord = {
  start: WorkflowStartNodeConfig
  approver: WorkflowApproverNodeConfig
  subprocess: WorkflowSubprocessNodeConfig
  dynamicBuilder: WorkflowDynamicBuilderNodeConfig
  condition: WorkflowConditionNodeConfig
  inclusive: WorkflowGatewayNodeConfig
  cc: WorkflowCcNodeConfig
  timer: WorkflowTimerNodeConfig
  trigger: WorkflowTriggerNodeConfig
  parallel: WorkflowGatewayNodeConfig
  end: Record<string, never>
}

const defaultApproverConfig: WorkflowApproverNodeConfig = {
  assignment: {
    mode: 'USER',
    userIds: ['usr_002'],
    roleCodes: [],
    departmentRef: '',
    formFieldKey: '',
  },
  nodeFormKey: '',
  nodeFormVersion: '',
  fieldBindings: [],
  approvalMode: 'SINGLE',
  voteRule: {
    thresholdPercent: null,
    passCondition: 'THRESHOLD_REACHED',
    rejectCondition: 'REJECT_THRESHOLD',
    weights: [],
  },
  reapprovePolicy: 'RESTART_ALL',
  autoFinishRemaining: false,
  approvalPolicy: {
    type: 'SEQUENTIAL',
    voteThreshold: null,
  },
  timeoutPolicy: {
    enabled: false,
    durationMinutes: null,
    action: 'APPROVE',
  },
  reminderPolicy: {
    enabled: false,
    firstReminderAfterMinutes: null,
    repeatIntervalMinutes: null,
    maxTimes: null,
    channels: ['IN_APP'],
  },
  operations: ['APPROVE', 'REJECT', 'RETURN'],
  commentRequired: false,
}

const defaultApproverVoteRule = defaultApproverConfig.voteRule ?? {
  thresholdPercent: null,
  passCondition: 'THRESHOLD_REACHED',
  rejectCondition: 'REJECT_THRESHOLD',
  weights: [],
}

const defaultCcConfig: WorkflowCcNodeConfig = {
  targets: {
    mode: 'USER',
    userIds: ['usr_003'],
    roleCodes: [],
    departmentRef: '',
  },
  readRequired: false,
}

const defaultConditionConfig: WorkflowConditionNodeConfig = {
  defaultEdgeId: '',
  expressionMode: 'EXPRESSION',
  expressionFieldKey: '',
}

const defaultTimerConfig: WorkflowTimerNodeConfig = {
  scheduleType: 'RELATIVE_TO_ARRIVAL',
  runAt: '',
  delayMinutes: 30,
  comment: '',
}

const defaultTriggerConfig: WorkflowTriggerNodeConfig = {
  triggerMode: 'IMMEDIATE',
  scheduleType: 'RELATIVE_TO_ARRIVAL',
  runAt: '',
  delayMinutes: 0,
  triggerKey: '',
  retryTimes: 0,
  retryIntervalMinutes: 5,
  payloadTemplate: '',
}

const defaultSubprocessConfig: WorkflowSubprocessNodeConfig = {
  calledProcessKey: '',
  calledVersionPolicy: 'LATEST_PUBLISHED',
  calledVersion: null,
  businessBindingMode: 'INHERIT_PARENT',
  terminatePolicy: 'TERMINATE_SUBPROCESS_ONLY',
  childFinishPolicy: 'RETURN_TO_PARENT',
  inputMappings: [],
  outputMappings: [],
}

const defaultDynamicBuilderConfig: WorkflowDynamicBuilderNodeConfig = {
  buildMode: 'APPROVER_TASKS',
  sourceMode: 'RULE',
  ruleExpression: '',
  manualTemplateCode: '',
  appendPolicy: 'SERIAL_AFTER_CURRENT',
  maxGeneratedCount: 1,
  terminatePolicy: 'TERMINATE_GENERATED_ONLY',
}

const defaultGatewayConfig: WorkflowGatewayNodeConfig = {
  gatewayDirection: 'SPLIT',
}

const defaultStartConfig: WorkflowStartNodeConfig = {
  initiatorEditable: true,
}

const emptyConfig = {}

// 字段绑定在持久化前统一清洗成可用结构。
function normalizeFieldBindings(value: unknown): WorkflowFieldBinding[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => item as Partial<WorkflowFieldBinding> | null | undefined)
    .filter((item): item is Partial<WorkflowFieldBinding> => Boolean(item))
    .map<WorkflowFieldBinding>((item) => ({
      source: item.source === 'NODE_FORM' ? 'NODE_FORM' : 'PROCESS_FORM',
      sourceFieldKey: item.sourceFieldKey ? String(item.sourceFieldKey) : '',
      targetFieldKey: item.targetFieldKey ? String(item.targetFieldKey) : '',
    }))
    .filter((item) => item.sourceFieldKey && item.targetFieldKey)
}

// 数值字段统一做空值兜底。
function normalizeNumber(value: unknown, fallback: number | null) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  return fallback
}

// 票签权重只保留正整数配置。
function normalizeVoteWeights(value: unknown): WorkflowVoteWeight[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => item as Partial<WorkflowVoteWeight> | null | undefined)
    .filter((item): item is Partial<WorkflowVoteWeight> => Boolean(item))
    .map<WorkflowVoteWeight>((item) => ({
      userId: item.userId ? String(item.userId) : '',
      weight:
        typeof item.weight === 'number' && Number.isFinite(item.weight)
          ? item.weight
          : Number.NaN,
    }))
    .filter((item) => item.userId && Number.isFinite(item.weight) && item.weight > 0)
}

// 重审策略只接受约定好的两种枚举值。
function normalizeReapprovePolicy(value: unknown): WorkflowReapprovePolicy {
  return value === 'CONTINUE_PROGRESS' ? 'CONTINUE_PROGRESS' : 'RESTART_ALL'
}

// 提醒渠道只保留支持的枚举值。
function normalizeReminderChannels(value: unknown): WorkflowReminderChannel[] {
  if (!Array.isArray(value)) {
    return [...defaultApproverConfig.reminderPolicy.channels]
  }

  const channels = value
    .map((item) => String(item))
    .filter(
      (item): item is WorkflowReminderChannel =>
        item === 'IN_APP' ||
        item === 'EMAIL' ||
        item === 'WEBHOOK' ||
        item === 'SMS' ||
        item === 'WECHAT' ||
        item === 'DINGTALK'
    )

  return channels.length > 0 ? channels : [...defaultApproverConfig.reminderPolicy.channels]
}

function normalizeDynamicBuilderBuildMode(
  value: unknown
): WorkflowDynamicBuildMode {
  return value === 'SUBPROCESS_CALLS' ? 'SUBPROCESS_CALLS' : 'APPROVER_TASKS'
}

function normalizeDynamicBuilderSourceMode(
  value: unknown
): WorkflowDynamicBuilderSourceMode {
  return value === 'MANUAL_TEMPLATE' ? 'MANUAL_TEMPLATE' : 'RULE'
}

function normalizeDynamicBuilderAppendPolicy(
  value: unknown
): WorkflowDynamicBuilderAppendPolicy {
  if (value === 'PARALLEL_WITH_CURRENT' || value === 'SERIAL_BEFORE_NEXT') {
    return value
  }

  return 'SERIAL_AFTER_CURRENT'
}

function normalizeDynamicBuilderTerminatePolicy(
  value: unknown
): WorkflowDynamicBuilderTerminatePolicy {
  return value === 'TERMINATE_PARENT_AND_GENERATED'
    ? 'TERMINATE_PARENT_AND_GENERATED'
    : 'TERMINATE_GENERATED_ONLY'
}

function normalizeGatewayDirection(value: unknown): WorkflowGatewayDirection {
  return value === 'JOIN' ? 'JOIN' : 'SPLIT'
}

// 根据节点类型返回默认配置，设计器初始化会用到。
export function defaultNodeConfig<K extends WorkflowNodeKind>(
  kind: K
): WorkflowNodeConfigMap[K] {
  switch (kind) {
    case 'start':
      return defaultStartConfig as WorkflowNodeConfigMap[K]
    case 'approver':
      return defaultApproverConfig as WorkflowNodeConfigMap[K]
    case 'subprocess':
      return defaultSubprocessConfig as WorkflowNodeConfigMap[K]
    case 'dynamic-builder':
      return defaultDynamicBuilderConfig as WorkflowNodeConfigMap[K]
    case 'condition':
      return defaultConditionConfig as WorkflowNodeConfigMap[K]
    case 'inclusive':
      return defaultGatewayConfig as WorkflowNodeConfigMap[K]
    case 'cc':
      return defaultCcConfig as WorkflowNodeConfigMap[K]
    case 'timer':
      return defaultTimerConfig as WorkflowNodeConfigMap[K]
    case 'trigger':
      return defaultTriggerConfig as WorkflowNodeConfigMap[K]
    case 'parallel':
      return defaultGatewayConfig as WorkflowNodeConfigMap[K]
    case 'end':
      return emptyConfig as WorkflowNodeConfigMap[K]
  }
}

// 节点语气决定画布里的视觉颜色。
export function toneForKind(kind: WorkflowNodeKind): WorkflowNodeTone {
  switch (kind) {
    case 'start':
      return 'success'
    case 'approver':
      return 'brand'
    case 'subprocess':
      return 'brand'
    case 'dynamic-builder':
      return 'brand'
    case 'condition':
      return 'warning'
    case 'inclusive':
      return 'warning'
    case 'timer':
      return 'warning'
    case 'trigger':
      return 'brand'
    default:
      return 'neutral'
  }
}

// 节点描述文案用于画布和表单提示。
export function descriptionForKind(kind: WorkflowNodeKind) {
  switch (kind) {
    case 'start':
      return '流程发起与表单提交入口'
    case 'approver':
      return '审批节点'
    case 'subprocess':
      return '主流程调用已发布子流程'
    case 'dynamic-builder':
      return '运行时动态生成审批链路'
    case 'condition':
      return '排他网关节点'
    case 'inclusive':
      return '包容分支节点'
    case 'cc':
      return '抄送节点'
    case 'timer':
      return '定时节点'
    case 'trigger':
      return '触发节点'
    case 'parallel':
      return '并行编排节点'
    case 'end':
      return '流程结束节点'
  }
}

// 节点标题优先用固定中文名，实在没有再回退原值。
export function labelForKind(kind: WorkflowNodeKind, fallback: string) {
  switch (kind) {
    case 'start':
      return '开始'
    case 'approver':
      return '审批'
    case 'subprocess':
      return '子流程'
    case 'dynamic-builder':
      return '动态构建'
    case 'condition':
      return '排他网关'
    case 'inclusive':
      return '包容分支'
    case 'cc':
      return '抄送'
    case 'timer':
      return '定时'
    case 'trigger':
      return '触发'
    case 'parallel':
      return '并行'
    case 'end':
      return '结束'
    default:
      return fallback
  }
}

// 保存前把节点配置清洗成统一结构。
export function normalizeNodeConfig<K extends WorkflowNodeKind>(
  kind: K,
  config: unknown
): WorkflowNodeConfigMap[K] {
  if (kind === 'start') {
    const value = config as Partial<WorkflowStartNodeConfig> | null | undefined
    return {
      initiatorEditable:
        typeof value?.initiatorEditable === 'boolean'
          ? value.initiatorEditable
          : defaultStartConfig.initiatorEditable,
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'parallel' || kind === 'inclusive') {
    const value = config as Partial<WorkflowGatewayNodeConfig> | null | undefined
    return {
      gatewayDirection: normalizeGatewayDirection(value?.gatewayDirection),
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'approver') {
    const value = config as Partial<WorkflowApproverNodeConfig> | null | undefined
    return {
      assignment: {
        mode: value?.assignment?.mode ?? defaultApproverConfig.assignment.mode,
        userIds: Array.isArray(value?.assignment?.userIds)
          ? value.assignment.userIds.map(String)
          : [...defaultApproverConfig.assignment.userIds],
        roleCodes: Array.isArray(value?.assignment?.roleCodes)
          ? value.assignment.roleCodes.map(String)
          : [...defaultApproverConfig.assignment.roleCodes],
        departmentRef: value?.assignment?.departmentRef
          ? String(value.assignment.departmentRef)
          : defaultApproverConfig.assignment.departmentRef,
        formFieldKey: value?.assignment?.formFieldKey
          ? String(value.assignment.formFieldKey)
          : defaultApproverConfig.assignment.formFieldKey,
      },
      nodeFormKey: value?.nodeFormKey ? String(value.nodeFormKey) : '',
      nodeFormVersion: value?.nodeFormVersion ? String(value.nodeFormVersion) : '',
      fieldBindings: normalizeFieldBindings(value?.fieldBindings),
      approvalMode:
        value?.approvalMode === 'SEQUENTIAL' ||
        value?.approvalMode === 'PARALLEL' ||
        value?.approvalMode === 'OR_SIGN' ||
        value?.approvalMode === 'VOTE'
          ? value.approvalMode
          : defaultApproverConfig.approvalMode,
      voteRule: {
        thresholdPercent: normalizeNumber(
          value?.voteRule?.thresholdPercent,
          defaultApproverVoteRule.thresholdPercent
        ),
        passCondition: value?.voteRule?.passCondition
          ? String(value.voteRule.passCondition)
          : defaultApproverVoteRule.passCondition,
        rejectCondition: value?.voteRule?.rejectCondition
          ? String(value.voteRule.rejectCondition)
          : defaultApproverVoteRule.rejectCondition,
        weights: normalizeVoteWeights(value?.voteRule?.weights),
      },
      reapprovePolicy: normalizeReapprovePolicy(value?.reapprovePolicy),
      autoFinishRemaining: Boolean(
        value?.autoFinishRemaining ?? defaultApproverConfig.autoFinishRemaining
      ),
      approvalPolicy: {
        type: value?.approvalPolicy?.type ?? defaultApproverConfig.approvalPolicy.type,
        voteThreshold:
          typeof value?.approvalPolicy?.voteThreshold === 'number'
            ? value.approvalPolicy.voteThreshold
            : value?.approvalPolicy?.voteThreshold === null
              ? null
              : defaultApproverConfig.approvalPolicy.voteThreshold,
      },
      timeoutPolicy: {
        enabled: Boolean(value?.timeoutPolicy?.enabled ?? defaultApproverConfig.timeoutPolicy.enabled),
        durationMinutes: normalizeNumber(
          value?.timeoutPolicy?.durationMinutes,
          defaultApproverConfig.timeoutPolicy.durationMinutes
        ),
        action:
          value?.timeoutPolicy?.action === 'REJECT'
            ? 'REJECT'
            : defaultApproverConfig.timeoutPolicy.action,
      },
      reminderPolicy: {
        enabled: Boolean(value?.reminderPolicy?.enabled ?? defaultApproverConfig.reminderPolicy.enabled),
        firstReminderAfterMinutes: normalizeNumber(
          value?.reminderPolicy?.firstReminderAfterMinutes,
          defaultApproverConfig.reminderPolicy.firstReminderAfterMinutes
        ),
        repeatIntervalMinutes: normalizeNumber(
          value?.reminderPolicy?.repeatIntervalMinutes,
          defaultApproverConfig.reminderPolicy.repeatIntervalMinutes
        ),
        maxTimes: normalizeNumber(
          value?.reminderPolicy?.maxTimes,
          defaultApproverConfig.reminderPolicy.maxTimes
        ),
        channels: normalizeReminderChannels(value?.reminderPolicy?.channels),
      },
      operations: Array.isArray(value?.operations) && value.operations.length > 0
        ? value.operations.map(String)
        : [...defaultApproverConfig.operations],
      commentRequired: Boolean(value?.commentRequired ?? defaultApproverConfig.commentRequired),
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'subprocess') {
    const value = config as Partial<WorkflowSubprocessNodeConfig> | null | undefined
    return {
      calledProcessKey: value?.calledProcessKey ? String(value.calledProcessKey) : '',
      calledVersionPolicy:
        value?.calledVersionPolicy === 'FIXED_VERSION'
          ? 'FIXED_VERSION'
          : defaultSubprocessConfig.calledVersionPolicy,
      calledVersion: normalizeNumber(value?.calledVersion, defaultSubprocessConfig.calledVersion),
      businessBindingMode:
        value?.businessBindingMode === 'OVERRIDE' ? 'OVERRIDE' : 'INHERIT_PARENT',
      terminatePolicy:
        value?.terminatePolicy === 'TERMINATE_PARENT_AND_SUBPROCESS'
          ? 'TERMINATE_PARENT_AND_SUBPROCESS'
          : defaultSubprocessConfig.terminatePolicy,
      childFinishPolicy:
        value?.childFinishPolicy === 'TERMINATE_PARENT'
          ? 'TERMINATE_PARENT'
          : defaultSubprocessConfig.childFinishPolicy,
      inputMappings: Array.isArray(value?.inputMappings)
        ? value.inputMappings
            .map((item) => item as { source?: unknown; target?: unknown })
            .map((item) => ({
              source: item.source ? String(item.source) : '',
              target: item.target ? String(item.target) : '',
            }))
            .filter((item) => item.source && item.target)
        : [],
      outputMappings: Array.isArray(value?.outputMappings)
        ? value.outputMappings
            .map((item) => item as { source?: unknown; target?: unknown })
            .map((item) => ({
              source: item.source ? String(item.source) : '',
              target: item.target ? String(item.target) : '',
            }))
            .filter((item) => item.source && item.target)
        : [],
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'dynamic-builder') {
    const value = config as Partial<WorkflowDynamicBuilderNodeConfig> | null | undefined
    return {
      buildMode: normalizeDynamicBuilderBuildMode(value?.buildMode),
      sourceMode: normalizeDynamicBuilderSourceMode(value?.sourceMode),
      ruleExpression: value?.ruleExpression ? String(value.ruleExpression) : '',
      manualTemplateCode: value?.manualTemplateCode ? String(value.manualTemplateCode) : '',
      appendPolicy: normalizeDynamicBuilderAppendPolicy(value?.appendPolicy),
      maxGeneratedCount: normalizeNumber(
        value?.maxGeneratedCount,
        defaultDynamicBuilderConfig.maxGeneratedCount
      ),
      terminatePolicy: normalizeDynamicBuilderTerminatePolicy(value?.terminatePolicy),
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'condition') {
    const value = config as Partial<WorkflowConditionNodeConfig> | null | undefined
    return {
      defaultEdgeId: value?.defaultEdgeId ? String(value.defaultEdgeId) : '',
      expressionMode:
        value?.expressionMode === 'FIELD_COMPARE'
          ? 'FIELD_COMPARE'
          : defaultConditionConfig.expressionMode,
      expressionFieldKey: value?.expressionFieldKey ? String(value.expressionFieldKey) : '',
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'cc') {
    const value = config as Partial<WorkflowCcNodeConfig> | null | undefined
    return {
      targets: {
        mode: value?.targets?.mode ?? defaultCcConfig.targets.mode,
        userIds: Array.isArray(value?.targets?.userIds)
          ? value.targets.userIds.map(String)
          : [...defaultCcConfig.targets.userIds],
        roleCodes: Array.isArray(value?.targets?.roleCodes)
          ? value.targets.roleCodes.map(String)
          : [...defaultCcConfig.targets.roleCodes],
        departmentRef: value?.targets?.departmentRef
          ? String(value.targets.departmentRef)
          : defaultCcConfig.targets.departmentRef,
      },
      readRequired: Boolean(value?.readRequired ?? defaultCcConfig.readRequired),
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'timer') {
    const value = config as Partial<WorkflowTimerNodeConfig> | null | undefined
    return {
      scheduleType:
        value?.scheduleType === 'ABSOLUTE_TIME'
          ? 'ABSOLUTE_TIME'
          : defaultTimerConfig.scheduleType,
      runAt: value?.runAt ? String(value.runAt) : defaultTimerConfig.runAt,
      delayMinutes: normalizeNumber(value?.delayMinutes, defaultTimerConfig.delayMinutes),
      comment: value?.comment ? String(value.comment) : defaultTimerConfig.comment,
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'trigger') {
    const value = config as Partial<WorkflowTriggerNodeConfig> | null | undefined
    return {
      triggerMode:
        value?.triggerMode === 'SCHEDULED'
          ? 'SCHEDULED'
          : defaultTriggerConfig.triggerMode,
      scheduleType:
        value?.scheduleType === 'ABSOLUTE_TIME'
          ? 'ABSOLUTE_TIME'
          : defaultTriggerConfig.scheduleType,
      runAt: value?.runAt ? String(value.runAt) : defaultTriggerConfig.runAt,
      delayMinutes: normalizeNumber(value?.delayMinutes, defaultTriggerConfig.delayMinutes),
      triggerKey: value?.triggerKey ? String(value.triggerKey) : defaultTriggerConfig.triggerKey,
      retryTimes: normalizeNumber(value?.retryTimes, defaultTriggerConfig.retryTimes),
      retryIntervalMinutes: normalizeNumber(
        value?.retryIntervalMinutes,
        defaultTriggerConfig.retryIntervalMinutes
      ),
      payloadTemplate: value?.payloadTemplate
        ? String(value.payloadTemplate)
        : defaultTriggerConfig.payloadTemplate,
    } as WorkflowNodeConfigMap[K]
  }

  return emptyConfig as WorkflowNodeConfigMap[K]
}

export function parseListValue(value: string) {
  return value
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function joinListValue(values: string[]) {
  return values.join(', ')
}

export function edgeConditionFromConfig(
  condition: unknown
): WorkflowEdgeCondition | undefined {
  const value = condition as Partial<WorkflowEdgeCondition> | null | undefined
  if (!value?.expression) {
    return undefined
  }
  return {
    type: value.type === 'EXPRESSION' ? value.type : 'EXPRESSION',
    expression: String(value.expression),
  }
}

export function normalizeEdgeCondition(condition: unknown) {
  return edgeConditionFromConfig(condition)
}

export function buildConditionFormDefaults(edgeId: string, expression = '', label = '') {
  return {
    edgeId,
    label,
    conditionExpression: expression,
  }
}
