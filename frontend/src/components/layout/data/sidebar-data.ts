import { Command } from 'lucide-react'
import { type SidebarData } from '../types'

// 侧边栏静态数据只保留团队与用户展示信息，菜单树统一走后端查询。
export const sidebarData: SidebarData = {
  user: {
    name: '平台用户',
    email: '登录后自动显示账号信息',
    avatar: '',
  },
  teams: [
    {
      name: '西流智能流程平台',
      logo: Command,
      plan: '流程平台',
    },
  ],
}
