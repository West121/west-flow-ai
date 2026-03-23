import { useQuery } from '@tanstack/react-query'
import { useLayout } from '@/context/layout-provider'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from '@/components/ui/sidebar'
import { getSidebarMenuTree } from '@/lib/api/system-menus'
import { sidebarData } from './data/sidebar-data'
import { buildNavGroups } from './sidebar-menu-helpers'
import { NavGroup } from './nav-group'
import { NavUser } from './nav-user'
import { TeamSwitcher } from './team-switcher'

// 应用侧边栏，集中承载工作空间、导航分组和用户菜单。
export function AppSidebar() {
  const { collapsible, variant } = useLayout()
  const { data } = useQuery({
    queryKey: ['sidebar-menu-tree'],
    queryFn: getSidebarMenuTree,
  })
  const navGroups = buildNavGroups(data ?? [])

  return (
    <Sidebar collapsible={collapsible} variant={variant}>
      <SidebarHeader>
        <TeamSwitcher teams={sidebarData.teams} />

        {/* 如需普通应用标题，可以把 TeamSwitcher 替换成 AppTitle。 */}
        {/* <AppTitle /> */}
      </SidebarHeader>
      <SidebarContent>
        {navGroups.map((props) => (
          <NavGroup key={props.title} {...props} />
        ))}
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={sidebarData.user} />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
