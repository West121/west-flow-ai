# 审批平台全场景测试计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 覆盖当前中国式审批平台的全部核心审批动作、协同模式、签章、SLA、批量处理和复杂流程结构，形成一轮可回归、可复用、可追踪的测试闭环。

**Architecture:** 先按测试域拆成运行态动作、协同审批、复杂流程结构、签章与 SLA、列表与详情一致性五条主线，再按“单任务 -> 批量 -> 复杂实例 -> 异常/回退”顺序执行。每条用例要求同时验证后端接口结果、工作台 UI 状态、审批详情时间轴/流程图投影三层一致。

**Tech Stack:** Spring Boot, Flowable, React, Vitest, MockMvc, PostgreSQL(local), 浏览器人工回归

---

## 范围

本计划覆盖：

- 单任务审批动作
- 批量任务动作
- 督办 / 会办 / 阅办 / 传阅
- 会签 / 子流程 / 动态追加 / 包容分支 / 时间旅行 / 终止
- 电子签章
- SLA 超时提醒与升级
- 列表、详情、轨迹、时间轴、流程图一致性
- 任职上下文对候选任务、审批动作、发起身份的影响

本计划暂不覆盖：

- 跨系统外部联调
- 正式短信/邮件/企业微信通道送达验证
- 法务级 PDF 落章版式校验
- 高并发压测

## 测试环境

### 运行环境

