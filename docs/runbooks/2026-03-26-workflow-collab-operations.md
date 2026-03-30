# 流程设计器协同服务运维说明

## 目标

本说明覆盖流程设计器协同编辑服务的本地启动、健康检查、鉴权依赖、日志和验收方式。

## 组件关系

- 前端应用：`frontend`
- 后端鉴权接口：`/api/v1/process-definitions/collaboration/authorize`
- 后端审计接口：`/api/v1/process-definitions/collaboration/audit`
- 协同服务：`services/workflow-collab/server.mjs`

## 默认端口

- 前端：`5174`
- 后端：`8080`
- 协同服务：`1235`

## 本地启动

1. 启动依赖：

   ```bash
   docker compose -f infra/docker-compose.yml up -d
   ```

2. 启动后端：

   ```bash
   mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
   ```

3. 启动前端：

   ```bash
   pnpm --dir frontend dev --host 127.0.0.1 --port 5174
   ```

4. 安装协同服务依赖：

   ```bash
   pnpm --dir services/workflow-collab install
   ```

5. 启动协同服务：

   ```bash
   docker compose -f infra/docker-compose.yml up -d workflow-collab
   ```

推荐把协同服务交给 Docker 守护运行，不再使用临时终端会话直接执行 `node server.mjs`。这样即使本地终端关闭或会话被回收，协同服务也会按 `restart: unless-stopped` 自动保活。

## 环境变量

前端：

- `VITE_WORKFLOW_COLLAB_URL`
  - 不填时，本地开发默认走 `ws://127.0.0.1:1235`
  - 生产环境应显式配置

协同服务：

- `HOST`
- `PORT`
- `WORKFLOW_COLLAB_AUTH_API`
  - Docker 默认：`http://host.docker.internal:8080/api/v1/process-definitions/collaboration`
  - 直接本机运行时可用：`http://127.0.0.1:8080/api/v1/process-definitions/collaboration`
- `WORKFLOW_COLLAB_HEARTBEAT_INTERVAL_MS`
- `WORKFLOW_COLLAB_HEARTBEAT_TIMEOUT_MS`
- `WORKFLOW_COLLAB_ROOM_IDLE_TTL_MS`
- `WORKFLOW_COLLAB_MAX_CONNECTIONS_PER_ROOM`
- `WORKFLOW_COLLAB_MAX_TOTAL_CONNECTIONS`

## 健康检查

协同服务：

```bash
curl http://127.0.0.1:1235/health
```

期望返回：

```json
{"status":"UP","mode":"auth","rooms":0,"connections":0}
```

## 鉴权与审计

- websocket 升级前必须带 token
- 协同服务会调用后端授权接口校验房间
- 用户加入/离开房间时，会写入流程操作日志：
  - `DESIGNER_COLLAB_JOIN`
  - `DESIGNER_COLLAB_LEAVE`

## 断线恢复

- 前端会显示：
  - `协同连接中`
  - `协同重连中`
  - `协同已断开`
- 已断开时仍允许本地继续编辑
- 用户可以点击 `立即重试`

## E2E 验收

安装浏览器：

```bash
pnpm -C frontend exec playwright install chromium
```

运行协同 E2E：

```bash
pnpm -C frontend test:e2e --project=chromium
```

当前覆盖：

- 双浏览器同房间编辑同步
- 只读观摩模式

## 故障排查

1. 协同状态一直断开
   - 检查 `http://127.0.0.1:1235/health`
   - 检查 `docker compose -f infra/docker-compose.yml ps workflow-collab`
   - 检查 `docker compose -f infra/docker-compose.yml logs --tail=200 workflow-collab`
   - 检查后端 `8080` 是否可达
   - 检查 token 是否过期

2. websocket 连接失败
   - 检查 `VITE_WORKFLOW_COLLAB_URL`
   - 检查浏览器控制台 websocket 地址是否正确
   - 检查协同服务是否命中单房间或全局连接上限

3. 协同无权限
   - 检查登录态
   - 检查房间名是否符合 `workflow-designer:*`

4. 协同日志未落库
   - 检查后端 `/api/v1/process-definitions/collaboration/audit`
   - 检查协同服务控制台是否有 `audit failed`
