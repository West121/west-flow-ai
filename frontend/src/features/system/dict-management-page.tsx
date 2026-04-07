import { useEffect, useState } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { AlertCircle, ChevronRight, Loader2, Plus, Save, Search } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { DataTablePagination } from '@/components/data-table'
import { PageShell } from '@/features/shared/page-shell'
import { type NavigateFn, useTableUrlState } from '@/hooks/use-table-url-state'
import { getApiErrorResponse } from '@/lib/api/client'
import { handleServerError } from '@/lib/handle-server-error'
import {
  createSystemDictItem,
  createSystemDictType,
  getSystemDictItemDetail,
  getSystemDictTypeDetail,
  listSystemDictItems,
  listSystemDictTypes,
  updateSystemDictItem,
  updateSystemDictType,
  type SaveSystemDictItemPayload,
  type SaveSystemDictTypePayload,
  type SystemDictItemDetail,
  type SystemDictItemPageResponse,
  type SystemDictItemRecord,
  type SystemDictStatus,
  type SystemDictTypeDetail,
  type SystemDictTypePageResponse,
  type SystemDictTypeRecord,
} from '@/lib/api/system-dicts'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
  type ListQuerySearch,
  type ListQueryRouteSearch,
} from '@/features/shared/table/query-contract'

// eslint-disable-next-line react-refresh/only-export-components
export const dictManagementSearchSchema = listQueryRouteSearchSchema.extend({
  typeId: z.string().optional().catch(undefined),
})

export type DictManagementSearch = ListQuerySearch & {
  typeId: string
}

// 路由层允许省略默认查询参数，页面内再补齐默认值。
// eslint-disable-next-line react-refresh/only-export-components
export function normalizeDictManagementSearch(
  search: ListQueryRouteSearch & { typeId?: string | undefined }
): DictManagementSearch {
  return {
    ...normalizeListQuerySearch(search),
    typeId: search.typeId ?? '',
  }
}

type DialogMode = 'create' | 'edit' | 'detail'

type TypeDialogState =
  | { mode: DialogMode; dictTypeId?: string }
  | null

type ItemDialogState =
  | { mode: DialogMode; dictItemId?: string; dictTypeId?: string }
  | null

const dictTypeFormSchema = z.object({
  typeCode: z.string().trim().min(2, '字典类型编码至少需要 2 个字符'),
  typeName: z.string().trim().min(2, '字典类型名称至少需要 2 个字符'),
  description: z.string().max(500, '描述最多 500 个字符').nullable(),
  enabled: z.boolean(),
})

const dictItemFormSchema = z.object({
  dictTypeId: z.string().trim().min(1, '请选择所属字典类型'),
  itemCode: z.string().trim().min(1, '字典项编码不能为空'),
  itemLabel: z.string().trim().min(1, '字典项名称不能为空'),
  itemValue: z.string().trim().min(1, '字典项值不能为空'),
  sortOrder: z.number().int().min(0, '排序值不能小于 0'),
  remark: z.string().max(500, '备注最多 500 个字符').nullable(),
  enabled: z.boolean(),
})

type DictTypeFormValues = z.infer<typeof dictTypeFormSchema>
type DictItemFormValues = z.infer<typeof dictItemFormSchema>

type DictPageErrorProps = {
  title: string
  description: string
  retry?: () => void
}

const EMPTY_PAGE: ListQuerySearch = {
  page: 1,
  pageSize: 10,
  keyword: '',
  filters: [],
  sorts: [],
  groups: [],
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function resolveDictStatusLabel(status: SystemDictStatus) {
  return status === 'ENABLED' ? '启用' : '停用'
}

function resolveDictStatusVariant(status: SystemDictStatus) {
  return status === 'ENABLED' ? 'secondary' : 'outline'
}

function DictPageErrorState({ title, description, retry }: DictPageErrorProps) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>字典管理数据请求未成功，请重试或稍后再查看。</AlertDescription>
      </Alert>
      {retry ? <Button onClick={retry}>重新加载</Button> : null}
    </PageShell>
  )
}

