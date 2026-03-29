import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RuntimeStructureSection } from './runtime-structure'

describe('runtime structure section', () => {
  it('renders subprocess, append and dynamic build links as a nested runtime tree', () => {
    render(
      <RuntimeStructureSection
        currentInstanceId='pi_root'
        links={[
          {
            linkId: 'subprocess_001',
            rootInstanceId: 'pi_root',
            parentInstanceId: 'pi_root',
            childInstanceId: 'pi_sub_001',
            parentNodeId: 'subprocess_review',
            calledProcessKey: 'oa_sub_review',
            calledDefinitionId: 'oa_sub_review:1:1004',
            calledVersionPolicy: 'FIXED_VERSION',
            calledVersion: 1,
            linkType: 'CALL_ACTIVITY',
            status: 'RUNNING',
            callScope: 'CHILD_AND_DESCENDANTS',
            joinMode: 'WAIT_PARENT_CONFIRM',
            childStartStrategy: 'FIXED_VERSION',
            childStartDecisionReason: 'oa_sub_review@1',
            parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
            resumeDecisionReason: 'PARENT_CONFIRM_RESUMED',
            descendantCount: 1,
            runningDescendantCount: 1,
            terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
            childFinishPolicy: 'RETURN_TO_PARENT',
            createdAt: '2026-03-23T10:10:00+08:00',
            finishedAt: null,
          },
          {
            linkId: 'append_001',
            rootInstanceId: 'pi_root',
            parentInstanceId: 'pi_sub_001',
            childInstanceId: 'pi_append_001',
            parentNodeId: 'dynamic_builder_001',
            calledProcessKey: 'oa_leave_append_review',
            calledDefinitionId: 'oa_leave_append_review:1:1008',
            linkType: 'ADHOC',
            runtimeLinkType: 'ADHOC_TASK',
            triggerMode: 'APPEND',
            appendType: 'TASK',
            status: 'RUNNING',
            terminatePolicy: 'TERMINATE_GENERATED_ONLY',
            childFinishPolicy: 'RETURN_TO_PARENT',
            sourceTaskId: 'task_root_001',
            sourceNodeId: 'dynamic_builder_001',
            targetTaskId: 'task_append_001',
            targetInstanceId: 'pi_append_001',
            targetUserId: 'usr_003',
            operatorUserId: 'usr_002',
            commentText: '追加一位串行复核人',
            createdAt: '2026-03-23T10:20:00+08:00',
            finishedAt: null,
          },
          {
            linkId: 'dynamic_001',
            rootInstanceId: 'pi_root',
            parentInstanceId: 'pi_root',
            childInstanceId: 'pi_dynamic_001',
            parentNodeId: 'dynamic_builder_002',
            calledProcessKey: 'oa_leave_dynamic_subflow',
            calledDefinitionId: 'oa_leave_dynamic_subflow:2:1009',
            calledVersionPolicy: 'LATEST_PUBLISHED',
            calledVersion: null,
            linkType: 'ADHOC',
            runtimeLinkType: 'ADHOC_SUBPROCESS',
            triggerMode: 'DYNAMIC_BUILD',
            appendType: 'SUBPROCESS',
            status: 'COMPLETED',
            buildMode: 'SUBPROCESS_CALLS',
            sourceMode: 'MODEL_DRIVEN',
            sceneCode: 'leave_sub_scene',
            ruleExpression: 'ifElse(leaveDays >= 5, "SUBPROCESS_CALL", "APPEND_TASK")',
            manualTemplateCode: 'append_sub_review',
            resolvedTargetMode: 'PROCESS_KEY',
            executionStrategy: 'TEMPLATE_FIRST',
            fallbackStrategy: 'USE_TEMPLATE',
            maxGeneratedCount: 2,
            generatedCount: 1,
            generationTruncated: false,
            resolvedSourceMode: 'MODEL_DRIVEN',
            resolutionPath: 'TEMPLATE_PRIMARY',
            templateSource: 'SCENE_CODE',
            targetBusinessType: 'OA_LEAVE',
            targetSceneCode: 'leave_sub_review_scene',
            terminatePolicy: 'TERMINATE_PARENT_AND_GENERATED',
            childFinishPolicy: 'TERMINATE_PARENT',
            sourceTaskId: 'task_root_001',
            sourceNodeId: 'dynamic_builder_002',
            targetTaskId: null,
            targetInstanceId: 'pi_dynamic_001',
            targetUserId: null,
            operatorUserId: 'system',
            commentText: '规则命中后自动构建子流程',
            createdAt: '2026-03-23T10:30:00+08:00',
            finishedAt: '2026-03-23T10:45:00+08:00',
          },
        ]}
      />
    )

    expect(screen.getByText('运行态结构')).toBeInTheDocument()
    expect(screen.getByText('根流程实例：pi_root')).toBeInTheDocument()
    expect(screen.getAllByText('主子流程').length).toBeGreaterThan(0)
    expect(screen.getAllByText('追加任务').length).toBeGreaterThan(0)
    expect(screen.getAllByText('动态构建').length).toBeGreaterThan(0)
    expect(
      screen.getByText((_, element) =>
        element?.textContent?.replace(/\s+/g, '') === '子流程实例：pi_sub_001'
      )
    ).toBeInTheDocument()
    expect(screen.getByText(/附属任务/)).toBeInTheDocument()
    expect(
      screen
        .getAllByText((_, element) =>
          element?.textContent?.includes('pi_append_001') ?? false
        )
        .some((element) =>
          element.textContent?.replace(/\s+/g, '') === '附属任务：pi_append_001'
        )
    ).toBe(true)
    expect(screen.getByText(/动态构建实例/)).toBeInTheDocument()
    expect(
      screen
        .getAllByText((_, element) =>
          element?.textContent?.includes('pi_dynamic_001') ?? false
        )
        .some((element) =>
          element.textContent?.replace(/\s+/g, '') === '动态构建实例：pi_dynamic_001'
        )
    ).toBe(true)
    expect(screen.getByText('结构来源：ADHOC_TASK')).toBeInTheDocument()
    expect(screen.getByText('结构来源：ADHOC_SUBPROCESS')).toBeInTheDocument()
    expect(screen.getByText('终止策略：TERMINATE_PARENT_AND_SUBPROCESS')).toBeInTheDocument()
    expect(screen.getByText('调用版本策略：FIXED_VERSION')).toBeInTheDocument()
    expect(screen.getByText('调用版本：1')).toBeInTheDocument()
    expect(screen.getByText('子流程策略')).toBeInTheDocument()
    expect(screen.getByText('调用范围：CHILD_AND_DESCENDANTS')).toBeInTheDocument()
    expect(screen.getByText('汇合模式：WAIT_PARENT_CONFIRM')).toBeInTheDocument()
    expect(screen.getByText('子流程启动策略：FIXED_VERSION')).toBeInTheDocument()
    expect(screen.getByText('启动决策：oa_sub_review@1')).toBeInTheDocument()
    expect(screen.getByText('父流程恢复策略：WAIT_PARENT_CONFIRM')).toBeInTheDocument()
    expect(screen.getByText('恢复决策：PARENT_CONFIRM_RESUMED')).toBeInTheDocument()
    expect(screen.getByText('后代子流程数：1')).toBeInTheDocument()
    expect(screen.getByText('运行中后代数：1')).toBeInTheDocument()
    expect(screen.getByText('动态构建策略')).toBeInTheDocument()
    expect(screen.getByText('构建模式：SUBPROCESS_CALLS')).toBeInTheDocument()
    expect(screen.getByText('来源模式：MODEL_DRIVEN')).toBeInTheDocument()
    expect(screen.getByText('场景编码：leave_sub_scene')).toBeInTheDocument()
    expect(
      screen.getByText('规则表达式：ifElse(leaveDays >= 5, "SUBPROCESS_CALL", "APPEND_TASK")')
    ).toBeInTheDocument()
    expect(screen.getByText('模板编码：append_sub_review')).toBeInTheDocument()
    expect(screen.getByText('目标模式：PROCESS_KEY')).toBeInTheDocument()
    expect(screen.getByText('执行策略：TEMPLATE_FIRST')).toBeInTheDocument()
    expect(screen.getByText('回退策略：USE_TEMPLATE')).toBeInTheDocument()
    expect(screen.getByText('最大生成数：2')).toBeInTheDocument()
    expect(screen.getByText('实际生成数：1')).toBeInTheDocument()
    expect(screen.getByText('是否截断：否')).toBeInTheDocument()
    expect(screen.getByText('实际来源：MODEL_DRIVEN')).toBeInTheDocument()
    expect(screen.getByText('解析路径：TEMPLATE_PRIMARY')).toBeInTheDocument()
    expect(screen.getByText('模板来源：SCENE_CODE')).toBeInTheDocument()
    expect(screen.getByText('业务类型：OA_LEAVE')).toBeInTheDocument()
    expect(screen.getByText('场景编码：leave_sub_review_scene')).toBeInTheDocument()
    expect(screen.getByText('附言：追加一位串行复核人')).toBeInTheDocument()
  })

  it('allows confirming wait-parent-confirm subprocess links', () => {
    const confirmedLinkIds: string[] = []
    render(
      <RuntimeStructureSection
        currentInstanceId='pi_root'
        onConfirmParentResume={(link) => confirmedLinkIds.push(link.linkId)}
        links={[
          {
            linkId: 'subprocess_wait_confirm_001',
            rootInstanceId: 'pi_root',
            parentInstanceId: 'pi_root',
            childInstanceId: 'pi_sub_001',
            parentNodeId: 'subprocess_review',
            calledProcessKey: 'oa_sub_review',
            calledDefinitionId: 'oa_sub_review:1:1004',
            calledVersionPolicy: 'LATEST_PUBLISHED',
            calledVersion: null,
            linkType: 'CALL_ACTIVITY',
            status: 'WAIT_PARENT_CONFIRM',
            callScope: 'CHILD_ONLY',
            joinMode: 'WAIT_PARENT_CONFIRM',
            childStartStrategy: 'LATEST_PUBLISHED',
            parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
            resumeDecisionReason: 'WAIT_PARENT_CONFIRM',
            terminatePolicy: 'TERMINATE_SUBPROCESS_ONLY',
            childFinishPolicy: 'RETURN_TO_PARENT',
            createdAt: '2026-03-24T10:10:00+08:00',
            finishedAt: '2026-03-24T10:20:00+08:00',
          },
        ]}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: '父流程确认恢复' }))
    expect(confirmedLinkIds).toEqual(['subprocess_wait_confirm_001'])
  })

  it('still renders subprocess links when current instance is the child instance', () => {
    render(
      <RuntimeStructureSection
        currentInstanceId='pi_sub_001'
        links={[
          {
            linkId: 'subprocess_child_focus_001',
            rootInstanceId: 'pi_root',
            parentInstanceId: 'pi_root',
            childInstanceId: 'pi_sub_001',
            parentNodeId: 'subprocess_review',
            calledProcessKey: 'oa_sub_review',
            calledDefinitionId: 'oa_sub_review:1:1004',
            calledVersionPolicy: 'LATEST_PUBLISHED',
            calledVersion: null,
            linkType: 'CALL_ACTIVITY',
            status: 'RUNNING',
            callScope: 'CHILD_ONLY',
            joinMode: 'WAIT_PARENT_CONFIRM',
            childStartStrategy: 'LATEST_PUBLISHED',
            parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
            resumeDecisionReason: 'PARENT_CONFIRM_RESUMED',
            terminatePolicy: 'TERMINATE_SUBPROCESS_ONLY',
            childFinishPolicy: 'RETURN_TO_PARENT',
            createdAt: '2026-03-24T10:10:00+08:00',
            finishedAt: null,
          },
        ]}
      />
    )

    expect(screen.getByText('根流程实例：pi_root')).toBeInTheDocument()
    expect(
      screen.getByText((_, element) =>
        element?.textContent?.replace(/\s+/g, '') === '子流程实例：pi_sub_001'
      )
    ).toBeInTheDocument()
    expect(screen.getByText('子流程编码：oa_sub_review')).toBeInTheDocument()
  })
})
