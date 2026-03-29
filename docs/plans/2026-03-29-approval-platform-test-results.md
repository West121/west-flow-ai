# 审批功能测试结果清单

## 已通过

### 基础运行态动作

- 认领：待认领任务只显示认领按钮，认领后进入正常办理态。
- 同意：单任务同意后，列表、详情、时间轴、流程图状态一致。
- 驳回：普通任务支持驳回到上一步、发起人、指定已走人工节点。
- 退回：普通任务支持退回到上一步、发起人、指定已走人工节点。
- 转办：转办后当前办理人切换正确，详情轨迹可回溯。
- 委派：委派后任务归属和后续处理链正确。
- 加签：新增加签任务后详情焦点切到加签任务，加签完成后焦点回到原任务。
- 减签：可移除未完成加签任务，任务组和轨迹同步更新。
- 催办：可触发催办事件，轨迹有对应记录。
- 撤销：发起人撤销后，列表、详情、时间轴统一显示已撤销。
- 拿回：满足条件时可拿回，拿回后轨迹标记正确。
- 唤醒：挂起/唤醒链路可用。
- 跳转：跳转到指定节点后实例继续运行，当前节点投影正确。
- 终止：终止后实例状态、详情状态、轨迹一致。
- 电子签章：签章事件记录和详情展示已验证。
- 离职转办：预览与执行链路通过。

### 批量动作

- 批量认领：成功/失败口径正确。
- 批量已读：仅允许符合条件任务执行。
- 批量同意：批量提交后任务状态和计数正确。
- 批量驳回：批量驳回后返回逐任务结果。

### 会签与复杂流程

- 串签：会签进度、当前签核人、任务组状态正确。
- 并签：并行任务组、焦点任务、完成条件正确。
- 或签：首个同意后能按规则完成。
- 票签：任务组投票快照和结论同步。
- 串签驳回到上一签：已修复并通过真实运行态测试。
- 串签退回到上一签：已通过真实运行态测试。
- 并签驳回到发起人：已修复并通过真实运行态测试。
- 并签退回到发起人：已通过真实运行态测试。
- 会签中加签：加签完成后焦点可回到原会签人。
- 驳回/退回到网关节点：会被明确拦截，返回业务错误，不出现系统异常。
- 包容分支：多命中分支链路已回归。
- 子流程：追加子流程和父流程恢复链路已回归。
- 动态追加：追加人工节点、追加子流程链路已回归。

### 协同审批模式

- 抄送已阅：已读事件、视图口径、审批单展示通过。
- 督办：已阅事件名称和语义正确。
- 会办：已阅事件名称和语义正确。
- 阅办：已阅事件名称和语义正确。
- 传阅：已阅事件名称和语义正确。
- 权限保护：非目标用户不能执行协同已阅动作。

### SLA 与轨迹

- 自动提醒：提醒策略 trace 已验证。
- 升级策略：升级 trace 与详情展示已验证。
- Trace Store：数据库与内存实现回归通过。

### 任职上下文

- 当前任职影响候选任务范围：主职/兼职切换后待办过滤正确。
- 当前任职影响发起身份：发起部门、发起岗位写入并在列表/详情展示。

### 前端一致性

- 列表、详情、时间轴、流程图的状态投影已对齐。
- 回顾流程图播放从开始到结束。
- 加签、撤销、驳回后时间轴和流程图不再串任务、串色。

## 需补自动化

- 督办 / 会办 / 阅办 / 传阅在前端工作台的完整 E2E 覆盖。
- 批量动作的前端交互回归，包括部分失败时的 UI 反馈。
- 会签下加签、减签、驳回、退回的前端端到端用例。
- 电子签章在前端详情页、时间轴、材料回显的 E2E。
- SLA 提醒/升级在前端自动化区块与时间轴联动的 E2E。
- 任职上下文切换后再执行审批动作的浏览器回归。
- 列表与详情在复杂动作后的统一快照测试。

## 后续增强项

- 正式短信 / 邮件 / 企业微信等外部送达通道联调。
- 法务级签章版式、PDF 落章、验签与留痕验收。
- 更高强度并发下的审批动作稳定性与数据库瓶颈定位。
- 复杂组合场景的长链人工巡检：
  - 会签 + 加签 + 驳回 + SLA
  - 子流程 + 终止 + 唤醒
  - 代理 / 委派 / 离职转办混合链路
- 审批详情视觉一致性和异常空态的人工巡检。

## 本轮执行证据

### 后端

```bash
mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest#shouldSupportSemanticCollaborationReadFlowOnRealFlowableRuntime+shouldRejectSemanticReadWhenCurrentUserIsNotTarget test
mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest#shouldSupportSemanticCollaborationReadFlowOnRealFlowableRuntime+shouldSupportCcReadAndCcApprovalSheetViewOnRealFlowableRuntime+shouldSupportBatchClaimReadCompleteAndRejectOnRealFlowableRuntime+shouldSupportAddSignAndRemoveSignOnRealFlowableRuntime+shouldReturnFocusToOriginalTaskAfterAddSignTaskCompletedOnRealFlowableRuntime+shouldSupportRejectSequentialCountersignToPreviousSignerOnRealFlowableRuntime+shouldSupportReturnSequentialCountersignToPreviousSignerOnRealFlowableRuntime+shouldSupportRejectParallelCountersignToInitiatorOnRealFlowableRuntime+shouldSupportReturnParallelCountersignToInitiatorOnRealFlowableRuntime+shouldSupportAddSignOnSequentialCountersignTaskAndReturnFocusToCountersignAssignee+shouldSupportTakeBackAndWakeUpOnRealFlowableRuntime+shouldSupportReturnAndRejectToInitiatorOnRealFlowableRuntime+shouldFailRejectAndReturnToGatewayNodeOnRealFlowableRuntime test
mvn -q -f backend/pom.xml -Dtest=OrchestratorServiceTest,FlowableProcessRuntimeTraceStoreTest,InMemoryProcessRuntimeTraceStoreTest test
```

### 前端

```bash
pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx src/features/workbench/approval-sheet-helpers.test.ts --reporter=verbose
pnpm -C frontend typecheck
pnpm -C frontend build
```
