import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NodeFormSelector, ProcessFormSelector } from './form-selection'

describe('runtime form selection', () => {
  it('selects a process default form from the static registry', () => {
    const onChange = vi.fn()

    render(
      <ProcessFormSelector
        label='流程默认表单'
        value={{
          processFormKey: 'oa-leave-start-form',
          processFormVersion: '1.0.0',
        }}
        onChange={onChange}
      />
    )

    expect(screen.getByText('流程默认表单')).toBeInTheDocument()
    expect(screen.getByText('编码：oa-leave-start-form')).toBeInTheDocument()
    expect(
      screen.getByRole('combobox', { name: '流程默认表单' })
    ).toHaveValue('oa-leave-start-form@@1.0.0')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('selects a node override form from the static registry', () => {
    const onChange = vi.fn()

    render(
      <NodeFormSelector
        label='节点表单'
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText('节点表单')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '节点表单' })).toHaveValue('')
    expect(screen.getByText('请选择表单')).toBeInTheDocument()
    expect(screen.queryByText('编码：oa-leave-approve-form')).not.toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: '节点表单' }), {
      target: { value: 'oa-leave-approve-form@@1.0.0' },
    })

    expect(onChange).toHaveBeenCalledWith({
      nodeFormKey: 'oa-leave-approve-form',
      nodeFormVersion: '1.0.0',
    })
  })
})
