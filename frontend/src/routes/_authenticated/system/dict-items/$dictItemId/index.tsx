import { createFileRoute } from '@tanstack/react-router'
import { DictItemDetailPage } from '@/features/system/dict-pages'

// 字典项详情路由只负责参数注入。
export const Route = createFileRoute('/_authenticated/system/dict-items/$dictItemId/')({
  component: DictItemDetailRoute,
})

function DictItemDetailRoute() {
  const { dictItemId } = Route.useParams()

  return <DictItemDetailPage dictItemId={dictItemId} />
}
