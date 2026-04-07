import { useMemo, useState } from 'react'
import { useLocalSearchParams, useRouter } from 'expo-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'
import { AppLoader } from '@/components/AppLoader'
import { SectionCard } from '@/components/SectionCard'
import { StatusBadge } from '@/components/StatusBadge'
import {
  claimWorkbenchTask,
  completeWorkbenchTask,
  getWorkbenchTaskActions,
  getWorkbenchTaskDetail,
  rejectWorkbenchTask,
} from '@/lib/api/workbench'
import { getApiErrorMessage } from '@/lib/api/client'

export function ApprovalDetailScreen() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const params = useLocalSearchParams<{ taskId?: string }>()
  const taskId = typeof params.taskId === 'string' ? params.taskId : undefined
  const [comment, setComment] = useState('')

  const detailQuery = useQuery({
    queryKey: ['mobile', 'approval', 'detail', taskId],
    queryFn: () => getWorkbenchTaskDetail(taskId!),
    enabled: Boolean(taskId),
  })

  const actionsQuery = useQuery({
    queryKey: ['mobile', 'approval', 'actions', taskId],
    queryFn: () => getWorkbenchTaskActions(taskId!),
    enabled: Boolean(taskId),
  })

  const actionMutation = useMutation({
    mutationFn: async (action: 'claim' | 'approve' | 'reject') => {
      if (!taskId) {
        throw new Error('缺少待办编号')
      }
      if (action === 'claim') {
        return claimWorkbenchTask(taskId)
      }
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
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['mobile', 'workbench'] }),
        queryClient.invalidateQueries({ queryKey: ['mobile', 'approval'] }),
      ])
      setComment('')
      await Promise.all([detailQuery.refetch(), actionsQuery.refetch()])
    },
    onError: (error) => {
      Alert.alert('操作失败', getApiErrorMessage(error))
    },
  })

  const fields = useMemo(() => {
    const formData = detailQuery.data?.formData ?? {}
    return Object.entries(formData).filter(([, value]) => value !== null && value !== '')
  }, [detailQuery.data?.formData])

  if (!taskId) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyText}>缺少待办编号，无法打开审批详情。</Text>
      </View>
    )
  }

  if (detailQuery.isLoading) {
    return <AppLoader message="正在读取审批详情…" />
  }

  if (!detailQuery.data) {
    return (
      <View style={styles.centered}>
        <Text style={styles.emptyText}>没有找到对应的审批单。</Text>
      </View>
    )
  }

  const detail = detailQuery.data
  const actions = actionsQuery.data

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <SectionCard
        title={detail.processName}
        description={`${detail.nodeName} · ${detail.instanceStatus}`}
      >
        <View style={styles.headerMeta}>
          <StatusBadge label={detail.status} tone="info" />
          <Text style={styles.metaText}>实例 {detail.instanceId}</Text>
        </View>
        <Text style={styles.summaryText}>
          当前节点 {detail.nodeName}，可直接进入流程播放器查看历史回顾和路径高亮。
        </Text>
        <Pressable
          style={styles.secondaryButton}
          onPress={() =>
            router.push({ pathname: '/process-player' as never, params: { taskId } as never })
          }
        >
          <Text style={styles.secondaryButtonLabel}>打开流程播放器</Text>
        </Pressable>
      </SectionCard>

      <SectionCard title="申请信息" description="直接展示当前审批单的表单字段。">
        {fields.length === 0 ? (
          <Text style={styles.emptyText}>当前表单没有可展示字段。</Text>
        ) : (
          fields.map(([key, value]) => (
            <View key={key} style={styles.fieldRow}>
              <Text style={styles.fieldLabel}>{key}</Text>
              <Text style={styles.fieldValue}>
                {typeof value === 'string' ? value : JSON.stringify(value)}
              </Text>
            </View>
          ))
        )}
      </SectionCard>

      <SectionCard title="处理意见" description="先收常用动作：认领、同意、驳回。">
        <TextInput
          value={comment}
          onChangeText={setComment}
          placeholder="请输入审批意见"
          multiline
          style={styles.commentInput}
        />
        <View style={styles.actionRow}>
          {actions?.canClaim ? (
            <ActionButton
              label="认领"
              tone="neutral"
              busy={actionMutation.isPending}
              onPress={() => actionMutation.mutate('claim')}
            />
          ) : null}
          {actions?.canApprove ? (
            <ActionButton
              label="同意"
              tone="success"
              busy={actionMutation.isPending}
              onPress={() => actionMutation.mutate('approve')}
            />
          ) : null}
          {actions?.canReject ? (
            <ActionButton
              label="驳回发起人"
              tone="danger"
              busy={actionMutation.isPending}
              onPress={() => actionMutation.mutate('reject')}
            />
          ) : null}
        </View>
      </SectionCard>
    </ScrollView>
  )
}

function ActionButton({
  label,
  tone,
  busy,
  onPress,
}: {
  label: string
  tone: 'neutral' | 'success' | 'danger'
  busy: boolean
  onPress: () => void
}) {
  return (
    <Pressable
      disabled={busy}
      onPress={onPress}
      style={[
        styles.actionButton,
        tone === 'success'
          ? styles.actionButtonSuccess
          : tone === 'danger'
            ? styles.actionButtonDanger
            : styles.actionButtonNeutral,
        busy && styles.actionButtonBusy,
      ]}
    >
      <Text style={styles.actionLabel}>{busy ? '处理中…' : label}</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#F4F3F8',
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
    backgroundColor: '#F4F3F8',
    padding: 24,
  },
  emptyText: {
    color: '#777287',
    lineHeight: 20,
  },
  headerMeta: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 10,
  },
  metaText: {
    color: '#7A748C',
    fontSize: 12,
  },
  summaryText: {
    color: '#59556B',
    lineHeight: 22,
  },
  secondaryButton: {
    alignSelf: 'flex-start',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.78)',
    backgroundColor: 'rgba(255,255,255,0.58)',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  secondaryButtonLabel: {
    color: '#1B2132',
    fontWeight: '700',
  },
  fieldRow: {
    gap: 6,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.55)',
    paddingBottom: 12,
  },
  fieldLabel: {
    color: '#78738A',
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  fieldValue: {
    color: '#1A2030',
    lineHeight: 22,
  },
  commentInput: {
    minHeight: 100,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.68)',
    padding: 14,
    color: '#1A2030',
    textAlignVertical: 'top',
  },
  actionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  actionButton: {
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 13,
  },
  actionButtonNeutral: {
    backgroundColor: 'rgba(255,255,255,0.72)',
    borderColor: 'rgba(255,255,255,0.82)',
  },
  actionButtonSuccess: {
    backgroundColor: '#1C7B59',
    borderColor: 'rgba(255,255,255,0.2)',
  },
  actionButtonDanger: {
    backgroundColor: '#B53B46',
    borderColor: 'rgba(255,255,255,0.2)',
  },
  actionButtonBusy: {
    opacity: 0.72,
  },
  actionLabel: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
})
