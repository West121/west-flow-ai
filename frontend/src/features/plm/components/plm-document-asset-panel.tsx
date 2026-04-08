import { type PLMDocumentAsset } from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

function formatDocumentType(value: string) {
  switch (value) {
    case 'DRAWING':
      return '图纸'
    case 'DOCUMENT':
      return '文档'
    default:
      return value
  }
}

export function PLMDocumentAssetPanel({
  assets,
  pendingAssetId,
  onReleaseAsset,
}: {
  assets: PLMDocumentAsset[]
  pendingAssetId?: string | null
  onReleaseAsset?: (asset: PLMDocumentAsset) => void
}) {
  const drawingCount = assets.filter((asset) => asset.documentType === 'DRAWING').length
  const controlledCount = assets.filter((asset) => asset.vaultState === 'RELEASED').length
  const mappedExternalCount = assets.filter((asset) => Boolean(asset.externalRef)).length

  return (
    <Card>
      <CardHeader>
        <CardTitle>图纸 / 文档</CardTitle>
        <CardDescription>
          查看受控文档资产、版本标签和库状态。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {assets.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有图纸或文档资产。
          </div>
        ) : (
          <div className='space-y-4'>
            <div className='grid gap-3 sm:grid-cols-3'>
              <div className='rounded-xl border bg-muted/15 p-4'>
                <div className='text-sm text-muted-foreground'>文档资产</div>
                <div className='mt-1 text-2xl font-semibold'>{assets.length}</div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  含图纸 {drawingCount} 份
                </div>
              </div>
              <div className='rounded-xl border bg-muted/15 p-4'>
                <div className='text-sm text-muted-foreground'>受控版本</div>
                <div className='mt-1 text-2xl font-semibold'>{controlledCount}</div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  vault 状态为 RELEASED
                </div>
              </div>
              <div className='rounded-xl border bg-muted/15 p-4'>
                <div className='text-sm text-muted-foreground'>外部映射</div>
                <div className='mt-1 text-2xl font-semibold'>{mappedExternalCount}</div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  已挂接到外部文档库记录
                </div>
              </div>
            </div>
            <Separator />
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>类型</TableHead>
                  <TableHead>编码</TableHead>
                  <TableHead>名称</TableHead>
                  <TableHead>版本</TableHead>
                  <TableHead>库状态</TableHead>
                  <TableHead>来源系统</TableHead>
                  <TableHead>文件</TableHead>
                  <TableHead className='text-right'>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {assets.map((asset) => (
                  <TableRow key={asset.id}>
                    <TableCell>
                      <Badge variant='outline'>
                        {formatDocumentType(asset.documentType)}
                      </Badge>
                    </TableCell>
                    <TableCell className='font-medium'>{asset.documentCode}</TableCell>
                    <TableCell>{asset.documentName}</TableCell>
                    <TableCell>{asset.versionLabel ?? '--'}</TableCell>
                    <TableCell>{asset.vaultState}</TableCell>
                    <TableCell>
                      <div className='space-y-1'>
                        <div>{asset.sourceSystem ?? '--'}</div>
                        <div className='text-xs text-muted-foreground'>
                          {asset.externalRef ?? '--'}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>{asset.fileName ?? '--'}</TableCell>
                    <TableCell className='w-[132px] text-right'>
                      {asset.vaultState !== 'RELEASED' ? (
                        <Button
                          type='button'
                          size='sm'
                          variant='outline'
                          disabled={pendingAssetId != null}
                          onClick={() => onReleaseAsset?.(asset)}
                        >
                          {pendingAssetId === asset.id ? '发布中...' : '受控发布'}
                        </Button>
                      ) : (
                        <span className='text-xs text-muted-foreground'>已受控</span>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
