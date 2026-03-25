import { ContentSection } from '../components/content-section'
import { AppearanceForm } from './appearance-form'

// 外观设置页，只承载主题和字体偏好配置。
export function SettingsAppearance() {
  return (
    <ContentSection
      title='外观'
      desc='调整应用主题和字体偏好。'
    >
      <AppearanceForm />
    </ContentSection>
  )
}
