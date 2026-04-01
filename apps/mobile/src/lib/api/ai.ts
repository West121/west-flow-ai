import type {
  AICopilotAttachmentItem,
  AICopilotAudioTranscriptionResponse,
  AICopilotSession,
  AICopilotSessionSummary,
} from '@westflow/shared-types'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from '@/lib/api/client'

export async function listAICopilotSessions() {
  const response = await apiClient.post<
    ApiSuccessResponse<{ records: Array<Record<string, unknown>> }>
  >('/ai/copilot/conversations/page', {
    page: 1,
    pageSize: 50,
    keyword: '',
    filters: [],
    sorts: [],
    groups: [],
  })

  return unwrapResponse(response).records.map(mapSummary)
}

export async function getAICopilotSession(sessionId: string): Promise<AICopilotSession | null> {
  try {
    const response = await apiClient.get<ApiSuccessResponse<Record<string, unknown>>>(
      `/ai/copilot/conversations/${sessionId}`
    )
    return mapSession(unwrapResponse(response))
  } catch {
    return null
  }
}

export async function createAICopilotSession(input?: {
  title?: string
  contextTags?: string[]
}) {
  const response = await apiClient.post<ApiSuccessResponse<Record<string, unknown>>>(
    '/ai/copilot/conversations',
    {
      title: input?.title?.trim() || undefined,
      contextTags: input?.contextTags?.filter(Boolean) ?? [],
    }
  )
  return mapSession(unwrapResponse(response))
}

export async function sendAICopilotMessage(input: {
  sessionId: string
  content: string
  attachments?: AICopilotAttachmentItem[]
}) {
  const response = await apiClient.post<ApiSuccessResponse<Record<string, unknown>>>(
    `/ai/copilot/conversations/${input.sessionId}/messages`,
    {
      content: input.content,
      attachments: input.attachments ?? [],
    }
  )
  return mapSession(unwrapResponse(response))
}

export async function uploadAICopilotAsset(file: {
  uri: string
  name: string
  mimeType: string
}) {
  const formData = new FormData()
  formData.append('file', {
    uri: file.uri,
    name: file.name,
    type: file.mimeType,
  } as never)
  formData.append('displayName', file.name)

  const response = await apiClient.post<ApiSuccessResponse<AICopilotAttachmentItem>>(
    '/ai/copilot/assets',
    formData
  )
  return unwrapResponse(response)
}

export async function transcribeAICopilotAudio(file: {
  uri: string
  name: string
  mimeType: string
}) {
  const formData = new FormData()
  formData.append('file', {
    uri: file.uri,
    name: file.name,
    type: file.mimeType,
  } as never)

  const response = await apiClient.post<ApiSuccessResponse<AICopilotAudioTranscriptionResponse>>(
    '/ai/copilot/audio/transcriptions',
    formData
  )
  return unwrapResponse(response)
}

function mapSummary(value: Record<string, unknown>): AICopilotSessionSummary {
  return {
    sessionId: String(value.conversationId ?? value.sessionId ?? ''),
    title: String(value.title ?? '新会话'),
    preview: String(value.preview ?? ''),
    status: (value.status as AICopilotSessionSummary['status']) ?? 'active',
    updatedAt: String(value.updatedAt ?? new Date().toISOString()),
    messageCount: Number(value.messageCount ?? 0),
    contextTags: Array.isArray(value.contextTags)
      ? value.contextTags.map((item) => String(item))
      : [],
  }
}

function mapSession(value: Record<string, unknown>): AICopilotSession {
  const summary = mapSummary(value)
  const history = Array.isArray(value.history) ? value.history : []
  return {
    ...summary,
    history: history.map((message) => {
      const item = message as Record<string, unknown>
      return {
        messageId: String(item.messageId ?? ''),
        role: (item.role as 'user' | 'assistant' | 'system') ?? 'assistant',
        authorName: String(item.authorName ?? 'AI Copilot'),
        createdAt: String(item.createdAt ?? new Date().toISOString()),
        content: String(item.content ?? ''),
        blocks: Array.isArray(item.blocks)
          ? (item.blocks as AICopilotSession['history'][number]['blocks'])
          : [],
      }
    }),
    toolCalls: Array.isArray(value.toolCalls)
      ? value.toolCalls.map((toolCall) => {
          const item = toolCall as Record<string, unknown>
          return {
            toolCallId: String(item.toolCallId ?? ''),
            toolKey: String(item.toolKey ?? ''),
            toolType: String(item.toolType ?? ''),
            toolSource: String(item.toolSource ?? ''),
            status: String(item.status ?? ''),
            requiresConfirmation: Boolean(item.requiresConfirmation),
            confirmationId:
              item.confirmationId == null ? null : String(item.confirmationId),
            summary: String(item.summary ?? ''),
            createdAt:
              item.createdAt == null ? null : String(item.createdAt),
            completedAt:
              item.completedAt == null ? null : String(item.completedAt),
          }
        })
      : [],
    audit: Array.isArray(value.audit)
      ? value.audit.map((entry) => {
          const item = entry as Record<string, unknown>
          return {
            auditId: String(item.auditId ?? ''),
            conversationId: String(item.conversationId ?? summary.sessionId),
            toolCallId: item.toolCallId == null ? null : String(item.toolCallId),
            actionType: String(item.actionType ?? ''),
            summary: String(item.summary ?? ''),
            occurredAt: String(item.occurredAt ?? new Date().toISOString()),
          }
        })
      : [],
  }
}
