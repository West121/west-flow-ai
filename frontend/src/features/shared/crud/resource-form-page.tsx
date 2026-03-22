import { Link } from '@tanstack/react-router'
import { AlertCircle, Save } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { PageShell } from '@/features/shared/page-shell'

type FormField = {
  label: string
  value: string
  hint: string
}

type FormSection = {
  title: string
  description: string
  fields: FormField[]
}

type ResourceFormPageProps = {
  title: string
  description: string
  listHref: string
  sections: FormSection[]
}

// 表单页通用骨架，保留保存、继续编辑和返回列表的固定布局。
export function ResourceFormPage({
  title,
  description,
  listHref,
  sections,
}: ResourceFormPageProps) {
  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <>
          <Button disabled>
            <Save data-icon='inline-start' />
            保存并返回列表
          </Button>
          <Button variant='outline' disabled>
            <Save data-icon='inline-start' />
            保存并继续编辑
          </Button>
          <Button asChild variant='ghost'>
            <Link to={listHref} search={{}}>取消返回列表</Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <div className='flex flex-col gap-4'>
          {sections.map((section) => (
            <Card key={section.title}>
              <CardHeader>
                <CardTitle>{section.title}</CardTitle>
                <CardDescription>{section.description}</CardDescription>
              </CardHeader>
              <CardContent className='grid gap-3 md:grid-cols-2'>
                {section.fields.map((field) => (
                  <div
                    key={field.label}
                    className='rounded-lg border border-dashed p-4'
                  >
                    <p className='text-sm font-medium'>{field.label}</p>
                    <p className='mt-2 text-sm'>{field.value}</p>
                    <p className='mt-2 text-xs text-muted-foreground'>
                      {field.hint}
                    </p>
                  </div>
                ))}
              </CardContent>
            </Card>
          ))}
        </div>

        <div className='flex flex-col gap-4'>
          <Alert>
            <AlertCircle />
            <AlertTitle>表单接入说明</AlertTitle>
            <AlertDescription>
              M0 先交付独立创建页与编辑页骨架。M1 将在此页接入
              react-hook-form、zod 校验、字段级错误映射和 AI 智能填报入口。
            </AlertDescription>
          </Alert>

          <Card>
            <CardHeader>
              <CardTitle>固定动作规范</CardTitle>
              <CardDescription>当前页面必须长期保持以下交互结构。</CardDescription>
            </CardHeader>
            <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
              <p>1. 保存并返回列表</p>
              <p>2. 保存并继续编辑</p>
              <p>3. 取消返回列表</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </PageShell>
  )
}
