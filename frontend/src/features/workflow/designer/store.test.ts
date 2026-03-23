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
    expect(snapshot.nodes.some((node) => node.data.label === '销假子流程')).toBe(true)
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
})
