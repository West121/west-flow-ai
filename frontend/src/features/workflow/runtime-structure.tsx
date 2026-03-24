import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  buildRuntimeStructureTree,
  mergeRuntimeStructureLinks,
  resolveRuntimeStructureKindLabel,
  resolveRuntimeStructureTargetInstanceId,
  type RuntimeStructureTreeNode,
  type RuntimeStructureLink,
} from './runtime-structure-utils'

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function resolveStructureTargetLabel(link: RuntimeStructureLink) {
  if (link.triggerMode === 'DYNAMIC_BUILD') {
    return '动态构建实例'
  }

  if (
    link.triggerMode === 'APPEND' ||
    link.runtimeLinkType === 'ADHOC_TASK' ||
    link.appendType === 'TASK'
  ) {
    return '附属任务'
  }

  if (
    link.runtimeLinkType === 'ADHOC_SUBPROCESS' ||
    link.appendType === 'SUBPROCESS'
  ) {
    return '附属子流程实例'
  }

  return '子流程实例'
}

function resolveLinkField(link: RuntimeStructureLink, field: string) {
  const value = (link as RuntimeStructureLink & Record<string, unknown>)[field]
  if (typeof value === 'string' && value.trim()) {
    return value.trim()
  }

  return null
}

function hasAnyLinkField(link: RuntimeStructureLink, fields: string[]) {
  return fields.some((field) => resolveLinkField(link, field))
}

function RuntimeStructureFieldGrid({
  items,
}: {
  items: Array<{ label: string; value: string | null | undefined }>
}) {
  const visibleItems = items.filter((item) => Boolean(item.value))
  if (!visibleItems.length) {
    return null
  }

  return (
    <div className='grid gap-2 text-sm text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
      {visibleItems.map((item) => (
        <div key={item.label}>
          {item.label}：{item.value ?? '--'}
        </div>
      ))}
    </div>
  )
}

