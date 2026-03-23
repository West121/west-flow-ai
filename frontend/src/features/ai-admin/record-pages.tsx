import { type ColumnDef } from '@tanstack/react-table'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import {
  getAiConfirmationDetail,
  getAiConversationDetail,
  getAiToolCallDetail,
  listAiConfirmations,
  listAiConversations,
  listAiToolCalls,
  type AiAuditRecord,
  type AiConfirmationRecord,
  type AiConversationRecord,
  type AiMessageRecord,
  type AiToolCallRecord,
} from '@/lib/api/ai-admin'
import {
  AiInfoCard,
  AiKeyValueGrid,
  AiJsonBlock,
  AiPageErrorState,
  AiStatusBadge,
  formatDateTime,
  renderTags,
} from './shared'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

type PageSearchProps = {
  search: ListQuerySearch
  navigate: NavigateFn
}

function emptyPage(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

function statusLabel(value: string) {
  switch (value) {
    case 'ACTIVE':
      return '活跃'
    case 'ARCHIVED':
      return '已归档'
    case 'PENDING':
      return '待处理'
    case 'CONFIRMED':
      return '已确认'
    case 'EXECUTED':
      return '已执行'
    case 'FAILED':
      return '已失败'
    case 'REJECTED':
      return '已拒绝'
    case 'APPROVED':
      return '已通过'
    case 'READ':
      return '已读'
    case 'UNREAD':
      return '未读'
    default:
      return value
  }
}

function statusVariant(value: string) {
  switch (value) {
    case 'FAILED':
    case 'REJECTED':
      return 'destructive' as const
    case 'ARCHIVED':
    case 'READ':
    case 'APPROVED':
    case 'EXECUTED':
    case 'CONFIRMED':
      return 'secondary' as const
    default:
      return 'outline' as const
  }
}

function listSummary(search: ListQuerySearch, total: number) {
  return [
    { label: '总条数', value: `${total}`, hint: `当前查询页：${search.page} / ${search.pageSize}` },
    { label: '筛选项', value: `${search.filters?.length ?? 0}`, hint: '支持关键字、筛选、排序和分组' },
    { label: '分组项', value: `${search.groups?.length ?? 0}`, hint: '协议已保留分组能力' },
  ]
}

function renderMessageTable(messages: AiMessageRecord[]) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>角色</TableHead>
          <TableHead>作者</TableHead>
          <TableHead>内容</TableHead>
          <TableHead>更新时间</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {messages.length > 0 ? (
          messages.map((message) => (
            <TableRow key={message.messageId}>
              <TableCell>{message.role}</TableCell>
              <TableCell>{message.authorName}</TableCell>
              <TableCell className='max-w-[520px] whitespace-pre-wrap'>{message.content}</TableCell>
              <TableCell>{formatDateTime(message.updatedAt)}</TableCell>
            </TableRow>
          ))
        ) : (
          <TableRow>
            <TableCell colSpan={4} className='py-8 text-center text-muted-foreground'>
              暂无消息
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  )
}

function renderToolCallTable(toolCalls: AiToolCallRecord[]) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>工具</TableHead>
          <TableHead>类型</TableHead>
          <TableHead>来源</TableHead>
          <TableHead>状态</TableHead>
          <TableHead>摘要</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {toolCalls.length > 0 ? (
          toolCalls.map((toolCall) => (
            <TableRow key={toolCall.toolCallId}>
              <TableCell>{toolCall.toolKey}</TableCell>
              <TableCell>{toolCall.toolType}</TableCell>
              <TableCell>{toolCall.toolSource}</TableCell>
              <TableCell>
                <Badge variant={statusVariant(toolCall.status)}>{statusLabel(toolCall.status)}</Badge>
              </TableCell>
              <TableCell>{toolCall.summary || '-'}</TableCell>
            </TableRow>
          ))
        ) : (
          <TableRow>
            <TableCell colSpan={5} className='py-8 text-center text-muted-foreground'>
              暂无工具调用
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  )
}

function renderConfirmationTable(confirmations: AiConfirmationRecord[]) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>状态</TableHead>
          <TableHead>是否通过</TableHead>
          <TableHead>说明</TableHead>
          <TableHead>处理人</TableHead>
          <TableHead>更新时间</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {confirmations.length > 0 ? (
          confirmations.map((confirmation) => (
            <TableRow key={confirmation.confirmationId}>
              <TableCell>
                <Badge variant={statusVariant(confirmation.status)}>{statusLabel(confirmation.status)}</Badge>
              </TableCell>
              <TableCell>{confirmation.approved ? '是' : '否'}</TableCell>
              <TableCell>{confirmation.comment || '-'}</TableCell>
              <TableCell>{confirmation.resolvedBy || '-'}</TableCell>
              <TableCell>{formatDateTime(confirmation.updatedAt)}</TableCell>
            </TableRow>
          ))
        ) : (
          <TableRow>
            <TableCell colSpan={5} className='py-8 text-center text-muted-foreground'>
              暂无确认记录
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  )
}

function renderAuditTable(audits: AiAuditRecord[]) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>动作</TableHead>
          <TableHead>摘要</TableHead>
          <TableHead>操作人</TableHead>
          <TableHead>时间</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {audits.length > 0 ? (
          audits.map((audit) => (
            <TableRow key={audit.auditId}>
              <TableCell>{audit.actionType}</TableCell>
              <TableCell>{audit.summary || '-'}</TableCell>
              <TableCell>{audit.operatorUserId}</TableCell>
              <TableCell>{formatDateTime(audit.occurredAt)}</TableCell>
            </TableRow>
          ))
        ) : (
          <TableRow>
            <TableCell colSpan={4} className='py-8 text-center text-muted-foreground'>
              暂无审计轨迹
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  )
}

// ========================= Conversation =========================

export function AiConversationListPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'conversations', search],
    queryFn: () => listAiConversations(search),
  })

  const data = query.data ?? emptyPage(search)

  const columns: ColumnDef<AiConversationRecord, unknown>[] = [
    { accessorKey: 'title', header: '标题' },
    { accessorKey: 'preview', header: '摘要' },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => <Badge variant={statusVariant(row.original.status)}>{statusLabel(row.original.status)}</Badge>,
    },
    { accessorKey: 'messageCount', header: '消息数' },
    { accessorKey: 'operatorUserId', header: '操作人' },
    { accessorKey: 'updatedAt', header: '更新时间', cell: ({ row }) => formatDateTime(row.original.updatedAt) },
  ]

  return (
    <ResourceListPage
      title='Conversation 会话列表'
      description='统一查看 AI Copilot 会话、上下文标签和消息数量。'
      endpoint='/system/ai/conversations/page'
      searchPlaceholder='搜索会话标题、摘要或操作人'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listSummary(search, data.total)}
      createAction={undefined}
    />
  )
}

