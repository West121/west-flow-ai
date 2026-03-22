# 代码组件表单注册中心协议

> 状态：Frozen v1
> Owner：表单运行时 owner
> 生效里程碑：M2

## 目标

定义流程表单与节点表单的元数据注册协议。  
本协议只描述“代码组件表单”的注册与运行态消费，不包含任何可视化表单设计能力。

## 非目标

- 不做可视化表单设计器
- 不做拖拽布局
- 不做在线字段设计器
- 不做组件源码托管

## 资源类型

- `PROCESS_FORM`
- `NODE_FORM`

## 表单定义结构

```json
{
  "formKey": "oa_leave_start_form",
  "formVersion": "1.0.0",
  "formName": "OA 请假发起表单",
  "formType": "PROCESS_FORM",
  "componentKey": "oa.leave.start.v1",
  "status": "ACTIVE",
  "fieldDefinitions": [
    {
      "fieldKey": "days",
      "label": "请假天数",
      "valueType": "number",
      "required": true
    }
  ],
  "remark": "代码组件表单元数据"
}
```

## 必填字段

- `formKey`
- `formVersion`
- `formName`
- `formType`
- `componentKey`
- `status`
- `fieldDefinitions[]`

## 字段说明

### formType

- `PROCESS_FORM`：用于流程发起页
- `NODE_FORM`：用于任务处理页中的节点表单

### componentKey

前端代码组件注册键。后端只存元数据，不存表单源码。

### status

- `DRAFT`
- `ACTIVE`
- `DISABLED`

### fieldDefinitions[]

```json
{
  "fieldKey": "days",
  "label": "请假天数",
  "valueType": "number",
  "required": true
}
```

`valueType` 本期允许：

- `string`
- `number`
- `boolean`
- `date`
- `datetime`

## 运行态消费规则

- 流程定义只引用 `formKey/formVersion`
- 节点配置只引用 `nodeFormKey/nodeFormVersion`
- 前端按 `componentKey` 解析真实 React 代码组件
- 后端负责返回表单元数据、保存运行态载荷

## 运行态表单载荷

### 发起流程

`formData` 表示流程表单提交结果：

```json
{
  "days": 3,
  "reason": "事假"
}
```

### 任务处理

`taskFormData` 表示节点表单提交结果：

```json
{
  "approvedDays": 2,
  "opinionTag": "同意"
}
```

## 约束

- 本期表单由前端代码组件维护
- 本期不支持通过后台拖拽设计表单
- 同一个 `formKey + formVersion` 必须唯一
- `componentKey` 必须与前端注册中心中的键保持一致
