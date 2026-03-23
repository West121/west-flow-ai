import { createFileRoute } from '@tanstack/react-router'
import { AiSkillCreatePage } from '@/features/ai-admin/registry-pages'

// AI 技能新建路由。
export const Route = createFileRoute('/_authenticated/system/ai/skills/create')({
  component: AiSkillCreatePage,
})
