import { createFileRoute } from '@tanstack/react-router'
import { WorkbenchPredictionCockpitPage } from '@/features/workbench/pages'

export const Route = createFileRoute('/_authenticated/workbench/prediction')({
  component: WorkbenchPredictionCockpitPage,
})
