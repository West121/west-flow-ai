import { redirect } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/auth-store'

export function ensureProcessAdminRouteAccess() {
  const roles = useAuthStore.getState().currentUser?.roles ?? []

  // 代理关系管理和离职转办执行属于系统侧敏感操作，只允许流程管理员进入。
  if (!roles.includes('PROCESS_ADMIN')) {
    throw redirect({
      to: '/403',
      replace: true,
    })
  }
}
