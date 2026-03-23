import type { SidebarMenuNode } from '@/lib/api/system-menus'
import type { NavGroup, NavItem } from './types'
import { menuIconRegistry } from './menu-icon-registry'

function resolveIcon(iconName: string | null) {
  if (!iconName) {
    return undefined
  }

  return menuIconRegistry[iconName]
}

function toNavItem(node: SidebarMenuNode): NavItem | null {
  const icon = resolveIcon(node.iconName)

  if (node.children.length > 0) {
    const items = node.children
      .filter((child) => child.routePath)
      .map((child) => ({
        title: child.title,
        url: child.routePath!,
        icon: resolveIcon(child.iconName),
      }))

    if (items.length === 0 && !node.routePath) {
      return null
    }

    if (items.length === 0 && node.routePath) {
      return {
        title: node.title,
        url: node.routePath,
        icon,
      }
    }

    return {
      title: node.title,
      icon,
      items,
    }
  }

  if (!node.routePath) {
    return null
  }

  return {
    title: node.title,
    url: node.routePath,
    icon,
  }
}

export function buildNavGroups(menuTree: SidebarMenuNode[]): NavGroup[] {
  return menuTree
    .map((root) => {
      const items = root.children
        .map((child) => toNavItem(child))
        .filter((child): child is NavItem => child !== null)

      if (items.length === 0 && root.routePath) {
        const rootItem = toNavItem(root)

        return rootItem
          ? {
              title: root.title,
              items: [rootItem],
            }
          : null
      }

      if (items.length === 0) {
        return null
      }

      return {
        title: root.title,
        items,
      }
    })
    .filter((group): group is NavGroup => group !== null)
}

export function flattenSidebarMenuItems(menuTree: SidebarMenuNode[]) {
  return buildNavGroups(menuTree).flatMap((group) =>
    group.items.flatMap((item) => {
      if ('url' in item) {
        return [
          {
            groupTitle: group.title,
            title: item.title,
            url: item.url,
          },
        ]
      }

      return item.items.map((child) => ({
        groupTitle: group.title,
        title: `${item.title} / ${child.title}`,
        url: child.url,
      }))
    })
  )
}
