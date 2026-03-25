import { ContentSection } from '../components/content-section'
import { NotificationsForm } from './notifications-form'

// 通知设置页，只承载通知偏好配置。
export function SettingsNotifications() {
  return (
    <ContentSection
      title='通知'
      desc='配置消息和邮件通知方式。'
    >
      <NotificationsForm />
    </ContentSection>
  )
}
