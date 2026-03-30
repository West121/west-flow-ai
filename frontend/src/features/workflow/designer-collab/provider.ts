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
  let transportGeneration = 0
  let provider: WebsocketProvider | null = null
  let currentLocalState: WorkflowDesignerCollaborationAwarenessState | null = null

  const params: Record<string, string> = {}
  if (options.authToken) {
    params.token = options.authToken
  }

  const notifyStatus = (nextStatus: WorkflowDesignerCollaborationStatus) => {
    if (status === nextStatus) {
      return
    }
    status = nextStatus
    for (const listener of statusListeners) {
      listener(nextStatus)
    }
  }

  const attachTransportHandlers = (nextProvider: WebsocketProvider, generation: number) => {
    nextProvider.on(
      'status',
      (event: { status: 'connected' | 'disconnected' | 'connecting' }) => {
        if (isDestroyed || generation !== transportGeneration) {
          return
        }
        if (event.status === 'connected') {
          hasConnected = true
          notifyStatus('connected')
          return
        }
        if (event.status === 'connecting') {
          notifyStatus(hasConnected ? 'reconnecting' : 'connecting')
          return
        }
        notifyStatus(hasConnected ? 'reconnecting' : 'disconnected')
      }
    )
    nextProvider.on('connection-error', () => {
      if (isDestroyed || generation !== transportGeneration) {
        return
      }
      notifyStatus(hasConnected ? 'reconnecting' : 'disconnected')
    })
    nextProvider.on('connection-close', () => {
      if (isDestroyed || generation !== transportGeneration) {
        return
      }
      notifyStatus(hasConnected ? 'reconnecting' : 'disconnected')
    })
  }

  const createTransport = (generation: number) => {
    const nextProvider = new WebsocketProvider(options.serverUrl, roomName, doc, {
      awareness,
      params,
      maxBackoffTime: 2_500,
      resyncInterval: 20_000,
    })
    attachTransportHandlers(nextProvider, generation)
    return nextProvider
  }

  const startTransport = () => {
    transportGeneration += 1
    return createTransport(transportGeneration)
  }

  provider = startTransport()

  return {
    awareness,
    mode: 'websocket',
    get status() {
      return status
    },
    setLocalState: (state) => {
      currentLocalState = state
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
      if (isDestroyed) {
        return
      }
      notifyStatus(hasConnected ? 'reconnecting' : 'connecting')
      currentLocalState = awareness.getLocalState() as WorkflowDesignerCollaborationAwarenessState | null
      transportGeneration += 1
      provider?.destroy()
      provider = createTransport(transportGeneration)
      if (currentLocalState) {
        awareness.setLocalState(currentLocalState)
      }
    },
    destroy: () => {
      isDestroyed = true
      transportGeneration += 1
      currentLocalState = null
      awareness.setLocalState(null)
      provider?.destroy()
      provider = null
      notifyStatus('disconnected')
    },
  }
}
