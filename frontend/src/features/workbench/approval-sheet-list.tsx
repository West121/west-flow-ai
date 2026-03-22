import { Link } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { type ApprovalSheetListItem } from '@/lib/api/workbench'

export type ApprovalSheetLinkMode = 'workbench' | 'oa'

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

export function resolveApprovalSheetInstanceStatusLabel(status: string) {
  switch (status) {
    case 'COMPLETED':
      return '已完成'
    case 'RUNNING':
    default:
      return '进行中'
  }
}

function resolveApprovalSheetInstanceStatusVariant(status: string) {
  return status === 'COMPLETED' ? 'secondary' : 'destructive'
}

function renderApprovalSheetDetailAction({
  item,
  mode,
}: {
  item: ApprovalSheetListItem
  mode: ApprovalSheetLinkMode
}) {
  if (mode === 'oa' && item.businessId) {
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

export function summarizeApprovalSheets(records: ApprovalSheetListItem[]) {
  return {
    total: records.length,
    running: records.filter((record) => record.instanceStatus === 'RUNNING').length,
    completed: records.filter((record) => record.instanceStatus === 'COMPLETED').length,
  }
}

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
