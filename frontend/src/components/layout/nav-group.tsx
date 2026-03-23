import { type ReactNode } from 'react'
import { Link, useLocation } from '@tanstack/react-router'
import { ChevronRight } from 'lucide-react'
import { useAuthStore } from '@/stores/auth-store'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { Badge } from '../ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu'
import {
  type NavCollapsible,
  type NavItem,
  type NavLink,
  type NavGroup as NavGroupProps,
} from './types'

function isNavCollapsible(item: NavItem): item is NavCollapsible {
  return Array.isArray((item as NavCollapsible).items)
}

// 按权限过滤并渲染侧边栏导航分组。
export function NavGroup({ title, items }: NavGroupProps) {
  const { state, isMobile } = useSidebar()
  const href = useLocation({ select: (location) => location.href })
  const roleCodes = useAuthStore((state) => state.currentUser?.roles ?? [])
  const visibleItems = items
    .map((item) => filterNavItemByRole(item, roleCodes))
    .filter((item) => item !== null)

  if (visibleItems.length === 0) {
    return null
  }

  return (
    <SidebarGroup>
      {title ? <SidebarGroupLabel>{title}</SidebarGroupLabel> : null}
      <SidebarMenu>
        {visibleItems.map((item) => {
          const key = `${item.title}-${'url' in item ? item.url : item.url ?? 'branch'}`
          return (
            <SidebarTreeItem
              key={key}
              item={item}
              href={href}
              depth={0}
              collapsed={state === 'collapsed' && !isMobile}
            />
          )
        })}
      </SidebarMenu>
    </SidebarGroup>
  )
}

function NavBadge({ children }: { children: ReactNode }) {
  return <Badge className='rounded-full px-1 py-0 text-xs'>{children}</Badge>
}

// 普通导航项，负责处理激活态和移动端收起侧边栏。
function SidebarMenuLink({ item, href }: { item: NavLink; href: string }) {
  const { setOpenMobile } = useSidebar()
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={checkIsActive(href, item)}
        tooltip={item.title}
      >
        <Link to={item.url} onClick={() => setOpenMobile(false)}>
          {item.icon && <item.icon />}
          <span>{item.title}</span>
          {item.badge && <NavBadge>{item.badge}</NavBadge>}
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  )
}

