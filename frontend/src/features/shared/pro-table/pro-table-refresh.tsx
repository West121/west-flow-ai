import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function ProTableRefresh({
  onRefresh,
  isRefreshing = false,
}: {
  onRefresh?: () => void
  isRefreshing?: boolean
}) {
  return (
    <Button
      variant='outline'
      size='sm'
      type='button'
      onClick={onRefresh}
      disabled={!onRefresh || isRefreshing}
    >
      <RefreshCw className={isRefreshing ? 'animate-spin' : undefined} />
      刷新
    </Button>
  )
}
