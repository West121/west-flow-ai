import * as Y from 'yjs'
import { type WorkflowDesignerSharedState } from './types'

const WORKFLOW_DESIGNER_COLLAB_ROOT = 'workflow-designer-collab'
const WORKFLOW_DESIGNER_COLLAB_STATE_KEY = 'state'

export const WORKFLOW_DESIGNER_LOCAL_ORIGIN = Symbol(
  'workflow-designer-collaboration-local'
)
export const WORKFLOW_DESIGNER_REMOTE_ORIGIN = Symbol(
  'workflow-designer-collaboration-remote'
)

function collabRoot(doc: Y.Doc) {
  return doc.getMap<string>(WORKFLOW_DESIGNER_COLLAB_ROOT)
}

export function createWorkflowDesignerYDoc() {
  return new Y.Doc()
}

export function serializeWorkflowDesignerSharedState(
  state: WorkflowDesignerSharedState
) {
  return JSON.stringify(state)
}

export function parseWorkflowDesignerSharedState(raw: string | undefined | null) {
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as WorkflowDesignerSharedState
  } catch {
    return null
  }
}

export function readWorkflowDesignerSharedState(doc: Y.Doc) {
  return parseWorkflowDesignerSharedState(
    collabRoot(doc).get(WORKFLOW_DESIGNER_COLLAB_STATE_KEY)
  )
}

export function writeWorkflowDesignerSharedState(
  doc: Y.Doc,
  state: WorkflowDesignerSharedState,
  origin: unknown = WORKFLOW_DESIGNER_LOCAL_ORIGIN
) {
  const root = collabRoot(doc)
  const serialized = serializeWorkflowDesignerSharedState(state)
  if (root.get(WORKFLOW_DESIGNER_COLLAB_STATE_KEY) === serialized) {
    return serialized
  }

  doc.transact(() => {
    root.set(WORKFLOW_DESIGNER_COLLAB_STATE_KEY, serialized)
  }, origin)

  return serialized
}
