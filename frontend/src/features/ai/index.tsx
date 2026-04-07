import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  Trash2,
  ArrowRight,
  BadgeCheck,
  BarChart3,
  Bot,
  CheckCircle2,
  Check,
  CircleAlert,
  Clock3,
  Copy,
  FileText,
  GitBranch,
  ImagePlus,
  Mic,
  MicOff,
  Plus,
  RefreshCw,
  Search,
  SendHorizontal,
  Sparkles,
  SquareStack,
  TriangleAlert,
  UserRound,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { Main } from '@/components/layout/main'
import {
  findRuntimeFormRegistration,
} from '@/features/forms/runtime/form-component-registry'
import { ProcessFormRenderer } from '@/features/forms/runtime/process-form-renderer'
import { getApiErrorMessage } from '@/lib/api/client'
import { cn } from '@/lib/utils'
import {
  clearAICopilotSessions,
  confirmAICopilotConfirmation,
  createAICopilotSession,
  downloadAICopilotAssetPreview,
  deleteAICopilotSession,
  getAICopilotSession,
  listAICopilotSessions,
  sendAICopilotMessage,
  transcribeAICopilotAudio,
  uploadAICopilotAsset,
  type AICopilotAttachmentItem,
  type AICopilotChartBlock,
  type AICopilotConfirmationDecision,
  type AICopilotMessage,
  type AICopilotMessageBlock,
  type AICopilotSession,
  type AICopilotSessionSummary,
  type AICopilotTraceStep,
} from '@/lib/api/ai-copilot'

const aiCopilotSessionsKey = ['ai-copilot', 'sessions'] as const
const attachmentPreviewUrlCache = new Map<string, string>()

const aiCopilotSessionKey = (sessionId: string) =>
  ['ai-copilot', 'sessions', sessionId] as const

// AI Copilot 页面负责会话导航、消息流、富消息块与确认动作的完整渲染。
export function AICopilotPage({
  sourceRoute = '',
}: {
  sourceRoute?: string
}) {
  return (
    <Main fixed fluid className='p-4 sm:p-6'>
      <AICopilotWorkspace sourceRoute={sourceRoute} mode='page' />
    </Main>
  )
}

