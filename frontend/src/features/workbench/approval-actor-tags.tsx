import { Badge } from '@/components/ui/badge'

type DisplayNameMap = Record<string, string> | null | undefined

function resolveDisplayName(
  id: string | null | undefined,
  displayNames: DisplayNameMap
) {
  if (!id?.trim()) {
    return null
  }

  return displayNames?.[id] || id
}

export function ApprovalUserTag({
  userId,
  displayNames,
  fallback = '--',
}: {
  userId: string | null | undefined
  displayNames: DisplayNameMap
  fallback?: string
}) {
  const label = resolveDisplayName(userId, displayNames)
  if (!label) {
    return <span>{fallback}</span>
  }

  return (
    <Badge variant='outline' className='font-normal'>
      {label}
    </Badge>
  )
}

export function ApprovalTagList({
  ids,
  displayNames,
  fallback = '--',
}: {
  ids: Array<string | null | undefined> | null | undefined
  displayNames: DisplayNameMap
  fallback?: string
}) {
  const items = (ids ?? [])
    .map((id) => resolveDisplayName(id, displayNames))
    .filter((value): value is string => Boolean(value))

  if (!items.length) {
    return <span>{fallback}</span>
  }

  return (
    <div className='flex flex-wrap justify-end gap-1.5'>
      {items.map((item) => (
        <Badge key={item} variant='outline' className='font-normal'>
          {item}
        </Badge>
      ))}
    </div>
  )
}
