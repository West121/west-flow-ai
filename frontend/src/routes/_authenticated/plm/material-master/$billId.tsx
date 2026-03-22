import { createFileRoute } from '@tanstack/react-router'
import { PLMMaterialChangeBillDetailPage } from '@/features/plm/pages'

// 物料主数据变更详情路由只负责把单号参数传给统一审批单详情页。
export const Route = createFileRoute(
  '/_authenticated/plm/material-master/$billId'
)({
  component: function PLMMaterialChangeBillDetailRoute() {
    const { billId } = Route.useParams()

    return <PLMMaterialChangeBillDetailPage billId={billId} />
  },
})