export function AICopilotWorkspace({
  sourceRoute = '',
  mode = 'page',
}: {
  sourceRoute?: string
  mode?: 'page' | 'drawer'
}) {
  const queryClient = useQueryClient()
  const [activeSessionId, setActiveSessionId] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [draft, setDraft] = useState('')
  const [draftAttachments, setDraftAttachments] = useState<AICopilotAttachmentItem[]>([])
  const [optimisticMessages, setOptimisticMessages] = useState<AICopilotMessage[]>([])
  const [pendingConfirmationId, setPendingConfirmationId] = useState<string | null>(
    null
  )
  const [contextMenuState, setContextMenuState] = useState<{
    sessionId: string
    x: number
    y: number
  } | null>(null)
  const [deleteTargetSessionId, setDeleteTargetSessionId] = useState<string | null>(null)
  const [clearSessionsOpen, setClearSessionsOpen] = useState(false)
  const [previewAttachment, setPreviewAttachment] =
    useState<AICopilotAttachmentItem | null>(null)
  const bootstrappedRouteRef = useRef('')
  const composerRef = useRef<HTMLTextAreaElement | null>(null)
  const messageEndRef = useRef<HTMLDivElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null)
  const audioStreamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const audioSourceRef = useRef<MediaStreamAudioSourceNode | null>(null)
  const audioProcessorRef = useRef<ScriptProcessorNode | null>(null)
  const audioBuffersRef = useRef<Float32Array[]>([])
  const [isRecording, setIsRecording] = useState(false)
  const [isTranscribingAudio, setIsTranscribingAudio] = useState(false)

  const sessionsQuery = useQuery({
    queryKey: aiCopilotSessionsKey,
    queryFn: listAICopilotSessions,
  })

  const sessions = useMemo(() => sessionsQuery.data ?? [], [sessionsQuery.data])
  const routeTag = sourceRoute ? `route:${sourceRoute}` : ''
  const contextualSessionId = useMemo(() => {
    if (!routeTag) {
      return ''
    }

    return sessions.find((session) => session.contextTags.includes(routeTag))?.sessionId ?? ''
  }, [routeTag, sessions])
  const activeSessionIdFromList = sessionsQuery.data?.[0]?.sessionId ?? ''
  const effectiveActiveSessionId =
    activeSessionId || contextualSessionId || activeSessionIdFromList

  const activeSessionQuery = useQuery({
    queryKey: aiCopilotSessionKey(effectiveActiveSessionId),
    queryFn: () => getAICopilotSession(effectiveActiveSessionId),
    enabled: Boolean(effectiveActiveSessionId),
  })

  const createSessionMutation = useMutation({
    mutationFn: createAICopilotSession,
    onSuccess: (createdSession) => {
      setActiveSessionId(createdSession.sessionId)
      queryClient.setQueryData(
        aiCopilotSessionKey(createdSession.sessionId),
        createdSession
      )
      queryClient.setQueryData(
        aiCopilotSessionsKey,
        (previous?: AICopilotSessionSummary[]) =>
          upsertSessionSummary(previous ?? [], createdSession)
      )
    },
  })
  const { mutate: createSession, isPending: isCreatingSession } =
    createSessionMutation

  const sendMessageMutation = useMutation({
    mutationFn: sendAICopilotMessage,
    onMutate: async (input) => {
      const optimisticMessage = buildOptimisticUserMessage(
        input.content,
        input.attachments ?? []
      )
      setOptimisticMessages((previous) => [...previous, optimisticMessage])
      setDraft('')
      setDraftAttachments([])
      return {
        draft,
        attachments: draftAttachments,
        optimisticMessageId: optimisticMessage.messageId,
      }
    },
    onSuccess: (updatedSession) => {
      setOptimisticMessages([])
      queryClient.setQueryData(
        aiCopilotSessionKey(updatedSession.sessionId),
        updatedSession
      )
      queryClient.setQueryData(
        aiCopilotSessionsKey,
        (previous?: AICopilotSessionSummary[]) =>
          upsertSessionSummary(previous ?? [], updatedSession)
      )
    },
    onError: (error, _input, context) => {
      setOptimisticMessages((previous) =>
        previous.filter((message) => message.messageId !== context?.optimisticMessageId)
      )
      setDraft(context?.draft ?? '')
      setDraftAttachments(context?.attachments ?? [])
      toast.error(getApiErrorMessage(error, '消息发送失败，请稍后重试。'))
    },
  })

  const confirmMutation = useMutation({
    mutationFn: confirmAICopilotConfirmation,
    onSuccess: (updatedSession) => {
      setPendingConfirmationId(null)
      queryClient.setQueryData(
        aiCopilotSessionKey(updatedSession.sessionId),
        updatedSession
      )
      queryClient.setQueryData(
        aiCopilotSessionsKey,
        (previous?: AICopilotSessionSummary[]) =>
          upsertSessionSummary(previous ?? [], updatedSession)
      )
    },
    onError: () => {
      setPendingConfirmationId(null)
    },
  })

  const deleteSessionMutation = useMutation({
    mutationFn: deleteAICopilotSession,
    onSuccess: async (_, deletedSessionId) => {
      setDeleteTargetSessionId(null)
      setContextMenuState(null)
      queryClient.setQueryData(
        aiCopilotSessionsKey,
        (previous?: AICopilotSessionSummary[]) =>
          (previous ?? []).filter((session) => session.sessionId !== deletedSessionId)
      )
      queryClient.removeQueries({
        queryKey: aiCopilotSessionKey(deletedSessionId),
      })
      const remainingSessions = (queryClient.getQueryData(
        aiCopilotSessionsKey
      ) as AICopilotSessionSummary[] | undefined) ?? []
      if (effectiveActiveSessionId === deletedSessionId) {
        setActiveSessionId(remainingSessions[0]?.sessionId ?? '')
      }
      await queryClient.invalidateQueries({ queryKey: aiCopilotSessionsKey })
    },
  })

  const clearSessionsMutation = useMutation({
    mutationFn: clearAICopilotSessions,
    onSuccess: async () => {
      setClearSessionsOpen(false)
      setDeleteTargetSessionId(null)
      setContextMenuState(null)
      setActiveSessionId('')
      setOptimisticMessages([])
      queryClient.setQueryData(aiCopilotSessionsKey, [] as AICopilotSessionSummary[])
      await queryClient.invalidateQueries({ queryKey: aiCopilotSessionsKey })
    },
  })

  useEffect(() => {
    if (!sourceRoute || sessionsQuery.isLoading || isCreatingSession) {
      return
    }

    if (bootstrappedRouteRef.current === sourceRoute) {
      return
    }

    bootstrappedRouteRef.current = sourceRoute
    if (contextualSessionId) {
      return
    }

    createSession({
      title: buildContextualSessionTitle(sourceRoute),
      contextTags: ['AI Copilot', routeTag],
    })
  }, [
    sourceRoute,
    routeTag,
    contextualSessionId,
    sessionsQuery.isLoading,
    isCreatingSession,
    createSession,
  ])

  const activeSession = activeSessionQuery.data ?? null
  const orderedHistory = activeSession?.history?.length || optimisticMessages.length
    ? [...(activeSession?.history ?? []), ...optimisticMessages].sort((left, right) => {
        const leftTime = Date.parse(left.createdAt)
        const rightTime = Date.parse(right.createdAt)
        if (leftTime !== rightTime) {
          return leftTime - rightTime
        }
        if (left.role !== right.role) {
          if (left.role === 'user') {
            return -1
          }
          if (right.role === 'user') {
            return 1
          }
        }
        return left.messageId.localeCompare(right.messageId)
      })
    : []
  const activeContextRoute = useMemo(
    () => sourceRoute || extractRouteTagPath(activeSession?.contextTags ?? []),
    [activeSession?.contextTags, sourceRoute]
  )
  const activeContextLabel = useMemo(
    () => formatSourceRouteLabel(activeContextRoute),
    [activeContextRoute]
  )
  const filteredSessions = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase()

    if (!keyword) {
      return sessions
    }

    return sessions.filter((session) =>
      [session.title, session.preview, ...session.contextTags]
        .join(' ')
        .toLowerCase()
        .includes(keyword)
    )
  }, [searchTerm, sessions])

  const recentHistory = useMemo(() => {
    return activeSession?.history.slice(-5) ?? []
  }, [activeSession])

  const loadErrorMessage = getApiErrorMessage(
    sessionsQuery.error ?? activeSessionQuery.error,
    ''
  )
  const isLoading =
    sessionsQuery.isLoading ||
    (Boolean(effectiveActiveSessionId) &&
      activeSessionQuery.isLoading &&
      !activeSession)

  useEffect(() => {
    if (!effectiveActiveSessionId || isLoading) {
      return
    }

    composerRef.current?.focus()
  }, [effectiveActiveSessionId, isLoading])

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [activeSession?.history, optimisticMessages, sendMessageMutation.isPending])

  useEffect(
    () => () => {
      void stopAudioRecording(true)
      recognitionRef.current?.stop()
    },
    []
  )

  useEffect(() => {
    if (!contextMenuState) {
      return
    }

    const handleClose = () => setContextMenuState(null)
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setContextMenuState(null)
      }
    }

    window.addEventListener('click', handleClose)
    window.addEventListener('keydown', handleKeyDown)

    return () => {
      window.removeEventListener('click', handleClose)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [contextMenuState])

  const handleCreateSession = () => {
    if (isCreatingSession) {
      return
    }

    createSession({
      title: sourceRoute
        ? buildContextualSessionTitle(sourceRoute)
        : '新建 Copilot 会话',
      contextTags: sourceRoute
        ? ['AI Copilot', `route:${sourceRoute}`]
        : ['AI Copilot'],
    })
  }

  const handleSendMessage = () => {
    const content = draft.trim()

    if (
      (!content && !draftAttachments.length) ||
      !effectiveActiveSessionId ||
      sendMessageMutation.isPending
    ) {
      return
    }

    sendMessageMutation.mutate({
      sessionId: effectiveActiveSessionId,
      content,
      attachments: draftAttachments,
    })
  }

  const handlePickAttachment = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }
    const isImage = file.type.startsWith('image/')
    const isPdf =
      file.type === 'application/pdf' ||
      file.name.toLowerCase().endsWith('.pdf')
    if (!isImage && !isPdf) {
      toast.error('当前仅支持上传图片或 PDF')
      return
    }
    try {
      const uploaded = await uploadAICopilotAsset(file)
      const localPreviewUrl = URL.createObjectURL(file)
      attachmentPreviewUrlCache.set(uploaded.fileId, localPreviewUrl)
      setDraftAttachments((previous) => [
        ...previous,
        {
          ...uploaded,
          previewUrl: localPreviewUrl,
        },
      ])
      toast.success(isPdf ? 'PDF 已上传' : '图片已上传', {
        position: 'top-right',
        closeButton: true,
      })
    } catch (error) {
      toast.error(getApiErrorMessage(error, '图片上传失败，请稍后重试。'), {
        position: 'top-right',
        closeButton: true,
      })
    }
  }

  const startBrowserSpeechRecognitionFallback = () => {
    const RecognitionConstructor =
      window.SpeechRecognition ?? window.webkitSpeechRecognition
    if (!RecognitionConstructor) {
      toast.error('当前环境不支持语音输入')
      return
    }
    const recognition = new RecognitionConstructor()
    recognition.lang = 'zh-CN'
    recognition.continuous = false
    recognition.interimResults = true
    recognition.onstart = () => setIsRecording(true)
    recognition.onend = () => setIsRecording(false)
    recognition.onerror = () => {
      setIsRecording(false)
      toast.error('语音识别失败，请重试')
    }
    recognition.onresult = (event) => {
      const transcript = Array.from(event.results)
        .map((result) => result[0]?.transcript ?? '')
        .join('')
        .trim()
      if (transcript) {
        setDraft((previous) =>
          previous.trim() ? `${previous.trim()} ${transcript}` : transcript
        )
      }
    }
    recognitionRef.current = recognition
    recognition.start()
  }

  const stopAudioRecording = async (silent = false) => {
    const stream = audioStreamRef.current
    const processor = audioProcessorRef.current
    const sourceNode = audioSourceRef.current
    const audioContext = audioContextRef.current

    audioProcessorRef.current = null
    audioSourceRef.current = null
    audioStreamRef.current = null
    audioContextRef.current = null

    processor?.disconnect()
    sourceNode?.disconnect()
    stream?.getTracks().forEach((track) => track.stop())
    if (audioContext) {
      await audioContext.close()
    }

    const recordedBuffer = mergeAudioBuffers(audioBuffersRef.current)
    audioBuffersRef.current = []
    setIsRecording(false)

    if (!recordedBuffer.length || silent) {
      return
    }

    const wavBlob = encodeWavAudio(recordedBuffer, 16000)
    const audioFile = new File([wavBlob], `copilot-recording-${Date.now()}.wav`, {
      type: 'audio/wav',
    })

    try {
      setIsTranscribingAudio(true)
      const result = await transcribeAICopilotAudio(audioFile)
      const transcript = result.text.trim()
      if (!transcript) {
        toast.error('未识别到有效语音内容')
        return
      }
      setDraft((previous) =>
        previous.trim() ? `${previous.trim()} ${transcript}` : transcript
      )
      toast.success('语音已转写')
    } catch (error) {
      toast.error(getApiErrorMessage(error, '语音转写失败，请稍后重试。'))
    } finally {
      setIsTranscribingAudio(false)
    }
  }

  const startAudioRecording = async () => {
    if (
      typeof window === 'undefined' ||
      typeof window.AudioContext === 'undefined' ||
      typeof navigator === 'undefined' ||
      !navigator.mediaDevices?.getUserMedia
    ) {
      startBrowserSpeechRecognitionFallback()
      return
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const AudioContextConstructor =
        window.AudioContext ?? window.webkitAudioContext
      if (!AudioContextConstructor) {
        stream.getTracks().forEach((track) => track.stop())
        startBrowserSpeechRecognitionFallback()
        return
      }
      const audioContext = new AudioContextConstructor({ sampleRate: 16000 })
      const sourceNode = audioContext.createMediaStreamSource(stream)
      const processorNode = audioContext.createScriptProcessor(4096, 1, 1)
      audioBuffersRef.current = []
      processorNode.onaudioprocess = (event) => {
        const channel = event.inputBuffer.getChannelData(0)
        audioBuffersRef.current.push(new Float32Array(channel))
      }
      sourceNode.connect(processorNode)
      processorNode.connect(audioContext.destination)
      audioStreamRef.current = stream
      audioContextRef.current = audioContext
      audioSourceRef.current = sourceNode
      audioProcessorRef.current = processorNode
      setIsRecording(true)
      toast.info('开始录音，再点一次结束')
    } catch (error) {
      toast.error(getApiErrorMessage(error, '无法开始录音，请检查麦克风权限。'))
    }
  }

  const handleToggleRecording = () => {
    if (isTranscribingAudio) {
      return
    }
    if (isRecording) {
      void stopAudioRecording(false)
      return
    }
    void startAudioRecording()
  }

  const handleComposerKeyDown = (
    event: React.KeyboardEvent<HTMLTextAreaElement>
  ) => {
    if (event.key !== 'Enter') {
      return
    }

    if (event.shiftKey || event.nativeEvent.isComposing) {
      return
    }

    event.preventDefault()
    handleSendMessage()
  }

  const handleResolveConfirmation = (
    confirmationId: string,
    decision: AICopilotConfirmationDecision,
    argumentsOverride?: Record<string, unknown>
  ) => {
    if (!effectiveActiveSessionId || confirmMutation.isPending) {
      return
    }

    setPendingConfirmationId(confirmationId)
    confirmMutation.mutate({
      sessionId: effectiveActiveSessionId,
      confirmationId,
      decision,
      argumentsOverride,
    })
  }

  return (
    <div
      className={cn(
        'flex min-h-0 flex-1 flex-col overflow-hidden text-foreground',
        mode === 'page'
          ? 'relative overflow-hidden rounded-[2rem] border border-border bg-background shadow-xl backdrop-blur-2xl'
          : 'bg-background'
      )}
    >
      <div className='pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.12),_transparent_26%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.08),_transparent_22%)]' />
      <div
        className={cn(
          'relative grid min-h-0 flex-1 gap-4 p-4 sm:p-5',
          mode === 'page'
            ? 'min-h-[calc(100svh-8rem)] xl:grid-cols-[320px_minmax(0,1fr)]'
            : 'h-full xl:grid-cols-[280px_minmax(0,1fr)]'
        )}
      >
        <aside className='flex min-h-0 flex-col overflow-hidden rounded-[1.5rem] border border-border bg-card/80 shadow-sm'>
          <div className='border-b border-border px-4 py-4'>
              <div className='flex items-start justify-between gap-3'>
              <div>
                <p className='text-xs uppercase tracking-[0.28em] text-primary/70'>
                  AI Copilot
                </p>
                <h1 className='mt-2 text-lg font-semibold text-foreground'>会话</h1>
                <p className='mt-1 text-sm text-muted-foreground'>
                  选择会话，继续当前页面上下文。
                </p>
              </div>
              <div className='flex items-center gap-2'>
                <Button
                  type='button'
                  size='icon'
                  variant='outline'
                  className='border-border bg-background/80 text-destructive hover:bg-destructive/10 hover:text-destructive'
                  onClick={() => setClearSessionsOpen(true)}
                  disabled={!sessions.length || clearSessionsMutation.isPending}
                  aria-label='清空我的会话'
                  title='清空我的会话'
                >
                  <Trash2 className='size-4' />
                </Button>
                <Button
                  type='button'
                  size='icon'
                  variant='outline'
                  className='border-border bg-background/80 text-foreground hover:bg-muted'
                  onClick={handleCreateSession}
                >
                  <Plus />
                </Button>
              </div>
            </div>

            <div className='mt-4 flex items-center gap-2 rounded-full border border-border bg-background/80 px-3 py-2'>
              <Search className='size-4 text-muted-foreground' />
              <Input
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                placeholder='搜索会话或标签…'
                className='h-auto border-0 bg-transparent p-0 text-sm text-foreground shadow-none placeholder:text-muted-foreground focus-visible:ring-0'
              />
            </div>
          </div>

          <ScrollArea className='min-h-0 flex-1'>
            <div className='w-full max-w-full min-w-0 space-y-3 pl-4 pr-6 py-4 overflow-x-hidden'>
              <div className='flex items-center justify-between gap-3'>
                <SectionLabel
                  icon={<SquareStack className='size-3.5' />}
                  title='会话列表'
                  description={`${filteredSessions.length} 个会话`}
                />
                {activeContextLabel && mode === 'drawer' ? (
                  <BadgePill tone='subtle'>{activeContextLabel}</BadgePill>
                ) : null}
              </div>
              <div className='w-full max-w-full min-w-0 space-y-2 pr-1 overflow-x-hidden'>
                {filteredSessions.map((session) => {
                  const isActive = session.sessionId === effectiveActiveSessionId

                  return (
                    <button
                      key={session.sessionId}
                      type='button'
                      onClick={() => setActiveSessionId(session.sessionId)}
                      onContextMenu={(event) => {
                        event.preventDefault()
                        setContextMenuState({
                          sessionId: session.sessionId,
                          x: event.clientX,
                          y: event.clientY,
                        })
                      }}
                      className={cn(
                        'group box-border block max-w-full min-w-0 w-full overflow-hidden rounded-2xl border px-3 py-3 text-left transition-all duration-200',
                        isActive
                          ? 'border-primary/40 bg-primary/10 shadow-[0_0_0_1px_rgba(34,211,238,0.10)]'
                          : 'border-border bg-background/70 hover:border-primary/20 hover:bg-muted/60'
                      )}
                    >
                      <div className='flex w-full min-w-0 max-w-full items-start justify-between gap-3 overflow-hidden'>
                        <div className='min-w-0 flex-1'>
                          <p className='truncate text-sm font-medium text-foreground'>
                            {session.title}
                          </p>
                          <p className='mt-1 line-clamp-2 min-w-0 overflow-hidden text-xs leading-5 text-muted-foreground'>
                            {session.preview}
                          </p>
                        </div>
                        <ArrowRight
                          className={cn(
                            'mt-0.5 size-4 shrink-0 transition-transform',
                            isActive
                              ? 'text-primary'
                              : 'text-muted-foreground group-hover:translate-x-0.5'
                          )}
                        />
                      </div>
                      <div className='mt-3 flex w-full min-w-0 max-w-full flex-wrap gap-2 overflow-hidden'>
                        {session.contextTags.slice(0, 3).map((tag) => (
                          <span
                            key={tag}
                            className='max-w-full truncate rounded-full border border-border bg-muted/40 px-2.5 py-1 text-[11px] text-muted-foreground'
                            title={tag}
                          >
                            {tag}
                          </span>
                        ))}
                      </div>
                      <div className='mt-3 flex w-full min-w-0 max-w-full items-center justify-between gap-3 overflow-hidden text-[11px] text-muted-foreground'>
                        <span className='min-w-0 truncate'>{session.messageCount} 条消息</span>
                        <span className='shrink-0 whitespace-nowrap'>{formatDate(session.updatedAt)}</span>
                      </div>
                    </button>
                  )
                })}
                {!filteredSessions.length ? (
                  <div className='rounded-2xl border border-dashed border-border bg-muted/30 px-4 py-6 text-sm text-muted-foreground'>
                    没有找到匹配的会话，先试试新的搜索词。
                  </div>
                ) : null}
              </div>
              {mode === 'page' ? (
                <>
                  <Separator className='bg-border' />

                  <SectionLabel
                    icon={<Clock3 className='size-3.5' />}
                    title='历史快照'
                    description='当前会话最近的消息预览'
                  />
                  <div className='space-y-2'>
                    {recentHistory.map((message) => (
                      <HistoryRow key={message.messageId} message={message} />
                    ))}
                    {!recentHistory.length ? (
                      <div className='rounded-2xl border border-dashed border-border bg-muted/30 px-4 py-6 text-sm text-muted-foreground'>
                        当前会话暂无历史记录。
                      </div>
                    ) : null}
                  </div>
                </>
              ) : null}
            </div>
          </ScrollArea>
        </aside>

        <section className='flex min-h-0 min-w-0 flex-col overflow-hidden rounded-[1.75rem] border border-border bg-card/80 shadow-sm'>
          <div className='border-b border-border px-5 py-4'>
            <div className='flex flex-wrap items-start justify-between gap-4'>
              <div>
                <div className='flex flex-wrap items-center gap-2'>
                  <BadgePill icon={<Sparkles className='size-3.5' />}>
                    AI Copilot
                  </BadgePill>
                  {activeSession ? (
                    <BadgePill tone='subtle'>{activeSession.status}</BadgePill>
                  ) : null}
                  {activeContextLabel ? (
                    <BadgePill tone='subtle'>
                      上下文：{activeContextLabel}
                    </BadgePill>
                  ) : null}
                </div>
                <h2 className='mt-3 text-2xl font-semibold text-foreground'>
                  {activeSession?.title ?? '请选择一个会话'}
                </h2>
                <p className='mt-2 max-w-3xl text-sm leading-6 text-muted-foreground'>
                  在当前页面语境下对话、确认和继续处理。
                </p>
              </div>
              <div className='flex flex-wrap items-center gap-2'>
                <Button
                  type='button'
                  variant='outline'
                  className='border-border bg-background/80 text-foreground hover:bg-muted'
                  onClick={() => void activeSessionQuery.refetch()}
                >
                  <RefreshCw className='size-4' />
                  刷新会话
                </Button>
                <Button
                  type='button'
                  className='bg-primary text-primary-foreground hover:bg-primary/90'
                  onClick={handleCreateSession}
                >
                  <Plus className='size-4' />
                  新建会话
                </Button>
              </div>
            </div>
            {loadErrorMessage ? (
              <p className='mt-3 rounded-2xl border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive'>
                {loadErrorMessage}
              </p>
            ) : null}
          </div>

          <div
            className={cn(
              'grid min-h-0 flex-1',
              mode === 'page'
                ? 'xl:grid-cols-[minmax(0,1fr)_340px]'
                : 'grid-cols-[minmax(0,1fr)]'
            )}
          >
            <div className='flex min-h-0 min-w-0 flex-col border-r border-border'>
              <ScrollArea className='min-h-0 flex-1'>
                <div className='space-y-4 p-5'>
                  {isLoading ? (
                    <ShellSkeleton />
                  ) : activeSession ? (
                    orderedHistory.map((message) => (
                      <MessageBubble
                        key={message.messageId}
                        message={message}
                        pendingConfirmationId={pendingConfirmationId}
                        onConfirm={(confirmationId) =>
                          handleResolveConfirmation(confirmationId, 'confirm')
                        }
                        onCancel={(confirmationId) =>
                          handleResolveConfirmation(confirmationId, 'cancel')
                        }
                      />
                    ))
                  ) : null}
                  {sendMessageMutation.isPending ? <ThinkingBubble /> : null}
                  <div ref={messageEndRef} />
                  {!isLoading && !activeSession ? <EmptyState /> : null}
                </div>
              </ScrollArea>

              <div className='border-t border-border bg-background/80 p-4 backdrop-blur-xl'>
                <div className='rounded-[2rem] border border-border bg-card/90 px-5 py-4 shadow-[0_18px_40px_rgba(15,23,42,0.08)]'>
                  <Textarea
                    ref={composerRef}
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={handleComposerKeyDown}
                    placeholder='输入你的问题或指令…'
                    className='min-h-32 resize-none border-0 bg-transparent px-0 py-0 text-[15px] leading-7 text-foreground shadow-none placeholder:text-muted-foreground focus-visible:ring-0'
                  />
                  {draftAttachments.length ? (
                    <div className='mt-3 flex flex-wrap gap-2'>
                      {draftAttachments.map((attachment) => (
                        <div
                          key={attachment.fileId}
                          className='flex items-center gap-2 rounded-2xl border border-border bg-background/80 px-3 py-2'
                        >
                          <button
                            type='button'
                            className='flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-border bg-muted/40 p-1'
                            onClick={() =>
                              canPreviewAttachment(attachment)
                                ? setPreviewAttachment(attachment)
                                : undefined
                            }
                          >
                            {canPreviewAttachment(attachment) ? (
                              <AttachmentPreviewMedia
                                attachment={attachment}
                                className='rounded-lg border-0'
                              />
                            ) : (
                              <FileText className='size-5 text-muted-foreground' />
                            )}
                          </button>
                          <div className='min-w-0'>
                            <p className='max-w-40 truncate text-xs font-medium text-foreground'>
                              {attachment.displayName}
                            </p>
                            <p className='text-[11px] text-muted-foreground'>
                              {attachment.contentType}
                            </p>
                          </div>
                          <Button
                            type='button'
                            size='icon'
                            variant='ghost'
                            className='size-7 rounded-full'
                            onClick={() =>
                              setDraftAttachments((previous) =>
                                previous.filter((item) => item.fileId !== attachment.fileId)
                              )
                            }
                          >
                            <Trash2 className='size-3.5' />
                          </Button>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  <div className='mt-4 flex items-center justify-between gap-3'>
                    <div className='flex min-w-0 items-center gap-2 text-xs text-muted-foreground'>
                      <div className='flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary'>
                        <Sparkles className='size-4' />
                      </div>
                      <div className='min-w-0'>
                        <p className='truncate'>
                          {activeContextLabel
                            ? `当前上下文：${activeContextLabel}`
                            : '当前会话未绑定业务页面语境'}
                        </p>
                        <p>Enter 发送，Shift+Enter 换行</p>
                      </div>
                    </div>
                    <div className='flex items-center gap-2'>
                      <input
                        ref={fileInputRef}
                        type='file'
                        accept='image/*,application/pdf'
                        className='hidden'
                        onChange={handlePickAttachment}
                      />
                      <Button
                        type='button'
                        size='icon'
                        variant='outline'
                        aria-label='上传图片'
                        className='size-11 rounded-full border-border bg-background/80 text-foreground hover:bg-muted'
                        onClick={() => fileInputRef.current?.click()}
                      >
                        <ImagePlus className='size-4.5' />
                      </Button>
                      <Button
                        type='button'
                        size='icon'
                        variant='outline'
                        aria-label={
                          isTranscribingAudio
                            ? '语音转写中'
                            : isRecording
                              ? '停止语音输入'
                              : '开始语音输入'
                        }
                        className={cn(
                          'size-11 rounded-full border-border bg-background/80 text-foreground hover:bg-muted',
                          isRecording || isTranscribingAudio
                            ? 'border-primary/40 text-primary'
                            : ''
                        )}
                        onClick={handleToggleRecording}
                        disabled={isTranscribingAudio}
                      >
                        {isTranscribingAudio ? (
                          <RefreshCw className='size-4.5 animate-spin' />
                        ) : isRecording ? (
                          <MicOff className='size-4.5' />
                        ) : (
                          <Mic className='size-4.5' />
                        )}
                      </Button>
                      <Button
                        type='button'
                        size='icon'
                        aria-label='发送消息'
                        className='size-12 rounded-full bg-primary text-primary-foreground shadow-sm hover:bg-primary/90'
                        disabled={
                          (!draft.trim() && !draftAttachments.length) ||
                          sendMessageMutation.isPending ||
                          !effectiveActiveSessionId
                        }
                        onClick={() => void handleSendMessage()}
                      >
                        <SendHorizontal className='size-4.5' />
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </section>
      </div>
      {contextMenuState ? (
        <div
          className='fixed z-[60] min-w-40 rounded-md border border-border bg-popover p-1 shadow-lg'
          style={{
            left: contextMenuState.x,
            top: contextMenuState.y,
          }}
          onClick={(event) => event.stopPropagation()}
        >
          <button
            type='button'
            className='flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm text-destructive transition-colors hover:bg-destructive/10'
            onClick={() => {
              setDeleteTargetSessionId(contextMenuState.sessionId)
              setContextMenuState(null)
            }}
          >
            <Trash2 className='size-4 text-destructive' />
            删除会话
          </button>
        </div>
      ) : null}
      <AlertDialog
        open={Boolean(deleteTargetSessionId)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTargetSessionId(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除会话</AlertDialogTitle>
            <AlertDialogDescription>
              删除后会同时移除该会话的消息、工具调用和审计记录，且无法恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteSessionMutation.isPending}>
              取消
            </AlertDialogCancel>
            <AlertDialogAction
              className='bg-destructive text-destructive-foreground hover:bg-destructive/90'
              disabled={deleteSessionMutation.isPending || !deleteTargetSessionId}
              onClick={(event) => {
                event.preventDefault()
                if (!deleteTargetSessionId) {
                  return
                }
                deleteSessionMutation.mutate(deleteTargetSessionId)
              }}
            >
              {deleteSessionMutation.isPending ? '删除中…' : '确认删除'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <AlertDialog open={clearSessionsOpen} onOpenChange={setClearSessionsOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>清空我的会话</AlertDialogTitle>
            <AlertDialogDescription>
              仅清空当前登录账号创建的全部 AI 会话、消息、工具调用和审计记录，操作后无法恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={clearSessionsMutation.isPending}>
              取消
            </AlertDialogCancel>
            <AlertDialogAction
              className='bg-destructive text-destructive-foreground hover:bg-destructive/90'
              disabled={clearSessionsMutation.isPending || !sessions.length}
              onClick={(event) => {
                event.preventDefault()
                clearSessionsMutation.mutate()
              }}
            >
              {clearSessionsMutation.isPending ? '清空中…' : '确认清空'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <Dialog
        open={Boolean(previewAttachment)}
        onOpenChange={(open) => {
          if (!open) {
            setPreviewAttachment(null)
          }
        }}
      >
        <DialogContent className='max-w-5xl overflow-hidden p-0 sm:max-w-[min(92vw,1100px)]'>
          <DialogHeader className='border-b border-border px-6 py-4'>
            <DialogTitle className='truncate text-base'>
              {previewAttachment?.displayName ?? '附件预览'}
            </DialogTitle>
          </DialogHeader>
          <AttachmentPreviewPanel attachment={previewAttachment} />
        </DialogContent>
      </Dialog>
    </div>
  )
}

function buildContextualSessionTitle(sourceRoute: string) {
  if (sourceRoute.startsWith('/workbench')) {
    return '当前审批事项 Copilot'
  }
  if (sourceRoute.startsWith('/oa/')) {
    return '当前 OA 单据 Copilot'
  }
  if (sourceRoute.startsWith('/plm/')) {
    return '当前 PLM 单据 Copilot'
  }
  if (sourceRoute.startsWith('/workflow/')) {
    return '当前流程设计 Copilot'
  }
  if (sourceRoute.startsWith('/system/')) {
    return '当前系统管理 Copilot'
  }
  return '当前页面 Copilot'
}

function mergeAudioBuffers(buffers: Float32Array[]) {
  const totalLength = buffers.reduce((sum, buffer) => sum + buffer.length, 0)
  const merged = new Float32Array(totalLength)
  let offset = 0
  for (const buffer of buffers) {
    merged.set(buffer, offset)
    offset += buffer.length
  }
  return merged
}

function encodeWavAudio(samples: Float32Array, sampleRate: number) {
  const wavBuffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(wavBuffer)
  writeAsciiChunk(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeAsciiChunk(view, 8, 'WAVE')
  writeAsciiChunk(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAsciiChunk(view, 36, 'data')
  view.setUint32(40, samples.length * 2, true)

  let offset = 44
  for (let index = 0; index < samples.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, samples[index] ?? 0))
    view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
    offset += 2
  }

  return new Blob([wavBuffer], { type: 'audio/wav' })
}

function writeAsciiChunk(view: DataView, offset: number, value: string) {
  for (let index = 0; index < value.length; index += 1) {
    view.setUint8(offset + index, value.charCodeAt(index))
  }
}

type SpeechRecognitionResultLike = {
  0?: {
    transcript?: string
  }
}

type SpeechRecognitionLike = {
  lang: string
  continuous: boolean
  interimResults: boolean
  onstart: (() => void) | null
  onend: (() => void) | null
  onerror: (() => void) | null
  onresult:
    | ((event: { results: ArrayLike<SpeechRecognitionResultLike> }) => void)
    | null
  start: () => void
  stop: () => void
}

type SpeechRecognitionConstructorLike = new () => SpeechRecognitionLike

declare global {
  interface AudioContext {
    createScriptProcessor(
      bufferSize: number,
      numberOfInputChannels: number,
      numberOfOutputChannels: number
    ): ScriptProcessorNode
  }

  interface Window {
    AudioContext?: typeof AudioContext
    webkitAudioContext?: typeof AudioContext
  }

  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructorLike
    webkitSpeechRecognition?: SpeechRecognitionConstructorLike
  }
}

function extractRouteTagPath(contextTags: string[]) {
  const routeTag = contextTags.find((tag) => tag.startsWith('route:'))

  return routeTag ? routeTag.slice('route:'.length) : ''
}

function formatSourceRouteLabel(sourceRoute: string) {
  if (!sourceRoute) {
    return ''
  }
  if (sourceRoute.startsWith('/plm/ecr/create')) {
    return 'PLM / ECR 新建'
  }
  if (sourceRoute.startsWith('/plm/eco/create')) {
    return 'PLM / ECO 新建'
  }
  if (sourceRoute.startsWith('/plm/material-master/create')) {
    return 'PLM / 物料主数据变更'
  }
  if (sourceRoute.startsWith('/plm/')) {
    return 'PLM 业务页'
  }
  if (sourceRoute.startsWith('/workbench/todos/')) {
    return '工作台 / 待办详情'
  }
  if (sourceRoute.startsWith('/workbench/')) {
    return '工作台'
  }
  if (sourceRoute.startsWith('/oa/leave/create')) {
    return 'OA / 请假新建'
  }
  if (sourceRoute.startsWith('/oa/expense/create')) {
    return 'OA / 费用新建'
  }
  if (sourceRoute.startsWith('/oa/')) {
    return 'OA 业务页'
  }
  if (sourceRoute.startsWith('/workflow/')) {
    return '流程设计'
  }
  if (sourceRoute.startsWith('/system/')) {
    return '系统管理'
  }

  return sourceRoute
}

function buildOptimisticUserMessage(
  content: string,
  attachments: AICopilotAttachmentItem[]
): AICopilotMessage {
  const normalizedContent = content.trim()
  const summary =
    normalizedContent || (attachments.length ? `已上传 ${attachments.length} 个附件` : '')

  return {
    messageId: `optimistic_${Date.now()}`,
    role: 'user',
    authorName: '你',
    createdAt: new Date().toISOString(),
    content: summary,
    blocks: attachments.length
      ? [
          {
            type: 'attachments',
            items: attachments,
          },
        ]
      : [],
  }
}

function isImageAttachment(attachment: AICopilotAttachmentItem) {
  return attachment.contentType.startsWith('image/')
}

function canPreviewAttachment(attachment: AICopilotAttachmentItem | null) {
  if (!attachment?.fileId) {
    return false
  }
  return (
    isImageAttachment(attachment) ||
    attachment.contentType === 'application/pdf' ||
    attachment.displayName.toLowerCase().endsWith('.pdf')
  )
}

function useResolvedAttachmentPreviewUrl(
  attachment: AICopilotAttachmentItem | null
) {
  const [resolvedPreviewUrl, setResolvedPreviewUrl] = useState('')

  useEffect(() => {
    if (!attachment) {
      setResolvedPreviewUrl('')
      return
    }

    const cachedPreviewUrl = attachmentPreviewUrlCache.get(attachment.fileId)
    if (cachedPreviewUrl) {
      setResolvedPreviewUrl(cachedPreviewUrl)
      return
    }

    if (!canPreviewAttachment(attachment)) {
      setResolvedPreviewUrl('')
      return
    }

    if (
      attachment.previewUrl?.startsWith('blob:') ||
      attachment.previewUrl?.startsWith('data:')
    ) {
      attachmentPreviewUrlCache.set(attachment.fileId, attachment.previewUrl)
      setResolvedPreviewUrl(attachment.previewUrl)
      return
    }

    let disposed = false

    downloadAICopilotAssetPreview(attachment.fileId)
      .then((blob) => {
        if (disposed) {
          return
        }
        const objectUrl = URL.createObjectURL(blob)
        attachmentPreviewUrlCache.set(attachment.fileId, objectUrl)
        setResolvedPreviewUrl(objectUrl)
      })
      .catch(() => {
        if (!disposed) {
          setResolvedPreviewUrl('')
        }
      })

    return () => {
      disposed = true
    }
  }, [attachment])

  return resolvedPreviewUrl
}

function AttachmentPreviewMedia({
  attachment,
  className,
}: {
  attachment: AICopilotAttachmentItem
  className?: string
}) {
  const resolvedPreviewUrl = useResolvedAttachmentPreviewUrl(attachment)

  if (isImageAttachment(attachment) && resolvedPreviewUrl) {
    return (
      <img
        src={resolvedPreviewUrl}
        alt={attachment.displayName}
        className={cn(
          'h-full w-full rounded-xl border border-slate-200 bg-slate-100 object-contain p-3 shadow-sm',
          className
        )}
      />
    )
  }

  return (
    <div className='flex flex-col items-center gap-2 text-muted-foreground'>
      <FileText className='size-10' />
      <span className='text-xs'>点击预览</span>
    </div>
  )
}

function AttachmentPreviewPanel({
  attachment,
}: {
  attachment: AICopilotAttachmentItem | null
}) {
  const resolvedPreviewUrl = useResolvedAttachmentPreviewUrl(attachment)

  return (
    <div className='flex max-h-[80vh] min-h-[50vh] items-center justify-center bg-muted/30 p-6'>
      {attachment && isImageAttachment(attachment) && resolvedPreviewUrl ? (
        <img
          src={resolvedPreviewUrl}
          alt={attachment.displayName}
          className='max-h-[calc(80vh-6rem)] max-w-full rounded-xl border border-border bg-white object-contain shadow-sm'
        />
      ) : resolvedPreviewUrl ? (
        <iframe
          src={resolvedPreviewUrl}
          title={attachment?.displayName}
          className='h-[calc(80vh-6rem)] w-full rounded-xl border border-border bg-background'
        />
      ) : (
        <div className='flex flex-col items-center gap-2 text-muted-foreground'>
          <FileText className='size-12' />
          <span className='text-sm'>附件预览加载失败</span>
        </div>
      )}
    </div>
  )
}

function buildMessageCopyText(message: AICopilotMessage) {
  const textBodies =
    message.blocks
      ?.filter(
        (block): block is Extract<AICopilotMessageBlock, { type: 'text' }> =>
          block.type === 'text'
      )
      .map((block) => block.body.trim())
      .filter(Boolean) ?? []

  return [message.content, ...textBodies].filter(Boolean).join('\n\n').trim()
}

function SectionLabel({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode
  title: string
  description: string
}) {
  return (
    <div className='flex items-start gap-3'>
      <div className='mt-0.5 flex size-8 items-center justify-center rounded-full border border-border bg-muted/40 text-primary'>
        {icon}
      </div>
      <div>
        <p className='text-sm font-medium text-foreground'>{title}</p>
        <p className='text-xs text-muted-foreground'>{description}</p>
      </div>
    </div>
  )
}

function BadgePill({
  children,
  icon,
  tone = 'default',
}: {
  children: React.ReactNode
  icon?: React.ReactNode
  tone?: 'default' | 'subtle'
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.18em]',
        tone === 'subtle'
          ? 'border-border bg-muted/40 text-muted-foreground'
          : 'border-primary/30 bg-primary/10 text-primary'
      )}
    >
      {icon}
      {children}
    </span>
  )
}

function HistoryRow({ message }: { message: AICopilotMessage }) {
  return (
    <div className='rounded-2xl border border-border bg-muted/20 px-3 py-3'>
      <div className='flex items-center justify-between gap-3'>
        <div className='flex items-center gap-2'>
          <div
            className={cn(
              'flex size-7 items-center justify-center rounded-full text-xs font-semibold',
              message.role === 'assistant'
                ? 'bg-primary/10 text-primary'
                : message.role === 'system'
                  ? 'bg-amber-500/10 text-amber-700 dark:text-amber-300'
                  : 'bg-muted text-foreground'
            )}
          >
            {message.role === 'assistant' ? (
              <Bot className='size-3.5' />
            ) : (
              <UserRound className='size-3.5' />
            )}
          </div>
          <div>
            <p className='text-xs font-medium text-foreground'>{message.authorName}</p>
            <p className='text-[11px] text-muted-foreground'>
              {formatDate(message.createdAt)}
            </p>
          </div>
        </div>
        <ArrowRight className='size-3.5 text-muted-foreground' />
      </div>
      <p className='mt-2 line-clamp-2 text-xs leading-5 text-muted-foreground'>
        {message.content}
      </p>
    </div>
  )
}

function MessageBubble({
  message,
  pendingConfirmationId,
  onConfirm,
  onCancel,
}: {
  message: AICopilotMessage
  pendingConfirmationId: string | null
  onConfirm: (confirmationId: string, argumentsOverride?: Record<string, unknown>) => void
  onCancel: (confirmationId: string) => void
}) {
  const isUser = message.role === 'user'
  const [copied, setCopied] = useState(false)
  const visibleBlocks = useMemo(() => {
    const blocks = message.blocks?.filter(shouldRenderConversationBlock) ?? []
    const hasFormPreview = blocks.some((block) => block.type === 'form-preview')

    if (!hasFormPreview) {
      return blocks
    }

    return blocks.filter((block) => block.type !== 'confirm')
  }, [message.blocks])
  const confirmBlock = message.blocks?.find(
    (block): block is Extract<AICopilotMessageBlock, { type: 'confirm' }> =>
      block.type === 'confirm'
  )

  return (
    <div
      className={cn('flex gap-3', isUser ? 'justify-end' : 'justify-start')}
    >
      {!isUser ? (
        <div className='mt-1 flex size-9 shrink-0 items-center justify-center rounded-full border border-primary/30 bg-primary/10 text-primary'>
          <Bot className='size-4' />
        </div>
      ) : null}

      <div
        className={cn(
          'max-w-[min(100%,52rem)] rounded-[1.5rem] border px-4 py-3 shadow-lg',
          isUser
            ? 'border-primary/20 bg-primary/10 text-foreground'
            : 'border-border bg-card text-foreground'
        )}
      >
        <div className='flex items-center justify-between gap-3 text-xs text-muted-foreground'>
          <div className='flex items-center gap-2'>
            <span className='font-medium text-foreground'>{message.authorName}</span>
            <span>·</span>
            <span>{formatDate(message.createdAt)}</span>
          </div>
          <Button
            type='button'
            size='icon'
            variant='ghost'
            className='size-7 rounded-full text-muted-foreground'
            onClick={async () => {
              const text = buildMessageCopyText(message)
              await navigator.clipboard.writeText(text)
              setCopied(true)
              window.setTimeout(() => setCopied(false), 1200)
              toast.success('已复制消息')
            }}
          >
            {copied ? <Check className='size-3.5' /> : <Copy className='size-3.5' />}
          </Button>
        </div>
        <p className='mt-2 text-sm leading-6 text-foreground'>
          {message.content}
        </p>
        {visibleBlocks.length ? (
          <div className='mt-4 space-y-3'>
            {visibleBlocks.map((block, index) => (
              <BlockCard
                key={`${message.messageId}-${index}`}
                block={block}
                isPending={
                  block.type === 'confirm'
                    ? pendingConfirmationId === block.confirmationId
                    : (block.type === 'form-preview' ||
                        block.type === 'failure' ||
                        block.type === 'retry') &&
                        typeof block.result?.confirmationId === 'string'
                      ? pendingConfirmationId === block.result.confirmationId
                    : false
                }
                onConfirm={
                  block.type === 'confirm'
                    ? () => onConfirm(block.confirmationId)
                    : block.type === 'form-preview' &&
                        typeof block.result?.confirmationId === 'string'
                      ? (argumentsOverride) =>
                          onConfirm(block.result?.confirmationId as string, argumentsOverride)
                    : block.type === 'failure' &&
                          typeof block.result?.confirmationId === 'string'
                        ? () => onConfirm(block.result?.confirmationId as string)
                    : block.type === 'retry' &&
                          typeof block.result?.confirmationId === 'string'
                        ? () => onConfirm(block.result?.confirmationId as string)
                    : undefined
                }
                onCancel={
                  block.type === 'confirm' && block.cancelLabel
                    ? () => onCancel(block.confirmationId)
                    : undefined
                }
                linkedConfirmBlock={confirmBlock}
                onRetry={
                  (block.type === 'failure' || block.type === 'retry') &&
                      typeof block.result?.confirmationId === 'string'
                    ? () => onConfirm(block.result?.confirmationId as string)
                    : undefined
                }
              />
            ))}
          </div>
        ) : null}
      </div>

      {isUser ? (
        <div className='mt-1 flex size-9 shrink-0 items-center justify-center rounded-full border border-border bg-muted text-foreground'>
          <UserRound className='size-4' />
        </div>
      ) : null}
    </div>
  )
}

function ThinkingBubble() {
  return (
    <div className='flex justify-start gap-3'>
      <div className='mt-1 flex size-9 shrink-0 items-center justify-center rounded-full border border-primary/30 bg-primary/10 text-primary'>
        <Bot className='size-4' />
      </div>
      <div className='max-w-[min(100%,32rem)] rounded-[1.5rem] border border-border bg-card px-4 py-3 shadow-sm'>
        <div className='flex items-center gap-2 text-xs text-muted-foreground'>
          <span className='font-medium text-foreground'>AI Copilot</span>
          <span>·</span>
          <span>思考中</span>
        </div>
        <div className='mt-3 flex items-center gap-2'>
          <span className='size-2 animate-pulse rounded-full bg-primary/70' />
          <span className='size-2 animate-pulse rounded-full bg-primary/50 [animation-delay:120ms]' />
          <span className='size-2 animate-pulse rounded-full bg-primary/30 [animation-delay:240ms]' />
          <span className='ml-2 text-sm text-muted-foreground'>正在分析当前页面与业务数据…</span>
        </div>
      </div>
    </div>
  )
}

function EditableFormPreviewCard({
  block,
  isPending,
  onConfirm,
  linkedConfirmBlock,
}: {
  block: Extract<AICopilotMessageBlock, { type: 'form-preview' }>
  isPending?: boolean
  onConfirm?: (argumentsOverride?: Record<string, unknown>) => void
  linkedConfirmBlock?: Extract<AICopilotMessageBlock, { type: 'confirm' }>
}) {
  const editable = Boolean(block.result?.editable)
  const initialFormData = useMemo(
    () => toEditableFormData(block.result?.formData),
    [block.result]
  )
  const [formData, setFormData] = useState<Record<string, unknown>>(initialFormData)

  useEffect(() => {
    setFormData(initialFormData)
  }, [initialFormData])

  const confirmationSummary =
    linkedConfirmBlock?.summary ?? '确认后会按当前表单参数执行真实业务发起。'
  const processFormKey = String(block.result?.processFormKey ?? '')
  const processFormVersion = String(block.result?.processFormVersion ?? '')
  const runtimeProcessForm =
    processFormKey && processFormVersion
      ? findRuntimeFormRegistration(
          processFormKey,
          processFormVersion,
          'PROCESS_FORM'
        )
      : null
  const canSubmit =
    Boolean(onConfirm) &&
    !isPending &&
    Boolean(runtimeProcessForm) &&
    Boolean(block.result?.processKey)

  return (
    <Card className='border-primary/20 bg-primary/10 text-foreground shadow-none'>
      <CardHeader className='space-y-2 pb-3'>
        <CardTitle className='flex items-center gap-2 text-base'>
          <SquareStack className='size-4 text-primary' />
          {block.title}
        </CardTitle>
        {block.description ? (
          <p className='text-sm leading-6 text-muted-foreground'>{block.description}</p>
        ) : null}
      </CardHeader>
      <CardContent className='space-y-3 pt-0'>
        {editable ? (
          <div
            data-testid='ai-form-preview-editor'
            className='space-y-3 rounded-2xl border border-border bg-background/80 p-3'
          >
            <p className='text-xs uppercase tracking-[0.18em] text-primary/80'>
              可编辑业务表单
            </p>
            {runtimeProcessForm ? (
              <ProcessFormRenderer
                processFormKey={processFormKey}
                processFormVersion={processFormVersion}
                value={formData}
                onChange={setFormData}
              />
            ) : (
              <div className='rounded-2xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-foreground'>
                系统未找到该流程对应的运行时表单组件，当前不能通过 AI 直接发起。请先确认流程已发布且表单已接入运行态注册中心。
              </div>
            )}
            <p className='text-xs leading-5 text-muted-foreground'>{confirmationSummary}</p>
            <div className='flex flex-wrap gap-2'>
              <Button
                type='button'
                size='sm'
                data-testid='ai-form-preview-submit'
                className='bg-primary text-primary-foreground hover:bg-primary/90'
                onClick={() =>
                  onConfirm?.({
                    processKey: block.result?.processKey,
                    processDefinitionId: block.result?.processDefinitionId,
                    processName: block.result?.processName,
                    businessType: block.result?.businessType,
                    sceneCode: block.result?.sceneCode,
                    processFormKey: processFormKey || undefined,
                    processFormVersion: processFormVersion || undefined,
                    formData,
                  })
                }
                disabled={!canSubmit}
              >
                {isPending ? '提交中…' : '确认并发起'}
              </Button>
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}

function AttachmentBlockCard({
  items,
}: {
  items: AICopilotAttachmentItem[]
}) {
  const [previewAttachment, setPreviewAttachment] =
    useState<AICopilotAttachmentItem | null>(null)

  return (
    <>
      <Card className='border-border bg-background/80 text-foreground shadow-none'>
        <CardContent className='grid gap-3 pt-6 sm:grid-cols-2'>
          {items.map((item) => (
            <div
              key={item.fileId}
              className='overflow-hidden rounded-2xl border border-border bg-card'
            >
              <button
                type='button'
                className='flex h-40 w-full items-center justify-center bg-muted/30 p-3'
                onClick={() =>
                  canPreviewAttachment(item) ? setPreviewAttachment(item) : undefined
                }
              >
                {canPreviewAttachment(item) ? (
                  <AttachmentPreviewMedia attachment={item} />
                ) : (
                  <div className='flex flex-col items-center gap-2 text-muted-foreground'>
                    <FileText className='size-10' />
                    <span className='text-xs'>点击预览</span>
                  </div>
                )}
              </button>
              <div className='p-3'>
                <p className='truncate text-sm font-medium text-foreground'>
                  {item.displayName}
                </p>
                <p className='mt-1 text-xs text-muted-foreground'>
                  {item.contentType}
                </p>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
      <Dialog
        open={Boolean(previewAttachment)}
        onOpenChange={(open) => {
          if (!open) {
            setPreviewAttachment(null)
          }
        }}
      >
        <DialogContent className='max-w-5xl overflow-hidden p-0 sm:max-w-[min(92vw,1100px)]'>
          <DialogHeader className='border-b border-border px-6 py-4'>
            <DialogTitle className='truncate text-base'>
              {previewAttachment?.displayName ?? '附件预览'}
            </DialogTitle>
          </DialogHeader>
          <AttachmentPreviewPanel attachment={previewAttachment} />
        </DialogContent>
      </Dialog>
    </>
  )
}

function BlockCard({
  block,
  isPending,
  onConfirm,
  onCancel,
  onRetry,
  linkedConfirmBlock,
}: {
  block: AICopilotMessageBlock
  isPending?: boolean
  onConfirm?: (argumentsOverride?: Record<string, unknown>) => void
  onCancel?: () => void
  onRetry?: () => void
  linkedConfirmBlock?: Extract<AICopilotMessageBlock, { type: 'confirm' }>
}) {
  switch (block.type) {
    case 'attachments':
      return <AttachmentBlockCard items={block.items} />
    case 'confirm': {
      const status = block.status ?? 'pending'
      const statusLabel = formatConfirmationStatus(status)

      return (
        <Card
          className={cn(
            'shadow-none',
            status === 'pending'
              ? 'border-amber-500/20 bg-amber-500/10 text-foreground'
              : status === 'confirmed'
                ? 'border-emerald-500/20 bg-emerald-500/10 text-foreground'
                : 'border-border bg-card text-foreground'
          )}
        >
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <CheckCircle2 className='size-4 text-amber-600 dark:text-amber-300' />
              {block.title}
            </CardTitle>
            <p className='text-sm leading-6 text-foreground'>{block.summary}</p>
            {block.detail ? (
              <p className='text-xs leading-5 text-muted-foreground'>{block.detail}</p>
            ) : null}
            <div className='flex flex-wrap items-center gap-2 pt-1'>
              <BadgePill tone='subtle'>{statusLabel}</BadgePill>
              {block.resolvedBy ? (
                <span className='text-xs text-muted-foreground'>
                  处理人：{block.resolvedBy}
                </span>
              ) : null}
              {block.resolvedAt ? (
                <span className='text-xs text-muted-foreground'>
                  {formatDate(block.resolvedAt)}
                </span>
              ) : null}
            </div>
            {block.resolutionNote ? (
              <p className='text-xs leading-5 text-muted-foreground'>
                {block.resolutionNote}
              </p>
            ) : null}
          </CardHeader>
          {status === 'pending' ? (
            <CardContent className='flex flex-wrap gap-2 pt-0'>
              <Button
                type='button'
                size='sm'
                className='bg-amber-500 text-white hover:bg-amber-500/90'
                onClick={() => onConfirm?.()}
                disabled={!onConfirm || isPending}
              >
                {isPending ? '确认中…' : block.confirmLabel}
              </Button>
              {block.cancelLabel ? (
                <Button
                  type='button'
                  size='sm'
                  variant='outline'
                  className='border-border bg-background/80 text-foreground hover:bg-muted'
                  onClick={onCancel}
                  disabled={!onCancel || isPending}
                >
                  {block.cancelLabel}
                </Button>
              ) : null}
            </CardContent>
          ) : null}
        </Card>
      )
    }
    case 'form-preview':
      return (
        <EditableFormPreviewCard
          block={block}
          isPending={isPending}
          onConfirm={onConfirm}
          linkedConfirmBlock={linkedConfirmBlock}
        />
      )
    case 'stats':
      return (
        <Card className='border-emerald-500/20 bg-emerald-500/10 text-foreground shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <BarChart3 className='size-4 text-emerald-600 dark:text-emerald-300' />
              {block.title}
            </CardTitle>
            {block.description ? (
              <p className='text-sm leading-6 text-muted-foreground'>
                {block.description}
              </p>
            ) : null}
          </CardHeader>
          <CardContent className='grid gap-2 pt-0 sm:grid-cols-3'>
            {block.metrics.map((metric) => (
              <div
                key={metric.label}
                className='rounded-2xl border border-border bg-background/80 px-3 py-3'
              >
                <div className='flex items-center gap-2'>
                  <span className='text-[11px] text-muted-foreground'>
                    {metric.label}
                  </span>
                  {metric.tone ? (
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-[10px] uppercase tracking-[0.18em]',
                        metric.tone === 'positive'
                          ? 'bg-emerald-300/15 text-emerald-100'
                          : metric.tone === 'warning'
                            ? 'bg-amber-300/15 text-amber-100'
                            : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {metric.tone}
                    </span>
                  ) : null}
                </div>
                <p className='mt-2 text-lg font-semibold text-foreground'>
                  {metric.value}
                </p>
                {metric.hint ? (
                  <p className='mt-1 text-[11px] text-muted-foreground'>{metric.hint}</p>
                ) : null}
              </div>
            ))}
          </CardContent>
        </Card>
      )
    case 'chart':
      return <ChartBlockCard block={block} />
    case 'trace':
      return (
        <Card className='border-sky-500/20 bg-sky-500/10 text-foreground shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <GitBranch className='size-4 text-sky-600 dark:text-sky-300' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-foreground'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-muted-foreground'>{block.detail}</p>
            ) : null}
            <TraceMeta block={block} />
          </CardHeader>
          <CardContent className='space-y-2 pt-0'>
            <TraceTimeline trace={block.trace ?? []} />
          </CardContent>
        </Card>
      )
    case 'result':
      return (
        <Card className='border-emerald-500/20 bg-emerald-500/10 text-foreground shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <BadgeCheck className='size-4 text-emerald-600 dark:text-emerald-300' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-foreground'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-muted-foreground'>{block.detail}</p>
            ) : null}
            <TraceMeta block={block} />
          </CardHeader>
          <CardContent className='space-y-3 pt-0'>
            <ExecutionSummaryBanner
              tone='success'
              block={block}
              defaultLabel='执行结果'
            />
            <FieldList fields={block.fields} />
            <MetricGrid metrics={block.metrics} />
            {shouldShowRawResultPayload(block) ? (
              <pre className='overflow-x-auto rounded-2xl border border-border bg-background/80 px-3 py-3 text-xs leading-5 text-foreground'>
                {JSON.stringify(block.result, null, 2)}
              </pre>
            ) : null}
            {block.trace?.length ? <TraceTimeline trace={block.trace} /> : null}
          </CardContent>
        </Card>
      )
    case 'failure':
      return (
        <Card className='border-destructive/20 bg-destructive/10 text-foreground shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <TriangleAlert className='size-4 text-rose-200' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-foreground'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-muted-foreground'>{block.detail}</p>
            ) : null}
          </CardHeader>
          <CardContent className='space-y-3 pt-0'>
            {block.failure ? (
              <div className='rounded-2xl border border-border bg-background/80 px-3 py-3'>
                <div className='flex items-center justify-between gap-3'>
                  <span className='text-xs text-muted-foreground'>错误码</span>
                  <span className='text-sm font-medium text-foreground'>
                    {block.failure.code}
                  </span>
                </div>
                <p className='mt-2 text-sm text-foreground'>{block.failure.message}</p>
                {block.failure.detail ? (
                  <p className='mt-1 text-[11px] leading-5 text-muted-foreground'>
                    {block.failure.detail}
                  </p>
                ) : null}
              </div>
            ) : null}
            {block.result?.retryable ? (
              <div className='flex gap-2'>
                <Button
                  type='button'
                  size='sm'
                  className='bg-destructive text-destructive-foreground hover:bg-destructive/90'
                  onClick={onRetry}
                  disabled={!onRetry || isPending}
                >
                  {isPending
                    ? '重试中…'
                    : resolveRetryButtonLabel(block, '重新执行')}
                </Button>
              </div>
            ) : null}
          </CardContent>
        </Card>
      )
    case 'retry':
      return (
        <Card className='border-amber-500/20 bg-amber-500/10 text-foreground shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <RefreshCw className='size-4 text-amber-200' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-foreground'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-muted-foreground'>{block.detail}</p>
            ) : null}
          </CardHeader>
          <CardContent className='space-y-3 pt-0'>
            <div className='flex gap-2'>
              <Button
                type='button'
                size='sm'
                className='bg-amber-500 text-white hover:bg-amber-500/90'
                onClick={onRetry}
                disabled={!onRetry || isPending}
              >
                {isPending
                  ? '重试中…'
                  : resolveRetryButtonLabel(block, '按当前参数重试')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )
    case 'text':
    default:
      return (
        <Card className='border-border bg-card/80 text-foreground shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <CircleAlert className='size-4 text-cyan-200' />
              {block.title ?? '文本块'}
            </CardTitle>
          </CardHeader>
          <CardContent className='pt-0 text-sm leading-6 text-foreground'>
            {block.body}
          </CardContent>
        </Card>
      )
  }
}

function ExecutionSummaryBanner({
  tone,
  block,
  defaultLabel,
}: {
  tone: 'success' | 'warning' | 'danger'
  block:
    | Extract<AICopilotMessageBlock, { type: 'result' }>
    | Extract<AICopilotMessageBlock, { type: 'failure' }>
    | Extract<AICopilotMessageBlock, { type: 'retry' }>
  defaultLabel: string
}) {
  const result = block.result ?? {}
  const confirmationId =
    typeof result.confirmationId === 'string'
      ? result.confirmationId
      : null
  const toolCallId =
    typeof result.toolCallId === 'string' ? result.toolCallId : null
  const taskHandleAction = resolveTaskHandleActionLabel(block)
  const taskHandleTaskId = resolveTaskHandleTaskId(block)
  const sourceLabel = resolveDisplaySourceLabel(block)
  const bannerClass =
    tone === 'success'
      ? 'border-emerald-500/20 bg-emerald-500/10 text-foreground'
      : tone === 'danger'
        ? 'border-destructive/20 bg-destructive/10 text-foreground'
        : 'border-amber-500/20 bg-amber-500/10 text-foreground'

  if (
    !confirmationId &&
    !toolCallId &&
    !block.sourceName &&
    !block.sourceKey &&
    !block.toolType
  ) {
    return null
  }

  return (
    <div className={cn('rounded-2xl border px-3 py-3', bannerClass)}>
      <div className='flex flex-wrap gap-3 text-xs'>
        <span>{defaultLabel}</span>
        {taskHandleAction ? <span>待办动作：{taskHandleAction}</span> : null}
        {taskHandleTaskId ? <span>待办编号：{taskHandleTaskId}</span> : null}
        {sourceLabel ? <span>命中：{sourceLabel}</span> : null}
        {block.toolType ? <span>动作：{block.toolType}</span> : null}
        {toolCallId ? <span>调用：{toolCallId}</span> : null}
        {confirmationId ? <span>确认单：{confirmationId}</span> : null}
      </div>
    </div>
  )
}

function FieldList({
  fields,
}: {
  fields?: {
    label: string
    value: string
    hint?: string
  }[]
}) {
  if (!fields?.length) {
    return null
  }

  return (
    <div className='space-y-2'>
      {fields.map((field) => (
        <div
          key={field.label}
          className='rounded-2xl border border-border bg-background/80 px-3 py-2'
        >
          <div className='flex items-center justify-between gap-3'>
            <span className='text-xs text-muted-foreground'>{field.label}</span>
            <span className='text-sm font-medium text-foreground'>{field.value}</span>
          </div>
          {field.hint ? (
            <p className='mt-1 text-[11px] text-muted-foreground'>{field.hint}</p>
          ) : null}
        </div>
      ))}
    </div>
  )
}

function MetricGrid({
  metrics,
}: {
  metrics?: {
    label: string
    value: string
    hint?: string
    tone?: 'neutral' | 'positive' | 'warning'
  }[]
}) {
  if (!metrics?.length) {
    return null
  }

  return (
    <div className='grid gap-2 sm:grid-cols-2'>
      {metrics.map((metric) => (
        <div
          key={metric.label}
          className='rounded-2xl border border-border bg-background/80 px-3 py-3'
        >
          <div className='flex items-center gap-2'>
            <span className='text-[11px] text-muted-foreground'>{metric.label}</span>
            {metric.tone ? (
              <span
                className={cn(
                  'rounded-full px-2 py-0.5 text-[10px] uppercase tracking-[0.18em]',
                  metric.tone === 'positive'
                    ? 'bg-emerald-300/15 text-emerald-100'
                    : metric.tone === 'warning'
                      ? 'bg-amber-300/15 text-amber-100'
                      : 'bg-muted text-muted-foreground'
                )}
              >
                {metric.tone}
              </span>
            ) : null}
          </div>
          <p className='mt-2 text-lg font-semibold text-foreground'>{metric.value}</p>
          {metric.hint ? (
            <p className='mt-1 text-[11px] text-muted-foreground'>{metric.hint}</p>
          ) : null}
        </div>
      ))}
    </div>
  )
}

function TraceMeta({
  block,
}: {
  block: Extract<
    AICopilotMessageBlock,
    { sourceType?: string; sourceKey?: string; sourceName?: string; toolType?: string }
  >
}) {
  const sourceLabel = resolveDisplaySourceLabel(block)

  if (!block.sourceType && !sourceLabel && !block.sourceKey && !block.toolType) {
    return null
  }

  return (
    <div className='flex flex-wrap gap-2 pt-1'>
      {block.sourceType ? <BadgePill tone='subtle'>来源：{block.sourceType}</BadgePill> : null}
      {block.toolType ? <BadgePill tone='subtle'>工具：{block.toolType}</BadgePill> : null}
      {sourceLabel ? (
        <span className='text-xs text-muted-foreground'>命中：{sourceLabel}</span>
      ) : null}
      {block.sourceKey ? (
        <span className='text-xs text-muted-foreground'>键：{block.sourceKey}</span>
      ) : null}
    </div>
  )
}

function resolveDisplaySourceLabel(
  block: {
    sourceName?: string | null
    summary?: string | null
  }
) {
  const sourceName = String(block.sourceName ?? '').trim()
  const summary = String('summary' in block ? block.summary ?? '' : '').trim()

  if (!sourceName) {
    return null
  }

  if (sourceName === summary) {
    return null
  }

  return sourceName
}

function shouldRenderConversationBlock(block: AICopilotMessageBlock) {
  return (
    block.type === 'attachments' ||
    (block.type === 'confirm' &&
      ((block.status ?? 'pending') === 'pending')) ||
    block.type === 'stats' ||
    block.type === 'chart' ||
    block.type === 'form-preview' ||
    block.type === 'failure' ||
    block.type === 'retry'
  )
}

function ChartBlockCard({ block }: { block: AICopilotChartBlock }) {
  const chart = block.result?.chart
  const data = Array.isArray(block.result?.data) ? block.result.data : []
  const chartType = chart?.type ?? 'bar'
  const xField = chart?.xField ?? 'label'
  const yField = chart?.yField ?? 'value'
  const series = chart?.series ?? [{ dataKey: yField, name: '数值' }]
  const palette = ['#0f766e', '#2563eb', '#f59e0b', '#7c3aed', '#ef4444', '#10b981']
  const visibleMetrics =
    chartType === 'metric'
      ? (block.metrics ?? []).filter((metric) => {
          const metricLabel = String(chart?.metricLabel ?? '').trim()
          const chartValue = String(chart?.value ?? data[0]?.[yField] ?? '--').trim()
          return !(
            metricLabel &&
            metric.label === metricLabel &&
            String(metric.value).trim() === chartValue
          )
        })
      : block.metrics

  if (!chart || data.length === 0) {
    if (chartType !== 'metric') {
      return null
    }
  }

  return (
    <Card className='border-primary/20 bg-card text-foreground shadow-none'>
      <CardHeader className='space-y-2 pb-3'>
        <CardTitle className='flex items-center gap-2 text-base'>
          <BarChart3 className='size-4 text-primary' />
          {block.title}
        </CardTitle>
        {block.summary ? (
          <p className='text-sm leading-6 text-foreground'>{block.summary}</p>
        ) : null}
        {block.detail ? (
          <p className='text-xs leading-5 text-muted-foreground'>{block.detail}</p>
        ) : null}
      </CardHeader>
      <CardContent className='space-y-4 pt-0'>
        {chartType === 'metric' ? (
          <div className='rounded-2xl border border-border bg-background/80 p-6'>
            <div className='space-y-2'>
              <p className='text-sm text-muted-foreground'>
                {chart?.metricLabel ?? '核心指标'}
              </p>
              <div className='flex items-end gap-3'>
                <span className='text-4xl font-semibold tracking-tight text-foreground'>
                  {String(chart?.value ?? data[0]?.[yField] ?? '--')}
                </span>
                {chart?.valueLabel ? (
                  <span className='pb-1 text-sm text-muted-foreground'>
                    {chart.valueLabel}
                  </span>
                ) : null}
              </div>
            </div>
          </div>
        ) : chartType === 'table' ? (
          <div className='overflow-hidden rounded-2xl border border-border bg-background/80'>
            <div className='overflow-x-auto'>
              <table className='min-w-full divide-y divide-border text-sm'>
                <thead className='bg-muted/40'>
                  <tr>
                    {(chart?.columns ?? []).map((column) => (
                      <th
                        key={column.key}
                        className='px-4 py-3 text-left font-medium text-foreground'
                      >
                        {column.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className='divide-y divide-border'>
                  {data.map((row, index) => (
                    <tr key={`chart-row-${index}`} className='bg-background/80'>
                      {(chart?.columns ?? []).map((column) => (
                        <td key={column.key} className='px-4 py-3 text-foreground'>
                          {String(row[column.key] ?? '--')}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <div className='h-72 rounded-2xl border border-border bg-background/80 p-3'>
            <ResponsiveContainer width='100%' height='100%'>
              {chartType === 'pie' || chartType === 'donut' ? (
              <PieChart>
                <Tooltip />
                <Pie
                  data={data}
                  dataKey={series[0]?.dataKey ?? yField}
                  nameKey={xField}
                  innerRadius={chartType === 'donut' ? 52 : 0}
                  outerRadius={88}
                  paddingAngle={2}
                >
                  {data.map((_, index) => (
                    <Cell key={`cell-${index}`} fill={palette[index % palette.length]} />
                  ))}
                </Pie>
              </PieChart>
              ) : chartType === 'line' ? (
              <LineChart data={data}>
                <CartesianGrid strokeDasharray='3 3' vertical={false} stroke='var(--border)' />
                <XAxis
                  dataKey={xField}
                  tickLine={false}
                  axisLine={false}
                  fontSize={12}
                  stroke='var(--muted-foreground)'
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  fontSize={12}
                  stroke='var(--muted-foreground)'
                />
                <Tooltip />
                {series.map((item, index) => (
                  <Line
                    key={item.dataKey}
                    type='monotone'
                    dataKey={item.dataKey}
                    name={item.name ?? item.dataKey}
                    stroke={item.color ?? palette[index % palette.length]}
                    strokeWidth={2}
                    dot={{ r: 3 }}
                  />
                ))}
              </LineChart>
              ) : chartType === 'area' ? (
              <AreaChart data={data}>
                <CartesianGrid strokeDasharray='3 3' vertical={false} stroke='var(--border)' />
                <XAxis
                  dataKey={xField}
                  tickLine={false}
                  axisLine={false}
                  fontSize={12}
                  stroke='var(--muted-foreground)'
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  fontSize={12}
                  stroke='var(--muted-foreground)'
                />
                <Tooltip />
                {series.map((item, index) => (
                  <Area
                    key={item.dataKey}
                    type='monotone'
                    dataKey={item.dataKey}
                    name={item.name ?? item.dataKey}
                    stroke={item.color ?? palette[index % palette.length]}
                    fill={item.color ?? palette[index % palette.length]}
                    fillOpacity={0.2}
                    strokeWidth={2}
                  />
                ))}
              </AreaChart>
              ) : (
              <BarChart data={data}>
                <CartesianGrid strokeDasharray='3 3' vertical={false} stroke='var(--border)' />
                <XAxis
                  dataKey={xField}
                  tickLine={false}
                  axisLine={false}
                  fontSize={12}
                  stroke='var(--muted-foreground)'
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  fontSize={12}
                  stroke='var(--muted-foreground)'
                />
                <Tooltip />
                {series.map((item, index) => (
                  <Bar
                    key={item.dataKey}
                    dataKey={item.dataKey}
                    name={item.name ?? item.dataKey}
                    radius={[8, 8, 0, 0]}
                    fill={item.color ?? palette[index % palette.length]}
                  />
                ))}
              </BarChart>
              )}
            </ResponsiveContainer>
          </div>
        )}
        <MetricGrid metrics={visibleMetrics} />
      </CardContent>
    </Card>
  )
}

function TraceTimeline({ trace }: { trace: AICopilotTraceStep[] }) {
  if (!trace.length) {
    return null
  }

  return (
    <div className='space-y-2'>
      {trace.map((step, index) => (
        <div
          key={`${step.stage}-${step.label}-${index}`}
          className='rounded-2xl border border-border bg-background/80 px-3 py-3'
        >
          <div className='flex items-center justify-between gap-3'>
            <p className='text-sm font-medium text-foreground'>{step.label}</p>
            <span className='text-[11px] uppercase tracking-[0.18em] text-muted-foreground'>
              {step.status ?? step.stage}
            </span>
          </div>
          <p className='mt-1 text-[11px] text-muted-foreground'>{step.stage}</p>
          {step.detail ? (
            <p className='mt-2 text-xs leading-5 text-foreground'>{step.detail}</p>
          ) : null}
        </div>
      ))}
    </div>
  )
}

function EmptyState() {
  return (
    <div className='flex min-h-[28rem] flex-col items-center justify-center rounded-[1.5rem] border border-dashed border-border bg-card/60 px-6 text-center'>
      <div className='flex size-14 items-center justify-center rounded-full border border-border bg-background/80 text-primary'>
        <Bot className='size-6' />
      </div>
      <h3 className='mt-4 text-lg font-semibold text-foreground'>等待 Copilot 会话</h3>
      <p className='mt-2 max-w-md text-sm leading-6 text-muted-foreground'>
        选择左侧会话，或者新建一个会话开始发送消息。这里会承载后端返回的真实消息流和结构化动作。
      </p>
    </div>
  )
}

function ShellSkeleton() {
  return (
    <div className='space-y-3'>
      <div className='h-20 animate-pulse rounded-[1.5rem] border border-border bg-card/60' />
      <div className='ml-auto h-24 w-[72%] animate-pulse rounded-[1.5rem] border border-border bg-card/60' />
      <div className='h-24 animate-pulse rounded-[1.5rem] border border-border bg-card/60' />
      <div className='ml-auto h-28 w-[70%] animate-pulse rounded-[1.5rem] border border-border bg-card/60' />
    </div>
  )
}

function formatConfirmationStatus(status: string) {
  switch (status) {
    case 'confirmed':
      return '已确认'
    case 'cancelled':
      return '已取消'
    case 'failed':
      return '确认失败'
    case 'pending':
    default:
      return '待确认'
  }
}

function resolveRetryButtonLabel(
  block:
    | Extract<AICopilotMessageBlock, { type: 'failure' }>
    | Extract<AICopilotMessageBlock, { type: 'retry' }>,
  fallback: string
) {
  const actionLabel = resolveTaskHandleActionLabel(block)
  if (!actionLabel) {
    return fallback
  }

  return `重试${actionLabel}`
}

function resolveTaskHandleActionLabel(
  block:
    | Extract<AICopilotMessageBlock, { type: 'result' }>
    | Extract<AICopilotMessageBlock, { type: 'failure' }>
    | Extract<AICopilotMessageBlock, { type: 'retry' }>
) {
  if (!isTaskHandleSource(block.sourceKey)) {
    return null
  }

  const argumentsValue = resolveTaskHandleArguments(block.result)
  const action = String(argumentsValue?.action ?? '').toUpperCase()

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

function resolveTaskHandleTaskId(
  block:
    | Extract<AICopilotMessageBlock, { type: 'result' }>
    | Extract<AICopilotMessageBlock, { type: 'failure' }>
    | Extract<AICopilotMessageBlock, { type: 'retry' }>
) {
  if (!isTaskHandleSource(block.sourceKey)) {
    return null
  }

  const argumentsValue = resolveTaskHandleArguments(block.result)
  const taskId = String(argumentsValue?.taskId ?? '').trim()

  return taskId || null
}

function resolveTaskHandleArguments(result?: Record<string, unknown>) {
  if (!result || typeof result !== 'object') {
    return null
  }

  const argumentsValue = result.arguments
  if (
    !argumentsValue ||
    typeof argumentsValue !== 'object' ||
    Array.isArray(argumentsValue)
  ) {
    return result
  }

  return argumentsValue as Record<string, unknown>
}

function isTaskHandleSource(sourceKey?: string) {
  return (
    sourceKey === 'task.handle' ||
    sourceKey === 'workflow.task.complete' ||
    sourceKey === 'workflow.task.reject'
  )
}

function shouldShowRawResultPayload(
  block: Extract<AICopilotMessageBlock, { type: 'result' }>
) {
  if (!block.result || Object.keys(block.result).length === 0) {
    return false
  }

  if (
    block.sourceKey === 'process.start' ||
    block.sourceKey === 'task.handle' ||
    block.sourceKey === 'plm.bill.query' ||
    block.sourceKey === 'plm.change.summary'
  ) {
    return false
  }

  return !block.fields?.length && !block.metrics?.length
}

function toEditableFormData(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }

  return { ...(value as Record<string, unknown>) }
}

function upsertSessionSummary(
  sessions: AICopilotSessionSummary[],
  session: AICopilotSession
) {
  const summary: AICopilotSessionSummary = {
    sessionId: session.sessionId,
    title: session.title,
    preview: session.preview,
    status: session.status,
    updatedAt: session.updatedAt,
    messageCount: session.messageCount,
    contextTags: session.contextTags,
  }

  return [
    summary,
    ...sessions.filter((item) => item.sessionId !== summary.sessionId),
  ]
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}
