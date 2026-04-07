# WeApp Data Fetching Notes

## 页面分层

### 1. 显式请求页

适用于：
- tab 首页
- 工作台
- 审批详情
- 任何首屏必须稳定出数据的核心页

规则：
- 由页面自己在 `useEffect + useDidShow` 中显式触发请求
- 页面自己维护 `loading / error / data`
- 不把首屏首次请求完全依赖在 `React Query enabled/refetch` 上

当前页面：
- `pages/workbench/index`
- `pages/approval/detail`

### 2. React Query 缓存页

适用于：
- 会话页
- 列表缓存页
- 二级功能页
- 更偏“缓存、失效、同步”的场景

规则：
- 可以继续使用 `useQuery / useMutation`
- 但不要承担小程序 tab 首屏首次加载的唯一触发器职责

当前页面：
- `pages/ai/index`

## 原因

在 Taro 小程序 tab 页和页面缓存场景下，`enabled + refetch + useDidShow`
的组合存在执行时机不稳定的问题。工作台排查中已经出现：

- `ready=true`
- `didShow` 正常
- `refetch()` 被调用
- 但 `queryFn` 未执行

因此核心首页和审批详情改为显式请求，优先保证可预测性。
