import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NodeFormSelector, ProcessFormSelector } from './form-selection'

describe('runtime form selection', () => {
  it('selects a process default form from the static registry', () => {
    const onChange = vi.fn()

    render(
      <ProcessFormSelector
        label='流程默认表单'
        description='发起页和任务页回退时使用'
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText('流程默认表单')).toBeInTheDocument()
    expect(screen.getByText('流程表单已注册')).toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: '流程默认表单编码' }), {
      target: { value: 'oa-leave-start-form' },
    })

    expect(onChange).toHaveBeenCalledWith({
      processFormKey: 'oa-leave-start-form',
      processFormVersion: '1.0.0',
    })
  })

  it('selects a node override form from the static registry', () => {
    const onChange = vi.fn()

    render(
      <NodeFormSelector
        label='节点表单'
        description='审批节点覆盖流程默认表单'
        value={null}
        onChange={onChange}
      />
    )

    expect(screen.getByText('节点表单')).toBeInTheDocument()
    expect(screen.getByText('节点表单已注册')).toBeInTheDocument()

    fireEvent.change(screen.getByRole('combobox', { name: '节点表单编码' }), {
      target: { value: 'oa-leave-approve-form' },
    })

    expect(onChange).toHaveBeenCalledWith({
      nodeFormKey: 'oa-leave-approve-form',
      nodeFormVersion: '1.0.0',
    })
  })
})
