import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NodeFormRenderer } from './node-form-renderer'
import { ProcessFormRenderer } from './process-form-renderer'

const systemUserApiMocks = vi.hoisted(() => ({
  listSystemUsers: vi.fn(),
  getSystemUserDetail: vi.fn(),
}))

vi.mock('@/lib/api/system-users', () => systemUserApiMocks)

describe('runtime form renderers', () => {
  beforeEach(() => {
    systemUserApiMocks.listSystemUsers.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 2,
      pages: 1,
      groups: [],
      records: [
        {
          userId: 'usr_002',
          displayName: '李四',
          username: 'lisi',
          mobile: '13800000002',
          email: 'lisi@westflow.cn',
          departmentName: '人力资源部',
          postName: '部门负责人',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
        {
          userId: 'usr_005',
          displayName: '王主管',
          username: 'wangzhuguan',
          mobile: '13800000005',
          email: 'wangzhuguan@westflow.cn',
          departmentName: '运营中心',
          postName: '总监',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
      ],
    })
    systemUserApiMocks.getSystemUserDetail.mockImplementation(async (userId: string) => ({
      userId,
      displayName: userId === 'usr_002' ? '李四' : '王主管',
      username: userId === 'usr_002' ? 'lisi' : 'wangzhuguan',
      mobile: '',
      email: '',
      companyId: 'comp_001',
      companyName: '西流科技',
      departmentId: userId === 'usr_002' ? 'dept_002' : 'dept_005',
      departmentName: userId === 'usr_002' ? '人力资源部' : '运营中心',
      postId: userId === 'usr_002' ? 'post_002' : 'post_005',
      postName: userId === 'usr_002' ? '部门负责人' : '总监',
      roleIds: [],
      enabled: true,
      primaryAssignment: {
        userPostId: `up_${userId}`,
        companyId: 'comp_001',
        companyName: '西流科技',
        departmentId: userId === 'usr_002' ? 'dept_002' : 'dept_005',
        departmentName: userId === 'usr_002' ? '人力资源部' : '运营中心',
        postId: userId === 'usr_002' ? 'post_002' : 'post_005',
        postName: userId === 'usr_002' ? '部门负责人' : '总监',
        roleIds: [],
        roleNames: [],
        primary: true,
        enabled: true,
      },
      partTimeAssignments: [],
    }))
  })

  it('renders the registered process form and propagates form data changes', async () => {
    const onChange = vi.fn()

    function TestHarness() {
      const [value, setValue] = useState<Record<string, unknown>>({
        leaveType: 'ANNUAL',
        days: 2,
        reason: '事假',
        urgent: false,
        managerUserId: 'usr_002',
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

    fireEvent.click(screen.getByRole('combobox', { name: '请假类型' }))
    fireEvent.click(screen.getByRole('option', { name: '病假' }))
    fireEvent.change(screen.getByLabelText('请假天数'), {
      target: { value: '3' },
    })
    fireEvent.click(screen.getByRole('switch', { name: '是否紧急' }))
    fireEvent.click(screen.getByLabelText('直属负责人'))
    await waitFor(() => {
      const managerOptions = screen.getAllByText('王主管')
      fireEvent.click(managerOptions[managerOptions.length - 1]!)
    })
    fireEvent.change(screen.getByLabelText('请假原因'), {
      target: { value: '外出处理事务' },
    })

    expect(onChange).toHaveBeenLastCalledWith({
      leaveType: 'SICK',
      days: 3,
      leaveDays: 3,
      reason: '外出处理事务',
      urgent: true,
      managerUserId: 'usr_005',
    })
  })

  it('renders the leave process form for version 1.1.0', () => {
    render(
      <ProcessFormRenderer
        processFormKey='oa-leave-start-form'
        processFormVersion='1.1.0'
        value={{
          leaveType: 'ANNUAL',
          days: 2,
          reason: '事假',
          urgent: false,
          managerUserId: 'usr_002',
        }}
        onChange={() => {}}
      />
    )

    expect(screen.queryByText('表单组件未注册')).not.toBeInTheDocument()
    expect(
      screen.getByText('表单编码 oa-leave-start-form · 版本 1.1.0')
    ).toBeInTheDocument()
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
