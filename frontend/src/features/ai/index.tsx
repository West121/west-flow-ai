import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowRight,
  BadgeCheck,
  BarChart3,
  Bot,
  CheckCircle2,
  CircleAlert,
  Clock3,
  GitBranch,
  Plus,
  RefreshCw,
  Search,
  SendHorizontal,
  Sparkles,
  SquareStack,
  TriangleAlert,
  UserRound,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { Main } from '@/components/layout/main'
import { getApiErrorMessage } from '@/lib/api/client'
import { cn } from '@/lib/utils'
import {
  confirmAICopilotConfirmation,
  type AICopilotAuditEntry,
  createAICopilotSession,
  getAICopilotSession,
  listAICopilotSessions,
  sendAICopilotMessage,
  type AICopilotConfirmationDecision,
  type AICopilotMessage,
  type AICopilotMessageBlock,
  type AICopilotSession,
  type AICopilotSessionSummary,
  type AICopilotTraceStep,
  type AICopilotToolCall,
} from '@/lib/api/ai-copilot'

const aiCopilotSessionsKey = ['ai-copilot', 'sessions'] as const

const aiCopilotSessionKey = (sessionId: string) =>
  ['ai-copilot', 'sessions', sessionId] as const

// AI Copilot 页面负责会话导航、消息流、富消息块与确认动作的完整渲染。
export function AICopilotPage({
  sourceRoute = '',
}: {
  sourceRoute?: string
}) {
  const queryClient = useQueryClient()
  const [activeSessionId, setActiveSessionId] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [draft, setDraft] = useState('')
  const [pendingConfirmationId, setPendingConfirmationId] = useState<string | null>(
    null
  )
  const bootstrappedRouteRef = useRef('')

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
    onSuccess: (updatedSession) => {
      setDraft('')
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
      !content ||
      !effectiveActiveSessionId ||
      sendMessageMutation.isPending
    ) {
      return
    }

    sendMessageMutation.mutate({
      sessionId: effectiveActiveSessionId,
      content,
    })
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
    <Main fixed fluid className='p-4 sm:p-6'>
      <div className='relative overflow-hidden rounded-[2rem] border border-white/10 bg-slate-950/90 text-slate-50 shadow-[0_30px_100px_-50px_rgba(15,23,42,0.95)] backdrop-blur-2xl'>
        <div className='pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.24),_transparent_26%),radial-gradient(circle_at_bottom_right,_rgba(245,158,11,0.18),_transparent_22%),linear-gradient(135deg,_rgba(255,255,255,0.04),_transparent_45%)]' />
        <div className='relative grid min-h-[calc(100svh-8rem)] gap-4 p-4 sm:p-5 xl:grid-cols-[320px_minmax(0,1fr)]'>
          <aside className='flex min-h-0 flex-col overflow-hidden rounded-[1.5rem] border border-white/10 bg-white/5 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]'>
            <div className='border-b border-white/10 px-4 py-4'>
              <div className='flex items-start justify-between gap-3'>
                <div>
                  <p className='text-xs uppercase tracking-[0.28em] text-cyan-200/70'>
                    AI Copilot
                  </p>
                  <h1 className='mt-2 text-lg font-semibold text-white'>
                    会话工位
                  </h1>
                  <p className='mt-1 text-sm text-slate-300'>
                    这里通过真实 HTTP API 拉取会话、消息和确认卡状态，不再使用本地内存 store。
                  </p>
                </div>
                <Button
                  type='button'
                  size='icon'
                  variant='outline'
                  className='border-white/15 bg-white/5 text-slate-50 hover:bg-white/10 hover:text-white'
                  onClick={handleCreateSession}
                >
                  <Plus />
                </Button>
              </div>

              <div className='mt-4 flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-2'>
                <Search className='size-4 text-slate-300' />
                <Input
                  value={searchTerm}
                  onChange={(event) => setSearchTerm(event.target.value)}
                  placeholder='搜索会话或标签…'
                  className='h-auto border-0 bg-transparent p-0 text-sm text-white shadow-none placeholder:text-slate-400 focus-visible:ring-0'
                />
              </div>
            </div>

            <ScrollArea className='min-h-0 flex-1'>
              <div className='space-y-3 p-4'>
                <SectionLabel
                  icon={<SquareStack className='size-3.5' />}
                  title='会话列表'
                  description={`${filteredSessions.length} 个会话`}
                />
                <div className='space-y-2'>
                  {filteredSessions.map((session) => {
                    const isActive = session.sessionId === effectiveActiveSessionId

                    return (
                      <button
                        key={session.sessionId}
                        type='button'
                        onClick={() => setActiveSessionId(session.sessionId)}
                        className={cn(
                          'group w-full rounded-2xl border px-3 py-3 text-left transition-all duration-200',
                          isActive
                            ? 'border-cyan-300/50 bg-cyan-300/12 shadow-[0_0_0_1px_rgba(34,211,238,0.12)]'
                            : 'border-white/10 bg-white/5 hover:border-white/20 hover:bg-white/10'
                        )}
                      >
                        <div className='flex items-start justify-between gap-3'>
                          <div>
                            <p className='text-sm font-medium text-white'>
                              {session.title}
                            </p>
                            <p className='mt-1 line-clamp-2 text-xs leading-5 text-slate-300'>
                              {session.preview}
                            </p>
                          </div>
                          <ArrowRight
                            className={cn(
                              'mt-0.5 size-4 shrink-0 transition-transform',
                              isActive
                                ? 'text-cyan-200'
                                : 'text-slate-400 group-hover:translate-x-0.5'
                            )}
                          />
                        </div>
                        <div className='mt-3 flex flex-wrap gap-2'>
                          {session.contextTags.slice(0, 3).map((tag) => (
                            <span
                              key={tag}
                              className='rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] text-slate-200'
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                        <div className='mt-3 flex items-center justify-between text-[11px] text-slate-400'>
                          <span>{session.messageCount} 条消息</span>
                          <span>{formatDate(session.updatedAt)}</span>
                        </div>
                      </button>
                    )
                  })}
                  {!filteredSessions.length ? (
                    <div className='rounded-2xl border border-dashed border-white/10 bg-white/5 px-4 py-6 text-sm text-slate-300'>
                      没有找到匹配的会话，先试试新的搜索词。
                    </div>
                  ) : null}
                </div>

                <Separator className='bg-white/10' />

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
                    <div className='rounded-2xl border border-dashed border-white/10 bg-white/5 px-4 py-6 text-sm text-slate-300'>
                      当前会话暂无历史记录。
                    </div>
                  ) : null}
                </div>
              </div>
            </ScrollArea>
          </aside>

          <section className='flex min-h-0 flex-col overflow-hidden rounded-[1.75rem] border border-white/10 bg-white/5 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]'>
            <div className='border-b border-white/10 px-5 py-4'>
              <div className='flex flex-wrap items-start justify-between gap-4'>
                <div>
                  <div className='flex flex-wrap items-center gap-2'>
                    <BadgePill icon={<Sparkles className='size-3.5' />}>
                      AI Copilot
                    </BadgePill>
                    {activeSession ? (
                      <BadgePill tone='subtle'>{activeSession.status}</BadgePill>
                    ) : null}
                  </div>
                  <h2 className='mt-3 text-2xl font-semibold text-white'>
                    {activeSession?.title ?? '请选择一个会话'}
                  </h2>
                  <p className='mt-2 max-w-3xl text-sm leading-6 text-slate-300'>
                    这里是毛玻璃聊天面板，消息流、确认卡、表单预览卡和统计卡都会从 HTTP API 实时读取。
                  </p>
                </div>
                <div className='flex flex-wrap items-center gap-2'>
                  <Button
                    type='button'
                    variant='outline'
                    className='border-white/15 bg-white/5 text-slate-50 hover:bg-white/10'
                    onClick={() => void activeSessionQuery.refetch()}
                  >
                    <RefreshCw className='size-4' />
                    刷新会话
                  </Button>
                  <Button
                    type='button'
                    className='bg-cyan-400 text-slate-950 hover:bg-cyan-300'
                    onClick={handleCreateSession}
                  >
                    <Plus className='size-4' />
                    新建会话
                  </Button>
                </div>
              </div>
              {loadErrorMessage ? (
                <p className='mt-3 rounded-2xl border border-rose-300/20 bg-rose-300/10 px-4 py-3 text-sm text-rose-50'>
                  {loadErrorMessage}
                </p>
              ) : null}
            </div>

            <div className='grid min-h-0 flex-1 gap-0 xl:grid-cols-[minmax(0,1fr)_340px]'>
              <div className='flex min-h-0 flex-col border-r border-white/10'>
                <ScrollArea className='min-h-0 flex-1'>
                  <div className='space-y-4 p-5'>
                    {isLoading ? (
                      <ShellSkeleton />
                    ) : activeSession ? (
                      activeSession.history.map((message) => (
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
                    ) : (
                      <EmptyState />
                    )}
                  </div>
                </ScrollArea>

                <div className='border-t border-white/10 bg-slate-950/60 p-4 backdrop-blur-xl'>
                  <div className='mb-3 flex flex-wrap gap-2'>
                    {[
                      '梳理当前待办',
                      '生成确认卡',
                      '预览表单',
                      '输出统计卡',
                    ].map((preset) => (
                      <button
                        key={preset}
                        type='button'
                        onClick={() => setDraft(preset)}
                        className='rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-slate-200 transition-colors hover:border-white/20 hover:bg-white/10'
                      >
                        {preset}
                      </button>
                    ))}
                  </div>
                  <div className='flex flex-col gap-3 md:flex-row md:items-end'>
                    <Textarea
                      value={draft}
                      onChange={(event) => setDraft(event.target.value)}
                      placeholder='输入一条 Copilot 指令，例如：帮我生成一个确认卡和统计摘要'
                      className='min-h-24 flex-1 resize-none border-white/10 bg-white/5 text-sm text-white placeholder:text-slate-400 focus-visible:ring-cyan-300/60'
                    />
                    <Button
                      type='button'
                      className='md:h-24 md:w-40'
                      disabled={
                        !draft.trim() ||
                        sendMessageMutation.isPending ||
                        !effectiveActiveSessionId
                      }
                      onClick={() => void handleSendMessage()}
                    >
                      <SendHorizontal className='size-4' />
                      {sendMessageMutation.isPending ? '发送中…' : '发送'}
                    </Button>
                  </div>
                </div>
              </div>

              <div className='flex min-h-0 flex-col bg-white/[0.03]'>
                <div className='border-b border-white/10 px-4 py-4'>
                  <div className='flex items-center justify-between gap-3'>
                    <div>
                      <p className='text-xs uppercase tracking-[0.24em] text-slate-400'>
                        结构化插槽
                      </p>
                      <h3 className='mt-2 text-sm font-semibold text-white'>
                        富消息块
                      </h3>
                    </div>
                    <BadgePill tone='subtle'>
                      text / confirm / form / stats / result / failure / trace
                    </BadgePill>
                  </div>
                </div>

                <ScrollArea className='min-h-0 flex-1'>
                  <div className='space-y-3 p-4'>
                    <InfoPanel
                      title='上下文摘要'
                      description='当前会话沿用页面、菜单和业务标签，帮助 Copilot 保持连续上下文。'
                      badge={`${activeSession?.contextTags.length ?? 0} 个标签`}
                    >
                      {activeSession?.contextTags.length ? (
                        <div className='flex flex-wrap gap-2'>
                          {activeSession.contextTags.map((tag) => (
                            <span
                              key={tag}
                              className='rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] text-slate-200'
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <EmptyInfoHint text='当前会话还没有上下文标签。' />
                      )}
                    </InfoPanel>

                    <InfoPanel
                      title='工具命中'
                      description='这里展示当前会话已经实际命中的 tool、skill 或 mcp 调用结果。'
                      badge={`${activeSession?.toolCalls.length ?? 0} 次调用`}
                    >
                      {activeSession?.toolCalls.length ? (
                        <div className='space-y-2'>
                          {activeSession.toolCalls.map((toolCall) => (
                            <ToolCallRow
                              key={toolCall.toolCallId}
                              toolCall={toolCall}
                            />
                          ))}
                        </div>
                      ) : (
                        <EmptyInfoHint text='当前会话还没有命中任何工具调用。' />
                      )}
                    </InfoPanel>

                    <InfoPanel
                      title='审计轨迹'
                      description='所有确认、执行和结果回写都会沉淀到审计流，方便排查和回放。'
                      badge={`${activeSession?.audit.length ?? 0} 条记录`}
                    >
                      {activeSession?.audit.length ? (
                        <div className='space-y-2'>
                          {activeSession.audit.map((auditEntry) => (
                            <AuditRow
                              key={auditEntry.auditId}
                              auditEntry={auditEntry}
                            />
                          ))}
                        </div>
                      ) : (
                        <EmptyInfoHint text='当前会话还没有新增审计轨迹。' />
                      )}
                    </InfoPanel>

                    {activeSession?.history.flatMap((message) =>
                      (message.blocks ?? []).map((block, index) => {
                        const confirmBlock = block.type === 'confirm' ? block : null
                        const blockConfirmationId =
                          block.type === 'form-preview' ||
                          block.type === 'failure' ||
                          block.type === 'retry'
                            ? typeof block.result?.confirmationId === 'string'
                              ? block.result.confirmationId
                              : null
                            : null

                        return (
                          <BlockCard
                            key={`${message.messageId}-${index}`}
                            block={block}
                            isPending={
                              confirmBlock
                                ? pendingConfirmationId === confirmBlock.confirmationId
                                : blockConfirmationId
                                  ? pendingConfirmationId === blockConfirmationId
                                  : false
                            }
                            onConfirm={
                              confirmBlock
                                ? () =>
                                    handleResolveConfirmation(
                                      confirmBlock.confirmationId,
                                      'confirm'
                                    )
                                : block.type === 'form-preview' && blockConfirmationId
                                  ? (argumentsOverride) =>
                                      handleResolveConfirmation(
                                        blockConfirmationId,
                                        'confirm',
                                        argumentsOverride
                                      )
                                : undefined
                            }
                            onCancel={
                              confirmBlock && confirmBlock.cancelLabel
                                ? () =>
                                    handleResolveConfirmation(
                                      confirmBlock.confirmationId,
                                      'cancel'
                                    )
                                : undefined
                            }
                            onRetry={
                              (block.type === 'failure' || block.type === 'retry') &&
                                  blockConfirmationId
                                ? () =>
                                    handleResolveConfirmation(
                                      blockConfirmationId,
                                      'confirm'
                                    )
                                : undefined
                            }
                          />
                        )
                      })
                    ) ?? null}
                  </div>
                </ScrollArea>
              </div>
            </div>
          </section>
        </div>
      </div>
    </Main>
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
      <div className='mt-0.5 flex size-8 items-center justify-center rounded-full border border-white/10 bg-white/5 text-cyan-200'>
        {icon}
      </div>
      <div>
        <p className='text-sm font-medium text-white'>{title}</p>
        <p className='text-xs text-slate-400'>{description}</p>
      </div>
    </div>
  )
}

function InfoPanel({
  title,
  description,
  badge,
  children,
}: {
  title: string
  description: string
  badge: string
  children: React.ReactNode
}) {
  return (
    <div className='rounded-[1.25rem] border border-white/10 bg-white/5 p-4'>
      <div className='flex items-start justify-between gap-3'>
        <div>
          <p className='text-sm font-semibold text-white'>{title}</p>
          <p className='mt-1 text-xs leading-5 text-slate-400'>{description}</p>
        </div>
        <BadgePill tone='subtle'>{badge}</BadgePill>
      </div>
      <div className='mt-3'>{children}</div>
    </div>
  )
}

function EmptyInfoHint({ text }: { text: string }) {
  return (
    <div className='rounded-2xl border border-dashed border-white/10 bg-slate-950/20 px-3 py-4 text-xs leading-5 text-slate-400'>
      {text}
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
          ? 'border-white/10 bg-white/5 text-slate-200'
          : 'border-cyan-300/30 bg-cyan-300/10 text-cyan-100'
      )}
    >
      {icon}
      {children}
    </span>
  )
}

function HistoryRow({ message }: { message: AICopilotMessage }) {
  return (
    <div className='rounded-2xl border border-white/10 bg-white/5 px-3 py-3'>
      <div className='flex items-center justify-between gap-3'>
        <div className='flex items-center gap-2'>
          <div
            className={cn(
              'flex size-7 items-center justify-center rounded-full text-xs font-semibold',
              message.role === 'assistant'
                ? 'bg-cyan-300/15 text-cyan-100'
                : message.role === 'system'
                  ? 'bg-amber-300/15 text-amber-100'
                  : 'bg-white/10 text-white'
            )}
          >
            {message.role === 'assistant' ? (
              <Bot className='size-3.5' />
            ) : (
              <UserRound className='size-3.5' />
            )}
          </div>
          <div>
            <p className='text-xs font-medium text-white'>{message.authorName}</p>
            <p className='text-[11px] text-slate-400'>
              {formatDate(message.createdAt)}
            </p>
          </div>
        </div>
        <ArrowRight className='size-3.5 text-slate-500' />
      </div>
      <p className='mt-2 line-clamp-2 text-xs leading-5 text-slate-300'>
        {message.content}
      </p>
    </div>
  )
}

function ToolCallRow({ toolCall }: { toolCall: AICopilotToolCall }) {
  return (
    <div className='rounded-2xl border border-white/10 bg-slate-950/25 px-3 py-3'>
      <div className='flex items-start justify-between gap-3'>
        <div>
          <p className='text-sm font-medium text-white'>{toolCall.toolKey}</p>
          <p className='mt-1 text-xs leading-5 text-slate-300'>
            {toolCall.summary}
          </p>
        </div>
        <BadgePill tone='subtle'>{formatToolCallStatus(toolCall.status)}</BadgePill>
      </div>
      <div className='mt-3 flex flex-wrap gap-2 text-[11px] text-slate-400'>
        <span>类型：{toolCall.toolType}</span>
        <span>来源：{toolCall.toolSource}</span>
        {toolCall.confirmationId ? <span>确认单：{toolCall.confirmationId}</span> : null}
        {toolCall.requiresConfirmation ? <span>写操作确认</span> : <span>读操作直执</span>}
      </div>
      <div className='mt-2 flex flex-wrap gap-3 text-[11px] text-slate-500'>
        {toolCall.createdAt ? <span>发起：{formatDate(toolCall.createdAt)}</span> : null}
        {toolCall.completedAt ? (
          <span>完成：{formatDate(toolCall.completedAt)}</span>
        ) : null}
      </div>
    </div>
  )
}

function AuditRow({ auditEntry }: { auditEntry: AICopilotAuditEntry }) {
  return (
    <div className='rounded-2xl border border-white/10 bg-slate-950/25 px-3 py-3'>
      <div className='flex items-center justify-between gap-3'>
        <p className='text-sm font-medium text-white'>
          {formatAuditAction(auditEntry.actionType)}
        </p>
        <span className='text-[11px] text-slate-400'>
          {formatDate(auditEntry.occurredAt)}
        </span>
      </div>
      <p className='mt-1 text-xs leading-5 text-slate-300'>{auditEntry.summary}</p>
      {auditEntry.toolCallId ? (
        <p className='mt-2 text-[11px] text-slate-500'>
          关联调用：{auditEntry.toolCallId}
        </p>
      ) : null}
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
  const confirmBlock = message.blocks?.find(
    (block): block is Extract<AICopilotMessageBlock, { type: 'confirm' }> =>
      block.type === 'confirm'
  )

  return (
    <div
      className={cn('flex gap-3', isUser ? 'justify-end' : 'justify-start')}
    >
      {!isUser ? (
        <div className='mt-1 flex size-9 shrink-0 items-center justify-center rounded-full border border-cyan-300/30 bg-cyan-300/10 text-cyan-100'>
          <Bot className='size-4' />
        </div>
      ) : null}

      <div
        className={cn(
          'max-w-[min(100%,52rem)] rounded-[1.5rem] border px-4 py-3 shadow-lg',
          isUser
            ? 'border-cyan-300/20 bg-cyan-300/12 text-white'
            : 'border-white/10 bg-white/5 text-slate-100'
        )}
      >
        <div className='flex items-center gap-2 text-xs text-slate-400'>
          <span className='font-medium text-white'>{message.authorName}</span>
          <span>·</span>
          <span>{formatDate(message.createdAt)}</span>
        </div>
        <p className='mt-2 text-sm leading-6 text-slate-100'>
          {message.content}
        </p>
        {message.blocks?.length ? (
          <div className='mt-4 space-y-3'>
            {message.blocks.map((block, index) => (
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
        <div className='mt-1 flex size-9 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/10 text-white'>
          <UserRound className='size-4' />
        </div>
      ) : null}
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
  const [formData, setFormData] = useState<Record<string, string>>(initialFormData)

  useEffect(() => {
    setFormData(initialFormData)
  }, [initialFormData])

  const confirmationSummary =
    linkedConfirmBlock?.summary ?? '确认后会按当前表单参数执行真实业务发起。'
  const formSchema = resolveBusinessFormSchema(block.result)
  const groupedFields = groupEditableFields(formData, formSchema)

  return (
    <Card className='border-cyan-300/20 bg-cyan-300/10 text-slate-50 shadow-none'>
      <CardHeader className='space-y-2 pb-3'>
        <CardTitle className='flex items-center gap-2 text-base'>
          <SquareStack className='size-4 text-cyan-200' />
          {block.title}
        </CardTitle>
        {block.description ? (
          <p className='text-sm leading-6 text-cyan-50/80'>{block.description}</p>
        ) : null}
        <TraceMeta block={block} />
      </CardHeader>
      <CardContent className='space-y-3 pt-0'>
        {block.fields.map((field) => (
          <div
            key={field.label}
            className='rounded-2xl border border-white/10 bg-slate-950/30 px-3 py-2'
          >
            <div className='flex items-center justify-between gap-3'>
              <span className='text-xs text-slate-300'>{field.label}</span>
              <span className='text-sm font-medium text-white'>{field.value}</span>
            </div>
            {field.hint ? (
              <p className='mt-1 text-[11px] text-slate-400'>{field.hint}</p>
            ) : null}
          </div>
        ))}

        {editable ? (
          <div
            data-testid='ai-form-preview-editor'
            className='space-y-3 rounded-2xl border border-white/10 bg-slate-950/30 p-3'
          >
            <p className='text-xs uppercase tracking-[0.18em] text-cyan-100/80'>
              可编辑业务表单
            </p>
            {groupedFields.map((group) => (
              <div key={group.title} className='space-y-3'>
                <div className='flex items-center justify-between gap-3'>
                  <p className='text-xs font-medium text-white'>{group.title}</p>
                  {group.description ? (
                    <span className='text-[11px] text-slate-400'>
                      {group.description}
                    </span>
                  ) : null}
                </div>
                <div className='grid gap-3 md:grid-cols-2'>
                  {group.fields.map((field) => (
                    <div key={field.key} className='space-y-1'>
                      <label
                        htmlFor={`ai-form-${field.key}`}
                        className='text-xs text-slate-300'
                      >
                        {field.label}
                      </label>
                      {field.multiline ? (
                        <Textarea
                          id={`ai-form-${field.key}`}
                          value={formData[field.key] ?? ''}
                          onChange={(event) =>
                            setFormData((current) => ({
                              ...current,
                              [field.key]: event.target.value,
                            }))
                          }
                          className='min-h-24 border-white/10 bg-slate-950/50 text-white'
                        />
                      ) : (
                        <Input
                          id={`ai-form-${field.key}`}
                          type={field.inputType}
                          value={formData[field.key] ?? ''}
                          onChange={(event) =>
                            setFormData((current) => ({
                              ...current,
                              [field.key]: event.target.value,
                            }))
                          }
                          className='border-white/10 bg-slate-950/50 text-white'
                        />
                      )}
                      {field.hint ? (
                        <p className='text-[11px] leading-5 text-slate-400'>
                          {field.hint}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              </div>
            ))}
            <p className='text-xs leading-5 text-slate-300'>{confirmationSummary}</p>
            <div className='flex flex-wrap gap-2'>
              <Button
                type='button'
                size='sm'
                data-testid='ai-form-preview-submit'
                className='bg-cyan-200 text-slate-950 hover:bg-cyan-100'
                onClick={() =>
                  onConfirm?.({
                    processKey: block.result?.processKey,
                    businessType: block.result?.businessType,
                    sceneCode: block.result?.sceneCode,
                    formData,
                  })
                }
                disabled={!onConfirm || isPending}
              >
                {isPending ? '提交中…' : '确认并发起'}
              </Button>
            </div>
          </div>
        ) : null}

        {block.trace?.length ? <TraceTimeline trace={block.trace} /> : null}
      </CardContent>
    </Card>
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
    case 'confirm': {
      const status = block.status ?? 'pending'
      const statusLabel = formatConfirmationStatus(status)

      return (
        <Card
          className={cn(
            'shadow-none',
            status === 'pending'
              ? 'border-amber-300/20 bg-amber-300/10 text-slate-50'
              : status === 'confirmed'
                ? 'border-emerald-300/20 bg-emerald-300/10 text-slate-50'
                : 'border-white/10 bg-white/5 text-slate-50'
          )}
        >
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <CheckCircle2 className='size-4 text-amber-200' />
              {block.title}
            </CardTitle>
            <p className='text-sm leading-6 text-amber-50/85'>{block.summary}</p>
            {block.detail ? (
              <p className='text-xs leading-5 text-amber-50/65'>{block.detail}</p>
            ) : null}
            <div className='flex flex-wrap items-center gap-2 pt-1'>
              <BadgePill tone='subtle'>{statusLabel}</BadgePill>
              {block.resolvedBy ? (
                <span className='text-xs text-slate-300'>
                  处理人：{block.resolvedBy}
                </span>
              ) : null}
              {block.resolvedAt ? (
                <span className='text-xs text-slate-400'>
                  {formatDate(block.resolvedAt)}
                </span>
              ) : null}
            </div>
            {block.resolutionNote ? (
              <p className='text-xs leading-5 text-slate-300'>
                {block.resolutionNote}
              </p>
            ) : null}
          </CardHeader>
          {status === 'pending' ? (
            <CardContent className='flex flex-wrap gap-2 pt-0'>
              <Button
                type='button'
                size='sm'
                className='bg-amber-200 text-slate-950 hover:bg-amber-100'
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
                  className='border-white/15 bg-white/5 text-slate-50 hover:bg-white/10'
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
        <Card className='border-emerald-300/20 bg-emerald-300/10 text-slate-50 shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <BarChart3 className='size-4 text-emerald-200' />
              {block.title}
            </CardTitle>
            {block.description ? (
              <p className='text-sm leading-6 text-emerald-50/80'>
                {block.description}
              </p>
            ) : null}
          </CardHeader>
          <CardContent className='grid gap-2 pt-0 sm:grid-cols-3'>
            {block.metrics.map((metric) => (
              <div
                key={metric.label}
                className='rounded-2xl border border-white/10 bg-slate-950/30 px-3 py-3'
              >
                <div className='flex items-center gap-2'>
                  <span className='text-[11px] text-slate-400'>
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
                            : 'bg-white/10 text-slate-200'
                      )}
                    >
                      {metric.tone}
                    </span>
                  ) : null}
                </div>
                <p className='mt-2 text-lg font-semibold text-white'>
                  {metric.value}
                </p>
                {metric.hint ? (
                  <p className='mt-1 text-[11px] text-slate-400'>{metric.hint}</p>
                ) : null}
              </div>
            ))}
          </CardContent>
        </Card>
      )
    case 'trace':
      return (
        <Card className='border-sky-300/20 bg-sky-300/10 text-slate-50 shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <GitBranch className='size-4 text-sky-200' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-sky-50/85'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-sky-50/70'>{block.detail}</p>
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
        <Card className='border-emerald-300/20 bg-emerald-300/10 text-slate-50 shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <BadgeCheck className='size-4 text-emerald-200' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-emerald-50/85'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-emerald-50/70'>{block.detail}</p>
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
              <pre className='overflow-x-auto rounded-2xl border border-white/10 bg-slate-950/40 px-3 py-3 text-xs leading-5 text-slate-200'>
                {JSON.stringify(block.result, null, 2)}
              </pre>
            ) : null}
            {block.trace?.length ? <TraceTimeline trace={block.trace} /> : null}
          </CardContent>
        </Card>
      )
    case 'failure':
      return (
        <Card className='border-rose-300/20 bg-rose-300/10 text-slate-50 shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <TriangleAlert className='size-4 text-rose-200' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-rose-50/85'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-rose-50/70'>{block.detail}</p>
            ) : null}
            <TraceMeta block={block} />
          </CardHeader>
          <CardContent className='space-y-3 pt-0'>
            <ExecutionSummaryBanner
              tone='danger'
              block={block}
              defaultLabel='失败原因'
            />
            {block.failure ? (
              <div className='rounded-2xl border border-white/10 bg-slate-950/30 px-3 py-3'>
                <div className='flex items-center justify-between gap-3'>
                  <span className='text-xs text-slate-300'>错误码</span>
                  <span className='text-sm font-medium text-white'>
                    {block.failure.code}
                  </span>
                </div>
                <p className='mt-2 text-sm text-white'>{block.failure.message}</p>
                {block.failure.detail ? (
                  <p className='mt-1 text-[11px] leading-5 text-slate-300'>
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
                  className='bg-rose-200 text-slate-950 hover:bg-rose-100'
                  onClick={onRetry}
                  disabled={!onRetry || isPending}
                >
                  {isPending
                    ? '重试中…'
                    : resolveRetryButtonLabel(block, '重新执行')}
                </Button>
              </div>
            ) : null}
            {block.trace?.length ? <TraceTimeline trace={block.trace} /> : null}
          </CardContent>
        </Card>
      )
    case 'retry':
      return (
        <Card className='border-amber-300/20 bg-amber-300/10 text-slate-50 shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <RefreshCw className='size-4 text-amber-200' />
              {block.title}
            </CardTitle>
            {block.summary ? (
              <p className='text-sm leading-6 text-amber-50/85'>{block.summary}</p>
            ) : null}
            {block.detail ? (
              <p className='text-xs leading-5 text-amber-50/70'>{block.detail}</p>
            ) : null}
            <TraceMeta block={block} />
          </CardHeader>
          <CardContent className='space-y-3 pt-0'>
            <ExecutionSummaryBanner
              tone='warning'
              block={block}
              defaultLabel='重试信息'
            />
            <FieldList fields={block.fields} />
            <MetricGrid metrics={block.metrics} />
            <div className='flex gap-2'>
              <Button
                type='button'
                size='sm'
                className='bg-amber-200 text-slate-950 hover:bg-amber-100'
                onClick={onRetry}
                disabled={!onRetry || isPending}
              >
                {isPending
                  ? '重试中…'
                  : resolveRetryButtonLabel(block, '按当前参数重试')}
              </Button>
            </div>
            {block.trace?.length ? <TraceTimeline trace={block.trace} /> : null}
          </CardContent>
        </Card>
      )
    case 'text':
    default:
      return (
        <Card className='border-white/10 bg-white/5 text-slate-50 shadow-none'>
          <CardHeader className='space-y-2 pb-3'>
            <CardTitle className='flex items-center gap-2 text-base'>
              <CircleAlert className='size-4 text-cyan-200' />
              {block.title ?? '文本块'}
            </CardTitle>
          </CardHeader>
          <CardContent className='pt-0 text-sm leading-6 text-slate-200'>
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
  const bannerClass =
    tone === 'success'
      ? 'border-emerald-300/20 bg-emerald-300/10 text-emerald-50'
      : tone === 'danger'
        ? 'border-rose-300/20 bg-rose-300/10 text-rose-50'
        : 'border-amber-300/20 bg-amber-300/10 text-amber-50'

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
        {block.sourceName ? <span>命中：{block.sourceName}</span> : null}
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
          className='rounded-2xl border border-white/10 bg-slate-950/30 px-3 py-2'
        >
          <div className='flex items-center justify-between gap-3'>
            <span className='text-xs text-slate-300'>{field.label}</span>
            <span className='text-sm font-medium text-white'>{field.value}</span>
          </div>
          {field.hint ? (
            <p className='mt-1 text-[11px] text-slate-400'>{field.hint}</p>
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
          className='rounded-2xl border border-white/10 bg-slate-950/30 px-3 py-3'
        >
          <div className='flex items-center gap-2'>
            <span className='text-[11px] text-slate-400'>{metric.label}</span>
            {metric.tone ? (
              <span
                className={cn(
                  'rounded-full px-2 py-0.5 text-[10px] uppercase tracking-[0.18em]',
                  metric.tone === 'positive'
                    ? 'bg-emerald-300/15 text-emerald-100'
                    : metric.tone === 'warning'
                      ? 'bg-amber-300/15 text-amber-100'
                      : 'bg-white/10 text-slate-200'
                )}
              >
                {metric.tone}
              </span>
            ) : null}
          </div>
          <p className='mt-2 text-lg font-semibold text-white'>{metric.value}</p>
          {metric.hint ? (
            <p className='mt-1 text-[11px] text-slate-400'>{metric.hint}</p>
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
  if (!block.sourceType && !block.sourceName && !block.sourceKey && !block.toolType) {
    return null
  }

  return (
    <div className='flex flex-wrap gap-2 pt-1'>
      {block.sourceType ? <BadgePill tone='subtle'>来源：{block.sourceType}</BadgePill> : null}
      {block.toolType ? <BadgePill tone='subtle'>工具：{block.toolType}</BadgePill> : null}
      {block.sourceName ? (
        <span className='text-xs text-slate-300'>命中：{block.sourceName}</span>
      ) : null}
      {block.sourceKey ? (
        <span className='text-xs text-slate-400'>键：{block.sourceKey}</span>
      ) : null}
    </div>
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
          className='rounded-2xl border border-white/10 bg-slate-950/30 px-3 py-3'
        >
          <div className='flex items-center justify-between gap-3'>
            <p className='text-sm font-medium text-white'>{step.label}</p>
            <span className='text-[11px] uppercase tracking-[0.18em] text-slate-400'>
              {step.status ?? step.stage}
            </span>
          </div>
          <p className='mt-1 text-[11px] text-slate-400'>{step.stage}</p>
          {step.detail ? (
            <p className='mt-2 text-xs leading-5 text-slate-200'>{step.detail}</p>
          ) : null}
        </div>
      ))}
    </div>
  )
}

function EmptyState() {
  return (
    <div className='flex min-h-[28rem] flex-col items-center justify-center rounded-[1.5rem] border border-dashed border-white/10 bg-white/5 px-6 text-center'>
      <div className='flex size-14 items-center justify-center rounded-full border border-white/10 bg-white/5 text-cyan-200'>
        <Bot className='size-6' />
      </div>
      <h3 className='mt-4 text-lg font-semibold text-white'>等待 Copilot 会话</h3>
      <p className='mt-2 max-w-md text-sm leading-6 text-slate-300'>
        选择左侧会话，或者新建一个会话开始发送消息。这里会承载后端返回的真实消息流和结构化动作。
      </p>
    </div>
  )
}

function ShellSkeleton() {
  return (
    <div className='space-y-3'>
      <div className='h-20 animate-pulse rounded-[1.5rem] border border-white/10 bg-white/5' />
      <div className='ml-auto h-24 w-[72%] animate-pulse rounded-[1.5rem] border border-white/10 bg-white/5' />
      <div className='h-24 animate-pulse rounded-[1.5rem] border border-white/10 bg-white/5' />
      <div className='ml-auto h-28 w-[70%] animate-pulse rounded-[1.5rem] border border-white/10 bg-white/5' />
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

function formatToolCallStatus(status: string) {
  switch (status) {
    case 'SUCCEEDED':
      return '执行成功'
    case 'PENDING_CONFIRMATION':
      return '待确认'
    case 'FAILED':
      return '执行失败'
    case 'CANCELLED':
      return '已取消'
    case 'PENDING':
    default:
      return '执行中'
  }
}

function formatAuditAction(actionType: string) {
  switch (actionType) {
    case 'TOOL_CALL_CREATED':
      return '已创建调用'
    case 'TOOL_CALL_CONFIRMED':
      return '已确认执行'
    case 'TOOL_CALL_CANCELLED':
      return '已取消执行'
    case 'TOOL_CALL_COMPLETED':
      return '已完成执行'
    case 'TOOL_CALL_FAILED':
      return '执行失败'
    default:
      return actionType
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

  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, item]) => [
      key,
      item == null ? '' : String(item),
    ])
  )
}

type FormFieldSchema = {
  key: string
  label: string
  hint?: string
  multiline?: boolean
  inputType?: 'text' | 'number' | 'date'
}

type FormFieldGroup = {
  title: string
  description?: string
  fields: FormFieldSchema[]
}

function resolveBusinessFormSchema(result: Record<string, unknown> | null | undefined) {
  const businessType = String(result?.businessType ?? '')
  const processKey = String(result?.processKey ?? '')

  const schemaByBusinessType: Record<string, FormFieldGroup[]> = {
    OA_LEAVE: [
      {
        title: '请假信息',
        description: '先确认请假时长和请假原因。',
        fields: [
          { key: 'days', label: '请假天数', inputType: 'number', hint: '支持半天或多天场景时可再扩展。' },
          { key: 'reason', label: '请假原因', multiline: true, hint: '建议写清具体事项和时间安排。' },
        ],
      },
    ],
    OA_EXPENSE: [
      {
        title: '报销信息',
        description: '确认金额和报销事由后再发起。',
        fields: [
          { key: 'amount', label: '报销金额', inputType: 'number', hint: '当前使用文本输入承接金额。' },
          { key: 'reason', label: '报销事由', multiline: true, hint: '建议补充票据和费用背景。' },
        ],
      },
    ],
    OA_COMMON: [
      {
        title: '通用申请',
        description: '通用场景会保留标题和正文两块。',
        fields: [
          { key: 'title', label: '申请标题', hint: '概括本次申请主题。' },
          { key: 'content', label: '申请内容', multiline: true, hint: '补充详细背景和诉求。' },
        ],
      },
    ],
    PLM_ECR: [
      {
        title: 'ECR 变更申请',
        description: '围绕变更标题、原因和影响产品补齐信息。',
        fields: [
          { key: 'changeTitle', label: '变更标题' },
          { key: 'affectedProductCode', label: '影响产品编码', hint: '可填写单个或多个产品编码。' },
          { key: 'priorityLevel', label: '优先级', hint: '如需更高优先级，可直接调整。' },
          { key: 'changeReason', label: '变更原因', multiline: true },
        ],
      },
    ],
    PLM_ECO: [
      {
        title: 'ECO 执行单',
        description: '确认执行标题、生效日期和执行计划。',
        fields: [
          { key: 'executionTitle', label: '执行标题' },
          { key: 'effectiveDate', label: '生效日期', inputType: 'date' },
          { key: 'changeReason', label: '变更原因', multiline: true },
          { key: 'executionPlan', label: '执行计划', multiline: true },
        ],
      },
    ],
    PLM_MATERIAL: [
      {
        title: '物料主数据变更',
        description: '确认物料编码、名称和变更类型。',
        fields: [
          { key: 'materialCode', label: '物料编码' },
          { key: 'materialName', label: '物料名称' },
          { key: 'changeType', label: '变更类型' },
          { key: 'changeReason', label: '变更原因', multiline: true },
        ],
      },
    ],
  }

  return schemaByBusinessType[businessType] ?? schemaByBusinessType[processKey.toUpperCase()] ?? []
}

function groupEditableFields(
  formData: Record<string, string>,
  schemaGroups: FormFieldGroup[]
) {
  const remainingKeys = new Set(Object.keys(formData))
  const groups = schemaGroups.map((group) => {
    const fields = group.fields
      .filter((field) => field.key in formData)
      .map((field) => {
        remainingKeys.delete(field.key)
        return field
      })

    return {
      ...group,
      fields,
    }
  }).filter((group) => group.fields.length > 0)

  if (remainingKeys.size > 0) {
    groups.push({
      title: '扩展参数',
      description: '这些字段暂未映射到业务卡片，仍然可以直接编辑。',
      fields: Array.from(remainingKeys).map((key) => ({
        key,
        label: key,
        multiline: (formData[key] ?? '').length > 40,
      })),
    })
  }

  if (!groups.length) {
    groups.push({
      title: '业务参数',
      fields: Object.keys(formData).map((key) => ({
        key,
        label: key,
        multiline: (formData[key] ?? '').length > 40,
      })),
    })
  }

  return groups
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
    hour12: false,
  }).format(date)
}
