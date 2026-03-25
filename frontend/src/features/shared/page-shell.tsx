import { type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation } from '@tanstack/react-router'
import { ConfigDrawer } from '@/components/config-drawer'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import { getSidebarMenuTree, type SidebarMenuNode } from '@/lib/api/system-menus'
import { cn } from '@/lib/utils'

type PageShellProps = {
  title: string
  description: string
  actions?: ReactNode
  children: ReactNode
  contentClassName?: string
}

type BreadcrumbItem = {
  title: string
  href: string | null
}

function resolveBreadcrumbTrail(
  menuTree: SidebarMenuNode[],
  pathname: string,
  parents: BreadcrumbItem[] = []
): BreadcrumbItem[] | null {
  for (const node of menuTree) {
    const currentItem: BreadcrumbItem = {
      title: node.title,
      href: node.routePath,
    }
    const trail = [...parents, currentItem]

    if (
      node.routePath &&
      (pathname === node.routePath || pathname.startsWith(`${node.routePath}/`))
    ) {
      const descendantMatch = resolveBreadcrumbTrail(node.children, pathname, trail)
      return descendantMatch ?? trail
    }

    const descendantOnlyMatch = resolveBreadcrumbTrail(node.children, pathname, trail)
    if (descendantOnlyMatch) {
      return descendantOnlyMatch
    }
  }

  return null
}

// 复用的页面壳层，统一承载标题区、操作区和主内容区。
export function PageShell({
  title,
  description,
  actions,
  children,
  contentClassName,
}: PageShellProps) {
  const pathname = useLocation({ select: (location) => location.pathname })
  const { data: menuTree } = useQuery({
    queryKey: ['sidebar-menu-tree'],
    queryFn: getSidebarMenuTree,
  })
  const breadcrumbTrail = resolveBreadcrumbTrail(menuTree ?? [], pathname) ?? [
    { title, href: null },
  ]

  return (
    <>
      <Header fixed className='border-b bg-background/80'>
        <nav
          aria-label='页面面包屑'
          className='flex min-w-0 items-center gap-2 text-sm text-muted-foreground'
        >
          {breadcrumbTrail.map((item, index) => {
            const isLast = index === breadcrumbTrail.length - 1
            return (
              <div key={`${item.title}-${item.href ?? index}`} className='flex min-w-0 items-center gap-2'>
                {index > 0 ? <span className='text-muted-foreground/60'>/</span> : null}
                {isLast || !item.href ? (
                  <span className='truncate font-medium text-foreground'>{item.title}</span>
                ) : (
                  <Link
                    to={item.href}
                    className='truncate transition-colors hover:text-foreground'
                  >
                    {item.title}
                  </Link>
                )}
              </div>
            )
          })}
        </nav>
        <div className='ms-auto flex items-center gap-2 sm:gap-3'>
          <Search placeholder='搜索菜单与页面' />
          <ThemeSwitch />
          <ConfigDrawer />
          <ProfileDropdown />
        </div>
      </Header>

      <Main className={cn('flex flex-1 flex-col gap-6 sm:gap-8', contentClassName)}>
        <div className='flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between'>
          <div className='flex flex-col gap-2'>
            <h1 className='text-3xl font-semibold tracking-tight'>{title}</h1>
            <p className='max-w-3xl text-sm text-muted-foreground sm:text-base'>
              {description}
            </p>
          </div>
          {actions ? (
            <div className='flex flex-wrap items-center gap-2'>{actions}</div>
          ) : null}
        </div>

        {children}
      </Main>
    </>
  )
}
