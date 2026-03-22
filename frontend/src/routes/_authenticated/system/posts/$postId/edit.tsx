import { createFileRoute } from '@tanstack/react-router'
import { PostEditPage } from '@/features/system/org-pages'

export const Route = createFileRoute(
  '/_authenticated/system/posts/$postId/edit'
)({
  component: PostEditRoute,
})

function PostEditRoute() {
  const { postId } = Route.useParams()

  return <PostEditPage postId={postId} />
}
