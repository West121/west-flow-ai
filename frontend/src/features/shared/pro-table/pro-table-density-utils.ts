import { type ProTableDensityMode } from './pro-table-density'

export function resolveDensityClassName(mode: ProTableDensityMode) {
  switch (mode) {
    case 'compact':
      return 'text-sm [&_[data-slot=table-row]]:h-10 [&_[data-slot=table-cell]]:py-2'
    case 'comfortable':
      return '[&_[data-slot=table-row]]:h-14 [&_[data-slot=table-cell]]:py-4'
    default:
      return '[&_[data-slot=table-row]]:h-12 [&_[data-slot=table-cell]]:py-3'
  }
}
