import { useCallback, useEffect, useState } from 'react'
import Taro, { useDidShow, useRouter } from '@tarojs/taro'
import { useMutation } from '@tanstack/react-query'
import { Text, Textarea, View } from '@tarojs/components'
import { WorkbenchTaskDetail } from '@westflow/shared-types'
import { GlassCard } from '../../components/GlassCard'
import { PageShell, cardStyle } from '../../components/PageShell'
import { StatusBadge } from '../../components/StatusBadge'
import { useAuthGuard } from '../../hooks/use-auth-guard'
import { getApiErrorMessage } from '../../lib/api/client'
import {
  claimWorkbenchTask,
  completeWorkbenchTask,
  createWorkbenchReviewTicket,
  getWorkbenchTaskActions,
  getWorkbenchTaskDetail,
  rejectWorkbenchTask,
} from '../../lib/api/workbench'
import { colors } from '../../styles/theme'

export default function ApprovalDetailPage() {
  const ready = useAuthGuard()
  const router = useRouter<{ taskId?: string }>()
  const taskId = router.params.taskId
  const [comment, setComment] = useState('')
  const [detail, setDetail] = useState<Awaited<ReturnType<typeof getWorkbenchTaskDetail>> | null>(null)
  const [actions, setActions] = useState<Awaited<ReturnType<typeof getWorkbenchTaskActions>> | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const loadApprovalDetail = useCallback(async () => {
    if (!ready || !taskId) {
      return
    }
    setIsLoading(true)
    setLoadError(null)
    try {
      const [latestDetail, latestActions] = await Promise.all([
        getWorkbenchTaskDetail(taskId),
        getWorkbenchTaskActions(taskId),
      ])
      setDetail(latestDetail)
      setActions(latestActions)
    } catch (error) {
      setLoadError(getApiErrorMessage(error))
      setDetail(null)
      setActions(null)
    } finally {
      setIsLoading(false)
    }
  }, [ready, taskId])

  useEffect(() => {
    void loadApprovalDetail()
  }, [loadApprovalDetail])

  useDidShow(() => {
    void loadApprovalDetail()
  })

  const actionMutation = useMutation({
    mutationFn: async (action: 'claim' | 'approve' | 'reject') => {
      if (!taskId) {
        throw new Error('缺少待办编号')
      }
      if (action === 'claim') return claimWorkbenchTask(taskId)
      if (action === 'approve') {
        return completeWorkbenchTask(taskId, {
          action: 'APPROVE',
          comment: comment.trim() || undefined,
        })
      }
      return rejectWorkbenchTask(taskId, {
        targetStrategy: 'INITIATOR',
        reapproveStrategy: 'CONTINUE',
        comment: comment.trim() || undefined,
      })
    },
    onSuccess: async () => {
      setComment('')
      await loadApprovalDetail()
    },
    onError: (error) => {
      void Taro.showToast({
        title: getApiErrorMessage(error),
        icon: 'none',
      })
    },
  })

  const fields = resolveDisplayFields(detail)

  return (
    <PageShell title="审批详情" description="表单、动作和流程回顾都从这里进入。">
      {!taskId ? (
        <GlassCard title="缺少待办编号">
          <Text style={{ color: colors.textSecondary }}>没有找到要打开的任务。</Text>
        </GlassCard>
      ) : null}

      {taskId && loadError ? (
        <GlassCard title="详情加载失败">
          <Text style={{ color: colors.danger, fontSize: '14px', fontWeight: 700 }}>{loadError}</Text>
          <Text style={{ color: colors.textSecondary, lineHeight: '22px', fontSize: '13px' }}>
            请确认小程序当前网络、登录态和本机调试服务均正常。
          </Text>
        </GlassCard>
      ) : null}

      {taskId && isLoading ? (
        <GlassCard title="加载中">
          <Text style={{ color: colors.textSecondary }}>正在拉取审批详情与动作…</Text>
        </GlassCard>
      ) : null}

      {detail ? (
        <>
          <GlassCard title={detail.processName} description={`${detail.nodeName} · ${detail.instanceStatus}`}>
            <View style={{ display: 'flex', flexDirection: 'row', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
              <StatusBadge label={detail.status} tone="info" />
              <Text style={{ color: colors.textMuted, fontSize: '12px' }}>实例 {detail.instanceId}</Text>
            </View>
            <Text style={{ color: colors.textSecondary, lineHeight: '22px', fontSize: '14px' }}>
              当前节点 {detail.nodeName}，可进入流程回顾页查看只读播放和时间轴。
            </Text>
            <View
              onClick={() => {
                if (taskId) {
                  void (async () => {
                    try {
                      const reviewTicket = await createWorkbenchReviewTicket(taskId)
                      await Taro.navigateTo({
                        url: `/pages/process-player/index?ticket=${encodeURIComponent(reviewTicket.ticket)}`,
                      })
                    } catch (error) {
                      await Taro.showToast({
                        title: getApiErrorMessage(error),
                        icon: 'none',
                      })
                    }
                  })()
                }
              }}
              style={{
                alignSelf: 'flex-start',
                background: colors.primary,
                borderRadius: '18px',
                padding: '12px 16px',
              }}
            >
              <Text style={{ color: '#FFFFFF', fontWeight: 700 }}>打开流程回顾</Text>
            </View>
          </GlassCard>

          <GlassCard title="申请信息" description="直接回显当前审批单表单字段。">
            {fields.map(([key, value]) => (
              <View
                key={key}
                style={{
                  ...cardStyle({
                    borderRadius: '20px',
                    padding: '14px 16px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px',
                  }),
                }}
              >
                <Text style={{ fontSize: '12px', color: colors.textMuted, fontWeight: 700 }}>
                  {key}
                </Text>
                <Text
                  style={{
                    fontSize: '18px',
                    lineHeight: '26px',
                    fontWeight: 700,
                    color: colors.text,
                    wordBreak: 'break-all',
                  }}
                >
                  {value}
                </Text>
              </View>
            ))}
          </GlassCard>

          <GlassCard title="处理意见" description="移动端先收常用动作：认领、同意、驳回。">
            <Textarea
              value={comment}
              onInput={(event) => setComment(event.detail.value)}
              placeholder="请输入审批意见"
              style={{
                width: '100%',
                minHeight: '120px',
                background: colors.cardStrong,
                borderRadius: '20px',
                border: `1px solid ${colors.cardBorder}`,
                padding: '14px',
                boxSizing: 'border-box',
              }}
            />
            <View style={{ display: 'flex', flexDirection: 'row', flexWrap: 'wrap', gap: '10px' }}>
              {actions?.canClaim ? <ActionButton label="认领" onClick={() => actionMutation.mutate('claim')} /> : null}
              {actions?.canApprove ? <ActionButton label="同意" primary onClick={() => actionMutation.mutate('approve')} /> : null}
              {actions?.canReject ? <ActionButton label="驳回发起人" warning onClick={() => actionMutation.mutate('reject')} /> : null}
            </View>
          </GlassCard>
        </>
      ) : null}
    </PageShell>
  )
}

const FIELD_LABELS: Record<string, string> = {
  leaveType: '请假类型',
  days: '请假天数',
  leaveDays: '请假天数',
  reason: '请假原因',
  urgent: '是否紧急',
  managerUserId: '直属负责人',
}

const LEAVE_TYPE_LABELS: Record<string, string> = {
  PERSONAL: '事假',
  ANNUAL: '年假',
  SICK: '病假',
  MARRIAGE: '婚假',
  MATERNITY: '产假',
  PATERNITY: '陪产假',
  FUNERAL: '丧假',
  OTHER: '其他',
}

function resolveDisplayFields(detail: WorkbenchTaskDetail | null) {
  if (!detail) {
    return []
  }

  const formData = detail.formData ?? {}
  const userDisplayNames = detail.userDisplayNames ?? {}
  const hasDays = formData.days !== null && formData.days !== undefined && `${formData.days}` !== ''

  return Object.entries(formData)
    .filter(([key, value]) => {
      if (value === null || value === undefined || value === '') {
        return false
      }
      if (key === 'leaveDays' && hasDays) {
        return false
      }
      return true
    })
    .map(([key, value]) => [
      FIELD_LABELS[key] ?? key,
      formatFieldValue(key, value, userDisplayNames),
    ] as const)
}

function formatFieldValue(
  key: string,
  value: unknown,
  userDisplayNames: Record<string, string>
) {
  if (key === 'leaveType') {
    const normalized = `${value}`.trim().toUpperCase()
    return LEAVE_TYPE_LABELS[normalized] ?? normalized
  }

  if (key === 'urgent') {
    return value === true || `${value}` === 'true' ? '是' : '否'
  }

  if (key === 'managerUserId') {
    const userId = `${value}`.trim()
    return userDisplayNames[userId] ?? userId
  }

  if (typeof value === 'string') {
    return value
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return `${value}`
  }

  return JSON.stringify(value)
}

function ActionButton({
  label,
  onClick,
  primary = false,
  warning = false,
}: {
  label: string
  onClick: () => void
  primary?: boolean
  warning?: boolean
}) {
  return (
    <View
      onClick={onClick}
      style={{
        padding: '12px 16px',
        borderRadius: '18px',
        background: primary ? colors.primary : warning ? 'rgba(176,138,98,0.16)' : colors.cardStrong,
        border: primary ? 'none' : `1px solid ${colors.cardBorder}`,
      }}
    >
      <Text style={{ color: primary ? '#FFFFFF' : warning ? colors.warning : colors.text, fontWeight: 700 }}>
        {label}
      </Text>
    </View>
  )
}
