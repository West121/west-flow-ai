# 代码组件表单注册与选择约定

> 状态：Frozen v2
> Owner：流程定义 owner + 运行态 owner
> 生效里程碑：M1-M2

## 目标

定义“写好的前端代码表单组件”如何在流程设计器与运行态中被选择和消费。  
本协议不定义任何后台 CRUD 表单管理能力。

## 非目标

- 不做可视化表单设计器
- 不做拖拽布局
- 不做在线字段设计器
- 不做组件源码托管
- 不做独立“流程表单管理”“节点表单管理”菜单
- 不做后端表单元数据 CRUD

## 前端注册项结构

```ts
type WorkflowFormRegistryItem = {
  businessType: string
  formKey: string
  formVersion: string
  formType: 'PROCESS_DEFAULT' | 'NODE_OVERRIDE'
  componentKey: string
  supportedModes: Array<'start' | 'task' | 'detail'>
}
```

## 字段说明

### businessType

用于区分业务域，例如：

- `OA_LEAVE`
- `OA_EXPENSE`
- `OA_COMMON_REQUEST`

### formType

- `PROCESS_DEFAULT`：流程默认表单
- `NODE_OVERRIDE`：节点覆盖表单

### supportedModes

至少允许：

- `start`
- `task`
- `detail`

### componentKey

前端代码组件注册键，用于映射真实 React 组件。

## 设计器消费规则

- 流程定义根级只引用 `processFormKey/processFormVersion`
- 节点配置只引用 `nodeFormKey/nodeFormVersion`
- 表单选择在流程设计器属性面板中完成
- 不允许通过独立后台菜单管理表单元数据

## 运行态消费规则

- 发起页始终使用流程默认表单
- 节点处理页优先使用节点覆盖表单
- 节点未配置覆盖表单时，回退到流程默认表单
- 审批单详情页应同时保留业务正文展示区

## 业务入口规则

- `OA` 下的业务入口直接进入业务发起页
- `流程中心 > 发起流程` 先选业务入口，再跳同一业务发起页
- 不做“先选流程模板再填表”的通用空壳页

## 约束

- 本期表单由前端代码组件维护
- 本期不支持通过后台拖拽设计表单
- 本期不支持独立后端表单管理接口
- 同一个 `formKey + formVersion` 必须唯一
- `componentKey` 必须与前端注册中心中的键保持一致
