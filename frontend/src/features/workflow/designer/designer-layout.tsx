import type { ReactNode } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

type WorkflowDesignerLayoutProps = {
  palette: ReactNode
  canvas: ReactNode
  flowSettings: ReactNode
  nodeSettings: ReactNode
  activeTab: 'flow' | 'node'
  onActiveTabChange: (value: 'flow' | 'node') => void
}

// 设计器三栏布局只管结构，不碰具体表单字段，方便后续把右侧配置继续拆分。
export function WorkflowDesignerLayout({
  palette,
  canvas,
  flowSettings,
  nodeSettings,
  activeTab,
  onActiveTabChange,
}: WorkflowDesignerLayoutProps) {
  return (
    <div
      data-testid='workflow-designer-layout'
      className='grid h-[calc(100vh-12rem)] min-h-[760px] gap-4 xl:grid-cols-[280px_minmax(0,1fr)_360px]'
    >
      <aside
        data-testid='workflow-designer-palette'
        className='min-h-0 overflow-hidden rounded-3xl border bg-card/95 shadow-sm'
      >
        <ScrollArea className='h-full'>
          <div className='p-4'>{palette}</div>
        </ScrollArea>
      </aside>

      <main
        data-testid='workflow-designer-canvas'
        className='flex min-w-0 min-h-0 flex-1 justify-center'
      >
        <div className='flex min-h-0 w-full max-w-[1440px] flex-1 flex-col'>
          {canvas}
        </div>
      </main>

      <aside
        data-testid='workflow-designer-properties'
        className='min-h-0 overflow-hidden rounded-3xl border bg-card/95 shadow-sm'
      >
        <Card className='flex h-full flex-col border-0 shadow-none'>
          <CardHeader className='space-y-2 border-b pb-4'>
            <CardTitle>属性面板</CardTitle>
          </CardHeader>
          <CardContent className='flex min-h-0 flex-1 flex-col gap-4 pt-4'>
            <Tabs
              value={activeTab}
              onValueChange={(value) => onActiveTabChange(value as 'flow' | 'node')}
              className='flex min-h-0 flex-1 flex-col'
            >
              <TabsList className='grid w-full grid-cols-2'>
                <TabsTrigger value='flow'>流程属性</TabsTrigger>
                <TabsTrigger value='node'>节点属性</TabsTrigger>
              </TabsList>

              <TabsContent value='flow' className='mt-4 flex min-h-0 flex-1'>
                <ScrollArea className='h-full pr-2'>
                  <div data-testid='workflow-designer-flow-panel' className='space-y-4'>
                    {flowSettings}
                  </div>
                </ScrollArea>
              </TabsContent>

              <TabsContent value='node' className='mt-4 flex min-h-0 flex-1'>
                <ScrollArea className='h-full pr-2'>
                  <div data-testid='workflow-designer-node-panel' className='space-y-4'>
                    {nodeSettings}
                  </div>
                </ScrollArea>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>
      </aside>
    </div>
  )
}
