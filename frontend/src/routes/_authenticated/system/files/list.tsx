import { createFileRoute } from '@tanstack/react-router'
import { FileListPage } from '@/features/system/file-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/files/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: FileListPage,
})
