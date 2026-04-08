import { type PLMDomainAcl, type PLMRoleAssignment } from '@/lib/api/plm'
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

export function PLMRoleMatrixPanel({
  roles,
  domainAcl,
}: {
  roles: PLMRoleAssignment[]
  domainAcl: PLMDomainAcl[]
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>角色矩阵与域权限</CardTitle>
        <CardDescription>
          用于确认 PLM 角色覆盖度、域级权限和后续审批/实施责任边界。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-6'>
        <div className='space-y-3'>
          <div className='text-sm font-medium'>角色矩阵</div>
          {roles.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              当前没有角色矩阵记录。
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>角色</TableHead>
                  <TableHead>责任人</TableHead>
                  <TableHead>范围</TableHead>
                  <TableHead>状态</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {roles.map((role) => (
                  <TableRow key={role.id}>
                    <TableCell>
                      <div className='font-medium'>{role.roleLabel}</div>
                      <div className='text-xs text-muted-foreground'>
                        {role.roleCode}
                      </div>
                    </TableCell>
                    <TableCell>
                      {role.assigneeDisplayName ?? role.assigneeUserId ?? '--'}
                    </TableCell>
                    <TableCell>{role.assignmentScope}</TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          role.status === 'ASSIGNED' ? 'secondary' : 'outline'
                        }
                      >
                        {role.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>

        <div className='space-y-3'>
          <div className='text-sm font-medium'>域级 ACL</div>
          {domainAcl.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              当前没有域级 ACL 记录。
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>业务域</TableHead>
                  <TableHead>角色</TableHead>
                  <TableHead>权限</TableHead>
                  <TableHead>策略来源</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {domainAcl.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell>{entry.domainCode}</TableCell>
                    <TableCell>{entry.roleCode}</TableCell>
                    <TableCell>{entry.permissionCode}</TableCell>
                    <TableCell>{entry.policySource}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
