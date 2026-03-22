import { startTransition, useEffect, useMemo, useState } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  Eye,
  EyeOff,
  Layers3,
  Loader2,
  Save,
  SquareMenu,
  Workflow,
} from 'lucide-react'
import { toast } from 'sonner'
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
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { getApiErrorResponse } from '@/lib/api/client'
import { handleServerError } from '@/lib/handle-server-error'
import {
  createMenu,
  getMenuDetail,
  getMenuFormOptions,
  listMenus,
  updateMenu,
  type MenuDetail,
  type MenuRecord,
  type MenuType,
  type SaveMenuPayload,
} from '@/lib/api/system-menus'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'

const menusRoute = getRouteApi('/_authenticated/system/menus/list')

const ROOT_VALUE = '__ROOT__'

const menuFormSchema = z.object({
  parentMenuId: z.string().nullable(),
  menuName: z.string().trim().min(2, '菜单名称至少需要 2 个字符'),
  menuType: z.enum(['DIRECTORY', 'MENU', 'BUTTON'], {
    message: '请选择菜单类型',
  }),
  routePath: z.string().nullable(),
  componentPath: z.string().nullable(),
  permissionCode: z.string().nullable(),
  iconName: z.string().nullable(),
  sortOrder: z.number().int().min(0, '排序值不能小于 0'),
  visible: z.boolean(),
  enabled: z.boolean(),
})

type MenuFormValues = z.infer<typeof menuFormSchema>
type SubmitAction = 'list' | 'continue'

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

function resolveStatusLabel(status: string) {
  return status === 'ENABLED' ? '启用' : '停用'
}

function resolveMenuTypeLabel(menuType: MenuType) {
  return (
    {
      DIRECTORY: '目录',
      MENU: '菜单',
      BUTTON: '按钮',
    } satisfies Record<MenuType, string>
  )[menuType]
}

function resolveMenuTypeVariant(menuType: MenuType) {
  return (
    {
      DIRECTORY: 'secondary',
      MENU: 'outline',
      BUTTON: 'default',
    } satisfies Record<MenuType, 'secondary' | 'outline' | 'default'>
  )[menuType]
}

function applyMenuFieldErrors(form: UseFormReturn<MenuFormValues>, error: unknown) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    switch (fieldError.field) {
      case 'parentMenuId':
      case 'menuName':
      case 'menuType':
      case 'routePath':
      case 'componentPath':
      case 'permissionCode':
      case 'iconName':
      case 'sortOrder':
      case 'visible':
      case 'enabled':
        form.setError(fieldError.field, {
          type: 'server',
          message: fieldError.message,
        })
        break
      default:
        break
    }
  })

  if (apiError?.code === 'BIZ.MENU_NAME_DUPLICATED') {
    form.setError('menuName', {
      type: 'server',
      message: apiError.message,
    })
  }

  return apiError
}

function MenuPageErrorState({
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
        <AlertDescription>
          菜单管理数据请求未成功，请重试或先返回列表页。
        </AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
        <Button asChild variant='outline'>
          <Link to='/system/menus/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      </div>
    </PageShell>
  )
}

function MenuPageLoadingState({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <PageShell title={title} description={description}>
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <Skeleton className='h-6 w-40' />
            <Skeleton className='h-4 w-full max-w-xl' />
          </CardHeader>
          <CardContent className='grid gap-4 md:grid-cols-2'>
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className='flex flex-col gap-2'>
                <Skeleton className='h-4 w-20' />
                <Skeleton className='h-10 w-full' />
              </div>
            ))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <Skeleton className='h-6 w-24' />
            <Skeleton className='h-4 w-full' />
          </CardHeader>
          <CardContent className='flex flex-col gap-3'>
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className='h-16 w-full' />
            ))}
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

function MenuDetailMetric({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Workflow
  label: string
  value: string
}) {
  return (
    <div className='rounded-lg border bg-muted/20 p-4'>
      <div className='flex items-center gap-2 text-sm text-muted-foreground'>
        <Icon className='size-4' />
        <span>{label}</span>
      </div>
      <p className='mt-3 text-sm font-medium'>{value}</p>
    </div>
  )
}