function RuntimeStructureItem({
  node,
  currentInstanceId,
  depth,
  onConfirmParentResume,
  pendingLinkId,
}: {
  node: RuntimeStructureTreeNode
  currentInstanceId: string
  depth: number
  onConfirmParentResume?: ((link: RuntimeStructureLink) => void) | undefined
  pendingLinkId?: string | null
}) {
  const { link, children } = node
  const targetInstanceId = resolveRuntimeStructureTargetInstanceId(link)
  const isCurrentParent = link.parentInstanceId === currentInstanceId
  const isCurrentChild = targetInstanceId === currentInstanceId
  const canConfirmParentResume =
    link.linkType === 'CALL_ACTIVITY' &&
    link.status === 'WAIT_PARENT_CONFIRM' &&
    typeof onConfirmParentResume === 'function'

  return (
    <div className='space-y-3'>
      <div
        className='space-y-2 rounded-lg border p-4'
        style={{ marginLeft: depth > 0 ? `${depth * 20}px` : undefined }}
      >
        <div className='flex flex-wrap items-center gap-2'>
          <Badge variant='secondary'>
            {resolveRuntimeStructureKindLabel(link)}
          </Badge>
          <Badge variant='outline'>{link.status}</Badge>
          {link.triggerMode ? (
            <Badge variant='outline'>{link.triggerMode}</Badge>
          ) : null}
          {isCurrentParent ? (
            <Badge variant='outline'>当前主流程</Badge>
          ) : null}
          {isCurrentChild ? (
            <Badge variant='outline'>当前子流程</Badge>
          ) : null}
          {canConfirmParentResume ? (
            <Button
              type='button'
              size='sm'
              variant='outline'
              onClick={() => onConfirmParentResume?.(link)}
              disabled={pendingLinkId === link.linkId}
            >
              {pendingLinkId === link.linkId ? '确认中...' : '父流程确认恢复'}
            </Button>
          ) : null}
        </div>

        <div className='grid gap-2 text-sm text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
          <div>主流程实例：{link.parentInstanceId}</div>
          <div>
            {resolveStructureTargetLabel(link)}：
            {targetInstanceId ?? '--'}
          </div>
          <div>调用节点：{link.parentNodeId ?? link.sourceNodeId ?? '--'}</div>
          <div>源任务：{link.sourceTaskId ?? '--'}</div>
          <div>结构来源：{link.runtimeLinkType ?? link.linkType ?? '--'}</div>
          <div>子流程编码：{link.calledProcessKey ?? '--'}</div>
          <div>子流程定义：{link.calledDefinitionId ?? '--'}</div>
          <div>目标用户：{link.targetUserId ?? '--'}</div>
          <div>终止策略：{link.terminatePolicy ?? '--'}</div>
          <div>完成策略：{link.childFinishPolicy ?? '--'}</div>
          <div>创建时间：{formatDateTime(link.createdAt)}</div>
          <div>结束时间：{formatDateTime(link.finishedAt)}</div>
        </div>

        {hasAnyLinkField(link, [
          'callScope',
          'joinMode',
          'childStartStrategy',
          'parentResumeStrategy',
        ]) ? (
          <div className='space-y-2 rounded-md border bg-muted/20 p-3'>
            <div className='text-sm font-medium'>子流程策略</div>
            <RuntimeStructureFieldGrid
              items={[
                { label: '调用范围', value: resolveLinkField(link, 'callScope') },
                { label: '汇合模式', value: resolveLinkField(link, 'joinMode') },
                {
                  label: '子流程启动策略',
                  value: resolveLinkField(link, 'childStartStrategy'),
                },
                {
                  label: '父流程恢复策略',
                  value: resolveLinkField(link, 'parentResumeStrategy'),
                },
              ]}
            />
          </div>
        ) : null}

        {hasAnyLinkField(link, [
          'buildMode',
          'sourceMode',
          'executionStrategy',
          'fallbackStrategy',
          'resolvedSourceMode',
          'resolutionPath',
          'templateSource',
        ]) ? (
          <div className='space-y-2 rounded-md border bg-muted/20 p-3'>
            <div className='text-sm font-medium'>动态构建策略</div>
            <RuntimeStructureFieldGrid
              items={[
                {
                  label: '构建模式',
                  value: resolveLinkField(link, 'buildMode'),
                },
                {
                  label: '来源模式',
                  value: resolveLinkField(link, 'sourceMode'),
                },
                {
                  label: '执行策略',
                  value: resolveLinkField(link, 'executionStrategy'),
                },
                {
                  label: '回退策略',
                  value: resolveLinkField(link, 'fallbackStrategy'),
                },
                {
                  label: '实际来源',
                  value: resolveLinkField(link, 'resolvedSourceMode'),
                },
                {
                  label: '解析路径',
                  value: resolveLinkField(link, 'resolutionPath'),
                },
                {
                  label: '模板来源',
                  value: resolveLinkField(link, 'templateSource'),
                },
              ]}
            />
          </div>
        ) : null}

        {link.commentText ? (
          <div className='rounded-md border border-dashed bg-muted/20 p-3 text-sm text-muted-foreground'>
            附言：{link.commentText}
          </div>
        ) : null}
      </div>

      {children.length > 0 ? (
        <div className='space-y-3 border-l border-dashed pl-3'>
          {children.map((child) => (
            <RuntimeStructureItem
              key={child.link.linkId}
              node={child}
              currentInstanceId={currentInstanceId}
              depth={depth + 1}
              onConfirmParentResume={onConfirmParentResume}
              pendingLinkId={pendingLinkId}
            />
          ))}
        </div>
      ) : null}
    </div>
  )
}

export function RuntimeStructureSection({
  title = '运行态结构',
  description = '展示主子流程、追加和动态构建形成的附属结构关系。',
  links,
  currentInstanceId,
  onConfirmParentResume,
  pendingLinkId,
}: {
  title?: string
  description?: string
  links: RuntimeStructureLink[]
  currentInstanceId: string
  onConfirmParentResume?: ((link: RuntimeStructureLink) => void) | undefined
  pendingLinkId?: string | null
}) {
  const visibleLinks = mergeRuntimeStructureLinks(links)

  if (!visibleLinks.length) {
    return null
  }

  const runtimeTree = buildRuntimeStructureTree(visibleLinks, currentInstanceId)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className='space-y-3'>
        <div className='rounded-lg border border-dashed bg-muted/10 p-3 text-sm text-muted-foreground'>
          根流程实例：{runtimeTree.rootInstanceId}
        </div>
        {runtimeTree.rootChildren.map((node) => (
          <RuntimeStructureItem
            key={node.link.linkId}
            node={node}
            currentInstanceId={currentInstanceId}
            depth={0}
            onConfirmParentResume={onConfirmParentResume}
            pendingLinkId={pendingLinkId}
          />
        ))}
      </CardContent>
    </Card>
  )
}
