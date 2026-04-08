import { type PLMBomNode } from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

function formatNodeType(value: string) {
  switch (value) {
    case 'PART':
      return '零部件'
    case 'BOM':
      return 'BOM'
    case 'MATERIAL':
      return '物料'
    case 'PROCESS':
      return '工艺'
    default:
      return value
  }
}

function formatChangeAction(value?: string | null) {
  switch (value) {
    case 'ADD':
      return '新增'
    case 'UPDATE':
      return '更新'
    case 'REMOVE':
      return '移除'
    case 'REPLACE':
      return '替换'
    default:
      return value ?? '--'
  }
}

export function PLMBomTreePanel({
  nodes,
}: {
  nodes: PLMBomNode[]
}) {
  const changedNodeCount = nodes.filter((node) => Boolean(node.changeAction)).length
  const effectivityCount = nodes.filter((node) => Boolean(node.effectivity)).length
  const maxDepth = nodes.reduce(
    (depth, node) => Math.max(depth, node.hierarchyLevel ?? 0),
    0
  )

  return (
    <Card>
      <CardHeader>
        <CardTitle>BOM 结构</CardTitle>
        <CardDescription>
          以只读树形式查看受影响 BOM / 物料对象及其结构层级。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {nodes.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有结构化 BOM 节点。
          </div>
        ) : (
          <div className='space-y-4'>
            <div className='grid gap-3 sm:grid-cols-3'>
              <div className='rounded-xl border bg-muted/15 p-4'>
                <div className='text-sm text-muted-foreground'>结构节点</div>
                <div className='mt-1 text-2xl font-semibold'>{nodes.length}</div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  最高层级 {maxDepth + 1}
                </div>
              </div>
              <div className='rounded-xl border bg-muted/15 p-4'>
                <div className='text-sm text-muted-foreground'>变更节点</div>
                <div className='mt-1 text-2xl font-semibold'>{changedNodeCount}</div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  覆盖新增、更新、替换和移除
                </div>
              </div>
              <div className='rounded-xl border bg-muted/15 p-4'>
                <div className='text-sm text-muted-foreground'>生效条件</div>
                <div className='mt-1 text-2xl font-semibold'>{effectivityCount}</div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  已配置 effectivity 的结构项
                </div>
              </div>
            </div>
            <Separator />
            <div className='space-y-3'>
              {nodes.map((node) => (
                <div
                  key={node.id}
                  className='rounded-xl border bg-muted/15 p-4'
                  style={{ marginLeft: `${(node.hierarchyLevel ?? 0) * 20}px` }}
                >
                  <div className='flex flex-wrap items-center gap-2'>
                    <div className='font-medium'>{node.nodeName}</div>
                    <Badge variant='outline'>{formatNodeType(node.nodeType)}</Badge>
                    <Badge variant='secondary'>
                      {formatChangeAction(node.changeAction)}
                    </Badge>
                  </div>
                  <div className='mt-2 text-sm text-muted-foreground'>
                    {node.nodeCode}
                    {node.quantity != null ? ` · 数量 ${node.quantity}` : ''}
                    {node.unit ? ` ${node.unit}` : ''}
                    {node.effectivity ? ` · ${node.effectivity}` : ''}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
