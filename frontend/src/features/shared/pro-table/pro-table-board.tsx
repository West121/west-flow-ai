import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export type ProTableBoardColumn<TData> = {
  key: string
  title: string
  items: TData[]
}

export function ProTableBoard<TData>({
  columns,
  renderCard,
  emptyLabel = '暂无数据',
}: {
  columns: ProTableBoardColumn<TData>[]
  renderCard: (item: TData) => React.ReactNode
  emptyLabel?: string
}) {
  return (
    <div className='grid gap-4 xl:grid-cols-3 2xl:grid-cols-4'>
      {columns.map((column) => (
        <Card key={column.key} className='py-4'>
          <CardHeader className='px-4 pb-0'>
            <CardTitle className='text-base'>{column.title}</CardTitle>
            <CardDescription>{column.items.length} 条记录</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3 px-4'>
            {column.items.length > 0 ? (
              column.items.map((item, index) => (
                <div key={`${column.key}-${index}`}>{renderCard(item)}</div>
              ))
            ) : (
              <div className='rounded-lg border border-dashed px-4 py-6 text-sm text-muted-foreground'>
                {emptyLabel}
              </div>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
