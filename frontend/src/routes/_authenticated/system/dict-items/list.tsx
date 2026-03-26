import { createFileRoute } from '@tanstack/react-router'
import {
  DictManagementPage,
  dictManagementSearchSchema,
  normalizeDictManagementSearch,
} from '@/features/system/dict-management-page'

// 字典项列表路由也复用统一的字典管理页面。
export const Route = createFileRoute('/_authenticated/system/dict-items/list')({
  validateSearch: dictManagementSearchSchema,
  component: DictItemsListRoute,
})

function DictItemsListRoute() {
  const search = normalizeDictManagementSearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <DictManagementPage search={search} navigate={navigate} />
}
