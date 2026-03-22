#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

required_files=(
  "docs/contracts/auth.md"
  "docs/contracts/errors.md"
  "docs/contracts/pagination.md"
  "docs/contracts/table-query.md"
  "docs/contracts/process-dsl.md"
  "docs/contracts/task-actions.md"
  "docs/contracts/dsl-bpmn-mapping.md"
  "docs/contracts/ai-tools.md"
  "docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md"
  "docs/superpowers/plans/2026-03-21-m0-foundation-plan.md"
)

missing=()

for rel_path in "${required_files[@]}"; do
  if [[ ! -f "${ROOT_DIR}/${rel_path}" ]]; then
    missing+=("${rel_path}")
  fi
done

if (( ${#missing[@]} > 0 )); then
  echo "Missing required contract docs:" >&2
  printf ' - %s\n' "${missing[@]}" >&2
  exit 1
fi

echo "M0 contract docs verified."

