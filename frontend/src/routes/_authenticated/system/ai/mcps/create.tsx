import { createFileRoute } from '@tanstack/react-router'
import { AiMcpCreatePage } from '@/features/ai-admin/registry-pages'

// AI MCP 新建路由。
export const Route = createFileRoute('/_authenticated/system/ai/mcps/create')({
  component: AiMcpCreatePage,
})
