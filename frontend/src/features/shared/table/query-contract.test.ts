import { describe, expect, it } from 'vitest'
import {
  listQuerySearchSchema,
  stripDefaultListQuerySearchValues,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'

describe('listQuerySearchSchema', () => {
  it('applies M0 defaults and preserves structured query fields', () => {
    const parsed = listQuerySearchSchema.parse({
      filters: [{ field: 'status', operator: 'eq', value: 'ENABLED' }],
      sorts: [{ field: 'createdAt', direction: 'desc' }],
      groups: [{ field: 'departmentName' }],
    })

    expect(parsed).toEqual({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [{ field: 'status', operator: 'eq', value: 'ENABLED' }],
      sorts: [{ field: 'createdAt', direction: 'desc' }],
      groups: [{ field: 'departmentName' }],
    })
  })

  it('maps query state to the pagination request body', () => {
    expect(
      toPaginationRequest(
        listQuerySearchSchema.parse({
          page: 2,
          pageSize: 50,
          keyword: '张三',
        })
      )
    ).toEqual({
      page: 2,
      pageSize: 50,
      keyword: '张三',
      filters: [],
      sorts: [],
      groups: [],
    })
  })

  it('strips default query values for clean urls', () => {
    expect(
      stripDefaultListQuerySearchValues(
        listQuerySearchSchema.parse({
          page: 1,
          pageSize: 20,
          keyword: '',
          filters: [],
          sorts: [],
          groups: [],
        })
      )
    ).toEqual({
      page: undefined,
      pageSize: undefined,
      keyword: undefined,
      filters: undefined,
      sorts: undefined,
      groups: undefined,
    })
  })
})
