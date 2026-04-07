import { useMemo, useState } from 'react'
import { useLocalSearchParams } from 'expo-router'
import { useQuery } from '@tanstack/react-query'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import {
  buildTimelineEntries,
  buildPlaybackEvents,
} from '@westflow/shared-workflow'
import { AppLoader } from '@/components/AppLoader'
import { SectionCard } from '@/components/SectionCard'
import { StatusBadge } from '@/components/StatusBadge'
import { getWorkbenchTaskDetail } from '@/lib/api/workbench'

export function ProcessPlayerScreen() {
  const params = useLocalSearchParams<{ taskId?: string }>()
  const taskId = typeof params.taskId === 'string' ? params.taskId : undefined
  const [activeIndex, setActiveIndex] = useState(0)

  const detailQuery = useQuery({
    queryKey: ['mobile', 'process-player', 'web', taskId],
    queryFn: () => getWorkbenchTaskDetail(taskId!),
    enabled: Boolean(taskId),
  })

  const detail = detailQuery.data
  const playbackEvents = useMemo(
    () =>
      detail
        ? buildPlaybackEvents(
            detail.flowNodes ?? [],
            detail.instanceEvents ?? [],
            detail.taskTrace ?? [],
            detail.instanceStatus
          )
        : [],
    [detail]
  )

  const timelineEntries = detail
    ? buildTimelineEntries(playbackEvents, detail.taskTrace ?? [])
    : []

  if (!taskId) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyText}>缺少待办编号，无法打开流程播放器。</Text>
      </View>
    )
  }

  if (detailQuery.isLoading) {
    return <AppLoader message="正在读取流程回顾…" />
  }

  if (!detail) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyText}>没有找到对应的流程详情。</Text>
      </View>
    )
  }

  const activeEntry = timelineEntries[Math.min(activeIndex, Math.max(timelineEntries.length - 1, 0))]

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <SectionCard
        title="流程回顾播放器"
        description="Web 端当前使用时间轴降级视图，原生端使用 Skia 画布播放器。"
      >
        <View style={styles.headerRow}>
          <StatusBadge label={detail.instanceStatus} tone="info" />
          <Text style={styles.metaText}>{detail.processName}</Text>
        </View>
        <View style={styles.summaryCard}>
          <Text style={styles.summaryTitle}>
            {activeEntry?.event.label ?? '暂无回顾事件'}
          </Text>
          <Text style={styles.summaryHint}>
            {activeEntry?.occurredAt ?? '时间未知'}
          </Text>
          <Text style={styles.summaryBody}>
            Web 端不加载 Skia/CanvasKit，避免浏览器初始化错误；时间轴与原生端使用同一套 shared-workflow 播放事件。
          </Text>
        </View>
      </SectionCard>

      <SectionCard title="时间轴" description="点击任一事件查看当前回顾焦点。">
        {timelineEntries.map((entry, index) => (
          <Pressable
            key={entry.id}
            style={[
              styles.timelineRow,
              index === activeIndex && styles.timelineRowActive,
            ]}
            onPress={() => setActiveIndex(index)}
          >
            <View style={styles.timelineHeader}>
              <Text style={styles.timelineTitle}>{entry.event.label}</Text>
              <StatusBadge label={entry.statusLabel} tone={mapTone(entry.dotTone)} />
            </View>
            <Text style={styles.timelineHint}>
              {entry.occurredAt ?? '时间未知'} · {entry.traceItem?.status ?? entry.event.eventType}
            </Text>
          </Pressable>
        ))}
      </SectionCard>
    </ScrollView>
  )
}

function mapTone(tone: 'info' | 'warning' | 'danger' | 'success') {
  switch (tone) {
    case 'info':
      return 'info' as const
    case 'warning':
      return 'warning' as const
    case 'danger':
      return 'danger' as const
    case 'success':
      return 'success' as const
  }
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#F6F4EF',
  },
  content: {
    gap: 16,
    padding: 20,
    paddingBottom: 40,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F6F4EF',
    padding: 24,
  },
  emptyText: {
    color: '#75695E',
    lineHeight: 20,
  },
  headerRow: {
    flexDirection: 'row',
    gap: 10,
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  metaText: {
    color: '#6E6257',
    fontSize: 12,
  },
  summaryCard: {
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#E7DDCF',
    backgroundColor: '#FFFDF8',
    padding: 14,
    gap: 8,
  },
  summaryTitle: {
    color: '#171312',
    fontSize: 16,
    fontWeight: '700',
  },
  summaryHint: {
    color: '#7A6E61',
    fontSize: 12,
  },
  summaryBody: {
    color: '#4C433D',
    lineHeight: 20,
  },
  timelineRow: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#ECE2D5',
    backgroundColor: '#FFFFFF',
    padding: 12,
    gap: 6,
  },
  timelineRowActive: {
    borderColor: '#1A74DA',
    backgroundColor: '#F5FAFF',
  },
  timelineHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
    alignItems: 'center',
  },
  timelineTitle: {
    flex: 1,
    color: '#171312',
    fontWeight: '700',
  },
  timelineHint: {
    color: '#776A5E',
    fontSize: 12,
    lineHeight: 18,
  },
})
