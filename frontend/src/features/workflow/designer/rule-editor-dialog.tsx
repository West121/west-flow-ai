import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import Editor, {
  type BeforeMount,
  type OnMount,
} from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import { CircleAlert, FunctionSquare, ListTree, Sigma } from 'lucide-react'
import {
  Dialog as ProDialog,
  DialogContent as ProDialogContent,
  DialogFooter as ProDialogFooter,
} from '@/components/pro-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import {
  getProcessRuleMetadata,
  type ProcessRuleMetadataFunction,
  type ProcessRuleMetadataResponse,
  type ProcessRuleMetadataSnippet,
  type ProcessRuleMetadataVariable,
  validateProcessRule,
} from '@/lib/api/workflow'
import type { ProcessDefinitionMeta } from './dsl'

const workflowRuleLanguageId = 'workflow-rule'

type MonacoEditorInstance = Monaco.editor.IStandaloneCodeEditor
type MonacoNamespace = typeof Monaco

type RuleEditorDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  processDefinitionId?: string
  processMeta: ProcessDefinitionMeta
  nodeId: string
  nodeLabel: string
  initialExpression: string
  onSave: (expression: string) => void
}

let languageRegistered = false
let completionProviderRegistered = false
let completionContext: ProcessRuleMetadataResponse = {
  variables: [],
  functions: [],
  snippets: [],
}

const fallbackSnippets: ProcessRuleMetadataSnippet[] = [
  {
    key: 'and',
    label: '并且',
    description: '组合两个条件',
    template: '($leftCondition) && ($rightCondition)',
  },
  {
    key: 'or',
    label: '或者',
    description: '满足任一条件即可',
    template: '($leftCondition) || ($rightCondition)',
  },
  {
    key: 'ifElse',
    label: '条件分支',
    description: '按条件返回不同值',
    template: 'ifElse($condition, trueValue, falseValue)',
  },
  {
    key: 'contains',
    label: '包含',
    description: '判断字符串或集合是否包含指定值',
    template: 'contains($target, keyword)',
  },
  {
    key: 'isBlank',
    label: '判空',
    description: '判断字段是否为空',
    template: 'isBlank($value)',
  },
]

