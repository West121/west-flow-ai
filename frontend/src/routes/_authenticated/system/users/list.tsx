import { createFileRoute } from '@tanstack/react-router'
import { UsersListPage } from '@/features/system/user-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// 用户列表路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/users/list')({
  validateSearch: listQuerySearchSchema,
  component: UsersListPage,
})