function resolveTypeSearchRequest() {
  return {
    page: 1,
    pageSize: 100,
    keyword: '',
    filters: [],
    sorts: [{ field: 'typeCode', direction: 'asc' as const }],
    groups: [],
  }
}

function resolveTypeIdFromSearch(search: DictManagementSearch, records: SystemDictTypeRecord[]) {
  if (search.typeId && records.some((item) => item.dictTypeId === search.typeId)) {
    return search.typeId
  }
  return records[0]?.dictTypeId ?? ''
}

function buildItemSearch(search: DictManagementSearch, dictTypeId: string): ListQuerySearch {
  const filters = (search.filters ?? []).filter((item) => item.field !== 'dictTypeId')
  if (dictTypeId) {
    filters.push({ field: 'dictTypeId', operator: 'eq', value: dictTypeId })
  }
  return {
    ...EMPTY_PAGE,
    ...search,
    filters,
  }
}

function typeMatchesKeyword(item: SystemDictTypeRecord, keyword: string) {
  const normalized = keyword.trim().toLowerCase()
  if (!normalized) return true
  return (
    item.typeCode.toLowerCase().includes(normalized) ||
    item.typeName.toLowerCase().includes(normalized) ||
    (item.description ?? '').toLowerCase().includes(normalized)
  )
}

function DictTypeBadge({ status }: { status: SystemDictStatus }) {
  return <Badge variant={resolveDictStatusVariant(status)}>{resolveDictStatusLabel(status)}</Badge>
}

function TypeListItem({
  item,
  active,
  onSelect,
  onDetail,
  onEdit,
}: {
  item: SystemDictTypeRecord
  active: boolean
  onSelect: () => void
  onDetail: () => void
  onEdit: () => void
}) {
  return (
    <div
      className={[
        'rounded-lg border p-3 transition-colors',
        active ? 'border-primary bg-primary/5' : 'hover:bg-muted/50',
      ].join(' ')}
    >
      <button type='button' className='w-full text-left' onClick={onSelect}>
        <div className='flex items-start justify-between gap-3'>
          <div className='min-w-0'>
            <div className='flex items-center gap-2'>
              <span className='truncate font-medium'>{item.typeName}</span>
              <DictTypeBadge status={item.status} />
            </div>
            <div className='mt-1 text-xs text-muted-foreground'>
              {item.typeCode} · {item.dictTypeId}
            </div>
            <div className='mt-2 line-clamp-2 text-xs text-muted-foreground'>
              {item.description ?? '未填写说明'}
            </div>
          </div>
          <ChevronRight className='mt-0.5 size-4 shrink-0 text-muted-foreground' />
        </div>
      </button>
      <div className='mt-3 flex items-center justify-between gap-2'>
        <div className='text-xs text-muted-foreground'>字典项 {item.itemCount} 条</div>
        <div className='flex items-center gap-1'>
          <Button type='button' variant='ghost' size='sm' className='h-7 px-2' onClick={onDetail}>
            详情
          </Button>
          <Button type='button' variant='ghost' size='sm' className='h-7 px-2' onClick={onEdit}>
            编辑
          </Button>
        </div>
      </div>
    </div>
  )
}

function TypeDetailView({ detail }: { detail: SystemDictTypeDetail }) {
  return (
    <div className='grid gap-4'>
      <div className='grid gap-4 md:grid-cols-2'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>类型编码</div>
          <div className='mt-1 font-medium'>{detail.typeCode}</div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>类型名称</div>
          <div className='mt-1 font-medium'>{detail.typeName}</div>
        </div>
      </div>
      <div className='rounded-lg border bg-muted/20 p-4'>
        <div className='text-sm text-muted-foreground'>说明</div>
        <div className='mt-1 text-sm'>{detail.description ?? '-'}</div>
      </div>
      <div className='grid gap-4 md:grid-cols-3'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>状态</div>
          <div className='mt-1'>
            <DictTypeBadge status={detail.status} />
          </div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>字典项数量</div>
          <div className='mt-1 font-medium'>{detail.itemCount}</div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>更新时间</div>
          <div className='mt-1 text-sm'>{formatDateTime(detail.updatedAt)}</div>
        </div>
      </div>
    </div>
  )
}

