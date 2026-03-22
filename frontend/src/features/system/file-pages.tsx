import { startTransition, useEffect, useState, type ReactNode } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { useForm, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  Download,
  FileUp,
  Loader2,
  PencilLine,
  Save,
  Trash2,
} from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { getApiErrorResponse } from '@/lib/api/client'
import {
  deleteSystemFile,
  getSystemFileDetail,
  getSystemFileDownloadUrl,
  getSystemFilePreviewUrl,
  listSystemFiles,
  updateSystemFile,
  uploadSystemFile,
  type SaveSystemFilePayload,
  type SystemFileDetail,
  type SystemFileRecord,
  type SystemFileStatus,
} from '@/lib/api/system-files'
import { handleServerError } from '@/lib/handle-server-error'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

const fileListRoute = getRouteApi('/_authenticated/system/files/list')
const fileDetailRoute = getRouteApi('/_authenticated/system/files/$fileId/')
const fileEditRoute = getRouteApi('/_authenticated/system/files/$fileId/edit')

const fileFormSchema = z.object({
  displayName: z.string().trim().min(2, '文件名称至少需要 2 个字符'),
  remark: z.string().max(500, '说明最多 500 个字符').optional(),
})

type FileFormValues = z.infer<typeof fileFormSchema>

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function resolveStatusLabel(status: SystemFileStatus) {
  return status === 'ACTIVE' ? '正常' : '已删除'
}

function resolveStatusVariant(status: SystemFileStatus) {
  return status === 'ACTIVE' ? 'secondary' : 'outline'
}

function toFormValues(detail?: SystemFileDetail): FileFormValues {
  return {
    displayName: detail?.displayName ?? '',
    remark: detail?.remark ?? '',
  }
}

function applyFileFieldErrors(
  form: UseFormReturn<FileFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)
  apiError?.fieldErrors?.forEach((fieldError) => {
    if (fieldError.field === 'displayName' || fieldError.field === 'remark') {
      form.setError(fieldError.field, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })
  return apiError
}

function buildEmptyFilePage(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

const fileColumns: ColumnDef<SystemFileRecord>[] = [
  {
    accessorKey: 'displayName',
    header: '文件名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.displayName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.originalFilename}
        </span>
      </div>
    ),
  },
  { accessorKey: 'bucketName', header: 'Bucket' },
  {
    accessorKey: 'fileSize',
    header: '大小',
    cell: ({ row }) => formatFileSize(row.original.fileSize),
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveStatusVariant(row.original.status)}>
        {resolveStatusLabel(row.original.status)}
      </Badge>
    ),
  },
  {
    accessorKey: 'createdAt',
    header: '创建时间',
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
  {
    id: 'action',
    header: '操作',
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/files/$fileId'
            params={{ fileId: row.original.fileId }}
          >
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/files/$fileId/edit'
            params={{ fileId: row.original.fileId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

export function FileListPage() {
  const search = fileListRoute.useSearch()
  const navigate = fileListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-files', search],
    queryFn: () => listSystemFiles(search),
  })

  const data = query.data ?? buildEmptyFilePage(search)

  return (
    <ResourceListPage
      title='文件管理'
      description='基于 MinIO 元数据的文件运营后台，支持上传、详情、下载和逻辑删除。'
      endpoint='/api/v1/system/files/page'
      searchPlaceholder='搜索文件名称、原始文件名或备注'
      search={search}
      navigate={navigate}
      columns={fileColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '文件总数', value: String(data.total), hint: '当前页外不区分物理内容，只看元数据。' },
        { label: '当前分页', value: String(search.page), hint: '分页参数沿用统一查询契约。' },
        { label: '已删除标记', value: '逻辑删除', hint: '删除只变更状态，不清理对象存储。' },
      ]}
      createAction={{
        label: '上传文件',
        href: '/system/files/create',
      }}
    />
  )
}

function FilePageErrorState({
  title,
  description,
  retry,
}: {
  title: string
  description: string
  retry?: () => void
}) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>文件数据请求未成功，请重试或返回列表页。</AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
        <Button asChild variant='outline'>
          <Link to='/system/files/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      </div>
    </PageShell>
  )
}

