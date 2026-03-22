import { ContentSection } from '../components/content-section'
import { DisplayForm } from './display-form'

// 显示设置页，只承载侧边栏可见项配置。
export function SettingsDisplay() {
  return (
    <ContentSection
      title='Display'
      desc="Turn items on or off to control what's displayed in the app."
    >
      <DisplayForm />
    </ContentSection>
  )
}
