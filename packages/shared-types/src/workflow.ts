export type ProcessRuleMetadataVariable = {
  key: string
  label: string
  scope: 'FORM' | 'SUBFORM_AGGREGATE' | 'PROCESS' | 'NODE' | 'SYSTEM'
  valueType: string
  description?: string | null
  expression?: string | null
}

export type ProcessRuleMetadataFunction = {
  name: string
  label: string
  signature: string
  description?: string | null
  category?: string | null
  snippet?: string | null
}

export type ProcessRuleMetadataSnippet = {
  key: string
  label: string
  description?: string | null
  template: string
}

export type ProcessRuleMetadataResponse = {
  variables: ProcessRuleMetadataVariable[]
  functions: ProcessRuleMetadataFunction[]
  snippets: ProcessRuleMetadataSnippet[]
}

export type ProcessRuleValidationIssue = {
  message: string
  line?: number | null
  column?: number | null
  startOffset?: number | null
  endOffset?: number | null
}

export type ProcessRuleValidationResponse = {
  valid: boolean
  normalizedExpression: string | null
  summary: string | null
  errors: ProcessRuleValidationIssue[]
  availableFunctions?: string[]
}
