import { Link } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { type ApprovalSheetListItem } from '@/lib/api/workbench'

export type ApprovalSheetLinkMode = 'workbench' | 'oa'

// 审批单列表统一用这个方法格式化时间。
export function formatApprovalSheetDateTime(value: string | null | undefined) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

// 实例状态标签在工作台和 OA 页面保持一致。
export function resolveApprovalSheetInstanceStatusLabel(status: string) {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'REVOKED':
      return '已撤销'
    case 'TERMINATED':
      return '已终止'
    case 'COMPLETED':
      return '已完成'
    case 'RUNNING':
    default:
      return '进行中'
  }
}

function resolveApprovalSheetInstanceStatusVariant(status: string) {
  if (status === 'DRAFT') {
    return 'outline'
  }
  if (status === 'COMPLETED') {
    return 'secondary'
  }
  if (status === 'REVOKED' || status === 'TERMINATED') {
    return 'outline'
  }
  return 'destructive'
}

// 流程中心把自动化状态直接暴露成标签，便于一眼看出规则是否已经生效。
// 自动化状态标签统一由这里生成。
export function resolveApprovalSheetAutomationStatusLabel(
  status: string | null | undefined
) {
  switch (status) {
    case 'SUCCESS':
      return '执行成功'
    case 'FAILED':
      return '执行失败'
    case 'RUNNING':
      return '执行中'
    case 'SKIPPED':
      return '已跳过'
    case 'DISABLED':
      return '已停用'
    case 'PENDING':
      return '待执行'
    default:
      return '未配置'
  }
}

function resolveApprovalSheetAutomationStatusVariant(status: string | null | undefined) {
  switch (status) {
    case 'SUCCESS':
      return 'secondary'
    case 'FAILED':
      return 'destructive'
    case 'RUNNING':
    case 'PENDING':
      return 'default'
    default:
      return 'outline'
  }
}

function renderApprovalSheetDetailAction({
  item,
  mode,
}: {
  item: ApprovalSheetListItem
  mode: ApprovalSheetLinkMode
}) {
  if (mode === 'oa' && item.businessId) {
    if (item.instanceStatus === 'DRAFT') {
      if (item.businessType === 'OA_LEAVE') {
        return (
          <Button asChild size='sm' variant='outline'>
            <Link to='/oa/leave/create' search={{ draftId: item.businessId }}>
              继续编辑
            </Link>
          </Button>
        )
      }
      if (item.businessType === 'OA_EXPENSE') {
        return (
          <Button asChild size='sm' variant='outline'>
            <Link to='/oa/expense/create' search={{ draftId: item.businessId }}>
              继续编辑
            </Link>
          </Button>
        )
      }
      if (item.businessType === 'OA_COMMON') {
        return (
          <Button asChild size='sm' variant='outline'>
            <Link to='/oa/common/create' search={{ draftId: item.businessId }}>
              继续编辑
            </Link>
          </Button>
        )
      }
    }
    if (item.businessType === 'OA_LEAVE') {
      return (
        <Button asChild size='sm' variant='outline'>
          <Link to='/oa/leave/$billId' params={{ billId: item.businessId }}>
            查看
          </Link>
        </Button>
      )
    }
    if (item.businessType === 'OA_EXPENSE') {
      return (
        <Button asChild size='sm' variant='outline'>
          <Link to='/oa/expense/$billId' params={{ billId: item.businessId }}>
            查看
          </Link>
        </Button>
      )
    }
    if (item.businessType === 'OA_COMMON') {
      return (
        <Button asChild size='sm' variant='outline'>
          <Link to='/oa/common/$billId' params={{ billId: item.businessId }}>
            查看
          </Link>
        </Button>
      )
    }
  }

  if (item.currentTaskId) {
    return (
      <Button asChild size='sm' variant='outline'>
        <Link to='/workbench/todos/$taskId' params={{ taskId: item.currentTaskId }}>
          查看
        </Link>
      </Button>
    )
  }

  return (
    <Button size='sm' variant='outline' disabled>
      无详情
    </Button>
  )
}

// 列表顶部统计只看总量、进行中和已完成。
export function summarizeApprovalSheets(records: ApprovalSheetListItem[]) {
  return {
    total: records.length,
    draft: records.filter((record) => record.instanceStatus === 'DRAFT').length,
    running: records.filter((record) => record.instanceStatus === 'RUNNING').length,
    completed: records.filter((record) => record.instanceStatus === 'COMPLETED').length,
  }
}

// 审批单列表列定义在工作台和 OA 页面复用。
export function createApprovalSheetColumns(
  mode: ApprovalSheetLinkMode
): ColumnDef<ApprovalSheetListItem>[] {
  return [
    {
      accessorKey: 'processName',
      header: '流程标题',
      cell: ({ row }) => (
        <div className='flex flex-col gap-1'>
          <span className='font-medium'>{row.original.processName}</span>
          <span className='text-xs text-muted-foreground'>
            {row.original.processKey}
          </span>
        </div>
      ),
    },
    {
      accessorKey: 'businessTitle',
      header: '业务标题',
      cell: ({ row }) => row.original.businessTitle ?? '--',
    },
    {
      accessorKey: 'billNo',
      header: '业务单号',
      cell: ({ row }) => row.original.billNo ?? '--',
    },
    {
      accessorKey: 'initiatorUserId',
      header: '发起人',
      cell: ({ row }) => (
        <div className='flex flex-col gap-1'>
          <span>{row.original.initiatorUserId}</span>
          {row.original.initiatorDepartmentName || row.original.initiatorPostName ? (
            <span className='text-xs text-muted-foreground'>
              {[row.original.initiatorDepartmentName, row.original.initiatorPostName]
                .filter(Boolean)
                .join(' / ')}
            </span>
          ) : null}
        </div>
      ),
    },
    {
      accessorKey: 'currentNodeName',
      header: '当前节点',
      cell: ({ row }) => row.original.currentNodeName ?? '--',
    },
    {
      accessorKey: 'instanceStatus',
      header: '实例状态',
      cell: ({ row }) => (
        <Badge variant={resolveApprovalSheetInstanceStatusVariant(row.original.instanceStatus)}>
          {resolveApprovalSheetInstanceStatusLabel(row.original.instanceStatus)}
        </Badge>
      ),
    },
    {
      accessorKey: 'automationStatus',
      header: '自动化状态',
      cell: ({ row }) => (
        <Badge
          variant={resolveApprovalSheetAutomationStatusVariant(
            row.original.automationStatus
          )}
        >
          {resolveApprovalSheetAutomationStatusLabel(row.original.automationStatus)}
        </Badge>
      ),
    },
    {
      id: 'updatedAt',
      accessorKey: 'updatedAt',
      header: '最近更新时间',
      cell: ({ row }) => formatApprovalSheetDateTime(row.original.updatedAt),
    },
    {
      id: 'actions',
      header: '操作',
      cell: ({ row }) => renderApprovalSheetDetailAction({ item: row.original, mode }),
    },
  ]
}