function ItemDetailView({ detail }: { detail: SystemDictItemDetail }) {
  return (
    <div className='grid gap-4'>
      <div className='grid gap-4 md:grid-cols-2'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>所属类型</div>
          <div className='mt-1 font-medium'>
            {detail.dictTypeCode} · {detail.dictTypeName}
          </div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>状态</div>
          <div className='mt-1'>
            <DictTypeBadge status={detail.status} />
          </div>
        </div>
      </div>
      <div className='grid gap-4 md:grid-cols-2'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>字典项编码</div>
          <div className='mt-1 font-medium'>{detail.itemCode}</div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>字典项名称</div>
          <div className='mt-1 font-medium'>{detail.itemLabel}</div>
        </div>
      </div>
      <div className='grid gap-4 md:grid-cols-2'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>字典项值</div>
          <div className='mt-1 font-medium'>{detail.itemValue}</div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>排序</div>
          <div className='mt-1 font-medium'>{detail.sortOrder}</div>
        </div>
      </div>
      <div className='rounded-lg border bg-muted/20 p-4'>
        <div className='text-sm text-muted-foreground'>备注</div>
        <div className='mt-1 text-sm'>{detail.remark ?? '-'}</div>
      </div>
      <div className='grid gap-4 md:grid-cols-2'>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>创建时间</div>
          <div className='mt-1 text-sm'>{formatDateTime(detail.createdAt)}</div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-sm text-muted-foreground'>更新时间</div>
          <div className='mt-1 text-sm'>{formatDateTime(detail.updatedAt)}</div>
        </div>
      </div>
    </div>
  )
}

