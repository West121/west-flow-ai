import { useMemo, useState } from 'react'
import { useRouter } from 'expo-router'
import { useQuery } from '@tanstack/react-query'
import type { ApprovalSheetListItem, WorkbenchTaskListItem } from '@westflow/shared-types'
import {
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native'
import { AppLoader } from '@/components/AppLoader'
import { ScreenShell } from '@/components/ScreenShell'
import { SectionCard } from '@/components/SectionCard'
import { StatusBadge } from '@/components/StatusBadge'
import {
  getWorkbenchDashboardSummary,
  listApprovalSheets,
  listWorkbenchTasks,
  type ListQuerySearch,
  type ApprovalSheetListView,
} from '@/lib/api/workbench'
import { useAuthStore } from '@/stores/auth-store'

type WorkbenchView = 'TODO' | 'DONE' | 'INITIATED'

const baseQuery: ListQuerySearch = {
  page: 1,
  pageSize: 10,
  keyword: '',
  filters: [],
  sorts: [],
  groups: [],
}

export function WorkbenchScreen() {
  const router = useRouter()
  const currentUser = useAuthStore((state) => state.currentUser)
  const accessToken = useAuthStore((state) => state.accessToken)
  const [view, setView] = useState<WorkbenchView>('TODO')

  const enabled = Boolean(accessToken)

  const summaryQuery = useQuery({
    queryKey: ['mobile', 'workbench', 'summary'],
    queryFn: getWorkbenchDashboardSummary,
    enabled,
  })

  const todoQuery = useQuery({
    queryKey: ['mobile', 'workbench', 'tasks', baseQuery],
    queryFn: () => listWorkbenchTasks({ ...baseQuery }),
    enabled: enabled && view === 'TODO',
  })

  const sheetsQuery = useQuery({
    queryKey: ['mobile', 'workbench', 'sheets', view],
    queryFn: () =>
      listApprovalSheets({
        ...baseQuery,
        view: view as ApprovalSheetListView,
      }),
    enabled: enabled && view !== 'TODO',
  })

  const records = useMemo<Array<WorkbenchTaskListItem | ApprovalSheetListItem>>(() => {
    if (view === 'TODO') {
      return todoQuery.data?.records ?? []
    }
    return sheetsQuery.data?.records ?? []
  }, [sheetsQuery.data?.records, todoQuery.data?.records, view])

  if (!currentUser) {
    return (
      <ScreenShell title="工作台" description="请先登录，再查看待办、已办和我发起的审批。">
        <SectionCard title="尚未登录" description="移动端和 Web 端共用同一套账号、岗位和权限。">
          <Text style={styles.emptyText}>先登录后再进入审批主链。</Text>
        </SectionCard>
      </ScreenShell>
    )
  }

  const isLoading =
    summaryQuery.isLoading ||
    (view === 'TODO' ? todoQuery.isLoading : sheetsQuery.isLoading)

  return (
    <ScreenShell
      title="工作台"
      description="先看最需要你处理的，再决定是否进入详细审批链。"
    >
      <View style={styles.heroCard}>
        <View style={styles.heroGlow} />
        <View style={styles.heroRibbon}>
          <Text style={styles.heroRibbonText}>Today Focus</Text>
        </View>
        <View style={styles.heroHeader}>
          <View style={styles.heroMain}>
            <Text style={styles.heroTitle}>先处理待办</Text>
            <Text style={styles.heroBody}>今天最值得先看的，是还在等你动作的审批单。</Text>
          </View>
          <View style={styles.heroCountBadge}>
            <Text style={styles.heroCountLabel}>待办</Text>
            <Text style={styles.heroCountValue}>{summaryQuery.data?.todoTodayCount ?? 0}</Text>
          </View>
        </View>
        <View style={styles.summaryRow}>
          <MetricCard
            label="已办"
            value={`${summaryQuery.data?.doneApprovalCount ?? 0}`}
            hint="已经处理"
          />
          <MetricCard
            label="处理率"
            value={summaryQuery.data?.todoTodayCount ? `${Math.min(99, Math.round(((summaryQuery.data?.doneApprovalCount ?? 0) / Math.max((summaryQuery.data?.doneApprovalCount ?? 0) + (summaryQuery.data?.todoTodayCount ?? 0), 1)) * 100))}%` : `${Math.min(99, Math.round(((summaryQuery.data?.doneApprovalCount ?? 0) / Math.max((summaryQuery.data?.doneApprovalCount ?? 0) + 1, 1)) * 100))}%`}
            hint="今日节奏"
          />
        </View>
      </View>

      <View style={styles.segmentWrap}>
        {(['TODO', 'DONE', 'INITIATED'] as WorkbenchView[]).map((item) => (
          <Pressable
            key={item}
            style={[
              styles.segmentButton,
              item === view && styles.segmentButtonActive,
            ]}
            onPress={() => setView(item)}
          >
            <Text
              style={[
                styles.segmentLabel,
                item === view && styles.segmentLabelActive,
              ]}
            >
              {resolveViewLabel(item)}
            </Text>
          </Pressable>
        ))}
      </View>

      {isLoading ? (
        <SectionCard compact>
          <AppLoader message="正在读取工作台…" />
        </SectionCard>
      ) : (
        <SectionCard
          title={resolveViewLabel(view)}
          description="只保留 10 条高频记录，轻点一条进入完整审批详情。"
        >
          {records.length === 0 ? (
            <Text style={styles.emptyText}>当前没有记录。</Text>
          ) : (
            records.map((item) => (
              <Pressable
                key={resolveRecordKey(item)}
                onPress={() => {
                  const taskId =
                    'taskId' in item
                      ? item.taskId
                      : (item.currentTaskId ?? undefined)
                  if (taskId) {
                    router.push({
                      pathname: '/approval',
                      params: { taskId },
                    })
                  }
                }}
                style={styles.recordCard}
              >
                <View style={styles.recordHeader}>
                  <View style={styles.recordIcon}>
                    <Text style={styles.recordIconText}>WF</Text>
                  </View>
                  <View style={styles.recordHeaderText}>
                    <Text style={styles.recordTitle} numberOfLines={1}>
                      {item.processName}
                    </Text>
                    <Text style={styles.recordSubtitle} numberOfLines={2}>
                      {'nodeName' in item
                        ? item.nodeName
                        : item.currentNodeName || item.businessTitle || '暂无节点名称'}
                    </Text>
                  </View>
                  <StatusBadge
                    label={resolveRecordStatus(item)}
                    tone={resolveRecordTone(item)}
                  />
                </View>
                <View style={styles.recordFooter}>
                  <Text style={styles.recordHint}>
                    {'updatedAt' in item ? `更新于 ${formatShortTime(item.updatedAt)}` : ''}
                  </Text>
                  <Text style={styles.recordChevron}>›</Text>
                </View>
              </Pressable>
            ))
          )}
        </SectionCard>
      )}
    </ScreenShell>
  )
}

function MetricCard({
  label,
  value,
  hint,
}: {
  label: string
  value: string
  hint: string
}) {
  return (
    <View style={styles.metricCard}>
      <Text style={styles.metricHint}>{hint}</Text>
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
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
  if (status.includes('PENDING')) {
    return 'info' as const
  }
  if (status.includes('REJECT') || status.includes('RETURN')) {
    return 'warning' as const
  }
  if (status.includes('REVOKE') || status.includes('TERMINATE')) {
    return 'danger' as const
  }
  return 'success' as const
}

function formatShortTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

const styles = StyleSheet.create({
  heroCard: {
    overflow: 'hidden',
    borderRadius: 36,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.82)',
    backgroundColor: 'rgba(255,255,255,0.5)',
    padding: 18,
    gap: 16,
    shadowColor: '#97A1CC',
    shadowOpacity: 0.16,
    shadowRadius: 30,
    shadowOffset: { width: 0, height: 18 },
  },
  heroGlow: {
    position: 'absolute',
    top: -42,
    right: -26,
    width: 210,
    height: 210,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.76)',
  },
  heroRibbon: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    backgroundColor: 'rgba(24,28,43,0.92)',
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  heroRibbonText: {
    color: '#F7F8FC',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.9,
    textTransform: 'uppercase',
  },
  heroHeader: {
    flexDirection: 'row',
    alignItems: 'stretch',
    justifyContent: 'space-between',
    gap: 12,
  },
  heroMain: {
    flex: 1,
  },
  heroTitle: {
    color: '#181C2B',
    fontSize: 30,
    fontWeight: '800',
    letterSpacing: -0.9,
    marginTop: 2,
    maxWidth: 180,
  },
  heroCountBadge: {
    minWidth: 96,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.84)',
    backgroundColor: 'rgba(255,255,255,0.68)',
    paddingHorizontal: 16,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroCountLabel: {
    color: '#7B7590',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  heroCountValue: {
    color: '#1A2030',
    fontSize: 32,
    fontWeight: '800',
    letterSpacing: -1,
    marginTop: 4,
  },
  heroBody: {
    color: '#615D73',
    lineHeight: 22,
    maxWidth: 220,
    marginTop: 10,
  },
  summaryRow: {
    flexDirection: 'row',
    gap: 10,
  },
  metricCard: {
    flex: 1,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.7)',
    backgroundColor: 'rgba(255,255,255,0.48)',
    padding: 14,
    shadowColor: '#7784C4',
    shadowOpacity: 0.05,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
  },
  metricHint: {
    color: '#807B8E',
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
  metricValue: {
    color: '#171B29',
    fontSize: 30,
    fontWeight: '800',
    letterSpacing: -1,
    marginTop: 10,
  },
  metricLabel: {
    color: '#5D5870',
    marginTop: 2,
    fontSize: 13,
    fontWeight: '600',
  },
  segmentWrap: {
    flexDirection: 'row',
    gap: 6,
    alignSelf: 'stretch',
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.32)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.64)',
    padding: 5,
  },
  segmentButton: {
    borderRadius: 18,
    flex: 1,
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 13,
  },
  segmentButtonActive: {
    backgroundColor: 'rgba(24,28,43,0.96)',
    shadowColor: '#1A2030',
    shadowOpacity: 0.14,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
  },
  segmentLabel: {
    color: '#666277',
    fontWeight: '700',
  },
  segmentLabelActive: {
    color: '#FFFFFF',
  },
  recordCard: {
    borderRadius: 22,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.72)',
    backgroundColor: 'rgba(255,255,255,0.76)',
    padding: 14,
    gap: 12,
  },
  recordHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
  },
  recordIcon: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(216,229,255,0.9)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
  },
  recordIconText: {
    color: '#2B63BD',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  recordHeaderText: {
    flex: 1,
    gap: 6,
  },
  recordTitle: {
    color: '#171B29',
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  recordSubtitle: {
    color: '#67627A',
    lineHeight: 19,
  },
  recordFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  recordHint: {
    color: '#8C889D',
    fontSize: 12,
    fontWeight: '500',
  },
  recordChevron: {
    color: '#A29DB2',
    fontSize: 22,
    lineHeight: 22,
  },
  emptyText: {
    color: '#767286',
    lineHeight: 20,
  },
})
