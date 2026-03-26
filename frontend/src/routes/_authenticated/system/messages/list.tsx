import { createFileRoute } from '@tanstack/react-router'
import { MessagesListPage } from '@/features/system/message-pages'
import { listQueryRouteSearchSchema, normalizeListQuerySearch } from '@/features/shared/table/query-contract'

// 消息列表路由只负责校验查询参数并挂载页面。
export const Route = createFileRoute('/_authenticated/system/messages/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: MessagesListRoute,
})

function MessagesListRoute() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <MessagesListPage search={search} navigate={navigate} />
}
