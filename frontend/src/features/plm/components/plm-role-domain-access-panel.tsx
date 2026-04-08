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

function formatPrincipalType(value: string) {
  switch (value) {
    case 'ROLE':
      return '角色'
    case 'USER':
      return '用户'
    case 'GROUP':
      return '用户组'
    case 'DOMAIN':
      return '域'
    case 'ORG':
      return '组织'
    default:
      return value
  }
}

type PrincipalSummary = {
  principalKey: string
  subjectType: string
  subjectCode: string
  objectCount: number
  permissions: string[]
  scopes: string[]
  inheritedCount: number
  directCount: number
}

export function PLMRoleDomainAccessPanel({
  entries,
  sceneCode,
}: {
  entries: PLMObjectAcl[]
  sceneCode?: string | null
}) {
  const principalMap = new Map<string, PrincipalSummary>()

  entries.forEach((entry) => {
    const key = `${entry.subjectType}:${entry.subjectCode}`
    const current = principalMap.get(key) ?? {
      principalKey: key,
      subjectType: entry.subjectType,
      subjectCode: entry.subjectCode,
      objectCount: 0,
      permissions: [],
      scopes: [],
      inheritedCount: 0,
      directCount: 0,
    }

    current.objectCount += entry.objectId || entry.objectCode ? 1 : 0
    if (!current.permissions.includes(entry.permissionCode)) {
      current.permissions.push(entry.permissionCode)
    }
    if (!current.scopes.includes(entry.accessScope)) {
      current.scopes.push(entry.accessScope)
    }
    if (entry.inherited) {
      current.inheritedCount += 1
    } else {
      current.directCount += 1
    }
    principalMap.set(key, current)
  })

  const principalRows = [...principalMap.values()].sort(
    (left, right) => right.objectCount - left.objectCount
  )
  const inheritedCount = entries.filter((entry) => entry.inherited).length
  const directCount = entries.length - inheritedCount
  const scopeCount = new Set(entries.map((entry) => entry.accessScope)).size

  return (
    <Card>
      <CardHeader>
        <CardTitle>角色 / 域权限视图</CardTitle>
        <CardDescription>
          把对象 ACL 收敛成角色矩阵、域范围和继承关系，用于判断谁能看、谁能改、谁能关闭。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='flex flex-wrap items-center gap-2'>
          {sceneCode ? <Badge variant='outline'>业务域 {sceneCode}</Badge> : null}
          <Badge variant='secondary'>主体 {principalRows.length}</Badge>
          <Badge variant='secondary'>直接授权 {directCount}</Badge>
          <Badge variant='secondary'>继承授权 {inheritedCount}</Badge>
          <Badge variant='secondary'>作用域 {scopeCount}</Badge>
        </div>

        {entries.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有对象 ACL。
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>主体</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>覆盖对象</TableHead>
                <TableHead>权限集</TableHead>
                <TableHead>域范围</TableHead>
                <TableHead>授权方式</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {principalRows.map((row) => (
                <TableRow key={row.principalKey}>
                  <TableCell className='font-medium'>{row.subjectCode}</TableCell>
                  <TableCell>{formatPrincipalType(row.subjectType)}</TableCell>
                  <TableCell>{row.objectCount}</TableCell>
                  <TableCell className='max-w-56 whitespace-normal'>
                    {row.permissions.join(' · ')}
                  </TableCell>
                  <TableCell className='max-w-48 whitespace-normal'>
                    {row.scopes.join(' · ')}
                  </TableCell>
                  <TableCell>
                    直接 {row.directCount} / 继承 {row.inheritedCount}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
