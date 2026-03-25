import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { AlertCircle } from 'lucide-react'
import { type SystemAssociatedUser } from '@/lib/api/system-users'

function resolveStatusLabel(status: SystemAssociatedUser['status']) {
  return status === 'ENABLED' ? '启用' : '停用'
}

export function AssociatedUsersDialog({
  open,
  title,
  description,
  users,
  isLoading,
  isError,
  onRetry,
  onOpenChange,
}: {
  open: boolean
  title: string
  description: string
  users?: SystemAssociatedUser[]
  isLoading: boolean
  isError: boolean
  onRetry?: () => void
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <div className='grid gap-3'>
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className='h-12 w-full' />
            ))}
          </div>
        ) : null}

        {!isLoading && isError ? (
          <Alert variant='destructive'>
            <AlertCircle />
            <AlertTitle>关联用户加载失败</AlertTitle>
            <AlertDescription className='flex items-center justify-between gap-3'>
              <span>请重试，或稍后再查看这条记录的关联用户。</span>
              {onRetry ? (
                <Button variant='outline' size='sm' onClick={onRetry}>
                  重试
                </Button>
              ) : null}
            </AlertDescription>
          </Alert>
        ) : null}

        {!isLoading && !isError && (users?.length ?? 0) === 0 ? (
          <div className='rounded-md border border-dashed px-4 py-10 text-center text-sm text-muted-foreground'>
            当前没有关联用户。
          </div>
        ) : null}

        {!isLoading && !isError && (users?.length ?? 0) > 0 ? (
          <div className='overflow-hidden rounded-md border'>
            <table className='w-full text-sm'>
              <thead className='bg-muted/40 text-left'>
                <tr>
                  <th className='px-4 py-3 font-medium'>姓名</th>
                  <th className='px-4 py-3 font-medium'>部门</th>
                  <th className='px-4 py-3 font-medium'>岗位</th>
                  <th className='px-4 py-3 font-medium'>状态</th>
                </tr>
              </thead>
              <tbody>
                {users?.map((user) => (
                  <tr key={user.userId} className='border-t'>
                    <td className='px-4 py-3'>
                      <div className='flex flex-col gap-1'>
                        <span className='font-medium'>{user.displayName}</span>
                        <span className='text-xs text-muted-foreground'>
                          @{user.username}
                        </span>
                      </div>
                    </td>
                    <td className='px-4 py-3'>{user.departmentName || '-'}</td>
                    <td className='px-4 py-3'>{user.postName || '-'}</td>
                    <td className='px-4 py-3'>
                      <Badge
                        variant={
                          user.status === 'ENABLED' ? 'secondary' : 'outline'
                        }
                      >
                        {resolveStatusLabel(user.status)}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
