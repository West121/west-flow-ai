import {
  type PLMConfigurationBaseline,
  type PLMDocumentAsset,
  type PLMObjectLink,
} from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

type IntegrationRecord = {
  id: string
  sourceSystem: string
  entityType: 'OBJECT' | 'DOCUMENT'
  entityCode: string
  entityName: string
  revision: string
  boundaryLabel: string
  actionLabel: string
  externalRef: string | null
  note: string | null
}

function normalizeSourceSystem(value?: string | null) {
  return value?.trim() || 'PLM 本地'
}

function formatActionLabel(value?: string | null) {
  switch (value) {
    case 'ADD':
      return '新增'
    case 'UPDATE':
      return '更新'
    case 'REMOVE':
      return '移除'
    case 'REPLACE':
      return '替换'
    default:
      return value ?? '--'
  }
}

function buildRecords(
  objectLinks: PLMObjectLink[],
  documentAssets: PLMDocumentAsset[]
): IntegrationRecord[] {
  const objectRecords = objectLinks.map<IntegrationRecord>((link) => ({
    id: `object-${link.id}`,
    sourceSystem: normalizeSourceSystem(link.sourceSystem),
    entityType: 'OBJECT',
    entityCode: link.objectCode,
    entityName: link.objectName,
    revision:
      link.afterRevisionCode ??
      link.objectRevisionCode ??
      link.versionLabel ??
      '--',
    boundaryLabel: link.roleLabel ?? link.roleCode,
    actionLabel: formatActionLabel(link.changeAction),
    externalRef: link.externalRef ?? null,
    note: link.remark ?? null,
  }))
  const documentRecords = documentAssets.map<IntegrationRecord>((asset) => ({
    id: `document-${asset.id}`,
    sourceSystem: normalizeSourceSystem(asset.sourceSystem),
    entityType: 'DOCUMENT',
    entityCode: asset.documentCode,
    entityName: asset.documentName,
    revision: asset.versionLabel ?? '--',
    boundaryLabel: asset.documentType === 'DRAWING' ? '图纸受控库' : '文档库',
    actionLabel: formatActionLabel(asset.changeAction ?? asset.vaultState),
    externalRef: asset.externalRef ?? null,
    note: asset.fileName ?? null,
  }))

  return [...objectRecords, ...documentRecords]
}

export function PLMIntegrationBoundaryPanel({
  objectLinks,
  documentAssets,
  baselines,
}: {
  objectLinks: PLMObjectLink[]
  documentAssets: PLMDocumentAsset[]
  baselines: PLMConfigurationBaseline[]
}) {
  const records = buildRecords(objectLinks, documentAssets)
  const systemMap = new Map<
    string,
    {
      system: string
      totalCount: number
      mappedCount: number
      documentCount: number
      objectCount: number
    }
  >()

  records.forEach((record) => {
    const current = systemMap.get(record.sourceSystem) ?? {
      system: record.sourceSystem,
      totalCount: 0,
      mappedCount: 0,
      documentCount: 0,
      objectCount: 0,
    }
    current.totalCount += 1
    current.mappedCount += record.externalRef ? 1 : 0
    if (record.entityType === 'DOCUMENT') {
      current.documentCount += 1
    } else {
      current.objectCount += 1
    }
    systemMap.set(record.sourceSystem, current)
  })

  const systemSummary = [...systemMap.values()].sort(
    (left, right) => right.totalCount - left.totalCount
  )
  const pendingMappingCount = records.filter((record) => !record.externalRef).length

  return (
    <Card>
      <CardHeader>
        <CardTitle>外部系统边界 / 集成记录</CardTitle>
        <CardDescription>
          查看对象、文档和基线在外部系统中的挂接情况，识别待补映射边界。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='grid gap-3 sm:grid-cols-3'>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>接入系统</div>
            <div className='mt-1 text-2xl font-semibold'>{systemSummary.length}</div>
            <div className='mt-1 text-xs text-muted-foreground'>
              已识别边界与本地系统映射
            </div>
          </div>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>外部映射记录</div>
            <div className='mt-1 text-2xl font-semibold'>
              {records.length - pendingMappingCount}
            </div>
            <div className='mt-1 text-xs text-muted-foreground'>
              含对象链接与文档资产映射
            </div>
          </div>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>待补边界</div>
            <div className='mt-1 text-2xl font-semibold'>{pendingMappingCount}</div>
            <div className='mt-1 text-xs text-muted-foreground'>
              基线 {baselines.length} 组，需同步检查外部引用
            </div>
          </div>
        </div>

        {systemSummary.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前还没有外部系统边界记录。
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>系统</TableHead>
                <TableHead>对象</TableHead>
                <TableHead>文档</TableHead>
                <TableHead>总记录</TableHead>
                <TableHead>已映射</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {systemSummary.map((item) => (
                <TableRow key={item.system}>
                  <TableCell className='font-medium'>{item.system}</TableCell>
                  <TableCell>{item.objectCount}</TableCell>
                  <TableCell>{item.documentCount}</TableCell>
                  <TableCell>{item.totalCount}</TableCell>
                  <TableCell>{item.mappedCount}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        <ScrollArea className='max-h-[28rem]'>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>边界对象</TableHead>
                <TableHead>系统</TableHead>
                <TableHead>边界角色</TableHead>
                <TableHead>版本</TableHead>
                <TableHead>外部引用</TableHead>
                <TableHead>动作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {records.map((record) => (
                <TableRow key={record.id}>
                  <TableCell>
                    <div className='space-y-1'>
                      <div className='font-medium'>{record.entityName}</div>
                      <div className='text-xs text-muted-foreground'>
                        {record.entityCode}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>{record.sourceSystem}</TableCell>
                  <TableCell>
                    <div className='space-y-1'>
                      <Badge variant='outline'>
                        {record.entityType === 'DOCUMENT' ? '文档' : '对象'}
                      </Badge>
                      <div className='text-xs text-muted-foreground'>
                        {record.boundaryLabel}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>{record.revision}</TableCell>
                  <TableCell>
                    {record.externalRef ? (
                      <span className='font-medium'>{record.externalRef}</span>
                    ) : (
                      <Badge variant='secondary'>待补引用</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className='space-y-1'>
                      <div>{record.actionLabel}</div>
                      <div className='text-xs text-muted-foreground'>
                        {record.note ?? '--'}
                      </div>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </ScrollArea>
      </CardContent>
    </Card>
  )
}
