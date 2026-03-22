import { createFileRoute } from '@tanstack/react-router'
import { PLMECRRequestBillDetailPage } from '@/features/plm/pages'

// ECR 详情路由只负责把单号参数传给统一审批单详情页。
export const Route = createFileRoute('/_authenticated/plm/ecr/$billId')({
  component: function PLMECRRequestBillDetailRoute() {
    const { billId } = Route.useParams()

    return <PLMECRRequestBillDetailPage billId={billId} />
  },
})
