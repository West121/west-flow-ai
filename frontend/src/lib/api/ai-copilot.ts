import { apiClient, getApiErrorResponse, unwrapResponse } from '@/lib/api/client'

// AI Copilot 前端契约层，改为真实 HTTP 资源客户端，不再保存本地内存 store。

export type AICopilotSessionStatus = 'active' | 'archived'

export type AICopilotMessageRole = 'user' | 'assistant' | 'system'

export type AICopilotConfirmationStatus =
  | 'pending'
  | 'confirmed'
  | 'cancelled'
  | 'failed'

export type AICopilotTextBlock = {
  type: 'text'
  title?: string
  body: string
}

export type AICopilotConfirmBlock = {
  type: 'confirm'
  confirmationId: string
  title: string
  summary: string
  detail?: string
  confirmLabel: string
  cancelLabel?: string
  status?: AICopilotConfirmationStatus
  resolvedAt?: string | null
  resolvedBy?: string | null
  resolutionNote?: string | null
  sourceType?: string
  sourceKey?: string
  sourceName?: string
  toolType?: string
  result?: Record<string, unknown>
  trace?: AICopilotTraceStep[]
  fields?: {
    label: string
    value: string
    hint?: string
  }[]
  metrics?: {
    label: string
    value: string
    hint?: string
    tone?: 'neutral' | 'positive' | 'warning'
  }[]
}

export type AICopilotFormPreviewBlock = {
  type: 'form-preview'
  title: string
  description?: string
  sourceType?: string
  sourceKey?: string
  sourceName?: string
  toolType?: string
  result?: Record<string, unknown>
  trace?: AICopilotTraceStep[]
  fields: {
    label: string
    value: string
    hint?: string
  }[]
}

export type AICopilotStatsBlock = {
  type: 'stats'
  title: string
  description?: string
  metrics: {
    label: string
    value: string
    hint?: string
    tone?: 'neutral' | 'positive' | 'warning'
  }[]
}

export type AICopilotTraceStep = {
  stage: string
  label: string
  detail?: string
  status?: string
}

export type AICopilotFailureInfo = {
  code: string
  message: string
  detail?: string
}

export type AICopilotResultBlock = {
  type: 'result'
  title: string
  summary?: string
  detail?: string
  status?: string
  sourceType?: string
  sourceKey?: string
  sourceName?: string
  toolType?: string
  result?: Record<string, unknown>
  trace?: AICopilotTraceStep[]
  fields?: {
    label: string
    value: string
    hint?: string
  }[]
  metrics?: {
    label: string
    value: string
    hint?: string
    tone?: 'neutral' | 'positive' | 'warning'
  }[]
}

export type AICopilotFailureBlock = {
  type: 'failure'
  title: string
  summary?: string
  detail?: string
  status?: string
  sourceType?: string
  sourceKey?: string
  sourceName?: string
  toolType?: string
  result?: Record<string, unknown>
  failure?: AICopilotFailureInfo
  trace?: AICopilotTraceStep[]
}

export type AICopilotRetryBlock = {
  type: 'retry'
  title: string
  summary?: string
  detail?: string
  status?: string
  sourceType?: string
  sourceKey?: string
  sourceName?: string
  toolType?: string
  result?: Record<string, unknown>
  trace?: AICopilotTraceStep[]
  fields?: {
    label: string
    value: string
    hint?: string
  }[]
  metrics?: {
    label: string
    value: string
    hint?: string
    tone?: 'neutral' | 'positive' | 'warning'
  }[]
}

export type AICopilotTraceBlock = {
  type: 'trace'
  title: string
  summary?: string
  detail?: string
  status?: string
  sourceType?: string
  sourceKey?: string
  sourceName?: string
  toolType?: string
  trace?: AICopilotTraceStep[]
}

export type AICopilotMessageBlock =
  | AICopilotTextBlock
  | AICopilotConfirmBlock
  | AICopilotFormPreviewBlock
  | AICopilotStatsBlock
  | AICopilotResultBlock
  | AICopilotFailureBlock
  | AICopilotRetryBlock
  | AICopilotTraceBlock

