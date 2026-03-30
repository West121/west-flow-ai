# west-flow-ai

Monorepo for the AIBPMN approval platform.

## Layout

- `frontend/`: direct secondary build on top of `shadcn-admin`
- `backend/`: single Spring Boot application, split by domain packages
- `services/`: standalone side services such as workflow collaboration
- `docs/`: frozen contracts, specs, and implementation plans
- `infra/`: local services and environment templates
- `scripts/`: bootstrap and validation helpers

## M0 Rules

- Frontend visible copy is Chinese-only in this phase.
- Do not add i18n abstraction before it is explicitly needed.
- Backend Flyway uses a single init migration in M0/M1: `V1__init.sql`.
- If schema changes are needed during M0/M1, update that init migration instead of adding a new migration sequence.
- `frontend/` is imported from `shadcn-admin`, but all routes, menus, auth shape, CRUD conventions, and data contracts are project-owned.

## Quick Start

1. Validate the M0 contract set:

   ```bash
   ./scripts/validate-contracts.sh
   ```

2. Prepare local environment variables:

   ```bash
   cp infra/env/.env.example infra/env/.env
   ```

3. Bootstrap the frontend baseline:

   ```bash
   ./scripts/bootstrap-frontend.sh
   ```

4. Install workflow collaboration service dependencies:

   ```bash
   pnpm --dir services/workflow-collab install
   ```

5. Start local dependencies:

   ```bash
   docker compose -f infra/docker-compose.yml up -d
   ```

6. Run the applications:

   ```bash
   pnpm --dir frontend dev
   mvn -f backend/pom.xml spring-boot:run
   docker compose -f infra/docker-compose.yml up -d workflow-collab
   ```

7. Optional: run workflow designer collaboration E2E:

   ```bash
   pnpm -C frontend exec playwright install chromium
   pnpm -C frontend test:e2e:collab
   ```

## Docs

- Platform spec: [`docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`](./docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md)
- M0 plan: [`docs/superpowers/plans/2026-03-21-m0-foundation-plan.md`](./docs/superpowers/plans/2026-03-21-m0-foundation-plan.md)
- Auth contract: [`docs/contracts/auth.md`](./docs/contracts/auth.md)
- Pagination contract: [`docs/contracts/pagination.md`](./docs/contracts/pagination.md)
- Table query contract: [`docs/contracts/table-query.md`](./docs/contracts/table-query.md)
- Error contract: [`docs/contracts/errors.md`](./docs/contracts/errors.md)
- Process DSL contract: [`docs/contracts/process-dsl.md`](./docs/contracts/process-dsl.md)
- Task actions contract: [`docs/contracts/task-actions.md`](./docs/contracts/task-actions.md)
- DSL to BPMN mapping: [`docs/contracts/dsl-bpmn-mapping.md`](./docs/contracts/dsl-bpmn-mapping.md)
- AI tools contract: [`docs/contracts/ai-tools.md`](./docs/contracts/ai-tools.md)
- Workflow collaboration operations: [`docs/runbooks/2026-03-26-workflow-collab-operations.md`](./docs/runbooks/2026-03-26-workflow-collab-operations.md)
