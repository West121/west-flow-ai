# React Flow DSL 协议

> 状态：Frozen v1
> Owner：流程定义 owner
> 生效里程碑：M0

## 目标

定义前端 React Flow 设计态 DSL 与后端流程定义输入模型。  
M0 范围仅覆盖最小可用流程能力：开始、审批、抄送、条件、并行、结束。

## DSL 根结构

```json
{
  "dslVersion": "1.0.0",
  "processKey": "oa_leave",
  "processName": "请假审批",
  "category": "OA",
  "processFormKey": "oa_leave_start_form",
  "processFormVersion": "1.0.0",
  "formFields": [
    {
      "fieldKey": "days",
      "label": "请假天数",
      "valueType": "number"
    }
  ],
  "settings": {
    "allowWithdraw": true,
    "allowUrge": true,
    "allowTransfer": true
  },
  "nodes": [],
  "edges": []
}
```

## 节点公共结构

```json
{
  "id": "approve_1",
  "type": "approver",
  "name": "部门负责人审批",
  "position": {
    "x": 420,
    "y": 180
  },
  "config": {},
  "ui": {
    "width": 240,
    "height": 88
  }
}
```

## M0 支持的节点类型

- `start`
- `approver`
- `cc`
- `condition`
- `parallel_split`
- `parallel_join`
- `end`

## 流程默认表单

根结构中的 `processFormKey / processFormVersion` 表示流程默认表单。

约束：

- 发起页始终使用流程默认表单
- 审批节点如未配置覆盖表单，任务处理页回退使用流程默认表单
- 本期不建设独立流程表单管理模块，表单由前端代码组件维护

`formFields[]` 用于条件表达式、字段绑定、规则计算所需的业务字段描述，不代表可视化表单设计器字段协议。

## 节点配置

### start

```json
{
  "initiatorEditable": true
}
```

### approver

```json
{
  "assignment": {
    "mode": "USER",
    "userIds": ["usr_002"],
    "roleCodes": [],
    "departmentRef": "",
    "formFieldKey": ""
  },
  "nodeFormKey": "oa_leave_approve_form",
  "nodeFormVersion": "1.0.0",
  "fieldBindings": [
    {
      "source": "PROCESS_FORM",
      "sourceFieldKey": "days",
      "targetFieldKey": "approvedDays"
    }
  ],
  "approvalPolicy": {
    "type": "SEQUENTIAL",
    "voteThreshold": null
  },
  "operations": ["APPROVE", "REJECT", "RETURN"],
  "commentRequired": false
}
```

`assignment.mode` 在 M0 允许：

- `USER`
- `ROLE`
- `DEPT_LEADER`
- `INITIATOR_SELF`
- `FORM_FIELD`

`approvalPolicy.type` 在 M0 允许：

- `ANY`
- `ALL`
- `SEQUENTIAL`

### cc

```json
{
  "targets": {
    "mode": "USER",
    "userIds": ["usr_003"]
  },
  "readRequired": false
}
```

### condition

```json
{
  "defaultEdgeId": "edge_default",
  "expressionMode": "FIELD_COMPARE",
  "expressionFieldKey": "days"
}
```

说明：

- 条件表达式定义在边上，不定义在节点上
- `defaultEdgeId` 表示兜底路径
- `expressionMode` 本期允许：
  - `EXPRESSION`
  - `FIELD_COMPARE`
- `expressionFieldKey` 用于记录字段比较模式下引用的字段键

## M2 表单字段补充

- 根结构使用 `processFormKey`
- 根结构使用 `processFormVersion`
- 根结构保留 `formFields[]`
- 审批节点新增 `nodeFormKey`
- 审批节点新增 `nodeFormVersion`
- 审批节点新增 `fieldBindings[]`

运行态表单优先级：

- `nodeFormKey/nodeFormVersion`
- `processFormKey/processFormVersion`

`fieldBindings[].source` 本期允许：

- `PROCESS_FORM`
- `NODE_FORM`

### parallel_split / parallel_join

M0 不额外定义复杂配置，仅通过拓扑结构表达。

### end

无配置对象，`config` 置空对象即可。

## 边结构

```json
{
  "id": "edge_01",
  "source": "condition_1",
  "target": "approve_2",
  "priority": 10,
  "label": "金额 > 1000",
  "condition": {
    "type": "AVIATOR",
    "expression": "form.amount > 1000"
  }
}
```

说明：

- 普通边：`condition` 可为空
- 条件边：`condition` 必填
- `priority` 数字越小优先级越高

## 校验规则

- 必须且只能有一个 `start`
- 必须至少有一个 `end`
- 所有节点必须可达
- 不允许孤立节点
- `condition` 节点至少有两条出边
- `parallel_split` 与 `parallel_join` 必须成对出现
- `approver` 节点必须配置 `assignment`
- 所有节点 `id` 必须稳定，禁止发布时重排造成历史版本 diff 混乱

## 前后端职责

### 前端

- 负责 DSL 编辑、画布交互、基础本地校验
- 保存与发布时提交本协议 JSON

### 后端

- 负责最终校验
- 负责 DSL -> BPMN 转换
- 负责存储 DSL 原稿、发布快照、版本映射

## 版本规则

- `dslVersion` 由协议版本控制，不等于流程版本
- 流程定义版本由后端发布时生成
- 旧版本 DSL 必须可回放、可查看，不要求可编辑
