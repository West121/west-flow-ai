import type { ProcessDefinitionMeta } from '../designer/dsl'
import type { WorkflowSnapshot } from '../designer/types'
import {
  readWorkflowDesignerSharedState,
  serializeWorkflowDesignerSharedState,
  WORKFLOW_DESIGNER_LOCAL_ORIGIN,
  writeWorkflowDesignerSharedState,
} from './ydoc'
import { type WorkflowDesignerSharedState } from './types'
import type * as Y from 'yjs'

type WorkflowDesignerCollaborationBindingOptions = {
  doc: Y.Doc
  getSnapshot: () => WorkflowSnapshot
  getDefinitionMeta: () => ProcessDefinitionMeta
  applyRemoteSnapshot: (snapshot: Pick<WorkflowSnapshot, 'nodes' | 'edges'>) => void
  applyRemoteDefinitionMeta: (meta: ProcessDefinitionMeta) => void
}

export function createWorkflowDesignerCollaborationBinding({
  doc,
  getSnapshot,
  getDefinitionMeta,
  applyRemoteSnapshot,
  applyRemoteDefinitionMeta,
}: WorkflowDesignerCollaborationBindingOptions) {
  let lastSerialized = ''
  let applyingRemote = false

  const readLocalState = (): WorkflowDesignerSharedState => {
    const snapshot = getSnapshot()
    return {
      nodes: snapshot.nodes,
      edges: snapshot.edges,
      definitionMeta: getDefinitionMeta(),
    }
  }

  const syncLocalState = () => {
    if (applyingRemote) {
      return
    }
    const nextState = readLocalState()
    const nextSerialized = serializeWorkflowDesignerSharedState(nextState)
    if (nextSerialized === lastSerialized) {
      return
    }
    lastSerialized = writeWorkflowDesignerSharedState(
      doc,
      nextState,
      WORKFLOW_DESIGNER_LOCAL_ORIGIN
    )
  }

  const root = doc.getMap<string>('workflow-designer-collab')
  const handleRemoteChange = (
    _events: Y.YEvent<Y.AbstractType<unknown>>[],
    transaction: Y.Transaction
  ) => {
    if (transaction.origin === WORKFLOW_DESIGNER_LOCAL_ORIGIN) {
      return
    }
    const sharedState = readWorkflowDesignerSharedState(doc)
    if (!sharedState) {
      return
    }
    const serialized = serializeWorkflowDesignerSharedState(sharedState)
    if (serialized === lastSerialized) {
      return
    }
    lastSerialized = serialized
    applyingRemote = true
    applyRemoteSnapshot({
      nodes: sharedState.nodes,
      edges: sharedState.edges,
    })
    applyRemoteDefinitionMeta(sharedState.definitionMeta)
    queueMicrotask(() => {
      applyingRemote = false
    })
  }

  root.observeDeep(handleRemoteChange)

  const existingState = readWorkflowDesignerSharedState(doc)
  if (existingState) {
    lastSerialized = serializeWorkflowDesignerSharedState(existingState)
    applyRemoteSnapshot({
      nodes: existingState.nodes,
      edges: existingState.edges,
    })
    applyRemoteDefinitionMeta(existingState.definitionMeta)
  } else {
    syncLocalState()
  }

  return {
    syncLocalState,
    destroy: () => {
      root.unobserveDeep(handleRemoteChange)
    },
  }
}