export function AiConversationDetailPage({ conversationId }: { conversationId: string }) {
  const query = useQuery({
    queryKey: ['ai-admin', 'conversations', conversationId],
    queryFn: () => getAiConversationDetail(conversationId),
  })

  if (query.isLoading) {
    return (
      <PageShell title='Conversation 会话详情' description='查看会话、消息、工具调用、确认和审计。'>
        <Card>
          <CardContent className='space-y-4 py-6'>
            <div className='h-8 w-1/3 rounded bg-muted' />
            <div className='h-40 rounded bg-muted' />
          </CardContent>
        </Card>
      </PageShell>
    )
  }

  if (query.isError || !query.data) {
    return <AiPageErrorState title='Conversation 会话详情' description='查看会话详情失败。' listHref='/system/ai/conversations/list' />
  }

  const detail = query.data

  return (
    <PageShell
      title='Conversation 会话详情'
      description='查看会话、消息、工具调用、确认和审计。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/system/ai/conversations/list' search={{}}>
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='space-y-6'>
        <AiInfoCard title='会话信息'>
          <AiKeyValueGrid
            items={[
              { label: '会话标题', value: detail.title },
              { label: '状态', value: <AiStatusBadge label={statusLabel(detail.status)} variant={statusVariant(detail.status)} /> },
              { label: '上下文标签', value: renderTags(detail.contextTags) },
              { label: '消息数', value: detail.messageCount },
              { label: '操作人', value: detail.operatorUserId },
              { label: '创建时间', value: formatDateTime(detail.createdAt) },
              { label: '更新时间', value: formatDateTime(detail.updatedAt) },
            ]}
          />
          <div className='mt-4 whitespace-pre-wrap rounded-lg border bg-muted/20 p-4 text-sm leading-6'>
            {detail.preview || '-'}
          </div>
        </AiInfoCard>
        <AiInfoCard title='消息明细'>
          {renderMessageTable(detail.messages)}
        </AiInfoCard>
        <AiInfoCard title='工具调用'>
          {renderToolCallTable(detail.toolCalls)}
        </AiInfoCard>
        <AiInfoCard title='确认记录'>
          {renderConfirmationTable(detail.confirmations)}
        </AiInfoCard>
        <AiInfoCard title='审计轨迹'>
          {renderAuditTable(detail.audits)}
        </AiInfoCard>
      </div>
    </PageShell>
  )
}

