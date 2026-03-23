# PLM 助手工具协议

> 状态：Frozen v1
> Owner：PLM owner + AI Gateway owner
> 生效里程碑：Phase 6C

## 1. 目标

定义 PLM 助手在 AI Copilot 中可调用的业务工具范围、能力边界与安全限制。

## 2. 本阶段支持的业务域

仅支持以下 3 类业务：

- `ECR`
- `ECO`
- `MATERIAL_MASTER`

对应业务类型建议统一为：

- `PLM_ECR`
- `PLM_ECO`
- `PLM_MATERIAL_MASTER`

## 3. 可调用工具范围

本阶段 PLM 助手仅开放读能力和受控解释能力。

### 3.1 列表查询

工具示例：

- `plm.ecr.list`
- `plm.eco.list`
- `plm.material-master.list`

支持：

- 分页
- 模糊查询
- 状态筛选
- 时间范围筛选

### 3.2 详情查询

工具示例：

- `plm.ecr.detail`
- `plm.eco.detail`
- `plm.material-master.detail`

支持：

- 业务详情
- 发起人
- 当前状态
- 关联审批状态

### 3.3 审批状态查询

工具示例：

- `plm.ecr.approval-status`
- `plm.eco.approval-status`
- `plm.material-master.approval-status`

支持：

- 当前实例状态
- 当前节点
- 待办人
- 最近审批动作

### 3.4 业务单与实例联查

工具示例：

- `plm.ecr.linked-approval`
- `plm.eco.linked-approval`
- `plm.material-master.linked-approval`

支持：

- 业务详情跳审批单
- 审批单回业务详情所需路径信息

## 4. 明确限制

### 4.1 本阶段不允许 AI 直接写业务主数据

AI 不得直接通过 PLM 助手工具：

- 修改 ECR 业务字段
- 修改 ECO 业务字段
- 修改物料主数据变更业务字段
- 绕过业务页面直接改业务表

### 4.2 如需写操作，必须走正式业务接口

未来若开放写操作，必须满足：

- 工具定义为 `WRITE`
- 进入确认流
- 走正式业务服务层
- 写审计

## 5. 返回结果要求

PLM 助手返回结果必须能映射到 AI 富响应块：

- 列表查询：可映射为 `result` 或 `stats`
- 详情查询：可映射为 `result`
- 审批联查：可映射为 `trace`

## 6. 与 Lane B 的关系

本协议依赖 Lane B 补齐：

- 业务列表页
- 业务详情页
- 审批单双向联查

只有这些业务接口真实可用后，PLM 助手工具才算真正闭环。

## 7. 非目标

本协议不定义：

- PLM 全量业务域
- BOM、图纸、版本结构管理
- 真实 CAD / PDM 外部系统对接
