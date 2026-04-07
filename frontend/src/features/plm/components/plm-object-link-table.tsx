import { type PLMObjectLink } from '@/lib/api/plm'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

function formatObjectType(value: string) {
  switch (value) {
    case 'PART':
      return '零部件'
    case 'BOM':
      return 'BOM'
    case 'DOCUMENT':
      return '文档'
    case 'DRAWING':
      return '图纸'
    case 'MATERIAL':
      return '物料'
    case 'PROCESS':
      return '工艺'
    default:
      return value
  }
}

function formatChangeAction(value: string) {
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
      return value
  }
}

function formatRevisionSummary(link: PLMObjectLink) {
  const revisionParts = [
    link.objectRevisionCode ?? link.versionLabel,
    link.roleLabel ?? link.roleCode,
  ]
    .filter((part) => Boolean(part))
    .join(' · ')
  return revisionParts || '--'
}

export function PLMObjectLinkTable({
  objectLinks,
  emptyDescription = '当前没有结构化对象链接。',
}: {
  objectLinks: PLMObjectLink[]
  emptyDescription?: string
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>对象链接</CardTitle>
        <CardDescription>
          按对象、角色和版本基线查看 PLM 深度对象关系。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {objectLinks.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            {emptyDescription}
          </div>
        ) : (
          <ScrollArea className='max-h-[360px]'>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>角色</TableHead>
                  <TableHead>对象类型</TableHead>
                  <TableHead>对象编码</TableHead>
                  <TableHead>对象名称</TableHead>
                  <TableHead>版本基线</TableHead>
                  <TableHead>变更动作</TableHead>
                  <TableHead>来源</TableHead>
                  <TableHead>备注</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {objectLinks.map((link) => (
                  <TableRow key={link.id}>
                    <TableCell>
                      <Badge variant='outline'>
                        {link.roleLabel ?? link.roleCode}
                      </Badge>
                    </TableCell>
                    <TableCell>{formatObjectType(link.objectType)}</TableCell>
                    <TableCell className='font-medium'>
                      {link.objectCode}
                    </TableCell>
                    <TableCell>{link.objectName}</TableCell>
                    <TableCell>{formatRevisionSummary(link)}</TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          link.changeAction === 'REMOVE'
                            ? 'destructive'
                            : link.changeAction === 'REPLACE'
                              ? 'secondary'
                              : 'default'
                        }
                        className={cn('whitespace-nowrap')}
                      >
                        {formatChangeAction(link.changeAction)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className='space-y-1'>
                        <div>{link.sourceSystem ?? '--'}</div>
                        <div className='text-xs text-muted-foreground'>
                          {link.externalRef ?? '--'}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className='min-w-56 whitespace-normal'>
                      {link.remark ?? '--'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </ScrollArea>
        )}
      </CardContent>
    </Card>
  )
}