// ========================= Tool Call =========================

export function AiToolCallListPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'tool-calls', search],
    queryFn: () => listAiToolCalls(search),
  })

  const data = query.data ?? emptyPage(search)

  const columns: ColumnDef<AiToolCallRecord, unknown>[] = [
    { accessorKey: 'toolKey', header: '工具标识' },
    { accessorKey: 'toolType', header: '类型' },
    { accessorKey: 'toolSource', header: '来源' },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => <Badge variant={statusVariant(row.original.status)}>{statusLabel(row.original.status)}</Badge>,
    },
    {
      accessorKey: 'requiresConfirmation',
      header: '需确认',
      cell: ({ row }) => (row.original.requiresConfirmation ? '是' : '否'),
    },
    { accessorKey: 'createdAt', header: '创建时间', cell: ({ row }) => formatDateTime(row.original.createdAt) },
  ]

  return (
    <ResourceListPage
      title='ToolCall 记录列表'
      description='统一查看 AI 工具调用、来源、状态和确认要求。'
      endpoint='/system/ai/tool-calls/page'
      searchPlaceholder='搜索工具标识、来源或状态'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listSummary(search, data.total)}
      createAction={undefined}
    />
  )
}

export function AiToolCallDetailPage({ toolCallId }: { toolCallId: string }) {
  const query = useQuery({
    queryKey: ['ai-admin', 'tool-calls', toolCallId],
    queryFn: () => getAiToolCallDetail(toolCallId),
  })

  if (query.isLoading) {
    return (
      <PageShell title='ToolCall 记录详情' description='查看工具调用详情、参数和结果。'>
        <Card>
          <CardContent className='space-y-4 py-6'>
            <div className='h-8 w-1/3 rounded bg-muted' />
            <div className='h-40 rounded bg-muted' />
          </CardContent>
        </Card>
      </PageShell>
    )
  }

  if (query.isError || !query.data) {
    return <AiPageErrorState title='ToolCall 记录详情' description='查看工具调用详情失败。' listHref='/system/ai/tool-calls/list' />
  }

  const detail = query.data

  return (
    <PageShell
      title='ToolCall 记录详情'
      description='查看工具调用详情、参数和结果。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/system/ai/tool-calls/list' search={{}}>
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='space-y-6'>
        <AiInfoCard title='基础信息'>
          <AiKeyValueGrid
            items={[
              { label: '工具标识', value: detail.toolKey },
              { label: '工具类型', value: detail.toolType },
              { label: '工具来源', value: detail.toolSource },
              { label: '状态', value: <AiStatusBadge label={statusLabel(detail.status)} variant={statusVariant(detail.status)} /> },
              { label: '需确认', value: detail.requiresConfirmation ? '是' : '否' },
              { label: '会话 ID', value: detail.conversationId },
              { label: '确认单 ID', value: detail.confirmationId || '-' },
              { label: '创建时间', value: formatDateTime(detail.createdAt) },
              { label: '完成时间', value: formatDateTime(detail.completedAt) },
            ]}
          />
          <div className='mt-4 whitespace-pre-wrap rounded-lg border bg-muted/20 p-4 text-sm leading-6'>
            {detail.summary || '-'}
          </div>
        </AiInfoCard>
        <AiInfoCard title='参数 JSON'>
          <AiJsonBlock value={detail.argumentsJson} />
        </AiInfoCard>
        <AiInfoCard title='结果 JSON'>
          <AiJsonBlock value={detail.resultJson} />
        </AiInfoCard>
      </div>
    </PageShell>
  )
}

