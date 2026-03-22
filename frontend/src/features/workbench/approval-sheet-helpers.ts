import { type WorkbenchTaskDetail, type WorkbenchTaskTraceItem } from '@/lib/api/workbench'

export function formatApprovalSheetDateTime(
  value: string | null | undefined
) {
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

export function formatApprovalSheetBoolean(value: boolean | null | undefined) {
  return value ? '是' : '否'
}

export function formatApprovalSheetText(value: string | null | undefined) {
  return value?.trim() || '--'
}

export function formatApprovalSheetDuration(
  value: number | null | undefined
) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }

  if (value < 60) {
    return `${value} 秒`
  }

  if (value < 3600) {
    return `${Math.floor(value / 60)} 分钟`
  }

  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  return minutes > 0 ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`
}

export function formatApprovalSheetJsonValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '--'
  }

  if (typeof value === 'string') {
    return value
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  if (Array.isArray(value)) {
    return value.map((item) => formatApprovalSheetJsonValue(item)).join('、')
  }

  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>)
      .map(([key, item]) => `${key}: ${formatApprovalSheetJsonValue(item)}`)
      .join('；')
  }

  return String(value)
}

const businessTypeTitleMap: Record<string, string> = {
  OA_LEAVE: '请假申请',
  OA_EXPENSE: '报销申请',
  OA_COMMON: '通用申请',
}

const businessDataLabelMap: Record<string, string> = {
  billId: '业务 ID',
  billNo: '业务单号',
  sceneCode: '场景编码',
  status: '业务状态',
  creatorUserId: '发起人',
  processInstanceId: '实例编号',
  days: '请假天数',
  reason: '申请原因',
  amount: '报销金额',
  title: '申请标题',
  content: '申请内容',
}

export function resolveApprovalSheetBusinessTitle(detail: WorkbenchTaskDetail) {
  const businessTitle = detail.businessData?.title
  if (typeof businessTitle === 'string' && businessTitle.trim()) {
    return businessTitle.trim()
  }

  return businessTypeTitleMap[detail.businessType ?? ''] ?? detail.processName
}

export function resolveApprovalSheetBusinessRows(
  detail: WorkbenchTaskDetail
): Array<{ key: string; label: string; value: string }> {
  const source = detail.businessData ?? detail.formData

  return Object.entries(source)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({
      key,
      label: businessDataLabelMap[key] ?? key,
      value: formatApprovalSheetJsonValue(value),
    }))
}

export function resolveApprovalSheetResultLabel(item: WorkbenchTaskTraceItem) {
  if (item.action === 'REJECT_ROUTE') {
    return '驳回到上一步人工节点'
  }
  if (item.action === 'JUMP') {
    return '跳转'
  }
  if (item.action === 'TAKE_BACK') {
    return '拿回'
  }
  if (item.action === 'WAKE_UP') {
    return '唤醒'
  }
  if (item.action === 'APPROVE') {
    return '通过'
  }
  if (item.action === 'REJECT') {
    return '拒绝'
  }
  if (item.action === 'READ') {
    return '已阅'
  }
  if (item.action === 'RETURN') {
    return '退回'
  }
  if (item.action === 'TRANSFER') {
    return '转办'
  }
  if (item.action === 'CLAIM') {
    return '已认领'
  }
  if (item.action === 'ADD_SIGN') {
    return '加签'
  }
  if (item.action === 'REMOVE_SIGN') {
    return '减签'
  }
  if (item.action === 'WITHDRAW' || item.action === 'REVOKE') {
    return '撤销'
  }
  if (item.action === 'URGE') {
    return '催办'
  }

  switch (item.status) {
    case 'COMPLETED':
      return '已完成'
    case 'TRANSFERRED':
      return '已转办'
    case 'RETURNED':
      return '已退回'
    case 'PENDING_CLAIM':
      return '待认领'
    case 'PENDING':
    default:
      return '待处理'
  }
}
