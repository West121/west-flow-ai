# PLM v8 真实集成与执行深化实施计划

## 目标

执行 `PLM v8`：

- 真实连接器框架
- 实施协同工作区
- 对象深化 v2
- 驾驶舱 v2

## 阶段拆分

### Task 1：连接器框架底座

交付：

- migration：connector registry / job / ack / dispatch log
- 服务：connector orchestration / dispatcher
- API：查询、重试、回执

验证：

- 生命周期动作会生成 connector job
- connector job 可查询、可重试
- ack 可回写状态

### Task 2：实施协同深化

交付：

- 模板表 / 依赖表 / 证据表 / 验收表
- 实施任务扩展 API
- 关闭前校验增强

验证：

- 可以按模板生成实施任务
- 阻塞依赖生效
- 没有证据或验收未完成时不能关闭

### Task 3：对象深化 v2

交付：

- BOM diff 层级视图数据
- 文档 revision / publication 数据
- 前端对象面板升级

验证：

- 可以看 revision 历史
- 可以看发布状态
- 可以看 BOM 层级差异

### Task 4：驾驶舱 v2

交付：

- connector 维度统计
- 实施执行维度统计
- 阻塞来源分析
- 前端驾驶舱面板升级

验证：

- 管理页能看见外部推进与实施健康度

## 并行安排

### Lane A

负责人：后端连接器框架

### Lane B

负责人：后端实施协同深化

### Lane C

负责人：前端工作区与驾驶舱深化

## 验证

- `pnpm -C frontend typecheck`
- `pnpm -C frontend exec vitest run src/features/plm/pages.test.tsx --reporter=verbose`
- `mvn -q -f backend/pom.xml -Dtest=PLMControllerTest test`
- `mvn -q -f backend/pom.xml -DskipTests compile`
