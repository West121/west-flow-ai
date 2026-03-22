import { createFileRoute } from '@tanstack/react-router'
import { PostCreatePage } from '@/features/system/org-pages'

// 岗位新建路由只负责挂载表单页。
export const Route = createFileRoute('/_authenticated/system/posts/create')({
  component: PostCreatePage,
})
