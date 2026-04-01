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
      description="待办、已办和我发起的审批在移动端先收成高频查看与处理链。"
    >
      <View style={styles.summaryRow}>
        <MetricCard
          label="今日待办"
          value={`${summaryQuery.data?.todoTodayCount ?? 0}`}
        />
        <MetricCard
          label="已办审批"
          value={`${summaryQuery.data?.doneApprovalCount ?? 0}`}
        />
      </View>

      <View style={styles.segmentRow}>
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
          description="默认读取 10 条高频记录，详情页可继续查看回顾、动作和流程图。"
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
                  <Text style={styles.recordTitle} numberOfLines={1}>
                    {item.processName}
                  </Text>
                  <StatusBadge
                    label={resolveRecordStatus(item)}
                    tone={resolveRecordTone(item)}
                  />
                </View>
                <Text style={styles.recordSubtitle} numberOfLines={2}>
                  {'nodeName' in item
                    ? item.nodeName
                    : item.currentNodeName || item.businessTitle || '暂无节点名称'}
                </Text>
                <Text style={styles.recordHint}>
                  {'updatedAt' in item ? `更新时间 ${formatShortTime(item.updatedAt)}` : ''}
                </Text>
              </Pressable>
            ))
          )}
        </SectionCard>
      )}
    </ScreenShell>
  )
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metricCard}>
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
  summaryRow: {
    flexDirection: 'row',
    gap: 12,
  },
  metricCard: {
    flex: 1,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: '#E4DCCF',
    backgroundColor: '#FFFCF7',
    padding: 18,
  },
  metricValue: {
    color: '#171312',
    fontSize: 28,
    fontWeight: '800',
  },
  metricLabel: {
    color: '#75695E',
    marginTop: 4,
  },
  segmentRow: {
    flexDirection: 'row',
    gap: 10,
  },
  segmentButton: {
    borderRadius: 999,
    backgroundColor: '#EFE8DE',
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  segmentButtonActive: {
    backgroundColor: '#171312',
  },
  segmentLabel: {
    color: '#5E564F',
    fontWeight: '700',
  },
  segmentLabelActive: {
    color: '#FFFFFF',
  },
  recordCard: {
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#ECE3D8',
    backgroundColor: '#FFFFFF',
    padding: 14,
    gap: 8,
  },
  recordHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
  },
  recordTitle: {
    flex: 1,
    color: '#171312',
    fontSize: 16,
    fontWeight: '700',
  },
  recordSubtitle: {
    color: '#473E37',
    lineHeight: 20,
  },
  recordHint: {
    color: '#887C71',
    fontSize: 12,
  },
  emptyText: {
    color: '#7B6F63',
    lineHeight: 20,
  },
})
