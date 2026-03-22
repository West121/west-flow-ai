import { toast } from 'sonner'
import { getApiErrorMessage } from '@/lib/api/client'

// 统一把服务端错误转成用户可见的提示。
export function handleServerError(error: unknown) {
  toast.error(getApiErrorMessage(error))
}
