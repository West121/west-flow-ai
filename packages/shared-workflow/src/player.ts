import {
  type WorkflowPlaybackAction,
  type WorkflowPlaybackState,
} from './types'

function clampIndex(index: number, totalEvents: number): number {
  if (totalEvents <= 0) {
    return 0
  }
  return Math.min(Math.max(index, 0), totalEvents - 1)
}

/**
 * 创建回顾播放器状态。
 */
export function createPlaybackState(totalEvents: number, activeIndex = 0): WorkflowPlaybackState {
  return {
    mode: totalEvents > 0 ? 'idle' : 'paused',
    activeIndex: clampIndex(activeIndex, totalEvents),
    totalEvents,
  }
}

/**
 * 推进回顾播放器状态。
 */
export function transitionPlaybackState(
  state: WorkflowPlaybackState,
  action: WorkflowPlaybackAction
): WorkflowPlaybackState {
  switch (action.type) {
    case 'play':
      return {
        ...state,
        mode: 'playing',
        activeIndex: clampIndex(action.startIndex ?? state.activeIndex, state.totalEvents),
      }
    case 'pause':
      return {
        ...state,
        mode: 'paused',
      }
    case 'reset':
      return {
        ...state,
        mode: 'idle',
        activeIndex: 0,
      }
    case 'seek':
      return {
        ...state,
        activeIndex: clampIndex(action.index, state.totalEvents),
      }
    case 'tick':
      if (state.mode !== 'playing' || state.totalEvents <= 0) {
        return state
      }
      if (state.activeIndex >= state.totalEvents - 1) {
        return {
          ...state,
          mode: 'paused',
        }
      }
      return {
        ...state,
        activeIndex: state.activeIndex + 1,
      }
    case 'sync':
      return {
        ...state,
        totalEvents: action.totalEvents,
        activeIndex: clampIndex(state.activeIndex, action.totalEvents),
      }
  }
}
