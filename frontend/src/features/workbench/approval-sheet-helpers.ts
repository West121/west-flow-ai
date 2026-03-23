import { type WorkbenchTaskDetail, type WorkbenchTaskTraceItem } from '@/lib/api/workbench'

// 统一把审批单详情里的时间、文本和轨迹文案转成页面可直接展示的格式。
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

// 数值型时长统一转成中文可读格式，详情页和轨迹卡片复用。
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

// 业务详情里经常会混合字符串、数组和对象，这里统一拍平成可读文本。
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

// 业务类型标题和字段标签保持一份映射，避免页面文案散落各处。
const businessTypeTitleMap: Record<string, string> = {
  OA_LEAVE: '请假申请',
  OA_EXPENSE: '报销申请',
  OA_COMMON: '通用申请',
  PLM_ECR: 'ECR 变更申请',
  PLM_ECO: 'ECO 变更执行',
  PLM_MATERIAL: '物料主数据变更',
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
  changeTitle: '变更标题',
  changeReason: '变更原因',
  affectedProductCode: '影响产品编码',
  priorityLevel: '优先级',
  executionTitle: '执行标题',
  executionPlan: '执行计划',
  effectiveDate: '生效日期',
  materialCode: '物料编码',
  materialName: '物料名称',
  changeType: '变更类型',
}

// 详情页的业务标题优先取单据自己的标题，取不到再回退到流程名称。
export function resolveApprovalSheetBusinessTitle(detail: WorkbenchTaskDetail) {
  const businessTitle = detail.businessData?.title
  if (typeof businessTitle === 'string' && businessTitle.trim()) {
    return businessTitle.trim()
  }

  return businessTypeTitleMap[detail.businessType ?? ''] ?? detail.processName
}

// 业务字段在详情页按统一标签展示，保证流程中心和 OA 详情口径一致。
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

// 轨迹结果文案统一从这里出，详情页、时间线和流程图都可以复用。
export function resolveApprovalSheetResultLabel(item: WorkbenchTaskTraceItem) {
  if (item.action === 'REJECT_ROUTE') {
    return '驳回到上一步人工节点'
  }
  if (item.action === 'DELEGATE') {
    return '委派'
  }
  if (item.action === 'PROXY') {
    return '代理代办'
  }
  if (item.action === 'HANDOVER') {
    return '离职转办'
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
  if (item.action === 'APPEND') {
    return '追加'
  }
  if (item.action === 'DYNAMIC_BUILD') {
    return '动态构建'
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
    case 'AUTO_FINISHED':
      return '自动结束'
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

// 会签模式文案统一集中在这里，避免详情页和设计器说明口径漂移。
export function resolveCountersignModeLabel(value: string | null | undefined) {
  switch (value) {
    case 'SEQUENTIAL':
      return '顺序会签'
    case 'PARALLEL':
      return '并行会签'
    case 'OR_SIGN':
      return '或签'
    case 'VOTE':
      return '票签'
    case 'SINGLE':
      return '单人审批'
    default:
      return value?.trim() || '--'
  }
}

// 会签成员状态文案单独映射，便于详情页直接展示每个人当前进度。
export function resolveCountersignMemberStatusLabel(
  value: string | null | undefined
) {
  switch (value) {
    case 'COMPLETED':
      return '已完成'
    case 'ACTIVE':
      return '处理中'
    case 'WAITING':
      return '等待中'
    case 'AUTO_FINISHED':
      return '自动结束'
    default:
      return value?.trim() || '--'
  }
}

// 办理方式标签用于展示代理、委派、离职转办等运行态语义。
export function resolveApprovalSheetActingModeLabel(
  value: string | null | undefined
) {
  switch (value) {
    case 'PROXY':
      return '代理代办'
    case 'DELEGATE':
      return '委派任务'
    case 'HANDOVER':
      return '离职转办'
    case 'DIRECT':
    case null:
    case undefined:
    default:
      return '直接办理'
  }
}
