import { type PLMRevisionDiff } from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatApprovalSheetDateTime } from '@/features/workbench/approval-sheet-list'

function formatDiffKind(value: string) {
  switch (value) {
    case 'ATTRIBUTE':
      return '属性变更'
    case 'BOM_STRUCTURE':
      return 'BOM 结构'
    case 'DOCUMENT':
      return '文档变更'
    case 'ROUTING':
      return '工艺路线'
    default:
      return value
  }
}

function formatRevisionValue(value: string | null | undefined) {
  return value ?? '--'
}

function renderPayload(value: Record<string, unknown> | null | undefined) {
  if (!value || Object.keys(value).length === 0) {
    return <span className='text-muted-foreground'>--</span>
  }

  return (
    <pre className='max-w-[32rem] rounded-md bg-muted/40 p-3 text-xs leading-5 break-words whitespace-pre-wrap'>
      {JSON.stringify(value, null, 2)}
    </pre>
  )
}

export function PLMRevisionDiffPanel({
  revisionDiffs,
  emptyDescription = '当前没有结构化版本差异。',
}: {
  revisionDiffs: PLMRevisionDiff[]
  emptyDescription?: string
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>版本对比</CardTitle>
        <CardDescription>
          按对象查看版本基线与差异摘要，支持结构化 payload。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {revisionDiffs.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            {emptyDescription}
          </div>
        ) : (
          <ScrollArea className='max-h-[420px]'>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>对象</TableHead>
                  <TableHead>差异类型</TableHead>
                  <TableHead>前版本</TableHead>
                  <TableHead>后版本</TableHead>
                  <TableHead>差异摘要</TableHead>
                  <TableHead>结构化内容</TableHead>
                  <TableHead>生成时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {revisionDiffs.map((diff) => (
                  <TableRow key={diff.id}>
                    <TableCell>
                      <div className='space-y-1'>
                        <div className='font-medium'>
                          {diff.objectName ?? diff.objectCode ?? diff.objectId}
                        </div>
                        <div className='text-xs text-muted-foreground'>
                          {diff.objectCode ?? diff.objectId}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant='outline'>
                        {formatDiffKind(diff.diffKind)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {formatRevisionValue(diff.beforeRevisionCode)}
                    </TableCell>
                    <TableCell>
                      {formatRevisionValue(diff.afterRevisionCode)}
                    </TableCell>
                    <TableCell className='min-w-60 whitespace-normal'>
                      {diff.diffSummary}
                    </TableCell>
                    <TableCell className='min-w-80 whitespace-normal'>
                      {renderPayload(diff.diffPayloadJson)}
                    </TableCell>
                    <TableCell>
                      {diff.createdAt
                        ? formatApprovalSheetDateTime(diff.createdAt)
                        : '--'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </ScrollArea>
        )}
        <Separator className='my-4' />
        <div className='text-xs text-muted-foreground'>
          差异类型可以覆盖属性、BOM
          结构、文档和工艺路线，前端仅负责展示结构化摘要。
        </div>
      </CardContent>
    </Card>
  )
}
