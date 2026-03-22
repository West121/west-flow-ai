import { createFileRoute } from '@tanstack/react-router'
import { DictItemEditPage } from '@/features/system/dict-pages'

// 字典项编辑路由只负责参数透传。
export const Route = createFileRoute('/_authenticated/system/dict-items/$dictItemId/edit')({
  component: DictItemEditRoute,
})

function DictItemEditRoute() {
  const { dictItemId } = Route.useParams()

  return <DictItemEditPage dictItemId={dictItemId} />
}
