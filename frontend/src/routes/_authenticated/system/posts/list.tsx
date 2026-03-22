import { createFileRoute } from '@tanstack/react-router'
import { PostsListPage } from '@/features/system/org-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/posts/list')({
  validateSearch: listQuerySearchSchema,
  component: PostsListPage,
})
