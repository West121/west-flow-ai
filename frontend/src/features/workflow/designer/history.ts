import { type WorkflowSnapshot } from './types'

export type WorkflowHistoryState = {
  past: WorkflowSnapshot[]
  present: WorkflowSnapshot
  future: WorkflowSnapshot[]
}

function cloneSnapshot(snapshot: WorkflowSnapshot): WorkflowSnapshot {
  return structuredClone(snapshot)
}

function isSameSnapshot(left: WorkflowSnapshot, right: WorkflowSnapshot) {
  return JSON.stringify(left) === JSON.stringify(right)
}

export function createWorkflowHistoryState(
  initialSnapshot: WorkflowSnapshot
): WorkflowHistoryState {
  return {
    past: [],
    present: cloneSnapshot(initialSnapshot),
    future: [],
  }
}

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