function TypeDialog({
  state,
  open,
  onOpenChange,
  onSaved,
}: {
  state: TypeDialogState
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: (dictTypeId?: string) => void
}) {
  const isEdit = state?.mode === 'edit'
  const isDetail = state?.mode === 'detail'
  const queryClient = useQueryClient()
  const form = useForm<DictTypeFormValues>({
    resolver: zodResolver(dictTypeFormSchema),
    defaultValues: {
      typeCode: '',
      typeName: '',
      description: '',
      enabled: true,
    },
  })
  const detailQuery = useQuery({
    queryKey: ['system-dict-type-detail', state?.dictTypeId],
    enabled: open && Boolean(state?.dictTypeId) && (isEdit || isDetail),
    queryFn: () => getSystemDictTypeDetail(state!.dictTypeId!),
  })
  const mutation = useMutation({
    mutationFn: (payload: SaveSystemDictTypePayload) =>
      isEdit && state?.dictTypeId
        ? updateSystemDictType(state.dictTypeId, payload)
        : createSystemDictType(payload),
    onError: (error) => {
      const apiError = getApiErrorResponse(error)
      if (apiError?.fieldErrors?.length) {
        for (const fieldError of apiError.fieldErrors) {
          if (
            fieldError.field === 'typeCode' ||
            fieldError.field === 'typeName' ||
            fieldError.field === 'description' ||
            fieldError.field === 'enabled'
          ) {
            form.setError(fieldError.field as keyof DictTypeFormValues, {
              type: 'server',
              message: fieldError.message,
            })
          }
        }
        return
      }
      handleServerError(error)
    },
  })

  useEffect(() => {
    if (open && isEdit && detailQuery.data) {
      form.reset({
        typeCode: detailQuery.data.typeCode,
        typeName: detailQuery.data.typeName,
        description: detailQuery.data.description,
        enabled: detailQuery.data.status === 'ENABLED',
      })
    }
    if (open && !isEdit) {
      form.reset({
        typeCode: '',
        typeName: '',
        description: '',
        enabled: true,
      })
    }
  }, [detailQuery.data, form, isEdit, open])

  async function submit(values: DictTypeFormValues) {
    const payload: SaveSystemDictTypePayload = {
      typeCode: values.typeCode.trim(),
      typeName: values.typeName.trim(),
      description: values.description?.trim() ? values.description.trim() : null,
      enabled: values.enabled,
    }
    const result = await mutation.mutateAsync(payload)
    await queryClient.invalidateQueries({ queryKey: ['system-dict-management-types'] })
    await queryClient.invalidateQueries({ queryKey: ['system-dict-type-detail'] })
    toast.success(isEdit ? '字典类型已更新' : '字典类型已创建')
    onSaved(result.dictTypeId)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[90vh] overflow-y-auto sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>
            {isDetail ? '字典类型详情' : isEdit ? '编辑字典类型' : '新建字典类型'}
          </DialogTitle>
          <DialogDescription>
            {isDetail ? '只读查看字典类型信息。' : '保存后会立即刷新左侧类型列表。'}
          </DialogDescription>
        </DialogHeader>
        {isDetail ? (
          detailQuery.isLoading ? (
            <Skeleton className='h-56 w-full' />
          ) : detailQuery.data ? (
            <TypeDetailView detail={detailQuery.data} />
          ) : (
            <Alert variant='destructive'>
              <AlertCircle />
              <AlertTitle>加载失败</AlertTitle>
              <AlertDescription>字典类型详情加载失败。</AlertDescription>
            </Alert>
          )
        ) : (
          <Form {...form}>
            <form className='grid gap-4' onSubmit={form.handleSubmit(submit)}>
              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='typeCode'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>类型编码</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：TASK_STATUS' {...field} />
                      </FormControl>
                      <FormDescription>编码用于程序侧关联，不建议使用中文。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='typeName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>类型名称</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：任务状态' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <FormField
                control={form.control}
                name='description'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>说明</FormLabel>
                    <FormControl>
                      <Textarea
                        rows={4}
                        placeholder='可选，描述该字典类型的业务场景。'
                        {...field}
                        value={field.value ?? ''}
                        onChange={(event) => field.onChange(event.target.value || null)}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='enabled'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>状态</FormLabel>
                    <FormControl>
                      <div className='flex items-center gap-3 rounded-md border p-3'>
                        <Switch checked={field.value} onCheckedChange={field.onChange} />
                        <span className='text-sm text-muted-foreground'>
                          {field.value ? '启用' : '停用'}
                        </span>
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <DialogFooter>
                <Button type='button' variant='outline' onClick={() => onOpenChange(false)}>
                  取消
                </Button>
                <Button type='submit' disabled={mutation.isPending}>
                  {mutation.isPending ? <Loader2 className='animate-spin' /> : <Save />}
                  保存
                </Button>
              </DialogFooter>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  )
}

function ItemDialog({
  state,
  open,
  onOpenChange,
  onSaved,
  dictTypeOptions,
}: {
  state: ItemDialogState
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: (dictTypeId?: string) => void
  dictTypeOptions: SystemDictTypeRecord[]
}) {
  const isEdit = state?.mode === 'edit'
  const isDetail = state?.mode === 'detail'
  const queryClient = useQueryClient()
  const form = useForm<DictItemFormValues>({
    resolver: zodResolver(dictItemFormSchema),
    defaultValues: {
      dictTypeId: state?.dictTypeId ?? '',
      itemCode: '',
      itemLabel: '',
      itemValue: '',
      sortOrder: 0,
      remark: '',
      enabled: true,
    },
  })
  const detailQuery = useQuery({
    queryKey: ['system-dict-item-detail', state?.dictItemId],
    enabled: open && Boolean(state?.dictItemId) && (isEdit || isDetail),
    queryFn: () => getSystemDictItemDetail(state!.dictItemId!),
  })
  const mutation = useMutation({
    mutationFn: (payload: SaveSystemDictItemPayload) =>
      isEdit && state?.dictItemId
        ? updateSystemDictItem(state.dictItemId, payload)
        : createSystemDictItem(payload),
    onError: (error) => {
      const apiError = getApiErrorResponse(error)
      if (apiError?.fieldErrors?.length) {
        for (const fieldError of apiError.fieldErrors) {
          if (
            fieldError.field === 'dictTypeId' ||
            fieldError.field === 'itemCode' ||
            fieldError.field === 'itemLabel' ||
            fieldError.field === 'itemValue' ||
            fieldError.field === 'sortOrder' ||
            fieldError.field === 'remark' ||
            fieldError.field === 'enabled'
          ) {
            form.setError(fieldError.field as keyof DictItemFormValues, {
              type: 'server',
              message: fieldError.message,
            })
          }
        }
        return
      }
      handleServerError(error)
    },
  })

  useEffect(() => {
    if (open && isEdit && detailQuery.data) {
      form.reset({
        dictTypeId: detailQuery.data.dictTypeId,
        itemCode: detailQuery.data.itemCode,
        itemLabel: detailQuery.data.itemLabel,
        itemValue: detailQuery.data.itemValue,
        sortOrder: detailQuery.data.sortOrder,
        remark: detailQuery.data.remark ?? '',
        enabled: detailQuery.data.status === 'ENABLED',
      })
    }
    if (open && !isEdit) {
      form.reset({
        dictTypeId: state?.dictTypeId ?? dictTypeOptions[0]?.dictTypeId ?? '',
        itemCode: '',
        itemLabel: '',
        itemValue: '',
        sortOrder: 0,
        remark: '',
        enabled: true,
      })
    }
  }, [detailQuery.data, dictTypeOptions, form, isEdit, open, state?.dictTypeId])

  async function submit(values: DictItemFormValues) {
    const payload: SaveSystemDictItemPayload = {
      dictTypeId: values.dictTypeId,
      itemCode: values.itemCode.trim(),
      itemLabel: values.itemLabel.trim(),
      itemValue: values.itemValue.trim(),
      sortOrder: values.sortOrder,
      remark: values.remark?.trim() ? values.remark.trim() : null,
      enabled: values.enabled,
    }
    await mutation.mutateAsync(payload)
    await queryClient.invalidateQueries({ queryKey: ['system-dict-management-items'] })
    await queryClient.invalidateQueries({ queryKey: ['system-dict-management-types'] })
    await queryClient.invalidateQueries({ queryKey: ['system-dict-item-detail'] })
    toast.success(isEdit ? '字典项已更新' : '字典项已创建')
    onSaved(payload.dictTypeId || state?.dictTypeId)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className='max-h-[90vh] overflow-y-auto sm:max-w-3xl'>
        <DialogHeader>
          <DialogTitle>{isDetail ? '字典项详情' : isEdit ? '编辑字典项' : '新建字典项'}</DialogTitle>
          <DialogDescription>
            {isDetail ? '只读查看字典项信息。' : '保存后会立即刷新右侧字典项列表。'}
          </DialogDescription>
        </DialogHeader>
        {isDetail ? (
          detailQuery.isLoading ? (
            <Skeleton className='h-56 w-full' />
          ) : detailQuery.data ? (
            <ItemDetailView detail={detailQuery.data} />
          ) : (
            <Alert variant='destructive'>
              <AlertCircle />
              <AlertTitle>加载失败</AlertTitle>
              <AlertDescription>字典项详情加载失败。</AlertDescription>
            </Alert>
          )
        ) : (
          <Form {...form}>
            <form className='grid gap-4' onSubmit={form.handleSubmit(submit)}>
              <FormField
                control={form.control}
                name='dictTypeId'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>所属字典类型</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder='请选择所属类型' />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {dictTypeOptions.map((item) => (
                          <SelectItem key={item.dictTypeId} value={item.dictTypeId}>
                            {item.typeCode} · {item.typeName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='itemCode'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>字典项编码</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：PENDING' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='itemLabel'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>字典项名称</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：待处理' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='itemValue'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>字典项值</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：01' {...field} />
                      </FormControl>
                      <FormDescription>字典项值为业务实际写入字段值。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='sortOrder'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>排序值</FormLabel>
                      <FormControl>
                        <Input
                          type='number'
                          min={0}
                          step={1}
                          value={field.value}
                          onChange={(event) => field.onChange(Number(event.target.value || 0))}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <FormField
                control={form.control}
                name='remark'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>备注</FormLabel>
                    <FormControl>
                      <Textarea
                        rows={3}
                        placeholder='可选，填写说明。'
                        {...field}
                        value={field.value ?? ''}
                        onChange={(event) => field.onChange(event.target.value || null)}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='enabled'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>状态</FormLabel>
                    <FormControl>
                      <div className='flex items-center gap-3 rounded-md border p-3'>
                        <Switch checked={field.value} onCheckedChange={field.onChange} />
                        <span className='text-sm text-muted-foreground'>
                          {field.value ? '启用' : '停用'}
                        </span>
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <DialogFooter>
                <Button type='button' variant='outline' onClick={() => onOpenChange(false)}>
                  取消
                </Button>
                <Button type='submit' disabled={mutation.isPending}>
                  {mutation.isPending ? <Loader2 className='animate-spin' /> : <Save />}
                  保存
                </Button>
              </DialogFooter>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  )
}

function itemColumns({
  onDetail,
  onEdit,
}: {
  onDetail: (item: SystemDictItemRecord) => void
  onEdit: (item: SystemDictItemRecord) => void
}): ColumnDef<SystemDictItemRecord>[] {
  return [
    {
      accessorKey: 'itemCode',
      header: '字典项编码',
      cell: ({ row }) => row.original.itemCode,
    },
    {
      accessorKey: 'itemLabel',
      header: '字典项名称',
      cell: ({ row }) => row.original.itemLabel,
    },
    {
      accessorKey: 'itemValue',
      header: '字典项值',
      cell: ({ row }) => row.original.itemValue,
    },
    {
      accessorKey: 'sortOrder',
      header: '排序',
    },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => <DictTypeBadge status={row.original.status} />,
    },
    {
      id: 'action',
      header: '操作',
      cell: ({ row }) => (
        <div className='flex items-center gap-2'>
          <Button variant='ghost' size='sm' className='h-8 px-2' onClick={() => onDetail(row.original)}>
            详情
          </Button>
          <Button variant='ghost' size='sm' className='h-8 px-2' onClick={() => onEdit(row.original)}>
            编辑
          </Button>
        </div>
      ),
    },
  ]
}

function buildTypePage(search: DictManagementSearch): SystemDictTypePageResponse {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

function buildItemPage(search: ListQuerySearch): SystemDictItemPageResponse {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

export function DictManagementPage({
  search,
  navigate,
}: {
  search: DictManagementSearch
  navigate: NavigateFn
}) {
  'use no memo'

  const queryClient = useQueryClient()
  const [typeDialog, setTypeDialog] = useState<TypeDialogState>(null)
  const [itemDialog, setItemDialog] = useState<ItemDialogState>(null)
  const [typeKeyword, setTypeKeyword] = useState('')
  const typeQuery = useQuery({
    queryKey: ['system-dict-management-types'],
    queryFn: () => listSystemDictTypes(resolveTypeSearchRequest()),
  })

  const types = typeQuery.data?.records ?? buildTypePage(search).records
  const selectedTypeId = resolveTypeIdFromSearch(search, types)
  const selectedType = types.find((item) => item.dictTypeId === selectedTypeId) ?? types[0] ?? null

  useEffect(() => {
    if (types.length === 0) {
      return
    }
    if (search.typeId !== selectedType?.dictTypeId && selectedType?.dictTypeId) {
      navigate({
        replace: true,
        search: (prev) => ({
          ...(prev as Record<string, unknown>),
          typeId: selectedType.dictTypeId,
        }),
      })
    }
  }, [navigate, search.typeId, selectedType?.dictTypeId, types.length])

  const itemSearch = buildItemSearch(search, selectedTypeId)
  const itemQuery = useQuery({
    queryKey: ['system-dict-management-items', itemSearch],
    enabled: Boolean(selectedTypeId),
    queryFn: () => listSystemDictItems(itemSearch),
  })

  const items = itemQuery.data?.records ?? buildItemPage(itemSearch).records

  const { globalFilter, onGlobalFilterChange, pagination, onPaginationChange, ensurePageInRange } =
    useTableUrlState({
      search: itemSearch,
      navigate,
      pagination: { defaultPage: 1, defaultPageSize: 10 },
      globalFilter: { enabled: true, key: 'keyword' },
    })

  // TanStack Table 当前会触发 React Compiler 的兼容性告警，这里显式保留非 memo 边界。
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data: items,
    columns: itemColumns({
      onDetail: (item) => setItemDialog({ mode: 'detail', dictItemId: item.dictItemId }),
      onEdit: (item) =>
        setItemDialog({
          mode: 'edit',
          dictItemId: item.dictItemId,
          dictTypeId: item.dictTypeId,
        }),
    }),
    state: { pagination },
    manualPagination: true,
    pageCount: itemQuery.data?.pages ?? 1,
    onPaginationChange,
    getCoreRowModel: getCoreRowModel(),
  })

  useEffect(() => {
    ensurePageInRange(table.getPageCount())
  }, [ensurePageInRange, table])

  if (typeQuery.isLoading) {
    return (
      <PageShell title='字典管理' description='左侧字典类型，右侧字典项联动管理。'>
        <Skeleton className='h-[520px] w-full' />
      </PageShell>
    )
  }

  if (typeQuery.isError) {
    return <DictPageErrorState title='字典管理' description='字典类型加载失败。' retry={() => void typeQuery.refetch()} />
  }

  return (
    <PageShell
      title='字典管理'
      description='左侧维护字典类型，点击后在右侧联动查看和维护对应字典项。'
      fixed
      contentClassName='min-h-0'
      actions={
        <>
          <Button variant='outline' onClick={() => setTypeDialog({ mode: 'create' })}>
            <Plus />
            新建字典类型
          </Button>
          <Button
            onClick={() =>
              setItemDialog({
                mode: 'create',
                dictTypeId: selectedTypeId || selectedType?.dictTypeId,
              })
            }
            disabled={!selectedTypeId}
          >
            <Plus />
            新建字典项
          </Button>
        </>
      }
    >
      <div className='grid min-h-0 flex-1 gap-4 xl:grid-cols-[320px_minmax(0,1fr)]'>
        <Card className='flex min-h-0 flex-col overflow-hidden'>
          <CardHeader className='space-y-3'>
            <div className='flex items-center justify-between gap-3'>
              <div>
                <CardTitle>字典类型</CardTitle>
                <CardDescription>点击类型后，右侧显示对应字典项。</CardDescription>
              </div>
              <Badge variant='outline'>{types.length}</Badge>
            </div>
            <div className='relative'>
              <Search className='pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground' />
              <Input
                value={typeKeyword}
                onChange={(event) => setTypeKeyword(event.target.value)}
                placeholder='搜索类型编码、名称或说明'
                className='ps-9'
              />
            </div>
          </CardHeader>
          <CardContent className='min-h-0 flex-1 space-y-3 overflow-y-auto'>
            {types.filter((item) => typeMatchesKeyword(item, typeKeyword)).map((item) => (
              <TypeListItem
                key={item.dictTypeId}
                item={item}
                active={item.dictTypeId === selectedTypeId}
                onSelect={() =>
                  navigate({
                    replace: true,
                    search: (prev) => ({
                      ...(prev as Record<string, unknown>),
                      typeId: item.dictTypeId,
                      page: undefined,
                    }),
                  })
                }
                onDetail={() => setTypeDialog({ mode: 'detail', dictTypeId: item.dictTypeId })}
                onEdit={() => setTypeDialog({ mode: 'edit', dictTypeId: item.dictTypeId })}
              />
            ))}
            {types.filter((item) => typeMatchesKeyword(item, typeKeyword)).length === 0 ? (
              <div className='rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground'>
                没有匹配的字典类型
              </div>
            ) : null}
          </CardContent>
        </Card>

        <Card className='flex min-h-0 flex-col overflow-hidden'>
          <CardHeader className='space-y-3'>
            <div className='flex items-start justify-between gap-3'>
              <div>
                <CardTitle>
                  {selectedType ? `${selectedType.typeName} 的字典项` : '字典项'}
                </CardTitle>
                <CardDescription>
                  {selectedType
                    ? `${selectedType.typeCode} · ${selectedType.itemCount} 条字典项`
                    : '请选择左侧字典类型。'}
                </CardDescription>
              </div>
              <div className='flex flex-wrap items-center gap-2'>
                <Badge variant='outline'>共 {itemQuery.data?.total ?? 0} 条</Badge>
                <Button
                  variant='outline'
                  size='sm'
                  onClick={() =>
                    selectedTypeId &&
                    setItemDialog({
                      mode: 'create',
                      dictTypeId: selectedTypeId,
                    })
                  }
                  disabled={!selectedTypeId}
                >
                  <Plus />
                  新建
                </Button>
              </div>
            </div>
            <div className='flex flex-wrap items-center gap-2'>
              <div className='relative min-w-[220px] flex-1'>
                <Search className='pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground' />
                <Input
                  value={globalFilter ?? ''}
                  onChange={(event) => onGlobalFilterChange?.(event.target.value)}
                  placeholder='搜索字典项编码、名称、值或类型'
                  className='ps-9'
                />
              </div>
              <Button
                type='button'
                variant='ghost'
                onClick={() =>
                  {
                    onGlobalFilterChange?.('')
                    navigate({
                      search: (prev) => ({
                        ...(prev as Record<string, unknown>),
                        sorts: undefined,
                        pageSize: undefined,
                      }),
                    })
                  }
                }
              >
                清空条件
              </Button>
            </div>
          </CardHeader>
          <CardContent className='flex min-h-0 flex-1 flex-col gap-4 overflow-hidden'>
            {itemQuery.isLoading ? (
              <Skeleton className='h-80 w-full' />
            ) : itemQuery.isError ? (
              <Alert variant='destructive'>
                <AlertCircle />
                <AlertTitle>加载失败</AlertTitle>
                <AlertDescription>右侧字典项加载失败，请重试。</AlertDescription>
              </Alert>
            ) : (
              <>
                <div className='min-h-0 flex-1 overflow-auto rounded-lg border'>
                  <Table>
                    <TableHeader>
                      {table.getHeaderGroups().map((headerGroup) => (
                        <TableRow key={headerGroup.id}>
                          {headerGroup.headers.map((header) => (
                            <TableHead key={header.id}>
                              {header.isPlaceholder
                                ? null
                                : flexRender(header.column.columnDef.header, header.getContext())}
                            </TableHead>
                          ))}
                        </TableRow>
                      ))}
                    </TableHeader>
                    <TableBody>
                      {table.getRowModel().rows.length > 0 ? (
                        table.getRowModel().rows.map((row) => (
                          <TableRow key={row.id}>
                            {row.getVisibleCells().map((cell) => (
                              <TableCell key={cell.id}>
                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                              </TableCell>
                            ))}
                          </TableRow>
                        ))
                      ) : (
                        <TableRow>
                          <TableCell colSpan={6} className='h-24 text-center text-muted-foreground'>
                            当前类型下没有字典项
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </div>
                <DataTablePagination table={table} className='shrink-0' />
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <TypeDialog
        state={typeDialog}
        open={Boolean(typeDialog)}
        onOpenChange={(open) => setTypeDialog(open ? typeDialog : null)}
        onSaved={(dictTypeId) => {
          void queryClient.invalidateQueries({ queryKey: ['system-dict-management-types'] })
          if (dictTypeId) {
            navigate({
              replace: true,
              search: (prev) => ({
                ...(prev as Record<string, unknown>),
                typeId: dictTypeId,
                page: undefined,
              }),
            })
          }
        }}
      />
      <ItemDialog
        state={itemDialog}
        open={Boolean(itemDialog)}
        dictTypeOptions={types}
        onOpenChange={(open) => setItemDialog(open ? itemDialog : null)}
        onSaved={(dictTypeId) => {
          void queryClient.invalidateQueries({ queryKey: ['system-dict-management-items'] })
          if (dictTypeId) {
            navigate({
              replace: true,
              search: (prev) => ({
                ...(prev as Record<string, unknown>),
                typeId: dictTypeId,
                page: undefined,
              }),
            })
          }
        }}
      />
    </PageShell>
  )
}
