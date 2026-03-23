# 2026-03-23 菜单与 Sidebar 体系重构实施计划

## 范围冻结

只做：

- 动态 sidebar 查询展示
- 菜单树接口
- 菜单管理树形表格
- 初始化菜单 SQL 重排
- 子页面改成权限型菜单且不展示

不做：

- 高级流程能力
- AI 深化
- OA/PLM 业务深化

## Chunk 1: 菜单接口与模型

### Task 1: 扩展菜单类型与树查询

**Files**

- Modify: `backend/src/main/java/com/westflow/system/menu/service/SystemMenuService.java`
- Modify: `backend/src/main/java/com/westflow/system/menu/mapper/SystemMenuMapper.java`
- Modify: `backend/src/main/java/com/westflow/system/menu/api/SystemMenuController.java`
- Modify: `backend/src/main/java/com/westflow/system/menu/api/SaveSystemMenuRequest.java`
- Modify: `backend/src/main/java/com/westflow/system/menu/api/SystemMenuFormOptionsResponse.java`
- Modify: `backend/src/main/java/com/westflow/system/menu/api/SystemMenuListItemResponse.java`
- Create: `backend/src/main/java/com/westflow/system/menu/api/SystemMenuTreeNodeResponse.java`
- Test: `backend/src/test/java/com/westflow/system/menu/api/SystemMenuControllerTest.java`

**Steps**

1. 增加 `PERMISSION` 菜单类型支持
2. 新增 `tree` 和 `sidebar-tree` 接口
3. 菜单树按 `parent_menu_id + sort_order` 组装
4. `sidebar-tree` 只返回当前用户可见的 `DIRECTORY + MENU`
5. 运行后端菜单接口测试

## Chunk 2: 初始化 SQL 重排

### Task 2: 重排顶级目录与补齐权限型菜单

**Files**

- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/com/westflow/system/menu/MenuSeedSmokeTest.java`

**Steps**

1. 调整顶级目录顺序
2. 拆出“组织管理”为顶级目录
3. 补齐流程中心、流程管理、OA、PLM 菜单
4. 把 create/edit/detail/execute 等子页面改成 `PERMISSION + visible=false`
5. 增加种子菜单结构 smoke test

## Chunk 3: 动态 Sidebar

### Task 3: 前端按查询结果渲染左侧菜单

**Files**

- Modify: `frontend/src/components/layout/app-sidebar.tsx`
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`
- Create: `frontend/src/lib/api/sidebar-menus.ts`
- Create: `frontend/src/components/layout/menu-icon-registry.tsx`
- Test: `frontend/src/components/layout/app-sidebar.test.tsx`

**Steps**

1. 删除前端静态菜单树依赖
2. 查询当前用户 sidebar 菜单树
3. 只保留图标映射和团队展示
4. 隐藏 `PERMISSION` 节点
5. 运行前端 sidebar 测试

## Chunk 4: 菜单管理树形表格

### Task 4: 菜单管理页改树形表格

**Files**

- Modify: `frontend/src/features/system/menu-pages.tsx`
- Modify: `frontend/src/lib/api/system-menus.ts`
- Test: `frontend/src/features/system/menu-pages.test.tsx`

**Steps**

1. 列表页切换为树形表格视图
2. 展示目录/菜单/权限三种类型
3. 保留编辑、新建子节点入口
4. 展示父级、路由、权限码、排序、显示/隐藏、状态
5. 运行菜单页测试

## 验证

运行：

```bash
mvn -f backend/pom.xml -Dtest=SystemMenuControllerTest,MenuSeedSmokeTest test
pnpm -C frontend exec vitest run src/components/layout/app-sidebar.test.tsx src/features/system/menu-pages.test.tsx --reporter=verbose
pnpm -C frontend typecheck
pnpm -C frontend lint
pnpm -C frontend build
```

## 提交

```bash
git add backend/src/main/java/com/westflow/system/menu backend/src/main/resources/db/migration/V1__init.sql backend/src/test/java/com/westflow/system/menu frontend/src/components/layout frontend/src/features/system/menu-pages.tsx frontend/src/lib/api/system-menus.ts frontend/src/lib/api/sidebar-menus.ts docs/superpowers/specs/2026-03-23-menu-sidebar-rebuild-design.md docs/superpowers/plans/2026-03-23-menu-sidebar-rebuild-plan.md
git commit -m "feat: 重构动态菜单与sidebar体系"
```
