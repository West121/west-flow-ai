import type { Awareness } from 'y-protocols/awareness'
import {
  type WorkflowDesignerCollaborationAwarenessState,
  type WorkflowDesignerCollaborationPeer,
} from './types'

const peerPalette = [
  '#0ea5e9',
  '#f97316',
  '#22c55e',
  '#8b5cf6',
  '#ef4444',
  '#14b8a6',
  '#eab308',
  '#ec4899',
]

export function resolveWorkflowDesignerPeerColor(seed: string) {
  let hash = 0
  for (const char of seed) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }

  return peerPalette[hash % peerPalette.length]
}

export function resolveWorkflowDesignerPeers(
  awareness: Awareness,
  currentUserId: string | null
) {
  return Array.from(awareness.getStates().entries())
    .map(([clientId, state]) => {
      const value = state as WorkflowDesignerCollaborationAwarenessState | null
      if (!value?.userId || !value.displayName) {
        return null
      }
      if (currentUserId && value.userId === currentUserId) {
        return null
      }
      return {
        clientId,
        userId: value.userId,
        displayName: value.displayName,
        color: value.color,
        selectedNodeId: value.selectedNodeId ?? null,
        editingNodeId: value.editingNodeId ?? null,
        cursor: value.cursor ?? null,
      } satisfies WorkflowDesignerCollaborationPeer
    })
    .filter((peer): peer is WorkflowDesignerCollaborationPeer => peer !== null)
}
