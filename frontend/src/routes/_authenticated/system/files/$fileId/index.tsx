import { createFileRoute } from '@tanstack/react-router'
import { FileDetailPage } from '@/features/system/file-pages'

export const Route = createFileRoute('/_authenticated/system/files/$fileId/')({
  component: FileDetailPage,
})