function FilePageLoadingState({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <PageShell title={title} description={description}>
      <Card>
        <CardHeader>
          <Skeleton className='h-6 w-40' />
          <Skeleton className='h-4 w-full max-w-xl' />
        </CardHeader>
        <CardContent className='grid gap-4 md:grid-cols-2'>
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className='space-y-2 rounded-lg border p-4'>
              <Skeleton className='h-4 w-24' />
              <Skeleton className='h-5 w-full' />
              <Skeleton className='h-3 w-3/4' />
            </div>
          ))}
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function FileCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const form = useForm<FileFormValues>({
    resolver: zodResolver(fileFormSchema),
    defaultValues: {
      displayName: '',
      remark: '',
    },
  })

  const createMutation = useMutation({
    mutationFn: uploadSystemFile,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['system-files'] })
      toast.success('文件已上传')
      startTransition(() => {
        navigate({ to: '/system/files/list' })
      })
    },
    onError: (error) => {
      handleServerError(error)
      applyFileFieldErrors(form, error)
    },
  })

  const onSubmit = form.handleSubmit(async (values) => {
    if (!selectedFile) {
      form.setError('displayName', {
        type: 'server',
        message: '请先选择文件',
      })
      return
    }
    createMutation.mutate({
      file: selectedFile,
      displayName: values.displayName,
      remark: values.remark || null,
    })
  })

  return (
    <PageShell
      title='上传文件'
      description='上传文件时只保存元数据与对象存储引用，便于后续下载和追踪。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/files/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>文件信息</CardTitle>
          <CardDescription>先选择文件，再补充展示名称和备注。</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form className='grid gap-4' onSubmit={onSubmit}>
              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='displayName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>文件显示名</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：请假附件' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className='space-y-2'>
                  <FormLabel>选择文件</FormLabel>
                  <Input
                    type='file'
                    onChange={(event) => {
                      setSelectedFile(event.target.files?.[0] ?? null)
                    }}
                  />
                </div>
              </div>

              <FormField
                control={form.control}
                name='remark'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>备注</FormLabel>
                    <FormControl>
                      <Textarea placeholder='文件用途说明' rows={4} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className='flex flex-wrap gap-2'>
                <Button type='submit' disabled={createMutation.isPending}>
                  {createMutation.isPending ? (
                    <Loader2 className='mr-2 size-4 animate-spin' />
                  ) : (
                    <FileUp className='mr-2 size-4' />
                  )}
                  上传文件
                </Button>
                <Button asChild variant='outline'>
                  <Link to='/system/files/list'>取消返回列表</Link>
                </Button>
              </div>
            </form>
          </Form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function FileEditPage() {
  const { fileId } = fileEditRoute.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['system-files', fileId],
    queryFn: () => getSystemFileDetail(fileId),
  })

  const form = useForm<FileFormValues>({
    resolver: zodResolver(fileFormSchema),
    defaultValues: {
      displayName: '',
      remark: '',
    },
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(toFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const updateMutation = useMutation({
    mutationFn: (values: SaveSystemFilePayload) => updateSystemFile(fileId, values),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['system-files', fileId] })
      await queryClient.invalidateQueries({ queryKey: ['system-files'] })
      toast.success('文件信息已更新')
      startTransition(() => {
        navigate({ to: '/system/files/$fileId', params: { fileId } })
      })
    },
    onError: (error) => {
      handleServerError(error)
      applyFileFieldErrors(form, error)
    },
  })

  if (detailQuery.isLoading) {
    return (
      <FilePageLoadingState
        title='编辑文件'
        description='修改文件显示名和备注。'
      />
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <FilePageErrorState
        title='编辑文件'
        description='修改文件显示名和备注。'
        retry={detailQuery.refetch}
      />
    )
  }

  return (
    <PageShell
      title='编辑文件'
      description='仅修改元数据，不会变更对象存储中的文件内容。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/files/$fileId' params={{ fileId }}>
            <ArrowLeft data-icon='inline-start' />
            返回详情
          </Link>
        </Button>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>{detailQuery.data.displayName}</CardTitle>
          <CardDescription>{detailQuery.data.originalFilename}</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form
              className='grid gap-4'
              onSubmit={form.handleSubmit((values) =>
                updateMutation.mutate({
                  displayName: values.displayName,
                  remark: values.remark || null,
                })
              )}
            >
              <FormField
                control={form.control}
                name='displayName'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>文件显示名</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='remark'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>备注</FormLabel>
                    <FormControl>
                      <Textarea rows={4} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className='flex flex-wrap gap-2'>
                <Button type='submit' disabled={updateMutation.isPending}>
                  {updateMutation.isPending ? (
                    <Loader2 className='mr-2 size-4 animate-spin' />
                  ) : (
                    <Save className='mr-2 size-4' />
                  )}
                  保存修改
                </Button>
                <Button asChild variant='outline'>
                  <Link to='/system/files/$fileId' params={{ fileId }}>
                    取消返回详情
                  </Link>
                </Button>
              </div>
            </form>
          </Form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function FileDetailPage() {
  const { fileId } = fileDetailRoute.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const detailQuery = useQuery({
    queryKey: ['system-files', fileId],
    queryFn: () => getSystemFileDetail(fileId),
  })

  const deleteMutation = useMutation({
    mutationFn: () => deleteSystemFile(fileId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['system-files'] })
      toast.success('文件已标记为删除')
      startTransition(() => {
        navigate({ to: '/system/files/list' })
      })
    },
    onError: (error) => {
      handleServerError(error)
    },
  })

  if (detailQuery.isLoading) {
    return (
      <FilePageLoadingState
        title='文件详情'
        description='查看文件元数据、下载地址和预览地址。'
      />
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <FilePageErrorState
        title='文件详情'
        description='查看文件元数据、下载地址和预览地址。'
        retry={detailQuery.refetch}
      />
    )
  }

  const detail = detailQuery.data

  return (
    <PageShell
      title='文件详情'
      description='文件管理以 MinIO 元数据为中心，详情页展示对象引用和可访问地址。'
      actions={
        <>
          <Button asChild variant='outline'>
            <a href={getSystemFileDownloadUrl(fileId)}>
              <Download className='mr-2 size-4' />
              下载
            </a>
          </Button>
          <Button asChild variant='outline'>
            <a href={getSystemFilePreviewUrl(fileId)} target='_blank' rel='noreferrer'>
              预览
            </a>
          </Button>
          <Button asChild>
            <Link to='/system/files/$fileId/edit' params={{ fileId }}>
              <PencilLine className='mr-2 size-4' />
              编辑
            </Link>
          </Button>
          <Button
            variant='destructive'
            onClick={() => deleteMutation.mutate()}
            disabled={deleteMutation.isPending}
          >
            <Trash2 className='mr-2 size-4' />
            逻辑删除
          </Button>
        </>
      }
    >
      <div className='grid gap-4 lg:grid-cols-2'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
            <CardDescription>当前文件的核心元数据。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <FieldItem label='文件名称' value={detail.displayName} />
            <FieldItem label='原始文件名' value={detail.originalFilename} />
            <FieldItem label='Bucket' value={detail.bucketName} />
            <FieldItem label='Object' value={detail.objectName} />
            <FieldItem label='内容类型' value={detail.contentType} />
            <FieldItem label='文件大小' value={formatFileSize(detail.fileSize)} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>访问与状态</CardTitle>
            <CardDescription>下载地址、预览地址和删除状态。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <FieldItem
              label='状态'
              value={<Badge variant={resolveStatusVariant(detail.status)}>{resolveStatusLabel(detail.status)}</Badge>}
            />
            <FieldItem label='创建时间' value={formatDateTime(detail.createdAt)} />
            <FieldItem label='更新时间' value={formatDateTime(detail.updatedAt)} />
            <FieldItem label='删除时间' value={formatDateTime(detail.deletedAt)} />
            <FieldItem label='下载地址' value={detail.downloadUrl} />
            <FieldItem label='预览地址' value={detail.previewUrl} />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>备注</CardTitle>
        </CardHeader>
        <CardContent className='text-sm text-muted-foreground'>
          {detail.remark || '暂无备注'}
        </CardContent>
      </Card>
    </PageShell>
  )
}

function FieldItem({
  label,
  value,
}: {
  label: string
  value: ReactNode
}) {
  return (
    <div className='rounded-lg border p-4'>
      <p className='text-sm text-muted-foreground'>{label}</p>
      <div className='mt-2 text-sm font-medium'>{value}</div>
    </div>
  )
}
