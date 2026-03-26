import { useMemo } from 'react'
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type EdgeProps,
} from '@xyflow/react'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { workflowNodeTemplates } from './palette'
import { useWorkflowDesignerStore } from './store'

const quickInsertKinds = [
  'approver',
  'condition',
  'inclusive',
  'parallel',
  'subprocess',
  'dynamic-builder',
  'cc',
  'timer',
  'trigger',
  'end',
] as const

export function WorkflowQuickInsertEdge(props: EdgeProps) {
  const insertNodeOnEdge = useWorkflowDesignerStore((state) => state.insertNodeOnEdge)
  const {
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    markerEnd,
    markerStart,
    style,
    interactionWidth,
  } = props
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  })
  const quickInsertTemplates = useMemo(
    () =>
      workflowNodeTemplates.filter((template) =>
        quickInsertKinds.includes(
          template.kind as (typeof quickInsertKinds)[number]
        )
      ),
    []
  )

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        markerStart={markerStart}
        style={style}
        interactionWidth={interactionWidth}
      />
      <EdgeLabelRenderer>
        <div
          className='pointer-events-auto absolute z-30'
          style={{
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
          }}
        >
          <Popover>
            <PopoverTrigger asChild>
              <Button
                type='button'
                size='icon'
                variant='outline'
                className='size-8 rounded-full border-primary/30 bg-background/95 shadow-sm'
                aria-label='在线上快速新增节点'
              >
                <Plus className='size-4' />
              </Button>
            </PopoverTrigger>
            <PopoverContent align='center' className='w-72 p-3'>
              <div className='grid grid-cols-2 gap-2'>
                {quickInsertTemplates.map((template) => {
                  const Icon = template.icon
                  return (
                    <button
                      key={template.kind}
                      type='button'
                      className={cn(
                        'rounded-xl border bg-background px-3 py-2 text-left transition',
                        'hover:border-primary/40 hover:bg-primary/[0.04]'
                      )}
                      onClick={() => insertNodeOnEdge(id, template)}
                    >
                      <div className='mb-1 flex items-center gap-2'>
                        <div className='flex size-7 items-center justify-center rounded-lg bg-primary/10 text-primary'>
                          <Icon className='size-3.5' />
                        </div>
                        <span className='text-sm font-medium'>{template.label}</span>
                      </div>
                      <p className='text-[11px] leading-4 text-muted-foreground'>
                        {template.description}
                      </p>
                    </button>
                  )
                })}
              </div>
            </PopoverContent>
          </Popover>
        </div>
      </EdgeLabelRenderer>
    </>
  )
}
