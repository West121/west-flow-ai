import { useEffect, useMemo, useState } from 'react'
import * as DocumentPicker from 'expo-document-picker'
import { Audio } from 'expo-av'
import { Image } from 'expo-image'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  ActivityIndicator,
  LayoutChangeEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'
import { AppLoader } from '@/components/AppLoader'
import { ScreenShell } from '@/components/ScreenShell'
import { SectionCard } from '@/components/SectionCard'
import {
  createAICopilotSession,
  listAICopilotSessions,
  sendAICopilotMessage,
  transcribeAICopilotAudio,
  uploadAICopilotAsset,
  getAICopilotSession,
} from '@/lib/api/ai'
import { getApiErrorMessage, resolveApiUrl } from '@/lib/api/client'
import { useAuthStore } from '@/stores/auth-store'
import type { AICopilotAttachmentItem } from '@westflow/shared-types'

type DraftAttachment = AICopilotAttachmentItem & {
  localUri?: string
}

export function AICopilotScreen() {
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.currentUser)
  const accessToken = useAuthStore((state) => state.accessToken)
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [attachments, setAttachments] = useState<DraftAttachment[]>([])
  const [recording, setRecording] = useState<Audio.Recording | null>(null)
  const [isRecording, setIsRecording] = useState(false)
  const [messageWidth, setMessageWidth] = useState<number | null>(null)

  const sessionsQuery = useQuery({
    queryKey: ['mobile', 'ai', 'sessions'],
    queryFn: listAICopilotSessions,
    enabled: Boolean(currentUser),
  })

  const createSessionMutation = useMutation({
    mutationFn: () => createAICopilotSession({ title: '移动端会话' }),
    onSuccess: (session) => {
      setSelectedSessionId(session.sessionId)
      void queryClient.invalidateQueries({ queryKey: ['mobile', 'ai', 'sessions'] })
      queryClient.setQueryData(['mobile', 'ai', 'session', session.sessionId], session)
    },
  })

  useEffect(() => {
    if (!currentUser || createSessionMutation.isPending) {
      return
    }
    if (!selectedSessionId && sessionsQuery.data && sessionsQuery.data.length > 0) {
      setSelectedSessionId(sessionsQuery.data[0]!.sessionId)
      return
    }
    if (!selectedSessionId && sessionsQuery.data?.length === 0) {
      createSessionMutation.mutate()
    }
  }, [
    createSessionMutation,
    currentUser,
    selectedSessionId,
    sessionsQuery.data,
  ])

  const sessionQuery = useQuery({
    queryKey: ['mobile', 'ai', 'session', selectedSessionId],
    queryFn: () => getAICopilotSession(selectedSessionId!),
    enabled: Boolean(selectedSessionId),
  })

  const sendMutation = useMutation({
    mutationFn: async () => {
      let sessionId = selectedSessionId
      if (!sessionId) {
        const session = await createAICopilotSession({ title: '移动端会话' })
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
      queryClient.setQueryData(['mobile', 'ai', 'session', session.sessionId], session)
      void queryClient.invalidateQueries({ queryKey: ['mobile', 'ai', 'sessions'] })
    },
    onError: (error) => {
      Alert.alert('发送失败', getApiErrorMessage(error))
    },
  })

  const isBusy = createSessionMutation.isPending || sendMutation.isPending
  const session = sessionQuery.data
  const messages = useMemo(() => session?.history ?? [], [session?.history])

  if (!currentUser) {
    return (
      <ScreenShell title="AI Copilot" description="先登录后再进行问答、图片上传和 OCR 发起。">
        <SectionCard title="尚未登录">
          <Text style={styles.emptyText}>移动端 AI 与 Web 共用同一套会话和工具执行链。</Text>
        </SectionCard>
      </ScreenShell>
    )
  }

  return (
    <ScreenShell
      title="AI Copilot"
      description="移动端先接文本问答和图片/PDF 附件入口，复用现有后端 planner 与工具链。"
      scrollable={false}
    >
      <View style={styles.container}>
        <ScrollView style={styles.messages} contentContainerStyle={styles.messagesContent}>
          {sessionQuery.isLoading ? (
            <AppLoader message="正在加载会话…" />
          ) : (
            messages.map((message) => (
              <SectionCard
                key={message.messageId}
                compact
                title={`${message.authorName} · ${formatTime(message.createdAt)}`}
                onLayout={(event) => {
                  const width = event.nativeEvent.layout.width
                  if (width > 0 && width !== messageWidth) {
                    setMessageWidth(width)
                  }
                }}
              >
                <Text style={styles.messageText}>{message.content}</Text>
                {message.blocks?.map((block, index) => (
                  <View key={`${message.messageId}:${block.type}:${index}`} style={styles.block}>
                    {'body' in block && typeof block.body === 'string' ? (
                      <Text style={styles.blockText}>{block.body}</Text>
                    ) : null}
                    {block.type === 'attachments' ? (
                      (block.items ?? []).map((item) => (
                        <AttachmentPreviewCard
                          key={item.fileId}
                          attachment={item}
                          accessToken={accessToken}
                          width={messageWidth}
                        />
                      ))
                    ) : null}
                    {'fields' in block && Array.isArray(block.fields)
                      ? block.fields.map((field) => (
                          <Text key={field.label} style={styles.fieldText}>
                            {field.label}：{field.value}
                          </Text>
                        ))
                      : null}
                  </View>
                ))}
              </SectionCard>
            ))
          )}
        </ScrollView>

        <SectionCard compact title="发送消息">
          {attachments.length > 0 ? (
            <View style={styles.attachmentsRow}>
              {attachments.map((item) => (
                <AttachmentPreviewCard
                  key={item.fileId}
                  attachment={item}
                  accessToken={accessToken}
                  width={messageWidth}
                />
              ))}
            </View>
          ) : null}
          <TextInput
            value={draft}
            onChangeText={setDraft}
            placeholder="输入问题，或先上传图片/PDF"
            multiline
            style={styles.input}
          />
          <View style={styles.composerActions}>
            <Pressable
              style={styles.secondaryButton}
              onPress={async () => {
                const result = await DocumentPicker.getDocumentAsync({
                  type: ['image/*', 'application/pdf'],
                  copyToCacheDirectory: true,
                  multiple: false,
                })

                if (result.canceled || result.assets.length === 0) {
                  return
                }
                try {
                  const asset = result.assets[0]!
                  const uploaded = await uploadAICopilotAsset({
                    uri: asset.uri,
                    name: asset.name,
                    mimeType: asset.mimeType ?? 'application/octet-stream',
                  })
                  setAttachments((current) => [
                    ...current,
                    {
                      ...uploaded,
                      localUri: asset.uri,
                    },
                  ])
                } catch (error) {
                  Alert.alert('附件上传失败', getApiErrorMessage(error))
                }
              }}
            >
              <Text style={styles.secondaryButtonLabel}>上传图片/PDF</Text>
            </Pressable>
            <Pressable
              style={styles.secondaryButton}
              disabled={isRecording}
              onPress={async () => {
                const result = await DocumentPicker.getDocumentAsync({
                  type: ['audio/*'],
                  copyToCacheDirectory: true,
                  multiple: false,
                })

                if (result.canceled || result.assets.length === 0) {
                  return
                }
                try {
                  const asset = result.assets[0]!
                  const transcription = await transcribeAICopilotAudio({
                    uri: asset.uri,
                    name: asset.name,
                    mimeType: asset.mimeType ?? 'audio/mpeg',
                  })
                  setDraft((current) =>
                    current.trim()
                      ? `${current.trim()}\n${transcription.text}`
                      : transcription.text
                  )
                } catch (error) {
                  Alert.alert('音频转写失败', getApiErrorMessage(error))
                }
              }}
            >
              <Text style={styles.secondaryButtonLabel}>转写音频</Text>
            </Pressable>
            <Pressable
              style={[
                styles.secondaryButton,
                isRecording && styles.recordingButton,
              ]}
              disabled={sendMutation.isPending}
              onPress={async () => {
                if (isRecording && recording) {
                  try {
                    setIsRecording(false)
                    await recording.stopAndUnloadAsync()
                    const uri = recording.getURI()
                    setRecording(null)
                    if (!uri) {
                      throw new Error('录音文件不可用')
                    }
                    const transcription = await transcribeAICopilotAudio({
                      uri,
                      name: `mobile-recording-${Date.now()}.m4a`,
                      mimeType: 'audio/m4a',
                    })
                    setDraft((current) =>
                      current.trim()
                        ? `${current.trim()}\n${transcription.text}`
                        : transcription.text
                    )
                  } catch (error) {
                    setRecording(null)
                    setIsRecording(false)
                    Alert.alert('录音转写失败', getApiErrorMessage(error))
                  }
                  return
                }

                try {
                  const permission = await Audio.requestPermissionsAsync()
                  if (!permission.granted) {
                    Alert.alert('无法录音', '请先允许麦克风权限。')
                    return
                  }
                  await Audio.setAudioModeAsync({
                    allowsRecordingIOS: true,
                    playsInSilentModeIOS: true,
                  })
                  const nextRecording = new Audio.Recording()
                  await nextRecording.prepareToRecordAsync(
                    Audio.RecordingOptionsPresets.HIGH_QUALITY
                  )
                  await nextRecording.startAsync()
                  setRecording(nextRecording)
                  setIsRecording(true)
                } catch (error) {
                  setRecording(null)
                  setIsRecording(false)
                  Alert.alert('无法开始录音', getApiErrorMessage(error))
                }
              }}
            >
              <Text style={styles.secondaryButtonLabel}>
                {isRecording ? '结束录音' : '录音转写'}
              </Text>
            </Pressable>
            <Pressable
              disabled={isBusy || (!draft.trim() && attachments.length === 0)}
              style={[styles.primaryButton, isBusy && styles.primaryButtonDisabled]}
              onPress={() => sendMutation.mutate()}
            >
              <Text style={styles.primaryButtonLabel}>{isBusy ? '发送中…' : '发送'}</Text>
            </Pressable>
          </View>
        </SectionCard>
      </View>
    </ScreenShell>
  )
}

