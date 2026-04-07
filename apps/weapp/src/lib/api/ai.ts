import {
  AICopilotAttachmentItem,
  AICopilotAudioTranscriptionResponse,
  AICopilotSession,
  AICopilotSessionSummary,
} from '@westflow/shared-types'
import { requestApi, uploadApiFile } from './client'

export function listAICopilotSessions() {
  return requestApi<{ records: Array<Record<string, unknown>> }>({
    path: '/ai/copilot/conversations/page',
    method: 'POST',
    data: {
      page: 1,
      pageSize: 50,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    },
  }).then((result) => result.records.map(mapSummary))
}

export async function getAICopilotSession(sessionId: string): Promise<AICopilotSession | null> {
  try {
    const result = await requestApi<Record<string, unknown>>({
      path: `/ai/copilot/conversations/${sessionId}`,
    })
    return mapSession(result)
  } catch {
    return null
  }
}

export function createAICopilotSession(input?: {
  title?: string
  contextTags?: string[]
}) {
  return requestApi<Record<string, unknown>>({
    path: '/ai/copilot/conversations',
    method: 'POST',
    data: {
      title: input?.title?.trim() || undefined,
      contextTags: input?.contextTags?.filter(Boolean) ?? [],
    },
  }).then(mapSession)
}

export function sendAICopilotMessage(input: {
  sessionId: string
  content: string
  attachments?: AICopilotAttachmentItem[]
}) {
  return requestApi<Record<string, unknown>>({
    path: `/ai/copilot/conversations/${input.sessionId}/messages`,
    method: 'POST',
    data: {
      content: input.content,
      attachments: input.attachments ?? [],
    },
  }).then(mapSession)
}

export function uploadAICopilotAsset(file: {
  filePath: string
  name: string
  mimeType?: string
}) {
  return uploadApiFile<AICopilotAttachmentItem>({
    path: '/ai/copilot/assets',
    filePath: file.filePath,
    name: file.name,
    mimeType: file.mimeType,
  })
}

export function transcribeAICopilotAudio(file: {
  filePath: string
  name: string
  mimeType?: string
}) {
  return uploadApiFile<AICopilotAudioTranscriptionResponse>({
    path: '/ai/copilot/audio/transcriptions',
    filePath: file.filePath,
    name: file.name,
    mimeType: file.mimeType,
  })
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
    toolCalls: [],
    audit: [],
  }
}
