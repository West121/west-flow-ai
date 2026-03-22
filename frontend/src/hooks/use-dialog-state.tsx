import { useState } from 'react'

/**
 * 用于确认对话框的通用状态 Hook。
 * @param initialState 初始值，支持字符串状态或布尔状态。
 * @returns 当前状态值，以及用于切换状态的函数。
 * @example const [open, setOpen] = useDialogState<"approve" | "reject">()
 */
export default function useDialogState<T extends string | boolean>(
  initialState: T | null = null
) {
  const [open, _setOpen] = useState<T | null>(initialState)

  const setOpen = (str: T | null) =>
    _setOpen((prev) => (prev === str ? null : str))

  return [open, setOpen] as const
}
