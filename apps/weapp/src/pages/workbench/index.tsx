import { useCallback, useEffect, useMemo, useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { ApprovalSheetListItem, WorkbenchTaskListItem } from '@westflow/shared-types'
import { Text, View } from '@tarojs/components'
import { GlassCard } from '../../components/GlassCard'
import { PageShell, cardStyle } from '../../components/PageShell'
import { StatusBadge } from '../../components/StatusBadge'
import { useAuthGuard } from '../../hooks/use-auth-guard'
import {
  getWorkbenchDashboardSummary,
  listApprovalSheets,
  listWorkbenchTasks,
  type ApprovalSheetListView,
} from '../../lib/api/workbench'
import { getApiErrorMessage } from '../../lib/api/client'
import { colors } from '../../styles/theme'

type WorkbenchView = 'TODO' | 'DONE' | 'INITIATED'

export default function WorkbenchPage() {
  const ready = useAuthGuard()
  const [view, setView] = useState<WorkbenchView>('TODO')
  const [summary, setSummary] = useState<Awaited<ReturnType<typeof getWorkbenchDashboardSummary>> | null>(null)
  const [records, setRecords] = useState<Array<WorkbenchTaskListItem | ApprovalSheetListItem>>([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const loadWorkbench = useCallback(async () => {
    if (!ready) {
      console.log('[weapp/workbench] loadWorkbench:skip not ready')
      return
    }

    console.log('[weapp/workbench] loadWorkbench:start', { view })
    setLoading(true)
    setErrorMessage(null)
    try {
      const latestSummary = await getWorkbenchDashboardSummary()
      setSummary(latestSummary)
      console.log('[weapp/workbench] loadWorkbench:summary', latestSummary)

      if (view === 'TODO') {
        const todoResult = await listWorkbenchTasks()
        setRecords(todoResult.records ?? [])
        console.log('[weapp/workbench] loadWorkbench:todoRecords', {
          count: todoResult.records?.length ?? 0,
        })
      } else {
        const sheetResult = await listApprovalSheets({ view: view as ApprovalSheetListView })
        setRecords(sheetResult.records ?? [])
        console.log('[weapp/workbench] loadWorkbench:sheetRecords', {
          view,
          count: sheetResult.records?.length ?? 0,
        })
      }
    } catch (error) {
      const message = getApiErrorMessage(error)
      console.error('[weapp/workbench] loadWorkbench:error', error)
      setErrorMessage(message)
      setRecords([])
      setSummary(null)
    } finally {
      setLoading(false)
    }
  }, [ready, view])

  useDidShow(() => {
    console.log('[weapp/workbench] didShow', {
      ready,
      view,
    })
    void loadWorkbench()
  })

  useEffect(() => {
    void loadWorkbench()
  }, [loadWorkbench])

  useEffect(() => {
    console.log('[weapp/workbench] render-state', {
      ready,
      view,
      loading,
      errorMessage,
      summaryData: summary
        ? {
            todoTodayCount: summary.todoTodayCount,
            doneApprovalCount: summary.doneApprovalCount,
          }
        : null,
      recordCount: records.length,
    })
  }, [
    errorMessage,
    loading,
    records.length,
    ready,
    summary,
    view,
  ])
  const summaryValue = summary ? `${summary.todoTodayCount ?? 0}` : '—'
  const doneValue = summary ? `${summary.doneApprovalCount ?? 0}` : '—'

  return (
    <PageShell
      title="工作台"
      description="把高频审批、已办与我发起的链路收在一页里。"
    >
      <View
        style={cardStyle({
          display: 'flex',
          flexDirection: 'column',
          gap: '18px',
          padding: '16px',
          borderRadius: '34px',
          background: 'rgba(255,255,255,0.78)',
        })}
      >
        <View
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '10px',
          }}
        >
          <Text style={{ fontSize: '13px', color: colors.textSecondary, fontWeight: 700 }}>今日焦点</Text>
          <View
            style={{
              padding: '8px 12px',
              borderRadius: '999px',
              background: 'rgba(255,255,255,0.76)',
              border: `1px solid ${colors.cardBorder}`,
            }}
          >
            <Text style={{ color: colors.textMuted, fontSize: '11px', fontWeight: 700 }}>优先处理待办</Text>
          </View>
        </View>
        <View style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <Text style={{ fontSize: '32px', lineHeight: 1.16, fontWeight: 800, color: colors.text }}>
            先把审批链收在一页里
          </Text>
          <Text style={{ fontSize: '14px', lineHeight: '22px', color: colors.textSecondary }}>
            先处理最重要的，再决定是否进入表单、动作与流程回顾。
          </Text>
        </View>
        <View style={{ display: 'flex', flexDirection: 'row', gap: '12px' }}>
          <MetricCard
            label="今日待办"
            value={summaryValue}
            hint="需要你处理"
          />
          <MetricCard
            label="已办审批"
            value={doneValue}
            hint="已经处理"
          />
        </View>
      </View>

      <View
        style={{
          ...cardStyle({
            padding: '7px',
            display: 'flex',
            flexDirection: 'row',
            gap: '6px',
            borderRadius: '999px',
            background: 'rgba(255,255,255,0.68)',
          }),
        }}
      >
        {(['TODO', 'DONE', 'INITIATED'] as WorkbenchView[]).map((item) => {
          const active = item === view
          return (
            <View
              key={item}
              onClick={() => setView(item)}
              style={{
                flex: 1,
                borderRadius: '999px',
                padding: '14px 10px',
                textAlign: 'center',
                background: active ? colors.primary : 'rgba(255,255,255,0.22)',
                boxShadow: active ? '0 14px 30px rgba(17,24,39,0.18)' : 'none',
                transition: 'all 220ms ease',
              }}
            >
              <Text style={{ color: active ? '#FFFFFF' : colors.textSecondary, fontSize: '14px', fontWeight: 700 }}>
                {resolveViewLabel(item)}
              </Text>
            </View>
          )
        })}
      </View>

      <GlassCard
        title={resolveViewLabel(view)}
        description="先展示 10 条高频记录，进入详情后可继续查看表单、动作与流程回顾。"
        style={{ borderRadius: '34px' }}
      >
        {errorMessage ? (
          <View
            style={{
              ...cardStyle({
                borderRadius: '22px',
                padding: '14px',
                background: 'rgba(255,255,255,0.88)',
                border: `1px solid rgba(179,106,114,0.18)`,
              }),
            }}
          >
            <Text style={{ color: colors.danger, fontSize: '14px', fontWeight: 700 }}>工作台数据拉取失败</Text>
            <Text style={{ color: colors.textSecondary, fontSize: '13px', lineHeight: '20px', marginTop: '8px' }}>
              {errorMessage}
            </Text>
            <Text style={{ color: colors.textMuted, fontSize: '12px', lineHeight: '18px', marginTop: '8px' }}>
              请确认微信开发者工具已关闭域名校验，并且可访问当前局域网地址的后端服务。
            </Text>
          </View>
        ) : loading ? (
          <Text style={{ color: colors.textSecondary, fontSize: '14px' }}>正在拉取最新记录…</Text>
        ) : records.length === 0 ? (
          <Text style={{ color: colors.textSecondary, fontSize: '14px' }}>当前没有记录。</Text>
        ) : (
          records.map((item) => (
            <View
              key={resolveRecordKey(item)}
              onClick={() => {
                const taskId = 'taskId' in item ? item.taskId : item.currentTaskId
                if (taskId) {
                  void Taro.navigateTo({ url: `/pages/approval/detail?taskId=${encodeURIComponent(taskId)}` })
                }
              }}
              style={{
                ...cardStyle({
                  borderRadius: '24px',
                  padding: '16px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '10px',
                  background: 'rgba(255,255,255,0.84)',
                }),
              }}
            >
              <View style={{ display: 'flex', flexDirection: 'row', justifyContent: 'space-between', gap: '12px' }}>
                <View style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
                  <Text style={{ color: colors.text, fontSize: '17px', fontWeight: 700 }}>
                    {item.processName}
                  </Text>
                  <Text style={{ color: colors.textSecondary, fontSize: '14px', lineHeight: '20px' }}>
                    {'nodeName' in item
                      ? item.nodeName
                      : item.currentNodeName || item.businessTitle || '暂无节点名称'}
                  </Text>
                </View>
                <StatusBadge label={resolveRecordStatus(item)} tone={resolveRecordTone(item)} />
              </View>
              <Text style={{ color: colors.textMuted, fontSize: '12px' }}>
                {'updatedAt' in item ? `更新于 ${formatShortTime(item.updatedAt)}` : ''}
              </Text>
            </View>
          ))
        )}
      </GlassCard>
    </PageShell>
  )
}

function MetricCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <View
      style={{
        ...cardStyle({
          flex: 1,
          borderRadius: '28px',
          padding: '16px',
          background: 'rgba(255,255,255,0.9)',
        }),
      }}
    >
      <Text style={{ color: colors.textMuted, fontSize: '13px', fontWeight: 700 }}>{label}</Text>
      <View
        style={{
          marginTop: '14px',
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'baseline',
          gap: '8px',
          flexWrap: 'wrap',
        }}
      >
        <Text style={{ color: colors.text, fontSize: '38px', lineHeight: 1, fontWeight: 800 }}>{value}</Text>
        <Text style={{ color: colors.textSecondary, fontSize: '15px', fontWeight: 600 }}>{hint}</Text>
      </View>
    </View>
  )
}

function resolveViewLabel(view: WorkbenchView) {
  switch (view) {
    case 'TODO':
      return '待办'
    case 'DONE':
      return '已办'
    case 'INITIATED':
      return '我发起的'
  }
}

function resolveRecordKey(record: WorkbenchTaskListItem | ApprovalSheetListItem) {
  return 'taskId' in record ? record.taskId : `${record.instanceId}:${record.currentTaskId ?? 'history'}`
}

function resolveRecordStatus(record: WorkbenchTaskListItem | ApprovalSheetListItem) {
  return 'status' in record ? record.status : record.instanceStatus
}

function resolveRecordTone(record: WorkbenchTaskListItem | ApprovalSheetListItem) {
  const status = resolveRecordStatus(record)
  if (status.includes('PENDING')) return 'info'
  if (status.includes('REJECT') || status.includes('RETURN')) return 'warning'
  if (status.includes('REVOKE') || status.includes('TERMINATE')) return 'danger'
  return 'success'
}

function formatShortTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
