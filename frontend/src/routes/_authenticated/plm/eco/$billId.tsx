import { createFileRoute } from '@tanstack/react-router'
import { PLMECOExecutionBillDetailPage } from '@/features/plm/pages'

// ECO 详情路由只负责把单号参数传给统一审批单详情页。
export const Route = createFileRoute('/_authenticated/plm/eco/$billId')({
  component: function PLMECOExecutionBillDetailRoute() {
    const { billId } = Route.useParams()

    return <PLMECOExecutionBillDetailPage billId={billId} />
  },
})