export type AICopilotMessage = {
  messageId: string
  role: AICopilotMessageRole
  authorName: string
  createdAt: string
  content: string
  blocks?: AICopilotMessageBlock[]
}

export type AICopilotToolCall = {
  toolCallId: string
  toolKey: string
  toolType: string
  toolSource: string
  status: string
  requiresConfirmation: boolean
  confirmationId?: string | null
  summary: string
  createdAt?: string | null
  completedAt?: string | null
}

export type AICopilotAuditEntry = {
  auditId: string
  conversationId: string
  toolCallId?: string | null
  actionType: string
  summary: string
  occurredAt: string
}

export type AICopilotSessionSummary = {
  sessionId: string
  title: string
  preview: string
  status: AICopilotSessionStatus
  updatedAt: string
  messageCount: number
  contextTags: string[]
}

export type AICopilotSession = AICopilotSessionSummary & {
  history: AICopilotMessage[]
  toolCalls: AICopilotToolCall[]
  audit: AICopilotAuditEntry[]
}

export type AICopilotSessionCreationInput = {
  title?: string
  contextTags?: string[]
}

export type AICopilotSendMessageInput = {
  sessionId: string
  content: string
}

export type AICopilotConfirmationDecision = 'confirm' | 'cancel'

export type AICopilotConfirmationInput = {
  sessionId: string
  confirmationId: string
  decision: AICopilotConfirmationDecision
  note?: string
  argumentsOverride?: Record<string, unknown>
}

type AICopilotApiResponse<T> = {
  code: 'OK'
  message: string
  data: T
  requestId: string
}

type BackendAICopilotMessageBlock = AICopilotMessageBlock

type BackendAICopilotMessage = {
  messageId: string
  role: AICopilotMessageRole
  authorName: string
  createdAt: string
  content: string
  blocks?: BackendAICopilotMessageBlock[]
}

type BackendAICopilotToolCall = {
  toolCallId: string
  toolKey: string
  toolType: string
  toolSource: string
  status: string
  requiresConfirmation: boolean
  confirmationId?: string | null
  summary: string
  createdAt?: string | null
  completedAt?: string | null
}

type BackendAICopilotAuditEntry = {
  auditId: string
  conversationId: string
  toolCallId?: string | null
  actionType: string
  summary: string
  occurredAt: string
}

type BackendAICopilotConversationSummary = {
  conversationId: string
  title: string
  preview: string
  status: AICopilotSessionStatus
  updatedAt: string
  messageCount: number
  contextTags: string[]
}

type BackendAICopilotConversationDetail = BackendAICopilotConversationSummary & {
  history: BackendAICopilotMessage[]
  toolCalls: BackendAICopilotToolCall[]
  audit: BackendAICopilotAuditEntry[]
}

type BackendAICopilotConversationPage = {
  records: BackendAICopilotConversationSummary[]
}

async function getAICopilotResource<T>(path: string): Promise<T | null> {
  try {
    const response = await apiClient.get<AICopilotApiResponse<T>>(path)
    return unwrapResponse(response)
  } catch (error) {
    const apiError = getApiErrorResponse(error)
    if (apiError?.code === 'NOT_FOUND') {
      return null
    }

    throw error
  }
}

async function postAICopilotResource<TPayload, TResponse>(
  path: string,
  payload: TPayload
): Promise<TResponse> {
  const response = await apiClient.post<AICopilotApiResponse<TResponse>>(
    path,
    payload
  )

  return unwrapResponse(response)
}

export async function listAICopilotSessions(): Promise<
  AICopilotSessionSummary[]
> {
  const response = await apiClient.post<
    AICopilotApiResponse<BackendAICopilotConversationPage>
  >('/ai/copilot/conversations/page', {
    page: 1,
    pageSize: 50,
    keyword: '',
    filters: [],
    sorts: [],
    groups: [],
  })

  return unwrapResponse(response).records.map(mapConversationSummaryToSession)
}

