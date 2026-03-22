import { createFileRoute } from '@tanstack/react-router'
import { FileListPage } from '@/features/system/file-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/files/list')({
  validateSearch: listQuerySearchSchema,
  component: FileListPage,
})
