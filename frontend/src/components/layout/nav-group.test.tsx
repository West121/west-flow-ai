import type { ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NavGroup } from './nav-group'

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    children,
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to?: string }) => (
    <a href={to}>{children}</a>
  ),
  useLocation: () => '/workbench/todos/list',
}))

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (selector: (state: { currentUser: { roles: string[] } }) => unknown) =>
    selector({
      currentUser: {
        roles: ['OA_USER', 'DEPT_MANAGER'],
      },
    }),
}))

vi.mock('@/components/ui/sidebar', () => ({
  Collapsible: ({ children }: { children: ReactNode }) => <>{children}</>,
  CollapsibleContent: ({ children }: { children: ReactNode }) => <>{children}</>,
  CollapsibleTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  SidebarGroup: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  SidebarGroupLabel: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  SidebarMenu: ({ children }: { children: ReactNode }) => <ul>{children}</ul>,
  SidebarMenuButton: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  SidebarMenuItem: ({ children }: { children: ReactNode }) => <li>{children}</li>,
  SidebarMenuSub: ({ children }: { children: ReactNode }) => <ul>{children}</ul>,
  SidebarMenuSubButton: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  SidebarMenuSubItem: ({ children }: { children: ReactNode }) => <li>{children}</li>,
  useSidebar: () => ({
    state: 'expanded',
    isMobile: false,
    setOpenMobile: vi.fn(),
  }),
}))

describe('nav group', () => {
  it('renders tree navigation without directory group labels', () => {
    render(
      <NavGroup
        items={[
          {
            title: '工作台',
            url: '/workbench',
            icon: undefined,
            items: [
              {
                title: '工作台',
                url: '/',
              },
            ],
          },
        ]}
      />
    )

    expect(screen.getAllByText('工作台')).toHaveLength(2)
    expect(screen.queryByText('分组标题')).not.toBeInTheDocument()
  })
})