function mapConversationSummaryToSession(
  conversation: BackendAICopilotConversationSummary
): AICopilotSessionSummary {
  return {
    sessionId: conversation.conversationId,
    title: conversation.title,
    preview: conversation.preview,
    status: conversation.status,
    updatedAt: conversation.updatedAt,
    messageCount: conversation.messageCount,
    contextTags: conversation.contextTags ?? [],
  }
}

function mapConversationDetailToSession(
  conversation: BackendAICopilotConversationDetail
): AICopilotSession {
  return {
    ...mapConversationSummaryToSession(conversation),
    history: (conversation.history ?? []).map((message) => ({
      messageId: message.messageId,
      role: message.role,
      authorName: message.authorName,
      createdAt: message.createdAt,
      content: message.content,
      blocks: (message.blocks ?? []).map(enrichBusinessMessageBlock),
    })),
    toolCalls: (conversation.toolCalls ?? []).map((toolCall) => ({
      toolCallId: toolCall.toolCallId,
      toolKey: toolCall.toolKey,
      toolType: toolCall.toolType,
      toolSource: toolCall.toolSource,
      status: toolCall.status,
      requiresConfirmation: toolCall.requiresConfirmation,
      confirmationId: toolCall.confirmationId ?? null,
      summary: toolCall.summary,
      createdAt: toolCall.createdAt ?? null,
      completedAt: toolCall.completedAt ?? null,
    })),
    audit: (conversation.audit ?? []).map((auditEntry) => ({
      auditId: auditEntry.auditId,
      conversationId: auditEntry.conversationId,
      toolCallId: auditEntry.toolCallId ?? null,
      actionType: auditEntry.actionType,
      summary: auditEntry.summary,
      occurredAt: auditEntry.occurredAt,
    })),
  }
}

function enrichBusinessMessageBlock(
  block: BackendAICopilotMessageBlock
): BackendAICopilotMessageBlock {
  if (
    block.type !== 'result' &&
    block.type !== 'failure' &&
    block.type !== 'retry'
  ) {
    return block
  }

  if (!isTaskHandleBlock(block)) {
    return block
  }

  const taskHandleContext = resolveTaskHandleContext(block.result)
  const extraFields = [
    {
      label: '待办动作',
      value: taskHandleContext.actionLabel,
    },
    taskHandleContext.taskId
      ? {
          label: '待办编号',
          value: taskHandleContext.taskId,
        }
      : null,
    taskHandleContext.comment
      ? {
          label: '处理意见',
          value: taskHandleContext.comment,
        }
      : null,
    block.type === 'failure' && block.failure?.message
      ? {
          label: '失败原因',
          value: block.failure.message,
          hint: block.failure.detail,
        }
      : null,
    {
      label: '下一步建议',
      value: resolveTaskHandleNextStep(block.type, taskHandleContext.actionLabel),
    },
  ].filter(Boolean) as {
    label: string
    value: string
    hint?: string
  }[]

  return {
    ...block,
    ...(block.type === 'failure'
      ? {
          fields: extraFields,
        }
      : {
          fields: mergeFieldEntries(block.fields, extraFields),
          metrics:
            block.type === 'retry'
              ? mergeMetricEntries(block.metrics, [
                  {
                    label: '当前动作',
                    value: taskHandleContext.actionLabel,
                    tone: 'warning',
                  },
                ])
              : block.metrics,
        }),
  }
}

function isTaskHandleBlock(
  block: Extract<AICopilotMessageBlock, { type: 'result' | 'failure' | 'retry' }>
) {
  return (
    block.sourceKey === 'task.handle' ||
    block.sourceKey === 'workflow.task.complete' ||
    block.sourceKey === 'workflow.task.reject'
  )
}

function resolveTaskHandleContext(result?: Record<string, unknown>) {
  const rawArguments =
    result && typeof result.arguments === 'object' && result.arguments
      ? (result.arguments as Record<string, unknown>)
      : result ?? {}
  const action = String(rawArguments.action ?? result?.action ?? '').toUpperCase()
  const taskId = String(rawArguments.taskId ?? result?.taskId ?? '').trim()
  const comment = String(rawArguments.comment ?? result?.comment ?? '').trim()

  return {
    action,
    actionLabel: formatTaskHandleAction(action),
    taskId,
    comment,
  }
}

