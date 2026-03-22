import { Telescope } from 'lucide-react'

export function ComingSoon() {
  return (
    <div className='h-svh'>
      <div className='m-auto flex h-full w-full flex-col items-center justify-center gap-2'>
        <Telescope size={72} />
        <h1 className='text-4xl leading-tight font-bold'>敬请期待</h1>
        <p className='text-center text-muted-foreground'>
          当前页面骨架尚未接入正式功能。 <br />
          后续里程碑会在这里继续扩展。
        </p>
      </div>
    </div>
  )
}
