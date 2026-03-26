import { getApiErrorResponse } from '@/lib/api/client'
import {
  createCompany,
  createDepartment,
  createPost,
  getCompanyFormOptions,
  getDepartmentTree,
  listCompanies,
  listPosts,
  type DepartmentTreeNode,
} from '@/lib/api/system-org'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

type CompanyExportRow = {
  companyName: string
  status: '启用' | '停用'
}

type PostExportRow = {
  companyName: string
  departmentName: string
  postName: string
  status: '启用' | '停用'
}

const companyCsvHeaders = ['公司名称', '状态'] as const
const departmentCsvHeaders = ['所属公司', '上级部门路径', '部门名称', '状态'] as const
const postCsvHeaders = ['所属公司', '所属部门路径', '岗位名称', '状态'] as const

function toEnabled(value: string) {
  const normalized = value.trim()
  return normalized === '启用' || normalized.toUpperCase() === 'ENABLED' || normalized === 'true'
}

function escapeCsvCell(value: string) {
  if (value.includes(',') || value.includes('"') || value.includes('\n')) {
    return `"${value.split('"').join('""')}"`
  }
  return value
}

function buildCsv(headers: readonly string[], rows: string[][]) {
  const content = [headers.join(','), ...rows.map((row) => row.map(escapeCsvCell).join(','))].join('\n')
  return `\uFEFF${content}`
}

function downloadCsv(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  let currentCell = ''
  let currentRow: string[] = []
  let inQuotes = false

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index]
    const next = text[index + 1]

    if (char === '"') {
      if (inQuotes && next === '"') {
        currentCell += '"'
        index += 1
      } else {
        inQuotes = !inQuotes
      }
      continue
    }

    if (!inQuotes && char === ',') {
      currentRow.push(currentCell.trim())
      currentCell = ''
      continue
    }

    if (!inQuotes && (char === '\n' || char === '\r')) {
      if (char === '\r' && next === '\n') {
        index += 1
      }
      currentRow.push(currentCell.trim())
      if (currentRow.some((cell) => cell !== '')) {
        rows.push(currentRow)
      }
      currentRow = []
      currentCell = ''
      continue
    }

    currentCell += char
  }

  if (currentCell !== '' || currentRow.length > 0) {
    currentRow.push(currentCell.trim())
    if (currentRow.some((cell) => cell !== '')) {
      rows.push(currentRow)
    }
  }

  return rows
}

function assertHeaders(actual: string[] | undefined, expected: readonly string[]) {
  if (!actual || actual.length < expected.length) {
    throw new Error(`导入模板不正确，期望表头：${expected.join('、')}`)
  }
  const matches = expected.every((header, index) => actual[index]?.trim() === header)
  if (!matches) {
    throw new Error(`导入模板不正确，期望表头：${expected.join('、')}`)
  }
}

function flattenDepartmentNodes(
  nodes: DepartmentTreeNode[],
  parentPath = ''
): Array<{ node: DepartmentTreeNode; parentPath: string; fullPath: string }> {
  return nodes.flatMap((node) => {
    const fullPath = parentPath ? `${parentPath} / ${node.departmentName}` : node.departmentName
    return [
      { node, parentPath, fullPath },
      ...flattenDepartmentNodes(node.children, fullPath),
    ]
  })
}

export function exportCompaniesCsv(rows: CompanyExportRow[], filename = 'companies.csv') {
  downloadCsv(
    filename,
    buildCsv(
      companyCsvHeaders,
      rows.map((row) => [row.companyName, row.status])
    )
  )
}

export function exportDepartmentsCsv(rows: DepartmentTreeNode[], filename = 'departments.csv') {
  const flatRows = flattenDepartmentNodes(rows)
  downloadCsv(
    filename,
    buildCsv(
      departmentCsvHeaders,
      flatRows.map(({ node, parentPath }) => [
        node.companyName,
        parentPath,
        node.departmentName,
        node.status === 'ENABLED' ? '启用' : '停用',
      ])
    )
  )
}

export function exportPostsCsv(
  rows: PostExportRow[],
  departmentTree: DepartmentTreeNode[],
  filename = 'posts.csv'
) {
  const departmentMap = new Map(
    flattenDepartmentNodes(departmentTree).map(({ fullPath, node }) => [
      `${node.companyName}::${node.departmentName}`,
      fullPath,
    ])
  )
  downloadCsv(
    filename,
    buildCsv(
      postCsvHeaders,
      rows.map((row) => [
        row.companyName,
        departmentMap.get(`${row.companyName}::${row.departmentName}`) ?? row.departmentName,
        row.postName,
        row.status,
      ])
    )
  )
}

export async function exportAllCompaniesCsv(search: ListQuerySearch) {
  const records: CompanyExportRow[] = []
  let page = 1
  let totalPages = 1

  while (page <= totalPages) {
    const response = await listCompanies({
      ...search,
      page,
      pageSize: 100,
    })
    records.push(
      ...response.records.map((record) => ({
        companyName: record.companyName,
        status: (record.status === 'ENABLED' ? '启用' : '停用') as '启用' | '停用',
      }))
    )
    totalPages = response.pages
    page += 1
  }

  exportCompaniesCsv(
    records,
    'companies-filtered.csv'
  )
}

