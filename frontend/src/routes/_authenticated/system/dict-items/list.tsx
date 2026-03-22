import { createFileRoute } from '@tanstack/react-router'
import { DictItemsListPage } from '@/features/system/dict-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// 字典项列表路由仅负责校验查询参数并挂载列表页。
export const Route = createFileRoute('/_authenticated/system/dict-items/list')({
  validateSearch: listQuerySearchSchema,
  component: DictItemsListRoute,
})

function DictItemsListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <DictItemsListPage search={search} navigate={navigate} />
}
