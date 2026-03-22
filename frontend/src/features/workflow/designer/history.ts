import { type WorkflowSnapshot } from './types'

export type WorkflowHistoryState = {
  past: WorkflowSnapshot[]
  present: WorkflowSnapshot
  future: WorkflowSnapshot[]
}

// 历史状态里需要完整拷贝快照，避免引用被后续编辑污染。
function cloneSnapshot(snapshot: WorkflowSnapshot): WorkflowSnapshot {
  return structuredClone(snapshot)
}

// 用结构化字符串比较判断是否真的发生了编辑。
function isSameSnapshot(left: WorkflowSnapshot, right: WorkflowSnapshot) {
  return JSON.stringify(left) === JSON.stringify(right)
}

// 初始化历史栈时，把当前快照放进 present。
export function createWorkflowHistoryState(
  initialSnapshot: WorkflowSnapshot
): WorkflowHistoryState {
  return {
    past: [],
    present: cloneSnapshot(initialSnapshot),
    future: [],
  }
}

// 提交一次编辑到历史栈，供撤销/重做使用。
export function commitWorkflowSnapshot(
  state: WorkflowHistoryState,
  nextSnapshot: WorkflowSnapshot,
  maxHistory = 50
): WorkflowHistoryState {
  if (isSameSnapshot(state.present, nextSnapshot)) {
    return state
  }

  const nextPast = [...state.past, cloneSnapshot(state.present)]
  const trimmedPast = nextPast.slice(Math.max(0, nextPast.length - maxHistory))

  return {
    past: trimmedPast,
    present: cloneSnapshot(nextSnapshot),
    future: [],
  }
}

// 外部回填快照时直接替换 present，不新增历史记录。
export function replaceWorkflowSnapshot(
  state: WorkflowHistoryState,
  nextSnapshot: WorkflowSnapshot
): WorkflowHistoryState {
  if (isSameSnapshot(state.present, nextSnapshot)) {
    return state
  }

  return {
    ...state,
    present: cloneSnapshot(nextSnapshot),
  }
}

// 撤销时把 present 推回 future，并回到上一版快照。
export function undoWorkflowHistory(
  state: WorkflowHistoryState
): WorkflowHistoryState {
  const previous = state.past[state.past.length - 1]

  if (!previous) {
    return state
  }

  return {
    past: state.past.slice(0, -1),
    present: cloneSnapshot(previous),
    future: [cloneSnapshot(state.present), ...state.future],
  }
}

// 重做时从 future 取回下一版快照。
export function redoWorkflowHistory(
  state: WorkflowHistoryState
): WorkflowHistoryState {
  const next = state.future[0]

  if (!next) {
    return state
  }

  return {
    past: [...state.past, cloneSnapshot(state.present)],
    present: cloneSnapshot(next),
    future: state.future.slice(1),
  }
}
