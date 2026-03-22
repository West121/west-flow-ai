import { ContentSection } from '../components/content-section'
import { NotificationsForm } from './notifications-form'

// 通知设置页，只承载通知偏好配置。
export function SettingsNotifications() {
  return (
    <ContentSection
      title='Notifications'
      desc='Configure how you receive notifications.'
    >
      <NotificationsForm />
    </ContentSection>
  )
}
