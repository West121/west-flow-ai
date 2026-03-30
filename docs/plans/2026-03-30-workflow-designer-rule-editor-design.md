# Workflow Designer Rule Editor Design

## 背景

当前流程设计器的条件分支配置存在三个明显问题：

- 条件类型拆成 `字段比较 / 手写表达式 / 安全公式`，用户心智分裂。
- 连线条件属于“分支规则”，却被拆成多个小表单，难以承载复杂场景。
- 前端没有统一规则编辑体验，后端虽然已有 `Aviator` 公式执行能力，但没有元数据和校验接口给设计器直接消费。

用户已确认新的方向：

- 条件分支只保留一种统一规则模型。
- 规则入口名称改为 `分支规则`。
- 编辑方式升级为 `Monaco` 弹窗编辑器。
- 子表第一版只支持聚合上下文，不支持逐行自由遍历。
- 右侧面板只显示规则摘要与“编辑规则”入口，不再承载复杂条件小表单。

## 目标

为排他/包容分支提供一个统一的 `Monaco 分支规则编辑器`，让用户用同一套受控 DSL 配置简单条件、复杂条件和后端注册函数调用，同时保持流程 DSL 和运行态执行仍由后端 `Aviator` 统一解释。

## 现状判断

### 前端

- 当前前端没有 `math.js` 或类似公式执行引擎。
- [frontend/src/features/workflow/designer/expression-tools.tsx](/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/expression-tools.tsx) 只是输入控件和提示按钮，不具备语义能力。
- [frontend/src/features/workflow/designer/node-config-panel.tsx](/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx) 目前仍围绕旧条件类型设计。

### 后端

- [backend/src/main/java/com/westflow/processruntime/support/WorkflowFormulaEvaluator.java](/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/support/WorkflowFormulaEvaluator.java) 已经是统一公式执行入口。
- 公式执行依赖 `Aviator`，内置函数包括：
  - `ifElse`
  - `contains`
  - `daysBetween`
  - `isBlank`
- [backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java](/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java) 仍校验 `EXPRESSION / FIELD / FORMULA` 三类条件。
- [backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java](/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java) 在出 BPMN 时会把条件最终转成 Flowable 条件表达式。

## 设计结论

### 一、规则模型统一

条件分支统一只保留一种配置形式：

```json
{
  "type": "FORMULA",
  "expression": "days > 3",
  "formulaExpression": "days > 3"
}
```

设计器不再暴露：

- `字段比较`
- `手写表达式`
- `安全公式`

对用户统一叫 `分支规则`。

### 二、交互结构

#### 侧边属性面板

连线属性中只保留：

- 分支名称
- 分支优先级（包容分支）
- 规则摘要
- `编辑规则` 按钮

规则摘要示例：

- `未配置规则`
- `days > 3`
- `ifElse(urgent, true, days >= 5)`

#### 规则编辑弹窗

弹窗采用三段式布局：

1. 上方：`Monaco` 编辑器
2. 左下：上下文
3. 右下：规则构件

#### 左下“上下文”

分组如下：

- 主表字段
  - 字段名
  - 编码
  - 类型
- 子表聚合
  - `items.count`
  - `items.sum("amount")`
  - 后续可扩展其他受控聚合
- 流程上下文
  - `processKey`
  - `processName`
  - `initiatorId`
  - `initiatorDeptId`
- 节点上下文
  - `nodeId`
  - `nodeType`
  - `nodeName`
- 高级上下文
  - `instanceId`
  - `taskId`

第一版子表只开放聚合，不开放逐行 DSL。

#### 右下“规则构件”

分组如下：

- 逻辑运算
  - `&&`
  - `||`
  - `!`
- 比较运算
  - `==`
  - `!=`
  - `>`
  - `>=`
  - `<`
  - `<=`
- 字符串函数
  - `contains`
  - 判空模板
- 日期函数
  - `daysBetween`
- 条件函数
  - `ifElse`
- 后端注册公式
  - 来自后端元数据接口

点击构件后插入 Monaco 当前位置。

### 三、Monaco 编辑器策略

Monaco 只负责编辑体验，不负责执行。

第一版提供：

- 语法高亮
- 自动补全
- Hover 提示
- Snippet 插入
- 错误标记
- 规则试算结果反馈

语言不必完整自定义一套 parser，第一版采用：

- 轻量 DSL token 定义
- 通过后端校验接口返回错误位置
- 前端用 Monaco markers 显示错误

### 四、后端接口

新增两个接口：

#### 1. 规则元数据接口

用途：给设计器拉取字段、上下文和函数清单。

返回内容：

- 主表字段
- 子表聚合能力
- 流程上下文变量
- 节点上下文变量
- 后端注册函数
- 后端注册业务规则

#### 2. 规则校验接口

用途：在设计器中即时校验 DSL。

输入：

- 表达式
- 流程表单字段定义
- 选中的连线/节点上下文

输出：

- 是否合法
- 错误消息
- 错误位置
- 解析后的摘要
- 可选试算结果

### 五、后端执行边界

后端继续以 `Aviator` 为统一执行引擎。

这意味着：

- 前端不引入真正的公式执行库
- 运行态和设计态不会出现双引擎偏差
- 公式新增函数时，只需要在后端注册并同步元数据

### 六、DSL 兼容策略

为了避免一次性破坏现有流程定义：

- 现有 `FIELD / EXPRESSION / FORMULA` 仍继续被后端识别
- 设计器新编辑保存时统一写回 `FORMULA`
- 旧定义进入编辑器时，前端会先映射成统一的 `FORMULA` 文本摘要

这样可以实现：

- 新 UI 一套模型
- 旧 DSL 平滑兼容

## 技术选型

### 前端

- `@monaco-editor/react`
- 现有 `Dialog` / `Tabs` / `Button` / `Card`
- Monaco marker + completion provider

### 后端

- 继续使用 `Aviator`
- Spring Boot controller + service
- 不引入第二套公式执行引擎

## 风险与控制

### 风险 1：前后端规则语义不一致

控制：

- 所有校验和执行都以后端为准
- 前端只做展示和辅助编辑

### 风险 2：旧 DSL 迁移不完整

控制：

- 保持后端兼容旧类型
- 设计器侧只做读时映射和写时统一

### 风险 3：Monaco 过重

控制：

- 第一版只在弹窗中懒加载
- 不放在侧边面板常驻渲染

## 验收标准

- 连线配置不再出现三种条件类型
- 连线属性只显示规则摘要和编辑入口
- Monaco 弹窗可编辑、补全、插入构件
- 规则切换连线时不会出现空值或非法 option
- 规则校验错误能标记到编辑器
- 旧流程定义可打开并编辑
- 前端测试、类型检查、lint 通过
- 后端编译和接口测试通过
