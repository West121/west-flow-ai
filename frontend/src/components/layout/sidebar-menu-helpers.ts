import type { SidebarMenuNode } from '@/lib/api/system-menus'
import type { NavGroup, NavItem } from './types'
import { menuIconRegistry } from './menu-icon-registry'

function hasChildren(item: NavItem): item is NavItem & { items: NavItem[] } {
  return Array.isArray((item as { items?: NavItem[] }).items)
}

function resolveIcon(iconName: string | null) {
  if (!iconName) {
    return undefined
  }

  return menuIconRegistry[iconName]
}

function toNavItem(node: SidebarMenuNode): NavItem | null {
  const icon = resolveIcon(node.iconName)
  if (node.menuType !== 'MENU' || !node.routePath) {
    return null
  }

  return {
    title: node.title,
    url: node.routePath,
    icon,
  }
}

function flattenMenuNodes(menuTree: SidebarMenuNode[]): NavItem[] {
  return menuTree.flatMap((node) => {
    const current = toNavItem(node)
    const descendants = flattenMenuNodes(node.children)
    return current == null ? descendants : [current, ...descendants]
  })
}

// 侧边栏现在直接按后端菜单树渲染，不再把目录拆成分组标题。
export function buildNavGroups(menuTree: SidebarMenuNode[]): NavGroup[] {
  const items = flattenMenuNodes(menuTree)

  if (items.length === 0) {
    return []
  }

  return [{ items }]
}

function flattenNavItem(
  item: NavItem,
  ancestors: string[],
  groupTitle: string
): Array<{
  groupTitle: string
  title: string
  url: string
}> {
  if (!hasChildren(item)) {
    return [
      {
        groupTitle,
        title: [...ancestors, item.title].join(' / '),
        url: String(item.url),
      },
    ]
  }

  const branchTitle = ancestors.length === 0 ? item.title : [...ancestors, item.title].join(' / ')
  const currentGroupTitle = ancestors.length === 0 ? item.title : groupTitle
  const branchSelf =
    item.url != null
      ? [
          {
            groupTitle: currentGroupTitle,
            title: branchTitle,
            url: item.url,
          },
        ]
      : []

  const descendants = item.items.flatMap((child) =>
    flattenNavItem(
      child,
      ancestors.length === 0 ? [item.title] : [...ancestors, item.title],
      currentGroupTitle
    )
  )

  return [...branchSelf, ...descendants]
}

export function flattenSidebarMenuItems(menuTree: SidebarMenuNode[]) {
  return buildNavGroups(menuTree).flatMap((group) =>
    group.items.flatMap((item) => flattenNavItem(item, [], item.title))
  )
}
