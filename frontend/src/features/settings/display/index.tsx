import { ContentSection } from '../components/content-section'
import { DisplayForm } from './display-form'

// 显示设置页，只承载侧边栏可见项配置。
export function SettingsDisplay() {
  return (
    <ContentSection
      title='显示'
      desc='控制应用侧边栏中显示哪些项目。'
    >
      <DisplayForm />
    </ContentSection>
  )
}
