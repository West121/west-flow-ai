import z from 'zod'

export const filterOperatorSchema = z.enum([
  'eq',
  'ne',
  'in',
  'not_in',
  'gt',
  'gte',
  'lt',
  'lte',
  'between',
  'like',
  'prefix_like',
  'suffix_like',
  'is_null',
  'is_not_null',
])

export const filterItemSchema = z.object({
  field: z.string(),
  operator: filterOperatorSchema,
  value: z.unknown(),
})

export const sortItemSchema = z.object({
  field: z.string(),
  direction: z.enum(['asc', 'desc']),
})

export const groupItemSchema = z.object({
  field: z.string(),
})

export const listQuerySearchSchema = z.object({
  page: z.coerce.number().catch(1).default(1),
  pageSize: z.coerce.number().catch(10).default(10),
  keyword: z.string().catch('').default(''),
  filters: z.array(filterItemSchema).catch([]).default([]),
  sorts: z.array(sortItemSchema).catch([]).default([]),
  groups: z.array(groupItemSchema).catch([]).default([]),
})

export const listQueryRouteSearchSchema = z.object({
  page: z.coerce.number().optional().catch(undefined),
  pageSize: z.coerce.number().optional().catch(undefined),
  keyword: z.string().optional().catch(undefined),
  filters: z.array(filterItemSchema).optional().catch(undefined),
  sorts: z.array(sortItemSchema).optional().catch(undefined),
  groups: z.array(groupItemSchema).optional().catch(undefined),
})

export type FilterItem = z.infer<typeof filterItemSchema>
export type SortItem = z.infer<typeof sortItemSchema>
export type GroupItem = z.infer<typeof groupItemSchema>
export type ListQuerySearch = z.infer<typeof listQuerySearchSchema>
export type ListQueryRouteSearch = z.infer<typeof listQueryRouteSearchSchema>

export type ListQueryState = ListQuerySearch & {
  columns: Array<{
    key: string
    visible: boolean
  }>
}

export function normalizeListQuerySearch(
  search: Partial<ListQuerySearch> | ListQueryRouteSearch | undefined | null
): ListQuerySearch {
  return {
    page: search?.page ?? 1,
    pageSize: search?.pageSize ?? 10,
    keyword: search?.keyword ?? '',
    filters: search?.filters ?? [],
    sorts: search?.sorts ?? [],
    groups: search?.groups ?? [],
  }
}

export function stripDefaultListQuerySearchValues(search: ListQuerySearch) {
  return {
    page: search.page > 1 ? search.page : undefined,
    pageSize: search.pageSize !== 10 ? search.pageSize : undefined,
    keyword: search.keyword.trim() !== '' ? search.keyword : undefined,
    filters: search.filters.length > 0 ? search.filters : undefined,
    sorts: search.sorts.length > 0 ? search.sorts : undefined,
    groups: search.groups.length > 0 ? search.groups : undefined,
  }
}

// 将列表查询态整理成接口请求可直接使用的结构。
export function toPaginationRequest(state: ListQuerySearch) {
  return {
    page: state.page,
    pageSize: state.pageSize,
    keyword: state.keyword,
    filters: state.filters ?? [],
    sorts: state.sorts ?? [],
    groups: state.groups ?? [],
  }
}
