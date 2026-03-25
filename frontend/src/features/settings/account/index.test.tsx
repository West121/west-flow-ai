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
      activeDepartmentId: string
      activePostId: string
      roles: string[]
      permissions: string[]
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
        activeDepartmentId: 'dept_002',
        activePostId: 'post_002',
        roles: ['OA_USER', 'DEPT_MANAGER'],
        permissions: ['oa:leave:create', 'workflow:task:approve'],
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
    expect(screen.getByText('dept_002')).toBeInTheDocument()
    expect(screen.getByText('post_002')).toBeInTheDocument()
    expect(screen.getByText('OA_USER')).toBeInTheDocument()
    expect(screen.queryByText('计费信息')).not.toBeInTheDocument()
  })
})
