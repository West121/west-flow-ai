# M2 Code Form Runtime Closure Implementation Plan

> 状态：Superseded on 2026-03-22
> 请勿继续执行本文件中的旧方案。

本计划原先假设需要建设：

- 独立流程表单管理页
- 独立节点表单管理页
- 后端表单元数据 CRUD

上述方向已经被新的流程管理重规划明确废弃，原因如下：

- 与“表单是写好的页面/代码组件”约束冲突
- 会额外引入一套并不必要的后台管理模型
- 会让设计器配置、业务发起、运行态详情三者边界再次混乱

替代文件：

- 设计文档：`docs/superpowers/specs/2026-03-22-workflow-management-replan.md`
- 实施计划：`docs/superpowers/plans/2026-03-22-workflow-management-replan-plan.md`

新口径要求：

- 流程默认表单与节点覆盖表单都在流程设计器属性面板中配置
- 运行态详情统一收口到审批单详情页
- `OA` 与 `流程管理` 双入口复用同一套流程中心

任何后续多 Agent 开发都必须以新的重规划文档为准。
