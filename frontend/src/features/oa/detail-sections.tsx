import { useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ProcessFormRenderer } from '@/features/forms/runtime/process-form-renderer'
import { type WorkbenchTaskDetail } from '@/lib/api/workbench'
import {
  resolveApprovalSheetBusinessRows,
  resolveApprovalSheetBusinessTitle,
} from '@/features/workbench/approval-sheet-helpers'

export function ApprovalSheetBusinessSection({
  detail,
}: {
  detail: WorkbenchTaskDetail
}) {
  // 详情页只读展示当前表单快照，避免后续渲染过程里被意外改写。
  const [formSnapshot] = useState<Record<string, unknown>>(detail.formData ?? {})
  const title = resolveApprovalSheetBusinessTitle(detail)
  const rows = resolveApprovalSheetBusinessRows(detail)

  return (
    <Card>
      <CardHeader>
        <CardTitle>业务正文</CardTitle>
        <CardDescription>审批单正文固定展示流程默认业务表单，节点覆盖表单只影响办理区。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <p className='text-xs text-muted-foreground'>业务标题</p>
          <p className='mt-1 text-sm font-medium'>{title}</p>
        </div>

        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='mb-3 flex items-center justify-between gap-3'>
            <p className='text-sm font-medium'>业务表单正文</p>
            <span className='text-xs text-muted-foreground'>
              {detail.processFormKey} · {detail.processFormVersion}
            </span>
          </div>
          <ProcessFormRenderer
            processFormKey={detail.processFormKey}
            processFormVersion={detail.processFormVersion}
            value={formSnapshot}
            onChange={() => {}}
            disabled
          />
        </div>

        <div className='rounded-lg border bg-muted/20 p-4'>
          <p className='mb-3 text-sm font-medium'>业务摘要</p>
          <dl className='grid gap-3 text-sm sm:grid-cols-2'>
            {rows.length ? (
              rows.map((row) => (
                <div key={row.key} className='space-y-1'>
                  <dt className='text-xs text-muted-foreground'>{row.label}</dt>
                  <dd className='break-all'>{row.value}</dd>
                </div>
              ))
            ) : (
              <div className='sm:col-span-2 text-muted-foreground'>暂无业务正文数据</div>
            )}
          </dl>
        </div>
      </CardContent>
    </Card>
  )
}