function buildFallbackMetadata(
  processMeta: ProcessDefinitionMeta,
  nodeId: string,
  nodeLabel: string
): ProcessRuleMetadataResponse {
  const formVariables: ProcessRuleMetadataVariable[] = processMeta.formFields.map((field) => ({
    key: field.fieldKey,
    label: field.label,
    scope: 'FORM',
    valueType: field.valueType,
    description: `流程表单字段：${field.label}`,
  }))

  const aggregateVariables: ProcessRuleMetadataVariable[] = [
    {
      key: 'detailCount',
      label: '子表行数',
      scope: 'SUBFORM_AGGREGATE',
      valueType: 'number',
      expression: 'detail.count',
      description: '所有明细行的总数量。',
    },
    {
      key: 'detailAmountSum',
      label: '子表金额合计',
      scope: 'SUBFORM_AGGREGATE',
      valueType: 'number',
      expression: 'detail.sum("amount")',
      description: '按金额字段汇总子表总额。',
    },
    {
      key: 'detailAny',
      label: '子表任一满足',
      scope: 'SUBFORM_AGGREGATE',
      valueType: 'boolean',
      expression: 'detail.any("field", value)',
      description: '任一明细命中即可返回 true。',
    },
    {
      key: 'detailAll',
      label: '子表全部满足',
      scope: 'SUBFORM_AGGREGATE',
      valueType: 'boolean',
      expression: 'detail.all("field", value)',
      description: '全部明细命中才返回 true。',
    },
  ]

  const processVariables: ProcessRuleMetadataVariable[] = [
    {
      key: 'processKey',
      label: '流程编码',
      scope: 'PROCESS',
      valueType: 'string',
      description: processMeta.processKey || '当前流程编码',
    },
    {
      key: 'processName',
      label: '流程名称',
      scope: 'PROCESS',
      valueType: 'string',
      description: processMeta.processName || '当前流程名称',
    },
    {
      key: 'processCategory',
      label: '流程分类',
      scope: 'PROCESS',
      valueType: 'string',
      description: processMeta.category || '当前流程分类',
    },
    {
      key: 'processFormKey',
      label: '流程表单 Key',
      scope: 'PROCESS',
      valueType: 'string',
      description: processMeta.processFormKey || '当前流程表单标识',
    },
  ]

  const nodeVariables: ProcessRuleMetadataVariable[] = [
    {
      key: 'currentNodeId',
      label: '当前节点 ID',
      scope: 'NODE',
      valueType: 'string',
      description: nodeId,
    },
    {
      key: 'currentNodeName',
      label: '当前节点名称',
      scope: 'NODE',
      valueType: 'string',
      description: nodeLabel,
    },
    {
      key: 'instanceId',
      label: '流程实例 ID',
      scope: 'SYSTEM',
      valueType: 'string',
      description: '运行时可用，设计期用于表达式编写提示。',
    },
    {
      key: 'taskId',
      label: '任务 ID',
      scope: 'SYSTEM',
      valueType: 'string',
      description: '运行时当前任务标识。',
    },
  ]

  const functions: ProcessRuleMetadataFunction[] = [
    {
      name: 'ifElse',
      label: '条件函数',
      signature: 'ifElse(condition, whenTrue, whenFalse)',
      category: '条件',
      description: '按条件返回两个值中的一个。',
      snippet: 'ifElse(condition, whenTrue, whenFalse)',
    },
    {
      name: 'contains',
      label: '包含判断',
      signature: 'contains(target, keyword)',
      category: '字符串',
      description: '判断字符串或集合是否包含目标值。',
      snippet: 'contains(target, keyword)',
    },
    {
      name: 'daysBetween',
      label: '日期间隔',
      signature: 'daysBetween(leftDate, rightDate)',
      category: '日期',
      description: '返回两个日期之间的天数差。',
      snippet: 'daysBetween(startDate, endDate)',
    },
    {
      name: 'isBlank',
      label: '判空函数',
      signature: 'isBlank(value)',
      category: '条件',
      description: '判断变量是否为空。',
      snippet: 'isBlank(value)',
    },
  ]

  return {
    variables: [...formVariables, ...aggregateVariables, ...processVariables, ...nodeVariables],
    functions,
    snippets: fallbackSnippets,
  }
}

function variableReference(item: ProcessRuleMetadataVariable) {
  const raw = item.expression?.trim()
  if (raw?.startsWith('$')) {
    return raw
  }
  return `$${raw || item.key}`
}

