import { createFileRoute } from '@tanstack/react-router'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'
import { DictTypesListPage } from '@/features/system/dict-pages'

// 字典类型列表路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/dict-types/list')({
  validateSearch: listQuerySearchSchema,
  component: DictTypesListRoute,
})

function DictTypesListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <DictTypesListPage search={search} navigate={navigate} />
}
