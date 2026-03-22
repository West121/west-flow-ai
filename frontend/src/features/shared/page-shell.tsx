import { type ReactNode } from 'react'
import { useLocation } from '@tanstack/react-router'
import { ConfigDrawer } from '@/components/config-drawer'
import { Header } from '@/components/layout/header'
import { Main } from '@/components/layout/main'
import { TopNav } from '@/components/layout/top-nav'
import { ProfileDropdown } from '@/components/profile-dropdown'
import { Search } from '@/components/search'
import { ThemeSwitch } from '@/components/theme-switch'
import { cn } from '@/lib/utils'
import { appTopNavLinks, isAppTopNavActive } from './navigation'

type PageShellProps = {
  title: string
  description: string
  actions?: ReactNode
  children: ReactNode
  contentClassName?: string
}

// 复用的页面壳层，统一承载标题区、操作区和主内容区。
export function PageShell({
  title,
  description,
  actions,
  children,
  contentClassName,
}: PageShellProps) {
  const href = useLocation({ select: (location) => location.href })
  const topNavLinks = appTopNavLinks.map((link) => ({
    ...link,
    isActive: isAppTopNavActive(href, link.href),
  }))

  return (
    <>
      <Header fixed className='border-b bg-background/80'>
        <TopNav links={topNavLinks} />
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
