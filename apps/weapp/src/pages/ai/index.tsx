import { useEffect, useMemo, useRef, useState } from 'react'
import Taro from '@tarojs/taro'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Image, ScrollView, Text, Textarea, View } from '@tarojs/components'
import { AICopilotAttachmentItem } from '@westflow/shared-types'
import { GlassCard } from '../../components/GlassCard'
import { PageShell, cardStyle } from '../../components/PageShell'
import { useAuthGuard } from '../../hooks/use-auth-guard'
import {
  createAICopilotSession,
  getAICopilotSession,
  listAICopilotSessions,
  sendAICopilotMessage,
  transcribeAICopilotAudio,
  uploadAICopilotAsset,
} from '../../lib/api/ai'
import { getApiErrorMessage } from '../../lib/api/client'
import { colors } from '../../styles/theme'

type DraftAttachment = AICopilotAttachmentItem & {
  localUri?: string
}

export default function AiPage() {
  const ready = useAuthGuard()
  const queryClient = useQueryClient()
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [attachments, setAttachments] = useState<DraftAttachment[]>([])
  const [isRecording, setIsRecording] = useState(false)
  const recorderRef = useRef(Taro.getRecorderManager())
  const stopResolverRef = useRef<((path: string) => void) | null>(null)
  const stopRejectRef = useRef<((error: Error) => void) | null>(null)

  useEffect(() => {
    const recorder = recorderRef.current
    recorder.onStop((result) => {
      stopResolverRef.current?.(result.tempFilePath)
      stopResolverRef.current = null
      stopRejectRef.current = null
    })
    recorder.onError((error) => {
      stopRejectRef.current?.(new Error(error.errMsg))
      stopResolverRef.current = null
      stopRejectRef.current = null
    })
  }, [])

  const sessionsQuery = useQuery({
    queryKey: ['weapp', 'ai', 'sessions'],
    queryFn: listAICopilotSessions,
    enabled: ready,
  })

  const createSessionMutation = useMutation({
    mutationFn: () => createAICopilotSession({ title: '微信小程序会话' }),
    onSuccess: (session) => {
      setSelectedSessionId(session.sessionId)
      queryClient.setQueryData(['weapp', 'ai', 'session', session.sessionId], session)
      void queryClient.invalidateQueries({ queryKey: ['weapp', 'ai', 'sessions'] })
    },
  })

  useEffect(() => {
    if (!ready || createSessionMutation.isPending) return
    if (!selectedSessionId && sessionsQuery.data && sessionsQuery.data.length > 0) {
      setSelectedSessionId(sessionsQuery.data[0]!.sessionId)
      return
    }
    if (!selectedSessionId && sessionsQuery.data?.length === 0) {
      createSessionMutation.mutate()
    }
  }, [createSessionMutation, ready, selectedSessionId, sessionsQuery.data])

  const sessionQuery = useQuery({
    queryKey: ['weapp', 'ai', 'session', selectedSessionId],
    queryFn: () => getAICopilotSession(selectedSessionId!),
    enabled: ready && Boolean(selectedSessionId),
  })

  const sendMutation = useMutation({
    mutationFn: async () => {
      let sessionId = selectedSessionId
      if (!sessionId) {
        const session = await createAICopilotSession({ title: '微信小程序会话' })
        sessionId = session.sessionId
        setSelectedSessionId(sessionId)
      }
      return sendAICopilotMessage({
        sessionId,
        content: draft.trim(),
        attachments: attachments.map(({ localUri: _localUri, ...item }) => item),
      })
    },
    onSuccess: (session) => {
      setDraft('')
      setAttachments([])
      queryClient.setQueryData(['weapp', 'ai', 'session', session.sessionId], session)
      void queryClient.invalidateQueries({ queryKey: ['weapp', 'ai', 'sessions'] })
    },
    onError: (error) => {
      void Taro.showToast({ title: getApiErrorMessage(error), icon: 'none' })
    },
  })

  const session = sessionQuery.data
  const messages = useMemo(() => session?.history ?? [], [session?.history])
  const isBusy = createSessionMutation.isPending || sendMutation.isPending

  return (
    <PageShell title="AI Copilot" description="把问答、图片识别和语音输入收进一条轻量会话。">
      <GlassCard title="当前会话" description="先问一句，或者发一张图片让 AI 帮你识别并发起申请。">
        <ScrollView scrollY style={{ maxHeight: '680px' }}>
          <View style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {messages.map((message) => (
              <View
                key={message.messageId}
                style={{
                  ...cardStyle({
                    borderRadius: '24px',
                    background: message.role === 'user' ? 'rgba(226,233,247,0.86)' : colors.cardStrong,
                    marginLeft: message.role === 'user' ? '48px' : '0',
                    marginRight: message.role === 'assistant' ? '48px' : '0',
                  }),
                }}
              >
                <View style={{ display: 'flex', flexDirection: 'row', justifyContent: 'space-between', gap: '12px' }}>
                  <Text style={{ color: colors.text, fontSize: '12px', fontWeight: 700 }}>{message.authorName}</Text>
                  <Text style={{ color: colors.textMuted, fontSize: '12px' }}>{formatTime(message.createdAt)}</Text>
                </View>
                <Text style={{ color: colors.text, fontSize: '15px', lineHeight: '24px', marginTop: '8px', display: 'block' }}>
                  {message.content}
                </Text>
                {message.blocks?.map((block, index) => (
                  <View key={`${message.messageId}:${index}`} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '10px' }}>
                    {'body' in block && typeof block.body === 'string' ? (
                      <Text style={{ color: colors.textSecondary, fontSize: '13px', lineHeight: '20px' }}>{block.body}</Text>
                    ) : null}
                    {block.type === 'attachments'
                      ? block.items.map((item) => (
                          <AttachmentCard key={item.fileId} attachment={item} />
                        ))
                      : null}
                    {'fields' in block && Array.isArray(block.fields)
                      ? block.fields.map((field) => (
                          <Text key={field.label} style={{ color: colors.textSecondary, fontSize: '13px', lineHeight: '20px' }}>
                            {field.label}：{field.value}
                          </Text>
                        ))
                      : null}
                  </View>
                ))}
              </View>
            ))}
          </View>
        </ScrollView>
      </GlassCard>

      <GlassCard title="输入区" description="文字、图片和语音都可以继续补充到当前会话。">
        {attachments.length > 0 ? (
          <View style={{ display: 'flex', flexDirection: 'row', gap: '8px', flexWrap: 'wrap' }}>
            {attachments.map((item) => (
              <AttachmentCard key={item.fileId} attachment={item} />
            ))}
          </View>
        ) : null}

        <Textarea
          value={draft}
          onInput={(event) => setDraft(event.detail.value)}
          placeholder="继续提问，或先上传图片。"
          style={{
            width: '100%',
            minHeight: '140px',
            background: colors.cardStrong,
            border: `1px solid ${colors.cardBorder}`,
            borderRadius: '20px',
            padding: '14px',
            boxSizing: 'border-box',
          }}
        />

        <View style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap: '10px' }}>
          <ActionChip
            label="上传图片"
            onClick={async () => {
              const result = await Taro.chooseMedia({
                count: 1,
                mediaType: ['image'],
                sourceType: ['album', 'camera'],
              })
              const file = result.tempFiles[0]
              if (!file) return
              try {
                const uploaded = await uploadAICopilotAsset({
                  filePath: file.tempFilePath,
                  name: file.originalFileObj?.name || `image-${Date.now()}.png`,
                  mimeType: file.fileType ? `image/${file.fileType}` : 'image/png',
                })
                setAttachments((current) => [...current, { ...uploaded, localUri: file.tempFilePath }])
              } catch (error) {
                void Taro.showToast({ title: getApiErrorMessage(error), icon: 'none' })
              }
            }}
          />
          <ActionChip
            label={isRecording ? '结束录音' : '录音输入'}
            onClick={async () => {
              const recorder = recorderRef.current
              if (isRecording) {
                setIsRecording(false)
                try {
                  const path = await new Promise<string>((resolve, reject) => {
                    stopResolverRef.current = resolve
                    stopRejectRef.current = reject
                    recorder.stop()
                  })
                  const transcription = await transcribeAICopilotAudio({
                    filePath: path,
                    name: `weapp-recording-${Date.now()}.mp3`,
                    mimeType: 'audio/mpeg',
                  })
                  setDraft((current) => (current.trim() ? `${current.trim()}\n${transcription.text}` : transcription.text))
                } catch (error) {
                  void Taro.showToast({ title: getApiErrorMessage(error), icon: 'none' })
                }
                return
              }

              try {
                recorder.start({
                  duration: 60_000,
                  format: 'mp3',
                })
                setIsRecording(true)
              } catch (error) {
                void Taro.showToast({ title: getApiErrorMessage(error), icon: 'none' })
              }
            }}
          />
          <ActionChip
            label={isBusy ? '发送中…' : '发送'}
            primary
            onClick={() => {
              if (!draft.trim() && attachments.length === 0) return
              sendMutation.mutate()
            }}
          />
        </View>
      </GlassCard>
    </PageShell>
  )
}

