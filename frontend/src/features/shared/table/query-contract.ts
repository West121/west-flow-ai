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
  pageSize: z.coerce.number().catch(20).default(20),
  keyword: z.string().catch('').default(''),
  filters: z.array(filterItemSchema).catch([]).default([]),
  sorts: z.array(sortItemSchema).catch([]).default([]),
  groups: z.array(groupItemSchema).catch([]).default([]),
})

export type FilterItem = z.infer<typeof filterItemSchema>
export type SortItem = z.infer<typeof sortItemSchema>
export type GroupItem = z.infer<typeof groupItemSchema>
export type ListQuerySearch = z.infer<typeof listQuerySearchSchema>

export type ListQueryState = ListQuerySearch & {
  columns: Array<{
    key: string
    visible: boolean
  }>
}

export function stripDefaultListQuerySearchValues(search: ListQuerySearch) {
  return {
    page: search.page > 1 ? search.page : undefined,
    pageSize: search.pageSize !== 20 ? search.pageSize : undefined,
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