function formatTaskHandleAction(action: string) {
  switch (action) {
    case 'COMPLETE':
      return '完成待办'
    case 'REJECT':
      return '驳回待办'
    case 'CLAIM':
      return '认领待办'
    case 'READ':
      return '标记已阅'
    default:
      return '处理待办'
  }
}

function resolveTaskHandleNextStep(
  blockType: 'result' | 'failure' | 'retry',
  actionLabel: string
) {
  switch (blockType) {
    case 'result':
      return '返回工作台刷新状态或继续追问流程轨迹'
    case 'failure':
      return `先刷新待办状态并核对参数，再重新执行${actionLabel}`
    case 'retry':
      return `沿用当前参数，直接重新执行${actionLabel}`
  }
}

function mergeFieldEntries<
  T extends {
    label: string
    value: string
    hint?: string
  },
>(existing: T[] | undefined, appended: T[]) {
  const merged = [...(existing ?? [])]

  for (const field of appended) {
    if (!merged.some((current) => current.label === field.label)) {
      merged.push(field)
    }
  }

  return merged
}

function mergeMetricEntries<
  T extends {
    label: string
    value: string
    hint?: string
    tone?: 'neutral' | 'positive' | 'warning'
  },
>(existing: T[] | undefined, appended: T[]) {
  const merged = [...(existing ?? [])]

  for (const metric of appended) {
    if (!merged.some((current) => current.label === metric.label)) {
      merged.push(metric)
    }
  }

  return merged
}

async function getConversationDetail(
  conversationId: string
): Promise<BackendAICopilotConversationDetail | null> {
  return getAICopilotResource<BackendAICopilotConversationDetail>(
    `/ai/copilot/conversations/${conversationId}`
  )
}

export async function getAICopilotSession(
  sessionId: string
): Promise<AICopilotSession | null> {
  const detail = await getConversationDetail(sessionId)
  return detail ? mapConversationDetailToSession(detail) : null
}

export async function createAICopilotSession(
  input: AICopilotSessionCreationInput = {}
): Promise<AICopilotSession> {
  const detail = await postAICopilotResource<
    AICopilotSessionCreationInput,
    BackendAICopilotConversationDetail
  >('/ai/copilot/conversations', {
    title: input.title?.trim() || undefined,
    contextTags: input.contextTags?.filter(Boolean) ?? [],
  })

  return mapConversationDetailToSession(detail)
}

export async function deleteAICopilotSession(
  sessionId: string
): Promise<void> {
  await apiClient.delete<AICopilotApiResponse<{ conversationId: string }>>(
    `/ai/copilot/conversations/${sessionId}`
  )
}

export async function sendAICopilotMessage(
  input: AICopilotSendMessageInput
): Promise<AICopilotSession> {
  const detail = await postAICopilotResource<
    Pick<AICopilotSendMessageInput, 'content'>,
    BackendAICopilotConversationDetail
  >(`/ai/copilot/conversations/${input.sessionId}/messages`, {
    content: input.content,
  })

  return mapConversationDetailToSession(detail)
}

export async function confirmAICopilotConfirmation(
  input: AICopilotConfirmationInput
): Promise<AICopilotSession> {
  const detail = await getConversationDetail(input.sessionId)
  if (!detail) {
    throw new Error('AI 会话不存在')
  }

  const targetToolCall = detail.toolCalls.find(
    (toolCall) => toolCall.confirmationId === input.confirmationId
  )
  if (!targetToolCall) {
    throw new Error('未找到对应的确认卡')
  }

  await postAICopilotResource<
    { approved: boolean; comment?: string; argumentsOverride?: Record<string, unknown> },
    unknown
  >(`/ai/copilot/tool-calls/${targetToolCall.toolCallId}/confirm`, {
    approved: input.decision === 'confirm',
    comment: input.note,
    argumentsOverride: input.argumentsOverride,
  })

  const refreshed = await getConversationDetail(input.sessionId)
  if (!refreshed) {
    throw new Error('AI 会话不存在')
  }

  return mapConversationDetailToSession(refreshed)
}
