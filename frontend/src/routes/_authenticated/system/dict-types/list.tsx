import { createFileRoute } from '@tanstack/react-router'
import {
  DictManagementPage,
  dictManagementSearchSchema,
} from '@/features/system/dict-management-page'

// 字典管理路由挂载统一页面并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/dict-types/list')({
  validateSearch: dictManagementSearchSchema,
  component: DictTypesListRoute,
})

function DictTypesListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <DictManagementPage search={search} navigate={navigate} />
}
