import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  Dialog,
  DialogContent,
  DialogTrigger,
} from '@/components/pro-dialog'

function ExampleDialog() {
  return (
    <Dialog defaultOpen>
      <DialogTrigger asChild>
        <button type='button'>打开</button>
      </DialogTrigger>
      <DialogContent
        title='离职转办'
        description='选择来源用户和目标用户'
        draggable
        resizable
        fullscreenable
        minimizable
      >
        <div>内容</div>
      </DialogContent>
    </Dialog>
  )
}

describe('ProDialogContent', () => {
  it('uses content height in normal mode and hides fullscreen in minimized mode', () => {
    render(<ExampleDialog />)

    const dialog = screen.getByRole('dialog', { name: '离职转办' })
    expect(dialog).toHaveStyle({ height: 'auto' })
    expect(dialog).toHaveStyle({ maxHeight: 'calc(100vh - 2rem)' })
    expect(screen.getByRole('button', { name: '最小化' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '全屏' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '最小化' }))

    expect(screen.getByRole('button', { name: '还原' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '全屏' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '最小化' })).not.toBeInTheDocument()
  })
})
