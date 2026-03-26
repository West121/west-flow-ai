import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  createWorkflowDesignerCollaborationBinding,
} from './bindings'
import {
  createWorkflowDesignerYDoc,
  WORKFLOW_DESIGNER_REMOTE_ORIGIN,
  writeWorkflowDesignerSharedState,
} from './ydoc'
import { useWorkflowDesignerStore } from '../designer/store'
import { type ProcessDefinitionMeta } from '../designer/dsl'

const definitionMeta: ProcessDefinitionMeta = {
  processKey: 'oa_leave',
  processName: '请假审批',
  category: 'OA',
  processFormKey: 'oa-leave-start-form',
  processFormVersion: '1.1.0',
  formFields: [],
}

describe('workflow designer collaboration binding', () => {
  beforeEach(() => {
    useWorkflowDesignerStore.getState().resetDesigner()
  })

  afterEach(() => {
    useWorkflowDesignerStore.getState().resetDesigner()
  })

  it('applies remote updates to the store without polluting history', async () => {
    const doc = createWorkflowDesignerYDoc()
    let currentMeta = definitionMeta

    const binding = createWorkflowDesignerCollaborationBinding({
      doc,
      getSnapshot: () => useWorkflowDesignerStore.getState().history.present,
      getDefinitionMeta: () => currentMeta,
      applyRemoteSnapshot: (snapshot) => {
        useWorkflowDesignerStore.getState().applyRemoteSnapshot(snapshot)
      },
      applyRemoteDefinitionMeta: (meta) => {
        currentMeta = meta
      },
    })

    const initialHistoryLength = useWorkflowDesignerStore.getState().history.past.length
    const remoteState = {
      nodes: useWorkflowDesignerStore
        .getState()
        .history.present.nodes.map((node) =>
          node.id === 'node-manager-role'
            ? {
                ...node,
                data: {
                  ...node.data,
                  label: '远端直属主管审批',
                },
              }
            : node
        ),
      edges: useWorkflowDesignerStore.getState().history.present.edges,
      definitionMeta: {
        ...definitionMeta,
        processName: '远端请假审批',
      },
    }

    writeWorkflowDesignerSharedState(
      doc,
      remoteState,
      WORKFLOW_DESIGNER_REMOTE_ORIGIN
    )

    await Promise.resolve()

    const nextState = useWorkflowDesignerStore.getState()
    expect(
      nextState.history.present.nodes.find((node) => node.id === 'node-manager-role')
        ?.data.label
    ).toBe('远端直属主管审批')
    expect(nextState.history.past).toHaveLength(initialHistoryLength)
    expect(currentMeta.processName).toBe('远端请假审批')

    binding.destroy()
    doc.destroy()
  })
})
