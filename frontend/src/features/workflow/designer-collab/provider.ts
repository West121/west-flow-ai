import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate } from 'y-protocols/awareness'
import { WebsocketProvider } from 'y-websocket'
import * as Y from 'yjs'
import { WORKFLOW_DESIGNER_REMOTE_ORIGIN } from './ydoc'
import {
  type WorkflowDesignerCollaborationAwarenessState,
  type WorkflowDesignerCollaborationMode,
  type WorkflowDesignerCollaborationStatus,
} from './types'

type ProviderMessage =
  | { type: 'sync-request'; sender: number }
  | { type: 'doc-update'; sender: number; payload: number[] }
  | { type: 'awareness-update'; sender: number; payload: number[] }

export type WorkflowDesignerCollaborationProvider = {
  awareness: Awareness
  mode: WorkflowDesignerCollaborationMode
  status: WorkflowDesignerCollaborationStatus
  setLocalState: (state: WorkflowDesignerCollaborationAwarenessState) => void
  onStatusChange: (listener: (status: WorkflowDesignerCollaborationStatus) => void) => () => void
  reconnect: () => void
  destroy: () => void
}

type CreateWorkflowDesignerCollaborationProviderOptions = {
  serverUrl?: string
  authToken?: string
}

export function createWorkflowDesignerCollaborationProvider(
  roomName: string,
  doc: Y.Doc,
  options: CreateWorkflowDesignerCollaborationProviderOptions = {}
): WorkflowDesignerCollaborationProvider {
  const serverUrl = options.serverUrl?.trim()
  if (serverUrl) {
    return createWebsocketWorkflowDesignerCollaborationProvider(roomName, doc, {
      serverUrl,
      authToken: options.authToken,
    })
  }

  return createBroadcastWorkflowDesignerCollaborationProvider(roomName, doc)
}

function createBroadcastWorkflowDesignerCollaborationProvider(
  roomName: string,
  doc: Y.Doc
): WorkflowDesignerCollaborationProvider {
  const awareness = new Awareness(doc)
  const channelName = `westflow-workflow-designer:${roomName}`
  const channel =
    typeof globalThis.BroadcastChannel === 'function'
      ? new globalThis.BroadcastChannel(channelName)
      : null
  const statusListeners = new Set<(status: WorkflowDesignerCollaborationStatus) => void>()
  let status: WorkflowDesignerCollaborationStatus = channel ? 'connected' : 'local'

  const notifyStatus = (nextStatus: WorkflowDesignerCollaborationStatus) => {
    status = nextStatus
    for (const listener of statusListeners) {
      listener(nextStatus)
    }
  }

  const post = (message: ProviderMessage) => {
    if (!channel) {
      return
    }
    channel.postMessage(message)
  }

  const handleDocUpdate = (update: Uint8Array, origin: unknown) => {
    if (origin === WORKFLOW_DESIGNER_REMOTE_ORIGIN || !channel) {
      return
    }
    post({
      type: 'doc-update',
      sender: doc.clientID,
      payload: Array.from(update),
    })
  }

  const handleAwarenessUpdate = ({
    added,
    updated,
    removed,
  }: {
    added: number[]
    updated: number[]
    removed: number[]
  }) => {
    if (!channel) {
      return
    }
    const changedClients = [...added, ...updated, ...removed]
    if (changedClients.length === 0) {
      return
    }
    post({
      type: 'awareness-update',
      sender: doc.clientID,
      payload: Array.from(encodeAwarenessUpdate(awareness, changedClients)),
    })
  }

  doc.on('update', handleDocUpdate)
  awareness.on('update', handleAwarenessUpdate)

  channel?.addEventListener('message', (event: MessageEvent<ProviderMessage>) => {
    const message = event.data
    if (!message || message.sender === doc.clientID) {
      return
    }

    switch (message.type) {
      case 'sync-request':
        post({
          type: 'doc-update',
          sender: doc.clientID,
          payload: Array.from(Y.encodeStateAsUpdate(doc)),
        })
        if (awareness.getStates().size > 0) {
          post({
            type: 'awareness-update',
            sender: doc.clientID,
            payload: Array.from(
              encodeAwarenessUpdate(awareness, Array.from(awareness.getStates().keys()))
            ),
          })
        }
        break
      case 'doc-update':
        Y.applyUpdate(doc, Uint8Array.from(message.payload), WORKFLOW_DESIGNER_REMOTE_ORIGIN)
        break
      case 'awareness-update':
        applyAwarenessUpdate(
          awareness,
          Uint8Array.from(message.payload),
          WORKFLOW_DESIGNER_REMOTE_ORIGIN
        )
        break
    }
  })

  post({ type: 'sync-request', sender: doc.clientID })

  return {
    awareness,
    mode: 'broadcast',
    get status() {
      return status
    },
    setLocalState: (state) => {
      awareness.setLocalState(state)
    },
    onStatusChange: (listener) => {
      statusListeners.add(listener)
      listener(status)
      return () => {
        statusListeners.delete(listener)
      }
    },
    reconnect: () => {
      notifyStatus(channel ? 'connected' : 'local')
    },
    destroy: () => {
      awareness.setLocalState(null)
      doc.off('update', handleDocUpdate)
      awareness.off('update', handleAwarenessUpdate)
      channel?.close()
      notifyStatus('disconnected')
    },
  }
}

function createWebsocketWorkflowDesignerCollaborationProvider(
  roomName: string,
  doc: Y.Doc,
  options: {
    serverUrl: string
    authToken?: string
  }
): WorkflowDesignerCollaborationProvider {
  const awareness = new Awareness(doc)
  const statusListeners = new Set<(status: WorkflowDesignerCollaborationStatus) => void>()
  let status: WorkflowDesignerCollaborationStatus = 'connecting'
  let hasConnected = false
  let isDestroyed = false

  const params: Record<string, string> = {}
  if (options.authToken) {
    params.token = options.authToken
  }

  const provider = new WebsocketProvider(options.serverUrl, roomName, doc, {
    awareness,
    params,
    maxBackoffTime: 2_500,
  })

  const notifyStatus = (nextStatus: WorkflowDesignerCollaborationStatus) => {
    status = nextStatus
    for (const listener of statusListeners) {
      listener(nextStatus)
    }
  }

  provider.on('status', (event: { status: 'connected' | 'disconnected' | 'connecting' }) => {
    if (event.status === 'connected') {
      hasConnected = true
      notifyStatus('connected')
      return
    }
    if (event.status === 'connecting') {
      notifyStatus(hasConnected ? 'reconnecting' : 'connecting')
      return
    }
    notifyStatus(isDestroyed ? 'disconnected' : hasConnected ? 'reconnecting' : 'disconnected')
  })
  provider.on('connection-error', () => {
    notifyStatus(hasConnected ? 'reconnecting' : 'disconnected')
  })

  return {
    awareness,
    mode: 'websocket',
    get status() {
      return status
    },
    setLocalState: (state) => {
      awareness.setLocalState(state)
    },
    onStatusChange: (listener) => {
      statusListeners.add(listener)
      listener(status)
      return () => {
        statusListeners.delete(listener)
      }
    },
    reconnect: () => {
      provider.connect()
      notifyStatus(hasConnected ? 'reconnecting' : 'connecting')
    },
    destroy: () => {
      isDestroyed = true
      awareness.setLocalState(null)
      provider.destroy()
      notifyStatus('disconnected')
    },
  }
}
