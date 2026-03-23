import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import { listSystemUsers, type SystemUserRecord } from '@/lib/api/system-users'
import { listRoles, type SystemRoleRecord } from '@/lib/api/system-roles'
import { getDepartmentFormOptions } from '@/lib/api/system-org'

export type WorkflowPrincipalKind = 'USER' | 'ROLE' | 'DEPARTMENT'

export type WorkflowPrincipalOption = {
  id: string
  label: string
  description: string
  kind: WorkflowPrincipalKind
  companyId?: string
  parentId?: string | null
  groupLabel?: string
}

function buildSearch(keyword: string): ListQuerySearch {
  return {
    page: 1,
    pageSize: 20,
    keyword,
    filters: [],
    sorts: [],
    groups: [],
  }
}

function toUserOption(record: SystemUserRecord): WorkflowPrincipalOption {
  return {
    id: record.userId,
    label: record.displayName || record.username || record.userId,
    description: [record.username, record.departmentName, record.postName]
      .filter(Boolean)
      .join(' · '),
    kind: 'USER',
  }
}

function toRoleOption(record: SystemRoleRecord): WorkflowPrincipalOption {
  return {
    id: record.roleCode,
    label: record.roleName || record.roleCode,
    description: [record.roleCode, record.dataScopeSummary, record.roleCategory]
      .filter(Boolean)
      .join(' · '),
    kind: 'ROLE',
  }
}

function toDepartmentOption(record: {
  id: string
  name: string
  companyId: string
  companyName: string
  parentDepartmentId: string | null
  enabled: boolean
}): WorkflowPrincipalOption {
  return {
    id: record.id,
    label: record.name || record.id,
    description: [record.companyName]
      .filter(Boolean)
      .join(' · '),
    kind: 'DEPARTMENT',
    companyId: record.companyId,
    parentId: record.parentDepartmentId,
    groupLabel: record.companyName,
  }
}

export async function searchPrincipalOptions(
  kind: WorkflowPrincipalKind,
  keyword: string
): Promise<WorkflowPrincipalOption[]> {
  const search = buildSearch(keyword)

  if (kind === 'USER') {
    const response = await listSystemUsers(search)
    return response.records.map(toUserOption)
  }

  if (kind === 'ROLE') {
    const response = await listRoles(search)
    return response.records.map(toRoleOption)
  }

  const response = await getDepartmentFormOptions()
  const normalizedKeyword = keyword.trim().toLowerCase()
  return response.parentDepartments
    .filter((record) => {
      if (!normalizedKeyword) {
        return true
      }
      return [record.name, record.id, record.companyName]
        .filter(Boolean)
        .some((item) => item.toLowerCase().includes(normalizedKeyword))
    })
    .map(toDepartmentOption)
}
