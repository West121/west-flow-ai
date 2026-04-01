export type AICopilotSessionStatus = 'active' | 'archived'

export type AICopilotMessageRole = 'user' | 'assistant' | 'system'

export type AICopilotTraceStep = {
  stage: string
  label: string
  detail?: string
  status?: string
}

export type AICopilotAttachmentItem = {
  fileId: string
  displayName: string
  contentType: string
  previewUrl?: string
}

export type AICopilotAudioTranscriptionResponse = {
  text: string
}

export type AICopilotMessageBlock =
  | {
      type: 'text'
      title?: string
      body: string
    }
  | {
      type: 'attachments'
      title?: string
      items: AICopilotAttachmentItem[]
    }
  | {
      type: 'confirm'
      confirmationId: string
      title: string
      summary: string
      detail?: string
      confirmLabel: string
      cancelLabel?: string
      status?: 'pending' | 'confirmed' | 'cancelled' | 'failed'
      resolvedAt?: string | null
      resolvedBy?: string | null
      resolutionNote?: string | null
      result?: Record<string, unknown>
      trace?: AICopilotTraceStep[]
      fields?: { label: string; value: string; hint?: string }[]
      metrics?: {
        label: string
        value: string
        hint?: string
        tone?: 'neutral' | 'positive' | 'warning'
      }[]
    }
  | {
      type: 'form-preview'
      title: string
      description?: string
      result?: Record<string, unknown>
      trace?: AICopilotTraceStep[]
      fields: { label: string; value: string; hint?: string }[]
    }
  | {
      type: 'result' | 'failure' | 'retry' | 'trace'
      title: string
      summary?: string
      detail?: string
      status?: string
      result?: Record<string, unknown>
      trace?: AICopilotTraceStep[]
      fields?: { label: string; value: string; hint?: string }[]
      metrics?: {
        label: string
        value: string
        hint?: string
        tone?: 'neutral' | 'positive' | 'warning'
      }[]
      failure?: {
        code: string
        message: string
        detail?: string
      }
    }

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
