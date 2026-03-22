#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/shadcn-admin.XXXXXX")"
FRONTEND_DIR="${ROOT_DIR}/frontend"

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

command -v git >/dev/null 2>&1 || {
  echo "git is required" >&2
  exit 1
}

command -v rsync >/dev/null 2>&1 || {
  echo "rsync is required" >&2
  exit 1
}

rm -rf "${TMP_DIR}"
git clone --depth 1 https://github.com/satnaing/shadcn-admin.git "${TMP_DIR}"

mkdir -p "${FRONTEND_DIR}"
rsync -a --delete --exclude '.git' --exclude '.github' "${TMP_DIR}/" "${FRONTEND_DIR}/"

echo "Imported shadcn-admin into ${FRONTEND_DIR}"

