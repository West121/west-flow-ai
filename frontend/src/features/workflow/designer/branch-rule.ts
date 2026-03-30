import type { WorkflowEdge } from './types'

type LegacyCondition = {
  type?: string
  expression?: unknown
  fieldKey?: unknown
  operator?: unknown
  value?: unknown
  formulaExpression?: unknown
} | null | undefined

const operatorMap: Record<string, string> = {
  EQ: '==',
  NE: '!=',
  GT: '>',
  GE: '>=',
  LT: '<',
  LE: '<=',
}

function stringifyOperand(value: unknown) {
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  if (value === null || value === undefined) {
    return 'null'
  }

  const text = String(value).trim()
  if (!text.length) {
    return '""'
  }

  if (text === 'true' || text === 'false' || text === 'null') {
    return text
  }

  if (!Number.isNaN(Number(text)) && /^-?\d+(\.\d+)?$/.test(text)) {
    return text
  }

  return JSON.stringify(text)
}

export function conditionToRuleExpression(condition: unknown) {
  const value = condition as LegacyCondition
  if (!value?.type) {
    return ''
  }

  if (value.type === 'FORMULA') {
    return String(value.formulaExpression ?? value.expression ?? '').trim()
  }

  if (value.type === 'EXPRESSION') {
    return String(value.expression ?? '').trim()
  }

  if (value.type === 'FIELD') {
    const fieldKey = String(value.fieldKey ?? '').trim()
    const operator = operatorMap[String(value.operator ?? 'EQ')] ?? '=='
    const compareValue = stringifyOperand(value.value)
    if (!fieldKey) {
      return ''
    }
    return `${fieldKey} ${operator} ${compareValue}`
  }

  return ''
}

export function conditionToRuleSummary(condition: unknown) {
  const expression = conditionToRuleExpression(condition)
  if (!expression) {
    return '未配置分支规则'
  }

  if (expression.length <= 48) {
    return expression
  }

  return `${expression.slice(0, 48)}...`
}

export function buildFormulaCondition(expression: string) {
  const normalized = expression.trim()
  if (!normalized) {
    return undefined
  }

  return {
    type: 'FORMULA' as const,
    expression: normalized,
    formulaExpression: normalized,
  }
}

export function edgeRuleSummary(edge: WorkflowEdge) {
  return conditionToRuleSummary(edge.data?.condition)
}
