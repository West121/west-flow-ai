# 分页查询协议

> 状态：Frozen v1
> Owner：后端基础设施 owner
> 生效里程碑：M0

## 目标

统一所有主列表页的服务端分页、模糊查询、精确筛选、排序、分组协议。  
默认约定：主列表查询统一使用 `POST /api/v1/{resource}/page`，不用零散的 GET query 参数承载复杂查询。

## 请求结构

```json
{
  "page": 1,
  "pageSize": 20,
  "keyword": "张三",
  "filters": [
    {
      "field": "status",
      "operator": "eq",
      "value": "ENABLED"
    },
    {
      "field": "createdAt",
      "operator": "between",
      "value": ["2026-03-01 00:00:00", "2026-03-31 23:59:59"]
    }
  ],
  "sorts": [
    {
      "field": "createdAt",
      "direction": "desc"
    }
  ],
  "groups": [
    {
      "field": "departmentName"
    }
  ]
}
```

## 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | `number` | 是 | 从 `1` 开始 |
| `pageSize` | `number` | 是 | 默认 `20`，允许 `10/20/50/100` |
| `keyword` | `string` | 否 | 模糊查询关键字，由后端决定命中哪些字段 |
| `filters` | `FilterItem[]` | 否 | 精确或范围筛选 |
| `sorts` | `SortItem[]` | 否 | 多字段排序 |
| `groups` | `GroupItem[]` | 否 | 分组字段集合，M0 先支持单字段分组，协议保留数组 |

### FilterItem

```json
{
  "field": "status",
  "operator": "eq",
  "value": "ENABLED"
}
```

支持的 `operator`：

- `eq`
- `ne`
- `in`
- `not_in`
- `gt`
- `gte`
- `lt`
- `lte`
- `between`
- `like`
- `prefix_like`
- `suffix_like`
- `is_null`
- `is_not_null`

说明：

- `keyword` 用于统一模糊查询。
- `filters[].operator=like` 仅用于特定高级筛选项，不替代 `keyword`。
- `between` 的 `value` 必须为长度为 `2` 的数组。

### SortItem

```json
{
  "field": "createdAt",
  "direction": "desc"
}
```

说明：

- `direction` 仅允许 `asc` 或 `desc`
- 无显式排序时，默认按 `createdAt desc`

### GroupItem

```json
{
  "field": "departmentName"
}
```

说明：

- M0 仅要求返回分组后的普通分页结果与当前分组字段，不要求复杂树形汇总

## 响应结构

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 128,
    "pages": 7,
    "records": [
      {
        "id": "usr_001",
        "name": "张三"
      }
    ],
    "groups": [
      {
        "field": "departmentName",
        "value": "财务部"
      }
    ]
  },
  "requestId": "req_20260321_0001"
}
```

## 响应约束

- `records` 必须始终返回数组，空结果返回 `[]`
- `total`、`pages` 必须始终返回数字
- 空结果不视为异常，返回 `code=OK`
- 所有列表页接口必须支持 `keyword`
- 所有列表页接口必须支持至少一个默认排序字段

## 前后端约束

- 前端路由搜索参数负责保存查询态
- 前端请求适配器负责把路由查询态转换为本协议请求体
- 后端禁止每个接口自定义不兼容的分页字段命名
- 后端如不支持某个筛选字段，必须返回明确字段级错误

## 例外规则

- 简单下拉远程搜索可使用 `GET /options`
- 主列表、审核列表、历史列表、日志列表一律遵守本协议
