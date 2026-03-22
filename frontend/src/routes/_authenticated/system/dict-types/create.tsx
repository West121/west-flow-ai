import { createFileRoute } from '@tanstack/react-router'
import { DictTypeCreatePage } from '@/features/system/dict-pages'

// 字典类型新建路由仅负责挂载对应页面。
export const Route = createFileRoute('/_authenticated/system/dict-types/create')({
  component: DictTypeCreatePage,
})
