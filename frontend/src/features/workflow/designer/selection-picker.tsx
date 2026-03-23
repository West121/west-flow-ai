import { useEffect, useMemo, useState } from 'react'
import { Check, ChevronDown, ChevronRight, Search, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'
import {
  searchPrincipalOptions,
  type WorkflowPrincipalKind,
  type WorkflowPrincipalOption,
} from './selection-api'

const principalKindLabels: Record<WorkflowPrincipalKind, string> = {
  USER: '人员',
  ROLE: '角色',
  DEPARTMENT: '部门',
}

function defaultLabel(kind: WorkflowPrincipalKind) {
  return principalKindLabels[kind]
}

function renderSelection(value: string[], options: WorkflowPrincipalOption[]) {
  return value
    .map((item) => options.find((option) => option.id === item)?.label ?? item)
    .join(', ')
}

function mergeUniqueOptions(options: WorkflowPrincipalOption[]) {
  const optionMap = new Map<string, WorkflowPrincipalOption>()
  options.forEach((option) => {
    optionMap.set(option.id, option)
  })
  return Array.from(optionMap.values())
}

type DepartmentTreeNode = WorkflowPrincipalOption & {
  children: DepartmentTreeNode[]
}

function usePrincipalOptions(kind: WorkflowPrincipalKind, keyword: string) {
  const [options, setOptions] = useState<WorkflowPrincipalOption[]>([])

  useEffect(() => {
    let cancelled = false

    void searchPrincipalOptions(kind, keyword)
      .then((result) => {
        if (!cancelled) {
          setOptions(result)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setOptions([])
        }
      })

    return () => {
      cancelled = true
    }
  }, [kind, keyword])

  return options
}

function useResolvedSelection(
  kind: WorkflowPrincipalKind,
  value: string[]
) {
  const [options, setOptions] = useState<WorkflowPrincipalOption[]>([])

  useEffect(() => {
    let cancelled = false

    if (value.length === 0) {
      return () => {
        cancelled = true
      }
    }

    void (async () => {
      try {
        const batches = await Promise.all([
          searchPrincipalOptions(kind, ''),
          ...value.map((item) => searchPrincipalOptions(kind, item)),
        ])

        if (!cancelled) {
          setOptions(mergeUniqueOptions(batches.flat()))
        }
      } catch {
        if (!cancelled) {
          setOptions([])
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [kind, value])

  return options
}

function buildDepartmentTree(options: WorkflowPrincipalOption[]) {
  const nodeMap = new Map<string, DepartmentTreeNode>()
  const rootsByCompany = new Map<string, DepartmentTreeNode[]>()

  options.forEach((option) => {
    nodeMap.set(option.id, {
      ...option,
      children: [],
    })
  })

  nodeMap.forEach((node) => {
    const parentId = node.parentId
    if (parentId && nodeMap.has(parentId)) {
      nodeMap.get(parentId)?.children.push(node)
      return
    }
    const companyKey = node.companyId ?? 'default'
    const companyRoots = rootsByCompany.get(companyKey) ?? []
    companyRoots.push(node)
    rootsByCompany.set(companyKey, companyRoots)
  })

  const sortNodes = (nodes: DepartmentTreeNode[]) => {
    nodes.sort((left, right) => left.label.localeCompare(right.label, 'zh-CN'))
    nodes.forEach((node) => sortNodes(node.children))
  }

  rootsByCompany.forEach((nodes) => sortNodes(nodes))

  return Array.from(rootsByCompany.entries())
    .map(([companyId, nodes]) => ({
      companyId,
      companyName: nodes[0]?.groupLabel ?? '未分组部门',
      nodes,
    }))
    .sort((left, right) => left.companyName.localeCompare(right.companyName, 'zh-CN'))
}

function collectDepartmentMatches(
  groups: ReturnType<typeof buildDepartmentTree>,
  keyword: string
) {
  if (!keyword.trim()) {
    return groups
  }

  const normalizedKeyword = keyword.trim().toLowerCase()

  const filterNode = (node: DepartmentTreeNode): DepartmentTreeNode | null => {
    const children = node.children
      .map(filterNode)
      .filter((item): item is DepartmentTreeNode => item !== null)
    const selfMatched = [node.label, node.description, node.id]
      .filter(Boolean)
      .some((item) => item.toLowerCase().includes(normalizedKeyword))

    if (!selfMatched && children.length === 0) {
      return null
    }

    return {
      ...node,
      children,
    }
  }

  return groups
    .map((group) => ({
      ...group,
      nodes: group.nodes
        .map(filterNode)
        .filter((item): item is DepartmentTreeNode => item !== null),
    }))
    .filter((group) => group.nodes.length > 0)
}

function PrincipalTab({
  kind,
  selected,
  onToggle,
}: {
  kind: WorkflowPrincipalKind
  selected: string[]
  onToggle: (option: WorkflowPrincipalOption) => void
}) {
  const [keyword, setKeyword] = useState('')
  const selectedSet = useMemo(() => new Set(selected), [selected])
  const options = usePrincipalOptions(kind, keyword.trim())

  return (
    <div className='grid gap-4 pt-2'>
      <div className='relative'>
        <Search className='pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground' />
        <Input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder={`搜索${defaultLabel(kind)}名称、编码或关键字`}
          className='pl-9'
        />
      </div>

      <ScrollArea className='h-72 rounded-xl border'>
        <div className='grid gap-2 p-2'>
          {options.length === 0 ? (
            <div className='rounded-lg border border-dashed p-6 text-sm text-muted-foreground'>
              {keyword.trim() ? `没有找到匹配的${defaultLabel(kind)}` : `暂无可选${defaultLabel(kind)}`}
            </div>
          ) : null}
          {options.map((option) => {
            const active = selectedSet.has(option.id)
            return (
              <button
                key={option.id}
                type='button'
                className={cn(
                  'flex items-start gap-3 rounded-lg border px-3 py-2 text-left transition-colors hover:bg-muted/60',
                  active && 'border-primary bg-primary/5'
                )}
                onClick={() => onToggle(option)}
              >
                <span
                  className={cn(
                    'mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full border',
                    active ? 'border-primary bg-primary text-primary-foreground' : 'border-muted-foreground/30'
                  )}
                >
                  {active ? <Check className='size-3.5' /> : null}
                </span>
                <span className='flex min-w-0 flex-1 flex-col gap-1'>
                  <span className='text-sm font-medium'>{option.label}</span>
                  <span className='truncate text-xs text-muted-foreground'>
                    {option.description || option.id}
                  </span>
                  <span className='text-[11px] text-muted-foreground'>ID：{option.id}</span>
                </span>
              </button>
            )
          })}
        </div>
      </ScrollArea>
    </div>
  )
}

function DepartmentTreeNodeItem({
  node,
  level,
  expandedIds,
  onToggleExpand,
  selected,
  onToggle,
}: {
  node: DepartmentTreeNode
  level: number
  expandedIds: Set<string>
  onToggleExpand: (nodeId: string) => void
  selected: Set<string>
  onToggle: (option: WorkflowPrincipalOption) => void
}) {
  const active = selected.has(node.id)
  const hasChildren = node.children.length > 0
  const expanded = hasChildren && expandedIds.has(node.id)

  return (
    <div className='grid gap-2'>
      <div className='flex items-start gap-2'>
        <button
          type='button'
          className={cn(
            'mt-2 flex size-6 shrink-0 items-center justify-center rounded-md text-muted-foreground transition hover:bg-muted',
            !hasChildren && 'invisible'
          )}
          onClick={() => hasChildren && onToggleExpand(node.id)}
        >
          {hasChildren ? (
            expanded ? <ChevronDown className='size-4' /> : <ChevronRight className='size-4' />
          ) : null}
        </button>

        <button
          type='button'
          className={cn(
            'flex flex-1 items-start gap-3 rounded-lg border px-3 py-2 text-left transition-colors hover:bg-muted/60',
            active && 'border-primary bg-primary/5'
          )}
          style={{ marginLeft: `${Math.max(level - 1, 0) * 14}px` }}
          onClick={() => onToggle(node)}
        >
          <span
            className={cn(
              'mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full border',
              active ? 'border-primary bg-primary text-primary-foreground' : 'border-muted-foreground/30'
            )}
          >
            {active ? <Check className='size-3.5' /> : null}
          </span>
          <span className='flex min-w-0 flex-1 flex-col gap-1'>
            <span className='text-sm font-medium'>{node.label}</span>
            <span className='truncate text-xs text-muted-foreground'>
              {node.description || node.id}
            </span>
            <span className='text-[11px] text-muted-foreground'>ID：{node.id}</span>
          </span>
        </button>
      </div>

      {expanded ? (
        <div className='grid gap-2'>
          {node.children.map((child) => (
            <DepartmentTreeNodeItem
              key={child.id}
              node={child}
              level={level + 1}
              expandedIds={expandedIds}
              onToggleExpand={onToggleExpand}
              selected={selected}
              onToggle={onToggle}
            />
          ))}
        </div>
      ) : null}
    </div>
  )
}

function DepartmentPrincipalTab({
  selected,
  onToggle,
}: {
  selected: string[]
  onToggle: (option: WorkflowPrincipalOption) => void
}) {
  const [keyword, setKeyword] = useState('')
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const selectedSet = useMemo(() => new Set(selected), [selected])
  const options = usePrincipalOptions('DEPARTMENT', keyword.trim())
  const groupedTree = useMemo(
    () => collectDepartmentMatches(buildDepartmentTree(options), keyword),
    [keyword, options]
  )

  useEffect(() => {
    setExpandedIds(
      new Set(
        groupedTree.flatMap((group) =>
          group.nodes.flatMap(function collect(node): string[] {
            return node.children.length > 0
              ? [node.id, ...node.children.flatMap(collect)]
              : []
          })
        )
      )
    )
  }, [groupedTree])

  return (
    <div className='grid gap-4 pt-2'>
      <div className='relative'>
        <Search className='pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground' />
        <Input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder='搜索部门名称、公司或编码'
          className='pl-9'
        />
      </div>

      <ScrollArea className='h-72 rounded-xl border'>
        <div className='grid gap-3 p-3'>
          {groupedTree.length === 0 ? (
            <div className='rounded-lg border border-dashed p-6 text-sm text-muted-foreground'>
              {keyword.trim() ? '没有找到匹配的部门' : '暂无可选部门'}
            </div>
          ) : null}
          {groupedTree.map((group) => (
            <div key={group.companyId} className='grid gap-2'>
              <div className='rounded-lg bg-muted/60 px-3 py-2 text-xs font-medium text-muted-foreground'>
                {group.companyName}
              </div>
              <div className='grid gap-2'>
                {group.nodes.map((node) => (
                  <DepartmentTreeNodeItem
                    key={node.id}
                    node={node}
                    level={1}
                    expandedIds={expandedIds}
                    onToggleExpand={(nodeId) =>
                      setExpandedIds((current) => {
                        const next = new Set(current)
                        if (next.has(nodeId)) {
                          next.delete(nodeId)
                        } else {
                          next.add(nodeId)
                        }
                        return next
                      })
                    }
                    selected={selectedSet}
                    onToggle={onToggle}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      </ScrollArea>
    </div>
  )
}

export function WorkflowPrincipalPickerField({
  kind,
  label,
  description,
  value,
  onChange,
  placeholder,
}: {
  kind: WorkflowPrincipalKind
  label: string
  description?: string
  value: string[]
  onChange: (next: string[]) => void
  placeholder?: string
}) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<string[]>(value)
  const resolvedOptions = useResolvedSelection(kind, value)
  const resolvedMap = useMemo(
    () => new Map(resolvedOptions.map((option) => [option.id, option])),
    [resolvedOptions]
  )

  return (
    <>
      <div className='grid gap-2'>
        <Label>{label}</Label>
        {description ? (
          <p className='text-xs text-muted-foreground'>{description}</p>
        ) : null}
        <div className='flex gap-2'>
          <Input
            readOnly
            value={renderSelection(value, resolvedOptions)}
            placeholder={placeholder ?? `请选择${defaultLabel(kind)}`}
          />
          <Button
            type='button'
            variant='secondary'
            onClick={() => {
              setDraft(value)
              setOpen(true)
            }}
          >
            <Users className='mr-2 size-4' />
            选择
          </Button>
        </div>
        {value.length > 0 ? (
          <div className='flex flex-wrap gap-2'>
            {value.map((item) => (
              <span
                key={item}
                className='rounded-full bg-muted px-2.5 py-1 text-xs text-foreground'
              >
                {resolvedMap.get(item)?.label ?? item}
              </span>
            ))}
          </div>
        ) : null}
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className='max-w-3xl'>
          <DialogHeader>
            <DialogTitle>选择{defaultLabel(kind)}</DialogTitle>
            <DialogDescription>
              支持多选，确认后会同步回节点配置。
            </DialogDescription>
          </DialogHeader>

          <Separator />
          {kind === 'DEPARTMENT' ? (
            <DepartmentPrincipalTab
              selected={draft}
              onToggle={(option) =>
                setDraft((current) =>
                  current.includes(option.id)
                    ? current.filter((item) => item !== option.id)
                    : [...current, option.id]
                )
              }
            />
          ) : (
            <PrincipalTab
              kind={kind}
              selected={draft}
              onToggle={(option) =>
                setDraft((current) =>
                  current.includes(option.id)
                    ? current.filter((item) => item !== option.id)
                    : [...current, option.id]
                )
              }
            />
          )}

          <DialogFooter className='gap-2 sm:gap-0'>
            <Button
              type='button'
              variant='outline'
              onClick={() => setDraft([])}
            >
              清空
            </Button>
            <Button
              type='button'
              variant='outline'
              onClick={() => setOpen(false)}
            >
              取消
            </Button>
            <Button
              type='button'
              onClick={() => {
                onChange(draft)
                setOpen(false)
              }}
            >
              确认
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
