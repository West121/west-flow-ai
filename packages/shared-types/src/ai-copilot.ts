export type AICopilotSessionStatus = 'active' | 'archived'

export type AICopilotMessageRole = 'user' | 'assistant' | 'system'

export type AICopilotConfirmationStatus =
  | 'pending'
  | 'confirmed'
  | 'cancelled'
  | 'failed'

export type AICopilotAttachmentItem = {
  fileId: string
  displayName: string
  contentType: string
  previewUrl?: string
}

export type AICopilotTraceStep = {
  stage: string
  label: string
  detail?: string
  status?: string
}

export type AICopilotTextBlock = {
  type: 'text'
  title?: string
  body: string
}

export type AICopilotAttachmentsBlock = {
  type: 'attachments'
  title?: string
  items: AICopilotAttachmentItem[]
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

export type AICopilotChartBlock = {
  type: 'chart'
  title: string
  summary?: string
  detail?: string
  result?: {
    chart?: {
      type?: 'bar' | 'line' | 'pie' | 'area' | 'donut' | 'table' | 'metric'
      title?: string
      description?: string
      xField?: string
      yField?: string
      metricLabel?: string
      valueLabel?: string
      value?: string | number
      columns?: {
        key: string
        label: string
      }[]
      series?: {
        dataKey: string
        name?: string
        color?: string
      }[]
    }
    data?: Record<string, unknown>[]
  }
  metrics?: {
    label: string
    value: string
    hint?: string
    tone?: 'neutral' | 'positive' | 'warning'
  }[]
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

export type AICopilotFailureInfo = {
  code: string
  message: string
  detail?: string
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
  | AICopilotAttachmentsBlock
  | AICopilotConfirmBlock
  | AICopilotFormPreviewBlock
  | AICopilotStatsBlock
  | AICopilotChartBlock
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
  attachments?: AICopilotAttachmentItem[]
}

export type AICopilotAssetUploadResponse = AICopilotAttachmentItem

export type AICopilotAudioTranscriptionResponse = {
  text: string
}

export type AICopilotConfirmationDecision = 'confirm' | 'cancel'

export type AICopilotConfirmationInput = {
  sessionId: string
  confirmationId: string
  decision: AICopilotConfirmationDecision
  note?: string
  argumentsOverride?: Record<string, unknown>
}
