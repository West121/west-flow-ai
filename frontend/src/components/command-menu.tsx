import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { ArrowRight, ChevronRight, Laptop, Moon, Sun } from 'lucide-react'
import { useSearch } from '@/context/search-provider'
import { useTheme } from '@/context/theme-provider'
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from '@/components/ui/command'
import { getSidebarMenuTree } from '@/lib/api/system-menus'
import { flattenSidebarMenuItems } from './layout/sidebar-menu-helpers'
import { ScrollArea } from './ui/scroll-area'

export function CommandMenu() {
  const navigate = useNavigate()
  const { setTheme } = useTheme()
  const { open, setOpen } = useSearch()
  const { data } = useQuery({
    queryKey: ['sidebar-menu-tree'],
    queryFn: getSidebarMenuTree,
  })
  const menuItems = React.useMemo(
    () => flattenSidebarMenuItems(data ?? []),
    [data]
  )

  const runCommand = React.useCallback(
    (command: () => unknown) => {
      // 先收起命令面板，再执行具体动作，避免焦点和遮罩残留。
      setOpen(false)
      command()
    },
    [setOpen]
  )

  return (
    <CommandDialog modal open={open} onOpenChange={setOpen}>
      <CommandInput placeholder='输入命令或搜索页面…' />
      <CommandList>
        <ScrollArea type='hover' className='h-72 pe-1'>
          <CommandEmpty>未找到匹配结果。</CommandEmpty>
          {Array.from(new Set(menuItems.map((item) => item.groupTitle))).map(
            (groupTitle) => (
              <CommandGroup key={groupTitle} heading={groupTitle}>
                {menuItems
                  .filter((item) => item.groupTitle === groupTitle)
                  .map((item) => (
                    <CommandItem
                      key={`${item.url}-${item.title}`}
                      value={`${groupTitle}-${item.title}`}
                      onSelect={() => {
                        runCommand(() => navigate({ to: item.url }))
                      }}
                    >
                      <div className='flex size-4 items-center justify-center'>
                        <ArrowRight className='size-2 text-muted-foreground/80' />
                      </div>
                      {item.title.includes(' / ') ? (
                        <>
                          {item.title.split(' / ')[0]} <ChevronRight />{' '}
                          {item.title.split(' / ')[1]}
                        </>
                      ) : (
                        item.title
                      )}
                    </CommandItem>
                  ))}
            </CommandGroup>
            )
          )}
          <CommandSeparator />
          {/* 主题切换也作为命令入口统一放在这里处理。 */}
          <CommandGroup heading='主题'>
            <CommandItem onSelect={() => runCommand(() => setTheme('light'))}>
              <Sun /> <span>浅色</span>
            </CommandItem>
            <CommandItem onSelect={() => runCommand(() => setTheme('dark'))}>
              <Moon className='scale-90' />
              <span>深色</span>
            </CommandItem>
            <CommandItem onSelect={() => runCommand(() => setTheme('system'))}>
              <Laptop />
              <span>跟随系统</span>
            </CommandItem>
          </CommandGroup>
        </ScrollArea>
      </CommandList>
    </CommandDialog>
  )
}
