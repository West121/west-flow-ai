import { ContentSection } from '../components/content-section'
import { AppearanceForm } from './appearance-form'

// 外观设置页，只承载主题和字体偏好配置。
export function SettingsAppearance() {
  return (
    <ContentSection
      title='Appearance'
      desc='Customize the appearance of the app. Automatically switch between day
          and night themes.'
    >
      <AppearanceForm />
    </ContentSection>
  )
}
