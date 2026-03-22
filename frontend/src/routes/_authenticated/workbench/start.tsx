import { createFileRoute } from '@tanstack/react-router'
import { WorkbenchStartPage } from '@/features/workbench/pages'

export const Route = createFileRoute('/_authenticated/workbench/start')({
  component: WorkbenchStartPage,
})
