import { type LinkProps } from '@tanstack/react-router'

type User = {
  name: string
  email: string
  avatar: string
}

type Team = {
  name: string
  logo: React.ElementType
  plan: string
}

type BaseNavItem = {
  title: string
  badge?: string
  icon?: React.ElementType
  requiredRoles?: string[]
}

// 叶子导航节点对应一个可直接跳转的页面。
type NavLink = BaseNavItem & {
  url: LinkProps['to'] | (string & {})
  items?: never
}

// 树形导航节点允许继续嵌套更多目录或菜单。
type NavCollapsible = BaseNavItem & {
  items: NavItem[]
  url?: LinkProps['to'] | (string & {})
}

type NavItem = NavCollapsible | NavLink

type NavGroup = {
  title?: string
  items: NavItem[]
}

// 侧边栏完整数据结构，确保布局层和导航层读取同一份定义。
type SidebarData = {
  user: User
  teams: Team[]
}

export type { SidebarData, NavGroup, NavItem, NavCollapsible, NavLink }
