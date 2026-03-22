import { createFileRoute } from '@tanstack/react-router'
import { PostCreatePage } from '@/features/system/org-pages'

export const Route = createFileRoute('/_authenticated/system/posts/create')({
  component: PostCreatePage,
})
