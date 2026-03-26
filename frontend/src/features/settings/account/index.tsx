import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { useAuthStore } from '@/stores/auth-store'
import { ContentSection } from '../components/content-section'

function InfoCard({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <Card className='border-muted/60 bg-muted/20'>
      <CardContent className='space-y-2 p-4'>
        <p className='text-xs text-muted-foreground'>{label}</p>
        <p className='text-sm font-medium'>{value || '暂无'}</p>
      </CardContent>
    </Card>
  )
}

export function SettingsAccount() {
  const currentUser = useAuthStore((state) => state.currentUser)
  const activeAssignment = currentUser?.postAssignments?.find(
    (assignment) => assignment.postId === currentUser.activePostId
  )

  const roles = currentUser?.roles?.length ? currentUser.roles.join('、') : '暂无'
  const permissions = currentUser?.permissions?.length
    ? currentUser.permissions.slice(0, 6).join('、')
    : '暂无'

  return (
    <ContentSection
      title='账号'
      desc='查看当前登录账户的组织与权限信息。'
    >
      <div className='space-y-4'>
        <div className='rounded-lg border bg-muted/20 p-4 text-sm text-muted-foreground'>
          这里展示的是当前会话的真实账号上下文，不再使用演示数据。
        </div>
        <Card className='border-muted/60 bg-muted/20'>
          <CardContent className='space-y-4 p-4'>
            <div className='flex flex-wrap items-center gap-2'>
              <span className='text-sm font-semibold'>当前任职</span>
              <Badge variant={activeAssignment?.primary ? 'secondary' : 'outline'}>
                {activeAssignment?.primary ? '主职' : '兼职'}
              </Badge>
              {!activeAssignment?.enabled ? <Badge variant='outline'>停用</Badge> : null}
            </div>
            <div className='grid gap-3 md:grid-cols-3'>
              <InfoCard label='公司' value={activeAssignment?.companyName ?? currentUser?.companyName ?? ''} />
              <InfoCard label='部门' value={activeAssignment?.departmentName ?? currentUser?.activeDepartmentName ?? ''} />
              <InfoCard label='岗位' value={activeAssignment?.postName ?? currentUser?.activePostName ?? ''} />
            </div>
            <div className='space-y-2'>
              <p className='text-xs text-muted-foreground'>当前任职角色</p>
              <div className='flex flex-wrap gap-2'>
                {(activeAssignment?.roleNames?.length ? activeAssignment.roleNames : currentUser?.roles ?? []).map((role) => (
                  <Badge key={role} variant='secondary'>
                    {role}
                  </Badge>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>
        <div className='grid gap-4 md:grid-cols-2'>
          <InfoCard label='用户名' value={currentUser?.username ?? ''} />
          <InfoCard label='姓名' value={currentUser?.displayName ?? ''} />
          <InfoCard label='手机号' value={currentUser?.mobile ?? ''} />
          <InfoCard label='邮箱' value={currentUser?.email ?? ''} />
          <InfoCard label='公司 ID' value={currentUser?.companyId ?? ''} />
          <InfoCard
            label='当前部门 ID'
            value={currentUser?.activeDepartmentId ?? ''}
          />
          <InfoCard label='当前岗位 ID' value={currentUser?.activePostId ?? ''} />
          <Card className='border-muted/60 bg-muted/20'>
            <CardContent className='space-y-2 p-4'>
              <p className='text-xs text-muted-foreground'>角色</p>
              <p className='flex flex-wrap gap-2 text-sm font-medium'>
                {roles === '暂无' ? (
                  '暂无'
                ) : (
                  roles.split('、').map((role) => (
                    <Badge key={role} variant='secondary'>
                      {role}
                    </Badge>
                  ))
                )}
              </p>
            </CardContent>
          </Card>
          <InfoCard label='权限摘要' value={permissions} />
        </div>
        {currentUser?.postAssignments?.length ? (
          <div className='space-y-3'>
            <div>
              <h3 className='text-sm font-semibold'>全部任职</h3>
              <p className='text-xs text-muted-foreground'>主职与兼职都在这里，切换上下文后权限与候选任务会按当前任职生效。</p>
            </div>
            <div className='grid gap-3 md:grid-cols-2'>
              {currentUser.postAssignments.map((assignment) => {
                const active = assignment.postId === currentUser.activePostId
                return (
                  <Card key={assignment.postId} className={active ? 'border-primary/40 bg-primary/5' : 'border-muted/60 bg-muted/20'}>
                    <CardContent className='space-y-3 p-4'>
                      <div className='flex flex-wrap items-center gap-2'>
                        <span className='text-sm font-semibold'>{assignment.postName}</span>
                        <Badge variant={assignment.primary ? 'secondary' : 'outline'}>
                          {assignment.primary ? '主职' : '兼职'}
                        </Badge>
                        {active ? <Badge>当前上下文</Badge> : null}
                        {!assignment.enabled ? <Badge variant='outline'>停用</Badge> : null}
                      </div>
                      <div className='space-y-1 text-xs text-muted-foreground'>
                        <p>{assignment.companyName} / {assignment.departmentName}</p>
                        <p>岗位 ID：{assignment.postId}</p>
                      </div>
                      <div className='flex flex-wrap gap-2'>
                        {assignment.roleNames.length > 0 ? assignment.roleNames.map((role) => (
                          <Badge key={role} variant='secondary'>
                            {role}
                          </Badge>
                        )) : <span className='text-xs text-muted-foreground'>未配置角色</span>}
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          </div>
        ) : null}
      </div>
    </ContentSection>
  )
}
