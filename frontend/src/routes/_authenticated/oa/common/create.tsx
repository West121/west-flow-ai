import { createFileRoute } from '@tanstack/react-router'
import { OACommonCreatePage } from '@/features/oa/pages'

export const Route = createFileRoute('/_authenticated/oa/common/create')({
  component: OACommonCreatePage,
})
