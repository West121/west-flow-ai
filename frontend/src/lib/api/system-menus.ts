import {
  type ListQuerySearch,
  toPaginationRequest,
} from '@/features/shared/table/query-contract'
import { apiClient, unwrapResponse, type ApiSuccessResponse } from './client'

export type MenuStatus = 'ENABLED' | 'DISABLED'
export type MenuType = 'DIRECTORY' | 'MENU' | 'BUTTON'

type BasePageResponse<T> = {
  page: number
  pageSize: number
  total: number
  pages: number
  records: T[]
  groups: Array<{
    field: string
    value: string
  }>
}

export type MenuRecord = {
  menuId: string
  parentMenuName: string | null
  menuName: string
  menuType: MenuType
  routePath: string | null
  permissionCode: string | null
  sortOrder: number
  visible: boolean
  status: MenuStatus
  createdAt: string
}

export type MenuPageResponse = BasePageResponse<MenuRecord>

export type MenuDetail = {
  menuId: string
  parentMenuId: string | null
  parentMenuName: string | null
  menuName: string
  menuType: MenuType
  routePath: string | null
  componentPath: string | null
  permissionCode: string | null
  iconName: string | null
  sortOrder: number
  visible: boolean
  enabled: boolean
}

export type MenuFormOptions = {
  menuTypes: Array<{
    code: MenuType
    name: string
  }>
  parentMenus: Array<{
    id: string
    name: string
    menuType: MenuType
    enabled: boolean
  }>
}

export type SaveMenuPayload = {
  parentMenuId: string | null
  menuName: string
  menuType: MenuType
  routePath: string | null
  componentPath: string | null
  permissionCode: string | null
  iconName: string | null
  sortOrder: number
  visible: boolean
  enabled: boolean
}

export async function listMenus(
  search: ListQuerySearch
): Promise<MenuPageResponse> {
  const response = await apiClient.post<ApiSuccessResponse<MenuPageResponse>>(
    '/system/menus/page',
    toPaginationRequest(search)
  )

  return unwrapResponse(response)
}

export async function getMenuDetail(menuId: string): Promise<MenuDetail> {
  const response = await apiClient.get<ApiSuccessResponse<MenuDetail>>(
    `/system/menus/${menuId}`
  )

  return unwrapResponse(response)
}

export async function getMenuFormOptions(): Promise<MenuFormOptions> {
  const response = await apiClient.get<ApiSuccessResponse<MenuFormOptions>>(
    '/system/menus/options'
  )

  return unwrapResponse(response)
}

export async function createMenu(
  payload: SaveMenuPayload
): Promise<{ menuId: string }> {
  const response = await apiClient.post<ApiSuccessResponse<{ menuId: string }>>(
    '/system/menus',
    payload
  )

  return unwrapResponse(response)
}

export async function updateMenu(
  menuId: string,
  payload: SaveMenuPayload
): Promise<{ menuId: string }> {
  const response = await apiClient.put<ApiSuccessResponse<{ menuId: string }>>(
    `/system/menus/${menuId}`,
    payload
  )

  return unwrapResponse(response)
}
