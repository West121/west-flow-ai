import * as React from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { BriefcaseBusiness, Check, ChevronsUpDown, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { switchContext } from '@/lib/api/auth'
import { handleServerError } from '@/lib/handle-server-error'
import { useAuthStore } from '@/stores/auth-store'

type TeamSwitcherProps = {
  teams?: {
    name: string
    logo: React.ElementType
    plan: string
  }[]
}

export function TeamSwitcher({ teams = [] }: TeamSwitcherProps) {
  const { isMobile } = useSidebar()
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.currentUser)
  const setCurrentUser = useAuthStore((state) => state.setCurrentUser)

  const assignments = React.useMemo(() => {
    if (currentUser?.postAssignments?.length) {
      return currentUser.postAssignments
    }
    return []
  }, [currentUser?.postAssignments])

  const activeAssignment = React.useMemo(
    () => assignments.find((assignment) => assignment.postId === currentUser?.activePostId),
    [assignments, currentUser?.activePostId]
  )

  const switchMutation = useMutation({
    mutationFn: switchContext,
    onSuccess(nextCurrentUser) {
      setCurrentUser(nextCurrentUser)
      queryClient.invalidateQueries({ queryKey: ['sidebar-menu-tree'] })
      toast.success('已切换任职上下文')
    },
    onError(error) {
      handleServerError(error)
    },
  })

  if (!currentUser) {
    return null
  }

  if (assignments.length === 0) {
    const fallbackTeam = teams[0]
    return (
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton size='lg'>
            <div className='flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground'>
              {fallbackTeam?.logo ? <fallbackTeam.logo className='size-4' /> : <BriefcaseBusiness className='size-4' />}
            </div>
            <div className='grid flex-1 text-start text-sm leading-tight'>
              <span className='truncate font-semibold'>
                {fallbackTeam?.name ?? currentUser.displayName}
              </span>
              <span className='truncate text-xs'>
                {fallbackTeam?.plan ?? '当前任职未配置'}
              </span>
            </div>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    )
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size='lg'
              className='data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground'
            >
              <div className='flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground'>
                <BriefcaseBusiness className='size-4' />
              </div>
              <div className='grid flex-1 text-start text-sm leading-tight'>
                <span className='truncate font-semibold'>
                  {activeAssignment?.postName ?? currentUser.activePostName ?? currentUser.displayName}
                </span>
                <span className='truncate text-xs'>
                  {activeAssignment
                    ? `${activeAssignment.companyName} / ${activeAssignment.departmentName} · ${activeAssignment.primary ? '主职' : '兼职'}`
                    : currentUser.activeDepartmentName || '当前任职'}
                </span>
              </div>
              {switchMutation.isPending ? <Loader2 className='ms-auto size-4 animate-spin' /> : <ChevronsUpDown className='ms-auto' />}
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className='w-[--radix-dropdown-menu-trigger-width] min-w-72 rounded-lg'
            align='start'
            side={isMobile ? 'bottom' : 'right'}
            sideOffset={4}
          >
            <DropdownMenuLabel className='text-xs text-muted-foreground'>
              任职上下文
            </DropdownMenuLabel>
            {assignments.map((assignment) => {
              const active = assignment.postId === currentUser.activePostId
              return (
                <DropdownMenuItem
                  key={assignment.postId}
                  onClick={() => {
                    if (!active && assignment.enabled && !switchMutation.isPending) {
                      switchMutation.mutate({ activePostId: assignment.postId })
                    }
                  }}
                  className='items-start gap-3 p-3'
                  disabled={!assignment.enabled || switchMutation.isPending}
                >
                  <div className='mt-0.5 flex size-6 items-center justify-center rounded-sm border'>
                    {active ? <Check className='size-4' /> : <BriefcaseBusiness className='size-4' />}
                  </div>
                  <div className='grid flex-1 gap-1'>
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='font-medium'>{assignment.postName}</span>
                      <Badge variant={assignment.primary ? 'secondary' : 'outline'}>
                        {assignment.primary ? '主职' : '兼职'}
                      </Badge>
                      {!assignment.enabled ? <Badge variant='outline'>停用</Badge> : null}
                    </div>
                    <p className='text-xs text-muted-foreground'>
                      {assignment.companyName} / {assignment.departmentName}
                    </p>
                    <p className='text-xs text-muted-foreground'>
                      {assignment.roleNames.length > 0
                        ? `角色：${assignment.roleNames.join('、')}`
                        : '未配置角色'}
                    </p>
                  </div>
                </DropdownMenuItem>
              )
            })}
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
