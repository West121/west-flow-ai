# PLM v5 对象深度化实施计划

## 目标

在 `PLM v4` 之后，优先补齐企业级 PLM 最大缺口：

- BOM 深度对象模型
- 图纸 / 文档 / PDM 内部对象能力
- 配置基线与版本快照

## 实施范围

### 后端

1. 新增 BOM 结构表
   - `plm_bom_node`
   - `plm_bom_edge`
   - `plm_bom_baseline`

2. 新增文档 / 图纸对象扩展表
   - `plm_document_file`
   - `plm_document_revision`
   - `plm_object_attachment`

3. 新增配置基线表
   - `plm_config_baseline`
   - `plm_config_snapshot`

4. 新增服务
   - `PlmBomService`
   - `PlmDocumentService`
   - `PlmBaselineService`

5. 新增 API
   - BOM 树
   - 文档 / 图纸版本列表
   - 对象附件列表
   - 配置基线 / 快照详情

### 前端

1. 详情页新增区块
   - BOM 视图
   - 文档 / 图纸视图
   - 基线 / 快照视图

2. 新增组件
   - `plm-bom-tree-panel`
   - `plm-document-revision-panel`
   - `plm-baseline-panel`

3. 现有对象清单保留，但升级成导航入口

### AI

1. 补充对象深度摘要
   - BOM 影响范围
   - 图纸版本摘要
   - 基线差异摘要

## 风险

1. 数据结构复杂度明显上升
2. 前端详情页易继续膨胀
3. 测试数据构造成本提高

## 并行拆分

### Lane A：后端数据模型

- 迁移脚本
- Mapper / Record
- Service / Controller

### Lane B：前端详情工作区

- API contract
- 详情区块组件
- 页面接入

### Lane C：AI 与测试

- PLM 对象深度 AI 摘要
- 后端控制器测试
- 前端页面与 API 测试

## 完成标准

1. 一张 ECR / ECO / 物料变更单可以查看 BOM / 图纸 / 文档深度对象
2. 可以看到对象版本与基线快照
3. AI 能回答“影响哪些 BOM / 图纸 / 文档”
4. 前后端测试通过
