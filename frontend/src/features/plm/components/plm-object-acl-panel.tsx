import { type PLMObjectAcl } from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

export function PLMObjectAclPanel({
  entries,
}: {
  entries: PLMObjectAcl[]
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>对象权限</CardTitle>
        <CardDescription>
          查看对象归属人与 PLM 专属角色矩阵的访问范围。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有对象 ACL。
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>对象</TableHead>
                <TableHead>主体</TableHead>
                <TableHead>权限</TableHead>
                <TableHead>范围</TableHead>
                <TableHead>继承</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell>
                    <div className='font-medium'>
                      {entry.objectName ?? '--'}
                    </div>
                    <div className='text-xs text-muted-foreground'>
                      {entry.objectCode ?? '--'}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant='outline'>
                      {entry.subjectType}:{entry.subjectCode}
                    </Badge>
                  </TableCell>
                  <TableCell>{entry.permissionCode}</TableCell>
                  <TableCell>{entry.accessScope}</TableCell>
                  <TableCell>{entry.inherited ? '继承' : '直接'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