export async function exportAllPostsCsv(search: ListQuerySearch, departmentTree: DepartmentTreeNode[]) {
  const records: PostExportRow[] = []
  let page = 1
  let totalPages = 1

  while (page <= totalPages) {
    const response = await listPosts({
      ...search,
      page,
      pageSize: 100,
    })
    records.push(
      ...response.records.map((record) => ({
        companyName: record.companyName,
        departmentName: record.departmentName,
        postName: record.postName,
        status: (record.status === 'ENABLED' ? '启用' : '停用') as '启用' | '停用',
      }))
    )
    totalPages = response.pages
    page += 1
  }

  exportPostsCsv(
    records,
    departmentTree,
    'posts-filtered.csv'
  )
}

export async function importCompaniesCsv(file: File) {
  const rows = parseCsv(await file.text())
  assertHeaders(rows[0], companyCsvHeaders)

  let created = 0
  let skipped = 0
  for (const row of rows.slice(1)) {
    const [companyName = '', status = '启用'] = row
    if (!companyName.trim()) {
      continue
    }
    try {
      await createCompany({
        companyName: companyName.trim(),
        enabled: toEnabled(status),
      })
      created += 1
    } catch (error) {
      const apiError = getApiErrorResponse(error)
      if (apiError?.code === 'BIZ.COMPANY_NAME_DUPLICATED') {
        skipped += 1
        continue
      }
      throw error
    }
  }

  return { created, skipped }
}

export async function importDepartmentsCsv(file: File) {
  const rows = parseCsv(await file.text())
  assertHeaders(rows[0], departmentCsvHeaders)

  const companyOptions = await getCompanyFormOptions()
  const departmentTree = await getDepartmentTree()
  const companyMap = new Map(companyOptions.companies.map((item) => [item.name, item.id]))
  const departmentPathMap = new Map(
    flattenDepartmentNodes(departmentTree).map(({ node, fullPath }) => [
      `${node.companyName}::${fullPath}`,
      node.departmentId,
    ])
  )

  let created = 0
  let skipped = 0
  for (const row of rows.slice(1)) {
    const [companyName = '', parentPath = '', departmentName = '', status = '启用'] = row
    if (!companyName.trim() || !departmentName.trim()) {
      continue
    }
    const companyId = companyMap.get(companyName.trim())
    if (!companyId) {
      throw new Error(`未找到公司：${companyName}`)
    }
    const normalizedParentPath = parentPath.trim()
    const parentDepartmentId = normalizedParentPath
      ? departmentPathMap.get(`${companyName.trim()}::${normalizedParentPath}`) ?? null
      : null
    if (normalizedParentPath && !parentDepartmentId) {
      throw new Error(`未找到上级部门路径：${companyName} / ${normalizedParentPath}`)
    }

    try {
      await createDepartment({
        companyId,
        parentDepartmentId,
        departmentName: departmentName.trim(),
        enabled: toEnabled(status),
      })
      created += 1
    } catch (error) {
      const apiError = getApiErrorResponse(error)
      if (apiError?.code === 'BIZ.DEPARTMENT_NAME_DUPLICATED') {
        skipped += 1
      } else {
        throw error
      }
    }

    const fullPath = normalizedParentPath
      ? `${normalizedParentPath} / ${departmentName.trim()}`
      : departmentName.trim()
    if (!departmentPathMap.has(`${companyName.trim()}::${fullPath}`)) {
      const refreshedTree = await getDepartmentTree()
      departmentPathMap.clear()
      flattenDepartmentNodes(refreshedTree).forEach(({ node, fullPath: refreshedPath }) => {
        departmentPathMap.set(`${node.companyName}::${refreshedPath}`, node.departmentId)
      })
    }
  }

  return { created, skipped }
}

export async function importPostsCsv(file: File) {
  const rows = parseCsv(await file.text())
  assertHeaders(rows[0], postCsvHeaders)

  const departmentTree = await getDepartmentTree()
  const departmentPathMap = new Map(
    flattenDepartmentNodes(departmentTree).map(({ node, fullPath }) => [
      `${node.companyName}::${fullPath}`,
      node.departmentId,
    ])
  )

  let created = 0
  let skipped = 0
  for (const row of rows.slice(1)) {
    const [companyName = '', departmentPath = '', postName = '', status = '启用'] = row
    if (!companyName.trim() || !departmentPath.trim() || !postName.trim()) {
      continue
    }
    const departmentId = departmentPathMap.get(`${companyName.trim()}::${departmentPath.trim()}`)
    if (!departmentId) {
      throw new Error(`未找到所属部门路径：${companyName} / ${departmentPath}`)
    }
    try {
      await createPost({
        departmentId,
        postName: postName.trim(),
        enabled: toEnabled(status),
      })
      created += 1
    } catch (error) {
      const apiError = getApiErrorResponse(error)
      if (apiError?.code === 'BIZ.POST_NAME_DUPLICATED') {
        skipped += 1
        continue
      }
      throw error
    }
  }

  return { created, skipped }
}
