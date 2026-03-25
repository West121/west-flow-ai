# Org Role Post User List Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-row "关联用户" actions to department, role, and post list pages with backend-backed dialogs showing related users.

**Architecture:** Expose three read-only backend endpoints that return associated users for a department, role, or post while reusing existing user visibility rules. In the frontend, add one shared dialog presenter used by the three list pages so the UI change stays local to the action column.

**Tech Stack:** Spring Boot, MyBatis, React, TanStack Query, Vitest, MockMvc

---

### Task 1: Add failing backend coverage

**Files:**
- Modify: `backend/src/test/java/com/westflow/system/org/api/SystemOrgControllerTest.java`
- Modify: `backend/src/test/java/com/westflow/system/role/api/SystemRoleControllerTest.java`

- [ ] Step 1: Write failing tests for department/post/role user association endpoints.
- [ ] Step 2: Run targeted backend tests and confirm 404 or missing-field failures.
- [ ] Step 3: Implement minimal backend endpoints and queries.
- [ ] Step 4: Re-run targeted backend tests and confirm pass.

### Task 2: Add failing frontend coverage

**Files:**
- Modify: `frontend/src/features/system/org-pages.test.tsx`
- Modify: `frontend/src/lib/api/system-org.test.ts`
- Modify: `frontend/src/lib/api/system-roles.test.ts`
- Create or modify: `frontend/src/features/system/role-pages.test.tsx`

- [ ] Step 1: Write failing API and page tests for the new action/dialog flow.
- [ ] Step 2: Run targeted frontend tests and confirm failures.
- [ ] Step 3: Implement frontend API bindings and dialog UI.
- [ ] Step 4: Re-run targeted frontend tests and confirm pass.

### Task 3: Verify end-to-end slices

**Files:**
- Modify as needed during implementation.

- [ ] Step 1: Run focused backend and frontend verification commands.
- [ ] Step 2: Check outputs for zero failures.
- [ ] Step 3: Report completed behavior and any remaining limits.
