import { Upload } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function ProTableImport({
  onImport,
  disabled = false,
}: {
  onImport?: () => void
  disabled?: boolean
}) {
  return (
    <Button
      variant='outline'
      size='sm'
      type='button'
      onClick={onImport}
      disabled={!onImport || disabled}
    >
      <Upload />
      导入
    </Button>
  )
}
