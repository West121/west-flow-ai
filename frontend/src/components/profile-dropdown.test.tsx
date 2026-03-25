import type { ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ProfileDropdown } from './profile-dropdown'

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    children,
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to?: string }) => (
    <a href={to}>{children}</a>
  ),
}))

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (selector: (state: {
    currentUser: {
      username: string
      displayName: string
      email: string
      avatar: string
    } | null
  }) => unknown) =>
    selector({
      currentUser: {
        username: 'lisi',
        displayName: '李四',
        email: 'lisi@westflow.ai',
        avatar: '',
      },
    }),
}))

vi.mock('@/components/sign-out-dialog', () => ({
  SignOutDialog: () => null,
}))

vi.mock('@/hooks/use-dialog-state', () => ({
  default: () => [false, vi.fn()],
}))

vi.mock('@/components/ui/dropdown-menu', () => ({
  DropdownMenu: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  DropdownMenuContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuLabel: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuSeparator: () => <hr />,
  DropdownMenuGroup: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuItem: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuShortcut: ({ children }: { children: ReactNode }) => <span>{children}</span>,
}))

describe('ProfileDropdown', () => {
  it('renders the active login user instead of a hardcoded placeholder', () => {
    render(<ProfileDropdown />)

    expect(screen.getAllByText('李四')).toHaveLength(2)
    expect(screen.getByText('lisi@westflow.ai')).toBeInTheDocument()
    expect(screen.queryByText('SN')).not.toBeInTheDocument()
  })
})