// ========================= Confirmation =========================

export function AiConfirmationListPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'confirmations', search],
    queryFn: () => listAiConfirmations(search),
  })

  const data = query.data ?? emptyPage(search)

  const columns: ColumnDef<AiConfirmationRecord, unknown>[] = [
    { accessorKey: 'confirmationId', header: '确认单' },
    { accessorKey: 'toolCallId', header: '工具调用' },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => <Badge variant={statusVariant(row.original.status)}>{statusLabel(row.original.status)}</Badge>,
    },
    {
      accessorKey: 'approved',
      header: '是否通过',
      cell: ({ row }) => (row.original.approved ? '是' : '否'),
    },
    { accessorKey: 'resolvedBy', header: '处理人' },
    { accessorKey: 'updatedAt', header: '更新时间', cell: ({ row }) => formatDateTime(row.original.updatedAt) },
  ]

  return (
    <ResourceListPage
      title='Confirmation 记录列表'
      description='统一查看 AI 写操作确认、审批结果和处理人。'
      endpoint='/system/ai/confirmations/page'
      searchPlaceholder='搜索确认单、工具调用或处理人'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listSummary(search, data.total)}
      createAction={undefined}
    />
  )
}

export function AiConfirmationDetailPage({ confirmationId }: { confirmationId: string }) {
  const query = useQuery({
    queryKey: ['ai-admin', 'confirmations', confirmationId],
    queryFn: () => getAiConfirmationDetail(confirmationId),
  })

  if (query.isLoading) {
    return (
      <PageShell title='Confirmation 记录详情' description='查看确认单详情、评论和处理结果。'>
        <Card>
          <CardContent className='space-y-4 py-6'>
            <div className='h-8 w-1/3 rounded bg-muted' />
            <div className='h-40 rounded bg-muted' />
          </CardContent>
        </Card>
      </PageShell>
    )
  }

  if (query.isError || !query.data) {
    return <AiPageErrorState title='Confirmation 记录详情' description='查看确认单详情失败。' listHref='/system/ai/confirmations/list' />
  }

  const detail = query.data

  return (
    <PageShell
      title='Confirmation 记录详情'
      description='查看确认单详情、评论和处理结果。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/system/ai/confirmations/list' search={{}}>
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='space-y-6'>
        <AiInfoCard title='基础信息'>
          <AiKeyValueGrid
            items={[
              { label: '确认单 ID', value: detail.confirmationId },
              { label: '工具调用 ID', value: detail.toolCallId },
              { label: '状态', value: <AiStatusBadge label={statusLabel(detail.status)} variant={statusVariant(detail.status)} /> },
              { label: '是否通过', value: detail.approved ? '是' : '否' },
              { label: '处理人', value: detail.resolvedBy || '-' },
              { label: '创建时间', value: formatDateTime(detail.createdAt) },
              { label: '处理时间', value: formatDateTime(detail.resolvedAt) },
              { label: '更新时间', value: formatDateTime(detail.updatedAt) },
            ]}
          />
          <div className='mt-4 whitespace-pre-wrap rounded-lg border bg-muted/20 p-4 text-sm leading-6'>
            {detail.comment || '-'}
          </div>
        </AiInfoCard>
      </div>
    </PageShell>
  )
}