- 前端：[http://127.0.0.1:5173/](http://127.0.0.1:5173/)
- 后端：[http://127.0.0.1:8080](http://127.0.0.1:8080)
- 本地数据库：Docker PostgreSQL
- 当前 profile：`local`

### 基础账号

- `admin / admin123`
- `zhangsan / 123456`
- `lisi / 123456`
- `wangwu / 123456`

### 基础组织假设

- 至少存在主职 / 兼职任职上下文切换
- 至少存在 `role_manager`、`role_hr` 等审批角色
- 至少存在请假、OA、系统管理相关已发布流程

## 通过标准

- 每个动作至少验证：按钮可见性、执行结果、详情页轨迹
- 每个复杂实例至少验证：列表状态、详情状态、流程图、时间轴
- 每个批量动作至少验证：部分成功/全部成功口径
- 每个异常用例至少验证：错误提示中文化且不出现 `SYS.INTERNAL_ERROR`

## 测试主线

## Chunk 1: 基础动作链

### Task 1: 单任务可用性矩阵

**Files:**
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/response/TaskActionAvailabilityResponse.java`
- Reference: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.tsx`
- Record: `docs/superpowers/plans/2026-03-29-approval-platform-test-plan.md`

- [ ] **Step 1: 验证待认领任务动作**

手工验证：
- 使用 `zhangsan` 打开待办列表
- 找到 `PENDING_CLAIM` 任务
- 预期只显示 `认领任务`
- 预期不显示 `同意 / 驳回 / 退回 / 转办 / 委派 / 加签 / 减签`

- [ ] **Step 2: 验证已认领任务基础动作**

手工验证：
- 认领后进入详情
- 预期根据当前任务语义出现：
  - `同意`
  - `驳回`
  - `退回`
  - `转办`
  - `委派`
  - `加签`
  - `减签`
  - `催办`
  - `拿回`（仅满足条件时）
  - `电子签章`（要求签章时）

- [ ] **Step 3: 验证动作执行后状态一致性**

每个动作至少检查：
- 列表状态变化
- 审批详情顶部状态变化
- 时间轴新增对应事件
- 流程图当前焦点变化

### Task 2: 单任务动作明细

**Files:**
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/controller/ProcessRuntimeController.java`
- Reference: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/approval-sheet-helpers.ts`

- [ ] **Step 1: 同意**
- [ ] **Step 2: 驳回到上一步**
- [ ] **Step 3: 驳回到指定节点**
- [ ] **Step 4: 退回发起人**
- [ ] **Step 5: 退回到任意已走人工节点**
- [ ] **Step 6: 转办**
- [ ] **Step 7: 委派**
- [ ] **Step 8: 加签**
- [ ] **Step 9: 减签**
- [ ] **Step 10: 催办**
- [ ] **Step 11: 撤销**
- [ ] **Step 12: 拿回**
- [ ] **Step 13: 唤醒**
- [ ] **Step 14: 跳转**
- [ ] **Step 15: 离职转办**

每一步都要记录：
- 前置条件
- 谁执行
- 预期事件名
- 预期实例状态
- 预期当前节点

## Chunk 2: 批量动作链

### Task 3: 批量认领 / 已读 / 同意 / 驳回

**Files:**
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/request/BatchTaskActionRequest.java`
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/response/BatchTaskActionResponse.java`

- [ ] **Step 1: 准备至少 3 条同类任务**
- [ ] **Step 2: 批量认领**
- [ ] **Step 3: 批量已读**
- [ ] **Step 4: 批量同意**
- [ ] **Step 5: 批量驳回**
- [ ] **Step 6: 验证部分失败口径**

验证点：
- 成功数 / 失败数
- 失败任务明细
- 列表已选行状态清空
- 审批详情可单独打开核对

## Chunk 3: 协同审批模式

### Task 4: 督办 / 会办 / 阅办 / 传阅

**Files:**
- Reference: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/collaboration.ts`
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Reference: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.tsx`

- [ ] **Step 1: 设计器配置四类协同节点**
- [ ] **Step 2: 发起实例并进入相应节点**
- [ ] **Step 3: 分别执行已阅/协同动作**
- [ ] **Step 4: 验证详情 read 按钮文案**
- [ ] **Step 5: 验证时间轴事件名称**

预期文案：
- 督办已阅
- 会办已阅
- 阅办已阅
- 传阅已阅

## Chunk 4: 复杂流程结构

### Task 5: 会签

- [ ] **Step 1: 串签**
- [ ] **Step 2: 并签**
- [ ] **Step 3: 或签**
- [ ] **Step 4: 票签**

每种都要验证：
- 任务组进度
- 详情 countersign summary
- 审批完成条件

### Task 6: 子流程 / 动态追加 / 包容分支

- [ ] **Step 1: 子流程发起与父流程等待**
- [ ] **Step 2: 父流程确认恢复**
- [ ] **Step 3: 动态追加人工节点**
- [ ] **Step 4: 动态追加子流程**
- [ ] **Step 5: 包容分支多命中**

验证点：
- `links / append-links / task-groups` 接口
- 审批详情流程图高亮
- 时间轴事件和结构链接一致

### Task 7: 时间旅行 / 终止

- [ ] **Step 1: jump 到指定节点**
- [ ] **Step 2: terminate 实例**
- [ ] **Step 3: wake-up 挂起实例**

## Chunk 5: 签章与 SLA

### Task 8: 电子签章

**Files:**
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/signature/service/TaskSignatureService.java`

- [ ] **Step 1: 配置签章要求节点**
- [ ] **Step 2: 在工作台执行签章**
- [ ] **Step 3: 验证详情显示签章状态**
- [ ] **Step 4: 验证时间轴记录签章事件**

### Task 9: SLA 超时提醒与升级

**Files:**
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/orchestrator/service/FlowableOrchestratorRuntimeBridge.java`
- Reference: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStore.java`

- [ ] **Step 1: 设计器开启 reminderPolicy**
- [ ] **Step 2: 设计器开启 escalationPolicy**
- [ ] **Step 3: 触发 due target 扫描**
- [ ] **Step 4: 验证 trace 出现 `ESCALATION`**
- [ ] **Step 5: 验证详情时间轴和自动化区块可见**

## Chunk 6: 任职上下文与权限

### Task 10: 当前任职影响候选任务

- [ ] **Step 1: 张三切主职**
- [ ] **Step 2: 查待办**
- [ ] **Step 3: 切兼职**
- [ ] **Step 4: 再查待办**
- [ ] **Step 5: 验证候选任务范围变化**

### Task 11: 当前任职影响发起身份

- [ ] **Step 1: 主职发起请假**
- [ ] **Step 2: 兼职发起请假**
- [ ] **Step 3: 验证列表显示发起部门 / 发起岗位**
- [ ] **Step 4: 验证详情显示一致**

## Chunk 7: 列表 / 详情 / 图形一致性

### Task 12: 审批详情一致性

- [ ] **Step 1: 列表状态与详情状态一致**
- [ ] **Step 2: 时间轴顺序一致**
- [ ] **Step 3: 流程图高亮一致**
- [ ] **Step 4: 回顾播放从开始到结束**
- [ ] **Step 5: 加签 / 退回 / 已撤销场景不串色、不串任务**

### Task 13: 轨迹字段一致性

- [ ] **Step 1: taskSemanticMode 一致**
- [ ] **Step 2: targetStrategy / targetNodeId 一致**
- [ ] **Step 3: actingMode / delegatedBy / handoverFrom 一致**
- [ ] **Step 4: slaMetadata 一致**

## Chunk 8: 异常与边界

### Task 14: 不可执行动作保护

- [ ] **Step 1: 未认领任务直接 complete 应失败**
- [ ] **Step 2: 已完成任务重复处理应失败**
- [ ] **Step 3: 不存在目标节点的退回/驳回应失败**
- [ ] **Step 4: 无权限用户打开详情应受限**

### Task 15: UI 与错误提示

- [ ] **Step 1: 错误提示中文化**
- [ ] **Step 2: 不出现原始栈或 `SYS.INTERNAL_ERROR` 泛滥**
- [ ] **Step 3: 批量动作失败能看明细**
- [ ] **Step 4: 空态与无权限态文案清晰**

## 执行顺序建议

1. 基础动作链
2. 批量动作链
3. 协同审批模式
4. 复杂流程结构
5. 签章与 SLA
6. 任职上下文与权限
7. 一致性与异常链路

## 输出物

执行本计划时，最终应产出：

- 通过 / 失败用例清单
- 缺陷列表（按严重级别）
- 需要补自动化的场景建议
- 最终验收结论

Plan complete and saved to `docs/superpowers/plans/2026-03-29-approval-platform-test-plan.md`. Ready to execute?
