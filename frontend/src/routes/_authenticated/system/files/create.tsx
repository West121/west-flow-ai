import { createFileRoute } from '@tanstack/react-router'
import { FileCreatePage } from '@/features/system/file-pages'

export const Route = createFileRoute('/_authenticated/system/files/create')({
  component: FileCreatePage,
})
