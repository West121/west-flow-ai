import { Link } from '@tanstack/react-router'
import { FilePenLine } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { PageShell } from '@/features/shared/page-shell'

type DetailSection = {
  title: string
  description: string
  items: Array<{
    label: string
    value: string
  }>
}

type ResourceDetailPageProps = {
  title: string
  description: string
  editHref: string
  listHref: string
  statusBadges: string[]
  sections: DetailSection[]
}

export function ResourceDetailPage({
  title,
  description,
  editHref,
  listHref,
  statusBadges,
  sections,
}: ResourceDetailPageProps) {
  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <>
          <Button asChild>
            <Link to={editHref} search={{}}>
              <FilePenLine data-icon='inline-start' />
              编辑用户
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to={listHref} search={{}}>返回列表</Link>
          </Button>
        </>
      }
    >
      <div className='flex flex-wrap gap-2'>
        {statusBadges.map((badge) => (
          <Badge key={badge} variant='secondary'>
            {badge}
          </Badge>
        ))}
      </div>

      <div className='grid gap-4 lg:grid-cols-2'>
        {sections.map((section) => (
          <Card key={section.title}>
            <CardHeader>
              <CardTitle>{section.title}</CardTitle>
              <CardDescription>{section.description}</CardDescription>
            </CardHeader>
            <CardContent className='grid gap-3 sm:grid-cols-2'>
              {section.items.map((item) => (
                <div key={item.label} className='rounded-lg border p-4'>
                  <p className='text-sm text-muted-foreground'>{item.label}</p>
                  <p className='mt-2 text-sm font-medium'>{item.value}</p>
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>
    </PageShell>
  )
}