function registerWorkflowRuleLanguage(monaco: MonacoNamespace) {
  if (!languageRegistered) {
    monaco.languages.register({ id: workflowRuleLanguageId })
    monaco.languages.setMonarchTokensProvider(workflowRuleLanguageId, {
      tokenizer: {
        root: [
          [/\b(true|false|null)\b/, 'keyword'],
          [/\b(ifElse|contains|daysBetween|isBlank)\b/, 'predefined'],
          [/\$[a-zA-Z_][\w.]*/, 'variable'],
          [/\$?[a-zA-Z_][\w.]*/, 'identifier'],
          [/"([^"\\]|\\.)*$/, 'string.invalid'],
          [/"/, { token: 'string.quote', bracket: '@open', next: '@string' }],
          [/\d+(\.\d+)?/, 'number'],
          [/[<>]=?|==|!=|&&|\|\|/, 'operator'],
          [/[()]/, '@brackets'],
          [/[{}]/, '@brackets'],
        ],
        string: [
          [/[^\\"]+/, 'string'],
          [/\\./, 'string.escape.invalid'],
          [/"/, { token: 'string.quote', bracket: '@close', next: '@pop' }],
        ],
      },
    })
    monaco.languages.setLanguageConfiguration(workflowRuleLanguageId, {
      autoClosingPairs: [
        { open: '(', close: ')' },
        { open: '"', close: '"' },
      ],
      surroundingPairs: [
        { open: '(', close: ')' },
        { open: '"', close: '"' },
      ],
      brackets: [
        ['(', ')'],
        ['{', '}'],
      ],
    })
    monaco.editor.defineTheme('workflow-rule-theme', {
      base: 'vs',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: '7c3aed', fontStyle: 'bold' },
        { token: 'predefined', foreground: '1d4ed8', fontStyle: 'bold' },
        { token: 'variable', foreground: '0f766e', fontStyle: 'bold' },
        { token: 'identifier', foreground: '111827' },
        { token: 'number', foreground: 'b45309' },
        { token: 'string', foreground: '047857' },
        { token: 'operator', foreground: '7c2d12' },
      ],
      colors: {
        'editor.background': '#ffffff',
      },
    })
    languageRegistered = true
  }

  if (!completionProviderRegistered) {
    monaco.languages.registerCompletionItemProvider(workflowRuleLanguageId, {
      triggerCharacters: ['$', '.', '(', '"'],
      provideCompletionItems(model, position) {
        const lineContent = model.getLineContent(position.lineNumber)
        const beforeCursor = lineContent.slice(0, position.column - 1)
        const matchedWord = beforeCursor.match(/\$?[A-Za-z_][\w.]*$/)?.[0] ?? ''
        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: position.column - matchedWord.length,
          endColumn: position.column,
        }

        const variableItems = completionContext.variables.map((item) => ({
          label: item.key,
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: variableReference(item),
          filterText: `${item.key} ${variableReference(item)} ${item.label}`,
          sortText: `0-${item.key}`,
          detail: `${item.scope} · ${item.valueType}`,
          documentation: `${item.label}${item.description ? `\n\n${item.description}` : ''}`,
          range,
        }))

        const functionItems = completionContext.functions.map((item) => ({
          label: item.name,
          kind: monaco.languages.CompletionItemKind.Function,
          insertText: item.snippet || item.signature,
          insertTextRules:
            monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          detail: item.signature,
          documentation: item.description ?? item.label,
          range,
        }))

        const snippetItems = completionContext.snippets.map((item) => ({
          label: item.label,
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: item.template,
          insertTextRules:
            monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          detail: item.description ?? item.key,
          range,
        }))

        return {
          suggestions: [...variableItems, ...functionItems, ...snippetItems],
        }
      },
    })
    completionProviderRegistered = true
  }
}

function groupLabel(scope: ProcessRuleMetadataVariable['scope']) {
  switch (scope) {
    case 'FORM':
      return '表单主表字段'
    case 'SUBFORM_AGGREGATE':
      return '子表聚合'
    case 'PROCESS':
      return '流程上下文'
    case 'NODE':
      return '节点上下文'
    case 'SYSTEM':
      return '高级上下文'
    default:
      return '上下文'
  }
}

function validationErrorSummary(errors: Array<{ message: string }>) {
  if (!errors.length) {
    return null
  }
  return errors[0]?.message ?? '规则校验失败'
}

