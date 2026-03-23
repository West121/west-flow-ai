import { createFileRoute } from '@tanstack/react-router'
import { AiToolCreatePage } from '@/features/ai-admin/registry-pages'

// AI 工具新建路由。
export const Route = createFileRoute('/_authenticated/system/ai/tools/create')({
  component: AiToolCreatePage,
})
