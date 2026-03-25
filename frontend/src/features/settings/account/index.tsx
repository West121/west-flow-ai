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
      </div>
    </ContentSection>
  )
}