function AttachmentPreviewCard({
  attachment,
  accessToken,
  width,
}: {
  attachment: DraftAttachment | AICopilotAttachmentItem
  accessToken: string | null
  width: number | null
}) {
  const previewWidth = Math.max(160, Math.min((width ?? 260) - 24, 280))
  const isImage = attachment.contentType.startsWith('image/')
  const isPdf = attachment.contentType === 'application/pdf'
  const sourceUri =
    'localUri' in attachment && attachment.localUri
      ? attachment.localUri
      : attachment.previewUrl
        ? resolveApiUrl(attachment.previewUrl)
        : null

  if (!isImage) {
    return (
      <View style={[styles.attachmentCard, styles.attachmentFileCard, { width: previewWidth }]}>
        <Text style={styles.attachmentFileIcon}>{isPdf ? 'PDF' : '文件'}</Text>
        <Text style={styles.attachmentFileName} numberOfLines={2}>
          {attachment.displayName}
        </Text>
      </View>
    )
  }

  return (
    <View style={[styles.attachmentCard, { width: previewWidth }]}>
      {sourceUri ? (
        <Image
          source={
            accessToken && !('localUri' in attachment && attachment.localUri)
              ? { uri: sourceUri, headers: { Authorization: `Bearer ${accessToken}` } }
              : { uri: sourceUri }
          }
          style={styles.attachmentPreview}
          contentFit="cover"
          transition={150}
        />
      ) : (
        <View style={[styles.attachmentPreview, styles.attachmentPreviewFallback]}>
          <ActivityIndicator color="#8C8073" />
        </View>
      )}
      <Text style={styles.attachmentCaption} numberOfLines={1}>
        {attachment.displayName}
      </Text>
    </View>
  )
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    gap: 16,
  },
  messages: {
    flex: 1,
  },
  messagesContent: {
    gap: 12,
    paddingBottom: 12,
  },
  messageText: {
    color: '#171312',
    lineHeight: 22,
  },
  block: {
    gap: 6,
  },
  blockText: {
    color: '#473E37',
    lineHeight: 20,
  },
  attachmentText: {
    color: '#5C524A',
    fontSize: 13,
  },
  fieldText: {
    color: '#3E352E',
    lineHeight: 20,
  },
  attachmentsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  attachmentCard: {
    gap: 8,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#E3D8CA',
    backgroundColor: '#FFFDF9',
    padding: 10,
  },
  attachmentPreview: {
    width: '100%',
    height: 132,
    borderRadius: 14,
    backgroundColor: '#F6F1E7',
  },
  attachmentPreviewFallback: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  attachmentCaption: {
    color: '#4C433D',
    fontSize: 12,
    fontWeight: '600',
  },
  attachmentFileCard: {
    minHeight: 96,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F7F1E8',
  },
  attachmentFileIcon: {
    color: '#7B6E61',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 0.8,
  },
  attachmentFileName: {
    color: '#3C332D',
    fontSize: 13,
    fontWeight: '600',
    marginTop: 8,
    textAlign: 'center',
  },
  input: {
    minHeight: 96,
    borderWidth: 1,
    borderColor: '#D9CFBF',
    borderRadius: 18,
    backgroundColor: '#FFFFFF',
    padding: 14,
    textAlignVertical: 'top',
  },
  composerActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  primaryButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 16,
    backgroundColor: '#171312',
    paddingVertical: 14,
  },
  primaryButtonDisabled: {
    opacity: 0.72,
  },
  primaryButtonLabel: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  secondaryButton: {
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#D5CABC',
    paddingHorizontal: 14,
    paddingVertical: 14,
  },
  secondaryButtonLabel: {
    color: '#2A231F',
    fontWeight: '700',
  },
  recordingButton: {
    borderColor: '#D46A46',
    backgroundColor: '#FFF2ED',
  },
  emptyText: {
    color: '#75695E',
    lineHeight: 20,
  },
})
