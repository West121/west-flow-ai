import React, { useState } from 'react'
import useDialogState from '@/hooks/use-dialog-state'
import { type Task } from '../data/schema'

type TasksDialogType = 'create' | 'update' | 'delete' | 'import'

type TasksContextType = {
  open: TasksDialogType | null
  setOpen: (str: TasksDialogType | null) => void
  currentRow: Task | null
  setCurrentRow: React.Dispatch<React.SetStateAction<Task | null>>
}

const TasksContext = React.createContext<TasksContextType | null>(null)

// 任务页状态容器，统一管理弹窗状态和当前行。
export function TasksProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useDialogState<TasksDialogType>(null)
  const [currentRow, setCurrentRow] = useState<Task | null>(null)

  return (
    <TasksContext value={{ open, setOpen, currentRow, setCurrentRow }}>
      {children}
    </TasksContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useTasks = () => {
  // 从上下文读取任务页共享状态。
  const tasksContext = React.useContext(TasksContext)

  if (!tasksContext) {
    throw new Error('useTasks has to be used within <TasksContext>')
  }

  return tasksContext
}
