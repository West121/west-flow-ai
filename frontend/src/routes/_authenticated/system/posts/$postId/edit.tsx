import { createFileRoute } from '@tanstack/react-router'
import { PostEditPage } from '@/features/system/org-pages'

// 岗位编辑路由只负责把参数传给编辑页。
export const Route = createFileRoute(
  '/_authenticated/system/posts/$postId/edit'
)({
  component: PostEditRoute,
})

function PostEditRoute() {
  const { postId } = Route.useParams()

  return <PostEditPage postId={postId} />
}
