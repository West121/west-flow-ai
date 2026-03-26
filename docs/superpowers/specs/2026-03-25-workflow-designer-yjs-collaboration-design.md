# 流程设计器 Yjs 协同编辑设计

## 背景

当前流程设计器仍然是单人编辑模型：

- 前端以 `zustand` store 为唯一状态源
- 设计器草稿通过保存接口持久化到后端
- 发布流程仍然走显式发布接口

这套模型适合单人设计，但不支持多人同时编辑同一流程定义，也没有在线成员、远端选中态、远端编辑提示等协作能力。

本设计目标是在不推翻现有草稿/发布链路的前提下，为流程设计器增加基于 `Yjs` 的实时协同编辑能力。

## 目标

本次仅做 `Phase 1`：

- 同一流程定义支持多人同时打开并实时同步
- 实时同步节点、连线、选中态、视口与节点属性修改
- 显示在线成员和远端选中/编辑提示
- 保持现有“保存草稿 / 发布流程”语义不变

本次明确不做：

- 评论面板
- 逐字段锁
- 历史版本合并 UI
- 离线冲突恢复 UI
- 后端正式协同鉴权服务

## 方案选择

### 方案 A：加锁编辑

仅允许同一流程定义同时一个人编辑，其他人只读。

优点：

- 改动小
- 后端实现简单

缺点：

- 不是协同编辑
- 用户体验差
- 无法满足多人并行设计

### 方案 B：Yjs + y-websocket（推荐）

以 `Yjs` 作为协同状态层，设计器仍保留本地 store，store 与 Yjs 双向同步。后端继续保留保存草稿和发布接口，协同本身通过 websocket 房间同步。

优点：

- 冲突合并成熟
- 不需要重写现有设计器渲染层
- 可以最小成本引入 awareness、在线成员、远端选中态

缺点：

- 需要额外协同适配层
- 需要明确 store 与 ydoc 的同步边界

### 方案 C：自研 patch 协议

自建 websocket + patch diff 合并逻辑。

优点：

- 完全自控

缺点：

- 冲突合并和一致性成本高
- 比 Yjs 更重

结论：采用 `方案 B`。

## 总体架构

设计器状态分成三层：

1. React Flow 画布层
2. `zustand` 本地设计器 store
3. `Yjs` 协同文档

其中：

- React Flow 仍然消费 store
- store 仍然是页面唯一可读写入口
- Yjs 成为多人协同的共享底座

也就是说，页面不会直接读写 ydoc，而是通过协同绑定层把 Yjs 与现有 store 串起来。

## 协同房间模型

房间名规则：

- 已有流程定义：`workflow-designer:{processDefinitionId}`
- 新建未保存流程：`workflow-designer:draft:{localDraftId}`

说明：

- 未保存流程只能本地协同，不保证跨浏览器长期稳定
- 一旦保存出 `processDefinitionId`，前端切换到正式房间

## 同步面定义

### 同步对象

同步到 Yjs 的数据：

- `nodes`
- `edges`
- `selectedNodeId`
- `viewport`
- `definitionMeta`
  - `processKey`
  - `processName`
  - `category`
  - `processFormKey`
  - `processFormVersion`
  - `formFields`

### awareness 对象

awareness 只放临时协同态：

- `userId`
- `displayName`
- `color`
- `selectedNodeId`
- `editingNodeId`
- `cursor`

### 不进入 Yjs 的数据

以下仍然只属于本地或后端：

- 查询请求状态
- 保存/发布 mutation 状态
- toast
- 临时表单错误态
- 历史栈过去/未来记录

## store 与 Yjs 的关系

### 单向要求

用户在页面上的一切编辑操作仍然先作用到 store。

然后协同绑定层负责：

- 把本地 store 变更写入 ydoc
- 监听 ydoc 远端变更，再回写 store

### 去环要求

必须区分：

- 本地提交导致的 store 更新
- 远端同步导致的 store 更新

否则会出现：

- store -> ydoc -> store 的循环提交
- 历史栈污染
- 多次重复 layout/fitView

因此需要在绑定层引入：

- `isApplyingRemoteRef`
- `suppressHistory`

远端写回 store 时：

- 不进入 undo/redo 历史
- 不触发本地再次广播

## 组件拆分

前端新增：

- `frontend/src/features/workflow/designer-collab/ydoc.ts`
  - 创建/销毁设计器协同文档
- `frontend/src/features/workflow/designer-collab/provider.ts`
  - websocket provider 封装
- `frontend/src/features/workflow/designer-collab/bindings.ts`
  - store <-> ydoc 双向绑定
- `frontend/src/features/workflow/designer-collab/awareness.ts`
  - 在线成员、远端选中态、编辑态映射
- `frontend/src/features/workflow/designer-collab/types.ts`
  - 协同层共享类型

现有文件会调整：

- `frontend/src/features/workflow/designer/store.ts`
  - 为远端应用增加“无历史提交”入口
- `frontend/src/features/workflow/pages.tsx`
  - 在设计器页面生命周期中挂接协同层
- `frontend/src/features/workflow/designer/designer-layout.tsx`
  - 顶部显示在线成员和协同状态
- `frontend/src/features/workflow/designer/workflow-node.tsx`
  - 节点上显示远端选中/编辑提示

## UX 约束

### 顶部状态

设计器顶部增加：

- 在线成员头像
- 协同状态
  - 已连接
  - 连接中
  - 已断开

### 画布提示

节点上显示：

- 某某正在编辑
- 远端用户选中该节点的彩色边框

### 不做的体验

第一阶段不做：

- 远端光标实时飘动
- 文本输入逐字符协同光标位置

## 保存与发布语义

协同编辑不直接持久化数据库。

只有用户点击：

- 保存草稿
- 发布流程

时，才从当前 store/ydoc 快照导出 DSL，并调用现有接口。

这样可以保证：

- 后端草稿版本模型不变
- 发布链路不变
- 协同只影响编辑体验，不改变流程定义持久化语义

## 风险点

### 风险 1：历史栈污染

若远端变更也进入本地 undo/redo，会导致撤销重做混乱。

处理：

- 远端应用统一走 `replace` 风格写回
- 不进入历史栈

### 风险 2：自动整理/批量布局广播过大

自动整理会同时更新大量节点坐标。

处理：

- 允许作为一次本地批量更新写入 ydoc
- 远端按整体快照应用，不逐个节点散发历史记录

### 风险 3：新建流程房间切换

新建设计未保存时没有正式 `processDefinitionId`。

处理：

- 初次使用本地 draft room
- 保存成功后切换到正式 room 并重建 provider

## 测试策略

### 单元测试

- store 快照写入 ydoc
- 远端 ydoc 变更正确回写 store
- 远端应用不污染历史栈

### 组件测试

- 在线成员状态显示
- 远端选中态展示
- 协同断开提示

### 手工验证

两个浏览器窗口同时打开同一流程：

- 拖动节点
- 新增节点
- 修改节点属性
- 自动整理
- 保存草稿
- 发布流程

以上都应能正常同步，且不破坏现有保存/发布行为。

## 分阶段实施

### Phase 1

- Yjs 文档与 provider
- store 双向绑定
- awareness 在线成员
- 设计器页面接入

### Phase 2

- 正式协同服务鉴权
- 房间会话治理
- 断线重连优化

### Phase 3

- 光标跟随
- 更细粒度编辑提示
- 只读观摩模式
