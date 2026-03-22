import { toast } from 'sonner'

// 将提交结果以 toast 形式展示，方便表单调试和演示。
export function showSubmittedData(
  data: unknown,
  title: string = 'You submitted the following values:'
) {
  toast.message(title, {
    description: (
      // w-[340px]
      <pre className='mt-2 w-full overflow-x-auto rounded-md bg-slate-950 p-4'>
        <code className='text-white'>{JSON.stringify(data, null, 2)}</code>
      </pre>
    ),
  })
}
