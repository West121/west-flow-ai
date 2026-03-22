import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { NodeFormRenderer } from './node-form-renderer'
import { ProcessFormRenderer } from './process-form-renderer'

describe('runtime form renderers', () => {
  it('renders the registered process form and propagates form data changes', () => {
    const onChange = vi.fn()

    function TestHarness() {
      const [value, setValue] = useState<Record<string, unknown>>({
        days: 2,
        reason: '事假',
      })

      return (
        <ProcessFormRenderer
          processFormKey='oa-leave-start-form'
          processFormVersion='1.0.0'
          value={value}
          onChange={(nextValue) => {
            setValue(nextValue)
            onChange(nextValue)
          }}
        />
      )
    }

    render(<TestHarness />)

    fireEvent.change(screen.getByLabelText('请假天数'), {
      target: { value: '3' },
    })
    fireEvent.change(screen.getByLabelText('请假原因'), {
      target: { value: '外出处理事务' },
    })

    expect(onChange).toHaveBeenLastCalledWith({
      days: 3,
      reason: '外出处理事务',
    })
  })

  it('renders the registered node form and propagates task form data changes', () => {
    const onChange = vi.fn()

    function TestHarness() {
      const [value, setValue] = useState<Record<string, unknown>>({
        approved: false,
        comment: '请补充说明',
      })

      return (
        <NodeFormRenderer
          nodeFormKey='oa-leave-approve-form'
          nodeFormVersion='1.0.0'
          value={value}
          onChange={(nextValue) => {
            setValue(nextValue)
            onChange(nextValue)
          }}
          fieldBindings={[
            {
              source: 'PROCESS_FORM',
              sourceFieldKey: 'days',
              targetFieldKey: 'approvedDays',
            },
          ]}
          taskFormData={{ approved: false, comment: '请补充说明' }}
        />
      )
    }

    render(<TestHarness />)

    fireEvent.click(screen.getByLabelText('同意通过'))
    fireEvent.change(screen.getByLabelText('审批意见'), {
      target: { value: '同意，按 3 天执行' },
    })

    expect(onChange).toHaveBeenLastCalledWith({
      approved: true,
      comment: '同意，按 3 天执行',
    })
  })

  it('shows a clear prompt when a process form is not registered', () => {
    render(
      <ProcessFormRenderer
        processFormKey='missing-process-form'
        processFormVersion='1.0.0'
        value={{}}
        onChange={() => {}}
      />
    )

    expect(screen.getByText('表单组件未注册')).toBeInTheDocument()
    expect(screen.getByText(/missing-process-form/)).toBeInTheDocument()
  })
})
