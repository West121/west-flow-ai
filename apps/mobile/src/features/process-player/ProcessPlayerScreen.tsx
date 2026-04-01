import { useEffect, useMemo, useReducer, useState } from 'react'
import { useLocalSearchParams } from 'expo-router'
import { useQuery } from '@tanstack/react-query'
import {
  Canvas,
  Line,
  Rect,
} from '@shopify/react-native-skia'
import {
  LayoutChangeEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native'
import { Gesture, GestureDetector } from 'react-native-gesture-handler'
import Animated, {
  useAnimatedStyle,
  useSharedValue,
} from 'react-native-reanimated'
import {
  buildEdgePlaybackStates,
  buildNodePlaybackStates,
  buildPlaybackEvents,
  buildTimelineEntries,
  buildTraversalState,
  createPlaybackState,
  resolveActivePlaybackEvent,
  resolvePlaybackFocusNodeIds,
  transitionPlaybackState,
} from '@westflow/shared-workflow'
import { AppLoader } from '@/components/AppLoader'
import { SectionCard } from '@/components/SectionCard'
import { StatusBadge } from '@/components/StatusBadge'
import { getWorkbenchTaskDetail } from '@/lib/api/workbench'

const NODE_WIDTH = 156
const NODE_HEIGHT = 64

export function ProcessPlayerScreen() {
  const params = useLocalSearchParams<{ taskId?: string }>()
  const taskId = typeof params.taskId === 'string' ? params.taskId : undefined
  const [zoom, setZoom] = useState(1)
  const [viewportHeight, setViewportHeight] = useState(420)

  const detailQuery = useQuery({
    queryKey: ['mobile', 'process-player', taskId],
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
  const [playbackState, dispatch] = useReducer(
    transitionPlaybackState,
    createPlaybackState(playbackEvents.length)
  )

  useEffect(() => {
    dispatch({ type: 'sync', totalEvents: playbackEvents.length })
  }, [playbackEvents.length])

  useEffect(() => {
    if (playbackState.mode !== 'playing') {
      return
    }
    const timer = setInterval(() => {
      dispatch({ type: 'tick' })
    }, 900)
    return () => clearInterval(timer)
  }, [playbackState.mode])

  const activeEvent = detail
    ? resolveActivePlaybackEvent(
        playbackEvents,
        detail.taskTrace ?? [],
        playbackState.activeIndex,
        playbackState.mode,
        detail.instanceStatus
      )
    : null

  const focusNodeIds = detail
    ? resolvePlaybackFocusNodeIds(
        playbackEvents,
        detail.taskTrace ?? [],
        playbackState.activeIndex,
        playbackState.mode,
        detail.instanceStatus
      )
    : []

  const traversalState = detail
    ? buildTraversalState(
        detail.flowNodes ?? [],
        detail.flowEdges ?? [],
        focusNodeIds,
        playbackState.activeIndex
      )
    : { visitedNodeIds: [], visitedEdgeIds: [], activeEdgeIds: [] }

  const nodeStates = detail
    ? buildNodePlaybackStates(detail.flowNodes ?? [], {
        activeNodeId: activeEvent?.nodeId ?? null,
        completedNodeIds: playbackEvents
          .slice(0, Math.max(playbackState.activeIndex, 0) + 1)
          .map((event) => event.nodeId),
        visitedNodeIds: traversalState.visitedNodeIds,
        mode: playbackState.mode,
        instanceStatus: detail.instanceStatus,
      })
    : []

  const edgeStates = detail
    ? buildEdgePlaybackStates(detail.flowEdges ?? [], {
        visitedEdgeIds: traversalState.visitedEdgeIds,
        activeEdgeIds: traversalState.activeEdgeIds,
        mode: playbackState.mode,
      })
    : []

  const nodeStateMap = useMemo(
    () => new Map(nodeStates.map((node) => [node.id, node])),
    [nodeStates]
  )

  const timelineEntries = detail
    ? buildTimelineEntries(playbackEvents, detail.taskTrace ?? [])
    : []

  const canvasSize = useMemo(() => {
    const nodes = detail?.flowNodes ?? []
    const width =
      Math.max(
        720,
        ...nodes.map((node) => (node.position?.x ?? 0) + (node.ui?.width ?? NODE_WIDTH) + 120)
      )
    const height =
      Math.max(
        520,
        ...nodes.map((node) => (node.position?.y ?? 0) + (node.ui?.height ?? NODE_HEIGHT) + 160)
      )
    return { width, height }
  }, [detail?.flowNodes])

  const scale = useSharedValue(1)
  const translateX = useSharedValue(0)
  const translateY = useSharedValue(0)
  const panStartX = useSharedValue(0)
  const panStartY = useSharedValue(0)
  const pinchStartScale = useSharedValue(1)

  useEffect(() => {
    scale.value = zoom
  }, [scale, zoom])

  const panGesture = Gesture.Pan()
    .onStart(() => {
      panStartX.value = translateX.value
      panStartY.value = translateY.value
    })
    .onUpdate((event) => {
      translateX.value = panStartX.value + event.translationX
      translateY.value = panStartY.value + event.translationY
    })

  const pinchGesture = Gesture.Pinch()
    .onStart(() => {
      pinchStartScale.value = scale.value
    })
    .onUpdate((event) => {
      const nextScale = pinchStartScale.value * event.scale
      scale.value = Math.max(0.65, Math.min(2.2, nextScale))
    })
    .onEnd(() => {
      const normalized = Number(scale.value.toFixed(2))
      scale.value = normalized
      setZoom(normalized)
    })

  const gesture = Gesture.Simultaneous(panGesture, pinchGesture)

  const animatedCanvasStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }))

  if (!taskId) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyText}>缺少待办编号，无法打开流程播放器。</Text>
      </View>
    )
  }

  if (detailQuery.isLoading) {
    return <AppLoader message="正在读取流程图与时间轴…" />
  }

  if (!detail) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyText}>没有找到对应的流程详情。</Text>
      </View>
    )
  }

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <SectionCard
        title="流程回顾播放器"
        description="基于 Skia 的只读播放器，复用共享回顾事件与时间轴逻辑。"
      >
        <View style={styles.toolbar}>
          <Pressable
            style={styles.toolbarButton}
            onPress={() =>
              dispatch({
                type: playbackState.mode === 'playing' ? 'pause' : 'play',
              })
            }
          >
            <Text style={styles.toolbarButtonLabel}>
              {playbackState.mode === 'playing' ? '暂停' : '播放'}
            </Text>
          </Pressable>
          <Pressable style={styles.toolbarButton} onPress={() => dispatch({ type: 'reset' })}>
            <Text style={styles.toolbarButtonLabel}>重播</Text>
          </Pressable>
          <Pressable
            style={styles.toolbarButton}
            onPress={() => setZoom((value) => Math.max(0.7, Number((value - 0.1).toFixed(1))))}
          >
            <Text style={styles.toolbarButtonLabel}>缩小</Text>
          </Pressable>
          <Pressable
            style={styles.toolbarButton}
            onPress={() => setZoom((value) => Math.min(1.8, Number((value + 0.1).toFixed(1))))}
          >
            <Text style={styles.toolbarButtonLabel}>放大</Text>
          </Pressable>
          <StatusBadge
            label={`${playbackState.activeIndex + 1}/${Math.max(playbackState.totalEvents, 1)}`}
            tone="info"
          />
        </View>
        <View
          style={[styles.canvasViewport, { height: viewportHeight }]}
          onLayout={(event: LayoutChangeEvent) => {
            const nextHeight = Math.max(360, event.nativeEvent.layout.height)
            if (nextHeight !== viewportHeight) {
              setViewportHeight(nextHeight)
            }
          }}
        >
          <GestureDetector gesture={gesture}>
            <Animated.View style={[styles.canvasLayer, animatedCanvasStyle]}>
              <View style={[styles.canvasContainer, canvasSize]}>
                <Canvas style={StyleSheet.absoluteFill}>
                  {edgeStates.map((edge) => {
                    const source = detail.flowNodes?.find((node) => node.id === edge.source)
                    const target = detail.flowNodes?.find((node) => node.id === edge.target)
                    if (!source || !target) {
                      return null
                    }
                    const startX = (source.position?.x ?? 0) + (source.ui?.width ?? NODE_WIDTH) / 2
                    const startY = (source.position?.y ?? 0) + (source.ui?.height ?? NODE_HEIGHT) / 2
                    const endX = (target.position?.x ?? 0) + (target.ui?.width ?? NODE_WIDTH) / 2
                    const endY = (target.position?.y ?? 0) + (target.ui?.height ?? NODE_HEIGHT) / 2
                    const color = edge.isActive
                      ? '#1A74DA'
                      : edge.isTraversed
                        ? '#27825B'
                        : '#D6CCBE'
                    return (
                      <Line
                        key={edge.id}
                        p1={{ x: startX, y: startY }}
                        p2={{ x: endX, y: endY }}
                        color={color}
                        strokeWidth={edge.isActive ? 4 : 2}
                      />
                    )
                  })}
                  {(detail.flowNodes ?? []).map((node) => {
                    const playbackNode = nodeStateMap.get(node.id)
                    const x = node.position?.x ?? 0
                    const y = node.position?.y ?? 0
                    const width = node.ui?.width ?? NODE_WIDTH
                    const height = node.ui?.height ?? NODE_HEIGHT
                    const color =
                      playbackNode?.state === 'active'
                        ? '#DFF0FF'
                        : playbackNode?.state === 'completed'
                          ? '#E9F7EF'
                          : playbackNode?.state === 'visited'
                            ? '#FFF5E4'
                            : '#FFFFFF'
                    return (
                      <Rect
                        key={`bg:${node.id}`}
                        x={x}
                        y={y}
                        width={width}
                        height={height}
                        color={color}
                      />
                    )
                  })}
                </Canvas>

                {(detail.flowNodes ?? []).map((node) => {
                  const playbackNode = nodeStateMap.get(node.id)
                  const state = playbackNode?.state ?? 'idle'
                  return (
                    <View
                      key={node.id}
                      style={[
                        styles.nodeCard,
                        {
                          left: node.position?.x ?? 0,
                          top: node.position?.y ?? 0,
                          width: node.ui?.width ?? NODE_WIDTH,
                          height: node.ui?.height ?? NODE_HEIGHT,
                        },
                        state === 'active'
                          ? styles.nodeCardActive
                          : state === 'completed'
                            ? styles.nodeCardCompleted
                            : state === 'visited'
                              ? styles.nodeCardVisited
                              : undefined,
                      ]}
                    >
                      <Text style={styles.nodeTitle} numberOfLines={2}>
                        {node.name ?? node.id}
                      </Text>
                      <Text style={styles.nodeHint}>{resolveNodeStateLabel(state)}</Text>
                    </View>
                  )
                })}
              </View>
            </Animated.View>
          </GestureDetector>
        </View>
      </SectionCard>

      <SectionCard title="时间轴" description="播放事件和任务轨迹由 shared-workflow 统一推导。">
        {timelineEntries.map((entry, index) => (
          <Pressable
            key={entry.id}
            style={[
              styles.timelineRow,
              index === playbackState.activeIndex && styles.timelineRowActive,
            ]}
            onPress={() => dispatch({ type: 'seek', index })}
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

function resolveNodeStateLabel(state: 'active' | 'completed' | 'visited' | 'idle') {
  switch (state) {
    case 'active':
      return '当前高亮'
    case 'completed':
      return '已完成'
    case 'visited':
      return '已访问'
    case 'idle':
      return '未触达'
  }
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
  toolbar: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 10,
  },
  toolbarButton: {
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#D9CDBD',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  toolbarButtonLabel: {
    color: '#231C18',
    fontWeight: '700',
  },
  canvasContainer: {
    position: 'relative',
    borderRadius: 24,
    backgroundColor: '#FBF8F2',
    overflow: 'hidden',
  },
  canvasViewport: {
    overflow: 'hidden',
    borderRadius: 24,
    backgroundColor: '#F8F5EE',
  },
  canvasLayer: {
    minWidth: '100%',
    minHeight: '100%',
  },
  nodeCard: {
    position: 'absolute',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#E1D7CA',
    backgroundColor: 'transparent',
    padding: 12,
    justifyContent: 'center',
  },
  nodeCardActive: {
    borderColor: '#1A74DA',
    backgroundColor: 'rgba(223,240,255,0.76)',
  },
  nodeCardCompleted: {
    borderColor: '#27825B',
    backgroundColor: 'rgba(233,247,239,0.76)',
  },
  nodeCardVisited: {
    borderColor: '#E09C2D',
    backgroundColor: 'rgba(255,245,228,0.76)',
  },
  nodeTitle: {
    color: '#171312',
    fontSize: 13,
    fontWeight: '700',
  },
  nodeHint: {
    color: '#65594E',
    fontSize: 11,
    marginTop: 4,
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
