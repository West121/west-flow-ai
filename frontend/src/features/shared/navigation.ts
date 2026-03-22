export const appTopNavLinks = [
  { title: '平台总览', href: '/' },
  { title: '工作台待办', href: '/workbench/todos/list' },
  { title: '系统用户', href: '/system/users/list' },
  { title: '流程定义', href: '/workflow/definitions/list' },
  { title: '流程设计器', href: '/workflow/designer' },
]

// 判断顶部导航在当前地址下是否应该高亮。
export function isAppTopNavActive(currentHref: string, href: string) {
  const pathname = currentHref.split('?')[0]

  if (href === '/') {
    return pathname === '/'
  }

  return pathname === href || pathname.startsWith(`${href}/`)
}
