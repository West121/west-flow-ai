import { type ReactNode, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ProcessFormRenderer } from '@/features/forms/runtime/process-form-renderer'
import { type WorkbenchTaskDetail } from '@/lib/api/workbench'
import {
  resolveApprovalSheetBusinessTitle,
} from '@/features/workbench/approval-sheet-helpers'

export function ApprovalSheetBusinessSection({
  detail,
  headerActions,
  children,
}: {
  detail: WorkbenchTaskDetail
  headerActions?: ReactNode
  children?: ReactNode
}) {
  // 详情页只读展示当前表单快照，避免后续渲染过程里被意外改写。
  const [formSnapshot] = useState<Record<string, unknown>>(detail.formData ?? {})
  const title = resolveApprovalSheetBusinessTitle(detail)

  return (
    <Card>
      <CardHeader className='space-y-3'>
        <div className='flex flex-wrap items-start justify-between gap-3'>
          <div className='space-y-1'>
            <CardTitle>业务正文</CardTitle>
            <CardDescription>审批单正文固定展示流程默认业务表单。</CardDescription>
          </div>
          {headerActions ? <div className='flex flex-wrap items-center gap-2'>{headerActions}</div> : null}
        </div>
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
            userDisplayNames={detail.userDisplayNames}
          />
        </div>
        {children}
      </CardContent>
    </Card>
  )
}
