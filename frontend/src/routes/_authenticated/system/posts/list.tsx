import { createFileRoute } from '@tanstack/react-router'
import { PostsListPage } from '@/features/system/org-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 岗位列表路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/posts/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: PostsListPage,
})
