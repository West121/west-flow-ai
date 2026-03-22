import { createFileRoute } from '@tanstack/react-router'
import { DictItemCreatePage } from '@/features/system/dict-pages'

// 字典项新建路由只负责挂载对应页面。
export const Route = createFileRoute('/_authenticated/system/dict-items/create')({
  component: DictItemCreatePage,
})
