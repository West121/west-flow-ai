import { createFileRoute } from '@tanstack/react-router'
import { PostDetailPage } from '@/features/system/org-pages'

export const Route = createFileRoute('/_authenticated/system/posts/$postId/')({
  component: PostDetailRoute,
})

function PostDetailRoute() {
  const { postId } = Route.useParams()

  return <PostDetailPage postId={postId} />
}