function SidebarMenuCollapsible({
  item,
  href,
  depth,
}: {
  item: NavCollapsible
  href: string
  depth: number
}) {
  const { setOpenMobile } = useSidebar()
  return (
    <Collapsible
      asChild
      defaultOpen={checkIsActive(href, item, true)}
      className='group/collapsible'
    >
      <SidebarMenuItem>
        <CollapsibleTrigger asChild>
          <SidebarMenuButton tooltip={item.title}>
            {item.icon && <item.icon />}
            <span>{item.title}</span>
            {item.badge && <NavBadge>{item.badge}</NavBadge>}
            <ChevronRight className='ms-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 rtl:rotate-180' />
          </SidebarMenuButton>
        </CollapsibleTrigger>
        <CollapsibleContent className='CollapsibleContent'>
          <SidebarMenuSub className={depth > 0 ? 'ms-3 border-s border-sidebar-border/60 ps-2' : ''}>
            {item.items.map((subItem) => (
              <SidebarTreeSubItem
                key={`${subItem.title}-${'url' in subItem ? subItem.url : subItem.url ?? 'branch'}`}
                item={subItem}
                href={href}
                depth={depth + 1}
                onNavigate={() => setOpenMobile(false)}
              />
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  )
}

// 收起侧边栏时使用的下拉菜单形式导航。
function SidebarMenuCollapsedDropdown({
  item,
  href,
}: {
  item: NavCollapsible
  href: string
}) {
  const links = flattenTreeLinks(item)

  return (
    <SidebarMenuItem>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <SidebarMenuButton
            tooltip={item.title}
            isActive={checkIsActive(href, item)}
          >
            {item.icon && <item.icon />}
            <span>{item.title}</span>
            {item.badge && <NavBadge>{item.badge}</NavBadge>}
            <ChevronRight className='ms-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90' />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent side='right' align='start' sideOffset={4}>
          <DropdownMenuLabel>
            {item.title} {item.badge ? `(${item.badge})` : ''}
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          {links.map((sub) => (
            <DropdownMenuItem key={`${sub.title}-${sub.url}`} asChild>
              <Link
                to={sub.url}
                className={`${checkIsActive(href, sub) ? 'bg-secondary' : ''}`}
              >
                {sub.icon && <sub.icon />}
                <span className='max-w-52 text-wrap'>{sub.title}</span>
                {sub.badge && (
                  <span className='ms-auto text-xs'>{sub.badge}</span>
                )}
              </Link>
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  )
}

function SidebarTreeItem({
  item,
  href,
  depth,
  collapsed,
}: {
  item: NavItem
  href: string
  depth: number
  collapsed: boolean
}) {
  if (!isNavCollapsible(item)) {
    return <SidebarMenuLink item={item} href={href} />
  }

  if (collapsed) {
    return <SidebarMenuCollapsedDropdown item={item} href={href} />
  }

  return <SidebarMenuCollapsible item={item} href={href} depth={depth} />
}

function SidebarTreeSubItem({
  item,
  href,
  depth,
  onNavigate,
}: {
  item: NavItem
  href: string
  depth: number
  onNavigate: () => void
}) {
  if (!isNavCollapsible(item)) {
    return (
      <SidebarMenuSubItem>
        <SidebarMenuSubButton asChild isActive={checkIsActive(href, item)}>
          <Link to={item.url} onClick={onNavigate}>
            {item.icon && <item.icon />}
            <span>{item.title}</span>
            {item.badge && <NavBadge>{item.badge}</NavBadge>}
          </Link>
        </SidebarMenuSubButton>
      </SidebarMenuSubItem>
    )
  }

  return (
    <Collapsible
      asChild
      defaultOpen={checkIsActive(href, item, true)}
      className='group/collapsible'
    >
      <SidebarMenuSubItem>
        <CollapsibleTrigger asChild>
          <SidebarMenuSubButton isActive={checkIsActive(href, item, true)}>
            {item.icon && <item.icon />}
            <span>{item.title}</span>
            {item.badge && <NavBadge>{item.badge}</NavBadge>}
            <ChevronRight className='ms-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 rtl:rotate-180' />
          </SidebarMenuSubButton>
        </CollapsibleTrigger>
        <CollapsibleContent>
          <SidebarMenuSub className='ms-3 border-s border-sidebar-border/60 ps-2'>
            {item.items.map((child) => (
              <SidebarTreeSubItem
                key={`${child.title}-${'url' in child ? child.url : child.url ?? 'branch'}`}
                item={child}
                href={href}
                depth={depth + 1}
                onNavigate={onNavigate}
              />
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuSubItem>
    </Collapsible>
  )
}

// 判断导航项是否命中当前地址。
function checkIsActive(href: string, item: NavItem, mainNav = false): boolean {
  // 侧边栏高亮要同时兼容完整 URL、去掉 query 的路径和子菜单命中。
  return (
    href === item.url || // 带 query 的完整路径
    href.split('?')[0] === item.url || // 去掉 query 后的纯路径
    (isNavCollapsible(item) && item.items.some((i) => checkIsActive(href, i))) || // 子菜单递归命中
    (mainNav &&
      href.split('/')[1] !== '' &&
      href.split('/')[1] === item?.url?.split('/')[1])
  )
}

// 根据当前用户角色过滤掉无权限可见的导航项。
function filterNavItemByRole(item: NavItem, roleCodes: string[]): NavItem | null {
  if (item.requiredRoles?.length) {
    const matched = item.requiredRoles.some((roleCode) =>
      roleCodes.includes(roleCode)
    )

    if (!matched) {
      return null
    }
  }

  if (!isNavCollapsible(item)) {
    return item
  }

  const visibleChildren = item.items.filter((child) => {
    if (!child.requiredRoles?.length) {
      return true
    }

    return child.requiredRoles.some((roleCode) => roleCodes.includes(roleCode))
  })

  if (visibleChildren.length === 0) {
    return null
  }

  return {
    ...item,
    items: visibleChildren,
  }
}

function flattenTreeLinks(item: NavCollapsible, ancestors: string[] = []): NavLink[] {
  const current = [...ancestors, item.title]
  const self = item.url
    ? [
        {
          title: current.join(' / '),
          url: item.url,
          icon: item.icon,
          badge: item.badge,
        } satisfies NavLink,
      ]
    : []

  const descendants = item.items.flatMap((child) => {
    if (isNavCollapsible(child)) {
      return flattenTreeLinks(child, current)
    }

    return [
      {
        ...child,
        title: [...current, child.title].join(' / '),
      } satisfies NavLink,
    ]
  })

  return [...self, ...descendants]
}
