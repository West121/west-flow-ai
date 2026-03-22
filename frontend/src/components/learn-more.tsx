import { type Root, type Content, type Trigger } from '@radix-ui/react-popover'
import { CircleQuestionMark } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'

type LearnMoreProps = React.ComponentProps<typeof Root> & {
  contentProps?: React.ComponentProps<typeof Content>
  triggerProps?: React.ComponentProps<typeof Trigger>
}

// 轻量说明弹层，用于补充字段或页面的额外解释。
export function LearnMore({
  children,
  contentProps,
  triggerProps,
  ...props
}: LearnMoreProps) {
  return (
    <Popover {...props}>
      <PopoverTrigger
        asChild
        {...triggerProps}
        className={cn('size-5 rounded-full', triggerProps?.className)}
      >
        <Button variant='outline' size='icon'>
          <span className='sr-only'>Learn more</span>
          <CircleQuestionMark className='size-4 [&>circle]:hidden' />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        side='top'
        align='start'
        {...contentProps}
        className={cn('text-sm text-muted-foreground', contentProps?.className)}
      >
        {children}
      </PopoverContent>
    </Popover>
  )
}