function ActionChip({
  label,
  onClick,
  primary = false,
}: {
  label: string
  onClick: () => void
  primary?: boolean
}) {
  return (
    <View
      onClick={onClick}
      style={{
        padding: '12px 16px',
        borderRadius: '18px',
        background: primary ? colors.primary : colors.cardStrong,
        border: primary ? 'none' : `1px solid ${colors.cardBorder}`,
      }}
    >
      <Text style={{ color: primary ? '#FFFFFF' : colors.text, fontWeight: 700 }}>{label}</Text>
    </View>
  )
}

function AttachmentCard({ attachment }: { attachment: DraftAttachment | AICopilotAttachmentItem }) {
  const isImage = attachment.contentType.startsWith('image/')
  const localUri = 'localUri' in attachment ? attachment.localUri : undefined

  return (
    <View style={{ ...cardStyle({ width: '164px', borderRadius: '18px', padding: '10px' }) }}>
      {isImage && localUri ? (
        <Image
          src={localUri}
          mode="aspectFill"
          style={{ width: '100%', height: '120px', borderRadius: '14px', background: colors.bgSoft }}
        />
      ) : (
        <View style={{ width: '100%', height: '120px', borderRadius: '14px', background: colors.bgSoft, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ color: colors.textMuted, fontWeight: 700 }}>{isImage ? '图片' : '附件'}</Text>
        </View>
      )}
      <Text style={{ marginTop: '8px', color: colors.textSecondary, fontSize: '12px' }}>{attachment.displayName}</Text>
    </View>
  )
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
