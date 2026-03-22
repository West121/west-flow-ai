import { createFileRoute } from '@tanstack/react-router'
import { DictTypeEditPage } from '@/features/system/dict-pages'

// 字典类型编辑路由只负责传递路径参数。
export const Route = createFileRoute(
  '/_authenticated/system/dict-types/$dictTypeId/edit'
)({
  component: DictTypeEditRoute,
})

function DictTypeEditRoute() {
  const { dictTypeId } = Route.useParams()

  return <DictTypeEditPage dictTypeId={dictTypeId} />
}
