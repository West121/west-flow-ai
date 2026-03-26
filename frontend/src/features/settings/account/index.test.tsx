import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SettingsAccount } from './index'

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (selector: (state: {
    currentUser: {
      userId: string
      username: string
      displayName: string
      mobile: string
      email: string
      companyId: string
      companyName: string
      activeDepartmentId: string
      activeDepartmentName: string
      activePostId: string
      activePostName: string
      roles: string[]
      permissions: string[]
      postAssignments: Array<{
        postId: string
        departmentId: string
        departmentName: string
        companyId: string
        companyName: string
        postName: string
        roleIds: string[]
        roleNames: string[]
        primary: boolean
        enabled: boolean
      }>
    } | null
  }) => unknown) =>
    selector({
      currentUser: {
        userId: 'usr_002',
        username: 'lisi',
        displayName: '李四',
        mobile: '13900000000',
        email: 'lisi@westflow.ai',
        companyId: 'cmp_001',
        companyName: '西流科技',
        activeDepartmentId: 'dept_002',
        activeDepartmentName: '人力资源部',
        activePostId: 'post_002',
        activePostName: '请假复核岗',
        roles: ['OA_USER', 'DEPT_MANAGER'],
        permissions: ['oa:leave:create', 'workflow:task:approve'],
        postAssignments: [
          {
            postId: 'post_001',
            departmentId: 'dept_001',
            departmentName: '总经办',
            companyId: 'cmp_001',
            companyName: '西流科技',
            postName: '平台管理员',
            roleIds: ['role_admin'],
            roleNames: ['PLATFORM_ADMIN'],
            primary: true,
            enabled: true,
          },
          {
            postId: 'post_002',
            departmentId: 'dept_002',
            departmentName: '人力资源部',
            companyId: 'cmp_001',
            companyName: '西流科技',
            postName: '请假复核岗',
            roleIds: ['role_oa_user', 'role_manager'],
            roleNames: ['OA_USER', 'DEPT_MANAGER'],
            primary: false,
            enabled: true,
          },
        ],
      },
    }),
}))

describe('SettingsAccount', () => {
  it('shows the current session account instead of demo content', () => {
    render(<SettingsAccount />)

    expect(screen.getByText('李四')).toBeInTheDocument()
    expect(screen.getByText('lisi')).toBeInTheDocument()
    expect(screen.getByText('13900000000')).toBeInTheDocument()
    expect(screen.getByText('lisi@westflow.ai')).toBeInTheDocument()
    expect(screen.getByText('人力资源部')).toBeInTheDocument()
    expect(screen.getAllByText('请假复核岗').length).toBeGreaterThan(0)
    expect(screen.getAllByText('OA_USER').length).toBeGreaterThan(0)
    expect(screen.getByText('全部任职')).toBeInTheDocument()
    expect(screen.queryByText('计费信息')).not.toBeInTheDocument()
  })
})
