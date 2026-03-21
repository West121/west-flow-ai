# 前端通用表格查询字段协议

> 状态：Frozen v1
> Owner：前端 CRUD owner
> 生效里程碑：M0

## 目标

统一前端列表页查询态、路由搜索参数、表格状态与服务端分页协议之间的映射方式。  
该协议直接服务于独立 CRUD 页面体验，确保“进入编辑页再返回列表页”时保留查询态。

## 查询状态结构

```ts
type ListQueryState = {
  page: number
  pageSize: number
  keyword: string
  filters: Array<{
    field: string
    operator:
      | 'eq'
      | 'ne'
      | 'in'
      | 'not_in'
      | 'gt'
      | 'gte'
      | 'lt'
      | 'lte'
      | 'between'
      | 'like'
      | 'prefix_like'
      | 'suffix_like'
      | 'is_null'
      | 'is_not_null'
    value: unknown
  }>
  sorts: Array<{
    field: string
    direction: 'asc' | 'desc'
  }>
  groups: Array<{
    field: string
  }>
  columns: Array<{
    key: string
    visible: boolean
  }>
}
```

## 路由搜索参数规则

每个主列表页必须把以下状态持久化到 TanStack Router search：

- `page`
- `pageSize`
- `keyword`
- `filters`
- `sorts`
- `groups`

说明：

- `columns` 可先放本地存储，不强制放 URL
- 返回主列表时必须恢复上述 search 状态

## 页面范式约束

所有 CRUD 功能采用独立页面：

- `/system/users/list`
- `/system/users/create`
- `/system/users/:id`
- `/system/users/:id/edit`

禁止：

- 一个页面通过 tab 同时承载多个不同 CRUD 子域
- 用抽屉代替标准编辑页

允许：

- 选人、确认、审批意见、导出选项等轻量操作使用弹层

## 与服务端协议映射

前端请求适配器必须把 `ListQueryState` 映射为 [pagination.md](/Users/west/dev/code/west/west-flow-ai/docs/contracts/pagination.md) 定义的请求体。

示例：

```ts
const requestBody = {
  page: state.page,
  pageSize: state.pageSize,
  keyword: state.keyword,
  filters: state.filters,
  sorts: state.sorts,
  groups: state.groups,
}
```

## 默认交互规则

- 默认 `page=1`
- 默认 `pageSize=20`
- 关键字搜索支持防抖
- 修改筛选条件后自动重置 `page=1`
- 修改 `pageSize` 后自动重置 `page=1`
- 列表页进入详情、编辑、创建后，再返回必须恢复原查询态

## 通用表格能力

所有主列表页必须支持：

- 分页
- 模糊查询
- 精确筛选
- 分组
- 排序
- 列显隐
- 批量选择
- 空状态
- 骨架屏
- 权限态控制

## 基线目录约束

基于 `shadcn-admin` 二开，M0 默认公共表格能力落在以下位置：

- `frontend/src/components/data-table/*`
- `frontend/src/features/shared/table/*`
- `frontend/src/routes/.../list.tsx`

如需变更目录，必须先更新本协议与 M0 实施计划。
