import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { UserPickerField } from './user-picker-field'

vi.mock('@/lib/api/system-users', () => ({
  listSystemUsers: vi.fn(async (_search: { keyword?: string }) => ({
    page: 1,
    pageSize: 20,
    total: 1,
    pages: 1,
    records: [
      {
        userId: 'usr_002',
        displayName: '李四',
        username: 'lisi',
        mobile: '13800000000',
        email: 'lisi@example.com',
        departmentName: '研发部',
        postName: '工程师',
        status: 'ENABLED',
        createdAt: '2026-03-26T08:00:00Z',
      },
    ],
    groups: [],
  })),
}))

describe('UserPickerField', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('should open popover near the trigger and select a user', async () => {
    const onChange = vi.fn()

    render(<UserPickerField onChange={onChange} placeholder='请选择负责人' />)

    fireEvent.click(screen.getByRole('button', { name: /请选择负责人/ }))

    await screen.findByPlaceholderText('搜索姓名、账号、部门或岗位')
    await waitFor(() => {
      expect(screen.getByText('李四')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('李四'))

    expect(onChange).toHaveBeenCalledWith('usr_002')
    expect(screen.queryByPlaceholderText('搜索姓名、账号、部门或岗位')).not.toBeInTheDocument()
  })
})
