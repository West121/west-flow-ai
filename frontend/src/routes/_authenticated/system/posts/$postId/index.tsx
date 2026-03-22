import { createFileRoute } from '@tanstack/react-router'
import { PostDetailPage } from '@/features/system/org-pages'

// 岗位详情路由只负责把参数传给详情页。
export const Route = createFileRoute('/_authenticated/system/posts/$postId/')({
  component: PostDetailRoute,
})

function PostDetailRoute() {
  const { postId } = Route.useParams()

  return <PostDetailPage postId={postId} />
}
