import { useRef } from 'react'
import { Upload } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function ProTableImport({
  onImport,
  disabled = false,
}: {
  onImport?: (file: File) => void
  disabled?: boolean
}) {
  const inputRef = useRef<HTMLInputElement | null>(null)

  return (
    <>
      <input
        ref={inputRef}
        type='file'
        accept='.csv,text/csv'
        className='hidden'
        onChange={(event) => {
          const file = event.target.files?.[0]
          if (file) {
            onImport?.(file)
          }
          event.currentTarget.value = ''
        }}
      />
      <Button
        variant='outline'
        size='sm'
        type='button'
        onClick={() => inputRef.current?.click()}
        disabled={!onImport || disabled}
      >
        <Upload />
        导入
      </Button>
    </>
  )
}
