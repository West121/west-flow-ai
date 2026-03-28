import { useEffect, useRef } from 'react'
import { useIsFetching } from '@tanstack/react-query'
import { useRouterState } from '@tanstack/react-router'
import LoadingBar, { type LoadingBarRef } from 'react-top-loading-bar'

export function NavigationProgress() {
  const ref = useRef<LoadingBarRef>(null)
  const state = useRouterState()
  const isFetching = useIsFetching()

  useEffect(() => {
    // 统一由路由切换和查询请求驱动顶部进度条，保证分页和手动刷新反馈一致。
    if (state.status === 'pending' || isFetching > 0) {
      ref.current?.continuousStart()
    } else {
      ref.current?.complete()
    }
  }, [isFetching, state.status])

  return (
    <LoadingBar
      color='var(--muted-foreground)'
      ref={ref}
      shadow={true}
      height={2}
    />
  )
}