export function RuleEditorDialog({
  open,
  onOpenChange,
  processDefinitionId,
  processMeta,
  nodeId,
  nodeLabel,
  initialExpression,
  onSave,
}: RuleEditorDialogProps) {
  const [expression, setExpression] = useState(initialExpression)
  const [validationMessage, setValidationMessage] = useState<string | null>(null)
  const [validationPending, setValidationPending] = useState(false)
  const [validationOk, setValidationOk] = useState(true)
  const editorRef = useRef<MonacoEditorInstance | null>(null)
  const monacoRef = useRef<MonacoNamespace | null>(null)
  const validationSeqRef = useRef(0)

  useEffect(() => {
    if (open) {
      setExpression(initialExpression)
      setValidationMessage(null)
      setValidationOk(true)
      setValidationPending(false)
    }
  }, [initialExpression, open])

  const metadataQuery = useQuery({
    queryKey: ['process-rule-metadata', processDefinitionId ?? 'draft', nodeId],
    queryFn: () =>
      getProcessRuleMetadata({
        processDefinitionId,
        nodeId,
      }),
    enabled: open,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  const metadata = useMemo(() => {
    return (
      metadataQuery.data ??
      buildFallbackMetadata(processMeta, nodeId, nodeLabel)
    )
  }, [metadataQuery.data, nodeId, nodeLabel, processMeta])

  useEffect(() => {
    completionContext = metadata
  }, [metadata])

  useEffect(() => {
    const monaco = monacoRef.current
    const editor = editorRef.current
    if (!monaco || !editor) {
      return
    }

    if (!expression.trim()) {
      monaco.editor.setModelMarkers(editor.getModel()!, workflowRuleLanguageId, [])
      setValidationMessage(null)
      setValidationPending(false)
      setValidationOk(true)
      return
    }

    const currentSeq = validationSeqRef.current + 1
    validationSeqRef.current = currentSeq
    setValidationPending(true)

    const timer = window.setTimeout(async () => {
      try {
        const result = await validateProcessRule({
          processDefinitionId,
          nodeId,
          expression,
        })

        if (validationSeqRef.current !== currentSeq || !editor.getModel()) {
          return
        }

        const markers = result.errors.map((item) => ({
          message: item.message,
          startLineNumber: item.line ?? 1,
          startColumn: item.column ?? 1,
          endLineNumber: item.line ?? 1,
          endColumn:
            item.column && item.endOffset && item.endOffset > item.column
              ? item.endOffset
              : (item.column ?? 1) + 1,
          severity: monaco.MarkerSeverity.Error,
        }))
        monaco.editor.setModelMarkers(editor.getModel()!, workflowRuleLanguageId, markers)
        setValidationMessage(
          result.valid
            ? result.summary || '规则校验通过'
            : validationErrorSummary(result.errors)
        )
        setValidationOk(result.valid)
      } catch {
        if (validationSeqRef.current !== currentSeq || !editor.getModel()) {
          return
        }
        monaco.editor.setModelMarkers(editor.getModel()!, workflowRuleLanguageId, [])
        setValidationMessage('暂时无法完成后端校验，保存后会在发布阶段再次校验。')
        setValidationOk(true)
      } finally {
        if (validationSeqRef.current === currentSeq) {
          setValidationPending(false)
        }
      }
    }, 320)

    return () => window.clearTimeout(timer)
  }, [expression, nodeId, processDefinitionId])

  const groupedVariables = useMemo(() => {
    const groups = new Map<string, ProcessRuleMetadataVariable[]>()
    metadata.variables.forEach((item) => {
      const key = groupLabel(item.scope)
      const existing = groups.get(key) ?? []
      existing.push(item)
      groups.set(key, existing)
    })
    return Array.from(groups.entries())
  }, [metadata.variables])

  const groupedFunctions = useMemo(() => {
    const groups = new Map<string, ProcessRuleMetadataFunction[]>()
    metadata.functions.forEach((item) => {
      const key = item.category?.trim() || '内置函数'
      const existing = groups.get(key) ?? []
      existing.push(item)
      groups.set(key, existing)
    })
    return Array.from(groups.entries())
  }, [metadata.functions])

  const insertSnippet = (snippet: string) => {
    const editor = editorRef.current
    if (!editor) {
      setExpression((current) => `${current}${current ? '\n' : ''}${snippet}`)
      return
    }

    const selection = editor.getSelection()
    const position = editor.getPosition()
    const range =
      selection ??
      (position
        ? {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: position.column,
            endColumn: position.column,
          }
        : {
            startLineNumber: 1,
            endLineNumber: 1,
            startColumn: 1,
            endColumn: 1,
          })

    editor.executeEdits('workflow-rule-insert', [
      {
        range,
        text: snippet,
        forceMoveMarkers: true,
      },
    ])
    const nextValue = editor.getValue()
    setExpression(nextValue)
    editor.focus()
  }

  const beforeMount: BeforeMount = (monaco) => {
    monacoRef.current = monaco
    registerWorkflowRuleLanguage(monaco)
  }

  const onMount: OnMount = (editor, monaco) => {
    editorRef.current = editor
    monacoRef.current = monaco
    registerWorkflowRuleLanguage(monaco)
    editor.onDidChangeModelContent((event) => {
      if (!event.changes.length) {
        return
      }
      const latestChange = event.changes[event.changes.length - 1]
      if (!latestChange?.text) {
        return
      }
      const position = editor.getPosition()
      if (!position) {
        return
      }
      const lineContent = editor.getModel()?.getLineContent(position.lineNumber) ?? ''
      const beforeCursor = lineContent.slice(0, position.column - 1)
      if (/\$[A-Za-z_][\w.]*$/.test(beforeCursor) || beforeCursor.endsWith('$')) {
        editor.trigger('workflow-rule', 'editor.action.triggerSuggest', {})
      }
    })
    editor.focus()
  }

  return (
    <ProDialog open={open} onOpenChange={onOpenChange}>
      <ProDialogContent
        title='分支规则'
        description='用统一规则表达式定义当前连线的命中条件。简单规则直接写，复杂规则用右侧函数和模板拼装。'
        className='h-[min(86vh,860px)] max-h-[calc(100vh-2rem)] p-0'
        bodyClassName='h-full p-0'
        bodyScrollable={false}
        showCloseButton={false}
        draggable={false}
        minimizable={false}
      >
        <div className='flex min-h-0 flex-1 flex-col'>
          <section className='h-[180px] shrink-0 border-b lg:h-[200px]'>
            <div className='h-full min-h-0'>
              <Editor
                beforeMount={beforeMount}
                onMount={onMount}
                language={workflowRuleLanguageId}
                value={expression}
                onChange={(next) => setExpression(next ?? '')}
                options={{
                  minimap: { enabled: false },
                  fontSize: 14,
                  lineHeight: 21,
                  wordWrap: 'on',
                  smoothScrolling: true,
                  scrollBeyondLastLine: false,
                  automaticLayout: true,
                  suggestOnTriggerCharacters: true,
                  quickSuggestions: {
                    other: true,
                    comments: false,
                    strings: false,
                  },
                  padding: { top: 12, bottom: 12 },
                  suggest: {
                    showWords: false,
                  },
                }}
                theme='workflow-rule-theme'
              />
            </div>
          </section>

          <section className='grid min-h-0 flex-1 overflow-hidden lg:grid-cols-2'>
            <ScrollArea className='h-full min-h-0 border-b lg:border-r lg:border-b-0'>
              <div className='space-y-5 px-5 py-4'>
                <div className='flex items-center gap-2'>
                  <ListTree className='size-4 text-primary' />
                  <p className='text-sm font-semibold'>上下文</p>
                </div>
                {groupedVariables.map(([group, items]) => (
                  <div key={group} className='space-y-3'>
                    <div className='flex items-center gap-2'>
                      <p className='text-sm font-medium'>{group}</p>
                      <Badge variant='outline'>{items.length}</Badge>
                    </div>
                    <div className='space-y-2'>
                      {items.map((item) => (
                        <button
                          key={`${group}:${item.key}`}
                          type='button'
                          className='flex w-full flex-col rounded-2xl border px-3 py-2 text-left transition hover:border-primary/40 hover:bg-muted/30'
                          onClick={() => insertSnippet(variableReference(item))}
                        >
                          <div className='flex items-center justify-between gap-3'>
                            <span className='text-sm font-medium'>{item.label}</span>
                            <Badge variant='secondary'>{item.valueType}</Badge>
                          </div>
                          <span className='font-mono text-xs text-muted-foreground'>
                            {variableReference(item)}
                          </span>
                          {item.description ? (
                            <span className='mt-1 text-xs text-muted-foreground'>
                              {item.description}
                            </span>
                          ) : null}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>

            <ScrollArea className='h-full min-h-0 border-b lg:border-b-0'>
              <div className='space-y-5 px-5 py-4'>
                <div className='flex items-center gap-2'>
                  <FunctionSquare className='size-4 text-primary' />
                  <p className='text-sm font-semibold'>规则构件</p>
                </div>

                <div className='space-y-3'>
                  <div className='flex items-center gap-2'>
                    <Sigma className='size-4 text-muted-foreground' />
                    <p className='text-sm font-medium'>模板片段</p>
                  </div>
                  <div className='space-y-2'>
                    {metadata.snippets.map((item) => (
                      <button
                        key={item.key}
                        type='button'
                        className='flex w-full flex-col rounded-2xl border px-3 py-2 text-left transition hover:border-primary/40 hover:bg-muted/30'
                        onClick={() => insertSnippet(item.template)}
                      >
                        <div className='flex items-center justify-between gap-3'>
                          <span className='inline-flex items-center gap-2 text-sm font-medium'>
                            <span className='inline-flex h-5 min-w-5 items-center justify-center rounded-md bg-muted px-1.5 font-mono text-[11px] text-muted-foreground'>
                              &lt;&gt;
                            </span>
                            {item.label}
                          </span>
                          <Badge variant='outline'>模板</Badge>
                        </div>
                        <span className='font-mono text-xs text-muted-foreground'>
                          {item.template}
                        </span>
                        {item.description ? (
                          <span className='mt-1 text-xs text-muted-foreground'>
                            {item.description}
                          </span>
                        ) : null}
                      </button>
                    ))}
                  </div>
                </div>

                {groupedFunctions.map(([group, items]) => (
                  <div key={group} className='space-y-3'>
                    <p className='text-sm font-medium'>{group}</p>
                    <div className='space-y-2'>
                      {items.map((item) => (
                        <button
                          key={`${group}:${item.name}`}
                          type='button'
                          className='flex w-full flex-col rounded-2xl border px-3 py-2 text-left transition hover:border-primary/40 hover:bg-muted/30'
                          onClick={() => insertSnippet(item.snippet || item.signature)}
                        >
                          <div className='flex items-center justify-between gap-3'>
                            <span className='inline-flex items-center gap-2 text-sm font-medium'>
                              <span className='inline-flex h-5 min-w-5 items-center justify-center rounded-md bg-muted px-1.5 font-mono text-[11px] text-muted-foreground'>
                                ƒ
                              </span>
                              {item.label}
                            </span>
                            <Badge variant='outline'>{item.name}</Badge>
                          </div>
                          <span className='font-mono text-xs text-muted-foreground'>
                            {item.signature}
                          </span>
                          {item.description ? (
                            <span className='mt-1 text-xs text-muted-foreground'>
                              {item.description}
                            </span>
                          ) : null}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>
          </section>
        </div>

        <ProDialogFooter className='border-t px-6 py-4'>
          <div className='mr-auto flex items-center gap-2 text-sm text-muted-foreground'>
            <CircleAlert className='size-4' />
            <span>{validationMessage || '保存后会统一按分支规则写入 DSL，并在发布时再次校验。'}</span>
            {validationPending ? (
              <Badge variant='secondary'>校验中</Badge>
            ) : validationMessage ? (
              <Badge
                variant='secondary'
                className={cn(
                  validationOk
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                    : 'border-red-200 bg-red-50 text-red-700'
                )}
              >
                {validationOk ? '已通过' : '有错误'}
              </Badge>
            ) : null}
          </div>
          <Button type='button' variant='outline' onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            type='button'
            onClick={() => {
              onSave(expression)
              onOpenChange(false)
            }}
            disabled={!expression.trim()}
          >
            应用规则
          </Button>
        </ProDialogFooter>
      </ProDialogContent>
    </ProDialog>
  )
}
