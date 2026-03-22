import { toast } from 'sonner'
import { getApiErrorMessage } from '@/lib/api/client'

export function handleServerError(error: unknown) {
  toast.error(getApiErrorMessage(error))
}
