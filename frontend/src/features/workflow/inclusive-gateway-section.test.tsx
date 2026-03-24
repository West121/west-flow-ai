import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { InclusiveGatewaySection } from './inclusive-gateway-section'

describe('InclusiveGatewaySection', () => {
  it('renders inclusive gateway strategy metadata and branch traces', () => {
    render(
      <InclusiveGatewaySection
        hits={[
          {
            splitNodeId: 'inclusive_split_1',
            splitNodeName: '包容分支',
            joinNodeId: 'inclusive_join_1',
            joinNodeName: '包容汇聚',
            defaultBranchId: 'edge_urgent',
            requiredBranchCount: 1,
            branchMergePolicy: 'DEFAULT_BRANCH',
            branchPriorities: [10, 20],
            branchLabels: ['金额超限', '长假'],
            branchExpressions: ['amount > 1000', 'days > 3'],
            decisionSummary: '2 条候选路径，已命中 1 条，等待默认分支决策。',
            gatewayStatus: 'IN_PROGRESS',
            totalTargetCount: 2,
            activatedTargetCount: 1,
            activatedTargetNodeNames: ['财务审批'],
            skippedTargetNodeNames: ['人事审批'],
            firstActivatedAt: '2026-03-23T09:30:00+08:00',
            finishedAt: null,
          },
        ]}
      />
    )

    expect(screen.getAllByText('包容分支').length).toBeGreaterThan(0)
    expect(screen.getByText('DEFAULT_BRANCH')).toBeInTheDocument()
    expect(screen.getByText('默认分支：edge_urgent')).toBeInTheDocument()
    expect(screen.getByText('必选分支数：1')).toBeInTheDocument()
    expect(screen.getByText('分支优先级：10、20')).toBeInTheDocument()
    expect(screen.getByText('分支名称：金额超限、长假')).toBeInTheDocument()
    expect(screen.getByText('分支表达式：amount > 1000、days > 3')).toBeInTheDocument()
    expect(screen.getByText('决策摘要：2 条候选路径，已命中 1 条，等待默认分支决策。')).toBeInTheDocument()
    expect(screen.getByText('命中路径')).toBeInTheDocument()
    expect(screen.getByText('财务审批')).toBeInTheDocument()
    expect(screen.getByText('人事审批')).toBeInTheDocument()
  })

  it('returns null when no gateway hits are available', () => {
    const { container } = render(<InclusiveGatewaySection hits={[]} />)

    expect(container).toBeEmptyDOMElement()
  })
})
