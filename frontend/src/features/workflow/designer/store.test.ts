import { Play } from 'lucide-react'
import { afterEach, describe, expect, it } from 'vitest'
import { useWorkflowDesignerStore } from './store'

afterEach(() => {
  useWorkflowDesignerStore.getState().resetDesigner()
})

describe('workflow designer store', () => {
  it('appends subprocess preset as connected structure', () => {
    const store = useWorkflowDesignerStore.getState()

    store.addStructurePreset('SUBPROCESS_CHAIN')

    const snapshot = useWorkflowDesignerStore.getState().history.present
    const subprocess = snapshot.nodes.find((node) => node.data.label === '销假子流程')
    expect(subprocess).toBeTruthy()
    expect(subprocess?.data.config).toMatchObject({
      callScope: 'CHILD_ONLY',
      joinMode: 'AUTO_RETURN',
      childStartStrategy: 'LATEST_PUBLISHED',
      parentResumeStrategy: 'AUTO_RETURN',
    })
    expect(
      snapshot.edges.some((edge) => edge.label === '进入子流程')
    ).toBe(true)
  })

  it('appends dynamic builder preset as connected structure', () => {
    const store = useWorkflowDesignerStore.getState()

    store.addStructurePreset('DYNAMIC_BUILDER_CHAIN')

    const snapshot = useWorkflowDesignerStore.getState().history.present
    expect(
      snapshot.nodes.some((node) => node.data.label === '动态构建审批链')
    ).toBe(true)
    expect(
      snapshot.edges.some((edge) => edge.label === '规则生成')
    ).toBe(true)
  })

  it('appends inclusive branch preset with conditional edges', () => {
    const store = useWorkflowDesignerStore.getState()

    store.addStructurePreset('INCLUSIVE_BRANCH')

    const snapshot = useWorkflowDesignerStore.getState().history.present
    expect(
      snapshot.nodes.some((node) => node.data.label === '包容分支合流')
    ).toBe(true)
    expect(
      snapshot.edges.some(
        (edge) => edge.data?.condition?.type === 'FORMULA'
      )
    ).toBe(true)
  })

  it('inserts a node in the middle of an existing edge', () => {
    const store = useWorkflowDesignerStore.getState()
    const sourceEdge = store.history.present.edges.find(
      (edge) => edge.id === 'edge-start-condition'
    )

    expect(sourceEdge).toBeTruthy()

    store.insertNodeOnEdge(sourceEdge!.id, {
      kind: 'approver',
      label: '审批',
      description: '支持会签、或签、主办、转办',
      tone: 'brand',
      accent: 'from-sky-500/20 to-sky-500/5',
      icon: Play,
    })

    const snapshot = useWorkflowDesignerStore.getState().history.present
    const insertedNode = snapshot.nodes.find((node) => node.id === snapshot.selectedNodeId)

    expect(insertedNode?.data.kind).toBe('approver')
    expect(
      snapshot.edges.some(
        (edge) =>
          edge.source === 'node-start' && edge.target === insertedNode?.id
      )
    ).toBe(true)
    expect(
      snapshot.edges.some(
        (edge) =>
          edge.source === insertedNode?.id && edge.target === 'node-condition'
      )
    ).toBe(true)
  })

  it('selects edges independently from nodes', () => {
    const store = useWorkflowDesignerStore.getState()

    store.setSelectedNodeId('node-condition')
    store.setSelectedEdgeId('edge-start-condition')

    const snapshot = useWorkflowDesignerStore.getState().history.present
    expect(snapshot.selectedNodeId).toBeNull()
    expect(snapshot.selectedEdgeId).toBe('edge-start-condition')
  })

  it('adds a new branch from a condition gateway and selects the new edge', () => {
    const store = useWorkflowDesignerStore.getState()

    store.addBranchOnGateway('node-condition')

    const snapshot = useWorkflowDesignerStore.getState().history.present
    const newEdge = snapshot.edges.find(
      (edge) => edge.id === snapshot.selectedEdgeId
    )

    expect(newEdge).toBeTruthy()
    expect(newEdge?.source).toBe('node-condition')
    expect(newEdge?.label).toContain('条件分支')
    expect(snapshot.selectedNodeId).toBeNull()
    expect(
      snapshot.nodes.some((node) => node.id === newEdge?.target && node.data.kind === 'approver')
    ).toBe(true)
  })
})
