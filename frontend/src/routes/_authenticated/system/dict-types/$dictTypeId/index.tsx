import { createFileRoute } from '@tanstack/react-router'
import { DictTypeDetailPage } from '@/features/system/dict-pages'

// 字典类型详情路由只处理参数透传。
export const Route = createFileRoute('/_authenticated/system/dict-types/$dictTypeId/')({
  component: DictTypeDetailRoute,
})

function DictTypeDetailRoute() {
  const { dictTypeId } = Route.useParams()

  return <DictTypeDetailPage dictTypeId={dictTypeId} />
}
