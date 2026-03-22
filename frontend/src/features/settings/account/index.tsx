import { ContentSection } from '../components/content-section'
import { AccountForm } from './account-form'

// 账号设置页，只承载账号信息编辑表单。
export function SettingsAccount() {
  return (
    <ContentSection
      title='Account'
      desc='Update your account settings. Set your preferred language and
          timezone.'
    >
      <AccountForm />
    </ContentSection>
  )
}