const menuColumns: ColumnDef<MenuRecord>[] = [
  {
    accessorKey: 'menuName',
    header: '菜单名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.menuName}</span>
        <span className='text-xs text-muted-foreground'>{row.original.menuId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'menuType',
    header: '类型',
    cell: ({ row }) => (
      <Badge variant={resolveMenuTypeVariant(row.original.menuType)}>
        {resolveMenuTypeLabel(row.original.menuType)}
      </Badge>
    ),
  },
  {
    accessorKey: 'parentMenuName',
    header: '父级菜单',
    cell: ({ row }) => row.original.parentMenuName ?? '顶级菜单',
  },
  {
    accessorKey: 'routePath',
    header: '路由路径',
    cell: ({ row }) => row.original.routePath ?? '-',
  },
  {
    accessorKey: 'sortOrder',
    header: '排序',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Badge
          variant={
            row.original.status === 'ENABLED' ? 'secondary' : 'outline'
          }
        >
          {resolveStatusLabel(row.original.status)}
        </Badge>
        <Badge variant='outline'>
          {row.original.visible ? '显示' : '隐藏'}
        </Badge>
      </div>
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
    enableSorting: false,
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link to='/system/menus/$menuId' params={{ menuId: row.original.menuId }}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/menus/$menuId/edit'
            params={{ menuId: row.original.menuId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

export function MenusListPage() {
  const search = menusRoute.useSearch()
  const navigate = menusRoute.useNavigate()
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['system-menus', search],
    queryFn: () => listMenus(search),
  })

  const summaries = useMemo(() => {
    const records = data?.records ?? []
    const visibleCount = records.filter((item) => item.visible).length
    const enabledCount = records.filter((item) => item.status === 'ENABLED').length
    const directoryCount = records.filter(
      (item) => item.menuType === 'DIRECTORY'
    ).length

    return [
      {
        label: '菜单总量',
        value: String(data?.total ?? 0),
        hint: '统一管理系统与流程平台的导航、页面入口和按钮权限。',
      },
      {
        label: '当前页可见',
        value: String(visibleCount),
        hint: '可见状态用于控制侧边栏、导航区和工作台入口的展示。',
      },
      {
        label: '目录节点',
        value: String(directoryCount),
        hint: `当前页启用 ${enabledCount} 项，便于继续扩展日志、字典、监控等系统模块。`,
      },
    ]
  }, [data])

  if (isLoading) {
    return (
      <PageShell
        title='菜单管理'
        description='系统管理中的菜单模块采用独立列表页，统一承载分页、模糊搜索、排序和后续分组能力。'
      >
        <Skeleton className='h-64 w-full' />
      </PageShell>
    )
  }

  if (isError || !data) {
    return (
      <MenuPageErrorState
        title='菜单管理'
        description='菜单列表数据未能加载成功。'
        retry={() => void refetch()}
      />
    )
  }

  return (
    <ResourceListPage
      title='菜单管理'
      description='菜单、目录、按钮全部按独立 CRUD 页面维护，列表页保留模糊查询、分页、排序和后续筛选/分组能力。'
      endpoint='/system/menus/page'
      searchPlaceholder='搜索菜单名称、路由路径或权限标识'
      search={search}
      navigate={navigate}
      columns={menuColumns}
      data={data.records}
      total={data.total}
      summaries={summaries}
      createAction={{
        label: '新建菜单',
        href: '/system/menus/create',
      }}
    />
  )
}

function toFormValues(detail?: MenuDetail): MenuFormValues {
  return {
    parentMenuId: detail?.parentMenuId ?? null,
    menuName: detail?.menuName ?? '',
    menuType: detail?.menuType ?? 'MENU',
    routePath: detail?.routePath ?? '',
    componentPath: detail?.componentPath ?? '',
    permissionCode: detail?.permissionCode ?? '',
    iconName: detail?.iconName ?? 'SquareMenu',
    sortOrder: detail?.sortOrder ?? 0,
    visible: detail?.visible ?? true,
    enabled: detail?.enabled ?? true,
  }
}

function toPayload(values: MenuFormValues): SaveMenuPayload {
  return {
    parentMenuId: values.parentMenuId,
    menuName: values.menuName.trim(),
    menuType: values.menuType,
    routePath: values.routePath?.trim() ? values.routePath.trim() : null,
    componentPath: values.componentPath?.trim()
      ? values.componentPath.trim()
      : null,
    permissionCode: values.permissionCode?.trim()
      ? values.permissionCode.trim()
      : null,
    iconName: values.iconName?.trim() ? values.iconName.trim() : null,
    sortOrder: values.sortOrder,
    visible: values.visible,
    enabled: values.enabled,
  }
}

function MenuFormPage({
  mode,
  menuId,
}: {
  mode: 'create' | 'edit'
  menuId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const isEdit = mode === 'edit'
  const form = useForm<MenuFormValues>({
    resolver: zodResolver(menuFormSchema),
    defaultValues: toFormValues(),
    mode: 'onBlur',
  })

  const optionsQuery = useQuery({
    queryKey: ['system-menu-form-options'],
    queryFn: getMenuFormOptions,
  })

  const detailQuery = useQuery({
    queryKey: ['system-menu', menuId],
    queryFn: () => getMenuDetail(menuId!),
    enabled: isEdit && Boolean(menuId),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(toFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: createMenu,
    onError: () => undefined,
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: SaveMenuPayload }) =>
      updateMenu(id, payload),
    onError: () => undefined,
  })

  const menuType = useWatch({
    control: form.control,
    name: 'menuType',
  })
  const parentMenuId = useWatch({
    control: form.control,
    name: 'parentMenuId',
  })
  const visible = useWatch({
    control: form.control,
    name: 'visible',
  })
  const enabled = useWatch({
    control: form.control,
    name: 'enabled',
  })
  const parentMenuOptions = useMemo(
    () =>
      (optionsQuery.data?.parentMenus ?? []).filter(
        (item) => item.id !== menuId && item.menuType !== 'BUTTON'
      ),
    [menuId, optionsQuery.data?.parentMenus]
  )
  const selectedParent = parentMenuOptions.find((item) => item.id === parentMenuId)
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const isInitialLoading =
    optionsQuery.isLoading || (isEdit && detailQuery.isLoading)

  async function onSubmit(values: MenuFormValues) {
    form.clearErrors()

    try {
      const payload = toPayload(values)
      const result = isEdit
        ? await updateMutation.mutateAsync({ id: menuId!, payload })
        : await createMutation.mutateAsync(payload)

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['system-menus'] }),
        queryClient.invalidateQueries({ queryKey: ['system-menu', result.menuId] }),
      ])

      toast.success(isEdit ? '菜单已更新' : '菜单已创建')

      if (submitAction === 'continue') {
        startTransition(() => {
          navigate({
            to: '/system/menus/$menuId/edit',
            params: { menuId: result.menuId },
            replace: isEdit,
          })
        })
        return
      }

      startTransition(() => {
        navigate({
          to: '/system/menus/list',
        })
      })
    } catch (error) {
      const apiError = applyMenuFieldErrors(form, error)

      if (!apiError || (!apiError.fieldErrors?.length && apiError.code !== 'BIZ.MENU_NAME_DUPLICATED')) {
        handleServerError(error)
      }
    }
  }

  if (isInitialLoading) {
    return (
      <MenuPageLoadingState
        title={isEdit ? '编辑菜单' : '新建菜单'}
        description='正在加载菜单详情与父级选项，请稍候。'
      />
    )
  }

  if (optionsQuery.isError || (isEdit && detailQuery.isError)) {
    return (
      <MenuPageErrorState
        title={isEdit ? '编辑菜单' : '新建菜单'}
        description='菜单表单依赖的数据未能加载成功。'
        retry={() => {
          void optionsQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
      />
    )
  }

  return (
    <PageShell
      title={isEdit ? '编辑菜单' : '新建菜单'}
      description={
        isEdit
          ? '编辑页独立维护菜单层级、路由和权限标识，保存后可返回列表或继续编辑。'
          : '新建页独立维护菜单节点，不使用弹窗或抽屉代替正式表单。'
      }
      actions={
        <>
          <Button
            type='submit'
            form='system-menu-form'
            disabled={isSubmitting}
            onClick={() => setSubmitAction('list')}
          >
            {isSubmitting ? (
              <Loader2 className='animate-spin' data-icon='inline-start' />
            ) : (
              <Save data-icon='inline-start' />
            )}
            保存并返回列表
          </Button>
          <Button
            type='submit'
            form='system-menu-form'
            variant='outline'
            disabled={isSubmitting}
            onClick={() => setSubmitAction('continue')}
          >
            {isSubmitting ? (
              <Loader2 className='animate-spin' data-icon='inline-start' />
            ) : (
              <Save data-icon='inline-start' />
            )}
            保存并继续编辑
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/menus/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>菜单表单</CardTitle>
            <CardDescription>
              菜单管理覆盖目录、页面菜单和按钮权限，后续日志、字典、监控模块可以直接按这套模型扩展。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form
                id='system-menu-form'
                className='grid gap-6'
                onSubmit={form.handleSubmit(onSubmit)}
              >
                <div className='grid gap-4 md:grid-cols-2'>
                  <FormField
                    control={form.control}
                    name='menuName'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>菜单名称</FormLabel>
                        <FormControl>
                          <Input placeholder='例如：菜单管理' {...field} />
                        </FormControl>
                        <FormDescription>
                          同级菜单下名称唯一，用于侧边栏、面包屑和权限识别。
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name='menuType'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>菜单类型</FormLabel>
                        <Select
                          value={field.value}
                          onValueChange={(value) =>
                            field.onChange(value as MenuType)
                          }
                        >
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue placeholder='请选择菜单类型' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {optionsQuery.data?.menuTypes.map((item) => (
                              <SelectItem key={item.code} value={item.code}>
                                {item.name}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name='parentMenuId'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>父级菜单</FormLabel>
                        <Select
                          value={field.value ?? ROOT_VALUE}
                          onValueChange={(value) =>
                            field.onChange(value === ROOT_VALUE ? null : value)
                          }
                        >
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue placeholder='请选择父级菜单' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value={ROOT_VALUE}>顶级菜单</SelectItem>
                            {parentMenuOptions.map((item) => (
                              <SelectItem key={item.id} value={item.id}>
                                {item.name} · {resolveMenuTypeLabel(item.menuType)}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          按钮节点不能作为父级菜单，目录节点适合承载子菜单分组。
                        </FormDescription>
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
                            {...field}
                            onChange={(event) =>
                              field.onChange(Number(event.target.value || 0))
                            }
                          />
                        </FormControl>
                        <FormDescription>
                          列表默认按排序值升序展示，适合控制左侧导航顺序。
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name='routePath'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>路由路径</FormLabel>
                        <FormControl>
                          <Input
                            placeholder='/system/menus/list'
                            {...field}
                            value={field.value ?? ''}
                          />
                        </FormControl>
                        <FormDescription>
                          页面菜单建议填写实际路由，目录和按钮可留空。
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name='componentPath'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>组件路径</FormLabel>
                        <FormControl>
                          <Input
                            placeholder='system/menus/list'
                            {...field}
                            value={field.value ?? ''}
                          />
                        </FormControl>
                        <FormDescription>
                          用于后续前后端对齐页面映射和动态权限校验。
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name='permissionCode'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>权限标识</FormLabel>
                        <FormControl>
                          <Input
                            placeholder='system:menu:view'
                            {...field}
                            value={field.value ?? ''}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name='iconName'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>图标名称</FormLabel>
                        <FormControl>
                          <Input
                            placeholder='SquareMenu'
                            {...field}
                            value={field.value ?? ''}
                          />
                        </FormControl>
                        <FormDescription>
                          保留 lucide 图标名，便于后续菜单配置驱动化。
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>

                <div className='grid gap-4 md:grid-cols-2'>
                  <FormField
                    control={form.control}
                    name='visible'
                    render={({ field }) => (
                      <FormItem className='flex items-center justify-between rounded-lg border p-4'>
                        <div className='space-y-1'>
                          <FormLabel>导航可见</FormLabel>
                          <FormDescription>
                            控制菜单是否出现在侧边栏、顶部导航或工作台入口。
                          </FormDescription>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name='enabled'
                    render={({ field }) => (
                      <FormItem className='flex items-center justify-between rounded-lg border p-4'>
                        <div className='space-y-1'>
                          <FormLabel>启用状态</FormLabel>
                          <FormDescription>
                            控制菜单是否可被用户访问，停用后仍保留配置数据。
                          </FormDescription>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </div>
              </form>
            </Form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>配置预览</CardTitle>
            <CardDescription>
              当前表单会实时展示菜单类型、层级和显示态，便于确认导航结构。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3'>
            <MenuDetailMetric
              icon={Layers3}
              label='菜单类型'
              value={resolveMenuTypeLabel(menuType)}
            />
            <MenuDetailMetric
              icon={Workflow}
              label='父级层级'
              value={selectedParent?.name ?? '顶级菜单'}
            />
            <MenuDetailMetric
              icon={visible ? Eye : EyeOff}
              label='展示状态'
              value={`${visible ? '显示' : '隐藏'} / ${enabled ? '启用' : '停用'}`}
            />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

function MenuDetailPage({ menuId }: { menuId: string }) {
  const query = useQuery({
    queryKey: ['system-menu', menuId],
    queryFn: () => getMenuDetail(menuId),
  })

  if (query.isLoading) {
    return (
      <MenuPageLoadingState
        title='菜单详情'
        description='正在加载菜单详情，请稍候。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <MenuPageErrorState
        title='菜单详情'
        description='菜单详情加载失败。'
        retry={() => void query.refetch()}
      />
    )
  }

  const detail = query.data

  return (
    <PageShell
      title='菜单详情'
      description='详情页独立展示菜单层级、路由、权限标识和显示状态，便于后续审计和联调。'
      actions={
        <>
          <Button asChild>
            <Link to='/system/menus/$menuId/edit' params={{ menuId }}>
              <Save data-icon='inline-start' />
              编辑菜单
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/menus/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>{detail.menuName}</CardTitle>
            <CardDescription>
              菜单 ID：{detail.menuId}，父级：{detail.parentMenuName ?? '顶级菜单'}
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4 md:grid-cols-2'>
            <MenuDetailMetric
              icon={Layers3}
              label='菜单类型'
              value={resolveMenuTypeLabel(detail.menuType)}
            />
            <MenuDetailMetric
              icon={Workflow}
              label='父级菜单'
              value={detail.parentMenuName ?? '顶级菜单'}
            />
            <MenuDetailMetric
              icon={SquareMenu}
              label='路由路径'
              value={detail.routePath ?? '-'}
            />
            <MenuDetailMetric
              icon={SquareMenu}
              label='组件路径'
              value={detail.componentPath ?? '-'}
            />
            <MenuDetailMetric
              icon={SquareMenu}
              label='权限标识'
              value={detail.permissionCode ?? '-'}
            />
            <MenuDetailMetric
              icon={detail.visible ? Eye : EyeOff}
              label='显示与启用'
              value={`${detail.visible ? '显示' : '隐藏'} / ${
                detail.enabled ? '启用' : '停用'
              }`}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>配置摘要</CardTitle>
            <CardDescription>
              这部分用于联调菜单驱动渲染、权限控制和导航排序。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3'>
            <MenuDetailMetric
              icon={SquareMenu}
              label='图标名称'
              value={detail.iconName ?? '-'}
            />
            <MenuDetailMetric
              icon={Layers3}
              label='排序值'
              value={String(detail.sortOrder)}
            />
            <MenuDetailMetric
              icon={Workflow}
              label='菜单层级'
              value={detail.parentMenuId ? '子级节点' : '顶级节点'}
            />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function MenuCreatePage() {
  return <MenuFormPage mode='create' />
}

export function MenuEditPage() {
  const { menuId } = getRouteApi('/_authenticated/system/menus/$menuId/edit').useParams()

  return <MenuFormPage mode='edit' menuId={menuId} />
}

export function MenuDetailPageRoute() {
  const { menuId } = getRouteApi('/_authenticated/system/menus/$menuId/').useParams()

  return <MenuDetailPage menuId={menuId} />
}
