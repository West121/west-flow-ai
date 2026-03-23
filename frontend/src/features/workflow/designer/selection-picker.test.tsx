import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { WorkflowPrincipalPickerField } from './selection-picker'

vi.mock('./selection-api', () => ({
  searchPrincipalOptions: vi.fn(async (kind: string) => {
    if (kind !== 'DEPARTMENT') {
      return []
    }
    return [
      {
        id: 'dept_root',
        label: '行政中心',
        description: '西流科技',
        kind: 'DEPARTMENT',
        companyId: 'cmp_001',
        parentId: null,
        groupLabel: '西流科技',
      },
      {
        id: 'dept_child',
        label: '人事部',
        description: '西流科技',
        kind: 'DEPARTMENT',
        companyId: 'cmp_001',
        parentId: 'dept_root',
        groupLabel: '西流科技',
      },
    ]
  }),
}))

describe('workflow principal picker', () => {
  it('renders department tree and resolves selected labels', async () => {
    const onChange = vi.fn()

    render(
      <WorkflowPrincipalPickerField
        kind='DEPARTMENT'
        label='部门'
        value={['dept_child']}
        onChange={onChange}
      />
    )

    await waitFor(() => {
      expect(screen.getByDisplayValue('人事部')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: '选择' }))

    await waitFor(() => {
      expect(screen.getAllByText('西流科技').length).toBeGreaterThan(0)
      expect(screen.getByText('行政中心')).toBeInTheDocument()
      expect(screen.getAllByText('人事部').length).toBeGreaterThan(0)
    })
  })
})
