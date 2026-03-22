import { createFileRoute } from '@tanstack/react-router'
import { FileEditPage } from '@/features/system/file-pages'

export const Route = createFileRoute('/_authenticated/system/files/$fileId/edit')({
  component: FileEditPage,
})
